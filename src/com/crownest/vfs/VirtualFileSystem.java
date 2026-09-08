package com.crownest.vfs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of everything the server is allowed to serve.
 *
 * Nothing is reachable over HTTP unless it was added here. The web root is a
 * virtual folder; each shared item is mounted under a unique top level name.
 */
public final class VirtualFileSystem {

    public enum Kind { ROOT, DIR, FILE }

    /** Result of mapping a URL path onto the filesystem. */
    public record Resolved(Kind kind, Path path, ShareItem item) { }

    private final Object lock = new Object();
    private final Map<String, ShareItem> items = new LinkedHashMap<>();

    // ------------------------------------------------------------- mutation

    /** Share a file or folder. Returns null when the path is invalid or already shared. */
    public ShareItem add(Path candidate) {
        Path real;
        try {
            real = candidate.toAbsolutePath().toRealPath();
        } catch (IOException e) {
            return null;
        }
        if (!Files.isRegularFile(real) && !Files.isDirectory(real)) {
            return null;
        }
        synchronized (lock) {
            for (ShareItem existing : items.values()) {
                if (existing.path().equals(real)) {
                    return null;
                }
            }
            String name = uniqueName(sanitizeName(real.getFileName() == null
                    ? "root" : real.getFileName().toString()));
            ShareItem item = new ShareItem(name, real, Files.isDirectory(real));
            items.put(name, item);
            return item;
        }
    }

    public List<ShareItem> addAll(Collection<Path> paths) {
        List<ShareItem> added = new ArrayList<>();
        for (Path p : paths) {
            ShareItem item = add(p);
            if (item != null) {
                added.add(item);
            }
        }
        return added;
    }

    public boolean remove(String name) {
        synchronized (lock) {
            return items.remove(name) != null;
        }
    }

    public void clear() {
        synchronized (lock) {
            items.clear();
        }
    }

    /** Rename the URL segment of a share. The file on disk is untouched. */
    public String rename(String oldName, String newName) {
        synchronized (lock) {
            ShareItem item = items.get(oldName);
            if (item == null) {
                return null;
            }
            String candidate = sanitizeName(newName);
            if (candidate.equals(oldName)) {
                return oldName;
            }
            candidate = uniqueName(candidate);
            items.remove(oldName);
            item.setName(candidate);
            items.put(candidate, item);
            return candidate;
        }
    }

    // ---------------------------------------------------------------- query

    public List<ShareItem> items() {
        synchronized (lock) {
            return new ArrayList<>(items.values());
        }
    }

    public ShareItem get(String name) {
        synchronized (lock) {
            return items.get(name);
        }
    }

    public int size() {
        synchronized (lock) {
            return items.size();
        }
    }

    // -------------------------------------------------------------- resolve

    /**
     * Map a URL path onto a real path.
     *
     * Returns null when the path is unknown, malformed, or escapes the share
     * root it claims to be under.
     */
    public Resolved resolve(String urlPath, boolean followSymlinks) {
        String raw = urlPath;
        int q = raw.indexOf('?');
        if (q >= 0) {
            raw = raw.substring(0, q);
        }
        int h = raw.indexOf('#');
        if (h >= 0) {
            raw = raw.substring(0, h);
        }

        List<String> segments = new ArrayList<>();
        for (String piece : raw.split("/")) {
            if (piece.isEmpty()) {
                continue;
            }
            String decoded = decodeSegment(piece);
            if (decoded == null) {
                return null;
            }
            // Reject traversal, empty and null-byte segments outright.
            if (decoded.isEmpty() || decoded.equals(".") || decoded.equals("..")
                    || decoded.indexOf('\0') >= 0
                    || decoded.indexOf('/') >= 0
                    || decoded.indexOf('\\') >= 0) {
                return null;
            }
            segments.add(decoded);
        }

        if (segments.isEmpty()) {
            return new Resolved(Kind.ROOT, null, null);
        }

        ShareItem item = get(segments.get(0));
        if (item == null || !item.exists()) {
            return null;
        }

        if (segments.size() == 1) {
            return new Resolved(item.isDirectory() ? Kind.DIR : Kind.FILE, item.path(), item);
        }
        if (!item.isDirectory()) {
            return null;
        }

        Path target = item.path();
        for (String segment : segments.subList(1, segments.size())) {
            target = target.resolve(segment);
            if (!followSymlinks && Files.isSymbolicLink(target)) {
                return null;
            }
        }

        Path real;
        Path root;
        try {
            real = target.toRealPath();
            root = followSymlinks ? item.path() : item.path().toRealPath();
        } catch (IOException e) {
            return null;
        }

        // Containment check: the resolved path must live inside the share root.
        if (!real.startsWith(root)) {
            return null;
        }

        if (Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS) || Files.isDirectory(real)) {
            return new Resolved(Kind.DIR, real, item);
        }
        if (Files.isRegularFile(real)) {
            return new Resolved(Kind.FILE, real, item);
        }
        return null;
    }

    /**
     * Resolve the parent directory for an upload target, without requiring the
     * file itself to exist. Returns null when the path escapes the share.
     */
    public Path resolveUploadTarget(ShareItem item, List<String> segments, boolean followSymlinks) {
        if (item == null || !item.isDirectory()) {
            return null;
        }
        Path target = item.path();
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                    || segment.indexOf('\0') >= 0) {
                return null;
            }
            target = target.resolve(segment);
            if (!followSymlinks && Files.isSymbolicLink(target)) {
                return null;
            }
        }
        try {
            Path parentReal = target.getParent().toRealPath();
            Path root = item.path().toRealPath();
            if (!parentReal.startsWith(root)) {
                return null;
            }
            return parentReal.resolve(target.getFileName());
        } catch (IOException e) {
            return null;
        }
    }

    // --------------------------------------------------------------- helpers

    /** Make a filesystem name safe to use as a single URL path segment. */
    public static String sanitizeName(String name) {
        String cleaned = name.replace("\0", "").replace('/', '_').replace('\\', '_').trim();
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        while (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.isEmpty() ? "unnamed" : cleaned;
    }

    private String uniqueName(String base) {
        if (!items.containsKey(base)) {
            return base;
        }
        int dot = base.indexOf('.');
        String stem = dot < 0 ? base : base.substring(0, dot);
        String ext = dot < 0 ? "" : base.substring(dot);
        for (int n = 2; ; n++) {
            String candidate = stem + "(" + n + ")" + ext;
            if (!items.containsKey(candidate)) {
                return candidate;
            }
        }
    }

    /**
     * Strict percent-decoding for one path segment. Unlike URLDecoder this does
     * not translate '+' into a space, which would corrupt real file names.
     * Returns null on malformed input.
     */
    public static String decodeSegment(String segment) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(segment.length());
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '%') {
                if (i + 2 >= segment.length()) {
                    return null;
                }
                int hi = Character.digit(segment.charAt(i + 1), 16);
                int lo = Character.digit(segment.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) {
                    return null;
                }
                out.write((hi << 4) + lo);
                i += 2;
            } else {
                byte[] encoded = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                out.write(encoded, 0, encoded.length);
            }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    public static Path pathOf(String s) {
        return Paths.get(s);
    }
}
