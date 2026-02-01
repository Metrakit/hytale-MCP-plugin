package com.top_serveurs.hytale.plugins.mcp.models;

import java.util.Map;

public class McpToolCall {
    private final String toolName;
    private final Map<String, Object> arguments;

    public McpToolCall(String toolName, Map<String, Object> arguments) {
        this.toolName = toolName;
        this.arguments = arguments;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }
}
