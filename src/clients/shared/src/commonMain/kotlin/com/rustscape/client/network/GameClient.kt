package com.rustscape.client.network

import kotlin.random.Random

import com.rustscape.client.game.GameState
import com.rustscape.client.game.MessageType
import com.rustscape.client.game.PlayerRights
import com.rustscape.client.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/**
 * Connection state for the game client
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    HANDSHAKING,
    LOGGING_IN,
    CONNECTED,
    RECONNECTING,
    ERROR
}

/**
 * Login response codes from the server
 */
object LoginResponse {
    const val SUCCESS = 2
    const val INVALID_CREDENTIALS = 3
    const val ACCOUNT_DISABLED = 4
    const val ALREADY_LOGGED_IN = 5
    const val GAME_UPDATED = 6
    const val WORLD_FULL = 7
    const val LOGIN_SERVER_OFFLINE = 8
    const val TOO_MANY_CONNECTIONS = 9
    const val BAD_SESSION_ID = 10
    const val LOGIN_SERVER_REJECTED = 11
    const val MEMBERS_WORLD = 12
    const val COULD_NOT_COMPLETE = 13
    const val SERVER_UPDATING = 14
    const val RECONNECTING = 15
    const val TOO_MANY_LOGIN_ATTEMPTS = 16
    const val STANDING_IN_MEMBERS_AREA = 17
    const val LOCKED = 18
    const val CLOSED_BETA = 19
    const val INVALID_LOGIN_SERVER = 20
    const val PROFILE_TRANSFER = 21

    fun getMessage(code: Int): String = when (code) {
        SUCCESS -> "Login successful"
        INVALID_CREDENTIALS -> "Invalid username or password"
        ACCOUNT_DISABLED -> "Your account has been disabled"
        ALREADY_LOGGED_IN -> "Your account is already logged in"
        GAME_UPDATED -> "The game has been updated"
        WORLD_FULL -> "This world is full"
        LOGIN_SERVER_OFFLINE -> "Login server offline"
        TOO_MANY_CONNECTIONS -> "Too many connections from your address"
        BAD_SESSION_ID -> "Bad session ID"
        LOGIN_SERVER_REJECTED -> "Login server rejected session"
        MEMBERS_WORLD -> "You need a members account to login to this world"
        COULD_NOT_COMPLETE -> "Could not complete login"
        SERVER_UPDATING -> "Server is being updated"
        RECONNECTING -> "Reconnecting..."
        TOO_MANY_LOGIN_ATTEMPTS -> "Too many login attempts"
        STANDING_IN_MEMBERS_AREA -> "You are standing in a members-only area"
        LOCKED -> "Your account has been locked"
        CLOSED_BETA -> "Closed beta - access denied"
        INVALID_LOGIN_SERVER -> "Invalid login server"
        PROFILE_TRANSFER -> "Profile transfer in progress"
        else -> "Error code: $code"
    }
}

/**
 * Configuration for the game client
 */
data class ClientConfig(
    val serverHost: String = "localhost",
    val serverPort: Int = 43594,
    val wsPath: String = "/ws",
    val revision: Int = 317,
    val useWebSocket: Boolean = true,
    val reconnectAttempts: Int = 3,
    val reconnectDelayMs: Long = 5000,
    val pingIntervalMs: Long = 30000,
    val debug: Boolean = false
)

/**
 * Event types emitted by the game client
 */
sealed class ClientEvent {
    data class StateChanged(val state: ConnectionState) : ClientEvent()
    data class LoginSuccess(val rights: PlayerRights, val playerIndex: Int, val member: Boolean) : ClientEvent()
    data class LoginFailed(val code: Int, val message: String) : ClientEvent()
    data class PacketReceived(val opcode: Int, val payload: ByteBuffer) : ClientEvent()
    data class Error(val message: String, val cause: Throwable? = null) : ClientEvent()
    object Disconnected : ClientEvent()
    object Reconnecting : ClientEvent()
}

/**
 * Abstract base class for game client network connection
 * Platform-specific implementations will provide WebSocket or TCP functionality
 */
abstract class GameClient(
    protected val config: ClientConfig,
    protected val gameState: GameState
) {
    // Connection state
    protected var _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Event channel for client events
    protected val _events = Channel<ClientEvent>(Channel.BUFFERED)
    val events: Flow<ClientEvent> = _events.receiveAsFlow()

    // ISAAC ciphers for packet encryption
    protected var isaacPair: IsaacPair? = null
    protected var serverKey: Long = 0

    // Credentials for reconnection
    protected var username: String = ""
    protected var password: String = ""

    // Coroutine scope for async operations
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Ping job
    protected var pingJob: Job? = null

    // Reconnection state
    protected var reconnectAttempts = 0

    /**
     * Connect to the game server
     */
    abstract suspend fun connect(): Boolean

    /**
     * Disconnect from the game server
     */
    abstract suspend fun disconnect()

    /**
     * Send raw bytes to the server
     */
    protected abstract suspend fun sendRaw(data: ByteArray)

    /**
     * Called when raw data is received from the server
     */
    protected abstract fun onDataReceived(data: ByteArray)

    /**
     * Login to the game server
     */
    suspend fun login(username: String, password: String): Boolean {
        this.username = username
        this.password = password

        if (_connectionState.value == ConnectionState.DISCONNECTED) {
            if (!connect()) {
                return false
            }
        }

        return performLogin()
    }

    /**
     * Logout from the game server
     */
    suspend fun logout() {
        stopPingInterval()
        isaacPair = null
        gameState.reset()
        disconnect()
        setState(ConnectionState.DISCONNECTED)
        _events.send(ClientEvent.Disconnected)
    }

    /**
     * Send a game packet to the server
     */
    suspend fun sendPacket(opcode: Int, payload: ByteArray = byteArrayOf()) {
        val pair = isaacPair ?: run {
            log("Cannot send packet: ISAAC not initialized")
            return
        }

        val encodedOpcode = pair.encodeOpcode(opcode)
        val packetSize = PacketSize.getClientPacketSize(opcode)

        val buffer = ByteBuffer.allocate(payload.size + 3)
        buffer.writeUByte(encodedOpcode)

        when (packetSize) {
            -1 -> {
                // Variable byte size
                buffer.writeUByte(payload.size)
            }

            -2 -> {
                // Variable short size
                buffer.writeUShort(payload.size)
            }
            // Fixed size - no size byte needed
        }

        if (payload.isNotEmpty()) {
            buffer.writeBytes(payload)
        }

        val data = buffer.toByteArray()
        log("Sending packet: opcode=$opcode, encoded=$encodedOpcode, size=${data.size}")
        sendRaw(data)
    }

    /**
     * Send a keep-alive/ping packet
     */
    suspend fun sendPing() {
        sendPacket(ClientOpcode.KEEP_ALIVE)
    }

    /**
     * Send a chat message
     */
    suspend fun sendChat(message: String) {
        val buffer = ByteBuffer.allocate(message.length + 2)
        // Chat messages use compression in RS, simplified here
        for (char in message) {
            buffer.writeUByte(char.code and 0xFF)
        }
        buffer.writeUByte(0) // Null terminator
        sendPacket(ClientOpcode.CHAT, buffer.toByteArray())
    }

    /**
     * Send a walk command (regular click)
     */
    suspend fun sendWalk(destX: Int, destY: Int, running: Boolean = false) {
        val buffer = ByteBuffer.allocate(5)
        buffer.writeShortLEA(destY)
        buffer.writeShortA(destX)
        buffer.writeByteC(if (running) 1 else 0)
        sendPacket(ClientOpcode.WALK, buffer.toByteArray())
    }

    /**
     * Send a walk command from minimap click
     * Uses WALK_MINIMAP opcode for server-side distinction
     */
    suspend fun sendMinimapWalk(destX: Int, destY: Int, running: Boolean = false) {
        val buffer = ByteBuffer.allocate(5)
        buffer.writeShortLEA(destY)
        buffer.writeShortA(destX)
        buffer.writeByteC(if (running) 1 else 0)
        sendPacket(ClientOpcode.WALK_MINIMAP, buffer.toByteArray())
    }

    /**
     * Send a command (::command)
     */
    suspend fun sendCommand(command: String) {
        val buffer = ByteBuffer.allocate(command.length + 1)
        buffer.writeString(command)
        sendPacket(ClientOpcode.COMMAND, buffer.toByteArray())
    }

    /**
     * Send button click
     */
    suspend fun sendButtonClick(buttonId: Int) {
        val buffer = ByteBuffer.allocate(2)
        buffer.writeShort(buttonId)
        sendPacket(ClientOpcode.BUTTON_CLICK, buffer.toByteArray())
    }

    /**
     * Send close interface
     */
    suspend fun sendCloseInterface() {
        sendPacket(ClientOpcode.CLOSE_INTERFACE)
    }

    /**
     * Send window focus change
     */
    suspend fun sendWindowFocus(focused: Boolean) {
        val buffer = ByteBuffer.allocate(1)
        buffer.writeUByte(if (focused) 1 else 0)
        sendPacket(ClientOpcode.WINDOW_FOCUS, buffer.toByteArray())
    }

    /**
     * Send map loaded acknowledgment
     */
    suspend fun sendMapLoaded() {
        sendPacket(ClientOpcode.MAP_LOADED)
    }

    // ============ Entity Interaction Packets ============

    /**
     * Send NPC action (attack, talk, pickpocket, etc.)
     * @param npcIndex The server index of the NPC
     * @param actionSlot The action slot (1-5, where 1 is usually the first option)
     */
    suspend fun sendNpcAction(npcIndex: Int, actionSlot: Int) {
        val opcode = when (actionSlot) {
            1 -> ClientOpcode.NPC_ACTION_1
            2 -> ClientOpcode.NPC_ACTION_2
            3 -> ClientOpcode.NPC_ACTION_3
            4 -> ClientOpcode.NPC_ACTION_4
            5 -> ClientOpcode.NPC_ACTION_5
            else -> ClientOpcode.NPC_ACTION_1
        }
        val buffer = ByteBuffer.allocate(2)
        buffer.writeShortA(npcIndex)
        sendPacket(opcode, buffer.toByteArray())
        log("NPC action $actionSlot on index $npcIndex")
    }

    /**
     * Send NPC examine
     * @param npcIndex The server index of the NPC
     */
    suspend fun sendNpcExamine(npcIndex: Int) {
        val buffer = ByteBuffer.allocate(2)
        buffer.writeShort(npcIndex)
        sendPacket(ClientOpcode.NPC_EXAMINE, buffer.toByteArray())
        log("Examine NPC index $npcIndex")
    }

    /**
     * Send player action (follow, trade, attack, etc.)
     * @param playerIndex The server index of the player
     * @param actionSlot The action slot (1-5)
     */
    suspend fun sendPlayerAction(playerIndex: Int, actionSlot: Int) {
        val opcode = when (actionSlot) {
            1 -> ClientOpcode.PLAYER_ACTION_1
            2 -> ClientOpcode.PLAYER_ACTION_2
            3 -> ClientOpcode.PLAYER_ACTION_3
            4 -> ClientOpcode.PLAYER_ACTION_4
            5 -> ClientOpcode.PLAYER_ACTION_5
            else -> ClientOpcode.PLAYER_ACTION_1
        }
        val buffer = ByteBuffer.allocate(2)
        buffer.writeShortLE(playerIndex)
        sendPacket(opcode, buffer.toByteArray())
        log("Player action $actionSlot on index $playerIndex")
    }

    /**
     * Send object action (open, close, use, climb, etc.)
     * @param objectId The object definition ID
     * @param x The object's X coordinate
     * @param y The object's Y coordinate
     * @param actionSlot The action slot (1-5)
     */
    suspend fun sendObjectAction(objectId: Int, x: Int, y: Int, actionSlot: Int) {
        val opcode = when (actionSlot) {
            1 -> ClientOpcode.OBJECT_ACTION_1
            2 -> ClientOpcode.OBJECT_ACTION_2
            3 -> ClientOpcode.OBJECT_ACTION_3
            4 -> ClientOpcode.OBJECT_ACTION_4
            5 -> ClientOpcode.OBJECT_ACTION_5
            else -> ClientOpcode.OBJECT_ACTION_1
        }
        val buffer = ByteBuffer.allocate(6)
        buffer.writeShortA(x)
        buffer.writeShort(objectId)
        buffer.writeShortLE(y)
        sendPacket(opcode, buffer.toByteArray())
        log("Object action $actionSlot on $objectId at ($x, $y)")
    }

    /**
     * Send object examine
     * @param objectId The object definition ID
     */
    suspend fun sendObjectExamine(objectId: Int) {
        val buffer = ByteBuffer.allocate(2)
        buffer.writeShortLE(objectId)
        sendPacket(ClientOpcode.OBJECT_EXAMINE, buffer.toByteArray())
        log("Examine object $objectId")
    }

    /**
     * Send ground item action (take, examine)
     * @param itemId The item definition ID
     * @param x The item's X coordinate
     * @param y The item's Y coordinate
     * @param actionSlot The action slot (1-3, where 1 is usually "Take")
     */
    suspend fun sendGroundItemAction(itemId: Int, x: Int, y: Int, actionSlot: Int) {
        val opcode = when (actionSlot) {
            1 -> ClientOpcode.GROUND_ITEM_ACTION_1
            2 -> ClientOpcode.GROUND_ITEM_ACTION_2
            3 -> ClientOpcode.GROUND_ITEM_ACTION_3
            else -> ClientOpcode.GROUND_ITEM_ACTION_1
        }
        val buffer = ByteBuffer.allocate(6)
        buffer.writeShortLE(y)
        buffer.writeShort(itemId)
        buffer.writeShortLE(x)
        sendPacket(opcode, buffer.toByteArray())
        log("Ground item action $actionSlot on $itemId at ($x, $y)")
    }

    /**
     * Send item examine
     * @param itemId The item definition ID
     */
    suspend fun sendItemExamine(itemId: Int) {
        val buffer = ByteBuffer.allocate(2)
        buffer.writeShortLE(itemId)
        sendPacket(ClientOpcode.ITEM_EXAMINE, buffer.toByteArray())
        log("Examine item $itemId")
    }

    /**
     * Send inventory item action
     * @param itemId The item definition ID
     * @param slot The inventory slot (0-27)
     * @param interfaceId The interface ID (usually 3214 for inventory)
     * @param actionSlot The action slot (1-5)
     */
    suspend fun sendItemAction(itemId: Int, slot: Int, interfaceId: Int, actionSlot: Int) {
        val opcode = when (actionSlot) {
            1 -> ClientOpcode.ITEM_ACTION_1
            2 -> ClientOpcode.ITEM_ACTION_2
            3 -> ClientOpcode.ITEM_ACTION_3
            4 -> ClientOpcode.ITEM_ACTION_4
            5 -> ClientOpcode.ITEM_ACTION_5
            else -> ClientOpcode.ITEM_ACTION_1
        }
        val buffer = ByteBuffer.allocate(6)
        buffer.writeShortA(interfaceId)
        buffer.writeShortA(slot)
        buffer.writeShortA(itemId)
        sendPacket(opcode, buffer.toByteArray())
        log("Item action $actionSlot on $itemId in slot $slot")
    }

    /**
     * Add a friend
     * @param name The player name to add
     */
    suspend fun sendAddFriend(name: String) {
        val buffer = ByteBuffer.allocate(name.length + 1)
        buffer.writeString(name)
        sendPacket(ClientOpcode.ADD_FRIEND, buffer.toByteArray())
        log("Add friend: $name")
    }

    /**
     * Remove a friend
     * @param name The player name to remove
     */
    suspend fun sendRemoveFriend(name: String) {
        val buffer = ByteBuffer.allocate(name.length + 1)
        buffer.writeString(name)
        sendPacket(ClientOpcode.REMOVE_FRIEND, buffer.toByteArray())
        log("Remove friend: $name")
    }

    /**
     * Add to ignore list
     * @param name The player name to ignore
     */
    suspend fun sendAddIgnore(name: String) {
        val buffer = ByteBuffer.allocate(name.length + 1)
        buffer.writeString(name)
        sendPacket(ClientOpcode.ADD_IGNORE, buffer.toByteArray())
        log("Add ignore: $name")
    }

    /**
     * Remove from ignore list
     * @param name The player name to unignore
     */
    suspend fun sendRemoveIgnore(name: String) {
        val buffer = ByteBuffer.allocate(name.length + 1)
        buffer.writeString(name)
        sendPacket(ClientOpcode.REMOVE_IGNORE, buffer.toByteArray())
        log("Remove ignore: $name")
    }

    /**
     * Send private message
     * @param recipient The player name to message
     * @param message The message text
     */
    suspend fun sendPrivateMessage(recipient: String, message: String) {
        val buffer = ByteBuffer.allocate(recipient.length + message.length + 2)
        buffer.writeString(recipient)
        for (char in message) {
            buffer.writeUByte(char.code and 0xFF)
        }
        buffer.writeUByte(0)
        sendPacket(ClientOpcode.PRIVATE_MESSAGE, buffer.toByteArray())
        log("PM to $recipient: $message")
    }

    /**
     * Report abuse
     * @param name The player name to report
     * @param rule The rule violation ID
     */
    suspend fun sendReportAbuse(name: String, rule: Int) {
        val buffer = ByteBuffer.allocate(name.length + 2)
        buffer.writeString(name)
        buffer.writeUByte(rule)
        sendPacket(ClientOpcode.REPORT_ABUSE, buffer.toByteArray())
        log("Report $name for rule $rule")
    }

    // ============ Bank Operations ============

    /**
     * Close the bank interface
     */
    suspend fun sendBankClose() {
        sendPacket(ClientOpcode.BANK_CLOSE)
        log("Close bank")
    }

    /**
     * Withdraw an item from the bank
     * @param slot The bank slot to withdraw from
     * @param itemId The item ID to withdraw
     * @param amount The amount to withdraw
     * @param asNote Whether to withdraw as noted item
     */
    suspend fun sendBankWithdraw(slot: Int, itemId: Int, amount: Int, asNote: Boolean = false) {
        val buffer = ByteBuffer.allocate(11)
        buffer.writeShortLE(slot)
        buffer.writeShortLE(itemId)
        buffer.writeInt(amount)
        buffer.writeUByte(if (asNote) 1 else 0)
        sendPacket(ClientOpcode.BANK_WITHDRAW, buffer.toByteArray())
        log("Bank withdraw: slot=$slot, item=$itemId, amount=$amount, noted=$asNote")
    }

    /**
     * Deposit an item to the bank
     * @param inventorySlot The inventory slot to deposit from
     * @param itemId The item ID to deposit
     * @param amount The amount to deposit
     */
    suspend fun sendBankDeposit(inventorySlot: Int, itemId: Int, amount: Int) {
        val buffer = ByteBuffer.allocate(8)
        buffer.writeShortLE(inventorySlot)
        buffer.writeShortLE(itemId)
        buffer.writeInt(amount)
        sendPacket(ClientOpcode.BANK_DEPOSIT, buffer.toByteArray())
        log("Bank deposit: slot=$inventorySlot, item=$itemId, amount=$amount")
    }

    /**
     * Deposit all inventory items to the bank
     */
    suspend fun sendBankDepositAll() {
        sendPacket(ClientOpcode.BANK_DEPOSIT_ALL)
        log("Bank deposit all inventory")
    }

    /**
     * Deposit all worn equipment to the bank
     */
    suspend fun sendBankDepositEquipment() {
        sendPacket(ClientOpcode.BANK_DEPOSIT_EQUIPMENT)
        log("Bank deposit all equipment")
    }

    /**
     * Select a bank tab
     * @param tab The tab index (0-8)
     */
    suspend fun sendBankTabSelect(tab: Int) {
        val buffer = ByteBuffer.allocate(1)
        buffer.writeUByte(tab)
        sendPacket(ClientOpcode.BANK_TAB_SELECT, buffer.toByteArray())
        log("Bank tab select: $tab")
    }

    /**
     * Move/reorganize an item within the bank
     * @param fromSlot Source slot
     * @param toSlot Destination slot
     * @param mode 0 = swap, 1 = insert
     */
    suspend fun sendBankMoveItem(fromSlot: Int, toSlot: Int, mode: Int = 0) {
        val buffer = ByteBuffer.allocate(5)
        buffer.writeShortLE(fromSlot)
        buffer.writeShortLE(toSlot)
        buffer.writeUByte(mode)
        sendPacket(ClientOpcode.BANK_MOVE_ITEM, buffer.toByteArray())
        log("Bank move: from=$fromSlot, to=$toSlot, mode=$mode")
    }

    /**
     * Search bank items
     * @param query The search query string
     */
    suspend fun sendBankSearch(query: String) {
        val buffer = ByteBuffer.allocate(query.length + 1)
        buffer.writeString(query)
        sendPacket(ClientOpcode.BANK_SEARCH, buffer.toByteArray())
        log("Bank search: $query")
    }

    /**
     * Toggle withdraw-as-note mode
     * @param enabled Whether to withdraw as notes
     */
    suspend fun sendBankNoteMode(enabled: Boolean) {
        val buffer = ByteBuffer.allocate(1)
        buffer.writeUByte(if (enabled) 1 else 0)
        sendPacket(ClientOpcode.BANK_NOTE_MODE, buffer.toByteArray())
        log("Bank note mode: $enabled")
    }

    /**
     * Set the withdraw amount mode
     * @param mode 0=1, 1=5, 2=10, 3=X, 4=All
     */
    suspend fun sendBankWithdrawMode(mode: Int) {
        val buffer = ByteBuffer.allocate(1)
        buffer.writeUByte(mode)
        sendPacket(ClientOpcode.BANK_WITHDRAW_MODE, buffer.toByteArray())
        log("Bank withdraw mode: $mode")
    }

    // ============ Inventory Operations ============

    /**
     * Drop an item from inventory
     * @param slot The inventory slot
     * @param itemId The item ID being dropped
     */
    suspend fun sendItemDrop(slot: Int, itemId: Int) {
        val buffer = ByteBuffer.allocate(4)
        buffer.writeShortLE(slot)
        buffer.writeShortLE(itemId)
        sendPacket(ClientOpcode.ITEM_DROP, buffer.toByteArray())
        log("Drop item: slot=$slot, item=$itemId")
    }

    /**
     * Equip/wear an item from inventory
     * @param slot The inventory slot
     * @param itemId The item ID being equipped
     */
    suspend fun sendItemEquip(slot: Int, itemId: Int) {
        val buffer = ByteBuffer.allocate(4)
        buffer.writeShortLE(slot)
        buffer.writeShortLE(itemId)
        sendPacket(ClientOpcode.ITEM_EQUIP, buffer.toByteArray())
        log("Equip item: slot=$slot, item=$itemId")
    }

    /**
     * Swap two inventory slots
     * @param fromSlot Source slot
     * @param toSlot Destination slot
     */
    suspend fun sendInventorySwap(fromSlot: Int, toSlot: Int) {
        val buffer = ByteBuffer.allocate(4)
        buffer.writeShortLE(fromSlot)
        buffer.writeShortLE(toSlot)
        sendPacket(ClientOpcode.INVENTORY_SWAP, buffer.toByteArray())
        log("Inventory swap: from=$fromSlot, to=$toSlot")
    }

    // ============ Login Process ============

    /**
     * Perform the login handshake and authentication
     *
     * Rustscape server protocol (revision 530):
     * 1. Client sends: opcode (1 byte) + revision (4 bytes big-endian)
     * 2. Server responds: status (1 byte) + server_key (8 bytes) if status == 0
     */
    protected open suspend fun performLogin(): Boolean {
        try {
            setState(ConnectionState.HANDSHAKING)

            // Step 1: Send login opcode and revision
            // Server expects: opcode 14 + 4-byte revision (big-endian)
            val handshake = ByteBuffer.allocate(5)
            handshake.writeUByte(14) // Login connection type
            handshake.writeInt(config.revision) // Revision as 4-byte big-endian int
            sendRaw(handshake.toByteArray())

            log("Sent login handshake with revision ${config.revision}")

            // Wait for server response with server key
            // This will be handled by onHandshakeResponse()
            return true
        } catch (e: Exception) {
            log("Login failed: ${e.message}")
            setState(ConnectionState.ERROR)
            _events.send(ClientEvent.LoginFailed(-1, e.message ?: "Unknown error"))
            return false
        }
    }

    /**
     * Handle handshake response from server
     */
    protected suspend fun onHandshakeResponse(data: ByteArray) {
        log("onHandshakeResponse called with ${data.size} bytes: ${data.take(20).joinToString { it.toString() }}")

        val buffer = ByteBuffer.wrap(data)
        val responseCode = buffer.readUByte()

        log("Handshake response code: $responseCode (expected 0 for ExchangeKeys)")

        if (responseCode != 0) {
            log("Handshake failed with code: $responseCode")
            _events.send(ClientEvent.LoginFailed(responseCode, "Handshake failed"))
            setState(ConnectionState.ERROR)
            return
        }

        if (buffer.remaining < 8) {
            log("ERROR: Not enough data for server key, remaining=${buffer.remaining}")
            _events.send(ClientEvent.LoginFailed(-1, "Invalid handshake response: missing server key"))
            setState(ConnectionState.ERROR)
            return
        }

        // Read server key (8 bytes)
        serverKey = buffer.readLong()
        log("Received server key: $serverKey, now sending login block")

        // Now send login credentials
        setState(ConnectionState.LOGGING_IN)
        sendLoginBlock()
    }

    /**
     * Send the login block with credentials
     *
     * Server expects after login type (16):
     * - 2 bytes: packet_size (big-endian u16)
     * - packet_size bytes containing:
     *   - 4 bytes: revision (int, big-endian)
     *   - 1 byte: low_memory flag
     *   - 2 bytes: rsa_size (ushort, big-endian)
     *   - rsa_size bytes: RSA block
     *   - remaining: username string (null-terminated)
     */
    protected suspend fun sendLoginBlock() {
        // Generate client seeds for ISAAC
        val clientSeed1 = Random.nextInt()
        val clientSeed2 = Random.nextInt()
        val serverSeedHigh = (serverKey shr 32).toInt()
        val serverSeedLow = serverKey.toInt()

        // Initialize ISAAC
        isaacPair = IsaacPair.forClient(intArrayOf(clientSeed1, clientSeed2, serverSeedHigh, serverSeedLow))

        // Build RSA block (normally encrypted, simplified here)
        // Contains: magic(1) + seeds(16) + uid(4) + password(string)
        val rsaBlock = ByteBuffer.allocate(256)
        rsaBlock.writeUByte(10) // RSA magic number
        rsaBlock.writeInt(clientSeed1)
        rsaBlock.writeInt(clientSeed2)
        rsaBlock.writeInt(serverSeedHigh)
        rsaBlock.writeInt(serverSeedLow)
        rsaBlock.writeInt(0) // User ID (0 for new)
        rsaBlock.writeString(password) // Password in RSA block

        val rsaData = rsaBlock.toByteArray()

        // Calculate packet size (everything after the 2-byte size field)
        // revision(4) + low_memory(1) + rsa_size(2) + rsaData.size + username_string
        val usernameBytes = username.encodeToByteArray()
        val packetSize = 4 + 1 + 2 + rsaData.size + usernameBytes.size + 1 // +1 for null terminator

        // Build login packet
        val loginPacket = ByteBuffer.allocate(1 + 2 + packetSize)
        loginPacket.writeUByte(16) // New connection login opcode
        loginPacket.writeUShort(packetSize) // 2-byte packet size

        // Packet content:
        loginPacket.writeInt(config.revision) // 4-byte revision
        loginPacket.writeUByte(0) // Low memory flag
        loginPacket.writeUShort(rsaData.size) // 2-byte RSA block size
        loginPacket.writeBytes(rsaData) // RSA block
        loginPacket.writeBytes(usernameBytes) // Username
        loginPacket.writeUByte(0) // Null terminator for string

        log("Sending login block: packetSize=$packetSize, rsaSize=${rsaData.size}, username=$username")
        sendRaw(loginPacket.toByteArray())
    }

    /**
     * Handle login response from server
     */
    protected suspend fun onLoginResponse(data: ByteArray) {
        log("onLoginResponse called with ${data.size} bytes: ${data.take(20).joinToString { it.toString() }}")

        val buffer = ByteBuffer.wrap(data)
        val responseCode = buffer.readUByte()

        log("Login response code: $responseCode (expected ${LoginResponse.SUCCESS} for Success)")

        if (responseCode == LoginResponse.SUCCESS) {
            if (buffer.remaining < 5) {
                log("ERROR: Not enough data for login success, remaining=${buffer.remaining}")
                _events.send(ClientEvent.LoginFailed(-1, "Invalid login response: incomplete data"))
                setState(ConnectionState.ERROR)
                return
            }

            val rights = PlayerRights.fromId(buffer.readUByte())
            val flagged = buffer.readUByte() == 1
            val playerIndex = buffer.readUShort()
            val member = buffer.readUByte() == 1

            log("Login successful! Rights=$rights, Index=$playerIndex, Member=$member, Flagged=$flagged")

            gameState.setPlayerInfo(username, playerIndex, rights, member)

            setState(ConnectionState.CONNECTED)
            startPingInterval()
            reconnectAttempts = 0
            initPacketsReceived = false // Reset - expect raw init packets next

            log("Emitting LoginSuccess event")
            _events.send(ClientEvent.LoginSuccess(rights, playerIndex, member))
            log("LoginSuccess event emitted")
        } else {
            val message = LoginResponse.getMessage(responseCode)
            log("Login failed with code $responseCode: $message")
            setState(ConnectionState.ERROR)
            _events.send(ClientEvent.LoginFailed(responseCode, message))
        }
    }

    // ============ Packet Processing ============

    // Track whether we've received the first batch of init packets (sent without ISAAC)
    protected var initPacketsReceived = false

    /**
     * Process incoming game packet
     */
    protected suspend fun processGamePacket(data: ByteArray) {
        if (data.isEmpty()) return

        try {
            val buffer = ByteBuffer.wrap(data)
            log(
                "processGamePacket: ${data.size} bytes, initPacketsReceived=$initPacketsReceived, first bytes: ${
                    data.take(
                        10
                    ).joinToString { it.toUByte().toString() }
                }"
            )

            while (buffer.hasRemaining) {
                val rawOpcode = buffer.readUByte()

                // Server sends init packets WITHOUT ISAAC encoding
                // Only decode with ISAAC after init packets are done
                // For now, try raw opcode first - if it's a known packet, use it
                // Otherwise try ISAAC decoding
                val opcode = if (!initPacketsReceived) {
                    // First batch - try raw opcode (no ISAAC)
                    rawOpcode
                } else {
                    // After init, use ISAAC decoding
                    val pair = isaacPair
                    if (pair != null) {
                        pair.decodeOpcode(rawOpcode)
                    } else {
                        rawOpcode
                    }
                }

                val packetSize = PacketSize.getServerPacketSize(opcode)
                log("Packet: rawOpcode=$rawOpcode, opcode=$opcode, packetSize=$packetSize, remaining=${buffer.remaining}")

                val payloadSize = when (packetSize) {
                    -1 -> {
                        if (!buffer.hasRemaining) {
                            log("ERROR: Expected variable byte size but no data remaining")
                            return
                        }
                        buffer.readUByte()
                    }

                    -2 -> {
                        if (buffer.remaining < 2) {
                            log("ERROR: Expected variable short size but only ${buffer.remaining} bytes remaining")
                            return
                        }
                        buffer.readUShort()
                    }

                    else -> packetSize
                }

                log("Payload size: $payloadSize, remaining: ${buffer.remaining}")

                val payload = if (payloadSize > 0 && buffer.remaining >= payloadSize) {
                    ByteBuffer.wrap(buffer.readBytes(payloadSize))
                } else if (payloadSize > 0) {
                    log("WARN: Not enough data for payload, need $payloadSize but have ${buffer.remaining}")
                    ByteBuffer.allocate(0)
                } else {
                    ByteBuffer.allocate(0)
                }

                log("Received packet: opcode=$opcode, size=$payloadSize")

                // Handle known packets
                try {
                    handlePacket(opcode, payload)
                } catch (e: Exception) {
                    log("ERROR handling packet opcode=$opcode: ${e.message}")
                }

                // Emit event for custom handlers
                _events.send(ClientEvent.PacketReceived(opcode, payload))
            }
        } catch (e: Exception) {
            log("ERROR in processGamePacket: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Handle specific packet types
     */
    protected open fun handlePacket(opcode: Int, payload: ByteBuffer) {
        when (opcode) {
            ServerOpcode.SKILL_UPDATE -> handleSkillUpdate(payload)
            ServerOpcode.SYSTEM_MESSAGE, ServerOpcode.CHAT_MESSAGE -> handleMessage(payload)
            ServerOpcode.MAP_REGION -> handleMapRegion(payload)
            ServerOpcode.SET_PLAYER_OPTION -> handlePlayerOption(payload)
            ServerOpcode.RUN_ENERGY -> handleRunEnergy(payload)
            ServerOpcode.WEIGHT -> handleWeight(payload)
            ServerOpcode.LOGOUT -> handleLogout()
            // Bank packets
            ServerOpcode.BANK_OPEN -> handleBankOpen(payload)
            ServerOpcode.BANK_UPDATE -> handleBankUpdate(payload)
            ServerOpcode.BANK_SETTINGS -> handleBankSettings(payload)
            ServerOpcode.BANK_TAB_INFO -> handleBankTabInfo(payload)
        }
    }

    protected fun handleSkillUpdate(payload: ByteBuffer) {
        if (payload.remaining < 6) return
        val skillId = payload.readUByte()
        val level = payload.readUByte()
        val experience = payload.readInt()
        gameState.updateSkill(skillId, level, experience)
    }

    protected fun handleMessage(payload: ByteBuffer) {
        val text = payload.readString()
        gameState.addMessage(text, MessageType.GAME)
    }

    protected fun handleMapRegion(payload: ByteBuffer) {
        if (payload.remaining < 4) return
        val regionX = payload.readUShort()
        val regionY = payload.readUShort()
        gameState.setMapRegion(regionX * 8, regionY * 8)
    }

    protected fun handlePlayerOption(payload: ByteBuffer) {
        val slot = payload.readUByte()
        val priority = payload.readUByte() == 1
        val text = payload.readString()
        gameState.setPlayerOption(slot, text, priority)
    }

    protected fun handleRunEnergy(payload: ByteBuffer) {
        if (payload.remaining < 1) return
        gameState.runEnergy = payload.readUByte()
    }

    protected fun handleWeight(payload: ByteBuffer) {
        if (payload.remaining < 2) return
        gameState.weight = payload.readShort()
    }

    protected fun handleLogout() {
        scope.launch {
            logout()
        }
    }

    // ============ Bank Packet Handlers ============

    protected fun handleBankOpen(payload: ByteBuffer) {
        if (payload.remaining < 2) return
        val capacity = payload.readUShort()
        log("Bank opened with capacity: $capacity")
        gameState.openBank(capacity)
    }

    protected fun handleBankUpdate(payload: ByteBuffer) {
        if (payload.remaining < 2) return

        val itemCount = payload.readUShort()

        if (itemCount == 0) {
            // Empty bank or clear
            gameState.clearBank()
            return
        }

        // Check if this is a full update or single slot update
        // Full update: itemCount items with tab/slot/id/amount/placeholder each
        // Single slot: 1 item update (itemCount would be 1 with specific format)

        val items = mutableListOf<com.rustscape.client.game.BankItem>()

        for (i in 0 until itemCount) {
            if (payload.remaining < 10) break // Need at least 10 bytes per item

            val tab = payload.readUByte()
            val slot = payload.readUShort()
            val itemId = payload.readUShort()
            val amount = payload.readInt()
            val placeholder = payload.readUByte() == 1

            items.add(
                com.rustscape.client.game.BankItem(
                    slot = slot,
                    itemId = itemId,
                    amount = amount,
                    tab = tab,
                    placeholder = placeholder
                )
            )
        }

        if (itemCount == 1 && items.size == 1) {
            // Single slot update
            val item = items[0]
            gameState.updateBankSlot(item.tab, item.slot, item.itemId, item.amount, item.placeholder)
        } else {
            // Full bank update
            gameState.setBankItems(items)
        }

        log("Bank update received: ${items.size} items")
    }

    protected fun handleBankSettings(payload: ByteBuffer) {
        if (payload.remaining < 6) return

        val noteMode = payload.readUByte() == 1
        val placeholders = payload.readUByte() == 1
        val withdrawX = payload.readInt()

        log("Bank settings: noteMode=$noteMode, placeholders=$placeholders, withdrawX=$withdrawX")
        gameState.updateBankSettings(noteMode, placeholders, withdrawX)
    }

    protected fun handleBankTabInfo(payload: ByteBuffer) {
        if (payload.remaining < 1) return

        val tabCount = payload.readUByte()
        val tabSizes = IntArray(minOf(tabCount, 9))

        for (i in 0 until minOf(tabCount, 9)) {
            if (payload.remaining < 2) break
            tabSizes[i] = payload.readUShort()
        }

        log("Bank tabs: ${tabSizes.contentToString()}")
        gameState.updateBankTabSizes(tabSizes)
    }

    // ============ Utilities ============

    protected fun setState(state: ConnectionState) {
        _connectionState.value = state
        scope.launch {
            _events.send(ClientEvent.StateChanged(state))
        }
    }

    protected fun startPingInterval() {
        stopPingInterval()
        pingJob = scope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                delay(config.pingIntervalMs)
                sendPing()
            }
        }
    }

    protected fun stopPingInterval() {
        pingJob?.cancel()
        pingJob = null
    }

    protected suspend fun attemptReconnect() {
        if (reconnectAttempts >= config.reconnectAttempts) {
            log("Max reconnect attempts reached")
            setState(ConnectionState.ERROR)
            _events.send(ClientEvent.Error("Failed to reconnect after ${config.reconnectAttempts} attempts"))
            return
        }

        reconnectAttempts++
        setState(ConnectionState.RECONNECTING)
        _events.send(ClientEvent.Reconnecting)

        log("Attempting reconnect ($reconnectAttempts/${config.reconnectAttempts})...")
        delay(config.reconnectDelayMs)

        if (connect()) {
            performLogin()
        }
    }

    protected open fun log(message: String) {
        if (config.debug) {
            println("[GameClient] $message")
        }
    }

    /**
     * Clean up resources
     */
    open fun dispose() {
        stopPingInterval()
        scope.cancel()
        _events.close()
    }
}
