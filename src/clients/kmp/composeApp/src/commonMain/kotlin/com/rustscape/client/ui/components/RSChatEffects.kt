package com.rustscape.client.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * RuneScape Chat Text Effects
 *
 * Classic RS text effects for chat messages:
 * - wave: Text moves up and down in a wave pattern
 * - wave2: Faster wave pattern
 * - scroll: Text scrolls horizontally
 * - shake: Text shakes randomly
 * - slide: Text slides in from the left
 * - flash1: Flashes between red and yellow
 * - flash2: Flashes between cyan and blue
 * - flash3: Flashes between green and light green
 * - glow1: Cycles through rainbow colors (red)
 * - glow2: Cycles through rainbow colors (cyan)
 * - glow3: Cycles through rainbow colors (green)
 *
 * Color prefixes:
 * - yellow: (default)
 * - red:
 * - green:
 * - cyan:
 * - purple:
 * - white:
 * - black: (for light backgrounds)
 */

/**
 * Text effect types supported by the chat system
 */
enum class ChatTextEffect {
    NONE,
    WAVE,
    WAVE2,
    SCROLL,
    SHAKE,
    SLIDE,
    FLASH1,
    FLASH2,
    FLASH3,
    GLOW1,
    GLOW2,
    GLOW3
}

/**
 * Text color presets matching RS
 */
enum class ChatTextColor(val color: Color) {
    YELLOW(Color(0xFFFFFF00)),
    RED(Color(0xFFFF0000)),
    GREEN(Color(0xFF00FF00)),
    CYAN(Color(0xFF00FFFF)),
    PURPLE(Color(0xFFFF00FF)),
    WHITE(Color(0xFFFFFFFF)),
    BLACK(Color(0xFF000000)),
    ORANGE(Color(0xFFFF9040)),
    BLUE(Color(0xFF0000FF))
}

/**
 * Parse chat message for effects and colors
 * Returns the parsed text, effect, and color
 */
data class ParsedChatMessage(
    val text: String,
    val effect: ChatTextEffect,
    val color: ChatTextColor
)

/**
 * Parse RS-style chat tags from a message
 * Format: "effect:color:message" or "effect:message" or "color:message" or just "message"
 */
fun parseChatMessage(rawMessage: String): ParsedChatMessage {
    var message = rawMessage
    var effect = ChatTextEffect.NONE
    var color = ChatTextColor.YELLOW

    // Parse effect tags
    val effectTags = mapOf(
        "wave:" to ChatTextEffect.WAVE,
        "wave2:" to ChatTextEffect.WAVE2,
        "scroll:" to ChatTextEffect.SCROLL,
        "shake:" to ChatTextEffect.SHAKE,
        "slide:" to ChatTextEffect.SLIDE,
        "flash1:" to ChatTextEffect.FLASH1,
        "flash2:" to ChatTextEffect.FLASH2,
        "flash3:" to ChatTextEffect.FLASH3,
        "glow1:" to ChatTextEffect.GLOW1,
        "glow2:" to ChatTextEffect.GLOW2,
        "glow3:" to ChatTextEffect.GLOW3
    )

    // Parse color tags
    val colorTags = mapOf(
        "yellow:" to ChatTextColor.YELLOW,
        "red:" to ChatTextColor.RED,
        "green:" to ChatTextColor.GREEN,
        "cyan:" to ChatTextColor.CYAN,
        "purple:" to ChatTextColor.PURPLE,
        "white:" to ChatTextColor.WHITE,
        "black:" to ChatTextColor.BLACK,
        "orange:" to ChatTextColor.ORANGE,
        "blue:" to ChatTextColor.BLUE
    )

    // Check for effect tags (case insensitive)
    val lowerMessage = message.lowercase()
    for ((tag, eff) in effectTags) {
        if (lowerMessage.startsWith(tag)) {
            effect = eff
            message = message.substring(tag.length)
            break
        }
    }

    // Check for color tags (case insensitive)
    val lowerAfterEffect = message.lowercase()
    for ((tag, col) in colorTags) {
        if (lowerAfterEffect.startsWith(tag)) {
            color = col
            message = message.substring(tag.length)
            break
        }
    }

    return ParsedChatMessage(message, effect, color)
}

/**
 * Composable that renders chat text with RS-style effects
 */
@Composable
fun RSChatText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    parseEffects: Boolean = true
) {
    val parsed =
        if (parseEffects) parseChatMessage(text) else ParsedChatMessage(text, ChatTextEffect.NONE, ChatTextColor.YELLOW)

    when (parsed.effect) {
        ChatTextEffect.NONE -> {
            Text(
                text = parsed.text,
                color = parsed.color.color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                modifier = modifier
            )
        }

        ChatTextEffect.WAVE, ChatTextEffect.WAVE2 -> {
            WaveText(
                text = parsed.text,
                color = parsed.color.color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                speed = if (parsed.effect == ChatTextEffect.WAVE2) 2f else 1f,
                modifier = modifier
            )
        }

        ChatTextEffect.SCROLL -> {
            ScrollText(
                text = parsed.text,
                color = parsed.color.color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                modifier = modifier
            )
        }

        ChatTextEffect.SHAKE -> {
            ShakeText(
                text = parsed.text,
                color = parsed.color.color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                modifier = modifier
            )
        }

        ChatTextEffect.SLIDE -> {
            SlideText(
                text = parsed.text,
                color = parsed.color.color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                modifier = modifier
            )
        }

        ChatTextEffect.FLASH1 -> {
            FlashText(
                text = parsed.text,
                color1 = Color(0xFFFF0000), // Red
                color2 = Color(0xFFFFFF00), // Yellow
                fontSize = fontSize,
                fontWeight = fontWeight,
                modifier = modifier
            )
        }

        ChatTextEffect.FLASH2 -> {
            FlashText(
                text = parsed.text,
                color1 = Color(0xFF00FFFF), // Cyan
                color2 = Color(0xFF0000FF), // Blue
                fontSize = fontSize,
                fontWeight = fontWeight,
                modifier = modifier
            )
        }

        ChatTextEffect.FLASH3 -> {
            FlashText(
                text = parsed.text,
                color1 = Color(0xFF00FF00), // Green
                color2 = Color(0xFF80FF80), // Light green
                fontSize = fontSize,
                fontWeight = fontWeight,
                modifier = modifier
            )
        }

        ChatTextEffect.GLOW1 -> {
            GlowText(
                text = parsed.text,
                startHue = 0f, // Red
                fontSize = fontSize,
                fontWeight = fontWeight,
                modifier = modifier
            )
        }

        ChatTextEffect.GLOW2 -> {
            GlowText(
                text = parsed.text,
                startHue = 180f, // Cyan
                fontSize = fontSize,
                fontWeight = fontWeight,
                modifier = modifier
            )
        }

        ChatTextEffect.GLOW3 -> {
            GlowText(
                text = parsed.text,
                startHue = 120f, // Green
                fontSize = fontSize,
                fontWeight = fontWeight,
                modifier = modifier
            )
        }
    }
}

/**
 * Wave effect - characters move up and down in a sine wave pattern
 */
@Composable
fun WaveText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    speed: Float = 1f,
    amplitude: Dp = 4.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (2000 / speed).toInt(),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Row(modifier = modifier) {
        text.forEachIndexed { index, char ->
            val offset = sin((phase + index * 30) * PI / 180.0).toFloat() * amplitude.value
            Text(
                text = char.toString(),
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                modifier = Modifier.offset { IntOffset(0, offset.roundToInt()) }
            )
        }
    }
}

/**
 * Scroll effect - text scrolls horizontally
 */
@Composable
fun ScrollText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    scrollWidth: Dp = 200.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scroll")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = scrollWidth.value,
        targetValue = -scrollWidth.value,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 5000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "scrollOffset"
    )

    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = modifier.offset { IntOffset(offsetX.roundToInt(), 0) },
        maxLines = 1
    )
}

/**
 * Shake effect - text shakes randomly
 */
@Composable
fun ShakeText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    intensity: Dp = 2.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shake")

    Row(modifier = modifier) {
        text.forEachIndexed { index, char ->
            val offsetX by infiniteTransition.animateFloat(
                initialValue = -intensity.value,
                targetValue = intensity.value,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 50 + (index % 3) * 20,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "shakeX$index"
            )
            val offsetY by infiniteTransition.animateFloat(
                initialValue = intensity.value,
                targetValue = -intensity.value,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 60 + (index % 4) * 15,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "shakeY$index"
            )

            Text(
                text = char.toString(),
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            )
        }
    }
}

/**
 * Slide effect - text slides in from the left
 */
@Composable
fun SlideText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    var hasSlid by remember { mutableStateOf(false) }
    val offsetX by animateFloatAsState(
        targetValue = if (hasSlid) 0f else -100f,
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "slideIn"
    )
    val alpha by animateFloatAsState(
        targetValue = if (hasSlid) 1f else 0f,
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "fadeIn"
    )

    LaunchedEffect(Unit) {
        hasSlid = true
    }

    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .alpha(alpha)
    )
}

/**
 * Flash effect - text alternates between two colors
 */
@Composable
fun FlashText(
    text: String,
    color1: Color,
    color2: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    flashDuration: Int = 500,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flash")
    val colorProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = flashDuration,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flashColor"
    )

    val currentColor = lerp(color1, color2, colorProgress)

    Text(
        text = text,
        color = currentColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = modifier
    )
}

/**
 * Glow effect - text cycles through rainbow colors
 */
@Composable
fun GlowText(
    text: String,
    startHue: Float,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    cycleDuration: Int = 3000,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val hueProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = cycleDuration,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "glowHue"
    )

    val currentHue = (startHue + hueProgress) % 360f
    val currentColor = hsvToColor(currentHue, 1f, 1f)

    Text(
        text = text,
        color = currentColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = modifier,
        style = TextStyle(
            shadow = Shadow(
                color = currentColor.copy(alpha = 0.5f),
                offset = Offset(0f, 0f),
                blurRadius = 8f
            )
        )
    )
}

/**
 * Rainbow text - each character has a different color cycling through the rainbow
 */
@Composable
fun RainbowText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rainbow")
    val hueOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rainbowHue"
    )

    Row(modifier = modifier) {
        text.forEachIndexed { index, char ->
            val hue = (hueOffset + index * 30f) % 360f
            Text(
                text = char.toString(),
                color = hsvToColor(hue, 1f, 1f),
                fontSize = fontSize,
                fontWeight = fontWeight
            )
        }
    }
}

/**
 * Typing effect - text appears character by character
 */
@Composable
fun TypingText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    characterDelay: Long = 50L,
    onComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var visibleCharCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(text) {
        visibleCharCount = 0
        for (i in text.indices) {
            kotlinx.coroutines.delay(characterDelay)
            visibleCharCount = i + 1
        }
        onComplete()
    }

    Text(
        text = text.take(visibleCharCount),
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = modifier
    )
}

/**
 * Shadow text with customizable shadow
 */
@Composable
fun ShadowText(
    text: String,
    color: Color,
    shadowColor: Color = Color.Black,
    shadowOffset: Offset = Offset(1f, 1f),
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        style = TextStyle(
            shadow = Shadow(
                color = shadowColor,
                offset = shadowOffset,
                blurRadius = 0f
            )
        ),
        modifier = modifier
    )
}

/**
 * Outlined text (stroke effect)
 */
@Composable
fun OutlinedText(
    text: String,
    fillColor: Color,
    outlineColor: Color = Color.Black,
    outlineWidth: Float = 1f,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    // Draw outline by drawing text in 4 directions
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        // Outline layers (offset in 4 directions)
        val offsets = listOf(
            IntOffset(-outlineWidth.roundToInt(), 0),
            IntOffset(outlineWidth.roundToInt(), 0),
            IntOffset(0, -outlineWidth.roundToInt()),
            IntOffset(0, outlineWidth.roundToInt())
        )

        offsets.forEach { offset ->
            Text(
                text = text,
                color = outlineColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                modifier = Modifier.offset { offset }
            )
        }

        // Main text on top
        Text(
            text = text,
            color = fillColor,
            fontSize = fontSize,
            fontWeight = fontWeight
        )
    }
}

/**
 * Pulsing text - scales up and down
 */
@Composable
fun PulsingText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    minScale: Float = 0.9f,
    maxScale: Float = 1.1f,
    pulseDuration: Int = 1000,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = pulseDuration,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Text(
        text = text,
        color = color,
        fontSize = fontSize * scale,
        fontWeight = fontWeight,
        modifier = modifier
    )
}

// ============ Helper Functions ============

/**
 * Linear interpolation between two colors
 */
private fun lerp(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red = start.red + (end.red - start.red) * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue = start.blue + (end.blue - start.blue) * fraction,
        alpha = start.alpha + (end.alpha - start.alpha) * fraction
    )
}

/**
 * Convert HSV to Color
 */
private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = (hue % 360) / 60f
    val c = value * saturation
    val x = c * (1 - kotlin.math.abs(h % 2 - 1))
    val m = value - c

    val (r, g, b) = when {
        h < 1 -> Triple(c, x, 0f)
        h < 2 -> Triple(x, c, 0f)
        h < 3 -> Triple(0f, c, x)
        h < 4 -> Triple(0f, x, c)
        h < 5 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(
        red = r + m,
        green = g + m,
        blue = b + m
    )
}

/**
 * Preview/demo of all chat effects
 */
@Composable
fun ChatEffectsDemo() {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        RSChatText("wave:This text has a wave effect!")
        RSChatText("wave2:This text has a faster wave effect!")
        RSChatText("shake:This text is shaking!")
        RSChatText("flash1:red:Flashing red and yellow!")
        RSChatText("flash2:cyan:Flashing cyan and blue!")
        RSChatText("flash3:green:Flashing greens!")
        RSChatText("glow1:Rainbow glow from red!")
        RSChatText("glow2:Rainbow glow from cyan!")
        RSChatText("glow3:Rainbow glow from green!")
        RSChatText("slide:This text slides in!")
        RSChatText("purple:Just purple text")
        RSChatText("orange:Just orange text")
    }
}
