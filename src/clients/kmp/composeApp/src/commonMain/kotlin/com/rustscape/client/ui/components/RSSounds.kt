package com.rustscape.client.ui.components

import androidx.compose.runtime.*

/**
 * RuneScape-style Sound System
 *
 * Defines sound types and provides a cross-platform interface for playing
 * classic RS interface sounds, effects, and ambient audio.
 */

/**
 * Sound effect categories matching classic RS
 */
enum class SoundCategory {
    INTERFACE,      // UI clicks, tabs, buttons
    COMBAT,         // Hit sounds, prayers, abilities
    SKILL,          // Level ups, skill actions
    AMBIENT,        // Background, environment
    MUSIC,          // Background music tracks
    VOICE           // NPC dialogue, player sounds
}

/**
 * Individual sound effects available in the system
 */
enum class RSSound(
    val category: SoundCategory,
    val defaultVolume: Float = 1.0f,
    val description: String = ""
) {
    // Interface sounds
    BUTTON_CLICK(SoundCategory.INTERFACE, 0.5f, "Stone button click"),
    BUTTON_HOVER(SoundCategory.INTERFACE, 0.3f, "Button hover"),
    TAB_SWITCH(SoundCategory.INTERFACE, 0.4f, "Tab switching"),
    WINDOW_OPEN(SoundCategory.INTERFACE, 0.5f, "Window/panel open"),
    WINDOW_CLOSE(SoundCategory.INTERFACE, 0.5f, "Window/panel close"),
    CHECKBOX_CHECK(SoundCategory.INTERFACE, 0.4f, "Checkbox toggle"),
    SCROLL_CLICK(SoundCategory.INTERFACE, 0.3f, "Scroll click"),

    // Login sounds
    LOGIN_CLICK(SoundCategory.INTERFACE, 0.6f, "Login button"),
    LOGIN_SUCCESS(SoundCategory.INTERFACE, 0.7f, "Successful login"),
    LOGIN_FAIL(SoundCategory.INTERFACE, 0.5f, "Failed login"),
    LOGOUT(SoundCategory.INTERFACE, 0.5f, "Logout sound"),

    // Inventory/Item sounds
    ITEM_PICKUP(SoundCategory.INTERFACE, 0.5f, "Pick up item"),
    ITEM_DROP(SoundCategory.INTERFACE, 0.5f, "Drop item"),
    ITEM_MOVE(SoundCategory.INTERFACE, 0.3f, "Move item in inventory"),
    ITEM_EQUIP(SoundCategory.INTERFACE, 0.5f, "Equip item"),
    ITEM_UNEQUIP(SoundCategory.INTERFACE, 0.5f, "Unequip item"),
    COINS(SoundCategory.INTERFACE, 0.5f, "Coins sound"),

    // Chat sounds
    CHAT_MESSAGE(SoundCategory.INTERFACE, 0.3f, "New chat message"),
    PRIVATE_MESSAGE(SoundCategory.INTERFACE, 0.5f, "Private message received"),
    TRADE_REQUEST(SoundCategory.INTERFACE, 0.6f, "Trade request"),

    // Combat sounds
    HIT_NORMAL(SoundCategory.COMBAT, 0.6f, "Normal hit"),
    HIT_CRITICAL(SoundCategory.COMBAT, 0.7f, "Critical hit"),
    MISS(SoundCategory.COMBAT, 0.4f, "Attack miss"),
    BLOCK(SoundCategory.COMBAT, 0.5f, "Block/parry"),
    PRAYER_ACTIVATE(SoundCategory.COMBAT, 0.5f, "Prayer activation"),
    PRAYER_DEACTIVATE(SoundCategory.COMBAT, 0.4f, "Prayer deactivation"),
    PRAYER_DRAIN(SoundCategory.COMBAT, 0.3f, "Prayer points draining"),
    SPECIAL_ATTACK(SoundCategory.COMBAT, 0.7f, "Special attack"),

    // Skill sounds
    LEVEL_UP(SoundCategory.SKILL, 0.8f, "Level up fanfare"),
    XP_DROP(SoundCategory.SKILL, 0.2f, "XP drop tick"),
    MILESTONE(SoundCategory.SKILL, 0.7f, "Achievement/milestone"),

    // Quest sounds
    QUEST_COMPLETE(SoundCategory.SKILL, 0.8f, "Quest completion"),
    QUEST_STARTED(SoundCategory.SKILL, 0.6f, "Quest started"),

    // Ambient/World sounds
    TELEPORT(SoundCategory.AMBIENT, 0.6f, "Teleport spell"),
    DOOR_OPEN(SoundCategory.AMBIENT, 0.5f, "Door opening"),
    DOOR_CLOSE(SoundCategory.AMBIENT, 0.5f, "Door closing"),

    // Error/Warning sounds
    ERROR(SoundCategory.INTERFACE, 0.5f, "Error/invalid action"),
    WARNING(SoundCategory.INTERFACE, 0.4f, "Warning notification")
}

/**
 * Sound settings for volume control
 */
data class SoundSettings(
    val masterVolume: Float = 0.7f,
    val interfaceVolume: Float = 1.0f,
    val combatVolume: Float = 1.0f,
    val skillVolume: Float = 1.0f,
    val ambientVolume: Float = 0.5f,
    val musicVolume: Float = 0.3f,
    val voiceVolume: Float = 1.0f,
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true
) {
    fun getVolumeForCategory(category: SoundCategory): Float {
        if (!soundEnabled && category != SoundCategory.MUSIC) return 0f
        if (!musicEnabled && category == SoundCategory.MUSIC) return 0f

        val categoryVolume = when (category) {
            SoundCategory.INTERFACE -> interfaceVolume
            SoundCategory.COMBAT -> combatVolume
            SoundCategory.SKILL -> skillVolume
            SoundCategory.AMBIENT -> ambientVolume
            SoundCategory.MUSIC -> musicVolume
            SoundCategory.VOICE -> voiceVolume
        }
        return masterVolume * categoryVolume
    }
}

/**
 * Sound manager interface - platform implementations will provide actual audio playback
 */
interface SoundManager {
    val settings: SoundSettings

    fun updateSettings(newSettings: SoundSettings)
    fun play(sound: RSSound, volumeMultiplier: Float = 1.0f)
    fun playDelayed(sound: RSSound, delayMs: Long, volumeMultiplier: Float = 1.0f)
    fun stop(sound: RSSound)
    fun stopAll()
    fun preload(sounds: List<RSSound>)
    fun dispose()
}

/**
 * Composition local for providing sound manager throughout the UI
 */
val LocalSoundManager = staticCompositionLocalOf<SoundManager?> { null }

/**
 * Sound manager state holder for Compose
 */
class SoundManagerState {
    var settings by mutableStateOf(SoundSettings())
        private set

    fun updateSettings(newSettings: SoundSettings) {
        settings = newSettings
    }

    fun setMasterVolume(volume: Float) {
        settings = settings.copy(masterVolume = volume.coerceIn(0f, 1f))
    }

    fun setInterfaceVolume(volume: Float) {
        settings = settings.copy(interfaceVolume = volume.coerceIn(0f, 1f))
    }

    fun setCombatVolume(volume: Float) {
        settings = settings.copy(combatVolume = volume.coerceIn(0f, 1f))
    }

    fun setSkillVolume(volume: Float) {
        settings = settings.copy(skillVolume = volume.coerceIn(0f, 1f))
    }

    fun setAmbientVolume(volume: Float) {
        settings = settings.copy(ambientVolume = volume.coerceIn(0f, 1f))
    }

    fun setMusicVolume(volume: Float) {
        settings = settings.copy(musicVolume = volume.coerceIn(0f, 1f))
    }

    fun toggleSound() {
        settings = settings.copy(soundEnabled = !settings.soundEnabled)
    }

    fun toggleMusic() {
        settings = settings.copy(musicEnabled = !settings.musicEnabled)
    }
}

/**
 * Remember sound manager state
 */
@Composable
fun rememberSoundManagerState(): SoundManagerState {
    return remember { SoundManagerState() }
}

/**
 * Composable modifier extension for adding click sounds to any clickable
 */
@Composable
fun playOnClick(sound: RSSound = RSSound.BUTTON_CLICK): () -> Unit {
    val soundManager = LocalSoundManager.current
    return {
        soundManager?.play(sound)
    }
}

/**
 * Sound effect helper composable - plays sound on composition
 */
@Composable
fun SoundEffect(
    sound: RSSound,
    key: Any? = Unit,
    volumeMultiplier: Float = 1.0f
) {
    val soundManager = LocalSoundManager.current
    LaunchedEffect(key) {
        soundManager?.play(sound, volumeMultiplier)
    }
}

/**
 * Delayed sound effect composable
 */
@Composable
fun DelayedSoundEffect(
    sound: RSSound,
    delayMs: Long,
    key: Any? = Unit,
    volumeMultiplier: Float = 1.0f
) {
    val soundManager = LocalSoundManager.current
    LaunchedEffect(key) {
        soundManager?.playDelayed(sound, delayMs, volumeMultiplier)
    }
}

/**
 * No-op sound manager for platforms without sound support
 * or for testing/preview purposes
 */
class NoOpSoundManager : SoundManager {
    override val settings: SoundSettings = SoundSettings()

    override fun updateSettings(newSettings: SoundSettings) {}
    override fun play(sound: RSSound, volumeMultiplier: Float) {}
    override fun playDelayed(sound: RSSound, delayMs: Long, volumeMultiplier: Float) {}
    override fun stop(sound: RSSound) {}
    override fun stopAll() {}
    override fun preload(sounds: List<RSSound>) {}
    override fun dispose() {}
}

/**
 * Preload common UI sounds on startup
 */
val PRELOAD_UI_SOUNDS = listOf(
    RSSound.BUTTON_CLICK,
    RSSound.BUTTON_HOVER,
    RSSound.TAB_SWITCH,
    RSSound.WINDOW_OPEN,
    RSSound.WINDOW_CLOSE,
    RSSound.ITEM_MOVE,
    RSSound.CHAT_MESSAGE,
    RSSound.ERROR
)

/**
 * Preload combat sounds
 */
val PRELOAD_COMBAT_SOUNDS = listOf(
    RSSound.HIT_NORMAL,
    RSSound.HIT_CRITICAL,
    RSSound.MISS,
    RSSound.BLOCK,
    RSSound.PRAYER_ACTIVATE,
    RSSound.SPECIAL_ATTACK
)

/**
 * Preload all important sounds
 */
val PRELOAD_ALL_SOUNDS = PRELOAD_UI_SOUNDS + PRELOAD_COMBAT_SOUNDS + listOf(
    RSSound.LEVEL_UP,
    RSSound.XP_DROP,
    RSSound.LOGIN_SUCCESS,
    RSSound.LOGOUT
)
