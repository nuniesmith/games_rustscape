package com.rustscape.client.cache

import kotlinx.serialization.Serializable

/**
 * NPC definition containing all properties for an NPC type
 */
@Serializable
data class NpcDefinition(
    val id: Int,
    val name: String = "null",
    val description: String = "",
    val combatLevel: Int = -1,
    val size: Int = 1,
    val standAnim: Int = -1,
    val walkAnim: Int = -1,
    val turnAnim: Int = -1,
    val turn180Anim: Int = -1,
    val turn90CWAnim: Int = -1,
    val turn90CCWAnim: Int = -1,
    val options: List<String?> = listOf(null, null, null, null, null),
    val isClickable: Boolean = true,
    val isMinimapVisible: Boolean = true,
    val isVisible: Boolean = true,
    val recolorFind: List<Int> = emptyList(),
    val recolorReplace: List<Int> = emptyList(),
    val models: List<Int> = emptyList(),
    val chatHeadModels: List<Int> = emptyList(),
    val widthScale: Int = 128,
    val heightScale: Int = 128,
    val renderPriority: Boolean = false,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val headIcon: Int = -1,
    val rotationSpeed: Int = 32,
    val varbitId: Int = -1,
    val varpId: Int = -1,
    val transformIds: List<Int> = emptyList(),
    val isInteractable: Boolean = true
) {
    val hasOptions: Boolean
        get() = options.any { it != null && it.isNotBlank() }

    val isCombatNpc: Boolean
        get() = combatLevel > 0

    fun getOption(index: Int): String? {
        return options.getOrNull(index)
    }

    companion object {
        val NULL = NpcDefinition(id = -1)
    }
}

/**
 * Item definition containing all properties for an item type
 */
@Serializable
data class ItemDefinition(
    val id: Int,
    val name: String = "null",
    val description: String = "",
    val value: Int = 1,
    val stackable: Boolean = false,
    val noted: Boolean = false,
    val noteTemplateId: Int = -1,
    val noteLinkedId: Int = -1,
    val members: Boolean = false,
    val tradeable: Boolean = true,
    val weight: Double = 0.0,
    val options: List<String?> = listOf(null, null, null, null, "Drop"),
    val groundOptions: List<String?> = listOf(null, null, "Take", null, null),
    val inventoryModel: Int = -1,
    val zoom2d: Int = 2000,
    val xan2d: Int = 0,
    val yan2d: Int = 0,
    val zan2d: Int = 0,
    val xOffset2d: Int = 0,
    val yOffset2d: Int = 0,
    val maleModel0: Int = -1,
    val maleModel1: Int = -1,
    val maleModel2: Int = -1,
    val femaleModel0: Int = -1,
    val femaleModel1: Int = -1,
    val femaleModel2: Int = -1,
    val maleHeadModel0: Int = -1,
    val maleHeadModel1: Int = -1,
    val femaleHeadModel0: Int = -1,
    val femaleHeadModel1: Int = -1,
    val recolorFind: List<Int> = emptyList(),
    val recolorReplace: List<Int> = emptyList(),
    val countItem: List<Int> = emptyList(),
    val countCo: List<Int> = emptyList(),
    val team: Int = 0,
    val shiftClickDropIndex: Int = -2
) {
    val isStackable: Boolean
        get() = stackable || noted

    val isNoted: Boolean
        get() = noted

    val hasGroundOptions: Boolean
        get() = groundOptions.any { it != null && it.isNotBlank() }

    val hasInventoryOptions: Boolean
        get() = options.any { it != null && it.isNotBlank() }

    fun getInventoryOption(index: Int): String? {
        return options.getOrNull(index)
    }

    fun getGroundOption(index: Int): String? {
        return groundOptions.getOrNull(index)
    }

    /**
     * Get the high alchemy value (default is 60% of store value)
     */
    val highAlchValue: Int
        get() = (value * 0.6).toInt()

    /**
     * Get the low alchemy value (default is 40% of store value)
     */
    val lowAlchValue: Int
        get() = (value * 0.4).toInt()

    companion object {
        val NULL = ItemDefinition(id = -1)

        // Common item IDs
        const val COINS = 995
        const val BONES = 526
        const val LOGS = 1511
        const val BRONZE_AXE = 1351
        const val BRONZE_SWORD = 1277
        const val WOODEN_SHIELD = 1171
        const val SHRIMP = 315
        const val COOKED_SHRIMP = 315
        const val RAW_SHRIMP = 317
        const val TINDERBOX = 590
        const val SMALL_FISHING_NET = 303
    }
}

/**
 * Object (scenery) definition containing all properties for a world object
 */
@Serializable
data class ObjectDefinition(
    val id: Int,
    val name: String = "null",
    val description: String = "",
    val width: Int = 1,
    val length: Int = 1,
    val solid: Boolean = true,
    val impenetrable: Boolean = true,
    val interactive: Boolean = false,
    val obstructsGround: Boolean = false,
    val nonFlatShading: Boolean = false,
    val contouredGround: Boolean = false,
    val modelSizeX: Int = 128,
    val modelSizeY: Int = 128,
    val modelSizeZ: Int = 128,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val offsetZ: Int = 0,
    val decorDisplacement: Int = 16,
    val options: List<String?> = listOf(null, null, null, null, null),
    val models: List<Int> = emptyList(),
    val modelTypes: List<Int> = emptyList(),
    val mapAreaId: Int = -1,
    val mapSceneId: Int = -1,
    val animation: Int = -1,
    val varbitId: Int = -1,
    val varpId: Int = -1,
    val transformIds: List<Int> = emptyList(),
    val ambientSoundId: Int = -1,
    val recolorFind: List<Int> = emptyList(),
    val recolorReplace: List<Int> = emptyList(),
    val wallOrDoor: Int = -1,
    val supportsItems: Int = -1
) {
    val hasOptions: Boolean
        get() = options.any { it != null && it.isNotBlank() }

    val isWall: Boolean
        get() = wallOrDoor >= 0

    fun getOption(index: Int): String? {
        return options.getOrNull(index)
    }

    companion object {
        val NULL = ObjectDefinition(id = -1)

        // Common object types
        const val TYPE_WALL_STRAIGHT = 0
        const val TYPE_WALL_DIAGONAL_CORNER = 1
        const val TYPE_WALL_CORNER = 2
        const val TYPE_WALL_SQUARE_CORNER = 3
        const val TYPE_WALL_DECORATION = 4
        const val TYPE_SCENERY = 10
        const val TYPE_GROUND_DECORATION = 22
    }
}

/**
 * Animation definition
 */
@Serializable
data class AnimationDefinition(
    val id: Int,
    val frameIds: List<Int> = emptyList(),
    val frameLengths: List<Int> = emptyList(),
    val loopOffset: Int = -1,
    val interleaveLeave: List<Int> = emptyList(),
    val stretches: Boolean = false,
    val forcedPriority: Int = 5,
    val leftHandItem: Int = -1,
    val rightHandItem: Int = -1,
    val maxLoops: Int = 99,
    val precedenceAnimating: Int = -1,
    val priority: Int = -1,
    val replyMode: Int = 2
) {
    val frameCount: Int
        get() = frameIds.size

    val duration: Int
        get() = frameLengths.sum()

    val loops: Boolean
        get() = loopOffset >= 0

    companion object {
        val NULL = AnimationDefinition(id = -1)
    }
}

/**
 * Graphics (SpotAnim) definition
 */
@Serializable
data class GraphicsDefinition(
    val id: Int,
    val modelId: Int = -1,
    val animationId: Int = -1,
    val resizeX: Int = 128,
    val resizeY: Int = 128,
    val rotation: Int = 0,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val recolorFind: List<Int> = emptyList(),
    val recolorReplace: List<Int> = emptyList()
) {
    companion object {
        val NULL = GraphicsDefinition(id = -1)
    }
}

/**
 * Stores and manages all game definitions
 */
class DefinitionManager {
    private val npcDefinitions = mutableMapOf<Int, NpcDefinition>()
    private val itemDefinitions = mutableMapOf<Int, ItemDefinition>()
    private val objectDefinitions = mutableMapOf<Int, ObjectDefinition>()
    private val animationDefinitions = mutableMapOf<Int, AnimationDefinition>()
    private val graphicsDefinitions = mutableMapOf<Int, GraphicsDefinition>()

    // NPC definitions
    fun getNpc(id: Int): NpcDefinition = npcDefinitions[id] ?: NpcDefinition.NULL
    fun addNpc(definition: NpcDefinition) {
        npcDefinitions[definition.id] = definition
    }

    fun getNpcCount(): Int = npcDefinitions.size

    // Item definitions
    fun getItem(id: Int): ItemDefinition = itemDefinitions[id] ?: ItemDefinition.NULL
    fun addItem(definition: ItemDefinition) {
        itemDefinitions[definition.id] = definition
    }

    fun getItemCount(): Int = itemDefinitions.size

    // Object definitions
    fun getObject(id: Int): ObjectDefinition = objectDefinitions[id] ?: ObjectDefinition.NULL
    fun addObject(definition: ObjectDefinition) {
        objectDefinitions[definition.id] = definition
    }

    fun getObjectCount(): Int = objectDefinitions.size

    // Animation definitions
    fun getAnimation(id: Int): AnimationDefinition = animationDefinitions[id] ?: AnimationDefinition.NULL
    fun addAnimation(definition: AnimationDefinition) {
        animationDefinitions[definition.id] = definition
    }

    fun getAnimationCount(): Int = animationDefinitions.size

    // Graphics definitions
    fun getGraphics(id: Int): GraphicsDefinition = graphicsDefinitions[id] ?: GraphicsDefinition.NULL
    fun addGraphics(definition: GraphicsDefinition) {
        graphicsDefinitions[definition.id] = definition
    }

    fun getGraphicsCount(): Int = graphicsDefinitions.size

    /**
     * Clear all definitions
     */
    fun clear() {
        npcDefinitions.clear()
        itemDefinitions.clear()
        objectDefinitions.clear()
        animationDefinitions.clear()
        graphicsDefinitions.clear()
    }

    /**
     * Get total definition count
     */
    fun getTotalCount(): Int {
        return npcDefinitions.size +
                itemDefinitions.size +
                objectDefinitions.size +
                animationDefinitions.size +
                graphicsDefinitions.size
    }

    /**
     * Search for NPCs by name
     */
    fun searchNpcsByName(query: String): List<NpcDefinition> {
        val lowerQuery = query.lowercase()
        return npcDefinitions.values.filter {
            it.name.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Search for items by name
     */
    fun searchItemsByName(query: String): List<ItemDefinition> {
        val lowerQuery = query.lowercase()
        return itemDefinitions.values.filter {
            it.name.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Search for objects by name
     */
    fun searchObjectsByName(query: String): List<ObjectDefinition> {
        val lowerQuery = query.lowercase()
        return objectDefinitions.values.filter {
            it.name.lowercase().contains(lowerQuery)
        }
    }
}

/**
 * Equipment slot constants
 */
object EquipmentSlot {
    const val HEAD = 0
    const val CAPE = 1
    const val AMULET = 2
    const val WEAPON = 3
    const val BODY = 4
    const val SHIELD = 5
    const val LEGS = 7
    const val HANDS = 9
    const val FEET = 10
    const val RING = 12
    const val AMMO = 13

    const val SLOT_COUNT = 14

    fun getName(slot: Int): String = when (slot) {
        HEAD -> "Head"
        CAPE -> "Cape"
        AMULET -> "Amulet"
        WEAPON -> "Weapon"
        BODY -> "Body"
        SHIELD -> "Shield"
        LEGS -> "Legs"
        HANDS -> "Hands"
        FEET -> "Feet"
        RING -> "Ring"
        AMMO -> "Ammo"
        else -> "Unknown"
    }
}

/**
 * Combat style definitions
 */
enum class CombatStyle(val id: Int, val description: String) {
    ACCURATE(0, "Accurate"),
    AGGRESSIVE(1, "Aggressive"),
    DEFENSIVE(2, "Defensive"),
    CONTROLLED(3, "Controlled"),
    RAPID(4, "Rapid"),
    LONGRANGE(5, "Longrange"),
    MAGIC(6, "Magic"),
    DEFENSIVE_MAGIC(7, "Defensive Magic");

    companion object {
        fun fromId(id: Int): CombatStyle? = entries.find { it.id == id }
    }
}

/**
 * Attack type definitions
 */
enum class AttackType(val id: Int) {
    STAB(0),
    SLASH(1),
    CRUSH(2),
    MAGIC(3),
    RANGED(4);

    companion object {
        fun fromId(id: Int): AttackType? = entries.find { it.id == id }
    }
}
