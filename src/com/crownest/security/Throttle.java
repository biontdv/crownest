package com.crownest.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per client request rate limiting plus failed-authentication lockout. */
public final class Throttle {

    private static final class Bucket {
        final Deque<Long> hits = new ArrayDeque<>();
        int failures;
        long bannedUntil;
    }

    private final int requestsPerMinute;
    private final int maxFailures;
    private final long lockoutMillis;
    private final Map<String, Bucket> clients = new ConcurrentHashMap<>();

    public Throttle(int requestsPerMinute, int maxFailures, int lockoutSeconds) {
        this.requestsPerMinute = Math.max(0, requestsPerMinute);
        this.maxFailures = Math.max(0, maxFailures);
        this.lockoutMillis = Math.max(0, lockoutSeconds) * 1000L;
    }

    private Bucket bucket(String client) {
        return clients.computeIfAbsent(client, k -> new Bucket());
    }

    /** Milliseconds of lockout remaining, or 0 when the client is not banned. */
    public long bannedFor(String client) {
        Bucket b = clients.get(client);
        if (b == null) {
            return 0;
        }
        synchronized (b) {
            long remaining = b.bannedUntil - System.currentTimeMillis();
            return remaining > 0 ? remaining : 0;
        }
    }

    /** Consume one request slot. False when the client is over its rate limit. */
    public boolean allowRequest(String client) {
        if (requestsPerMinute <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Bucket b = bucket(client);
        synchronized (b) {
            while (!b.hits.isEmpty() && now - b.hits.peekFirst() > 60_000L) {
                b.hits.pollFirst();
            }
            if (b.hits.size() >= requestsPerMinute) {
                return false;
            }
            b.hits.addLast(now);
            return true;
        }
    }

    /** Record a failed login. Returns true when this attempt triggered a ban. */
    public boolean recordFailure(String client) {
        if (maxFailures <= 0) {
            return false;
        }
        Bucket b = bucket(client);
        synchronized (b) {
            b.failures++;
            if (b.failures >= maxFailures) {
                b.failures = 0;
                b.bannedUntil = System.currentTimeMillis() + lockoutMillis;
                return true;
            }
            return false;
        }
    }

    public void recordSuccess(String client) {
        Bucket b = clients.get(client);
        if (b != null) {
            synchronized (b) {
                b.failures = 0;
            }
        }
    }

    public void reset() {
        clients.clear();
    }

    /** Drop idle buckets so a long run cannot grow the map without bound. */
    public void prune(int maxEntries) {
        if (clients.size() <= maxEntries) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Bucket>> it = clients.entrySet().iterator();
        while (it.hasNext()) {
            Bucket b = it.next().getValue();
            synchronized (b) {
                boolean idle = b.hits.isEmpty() && b.failures == 0 && b.bannedUntil < now;
                if (idle) {
                    it.remove();
                }
            }
        }
    }
}
