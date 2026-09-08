package com.crownest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Small persistent preference store, so the next launch remembers what the
 * last one was doing. Deliberately holds no secrets.
 */
public final class Prefs {

    private static final String LAST_PORT = "last.port";
    private static final String THEME = "ui.theme";

    private static Properties cache;

    private Prefs() {
    }

    private static Path file() throws IOException {
        return AppPaths.configDir().resolve("settings.properties");
    }

    private static synchronized Properties load() {
        if (cache != null) {
            return cache;
        }
        Properties props = new Properties();
        try {
            Path path = file();
            if (Files.exists(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    props.load(in);
                }
            }
        } catch (IOException e) {
            // Unreadable settings are not fatal; defaults apply.
        }
        cache = props;
        return props;
    }

    private static synchronized void store() {
        try {
            Path path = file();
            try (OutputStream out = Files.newOutputStream(path)) {
                load().store(out, "Crownest settings");
            }
            AppPaths.restrict(path, false);
        } catch (IOException e) {
            // Losing preferences is not worth interrupting the user over.
        }
    }

    /** The port Crownest last served on, or the fallback on first run. */
    public static int lastPort(int fallback) {
        String raw = load().getProperty(LAST_PORT);
        if (raw == null) {
            return fallback;
        }
        try {
            int port = Integer.parseInt(raw.trim());
            return (port >= 1 && port <= 65535) ? port : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Record the port a server actually bound to. */
    public static void saveLastPort(int port) {
        if (port < 1 || port > 65535) {
            return;
        }
        load().setProperty(LAST_PORT, String.valueOf(port));
        store();
    }

    public static String theme(String fallback) {
        String value = load().getProperty(THEME);
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    public static void saveTheme(String themeName) {
        if (themeName == null || themeName.isBlank()) {
            return;
        }
        load().setProperty(THEME, themeName);
        store();
    }
}
