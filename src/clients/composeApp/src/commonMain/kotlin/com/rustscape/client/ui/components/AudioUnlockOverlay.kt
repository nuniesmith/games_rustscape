package com.rustscape.client.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rustscape.client.ui.theme.RustscapeColors

/**
 * Audio unlock state for managing browser audio context
 *
 * @param initialShowOverlay Whether to show the overlay initially (can be set to false
 *                           if user previously disabled sound via localStorage)
 * @param initialNeedsUnlock Whether audio needs to be unlocked
 */
class AudioUnlockState(
    initialShowOverlay: Boolean = true,
    initialNeedsUnlock: Boolean = true
) {
    var needsUnlock by mutableStateOf(initialNeedsUnlock)
        private set

    var showOverlay by mutableStateOf(initialShowOverlay)
        private set

    fun markUnlocked() {
        needsUnlock = false
        showOverlay = false
    }

    fun skipAudio() {
        // User chose to skip enabling audio
        showOverlay = false
    }

    /**
     * Initialize state from saved preferences
     * Call this from platform-specific code after checking localStorage/preferences
     */
    fun initFromSavedPreference(soundEnabled: Boolean?) {
        when (soundEnabled) {
            false -> {
                // User previously disabled sound, skip the overlay entirely
                showOverlay = false
                needsUnlock = false
            }

            true -> {
                // User previously enabled sound, still need gesture but we know preference
                showOverlay = true
                needsUnlock = true
            }

            null -> {
                // No saved preference, show overlay (first visit)
                showOverlay = true
                needsUnlock = true
            }
        }
    }
}

/**
 * Remember audio unlock state
 *
 * @param initialShowOverlay Whether to show the overlay initially
 * @param initialNeedsUnlock Whether audio needs to be unlocked
 */
@Composable
fun rememberAudioUnlockState(
    initialShowOverlay: Boolean = true,
    initialNeedsUnlock: Boolean = true
): AudioUnlockState {
    return remember { AudioUnlockState(initialShowOverlay, initialNeedsUnlock) }
}

/**
 * CompositionLocal for audio unlock state
 */
val LocalAudioUnlockState = staticCompositionLocalOf<AudioUnlockState?> { null }

/**
 * Overlay that prompts user to click to enable audio.
 * Required for web browsers due to autoplay policy.
 *
 * Shows a semi-transparent overlay with a prompt to click anywhere
 * to enable sound. This satisfies browser requirements for user gesture
 * before audio can play.
 */
@Composable
fun AudioUnlockOverlay(
    onUnlock: () -> Boolean,
    onSkip: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val audioUnlockState = LocalAudioUnlockState.current

    AnimatedVisibility(
        visible = audioUnlockState?.showOverlay == true,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    val unlocked = onUnlock()
                    if (unlocked) {
                        audioUnlockState?.markUnlocked()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                // Rustscape logo/title area
                Text(
                    text = "🎮 RUSTSCAPE",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = RustscapeColors.Primary,
                    textAlign = TextAlign.Center
                )

                // Sound icon with glow effect (using emoji instead of Material Icon)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    RustscapeColors.Primary.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔊",
                        fontSize = 40.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Main prompt
                Text(
                    text = "Click anywhere to enable sound",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                // Explanation
                Text(
                    text = "Browser security requires a user interaction\nbefore audio can play.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Skip button
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Play without sound",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier
                            .clickable {
                                onSkip()
                                audioUnlockState?.skipAudio()
                            }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Sound status indicator shown in the corner of the screen
 * Shows whether audio is enabled or muted
 */
@Composable
fun SoundStatusIndicator(
    isUnlocked: Boolean,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(RustscapeColors.BackgroundDark.copy(alpha = 0.8f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isUnlocked) "🔊" else "🔇",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}
