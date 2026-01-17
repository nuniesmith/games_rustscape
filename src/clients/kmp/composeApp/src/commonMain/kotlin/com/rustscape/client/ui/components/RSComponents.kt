package com.rustscape.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Classic RuneScape color palette (2008-2009 HD era)
 */
object RSColors {
    // Stone/Rock colors for buttons and frames
    val StoneLight = Color(0xFF8B8B8B)
    val StoneMid = Color(0xFF6B6B6B)
    val StoneDark = Color(0xFF4B4B4B)
    val StoneVeryDark = Color(0xFF2B2B2B)
    val StoneBorder = Color(0xFF1A1A1A)

    // Scroll/Parchment colors
    val ScrollLight = Color(0xFFE8D5B0)
    val ScrollMid = Color(0xFFD4C4A0)
    val ScrollDark = Color(0xFFB8A882)
    val ScrollBorder = Color(0xFF8B7355)
    val ScrollShadow = Color(0xFF5C4A36)

    // Gold/Bronze accent colors
    val GoldLight = Color(0xFFFFD700)
    val GoldMid = Color(0xFFDAA520)
    val GoldDark = Color(0xFFB8860B)
    val BronzeLight = Color(0xFFCD853F)
    val BronzeMid = Color(0xFF8B6914)
    val BronzeDark = Color(0xFF654321)

    // Interface background colors
    val PanelBackground = Color(0xFF3E3529)
    val PanelBackgroundDark = Color(0xFF2B2117)
    val PanelBorder = Color(0xFF5C4A36)
    val PanelBorderLight = Color(0xFF7A6248)

    // Orb colors
    val HealthRed = Color(0xFFB22222)
    val HealthRedLight = Color(0xFFDC143C)
    val HealthRedDark = Color(0xFF8B0000)
    val PrayerCyan = Color(0xFF00CED1)
    val PrayerCyanLight = Color(0xFF40E0D0)
    val PrayerCyanDark = Color(0xFF008B8B)
    val EnergyYellow = Color(0xFFFFD700)
    val EnergyYellowLight = Color(0xFFFFFF00)
    val EnergyYellowDark = Color(0xFFDAA520)
    val SpecialOrange = Color(0xFFFF8C00)

    // Text colors
    val TextYellow = Color(0xFFFFFF00)
    val TextOrange = Color(0xFFFF9900)
    val TextWhite = Color(0xFFFFFFFF)
    val TextCyan = Color(0xFF00FFFF)
    val TextGreen = Color(0xFF00FF00)
    val TextRed = Color(0xFFFF0000)
    val TextBrown = Color(0xFF5C4A36)
    val TextPrimary = Color(0xFFFFFF00)  // Same as yellow - classic RS text
    val TextMuted = Color(0xFF999999)    // Grayed out text

    // Chat colors
    val ChatBackground = Color(0xCC5D5447)
    val ChatBorder = Color(0xFF3E362E)
    val ChatTabActive = Color(0xFF6B5D4D)
    val ChatTabInactive = Color(0xFF4A4038)
}

/**
 * Classic RuneScape stone-textured button
 * Mimics the beveled 3D stone buttons from 2008 era
 */
@Composable
fun RSStoneButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 150.dp,
    height: Dp = 35.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
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
 * Draw stone button texture with beveled edges
 */
internal fun DrawScope.drawStoneButton(
    size: Size,
    isPressed: Boolean,
    enabled: Boolean
) {
    val cornerRadius = 4f

    // Base stone color
    val baseColor = if (enabled) {
        if (isPressed) RSColors.StoneDark else RSColors.StoneMid
    } else {
        RSColors.StoneVeryDark
    }

    // Draw shadow (only when not pressed)
    if (!isPressed) {
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(2f, 2f),
            size = size,
            cornerRadius = CornerRadius(cornerRadius)
        )
    }

    // Draw main button body
    drawRoundRect(
        color = baseColor,
        size = size,
        cornerRadius = CornerRadius(cornerRadius)
    )

    // Draw top/left highlight (light bevel)
    if (!isPressed) {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    RSColors.StoneLight.copy(alpha = 0.6f),
                    Color.Transparent
                ),
                start = Offset.Zero,
                end = Offset(size.width * 0.3f, size.height * 0.3f)
            ),
            size = size,
            cornerRadius = CornerRadius(cornerRadius)
        )
    }

    // Draw bottom/right shadow (dark bevel)
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                RSColors.StoneVeryDark.copy(alpha = if (isPressed) 0.3f else 0.5f)
            ),
            start = Offset(size.width * 0.7f, size.height * 0.7f),
            end = Offset(size.width, size.height)
        ),
        size = size,
        cornerRadius = CornerRadius(cornerRadius)
    )

    // Draw border
    drawRoundRect(
        color = RSColors.StoneBorder,
        size = size,
        cornerRadius = CornerRadius(cornerRadius),
        style = Stroke(width = 1.5f)
    )

    // Add subtle noise texture
    val random = Random(42) // Fixed seed for consistent texture
    for (i in 0 until 50) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        val alpha = random.nextFloat() * 0.1f
        drawCircle(
            color = if (random.nextBoolean()) Color.White else Color.Black,
            radius = 1f,
            center = Offset(x, y),
            alpha = alpha
        )
    }
}

/**
 * Classic RuneScape scroll/parchment panel
 * Used for login screens and dialog boxes
 */
@Composable
fun RSScrollPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .drawWithCache {
                onDrawBehind {
                    drawScrollPanel(size)
                }
            }
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

/**
 * Draw scroll/parchment texture
 */
private fun DrawScope.drawScrollPanel(size: Size) {
    val cornerRadius = 8f

    // Draw shadow
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.4f),
        topLeft = Offset(4f, 4f),
        size = size,
        cornerRadius = CornerRadius(cornerRadius)
    )

    // Draw main scroll body with gradient
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                RSColors.ScrollLight,
                RSColors.ScrollMid,
                RSColors.ScrollDark,
                RSColors.ScrollMid,
                RSColors.ScrollLight
            )
        ),
        size = size,
        cornerRadius = CornerRadius(cornerRadius)
    )

    // Draw inner shadow for depth
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                RSColors.ScrollShadow.copy(alpha = 0.2f)
            ),
            center = Offset(size.width / 2, size.height / 2),
            radius = maxOf(size.width, size.height) * 0.7f
        ),
        size = size,
        cornerRadius = CornerRadius(cornerRadius)
    )

    // Draw aged paper texture (dots)
    val random = Random(123)
    for (i in 0 until 100) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        val alpha = random.nextFloat() * 0.08f
        drawCircle(
            color = RSColors.ScrollBorder,
            radius = random.nextFloat() * 2f + 0.5f,
            center = Offset(x, y),
            alpha = alpha
        )
    }

    // Draw border
    drawRoundRect(
        color = RSColors.ScrollBorder,
        size = size,
        cornerRadius = CornerRadius(cornerRadius),
        style = Stroke(width = 3f)
    )

    // Draw inner border highlight
    drawRoundRect(
        color = RSColors.ScrollLight.copy(alpha = 0.5f),
        topLeft = Offset(3f, 3f),
        size = Size(size.width - 6f, size.height - 6f),
        cornerRadius = CornerRadius(cornerRadius - 2),
        style = Stroke(width = 1f)
    )
}

/**
 * Classic RuneScape orb (for HP, Prayer, Run energy)
 */
@Composable
fun RSOrbButton(
    value: Int,
    maxValue: Int,
    orbType: OrbType,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    showTooltip: Boolean = false
) {
    val fillPercentage = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .size(size)
            .clickable(onClick = onClick)
            .drawWithCache {
                onDrawBehind {
                    drawOrb(
                        size = this.size,
                        fillPercentage = fillPercentage,
                        orbType = orbType
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value.toString(),
            color = RSColors.TextWhite,
            fontSize = (size.value * 0.28f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

enum class OrbType {
    HEALTH,
    PRAYER,
    RUN_ENERGY,
    SPECIAL_ATTACK
}

/**
 * Draw orb with fill level
 */
internal fun DrawScope.drawOrb(
    size: Size,
    fillPercentage: Float,
    orbType: OrbType
) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val radius = minOf(size.width, size.height) / 2 - 4f

    val (fillColorLight, fillColorMid, fillColorDark) = when (orbType) {
        OrbType.HEALTH -> Triple(RSColors.HealthRedLight, RSColors.HealthRed, RSColors.HealthRedDark)
        OrbType.PRAYER -> Triple(RSColors.PrayerCyanLight, RSColors.PrayerCyan, RSColors.PrayerCyanDark)
        OrbType.RUN_ENERGY -> Triple(RSColors.EnergyYellowLight, RSColors.EnergyYellow, RSColors.EnergyYellowDark)
        OrbType.SPECIAL_ATTACK -> Triple(RSColors.SpecialOrange, RSColors.SpecialOrange, RSColors.GoldDark)
    }

    // Draw outer ring shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.5f),
        radius = radius + 2f,
        center = Offset(centerX + 2f, centerY + 2f)
    )

    // Draw outer stone ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(RSColors.StoneLight, RSColors.StoneMid, RSColors.StoneDark),
            center = Offset(centerX - radius * 0.3f, centerY - radius * 0.3f),
            radius = radius * 2f
        ),
        radius = radius + 3f,
        center = Offset(centerX, centerY)
    )

    // Draw inner dark background
    drawCircle(
        color = Color(0xFF1A1A1A),
        radius = radius - 2f,
        center = Offset(centerX, centerY)
    )

    // Draw fill (from bottom up)
    if (fillPercentage > 0f) {
        clipRect(
            top = size.height - (size.height * fillPercentage),
            bottom = size.height
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(fillColorLight, fillColorMid, fillColorDark),
                    center = Offset(centerX - radius * 0.2f, centerY - radius * 0.2f),
                    radius = radius * 1.5f
                ),
                radius = radius - 3f,
                center = Offset(centerX, centerY)
            )
        }
    }

    // Draw glass shine overlay
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.3f),
                Color.Transparent
            ),
            center = Offset(centerX - radius * 0.3f, centerY - radius * 0.3f),
            radius = radius * 0.6f
        ),
        radius = radius - 3f,
        center = Offset(centerX, centerY)
    )

    // Draw inner ring
    drawCircle(
        color = RSColors.StoneBorder,
        radius = radius - 2f,
        center = Offset(centerX, centerY),
        style = Stroke(width = 2f)
    )

    // Draw outer ring
    drawCircle(
        color = RSColors.StoneBorder,
        radius = radius + 3f,
        center = Offset(centerX, centerY),
        style = Stroke(width = 2f)
    )
}

/**
 * Classic RS tab button for the interface sidebar
 */
@Composable
fun RSTabButton(
    iconContent: @Composable () -> Unit,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 33.dp
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
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

internal fun DrawScope.drawTabButton(size: Size, isSelected: Boolean) {
    val cornerRadius = 3f

    // Background
    drawRoundRect(
        color = if (isSelected) RSColors.PanelBorderLight else RSColors.PanelBackgroundDark,
        size = size,
        cornerRadius = CornerRadius(cornerRadius)
    )

    // Border
    drawRoundRect(
        color = RSColors.PanelBorder,
        size = size,
        cornerRadius = CornerRadius(cornerRadius),
        style = Stroke(width = 1f)
    )

    // Highlight if selected
    if (isSelected) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.2f),
                    Color.Transparent
                )
            ),
            size = Size(size.width, size.height / 2),
            cornerRadius = CornerRadius(cornerRadius)
        )
    }
}

/**
 * Classic RS panel with stone frame
 */
@Composable
fun RSPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Column(modifier = modifier) {
        if (title != null) {
            RSPanelHeader(title = title)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithCache {
                    onDrawBehind {
                        drawPanelBackground(size)
                    }
                }
                .padding(4.dp),
            content = content
        )
    }
}

@Composable
private fun RSPanelHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .drawWithCache {
                onDrawBehind {
                    // Header background
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                RSColors.PanelBorderLight,
                                RSColors.PanelBorder,
                                RSColors.PanelBackgroundDark
                            )
                        )
                    )
                    // Bottom border
                    drawLine(
                        color = RSColors.StoneBorder,
                        start = Offset(0f, size.height - 1f),
                        end = Offset(size.width, size.height - 1f),
                        strokeWidth = 1f
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = RSColors.TextOrange,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun DrawScope.drawPanelBackground(size: Size) {
    // Main background
    drawRect(
        color = RSColors.PanelBackground
    )

    // Inner shadow
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.2f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.1f)
            )
        )
    )

    // Border
    drawRect(
        color = RSColors.PanelBorder,
        style = Stroke(width = 2f)
    )
}

/**
 * Classic RS minimap with compass rose
 */
@Composable
fun RSMinimap(
    modifier: Modifier = Modifier,
    playerX: Int = 0,
    playerY: Int = 0,
    compassRotation: Float = 0f,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .drawWithCache {
                onDrawBehind {
                    drawMinimapFrame(size, compassRotation)
                }
            }
    ) {
        // Content area for map rendering
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .clip(CircleShape),
            content = content
        )

        // Compass overlay
        Canvas(
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 4.dp)
        ) {
            drawCompass(size, compassRotation)
        }

        // Player dot (center)
        Box(
            modifier = Modifier
                .size(6.dp)
                .align(Alignment.Center)
                .background(RSColors.TextWhite, CircleShape)
                .border(1.dp, Color.Black, CircleShape)
        )
    }
}

private fun DrawScope.drawMinimapFrame(size: Size, compassRotation: Float) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val radius = minOf(size.width, size.height) / 2 - 4f

    // Outer shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.5f),
        radius = radius + 4f,
        center = Offset(centerX + 2f, centerY + 2f)
    )

    // Outer stone ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(RSColors.GoldLight, RSColors.GoldMid, RSColors.GoldDark),
            center = Offset(centerX - radius * 0.2f, centerY - radius * 0.2f),
            radius = radius * 1.5f
        ),
        radius = radius + 6f,
        center = Offset(centerX, centerY)
    )

    // Inner gold ring
    drawCircle(
        color = RSColors.BronzeDark,
        radius = radius + 2f,
        center = Offset(centerX, centerY)
    )

    // Map background (dark green for land)
    drawCircle(
        color = Color(0xFF2D4A2D),
        radius = radius - 2f,
        center = Offset(centerX, centerY)
    )

    // Inner border
    drawCircle(
        color = RSColors.StoneBorder,
        radius = radius + 6f,
        center = Offset(centerX, centerY),
        style = Stroke(width = 2f)
    )

    drawCircle(
        color = RSColors.StoneBorder,
        radius = radius - 2f,
        center = Offset(centerX, centerY),
        style = Stroke(width = 1f)
    )
}

private fun DrawScope.drawCompass(size: Size, rotation: Float) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val radius = minOf(size.width, size.height) / 2 - 2f

    // Background
    drawCircle(
        color = RSColors.StoneDark,
        radius = radius,
        center = Offset(centerX, centerY)
    )

    // Draw N, E, S, W markers
    val rotationRad = rotation * PI.toFloat() / 180f
    val directions = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)

    // North indicator (red triangle)
    val northAngle = -rotationRad - PI.toFloat() / 2
    val northX = centerX + cos(northAngle) * (radius - 4f)
    val northY = centerY + sin(northAngle) * (radius - 4f)

    // Draw simple north indicator
    drawCircle(
        color = RSColors.TextRed,
        radius = 3f,
        center = Offset(northX, northY)
    )

    // Border
    drawCircle(
        color = RSColors.StoneBorder,
        radius = radius,
        center = Offset(centerX, centerY),
        style = Stroke(width = 1f)
    )
}

/**
 * Classic RS chat box tabs
 */
@Composable
fun RSChatTabs(
    selectedTab: ChatTab,
    onTabSelected: (ChatTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val soundManager = LocalSoundManager.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(RSColors.ChatBorder),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ChatTab.entries.forEach { tab ->
            RSChatTabButton(
                text = tab.displayName,
                isSelected = tab == selectedTab,
                onClick = {
                    if (tab != selectedTab) {
                        soundManager?.play(RSSound.TAB_SWITCH)
                    }
                    onTabSelected(tab)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

enum class ChatTab(val displayName: String) {
    ALL("All"),
    GAME("Game"),
    PUBLIC("Public"),
    PRIVATE("Private"),
    CLAN("Clan"),
    TRADE("Trade")
}

@Composable
private fun RSChatTabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .background(if (isSelected) RSColors.ChatTabActive else RSColors.ChatTabInactive)
            .border(
                width = 1.dp,
                color = RSColors.ChatBorder
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) RSColors.TextYellow else RSColors.TextWhite,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * Classic RS inventory slot
 */
@Composable
fun RSInventorySlot(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clickable(onClick = onClick)
            .drawWithCache {
                onDrawBehind {
                    // Slot background
                    drawRect(
                        color = RSColors.PanelBackgroundDark
                    )
                    // Inner shadow
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Transparent
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width * 0.5f, size.height * 0.5f)
                        )
                    )
                    // Border
                    drawRect(
                        color = RSColors.PanelBorder,
                        style = Stroke(width = 1f)
                    )
                }
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Classic RS skill icon display
 */
@Composable
fun RSSkillIcon(
    level: Int,
    experience: Long,
    skillColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .size(width = 62.dp, height = 32.dp)
            .clickable(onClick = onClick)
            .drawWithCache {
                onDrawBehind {
                    // Background
                    drawRect(color = RSColors.PanelBackgroundDark)

                    // Skill color indicator
                    drawRect(
                        color = skillColor.copy(alpha = 0.3f),
                        size = Size(size.width * 0.3f, size.height)
                    )

                    // Border
                    drawRect(
                        color = RSColors.PanelBorder,
                        style = Stroke(width = 1f)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            // Skill level
            Text(
                text = level.toString(),
                color = RSColors.TextYellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            // Virtual/boosted level (same for now)
            Text(
                text = level.toString(),
                color = RSColors.TextYellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Helper extension to create RS-style text shadow
 */
@Composable
fun RSText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RSColors.TextYellow,
    fontSize: Int = 14,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Start
) {
    Box(modifier = modifier) {
        // Shadow
        Text(
            text = text,
            color = Color.Black,
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
            textAlign = textAlign,
            modifier = Modifier.offset(1.dp, 1.dp)
        )
        // Main text
        Text(
            text = text,
            color = color,
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
            textAlign = textAlign
        )
    }
}
