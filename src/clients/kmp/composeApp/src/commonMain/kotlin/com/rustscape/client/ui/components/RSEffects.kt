package com.rustscape.client.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlin.random.Random

/**
 * Animated torch flame effect for login screen
 * Creates a flickering fire animation with particles
 */
@Composable
fun TorchFlame(
    modifier: Modifier = Modifier,
    flameWidth: Dp = 40.dp,
    flameHeight: Dp = 80.dp,
    intensity: Float = 1.0f
) {
    // Animation for flame movement
    val infiniteTransition = rememberInfiniteTransition(label = "torch_flame")

    val flicker1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker1"
    )

    val flicker2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker2"
    )

    val flicker3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker3"
    )

    // Particle system state
    val particles = remember { mutableStateListOf<FlameParticle>() }
    val particleSeed = remember { Random.nextInt() }

    // Update particles each frame
    LaunchedEffect(flicker1) {
        // Add new particles
        if (Random.nextFloat() < 0.3f * intensity) {
            particles.add(
                FlameParticle(
                    x = Random.nextFloat() * 0.6f + 0.2f,
                    y = 0.8f,
                    vx = (Random.nextFloat() - 0.5f) * 0.02f,
                    vy = -Random.nextFloat() * 0.03f - 0.01f,
                    life = 1f,
                    size = Random.nextFloat() * 6f + 2f
                )
            )
        }

        // Update existing particles
        val toRemove = mutableListOf<FlameParticle>()
        particles.forEach { p ->
            p.x += p.vx
            p.y += p.vy
            p.life -= 0.05f
            if (p.life <= 0f) toRemove.add(p)
        }
        particles.removeAll(toRemove)

        // Limit particle count
        while (particles.size > 30) {
            particles.removeAt(0)
        }
    }

    Canvas(
        modifier = modifier.size(flameWidth, flameHeight)
    ) {
        val width = size.width
        val height = size.height
        val centerX = width / 2

        // Draw flame layers from back to front
        drawFlameLayer(
            centerX = centerX,
            baseY = height,
            width = width * 0.8f * (0.9f + flicker3 * 0.1f),
            height = height * 0.9f * (0.85f + flicker1 * 0.15f),
            color = Color(0xFFFF4500).copy(alpha = 0.6f * intensity), // Orange-red
            intensity = intensity
        )

        drawFlameLayer(
            centerX = centerX + (flicker2 - 0.5f) * 4f,
            baseY = height,
            width = width * 0.6f * (0.9f + flicker1 * 0.1f),
            height = height * 0.75f * (0.9f + flicker2 * 0.1f),
            color = Color(0xFFFF6B00).copy(alpha = 0.7f * intensity), // Orange
            intensity = intensity
        )

        drawFlameLayer(
            centerX = centerX + (flicker1 - 0.5f) * 3f,
            baseY = height,
            width = width * 0.4f * (0.9f + flicker2 * 0.1f),
            height = height * 0.6f * (0.85f + flicker3 * 0.15f),
            color = Color(0xFFFFAA00).copy(alpha = 0.8f * intensity), // Yellow-orange
            intensity = intensity
        )

        // Inner bright core
        drawFlameLayer(
            centerX = centerX,
            baseY = height,
            width = width * 0.25f,
            height = height * 0.4f * (0.9f + flicker1 * 0.1f),
            color = Color(0xFFFFDD44).copy(alpha = 0.9f * intensity), // Bright yellow
            intensity = intensity
        )

        // Draw particles
        particles.forEach { p ->
            val particleAlpha = (p.life * 0.8f * intensity).coerceIn(0f, 1f)
            val particleColor = when {
                p.life > 0.7f -> Color(0xFFFFDD44)
                p.life > 0.4f -> Color(0xFFFF8800)
                else -> Color(0xFFFF4400)
            }

            drawCircle(
                color = particleColor.copy(alpha = particleAlpha),
                radius = p.size * p.life,
                center = Offset(p.x * width, p.y * height),
                blendMode = BlendMode.Plus
            )
        }

        // Glow effect at base
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFAA00).copy(alpha = 0.4f * intensity),
                    Color(0xFFFF6600).copy(alpha = 0.2f * intensity),
                    Color.Transparent
                ),
                center = Offset(centerX, height),
                radius = width * 0.8f
            ),
            radius = width * 0.8f,
            center = Offset(centerX, height)
        )
    }
}

/**
 * Draw a single flame layer with bezier curves
 */
private fun DrawScope.drawFlameLayer(
    centerX: Float,
    baseY: Float,
    width: Float,
    height: Float,
    color: Color,
    intensity: Float
) {
    val path = Path().apply {
        moveTo(centerX - width / 2, baseY)

        // Left curve
        cubicTo(
            centerX - width / 2, baseY - height * 0.3f,
            centerX - width / 4, baseY - height * 0.6f,
            centerX, baseY - height
        )

        // Right curve
        cubicTo(
            centerX + width / 4, baseY - height * 0.6f,
            centerX + width / 2, baseY - height * 0.3f,
            centerX + width / 2, baseY
        )

        close()
    }

    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = 0f),
                color.copy(alpha = color.alpha * 0.5f),
                color
            ),
            startY = baseY - height,
            endY = baseY
        )
    )
}

/**
 * Particle data class for flame effects
 */
private data class FlameParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    var size: Float
)

/**
 * Sparkle/glitter effect for gold text or rewards
 */
@Composable
fun SparkleEffect(
    modifier: Modifier = Modifier,
    sparkleCount: Int = 10,
    sparkleColor: Color = Color(0xFFFFD700),
    intensity: Float = 1.0f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sparkle")

    val sparkles = remember {
        List(sparkleCount) {
            SparkleData(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                phase = Random.nextFloat() * 2 * PI.toFloat(),
                speed = Random.nextFloat() * 0.5f + 0.5f,
                size = Random.nextFloat() * 3f + 1f
            )
        }
    }

    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkle_time"
    )

    Canvas(modifier = modifier) {
        sparkles.forEach { sparkle ->
            val alpha = ((sin(time * sparkle.speed + sparkle.phase) + 1f) / 2f * intensity).coerceIn(0f, 1f)

            if (alpha > 0.1f) {
                val centerX = sparkle.x * size.width
                val centerY = sparkle.y * size.height
                val sparkleSize = sparkle.size * alpha

                // Draw 4-point star
                drawStar(
                    center = Offset(centerX, centerY),
                    size = sparkleSize,
                    color = sparkleColor.copy(alpha = alpha),
                    points = 4
                )
            }
        }
    }
}

private data class SparkleData(
    val x: Float,
    val y: Float,
    val phase: Float,
    val speed: Float,
    val size: Float
)

/**
 * Draw a star shape
 */
private fun DrawScope.drawStar(
    center: Offset,
    size: Float,
    color: Color,
    points: Int = 4
) {
    val path = Path()
    val innerRadius = size * 0.3f
    val outerRadius = size

    for (i in 0 until points * 2) {
        val radius = if (i % 2 == 0) outerRadius else innerRadius
        val angle = (i * PI / points - PI / 2).toFloat()
        val x = center.x + cos(angle) * radius
        val y = center.y + sin(angle) * radius

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()

    drawPath(path, color)

    // Add glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.5f), Color.Transparent),
            center = center,
            radius = size * 1.5f
        ),
        radius = size * 1.5f,
        center = center
    )
}

/**
 * Pulsing glow effect for buttons or important elements
 */
@Composable
fun PulsingGlow(
    modifier: Modifier = Modifier,
    glowColor: Color = RSColors.GoldMid,
    minAlpha: Float = 0.3f,
    maxAlpha: Float = 0.8f,
    pulseDuration: Int = 1500
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow_pulse")

    val alpha by infiniteTransition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = maxOf(size.width, size.height) / 2

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = alpha),
                    glowColor.copy(alpha = alpha * 0.5f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = radius
            ),
            radius = radius,
            center = Offset(centerX, centerY)
        )
    }
}

/**
 * Animated border glow effect
 */
@Composable
fun AnimatedBorderGlow(
    modifier: Modifier = Modifier,
    borderColor: Color = RSColors.GoldMid,
    glowColor: Color = RSColors.GoldLight,
    borderWidth: Dp = 2.dp,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "border_glow")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_glow_alpha"
    )

    Box(modifier = modifier) {
        // Glow layer
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = borderWidth.toPx()

            // Outer glow
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        glowColor.copy(alpha = glowAlpha * 0.5f),
                        glowColor.copy(alpha = glowAlpha),
                        glowColor.copy(alpha = glowAlpha * 0.5f)
                    )
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth * 3,
                    pathEffect = PathEffect.cornerPathEffect(4f)
                )
            )

            // Main border
            drawRect(
                color = borderColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    pathEffect = PathEffect.cornerPathEffect(4f)
                )
            )
        }

        content()
    }
}

/**
 * Floating text animation (like damage numbers or XP drops)
 */
@Composable
fun FloatingText(
    text: String,
    color: Color = RSColors.TextYellow,
    startOffset: Offset = Offset.Zero,
    onAnimationEnd: () -> Unit = {}
) {
    var isVisible by remember { mutableStateOf(true) }

    val animatedOffset by animateFloatAsState(
        targetValue = if (isVisible) -50f else 0f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        finishedListener = {
            isVisible = false
            onAnimationEnd()
        },
        label = "floating_y"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 0f else 1f,
        animationSpec = tween(1500),
        label = "floating_alpha"
    )

    if (isVisible || alpha > 0f) {
        Box(
            modifier = Modifier.offset(
                x = startOffset.x.dp,
                y = (startOffset.y + animatedOffset).dp
            )
        ) {
            RSText(
                text = text,
                color = color.copy(alpha = 1f - alpha),
                fontSize = 14,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
    }
}

/**
 * XP drop effect - shows floating XP gain
 */
@Composable
fun XPDrop(
    amount: Int,
    skillColor: Color = RSColors.TextYellow,
    onComplete: () -> Unit = {}
) {
    FloatingText(
        text = "+$amount XP",
        color = skillColor,
        startOffset = Offset(0f, 0f),
        onAnimationEnd = onComplete
    )
}

/**
 * Hit splat effect for combat
 */
@Composable
fun HitSplat(
    damage: Int,
    isPoison: Boolean = false,
    isHeal: Boolean = false,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isHeal -> Color(0xFF00AA00)
        isPoison -> Color(0xFF00AA00)
        damage == 0 -> Color(0xFF0000AA)
        else -> Color(0xFFAA0000)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "hit_splat")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hit_scale"
    )

    Canvas(modifier = modifier.size(24.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2 * scale

        // Shadow
        drawCircle(
            color = Color.Black.copy(alpha = 0.5f),
            radius = radius,
            center = Offset(centerX + 2f, centerY + 2f)
        )

        // Main splat
        drawCircle(
            color = backgroundColor,
            radius = radius,
            center = Offset(centerX, centerY)
        )

        // Highlight
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.4f),
                    Color.Transparent
                ),
                center = Offset(centerX - radius * 0.3f, centerY - radius * 0.3f),
                radius = radius * 0.6f
            ),
            radius = radius,
            center = Offset(centerX, centerY)
        )

        // Border
        drawCircle(
            color = Color.Black,
            radius = radius,
            center = Offset(centerX, centerY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        )
    }

    // Damage number would be drawn on top
    Box(modifier = modifier.size(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        RSText(
            text = if (damage == 0 && !isHeal) "0" else damage.toString(),
            color = RSColors.TextWhite,
            fontSize = 10,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}

/**
 * Loading spinner in RS style
 */
@Composable
fun RSLoadingSpinner(
    modifier: Modifier = Modifier,
    color: Color = RSColors.GoldMid,
    strokeWidth: Dp = 3.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading_spinner")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinner_rotation"
    )

    Canvas(modifier = modifier.size(32.dp)) {
        rotate(rotation) {
            drawArc(
                color = color.copy(alpha = 0.3f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            )

            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

/**
 * Animated level up effect
 */
@Composable
fun LevelUpEffect(
    skillName: String,
    newLevel: Int,
    onComplete: () -> Unit = {}
) {
    var isVisible by remember { mutableStateOf(true) }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1.2f else 0.8f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "level_up_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(2000),
        finishedListener = {
            isVisible = false
            onComplete()
        },
        label = "level_up_alpha"
    )

    if (alpha > 0f) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            // Firework particles in background
            SparkleEffect(
                modifier = Modifier.fillMaxSize(),
                sparkleCount = 20,
                sparkleColor = RSColors.GoldLight,
                intensity = alpha
            )

            // Level up text
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                RSText(
                    text = "Level Up!",
                    color = RSColors.TextYellow.copy(alpha = alpha),
                    fontSize = (24 * scale).toInt(),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )

                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.size(8.dp)
                )

                RSText(
                    text = "$skillName: $newLevel",
                    color = RSColors.TextWhite.copy(alpha = alpha),
                    fontSize = (18 * scale).toInt(),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
        }
    }
}
