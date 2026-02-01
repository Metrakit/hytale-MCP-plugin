package com.top_serveurs.hytale.plugins.mcp.features;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.top_serveurs.hytale.plugins.mcp.auth.McpAuthManager;
import com.top_serveurs.hytale.plugins.mcp.auth.McpAuthManager.AuthLevel;
import com.top_serveurs.hytale.plugins.mcp.config.McpConfig;
import com.top_serveurs.hytale.plugins.mcp.models.McpTool;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolCall;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolResponse;

public class GetBlockTypesFeature implements McpFeature {

    private static final Gson GSON = new Gson();
    private final HytaleLogger logger;

    public GetBlockTypesFeature(HytaleLogger logger) {
        this.logger = logger;
    }

    @Override
    public String getName() {
        return "get_block_types";
    }

    @Override
    public McpTool getToolDefinition() {
        return new McpTool(
                "get_block_types",
                "Gets a list of all available block types that can be used in building.",
                "function"
        );
    }

    @Override
    public String getInputSchema() {
        return McpToolSchema.emptyObjectSchema();
    }

    @Override
    public McpToolResponse execute(McpToolCall call, AuthLevel authLevel) {
        try {
            JsonArray blockTypes = new JsonArray();

            int maxBlockId = 10000;
            for (int id = 0; id < maxBlockId; id++) {
                try {
                    BlockType blockType = BlockType.getAssetMap().getAsset(id);
                    if (blockType != null && blockType != BlockType.EMPTY) {
                        String blockName = blockType.getId();
                        if (blockName != null && !blockName.isEmpty()) {
                            JsonObject blockInfo = new JsonObject();
                            blockInfo.addProperty("name", blockName);
                            blockInfo.addProperty("id", id);
                            blockTypes.add(blockInfo);
                        }
                    }
                } catch (Exception e) {
                }
            }

            JsonObject response = new JsonObject();
            response.addProperty("count", blockTypes.size());
            response.add("blocks", blockTypes);

            logger.atInfo().log("[GET_BLOCK_TYPES] Returning " + blockTypes.size() + " block types");

            return McpToolResponse.success(GSON.toJson(response));

        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Error getting block types");
            return McpToolResponse.error("Failed to get block types: " + e.getMessage());
        }
    }

    @Override
    public boolean hasPermission(McpAuthManager.AuthLevel authLevel, McpConfig config) {
        if (authLevel == McpAuthManager.AuthLevel.ADMIN) {
            return config.getFeatures().getAdmins().canGetBlockTypes();
        }
        if (authLevel == McpAuthManager.AuthLevel.PLAYER) {
            return config.getFeatures().getPlayers().canGetBlockTypes();
        }
        return false;
    }
}
