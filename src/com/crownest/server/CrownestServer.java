package com.crownest.server;

import com.crownest.security.Throttle;
import com.crownest.security.TlsManager;
import com.crownest.vfs.VirtualFileSystem;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/** Owns the HTTP(S) server lifecycle. */
public final class CrownestServer {

    private static final int BACKLOG = 64;
    private static final int WORKER_THREADS = 16;

    private final ServerConfig cfg;
    private final VirtualFileSystem vfs;
    private final Consumer<LogEvent> logger;

    private HttpServer server;
    private ExecutorService pool;
    private Throttle throttle;
    private volatile long startedAt;

    public CrownestServer(ServerConfig cfg, VirtualFileSystem vfs, Consumer<LogEvent> logger) {
        this.cfg = cfg;
        this.vfs = vfs;
        this.logger = logger;
    }

    public ServerConfig config() {
        return cfg;
    }

    public boolean isRunning() {
        return server != null;
    }

    public long startedAt() {
        return startedAt;
    }

    public String certificateFingerprint() {
        return TlsManager.fingerprint();
    }

    /** Bind and begin serving. Throws when the port is taken or TLS setup fails. */
    public void start() throws Exception {
        if (server != null) {
            throw new IllegalStateException("Server is already running");
        }

        InetSocketAddress address = new InetSocketAddress(cfg.bindAddress, cfg.port);
        throttle = new Throttle(cfg.ratePerMinute, cfg.maxFailures, cfg.lockoutSeconds);

        HttpServer created;
        if (cfg.tls) {
            SSLContext sslContext = TlsManager.sslContext(List.of(cfg.displayHost()));
            HttpsServer https = HttpsServer.create(address, BACKLOG);
            https.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                @Override
                public void configure(HttpsParameters params) {
                    SSLParameters defaults = getSSLContext().getDefaultSSLParameters();
                    defaults.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
                    params.setSSLParameters(defaults);
                }
            });
            created = https;
        } else {
            created = HttpServer.create(address, BACKLOG);
        }

        created.createContext("/", new FileRequestHandler(cfg, vfs, throttle, logger));

        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread t = new Thread(runnable, "crownest-http-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        pool = Executors.newFixedThreadPool(WORKER_THREADS, factory);
        created.setExecutor(pool);
        created.start();

        server = created;
        startedAt = System.currentTimeMillis();
    }

    /** Stop serving and release the port. Safe to call when already stopped. */
    public void stop() {
        HttpServer current = server;
        server = null;
        startedAt = 0;
        if (current != null) {
            current.stop(1);
        }
        if (pool != null) {
            pool.shutdownNow();
            try {
                pool.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            pool = null;
        }
        if (throttle != null) {
            throttle.reset();
            throttle = null;
        }
    }
}
