package com.rustscape.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * RS-style Sound Settings Panel
 *
 * Provides controls for:
 * - Master volume
 * - Sound effects (interface, combat, skill)
 * - Ambient/environment sounds
 * - Music volume
 * - Sound on/off toggle
 * - Music on/off toggle
 */
@Composable
fun RSSoundSettingsPanel(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {}
) {
    val soundManager = LocalSoundManager.current
    var settings by remember { mutableStateOf(soundManager?.settings ?: SoundSettings()) }

    // Update sound manager when settings change
    LaunchedEffect(settings) {
        soundManager?.updateSettings(settings)
    }

    RSScrollPanel(
        modifier = modifier
            .width(280.dp)
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Panel header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sound Settings",
                    color = RSColors.TextOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // Close button
                RSStoneButton(
                    text = "X",
                    onClick = {
                        soundManager?.play(RSSound.BUTTON_CLICK)
                        onClose()
                    },
                    width = 24.dp,
                    height = 24.dp
                )
            }

            // Sound enabled toggle
            SoundToggleRow(
                label = "Sound Effects",
                enabled = settings.soundEnabled,
                onToggle = {
                    settings = settings.copy(soundEnabled = !settings.soundEnabled)
                    if (settings.soundEnabled) {
                        soundManager?.play(RSSound.CHECKBOX_CHECK)
                    }
                }
            )

            // Music enabled toggle
            SoundToggleRow(
                label = "Music",
                enabled = settings.musicEnabled,
                onToggle = {
                    settings = settings.copy(musicEnabled = !settings.musicEnabled)
                    soundManager?.play(RSSound.CHECKBOX_CHECK)
                }
            )

            // Divider
            SoundSettingsDivider()

            // Master volume
            VolumeSliderRow(
                label = "Master Volume",
                value = settings.masterVolume,
                onValueChange = { settings = settings.copy(masterVolume = it) },
                onValueChangeFinished = {
                    soundManager?.play(RSSound.BUTTON_CLICK, 0.5f)
                }
            )

            // Interface volume (UI sounds)
            VolumeSliderRow(
                label = "Interface",
                value = settings.interfaceVolume,
                enabled = settings.soundEnabled,
                onValueChange = { settings = settings.copy(interfaceVolume = it) },
                onValueChangeFinished = {
                    soundManager?.play(RSSound.BUTTON_CLICK, 0.5f)
                }
            )

            // Combat volume
            VolumeSliderRow(
                label = "Combat",
                value = settings.combatVolume,
                enabled = settings.soundEnabled,
                onValueChange = { settings = settings.copy(combatVolume = it) },
                onValueChangeFinished = {
                    soundManager?.play(RSSound.HIT_NORMAL, 0.5f)
                }
            )

            // Skill volume
            VolumeSliderRow(
                label = "Skills",
                value = settings.skillVolume,
                enabled = settings.soundEnabled,
                onValueChange = { settings = settings.copy(skillVolume = it) },
                onValueChangeFinished = {
                    soundManager?.play(RSSound.XP_DROP, 0.5f)
                }
            )

            // Ambient volume
            VolumeSliderRow(
                label = "Ambient",
                value = settings.ambientVolume,
                enabled = settings.soundEnabled,
                onValueChange = { settings = settings.copy(ambientVolume = it) },
                onValueChangeFinished = {
                    soundManager?.play(RSSound.DOOR_OPEN, 0.5f)
                }
            )

            // Music volume
            VolumeSliderRow(
                label = "Music",
                value = settings.musicVolume,
                enabled = settings.musicEnabled,
                onValueChange = { settings = settings.copy(musicVolume = it) },
                onValueChangeFinished = {}
            )

            // Divider
            SoundSettingsDivider()

            // Test sounds buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RSStoneButton(
                    text = "Test UI",
                    onClick = { soundManager?.play(RSSound.BUTTON_CLICK) },
                    width = 70.dp,
                    height = 24.dp
                )

                RSStoneButton(
                    text = "Test Hit",
                    onClick = { soundManager?.play(RSSound.HIT_NORMAL) },
                    width = 70.dp,
                    height = 24.dp
                )

                RSStoneButton(
                    text = "Level Up",
                    onClick = { soundManager?.play(RSSound.LEVEL_UP) },
                    width = 70.dp,
                    height = 24.dp
                )
            }
        }
    }
}

/**
 * Toggle row for enabling/disabling sound categories
 */
@Composable
private fun SoundToggleRow(
    label: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = RSColors.TextPrimary,
            fontSize = 12.sp
        )

        RSCheckbox(
            checked = enabled,
            onCheckedChange = { onToggle() }
        )
    }
}

/**
 * RS-style checkbox
 */
@Composable
private fun RSCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        RSColors.StoneDark,
                        RSColors.StoneMid,
                        RSColors.StoneLight
                    )
                )
            )
            .border(1.dp, RSColors.StoneBorder, RoundedCornerShape(2.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Text(
                text = "✓",
                color = RSColors.TextGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Volume slider row with label and percentage
 */
@Composable
private fun VolumeSliderRow(
    label: String,
    value: Float,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (enabled) RSColors.TextPrimary else RSColors.TextMuted,
                fontSize = 11.sp
            )
            Text(
                text = "${(value * 100).toInt()}%",
                color = if (enabled) RSColors.TextWhite else RSColors.TextMuted,
                fontSize = 10.sp
            )
        }

        RSVolumeSlider(
            value = value,
            enabled = enabled,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished
        )
    }
}

/**
 * RS-style volume slider
 */
@Composable
private fun RSVolumeSlider(
    value: Float,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
        colors = SliderDefaults.colors(
            thumbColor = if (enabled) RSColors.GoldMid else RSColors.StoneMid,
            activeTrackColor = if (enabled) RSColors.GoldDark else RSColors.StoneDark,
            inactiveTrackColor = RSColors.StoneDark,
            disabledThumbColor = RSColors.StoneMid,
            disabledActiveTrackColor = RSColors.StoneDark,
            disabledInactiveTrackColor = RSColors.StoneDark
        )
    )
}

/**
 * Divider line for settings panel
 */
@Composable
private fun SoundSettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        RSColors.StoneBorder,
                        RSColors.StoneBorder,
                        Color.Transparent
                    )
                )
            )
    )
}

/**
 * Compact sound toggle button for toolbar/quick access
 */
@Composable
fun RSSoundToggleButton(
    modifier: Modifier = Modifier
) {
    val soundManager = LocalSoundManager.current
    var soundEnabled by remember { mutableStateOf(soundManager?.settings?.soundEnabled ?: true) }

    RSStoneButton(
        text = if (soundEnabled) "🔊" else "🔇",
        onClick = {
            soundEnabled = !soundEnabled
            soundManager?.updateSettings(
                soundManager.settings.copy(soundEnabled = soundEnabled)
            )
            if (soundEnabled) {
                soundManager?.play(RSSound.CHECKBOX_CHECK)
            }
        },
        width = 32.dp,
        height = 32.dp
    )
}

/**
 * Compact music toggle button for toolbar/quick access
 */
@Composable
fun RSMusicToggleButton(
    modifier: Modifier = Modifier
) {
    val soundManager = LocalSoundManager.current
    var musicEnabled by remember { mutableStateOf(soundManager?.settings?.musicEnabled ?: true) }

    RSStoneButton(
        text = "🎵",
        onClick = {
            musicEnabled = !musicEnabled
            soundManager?.updateSettings(
                soundManager.settings.copy(musicEnabled = musicEnabled)
            )
            soundManager?.play(RSSound.CHECKBOX_CHECK)
        },
        width = 32.dp,
        height = 32.dp
    )
}

/**
 * Mini sound settings bar for quick volume adjustment
 */
@Composable
fun RSQuickSoundBar(
    modifier: Modifier = Modifier
) {
    val soundManager = LocalSoundManager.current
    var masterVolume by remember { mutableFloatStateOf(soundManager?.settings?.masterVolume ?: 0.7f) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(RSColors.PanelBackground)
            .border(1.dp, RSColors.StoneBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sound toggle
        RSSoundToggleButton()

        // Volume slider
        RSVolumeSlider(
            value = masterVolume,
            onValueChange = {
                masterVolume = it
                soundManager?.updateSettings(
                    soundManager.settings.copy(masterVolume = it)
                )
            },
            onValueChangeFinished = {
                soundManager?.play(RSSound.BUTTON_CLICK, 0.5f)
            }
        )

        // Music toggle
        RSMusicToggleButton()
    }
}

/**
 * Sound settings button that opens the full settings panel
 */
@Composable
fun RSSoundSettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    RSStoneButton(
        text = "🔊 Sound",
        onClick = onClick,
        modifier = modifier,
        width = 100.dp,
        height = 28.dp
    )
}
