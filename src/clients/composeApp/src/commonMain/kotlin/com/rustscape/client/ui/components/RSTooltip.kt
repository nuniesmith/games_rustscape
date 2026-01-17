package com.rustscape.client.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

/**
 * Tooltip position relative to the anchor element
 */
enum class TooltipPosition {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    CURSOR  // Follows mouse position
}

/**
 * RS-style tooltip data
 */
data class TooltipData(
    val title: String? = null,
    val lines: List<TooltipLine> = emptyList(),
    val width: Dp = Dp.Unspecified
)

/**
 * A line in the tooltip with optional color
 */
data class TooltipLine(
    val text: String,
    val color: Color = RSColors.TextYellow,
    val isBold: Boolean = false
)

/**
 * Classic RuneScape-style tooltip
 * Features:
 * - Dark brown background with gradient
 * - Gold/tan border
 * - Yellow text with shadow effect
 * - Delay before showing
 */
@Composable
fun RSTooltip(
    tooltip: TooltipData,
    modifier: Modifier = Modifier,
    position: TooltipPosition = TooltipPosition.TOP,
    showDelay: Long = 500L,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var showTooltip by remember { mutableStateOf(false) }

    // Delay showing tooltip
    LaunchedEffect(isHovered) {
        if (isHovered) {
            delay(showDelay)
            showTooltip = true
        } else {
            showTooltip = false
        }
    }

    Box(
        modifier = modifier.hoverable(interactionSource)
    ) {
        content()

        if (showTooltip && (tooltip.title != null || tooltip.lines.isNotEmpty())) {
            RSTooltipPopup(
                tooltip = tooltip,
                position = position
            )
        }
    }
}

/**
 * Tooltip popup content
 */
@Composable
private fun RSTooltipPopup(
    tooltip: TooltipData,
    position: TooltipPosition
) {
    val offset = when (position) {
        TooltipPosition.TOP -> IntOffset(0, -8)
        TooltipPosition.BOTTOM -> IntOffset(0, 8)
        TooltipPosition.LEFT -> IntOffset(-8, 0)
        TooltipPosition.RIGHT -> IntOffset(8, 0)
        TooltipPosition.CURSOR -> IntOffset(16, 16)
    }

    val alignment = when (position) {
        TooltipPosition.TOP -> Alignment.TopCenter
        TooltipPosition.BOTTOM -> Alignment.BottomCenter
        TooltipPosition.LEFT -> Alignment.CenterStart
        TooltipPosition.RIGHT -> Alignment.CenterEnd
        TooltipPosition.CURSOR -> Alignment.TopStart
    }

    Popup(
        alignment = alignment,
        offset = offset,
        properties = PopupProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false
        )
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(initialScale = 0.95f),
            exit = fadeOut() + scaleOut(targetScale = 0.95f)
        ) {
            RSTooltipContent(tooltip = tooltip)
        }
    }
}

/**
 * Tooltip content box
 */
@Composable
private fun RSTooltipContent(tooltip: TooltipData) {
    Column(
        modifier = Modifier
            .shadow(4.dp, RoundedCornerShape(2.dp))
            .widthIn(min = 100.dp, max = tooltip.width.takeIf { it != Dp.Unspecified } ?: 250.dp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xF0403428),
                        Color(0xF0332A1E),
                        Color(0xF02A2218)
                    )
                ),
                shape = RoundedCornerShape(2.dp)
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF8B7355),
                        Color(0xFF6B5744),
                        Color(0xFF5A4836)
                    )
                ),
                shape = RoundedCornerShape(2.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title (if present)
        if (tooltip.title != null) {
            TooltipText(
                text = tooltip.title,
                color = RSColors.TextOrange,
                isBold = true
            )
            if (tooltip.lines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // Content lines
        tooltip.lines.forEach { line ->
            TooltipText(
                text = line.text,
                color = line.color,
                isBold = line.isBold
            )
        }
    }
}

/**
 * Tooltip text with shadow effect
 */
@Composable
private fun TooltipText(
    text: String,
    color: Color,
    isBold: Boolean = false
) {
    Box {
        // Shadow
        Text(
            text = text,
            color = Color.Black.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(x = 1.dp, y = 1.dp)
        )
        // Main text
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Pre-built tooltip builders for common RS elements
 */
object RSTooltips {

    /**
     * Skill tooltip showing level and XP info
     */
    fun forSkill(
        skillName: String,
        currentLevel: Int,
        maxLevel: Int,
        experience: Long,
        experienceToNextLevel: Long
    ): TooltipData {
        val lines = mutableListOf<TooltipLine>()

        // Level info
        if (currentLevel != maxLevel) {
            lines.add(TooltipLine("Level: $currentLevel / $maxLevel", RSColors.TextYellow))
        } else {
            lines.add(TooltipLine("Level: $currentLevel", RSColors.TextYellow))
        }

        // XP info
        lines.add(TooltipLine("XP: ${formatNumber(experience)}", RSColors.TextWhite))

        // XP to next level (if not max)
        if (maxLevel < 99) {
            lines.add(TooltipLine("Next level: ${formatNumber(experienceToNextLevel)} XP", RSColors.TextGreen))
        } else if (currentLevel >= 99) {
            lines.add(TooltipLine("Maximum level reached!", RSColors.TextGreen))
        }

        return TooltipData(
            title = skillName,
            lines = lines
        )
    }

    /**
     * Item tooltip with name and examine text
     */
    fun forItem(
        itemName: String,
        examineText: String? = null,
        value: Int? = null,
        isStackable: Boolean = false,
        amount: Int = 1
    ): TooltipData {
        val lines = mutableListOf<TooltipLine>()

        // Examine text
        if (examineText != null) {
            lines.add(TooltipLine(examineText, RSColors.TextWhite))
        }

        // Stack amount
        if (isStackable && amount > 1) {
            lines.add(TooltipLine("Amount: ${formatNumber(amount.toLong())}", RSColors.TextYellow))
        }

        // Value
        if (value != null && value > 0) {
            lines.add(TooltipLine("Value: ${formatNumber(value.toLong())} gp", RSColors.GoldMid))
        }

        return TooltipData(
            title = itemName,
            lines = lines
        )
    }

    /**
     * Equipment tooltip with stats
     */
    fun forEquipment(
        itemName: String,
        attackBonus: Map<String, Int> = emptyMap(),
        defenceBonus: Map<String, Int> = emptyMap(),
        strengthBonus: Int = 0,
        prayerBonus: Int = 0,
        requirements: Map<String, Int> = emptyMap()
    ): TooltipData {
        val lines = mutableListOf<TooltipLine>()

        // Attack bonuses
        if (attackBonus.isNotEmpty()) {
            lines.add(TooltipLine("Attack Bonuses", RSColors.TextOrange, isBold = true))
            attackBonus.forEach { (type, bonus) ->
                val color =
                    if (bonus > 0) RSColors.TextGreen else if (bonus < 0) RSColors.TextRed else RSColors.TextWhite
                lines.add(TooltipLine("$type: ${if (bonus >= 0) "+" else ""}$bonus", color))
            }
        }

        // Defence bonuses
        if (defenceBonus.isNotEmpty()) {
            lines.add(TooltipLine("Defence Bonuses", RSColors.TextOrange, isBold = true))
            defenceBonus.forEach { (type, bonus) ->
                val color =
                    if (bonus > 0) RSColors.TextGreen else if (bonus < 0) RSColors.TextRed else RSColors.TextWhite
                lines.add(TooltipLine("$type: ${if (bonus >= 0) "+" else ""}$bonus", color))
            }
        }

        // Other bonuses
        if (strengthBonus != 0) {
            val color = if (strengthBonus > 0) RSColors.TextGreen else RSColors.TextRed
            lines.add(TooltipLine("Strength: ${if (strengthBonus >= 0) "+" else ""}$strengthBonus", color))
        }
        if (prayerBonus != 0) {
            val color = if (prayerBonus > 0) RSColors.TextGreen else RSColors.TextRed
            lines.add(TooltipLine("Prayer: ${if (prayerBonus >= 0) "+" else ""}$prayerBonus", color))
        }

        // Requirements
        if (requirements.isNotEmpty()) {
            lines.add(TooltipLine("Requirements", RSColors.TextOrange, isBold = true))
            requirements.forEach { (skill, level) ->
                lines.add(TooltipLine("$skill: $level", RSColors.TextYellow))
            }
        }

        return TooltipData(
            title = itemName,
            lines = lines,
            width = 180.dp
        )
    }

    /**
     * Simple text tooltip
     */
    fun simple(text: String): TooltipData = TooltipData(
        lines = listOf(TooltipLine(text, RSColors.TextYellow))
    )

    /**
     * Tooltip with title and description
     */
    fun titled(title: String, description: String): TooltipData = TooltipData(
        title = title,
        lines = listOf(TooltipLine(description, RSColors.TextWhite))
    )

    /**
     * Orb tooltip (HP, Prayer, Run)
     */
    fun forOrb(
        orbType: String,
        current: Int,
        max: Int,
        extraInfo: String? = null
    ): TooltipData {
        val lines = mutableListOf<TooltipLine>()

        lines.add(TooltipLine("$current / $max", RSColors.TextYellow))

        // Percentage
        val percentage = (current.toFloat() / max * 100).toInt()
        val percentColor = when {
            percentage > 50 -> RSColors.TextGreen
            percentage > 25 -> RSColors.TextOrange
            else -> RSColors.TextRed
        }
        lines.add(TooltipLine("($percentage%)", percentColor))

        if (extraInfo != null) {
            lines.add(TooltipLine(extraInfo, RSColors.TextWhite))
        }

        return TooltipData(
            title = orbType,
            lines = lines
        )
    }

    /**
     * Format large numbers with K/M suffix
     */
    private fun formatNumber(num: Long): String {
        return when {
            num >= 10_000_000 -> "${num / 1_000_000}M"
            num >= 100_000 -> "${num / 1_000}K"
            num >= 10_000 -> {
                val thousands = num / 1000.0
                val rounded = (thousands * 10).toLong() / 10.0
                "${rounded}K"
            }

            else -> num.toString().reversed().chunked(3).joinToString(",").reversed()
        }
    }
}

/**
 * Convenience composable for adding tooltip to any element
 */
@Composable
fun WithRSTooltip(
    tooltip: TooltipData,
    position: TooltipPosition = TooltipPosition.TOP,
    showDelay: Long = 500L,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    RSTooltip(
        tooltip = tooltip,
        modifier = modifier,
        position = position,
        showDelay = showDelay,
        content = content
    )
}

/**
 * Convenience composable for simple text tooltips
 */
@Composable
fun WithRSTooltip(
    text: String,
    position: TooltipPosition = TooltipPosition.TOP,
    showDelay: Long = 500L,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    RSTooltip(
        tooltip = RSTooltips.simple(text),
        modifier = modifier,
        position = position,
        showDelay = showDelay,
        content = content
    )
}
