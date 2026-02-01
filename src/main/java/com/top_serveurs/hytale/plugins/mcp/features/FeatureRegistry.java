package com.top_serveurs.hytale.plugins.mcp.features;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.top_serveurs.hytale.plugins.mcp.auth.McpAuthManager;
import com.top_serveurs.hytale.plugins.mcp.config.McpConfig;
import com.top_serveurs.hytale.plugins.mcp.McpContextKeys;
import com.top_serveurs.hytale.plugins.mcp.models.McpTool;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolCall;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolResponse;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeatureRegistry {
    private final HytaleLogger logger;
    private final Map<String, McpFeature> features;

    public FeatureRegistry(HytaleLogger logger) {
        this.logger = logger;
        this.features = new HashMap<>();
    }

    public void registerFeature(McpFeature feature) {
        features.put(feature.getName(), feature);
        logger.atInfo().log("Registered MCP feature: " + feature.getName());
    }

    public McpFeature getFeature(String name) {
        return features.get(name);
    }

    public JsonArray getAvailableTools(McpAuthManager.AuthLevel authLevel, McpConfig config) {
        JsonArray tools = new JsonArray();

        for (McpFeature feature : features.values()) {
            if (feature.hasPermission(authLevel, config)) {
                McpTool tool = feature.getToolDefinition();
                JsonObject toolJson = new JsonObject();
                toolJson.addProperty("name", tool.getName());
                toolJson.addProperty("description", tool.getDescription());
                toolJson.addProperty("type", tool.getType());
                tools.add(toolJson);
            }
        }

        return tools;
    }

    public McpToolResponse executeFeature(String featureName, McpToolCall call, McpAuthManager.AuthLevel authLevel, McpConfig config) {
        McpFeature feature = features.get(featureName);

        if (feature == null) {
            return McpToolResponse.error("Feature not found: " + featureName);
        }

        if (!feature.hasPermission(authLevel, config)) {
            return McpToolResponse.error("Permission denied for feature: " + featureName);
        }

        return feature.execute(call, authLevel);
    }

    public List<McpServerFeatures.SyncToolSpecification> getToolSpecifications(ObjectMapper objectMapper, McpConfig config) {
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);
        return features.values().stream()
            .map(feature -> buildToolSpec(feature, jsonMapper, config))
            .toList();
    }

    private McpServerFeatures.SyncToolSpecification buildToolSpec(
        McpFeature feature,
        JacksonMcpJsonMapper jsonMapper,
        McpConfig config
    ) {
        McpTool tool = feature.getToolDefinition();
        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(McpSchema.Tool.builder()
                .name(tool.getName())
                .description(tool.getDescription())
                .inputSchema(jsonMapper, feature.getInputSchema())
                .build())
            .callHandler((exchange, request) -> callTool(exchange, request, tool.getName(), config))
            .build();
    }

    private McpSchema.CallToolResult callTool(
        McpSyncServerExchange exchange,
        McpSchema.CallToolRequest request,
        String toolName,
        McpConfig config
    ) {
        try {
            McpAuthManager.AuthLevel authLevel = getAuthLevel(exchange);
            Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
            McpToolCall call = new McpToolCall(request.name(), arguments);
            McpToolResponse result = executeFeature(toolName, call, authLevel, config);

            return McpSchema.CallToolResult.builder()
                .isError(result.isError())
                .addTextContent(result.getContent())
                .build();
        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Tool execution failed: " + toolName);
            return McpSchema.CallToolResult.builder()
                .isError(true)
                .addTextContent("Failed to execute tool: " + toolName)
                .build();
        }
    }

    private static McpAuthManager.AuthLevel getAuthLevel(McpSyncServerExchange exchange) {
        if (exchange == null || exchange.transportContext() == null) {
            return McpAuthManager.AuthLevel.NONE;
        }
        Object value = exchange.transportContext().get(McpContextKeys.AUTH_LEVEL);
        if (value instanceof McpAuthManager.AuthLevel) {
            return (McpAuthManager.AuthLevel) value;
        }
        return McpAuthManager.AuthLevel.NONE;
    }
}
