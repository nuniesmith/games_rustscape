package com.rustscape.client.protocol

/**
 * Server-to-Client packet opcodes
 * These are the decoded opcodes received from the game server
 */
object ServerOpcode {
    /** Player position and appearance updates */
    const val PLAYER_UPDATE = 81

    /** NPC position and state updates */
    const val NPC_UPDATE = 65

    /** Map region change notification */
    const val MAP_REGION = 73

    /** Open an interface/window */
    const val INTERFACE_OPEN = 97

    /** Close the current interface */
    const val INTERFACE_CLOSE = 219

    /** Chat message from another player */
    const val CHAT_MESSAGE = 253

    /** System message (yellow text) */
    const val SYSTEM_MESSAGE = 253

    /** Update run energy percentage */
    const val RUN_ENERGY = 110

    /** Update player weight */
    const val WEIGHT = 240

    /** Skill level/xp update */
    const val SKILL_UPDATE = 134

    /** Inventory contents update */
    const val INVENTORY_UPDATE = 53

    /** Force logout */
    const val LOGOUT = 109

    /** Set player option (right-click menu) */
    const val SET_PLAYER_OPTION = 104

    /** Update friend list entry */
    const val FRIEND_UPDATE = 50

    /** Update ignore list entry */
    const val IGNORE_UPDATE = 214

    /** Private message received */
    const val PRIVATE_MESSAGE = 196

    /** Play sound effect */
    const val SOUND_EFFECT = 174

    /** Play music track */
    const val MUSIC = 74

    /** Camera shake/movement */
    const val CAMERA = 35

    /** Ground item spawn */
    const val GROUND_ITEM_SPAWN = 44

    /** Ground item remove */
    const val GROUND_ITEM_REMOVE = 156

    /** Object spawn */
    const val OBJECT_SPAWN = 151

    /** Object remove */
    const val OBJECT_REMOVE = 101

    /** Projectile spawn */
    const val PROJECTILE = 117

    /** Graphics/spotanim spawn */
    const val GRAPHICS = 4

    /** Animation */
    const val ANIMATION = 89

    /** Force movement */
    const val FORCE_MOVEMENT = 52

    /** Hint arrow */
    const val HINT_ARROW = 254

    /** Tab interface */
    const val TAB_INTERFACE = 71

    /** Config/varp update */
    const val CONFIG = 36

    /** Config/varp update (large) */
    const val CONFIG_LARGE = 87

    /** Run config update */
    const val RUN_CONFIG = 113

    /** Minimap state */
    const val MINIMAP_STATE = 99

    /** Reset animations */
    const val RESET_ANIMS = 1
}

/**
 * Client-to-Server packet opcodes
 * These are the opcodes sent to the game server (before ISAAC encoding)
 */
object ClientOpcode {
    /** Keep-alive/ping packet */
    const val KEEP_ALIVE = 0

    /** Window focus changed */
    const val WINDOW_FOCUS = 3

    /** Public chat message */
    const val CHAT = 4

    /** Walk to position */
    const val WALK = 164

    /** Walk to position (minimap click) */
    const val WALK_MINIMAP = 248

    /** Examine NPC */
    const val NPC_EXAMINE = 6

    /** Examine item */
    const val ITEM_EXAMINE = 2

    /** Examine object */
    const val OBJECT_EXAMINE = 197

    /** Object action 1 (first option) */
    const val OBJECT_ACTION_1 = 132

    /** Object action 2 */
    const val OBJECT_ACTION_2 = 252

    /** Object action 3 */
    const val OBJECT_ACTION_3 = 70

    /** Object action 4 */
    const val OBJECT_ACTION_4 = 234

    /** Object action 5 */
    const val OBJECT_ACTION_5 = 228

    /** NPC action 1 (first option) */
    const val NPC_ACTION_1 = 155

    /** NPC action 2 */
    const val NPC_ACTION_2 = 72

    /** NPC action 3 */
    const val NPC_ACTION_3 = 17

    /** NPC action 4 */
    const val NPC_ACTION_4 = 21

    /** NPC action 5 */
    const val NPC_ACTION_5 = 18

    /** Developer command (::command) */
    const val COMMAND = 103

    /** Map region loaded acknowledgment */
    const val MAP_LOADED = 121

    /** Mouse click */
    const val MOUSE_CLICK = 241

    /** Item action 1 (inventory) */
    const val ITEM_ACTION_1 = 122

    /** Item action 2 */
    const val ITEM_ACTION_2 = 16

    /** Item action 3 */
    const val ITEM_ACTION_3 = 75

    /** Item action 4 */
    const val ITEM_ACTION_4 = 87

    /** Item action 5 (drop) */
    const val ITEM_ACTION_5 = 145

    /** Button/interface click */
    const val BUTTON_CLICK = 185

    /** Close interface */
    const val CLOSE_INTERFACE = 130

    /** Item on item */
    const val ITEM_ON_ITEM = 53

    /** Item on object */
    const val ITEM_ON_OBJECT = 192

    /** Item on NPC */
    const val ITEM_ON_NPC = 57

    /** Item on player */
    const val ITEM_ON_PLAYER = 14

    /** Item on ground item */
    const val ITEM_ON_GROUND_ITEM = 25

    /** Magic on NPC */
    const val MAGIC_ON_NPC = 131

    /** Magic on player */
    const val MAGIC_ON_PLAYER = 249

    /** Magic on item */
    const val MAGIC_ON_ITEM = 237

    /** Magic on ground item */
    const val MAGIC_ON_GROUND_ITEM = 181

    /** Magic on object */
    const val MAGIC_ON_OBJECT = 35

    /** Player action 1 (attack/follow) */
    const val PLAYER_ACTION_1 = 128

    /** Player action 2 */
    const val PLAYER_ACTION_2 = 153

    /** Player action 3 */
    const val PLAYER_ACTION_3 = 73

    /** Player action 4 */
    const val PLAYER_ACTION_4 = 139

    /** Player action 5 */
    const val PLAYER_ACTION_5 = 39

    /** Ground item action 1 (take) */
    const val GROUND_ITEM_ACTION_1 = 236

    /** Ground item action 2 */
    const val GROUND_ITEM_ACTION_2 = 253

    /** Ground item action 3 */
    const val GROUND_ITEM_ACTION_3 = 87

    /** Add friend */
    const val ADD_FRIEND = 188

    /** Remove friend */
    const val REMOVE_FRIEND = 215

    /** Add ignore */
    const val ADD_IGNORE = 133

    /** Remove ignore */
    const val REMOVE_IGNORE = 74

    /** Send private message */
    const val PRIVATE_MESSAGE = 126

    /** Report abuse */
    const val REPORT_ABUSE = 218

    /** Idle/AFK notification */
    const val IDLE = 202

    /** Camera angle changed */
    const val CAMERA = 86

    /** Screen resize */
    const val SCREEN_RESIZE = 94

    /** Typing in input field */
    const val TYPING = 60

    /** Enter amount dialog response */
    const val ENTER_AMOUNT = 208

    /** Enter name dialog response */
    const val ENTER_NAME = 244
}

/**
 * Packet size definitions
 * -1 = variable byte (size sent as 1 byte)
 * -2 = variable short (size sent as 2 bytes)
 * 0+ = fixed size
 */
object PacketSize {
    /** Server packet sizes indexed by opcode */
    val SERVER_SIZES = intArrayOf(
        0, 0, 0, 0, 6, 0, 0, 0, 4, 0, // 0-9
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 10-19
        0, 0, 0, 0, 1, 0, 0, 0, 0, 0, // 20-29
        0, 0, 0, 0, 0, 4, 3, 0, 0, 0, // 30-39
        0, 0, 0, 0, 5, 0, 0, 6, 0, 0, // 40-49
        9, 0, 0, -2, 0, 0, 0, 0, 0, 0, // 50-59
        0, 0, 0, 0, 0, -2, 0, 0, 0, 0, // 60-69
        -1, 1, 0, 4, 2, 0, 0, 0, 0, 0, // 70-79
        0, -2, 0, 0, 0, 0, 0, 3, 0, 4, // 80-89
        0, 0, 0, 0, 0, 0, 0, 2, 0, 1, // 90-99
        0, 2, 0, 0, -1, 0, 0, 0, 0, 1, // 100-109
        1, 0, 0, 1, 0, 0, 0, -2, 0, 0, // 110-119
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 120-129
        0, 0, 0, 0, 5, 0, 0, 0, 0, 0, // 130-139
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 140-149
        0, 6, 0, 0, 0, 0, 2, 0, 0, 0, // 150-159
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 160-169
        0, 0, 0, 0, 4, 0, 0, 0, 0, 0, // 170-179
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 180-189
        0, 0, 0, 0, 0, 0, -1, 0, 0, 0, // 190-199
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 200-209
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 210-219
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 220-229
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 230-239
        1, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 240-249
        0, 0, 0, -1, 6, 0              // 250-255
    )

    /** Client packet sizes indexed by opcode */
    val CLIENT_SIZES = intArrayOf(
        0, 0, 6, 1, -1, 0, 2, 0, 0, 0, // 0-9
        0, 0, 0, 0, 8, 0, 6, 2, 2, 0, // 10-19
        0, 2, 0, 6, 0, 12, 0, 0, 0, 0, // 20-29
        0, 0, 0, 0, 0, 6, 0, 0, 0, 2, // 30-39
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 40-49
        0, 0, 0, 12, 0, 0, 0, 8, 0, 0, // 50-59
        4, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 60-69
        6, 0, 6, 2, 2, 6, 0, 0, 0, 0, // 70-79
        0, 0, 0, 0, 0, 0, 6, 6, 0, 0, // 80-89
        0, 0, 0, 0, 4, 0, 0, 0, 0, 0, // 90-99
        0, 0, 0, -1, 0, 0, 0, 0, 0, 0, // 100-109
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 110-119
        0, 0, 8, 0, 0, 0, -1, 0, 6, 0, // 120-129
        6, 6, 6, 8, 0, 0, 0, 0, 0, 2, // 130-139
        0, 0, 0, 0, 0, 6, 0, 0, 0, 0, // 140-149
        0, 0, 0, 2, 0, 2, 0, 0, 0, 0, // 150-159
        0, 0, 0, 0, 6, 0, 0, 0, 0, 0, // 160-169
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 170-179
        0, 6, 0, 0, 0, 4, 0, 0, -1, 0, // 180-189
        0, 0, 6, 0, 0, 0, 0, 2, 0, 0, // 190-199
        0, 0, 0, 0, 0, 0, 0, 0, -1, 0, // 200-209
        0, 0, 0, 0, 0, -1, 0, 0, 0, 0, // 210-219
        0, 0, 0, 0, 0, 0, 0, 0, 6, 0, // 220-229
        0, 0, 0, 0, 6, 0, 6, 6, 0, 0, // 230-239
        0, 2, 0, 0, -1, 0, 0, 0, 6, 4, // 240-249
        0, 0, 6, 2, 0, 0               // 250-255
    )

    /**
     * Get the size for a server packet opcode
     */
    fun getServerPacketSize(opcode: Int): Int {
        return if (opcode in 0..255) SERVER_SIZES[opcode] else 0
    }

    /**
     * Get the size for a client packet opcode
     */
    fun getClientPacketSize(opcode: Int): Int {
        return if (opcode in 0..255) CLIENT_SIZES[opcode] else 0
    }
}

/**
 * Base class for all packets
 */
abstract class Packet(val opcode: Int) {
    /**
     * Encode this packet to a ByteBuffer
     */
    abstract fun encode(buffer: ByteBuffer)

    /**
     * Get the size of this packet's payload (not including opcode)
     */
    abstract fun size(): Int
}

/**
 * Incoming packet from server
 */
data class IncomingPacket(
    val opcode: Int,
    val payload: ByteBuffer
)

/**
 * Outgoing packet to server
 */
data class OutgoingPacket(
    val opcode: Int,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as OutgoingPacket
        return opcode == other.opcode && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = opcode
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
