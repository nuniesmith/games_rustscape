package com.rustscape.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Rustscape color palette - inspired by classic RuneScape UI
 */
object RustscapeColors {
    // Primary background colors (dark brown/stone)
    val Background = Color(0xFF2B2117)
    val BackgroundLight = Color(0xFF3D3024)
    val BackgroundDark = Color(0xFF1A140E)
    val Surface = Color(0xFF36281C)
    val SurfaceVariant = Color(0xFF4A3828)

    // Primary accent colors (gold/orange)
    val Primary = Color(0xFFD4A84B)
    val PrimaryLight = Color(0xFFE5C77A)
    val PrimaryDark = Color(0xFFB8892E)
    val OnPrimary = Color(0xFF1A140E)

    // Secondary colors (tan/beige)
    val Secondary = Color(0xFFC4A77D)
    val SecondaryLight = Color(0xFFD9C4A3)
    val SecondaryDark = Color(0xFF9B8561)
    val OnSecondary = Color(0xFF1A140E)

    // Text colors
    val TextPrimary = Color(0xFFFFFF00)      // Classic RS yellow
    val TextSecondary = Color(0xFFCCCCCC)    // Light gray
    val TextMuted = Color(0xFF999999)        // Darker gray
    val TextWhite = Color(0xFFFFFFFF)
    val TextBlack = Color(0xFF000000)

    // Special text colors (RS chat colors)
    val TextCyan = Color(0xFF00FFFF)         // Cyan for system messages
    val TextGreen = Color(0xFF00FF00)        // Green for trade/duel
    val TextRed = Color(0xFFFF0000)          // Red for warnings
    val TextOrange = Color(0xFFFF9040)       // Orange for mod chat
    val TextPurple = Color(0xFFAA00FF)       // Purple for special
    val TextGold = Color(0xFFFFD700)         // Gold for rewards
    val TextSilver = Color(0xFFC0C0C0)       // Silver for secondary

    // UI element colors
    val Border = Color(0xFF5C4A36)
    val BorderLight = Color(0xFF7A6248)
    val BorderDark = Color(0xFF3E3024)

    // Panel backgrounds
    val PanelBackground = Color(0xCC2B2117)  // Semi-transparent
    val ChatBackground = Color(0xE6000000)   // Chat box background
    val MinimapBackground = Color(0xFF1A1A1A)

    // Health/Prayer/Energy bars
    val HealthBar = Color(0xFF00FF00)
    val HealthBarLow = Color(0xFFFF0000)
    val PrayerBar = Color(0xFF00FFFF)
    val EnergyBar = Color(0xFFFFFF00)
    val SpecialBar = Color(0xFFFF8000)

    // Map colors
    val MapGreen = Color(0xFF0A5F38)         // Grass
    val MapBlue = Color(0xFF1E90FF)          // Water
    val MapGray = Color(0xFF808080)          // Paths/buildings
    val MapYellow = Color(0xFFFFD700)        // Player dot
    val MapWhite = Color(0xFFFFFFFF)         // NPCs/items
    val MapRed = Color(0xFFFF0000)           // Enemies

    // Rights/rank colors
    val RightsPlayer = TextPrimary
    val RightsMod = Color(0xFFAAAAAA)        // Silver crown
    val RightsAdmin = Color(0xFFFFD700)      // Gold crown
    val RightsOwner = Color(0xFF00FFFF)      // Cyan crown

    // Skill colors (for skill icons/backgrounds)
    val SkillAttack = Color(0xFF9B0000)
    val SkillDefence = Color(0xFF6090B0)
    val SkillStrength = Color(0xFF00A000)
    val SkillHitpoints = Color(0xFFB00000)
    val SkillRanged = Color(0xFF00B000)
    val SkillPrayer = Color(0xFFB0B000)
    val SkillMagic = Color(0xFF4040B0)

    // Error/success states
    val Error = Color(0xFFFF4444)
    val ErrorContainer = Color(0xFF442222)
    val Success = Color(0xFF44FF44)
    val SuccessContainer = Color(0xFF224422)
    val Warning = Color(0xFFFFAA00)
    val WarningContainer = Color(0xFF443300)
}

/**
 * Dark color scheme using Rustscape colors
 */
private val RustscapeDarkColorScheme = darkColorScheme(
    primary = RustscapeColors.Primary,
    onPrimary = RustscapeColors.OnPrimary,
    primaryContainer = RustscapeColors.PrimaryDark,
    onPrimaryContainer = RustscapeColors.TextWhite,
    secondary = RustscapeColors.Secondary,
    onSecondary = RustscapeColors.OnSecondary,
    secondaryContainer = RustscapeColors.SecondaryDark,
    onSecondaryContainer = RustscapeColors.TextWhite,
    tertiary = RustscapeColors.TextCyan,
    onTertiary = RustscapeColors.TextBlack,
    background = RustscapeColors.Background,
    onBackground = RustscapeColors.TextPrimary,
    surface = RustscapeColors.Surface,
    onSurface = RustscapeColors.TextSecondary,
    surfaceVariant = RustscapeColors.SurfaceVariant,
    onSurfaceVariant = RustscapeColors.TextMuted,
    outline = RustscapeColors.Border,
    outlineVariant = RustscapeColors.BorderDark,
    error = RustscapeColors.Error,
    onError = RustscapeColors.TextWhite,
    errorContainer = RustscapeColors.ErrorContainer,
    onErrorContainer = RustscapeColors.Error
)

/**
 * Typography for Rustscape UI
 */
val RustscapeTypography = Typography(
    // Large titles (game title, major headers)
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
        color = RustscapeColors.TextGold
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
        color = RustscapeColors.TextGold
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
        color = RustscapeColors.TextPrimary
    ),

    // Headlines (section titles)
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = RustscapeColors.TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        color = RustscapeColors.TextPrimary
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
        color = RustscapeColors.TextPrimary
    ),

    // Titles (panel titles, dialog titles)
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        color = RustscapeColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
        color = RustscapeColors.TextPrimary
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = RustscapeColors.TextSecondary
    ),

    // Body text (main content, descriptions)
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        color = RustscapeColors.TextSecondary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
        color = RustscapeColors.TextSecondary
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        color = RustscapeColors.TextMuted
    ),

    // Labels (buttons, form fields)
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = RustscapeColors.TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = RustscapeColors.TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
        color = RustscapeColors.TextMuted
    )
)

/**
 * Main Rustscape theme composable
 */
@Composable
fun RustscapeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Always use dark theme for Rustscape (RS-style)
    val colorScheme = RustscapeDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RustscapeTypography,
        content = content
    )
}

/**
 * Extension function to get rights-based color
 */
fun getRightsColor(rights: Int): Color = when (rights) {
    0 -> RustscapeColors.RightsPlayer
    1 -> RustscapeColors.RightsMod
    2 -> RustscapeColors.RightsAdmin
    3 -> RustscapeColors.RightsOwner
    else -> RustscapeColors.RightsPlayer
}

/**
 * Extension function to get skill color
 */
fun getSkillColor(skillId: Int): Color = when (skillId) {
    0 -> RustscapeColors.SkillAttack
    1 -> RustscapeColors.SkillDefence
    2 -> RustscapeColors.SkillStrength
    3 -> RustscapeColors.SkillHitpoints
    4 -> RustscapeColors.SkillRanged
    5 -> RustscapeColors.SkillPrayer
    6 -> RustscapeColors.SkillMagic
    else -> RustscapeColors.TextSecondary
}
