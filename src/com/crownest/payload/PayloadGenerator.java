package com.crownest.payload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs an MSFVenom command to produce a payload file.
 *
 * The command is executed as an argument vector, never through a shell, and the
 * only values interpolated into it are the validated LHOST and LPORT. That keeps
 * a hostile IP/port string from turning into shell injection.
 */
public final class PayloadGenerator {

    private static final int TIMEOUT_SECONDS = 180;

    /** Outcome of a generation attempt. */
    public record Result(boolean success, Path file, String commandShown, String output) { }

    private PayloadGenerator() {
    }

    /** True when msfvenom can be found on this machine. */
    public static boolean isAvailable() {
        return resolveBinary() != null;
    }

    private static String resolveBinary() {
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        String[] candidates = win
            ? new String[]{
                "C:\\metasploit-framework\\bin\\msfvenom.bat",
                "C:\\Program Files\\Metasploit\\msfvenom.bat",
              }
            : new String[]{
                "/usr/bin/msfvenom",
                "/usr/local/bin/msfvenom",
                "/opt/metasploit-framework/bin/msfvenom",
                System.getProperty("user.home") + "/.rbenv/shims/msfvenom",
              };
        for (String c : candidates) {
            if (Files.isExecutable(Paths.get(c))) {
                return c;
            }
        }
        // Fall back to a PATH lookup: `where` on Windows, `which` elsewhere.
        try {
            Process p = new ProcessBuilder(win ? "where" : "which", "msfvenom").start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0 && !out.isEmpty()) {
                return out.lines().findFirst().orElse(null);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }

    /**
     * The exact argument vector that would be executed, with "msfvenom" as the
     * head token. This is the single source of truth used by both the preview
     * and the real run, so what you see is what runs.
     */
    public static List<String> argv(MsfvenomCatalog.Entry entry, String ip, String port) {
        // Tokenise the template FIRST, then substitute per token. Because ip and
        // port are placed into already-separated tokens, no value they contain
        // (spaces, quotes, shell metacharacters) can split into extra arguments
        // or reshape the vector. Combined with ProcessBuilder(List) never using a
        // shell, this makes both shell injection and argument injection impossible.
        List<String> tokens = tokenize(entry.template());
        List<String> out = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.equals("-o")) {
                i++;                       // drop the template's own -o pair
                continue;
            }
            out.add(substitute(t, ip, port));
        }
        // Force our own output name; msfvenom is run inside the work directory.
        out.add("-o");
        out.add(entry.output());
        return out;
    }

    /** Replace only the whole-token-safe placeholders inside one argv element. */
    private static String substitute(String token, String ip, String port) {
        return token.replace("{ip}", ip).replace("{port}", port);
    }

    /** The command that {@link #generate} would run, for display only. */
    public static String preview(MsfvenomCatalog.Entry entry, String ip, String port, Path targetDir) {
        return String.join(" ", argv(entry, ip, port));
    }

    /** LHOST must look like an IPv4/IPv6 address or a hostname, nothing else. */
    public static boolean isValidHost(String host) {
        if (host == null || host.isEmpty() || host.length() > 255) {
            return false;
        }
        // Only the characters that appear in real IPs and hostnames. This alone
        // excludes every shell metacharacter, whitespace and quote.
        return host.matches("[A-Za-z0-9.:_-]+");
    }

    /** LPORT must be an integer in the valid TCP range. */
    public static boolean isValidPort(String port) {
        if (port == null || !port.matches("[0-9]{1,5}")) {
            return false;
        }
        int p = Integer.parseInt(port);
        return p >= 1 && p <= 65535;
    }

    /**
     * Generate the payload for {@code entry} into a fresh sub-directory of
     * {@code baseDir}. Blocks until msfvenom finishes; call it off the EDT.
     */
    public static Result generate(MsfvenomCatalog.Entry entry, String ip, String port, Path baseDir) {
        // Validate here as well as in the UI: this method must be safe to call
        // with any input, so it never trusts that the caller already checked.
        if (!isValidHost(ip)) {
            return new Result(false, null, "",
                    "Invalid LHOST. Use an IP address or hostname "
                            + "(letters, digits, dot, colon, hyphen, underscore only).");
        }
        if (!isValidPort(port)) {
            return new Result(false, null, "", "Invalid LPORT. Use a number between 1 and 65535.");
        }

        String binary = resolveBinary();
        if (binary == null) {
            return new Result(false, null, "",
                    "msfvenom was not found. Install the Metasploit Framework, or make "
                            + "sure msfvenom is on your PATH.");
        }

        Path workDir;
        try {
            String stamp = String.valueOf(System.currentTimeMillis());
            workDir = baseDir.resolve(stamp + "-" + safeSlug(entry.name()));
            Files.createDirectories(workDir);
        } catch (IOException e) {
            return new Result(false, null, "", "Could not create an output directory: " + e.getMessage());
        }

        // The displayed command and the executed command come from one builder.
        List<String> argv = argv(entry, ip, port);
        String shown = String.join(" ", argv);
        // Only the head token differs at runtime: the resolved absolute binary.
        List<String> runArgv = new ArrayList<>(argv);
        runArgv.set(0, binary);

        ProcessBuilder pb = new ProcessBuilder(runArgv);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);

        StringBuilder out = new StringBuilder();
        try {
            Process proc = pb.start();
            byte[] raw = proc.getInputStream().readAllBytes();
            out.append(new String(raw, StandardCharsets.UTF_8));
            boolean finished = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return new Result(false, null, shown,
                        out + "\n[timed out after " + TIMEOUT_SECONDS + "s]");
            }
            Path produced = workDir.resolve(entry.output());
            if (proc.exitValue() != 0 || !Files.isRegularFile(produced)) {
                return new Result(false, null, shown,
                        out.length() == 0 ? "msfvenom exited with code " + proc.exitValue() : out.toString());
            }
            return new Result(true, produced, shown, out.toString().trim());
        } catch (IOException e) {
            return new Result(false, null, shown, "Failed to launch msfvenom: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, null, shown, "Generation was interrupted.");
        }
    }

    // --------------------------------------------------------------- internals

    /** Whitespace tokeniser that keeps single-quoted spans intact. */
    public static List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuote = false;
        boolean started = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '\'') {
                inQuote = !inQuote;
                started = true;      // an empty quoted span is still a token
            } else if (Character.isWhitespace(c) && !inQuote) {
                if (started) {
                    tokens.add(sb.toString());
                    sb.setLength(0);
                    started = false;
                }
            } else {
                sb.append(c);
                started = true;
            }
        }
        if (started) {
            tokens.add(sb.toString());
        }
        return tokens;
    }

    private static String safeSlug(String name) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "payload" : slug;
    }
}
