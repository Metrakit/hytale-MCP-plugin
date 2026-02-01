package com.top_serveurs.hytale.plugins.mcp.models;

public class McpResponse {
    private final String jsonrpc;
    private final Object result;
    private final McpError error;
    private final Integer id;

    public McpResponse(String jsonrpc, Object result, McpError error, Integer id) {
        this.jsonrpc = jsonrpc;
        this.result = result;
        this.error = error;
        this.id = id;
    }

    public static McpResponse success(Object result, Integer id) {
        return new McpResponse("2.0", result, null, id);
    }

    public static McpResponse error(int code, String message, Integer id) {
        return new McpResponse("2.0", null, new McpError(code, message), id);
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public Object getResult() {
        return result;
    }

    public McpError getError() {
        return error;
    }

    public Integer getId() {
        return id;
    }

    public static class McpError {
        private final int code;
        private final String message;

        public McpError(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
