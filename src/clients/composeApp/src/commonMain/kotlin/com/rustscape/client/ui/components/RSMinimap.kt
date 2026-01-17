package com.rustscape.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rustscape.client.game.Position
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimap tile types for rendering
 */
enum class MinimapTileType(val color: Color) {
    GRASS(Color(0xFF2D5A2D)),           // Standard grass
    DARK_GRASS(Color(0xFF1E4A1E)),      // Darker grass/forest
    LIGHT_GRASS(Color(0xFF3D6A3D)),     // Lighter grass/field
    WATER(Color(0xFF1E4A7A)),           // Water/ocean
    SHALLOW_WATER(Color(0xFF2E5A8A)),   // Shallow water/rivers
    PATH(Color(0xFF8B7355)),            // Dirt paths
    ROAD(Color(0xFF6B6B6B)),            // Stone roads
    SAND(Color(0xFFD4B896)),            // Beach/desert sand
    BUILDING(Color(0xFF4A4A4A)),        // Building floors
    WALL(Color(0xFF2A2A2A)),            // Walls/obstacles
    TREE(Color(0xFF1A3A1A)),            // Trees
    ROCK(Color(0xFF5A5A5A)),            // Rocks/mountains
    BRIDGE(Color(0xFF7A5A3A)),          // Bridges
    UNKNOWN(Color(0xFF333333));         // Unknown/unloaded

    companion object {
        /**
         * Get tile type from terrain flags
         */
        fun fromFlags(flags: Int): MinimapTileType {
            return when {
                flags and 0x1 != 0 -> WALL           // Blocked
                flags and 0x2 != 0 -> WATER         // Water
                flags and 0x4 != 0 -> BUILDING      // Roof
                flags and 0x8 != 0 -> PATH          // Path
                else -> GRASS
            }
        }
    }
}

/**
 * Minimap icon types for entities/objects
 */
enum class MinimapIconType(val color: Color, val priority: Int) {
    PLAYER(Color.White, 10),                    // Local player (always centered)
    OTHER_PLAYER(Color.White, 8),               // Other players
    FRIEND(Color(0xFF00FF00), 9),               // Friends online
    CLAN_MEMBER(Color(0xFF00AAFF), 9),          // Clan members
    NPC_YELLOW(Color(0xFFFFFF00), 5),           // Yellow dot NPCs (traders, etc.)
    NPC_RED(Color(0xFFFF0000), 6),              // Red dot NPCs (enemies)
    ITEM(Color(0xFFFF00FF), 4),                 // Ground items
    OBJECT(Color(0xFF00FFFF), 3),               // Interactive objects
    BANK(Color(0xFFFFD700), 7),                 // Bank booth icon
    ALTAR(Color(0xFFFFFFFF), 7),                // Altar icon
    FISHING(Color(0xFF0066FF), 7),              // Fishing spot
    MINING(Color(0xFF8B4513), 7),               // Mining spot
    TREE(Color(0xFF228B22), 7),                 // Tree for woodcutting
    TRANSPORT(Color(0xFF8B0000), 7);            // Transportation (boats, etc.)
}

/**
 * Represents an entity to display on the minimap
 */
data class MinimapEntity(
    val x: Int,
    val y: Int,
    val type: MinimapIconType,
    val name: String = "",
    val size: Int = 1  // Tile size (1 for players, larger for big NPCs)
)

/**
 * Minimap state holder
 */
class MinimapState {
    // Map tiles (local area around player)
    // Each tile is stored as MinimapTileType
    private val tileData = mutableStateMapOf<Long, MinimapTileType>()

    // Entities on the minimap
    var entities by mutableStateOf<List<MinimapEntity>>(emptyList())

    // Compass rotation (0 = north up)
    var compassRotation by mutableStateOf(0f)

    // View radius in tiles
    var viewRadius by mutableStateOf(32)

    // Flag destination marker
    var flagDestination by mutableStateOf<Position?>(null)

    /**
     * Get tile key for coordinates
     */
    private fun tileKey(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFF)

    /**
     * Set tile type at coordinates
     */
    fun setTile(x: Int, y: Int, type: MinimapTileType) {
        tileData[tileKey(x, y)] = type
    }

    /**
     * Get tile type at coordinates
     */
    fun getTile(x: Int, y: Int): MinimapTileType {
        return tileData[tileKey(x, y)] ?: MinimapTileType.UNKNOWN
    }

    /**
     * Set terrain data for a region
     */
    fun setRegionTerrain(baseX: Int, baseY: Int, terrain: Array<IntArray>) {
        for (localY in terrain.indices) {
            for (localX in terrain[localY].indices) {
                val type = MinimapTileType.fromFlags(terrain[localY][localX])
                setTile(baseX + localX, baseY + localY, type)
            }
        }
    }

    /**
     * Clear all tile data
     */
    fun clearTiles() {
        tileData.clear()
    }

    /**
     * Load terrain data for the area around a position
     * Uses real region data when available, procedural fallback otherwise
     */
    fun loadTerrainAround(centerX: Int, centerY: Int, radius: Int = 64) {
        // Calculate which regions we need to load
        val minRegionX = (centerX - radius) shr 6
        val maxRegionX = (centerX + radius) shr 6
        val minRegionY = (centerY - radius) shr 6
        val maxRegionY = (centerY + radius) shr 6

        // Load each region
        for (regionY in minRegionY..maxRegionY) {
            for (regionX in minRegionX..maxRegionX) {
                loadRegion(regionX, regionY)
            }
        }
    }

    /**
     * Load terrain data for a specific region
     */
    private fun loadRegion(regionX: Int, regionY: Int) {
        val baseX = regionX shl 6
        val baseY = regionY shl 6

        // Try to get real terrain data first
        val terrain = MapTerrainData.getOrGenerateTerrain(regionX, regionY)

        // Store the terrain tiles
        for (localY in 0..63) {
            for (localX in 0..63) {
                setTile(baseX + localX, baseY + localY, terrain[localY][localX])
            }
        }
    }

    /**
     * Generate procedural terrain for demo/testing (legacy fallback)
     */
    fun generateDemoTerrain(centerX: Int, centerY: Int, radius: Int = 64) {
        // Use the new terrain loading system
        loadTerrainAround(centerX, centerY, radius)
    }

    /**
     * Check if a region is a known/mapped area
     */
    fun isKnownRegion(regionX: Int, regionY: Int): Boolean {
        return MapTerrainData.hasRegion(regionX, regionY)
    }

    /**
     * Get region info for debugging
     */
    fun getRegionInfo(worldX: Int, worldY: Int): String {
        val regionX = worldX shr 6
        val regionY = worldY shr 6
        val isKnown = MapTerrainData.hasRegion(regionX, regionY)
        return "Region ($regionX, $regionY) - ${if (isKnown) "Mapped" else "Procedural"}"
    }
}

/**
 * Remember minimap state
 */
@Composable
fun rememberMinimapState(): MinimapState {
    return remember { MinimapState() }
}

/**
 * Enhanced RSMinimap with real terrain rendering and entity dots
 *
 * @param playerPosition Current player position
 * @param minimapState State containing terrain and entity data
 * @param onMinimapClick Callback when minimap is clicked (for walk-to)
 * @param size Size of the minimap
 * @param modifier Modifier for the composable
 */
@Composable
fun RSMinimapEnhanced(
    playerPosition: Position,
    minimapState: MinimapState,
    onMinimapClick: ((worldX: Int, worldY: Int) -> Unit)? = null,
    minimapSize: Dp = 152.dp,
    modifier: Modifier = Modifier
) {
    val viewRadius = minimapState.viewRadius

    Box(
        modifier = modifier
            .size(minimapSize)
            .aspectRatio(1f)
    ) {
        // Main minimap canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .pointerInput(playerPosition, viewRadius) {
                    if (onMinimapClick != null) {
                        detectTapGestures { offset ->
                            // Convert click position to world coordinates
                            val sizeWidth = this.size.width.toFloat()
                            val sizeHeight = this.size.height.toFloat()
                            val centerX = sizeWidth / 2f
                            val centerY = sizeHeight / 2f
                            val radius = minOf(centerX, centerY)
                            val pixelsPerTile = radius / viewRadius

                            // Check if click is within the circular map area
                            val dx = offset.x - centerX
                            val dy = offset.y - centerY
                            val distFromCenter = kotlin.math.sqrt(dx * dx + dy * dy)

                            if (distFromCenter <= radius) {
                                // Convert to tile offset from player
                                val tileOffsetX = (dx / pixelsPerTile).toInt()
                                val tileOffsetY = -(dy / pixelsPerTile).toInt() // Y is inverted

                                val worldX = playerPosition.x + tileOffsetX
                                val worldY = playerPosition.y + tileOffsetY

                                onMinimapClick(worldX, worldY)
                            }
                        }
                    }
                }
        ) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val radius = minOf(centerX, centerY)
            val pixelsPerTile = radius / viewRadius

            // Draw terrain
            drawMinimapTerrain(
                playerX = playerPosition.x,
                playerY = playerPosition.y,
                centerX = centerX,
                centerY = centerY,
                radius = radius,
                pixelsPerTile = pixelsPerTile,
                viewRadius = viewRadius,
                minimapState = minimapState
            )

            // Draw flag destination if set
            minimapState.flagDestination?.let { flag ->
                drawFlagMarker(
                    flagX = flag.x,
                    flagY = flag.y,
                    playerX = playerPosition.x,
                    playerY = playerPosition.y,
                    centerX = centerX,
                    centerY = centerY,
                    pixelsPerTile = pixelsPerTile,
                    radius = radius
                )
            }

            // Draw entities
            drawMinimapEntities(
                entities = minimapState.entities,
                playerX = playerPosition.x,
                playerY = playerPosition.y,
                centerX = centerX,
                centerY = centerY,
                pixelsPerTile = pixelsPerTile,
                radius = radius
            )

            // Draw player dot (always centered)
            drawPlayerDot(centerX, centerY)
        }

        // Minimap frame overlay
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawMinimapFrameOverlay(this.size, minimapState.compassRotation)
        }

        // Compass rose in corner
        MinimapCompass(
            rotation = minimapState.compassRotation,
            modifier = Modifier
                .size(28.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-2).dp, y = 2.dp)
        )
    }
}

/**
 * Draw the terrain tiles on the minimap
 */
private fun DrawScope.drawMinimapTerrain(
    playerX: Int,
    playerY: Int,
    centerX: Float,
    centerY: Float,
    radius: Float,
    pixelsPerTile: Float,
    viewRadius: Int,
    minimapState: MinimapState
) {
    // Draw background (water for unloaded areas)
    drawCircle(
        color = MinimapTileType.WATER.color,
        radius = radius,
        center = Offset(centerX, centerY)
    )

    // Draw visible tiles
    for (dy in -viewRadius..viewRadius) {
        for (dx in -viewRadius..viewRadius) {
            val worldX = playerX + dx
            val worldY = playerY + dy

            // Calculate screen position (Y is inverted for screen coordinates)
            val screenX = centerX + dx * pixelsPerTile
            val screenY = centerY - dy * pixelsPerTile  // Invert Y

            // Check if tile is within circular view
            val distFromCenter = kotlin.math.sqrt(
                (screenX - centerX) * (screenX - centerX) +
                        (screenY - centerY) * (screenY - centerY)
            )
            if (distFromCenter > radius) continue

            // Get tile type and draw
            val tileType = minimapState.getTile(worldX, worldY)
            if (tileType != MinimapTileType.UNKNOWN) {
                drawRect(
                    color = tileType.color,
                    topLeft = Offset(screenX - pixelsPerTile / 2, screenY - pixelsPerTile / 2),
                    size = Size(pixelsPerTile + 1, pixelsPerTile + 1)
                )
            }
        }
    }
}

/**
 * Draw entities on the minimap
 */
private fun DrawScope.drawMinimapEntities(
    entities: List<MinimapEntity>,
    playerX: Int,
    playerY: Int,
    centerX: Float,
    centerY: Float,
    pixelsPerTile: Float,
    radius: Float
) {
    // Sort by priority (lower priority drawn first, higher on top)
    val sortedEntities = entities.sortedBy { it.type.priority }

    for (entity in sortedEntities) {
        val dx = entity.x - playerX
        val dy = entity.y - playerY

        val screenX = centerX + dx * pixelsPerTile
        val screenY = centerY - dy * pixelsPerTile  // Invert Y

        // Check if within view
        val distFromCenter = kotlin.math.sqrt(
            (screenX - centerX) * (screenX - centerX) +
                    (screenY - centerY) * (screenY - centerY)
        )
        if (distFromCenter > radius - 4) continue

        // Draw entity dot
        val dotSize = when (entity.type) {
            MinimapIconType.PLAYER -> 5f
            MinimapIconType.OTHER_PLAYER, MinimapIconType.FRIEND, MinimapIconType.CLAN_MEMBER -> 4f
            MinimapIconType.NPC_YELLOW, MinimapIconType.NPC_RED -> 3f * entity.size
            MinimapIconType.ITEM -> 3f
            else -> 4f
        }

        // Draw black outline
        drawCircle(
            color = Color.Black,
            radius = dotSize + 1f,
            center = Offset(screenX, screenY)
        )

        // Draw colored dot
        drawCircle(
            color = entity.type.color,
            radius = dotSize,
            center = Offset(screenX, screenY)
        )
    }
}

/**
 * Draw the local player dot (always centered)
 */
private fun DrawScope.drawPlayerDot(centerX: Float, centerY: Float) {
    // White dot with black border
    drawCircle(
        color = Color.Black,
        radius = 6f,
        center = Offset(centerX, centerY)
    )
    drawCircle(
        color = Color.White,
        radius = 4f,
        center = Offset(centerX, centerY)
    )
    // Inner highlight
    drawCircle(
        color = Color.White.copy(alpha = 0.8f),
        radius = 2f,
        center = Offset(centerX - 1f, centerY - 1f)
    )
}

/**
 * Draw flag destination marker
 */
private fun DrawScope.drawFlagMarker(
    flagX: Int,
    flagY: Int,
    playerX: Int,
    playerY: Int,
    centerX: Float,
    centerY: Float,
    pixelsPerTile: Float,
    radius: Float
) {
    val dx = flagX - playerX
    val dy = flagY - playerY

    val screenX = centerX + dx * pixelsPerTile
    val screenY = centerY - dy * pixelsPerTile

    // Check if within view
    val distFromCenter = kotlin.math.sqrt(
        (screenX - centerX) * (screenX - centerX) +
                (screenY - centerY) * (screenY - centerY)
    )

    val drawX: Float
    val drawY: Float

    if (distFromCenter > radius - 8) {
        // Clamp to edge of minimap
        val angle = kotlin.math.atan2(
            (screenY - centerY).toDouble(),
            (screenX - centerX).toDouble()
        ).toFloat()
        drawX = centerX + cos(angle) * (radius - 8)
        drawY = centerY + sin(angle) * (radius - 8)
    } else {
        drawX = screenX
        drawY = screenY
    }

    // Draw red flag
    val flagPath = Path().apply {
        moveTo(drawX, drawY)
        lineTo(drawX, drawY - 12f)
        lineTo(drawX + 8f, drawY - 8f)
        lineTo(drawX, drawY - 4f)
        close()
    }

    // Flag pole
    drawLine(
        color = Color.Black,
        start = Offset(drawX, drawY),
        end = Offset(drawX, drawY - 12f),
        strokeWidth = 2f
    )

    // Flag
    drawPath(
        path = flagPath,
        color = Color.Red
    )
    drawPath(
        path = flagPath,
        color = Color.Black,
        style = Stroke(width = 1f)
    )
}

/**
 * Draw minimap frame overlay (stone border)
 */
private fun DrawScope.drawMinimapFrameOverlay(size: Size, compassRotation: Float) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val radius = minOf(size.width, size.height) / 2 - 2f

    // Outer shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.4f),
        radius = radius + 4f,
        center = Offset(centerX + 2f, centerY + 2f)
    )

    // Outer stone ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFD4A84B),  // Gold light
                Color(0xFFB8892E),  // Gold mid
                Color(0xFF8B6914)   // Gold dark
            ),
            center = Offset(centerX - radius * 0.2f, centerY - radius * 0.2f),
            radius = radius * 1.5f
        ),
        radius = radius + 6f,
        center = Offset(centerX, centerY),
        style = Stroke(width = 8f)
    )

    // Inner border
    drawCircle(
        color = Color(0xFF3E3024),
        radius = radius + 2f,
        center = Offset(centerX, centerY),
        style = Stroke(width = 2f)
    )

    // Outer edge highlight
    drawCircle(
        color = Color(0x40FFFFFF),
        radius = radius + 8f,
        center = Offset(centerX, centerY),
        style = Stroke(width = 1f)
    )
}

/**
 * Minimap compass component
 */
@Composable
fun MinimapCompass(
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = minOf(size.width, size.height) / 2 - 2f

        // Background circle
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF4A3828),
                    Color(0xFF2B2117)
                ),
                center = Offset(centerX, centerY),
                radius = radius
            ),
            radius = radius,
            center = Offset(centerX, centerY)
        )

        // Border
        drawCircle(
            color = Color(0xFFD4A84B),
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2f)
        )

        // Rotate for compass direction
        rotate(rotation, pivot = Offset(centerX, centerY)) {
            // North pointer (red triangle)
            val northPath = Path().apply {
                moveTo(centerX, centerY - radius + 4f)
                lineTo(centerX - 4f, centerY)
                lineTo(centerX + 4f, centerY)
                close()
            }
            drawPath(northPath, Color(0xFFCC0000))

            // South pointer (white triangle)
            val southPath = Path().apply {
                moveTo(centerX, centerY + radius - 4f)
                lineTo(centerX - 4f, centerY)
                lineTo(centerX + 4f, centerY)
                close()
            }
            drawPath(southPath, Color(0xFFCCCCCC))

            // East/West lines
            drawLine(
                color = Color(0xFF888888),
                start = Offset(centerX - radius + 4f, centerY),
                end = Offset(centerX - 2f, centerY),
                strokeWidth = 2f
            )
            drawLine(
                color = Color(0xFF888888),
                start = Offset(centerX + 2f, centerY),
                end = Offset(centerX + radius - 4f, centerY),
                strokeWidth = 2f
            )
        }

        // Center dot
        drawCircle(
            color = Color(0xFFD4A84B),
            radius = 2f,
            center = Offset(centerX, centerY)
        )
    }
}

/**
 * Orb button colors and styles
 */
object MinimapOrbColors {
    val HealthFull = Color(0xFF00FF00)
    val HealthLow = Color(0xFFFF0000)
    val HealthMid = Color(0xFFFFFF00)

    val PrayerFull = Color(0xFF00FFFF)
    val PrayerEmpty = Color(0xFF003333)

    val RunFull = Color(0xFFFFFF00)
    val RunEmpty = Color(0xFF333300)

    val SpecialFull = Color(0xFFFF8800)
    val SpecialEmpty = Color(0xFF332200)

    fun healthColor(current: Int, max: Int): Color {
        val ratio = current.toFloat() / max.toFloat()
        return when {
            ratio > 0.5f -> HealthFull
            ratio > 0.25f -> HealthMid
            else -> HealthLow
        }
    }
}

/**
 * Minimap orb types
 */
enum class MinimapOrbType {
    HEALTH,
    PRAYER,
    RUN_ENERGY,
    SPECIAL_ATTACK
}

/**
 * Enhanced orb button for minimap area
 */
@Composable
fun MinimapOrbButton(
    current: Int,
    max: Int,
    orbType: MinimapOrbType,
    onClick: () -> Unit = {},
    isActive: Boolean = false,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val fillColor = when (orbType) {
        MinimapOrbType.HEALTH -> MinimapOrbColors.healthColor(current, max)
        MinimapOrbType.PRAYER -> MinimapOrbColors.PrayerFull
        MinimapOrbType.RUN_ENERGY -> if (isActive) MinimapOrbColors.RunFull else Color(0xFFAAAA00)
        MinimapOrbType.SPECIAL_ATTACK -> MinimapOrbColors.SpecialFull
    }

    val emptyColor = when (orbType) {
        MinimapOrbType.HEALTH -> Color(0xFF330000)
        MinimapOrbType.PRAYER -> MinimapOrbColors.PrayerEmpty
        MinimapOrbType.RUN_ENERGY -> MinimapOrbColors.RunEmpty
        MinimapOrbType.SPECIAL_ATTACK -> MinimapOrbColors.SpecialEmpty
    }

    val fillRatio = (current.toFloat() / max.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF2B2117))
            .border(2.dp, Color(0xFFD4A84B), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val centerX = this.size.width / 2
            val centerY = this.size.height / 2
            val radius = minOf(centerX, centerY)

            // Empty background
            drawCircle(
                color = emptyColor,
                radius = radius,
                center = Offset(centerX, centerY)
            )

            // Filled portion (draw as arc from bottom)
            if (fillRatio > 0) {
                drawArc(
                    color = fillColor,
                    startAngle = 90f,
                    sweepAngle = -360f * fillRatio,
                    useCenter = true,
                    topLeft = Offset(centerX - radius, centerY - radius),
                    size = Size(radius * 2, radius * 2)
                )
            }

            // Inner shadow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.3f)
                    ),
                    center = Offset(centerX, centerY),
                    radius = radius
                ),
                radius = radius,
                center = Offset(centerX, centerY)
            )
        }

        // Icon/text overlay
        val iconText = when (orbType) {
            MinimapOrbType.HEALTH -> "❤"
            MinimapOrbType.PRAYER -> "✝"
            MinimapOrbType.RUN_ENERGY -> if (isActive) "🏃" else "🚶"
            MinimapOrbType.SPECIAL_ATTACK -> "⚔"
        }

        androidx.compose.material3.Text(
            text = iconText,
            fontSize = (size.value * 0.35f).sp,
            color = Color.White
        )
    }
}

private val Float.sp: androidx.compose.ui.unit.TextUnit
    get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
