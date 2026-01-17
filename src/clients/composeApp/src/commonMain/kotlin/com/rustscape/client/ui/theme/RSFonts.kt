package com.rustscape.client.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rustscape.composeapp.generated.resources.Res
import com.rustscape.composeapp.generated.resources.silkscreen
import com.rustscape.composeapp.generated.resources.silkscreen_bold
import org.jetbrains.compose.resources.Font

/**
 * RuneScape-style pixel font configuration
 *
 * In classic RS, fonts have specific characteristics:
 * - Pixel-perfect rendering at specific sizes
 * - Black drop shadow (1px offset)
 * - Specific colors for different message types
 * - Monospace-like character widths for alignment
 *
 * Uses Silkscreen font (OFL licensed) for pixel-perfect rendering.
 */
object RSFonts {

    /**
     * Fallback font family (used when resources aren't loaded yet)
     */
    val FallbackFont: FontFamily = FontFamily.Monospace

    /**
     * Secondary font for UI elements
     */
    val UIFont: FontFamily = FontFamily.SansSerif

    /**
     * Classic RS text shadow (black, 1px down-right)
     */
    val ClassicShadow = Shadow(
        color = Color.Black,
        offset = Offset(1f, 1f),
        blurRadius = 0f
    )

    /**
     * Softer shadow for larger text
     */
    val SoftShadow = Shadow(
        color = Color.Black.copy(alpha = 0.8f),
        offset = Offset(1.5f, 1.5f),
        blurRadius = 1f
    )

    /**
     * No shadow
     */
    val NoShadow: Shadow? = null

    // ============ Font Sizes ============

    /**
     * RS classic font sizes (in approximate sp equivalents)
     * Silkscreen looks best at multiples of 8px
     */
    object Sizes {
        val Tiny = 8.sp      // Tiny text (tooltips, minor labels)
        val Small = 10.sp    // Small text (chat timestamps, minor info)
        val Normal = 12.sp   // Normal text (chat, most UI)
        val Medium = 14.sp   // Medium text (headers, important info)
        val Large = 16.sp    // Large text (panel titles)
        val XLarge = 20.sp   // Extra large (section headers)
        val Title = 24.sp    // Title text (login screen)
        val Display = 32.sp  // Display text (major titles)
    }

    // ============ Pre-built Text Styles (using fallback) ============
    // For composable contexts, use the @Composable style functions instead

    /**
     * Chat message text - the most common text style in RS
     */
    val chat = TextStyle(
        fontFamily = FallbackFont,
        fontWeight = FontWeight.Normal,
        fontSize = Sizes.Normal,
        lineHeight = 14.sp,
        letterSpacing = 0.sp,
        color = Color.White,
        shadow = ClassicShadow
    )

    /**
     * Chat message without shadow (for colored backgrounds)
     */
    val chatNoShadow = chat.copy(shadow = null)

    /**
     * Bold chat text (for player names, emphasis)
     */
    val chatBold = chat.copy(fontWeight = FontWeight.Bold)

    /**
     * System message text (yellow)
     */
    val systemMessage = chat.copy(color = RSTextColors.Yellow)

    /**
     * Game message text (cyan/light blue)
     */
    val gameMessage = chat.copy(color = RSTextColors.Cyan)

    /**
     * Error/warning message (red)
     */
    val errorMessage = chat.copy(color = RSTextColors.Red)

    /**
     * Private message text (purple)
     */
    val privateMessage = chat.copy(color = RSTextColors.Purple)

    /**
     * Clan chat text (lighter red)
     */
    val clanMessage = chat.copy(color = RSTextColors.ClanRed)

    /**
     * Trade/duel request text (purple)
     */
    val tradeMessage = chat.copy(color = RSTextColors.TradeRequest)

    /**
     * Skill level display in skills panel
     */
    val skillLevel = TextStyle(
        fontFamily = FallbackFont,
        fontWeight = FontWeight.Bold,
        fontSize = Sizes.Small,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
        color = Color.White,
        shadow = ClassicShadow
    )

    /**
     * Small labels (timestamps, minor info)
     */
    val smallLabel = TextStyle(
        fontFamily = FallbackFont,
        fontWeight = FontWeight.Normal,
        fontSize = Sizes.Tiny,
        lineHeight = 10.sp,
        letterSpacing = 0.sp,
        color = RSTextColors.Gray,
        shadow = null
    )

    /**
     * Panel header text
     */
    val panelHeader = TextStyle(
        fontFamily = UIFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = Sizes.Medium,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = RSTextColors.Gold,
        shadow = ClassicShadow
    )

    /**
     * Button text
     */
    val button = TextStyle(
        fontFamily = UIFont,
        fontWeight = FontWeight.Medium,
        fontSize = Sizes.Normal,
        lineHeight = 14.sp,
        letterSpacing = 0.sp,
        color = RSTextColors.Gold,
        shadow = ClassicShadow
    )

    /**
     * Tooltip text
     */
    val tooltip = TextStyle(
        fontFamily = FallbackFont,
        fontWeight = FontWeight.Normal,
        fontSize = Sizes.Small,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
        color = Color.White,
        shadow = ClassicShadow
    )

    /**
     * Context menu item text
     */
    val contextMenuItem = TextStyle(
        fontFamily = FallbackFont,
        fontWeight = FontWeight.Normal,
        fontSize = Sizes.Normal,
        lineHeight = 14.sp,
        letterSpacing = 0.sp,
        color = Color.White,
        shadow = ClassicShadow
    )

    /**
     * Context menu highlighted item
     */
    val contextMenuHighlight = contextMenuItem.copy(color = RSTextColors.Yellow)

    /**
     * Player/NPC name tag text
     */
    val nameTag = TextStyle(
        fontFamily = FallbackFont,
        fontWeight = FontWeight.Bold,
        fontSize = Sizes.Small,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
        color = RSTextColors.Yellow,
        shadow = ClassicShadow
    )

    /**
     * Combat level text
     */
    val combatLevel = TextStyle(
        fontFamily = FallbackFont,
        fontWeight = FontWeight.Bold,
        fontSize = Sizes.Tiny,
        lineHeight = 10.sp,
        letterSpacing = 0.sp,
        color = Color.White,
        shadow = ClassicShadow
    )

    /**
     * XP drop text
     */
    val xpDrop = TextStyle(
        fontFamily = FallbackFont,
        fontWeight = FontWeight.Bold,
        fontSize = Sizes.Normal,
        lineHeight = 14.sp,
        letterSpacing = 0.sp,
        color = Color.White,
        shadow = ClassicShadow
    )

    /**
     * Level up text
     */
    val levelUp = TextStyle(
        fontFamily = FallbackFont,
        fontWeight = FontWeight.Bold,
        fontSize = Sizes.Large,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
        color = RSTextColors.Gold,
        shadow = SoftShadow
    )
}

/**
 * Composable function to get the pixel font family from resources
 * This loads Silkscreen font for authentic pixel rendering
 */
@Composable
fun rememberPixelFontFamily(): FontFamily {
    val normalFont = Font(Res.font.silkscreen, FontWeight.Normal)
    val boldFont = Font(Res.font.silkscreen_bold, FontWeight.Bold)
    return remember(normalFont, boldFont) {
        FontFamily(normalFont, boldFont)
    }
}

/**
 * Composable text styles that use the pixel font from resources
 */
object RSTextStyles {

    /**
     * Get chat style with pixel font
     */
    @Composable
    fun chat(): TextStyle {
        val pixelFont = rememberPixelFontFamily()
        return RSFonts.chat.copy(fontFamily = pixelFont)
    }

    /**
     * Get bold chat style with pixel font
     */
    @Composable
    fun chatBold(): TextStyle {
        val pixelFont = rememberPixelFontFamily()
        return RSFonts.chatBold.copy(fontFamily = pixelFont)
    }

    /**
     * Get system message style with pixel font
     */
    @Composable
    fun systemMessage(): TextStyle {
        val pixelFont = rememberPixelFontFamily()
        return RSFonts.systemMessage.copy(fontFamily = pixelFont)
    }

    /**
     * Get game message style with pixel font
     */
    @Composable
    fun gameMessage(): TextStyle {
        val pixelFont = rememberPixelFontFamily()
        return RSFonts.gameMessage.copy(fontFamily = pixelFont)
    }

    /**
     * Get error message style with pixel font
     */
    @Composable
    fun errorMessage(): TextStyle {
        val pixelFont = rememberPixelFontFamily()
        return RSFonts.errorMessage.copy(fontFamily = pixelFont)
    }

    /**
     * Get private message style with pixel font
     */
    @Composable
    fun privateMessage(): TextStyle {
        val pixelFont = rememberPixelFontFamily()
        return RSFonts.privateMessage.copy(fontFamily = pixelFont)
    }

    /**
     * Get skill level style with pixel font
     */
    @Composable
    fun skillLevel(): TextStyle {
        val pixelFont = rememberPixelFontFamily()
        return RSFonts.skillLevel.copy(fontFamily = pixelFont)
    }

    /**
     * Get tooltip style with pixel font
     */
    @Composable
    fun tooltip(): TextStyle {
        val pixelFont = rememberPixelFontFamily()
        return RSFonts.tooltip.copy(fontFamily = pixelFont)
    }

    /**
     * Get context menu style with pixel font
     */
    @Composable
    fun contextMenuItem(): TextStyle {
        val pixelFont = rememberPixelFontFamily()
        return RSFonts.contextMenuItem.copy(fontFamily = pixelFont)
    }

    /**
     * Get highlighted context menu style with pixel font
     */
    @Composable
    fun contextMenuHighlight(): TextStyle {
        val pixelFont = rememberPixelFontFamily()
        return RSFonts.contextMenuHighlight.copy(fontFamily = pixelFont)
    }

    /**
     * Get name tag style with pixel font
     */
    @Composable
    fun nameTag(): TextStyle {
        val pixelFont = rememberPixelFontFamily()
        return RSFonts.nameTag.copy(fontFamily = pixelFont)
    }

    /**
     * Get XP drop style with pixel font
     */
    @Composable
    fun xpDrop(): TextStyle {
        val pixelFont = rememberPixelFontFamily()
        return RSFonts.xpDrop.copy(fontFamily = pixelFont)
    }

    /**
     * Get level up style with pixel font
     */
    @Composable
    fun levelUp(): TextStyle {
        val pixelFont = rememberPixelFontFamily()
        return RSFonts.levelUp.copy(fontFamily = pixelFont)
    }
}

/**
 * RS classic text colors
 */
object RSTextColors {
    // Primary colors
    val White = Color.White
    val Black = Color.Black
    val Yellow = Color(0xFFFFFF00)
    val Cyan = Color(0xFF00FFFF)
    val Green = Color(0xFF00FF00)
    val Red = Color(0xFFFF0000)
    val Purple = Color(0xFFFF00FF)
    val Orange = Color(0xFFFF9900)

    // Chat-specific colors
    val ClanRed = Color(0xFF9B0000)
    val TradeRequest = Color(0xFF8000FF)
    val PrivateIn = Color(0xFF00FFFF)
    val PrivateOut = Color(0xFF00FFFF)

    // UI colors
    val Gold = Color(0xFFFFD700)
    val Silver = Color(0xFFC0C0C0)
    val Gray = Color(0xFF808080)
    val DarkGray = Color(0xFF404040)

    // Combat level colors (based on level difference)
    val CombatLower = Color(0xFF00FF00)  // Green - lower level
    val CombatSame = Color(0xFFFFFF00)   // Yellow - similar level
    val CombatHigher = Color(0xFFFF0000) // Red - higher level

    // Item rarity colors (like drops)
    val CommonItem = Color.White
    val UncommonItem = Color(0xFF00FF00)
    val RareItem = Color(0xFF00FFFF)
    val VeryRareItem = Color(0xFFFF00FF)
    val UltraRareItem = Color(0xFFFF9900)
}

/**
 * RS wave/glow/scroll text effect colors
 */
object RSEffectColors {
    val Red = Color(0xFFFF0000)
    val Green = Color(0xFF00FF00)
    val Cyan = Color(0xFF00FFFF)
    val Purple = Color(0xFFFF00FF)
    val White = Color.White
    val Flash1 = Color(0xFFFF0000) // Red to Yellow
    val Flash2 = Color(0xFF00FFFF) // Cyan to Blue
    val Flash3 = Color(0xFF00FF00) // Green to Cyan
    val Glow1 = Color(0xFFFF0000)  // Red rainbow
    val Glow2 = Color(0xFF00FF00)  // Green rainbow
    val Glow3 = Color(0xFF00FFFF)  // Cyan rainbow
}
