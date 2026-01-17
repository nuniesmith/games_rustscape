package com.rustscape.client

import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.rustscape.client.game.GameState
import com.rustscape.client.network.ClientConfig
import com.rustscape.client.network.ClientEvent
import com.rustscape.client.network.ConnectionState
import com.rustscape.client.sprites.DesktopSpriteLoader
import com.rustscape.client.ui.App
import com.rustscape.client.ui.AppState
import com.rustscape.client.ui.EntityInteraction
import com.rustscape.client.ui.components.GameEntity
import com.rustscape.client.ui.components.KeyboardModifierState
import com.rustscape.client.ui.components.LocalKeyboardModifiers
import com.rustscape.client.ui.components.LocalShiftClickSettings
import com.rustscape.client.ui.components.LocalSpriteLoader
import com.rustscape.client.ui.components.ShiftClickSettings
import androidx.compose.ui.input.key.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Desktop application entry point
 * Launches the Rustscape client as a desktop window
 */
fun main() = application {
    // Application state
    val appState = remember { AppState() }

    // Client configuration - can be loaded from file
    val config = remember {
        ClientConfig(
            serverHost = System.getProperty("rustscape.server.host", "localhost"),
            serverPort = System.getProperty("rustscape.server.port", "43594").toIntOrNull() ?: 43594,
            wsPath = "/ws",
            revision = 317,
            useWebSocket = true,
            debug = System.getProperty("rustscape.debug", "false").toBoolean()
        )
    }

    // Event flow for client events (will be connected to actual GameClient)
    val clientEvents = remember { MutableSharedFlow<ClientEvent>() }

    // Desktop game client (placeholder - implement platform-specific WebSocket client)
    var gameClient: DesktopGameClient? by remember { mutableStateOf(null) }

    // Initialize client on launch
    LaunchedEffect(Unit) {
        gameClient = DesktopGameClient(config, appState.gameState)
    }

    // Window state with saved position
    val windowState = rememberWindowState(
        placement = WindowPlacement.Floating,
        position = WindowPosition(Alignment.Center),
        size = DpSize(1024.dp, 768.dp)
    )

    // Track keyboard modifiers at window level
    val keyboardModifiersForWindow = remember { KeyboardModifierState() }

    Window(
        onCloseRequest = {
            // Cleanup before exit
            gameClient?.dispose()
            exitApplication()
        },
        state = windowState,
        title = "Rustscape",
        resizable = true,
        onPreviewKeyEvent = { keyEvent ->
            // Track modifier key states
            when (keyEvent.key) {
                Key.ShiftLeft, Key.ShiftRight -> {
                    keyboardModifiersForWindow.isShiftHeld = keyEvent.type == KeyEventType.KeyDown
                    false
                }

                Key.CtrlLeft, Key.CtrlRight -> {
                    keyboardModifiersForWindow.isCtrlHeld = keyEvent.type == KeyEventType.KeyDown
                    false
                }

                Key.AltLeft, Key.AltRight -> {
                    keyboardModifiersForWindow.isAltHeld = keyEvent.type == KeyEventType.KeyDown
                    false
                }

                else -> false
            }
        }
    ) {
        // Set minimum window size
        window.minimumSize = java.awt.Dimension(800, 600)

        // Initialize sprite loader
        val spriteLoader = remember { DesktopSpriteLoader() }

        // Use the window-level keyboard modifiers
        val keyboardModifiers = keyboardModifiersForWindow
        val shiftClickSettings = remember { ShiftClickSettings() }

        // Preload common sprites
        LaunchedEffect(spriteLoader) {
            spriteLoader.preloadCommon()
        }

        // Handle keyboard events for modifier tracking
        LaunchedEffect(Unit) {
            // Desktop uses window-level key events
            // The modifier state will be updated via onPreviewKeyEvent in the window
        }

        // Provide sprite loader and keyboard state to entire app via CompositionLocal
        CompositionLocalProvider(
            LocalSpriteLoader provides spriteLoader,
            LocalKeyboardModifiers provides keyboardModifiers,
            LocalShiftClickSettings provides shiftClickSettings
        ) {
            App(
                appState = appState,
                onLogin = { username, password, rememberMe ->
                    gameClient?.login(username, password) ?: false
                },
                onRegister = { username, email, password ->
                    // Registration would typically go through HTTP API
                    println("Register: $username, $email")
                    false
                },
                onLogout = {
                    gameClient?.logout()
                },
                onSendChat = { message ->
                    gameClient?.sendChat(message)
                },
                onSendCommand = { command ->
                    gameClient?.sendCommand(command)
                },
                onWalkTo = { x, y, running ->
                    gameClient?.sendWalk(x, y, running)
                },
                onMinimapWalk = { x, y, running ->
                    gameClient?.sendMinimapWalk(x, y, running)
                },
                onEntityInteraction = { interaction ->
                    gameClient?.let { client ->
                        handleEntityInteraction(client, interaction)
                    }
                },
                // Bank operation callbacks
                onBankClose = {
                    gameClient?.sendBankClose()
                },
                onBankWithdraw = { slot, itemId, amount, asNote ->
                    gameClient?.sendBankWithdraw(slot, itemId, amount, asNote)
                },
                onBankDeposit = { inventorySlot, itemId, amount ->
                    gameClient?.sendBankDeposit(inventorySlot, itemId, amount)
                },
                onBankDepositAll = {
                    gameClient?.sendBankDepositAll()
                },
                onBankDepositEquipment = {
                    gameClient?.sendBankDepositEquipment()
                },
                onBankTabSelect = { tab ->
                    gameClient?.sendBankTabSelect(tab)
                },
                onBankSearch = { query ->
                    gameClient?.sendBankSearch(query)
                },
                onBankMoveItem = { fromSlot, toSlot ->
                    gameClient?.sendBankMoveItem(fromSlot, toSlot)
                },
                onBankNoteMode = { enabled ->
                    gameClient?.sendBankNoteMode(enabled)
                },
                onBankWithdrawMode = { mode ->
                    gameClient?.sendBankWithdrawMode(mode)
                },
                // Inventory operation callbacks
                onItemDrop = { slot, itemId ->
                    gameClient?.sendItemDrop(slot, itemId)
                },
                onItemEquip = { slot, itemId ->
                    gameClient?.sendItemEquip(slot, itemId)
                },
                onItemUse = { slot, itemId ->
                    gameClient?.sendItemAction(itemId, slot, 3214, 1) // Use = action slot 1, interface 3214 = inventory
                },
                onInventorySwap = { fromSlot, toSlot ->
                    gameClient?.sendInventorySwap(fromSlot, toSlot)
                },
                onItemExamine = { itemId ->
                    gameClient?.sendItemExamine(itemId)
                },
                clientEvents = gameClient?.events
            )
        }
    }
}

/**
 * Desktop-specific game client implementation
 * Uses Ktor CIO engine for WebSocket connections
 */
class DesktopGameClient(
    config: ClientConfig,
    gameState: GameState
) : com.rustscape.client.network.GameClient(config, gameState) {

    private var httpClient: HttpClient? = null
    private var session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession? = null

    init {
        // Initialize Ktor client with WebSocket support
        httpClient = HttpClient(CIO) {
            install(WebSockets) {
                pingIntervalMillis = config.pingIntervalMs
            }
        }
    }

    override suspend fun connect(): Boolean {
        return try {
            setState(com.rustscape.client.network.ConnectionState.CONNECTING)

            val protocol = if (config.useWebSocket) "ws" else "wss"
            val url = "$protocol://${config.serverHost}:${config.serverPort}${config.wsPath}"

            log("Connecting to $url")

            httpClient?.webSocket(url) {
                session = this
                setState(com.rustscape.client.network.ConnectionState.CONNECTED)

                // Receive loop
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Binary -> {
                            onDataReceived(frame.data)
                        }

                        is Frame.Text -> {
                            // Handle text frames if needed
                        }

                        is Frame.Close -> {
                            log("Connection closed by server")
                            break
                        }

                        else -> {}
                    }
                }
            }

            true
        } catch (e: Exception) {
            log("Connection failed: ${e.message}")
            setState(com.rustscape.client.network.ConnectionState.ERROR)
            _events.send(com.rustscape.client.network.ClientEvent.Error("Connection failed: ${e.message}", e))
            false
        }
    }

    override suspend fun disconnect() {
        try {
            session?.close()
            session = null
            setState(com.rustscape.client.network.ConnectionState.DISCONNECTED)
        } catch (e: Exception) {
            log("Disconnect error: ${e.message}")
        }
    }

    override suspend fun sendRaw(data: ByteArray) {
        try {
            session?.send(Frame.Binary(true, data))
        } catch (e: Exception) {
            log("Send error: ${e.message}")
            _events.send(com.rustscape.client.network.ClientEvent.Error("Send failed: ${e.message}", e))
        }
    }

    override fun onDataReceived(data: ByteArray) {
        scope.launch {
            when (_connectionState.value) {
                ConnectionState.HANDSHAKING -> onHandshakeResponse(data)
                ConnectionState.LOGGING_IN -> onLoginResponse(data)
                ConnectionState.CONNECTED -> processGamePacket(data)
                else -> log("Received data in unexpected state: ${_connectionState.value}")
            }
        }
    }

    override fun dispose() {
        super.dispose()
        httpClient?.close()
        httpClient = null
    }
}

/**
 * Handle entity interactions by sending appropriate packets to the server
 */
private suspend fun handleEntityInteraction(gameClient: DesktopGameClient, interaction: EntityInteraction) {
    val entity = interaction.entity
    val action = interaction.action.lowercase()
    val slot = interaction.actionSlot

    println("[EntityInteraction] $action on ${entity.name} (slot $slot)")

    when (entity) {
        is GameEntity.Npc -> {
            when (action) {
                "examine" -> gameClient.sendNpcExamine(entity.id)
                else -> gameClient.sendNpcAction(entity.id, slot)
            }
        }

        is GameEntity.Player -> {
            // Player index would need to come from server - using a placeholder
            val playerIndex = entity.name.hashCode() and 0x7FF
            gameClient.sendPlayerAction(playerIndex, slot)
        }

        is GameEntity.GameObject -> {
            when (action) {
                "examine" -> gameClient.sendObjectExamine(entity.objectId)
                else -> gameClient.sendObjectAction(
                    entity.objectId,
                    entity.position.x,
                    entity.position.y,
                    slot
                )
            }
        }

        is GameEntity.GroundItem -> {
            when (action) {
                "examine" -> gameClient.sendItemExamine(entity.itemId)
                else -> gameClient.sendGroundItemAction(
                    entity.itemId,
                    entity.position.x,
                    entity.position.y,
                    slot
                )
            }
        }
    }
}
