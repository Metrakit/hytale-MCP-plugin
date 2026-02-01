package com.top_serveurs.hytale.plugins.mcp.models;

public class McpTool {
    private final String name;
    private final String description;
    private final String type;

    public McpTool(String name, String description, String type) {
        this.name = name;
        this.description = description;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getType() {
        return type;
    }
}
