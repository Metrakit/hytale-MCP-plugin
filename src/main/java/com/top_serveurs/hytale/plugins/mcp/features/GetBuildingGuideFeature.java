package com.top_serveurs.hytale.plugins.mcp.features;

import com.hypixel.hytale.logger.HytaleLogger;
import com.top_serveurs.hytale.plugins.mcp.auth.McpAuthManager;
import com.top_serveurs.hytale.plugins.mcp.auth.McpAuthManager.AuthLevel;
import com.top_serveurs.hytale.plugins.mcp.config.McpConfig;
import com.top_serveurs.hytale.plugins.mcp.models.McpTool;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolCall;
import com.top_serveurs.hytale.plugins.mcp.models.McpToolResponse;

/**
 * Read-only tool that returns a comprehensive construction guide to AI agents.
 */
public class GetBuildingGuideFeature implements McpFeature {

    private final HytaleLogger logger;
    private final String guide;

    public GetBuildingGuideFeature(HytaleLogger logger, McpConfig config) {
        this.logger = logger;
        this.guide = GUIDE_TEMPLATE.replace("{MAX_BLOCKS_BATCH}",
                String.valueOf(config.getFeatures().getMaxBlocksBatch()));
    }

    @Override
    public String getName() {
        return "get_building_guide";
    }

    @Override
    public McpTool getToolDefinition() {
        return new McpTool(
                "get_building_guide",
                "Returns the complete Hytale construction guide for AI agents. "
                + "ALWAYS call this before using set_blocks_batch. Contains: coordinate system, "
                + "all valid block type names, support/gravity rules, batch strategies, "
                + "material palettes per architectural style, math patterns for common shapes, "
                + "and step-by-step blueprints for iconic structures (Eiffel Tower, Forbidden City, Colosseum, modern house).",
                "function"
        );
    }

    @Override
    public String getInputSchema() {
        return McpToolSchema.emptyObjectSchema();
    }

    @Override
    public McpToolResponse execute(McpToolCall call, AuthLevel authLevel) {
        logger.atInfo().log("[GET_BUILDING_GUIDE] Construction guide requested");
        return McpToolResponse.success(guide);
    }

    @Override
    public boolean hasPermission(McpAuthManager.AuthLevel authLevel, McpConfig config) {
        if (authLevel == McpAuthManager.AuthLevel.ADMIN) {
            return config.getFeatures().getAdmins().canListBlocks();
        }
        if (authLevel == McpAuthManager.AuthLevel.PLAYER) {
            return config.getFeatures().getPlayers().canListBlocks();
        }
        return false;
    }

    private static final String GUIDE_TEMPLATE = """
            {
              "guide_version": "1.0",
              "description": "Complete Hytale construction guide for AI agents. Call this before any set_blocks_batch.",
              "runtime_tip": "Call list_blocks for the full live block list.",

              "workflow": [
                "1. Call get_building_guide (this tool) — load all rules.",
                "2. Call get_player_position with the target player name to get X, Y, Z and worldUuid.",
                "3. Call get_world_info with that worldUuid to confirm the world is loaded.",
                "4. TERRAIN PREPARATION (RECOMMENDED): Ask if the user wants to prepare the terrain. If yes, use flatten_terrain to create a flat foundation area for the building. This ensures a clean, level surface and prevents uneven ground issues. Calculate the area needed based on your building dimensions and use flatten_terrain with appropriate coordinates.",
                "5. Plan all block positions mathematically. Use the player position (or flattened area) as your origin.",
                "6. SORT all blocks by Y ascending (lowest Y first). This is mandatory — blocks need support below.",
                "7. Split into batches of <= {MAX_BLOCKS_BATCH} blocks each. Call set_blocks_batch once per batch.",
                "8. Execute batches sequentially — wait for each response before sending the next.",
                "9. Check each response for per-block errors and retry any failures.",
                "10. CRITICAL: ONLY use set_blocks_batch for construction. NEVER use execute_command, /setblock, /fill, or any other method. set_blocks_batch is the ONLY authorized building tool."
              ],

              "coordinate_system": {
                "x": "East (+X) / West (-X). Horizontal.",
                "y": "Up (+Y) / Down (-Y). Vertical. Y increases going up. Always build low Y first.",
                "z": "South (+Z) / North (-Z). Horizontal.",
                "ground_y": "Ground Y varies by location. Use the player Y from get_player_position as reference. Player Y is typically 1-2 blocks above ground — subtract 1 for ground level.",
                "block_unit": "Each block is 1x1x1. Integer coordinates. Block at (x,y,z) fills the cube from (x,y,z) to (x+1,y+1,z+1)."
              },

              "critical_rules": [
                "EXCLUSIVE TOOL: You MUST EXCLUSIVELY use set_blocks_batch for ALL block placement. NEVER EVER use execute_command, /setblock, /fill, /clone, or ANY other command or method for construction. set_blocks_batch is the ONLY authorized and supported building API. Using other methods will fail and cause errors.",
                "TERRAIN PREPARATION: For large buildings or structures requiring a flat foundation, ALWAYS ask the user if they want to prepare the terrain first using flatten_terrain. This tool creates a perfect flat surface and saves time by avoiding manual ground leveling.",
                "GRAVITY: Blocks need solid support at Y-1 (directly below). Sort all planned blocks by Y ascending before placing. Build bottom-to-top, layer-by-layer.",
                "BATCH LIMIT: set_blocks_batch accepts MAX {MAX_BLOCKS_BATCH} blocks per call. Large structures must be split across multiple sequential calls.",
                "LAYER ORDER: Finish ALL blocks at Y=N before placing any block at Y=N+1. Never skip ahead.",
                "EXACT NAMES: blockType is case-sensitive. Use exact strings from block_catalog. Typos cause silent per-block failures visible in the response.",
                "WORLD UUID: Pass the exact UUID string in the 'world' field of every set_blocks_batch call. Obtain it from get_player_position.",
                "HALF BLOCKS: Slabs (e.g. Rock_Stone_Cobble_Half) are half-height visually but occupy a full integer Y coordinate and DO provide support for blocks above.",
                "ROTATION: Blocks are placed at rotation 0 (default facing). Stairs and asymmetric blocks face a fixed default direction. Design structures to work with default rotation or use symmetric full blocks where orientation does not matter.",
                "HOLLOW BUILDINGS: For enclosed rooms, only place perimeter (wall) blocks. The interior is left empty."
              ],

              "common_mistakes_to_avoid": [
                "ATTEMPTING TO USE OTHER COMMANDS: NEVER try to use execute_command with /setblock, /fill, /clone, or any Minecraft-style commands for building. These WILL NOT WORK. You MUST use set_blocks_batch exclusively. This is not optional — it is mandatory.",
                "Not asking about terrain preparation: For buildings that require a flat surface, always ask the user if they want to use flatten_terrain first. Building on uneven terrain causes alignment issues.",
                "Wrong blockType casing: 'rock_stone_cobble' will fail. The correct name is 'Rock_Stone_Cobble'. Always copy names exactly.",
                "Missing the 'world' UUID field in set_blocks_batch — it is mandatory on every call.",
                "Placing blocks top-to-bottom. Always sort your block list by Y ascending and build from the ground up.",
                "Sending more than {MAX_BLOCKS_BATCH} blocks in a single set_blocks_batch call — the API will reject it.",
                "Using Minecraft block names. Hytale has completely different block names (e.g. 'Rock_Stone_Cobble' not 'cobblestone').",
                "Not checking the per-block 'status' fields in the response — individual blocks can fail while others succeed.",
                "Forgetting that half-blocks (slabs) still occupy a full integer Y coordinate and cannot share a Y level with another block at the same (x,z).",
                "Not calling get_player_position first — you need the world UUID and the ground Y reference point before you can plan coordinates.",
                "Placing decorative or detail blocks before their structural support blocks beneath them have been placed.",
                "Trying to be 'clever' by using other tools instead of set_blocks_batch: Don't do it. set_blocks_batch is optimized, tested, and the ONLY supported method."
              ],

              "terrain_preparation_guide": {
                "when_to_use": "Use flatten_terrain before building structures that need a flat foundation: houses, towers, castles, platforms, arenas, monuments.",
                "benefits": [
                  "Creates perfectly level building surface",
                  "Removes hills, holes, and uneven terrain",
                  "Saves time compared to manual leveling",
                  "Ensures structural integrity from the start"
                ],
                "workflow": [
                  "1. Get player position to determine the center of your building area",
                  "2. Calculate the rectangular area needed (add 2-5 block margin around your building footprint)",
                  "3. Ask the user: 'Would you like me to flatten the terrain first? This will create a X×Z flat area at height Y.'",
                  "4. If yes, call flatten_terrain with appropriate coordinates",
                  "5. Use the flattened Y level as your foundation level for building"
                ],
                "example": "For a 20×20 building, flatten a 25×25 area to have a 2-3 block margin. Set Y to ground level or desired platform height."
              }
            }
            """;
}
