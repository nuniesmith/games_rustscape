package com.rustscape.client.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Represents a single tile in the game world
 */
data class MapTile(
    val height: Int = 0,
    val overlayId: Int = 0,
    val overlayShape: Int = 0,
    val overlayRotation: Int = 0,
    val underlayId: Int = 0,
    val flags: Int = 0
) {
    val isBlocked: Boolean get() = (flags and FLAG_BLOCKED) != 0
    val isBridge: Boolean get() = (flags and FLAG_BRIDGE) != 0
    val hasOverlay: Boolean get() = overlayId > 0
    val hasUnderlay: Boolean get() = underlayId > 0

    companion object {
        const val FLAG_BLOCKED = 0x1
        const val FLAG_BRIDGE = 0x2
        const val FLAG_FORCE_LOWEST_PLANE = 0x4
        const val FLAG_ROOF = 0x8

        val EMPTY = MapTile()
    }
}

/**
 * Represents an object placed in the world
 */
data class MapObject(
    val id: Int,
    val localX: Int,
    val localY: Int,
    val plane: Int,
    val type: Int,
    val rotation: Int
) {
    // Object types
    val isWall: Boolean get() = type in 0..3
    val isWallDecoration: Boolean get() = type in 4..8
    val isScenery: Boolean get() = type in 9..11
    val isGroundDecoration: Boolean get() = type == 22
    val isRoof: Boolean get() = type in 12..21

    companion object {
        // Object type constants
        const val TYPE_WALL_STRAIGHT = 0
        const val TYPE_WALL_DIAGONAL_CORNER = 1
        const val TYPE_WALL_CORNER = 2
        const val TYPE_WALL_SQUARE_CORNER = 3
        const val TYPE_WALL_DECORATION_STRAIGHT_OFFSET = 4
        const val TYPE_WALL_DECORATION_STRAIGHT_INSIDE = 5
        const val TYPE_WALL_DECORATION_STRAIGHT = 6
        const val TYPE_WALL_DECORATION_DIAGONAL_OFFSET = 7
        const val TYPE_WALL_DECORATION_DIAGONAL_INSIDE = 8
        const val TYPE_SCENERY_DIAGONAL = 9
        const val TYPE_SCENERY = 10
        const val TYPE_SCENERY_DIAGONAL_ROTATABLE = 11
        const val TYPE_GROUND_DECORATION = 22
    }
}

/**
 * Represents a loaded map region (64x64 tiles)
 */
class MapRegion(
    val regionX: Int,
    val regionY: Int
) {
    // 4 planes, 64x64 tiles each
    private val tiles = Array(4) { Array(64) { Array(64) { MapTile.EMPTY } } }
    private val objects = mutableListOf<MapObject>()

    val baseX: Int get() = regionX shl 6
    val baseY: Int get() = regionY shl 6

    /**
     * Get a tile at local coordinates
     */
    fun getTile(plane: Int, localX: Int, localY: Int): MapTile {
        if (plane !in 0..3 || localX !in 0..63 || localY !in 0..63) {
            return MapTile.EMPTY
        }
        return tiles[plane][localX][localY]
    }

    /**
     * Set a tile at local coordinates
     */
    fun setTile(plane: Int, localX: Int, localY: Int, tile: MapTile) {
        if (plane in 0..3 && localX in 0..63 && localY in 0..63) {
            tiles[plane][localX][localY] = tile
        }
    }

    /**
     * Add an object to this region
     */
    fun addObject(obj: MapObject) {
        objects.add(obj)
    }

    /**
     * Get all objects in this region
     */
    fun getObjects(): List<MapObject> = objects.toList()

    /**
     * Get objects at a specific location
     */
    fun getObjectsAt(plane: Int, localX: Int, localY: Int): List<MapObject> {
        return objects.filter {
            it.plane == plane && it.localX == localX && it.localY == localY
        }
    }

    /**
     * Clear all data
     */
    fun clear() {
        for (plane in 0..3) {
            for (x in 0..63) {
                for (y in 0..63) {
                    tiles[plane][x][y] = MapTile.EMPTY
                }
            }
        }
        objects.clear()
    }

    companion object {
        const val SIZE = 64
    }
}

/**
 * Collision flag constants
 */
object CollisionFlag {
    const val OPEN = 0x0
    const val WALL_NORTH = 0x1
    const val WALL_EAST = 0x2
    const val WALL_SOUTH = 0x4
    const val WALL_WEST = 0x8
    const val BLOCKED = 0x100
    const val BLOCKED_NORTH = 0x200
    const val BLOCKED_EAST = 0x400
    const val BLOCKED_SOUTH = 0x800
    const val BLOCKED_WEST = 0x1000
    const val FLOOR = 0x200000
    const val FLOOR_DECORATION = 0x40000

    const val WALL_NORTHWEST = WALL_NORTH or WALL_WEST
    const val WALL_NORTHEAST = WALL_NORTH or WALL_EAST
    const val WALL_SOUTHWEST = WALL_SOUTH or WALL_WEST
    const val WALL_SOUTHEAST = WALL_SOUTH or WALL_EAST

    const val BLOCK_MOVEMENT = BLOCKED or FLOOR or FLOOR_DECORATION
    const val BLOCK_NORTH = WALL_NORTH or BLOCKED_NORTH
    const val BLOCK_EAST = WALL_EAST or BLOCKED_EAST
    const val BLOCK_SOUTH = WALL_SOUTH or BLOCKED_SOUTH
    const val BLOCK_WEST = WALL_WEST or BLOCKED_WEST
}

/**
 * Collision map for pathfinding
 */
class CollisionMap(
    val regionX: Int,
    val regionY: Int
) {
    // 4 planes, 64x64 flags each
    private val flags = Array(4) { IntArray(64 * 64) }

    /**
     * Get collision flags at a position
     */
    fun getFlag(plane: Int, localX: Int, localY: Int): Int {
        if (plane !in 0..3 || localX !in 0..63 || localY !in 0..63) {
            return CollisionFlag.BLOCKED
        }
        return flags[plane][localX + localY * 64]
    }

    /**
     * Set collision flags at a position
     */
    fun setFlag(plane: Int, localX: Int, localY: Int, flag: Int) {
        if (plane in 0..3 && localX in 0..63 && localY in 0..63) {
            flags[plane][localX + localY * 64] = flag
        }
    }

    /**
     * Add flags to a position
     */
    fun addFlag(plane: Int, localX: Int, localY: Int, flag: Int) {
        if (plane in 0..3 && localX in 0..63 && localY in 0..63) {
            flags[plane][localX + localY * 64] = flags[plane][localX + localY * 64] or flag
        }
    }

    /**
     * Remove flags from a position
     */
    fun removeFlag(plane: Int, localX: Int, localY: Int, flag: Int) {
        if (plane in 0..3 && localX in 0..63 && localY in 0..63) {
            flags[plane][localX + localY * 64] = flags[plane][localX + localY * 64] and flag.inv()
        }
    }

    /**
     * Check if a position is walkable
     */
    fun canMove(plane: Int, srcX: Int, srcY: Int, dstX: Int, dstY: Int): Boolean {
        val dx = dstX - srcX
        val dy = dstY - srcY

        // Can only move one tile at a time
        if (kotlin.math.abs(dx) > 1 || kotlin.math.abs(dy) > 1) {
            return false
        }

        // Check diagonal movement
        if (dx != 0 && dy != 0) {
            // For diagonal, check both cardinal directions
            return canMoveCardinal(plane, srcX, srcY, dx, 0) &&
                    canMoveCardinal(plane, srcX, srcY, 0, dy) &&
                    canMoveCardinal(plane, srcX + dx, srcY, 0, dy) &&
                    canMoveCardinal(plane, srcX, srcY + dy, dx, 0)
        }

        return canMoveCardinal(plane, srcX, srcY, dx, dy)
    }

    private fun canMoveCardinal(plane: Int, x: Int, y: Int, dx: Int, dy: Int): Boolean {
        val destFlag = getFlag(plane, x + dx, y + dy)

        // Check if destination is blocked
        if (destFlag and CollisionFlag.BLOCK_MOVEMENT != 0) {
            return false
        }

        // Check wall flags
        when {
            dx == 1 -> {
                if (getFlag(plane, x, y) and CollisionFlag.WALL_EAST != 0) return false
                if (destFlag and CollisionFlag.WALL_WEST != 0) return false
            }

            dx == -1 -> {
                if (getFlag(plane, x, y) and CollisionFlag.WALL_WEST != 0) return false
                if (destFlag and CollisionFlag.WALL_EAST != 0) return false
            }

            dy == 1 -> {
                if (getFlag(plane, x, y) and CollisionFlag.WALL_NORTH != 0) return false
                if (destFlag and CollisionFlag.WALL_SOUTH != 0) return false
            }

            dy == -1 -> {
                if (getFlag(plane, x, y) and CollisionFlag.WALL_SOUTH != 0) return false
                if (destFlag and CollisionFlag.WALL_NORTH != 0) return false
            }
        }

        return true
    }

    /**
     * Clear all collision flags
     */
    fun clear() {
        for (plane in 0..3) {
            flags[plane].fill(0)
        }
    }
}

/**
 * Map loader for loading and caching map regions
 */
class MapLoader {
    private val regionCache = mutableMapOf<Int, MapRegion>()
    private val collisionCache = mutableMapOf<Int, CollisionMap>()
    private val mutex = Mutex()

    private var _loadingState: LoadingState = LoadingState.Idle
    val loadingState: LoadingState get() = _loadingState

    /**
     * Get or create a region
     */
    suspend fun getRegion(regionX: Int, regionY: Int): MapRegion = mutex.withLock {
        val key = (regionX shl 16) or regionY
        regionCache.getOrPut(key) { MapRegion(regionX, regionY) }
    }

    /**
     * Get or create a collision map
     */
    suspend fun getCollisionMap(regionX: Int, regionY: Int): CollisionMap = mutex.withLock {
        val key = (regionX shl 16) or regionY
        collisionCache.getOrPut(key) { CollisionMap(regionX, regionY) }
    }

    /**
     * Check if a region is loaded
     */
    suspend fun isRegionLoaded(regionX: Int, regionY: Int): Boolean = mutex.withLock {
        val key = (regionX shl 16) or regionY
        regionCache.containsKey(key)
    }

    /**
     * Load a map region from raw data
     * @param regionX Region X coordinate
     * @param regionY Region Y coordinate
     * @param tileData Tile data (heights, overlays, underlays)
     * @param objectData Object location data
     */
    suspend fun loadRegion(
        regionX: Int,
        regionY: Int,
        tileData: ByteArray?,
        objectData: ByteArray?
    ): MapRegion = mutex.withLock {
        val key = (regionX shl 16) or regionY
        val region = MapRegion(regionX, regionY)
        val collision = CollisionMap(regionX, regionY)

        // Decode tile data
        if (tileData != null && tileData.isNotEmpty()) {
            decodeTileData(region, collision, tileData)
        }

        // Decode object data
        if (objectData != null && objectData.isNotEmpty()) {
            decodeObjectData(region, collision, objectData)
        }

        regionCache[key] = region
        collisionCache[key] = collision

        region
    }

    /**
     * Decode tile data from RS cache format
     */
    private fun decodeTileData(region: MapRegion, collision: CollisionMap, data: ByteArray) {
        var offset = 0

        for (plane in 0..3) {
            for (x in 0..63) {
                for (y in 0..63) {
                    if (offset >= data.size) return

                    var height = 0
                    var overlayId = 0
                    var overlayShape = 0
                    var overlayRotation = 0
                    var underlayId = 0
                    var flags = 0

                    while (true) {
                        if (offset >= data.size) break

                        val opcode = data[offset++].toInt() and 0xFF

                        if (opcode == 0) {
                            // Calculate height from surrounding tiles
                            break
                        } else if (opcode == 1) {
                            // Height specified
                            if (offset < data.size) {
                                height = data[offset++].toInt() and 0xFF
                            }
                            break
                        } else if (opcode <= 49) {
                            // Overlay
                            if (offset < data.size) {
                                overlayId = data[offset++].toInt() and 0xFF
                            }
                            overlayShape = (opcode - 2) / 4
                            overlayRotation = (opcode - 2) and 3
                        } else if (opcode <= 81) {
                            // Flags
                            flags = opcode - 49
                        } else {
                            // Underlay
                            underlayId = opcode - 81
                        }
                    }

                    val tile = MapTile(
                        height = height,
                        overlayId = overlayId,
                        overlayShape = overlayShape,
                        overlayRotation = overlayRotation,
                        underlayId = underlayId,
                        flags = flags
                    )

                    region.setTile(plane, x, y, tile)

                    // Set collision flags
                    if (tile.isBlocked) {
                        collision.addFlag(plane, x, y, CollisionFlag.BLOCKED)
                    }
                }
            }
        }
    }

    /**
     * Decode object data from RS cache format
     */
    private fun decodeObjectData(region: MapRegion, collision: CollisionMap, data: ByteArray) {
        var offset = 0
        var objectId = -1

        while (offset < data.size) {
            // Read object ID delta
            val idDelta = readVarInt(data, offset)
            offset += varIntSize(idDelta)

            if (idDelta == 0) break

            objectId += idDelta

            var locationHash = 0
            while (offset < data.size) {
                // Read location delta
                val locationDelta = readVarInt(data, offset)
                offset += varIntSize(locationDelta)

                if (locationDelta == 0) break

                locationHash += locationDelta - 1

                val localX = (locationHash shr 6) and 63
                val localY = locationHash and 63
                val plane = (locationHash shr 12) and 3

                // Read attributes
                if (offset >= data.size) break
                val attributes = data[offset++].toInt() and 0xFF

                val type = attributes shr 2
                val rotation = attributes and 3

                val mapObject = MapObject(
                    id = objectId,
                    localX = localX,
                    localY = localY,
                    plane = plane,
                    type = type,
                    rotation = rotation
                )

                region.addObject(mapObject)

                // Add collision for objects
                addObjectCollision(collision, mapObject)
            }
        }
    }

    /**
     * Add collision flags for an object
     */
    private fun addObjectCollision(collision: CollisionMap, obj: MapObject) {
        // Simplified collision - in a full implementation, you'd look up
        // the object definition to get its size and solid flag
        when {
            obj.isWall -> {
                // Add wall collision based on rotation
                val flag = when (obj.rotation) {
                    0 -> CollisionFlag.WALL_WEST
                    1 -> CollisionFlag.WALL_NORTH
                    2 -> CollisionFlag.WALL_EAST
                    3 -> CollisionFlag.WALL_SOUTH
                    else -> 0
                }
                collision.addFlag(obj.plane, obj.localX, obj.localY, flag)
            }

            obj.isScenery -> {
                // Most scenery blocks the tile
                collision.addFlag(obj.plane, obj.localX, obj.localY, CollisionFlag.BLOCKED)
            }

            obj.isGroundDecoration -> {
                // Ground decorations usually don't block
                collision.addFlag(obj.plane, obj.localX, obj.localY, CollisionFlag.FLOOR_DECORATION)
            }
        }
    }

    /**
     * Unload a region
     */
    suspend fun unloadRegion(regionX: Int, regionY: Int) = mutex.withLock {
        val key = (regionX shl 16) or regionY
        regionCache.remove(key)
        collisionCache.remove(key)
    }

    /**
     * Clear all loaded regions
     */
    suspend fun clear() = mutex.withLock {
        regionCache.clear()
        collisionCache.clear()
        _loadingState = LoadingState.Idle
    }

    /**
     * Get number of loaded regions
     */
    suspend fun getLoadedRegionCount(): Int = mutex.withLock {
        regionCache.size
    }

    // Variable-length integer reading utilities
    private fun readVarInt(data: ByteArray, offset: Int): Int {
        var value = 0
        var shift = 0
        var pos = offset

        while (pos < data.size) {
            val b = data[pos++].toInt() and 0xFF
            value = value or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
        }

        return value
    }

    private fun varIntSize(value: Int): Int {
        var v = value
        var size = 1
        while (v >= 0x80) {
            v = v shr 7
            size++
        }
        return size
    }
}

/**
 * Overlay definition for map rendering
 */
data class OverlayDefinition(
    val id: Int,
    val color: Int,
    val textureId: Int = -1,
    val hideUnderlay: Boolean = false,
    val secondaryColor: Int = 0
)

/**
 * Underlay definition for map rendering
 */
data class UnderlayDefinition(
    val id: Int,
    val color: Int
)

/**
 * Stores overlay/underlay definitions
 */
class FloorDefinitions {
    private val overlays = mutableMapOf<Int, OverlayDefinition>()
    private val underlays = mutableMapOf<Int, UnderlayDefinition>()

    fun getOverlay(id: Int): OverlayDefinition? = overlays[id]
    fun getUnderlay(id: Int): UnderlayDefinition? = underlays[id]

    fun addOverlay(definition: OverlayDefinition) {
        overlays[definition.id] = definition
    }

    fun addUnderlay(definition: UnderlayDefinition) {
        underlays[definition.id] = definition
    }

    fun clear() {
        overlays.clear()
        underlays.clear()
    }

    companion object {
        // Default colors for common floor types
        val DEFAULT_GRASS = UnderlayDefinition(1, 0xFF228B22.toInt())
        val DEFAULT_DIRT = UnderlayDefinition(2, 0xFF8B4513.toInt())
        val DEFAULT_SAND = UnderlayDefinition(3, 0xFFD2B48C.toInt())
        val DEFAULT_STONE = UnderlayDefinition(4, 0xFF696969.toInt())
        val DEFAULT_WATER = OverlayDefinition(1, 0xFF1E90FF.toInt())
        val DEFAULT_ROAD = OverlayDefinition(2, 0xFF8B8378.toInt())
        val DEFAULT_WOOD = OverlayDefinition(3, 0xFF8B4513.toInt())
    }
}
