package com.rustscape.client

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.rustscape.client.game.GameState
import com.rustscape.client.network.ClientConfig
import com.rustscape.client.network.ClientEvent
import com.rustscape.client.network.ConnectionState
import com.rustscape.client.network.GameClient
import com.rustscape.client.ui.App
import com.rustscape.client.ui.AppState
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.dom.MessageEvent
import org.w3c.dom.WebSocket
import org.w3c.dom.events.Event

/**
 * WASM/JS web application entry point
 * Launches the Rustscape client in a browser canvas
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Read configuration from URL parameters or defaults
    val urlParams = window.location.search
    val config = parseConfig(urlParams)

    // Application state
    val appState = AppState().apply {
        this.config = config
    }

    // Create the web game client
    val gameClient = WebGameClient(config, appState.gameState)

    // Launch the Compose canvas application
    CanvasBasedWindow(
        canvasElementId = "gameCanvas",
        title = "Rustscape"
    ) {
        App(
            appState = appState,
            onLogin = { username, password, rememberMe ->
                gameClient.login(username, password)
            },
            onRegister = { username, email, password ->
                // Registration would typically go through HTTP API
                console.log("Register: $username, $email")
                false
            },
            onLogout = {
                gameClient.logout()
            },
            onSendChat = { message ->
                gameClient.sendChat(message)
            },
            onSendCommand = { command ->
                gameClient.sendCommand(command)
            },
            clientEvents = gameClient.events
        )
    }
}

/**
 * Parse configuration from URL parameters
 */
private fun parseConfig(urlParams: String): ClientConfig {
    val params = urlParams.removePrefix("?")
        .split("&")
        .filter { it.isNotEmpty() }
        .associate {
            val parts = it.split("=", limit = 2)
            parts[0] to (parts.getOrNull(1) ?: "")
        }

    return ClientConfig(
        serverHost = params["host"] ?: window.location.hostname.ifEmpty { "localhost" },
        serverPort = params["port"]?.toIntOrNull() ?: 43594,
        wsPath = params["wsPath"] ?: "/ws",
        revision = params["revision"]?.toIntOrNull() ?: 317,
        useWebSocket = true,
        debug = params["debug"] == "true"
    )
}

/**
 * Web-specific game client implementation using browser WebSocket API
 */
class WebGameClient(
    config: ClientConfig,
    gameState: GameState
) : GameClient(config, gameState) {

    private var webSocket: WebSocket? = null

    override suspend fun connect(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                setState(ConnectionState.CONNECTING)

                val protocol = if (window.location.protocol == "https:") "wss" else "ws"
                val url = "$protocol://${config.serverHost}:${config.serverPort}${config.wsPath}"

                log("Connecting to $url")

                val ws = WebSocket(url)
                ws.binaryType = "arraybuffer".toJsString().unsafeCast<org.w3c.dom.BinaryType>()

                ws.onopen = { _: Event ->
                    log("WebSocket connected")
                    webSocket = ws
                    setState(ConnectionState.CONNECTED)
                    if (continuation.isActive) {
                        continuation.resume(true) {}
                    }
                    Unit
                }

                ws.onclose = { _: Event ->
                    log("WebSocket closed")
                    webSocket = null
                    setState(ConnectionState.DISCONNECTED)
                    scope.launch {
                        _events.send(ClientEvent.Disconnected)
                    }
                    if (continuation.isActive) {
                        continuation.resume(false) {}
                    }
                    Unit
                }

                ws.onerror = { _: Event ->
                    log("WebSocket error")
                    setState(ConnectionState.ERROR)
                    scope.launch {
                        _events.send(ClientEvent.Error("WebSocket connection error"))
                    }
                    if (continuation.isActive) {
                        continuation.resume(false) {}
                    }
                    Unit
                }

                ws.onmessage = { event: MessageEvent ->
                    val data = event.data
                    if (data is ArrayBuffer) {
                        val bytes = arrayBufferToByteArray(data)
                        onDataReceived(bytes)
                    }
                    Unit
                }

                webSocket = ws

            } catch (e: Exception) {
                log("Connection failed: ${e.message}")
                setState(ConnectionState.ERROR)
                scope.launch {
                    _events.send(ClientEvent.Error("Connection failed: ${e.message}", e))
                }
                if (continuation.isActive) {
                    continuation.resume(false) {}
                }
            }
        }
    }

    override suspend fun disconnect() {
        try {
            webSocket?.close()
            webSocket = null
            setState(ConnectionState.DISCONNECTED)
        } catch (e: Exception) {
            log("Disconnect error: ${e.message}")
        }
    }

    override suspend fun sendRaw(data: ByteArray) {
        try {
            val arrayBuffer = byteArrayToArrayBuffer(data)
            webSocket?.send(arrayBuffer)
        } catch (e: Exception) {
            log("Send error: ${e.message}")
            _events.send(ClientEvent.Error("Send failed: ${e.message}", e))
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

    /**
     * Convert ArrayBuffer to ByteArray
     */
    private fun arrayBufferToByteArray(buffer: ArrayBuffer): ByteArray {
        val int8Array = Int8Array(buffer)
        return ByteArray(int8Array.length) { i -> int8Array[i] }
    }

    /**
     * Convert ByteArray to ArrayBuffer
     */
    private fun byteArrayToArrayBuffer(bytes: ByteArray): ArrayBuffer {
        val buffer = ArrayBuffer(bytes.size)
        val view = Uint8Array(buffer)
        for (i in bytes.indices) {
            view[i] = bytes[i]
        }
        return buffer
    }

    public override fun dispose() {
        super.dispose()
        webSocket?.close()
        webSocket = null
    }
}

/**
 * Console logging for web
 */
@JsName("console")
private external object console {
    fun log(message: String)
    fun error(message: String)
    fun warn(message: String)
}
