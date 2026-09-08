package com.crownest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

/** Where Crownest keeps its per-user state. */
public final class AppPaths {

    private AppPaths() {
    }

    /** Configuration directory, created with owner-only permissions. */
    public static Path configDir() throws IOException {
        Path dir;
        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            Path base = (appData != null && !appData.isBlank())
                    ? Paths.get(appData)
                    : Paths.get(System.getProperty("user.home"), "AppData", "Roaming");
            dir = base.resolve("Crownest");
        } else {
            String xdg = System.getenv("XDG_CONFIG_HOME");
            Path base = (xdg != null && !xdg.isBlank())
                    ? Paths.get(xdg)
                    : Paths.get(System.getProperty("user.home"), ".config");
            dir = base.resolve("crownest");
        }
        Files.createDirectories(dir);
        restrict(dir, true);
        return dir;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Directory that holds generated payloads, created with owner-only permissions. */
    public static Path payloadsDir() throws IOException {
        Path dir = configDir().resolve("payloads");
        Files.createDirectories(dir);
        restrict(dir, true);
        return dir;
    }

    /** Directory that holds imported script templates. */
    public static Path templatesDir() throws IOException {
        Path dir = configDir().resolve("templates");
        Files.createDirectories(dir);
        restrict(dir, true);
        return dir;
    }

    /** Tighten permissions where the filesystem supports it. */
    public static void restrict(Path path, boolean directory) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(
                    directory ? "rwx------" : "rw-------"));
        } catch (IOException | UnsupportedOperationException e) {
            // Non-POSIX filesystem: nothing further to enforce.
        }
    }
}
