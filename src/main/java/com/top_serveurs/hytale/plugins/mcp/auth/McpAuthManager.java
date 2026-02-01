package com.top_serveurs.hytale.plugins.mcp.auth;

import com.hypixel.hytale.logger.HytaleLogger;
import com.top_serveurs.hytale.plugins.mcp.config.McpConfig;
import jakarta.servlet.http.HttpServletRequest;

public class McpAuthManager {
    private final McpConfig config;
    private final HytaleLogger logger;

    public McpAuthManager(McpConfig config, HytaleLogger logger) {
        this.config = config;
        this.logger = logger;
    }

    public enum AuthLevel {
        ADMIN,
        PLAYER,
        NONE
    }

    public AuthLevel authenticate(HttpServletRequest request) {
        if (!config.getAuth().isEnabled()) {
            return AuthLevel.ADMIN;
        }

        String authToken = extractToken(request);
        if (authToken == null || authToken.isEmpty()) {
            logger.atWarning().log("Authentication failed: No token provided");
            return AuthLevel.NONE;
        }

        if (config.getAuth().getAdminTokens().contains(authToken)) {
            return AuthLevel.ADMIN;
        }

        if (config.getAuth().getPlayerTokens().contains(authToken)) {
            return AuthLevel.PLAYER;
        }

        logger.atWarning().log("Authentication failed: Invalid token");
        return AuthLevel.NONE;
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        String tokenParam = request.getParameter("token");
        if (tokenParam != null) {
            return tokenParam;
        }

        return null;
    }
}
