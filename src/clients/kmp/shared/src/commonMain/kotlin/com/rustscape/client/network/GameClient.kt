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
     * Send a walk command
     */
    suspend fun sendWalk(destX: Int, destY: Int, running: Boolean = false) {
        val buffer = ByteBuffer.allocate(5)
        buffer.writeShortLEA(destY)
        buffer.writeShortA(destX)
        buffer.writeByteC(if (running) 1 else 0)
        sendPacket(ClientOpcode.WALK, buffer.toByteArray())
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

    // ============ Login Process ============

    /**
     * Perform the login handshake and authentication
     */
    protected open suspend fun performLogin(): Boolean {
        try {
            setState(ConnectionState.HANDSHAKING)

            // Step 1: Send connection type and username hash
            val usernameHash = (username.lowercase().hashCode() shr 16) and 31
            val handshake = ByteBuffer.allocate(2)
            handshake.writeUByte(14) // Login connection type
            handshake.writeUByte(usernameHash)
            sendRaw(handshake.toByteArray())

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
        val buffer = ByteBuffer.wrap(data)
        val responseCode = buffer.readUByte()

        if (responseCode != 0) {
            log("Handshake failed with code: $responseCode")
            _events.send(ClientEvent.LoginFailed(responseCode, "Handshake failed"))
            setState(ConnectionState.ERROR)
            return
        }

        // Read server key (8 bytes)
        serverKey = buffer.readLong()
        log("Received server key: $serverKey")

        // Now send login credentials
        setState(ConnectionState.LOGGING_IN)
        sendLoginBlock()
    }

    /**
     * Send the login block with credentials
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
        val rsaBlock = ByteBuffer.allocate(128)
        rsaBlock.writeUByte(10) // RSA magic number
        rsaBlock.writeInt(clientSeed1)
        rsaBlock.writeInt(clientSeed2)
        rsaBlock.writeInt(serverSeedHigh)
        rsaBlock.writeInt(serverSeedLow)
        rsaBlock.writeInt(0) // User ID (0 for new)
        rsaBlock.writeString(username)
        rsaBlock.writeString(password)

        val rsaData = rsaBlock.toByteArray()

        // Build login packet
        val loginPacket = ByteBuffer.allocate(rsaData.size + 40)
        loginPacket.writeUByte(16) // New connection login opcode
        loginPacket.writeUByte(rsaData.size + 36 + 1 + 1) // Login block size

        loginPacket.writeUByte(255) // Magic byte
        loginPacket.writeUShort(config.revision) // Client revision

        loginPacket.writeUByte(0) // Low memory flag
        for (i in 0 until 9) {
            loginPacket.writeInt(0) // CRC values (simplified)
        }

        loginPacket.writeUByte(rsaData.size) // RSA block size
        loginPacket.writeBytes(rsaData)

        sendRaw(loginPacket.toByteArray())
    }

    /**
     * Handle login response from server
     */
    protected suspend fun onLoginResponse(data: ByteArray) {
        val buffer = ByteBuffer.wrap(data)
        val responseCode = buffer.readUByte()

        if (responseCode == LoginResponse.SUCCESS) {
            val rights = PlayerRights.fromId(buffer.readUByte())
            val flagged = buffer.readUByte() == 1
            val playerIndex = buffer.readUShort()
            val member = buffer.readUByte() == 1

            log("Login successful! Rights=$rights, Index=$playerIndex, Member=$member")

            gameState.setPlayerInfo(username, playerIndex, rights, member)

            setState(ConnectionState.CONNECTED)
            startPingInterval()
            reconnectAttempts = 0

            _events.send(ClientEvent.LoginSuccess(rights, playerIndex, member))
        } else {
            val message = LoginResponse.getMessage(responseCode)
            log("Login failed: $message")
            setState(ConnectionState.ERROR)
            _events.send(ClientEvent.LoginFailed(responseCode, message))
        }
    }

    // ============ Packet Processing ============

    /**
     * Process incoming game packet
     */
    protected suspend fun processGamePacket(data: ByteArray) {
        if (data.isEmpty()) return

        val buffer = ByteBuffer.wrap(data)
        val pair = isaacPair ?: return

        while (buffer.hasRemaining) {
            val encodedOpcode = buffer.readUByte()
            val opcode = pair.decodeOpcode(encodedOpcode)

            val packetSize = PacketSize.getServerPacketSize(opcode)
            val payloadSize = when (packetSize) {
                -1 -> buffer.readUByte()
                -2 -> buffer.readUShort()
                else -> packetSize
            }

            val payload = if (payloadSize > 0 && buffer.remaining >= payloadSize) {
                ByteBuffer.wrap(buffer.readBytes(payloadSize))
            } else {
                ByteBuffer.allocate(0)
            }

            log("Received packet: opcode=$opcode, size=$payloadSize")

            // Handle known packets
            handlePacket(opcode, payload)

            // Emit event for custom handlers
            _events.send(ClientEvent.PacketReceived(opcode, payload))
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
            // Add more handlers as needed
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

    protected fun log(message: String) {
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
