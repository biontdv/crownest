package com.crownest.server;

import com.crownest.security.IpAllowList;
import com.crownest.security.Secrets;
import com.crownest.security.Throttle;
import com.crownest.vfs.ShareItem;
import com.crownest.vfs.VirtualFileSystem;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Serves the virtual filesystem over HTTP.
 *
 * Every request passes an allow list check, a rate limit, and optional Basic
 * authentication before any path is resolved. Path resolution itself is done by
 * the VirtualFileSystem, which containment-checks the result.
 */
public final class FileRequestHandler implements HttpHandler {

    static final String SERVER_TOKEN = "Crownest/1.0";
    private static final int BUFFER = 64 * 1024;

    private final ServerConfig cfg;
    private final VirtualFileSystem vfs;
    private final Throttle throttle;
    private final IpAllowList allowList;
    private final Consumer<LogEvent> logger;

    public FileRequestHandler(ServerConfig cfg, VirtualFileSystem vfs, Throttle throttle,
                              Consumer<LogEvent> logger) {
        this.cfg = cfg;
        this.vfs = vfs;
        this.throttle = throttle;
        this.allowList = new IpAllowList(cfg.ipAllowList);
        this.logger = logger;
    }

    // ------------------------------------------------------------- entry point

    @Override
    public void handle(HttpExchange ex) {
        try {
            String path = gate(ex);
            if (path == null) {
                return;   // already answered by the gate
            }
            String method = ex.getRequestMethod();
            switch (method) {
                case "GET" -> serve(ex, path, false);
                case "HEAD" -> serve(ex, path, true);
                case "PUT" -> handlePut(ex, path);
                case "OPTIONS" -> handleOptions(ex);
                default -> fail(ex, 405, "Method not allowed", "method-blocked",
                        "Allow", allowHeader());
            }
        } catch (IOException e) {
            log(ex, 500, 0, "io-error");
        } catch (RuntimeException e) {
            log(ex, 500, 0, "handler-error");
        } finally {
            throttle.prune(4096);
            ex.close();
        }
    }

    private String allowHeader() {
        return cfg.allowUpload ? "GET, HEAD, OPTIONS, PUT" : "GET, HEAD, OPTIONS";
    }

    // ---------------------------------------------------------------- gateway

    /**
     * Run access control. Returns the URL path with any secret prefix stripped,
     * or null when the request has already been answered with an error.
     */
    private String gate(HttpExchange ex) throws IOException {
        String client = clientAddress(ex);

        if (!allowList.allows(client)) {
            fail(ex, 403, "Access denied", "ip-blocked");
            return null;
        }

        long banned = throttle.bannedFor(client);
        if (banned > 0) {
            fail(ex, 429, "Temporarily locked out", "locked-out",
                    "Retry-After", String.valueOf(banned / 1000 + 1));
            return null;
        }

        if (!throttle.allowRequest(client)) {
            fail(ex, 429, "Too many requests", "rate-limited", "Retry-After", "5");
            return null;
        }

        String path = ex.getRequestURI().getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }

        // The secret prefix acts as a coarse pre-authentication token.
        if (cfg.urlPrefix != null && !cfg.urlPrefix.isEmpty()) {
            String needle = "/" + cfg.urlPrefix;
            if (path.equals(needle)) {
                path = "/";
            } else if (path.startsWith(needle + "/")) {
                path = path.substring(needle.length());
            } else {
                fail(ex, 404, "Not found", "bad-prefix");
                return null;
            }
        }

        if (!checkAuth(ex, client)) {
            return null;
        }
        return path.isEmpty() ? "/" : path;
    }

    private boolean checkAuth(HttpExchange ex, String client) throws IOException {
        if (!cfg.authEnabled) {
            return true;
        }
        String header = ex.getRequestHeaders().getFirst("Authorization");
        boolean ok = false;

        if (header != null && header.startsWith("Basic ")) {
            try {
                byte[] raw = Base64.getDecoder().decode(header.substring(6).trim());
                String decoded = new String(raw, StandardCharsets.UTF_8);
                int colon = decoded.indexOf(':');
                if (colon >= 0) {
                    String user = decoded.substring(0, colon);
                    char[] password = decoded.substring(colon + 1).toCharArray();
                    ok = Secrets.constantTimeEquals(user, cfg.username)
                            && Secrets.verify(password, cfg.passwordHash);
                    java.util.Arrays.fill(password, ' ');
                }
            } catch (IllegalArgumentException e) {
                ok = false;
            }
        }

        if (ok) {
            throttle.recordSuccess(client);
            return true;
        }

        if (header != null && !header.isEmpty()) {
            boolean nowBanned = throttle.recordFailure(client);
            if (nowBanned) {
                fail(ex, 429, "Too many failed attempts", "auth-lockout",
                        "Retry-After", String.valueOf(cfg.lockoutSeconds));
                return false;
            }
        }

        baseHeaders(ex);
        ex.getResponseHeaders().set("WWW-Authenticate",
                "Basic realm=\"Crownest\", charset=\"UTF-8\"");
        ex.sendResponseHeaders(401, -1);
        log(ex, 401, 0, "auth-required");
        return false;
    }

    // ------------------------------------------------------------ GET / HEAD

    private void serve(HttpExchange ex, String path, boolean headOnly) throws IOException {
        VirtualFileSystem.Resolved resolved = vfs.resolve(path, cfg.followSymlinks);
        if (resolved == null) {
            fail(ex, 404, "Not found", "no-resource");
            return;
        }
        switch (resolved.kind()) {
            case ROOT -> sendRootIndex(ex, headOnly);
            case DIR -> sendDirIndex(ex, resolved, path, headOnly);
            case FILE -> sendFile(ex, resolved, headOnly);
        }
    }

    private void sendRootIndex(HttpExchange ex, boolean headOnly) throws IOException {
        if (!cfg.directoryListing) {
            fail(ex, 403, "Directory listing is disabled", "listing-off");
            return;
        }
        StringBuilder rows = new StringBuilder();
        List<ShareItem> items = vfs.items();
        if (items.isEmpty()) {
            rows.append("<li class=\"empty\">No files are being shared.</li>");
        }
        for (ShareItem item : items) {
            String href = ServerConfig.encodeSegment(item.name()) + (item.isDirectory() ? "/" : "");
            rows.append(row(href, item.name() + (item.isDirectory() ? "/" : ""),
                    item.isDirectory() ? "-" : Formats.bytes(item.size()), item.isDirectory()));
        }
        sendHtml(ex, 200, page("Shared items", rows.toString()), headOnly, "index");
    }

    private void sendDirIndex(HttpExchange ex, VirtualFileSystem.Resolved resolved,
                              String urlPath, boolean headOnly) throws IOException {
        if (!cfg.directoryListing) {
            fail(ex, 403, "Directory listing is disabled", "listing-off");
            return;
        }
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(resolved.path())) {
            for (Path p : stream) {
                entries.add(p);
            }
        } catch (IOException e) {
            fail(ex, 403, "Cannot read directory", "scandir-failed");
            return;
        }
        entries.sort(Comparator
                .comparing((Path p) -> Files.isDirectory(p) ? 0 : 1)
                .thenComparing(p -> p.getFileName().toString().toLowerCase()));

        StringBuilder rows = new StringBuilder();
        String trimmed = urlPath.endsWith("/") ? urlPath.substring(0, urlPath.length() - 1) : urlPath;
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash > 0) {
            rows.append(row(trimmed.substring(0, lastSlash) + "/", "../", "", true));
        }
        for (Path p : entries) {
            String name = p.getFileName().toString();
            if (name.startsWith(".")) {
                continue;                        // never advertise dotfiles
            }
            boolean dir = Files.isDirectory(p);
            long size = -1;
            if (!dir) {
                try {
                    size = Files.size(p);
                } catch (IOException e) {
                    continue;
                }
            }
            String href = ServerConfig.encodeSegment(name) + (dir ? "/" : "");
            rows.append(row(href, name + (dir ? "/" : ""), dir ? "-" : Formats.bytes(size), dir));
        }
        String title = resolved.item() == null ? "/" : "/" + resolved.item().name();
        sendHtml(ex, 200, page(title, rows.toString()), headOnly, "index");
    }

    private void sendFile(HttpExchange ex, VirtualFileSystem.Resolved resolved, boolean headOnly)
            throws IOException {
        Path file = resolved.path();
        long total;
        long modified;
        try {
            total = Files.size(file);
            modified = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            fail(ex, 404, "Not found", "stat-failed");
            return;
        }

        String etag = "\"" + Long.toHexString(modified) + "-" + Long.toHexString(total) + "\"";
        String ifNoneMatch = ex.getRequestHeaders().getFirst("If-None-Match");
        if (ifNoneMatch != null && ifNoneMatch.contains(etag)) {
            baseHeaders(ex);
            ex.getResponseHeaders().set("ETag", etag);
            ex.sendResponseHeaders(304, -1);
            log(ex, 304, 0, "not-modified");
            return;
        }

        long start = 0;
        long end = total - 1;
        int status = 200;
        String rangeHeader = ex.getRequestHeaders().getFirst("Range");
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            long[] range = parseRange(rangeHeader, total);
            if (range == null) {
                baseHeaders(ex);
                ex.getResponseHeaders().set("Content-Range", "bytes */" + total);
                byte[] body = "416 Range Not Satisfiable".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(416, body.length);
                ex.getResponseBody().write(body);
                log(ex, 416, 0, "bad-range");
                return;
            }
            start = range[0];
            end = range[1];
            status = 206;
        }

        long length = end - start + 1;
        String name = resolved.item() != null && resolved.item().path().equals(file)
                ? resolved.item().name()
                : file.getFileName().toString();

        baseHeaders(ex);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", contentType(file));
        h.set("Accept-Ranges", "bytes");
        h.set("ETag", etag);
        h.set("Last-Modified", httpDate(modified));
        // Force a download rather than letting the browser render attacker-supplied HTML.
        h.set("Content-Disposition",
                "attachment; filename*=UTF-8''" + ServerConfig.encodeSegment(name));
        if (status == 206) {
            h.set("Content-Range", "bytes " + start + "-" + end + "/" + total);
        }

        if (headOnly) {
            h.set("Content-Length", String.valueOf(length));
            ex.sendResponseHeaders(status, -1);
            log(ex, status, 0, "head");
            return;
        }

        ex.sendResponseHeaders(status, length);
        long sent = 0;
        try (InputStream in = Files.newInputStream(file); OutputStream out = ex.getResponseBody()) {
            in.skipNBytes(start);
            byte[] buffer = new byte[BUFFER];
            long remaining = length;
            while (remaining > 0) {
                int want = (int) Math.min(buffer.length, remaining);
                int read = in.read(buffer, 0, want);
                if (read < 0) {
                    break;
                }
                out.write(buffer, 0, read);
                remaining -= read;
                sent += read;
            }
        } catch (IOException e) {
            if (resolved.item() != null) {
                resolved.item().recordHit(sent);
            }
            log(ex, status, sent, "client-aborted");
            return;
        }
        if (resolved.item() != null) {
            resolved.item().recordHit(sent);
        }
        log(ex, status, sent, "");
    }

    // ----------------------------------------------------------------- upload

    private void handlePut(HttpExchange ex, String path) throws IOException {
        if (!cfg.allowUpload) {
            fail(ex, 405, "Uploads are disabled", "upload-off", "Allow", allowHeader());
            return;
        }

        List<String> segments = new ArrayList<>();
        for (String piece : path.split("/")) {
            if (piece.isEmpty()) {
                continue;
            }
            String decoded = VirtualFileSystem.decodeSegment(piece);
            if (decoded == null) {
                fail(ex, 400, "Malformed path", "put-badpath");
                return;
            }
            segments.add(decoded);
        }
        if (segments.size() < 2) {
            fail(ex, 400, "Upload into a shared folder: PUT /<folder>/<filename>", "put-noshare");
            return;
        }

        ShareItem item = vfs.get(segments.get(0));
        if (item == null || !item.isDirectory() || !item.exists()) {
            fail(ex, 404, "No such upload folder", "put-nodir");
            return;
        }

        Path target = vfs.resolveUploadTarget(item,
                segments.subList(1, segments.size()), cfg.followSymlinks);
        if (target == null) {
            fail(ex, 403, "Path escapes the shared folder", "put-escape");
            return;
        }

        long declared = -1;
        String lengthHeader = ex.getRequestHeaders().getFirst("Content-Length");
        if (lengthHeader != null) {
            try {
                declared = Long.parseLong(lengthHeader.trim());
            } catch (NumberFormatException e) {
                fail(ex, 400, "Bad Content-Length", "put-badlen");
                return;
            }
        }
        long maxBytes = (long) cfg.maxUploadMb * 1024L * 1024L;
        if (declared > maxBytes) {
            fail(ex, 413, "Maximum upload size is " + cfg.maxUploadMb + " MB", "put-toolarge");
            return;
        }

        boolean existed = Files.exists(target);
        Path temp = target.resolveSibling("." + target.getFileName() + "."
                + Secrets.randomToken(6) + ".part");
        long written = 0;
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = ex.getRequestBody();
                 OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[BUFFER];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    written += read;
                    if (written > maxBytes) {
                        throw new IOException("upload exceeded the configured limit");
                    }
                    out.write(buffer, 0, read);
                }
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            fail(ex, 413, "Upload rejected", "put-failed");
            return;
        }

        int status = existed ? 204 : 201;
        baseHeaders(ex);
        ex.sendResponseHeaders(status, -1);
        log(ex, status, written, "upload " + Formats.bytes(written));
    }

    private void handleOptions(HttpExchange ex) throws IOException {
        baseHeaders(ex);
        ex.getResponseHeaders().set("Allow", allowHeader());
        ex.sendResponseHeaders(204, -1);
        log(ex, 204, 0, "options");
    }

    // ---------------------------------------------------------------- output

    private void baseHeaders(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("Server", SERVER_TOKEN);
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "no-referrer");
        h.set("Content-Security-Policy",
                "default-src 'none'; style-src 'unsafe-inline'; img-src 'self' data:; "
                        + "base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
        h.set("Cache-Control", "no-store");
        if (cfg.tls) {
            h.set("Strict-Transport-Security", "max-age=31536000");
        }
    }

    private void sendHtml(HttpExchange ex, int status, String html, boolean headOnly, String note)
            throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        baseHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        if (headOnly) {
            ex.getResponseHeaders().set("Content-Length", String.valueOf(body.length));
            ex.sendResponseHeaders(status, -1);
        } else {
            ex.sendResponseHeaders(status, body.length);
            ex.getResponseBody().write(body);
        }
        log(ex, status, headOnly ? 0 : body.length, note);
    }

    private void fail(HttpExchange ex, int status, String message, String note,
                      String... extraHeaders) throws IOException {
        baseHeaders(ex);
        for (int i = 0; i + 1 < extraHeaders.length; i += 2) {
            ex.getResponseHeaders().set(extraHeaders[i], extraHeaders[i + 1]);
        }
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        String html = "<!doctype html><meta charset=utf-8><title>" + status + "</title>"
                + "<h1>" + status + "</h1><p>" + Formats.html(message) + "</p>";
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        if ("HEAD".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(status, -1);
        } else {
            ex.sendResponseHeaders(status, body.length);
            ex.getResponseBody().write(body);
        }
        log(ex, status, 0, note);
    }

    private void log(HttpExchange ex, int status, long bytes, String note) {
        if (logger == null) {
            return;
        }
        String path = ex.getRequestURI() == null ? "?" : ex.getRequestURI().toString();
        logger.accept(LogEvent.of(clientAddress(ex), ex.getRequestMethod(), path,
                status, bytes, note));
    }

    private static String clientAddress(HttpExchange ex) {
        if (ex.getRemoteAddress() == null || ex.getRemoteAddress().getAddress() == null) {
            return "unknown";
        }
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }

    // --------------------------------------------------------------- helpers

    /** Parse a single "bytes=start-end" range. Returns null when unsatisfiable. */
    static long[] parseRange(String header, long total) {
        if (!header.startsWith("bytes=") || header.contains(",")) {
            return null;
        }
        String spec = header.substring(6).trim();
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String startText = spec.substring(0, dash).trim();
        String endText = spec.substring(dash + 1).trim();
        try {
            long start;
            long end;
            if (startText.isEmpty()) {
                if (endText.isEmpty()) {
                    return null;
                }
                long suffix = Long.parseLong(endText);
                if (suffix <= 0) {
                    return null;
                }
                start = Math.max(0, total - suffix);
                end = total - 1;
            } else {
                start = Long.parseLong(startText);
                end = endText.isEmpty() ? total - 1 : Long.parseLong(endText);
            }
            if (start < 0 || end < start || start >= total) {
                return null;
            }
            return new long[]{start, Math.min(end, total - 1)};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1);
        return switch (ext) {
            case "txt", "log", "md", "conf", "cfg", "ini" -> "text/plain; charset=utf-8";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            case "zip" -> "application/zip";
            case "gz", "tgz" -> "application/gzip";
            case "tar" -> "application/x-tar";
            case "exe", "dll" -> "application/vnd.microsoft.portable-executable";
            case "sh", "py", "ps1", "php", "js" -> "text/plain; charset=utf-8";
            default -> "application/octet-stream";
        };
    }

    private static String httpDate(long millis) {
        return java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
                java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis),
                        java.time.ZoneOffset.UTC));
    }

    private static String row(String href, String label, String size, boolean dir) {
        return "<li class=\"" + (dir ? "dir" : "file") + "\">"
                + "<a href=\"" + Formats.html(href) + "\">" + Formats.html(label) + "</a>"
                + "<span class=\"sz\">" + Formats.html(size) + "</span></li>";
    }

    private static String page(String title, String rows) {
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%TITLE% - Crownest</title>
                <style>
                :root{color-scheme:dark}
                *{box-sizing:border-box}
                body{margin:0;background:#16171a;color:#dfe1e5;
                 font:15px/1.5 -apple-system,Segoe UI,Roboto,Ubuntu,sans-serif}
                header{padding:18px 28px;border-bottom:1px solid #2f3136;background:#1e1f22;
                 display:flex;align-items:center;gap:12px}
                header .logo{width:20px;height:20px;border-radius:5px;
                 background:linear-gradient(135deg,#f0b429,#8a6410)}
                header h1{font-size:15px;margin:0;font-weight:600;letter-spacing:.3px}
                main{max-width:940px;margin:0 auto;padding:24px 28px}
                h2{font-size:12px;text-transform:uppercase;letter-spacing:1px;color:#9da0a8;
                 font-weight:600;margin:0 0 14px}
                ul{list-style:none;margin:0;padding:0;border:1px solid #2f3136;border-radius:8px;
                 overflow:hidden;background:#1e1f22}
                li{display:flex;align-items:center;justify-content:space-between;
                 padding:10px 16px;border-bottom:1px solid #26282c}
                li:last-child{border-bottom:none}
                li:hover{background:#26282c}
                li a{color:#dfe1e5;text-decoration:none;font-weight:500;
                 overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
                li.dir a{color:#f0b429}
                li a::before{margin-right:10px;font-size:11px;opacity:.75}
                li.dir a::before{content:"[DIR]";color:#f0b429}
                li.file a::before{content:"[FILE]";color:#6ea8fe}
                li.empty{color:#9da0a8;justify-content:center;padding:26px}
                .sz{color:#787c85;font-size:12px;font-variant-numeric:tabular-nums;
                 flex-shrink:0;margin-left:16px}
                footer{max-width:940px;margin:0 auto;padding:14px 28px;color:#5a5e66;font-size:12px}
                </style></head>
                <body>
                <header><span class="logo"></span><h1>Crownest HTTP File Server</h1></header>
                <main><h2>%TITLE%</h2><ul>
                %ROWS%
                </ul></main>
                <footer>%SERVER%</footer>
                </body></html>
                """
                .replace("%TITLE%", Formats.html(title))
                .replace("%ROWS%", rows)
                .replace("%SERVER%", SERVER_TOKEN);
    }
}
