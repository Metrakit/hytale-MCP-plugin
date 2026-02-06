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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ListBlocksFeature implements McpFeature {

    private static final Gson GSON = new Gson();
    private final HytaleLogger logger;

    // Cache for store all blocks by category
    private static volatile Map<String, CategorizedBlock> blocksCache = null;
    private static final Object CACHE_LOCK = new Object();

    public ListBlocksFeature(HytaleLogger logger) {
        this.logger = logger;
    }

    @Override
    public String getName() {
        return "list_blocks";
    }

    @Override
    public McpTool getToolDefinition() {
        return new McpTool(
                "list_blocks",
                "Lists all available blocks with smart categorization. Supports filtering by search term, category, and limiting results.",
                "function"
        );
    }

    @Override
    public String getInputSchema() {
        return McpToolSchema.schemaWithProperties(
            java.util.Map.of(
                "limit", McpToolSchema.integerProperty("Maximum number of blocks to return (optional)"),
                "search", McpToolSchema.stringProperty("Search term to filter blocks by name (optional, case-insensitive)"),
                "category", McpToolSchema.stringProperty("Category to filter blocks (optional). Available categories: building, decoration, nature, ore, stone, wood, metal, glass, food, tool, weapon, misc")
            ),
            java.util.List.of()
        );
    }

    @Override
    public McpToolResponse execute(McpToolCall call, AuthLevel authLevel) {
        try {
            ensureCacheInitialized();

            Integer limit = getArgumentAsInteger(call, "limit");
            String search = getArgumentAsString(call, "search");
            String category = getArgumentAsString(call, "category");

            if (category != null && !category.isEmpty() && !isValidCategory(category)) {
                return McpToolResponse.error("Invalid category. Available categories: " + String.join(", ", getAvailableCategories()));
            }

            List<CategorizedBlock> filteredBlocks = filterBlocks(search, category);

            if (limit != null && limit > 0 && limit < filteredBlocks.size()) {
                filteredBlocks = filteredBlocks.subList(0, limit);
            }

            JsonArray blocksArray = new JsonArray();
            for (CategorizedBlock block : filteredBlocks) {
                JsonObject blockObj = new JsonObject();
                blockObj.addProperty("name", block.name);
                blockObj.addProperty("id", block.id);
                blockObj.addProperty("category", block.category);
                blocksArray.add(blockObj);
            }

            Map<String, Long> categoryStats = getCategoryStatistics();

            JsonObject response = new JsonObject();
            response.addProperty("total", blocksCache.size());
            response.addProperty("returned", filteredBlocks.size());
            response.add("blocks", blocksArray);

            JsonObject stats = new JsonObject();
            for (Map.Entry<String, Long> entry : categoryStats.entrySet()) {
                stats.addProperty(entry.getKey(), entry.getValue());
            }
            response.add("categoryStats", stats);

            if (search != null && !search.isEmpty()) {
                response.addProperty("searchTerm", search);
            }
            if (category != null && !category.isEmpty()) {
                response.addProperty("filterCategory", category);
            }

            logger.atInfo().log("[LIST_BLOCKS] Returned " + filteredBlocks.size() + " blocks" +
                (search != null ? " (search: " + search + ")" : "") +
                (category != null ? " (category: " + category + ")" : ""));

            return McpToolResponse.success(GSON.toJson(response));

        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Error listing blocks");
            return McpToolResponse.error("Failed to list blocks: " + e.getMessage());
        }
    }

    @Override
    public boolean hasPermission(McpAuthManager.AuthLevel authLevel, McpConfig config) {
        if (authLevel == McpAuthManager.AuthLevel.ADMIN) {
            return config.getFeatures().getAdmins().canListBlocks();
        }
        if (authLevel == McpAuthManager.AuthLevel.PLAYER) {
            return config.getFeatures().getPlayers().canListBlocks();
        }
        return false;
    }

    private void ensureCacheInitialized() {
        if (blocksCache == null) {
            synchronized (CACHE_LOCK) {
                if (blocksCache == null) {
                    logger.atInfo().log("[LIST_BLOCKS] Initializing blocks cache...");
                    long startTime = System.currentTimeMillis();
                    blocksCache = buildBlocksCache();
                    long duration = System.currentTimeMillis() - startTime;
                    logger.atInfo().log("[LIST_BLOCKS] Cache initialized with " + blocksCache.size() + " blocks in " + duration + "ms");
                }
            }
        }
    }

    private Map<String, CategorizedBlock> buildBlocksCache() {
        Map<String, CategorizedBlock> cache = new ConcurrentHashMap<>();

        int maxBlockId = 10000;
        for (int id = 0; id < maxBlockId; id++) {
            try {
                BlockType blockType = BlockType.getAssetMap().getAsset(id);
                if (blockType != null && blockType != BlockType.EMPTY) {
                    String blockName = blockType.getId();
                    if (blockName != null && !blockName.isEmpty()) {
                        String category = categorizeBlock(blockName);
                        CategorizedBlock block = new CategorizedBlock(blockName, id, category);
                        cache.put(blockName, block);
                    }
                }
            } catch (Exception e) {
                // Ignore errors for invalid IDs
            }
        }

        return cache;
    }

    private String categorizeBlock(String blockName) {
        String lowerName = blockName.toLowerCase();

        // building blocks
        if (lowerName.contains("brick") || lowerName.contains("concrete") || lowerName.contains("cement") ||
            lowerName.contains("plaster") || lowerName.contains("tile") || lowerName.contains("slab") ||
            lowerName.contains("stair") || lowerName.contains("wall") || lowerName.contains("fence")) {
            return "building";
        }

        // Ores and minerals
        if (lowerName.contains("ore") || lowerName.contains("vein") || lowerName.contains("deposit")) {
            return "ore";
        }

        // Stones
        if (lowerName.contains("stone") || lowerName.contains("rock") || lowerName.contains("cobble") ||
            lowerName.contains("granite") || lowerName.contains("marble") || lowerName.contains("slate") ||
            lowerName.contains("limestone") || lowerName.contains("sandstone")) {
            return "stone";
        }

        // Wood
        if (lowerName.contains("wood") || lowerName.contains("log") || lowerName.contains("plank") ||
            lowerName.contains("lumber") || lowerName.contains("timber")) {
            return "wood";
        }

        // Metal
        if (lowerName.contains("iron") || lowerName.contains("steel") || lowerName.contains("copper") ||
            lowerName.contains("bronze") || lowerName.contains("gold") || lowerName.contains("silver") ||
            lowerName.contains("metal") || lowerName.contains("ingot")) {
            return "metal";
        }

        // Glass
        if (lowerName.contains("glass") || lowerName.contains("pane") || lowerName.contains("window")) {
            return "glass";
        }

        // Nature
        if (lowerName.contains("grass") || lowerName.contains("dirt") || lowerName.contains("soil") ||
            lowerName.contains("sand") || lowerName.contains("gravel") || lowerName.contains("clay") ||
            lowerName.contains("leaf") || lowerName.contains("leaves") || lowerName.contains("flower") ||
            lowerName.contains("plant") || lowerName.contains("tree") || lowerName.contains("bush") ||
            lowerName.contains("vine") || lowerName.contains("moss") || lowerName.contains("mushroom")) {
            return "nature";
        }

        // Decoration
        if (lowerName.contains("carpet") || lowerName.contains("rug") || lowerName.contains("banner") ||
            lowerName.contains("painting") || lowerName.contains("frame") || lowerName.contains("pot") ||
            lowerName.contains("vase") || lowerName.contains("decoration") || lowerName.contains("ornament") ||
            lowerName.contains("lamp") || lowerName.contains("lantern") || lowerName.contains("torch") ||
            lowerName.contains("candle") || lowerName.contains("chandelier")) {
            return "decoration";
        }

        // Food
        if (lowerName.contains("food") || lowerName.contains("bread") || lowerName.contains("meat") ||
            lowerName.contains("fish") || lowerName.contains("fruit") || lowerName.contains("vegetable") ||
            lowerName.contains("berry") || lowerName.contains("apple") || lowerName.contains("carrot") ||
            lowerName.contains("potato") || lowerName.contains("wheat") || lowerName.contains("crop")) {
            return "food";
        }

        // Tools
        if (lowerName.contains("pickaxe") || lowerName.contains("axe") || lowerName.contains("shovel") ||
            lowerName.contains("hoe") || lowerName.contains("hammer") || lowerName.contains("saw") ||
            lowerName.contains("tool")) {
            return "tool";
        }

        // Weapons
        if (lowerName.contains("sword") || lowerName.contains("bow") || lowerName.contains("arrow") ||
            lowerName.contains("spear") || lowerName.contains("dagger") || lowerName.contains("weapon") ||
            lowerName.contains("blade")) {
            return "weapon";
        }

        // Default: misc
        return "misc";
    }

    private List<CategorizedBlock> filterBlocks(String search, String category) {
        return blocksCache.values().stream()
            .filter(block -> {
                if (search != null && !search.isEmpty()) {
                    return block.name.toLowerCase().contains(search.toLowerCase());
                }
                return true;
            })
            .filter(block -> {
                if (category != null && !category.isEmpty()) {
                    return block.category.equalsIgnoreCase(category);
                }
                return true;
            })
            .sorted(Comparator.comparing(block -> block.name))
            .collect(Collectors.toList());
    }

    private Map<String, Long> getCategoryStatistics() {
        return blocksCache.values().stream()
            .collect(Collectors.groupingBy(
                block -> block.category,
                Collectors.counting()
            ));
    }

    private boolean isValidCategory(String category) {
        return getAvailableCategories().contains(category.toLowerCase());
    }

    private Set<String> getAvailableCategories() {
        return Set.of("building", "decoration", "nature", "ore", "stone", "wood",
                     "metal", "glass", "food", "tool", "weapon", "misc");
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

    private static class CategorizedBlock {
        final String name;
        final int id;
        final String category;

        CategorizedBlock(String name, int id, String category) {
            this.name = name;
            this.id = id;
            this.category = category;
        }
    }
}
