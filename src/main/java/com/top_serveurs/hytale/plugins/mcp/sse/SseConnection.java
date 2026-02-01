package com.top_serveurs.hytale.plugins.mcp.sse;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SseConnection {
    private final AsyncContext asyncContext;
    private final HttpServletResponse response;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public SseConnection(AsyncContext asyncContext, HttpServletResponse response, long connectionTimeout) throws IOException {
        this.asyncContext = asyncContext;
        this.response = response;

        this.response.setContentType("text/event-stream");
        this.response.setCharacterEncoding("UTF-8");
        this.response.setHeader("Cache-Control", "no-cache, no-transform");
        this.response.setHeader("Connection", "keep-alive");
        this.response.setHeader("X-Accel-Buffering", "no");

        asyncContext.setTimeout(connectionTimeout);
    }

    public CompletableFuture<Void> sendEvent(String eventType, String data) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IOException("Connection closed"));
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            synchronized (this) {
                if (closed.get()) {
                    future.completeExceptionally(new IOException("Connection closed"));
                    return future;
                }

                PrintWriter writer = response.getWriter();
                if (eventType != null && !eventType.isEmpty()) {
                    writer.write("event: " + eventType + "\n");
                }
                writer.write("data: " + data + "\n\n");
                writer.flush();

                future.complete(null);
            }
        } catch (Exception e) {
            close();
            future.completeExceptionally(e);
        }

        return future;
    }

    public CompletableFuture<Void> sendJsonData(Object jsonObject) {
        return sendEvent("message", jsonObject.toString());
    }

    public void sendKeepAlive() {
        sendEvent(null, ": keep-alive").orTimeout(1, TimeUnit.SECONDS)
                .exceptionally(e -> {
                    if (!closed.get()) {
                        close();
                    }
                    return null;
                });
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                asyncContext.complete();
            } catch (Exception e) {
            }
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    public AsyncContext getAsyncContext() {
        return asyncContext;
    }
}
