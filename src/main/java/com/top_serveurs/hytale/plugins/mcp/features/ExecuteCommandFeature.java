package com.top_serveurs.hytale.plugins.mcp.features;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.top_serveurs.hytale.plugins.mcp.auth.McpAuthManager;
import com.top_serveurs.hytale.plugins.mcp.config.McpConfig;
import com.top_serveurs.hytale.plugins.mcp.models.McpTool;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolCall;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolResponse;

import java.util.UUID;

public class ExecuteCommandFeature implements McpFeature {
    private static final Gson GSON = new Gson();
    private final HytaleLogger logger;

    public ExecuteCommandFeature(HytaleLogger logger, McpConfig config) {
        this.logger = logger;
    }

    @Override
    public String getName() {
        return "execute_command";
    }

    @Override
    public McpTool getToolDefinition() {
        return new McpTool(
            "execute_command",
            "Executes a server command. Requires admin permissions.",
            "function"
        );
    }

    @Override
    public String getInputSchema() {
        return McpToolSchema.schemaWithProperties(
            java.util.Map.of(
                "command", McpToolSchema.stringProperty("Command to execute")
            ),
            java.util.List.of("command")
        );
    }

    @Override
    public McpToolResponse execute(McpToolCall call, McpAuthManager.AuthLevel authLevel) {
        try {
            String command = call.getArguments().get("command").toString();
            if (command == null || command.isEmpty()) {
                return McpToolResponse.error("Command is required");
            }

            CommandManager commandManager = HytaleServer.get().getCommandManager();
            CommandSender sender = new CommandSender() {
                @Override
                public String getDisplayName() {
                    return "MCP";
                }

                @Override
                public UUID getUuid() {
                    return UUID.fromString("00000000-0000-0000-0000-000000000001");
                }

                @Override
                public boolean hasPermission(String permission) {
                    return true;
                }

                @Override
                public boolean hasPermission(String permission, boolean defaultValue) {
                    return true;
                }

                @Override
                public void sendMessage(Message message) {
                    logger.atInfo().log("Command output: " + message);
                }
            };

            commandManager.handleCommand(sender, command);

            JsonObject response = new JsonObject();
            response.addProperty("command", command);
            response.addProperty("status", "executed");

            return McpToolResponse.success(GSON.toJson(response));
        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Error executing command");
            return McpToolResponse.error("Failed to execute command: " + e.getMessage());
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
}
