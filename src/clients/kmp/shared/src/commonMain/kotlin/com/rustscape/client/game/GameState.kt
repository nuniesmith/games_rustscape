package com.rustscape.client.game

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.abs

/**
 * Skill IDs matching the RS protocol
 */
object SkillId {
    const val ATTACK = 0
    const val DEFENCE = 1
    const val STRENGTH = 2
    const val HITPOINTS = 3
    const val RANGED = 4
    const val PRAYER = 5
    const val MAGIC = 6
    const val COOKING = 7
    const val WOODCUTTING = 8
    const val FLETCHING = 9
    const val FISHING = 10
    const val FIREMAKING = 11
    const val CRAFTING = 12
    const val SMITHING = 13
    const val MINING = 14
    const val HERBLORE = 15
    const val AGILITY = 16
    const val THIEVING = 17
    const val SLAYER = 18
    const val FARMING = 19
    const val RUNECRAFT = 20
    const val HUNTER = 21
    const val CONSTRUCTION = 22

    const val SKILL_COUNT = 23

    val NAMES = arrayOf(
        "Attack", "Defence", "Strength", "Hitpoints", "Ranged",
        "Prayer", "Magic", "Cooking", "Woodcutting", "Fletching",
        "Fishing", "Firemaking", "Crafting", "Smithing", "Mining",
        "Herblore", "Agility", "Thieving", "Slayer", "Farming",
        "Runecraft", "Hunter", "Construction"
    )

    fun getName(id: Int): String = NAMES.getOrElse(id) { "Unknown" }
}

/**
 * Player rights/privilege levels
 */
enum class PlayerRights(val id: Int) {
    PLAYER(0),
    MODERATOR(1),
    ADMINISTRATOR(2),
    OWNER(3);

    companion object {
        fun fromId(id: Int): PlayerRights = entries.find { it.id == id } ?: PLAYER
    }
}

/**
 * Represents a skill with level and experience
 */
@Serializable
data class Skill(
    val id: Int,
    var level: Int = 1,
    var experience: Int = 0,
    var boostedLevel: Int = level
) {
    val name: String get() = SkillId.getName(id)

    /**
     * Calculate level from experience using RS formula
     */
    fun calculateLevel(): Int {
        var points = 0
        var output = 0
        for (lvl in 1..99) {
            points += (lvl + 300.0 * 2.0.pow(lvl / 7.0)).toInt()
            output = points / 4
            if (output >= experience) {
                return lvl
            }
        }
        return 99
    }

    /**
     * Get experience required for next level
     */
    fun experienceToNextLevel(): Int {
        if (level >= 99) return 0
        return getExperienceForLevel(level + 1) - experience
    }

    companion object {
        /**
         * Get experience required for a specific level
         */
        fun getExperienceForLevel(level: Int): Int {
            var points = 0
            var output = 0
            for (lvl in 1 until level) {
                points += (lvl + 300.0 * 2.0.pow(lvl / 7.0)).toInt()
                output = points / 4
            }
            return output
        }
    }
}

/**
 * Represents a position in the game world
 */
@Serializable
data class Position(
    var x: Int = 0,
    var y: Int = 0,
    var z: Int = 0
) {
    /** Region X coordinate (x / 64) */
    val regionX: Int get() = x shr 6

    /** Region Y coordinate (y / 64) */
    val regionY: Int get() = y shr 6

    /** Local X within region (x % 64) */
    val localX: Int get() = x and 63

    /** Local Y within region (y % 64) */
    val localY: Int get() = y and 63

    /** Chunk X coordinate (x / 8) */
    val chunkX: Int get() = x shr 3

    /** Chunk Y coordinate (y / 8) */
    val chunkY: Int get() = y shr 3

    fun copy(): Position = Position(x, y, z)

    fun distanceTo(other: Position): Int {
        val dx = x - other.x
        val dy = y - other.y
        return maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
    }

    fun isWithinDistance(other: Position, distance: Int): Boolean {
        return z == other.z && distanceTo(other) <= distance
    }

    override fun toString(): String = "Position($x, $y, $z)"
}

/**
 * Represents the current map region
 */
@Serializable
data class MapRegion(
    val baseX: Int = 0,
    val baseY: Int = 0
) {
    val regionX: Int get() = baseX shr 6
    val regionY: Int get() = baseY shr 6

    override fun toString(): String = "Region($regionX, $regionY)"
}

/**
 * Chat message types - matching classic RS chat channels
 */
enum class MessageType(val id: Int) {
    SYSTEM(0),      // System messages (cyan)
    GAME(1),        // Game messages (white)
    PUBLIC(2),      // Public chat (yellow)
    PRIVATE(3),     // Private messages (cyan/teal)
    CLAN(4),        // Clan chat (dark red)
    TRADE(5),       // Trade messages (purple)
    COMMAND(6);     // Command responses (orange)

    companion object {
        fun fromId(id: Int): MessageType = entries.find { it.id == id } ?: GAME
    }
}

/**
 * Skill type enum for UI display ordering
 */
enum class SkillType {
    ATTACK, DEFENCE, STRENGTH, HITPOINTS, RANGED, PRAYER, MAGIC,
    COOKING, WOODCUTTING, FLETCHING, FISHING, FIREMAKING, CRAFTING,
    SMITHING, MINING, HERBLORE, AGILITY, THIEVING, SLAYER, FARMING,
    RUNECRAFT, HUNTER, CONSTRUCTION, SUMMONING
}

/**
 * Skill info for UI display
 */
data class SkillInfo(
    val id: Int,
    val level: Int = 1,
    val experience: Long = 0,
    val currentLevel: Int = level  // Can be boosted/drained
) {
    val name: String get() = SkillId.getName(id)
}

/**
 * Represents a chat message
 */
@Serializable
data class ChatMessage(
    val text: String,
    val sender: String? = null,
    val type: MessageType = MessageType.GAME,
    val timestamp: String? = null  // Optional formatted timestamp like "12:34"
) {
    // For serialization compatibility
    constructor(text: String, sender: String?, typeId: Int, timestampMs: Long) : this(
        text = text,
        sender = sender,
        type = MessageType.fromId(typeId),
        timestamp = formatTimestamp(timestampMs)
    )

    companion object {
        private fun formatTimestamp(ms: Long): String {
            // Simple hour:minute format
            val totalSeconds = ms / 1000
            val hours = (totalSeconds / 3600) % 24
            val minutes = (totalSeconds / 60) % 60
            return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
        }
    }
}

/**
 * Represents an item in inventory or equipment
 */
@Serializable
data class Item(
    val id: Int,
    val amount: Int = 1
) {
    val isEmpty: Boolean get() = id <= 0 || amount <= 0

    companion object {
        val EMPTY = Item(-1, 0)
    }
}

/**
 * Represents an inventory container
 */
@Serializable
class Inventory(val capacity: Int = 28) {
    private val items = Array(capacity) { Item.EMPTY }

    fun get(slot: Int): Item {
        return if (slot in 0 until capacity) items[slot] else Item.EMPTY
    }

    fun set(slot: Int, item: Item) {
        if (slot in 0 until capacity) {
            items[slot] = item
        }
    }

    fun clear() {
        for (i in items.indices) {
            items[i] = Item.EMPTY
        }
    }

    fun toList(): List<Item> = items.toList()

    val freeSlots: Int get() = items.count { it.isEmpty }
    val usedSlots: Int get() = items.count { !it.isEmpty }
}

/**
 * Player option in right-click menu
 */
@Serializable
data class PlayerOption(
    val slot: Int,
    val text: String,
    val priority: Boolean = false
)

/**
 * Main game state container
 * This holds all the client-side game state that needs to be shared
 * across the UI and network layers.
 */
class GameState {
    // Player info
    var playerName: String = ""
    var playerIndex: Int = -1
    var rights: PlayerRights = PlayerRights.PLAYER
    var isMember: Boolean = false

    // Position and map
    var position: Position = Position()
    var mapRegion: MapRegion = MapRegion()

    // Skills (24 skills total including Summoning)
    val skills: Array<SkillInfo> = Array(24) { SkillInfo(it) }

    // Energy and weight
    var runEnergy: Int = 100
    var weight: Int = 0
    var isRunning: Boolean = false

    // Chat messages
    private val _messages: MutableList<ChatMessage> = mutableListOf()
    val messages: List<ChatMessage> get() = _messages.toList()
    var maxMessages: Int = 100

    // Player options (right-click menu)
    private val _playerOptions: MutableList<PlayerOption> = mutableListOf()
    val playerOptions: List<PlayerOption> get() = _playerOptions.toList()

    // Inventory
    val inventory: Inventory = Inventory(28)

    // Equipment (11 slots)
    val equipment: Inventory = Inventory(11)

    // State listeners for UI updates
    private val stateListeners: MutableList<GameStateListener> = mutableListOf()

    // ============ Skill Methods ============

    fun getSkill(id: Int): SkillInfo? {
        return skills.getOrNull(id)
    }

    fun updateSkill(id: Int, level: Int, experience: Int, currentLevel: Int = level) {
        if (id in skills.indices) {
            skills[id] = SkillInfo(id, level, experience.toLong(), currentLevel)
            notifyListeners { it.onSkillUpdated(id, level, experience) }
        }
    }

    fun getTotalLevel(): Int = skills.sumOf { it.level }

    fun getCombatLevel(): Int {
        val attack = skills[SkillId.ATTACK].level
        val strength = skills[SkillId.STRENGTH].level
        val defence = skills[SkillId.DEFENCE].level
        val hitpoints = skills[SkillId.HITPOINTS].level
        val prayer = skills[SkillId.PRAYER].level
        val ranged = skills[SkillId.RANGED].level
        val magic = skills[SkillId.MAGIC].level

        val base = (defence + hitpoints + (prayer / 2)) * 0.25
        val melee = (attack + strength) * 0.325
        val range = ranged * 0.4875
        val mage = magic * 0.4875

        return (base + maxOf(melee, range, mage)).toInt()
    }

    // ============ Message Methods ============

    fun addMessage(message: ChatMessage) {
        _messages.add(0, message)
        while (_messages.size > maxMessages) {
            _messages.removeAt(_messages.size - 1)
        }
        notifyListeners { it.onMessageReceived(message) }
    }

    fun addMessage(text: String, type: MessageType = MessageType.GAME, sender: String? = null) {
        addMessage(ChatMessage(text, sender, type))
    }

    fun clearMessages() {
        _messages.clear()
    }

    // ============ Position Methods ============

    fun setPosition(x: Int, y: Int, z: Int = 0) {
        position.x = x
        position.y = y
        position.z = z
        notifyListeners { it.onPositionChanged(position) }
    }

    fun setMapRegion(baseX: Int, baseY: Int) {
        mapRegion = MapRegion(baseX, baseY)
        notifyListeners { it.onMapRegionChanged(mapRegion) }
    }

    // ============ Player Option Methods ============

    fun setPlayerOption(slot: Int, text: String, priority: Boolean = false) {
        _playerOptions.removeAll { it.slot == slot }
        if (text.isNotBlank()) {
            _playerOptions.add(PlayerOption(slot, text, priority))
            _playerOptions.sortBy { it.slot }
        }
        notifyListeners { it.onPlayerOptionsChanged(_playerOptions) }
    }

    fun clearPlayerOptions() {
        _playerOptions.clear()
    }

    // ============ Player Info Methods ============

    fun setPlayerInfo(name: String, index: Int, rights: PlayerRights, member: Boolean) {
        this.playerName = name
        this.playerIndex = index
        this.rights = rights
        this.isMember = member
        notifyListeners { it.onPlayerInfoChanged(name, index, rights, member) }
    }

    // ============ Listener Management ============

    fun addListener(listener: GameStateListener) {
        stateListeners.add(listener)
    }

    fun removeListener(listener: GameStateListener) {
        stateListeners.remove(listener)
    }

    private fun notifyListeners(action: (GameStateListener) -> Unit) {
        stateListeners.forEach { action(it) }
    }

    // ============ Reset ============

    fun reset() {
        playerName = ""
        playerIndex = -1
        rights = PlayerRights.PLAYER
        isMember = false
        position = Position()
        mapRegion = MapRegion()
        runEnergy = 100
        weight = 0
        isRunning = false

        for (i in skills.indices) {
            val defaultLevel = if (i == SkillId.HITPOINTS) 10 else 1
            skills[i] = SkillInfo(i, defaultLevel, 0, defaultLevel)
        }

        _messages.clear()
        _playerOptions.clear()
        inventory.clear()
        equipment.clear()
    }
}

/**
 * Listener interface for game state changes
 */
interface GameStateListener {
    fun onSkillUpdated(skillId: Int, level: Int, experience: Int) {}
    fun onMessageReceived(message: ChatMessage) {}
    fun onPositionChanged(position: Position) {}
    fun onMapRegionChanged(region: MapRegion) {}
    fun onPlayerOptionsChanged(options: List<PlayerOption>) {}
    fun onPlayerInfoChanged(name: String, index: Int, rights: PlayerRights, member: Boolean) {}
    fun onInventoryChanged(inventory: Inventory) {}
    fun onEquipmentChanged(equipment: Inventory) {}
    fun onRunEnergyChanged(energy: Int) {}
    fun onWeightChanged(weight: Int) {}
}
