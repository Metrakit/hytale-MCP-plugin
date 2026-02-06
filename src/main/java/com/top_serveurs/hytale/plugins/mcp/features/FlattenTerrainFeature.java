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

public class FlattenTerrainFeature implements McpFeature {

    private static final Gson GSON = new Gson();
    private final HytaleLogger logger;
    private final McpConfig config;

    public FlattenTerrainFeature(HytaleLogger logger, McpConfig config) {
        this.logger = logger;
        this.config = config;
    }

    @Override
    public String getName() {
        return "flatten_terrain";
    }

    @Override
    public McpTool getToolDefinition() {
        return new McpTool(
                "flatten_terrain",
                "Flattens a rectangular terrain area at a specific height, perfect for building foundations. Fills below with blocks and clears above with air.",
                "function"
        );
    }

    @Override
    public String getInputSchema() {
        return McpToolSchema.schemaWithProperties(
            java.util.Map.of(
                "world", McpToolSchema.stringProperty("World UUID"),
                "x1", McpToolSchema.integerProperty("First corner X coordinate"),
                "z1", McpToolSchema.integerProperty("First corner Z coordinate"),
                "x2", McpToolSchema.integerProperty("Second corner X coordinate"),
                "z2", McpToolSchema.integerProperty("Second corner Z coordinate"),
                "y", McpToolSchema.integerProperty("Height level to flatten at"),
                "fillBlock", McpToolSchema.stringProperty("Block type to fill below the surface (optional, default: dirt)"),
                "maxHeight", McpToolSchema.integerProperty("Maximum height to clear above (optional, default: y+10)")
            ),
            java.util.List.of("world", "x1", "z1", "x2", "z2", "y")
        );
    }

    @Override
    public McpToolResponse execute(McpToolCall call, AuthLevel authLevel) {
        try {
            String worldUuidStr = getArgumentAsString(call, "world");
            Integer x1 = getArgumentAsInteger(call, "x1");
            Integer z1 = getArgumentAsInteger(call, "z1");
            Integer x2 = getArgumentAsInteger(call, "x2");
            Integer z2 = getArgumentAsInteger(call, "z2");
            Integer targetY = getArgumentAsInteger(call, "y");
            String fillBlockStr = getArgumentAsString(call, "fillBlock");
            Integer maxHeight = getArgumentAsInteger(call, "maxHeight");

            // Validation
            if (worldUuidStr == null) {
                return McpToolResponse.error("world UUID is required");
            }
            if (x1 == null || z1 == null || x2 == null || z2 == null || targetY == null) {
                return McpToolResponse.error("x1, z1, x2, z2, and y are required");
            }

            // Default values
            if (fillBlockStr == null || fillBlockStr.isEmpty()) {
                fillBlockStr = "hytale:dirt";
            }
            if (maxHeight == null) {
                maxHeight = targetY + 10;
            }

            // Normalize coordinates (ensure min/max order)
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            int minZ = Math.min(z1, z2);
            int maxZ = Math.max(z1, z2);

            // Calculate total surface area and blocks to modify
            int surface = (maxX - minX + 1) * (maxZ - minZ + 1);
            int heightRange = maxHeight - targetY + 1;
            int totalBlocks = surface * heightRange;

            // Check block count limits (higher limit for terrain flattening)
            int maxBlocks = config.getFeatures().getMaxBlocksBatch() * 10;
            if (totalBlocks > maxBlocks) {
                return McpToolResponse.error("Area too large. Total blocks: " + totalBlocks + ", max: " + maxBlocks);
            }

            // Validate fill block type
            BlockType fillBlock = BlockType.getAssetMap().getAsset(fillBlockStr);
            if (fillBlock == null || fillBlock == BlockType.EMPTY) {
                return McpToolResponse.error("Unknown fill block type: " + fillBlockStr);
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
            final String finalFillBlockStr = fillBlockStr;
            final int finalMaxHeight = maxHeight;

            world.execute(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    int blocksPlaced = 0;
                    int blocksCleared = 0;

                    logger.atInfo().log("[FLATTEN_TERRAIN] Starting flatten from (" + minX + "," + minZ + ") to (" + maxX + "," + maxZ + ") at Y=" + targetY);

                    // Process each column in the flattening area
                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            // Fill below targetY with fill block
                            for (int y = 0; y < targetY; y++) {
                                world.setBlock(x, y, z, fillBlock.getId(), 0);
                                blocksPlaced++;
                            }

                            // Place surface block at targetY
                            world.setBlock(x, targetY, z, fillBlock.getId(), 0);
                            blocksPlaced++;

                            // Clear above with air
                            for (int y = targetY + 1; y <= finalMaxHeight; y++) {
                                world.setBlock(x, y, z, BlockType.EMPTY.getId(), 0);
                                blocksCleared++;
                            }
                        }
                    }

                    long duration = System.currentTimeMillis() - startTime;

                    JsonObject response = new JsonObject();
                    response.addProperty("area", surface);
                    response.addProperty("minX", minX);
                    response.addProperty("maxX", maxX);
                    response.addProperty("minZ", minZ);
                    response.addProperty("maxZ", maxZ);
                    response.addProperty("flattenY", targetY);
                    response.addProperty("maxHeight", finalMaxHeight);
                    response.addProperty("fillBlock", finalFillBlockStr);
                    response.addProperty("blocksPlaced", blocksPlaced);
                    response.addProperty("blocksCleared", blocksCleared);
                    response.addProperty("totalBlocks", blocksPlaced + blocksCleared);
                    response.addProperty("durationMs", duration);
                    response.addProperty("status", "success");

                    logger.atInfo().log("[FLATTEN_TERRAIN] Completed in " + duration + "ms. Placed: " +
                        blocksPlaced + ", Cleared: " + blocksCleared);

                    future.complete(McpToolResponse.success(GSON.toJson(response)));

                } catch (Throwable t) {
                    logger.atSevere().withCause(t).log("[FLATTEN_TERRAIN] Exception");
                    future.complete(McpToolResponse.error(t.toString()));
                }
            });

            return future.join();

        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Error flattening terrain");
            return McpToolResponse.error("Failed to flatten terrain: " + e.getMessage());
        }
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

    private String getArgumentAsString(McpToolCall call, String key) {
        Object value = call.getArguments().get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getArgumentAsInteger(McpToolCall call, String key) {
        Object value = call.getArguments().get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
