package com.top_serveurs.hytale.plugins.mcp.sse;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SseManager {
    private final HytaleLogger logger;
    private final ScheduledExecutorService scheduler;
    private final Map<String, SseConnection> activeConnections = new ConcurrentHashMap<>();
    private final AtomicInteger connectionIdCounter = new AtomicInteger(0);
    private final long keepAliveInterval;
    private final ScheduledFuture<?> keepAliveTask;

    public SseManager(HytaleLogger logger, long keepAliveIntervalSeconds) {
        this.logger = logger;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "SseManager-" + r.hashCode());
            thread.setDaemon(true);
            return thread;
        });
        this.keepAliveInterval = keepAliveIntervalSeconds * 1000L;

        this.keepAliveTask = scheduler.scheduleAtFixedRate(
                this::sendKeepAliveToAll,
                this.keepAliveInterval,
                this.keepAliveInterval,
                TimeUnit.MILLISECONDS
        );

        logger.atInfo().log("SSE Manager initialized with keep-alive interval: " + keepAliveIntervalSeconds + "s");
    }

    public String registerConnection(SseConnection connection) {
        String connectionId = "conn-" + connectionIdCounter.incrementAndGet();
        activeConnections.put(connectionId, connection);

        logger.atInfo().log("SSE connection registered: " + connectionId + " (Total: " + activeConnections.size() + ")");
        return connectionId;
    }

    public void removeConnection(String connectionId) {
        SseConnection removed = activeConnections.remove(connectionId);
        if (removed != null) {
            removed.close();
            logger.atInfo().log("SSE connection removed: " + connectionId + " (Total: " + activeConnections.size() + ")");
        }
    }

    public CompletableFuture<Void> sendToConnection(String connectionId, String eventType, String data) {
        SseConnection connection = activeConnections.get(connectionId);
        if (connection == null || connection.isClosed()) {
            activeConnections.remove(connectionId);
            return CompletableFuture.failedFuture(new IOException("Connection not found or closed"));
        }

        return connection.sendEvent(eventType, data)
                .exceptionally(e -> {
                    logger.atWarning().log("Failed to send event to " + connectionId + ": " + e.getMessage());
                    removeConnection(connectionId);
                    return null;
                });
    }

    public CompletableFuture<Void> broadcast(String eventType, String data) {
        if (activeConnections.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<?>[] futures = activeConnections.entrySet().stream()
                .map(entry -> entry.getValue().sendEvent(eventType, data)
                        .exceptionally(e -> {
                            logger.atWarning().log("Failed to broadcast to " + entry.getKey() + ": " + e.getMessage());
                            removeConnection(entry.getKey());
                            return null;
                        }))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures);
    }

    private void sendKeepAliveToAll() {
        if (activeConnections.isEmpty()) {
            return;
        }

        activeConnections.forEach((id, connection) -> {
            if (!connection.isClosed()) {
                connection.sendKeepAlive();
            } else {
                removeConnection(id);
            }
        });
    }

    public int getActiveConnectionCount() {
        return activeConnections.size();
    }

    public void shutdown() {
        logger.atInfo().log("Shutting down SSE Manager...");
        keepAliveTask.cancel(false);

        activeConnections.forEach((id, connection) -> {
            try {
                connection.close();
            } catch (Exception e) {
            }
        });
        activeConnections.clear();

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.atInfo().log("SSE Manager shut down");
    }
}
