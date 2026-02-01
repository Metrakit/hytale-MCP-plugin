package com.top_serveurs.hytale.plugins.mcp.features;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.top_serveurs.hytale.plugins.mcp.auth.McpAuthManager;
import com.top_serveurs.hytale.plugins.mcp.config.McpConfig;
import com.top_serveurs.hytale.plugins.mcp.models.McpTool;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolCall;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GetPlayerPositionFeature implements McpFeature {
    private static final Gson GSON = new Gson();
    private final HytaleLogger logger;

    public GetPlayerPositionFeature(HytaleLogger logger) {
        this.logger = logger;
    }

    @Override
    public String getName() {
        return "get_player_position";
    }

    @Override
    public McpTool getToolDefinition() {
        return new McpTool(
            "get_player_position",
            "Gets the current position (x, y, z) of a specific player",
            "function"
        );
    }

    @Override
    public String getInputSchema() {
        return McpToolSchema.schemaWithProperties(
            java.util.Map.of(
                "player", McpToolSchema.stringProperty("Player name or UUID")
            ),
            java.util.List.of("player")
        );
    }

    @Override
    public McpToolResponse execute(McpToolCall call, McpAuthManager.AuthLevel authLevel) {
        try {
            Map<String, Object> args = call.getArguments();
            if (!args.containsKey("player")) {
                return McpToolResponse.error("Missing required parameter: player");
            }

            String playerIdentifier = args.get("player").toString();
            PlayerRef player = findPlayer(playerIdentifier);

            if (player == null) {
                return McpToolResponse.error("Player not found: " + playerIdentifier);
            }

            com.hypixel.hytale.math.vector.Transform transform = player.getTransform();
            com.hypixel.hytale.math.vector.Vector3d pos = transform.getPosition();
            com.hypixel.hytale.math.vector.Vector3f rotation = transform.getRotation();

            JsonObject position = new JsonObject();
            position.addProperty("x", pos.getX());
            position.addProperty("y", pos.getY());
            position.addProperty("z", pos.getZ());
            position.addProperty("worldUuid", player.getWorldUuid().toString());
            position.addProperty("yaw", rotation.getY());
            position.addProperty("pitch", rotation.getX());

            JsonObject response = new JsonObject();
            response.addProperty("name", player.getUsername());
            response.addProperty("uuid", player.getUuid().toString());
            response.add("position", position);

            return McpToolResponse.success(GSON.toJson(response));
        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Error getting player position");
            return McpToolResponse.error("Failed to get player position: " + e.getMessage());
        }
    }

    private PlayerRef findPlayer(String identifier) {
        List<PlayerRef> players = Universe.get().getPlayers();
        
        try {
            UUID uuid = UUID.fromString(identifier);
            for (PlayerRef player : players) {
                if (player.getUuid().equals(uuid)) {
                    return player;
                }
            }
        } catch (IllegalArgumentException e) {
        }
        
        for (PlayerRef player : players) {
            if (player.getUsername().equalsIgnoreCase(identifier)) {
                return player;
            }
        }
        
        return null;
    }

    @Override
    public boolean hasPermission(McpAuthManager.AuthLevel authLevel, McpConfig config) {
        if (authLevel == McpAuthManager.AuthLevel.ADMIN) {
            return config.getFeatures().getAdmins().canGetPlayerPosition();
        }
        if (authLevel == McpAuthManager.AuthLevel.PLAYER) {
            return config.getFeatures().getPlayers().canGetPlayerPosition();
        }
        return false;
    }
}
