package com.gearup.http;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The self-contained web server (Factor VII), built on the JDK's own {@link HttpServer}.
 *
 * <p>Requests are handled by a fixed pool of worker threads (Factor VIII), so up to
 * {@value #WORKER_THREADS} requests are processed at the same time. To handle more load,
 * run more copies of the process rather than making this one bigger.
 */
public final class GearUpServer {

    private static final Logger log = LoggerFactory.getLogger(GearUpServer.class);

    private static final int WORKER_THREADS = 10;
    private static final int STOP_DELAY_SECONDS = 5;

    private final HttpServer httpServer;
    private final ExecutorService workers;

    public GearUpServer(int port, HttpHandler handler) throws IOException {
        this.httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        this.workers = Executors.newFixedThreadPool(WORKER_THREADS, namedThreads("http-worker-"));
        httpServer.setExecutor(workers);
        httpServer.createContext("/", handler);
    }

    public void start() {
        httpServer.start();
    }

    /**
     * Graceful shutdown (Factor IX): stop accepting new connections, give in-flight requests
     * up to {@value #STOP_DELAY_SECONDS} seconds to finish, then stop the worker threads.
     */
    public void stop() {
        log.info("Shutting down, waiting up to {} s for in-flight requests", STOP_DELAY_SECONDS);
        httpServer.stop(STOP_DELAY_SECONDS);
        workers.shutdown();
        try {
            if (!workers.awaitTermination(STOP_DELAY_SECONDS, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Shutdown complete");
    }

    /** Gives worker threads readable names (http-worker-1, http-worker-2, ...) so they are easy to spot in the logs. */
    private static ThreadFactory namedThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return task -> new Thread(task, prefix + counter.getAndIncrement());
    }
}
