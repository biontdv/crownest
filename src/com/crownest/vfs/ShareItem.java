package com.crownest.vfs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/** A single file or folder explicitly exposed over HTTP. */
public final class ShareItem {

    private volatile String name;
    private final Path path;
    private final boolean directory;
    private final long addedAt;
    private volatile boolean generated;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong bytesSent = new AtomicLong();

    public ShareItem(String name, Path path, boolean directory) {
        this.name = name;
        this.path = path;
        this.directory = directory;
        this.addedAt = System.currentTimeMillis();
    }

    public String name() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    public Path path() {
        return path;
    }

    public boolean isDirectory() {
        return directory;
    }

    /** True for payloads created by the Revshell tab, hidden from the Files tab. */
    public boolean isGenerated() {
        return generated;
    }

    public void setGenerated(boolean generated) {
        this.generated = generated;
    }

    public long addedAt() {
        return addedAt;
    }

    public long hits() {
        return hits.get();
    }

    public long bytesSent() {
        return bytesSent.get();
    }

    public void recordHit(long sent) {
        hits.incrementAndGet();
        bytesSent.addAndGet(sent);
    }

    /** Size in bytes, or -1 for a directory or an unreadable file. */
    public long size() {
        if (directory) {
            return -1;
        }
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1;
        }
    }

    public boolean exists() {
        return Files.exists(path);
    }
}
