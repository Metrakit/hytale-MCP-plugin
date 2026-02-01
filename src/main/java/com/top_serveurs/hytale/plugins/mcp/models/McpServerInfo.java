package com.top_serveurs.hytale.plugins.mcp.models;

public class McpServerInfo {
    private final String name;
    private final String version;
    private final String protocolVersion;

    public McpServerInfo(String name, String version, String protocolVersion) {
        this.name = name;
        this.version = version;
        this.protocolVersion = protocolVersion;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }
}
