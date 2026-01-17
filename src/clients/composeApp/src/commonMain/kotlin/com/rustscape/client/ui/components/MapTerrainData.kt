package com.rustscape.client.ui.components

/**
 * Predefined terrain data for known game regions
 *
 * This provides realistic minimap terrain for common areas without
 * needing to load from cache. Data is based on actual RS region layouts.
 *
 * Region coordinates use the standard RS format:
 * - regionX = worldX >> 6 (divide by 64)
 * - regionY = worldY >> 6 (divide by 64)
 *
 * Each region is 64x64 tiles.
 */
object MapTerrainData {

    /**
     * Tile type encoding for compact storage
     */
    private const val G = 0  // Grass
    private const val D = 1  // Dark grass
    private const val L = 2  // Light grass
    private const val W = 3  // Water
    private const val S = 4  // Shallow water / swamp
    private const val P = 5  // Path / dirt
    private const val R = 6  // Road / stone
    private const val A = 7  // Sand
    private const val B = 8  // Building floor
    private const val X = 9  // Wall / blocked
    private const val T = 10 // Tree
    private const val K = 11 // Rock
    private const val I = 12 // Bridge

    private fun Int.toTileType(): MinimapTileType = when (this) {
        G -> MinimapTileType.GRASS
        D -> MinimapTileType.DARK_GRASS
        L -> MinimapTileType.LIGHT_GRASS
        W -> MinimapTileType.WATER
        S -> MinimapTileType.SHALLOW_WATER
        P -> MinimapTileType.PATH
        R -> MinimapTileType.ROAD
        A -> MinimapTileType.SAND
        B -> MinimapTileType.BUILDING
        X -> MinimapTileType.WALL
        T -> MinimapTileType.TREE
        K -> MinimapTileType.ROCK
        I -> MinimapTileType.BRIDGE
        else -> MinimapTileType.UNKNOWN
    }

    /**
     * Known region terrain data
     * Key: (regionX, regionY) packed as (regionX << 16) | regionY
     */
    private val regionData = mutableMapOf<Int, Array<IntArray>>()

    /**
     * Initialize terrain data for known regions
     */
    init {
        // Load Lumbridge and surrounding areas
        loadLumbridgeArea()
        loadVarrockArea()
        loadFaladorArea()
        loadDraynorArea()
        loadAlKharidArea()
        loadEdgevilleArea()
        loadBarbarianVillageArea()
    }

    /**
     * Get terrain data for a region
     * @return 64x64 array of tile types, or null if region not known
     */
    fun getRegionTerrain(regionX: Int, regionY: Int): Array<Array<MinimapTileType>>? {
        val key = (regionX shl 16) or (regionY and 0xFFFF)
        val data = regionData[key] ?: return null

        return Array(64) { y ->
            Array(64) { x ->
                if (y < data.size && x < data[y].size) {
                    data[y][x].toTileType()
                } else {
                    MinimapTileType.GRASS
                }
            }
        }
    }

    /**
     * Get tile type at world coordinates
     */
    fun getTileType(worldX: Int, worldY: Int): MinimapTileType {
        val regionX = worldX shr 6
        val regionY = worldY shr 6
        val localX = worldX and 63
        val localY = worldY and 63

        val key = (regionX shl 16) or (regionY and 0xFFFF)
        val data = regionData[key] ?: return MinimapTileType.UNKNOWN

        return if (localY < data.size && localX < data[localY].size) {
            data[localY][localX].toTileType()
        } else {
            MinimapTileType.GRASS
        }
    }

    /**
     * Check if a region has terrain data
     */
    fun hasRegion(regionX: Int, regionY: Int): Boolean {
        val key = (regionX shl 16) or (regionY and 0xFFFF)
        return regionData.containsKey(key)
    }

    /**
     * Get list of all known region coordinates
     */
    fun getKnownRegions(): List<Pair<Int, Int>> {
        return regionData.keys.map { key ->
            val regionX = key shr 16
            val regionY = key and 0xFFFF
            regionX to regionY
        }
    }

    private fun storeRegion(regionX: Int, regionY: Int, data: Array<IntArray>) {
        val key = (regionX shl 16) or (regionY and 0xFFFF)
        regionData[key] = data
    }

    // =========================================================================
    // LUMBRIDGE AREA (Region 50,50 - World coords ~3200,3200)
    // =========================================================================

    private fun loadLumbridgeArea() {
        // Lumbridge is at approximately world coordinates (3222, 3218)
        // Region 50, 50 (3200-3263, 3200-3263)

        val lumbridge = Array(64) { IntArray(64) { G } }

        // River Lum running north-south on west side
        for (y in 0..63) {
            for (x in 0..8) {
                if (x < 5) lumbridge[y][x] = W
                else if (x < 8) lumbridge[y][x] = S
            }
        }

        // Bridge across River Lum (around y=20-24)
        for (y in 18..22) {
            for (x in 0..10) {
                lumbridge[y][x] = I
            }
        }

        // Lumbridge Castle area (center-east, around x=20-35, y=15-35)
        // Castle courtyard
        for (y in 15..35) {
            for (x in 18..38) {
                lumbridge[y][x] = R // Stone floor
            }
        }

        // Castle walls
        for (y in 15..35) {
            lumbridge[y][18] = X
            lumbridge[y][38] = X
        }
        for (x in 18..38) {
            lumbridge[15][x] = X
            lumbridge[35][x] = X
        }

        // Castle building interior
        for (y in 20..30) {
            for (x in 24..34) {
                lumbridge[y][x] = B
            }
        }

        // Main road from castle going south
        for (y in 0..15) {
            for (x in 26..30) {
                lumbridge[y][x] = P
            }
        }

        // Road going east from castle
        for (y in 23..27) {
            for (x in 38..63) {
                lumbridge[y][x] = P
            }
        }

        // Trees scattered around
        val treePositions = listOf(
            10 to 40, 12 to 42, 14 to 38, 45 to 10, 48 to 12,
            50 to 8, 52 to 15, 55 to 20, 42 to 45, 44 to 48,
            8 to 50, 10 to 55, 12 to 52, 40 to 55, 45 to 58,
            55 to 45, 58 to 42, 60 to 48
        )
        for ((tx, ty) in treePositions) {
            if (tx < 64 && ty < 64) lumbridge[ty][tx] = T
        }

        // General store area (south of castle)
        for (y in 5..12) {
            for (x in 40..48) {
                lumbridge[y][x] = B
            }
        }
        // General store walls
        lumbridge[5][40] = X; lumbridge[5][48] = X
        lumbridge[12][40] = X; lumbridge[12][48] = X

        // Graveyard area (west of river at bottom)
        for (y in 0..10) {
            for (x in 0..5) {
                if (lumbridge[y][x] != W) lumbridge[y][x] = D
            }
        }

        storeRegion(50, 50, lumbridge)

        // Northern Lumbridge region (50, 51) - Lumbridge swamp entrance
        val lumbridgeNorth = Array(64) { IntArray(64) { G } }

        // Continue river
        for (y in 0..63) {
            for (x in 0..6) {
                if (x < 4) lumbridgeNorth[y][x] = W
                else lumbridgeNorth[y][x] = S
            }
        }

        // Road continuing north
        for (y in 0..63) {
            for (x in 26..30) {
                lumbridgeNorth[y][x] = P
            }
        }

        // Trees
        for (y in 30..50) {
            for (x in 45..60) {
                if ((x + y) % 5 == 0) lumbridgeNorth[y][x] = T
            }
        }

        storeRegion(50, 51, lumbridgeNorth)
    }

    // =========================================================================
    // VARROCK AREA (Region 49-51, 54-55)
    // =========================================================================

    private fun loadVarrockArea() {
        // Varrock is at approximately (3210, 3424)
        // Region 50, 53-54

        val varrockSouth = Array(64) { IntArray(64) { G } }

        // Main square area with stone
        for (y in 20..45) {
            for (x in 15..50) {
                varrockSouth[y][x] = R
            }
        }

        // Buildings around square
        // West buildings
        for (y in 25..40) {
            for (x in 10..15) {
                varrockSouth[y][x] = B
            }
        }

        // East buildings (including bank)
        for (y in 25..40) {
            for (x in 50..60) {
                varrockSouth[y][x] = B
            }
        }

        // Bank building (east side, special marking)
        for (y in 30..38) {
            for (x in 52..58) {
                varrockSouth[y][x] = B
            }
            varrockSouth[y][52] = X
            varrockSouth[y][58] = X
        }

        // Fountain in center
        for (y in 30..34) {
            for (x in 30..34) {
                varrockSouth[y][x] = S
            }
        }

        // Roads leading out
        // South road
        for (y in 0..20) {
            for (x in 30..36) {
                varrockSouth[y][x] = P
            }
        }

        // North road
        for (y in 45..63) {
            for (x in 30..36) {
                varrockSouth[y][x] = P
            }
        }

        // East road
        for (x in 50..63) {
            for (y in 30..36) {
                varrockSouth[y][x] = P
            }
        }

        // West road
        for (x in 0..15) {
            for (y in 30..36) {
                varrockSouth[y][x] = P
            }
        }

        storeRegion(50, 53, varrockSouth)

        // Varrock Palace area (north)
        val varrockNorth = Array(64) { IntArray(64) { G } }

        // Palace grounds
        for (y in 10..55) {
            for (x in 15..50) {
                varrockNorth[y][x] = L
            }
        }

        // Palace building
        for (y in 25..45) {
            for (x in 22..42) {
                varrockNorth[y][x] = B
            }
        }

        // Palace walls
        for (y in 25..45) {
            varrockNorth[y][22] = X
            varrockNorth[y][42] = X
        }
        for (x in 22..42) {
            varrockNorth[25][x] = X
            varrockNorth[45][x] = X
        }

        // Gardens with trees
        val gardenTrees = listOf(
            16 to 30, 16 to 40, 18 to 35,
            46 to 30, 46 to 40, 48 to 35,
            25 to 15, 35 to 15, 30 to 12
        )
        for ((tx, ty) in gardenTrees) {
            varrockNorth[ty][tx] = T
        }

        // Road from south
        for (y in 0..10) {
            for (x in 30..36) {
                varrockNorth[y][x] = P
            }
        }

        storeRegion(50, 54, varrockNorth)
    }

    // =========================================================================
    // FALADOR AREA (Region 46-47, 52-53)
    // =========================================================================

    private fun loadFaladorArea() {
        // Falador is at approximately (2965, 3380)
        // Region 46, 52

        val falador = Array(64) { IntArray(64) { G } }

        // White stone city center
        for (y in 15..50) {
            for (x in 15..50) {
                falador[y][x] = R
            }
        }

        // Park area in center (green)
        for (y in 25..40) {
            for (x in 25..40) {
                falador[y][x] = L
            }
        }

        // Trees in park
        for (y in 27..38 step 4) {
            for (x in 27..38 step 4) {
                falador[y][x] = T
            }
        }

        // Castle to the north
        for (y in 50..63) {
            for (x in 25..45) {
                falador[y][x] = B
            }
        }

        // Castle walls
        for (x in 25..45) {
            falador[50][x] = X
            falador[63][x] = X
        }
        for (y in 50..63) {
            falador[y][25] = X
            falador[y][45] = X
        }

        // Mining guild entrance (south)
        for (y in 0..10) {
            for (x in 20..30) {
                falador[y][x] = K
            }
        }

        // Roads
        for (y in 0..15) {
            for (x in 30..36) {
                falador[y][x] = P
            }
        }

        for (x in 0..15) {
            for (y in 30..36) {
                falador[y][x] = P
            }
        }

        for (x in 50..63) {
            for (y in 30..36) {
                falador[y][x] = P
            }
        }

        storeRegion(46, 52, falador)
    }

    // =========================================================================
    // DRAYNOR VILLAGE (Region 48, 51)
    // =========================================================================

    private fun loadDraynorArea() {
        // Draynor is at approximately (3105, 3250)
        // Region 48, 50

        val draynor = Array(64) { IntArray(64) { G } }

        // Water on east side (ocean)
        for (y in 0..63) {
            for (x in 55..63) {
                draynor[y][x] = W
            }
        }

        // Beach
        for (y in 0..63) {
            for (x in 50..55) {
                draynor[y][x] = A
            }
        }

        // Village center
        for (y in 20..40) {
            for (x in 15..35) {
                draynor[y][x] = P
            }
        }

        // Bank building
        for (y in 25..32) {
            for (x in 30..40) {
                draynor[y][x] = B
            }
        }

        // Willow trees by water
        for (y in 10..50 step 5) {
            draynor[y][48] = T
            draynor[y][46] = T
        }

        // Dark wizard tower area (west) - darker grass
        for (y in 0..20) {
            for (x in 0..15) {
                draynor[y][x] = D
            }
        }

        // Road going north
        for (y in 40..63) {
            for (x in 20..26) {
                draynor[y][x] = P
            }
        }

        storeRegion(48, 50, draynor)

        // Draynor Manor (north)
        val draynorManor = Array(64) { IntArray(64) { D } }

        // Spooky forest
        for (y in 0..63) {
            for (x in 0..63) {
                if ((x + y) % 3 == 0) draynorManor[y][x] = T
            }
        }

        // Manor building
        for (y in 20..45) {
            for (x in 20..45) {
                draynorManor[y][x] = B
            }
        }

        // Manor walls
        for (y in 20..45) {
            draynorManor[y][20] = X
            draynorManor[y][45] = X
        }
        for (x in 20..45) {
            draynorManor[20][x] = X
            draynorManor[45][x] = X
        }

        // Entrance path
        for (y in 0..20) {
            for (x in 30..35) {
                draynorManor[y][x] = P
            }
        }

        storeRegion(48, 51, draynorManor)
    }

    // =========================================================================
    // AL KHARID (Region 51-52, 49-50)
    // =========================================================================

    private fun loadAlKharidArea() {
        // Al Kharid is at approximately (3293, 3174)
        // Region 51, 49

        val alKharid = Array(64) { IntArray(64) { A } } // Desert sand base

        // Main palace area
        for (y in 30..55) {
            for (x in 20..45) {
                alKharid[y][x] = R
            }
        }

        // Palace building
        for (y in 40..52) {
            for (x in 28..38) {
                alKharid[y][x] = B
            }
        }

        // Palace walls
        for (y in 40..52) {
            alKharid[y][28] = X
            alKharid[y][38] = X
        }
        for (x in 28..38) {
            alKharid[40][x] = X
            alKharid[52][x] = X
        }

        // Bank (east side)
        for (y in 30..38) {
            for (x in 50..58) {
                alKharid[y][x] = B
            }
        }

        // Gate to Lumbridge (west)
        for (y in 25..35) {
            for (x in 0..5) {
                alKharid[y][x] = R
            }
        }
        alKharid[30][0] = X
        alKharid[30][1] = X

        // Road through town
        for (y in 0..63) {
            for (x in 30..36) {
                if (alKharid[y][x] != B && alKharid[y][x] != X) {
                    alKharid[y][x] = P
                }
            }
        }

        // Mining area (east)
        for (y in 5..20) {
            for (x in 50..60) {
                alKharid[y][x] = K
            }
        }

        // Shantay Pass area (south)
        for (y in 0..10) {
            for (x in 25..40) {
                alKharid[y][x] = P
            }
        }

        storeRegion(51, 49, alKharid)
    }

    // =========================================================================
    // EDGEVILLE (Region 48, 54)
    // =========================================================================

    private fun loadEdgevilleArea() {
        // Edgeville is at approximately (3087, 3496)
        // Region 48, 54

        val edgeville = Array(64) { IntArray(64) { G } }

        // River on west side
        for (y in 0..63) {
            for (x in 0..8) {
                if (x < 5) edgeville[y][x] = W
                else edgeville[y][x] = S
            }
        }

        // Bank building
        for (y in 25..35) {
            for (x in 25..38) {
                edgeville[y][x] = B
            }
        }

        // Bank walls
        for (y in 25..35) {
            edgeville[y][25] = X
            edgeville[y][38] = X
        }
        for (x in 25..38) {
            edgeville[25][x] = X
            edgeville[35][x] = X
        }

        // Furnace building
        for (y in 15..22) {
            for (x in 42..52) {
                edgeville[y][x] = B
            }
        }

        // General store
        for (y in 40..48) {
            for (x in 35..45) {
                edgeville[y][x] = B
            }
        }

        // Wilderness ditch (north) - dark grass
        for (y in 55..63) {
            for (x in 0..63) {
                edgeville[y][x] = D
            }
        }

        // Ditch itself
        for (x in 0..63) {
            edgeville[58][x] = W
        }

        // Main road north-south
        for (y in 0..55) {
            for (x in 30..36) {
                if (edgeville[y][x] != B && edgeville[y][x] != X && edgeville[y][x] != W) {
                    edgeville[y][x] = P
                }
            }
        }

        // Path to monastery (east)
        for (x in 36..63) {
            for (y in 45..50) {
                edgeville[y][x] = P
            }
        }

        // Yew trees
        edgeville[40][15] = T
        edgeville[45][18] = T
        edgeville[35][12] = T

        storeRegion(48, 54, edgeville)
    }

    // =========================================================================
    // BARBARIAN VILLAGE (Region 48, 53)
    // =========================================================================

    private fun loadBarbarianVillageArea() {
        // Barbarian Village is at approximately (3079, 3420)
        // Region 48, 53

        val barbVillage = Array(64) { IntArray(64) { G } }

        // River on west
        for (y in 0..63) {
            for (x in 0..6) {
                if (x < 4) barbVillage[y][x] = W
                else barbVillage[y][x] = S
            }
        }

        // Fishing spots along river
        for (y in 20..40 step 5) {
            barbVillage[y][5] = S
        }

        // Village center with huts
        // Hut 1
        for (y in 25..32) {
            for (x in 25..32) {
                barbVillage[y][x] = B
            }
        }

        // Hut 2
        for (y in 35..42) {
            for (x in 20..27) {
                barbVillage[y][x] = B
            }
        }

        // Hut 3
        for (y in 20..28) {
            for (x in 40..48) {
                barbVillage[y][x] = B
            }
        }

        // Stronghold entrance (mines)
        for (y in 45..55) {
            for (x in 35..45) {
                barbVillage[y][x] = K
            }
        }

        // Central fire pit
        for (y in 30..34) {
            for (x in 33..37) {
                barbVillage[y][x] = P
            }
        }

        // Main road
        for (y in 0..63) {
            for (x in 50..56) {
                barbVillage[y][x] = P
            }
        }

        // Trees around edges
        for (y in 0..15) {
            for (x in 10..30) {
                if ((x + y) % 4 == 0) barbVillage[y][x] = T
            }
        }

        for (y in 50..63) {
            for (x in 55..63) {
                if ((x + y) % 3 == 0) barbVillage[y][x] = T
            }
        }

        storeRegion(48, 53, barbVillage)
    }

    /**
     * Generate procedural terrain for unknown regions
     * This provides fallback terrain when region data isn't available
     */
    fun generateProceduralTerrain(regionX: Int, regionY: Int, seed: Long = 0): Array<IntArray> {
        val terrain = Array(64) { IntArray(64) { G } }
        val random = kotlin.random.Random(seed + regionX.toLong() * 1000 + regionY)

        // Base terrain from noise
        for (y in 0..63) {
            for (x in 0..63) {
                val worldX = (regionX shl 6) + x
                val worldY = (regionY shl 6) + y

                // Simple noise function
                val noise = (kotlin.math.sin(worldX * 0.08) *
                        kotlin.math.cos(worldY * 0.08) +
                        kotlin.math.sin((worldX + worldY) * 0.04) * 0.5 +
                        random.nextDouble() * 0.3)

                terrain[y][x] = when {
                    noise < -0.4 -> W      // Water
                    noise < -0.25 -> S     // Shallow water
                    noise < -0.1 -> A      // Sand/beach
                    noise < 0.2 -> G       // Grass
                    noise < 0.4 -> L       // Light grass
                    noise < 0.6 -> D       // Dark grass
                    noise < 0.7 -> T       // Trees
                    else -> K              // Rocks
                }
            }
        }

        // Add some paths
        if (random.nextDouble() > 0.3) {
            val pathY = random.nextInt(25, 40)
            for (x in 0..63) {
                terrain[pathY][x] = P
                terrain[pathY + 1][x] = P
            }
        }

        if (random.nextDouble() > 0.3) {
            val pathX = random.nextInt(25, 40)
            for (y in 0..63) {
                terrain[y][pathX] = P
                terrain[y][pathX + 1] = P
            }
        }

        return terrain
    }

    /**
     * Get terrain for a region, using procedural generation as fallback
     */
    fun getOrGenerateTerrain(regionX: Int, regionY: Int): Array<Array<MinimapTileType>> {
        // Try to get predefined terrain first
        getRegionTerrain(regionX, regionY)?.let { return it }

        // Generate procedural terrain
        val procedural = generateProceduralTerrain(
            regionX, regionY,
            (regionX.toLong() * 31 + regionY).toLong()
        )

        return Array(64) { y ->
            Array(64) { x ->
                procedural[y][x].toTileType()
            }
        }
    }
}
