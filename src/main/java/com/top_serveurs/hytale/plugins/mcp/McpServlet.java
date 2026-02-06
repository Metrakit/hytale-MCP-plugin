package com.top_serveurs.hytale.plugins.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypixel.hytale.logger.HytaleLogger;
import com.top_serveurs.hytale.plugins.mcp.auth.McpAuthManager;
import com.top_serveurs.hytale.plugins.mcp.config.McpConfig;
import com.top_serveurs.hytale.plugins.mcp.features.FeatureRegistry;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.schema.jackson.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class McpServlet extends HttpServlet {
    private static final String MCP_CONTEXT_KEY = McpServlet.class.getName() + ".mcpContext";
    private static final String MCP_ENDPOINT = "/mcp";

    private final HytaleLogger logger;
    private final McpConfig config;
    private final McpAuthManager authManager;
    private final FeatureRegistry featureRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServletSseServerTransportProvider sseProvider;
    private HttpServletStreamableServerTransportProvider streamableProvider;
    private volatile boolean initialized = false;

    public McpServlet(McpPlugin plugin) {
        this.logger = plugin.getPluginLogger();
        this.config = plugin.getConfig();
        this.authManager = plugin.getAuthManager();
        this.featureRegistry = plugin.getFeatureRegistry();
    }

    public void shutdown() {
        logger.atInfo().log("MCP servlet shutdown");
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        ensureInitialized();

        McpAuthManager.AuthLevel authLevel = authManager.authenticate(req);
        if (authLevel == McpAuthManager.AuthLevel.NONE) {
            sendUnauthorized(resp);
            return;
        }

        prepareMcpContext(req, authLevel);

        String requestPath = getRequestPath(req);
        if (requestPath.endsWith(MCP_ENDPOINT)) {
            handleStreamable(req, resp);
            return;
        }

        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private synchronized void ensureInitialized() throws ServletException {
        if (initialized) {
            return;
        }

        McpSchema.ServerCapabilities capabilities = McpSchema.ServerCapabilities.builder()
            .tools(true)
            .build();

        var tools = featureRegistry.getToolSpecifications(objectMapper, config);
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);

        streamableProvider = HttpServletStreamableServerTransportProvider.builder()
            .jsonMapper(jsonMapper)
            .mcpEndpoint(MCP_ENDPOINT)
            .contextExtractor(createContextExtractor())
            .build();

        io.modelcontextprotocol.server.McpServer.sync(streamableProvider)
            .jsonMapper(jsonMapper)
            .jsonSchemaValidator(new DefaultJsonSchemaValidator(objectMapper))
            .capabilities(capabilities)
            .tools(tools)
            .build();

        initialized = true;
        logger.atInfo().log("MCP server initialized");
    }

    private void handleStreamable(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        if (meansMethod(req, "GET") || meansMethod(req, "POST")) {
            if (isBrowserRequest(req)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.setContentType("text/html;charset=UTF-8");
                resp.getWriter().write(
                    "<html><head><title>Model Context Protocol</title></head>"
                        + "<body><h2>This endpoint is for MCP clients.</h2></body></html>"
                );
                resp.getWriter().flush();
                return;
            }
            streamableProvider.service(req, resp);
        } else {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
    }

    private boolean isBrowserRequest(HttpServletRequest req) {
        if (!meansMethod(req, "GET")) {
            return false;
        }
        String acceptHeader = req.getHeader("Accept");
        return acceptHeader != null && acceptHeader.contains("text/html");
    }

    private static McpTransportContextExtractor<HttpServletRequest> createContextExtractor() {
        return request -> (McpTransportContext) request.getAttribute(MCP_CONTEXT_KEY);
    }

    private void prepareMcpContext(HttpServletRequest request, McpAuthManager.AuthLevel authLevel) {
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put(McpContextKeys.AUTH_LEVEL, authLevel);
        contextMap.put(McpContextKeys.HTTP_REQUEST, request);
        request.setAttribute(MCP_CONTEXT_KEY, McpTransportContext.create(contextMap));
    }

    private String getRequestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private static boolean meansMethod(HttpServletRequest req, String method) {
        return req.getMethod().equalsIgnoreCase(method);
    }

    private void sendUnauthorized(HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write("{\"error\":\"Unauthorized\"}");
        resp.getWriter().flush();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    }
}
