package com.crownest.server;

/** One HTTP transaction, as shown in the activity log panel. */
public record LogEvent(long timestamp,
                       String client,
                       String method,
                       String path,
                       int status,
                       long bytes,
                       String note) {

    public static LogEvent of(String client, String method, String path,
                              int status, long bytes, String note) {
        return new LogEvent(System.currentTimeMillis(), client, method, path,
                status, bytes, note == null ? "" : note);
    }
}
