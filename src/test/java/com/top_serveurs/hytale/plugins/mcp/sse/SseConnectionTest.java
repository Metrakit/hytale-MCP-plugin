package com.top_serveurs.hytale.plugins.mcp.sse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SseConnectionTest {

    @Mock
    private AsyncContext asyncContext;

    @Mock
    private HttpServletResponse response;

    private StringWriter stringWriter;
    private PrintWriter printWriter;
    private SseConnection connection;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        stringWriter = new StringWriter();
        printWriter = new PrintWriter(stringWriter);

        when(response.getWriter()).thenReturn(printWriter);
        when(response.getWriter()).thenReturn(printWriter);

        connection = new SseConnection(asyncContext, response, 300000);
    }

    @AfterEach
    void tearDown() {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void testConnectionInitialization() throws Exception {
        verify(response).setContentType("text/event-stream");
        verify(response).setCharacterEncoding("UTF-8");
        verify(response).setHeader("Cache-Control", "no-cache, no-transform");
        verify(response).setHeader("Connection", "keep-alive");
        verify(response).setHeader("X-Accel-Buffering", "no");
        verify(asyncContext).setTimeout(300000);
    }

    @Test
    void testSendEventWithEventType() throws Exception {
        CompletableFuture<Void> future = connection.sendEvent("message", "test data");
        future.get(1, TimeUnit.SECONDS);

        String output = stringWriter.toString();
        assertTrue(output.contains("event: message"));
        assertTrue(output.contains("data: test data"));
        assertTrue(output.endsWith("\n\n"));
    }

    @Test
    void testSendEventWithoutEventType() throws Exception {
        CompletableFuture<Void> future = connection.sendEvent(null, "test data");
        future.get(1, TimeUnit.SECONDS);

        String output = stringWriter.toString();
        assertFalse(output.contains("event:"));
        assertTrue(output.contains("data: test data"));
        assertTrue(output.endsWith("\n\n"));
    }

    @Test
    void testSendJsonData() throws Exception {
        CompletableFuture<Void> future = connection.sendJsonData("{\"key\":\"value\"}");
        future.get(1, TimeUnit.SECONDS);

        String output = stringWriter.toString();
        assertTrue(output.contains("event: message"));
        assertTrue(output.contains("data: {\"key\":\"value\"}"));
    }

    @Test
    void testMultipleEvents() throws Exception {
        CompletableFuture<Void> future1 = connection.sendEvent("event1", "data1");
        CompletableFuture<Void> future2 = connection.sendEvent("event2", "data2");

        CompletableFuture.allOf(future1, future2).get(2, TimeUnit.SECONDS);

        String output = stringWriter.toString();
        assertTrue(output.contains("event: event1"));
        assertTrue(output.contains("data: data1"));
        assertTrue(output.contains("event: event2"));
        assertTrue(output.contains("data: data2"));
    }

    @Test
    void testIsClosed() {
        assertFalse(connection.isClosed());
        connection.close();
        assertTrue(connection.isClosed());
    }

    @Test
    void testClose() {
        assertFalse(connection.isClosed());
        connection.close();
        assertTrue(connection.isClosed());
        verify(asyncContext).complete();
    }

    @Test
    void testGetAsyncContext() {
        assertEquals(asyncContext, connection.getAsyncContext());
    }

    @Test
    void testSendKeepAlive() throws Exception {
        connection.sendKeepAlive();
        Thread.sleep(100);

        String output = stringWriter.toString();
        assertTrue(output.contains(": keep-alive"));
    }
}
