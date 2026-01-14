package com.rustscape.client

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.rustscape.client.game.GameState
import com.rustscape.client.network.ClientConfig
import com.rustscape.client.network.ClientEvent
import com.rustscape.client.network.ConnectionState
import com.rustscape.client.ui.App
import com.rustscape.client.ui.AppState
import kotlinx.coroutines.flow.MutableSharedFlow

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

    Window(
        onCloseRequest = {
            // Cleanup before exit
            gameClient?.dispose()
            exitApplication()
        },
        state = windowState,
        title = "Rustscape",
        resizable = true
    ) {
        // Set minimum window size
        window.minimumSize = java.awt.Dimension(800, 600)

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
            clientEvents = gameClient?.events
        )
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

    private var webSocket: io.ktor.client.HttpClient? = null
    private var session: io.ktor.client.plugins.websocket.WebSocketSession? = null

    init {
        // Initialize Ktor client with WebSocket support
        webSocket = io.ktor.client.HttpClient(io.ktor.client.engine.cio.CIO) {
            install(io.ktor.client.plugins.websocket.WebSockets) {
                pingInterval = config.pingIntervalMs
            }
        }
    }

    override suspend fun connect(): Boolean {
        return try {
            setState(com.rustscape.client.network.ConnectionState.CONNECTING)

            val protocol = if (config.useWebSocket) "ws" else "wss"
            val url = "$protocol://${config.serverHost}:${config.serverPort}${config.wsPath}"

            log("Connecting to $url")

            webSocket?.webSocket(url) {
                session = this
                setState(com.rustscape.client.network.ConnectionState.CONNECTED)

                // Receive loop
                for (frame in incoming) {
                    when (frame) {
                        is io.ktor.websocket.Frame.Binary -> {
                            onDataReceived(frame.data)
                        }

                        is io.ktor.websocket.Frame.Text -> {
                            // Handle text frames if needed
                        }

                        is io.ktor.websocket.Frame.Close -> {
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
            session?.send(io.ktor.websocket.Frame.Binary(true, data))
        } catch (e: Exception) {
            log("Send error: ${e.message}")
            _events.send(com.rustscape.client.network.ClientEvent.Error("Send failed: ${e.message}", e))
        }
    }

    override fun onDataReceived(data: ByteArray) {
        scope.launch {
            when (_connectionState.value) {
                com.rustscape.client.network.ConnectionState.HANDSHAKING -> onHandshakeResponse(data)
                com.rustscape.client.network.ConnectionState.LOGGING_IN -> onLoginResponse(data)
                com.rustscape.client.network.ConnectionState.CONNECTED -> processGamePacket(data)
                else -> log("Received data in unexpected state: ${_connectionState.value}")
            }
        }
    }

    override fun dispose() {
        super.dispose()
        webSocket?.close()
        webSocket = null
    }
}
