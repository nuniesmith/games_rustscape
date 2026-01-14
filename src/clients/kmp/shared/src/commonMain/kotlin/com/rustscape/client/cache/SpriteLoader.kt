package com.rustscape.client.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Represents a loaded sprite/image
 */
data class Sprite(
    val id: Int,
    val width: Int,
    val height: Int,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val pixels: IntArray // ARGB pixel data
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as Sprite
        return id == other.id && width == other.width && height == other.height
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + width
        result = 31 * result + height
        return result
    }

    companion object {
        val EMPTY = Sprite(
            id = -1,
            width = 1,
            height = 1,
            pixels = intArrayOf(0)
        )
    }
}

/**
 * Represents a sprite sheet containing multiple frames
 */
data class SpriteSheet(
    val id: Int,
    val frames: List<Sprite>
) {
    val frameCount: Int get() = frames.size

    fun getFrame(index: Int): Sprite {
        return frames.getOrElse(index) { Sprite.EMPTY }
    }

    companion object {
        val EMPTY = SpriteSheet(-1, emptyList())
    }
}

/**
 * Sprite group identifiers for common game sprites
 */
object SpriteGroup {
    // UI Elements
    const val CHAT_BUTTONS = 0
    const val TAB_ICONS = 1
    const val SCROLLBAR = 2
    const val CLOSE_BUTTON = 3

    // Map elements
    const val MINIMAP_ICONS = 4
    const val COMPASS = 5
    const val MAP_SCENE = 6
    const val MAP_FUNCTION = 7

    // Skill icons
    const val SKILL_ICONS = 8

    // Items
    const val ITEM_ICONS_BASE = 100  // Items start at 100+

    // NPCs
    const val NPC_SPRITES_BASE = 1000

    // Players
    const val PLAYER_BASE = 2000
    const val PLAYER_HEAD = 2001
    const val PLAYER_BODY = 2002
    const val PLAYER_ARMS = 2003
    const val PLAYER_HANDS = 2004
    const val PLAYER_LEGS = 2005
    const val PLAYER_FEET = 2006

    // Effects
    const val SPOTANIMS = 3000
    const val PROJECTILES = 3100
    const val HITS = 3200
    const val HEALTH_BARS = 3201

    // Objects
    const val OBJECT_SPRITES = 4000

    // Ground decorations
    const val FLOOR_UNDERLAY = 5000
    const val FLOOR_OVERLAY = 5100

    // Title screen
    const val TITLE_LOGO = 6000
    const val TITLE_BACKGROUND = 6001
}

/**
 * Cache archive types
 */
enum class CacheArchive(val id: Int) {
    TITLE(0),
    CONFIG(1),
    INTERFACE(2),
    MEDIA(3),
    SOUND(4),
    LANDSCAPE(5),
    MUSIC(6),
    MODEL(7),
    MAP(8),
    TEXTURE(9),
    SPRITE(10)
}

/**
 * Asset loading state
 */
sealed class LoadingState {
    object Idle : LoadingState()
    data class Loading(val progress: Float, val message: String) : LoadingState()
    data class Loaded(val assetCount: Int) : LoadingState()
    data class Error(val message: String, val cause: Throwable? = null) : LoadingState()
}

/**
 * Sprite loader and cache manager
 * Handles loading sprites from the game cache and caching them in memory
 */
class SpriteLoader {
    private val spriteCache = mutableMapOf<Int, Sprite>()
    private val sheetCache = mutableMapOf<Int, SpriteSheet>()
    private val mutex = Mutex()

    private var _loadingState: LoadingState = LoadingState.Idle
    val loadingState: LoadingState get() = _loadingState

    /**
     * Get a sprite by ID, returns EMPTY if not loaded
     */
    suspend fun getSprite(id: Int): Sprite = mutex.withLock {
        spriteCache[id] ?: Sprite.EMPTY
    }

    /**
     * Get a sprite sheet by ID, returns EMPTY if not loaded
     */
    suspend fun getSpriteSheet(id: Int): SpriteSheet = mutex.withLock {
        sheetCache[id] ?: SpriteSheet.EMPTY
    }

    /**
     * Check if a sprite is loaded
     */
    suspend fun isLoaded(id: Int): Boolean = mutex.withLock {
        spriteCache.containsKey(id)
    }

    /**
     * Load a sprite from raw data
     */
    suspend fun loadSprite(id: Int, data: ByteArray): Sprite = mutex.withLock {
        val sprite = decodeSprite(id, data)
        spriteCache[id] = sprite
        sprite
    }

    /**
     * Load a sprite sheet from raw data
     */
    suspend fun loadSpriteSheet(id: Int, data: ByteArray): SpriteSheet = mutex.withLock {
        val sheet = decodeSpriteSheet(id, data)
        sheetCache[id] = sheet
        // Also cache individual frames
        sheet.frames.forEachIndexed { index, sprite ->
            spriteCache[id * 1000 + index] = sprite
        }
        sheet
    }

    /**
     * Decode a single sprite from RS cache format
     * Format: width(2) + height(2) + offsetX(2) + offsetY(2) + palette_size(1) + palette + pixels
     */
    private fun decodeSprite(id: Int, data: ByteArray): Sprite {
        if (data.size < 9) {
            return Sprite.EMPTY.copy(id = id)
        }

        var offset = 0

        // Read dimensions
        val width = readUShort(data, offset)
        offset += 2
        val height = readUShort(data, offset)
        offset += 2
        val offsetX = readShort(data, offset)
        offset += 2
        val offsetY = readShort(data, offset)
        offset += 2

        if (width <= 0 || height <= 0 || width > 2048 || height > 2048) {
            return Sprite.EMPTY.copy(id = id)
        }

        // Read palette size
        val paletteSize = data[offset].toInt() and 0xFF
        offset += 1

        // Read palette (RGB values, 0 = transparent)
        val palette = IntArray(paletteSize + 1)
        palette[0] = 0 // Index 0 is always transparent
        for (i in 1..paletteSize) {
            if (offset + 3 > data.size) break
            val r = data[offset++].toInt() and 0xFF
            val g = data[offset++].toInt() and 0xFF
            val b = data[offset++].toInt() and 0xFF
            // If color is black, make it slightly visible (RS quirk)
            palette[i] = if (r == 0 && g == 0 && b == 0) {
                0xFF000001.toInt()
            } else {
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        // Read pixel indices
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            if (offset >= data.size) break
            val paletteIndex = data[offset++].toInt() and 0xFF
            pixels[i] = if (paletteIndex < palette.size) {
                palette[paletteIndex]
            } else {
                0 // Transparent
            }
        }

        return Sprite(
            id = id,
            width = width,
            height = height,
            offsetX = offsetX,
            offsetY = offsetY,
            pixels = pixels
        )
    }

    /**
     * Decode a sprite sheet (multiple frames)
     * Format: frame_count(2) + [frame_data...]
     */
    private fun decodeSpriteSheet(id: Int, data: ByteArray): SpriteSheet {
        if (data.size < 2) {
            return SpriteSheet.EMPTY.copy(id = id)
        }

        val frameCount = readUShort(data, 0)
        if (frameCount <= 0 || frameCount > 1000) {
            // Probably single sprite, not a sheet
            return SpriteSheet(id, listOf(decodeSprite(id, data)))
        }

        var offset = 2
        val frames = mutableListOf<Sprite>()

        for (i in 0 until frameCount) {
            if (offset + 4 > data.size) break

            // Read frame size
            val frameSize = readInt(data, offset)
            offset += 4

            if (frameSize <= 0 || offset + frameSize > data.size) break

            // Extract and decode frame
            val frameData = data.copyOfRange(offset, offset + frameSize)
            frames.add(decodeSprite(id * 1000 + i, frameData))
            offset += frameSize
        }

        return SpriteSheet(id, frames)
    }

    /**
     * Preload essential sprites for the UI
     */
    suspend fun preloadEssentialSprites(
        fetchData: suspend (archive: CacheArchive, id: Int) -> ByteArray?
    ) {
        _loadingState = LoadingState.Loading(0f, "Loading sprites...")

        val essentialSprites = listOf(
            SpriteGroup.CHAT_BUTTONS,
            SpriteGroup.TAB_ICONS,
            SpriteGroup.SCROLLBAR,
            SpriteGroup.CLOSE_BUTTON,
            SpriteGroup.MINIMAP_ICONS,
            SpriteGroup.COMPASS,
            SpriteGroup.SKILL_ICONS,
            SpriteGroup.HEALTH_BARS,
            SpriteGroup.HITS
        )

        var loaded = 0
        val total = essentialSprites.size

        for (spriteId in essentialSprites) {
            try {
                val data = fetchData(CacheArchive.SPRITE, spriteId)
                if (data != null) {
                    loadSprite(spriteId, data)
                }
            } catch (e: Exception) {
                // Continue loading other sprites
            }

            loaded++
            _loadingState = LoadingState.Loading(
                progress = loaded.toFloat() / total,
                message = "Loading sprites... ($loaded/$total)"
            )
        }

        _loadingState = LoadingState.Loaded(loaded)
    }

    /**
     * Clear all cached sprites
     */
    suspend fun clear() = mutex.withLock {
        spriteCache.clear()
        sheetCache.clear()
        _loadingState = LoadingState.Idle
    }

    /**
     * Get cache statistics
     */
    suspend fun getStats(): CacheStats = mutex.withLock {
        CacheStats(
            spriteCount = spriteCache.size,
            sheetCount = sheetCache.size,
            estimatedMemoryBytes = spriteCache.values.sumOf { it.pixels.size * 4L } +
                    sheetCache.values.sumOf { sheet ->
                        sheet.frames.sumOf { it.pixels.size * 4L }
                    }
        )
    }

    // Utility functions for reading binary data
    private fun readUShort(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 8) or
                (data[offset + 1].toInt() and 0xFF)
    }

    private fun readShort(data: ByteArray, offset: Int): Int {
        val value = readUShort(data, offset)
        return if (value > 32767) value - 65536 else value
    }

    private fun readInt(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
    }
}

/**
 * Cache statistics
 */
data class CacheStats(
    val spriteCount: Int,
    val sheetCount: Int,
    val estimatedMemoryBytes: Long
) {
    val estimatedMemoryMB: Float
        get() = estimatedMemoryBytes / (1024f * 1024f)
}

/**
 * Color utilities for sprite manipulation
 */
object SpriteColors {
    fun argb(a: Int, r: Int, g: Int, b: Int): Int {
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun alpha(color: Int): Int = (color shr 24) and 0xFF
    fun red(color: Int): Int = (color shr 16) and 0xFF
    fun green(color: Int): Int = (color shr 8) and 0xFF
    fun blue(color: Int): Int = color and 0xFF

    fun blend(color1: Int, color2: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        val ir = 1f - r

        val a = (alpha(color1) * ir + alpha(color2) * r).toInt()
        val red = (red(color1) * ir + red(color2) * r).toInt()
        val g = (green(color1) * ir + green(color2) * r).toInt()
        val b = (blue(color1) * ir + blue(color2) * r).toInt()

        return argb(a, red, g, b)
    }

    fun brighten(color: Int, factor: Float): Int {
        val f = 1f + factor.coerceIn(-1f, 1f)
        val a = alpha(color)
        val r = (red(color) * f).toInt().coerceIn(0, 255)
        val g = (green(color) * f).toInt().coerceIn(0, 255)
        val b = (blue(color) * f).toInt().coerceIn(0, 255)
        return argb(a, r, g, b)
    }

    fun withAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    // RS uses a specific palette for chat colors
    object ChatColors {
        const val CYAN = 0xFF00FFFF.toInt()
        const val YELLOW = 0xFFFFFF00.toInt()
        const val RED = 0xFFFF0000.toInt()
        const val GREEN = 0xFF00FF00.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
        const val BLUE = 0xFF0000FF.toInt()
        const val PURPLE = 0xFFFF00FF.toInt()
        const val ORANGE = 0xFFFF9900.toInt()
    }
}

/**
 * Sprite transformation utilities
 */
object SpriteTransform {
    /**
     * Flip sprite horizontally
     */
    fun flipHorizontal(sprite: Sprite): Sprite {
        val newPixels = IntArray(sprite.pixels.size)
        for (y in 0 until sprite.height) {
            for (x in 0 until sprite.width) {
                val srcIndex = y * sprite.width + x
                val dstIndex = y * sprite.width + (sprite.width - 1 - x)
                newPixels[dstIndex] = sprite.pixels[srcIndex]
            }
        }
        return sprite.copy(
            pixels = newPixels,
            offsetX = -sprite.offsetX
        )
    }

    /**
     * Flip sprite vertically
     */
    fun flipVertical(sprite: Sprite): Sprite {
        val newPixels = IntArray(sprite.pixels.size)
        for (y in 0 until sprite.height) {
            for (x in 0 until sprite.width) {
                val srcIndex = y * sprite.width + x
                val dstIndex = (sprite.height - 1 - y) * sprite.width + x
                newPixels[dstIndex] = sprite.pixels[srcIndex]
            }
        }
        return sprite.copy(
            pixels = newPixels,
            offsetY = -sprite.offsetY
        )
    }

    /**
     * Rotate sprite 90 degrees clockwise
     */
    fun rotate90(sprite: Sprite): Sprite {
        val newPixels = IntArray(sprite.pixels.size)
        for (y in 0 until sprite.height) {
            for (x in 0 until sprite.width) {
                val srcIndex = y * sprite.width + x
                val newX = sprite.height - 1 - y
                val newY = x
                val dstIndex = newY * sprite.height + newX
                if (dstIndex < newPixels.size) {
                    newPixels[dstIndex] = sprite.pixels[srcIndex]
                }
            }
        }
        return sprite.copy(
            width = sprite.height,
            height = sprite.width,
            pixels = newPixels
        )
    }

    /**
     * Scale sprite by integer factor
     */
    fun scale(sprite: Sprite, factor: Int): Sprite {
        if (factor <= 0) return sprite
        if (factor == 1) return sprite

        val newWidth = sprite.width * factor
        val newHeight = sprite.height * factor
        val newPixels = IntArray(newWidth * newHeight)

        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                val srcX = x / factor
                val srcY = y / factor
                val srcIndex = srcY * sprite.width + srcX
                val dstIndex = y * newWidth + x
                if (srcIndex < sprite.pixels.size) {
                    newPixels[dstIndex] = sprite.pixels[srcIndex]
                }
            }
        }

        return sprite.copy(
            width = newWidth,
            height = newHeight,
            offsetX = sprite.offsetX * factor,
            offsetY = sprite.offsetY * factor,
            pixels = newPixels
        )
    }

    /**
     * Apply color tint to sprite
     */
    fun tint(sprite: Sprite, tintColor: Int): Sprite {
        val tintR = SpriteColors.red(tintColor) / 255f
        val tintG = SpriteColors.green(tintColor) / 255f
        val tintB = SpriteColors.blue(tintColor) / 255f

        val newPixels = IntArray(sprite.pixels.size)
        for (i in sprite.pixels.indices) {
            val pixel = sprite.pixels[i]
            val a = SpriteColors.alpha(pixel)
            if (a == 0) {
                newPixels[i] = 0
                continue
            }

            val r = (SpriteColors.red(pixel) * tintR).toInt().coerceIn(0, 255)
            val g = (SpriteColors.green(pixel) * tintG).toInt().coerceIn(0, 255)
            val b = (SpriteColors.blue(pixel) * tintB).toInt().coerceIn(0, 255)
            newPixels[i] = SpriteColors.argb(a, r, g, b)
        }

        return sprite.copy(pixels = newPixels)
    }
}
