package com.top_serveurs.hytale.plugins.mcp.models;

public class McpToolResponse {
    private final String content;
    private final boolean isError;

    public McpToolResponse(String content, boolean isError) {
        this.content = content;
        this.isError = isError;
    }

    public static McpToolResponse success(String content) {
        return new McpToolResponse(content, false);
    }

    public static McpToolResponse error(String error) {
        return new McpToolResponse(error, true);
    }

    public String getContent() {
        return content;
    }

    public boolean isError() {
        return isError;
    }
}
