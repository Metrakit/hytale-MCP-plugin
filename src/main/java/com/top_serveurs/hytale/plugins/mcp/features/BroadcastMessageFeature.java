package com.top_serveurs.hytale.plugins.mcp.features;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.Universe;
import com.top_serveurs.hytale.plugins.mcp.auth.McpAuthManager;
import com.top_serveurs.hytale.plugins.mcp.config.McpConfig;
import com.top_serveurs.hytale.plugins.mcp.models.McpTool;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolCall;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolResponse;

public class BroadcastMessageFeature implements McpFeature {
    private static final Gson GSON = new Gson();
    private final HytaleLogger logger;

    public BroadcastMessageFeature(HytaleLogger logger, McpConfig config) {
        this.logger = logger;
    }

    @Override
    public String getName() {
        return "broadcast_message";
    }

    @Override
    public McpTool getToolDefinition() {
        return new McpTool(
            "broadcast_message",
            "Broadcasts a message to all connected players",
            "function"
        );
    }

    @Override
    public String getInputSchema() {
        return McpToolSchema.schemaWithProperties(
            java.util.Map.of(
                "message", McpToolSchema.stringProperty("Message to broadcast to all players")
            ),
            java.util.List.of("message")
        );
    }

    @Override
    public McpToolResponse execute(McpToolCall call, McpAuthManager.AuthLevel authLevel) {
        try {
            String message = call.getArguments().get("message").toString();
            if (message == null || message.isEmpty()) {
                return McpToolResponse.error("Message is required");
            }

            Universe.get().sendMessage(Message.raw(message));

            JsonObject response = new JsonObject();
            response.addProperty("message", message);
            response.addProperty("status", "broadcasted");

            return McpToolResponse.success(GSON.toJson(response));
        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Error broadcasting message");
            return McpToolResponse.error("Failed to broadcast message: " + e.getMessage());
        }
    }

    @Override
    public boolean hasPermission(McpAuthManager.AuthLevel authLevel, McpConfig config) {
        if (authLevel == McpAuthManager.AuthLevel.ADMIN) {
            return config.getFeatures().getAdmins().canBroadcastMessage();
        }
        if (authLevel == McpAuthManager.AuthLevel.PLAYER) {
            return config.getFeatures().getPlayers().canBroadcastMessage();
        }
        return false;
    }
}
