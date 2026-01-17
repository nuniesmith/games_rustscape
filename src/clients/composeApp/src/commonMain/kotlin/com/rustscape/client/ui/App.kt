package com.rustscape.client.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rustscape.client.game.GameState
import com.rustscape.client.game.PlayerRights
import com.rustscape.client.network.ClientConfig
import com.rustscape.client.network.ClientEvent
import com.rustscape.client.network.ConnectionState
import com.rustscape.client.ui.components.AudioUnlockOverlay
import com.rustscape.client.ui.components.GameEntity
import com.rustscape.client.ui.components.LocalAudioUnlockState
import com.rustscape.client.ui.components.LocalSoundManager
import com.rustscape.client.ui.components.RSSound
import com.rustscape.client.ui.components.SoundStatusIndicator
import com.rustscape.client.ui.components.rememberAudioUnlockState
import com.rustscape.client.ui.screens.BankCallbacks
import com.rustscape.client.ui.screens.GameScreen
import com.rustscape.client.ui.screens.InventoryCallbacks
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
/**
 * Entity interaction callback data
 */
data class EntityInteraction(
    val entity: GameEntity,
    val action: String,
    val actionSlot: Int = 1
)

@Composable
fun App(
    appState: AppState = remember { AppState() },
    onLogin: suspend (username: String, password: String, rememberMe: Boolean) -> Boolean = { _, _, _ -> false },
    onRegister: suspend (username: String, email: String, password: String) -> Boolean = { _, _, _ -> false },
    onLogout: suspend () -> Unit = {},
    onSendChat: suspend (message: String) -> Unit = {},
    onSendCommand: suspend (command: String) -> Unit = {},
    onWalkTo: suspend (x: Int, y: Int, running: Boolean) -> Unit = { _, _, _ -> },
    onMinimapWalk: suspend (x: Int, y: Int, running: Boolean) -> Unit = { _, _, _ -> },
    onEntityInteraction: suspend (EntityInteraction) -> Unit = { _ -> },
    // Bank operation callbacks
    onBankClose: suspend () -> Unit = {},
    onBankWithdraw: suspend (slot: Int, itemId: Int, amount: Int, asNote: Boolean) -> Unit = { _, _, _, _ -> },
    onBankDeposit: suspend (inventorySlot: Int, itemId: Int, amount: Int) -> Unit = { _, _, _ -> },
    onBankDepositAll: suspend () -> Unit = {},
    onBankDepositEquipment: suspend () -> Unit = {},
    onBankTabSelect: suspend (Int) -> Unit = {},
    onBankSearch: suspend (String) -> Unit = {},
    onBankMoveItem: suspend (fromSlot: Int, toSlot: Int) -> Unit = { _, _ -> },
    onBankNoteMode: suspend (Boolean) -> Unit = {},
    onBankWithdrawMode: suspend (Int) -> Unit = {},
    // Inventory operation callbacks
    onItemDrop: suspend (slot: Int, itemId: Int) -> Unit = { _, _ -> },
    onItemEquip: suspend (slot: Int, itemId: Int) -> Unit = { _, _ -> },
    onItemUse: suspend (slot: Int, itemId: Int) -> Unit = { _, _ -> },
    onInventorySwap: suspend (fromSlot: Int, toSlot: Int) -> Unit = { _, _ -> },
    onItemExamine: suspend (itemId: Int) -> Unit = {},
    clientEvents: Flow<ClientEvent>? = null
) {
    val scope = rememberCoroutineScope()
    val soundManager = LocalSoundManager.current
    val audioUnlockState = rememberAudioUnlockState()

    // Check if audio is already unlocked (e.g., desktop platform)
    // Also check for saved sound preference from localStorage (web)
    LaunchedEffect(soundManager) {
        if (soundManager?.isAudioUnlocked == true) {
            audioUnlockState.markUnlocked()
        } else if (soundManager?.settings?.soundEnabled == false) {
            // User previously disabled sound, skip the overlay
            audioUnlockState.skipAudio()
        }
    }

    // Collect client events and play appropriate sounds
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
                    // Play login success sound
                    soundManager?.play(RSSound.LOGIN_SUCCESS)
                }

                is ClientEvent.LoginFailed -> {
                    appState.isLoading = false
                    appState.errorMessage = event.message
                    appState.currentScreen = AppScreen.LOGIN
                    // Play login failure sound
                    soundManager?.play(RSSound.LOGIN_FAIL)
                }

                is ClientEvent.Error -> {
                    appState.isLoading = false
                    appState.errorMessage = event.message
                    // Play error sound
                    soundManager?.play(RSSound.ERROR)
                }

                is ClientEvent.Disconnected -> {
                    appState.currentScreen = AppScreen.LOGIN
                    appState.isLoading = false
                    // Play logout/disconnect sound
                    soundManager?.play(RSSound.LOGOUT)
                }

                is ClientEvent.Reconnecting -> {
                    appState.isLoading = true
                    appState.errorMessage = "Reconnecting..."
                    // Play warning sound for reconnection
                    soundManager?.play(RSSound.WARNING)
                }

                is ClientEvent.PacketReceived -> {
                    // Packets are handled by GameClient
                }
            }
        }
    }

    RustscapeTheme {
        CompositionLocalProvider(LocalAudioUnlockState provides audioUnlockState) {
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
                                onWalkTo = { x, y ->
                                    scope.launch { onWalkTo(x, y, false) }
                                },
                                onMinimapWalk = { x, y ->
                                    scope.launch { onMinimapWalk(x, y, appState.gameState.isRunning) }
                                },
                                onEntityInteraction = { entity, action ->
                                    scope.launch {
                                        val actionSlot = getActionSlotForAction(action)
                                        onEntityInteraction(EntityInteraction(entity, action, actionSlot))
                                    }
                                },
                                onInventorySwap = { fromSlot, toSlot ->
                                    scope.launch { onInventorySwap(fromSlot, toSlot) }
                                },
                                bankCallbacks = BankCallbacks(
                                    onClose = { scope.launch { onBankClose() } },
                                    onWithdraw = { slot, itemId, amount, asNote ->
                                        scope.launch { onBankWithdraw(slot, itemId, amount, asNote) }
                                    },
                                    onDeposit = { inventorySlot, itemId, amount ->
                                        scope.launch { onBankDeposit(inventorySlot, itemId, amount) }
                                    },
                                    onDepositAll = { scope.launch { onBankDepositAll() } },
                                    onDepositEquipment = { scope.launch { onBankDepositEquipment() } },
                                    onTabSelect = { tab -> scope.launch { onBankTabSelect(tab) } },
                                    onSearch = { query -> scope.launch { onBankSearch(query) } },
                                    onMoveItem = { from, to -> scope.launch { onBankMoveItem(from, to) } },
                                    onNoteMode = { enabled -> scope.launch { onBankNoteMode(enabled) } },
                                    onWithdrawMode = { mode -> scope.launch { onBankWithdrawMode(mode) } }
                                ),
                                inventoryCallbacks = InventoryCallbacks(
                                    onItemClick = { slot, itemId ->
                                        // Default click action
                                    },
                                    onItemDrop = { slot, itemId ->
                                        scope.launch { onItemDrop(slot, itemId) }
                                    },
                                    onItemEquip = { slot, itemId ->
                                        scope.launch { onItemEquip(slot, itemId) }
                                    },
                                    onItemUse = { slot, itemId ->
                                        scope.launch { onItemUse(slot, itemId) }
                                    },
                                    onItemSwap = { fromSlot, toSlot ->
                                        scope.launch { onInventorySwap(fromSlot, toSlot) }
                                    },
                                    onItemExamine = { itemId ->
                                        scope.launch { onItemExamine(itemId) }
                                    }
                                ),
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

                // Audio unlock overlay (shown on web platforms)
                AudioUnlockOverlay(
                    onUnlock = {
                        soundManager?.unlockAudio() ?: true
                    },
                    onSkip = {
                        // User chose to play without sound - disable all sound
                        soundManager?.updateSettings(
                            soundManager.settings.copy(soundEnabled = false)
                        )
                    }
                )

                // Sound status indicator (shown after overlay is dismissed)
                if (!audioUnlockState.showOverlay) {
                    SoundStatusIndicator(
                        isUnlocked = soundManager?.isAudioUnlocked == true &&
                                soundManager.settings.soundEnabled,
                        onClick = {
                            // Toggle sound on/off
                            soundManager?.let { sm ->
                                sm.updateSettings(
                                    sm.settings.copy(soundEnabled = !sm.settings.soundEnabled)
                                )
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
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
 * Map action names to action slots for the server protocol
 */
private fun getActionSlotForAction(action: String): Int {
    return when (action.lowercase()) {
        // NPC actions
        "attack", "talk", "talk-to" -> 1
        "pickpocket", "steal" -> 2
        "trade", "trade with" -> 3
        "examine" -> 5

        // Player actions
        "follow" -> 1
        "trade" -> 2
        "challenge", "duel" -> 3
        "report" -> 5

        // Object actions
        "open", "use", "enter", "climb", "climb-up" -> 1
        "close", "climb-down" -> 2
        "search", "check" -> 3
        "chop", "chop down", "mine", "fish", "cook" -> 1
        "smelt", "smith" -> 1

        // Ground item actions
        "take" -> 1

        else -> 1
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
