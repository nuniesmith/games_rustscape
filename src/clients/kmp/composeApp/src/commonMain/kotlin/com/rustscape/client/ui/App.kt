package com.rustscape.client.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rustscape.client.game.GameState
import com.rustscape.client.game.PlayerRights
import com.rustscape.client.network.ClientConfig
import com.rustscape.client.network.ClientEvent
import com.rustscape.client.network.ConnectionState
import com.rustscape.client.ui.screens.GameScreen
import com.rustscape.client.ui.screens.LoginScreen
import com.rustscape.client.ui.theme.RustscapeTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Application screen state
 */
enum class AppScreen {
    LOGIN,
    GAME,
    LOADING
}

/**
 * Application state holder
 */
class AppState {
    var currentScreen by mutableStateOf(AppScreen.LOGIN)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var connectionState by mutableStateOf(ConnectionState.DISCONNECTED)

    // Game state
    val gameState = GameState()

    // Client configuration
    var config = ClientConfig()

    fun reset() {
        currentScreen = AppScreen.LOGIN
        isLoading = false
        errorMessage = null
        connectionState = ConnectionState.DISCONNECTED
        gameState.reset()
    }
}

/**
 * Main application composable
 * This is the root composable that manages navigation between screens
 */
@Composable
fun App(
    appState: AppState = remember { AppState() },
    onLogin: suspend (username: String, password: String, rememberMe: Boolean) -> Boolean = { _, _, _ -> false },
    onRegister: suspend (username: String, email: String, password: String) -> Boolean = { _, _, _ -> false },
    onLogout: suspend () -> Unit = {},
    onSendChat: suspend (message: String) -> Unit = {},
    onSendCommand: suspend (command: String) -> Unit = {},
    clientEvents: Flow<ClientEvent>? = null
) {
    val scope = rememberCoroutineScope()

    // Collect client events
    LaunchedEffect(clientEvents) {
        clientEvents?.collect { event ->
            when (event) {
                is ClientEvent.StateChanged -> {
                    appState.connectionState = event.state
                    appState.isLoading = event.state == ConnectionState.CONNECTING ||
                            event.state == ConnectionState.HANDSHAKING ||
                            event.state == ConnectionState.LOGGING_IN
                }

                is ClientEvent.LoginSuccess -> {
                    appState.isLoading = false
                    appState.errorMessage = null
                    appState.currentScreen = AppScreen.GAME
                    appState.gameState.setPlayerInfo(
                        appState.gameState.playerName,
                        event.playerIndex,
                        event.rights,
                        event.member
                    )
                }

                is ClientEvent.LoginFailed -> {
                    appState.isLoading = false
                    appState.errorMessage = event.message
                    appState.currentScreen = AppScreen.LOGIN
                }

                is ClientEvent.Error -> {
                    appState.isLoading = false
                    appState.errorMessage = event.message
                }

                is ClientEvent.Disconnected -> {
                    appState.currentScreen = AppScreen.LOGIN
                    appState.isLoading = false
                }

                is ClientEvent.Reconnecting -> {
                    appState.isLoading = true
                    appState.errorMessage = "Reconnecting..."
                }

                is ClientEvent.PacketReceived -> {
                    // Packets are handled by GameClient
                }
            }
        }
    }

    RustscapeTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = appState.currentScreen,
                transitionSpec = {
                    fadeIn() + slideInHorizontally { width ->
                        if (targetState == AppScreen.GAME) width else -width
                    } togetherWith fadeOut() + slideOutHorizontally { width ->
                        if (targetState == AppScreen.GAME) -width else width
                    }
                },
                label = "screen_transition"
            ) { screen ->
                when (screen) {
                    AppScreen.LOGIN -> {
                        LoginScreen(
                            onLogin = { username, password, rememberMe ->
                                scope.launch {
                                    appState.isLoading = true
                                    appState.errorMessage = null
                                    appState.gameState.playerName = username
                                    onLogin(username, password, rememberMe)
                                }
                            },
                            onRegister = { username, email, password ->
                                scope.launch {
                                    appState.isLoading = true
                                    appState.errorMessage = null
                                    onRegister(username, email, password)
                                }
                            },
                            isLoading = appState.isLoading,
                            errorMessage = appState.errorMessage,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    AppScreen.GAME -> {
                        GameScreen(
                            gameState = appState.gameState,
                            onLogout = {
                                scope.launch {
                                    onLogout()
                                    appState.reset()
                                }
                            },
                            onSendChat = { message ->
                                scope.launch { onSendChat(message) }
                            },
                            onSendCommand = { command ->
                                scope.launch { onSendCommand(command) }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    AppScreen.LOADING -> {
                        LoadingScreen(
                            message = "Loading...",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Loading screen displayed during initialization
 */
@Composable
fun LoadingScreen(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                color = com.rustscape.client.ui.theme.RustscapeColors.Primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Text(
                text = message,
                color = com.rustscape.client.ui.theme.RustscapeColors.TextSecondary
            )
        }
    }
}

/**
 * Preview/standalone app for testing without network
 */
@Composable
fun AppPreview() {
    val appState = remember { AppState() }

    App(
        appState = appState,
        onLogin = { username, _, _ ->
            // Simulate login for preview
            appState.gameState.setPlayerInfo(username, 1, PlayerRights.PLAYER, false)
            appState.currentScreen = AppScreen.GAME
            true
        },
        onRegister = { _, _, _ -> true },
        onLogout = {
            appState.reset()
        },
        onSendChat = { message ->
            appState.gameState.addMessage(
                com.rustscape.client.game.ChatMessage(
                    text = "${appState.gameState.playerName}: $message",
                    sender = appState.gameState.playerName
                )
            )
        },
        onSendCommand = { command ->
            appState.gameState.addMessage(
                com.rustscape.client.game.ChatMessage(
                    text = "Command: $command"
                )
            )
        }
    )
}
