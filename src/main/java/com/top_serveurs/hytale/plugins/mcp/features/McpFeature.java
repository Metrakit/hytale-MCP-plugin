package com.top_serveurs.hytale.plugins.mcp.features;

import com.top_serveurs.hytale.plugins.mcp.auth.McpAuthManager;
import com.top_serveurs.hytale.plugins.mcp.config.McpConfig;
import com.top_serveurs.hytale.plugins.mcp.models.McpTool;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolCall;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolResponse;

public interface McpFeature {
    String getName();

    McpTool getToolDefinition();

    String getInputSchema();

    McpToolResponse execute(McpToolCall call, McpAuthManager.AuthLevel authLevel);

    boolean hasPermission(McpAuthManager.AuthLevel authLevel, McpConfig config);
}
