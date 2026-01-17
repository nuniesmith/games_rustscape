package com.rustscape.client.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sound-enabled RuneScape UI Components
 *
 * These components wrap the base RS components and add appropriate sound effects
 * for clicks, hovers, and other interactions.
 */

/**
 * Stone button with click sound effect
 */
@Composable
fun RSSoundStoneButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 150.dp,
    height: Dp = 35.dp,
    clickSound: RSSound = RSSound.BUTTON_CLICK,
    hoverSound: RSSound? = RSSound.BUTTON_HOVER
) {
    val soundManager = LocalSoundManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Track previous hover state to play sound on hover enter
    var wasHovered by remember { mutableStateOf(false) }

    LaunchedEffect(isHovered) {
        if (isHovered && !wasHovered && enabled && hoverSound != null) {
            soundManager?.play(hoverSound)
        }
        wasHovered = isHovered
    }

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (enabled) {
                        soundManager?.play(clickSound)
                        onClick()
                    }
                }
            )
            .drawWithCache {
                onDrawBehind {
                    drawStoneButton(
                        size = size,
                        isPressed = isPressed,
                        enabled = enabled
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) RSColors.TextYellow else RSColors.StoneMid,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(
                y = if (isPressed) 1.dp else 0.dp
            )
        )
    }
}

/**
 * Tab button with sound effect
 */
@Composable
fun RSSoundTabButton(
    iconContent: @Composable () -> Unit,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 33.dp,
    sound: RSSound = RSSound.TAB_SWITCH
) {
    val soundManager = LocalSoundManager.current
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (!isSelected) {
                        soundManager?.play(sound)
                    }
                    onClick()
                }
            )
            .drawWithCache {
                onDrawBehind {
                    drawTabButton(this.size, isSelected)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        iconContent()
    }
}

/**
 * Orb button with sound effect (HP, Prayer, Run Energy)
 */
@Composable
fun RSSoundOrbButton(
    orbType: OrbType,
    currentValue: Int,
    maxValue: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val soundManager = LocalSoundManager.current
    val fillPercentage = if (maxValue > 0) currentValue.toFloat() / maxValue else 0f

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    when (orbType) {
                        OrbType.PRAYER -> soundManager?.play(RSSound.PRAYER_ACTIVATE)
                        OrbType.RUN_ENERGY -> soundManager?.play(RSSound.BUTTON_CLICK)
                        OrbType.SPECIAL_ATTACK -> soundManager?.play(RSSound.SPECIAL_ATTACK)
                        else -> soundManager?.play(RSSound.BUTTON_CLICK)
                    }
                    onClick()
                }
            )
            .drawWithCache {
                onDrawBehind {
                    drawOrb(this.size, fillPercentage, orbType)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = currentValue.toString(),
            color = RSColors.TextWhite,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Inventory slot with item interaction sounds
 */
@Composable
fun RSSoundInventorySlot(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasItem: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val soundManager = LocalSoundManager.current

    RSInventorySlot(
        modifier = modifier,
        onClick = {
            if (hasItem) {
                soundManager?.play(RSSound.ITEM_MOVE)
            }
            onClick()
        },
        content = content
    )
}

/**
 * Skill icon with click sound and level-up capability
 */
@Composable
fun RSSoundSkillIcon(
    level: Int,
    experience: Long,
    skillColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLevelUp: Boolean = false
) {
    val soundManager = LocalSoundManager.current

    // Play level up sound when showLevelUp becomes true
    LaunchedEffect(showLevelUp) {
        if (showLevelUp) {
            soundManager?.play(RSSound.LEVEL_UP)
        }
    }

    RSSkillIcon(
        level = level,
        experience = experience,
        skillColor = skillColor,
        modifier = modifier,
        onClick = {
            soundManager?.play(RSSound.BUTTON_CLICK)
            onClick()
        }
    )
}

/**
 * Checkbox with toggle sound
 */
@Composable
fun RSSoundCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null
) {
    val soundManager = LocalSoundManager.current
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    soundManager?.play(RSSound.CHECKBOX_CHECK)
                    onCheckedChange(!checked)
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .drawWithCache {
                    onDrawBehind {
                        // Checkbox background
                        drawRoundRect(
                            color = RSColors.PanelBackgroundDark,
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
                        )
                        // Border
                        drawRoundRect(
                            color = RSColors.PanelBorder,
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                        )
                        // Checkmark
                        if (checked) {
                            drawLine(
                                color = RSColors.TextGreen,
                                start = androidx.compose.ui.geometry.Offset(4f, size.height / 2),
                                end = androidx.compose.ui.geometry.Offset(size.width / 3, size.height - 4f),
                                strokeWidth = 2f
                            )
                            drawLine(
                                color = RSColors.TextGreen,
                                start = androidx.compose.ui.geometry.Offset(size.width / 3, size.height - 4f),
                                end = androidx.compose.ui.geometry.Offset(size.width - 4f, 4f),
                                strokeWidth = 2f
                            )
                        }
                    }
                }
        )

        if (label != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = if (enabled) RSColors.TextYellow else RSColors.StoneMid,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Clickable modifier with sound effect
 */
@Composable
fun Modifier.clickableWithSound(
    sound: RSSound = RSSound.BUTTON_CLICK,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val soundManager = LocalSoundManager.current
    return this.clickable(
        enabled = enabled,
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = {
            soundManager?.play(sound)
            onClick()
        }
    )
}

/**
 * Login button (special styling and sound)
 */
@Composable
fun RSLoginButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    RSSoundStoneButton(
        text = if (isLoading) "Loading..." else text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isLoading,
        width = 200.dp,
        height = 40.dp,
        clickSound = RSSound.LOGIN_CLICK
    )
}

/**
 * Logout button with logout sound
 */
@Composable
fun RSLogoutButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val soundManager = LocalSoundManager.current

    RSSoundStoneButton(
        text = "Logout",
        onClick = {
            soundManager?.play(RSSound.LOGOUT)
            onClick()
        },
        modifier = modifier,
        width = 80.dp,
        height = 30.dp,
        clickSound = RSSound.LOGOUT,
        hoverSound = null
    )
}

/**
 * Window/Panel open animation with sound
 */
@Composable
fun RSSoundPanel(
    modifier: Modifier = Modifier,
    playOpenSound: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val soundManager = LocalSoundManager.current

    // Play open sound on first composition
    LaunchedEffect(Unit) {
        if (playOpenSound) {
            soundManager?.play(RSSound.WINDOW_OPEN)
        }
    }

    RSPanel(
        modifier = modifier,
        content = content
    )
}

/**
 * Error message display with sound
 */
@Composable
fun RSErrorMessage(
    message: String,
    modifier: Modifier = Modifier,
    playSound: Boolean = true
) {
    val soundManager = LocalSoundManager.current

    LaunchedEffect(message) {
        if (playSound && message.isNotEmpty()) {
            soundManager?.play(RSSound.ERROR)
        }
    }

    if (message.isNotEmpty()) {
        Text(
            text = message,
            color = RSColors.TextRed,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = modifier
        )
    }
}

/**
 * Success message display with sound
 */
@Composable
fun RSSuccessMessage(
    message: String,
    modifier: Modifier = Modifier,
    playSound: Boolean = true
) {
    val soundManager = LocalSoundManager.current

    LaunchedEffect(message) {
        if (playSound && message.isNotEmpty()) {
            soundManager?.play(RSSound.LOGIN_SUCCESS)
        }
    }

    if (message.isNotEmpty()) {
        Text(
            text = message,
            color = RSColors.TextGreen,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = modifier
        )
    }
}

/**
 * Trade request notification with sound
 */
@Composable
fun RSTradeNotification(
    playerName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val soundManager = LocalSoundManager.current

    LaunchedEffect(playerName) {
        soundManager?.play(RSSound.TRADE_REQUEST)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$playerName wants to trade",
            color = RSColors.TextYellow,
            fontSize = 12.sp
        )
        RSSoundStoneButton(
            text = "Accept",
            onClick = onAccept,
            width = 60.dp,
            height = 24.dp
        )
        RSSoundStoneButton(
            text = "Decline",
            onClick = onDecline,
            width = 60.dp,
            height = 24.dp
        )
    }
}

/**
 * Private message notification with sound
 */
@Composable
fun RSPrivateMessageNotification(
    from: String,
    message: String,
    modifier: Modifier = Modifier
) {
    val soundManager = LocalSoundManager.current

    LaunchedEffect(message) {
        soundManager?.play(RSSound.PRIVATE_MESSAGE)
    }

    Row(modifier = modifier) {
        Text(
            text = "From $from: ",
            color = RSColors.TextCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = message,
            color = RSColors.TextCyan,
            fontSize = 12.sp
        )
    }
}

/**
 * XP drop display with subtle sound
 */
@Composable
fun RSXpDrop(
    xpAmount: Int,
    modifier: Modifier = Modifier
) {
    val soundManager = LocalSoundManager.current

    LaunchedEffect(Unit) {
        soundManager?.play(RSSound.XP_DROP)
    }

    Text(
        text = "+$xpAmount xp",
        color = RSColors.TextWhite,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

/**
 * Helper extension to play sound from any composable
 */
@Composable
fun rememberPlaySound(): (RSSound, Float) -> Unit {
    val soundManager = LocalSoundManager.current
    return remember(soundManager) {
        { sound: RSSound, volume: Float ->
            soundManager?.play(sound, volume)
        }
    }
}
