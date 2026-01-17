package com.rustscape.client.audio

import com.rustscape.client.ui.components.RSSound
import com.rustscape.client.ui.components.SoundManager
import com.rustscape.client.ui.components.SoundSettings
import kotlinx.browser.window

/**
 * Web Audio API based sound manager for WASM/JS platform
 *
 * Uses synthesized sounds via Web Audio API to create classic RS-style
 * interface and game sounds without requiring external audio files.
 *
 * This implementation uses @JsFun annotations for proper Kotlin/WASM JS interop.
 */

// ========== JS Interop Functions ==========

// localStorage for persisting audio preferences
@JsFun("(key) => { try { return localStorage.getItem(key); } catch(e) { return null; } }")
private external fun localStorageGet(key: JsString): JsString?

@JsFun("(key, value) => { try { localStorage.setItem(key, value); } catch(e) {} }")
private external fun localStorageSet(key: JsString, value: JsString)

@JsFun("(key) => { try { localStorage.removeItem(key); } catch(e) {} }")
private external fun localStorageRemove(key: JsString)

@JsFun("() => { try { return new (window.AudioContext || window.webkitAudioContext)(); } catch(e) { return null; } }")
private external fun createAudioContext(): JsAny?

@JsFun("(ctx) => { return ctx.state; }")
private external fun getContextState(ctx: JsAny): JsString

@JsFun("(ctx) => { ctx.resume(); }")
private external fun resumeContext(ctx: JsAny)

@JsFun("(ctx) => { try { ctx.close(); } catch(e) {} }")
private external fun closeContext(ctx: JsAny)

@JsFun("(ctx) => { return ctx.currentTime; }")
private external fun getCurrentTime(ctx: JsAny): JsNumber

@JsFun("(ctx) => { return ctx.sampleRate; }")
private external fun getSampleRate(ctx: JsAny): JsNumber

// Oscillator creation and control
@JsFun("(ctx) => { return ctx.createOscillator(); }")
private external fun createOscillator(ctx: JsAny): JsAny

@JsFun("(osc, type) => { osc.type = type; }")
private external fun setOscillatorType(osc: JsAny, type: JsString)

@JsFun("(osc, value, time) => { osc.frequency.setValueAtTime(value, time); }")
private external fun setFrequencyAtTime(osc: JsAny, value: JsNumber, time: JsNumber)

@JsFun("(osc, value, time) => { osc.frequency.exponentialRampToValueAtTime(value, time); }")
private external fun expRampFrequency(osc: JsAny, value: JsNumber, time: JsNumber)

@JsFun("(osc, value, time) => { osc.frequency.linearRampToValueAtTime(value, time); }")
private external fun linearRampFrequency(osc: JsAny, value: JsNumber, time: JsNumber)

@JsFun("(osc, time) => { osc.start(time); }")
private external fun startOscillator(osc: JsAny, time: JsNumber)

@JsFun("(osc, time) => { osc.stop(time); }")
private external fun stopOscillator(osc: JsAny, time: JsNumber)

// Gain node creation and control
@JsFun("(ctx) => { return ctx.createGain(); }")
private external fun createGain(ctx: JsAny): JsAny

@JsFun("(gain, value, time) => { gain.gain.setValueAtTime(value, time); }")
private external fun setGainAtTime(gain: JsAny, value: JsNumber, time: JsNumber)

@JsFun("(gain, value, time) => { gain.gain.exponentialRampToValueAtTime(Math.max(0.0001, value), time); }")
private external fun expRampGain(gain: JsAny, value: JsNumber, time: JsNumber)

@JsFun("(gain, value, time) => { gain.gain.linearRampToValueAtTime(value, time); }")
private external fun linearRampGain(gain: JsAny, value: JsNumber, time: JsNumber)

// Filter creation and control
@JsFun("(ctx) => { return ctx.createBiquadFilter(); }")
private external fun createFilter(ctx: JsAny): JsAny

@JsFun("(filter, type) => { filter.type = type; }")
private external fun setFilterType(filter: JsAny, type: JsString)

@JsFun("(filter, value) => { filter.frequency.value = value; }")
private external fun setFilterFrequency(filter: JsAny, value: JsNumber)

@JsFun("(filter, value, time) => { filter.frequency.setValueAtTime(value, time); }")
private external fun setFilterFreqAtTime(filter: JsAny, value: JsNumber, time: JsNumber)

@JsFun("(filter, value, time) => { filter.frequency.exponentialRampToValueAtTime(value, time); }")
private external fun expRampFilterFreq(filter: JsAny, value: JsNumber, time: JsNumber)

@JsFun("(filter, value) => { filter.Q.value = value; }")
private external fun setFilterQ(filter: JsAny, value: JsNumber)

// Connection
@JsFun("(source, dest) => { source.connect(dest); }")
private external fun connectNodes(source: JsAny, dest: JsAny)

@JsFun("(ctx) => { return ctx.destination; }")
private external fun getDestination(ctx: JsAny): JsAny

// Buffer for noise
@JsFun("(ctx, channels, length, sampleRate) => { return ctx.createBuffer(channels, length, sampleRate); }")
private external fun createBuffer(ctx: JsAny, channels: JsNumber, length: JsNumber, sampleRate: JsNumber): JsAny

@JsFun("(buffer, channel) => { return buffer.getChannelData(channel); }")
private external fun getChannelData(buffer: JsAny, channel: JsNumber): JsAny

@JsFun("(data, index, value) => { data[index] = value; }")
private external fun setBufferData(data: JsAny, index: JsNumber, value: JsNumber)

@JsFun("(ctx) => { return ctx.createBufferSource(); }")
private external fun createBufferSource(ctx: JsAny): JsAny

@JsFun("(source, buffer) => { source.buffer = buffer; }")
private external fun setSourceBuffer(source: JsAny, buffer: JsAny)

@JsFun("(source, time) => { source.start(time); }")
private external fun startSource(source: JsAny, time: JsNumber)

// Console logging
@JsFun("(msg) => { console.log(msg); }")
private external fun consoleLog(msg: JsString)

@JsFun("(msg) => { console.error(msg); }")
private external fun consoleError(msg: JsString)

// setTimeout for delayed sounds
@JsFun("(callback, delay) => { setTimeout(callback, delay); }")
private external fun jsSetTimeout(callback: () -> Unit, delay: JsNumber)

class WebSoundManager : SoundManager {
    private var _settings = SoundSettings()
    override val settings: SoundSettings get() = _settings

    private var audioContext: JsAny? = null
    private var isInitialized = false
    private var _isAudioUnlocked = false

    // localStorage keys for audio preferences
    private val STORAGE_KEY_SOUND_ENABLED = "rustscape_sound_enabled"
    private val STORAGE_KEY_MASTER_VOLUME = "rustscape_master_volume"
    private val STORAGE_KEY_MUSIC_VOLUME = "rustscape_music_volume"
    private val STORAGE_KEY_INTERFACE_VOLUME = "rustscape_interface_volume"
    private val STORAGE_KEY_AMBIENT_VOLUME = "rustscape_ambient_volume"

    override val isAudioUnlocked: Boolean
        get() {
            val ctx = audioContext ?: return false
            val state = getContextState(ctx).toString()
            return state == "running"
        }

    init {
        initAudioContext()
        loadSettingsFromStorage()
    }

    private fun initAudioContext() {
        try {
            audioContext = createAudioContext()
            isInitialized = audioContext != null
            if (isInitialized) {
                consoleLog("[WebSoundManager] Audio context initialized".toJsString())
            }
        } catch (e: Exception) {
            consoleError("[WebSoundManager] Failed to initialize audio context".toJsString())
        }
    }

    private fun resumeContextIfNeeded() {
        val ctx = audioContext ?: return
        val state = getContextState(ctx).toString()
        if (state == "suspended") {
            resumeContext(ctx)
        }
    }

    override fun updateSettings(newSettings: SoundSettings) {
        _settings = newSettings
        saveSettingsToStorage()
    }

    /**
     * Load audio settings from localStorage
     */
    private fun loadSettingsFromStorage() {
        try {
            val soundEnabled = localStorageGet(STORAGE_KEY_SOUND_ENABLED.toJsString())?.toString()
            val masterVolume = localStorageGet(STORAGE_KEY_MASTER_VOLUME.toJsString())?.toString()?.toFloatOrNull()
            val musicVolume = localStorageGet(STORAGE_KEY_MUSIC_VOLUME.toJsString())?.toString()?.toFloatOrNull()
            val interfaceVolume =
                localStorageGet(STORAGE_KEY_INTERFACE_VOLUME.toJsString())?.toString()?.toFloatOrNull()
            val ambientVolume = localStorageGet(STORAGE_KEY_AMBIENT_VOLUME.toJsString())?.toString()?.toFloatOrNull()

            _settings = SoundSettings(
                soundEnabled = soundEnabled?.toBooleanStrictOrNull() ?: true,
                masterVolume = masterVolume ?: 0.7f,
                musicVolume = musicVolume ?: 0.3f,
                interfaceVolume = interfaceVolume ?: 1.0f,
                ambientVolume = ambientVolume ?: 0.5f
            )

            consoleLog("[WebSoundManager] Loaded settings from localStorage: soundEnabled=${_settings.soundEnabled}".toJsString())
        } catch (e: Exception) {
            consoleLog("[WebSoundManager] Could not load settings from localStorage, using defaults".toJsString())
        }
    }

    /**
     * Save audio settings to localStorage
     */
    private fun saveSettingsToStorage() {
        try {
            localStorageSet(STORAGE_KEY_SOUND_ENABLED.toJsString(), _settings.soundEnabled.toString().toJsString())
            localStorageSet(STORAGE_KEY_MASTER_VOLUME.toJsString(), _settings.masterVolume.toString().toJsString())
            localStorageSet(STORAGE_KEY_MUSIC_VOLUME.toJsString(), _settings.musicVolume.toString().toJsString())
            localStorageSet(
                STORAGE_KEY_INTERFACE_VOLUME.toJsString(),
                _settings.interfaceVolume.toString().toJsString()
            )
            localStorageSet(STORAGE_KEY_AMBIENT_VOLUME.toJsString(), _settings.ambientVolume.toString().toJsString())
            consoleLog("[WebSoundManager] Saved settings to localStorage".toJsString())
        } catch (e: Exception) {
            consoleError("[WebSoundManager] Failed to save settings to localStorage".toJsString())
        }
    }

    /**
     * Check if user has previously enabled/disabled sound
     * Returns null if no preference was saved (first visit)
     */
    fun getSavedSoundPreference(): Boolean? {
        return try {
            localStorageGet(STORAGE_KEY_SOUND_ENABLED.toJsString())?.toString()?.toBooleanStrictOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clear all saved audio preferences
     */
    fun clearSavedPreferences() {
        try {
            localStorageRemove(STORAGE_KEY_SOUND_ENABLED.toJsString())
            localStorageRemove(STORAGE_KEY_MASTER_VOLUME.toJsString())
            localStorageRemove(STORAGE_KEY_MUSIC_VOLUME.toJsString())
            localStorageRemove(STORAGE_KEY_INTERFACE_VOLUME.toJsString())
            localStorageRemove(STORAGE_KEY_AMBIENT_VOLUME.toJsString())
            consoleLog("[WebSoundManager] Cleared saved audio preferences".toJsString())
        } catch (e: Exception) {
            consoleError("[WebSoundManager] Failed to clear saved preferences".toJsString())
        }
    }

    override fun play(sound: RSSound, volumeMultiplier: Float) {
        if (!isInitialized) return

        val categoryVolume = _settings.getVolumeForCategory(sound.category)
        if (categoryVolume <= 0f) return

        val finalVolume = categoryVolume * sound.defaultVolume * volumeMultiplier

        resumeContextIfNeeded()
        synthesizeAndPlay(sound, finalVolume)
    }

    override fun playDelayed(sound: RSSound, delayMs: Long, volumeMultiplier: Float) {
        jsSetTimeout({
            play(sound, volumeMultiplier)
        }, delayMs.toDouble().toJsNumber())
    }

    override fun stop(sound: RSSound) {
        // For synthesized sounds, they're typically short and don't need stopping
    }

    override fun stopAll() {
        try {
            audioContext?.let { closeContext(it) }
            initAudioContext()
        } catch (e: Exception) {
            consoleError("[WebSoundManager] Error stopping sounds".toJsString())
        }
    }

    override fun preload(sounds: List<RSSound>) {
        consoleLog("[WebSoundManager] Preloading ${sounds.size} sounds".toJsString())
    }

    override fun dispose() {
        try {
            audioContext?.let { closeContext(it) }
            audioContext = null
            isInitialized = false
            _isAudioUnlocked = false
        } catch (e: Exception) {
            consoleError("[WebSoundManager] Error disposing".toJsString())
        }
    }

    override fun unlockAudio(): Boolean {
        if (!isInitialized) {
            initAudioContext()
        }
        val ctx = audioContext ?: return false

        try {
            // Resume the audio context - this must be called from a user gesture
            resumeContext(ctx)

            // Check if we're now running
            val state = getContextState(ctx).toString()
            val unlocked = state == "running"

            if (unlocked) {
                _isAudioUnlocked = true
                consoleLog("[WebSoundManager] Audio context unlocked successfully".toJsString())
                // Play a tiny sound to confirm audio is working
                playButtonClick(0.1f)
            } else {
                consoleLog("[WebSoundManager] Audio context state: $state".toJsString())
            }

            return unlocked
        } catch (e: Exception) {
            consoleError("[WebSoundManager] Failed to unlock audio context".toJsString())
            return false
        }
    }

    private fun synthesizeAndPlay(sound: RSSound, volume: Float) {
        try {
            when (sound) {
                // Interface sounds
                RSSound.BUTTON_CLICK -> playButtonClick(volume)
                RSSound.BUTTON_HOVER -> playButtonHover(volume)
                RSSound.TAB_SWITCH -> playTabSwitch(volume)
                RSSound.WINDOW_OPEN -> playWindowOpen(volume)
                RSSound.WINDOW_CLOSE -> playWindowClose(volume)
                RSSound.CHECKBOX_CHECK -> playCheckbox(volume)
                RSSound.SCROLL_CLICK -> playScrollClick(volume)

                // Login sounds
                RSSound.LOGIN_CLICK -> playLoginClick(volume)
                RSSound.LOGIN_SUCCESS -> playLoginSuccess(volume)
                RSSound.LOGIN_FAIL -> playLoginFail(volume)
                RSSound.LOGOUT -> playLogout(volume)

                // Item sounds
                RSSound.ITEM_PICKUP -> playItemPickup(volume)
                RSSound.ITEM_DROP -> playItemDrop(volume)
                RSSound.ITEM_MOVE -> playItemMove(volume)
                RSSound.ITEM_EQUIP -> playItemEquip(volume)
                RSSound.ITEM_UNEQUIP -> playItemUnequip(volume)
                RSSound.COINS -> playCoins(volume)

                // Chat sounds
                RSSound.CHAT_MESSAGE -> playChatMessage(volume)
                RSSound.PRIVATE_MESSAGE -> playPrivateMessage(volume)
                RSSound.TRADE_REQUEST -> playTradeRequest(volume)

                // Combat sounds
                RSSound.HIT_NORMAL -> playHitNormal(volume)
                RSSound.HIT_CRITICAL -> playHitCritical(volume)
                RSSound.MISS -> playMiss(volume)
                RSSound.BLOCK -> playBlock(volume)
                RSSound.PRAYER_ACTIVATE -> playPrayerActivate(volume)
                RSSound.PRAYER_DEACTIVATE -> playPrayerDeactivate(volume)
                RSSound.PRAYER_DRAIN -> playPrayerDrain(volume)
                RSSound.SPECIAL_ATTACK -> playSpecialAttack(volume)

                // Skill sounds
                RSSound.LEVEL_UP -> playLevelUp(volume)
                RSSound.XP_DROP -> playXpDrop(volume)
                RSSound.MILESTONE -> playMilestone(volume)

                // Quest sounds
                RSSound.QUEST_COMPLETE -> playQuestComplete(volume)
                RSSound.QUEST_STARTED -> playQuestStarted(volume)

                // Ambient sounds
                RSSound.TELEPORT -> playTeleport(volume)
                RSSound.DOOR_OPEN -> playDoorOpen(volume)
                RSSound.DOOR_CLOSE -> playDoorClose(volume)

                // Error sounds
                RSSound.ERROR -> playError(volume)
                RSSound.WARNING -> playWarning(volume)
            }
        } catch (e: Exception) {
            consoleError("[WebSoundManager] Error playing sound ${sound.name}".toJsString())
        }
    }

    // ========== Interface Sounds ==========

    private fun playButtonClick(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)
        val filter = createFilter(ctx)

        setOscillatorType(osc, "square".toJsString())
        setFrequencyAtTime(osc, 800.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 200.toJsNumber(), (now + 0.05).toJsNumber())

        setFilterType(filter, "lowpass".toJsString())
        setFilterFreqAtTime(filter, 2000.toJsNumber(), now.toJsNumber())
        expRampFilterFreq(filter, 500.toJsNumber(), (now + 0.05).toJsNumber())
        setFilterQ(filter, 2.toJsNumber())

        setGainAtTime(gain, (volume * 0.3).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.08).toJsNumber())

        connectNodes(osc, filter)
        connectNodes(filter, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.1).toJsNumber())

        playNoiseClick(volume * 0.15f, 0.03)
    }

    private fun playButtonHover(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)

        setOscillatorType(osc, "sine".toJsString())
        setFrequencyAtTime(osc, 1200.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 800.toJsNumber(), (now + 0.02).toJsNumber())

        setGainAtTime(gain, (volume * 0.1).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.03).toJsNumber())

        connectNodes(osc, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.04).toJsNumber())
    }

    private fun playTabSwitch(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        // First click
        val osc1 = createOscillator(ctx)
        val gain1 = createGain(ctx)
        setOscillatorType(osc1, "triangle".toJsString())
        setFrequencyAtTime(osc1, 600.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc1, 300.toJsNumber(), (now + 0.03).toJsNumber())
        setGainAtTime(gain1, (volume * 0.2).toJsNumber(), now.toJsNumber())
        expRampGain(gain1, 0.001.toJsNumber(), (now + 0.05).toJsNumber())
        connectNodes(osc1, gain1)
        connectNodes(gain1, dest)
        startOscillator(osc1, now.toJsNumber())
        stopOscillator(osc1, (now + 0.06).toJsNumber())

        // Second click
        val osc2 = createOscillator(ctx)
        val gain2 = createGain(ctx)
        setOscillatorType(osc2, "triangle".toJsString())
        setFrequencyAtTime(osc2, 900.toJsNumber(), (now + 0.02).toJsNumber())
        expRampFrequency(osc2, 500.toJsNumber(), (now + 0.05).toJsNumber())
        setGainAtTime(gain2, 0.001.toJsNumber(), now.toJsNumber())
        setGainAtTime(gain2, (volume * 0.25).toJsNumber(), (now + 0.02).toJsNumber())
        expRampGain(gain2, 0.001.toJsNumber(), (now + 0.07).toJsNumber())
        connectNodes(osc2, gain2)
        connectNodes(gain2, dest)
        startOscillator(osc2, now.toJsNumber())
        stopOscillator(osc2, (now + 0.08).toJsNumber())
    }

    private fun playWindowOpen(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)
        val filter = createFilter(ctx)

        setOscillatorType(osc, "sawtooth".toJsString())
        setFrequencyAtTime(osc, 200.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 800.toJsNumber(), (now + 0.1).toJsNumber())

        setFilterType(filter, "lowpass".toJsString())
        setFilterFreqAtTime(filter, 1000.toJsNumber(), now.toJsNumber())
        expRampFilterFreq(filter, 3000.toJsNumber(), (now + 0.1).toJsNumber())

        setGainAtTime(gain, (volume * 0.15).toJsNumber(), now.toJsNumber())
        linearRampGain(gain, (volume * 0.2).toJsNumber(), (now + 0.05).toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.15).toJsNumber())

        connectNodes(osc, filter)
        connectNodes(filter, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.2).toJsNumber())
    }

    private fun playWindowClose(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)
        val filter = createFilter(ctx)

        setOscillatorType(osc, "sawtooth".toJsString())
        setFrequencyAtTime(osc, 600.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 150.toJsNumber(), (now + 0.12).toJsNumber())

        setFilterType(filter, "lowpass".toJsString())
        setFilterFreqAtTime(filter, 2500.toJsNumber(), now.toJsNumber())
        expRampFilterFreq(filter, 800.toJsNumber(), (now + 0.12).toJsNumber())

        setGainAtTime(gain, (volume * 0.2).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.15).toJsNumber())

        connectNodes(osc, filter)
        connectNodes(filter, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.2).toJsNumber())
    }

    private fun playCheckbox(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)

        setOscillatorType(osc, "sine".toJsString())
        setFrequencyAtTime(osc, 1000.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 1400.toJsNumber(), (now + 0.02).toJsNumber())
        expRampFrequency(osc, 1200.toJsNumber(), (now + 0.04).toJsNumber())

        setGainAtTime(gain, (volume * 0.2).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.06).toJsNumber())

        connectNodes(osc, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.08).toJsNumber())
    }

    private fun playScrollClick(volume: Float) {
        playNoiseClick(volume * 0.3f, 0.05)
    }

    // ========== Login Sounds ==========

    private fun playLoginClick(volume: Float) {
        playButtonClick(volume * 1.3f)
    }

    private fun playLoginSuccess(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val notes = listOf(440.0, 554.37, 659.25, 880.0)
        val noteDuration = 0.08

        notes.forEachIndexed { index, freq ->
            val noteStart = now + index * noteDuration

            val osc = createOscillator(ctx)
            val gain = createGain(ctx)

            setOscillatorType(osc, "triangle".toJsString())
            setFrequencyAtTime(osc, freq.toJsNumber(), noteStart.toJsNumber())

            setGainAtTime(gain, 0.001.toJsNumber(), noteStart.toJsNumber())
            linearRampGain(gain, (volume * 0.25).toJsNumber(), (noteStart + 0.01).toJsNumber())
            expRampGain(gain, 0.001.toJsNumber(), (noteStart + noteDuration + 0.15).toJsNumber())

            connectNodes(osc, gain)
            connectNodes(gain, dest)

            startOscillator(osc, noteStart.toJsNumber())
            stopOscillator(osc, (noteStart + noteDuration + 0.2).toJsNumber())
        }
    }

    private fun playLoginFail(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)
        val filter = createFilter(ctx)

        setOscillatorType(osc, "sawtooth".toJsString())
        setFrequencyAtTime(osc, 400.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 150.toJsNumber(), (now + 0.3).toJsNumber())

        setGainAtTime(gain, (volume * 0.2).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.4).toJsNumber())

        setFilterType(filter, "lowpass".toJsString())
        setFilterFrequency(filter, 1500.toJsNumber())

        connectNodes(osc, filter)
        connectNodes(filter, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.5).toJsNumber())
    }

    private fun playLogout(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)

        setOscillatorType(osc, "sine".toJsString())
        setFrequencyAtTime(osc, 600.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 200.toJsNumber(), (now + 0.4).toJsNumber())

        setGainAtTime(gain, (volume * 0.3).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.5).toJsNumber())

        connectNodes(osc, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.6).toJsNumber())
    }

    // ========== Item Sounds ==========

    private fun playItemPickup(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)
        val filter = createFilter(ctx)

        setOscillatorType(osc, "square".toJsString())
        setFrequencyAtTime(osc, 300.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 800.toJsNumber(), (now + 0.06).toJsNumber())

        setGainAtTime(gain, (volume * 0.15).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.1).toJsNumber())

        setFilterType(filter, "lowpass".toJsString())
        setFilterFrequency(filter, 2000.toJsNumber())

        connectNodes(osc, filter)
        connectNodes(filter, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.15).toJsNumber())
    }

    private fun playItemDrop(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)

        setOscillatorType(osc, "sine".toJsString())
        setFrequencyAtTime(osc, 400.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 80.toJsNumber(), (now + 0.1).toJsNumber())

        setGainAtTime(gain, (volume * 0.3).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.15).toJsNumber())

        connectNodes(osc, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.2).toJsNumber())
    }

    private fun playItemMove(volume: Float) {
        playNoiseClick(volume * 0.2f, 0.04)
    }

    private fun playItemEquip(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc1 = createOscillator(ctx)
        val osc2 = createOscillator(ctx)
        val gain = createGain(ctx)
        val filter = createFilter(ctx)

        setOscillatorType(osc1, "triangle".toJsString())
        setFrequencyAtTime(osc1, 800.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc1, 400.toJsNumber(), (now + 0.1).toJsNumber())

        setOscillatorType(osc2, "square".toJsString())
        setFrequencyAtTime(osc2, 1200.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc2, 600.toJsNumber(), (now + 0.08).toJsNumber())

        setGainAtTime(gain, (volume * 0.2).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.15).toJsNumber())

        setFilterType(filter, "bandpass".toJsString())
        setFilterFrequency(filter, 1500.toJsNumber())
        setFilterQ(filter, 3.toJsNumber())

        connectNodes(osc1, filter)
        connectNodes(osc2, filter)
        connectNodes(filter, gain)
        connectNodes(gain, dest)

        startOscillator(osc1, now.toJsNumber())
        startOscillator(osc2, now.toJsNumber())
        stopOscillator(osc1, (now + 0.2).toJsNumber())
        stopOscillator(osc2, (now + 0.2).toJsNumber())
    }

    private fun playItemUnequip(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)

        setOscillatorType(osc, "triangle".toJsString())
        setFrequencyAtTime(osc, 500.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 250.toJsNumber(), (now + 0.1).toJsNumber())

        setGainAtTime(gain, (volume * 0.15).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.12).toJsNumber())

        connectNodes(osc, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.15).toJsNumber())
    }

    private fun playCoins(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val freqs = listOf(2000.0, 2400.0, 1800.0, 2200.0)

        freqs.forEachIndexed { index, freq ->
            val start = now + index * 0.02

            val osc = createOscillator(ctx)
            val gain = createGain(ctx)

            setOscillatorType(osc, "sine".toJsString())
            setFrequencyAtTime(osc, freq.toJsNumber(), start.toJsNumber())
            expRampFrequency(osc, (freq * 0.7).toJsNumber(), (start + 0.05).toJsNumber())

            setGainAtTime(gain, (volume * 0.12).toJsNumber(), start.toJsNumber())
            expRampGain(gain, 0.001.toJsNumber(), (start + 0.08).toJsNumber())

            connectNodes(osc, gain)
            connectNodes(gain, dest)

            startOscillator(osc, start.toJsNumber())
            stopOscillator(osc, (start + 0.1).toJsNumber())
        }
    }

    // ========== Chat Sounds ==========

    private fun playChatMessage(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)

        setOscillatorType(osc, "sine".toJsString())
        setFrequencyAtTime(osc, 800.toJsNumber(), now.toJsNumber())

        setGainAtTime(gain, (volume * 0.08).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.05).toJsNumber())

        connectNodes(osc, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.08).toJsNumber())
    }

    private fun playPrivateMessage(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val notes = listOf(880.0, 1100.0)

        notes.forEachIndexed { index, freq ->
            val start = now + index * 0.08

            val osc = createOscillator(ctx)
            val gain = createGain(ctx)

            setOscillatorType(osc, "sine".toJsString())
            setFrequencyAtTime(osc, freq.toJsNumber(), start.toJsNumber())

            setGainAtTime(gain, (volume * 0.15).toJsNumber(), start.toJsNumber())
            expRampGain(gain, 0.001.toJsNumber(), (start + 0.1).toJsNumber())

            connectNodes(osc, gain)
            connectNodes(gain, dest)

            startOscillator(osc, start.toJsNumber())
            stopOscillator(osc, (start + 0.15).toJsNumber())
        }
    }

    private fun playTradeRequest(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val notes = listOf(600.0, 800.0, 1000.0)

        notes.forEachIndexed { index, freq ->
            val start = now + index * 0.1

            val osc = createOscillator(ctx)
            val gain = createGain(ctx)

            setOscillatorType(osc, "triangle".toJsString())
            setFrequencyAtTime(osc, freq.toJsNumber(), start.toJsNumber())

            setGainAtTime(gain, (volume * 0.2).toJsNumber(), start.toJsNumber())
            expRampGain(gain, 0.001.toJsNumber(), (start + 0.12).toJsNumber())

            connectNodes(osc, gain)
            connectNodes(gain, dest)

            startOscillator(osc, start.toJsNumber())
            stopOscillator(osc, (start + 0.15).toJsNumber())
        }
    }

    // ========== Combat Sounds ==========

    private fun playHitNormal(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)

        setOscillatorType(osc, "sine".toJsString())
        setFrequencyAtTime(osc, 150.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 50.toJsNumber(), (now + 0.1).toJsNumber())

        setGainAtTime(gain, (volume * 0.4).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.15).toJsNumber())

        connectNodes(osc, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.2).toJsNumber())

        playNoiseClick(volume * 0.3f, 0.06)
    }

    private fun playHitCritical(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        // Deep impact
        val osc1 = createOscillator(ctx)
        val gain1 = createGain(ctx)

        setOscillatorType(osc1, "sine".toJsString())
        setFrequencyAtTime(osc1, 100.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc1, 30.toJsNumber(), (now + 0.15).toJsNumber())

        setGainAtTime(gain1, (volume * 0.5).toJsNumber(), now.toJsNumber())
        expRampGain(gain1, 0.001.toJsNumber(), (now + 0.2).toJsNumber())

        connectNodes(osc1, gain1)
        connectNodes(gain1, dest)

        startOscillator(osc1, now.toJsNumber())
        stopOscillator(osc1, (now + 0.25).toJsNumber())

        // Higher crack
        val osc2 = createOscillator(ctx)
        val gain2 = createGain(ctx)
        val filter = createFilter(ctx)

        setOscillatorType(osc2, "sawtooth".toJsString())
        setFrequencyAtTime(osc2, 500.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc2, 150.toJsNumber(), (now + 0.08).toJsNumber())

        setGainAtTime(gain2, (volume * 0.25).toJsNumber(), now.toJsNumber())
        expRampGain(gain2, 0.001.toJsNumber(), (now + 0.1).toJsNumber())

        setFilterType(filter, "lowpass".toJsString())
        setFilterFrequency(filter, 2000.toJsNumber())

        connectNodes(osc2, filter)
        connectNodes(filter, gain2)
        connectNodes(gain2, dest)

        startOscillator(osc2, now.toJsNumber())
        stopOscillator(osc2, (now + 0.15).toJsNumber())

        playNoiseClick(volume * 0.4f, 0.08)
    }

    private fun playMiss(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)
        val filter = createFilter(ctx)

        setOscillatorType(osc, "sawtooth".toJsString())
        setFrequencyAtTime(osc, 200.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 800.toJsNumber(), (now + 0.05).toJsNumber())
        expRampFrequency(osc, 100.toJsNumber(), (now + 0.15).toJsNumber())

        setFilterType(filter, "bandpass".toJsString())
        setFilterFreqAtTime(filter, 500.toJsNumber(), now.toJsNumber())
        expRampFilterFreq(filter, 1500.toJsNumber(), (now + 0.05).toJsNumber())
        expRampFilterFreq(filter, 300.toJsNumber(), (now + 0.15).toJsNumber())
        setFilterQ(filter, 1.toJsNumber())

        setGainAtTime(gain, (volume * 0.15).toJsNumber(), now.toJsNumber())
        linearRampGain(gain, (volume * 0.2).toJsNumber(), (now + 0.05).toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.2).toJsNumber())

        connectNodes(osc, filter)
        connectNodes(filter, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.25).toJsNumber())
    }

    private fun playBlock(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)

        setOscillatorType(osc, "triangle".toJsString())
        setFrequencyAtTime(osc, 1200.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 800.toJsNumber(), (now + 0.05).toJsNumber())
        setFrequencyAtTime(osc, 1000.toJsNumber(), (now + 0.05).toJsNumber())
        expRampFrequency(osc, 600.toJsNumber(), (now + 0.1).toJsNumber())

        setGainAtTime(gain, (volume * 0.25).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.15).toJsNumber())

        connectNodes(osc, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.2).toJsNumber())
    }

    private fun playPrayerActivate(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)

        setOscillatorType(osc, "sine".toJsString())
        setFrequencyAtTime(osc, 400.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 800.toJsNumber(), (now + 0.2).toJsNumber())

        setGainAtTime(gain, 0.001.toJsNumber(), now.toJsNumber())
        linearRampGain(gain, (volume * 0.2).toJsNumber(), (now + 0.05).toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.3).toJsNumber())

        connectNodes(osc, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.35).toJsNumber())

        // Add harmonic
        val osc2 = createOscillator(ctx)
        val gain2 = createGain(ctx)

        setOscillatorType(osc2, "sine".toJsString())
        setFrequencyAtTime(osc2, 800.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc2, 1600.toJsNumber(), (now + 0.2).toJsNumber())

        setGainAtTime(gain2, 0.001.toJsNumber(), now.toJsNumber())
        linearRampGain(gain2, (volume * 0.1).toJsNumber(), (now + 0.05).toJsNumber())
        expRampGain(gain2, 0.001.toJsNumber(), (now + 0.25).toJsNumber())

        connectNodes(osc2, gain2)
        connectNodes(gain2, dest)

        startOscillator(osc2, now.toJsNumber())
        stopOscillator(osc2, (now + 0.3).toJsNumber())
    }

    private fun playPrayerDeactivate(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)

        setOscillatorType(osc, "sine".toJsString())
        setFrequencyAtTime(osc, 600.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 300.toJsNumber(), (now + 0.15).toJsNumber())

        setGainAtTime(gain, (volume * 0.15).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.2).toJsNumber())

        connectNodes(osc, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.25).toJsNumber())
    }

    private fun playPrayerDrain(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)

        setOscillatorType(osc, "sine".toJsString())
        setFrequencyAtTime(osc, 500.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 400.toJsNumber(), (now + 0.03).toJsNumber())

        setGainAtTime(gain, (volume * 0.05).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.05).toJsNumber())

        connectNodes(osc, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.08).toJsNumber())
    }

    private fun playSpecialAttack(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        // Rising sweep
        val osc1 = createOscillator(ctx)
        val gain1 = createGain(ctx)
        val filter = createFilter(ctx)

        setOscillatorType(osc1, "sawtooth".toJsString())
        setFrequencyAtTime(osc1, 100.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc1, 1000.toJsNumber(), (now + 0.2).toJsNumber())

        setGainAtTime(gain1, (volume * 0.2).toJsNumber(), now.toJsNumber())
        linearRampGain(gain1, (volume * 0.3).toJsNumber(), (now + 0.15).toJsNumber())
        expRampGain(gain1, 0.001.toJsNumber(), (now + 0.3).toJsNumber())

        setFilterType(filter, "lowpass".toJsString())
        setFilterFreqAtTime(filter, 500.toJsNumber(), now.toJsNumber())
        expRampFilterFreq(filter, 4000.toJsNumber(), (now + 0.2).toJsNumber())

        connectNodes(osc1, filter)
        connectNodes(filter, gain1)
        connectNodes(gain1, dest)

        startOscillator(osc1, now.toJsNumber())
        stopOscillator(osc1, (now + 0.35).toJsNumber())

        // Impact at end
        val osc2 = createOscillator(ctx)
        val gain2 = createGain(ctx)

        setOscillatorType(osc2, "sine".toJsString())
        setFrequencyAtTime(osc2, 200.toJsNumber(), (now + 0.2).toJsNumber())
        expRampFrequency(osc2, 50.toJsNumber(), (now + 0.35).toJsNumber())

        setGainAtTime(gain2, 0.001.toJsNumber(), now.toJsNumber())
        setGainAtTime(gain2, (volume * 0.4).toJsNumber(), (now + 0.2).toJsNumber())
        expRampGain(gain2, 0.001.toJsNumber(), (now + 0.4).toJsNumber())

        connectNodes(osc2, gain2)
        connectNodes(gain2, dest)

        startOscillator(osc2, (now + 0.15).toJsNumber())
        stopOscillator(osc2, (now + 0.45).toJsNumber())
    }

    // ========== Skill Sounds ==========

    private fun playLevelUp(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val notes = listOf(
            Pair(523.25, 0.0),
            Pair(659.25, 0.08),
            Pair(783.99, 0.16),
            Pair(1046.5, 0.24),
            Pair(1318.5, 0.32),
            Pair(1046.5, 0.4)
        )

        notes.forEach { (freq, delay) ->
            val noteStart = now + delay

            val osc = createOscillator(ctx)
            val gain = createGain(ctx)

            setOscillatorType(osc, "triangle".toJsString())
            setFrequencyAtTime(osc, freq.toJsNumber(), noteStart.toJsNumber())

            setGainAtTime(gain, 0.001.toJsNumber(), noteStart.toJsNumber())
            linearRampGain(gain, (volume * 0.3).toJsNumber(), (noteStart + 0.02).toJsNumber())
            expRampGain(gain, 0.001.toJsNumber(), (noteStart + 0.3).toJsNumber())

            connectNodes(osc, gain)
            connectNodes(gain, dest)

            startOscillator(osc, noteStart.toJsNumber())
            stopOscillator(osc, (noteStart + 0.35).toJsNumber())
        }
    }

    private fun playXpDrop(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)

        setOscillatorType(osc, "sine".toJsString())
        setFrequencyAtTime(osc, 1500.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 1200.toJsNumber(), (now + 0.02).toJsNumber())

        setGainAtTime(gain, (volume * 0.05).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.04).toJsNumber())

        connectNodes(osc, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.06).toJsNumber())
    }

    private fun playMilestone(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val freqs = listOf(523.25, 659.25, 783.99)

        freqs.forEach { freq ->
            val osc = createOscillator(ctx)
            val gain = createGain(ctx)

            setOscillatorType(osc, "triangle".toJsString())
            setFrequencyAtTime(osc, freq.toJsNumber(), now.toJsNumber())

            setGainAtTime(gain, 0.001.toJsNumber(), now.toJsNumber())
            linearRampGain(gain, (volume * 0.2).toJsNumber(), (now + 0.05).toJsNumber())
            expRampGain(gain, 0.001.toJsNumber(), (now + 0.5).toJsNumber())

            connectNodes(osc, gain)
            connectNodes(gain, dest)

            startOscillator(osc, now.toJsNumber())
            stopOscillator(osc, (now + 0.6).toJsNumber())
        }
    }

    // ========== Quest Sounds ==========

    private fun playQuestComplete(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val notes = listOf(
            Pair(392.0, 0.0),
            Pair(493.88, 0.1),
            Pair(587.33, 0.2),
            Pair(783.99, 0.3),
            Pair(987.77, 0.5),
            Pair(1174.66, 0.6)
        )

        notes.forEach { (freq, delay) ->
            val noteStart = now + delay

            val osc = createOscillator(ctx)
            val gain = createGain(ctx)

            setOscillatorType(osc, "triangle".toJsString())
            setFrequencyAtTime(osc, freq.toJsNumber(), noteStart.toJsNumber())

            setGainAtTime(gain, 0.001.toJsNumber(), noteStart.toJsNumber())
            linearRampGain(gain, (volume * 0.25).toJsNumber(), (noteStart + 0.03).toJsNumber())
            setGainAtTime(gain, (volume * 0.25).toJsNumber(), (noteStart + 0.15).toJsNumber())
            expRampGain(gain, 0.001.toJsNumber(), (noteStart + 0.4).toJsNumber())

            connectNodes(osc, gain)
            connectNodes(gain, dest)

            startOscillator(osc, noteStart.toJsNumber())
            stopOscillator(osc, (noteStart + 0.5).toJsNumber())
        }
    }

    private fun playQuestStarted(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)

        setOscillatorType(osc, "sine".toJsString())
        setFrequencyAtTime(osc, 220.toJsNumber(), now.toJsNumber())
        linearRampFrequency(osc, 440.toJsNumber(), (now + 0.2).toJsNumber())
        linearRampFrequency(osc, 330.toJsNumber(), (now + 0.4).toJsNumber())

        setGainAtTime(gain, 0.001.toJsNumber(), now.toJsNumber())
        linearRampGain(gain, (volume * 0.2).toJsNumber(), (now + 0.1).toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.5).toJsNumber())

        connectNodes(osc, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.6).toJsNumber())
    }

    // ========== Ambient Sounds ==========

    private fun playTeleport(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        // Rising sweep
        val osc1 = createOscillator(ctx)
        val gain1 = createGain(ctx)

        setOscillatorType(osc1, "sine".toJsString())
        setFrequencyAtTime(osc1, 200.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc1, 2000.toJsNumber(), (now + 0.3).toJsNumber())

        setGainAtTime(gain1, (volume * 0.2).toJsNumber(), now.toJsNumber())
        linearRampGain(gain1, (volume * 0.3).toJsNumber(), (now + 0.2).toJsNumber())
        expRampGain(gain1, 0.001.toJsNumber(), (now + 0.4).toJsNumber())

        connectNodes(osc1, gain1)
        connectNodes(gain1, dest)

        startOscillator(osc1, now.toJsNumber())
        stopOscillator(osc1, (now + 0.5).toJsNumber())

        // Sparkle layer
        val osc2 = createOscillator(ctx)
        val gain2 = createGain(ctx)

        setOscillatorType(osc2, "triangle".toJsString())
        setFrequencyAtTime(osc2, 1500.toJsNumber(), (now + 0.1).toJsNumber())
        setFrequencyAtTime(osc2, 2500.toJsNumber(), (now + 0.15).toJsNumber())
        setFrequencyAtTime(osc2, 2000.toJsNumber(), (now + 0.2).toJsNumber())
        setFrequencyAtTime(osc2, 3000.toJsNumber(), (now + 0.25).toJsNumber())

        setGainAtTime(gain2, 0.001.toJsNumber(), now.toJsNumber())
        setGainAtTime(gain2, (volume * 0.1).toJsNumber(), (now + 0.1).toJsNumber())
        expRampGain(gain2, 0.001.toJsNumber(), (now + 0.35).toJsNumber())

        connectNodes(osc2, gain2)
        connectNodes(gain2, dest)

        startOscillator(osc2, (now + 0.1).toJsNumber())
        stopOscillator(osc2, (now + 0.4).toJsNumber())
    }

    private fun playDoorOpen(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)
        val filter = createFilter(ctx)

        setOscillatorType(osc, "sawtooth".toJsString())
        setFrequencyAtTime(osc, 80.toJsNumber(), now.toJsNumber())
        linearRampFrequency(osc, 120.toJsNumber(), (now + 0.05).toJsNumber())
        linearRampFrequency(osc, 60.toJsNumber(), (now + 0.15).toJsNumber())
        linearRampFrequency(osc, 100.toJsNumber(), (now + 0.2).toJsNumber())

        setGainAtTime(gain, (volume * 0.15).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.25).toJsNumber())

        setFilterType(filter, "bandpass".toJsString())
        setFilterFrequency(filter, 300.toJsNumber())
        setFilterQ(filter, 5.toJsNumber())

        connectNodes(osc, filter)
        connectNodes(filter, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.3).toJsNumber())
    }

    private fun playDoorClose(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)

        setOscillatorType(osc, "sine".toJsString())
        setFrequencyAtTime(osc, 150.toJsNumber(), now.toJsNumber())
        expRampFrequency(osc, 40.toJsNumber(), (now + 0.1).toJsNumber())

        setGainAtTime(gain, (volume * 0.3).toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.15).toJsNumber())

        connectNodes(osc, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.2).toJsNumber())

        playNoiseClick(volume * 0.2f, 0.05)
    }

    // ========== Error Sounds ==========

    private fun playError(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)
        val filter = createFilter(ctx)

        setOscillatorType(osc, "square".toJsString())
        setFrequencyAtTime(osc, 200.toJsNumber(), now.toJsNumber())
        setFrequencyAtTime(osc, 150.toJsNumber(), (now + 0.05).toJsNumber())
        setFrequencyAtTime(osc, 200.toJsNumber(), (now + 0.1).toJsNumber())

        setGainAtTime(gain, (volume * 0.2).toJsNumber(), now.toJsNumber())
        setGainAtTime(gain, (volume * 0.15).toJsNumber(), (now + 0.05).toJsNumber())
        setGainAtTime(gain, (volume * 0.2).toJsNumber(), (now + 0.1).toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.2).toJsNumber())

        setFilterType(filter, "lowpass".toJsString())
        setFilterFrequency(filter, 1000.toJsNumber())

        connectNodes(osc, filter)
        connectNodes(filter, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.25).toJsNumber())
    }

    private fun playWarning(volume: Float) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)

        val osc = createOscillator(ctx)
        val gain = createGain(ctx)

        setOscillatorType(osc, "triangle".toJsString())
        setFrequencyAtTime(osc, 600.toJsNumber(), now.toJsNumber())
        setFrequencyAtTime(osc, 400.toJsNumber(), (now + 0.1).toJsNumber())

        setGainAtTime(gain, (volume * 0.15).toJsNumber(), now.toJsNumber())
        setGainAtTime(gain, (volume * 0.12).toJsNumber(), (now + 0.1).toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + 0.2).toJsNumber())

        connectNodes(osc, gain)
        connectNodes(gain, dest)

        startOscillator(osc, now.toJsNumber())
        stopOscillator(osc, (now + 0.25).toJsNumber())
    }

    // ========== Helper Functions ==========

    private fun playNoiseClick(volume: Float, duration: Double) {
        val ctx = audioContext ?: return
        val now = getCurrentTime(ctx).toDouble()
        val dest = getDestination(ctx)
        val sampleRate = getSampleRate(ctx).toDouble()

        val bufferSize = (sampleRate * duration).toInt()
        val buffer = createBuffer(ctx, 1.toJsNumber(), bufferSize.toJsNumber(), sampleRate.toJsNumber())
        val data = getChannelData(buffer, 0.toJsNumber())

        for (i in 0 until bufferSize) {
            val value = (kotlin.random.Random.nextFloat() * 2 - 1) * 0.5f
            setBufferData(data, i.toJsNumber(), value.toJsNumber())
        }

        val source = createBufferSource(ctx)
        setSourceBuffer(source, buffer)

        val gain = createGain(ctx)
        setGainAtTime(gain, volume.toJsNumber(), now.toJsNumber())
        expRampGain(gain, 0.001.toJsNumber(), (now + duration).toJsNumber())

        val filter = createFilter(ctx)
        setFilterType(filter, "highpass".toJsString())
        setFilterFrequency(filter, 1000.toJsNumber())

        connectNodes(source, filter)
        connectNodes(filter, gain)
        connectNodes(gain, dest)

        startSource(source, now.toJsNumber())
    }
}

// Extension functions for JS interop
private fun Int.toJsNumber(): JsNumber = this.toDouble().toJsNumber()
private fun Float.toJsNumber(): JsNumber = this.toDouble().toJsNumber()
private fun Double.toJsNumber(): JsNumber = toJsNumber(this)
private fun JsNumber.toDouble(): Double = toKotlinDouble(this)

@JsFun("(value) => { return value; }")
private external fun toJsNumber(value: Double): JsNumber

@JsFun("(value) => { return value; }")
private external fun toKotlinDouble(value: JsNumber): Double
