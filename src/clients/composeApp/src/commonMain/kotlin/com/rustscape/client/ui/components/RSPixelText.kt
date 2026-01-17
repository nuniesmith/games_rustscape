package com.rustscape.client.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rustscape.client.ui.theme.RSTypography
import com.rustscape.client.ui.theme.RustscapeColors

/**
 * Pixel text style presets matching classic RuneScape
 */
enum class RSTextStyle {
    /** Standard chat text */
    CHAT,

    /** Bold chat text for names */
    CHAT_BOLD,

    /** Small labels (item counts, etc.) */
    SMALL,

    /** Skill level numbers */
    SKILL,

    /** Button text */
    BUTTON,

    /** Panel headers */
    HEADER,

    /** Tooltip text */
    TOOLTIP,

    /** Game title (large) */
    TITLE,

    /** Name tags above entities */
    NAME_TAG,

    /** Combat level display */
    COMBAT_LEVEL,

    /** XP drop display */
    XP_DROP,

    /** Level up announcement */
    LEVEL_UP,

    /** System messages */
    SYSTEM,

    /** Quest text */
    QUEST,

    /** Quest title */
    QUEST_TITLE
}

/**
 * Classic RS-style pixel text composable
 * Uses monospace font with optional drop shadow for authentic look
 */
@Composable
fun RSPixelText(
    text: String,
    modifier: Modifier = Modifier,
    style: RSTextStyle = RSTextStyle.CHAT,
    color: Color? = null,
    fontSize: TextUnit? = null,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    withShadow: Boolean = true,
    shadowColor: Color = Color.Black,
    shadowOffset: Offset = Offset(1f, 1f)
) {
    val baseStyle = when (style) {
        RSTextStyle.CHAT -> RSTypography.chat
        RSTextStyle.CHAT_BOLD -> RSTypography.chatBold
        RSTextStyle.SMALL -> RSTypography.smallLabel
        RSTextStyle.SKILL -> RSTypography.skillLevel
        RSTextStyle.BUTTON -> RSTypography.button
        RSTextStyle.HEADER -> RSTypography.panelHeader
        RSTextStyle.TOOLTIP -> RSTypography.tooltip
        RSTextStyle.TITLE -> RSTypography.gameTitle
        RSTextStyle.NAME_TAG -> RSTypography.nameTag
        RSTextStyle.COMBAT_LEVEL -> RSTypography.combatLevel
        RSTextStyle.XP_DROP -> RSTypography.xpDrop
        RSTextStyle.LEVEL_UP -> RSTypography.levelUp
        RSTextStyle.SYSTEM -> RSTypography.systemMessage
        RSTextStyle.QUEST -> RSTypography.questText
        RSTextStyle.QUEST_TITLE -> RSTypography.questTitle
    }

    // Build the final style with overrides
    val finalStyle = baseStyle.copy(
        color = color ?: baseStyle.color,
        fontSize = fontSize ?: baseStyle.fontSize,
        fontWeight = fontWeight ?: baseStyle.fontWeight,
        textAlign = textAlign ?: TextAlign.Unspecified,
        shadow = if (withShadow) {
            Shadow(
                color = shadowColor,
                offset = shadowOffset,
                blurRadius = 0f
            )
        } else null
    )

    Text(
        text = text,
        style = finalStyle,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow
    )
}

/**
 * Simple helper for chat text with RS yellow color
 */
@Composable
fun RSChatMessage(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RustscapeColors.TextPrimary,
    maxLines: Int = Int.MAX_VALUE
) {
    RSPixelText(
        text = text,
        style = RSTextStyle.CHAT,
        color = color,
        modifier = modifier,
        maxLines = maxLines,
        withShadow = true
    )
}

/**
 * Player name text (bold, yellow)
 */
@Composable
fun RSPlayerName(
    name: String,
    modifier: Modifier = Modifier,
    color: Color = RustscapeColors.TextPrimary,
    rights: Int = 0
) {
    val nameColor = when (rights) {
        1 -> RustscapeColors.RightsMod    // Silver for mods
        2 -> RustscapeColors.RightsAdmin  // Gold for admins
        3 -> RustscapeColors.RightsOwner  // Cyan for owner
        else -> color
    }

    RSPixelText(
        text = name,
        style = RSTextStyle.CHAT_BOLD,
        color = nameColor,
        modifier = modifier,
        withShadow = true
    )
}

/**
 * System message text (cyan)
 */
@Composable
fun RSSystemMessage(
    text: String,
    modifier: Modifier = Modifier
) {
    RSPixelText(
        text = text,
        style = RSTextStyle.SYSTEM,
        modifier = modifier,
        withShadow = true
    )
}

/**
 * Skill level display for skill panel
 */
@Composable
fun RSSkillLevel(
    level: Int,
    boostedLevel: Int? = null,
    modifier: Modifier = Modifier
) {
    val displayText = if (boostedLevel != null && boostedLevel != level) {
        "$boostedLevel"
    } else {
        "$level"
    }

    val levelColor = when {
        boostedLevel != null && boostedLevel > level -> RustscapeColors.TextGreen
        boostedLevel != null && boostedLevel < level -> RustscapeColors.TextRed
        else -> RustscapeColors.TextPrimary
    }

    RSPixelText(
        text = displayText,
        style = RSTextStyle.SKILL,
        color = levelColor,
        modifier = modifier,
        withShadow = true
    )
}

/**
 * XP drop text that floats up
 */
@Composable
fun RSXpDropText(
    xp: Int,
    modifier: Modifier = Modifier
) {
    RSPixelText(
        text = "+$xp xp",
        style = RSTextStyle.XP_DROP,
        modifier = modifier,
        withShadow = true
    )
}

/**
 * Level up announcement text
 */
@Composable
fun RSLevelUp(
    skillName: String,
    newLevel: Int,
    modifier: Modifier = Modifier
) {
    RSPixelText(
        text = "Congratulations! You've advanced a $skillName level! You are now level $newLevel.",
        style = RSTextStyle.LEVEL_UP,
        modifier = modifier,
        withShadow = true,
        textAlign = TextAlign.Center
    )
}

/**
 * Item count display (bottom-left of inventory slot)
 */
@Composable
fun RSItemCount(
    count: Int,
    modifier: Modifier = Modifier
) {
    val displayText = when {
        count >= 10_000_000 -> "${count / 1_000_000}M"
        count >= 100_000 -> "${count / 1_000}K"
        count > 1 -> "$count"
        else -> ""
    }

    val countColor = when {
        count >= 10_000_000 -> RustscapeColors.TextGreen
        count >= 100_000 -> RustscapeColors.TextWhite
        count > 99_999 -> RustscapeColors.TextPrimary
        else -> RustscapeColors.TextPrimary
    }

    if (displayText.isNotEmpty()) {
        RSPixelText(
            text = displayText,
            style = RSTextStyle.SMALL,
            color = countColor,
            modifier = modifier,
            withShadow = true
        )
    }
}

/**
 * Combat level badge
 */
@Composable
fun RSCombatLevel(
    level: Int,
    modifier: Modifier = Modifier,
    color: Color = RustscapeColors.TextCyan
) {
    RSPixelText(
        text = "(level-$level)",
        style = RSTextStyle.COMBAT_LEVEL,
        color = color,
        modifier = modifier,
        withShadow = true
    )
}

/**
 * Name tag displayed above entities
 */
@Composable
fun RSNameTag(
    name: String,
    modifier: Modifier = Modifier,
    color: Color = RustscapeColors.TextPrimary
) {
    RSPixelText(
        text = name,
        style = RSTextStyle.NAME_TAG,
        color = color,
        modifier = modifier,
        withShadow = true,
        textAlign = TextAlign.Center
    )
}

/**
 * Button text with yellow color
 */
@Composable
fun RSButtonText(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    RSPixelText(
        text = text,
        style = RSTextStyle.BUTTON,
        color = if (enabled) RustscapeColors.TextPrimary else RustscapeColors.TextMuted,
        modifier = modifier,
        withShadow = true
    )
}

/**
 * Panel header text (orange)
 */
@Composable
fun RSPanelTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    RSPixelText(
        text = text,
        style = RSTextStyle.HEADER,
        modifier = modifier,
        withShadow = true,
        textAlign = TextAlign.Center
    )
}

/**
 * Tooltip text style
 */
@Composable
fun RSTooltipText(
    text: String,
    modifier: Modifier = Modifier
) {
    RSPixelText(
        text = text,
        style = RSTextStyle.TOOLTIP,
        modifier = modifier,
        withShadow = true
    )
}

/**
 * Quest list entry
 */
@Composable
fun RSQuestEntry(
    questName: String,
    modifier: Modifier = Modifier,
    completed: Boolean = false,
    started: Boolean = false
) {
    val textColor = when {
        completed -> RustscapeColors.TextGreen
        started -> RustscapeColors.TextPrimary
        else -> RustscapeColors.TextRed
    }

    RSPixelText(
        text = questName,
        style = RSTextStyle.QUEST,
        color = textColor,
        modifier = modifier,
        withShadow = false
    )
}

/**
 * Game title text for login screen
 */
@Composable
fun RSGameTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    RSPixelText(
        text = text,
        style = RSTextStyle.TITLE,
        modifier = modifier,
        withShadow = true,
        textAlign = TextAlign.Center
    )
}

/**
 * Outlined text effect (text with stroke outline)
 * Useful for important text that needs to stand out
 */
@Composable
fun RSOutlinedText(
    text: String,
    modifier: Modifier = Modifier,
    style: RSTextStyle = RSTextStyle.CHAT,
    fillColor: Color = RustscapeColors.TextPrimary,
    outlineColor: Color = Color.Black,
    outlineWidth: Dp = 1.dp,
    fontSize: TextUnit? = null
) {
    val baseStyle = when (style) {
        RSTextStyle.CHAT -> RSTypography.chat
        RSTextStyle.CHAT_BOLD -> RSTypography.chatBold
        RSTextStyle.SMALL -> RSTypography.smallLabel
        RSTextStyle.SKILL -> RSTypography.skillLevel
        RSTextStyle.BUTTON -> RSTypography.button
        RSTextStyle.HEADER -> RSTypography.panelHeader
        RSTextStyle.TOOLTIP -> RSTypography.tooltip
        RSTextStyle.TITLE -> RSTypography.gameTitle
        RSTextStyle.NAME_TAG -> RSTypography.nameTag
        RSTextStyle.COMBAT_LEVEL -> RSTypography.combatLevel
        RSTextStyle.XP_DROP -> RSTypography.xpDrop
        RSTextStyle.LEVEL_UP -> RSTypography.levelUp
        RSTextStyle.SYSTEM -> RSTypography.systemMessage
        RSTextStyle.QUEST -> RSTypography.questText
        RSTextStyle.QUEST_TITLE -> RSTypography.questTitle
    }

    val textStyle = baseStyle.copy(
        fontSize = fontSize ?: baseStyle.fontSize,
        shadow = null
    )

    Box(modifier = modifier) {
        // Draw outline by rendering text offset in multiple directions
        val offsets = listOf(
            Offset(-outlineWidth.value, 0f),
            Offset(outlineWidth.value, 0f),
            Offset(0f, -outlineWidth.value),
            Offset(0f, outlineWidth.value),
            Offset(-outlineWidth.value, -outlineWidth.value),
            Offset(outlineWidth.value, -outlineWidth.value),
            Offset(-outlineWidth.value, outlineWidth.value),
            Offset(outlineWidth.value, outlineWidth.value)
        )

        offsets.forEach { offset ->
            Text(
                text = text,
                style = textStyle.copy(color = outlineColor),
                modifier = Modifier.offset(offset.x.dp, offset.y.dp)
            )
        }

        // Main text on top
        Text(
            text = text,
            style = textStyle.copy(color = fillColor)
        )
    }
}
