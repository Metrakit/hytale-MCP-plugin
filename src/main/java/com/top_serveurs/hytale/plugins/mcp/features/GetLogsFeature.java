package com.top_serveurs.hytale.plugins.mcp.features;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.top_serveurs.hytale.plugins.mcp.auth.McpAuthManager;
import com.top_serveurs.hytale.plugins.mcp.config.McpConfig;
import com.top_serveurs.hytale.plugins.mcp.models.McpTool;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolCall;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolResponse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class GetLogsFeature implements McpFeature {
    private static final Gson GSON = new Gson();
    private static final int MAX_LINES = 1000;
    private static final int DEFAULT_LINES = 100;
    private final HytaleLogger logger;

    public GetLogsFeature(HytaleLogger logger) {
        this.logger = logger;
    }

    @Override
    public String getName() {
        return "get_logs";
    }

    @Override
    public McpTool getToolDefinition() {
        return new McpTool(
            "get_logs",
            "Retrieves server logs. Optionally specify line count, log level, and date. Default: 100 lines, all levels, latest logs.",
            "function"
        );
    }

    @Override
    public String getInputSchema() {
        return McpToolSchema.schemaWithProperties(
            java.util.Map.of(
                "lines", McpToolSchema.integerProperty("Number of lines to return (max 1000)"),
                "level", McpToolSchema.stringProperty("Filter by log level, e.g. INFO, WARN, ERROR"),
                "date", McpToolSchema.stringProperty("Log file date in YYYY-MM-DD format")
            ),
            java.util.List.of()
        );
    }

    @Override
    public McpToolResponse execute(McpToolCall call, McpAuthManager.AuthLevel authLevel) {
        try {
            int lineCount = DEFAULT_LINES;
            String logLevel = null;
            String date = null;

            if (call.getArguments() != null) {
                Object linesObj = call.getArguments().get("lines");
                if (linesObj != null) {
                    try {
                        lineCount = Integer.parseInt(linesObj.toString());
                        if (lineCount <= 0) {
                            return McpToolResponse.error("Line count must be positive");
                        }
                        if (lineCount > MAX_LINES) {
                            lineCount = MAX_LINES;
                        }
                    } catch (NumberFormatException e) {
                        return McpToolResponse.error("Invalid line count: must be a number");
                    }
                }

                Object levelObj = call.getArguments().get("level");
                if (levelObj != null) {
                    logLevel = levelObj.toString().toUpperCase();
                }

                Object dateObj = call.getArguments().get("date");
                if (dateObj != null) {
                    date = dateObj.toString();
                }
            }

            String logContent = readLogs(lineCount, logLevel, date);

            JsonObject response = new JsonObject();
            response.addProperty("lineCount", lineCount);
            if (logLevel != null) {
                response.addProperty("level", logLevel);
            }
            if (date != null) {
                response.addProperty("date", date);
            }
            response.addProperty("content", logContent);
            response.addProperty("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            return McpToolResponse.success(GSON.toJson(response));
        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Error retrieving logs");
            return McpToolResponse.error("Failed to retrieve logs: " + e.getMessage());
        }
    }

    private String readLogs(int lineCount, String logLevel, String date) throws IOException {
        File logFile = findLogFile(date);
        if (logFile == null || !logFile.exists()) {
            return "No log file found" + (date != null ? " for date: " + date : "");
        }

        if (!logFile.canRead()) {
            return "Permission denied: cannot read log file";
        }

        List<String> lines = new ArrayList<>();
        int linesToRead = lineCount;

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (logLevel == null || line.contains("[" + logLevel + "]") || 
                    line.toUpperCase().contains(" " + logLevel + " ")) {
                    lines.add(line);
                    if (lines.size() >= linesToRead) {
                        break;
                    }
                }
            }
        }

        if (lines.isEmpty()) {
            return "No logs found" + (logLevel != null ? " with level: " + logLevel : "");
        }

        if (lines.size() >= linesToRead) {
            int fromIndex = Math.max(0, lines.size() - linesToRead);
            return String.join("\n", lines.subList(fromIndex, lines.size()));
        }

        return String.join("\n", lines);
    }

    private File findLogFile(String date) {
        String logFileName = "latest.log";
        
        if (date != null) {
            logFileName = date + ".log";
        }

        Path[] possiblePaths = {
            Paths.get("logs", logFileName),
            Paths.get("..", "logs", logFileName),
            Paths.get(".", logFileName),
            Paths.get("server", "logs", logFileName),
            Paths.get("..", "..", "logs", logFileName)
        };

        for (Path path : possiblePaths) {
            File file = path.toFile();
            if (file.exists() && file.isFile()) {
                return file;
            }
        }

        if (date == null) {
            File logsDir = new File("logs");
            if (logsDir.exists() && logsDir.isDirectory()) {
                File[] logFiles = logsDir.listFiles((dir, name) -> name.endsWith(".log"));
                if (logFiles != null && logFiles.length > 0) {
                    return logFiles[logFiles.length - 1];
                }
            }
        }

        return null;
    }

    @Override
    public boolean hasPermission(McpAuthManager.AuthLevel authLevel, McpConfig config) {
        if (authLevel == McpAuthManager.AuthLevel.ADMIN) {
            return config.getFeatures().getAdmins().canGetLogs();
        }
        if (authLevel == McpAuthManager.AuthLevel.PLAYER) {
            return config.getFeatures().getPlayers().canGetLogs();
        }
        return false;
    }
}
