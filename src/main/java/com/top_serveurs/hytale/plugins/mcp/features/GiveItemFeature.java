package com.top_serveurs.hytale.plugins.mcp.features;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.top_serveurs.hytale.plugins.mcp.auth.McpAuthManager;
import com.top_serveurs.hytale.plugins.mcp.config.McpConfig;
import com.top_serveurs.hytale.plugins.mcp.models.McpTool;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolCall;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GiveItemFeature implements McpFeature {
    private static final Gson GSON = new Gson();
    private final HytaleLogger logger;

    public GiveItemFeature(HytaleLogger logger, McpConfig config) {
        this.logger = logger;
    }

    @Override
    public String getName() {
        return "give_item";
    }

    @Override
    public McpTool getToolDefinition() {
        return new McpTool(
            "give_item",
            "Gives an item to a player. Use list_blocks tool to search for available item IDs.",
            "function"
        );
    }

    @Override
    public String getInputSchema() {
        return McpToolSchema.schemaWithProperties(
            java.util.Map.of(
                "player", McpToolSchema.stringProperty("Player name to give the item to"),
                "itemId", McpToolSchema.stringProperty("Item ID (e.g., 'Ingredient_Stick', 'Tool_Fertilizer')"),
                "quantity", McpToolSchema.integerProperty("Quantity to give (optional, default: 1)")
            ),
            java.util.List.of("player", "itemId")
        );
    }

    @Override
    public McpToolResponse execute(McpToolCall call, McpAuthManager.AuthLevel authLevel) {
        try {
            String player = getArgumentAsString(call, "player");
            String itemId = getArgumentAsString(call, "itemId");
            Integer quantity = getArgumentAsInteger(call, "quantity");

            // Validation
            if (player == null || player.isEmpty()) {
                return McpToolResponse.error("Player name is required");
            }
            if (itemId == null || itemId.isEmpty()) {
                return McpToolResponse.error("Item ID is required");
            }

            // default
            if (quantity == null || quantity < 1) {
                quantity = 1;
            }

            // Build the command
            String command = "give " + player + " " + itemId + " --quantity=" + quantity;

            // Get first world available
            Map<String, World> worlds = Universe.get().getWorlds();
            if (worlds.isEmpty()) {
                return McpToolResponse.error("No world available to execute command");
            }

            World world = worlds.values().iterator().next();
            CompletableFuture<McpToolResponse> future = new CompletableFuture<>();
            final String finalCommand = command;
            final int finalQuantity = quantity;

            world.execute(() -> {
                try {
                    CommandManager commandManager = HytaleServer.get().getCommandManager();

                    logger.atInfo().log("[GIVE_ITEM] Executing: " + finalCommand);
                    commandManager.handleCommand(ConsoleSender.INSTANCE, finalCommand);

                    JsonObject response = new JsonObject();
                    response.addProperty("player", player);
                    response.addProperty("itemId", itemId);
                    response.addProperty("quantity", finalQuantity);
                    response.addProperty("command", finalCommand);
                    response.addProperty("status", "executed");

                    future.complete(McpToolResponse.success(GSON.toJson(response)));

                } catch (Throwable t) {
                    logger.atSevere().withCause(t).log("[GIVE_ITEM] Exception");
                    future.complete(McpToolResponse.error("Failed to give item: " + t.getMessage()));
                }
            });

            return future.join();

        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Error giving item");
            return McpToolResponse.error("Failed to give item: " + e.getMessage());
        }
    }

    @Override
    public boolean hasPermission(McpAuthManager.AuthLevel authLevel, McpConfig config) {
        if (authLevel == McpAuthManager.AuthLevel.ADMIN) {
            return config.getFeatures().getAdmins().canExecuteCommand();
        }
        if (authLevel == McpAuthManager.AuthLevel.PLAYER) {
            return config.getFeatures().getPlayers().canExecuteCommand();
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
