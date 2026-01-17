package com.rustscape.client

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.rustscape.client.audio.WebSoundManager
import com.rustscape.client.game.GameState
import com.rustscape.client.network.ClientConfig
import com.rustscape.client.network.ClientEvent
import com.rustscape.client.network.ConnectionState
import com.rustscape.client.network.GameClient
import com.rustscape.client.ui.App
import com.rustscape.client.ui.AppState
import com.rustscape.client.ui.components.LocalSoundManager
import com.rustscape.client.ui.components.PRELOAD_UI_SOUNDS
import com.rustscape.client.ui.components.RSSound
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
 * External JS functions for loading screen control
 * These call into window.rustscape object defined in index.html
 */
@JsFun("(progress, status) => { if (window.rustscape) window.rustscape.updateProgress(progress, status); }")
private external fun jsUpdateProgress(progress: Int, status: String)

@JsFun("() => { if (window.rustscape) window.rustscape.hideLoading(); }")
private external fun jsHideLoading()

@JsFun("(message) => { if (window.rustscape) window.rustscape.showError(message); }")
private external fun jsShowError(message: String)

/**
 * WASM/JS web application entry point
 * Launches the Rustscape client in a browser canvas
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Update loading progress - initializing WASM
    jsUpdateProgress(20, "Loading WebAssembly...")

    // Read configuration from URL parameters or defaults
    val urlParams = window.location.search
    val config = parseConfig(urlParams)

    jsUpdateProgress(40, "Initializing client...")

    // Application state
    val appState = AppState().apply {
        this.config = config
    }

    // Create the web game client
    val gameClient = WebGameClient(config, appState.gameState)

    jsUpdateProgress(60, "Setting up UI...")

    // Launch the Compose canvas application
    CanvasBasedWindow(
        canvasElementId = "gameCanvas",
        title = "Rustscape"
    ) {
        // Initialize sound manager
        val soundManager = remember { WebSoundManager() }

        // Cleanup sound manager on dispose
        DisposableEffect(Unit) {
            // Preload common UI sounds
            soundManager.preload(PRELOAD_UI_SOUNDS)
            onDispose {
                soundManager.dispose()
            }
        }

        // Hide loading screen once Compose is ready
        LaunchedEffect(Unit) {
            jsUpdateProgress(80, "Starting game...")
            // Small delay to ensure canvas is rendered
            delay(100)
            jsUpdateProgress(100, "Ready!")
            delay(200)
            jsHideLoading()
        }

        // Provide sound manager to entire app via CompositionLocal
        CompositionLocalProvider(LocalSoundManager provides soundManager) {
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

    // For web clients, use the browser's port (same origin) unless explicitly specified
    // This ensures WebSocket connects through nginx on the same port as the page
    val browserPort = window.location.port.toIntOrNull() ?: if (window.location.protocol == "https:") 443 else 80

    return ClientConfig(
        serverHost = params["host"] ?: window.location.hostname.ifEmpty { "localhost" },
        serverPort = params["port"]?.toIntOrNull() ?: browserPort,
        wsPath = params["wsPath"] ?: "/ws",
        revision = params["revision"]?.toIntOrNull() ?: 530,
        useWebSocket = true,
        debug = params["debug"] != "false"  // Enable debug by default for WASM
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

    /**
     * Override log to use browser console instead of println
     */
    override fun log(message: String) {
        if (config.debug) {
            console.log("[GameClient] $message")
        }
    }

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
                    console.log("[WebGameClient] WebSocket connected, raw socket open")
                    webSocket = ws
                    // Don't set CONNECTED here - we're not fully connected until login succeeds
                    // Keep state at CONNECTING until performLogin sets it to HANDSHAKING
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(true))
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
                        continuation.resumeWith(Result.success(false))
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
                        continuation.resumeWith(Result.success(false))
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
                    continuation.resumeWith(Result.success(false))
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
        val currentState = _connectionState.value
        console.log("[WebGameClient] onDataReceived: ${data.size} bytes in state: $currentState")
        scope.launch {
            when (currentState) {
                ConnectionState.HANDSHAKING -> {
                    console.log("[WebGameClient] Routing to onHandshakeResponse")
                    onHandshakeResponse(data)
                }

                ConnectionState.LOGGING_IN -> {
                    console.log("[WebGameClient] Routing to onLoginResponse")
                    onLoginResponse(data)
                }

                ConnectionState.CONNECTED -> {
                    console.log("[WebGameClient] Routing to processGamePacket")
                    processGamePacket(data)
                }

                ConnectionState.CONNECTING -> {
                    // Server responded while we're still connecting - treat as handshake response
                    // This can happen if the response arrives before performLogin() sets HANDSHAKING
                    console.log("[WebGameClient] Received data while CONNECTING - buffering or treating as handshake")
                    onHandshakeResponse(data)
                }

                else -> {
                    console.log("[WebGameClient] ERROR: Received data in unexpected state: $currentState")
                }
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
