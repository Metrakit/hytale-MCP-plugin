package com.top_serveurs.hytale.plugins.mcp.features;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.top_serveurs.hytale.plugins.mcp.auth.McpAuthManager;
import com.top_serveurs.hytale.plugins.mcp.auth.McpAuthManager.AuthLevel;
import com.top_serveurs.hytale.plugins.mcp.config.McpConfig;
import com.top_serveurs.hytale.plugins.mcp.models.McpTool;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolCall;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolResponse;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SetBlockFeature implements McpFeature {

    private static final Gson GSON = new Gson();
    private final HytaleLogger logger;

    public SetBlockFeature(HytaleLogger logger) {
        this.logger = logger;
    }

    @Override
    public String getName() {
        return "set_block";
    }

    @Override
    public McpTool getToolDefinition() {
        return new McpTool(
                "set_block",
                "Sets a block at specified world coordinates. ",
                "function"
        );
    }

    @Override
    public String getInputSchema() {
        return McpToolSchema.schemaWithProperties(
            java.util.Map.of(
                "x", McpToolSchema.integerProperty("X coordinate"),
                "y", McpToolSchema.integerProperty("Y coordinate"),
                "z", McpToolSchema.integerProperty("Z coordinate"),
                "blockType", McpToolSchema.stringProperty("Block type identifier"),
                "world", McpToolSchema.stringProperty("World UUID")
            ),
            java.util.List.of("x", "y", "z", "blockType", "world")
        );
    }

    @Override
    public McpToolResponse execute(McpToolCall call, AuthLevel authLevel) {

        int x = getArgumentAsInt(call, "x");
        int y = getArgumentAsInt(call, "y");
        int z = getArgumentAsInt(call, "z");
        String blockTypeStr = getArgumentAsString(call, "blockType");
        String worldUuidStr = getArgumentAsString(call, "world");

        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) {
            return McpToolResponse.error("x, y and z are required integers");
        }

        if (blockTypeStr == null || blockTypeStr.isEmpty()) {
            return McpToolResponse.error("blockType is required");
        }

        if (worldUuidStr == null) {
            return McpToolResponse.error("world UUID is required");
        }

        BlockType blockType = BlockType.getAssetMap().getAsset(blockTypeStr);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return McpToolResponse.error("Unknown block type: " + blockTypeStr);
        }

        UUID worldUuid;
        try {
            worldUuid = UUID.fromString(worldUuidStr);
        } catch (IllegalArgumentException e) {
            return McpToolResponse.error("Invalid world UUID");
        }

        World world = Universe.get().getWorld(worldUuid);
        if (world == null) {
            return McpToolResponse.error("World not found: " + worldUuidStr);
        }

        CompletableFuture<McpToolResponse> future = new CompletableFuture<>();

        world.execute(() -> {
            try {
                logger.atInfo().log(
                        "[SET_BLOCK] Using WORLD API at (" + x + "," + y + "," + z + ")"
                );

                world.setBlock(
                        x,
                        y,
                        z,
                        blockType.getId(),
                        0
                );

                JsonObject json = new JsonObject();
                json.addProperty("x", x);
                json.addProperty("y", y);
                json.addProperty("z", z);
                json.addProperty("blockType", blockTypeStr);

                future.complete(McpToolResponse.success(GSON.toJson(json)));

            } catch (Throwable t) {
                logger.atSevere().withCause(t).log("[SET_BLOCK] Exception");
                future.complete(McpToolResponse.error(t.toString()));
            }
        });

        return future.join();
    }

    @Override
    public boolean hasPermission(McpAuthManager.AuthLevel authLevel, McpConfig config) {
        if (authLevel == McpAuthManager.AuthLevel.ADMIN) {
            return config.getFeatures().getAdmins().canSetBlock();
        }
        if (authLevel == McpAuthManager.AuthLevel.PLAYER) {
            return config.getFeatures().getPlayers().canSetBlock();
        }
        return false;
    }

    private int getArgumentAsInt(McpToolCall call, String key) {
        try {
            Object value = call.getArguments().get(key);
            if (value == null) return Integer.MIN_VALUE;
            if (value instanceof Number) return ((Number) value).intValue();
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    private String getArgumentAsString(McpToolCall call, String key) {
        Object value = call.getArguments().get(key);
        return value != null ? value.toString() : null;
    }
}
