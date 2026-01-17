package com.rustscape.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Sprite definition within an atlas
 */
data class SpriteDefinition(
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val offsetX: Int = 0,
    val offsetY: Int = 0
)

/**
 * Sprite atlas containing multiple sprites in a single image
 */
class SpriteAtlas(
    val image: ImageBitmap,
    val sprites: Map<String, SpriteDefinition>
) {
    /**
     * Get a sprite definition by name
     */
    fun getSprite(name: String): SpriteDefinition? = sprites[name]

    /**
     * Check if a sprite exists
     */
    fun hasSprite(name: String): Boolean = sprites.containsKey(name)

    /**
     * Get all sprite names
     */
    fun getSpriteNames(): Set<String> = sprites.keys
}

/**
 * Atlas manager for loading and caching sprite atlases
 */
object AtlasManager {
    private val atlases = mutableMapOf<String, SpriteAtlas>()
    private val loadingCallbacks = mutableMapOf<String, MutableList<(SpriteAtlas?) -> Unit>>()

    /**
     * Get a cached atlas or null if not loaded
     */
    fun getAtlas(name: String): SpriteAtlas? = atlases[name]

    /**
     * Register an atlas (called when atlas is loaded)
     */
    fun registerAtlas(name: String, atlas: SpriteAtlas) {
        atlases[name] = atlas
        loadingCallbacks[name]?.forEach { it(atlas) }
        loadingCallbacks.remove(name)
    }

    /**
     * Request atlas load callback
     */
    fun onAtlasLoaded(name: String, callback: (SpriteAtlas?) -> Unit) {
        if (atlases.containsKey(name)) {
            callback(atlases[name])
        } else {
            loadingCallbacks.getOrPut(name) { mutableListOf() }.add(callback)
        }
    }

    /**
     * Clear all atlases
     */
    fun clear() {
        atlases.clear()
        loadingCallbacks.clear()
    }
}

/**
 * Pre-defined atlas types
 */
enum class AtlasType(val filename: String) {
    ITEMS("items_atlas"),
    SKILLS("skills_atlas"),
    UI("ui_atlas"),
    ICONS("icons_atlas"),
    EQUIPMENT("equipment_atlas")
}

/**
 * Skill icon names in the atlas
 */
object SkillIcons {
    const val ATTACK = "skill_attack"
    const val STRENGTH = "skill_strength"
    const val DEFENCE = "skill_defence"
    const val HITPOINTS = "skill_hitpoints"
    const val RANGED = "skill_ranged"
    const val PRAYER = "skill_prayer"
    const val MAGIC = "skill_magic"
    const val COOKING = "skill_cooking"
    const val WOODCUTTING = "skill_woodcutting"
    const val FLETCHING = "skill_fletching"
    const val FISHING = "skill_fishing"
    const val FIREMAKING = "skill_firemaking"
    const val CRAFTING = "skill_crafting"
    const val SMITHING = "skill_smithing"
    const val MINING = "skill_mining"
    const val HERBLORE = "skill_herblore"
    const val AGILITY = "skill_agility"
    const val THIEVING = "skill_thieving"
    const val SLAYER = "skill_slayer"
    const val FARMING = "skill_farming"
    const val RUNECRAFT = "skill_runecraft"
    const val HUNTER = "skill_hunter"
    const val CONSTRUCTION = "skill_construction"
    const val SUMMONING = "skill_summoning"

    /**
     * Get skill icon name by skill ID
     */
    fun forSkillId(skillId: Int): String {
        return when (skillId) {
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
            else -> ATTACK
        }
    }
}

/**
 * Equipment slot icon names
 */
object EquipmentIcons {
    const val HEAD = "equip_head"
    const val CAPE = "equip_cape"
    const val AMULET = "equip_amulet"
    const val WEAPON = "equip_weapon"
    const val BODY = "equip_body"
    const val SHIELD = "equip_shield"
    const val LEGS = "equip_legs"
    const val GLOVES = "equip_gloves"
    const val BOOTS = "equip_boots"
    const val RING = "equip_ring"
    const val AMMO = "equip_ammo"

    /**
     * Get equipment icon name by slot
     */
    fun forSlot(slot: EquipmentSlot): String {
        return when (slot) {
            EquipmentSlot.HEAD -> HEAD
            EquipmentSlot.CAPE -> CAPE
            EquipmentSlot.AMULET -> AMULET
            EquipmentSlot.WEAPON -> WEAPON
            EquipmentSlot.BODY -> BODY
            EquipmentSlot.SHIELD -> SHIELD
            EquipmentSlot.LEGS -> LEGS
            EquipmentSlot.GLOVES -> GLOVES
            EquipmentSlot.BOOTS -> BOOTS
            EquipmentSlot.RING -> RING
            EquipmentSlot.AMMO -> AMMO
        }
    }
}

/**
 * UI element icon names
 */
object UIIcons {
    const val BUTTON_STONE = "ui_button_stone"
    const val BUTTON_STONE_PRESSED = "ui_button_stone_pressed"
    const val PANEL_BACKGROUND = "ui_panel_bg"
    const val SCROLL_UP = "ui_scroll_up"
    const val SCROLL_DOWN = "ui_scroll_down"
    const val CLOSE_BUTTON = "ui_close"
    const val TAB_COMBAT = "tab_combat"
    const val TAB_SKILLS = "tab_skills"
    const val TAB_QUESTS = "tab_quests"
    const val TAB_INVENTORY = "tab_inventory"
    const val TAB_EQUIPMENT = "tab_equipment"
    const val TAB_PRAYER = "tab_prayer"
    const val TAB_MAGIC = "tab_magic"
    const val TAB_CLAN = "tab_clan"
    const val TAB_FRIENDS = "tab_friends"
    const val TAB_IGNORE = "tab_ignore"
    const val TAB_LOGOUT = "tab_logout"
    const val TAB_SETTINGS = "tab_settings"
    const val TAB_EMOTES = "tab_emotes"
    const val TAB_MUSIC = "tab_music"
    const val ORB_HP = "orb_hp"
    const val ORB_PRAYER = "orb_prayer"
    const val ORB_RUN = "orb_run"
    const val ORB_SPECIAL = "orb_special"
    const val MINIMAP_FRAME = "minimap_frame"
    const val COMPASS = "compass"
}

/**
 * Composable to render a sprite from an atlas
 */
@Composable
fun AtlasSprite(
    atlas: SpriteAtlas?,
    spriteName: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tint: Color? = null,
    fallback: (@Composable () -> Unit)? = null
) {
    val sprite = atlas?.getSprite(spriteName)

    if (atlas != null && sprite != null) {
        Canvas(modifier = modifier.size(size)) {
            drawAtlasSprite(
                atlas = atlas,
                sprite = sprite,
                topLeft = Offset.Zero,
                size = Size(this.size.width, this.size.height),
                tint = tint
            )
        }
    } else {
        // Render fallback or placeholder
        fallback?.invoke() ?: Canvas(modifier = modifier.size(size)) {
            // Draw placeholder rectangle
            drawRect(
                color = Color(0xFF3D3024),
                topLeft = Offset.Zero,
                size = this.size
            )
            drawRect(
                color = Color(0xFF5C4A36),
                topLeft = Offset(2f, 2f),
                size = Size(this.size.width - 4f, this.size.height - 4f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1f)
            )
        }
    }
}

/**
 * Draw a sprite from an atlas onto a DrawScope
 */
fun DrawScope.drawAtlasSprite(
    atlas: SpriteAtlas,
    sprite: SpriteDefinition,
    topLeft: Offset,
    size: Size,
    tint: Color? = null,
    alpha: Float = 1f
) {
    val srcOffset = IntOffset(sprite.x, sprite.y)
    val srcSize = IntSize(sprite.width, sprite.height)

    drawImage(
        image = atlas.image,
        srcOffset = srcOffset,
        srcSize = srcSize,
        dstOffset = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        alpha = alpha,
        colorFilter = tint?.let { ColorFilter.tint(it, BlendMode.Modulate) }
    )
}

/**
 * Draw a sprite from an atlas by name
 */
fun DrawScope.drawAtlasSprite(
    atlas: SpriteAtlas,
    spriteName: String,
    topLeft: Offset,
    size: Size,
    tint: Color? = null,
    alpha: Float = 1f
) {
    val sprite = atlas.getSprite(spriteName) ?: return
    drawAtlasSprite(atlas, sprite, topLeft, size, tint, alpha)
}

/**
 * Parse atlas definition from JSON-like format
 * Format: "name,x,y,width,height" per line
 */
fun parseAtlasDefinition(definition: String): Map<String, SpriteDefinition> {
    val sprites = mutableMapOf<String, SpriteDefinition>()

    definition.lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .forEach { line ->
            val parts = line.split(",").map { it.trim() }
            if (parts.size >= 5) {
                val name = parts[0]
                val x = parts[1].toIntOrNull() ?: 0
                val y = parts[2].toIntOrNull() ?: 0
                val width = parts[3].toIntOrNull() ?: 0
                val height = parts[4].toIntOrNull() ?: 0
                val offsetX = parts.getOrNull(5)?.toIntOrNull() ?: 0
                val offsetY = parts.getOrNull(6)?.toIntOrNull() ?: 0

                sprites[name] = SpriteDefinition(
                    name = name,
                    x = x,
                    y = y,
                    width = width,
                    height = height,
                    offsetX = offsetX,
                    offsetY = offsetY
                )
            }
        }

    return sprites
}

/**
 * Create a generated placeholder atlas with solid color sprites
 * Used as fallback when real atlas images aren't available
 */
fun createPlaceholderAtlas(
    width: Int = 512,
    height: Int = 512,
    sprites: Map<String, SpriteDefinition>
): SpriteAtlas {
    // Create a simple colored image
    val pixels = IntArray(width * height) { 0xFF2B2117.toInt() }

    // Fill each sprite region with a distinct color based on hash
    sprites.values.forEach { sprite ->
        val hash = sprite.name.hashCode()
        val r = ((hash shr 16) and 0xFF).coerceIn(64, 200)
        val g = ((hash shr 8) and 0xFF).coerceIn(64, 200)
        val b = (hash and 0xFF).coerceIn(64, 200)
        val color = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

        for (y in sprite.y until (sprite.y + sprite.height).coerceAtMost(height)) {
            for (x in sprite.x until (sprite.x + sprite.width).coerceAtMost(width)) {
                pixels[y * width + x] = color
            }
        }
    }

    val bitmap = ImageBitmap(width, height)
    bitmap.toPixelMap().apply {
        // Note: In actual Compose, you'd use platform-specific methods to create
        // the bitmap from pixels. This is a simplified representation.
    }

    return SpriteAtlas(bitmap, sprites)
}

/**
 * Default skill icons atlas definition (for when real atlas isn't available)
 * Each skill icon is 24x24 pixels, arranged in a 6x4 grid
 */
val DEFAULT_SKILL_ATLAS_DEFINITION = """
    # Skill Icons Atlas - 24x24 each, 6 columns
    skill_attack,0,0,24,24
    skill_defence,24,0,24,24
    skill_strength,48,0,24,24
    skill_hitpoints,72,0,24,24
    skill_ranged,96,0,24,24
    skill_prayer,120,0,24,24
    skill_magic,0,24,24,24
    skill_cooking,24,24,24,24
    skill_woodcutting,48,24,24,24
    skill_fletching,72,24,24,24
    skill_fishing,96,24,24,24
    skill_firemaking,120,24,24,24
    skill_crafting,0,48,24,24
    skill_smithing,24,48,24,24
    skill_mining,48,48,24,24
    skill_herblore,72,48,24,24
    skill_agility,96,48,24,24
    skill_thieving,120,48,24,24
    skill_slayer,0,72,24,24
    skill_farming,24,72,24,24
    skill_runecraft,48,72,24,24
    skill_hunter,72,72,24,24
    skill_construction,96,72,24,24
    skill_summoning,120,72,24,24
""".trimIndent()

/**
 * Default equipment icons atlas definition
 * Each equipment icon is 36x36 pixels
 */
val DEFAULT_EQUIPMENT_ATLAS_DEFINITION = """
    # Equipment Slot Icons - 36x36 each
    equip_head,0,0,36,36
    equip_cape,36,0,36,36
    equip_amulet,72,0,36,36
    equip_weapon,108,0,36,36
    equip_body,0,36,36,36
    equip_shield,36,36,36,36
    equip_legs,72,36,36,36
    equip_gloves,108,36,36,36
    equip_boots,0,72,36,36
    equip_ring,36,72,36,36
    equip_ammo,72,72,36,36
""".trimIndent()

/**
 * Default UI atlas definition
 */
val DEFAULT_UI_ATLAS_DEFINITION = """
    # UI Elements
    ui_button_stone,0,0,80,24
    ui_button_stone_pressed,0,24,80,24
    ui_panel_bg,0,48,256,256
    ui_close,80,0,16,16
    ui_scroll_up,96,0,16,16
    ui_scroll_down,96,16,16,16

    # Tab Icons - 30x33 each
    tab_combat,0,304,30,33
    tab_skills,30,304,30,33
    tab_quests,60,304,30,33
    tab_inventory,90,304,30,33
    tab_equipment,120,304,30,33
    tab_prayer,150,304,30,33
    tab_magic,180,304,30,33
    tab_clan,0,337,30,33
    tab_friends,30,337,30,33
    tab_ignore,60,337,30,33
    tab_logout,90,337,30,33
    tab_settings,120,337,30,33
    tab_emotes,150,337,30,33
    tab_music,180,337,30,33

    # Orbs - 28x28 each
    orb_hp,256,0,28,28
    orb_prayer,284,0,28,28
    orb_run,312,0,28,28
    orb_special,340,0,28,28

    # Minimap
    minimap_frame,256,28,168,168
    compass,424,28,33,33
""".trimIndent()

/**
 * CompositionLocal for providing atlases throughout the UI
 */
val LocalSkillAtlas = compositionLocalOf<SpriteAtlas?> { null }
val LocalItemAtlas = compositionLocalOf<SpriteAtlas?> { null }
val LocalUIAtlas = compositionLocalOf<SpriteAtlas?> { null }
val LocalEquipmentAtlas = compositionLocalOf<SpriteAtlas?> { null }
