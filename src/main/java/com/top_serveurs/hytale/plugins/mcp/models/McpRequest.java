package com.top_serveurs.hytale.plugins.mcp.models;

import java.util.List;

public class McpRequest {
    private final String jsonrpc;
    private final String method;
    private final Object params;
    private final Integer id;

    public McpRequest(String jsonrpc, String method, Object params, Integer id) {
        this.jsonrpc = jsonrpc;
        this.method = method;
        this.params = params;
        this.id = id;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public String getMethod() {
        return method;
    }

    public Object getParams() {
        return params;
    }

    public Integer getId() {
        return id;
    }
}
