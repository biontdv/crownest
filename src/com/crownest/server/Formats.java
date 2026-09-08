package com.crownest.server;

import java.time.Duration;

/** Small display helpers shared by the server and the UI. */
public final class Formats {

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB", "PB"};

    private Formats() {
    }

    /** Human readable byte count. Returns "-" for negative values. */
    public static String bytes(long value) {
        if (value < 0) {
            return "-";
        }
        double n = value;
        for (String unit : UNITS) {
            if (n < 1024.0) {
                return unit.equals("B")
                        ? String.format("%.0f %s", n, unit)
                        : String.format("%.1f %s", n, unit);
            }
            n /= 1024.0;
        }
        return String.format("%.1f EB", n);
    }

    /** HH:MM:SS uptime rendering. */
    public static String uptime(long millis) {
        Duration d = Duration.ofMillis(Math.max(0, millis));
        return String.format("%02d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart());
    }

    /** Escape text for safe inclusion in generated HTML. */
    public static String html(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
