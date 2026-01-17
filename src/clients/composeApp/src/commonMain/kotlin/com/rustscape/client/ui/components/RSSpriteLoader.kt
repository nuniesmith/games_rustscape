package com.rustscape.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rustscape.client.game.SkillType

/**
 * RuneScape 530 Sprite ID mappings
 * These IDs correspond to the extracted sprite files in assets/rendering/sprites/
 */
object SpriteIds {
    // Skill Icons (197-221)
    object Skills {
        const val ATTACK = 197
        const val STRENGTH = 198
        const val DEFENCE = 199
        const val RANGED = 200
        const val PRAYER = 201
        const val MAGIC = 202
        const val HITPOINTS = 203
        const val AGILITY = 204
        const val HERBLORE = 205
        const val THIEVING = 206
        const val CRAFTING = 207
        const val FLETCHING = 208
        const val SLAYER = 209
        const val HUNTER = 210
        const val MINING = 211
        const val SMITHING = 212
        const val FISHING = 213
        const val COOKING = 214
        const val FIREMAKING = 215
        const val WOODCUTTING = 216
        const val RUNECRAFT = 217
        const val DUNGEONEERING = 218
        const val FARMING = 219
        const val SUMMONING = 220
        const val CONSTRUCTION = 221

        /**
         * Get sprite ID for a skill by its index (0-24)
         */
        fun forIndex(index: Int): Int = when (index) {
            0 -> ATTACK
            1 -> DEFENCE
            2 -> STRENGTH
            3 -> HITPOINTS
            4 -> RANGED
            5 -> PRAYER
            6 -> MAGIC
            7 -> COOKING
            8 -> WOODCUTTING
            9 -> FLETCHING
            10 -> FISHING
            11 -> FIREMAKING
            12 -> CRAFTING
            13 -> SMITHING
            14 -> MINING
            15 -> HERBLORE
            16 -> AGILITY
            17 -> THIEVING
            18 -> SLAYER
            19 -> FARMING
            20 -> RUNECRAFT
            21 -> HUNTER
            22 -> CONSTRUCTION
            23 -> SUMMONING
            24 -> DUNGEONEERING
            else -> ATTACK
        }

        val ALL = listOf(
            ATTACK, STRENGTH, DEFENCE, RANGED, PRAYER, MAGIC, HITPOINTS,
            AGILITY, HERBLORE, THIEVING, CRAFTING, FLETCHING, SLAYER,
            HUNTER, MINING, SMITHING, FISHING, COOKING, FIREMAKING,
            WOODCUTTING, RUNECRAFT, DUNGEONEERING, FARMING, SUMMONING, CONSTRUCTION
        )
    }

    // Cursor sprites
    object Cursors {
        const val DEFAULT = 0
        const val CROSS = 2
        const val ATTACK = 3
        const val TALK = 4
        const val TAKE = 5
        const val USE = 6
    }

    // Tab icons (sidebar panel icons)
    object Tabs {
        const val COMBAT = 222
        const val SKILLS = 223
        const val QUESTS = 224
        const val INVENTORY = 225
        const val EQUIPMENT = 226
        const val PRAYER = 227
        const val MAGIC = 228
        const val SUMMONING = 229
        const val FRIENDS = 230
        const val IGNORE = 231
        const val CLAN = 232
        const val SETTINGS = 233
        const val EMOTES = 234
        const val MUSIC = 235
        const val LOGOUT = 236
    }

    // Minimap elements
    object Minimap {
        const val COMPASS = 237
        const val FRAME = 238
        const val ORB_HP = 239
        const val ORB_PRAYER = 240
        const val ORB_RUN = 241
        const val ORB_SPECIAL = 242
    }

    // Chat and interface icons
    object UI {
        const val CHAT_PUBLIC = 243
        const val CHAT_PRIVATE = 244
        const val CHAT_CLAN = 245
        const val CHAT_TRADE = 246
        const val CHAT_ASSIST = 247
        const val REPORT_BUTTON = 248
        const val WORLD_MAP = 249
        const val CLOSE_BUTTON = 250
        const val SCROLL_UP = 251
        const val SCROLL_DOWN = 252
    }

    // Prayer icons (Book of prayers)
    object Prayers {
        const val THICK_SKIN = 253
        const val BURST_OF_STRENGTH = 254
        const val CLARITY_OF_THOUGHT = 255
        const val SHARP_EYE = 256
        const val MYSTIC_WILL = 257
        const val ROCK_SKIN = 258
        const val SUPERHUMAN_STRENGTH = 259
        const val IMPROVED_REFLEXES = 260
        // ... more prayer icons follow
    }

    // Equipment slot backgrounds
    object EquipmentSlots {
        const val HEAD = 300
        const val CAPE = 301
        const val AMULET = 302
        const val WEAPON = 303
        const val BODY = 304
        const val SHIELD = 305
        const val LEGS = 306
        const val GLOVES = 307
        const val BOOTS = 308
        const val RING = 309
        const val AMMO = 310
    }

    // Combat style icons
    object CombatStyles {
        const val ACCURATE = 320
        const val AGGRESSIVE = 321
        const val CONTROLLED = 322
        const val DEFENSIVE = 323
        const val RAPID = 324
        const val LONGRANGE = 325
    }
}

/**
 * Sprite cache that stores loaded ImageBitmaps by sprite ID
 */
object SpriteCache {
    private val sprites = mutableMapOf<Int, ImageBitmap>()
    private val loadingStates = mutableMapOf<Int, LoadingState>()
    private val loadCallbacks = mutableMapOf<Int, MutableList<(ImageBitmap?) -> Unit>>()

    enum class LoadingState {
        NOT_STARTED,
        LOADING,
        LOADED,
        FAILED
    }

    /**
     * Get a cached sprite or null if not loaded
     */
    fun get(spriteId: Int): ImageBitmap? = sprites[spriteId]

    /**
     * Check if a sprite is cached
     */
    fun has(spriteId: Int): Boolean = sprites.containsKey(spriteId)

    /**
     * Get the loading state of a sprite
     */
    fun getState(spriteId: Int): LoadingState = loadingStates[spriteId] ?: LoadingState.NOT_STARTED

    /**
     * Cache a loaded sprite
     */
    fun put(spriteId: Int, bitmap: ImageBitmap) {
        sprites[spriteId] = bitmap
        loadingStates[spriteId] = LoadingState.LOADED
        // Notify any waiting callbacks
        loadCallbacks[spriteId]?.forEach { it(bitmap) }
        loadCallbacks.remove(spriteId)
    }

    /**
     * Mark a sprite as loading
     */
    fun markLoading(spriteId: Int) {
        loadingStates[spriteId] = LoadingState.LOADING
    }

    /**
     * Mark a sprite as failed to load
     */
    fun markFailed(spriteId: Int) {
        loadingStates[spriteId] = LoadingState.FAILED
        loadCallbacks[spriteId]?.forEach { it(null) }
        loadCallbacks.remove(spriteId)
    }

    /**
     * Register a callback for when a sprite loads
     */
    fun onLoaded(spriteId: Int, callback: (ImageBitmap?) -> Unit) {
        when (getState(spriteId)) {
            LoadingState.LOADED -> callback(sprites[spriteId])
            LoadingState.FAILED -> callback(null)
            else -> loadCallbacks.getOrPut(spriteId) { mutableListOf() }.add(callback)
        }
    }

    /**
     * Clear all cached sprites
     */
    fun clear() {
        sprites.clear()
        loadingStates.clear()
        loadCallbacks.clear()
    }

    /**
     * Get all loaded sprite IDs
     */
    fun getLoadedIds(): Set<Int> = sprites.keys.toSet()

    /**
     * Get cache statistics
     */
    fun getStats(): CacheStats {
        return CacheStats(
            loadedCount = sprites.size,
            loadingCount = loadingStates.count { it.value == LoadingState.LOADING },
            failedCount = loadingStates.count { it.value == LoadingState.FAILED }
        )
    }

    data class CacheStats(
        val loadedCount: Int,
        val loadingCount: Int,
        val failedCount: Int
    )
}

/**
 * Sprite loader interface - platform implementations will differ
 */
interface SpriteLoader {
    /**
     * Base URL/path for sprites
     */
    val basePath: String

    /**
     * Load a sprite by ID
     * Returns the loaded ImageBitmap or null if failed
     */
    suspend fun loadSprite(spriteId: Int): ImageBitmap?

    /**
     * Load multiple sprites
     */
    suspend fun loadSprites(spriteIds: List<Int>): Map<Int, ImageBitmap?>

    /**
     * Preload commonly used sprites
     */
    suspend fun preloadCommon()

    /**
     * Load a sprite from an external URL
     * Used for loading item sprites from external sources like OSRS Wiki
     * Returns the loaded ImageBitmap or null if failed
     */
    suspend fun loadFromUrl(url: String): ImageBitmap? {
        // Default implementation returns null - platforms can override
        return null
    }

    /**
     * Get the URL/path for a sprite
     */
    fun getSpriteUrl(spriteId: Int): String {
        return "$basePath/$spriteId.png"
    }

    /**
     * Get the URL/path for a multi-frame sprite
     */
    fun getFrameSpriteUrl(spriteId: Int, frame: Int): String {
        return "$basePath/${spriteId}_$frame.png"
    }
}

/**
 * CompositionLocal for providing the sprite loader throughout the app
 */
val LocalSpriteLoader = staticCompositionLocalOf<SpriteLoader?> { null }

/**
 * Composable that loads and displays a sprite by ID
 * Falls back to the fallback composable while loading or on error
 */
@Composable
fun rememberSprite(
    spriteId: Int,
    loader: SpriteLoader? = LocalSpriteLoader.current
): ImageBitmap? {
    var bitmap by remember(spriteId) { mutableStateOf(SpriteCache.get(spriteId)) }

    LaunchedEffect(spriteId, loader) {
        if (bitmap != null) return@LaunchedEffect
        if (loader == null) return@LaunchedEffect

        val state = SpriteCache.getState(spriteId)
        when (state) {
            SpriteCache.LoadingState.LOADED -> {
                bitmap = SpriteCache.get(spriteId)
            }

            SpriteCache.LoadingState.LOADING -> {
                SpriteCache.onLoaded(spriteId) { loadedBitmap ->
                    bitmap = loadedBitmap
                }
            }

            SpriteCache.LoadingState.FAILED -> {
                // Already failed, don't retry
            }

            SpriteCache.LoadingState.NOT_STARTED -> {
                SpriteCache.markLoading(spriteId)
                try {
                    val loaded = loader.loadSprite(spriteId)
                    if (loaded != null) {
                        SpriteCache.put(spriteId, loaded)
                        bitmap = loaded
                    } else {
                        SpriteCache.markFailed(spriteId)
                    }
                } catch (e: Exception) {
                    println("[SpriteLoader] Failed to load sprite $spriteId: ${e.message}")
                    SpriteCache.markFailed(spriteId)
                }
            }
        }
    }

    return bitmap
}

/**
 * Preload a batch of sprites in the background
 */
@Composable
fun PreloadSprites(
    spriteIds: List<Int>,
    loader: SpriteLoader? = LocalSpriteLoader.current
) {
    LaunchedEffect(spriteIds, loader) {
        if (loader == null) return@LaunchedEffect

        spriteIds.forEach { spriteId ->
            if (!SpriteCache.has(spriteId) && SpriteCache.getState(spriteId) == SpriteCache.LoadingState.NOT_STARTED) {
                SpriteCache.markLoading(spriteId)
                try {
                    val loaded = loader.loadSprite(spriteId)
                    if (loaded != null) {
                        SpriteCache.put(spriteId, loaded)
                    } else {
                        SpriteCache.markFailed(spriteId)
                    }
                } catch (e: Exception) {
                    SpriteCache.markFailed(spriteId)
                }
            }
        }
    }
}

/**
 * Skill ID to sprite ID mapping helper
 */
fun getSkillSpriteId(skillId: Int): Int = SpriteIds.Skills.forIndex(skillId)

/**
 * Get sprite ID for an equipment slot
 */
fun getEquipmentSlotSpriteId(slot: EquipmentSlot): Int = when (slot) {
    EquipmentSlot.HEAD -> SpriteIds.EquipmentSlots.HEAD
    EquipmentSlot.CAPE -> SpriteIds.EquipmentSlots.CAPE
    EquipmentSlot.AMULET -> SpriteIds.EquipmentSlots.AMULET
    EquipmentSlot.WEAPON -> SpriteIds.EquipmentSlots.WEAPON
    EquipmentSlot.BODY -> SpriteIds.EquipmentSlots.BODY
    EquipmentSlot.SHIELD -> SpriteIds.EquipmentSlots.SHIELD
    EquipmentSlot.LEGS -> SpriteIds.EquipmentSlots.LEGS
    EquipmentSlot.GLOVES -> SpriteIds.EquipmentSlots.GLOVES
    EquipmentSlot.BOOTS -> SpriteIds.EquipmentSlots.BOOTS
    EquipmentSlot.RING -> SpriteIds.EquipmentSlots.RING
    EquipmentSlot.AMMO -> SpriteIds.EquipmentSlots.AMMO
}

/**
 * Default list of sprites to preload for the game UI
 */
val PRELOAD_GAME_SPRITES = listOf(
    // All skill icons
    *SpriteIds.Skills.ALL.toTypedArray(),
    // Tab icons
    SpriteIds.Tabs.COMBAT,
    SpriteIds.Tabs.SKILLS,
    SpriteIds.Tabs.QUESTS,
    SpriteIds.Tabs.INVENTORY,
    SpriteIds.Tabs.EQUIPMENT,
    SpriteIds.Tabs.PRAYER,
    SpriteIds.Tabs.MAGIC,
    SpriteIds.Tabs.FRIENDS,
    SpriteIds.Tabs.SETTINGS,
    SpriteIds.Tabs.LOGOUT,
    // Minimap
    SpriteIds.Minimap.COMPASS,
    SpriteIds.Minimap.ORB_HP,
    SpriteIds.Minimap.ORB_PRAYER,
    SpriteIds.Minimap.ORB_RUN,
    // UI
    SpriteIds.UI.CLOSE_BUTTON,
    SpriteIds.UI.SCROLL_UP,
    SpriteIds.UI.SCROLL_DOWN
)

/**
 * Composable that renders a sprite from the cache
 * Falls back to a placeholder or custom fallback content if not loaded
 */
@Composable
fun RSSprite(
    spriteId: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    fallback: (@Composable () -> Unit)? = null
) {
    val bitmap = rememberSprite(spriteId)

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier
        )
    } else {
        // Show fallback or placeholder
        fallback?.invoke() ?: SpritePlaceholder(modifier)
    }
}

/**
 * Placeholder shown while sprite is loading or if load fails
 */
@Composable
private fun SpritePlaceholder(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        // Draw a simple placeholder box
        drawRect(
            color = Color(0xFF3D3024),
            topLeft = Offset.Zero,
            size = size
        )
        drawRect(
            color = Color(0xFF5C4A36),
            topLeft = Offset(2f, 2f),
            size = Size(size.width - 4f, size.height - 4f),
            style = Stroke(1f)
        )
    }
}

/**
 * Skill icon that uses real sprite with procedural fallback
 */
@Composable
fun RSSkillSpriteReal(
    skillIndex: Int,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    fallback: (@Composable () -> Unit)? = null
) {
    val spriteId = SpriteIds.Skills.forIndex(skillIndex)
    val bitmap = rememberSprite(spriteId)

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Skill icon",
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit
        )
    } else {
        // Use procedural fallback
        fallback?.invoke() ?: SpritePlaceholder(modifier.size(size))
    }
}

/**
 * Generic sprite with size parameter
 */
@Composable
fun RSSpriteWithSize(
    spriteId: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    fallback: (@Composable () -> Unit)? = null
) {
    RSSprite(
        spriteId = spriteId,
        modifier = modifier.size(size),
        contentDescription = contentDescription,
        fallback = fallback
    )
}

/**
 * Extension to convert SkillType to sprite ID
 */
fun SkillType.toSpriteId(): Int = SpriteIds.Skills.forIndex(this.ordinal)
