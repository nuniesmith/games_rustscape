package com.rustscape.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rustscape.client.game.*
import com.rustscape.client.ui.theme.RustscapeColors
import kotlinx.coroutines.delay
import kotlin.math.*

/**
 * Game canvas constants
 */
object GameCanvasConfig {
    const val TILE_SIZE = 32f
    const val VIEWPORT_TILES_X = 15
    const val VIEWPORT_TILES_Y = 15
    const val MINIMAP_TILE_SIZE = 4f
    const val PLAYER_SIZE = 28f
    const val NPC_SIZE = 24f
    const val ANIMATION_FRAME_MS = 100L
}

/**
 * Entity types that can be rendered
 */
sealed class GameEntity {
    abstract val position: Position
    abstract val name: String

    data class Player(
        override val position: Position,
        override val name: String,
        val rights: PlayerRights = PlayerRights.PLAYER,
        val combatLevel: Int = 3,
        val isLocalPlayer: Boolean = false,
        val animationFrame: Int = 0
    ) : GameEntity()

    data class Npc(
        override val position: Position,
        override val name: String,
        val id: Int,
        val combatLevel: Int? = null,
        val animationFrame: Int = 0
    ) : GameEntity()

    data class GroundItem(
        override val position: Position,
        override val name: String,
        val itemId: Int,
        val amount: Int = 1
    ) : GameEntity()

    data class GameObject(
        override val position: Position,
        override val name: String,
        val objectId: Int,
        val type: ObjectType = ObjectType.SCENERY
    ) : GameEntity()
}

enum class ObjectType {
    WALL, WALL_DECORATION, SCENERY, FLOOR_DECORATION, BOUNDARY
}

/**
 * Tile types for rendering
 */
enum class TileType(val color: Color) {
    GRASS(Color(0xFF228B22)),
    GRASS_LIGHT(Color(0xFF32CD32)),
    GRASS_DARK(Color(0xFF006400)),
    DIRT(Color(0xFF8B4513)),
    DIRT_LIGHT(Color(0xFFA0522D)),
    SAND(Color(0xFFD2B48C)),
    STONE(Color(0xFF696969)),
    STONE_LIGHT(Color(0xFF808080)),
    WATER(Color(0xFF1E90FF)),
    WATER_DEEP(Color(0xFF0000CD)),
    ROAD(Color(0xFF8B8378)),
    WOOD(Color(0xFF8B4513)),
    LAVA(Color(0xFFFF4500))
}

/**
 * Click event data
 */
data class CanvasClickEvent(
    val screenX: Float,
    val screenY: Float,
    val worldX: Int,
    val worldY: Int,
    val isRightClick: Boolean = false,
    val clickedEntity: GameEntity? = null
)

/**
 * Entity click event with full context
 */
data class EntityClickEvent(
    val entity: GameEntity,
    val screenX: Float,
    val screenY: Float,
    val isRightClick: Boolean
)

/**
 * Game canvas state holder
 */
class GameCanvasState {
    var cameraX by mutableFloatStateOf(0f)
    var cameraY by mutableFloatStateOf(0f)
    var zoom by mutableFloatStateOf(1f)
    var animationTick by mutableIntStateOf(0)
    var entities by mutableStateOf<List<GameEntity>>(emptyList())
    var tileMap by mutableStateOf<Map<Pair<Int, Int>, TileType>>(emptyMap())
    var highlightedTile by mutableStateOf<Pair<Int, Int>?>(null)
    var targetTile by mutableStateOf<Pair<Int, Int>?>(null)

    fun centerOnPosition(position: Position) {
        cameraX = position.x.toFloat()
        cameraY = position.y.toFloat()
    }
}

/**
 * Main game canvas composable
 * Renders the game world, entities, and handles input
 */
/**
 * Main game canvas composable
 * Renders the game world, entities, and handles input including right-click context menus
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GameCanvas(
    gameState: GameState,
    canvasState: GameCanvasState = remember { GameCanvasState() },
    contextMenuState: ContextMenuState = rememberContextMenuState(),
    onTileClick: (CanvasClickEvent) -> Unit = {},
    onEntityClick: (EntityClickEvent) -> Unit = {},
    onEntityAction: (GameEntity, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val soundManager = LocalSoundManager.current

    // Animation loop
    LaunchedEffect(Unit) {
        while (true) {
            delay(GameCanvasConfig.ANIMATION_FRAME_MS)
            canvasState.animationTick++
        }
    }

    // Center camera on player position
    LaunchedEffect(gameState.position) {
        canvasState.centerOnPosition(gameState.position)
    }

    // Generate demo tile map if empty
    LaunchedEffect(gameState.mapRegion) {
        if (canvasState.tileMap.isEmpty()) {
            canvasState.tileMap = generateDemoTileMap(gameState.mapRegion)
        }
    }

    // Generate local player entity
    val localPlayer = remember(gameState.playerName, gameState.position, gameState.rights, canvasState.animationTick) {
        GameEntity.Player(
            position = gameState.position,
            name = gameState.playerName,
            rights = gameState.rights,
            combatLevel = gameState.getCombatLevel(),
            isLocalPlayer = true,
            animationFrame = canvasState.animationTick % 4
        )
    }

    // Combine all entities
    val allEntities = remember(localPlayer, canvasState.entities) {
        listOf(localPlayer) + canvasState.entities
    }

    // Entity hit detection helper
    fun findEntityAtScreen(
        screenPos: Offset,
        entities: List<GameEntity>,
        cameraX: Float,
        cameraY: Float,
        zoom: Float,
        canvasWidth: Float,
        canvasHeight: Float
    ): GameEntity? {
        val tileSize = GameCanvasConfig.TILE_SIZE * zoom

        // Check entities in reverse order (top-most first due to painter's algorithm)
        val sortedEntities = entities.sortedByDescending { it.position.y }

        for (entity in sortedEntities) {
            val (entityScreenX, entityScreenY) = worldToScreen(
                entity.position.x,
                entity.position.y,
                cameraX,
                cameraY,
                zoom,
                canvasWidth,
                canvasHeight
            )

            // Calculate entity bounds based on type
            val (entityWidth, entityHeight) = when (entity) {
                is GameEntity.Player -> GameCanvasConfig.PLAYER_SIZE * zoom to GameCanvasConfig.PLAYER_SIZE * zoom
                is GameEntity.Npc -> GameCanvasConfig.NPC_SIZE * zoom to GameCanvasConfig.NPC_SIZE * zoom
                is GameEntity.GroundItem -> tileSize * 0.5f to tileSize * 0.5f
                is GameEntity.GameObject -> tileSize to tileSize
            }

            // Entity center is at entityScreenX, entityScreenY (center of tile)
            val halfWidth = entityWidth / 2
            val halfHeight = entityHeight / 2

            // Check if click is within entity bounds
            if (screenPos.x >= entityScreenX - halfWidth &&
                screenPos.x <= entityScreenX + halfWidth &&
                screenPos.y >= entityScreenY - halfHeight - (entityHeight * 0.3f) && // Offset up slightly for visual center
                screenPos.y <= entityScreenY + halfHeight
            ) {
                return entity
            }
        }
        return null
    }

    // Build context menu options for an entity
    fun buildContextMenuOptions(entity: GameEntity): List<ContextMenuOption> {
        return when (entity) {
            is GameEntity.Player -> {
                if (entity.isLocalPlayer) {
                    // Local player options
                    listOf(
                        ContextMenuOption(
                            text = "Walk here",
                            color = RSColors.TextYellow,
                            onClick = { onEntityAction(entity, "walk") }
                        )
                    )
                } else {
                    RSContextMenuOptions.forPlayer(
                        playerName = entity.name,
                        onFollow = {
                            soundManager?.play(RSSound.BUTTON_CLICK)
                            onEntityAction(entity, "follow")
                        },
                        onTrade = {
                            soundManager?.play(RSSound.TRADE_REQUEST)
                            onEntityAction(entity, "trade")
                        },
                        onChallenge = {
                            soundManager?.play(RSSound.BUTTON_CLICK)
                            onEntityAction(entity, "challenge")
                        },
                        onReport = {
                            soundManager?.play(RSSound.BUTTON_CLICK)
                            onEntityAction(entity, "report")
                        }
                    )
                }
            }

            is GameEntity.Npc -> {
                RSContextMenuOptions.forNpc(
                    npcName = entity.name,
                    onTalk = if (entity.combatLevel == null) {
                        {
                            soundManager?.play(RSSound.BUTTON_CLICK)
                            onEntityAction(entity, "talk")
                        }
                    } else null,
                    onAttack = if (entity.combatLevel != null) {
                        {
                            soundManager?.play(RSSound.HIT_NORMAL)
                            onEntityAction(entity, "attack")
                        }
                    } else null,
                    onPickpocket = if (entity.name.contains("Man", ignoreCase = true) ||
                        entity.name.contains("Woman", ignoreCase = true) ||
                        entity.name.contains("Guard", ignoreCase = true)
                    ) {
                        {
                            soundManager?.play(RSSound.BUTTON_CLICK)
                            onEntityAction(entity, "pickpocket")
                        }
                    } else null,
                    onExamine = {
                        soundManager?.play(RSSound.BUTTON_CLICK)
                        onEntityAction(entity, "examine")
                    }
                )
            }

            is GameEntity.GroundItem -> {
                RSContextMenuOptions.forGroundItem(
                    itemName = "${entity.name}${if (entity.amount > 1) " (${entity.amount})" else ""}",
                    onTake = {
                        soundManager?.play(RSSound.ITEM_PICKUP)
                        onEntityAction(entity, "take")
                    },
                    onExamine = {
                        soundManager?.play(RSSound.BUTTON_CLICK)
                        onEntityAction(entity, "examine")
                    }
                )
            }

            is GameEntity.GameObject -> {
                val primaryAction = getObjectPrimaryAction(entity)
                RSContextMenuOptions.forGameObject(
                    objectName = entity.name,
                    primaryAction = primaryAction,
                    onPrimaryAction = {
                        soundManager?.play(if (primaryAction == "Open" || primaryAction == "Close") RSSound.DOOR_OPEN else RSSound.BUTTON_CLICK)
                        onEntityAction(entity, primaryAction.lowercase())
                    },
                    onExamine = {
                        soundManager?.play(RSSound.BUTTON_CLICK)
                        onEntityAction(entity, "examine")
                    }
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Left-click handler
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val worldPos = screenToWorld(
                            offset,
                            canvasState.cameraX,
                            canvasState.cameraY,
                            canvasState.zoom,
                            size.width.toFloat(),
                            size.height.toFloat()
                        )

                        // Check if we clicked on an entity
                        val clickedEntity = findEntityAtScreen(
                            offset,
                            allEntities,
                            canvasState.cameraX,
                            canvasState.cameraY,
                            canvasState.zoom,
                            size.width.toFloat(),
                            size.height.toFloat()
                        )

                        if (clickedEntity != null && clickedEntity !is GameEntity.Player) {
                            // Left click on entity - perform primary action
                            soundManager?.play(RSSound.BUTTON_CLICK)
                            onEntityClick(
                                EntityClickEvent(
                                    entity = clickedEntity,
                                    screenX = offset.x,
                                    screenY = offset.y,
                                    isRightClick = false
                                )
                            )
                        } else {
                            // Left click on ground - walk
                            canvasState.targetTile = worldPos.first to worldPos.second
                            onTileClick(
                                CanvasClickEvent(
                                    screenX = offset.x,
                                    screenY = offset.y,
                                    worldX = worldPos.first,
                                    worldY = worldPos.second,
                                    clickedEntity = clickedEntity
                                )
                            )
                        }
                    }
                }
                // Right-click handler for context menu
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press &&
                                event.button == PointerButton.Secondary
                            ) {
                                val change = event.changes.firstOrNull()
                                if (change != null) {
                                    val offset = change.position
                                    val worldPos = screenToWorld(
                                        offset,
                                        canvasState.cameraX,
                                        canvasState.cameraY,
                                        canvasState.zoom,
                                        size.width.toFloat(),
                                        size.height.toFloat()
                                    )

                                    // Find entity at click position
                                    val clickedEntity = findEntityAtScreen(
                                        offset,
                                        allEntities,
                                        canvasState.cameraX,
                                        canvasState.cameraY,
                                        canvasState.zoom,
                                        size.width.toFloat(),
                                        size.height.toFloat()
                                    )

                                    // Build context menu options
                                    val options = mutableListOf<ContextMenuOption>()

                                    // Always add "Walk here" first
                                    options.add(
                                        RSContextMenuOptions.walkHere {
                                            canvasState.targetTile = worldPos.first to worldPos.second
                                            onTileClick(
                                                CanvasClickEvent(
                                                    screenX = offset.x,
                                                    screenY = offset.y,
                                                    worldX = worldPos.first,
                                                    worldY = worldPos.second,
                                                    isRightClick = true
                                                )
                                            )
                                        }
                                    )

                                    // Add entity-specific options if an entity was clicked
                                    if (clickedEntity != null) {
                                        options.addAll(buildContextMenuOptions(clickedEntity))
                                    }

                                    // Show context menu
                                    soundManager?.play(RSSound.BUTTON_CLICK)
                                    contextMenuState.show(
                                        x = offset.x,
                                        y = offset.y,
                                        menuOptions = options,
                                        menuTitle = clickedEntity?.name
                                    )

                                    change.consume()
                                }
                            }
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Calculate visible tile range
            val tileSize = GameCanvasConfig.TILE_SIZE * canvasState.zoom
            val tilesVisibleX = (canvasWidth / tileSize).toInt() + 2
            val tilesVisibleY = (canvasHeight / tileSize).toInt() + 2

            val startTileX = (canvasState.cameraX - tilesVisibleX / 2).toInt()
            val startTileY = (canvasState.cameraY - tilesVisibleY / 2).toInt()
            val endTileX = startTileX + tilesVisibleX
            val endTileY = startTileY + tilesVisibleY

            // Draw ground tiles
            drawTiles(
                tileMap = canvasState.tileMap,
                startX = startTileX,
                startY = startTileY,
                endX = endTileX,
                endY = endTileY,
                cameraX = canvasState.cameraX,
                cameraY = canvasState.cameraY,
                zoom = canvasState.zoom,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                animationTick = canvasState.animationTick
            )

            // Draw tile grid
            drawTileGrid(
                startX = startTileX,
                startY = startTileY,
                endX = endTileX,
                endY = endTileY,
                cameraX = canvasState.cameraX,
                cameraY = canvasState.cameraY,
                zoom = canvasState.zoom,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight
            )

            // Draw highlighted/target tile
            canvasState.targetTile?.let { (tx, ty) ->
                drawHighlightedTile(
                    tileX = tx,
                    tileY = ty,
                    cameraX = canvasState.cameraX,
                    cameraY = canvasState.cameraY,
                    zoom = canvasState.zoom,
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight,
                    color = Color.Yellow.copy(alpha = 0.3f)
                )
            }

            // Sort entities by Y position for proper layering (painter's algorithm)
            val sortedEntities = allEntities.sortedBy { it.position.y }

            // Draw entities
            sortedEntities.forEach { entity ->
                drawEntity(
                    entity = entity,
                    cameraX = canvasState.cameraX,
                    cameraY = canvasState.cameraY,
                    zoom = canvasState.zoom,
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight,
                    animationTick = canvasState.animationTick,
                    textMeasurer = textMeasurer
                )
            }

            // Draw coordinate debug info
            drawCoordinateInfo(
                playerPosition = gameState.position,
                mapRegion = gameState.mapRegion,
                textMeasurer = textMeasurer
            )
        }

        // Render context menu overlay
        RSContextMenu(state = contextMenuState)
    }
}

/**
 * Get primary action for a game object based on its name/type
 */
private fun getObjectPrimaryAction(obj: GameEntity.GameObject): String {
    val nameLower = obj.name.lowercase()
    return when {
        nameLower.contains("door") || nameLower.contains("gate") -> "Open"
        nameLower.contains("ladder") -> "Climb"
        nameLower.contains("stairs") -> "Climb"
        nameLower.contains("bank") || nameLower.contains("booth") -> "Use"
        nameLower.contains("furnace") || nameLower.contains("forge") -> "Smelt"
        nameLower.contains("anvil") -> "Smith"
        nameLower.contains("range") || nameLower.contains("stove") -> "Cook"
        nameLower.contains("tree") -> "Chop down"
        nameLower.contains("rock") || nameLower.contains("ore") -> "Mine"
        nameLower.contains("fishing") || nameLower.contains("spot") -> "Fish"
        nameLower.contains("altar") -> "Pray"
        nameLower.contains("chest") -> "Open"
        nameLower.contains("crate") || nameLower.contains("barrel") -> "Search"
        nameLower.contains("sign") || nameLower.contains("board") -> "Read"
        nameLower.contains("well") -> "Use"
        nameLower.contains("fire") || nameLower.contains("campfire") -> "Use"
        else -> "Use"
    }
}

/**
 * Draw ground tiles
 */
private fun DrawScope.drawTiles(
    tileMap: Map<Pair<Int, Int>, TileType>,
    startX: Int,
    startY: Int,
    endX: Int,
    endY: Int,
    cameraX: Float,
    cameraY: Float,
    zoom: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    animationTick: Int
) {
    val tileSize = GameCanvasConfig.TILE_SIZE * zoom

    for (y in startY..endY) {
        for (x in startX..endX) {
            val screenPos = worldToScreen(x, y, cameraX, cameraY, zoom, canvasWidth, canvasHeight)

            // Get tile type or generate procedurally
            val tileType = tileMap[x to y] ?: getProceduralTile(x, y)

            // Animate water tiles
            val color = if (tileType == TileType.WATER || tileType == TileType.WATER_DEEP) {
                val wave = sin((x + y + animationTick * 0.1f).toDouble()).toFloat() * 0.1f
                tileType.color.copy(
                    red = (tileType.color.red + wave).coerceIn(0f, 1f),
                    blue = (tileType.color.blue - wave * 0.5f).coerceIn(0f, 1f)
                )
            } else {
                tileType.color
            }

            drawRect(
                color = color,
                topLeft = Offset(screenPos.first, screenPos.second),
                size = Size(tileSize + 1, tileSize + 1)
            )

            // Add texture variation
            if (tileType == TileType.GRASS || tileType == TileType.GRASS_LIGHT) {
                drawGrassTexture(screenPos.first, screenPos.second, tileSize, x, y)
            }
        }
    }
}

/**
 * Draw grass texture details
 */
private fun DrawScope.drawGrassTexture(x: Float, y: Float, tileSize: Float, tileX: Int, tileY: Int) {
    val random = (tileX * 31 + tileY * 17) % 100
    if (random < 30) {
        // Draw small grass tufts
        val grassColor = Color(0xFF006400).copy(alpha = 0.5f)
        val cx = x + tileSize * 0.5f
        val cy = y + tileSize * 0.5f
        drawLine(
            color = grassColor,
            start = Offset(cx - 2, cy + 4),
            end = Offset(cx, cy - 4),
            strokeWidth = 1f
        )
        drawLine(
            color = grassColor,
            start = Offset(cx + 2, cy + 4),
            end = Offset(cx + 4, cy - 2),
            strokeWidth = 1f
        )
    }
}

/**
 * Draw tile grid overlay
 */
private fun DrawScope.drawTileGrid(
    startX: Int,
    startY: Int,
    endX: Int,
    endY: Int,
    cameraX: Float,
    cameraY: Float,
    zoom: Float,
    canvasWidth: Float,
    canvasHeight: Float
) {
    val tileSize = GameCanvasConfig.TILE_SIZE * zoom
    val gridColor = Color.Black.copy(alpha = 0.1f)

    // Vertical lines
    for (x in startX..endX) {
        val screenX = worldToScreen(x, startY, cameraX, cameraY, zoom, canvasWidth, canvasHeight).first
        drawLine(
            color = gridColor,
            start = Offset(screenX, 0f),
            end = Offset(screenX, canvasHeight),
            strokeWidth = 1f
        )
    }

    // Horizontal lines
    for (y in startY..endY) {
        val screenY = worldToScreen(startX, y, cameraX, cameraY, zoom, canvasWidth, canvasHeight).second
        drawLine(
            color = gridColor,
            start = Offset(0f, screenY),
            end = Offset(canvasWidth, screenY),
            strokeWidth = 1f
        )
    }
}

/**
 * Draw a highlighted tile
 */
private fun DrawScope.drawHighlightedTile(
    tileX: Int,
    tileY: Int,
    cameraX: Float,
    cameraY: Float,
    zoom: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    color: Color
) {
    val tileSize = GameCanvasConfig.TILE_SIZE * zoom
    val screenPos = worldToScreen(tileX, tileY, cameraX, cameraY, zoom, canvasWidth, canvasHeight)

    // Fill
    drawRect(
        color = color,
        topLeft = Offset(screenPos.first, screenPos.second),
        size = Size(tileSize, tileSize)
    )

    // Border
    drawRect(
        color = color.copy(alpha = 0.8f),
        topLeft = Offset(screenPos.first, screenPos.second),
        size = Size(tileSize, tileSize),
        style = Stroke(width = 2f)
    )
}

/**
 * Draw an entity on the canvas
 */
private fun DrawScope.drawEntity(
    entity: GameEntity,
    cameraX: Float,
    cameraY: Float,
    zoom: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    animationTick: Int,
    textMeasurer: TextMeasurer
) {
    val tileSize = GameCanvasConfig.TILE_SIZE * zoom
    val screenPos = worldToScreen(
        entity.position.x,
        entity.position.y,
        cameraX,
        cameraY,
        zoom,
        canvasWidth,
        canvasHeight
    )

    // Center position in tile
    val centerX = screenPos.first + tileSize / 2
    val centerY = screenPos.second + tileSize / 2

    when (entity) {
        is GameEntity.Player -> drawPlayer(entity, centerX, centerY, zoom, animationTick, textMeasurer)
        is GameEntity.Npc -> drawNpc(entity, centerX, centerY, zoom, animationTick, textMeasurer)
        is GameEntity.GroundItem -> drawGroundItem(entity, centerX, centerY, zoom, textMeasurer)
        is GameEntity.GameObject -> drawGameObject(entity, centerX, centerY, zoom)
    }
}

/**
 * Draw a player entity
 */
private fun DrawScope.drawPlayer(
    player: GameEntity.Player,
    centerX: Float,
    centerY: Float,
    zoom: Float,
    animationTick: Int,
    textMeasurer: TextMeasurer
) {
    val size = GameCanvasConfig.PLAYER_SIZE * zoom
    val halfSize = size / 2

    // Animation bob
    val bobOffset = if (player.isLocalPlayer) {
        sin(animationTick * 0.2).toFloat() * 2f * zoom
    } else 0f

    // Shadow
    drawOval(
        color = Color.Black.copy(alpha = 0.3f),
        topLeft = Offset(centerX - halfSize * 0.8f, centerY + halfSize * 0.6f),
        size = Size(size * 0.8f, size * 0.3f)
    )

    // Body color based on local player or other
    val bodyColor = if (player.isLocalPlayer) {
        Color(0xFF4169E1) // Royal blue for local player
    } else {
        Color(0xFF32CD32) // Green for other players
    }

    // Body
    drawOval(
        color = bodyColor,
        topLeft = Offset(centerX - halfSize, centerY - halfSize + bobOffset),
        size = Size(size, size)
    )

    // Body highlight
    drawOval(
        color = bodyColor.copy(red = minOf(bodyColor.red + 0.2f, 1f)),
        topLeft = Offset(centerX - halfSize * 0.6f, centerY - halfSize * 0.8f + bobOffset),
        size = Size(size * 0.4f, size * 0.3f)
    )

    // Border
    drawOval(
        color = Color.Black,
        topLeft = Offset(centerX - halfSize, centerY - halfSize + bobOffset),
        size = Size(size, size),
        style = Stroke(width = 2f * zoom)
    )

    // Rights crown
    if (player.rights != PlayerRights.PLAYER) {
        val crownColor = when (player.rights) {
            PlayerRights.MODERATOR -> Color(0xFFC0C0C0)
            PlayerRights.ADMINISTRATOR -> Color(0xFFFFD700)
            PlayerRights.OWNER -> Color(0xFF00FFFF)
            else -> Color.Transparent
        }
        drawCrown(centerX, centerY - halfSize - 8 * zoom + bobOffset, 10f * zoom, crownColor)
    }

    // Name tag
    drawNameTag(
        name = player.name,
        centerX = centerX,
        topY = centerY - halfSize - 20 * zoom + bobOffset,
        textMeasurer = textMeasurer,
        color = if (player.isLocalPlayer) RustscapeColors.TextCyan else RustscapeColors.TextPrimary,
        zoom = zoom
    )

    // Combat level
    drawCombatLevel(
        level = player.combatLevel,
        centerX = centerX,
        topY = centerY + halfSize + 4 * zoom,
        textMeasurer = textMeasurer,
        zoom = zoom
    )
}

/**
 * Draw an NPC entity
 */
private fun DrawScope.drawNpc(
    npc: GameEntity.Npc,
    centerX: Float,
    centerY: Float,
    zoom: Float,
    animationTick: Int,
    textMeasurer: TextMeasurer
) {
    val size = GameCanvasConfig.NPC_SIZE * zoom
    val halfSize = size / 2

    // Animation idle movement
    val idleOffset = sin(animationTick * 0.15 + npc.id).toFloat() * 1.5f * zoom

    // Shadow
    drawOval(
        color = Color.Black.copy(alpha = 0.3f),
        topLeft = Offset(centerX - halfSize * 0.7f, centerY + halfSize * 0.5f),
        size = Size(size * 0.7f, size * 0.25f)
    )

    // Body (yellow/orange for NPCs)
    val npcColor = Color(0xFFFF8C00)
    drawOval(
        color = npcColor,
        topLeft = Offset(centerX - halfSize, centerY - halfSize + idleOffset),
        size = Size(size, size)
    )

    // Highlight
    drawOval(
        color = npcColor.copy(red = 1f, green = 0.7f),
        topLeft = Offset(centerX - halfSize * 0.5f, centerY - halfSize * 0.7f + idleOffset),
        size = Size(size * 0.35f, size * 0.25f)
    )

    // Border
    drawOval(
        color = Color.Black,
        topLeft = Offset(centerX - halfSize, centerY - halfSize + idleOffset),
        size = Size(size, size),
        style = Stroke(width = 2f * zoom)
    )

    // Name tag
    drawNameTag(
        name = npc.name,
        centerX = centerX,
        topY = centerY - halfSize - 16 * zoom + idleOffset,
        textMeasurer = textMeasurer,
        color = Color(0xFFFFFF00),
        zoom = zoom
    )

    // Combat level if attackable
    npc.combatLevel?.let { level ->
        drawCombatLevel(
            level = level,
            centerX = centerX,
            topY = centerY + halfSize + 4 * zoom,
            textMeasurer = textMeasurer,
            zoom = zoom
        )
    }
}

/**
 * Draw a ground item
 */
private fun DrawScope.drawGroundItem(
    item: GameEntity.GroundItem,
    centerX: Float,
    centerY: Float,
    zoom: Float,
    textMeasurer: TextMeasurer
) {
    val size = 12f * zoom

    // Item dot/icon
    drawCircle(
        color = Color(0xFFFF00FF),
        center = Offset(centerX, centerY),
        radius = size / 2
    )

    // Glow effect
    drawCircle(
        color = Color(0xFFFF00FF).copy(alpha = 0.3f),
        center = Offset(centerX, centerY),
        radius = size
    )

    // Name
    drawNameTag(
        name = if (item.amount > 1) "${item.name} (${item.amount})" else item.name,
        centerX = centerX,
        topY = centerY - size - 8 * zoom,
        textMeasurer = textMeasurer,
        color = Color(0xFFFF00FF),
        zoom = zoom * 0.8f
    )
}

/**
 * Draw a game object (scenery, walls, etc.)
 */
private fun DrawScope.drawGameObject(
    obj: GameEntity.GameObject,
    centerX: Float,
    centerY: Float,
    zoom: Float
) {
    val size = GameCanvasConfig.TILE_SIZE * zoom * 0.8f

    when (obj.type) {
        ObjectType.SCENERY -> {
            // Tree/rock-like object
            drawRect(
                color = Color(0xFF8B4513),
                topLeft = Offset(centerX - size / 4, centerY - size / 2),
                size = Size(size / 2, size)
            )
            drawCircle(
                color = Color(0xFF228B22),
                center = Offset(centerX, centerY - size / 2),
                radius = size / 2
            )
        }

        ObjectType.WALL, ObjectType.BOUNDARY -> {
            drawRect(
                color = Color(0xFF808080),
                topLeft = Offset(centerX - size / 2, centerY - size / 4),
                size = Size(size, size / 2)
            )
        }

        else -> {
            drawRect(
                color = Color(0xFF696969),
                topLeft = Offset(centerX - size / 3, centerY - size / 3),
                size = Size(size * 0.66f, size * 0.66f)
            )
        }
    }
}

/**
 * Draw a crown icon for moderators/admins
 */
private fun DrawScope.drawCrown(centerX: Float, centerY: Float, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(centerX - size, centerY + size * 0.5f)
        lineTo(centerX - size * 0.6f, centerY - size * 0.3f)
        lineTo(centerX - size * 0.3f, centerY + size * 0.2f)
        lineTo(centerX, centerY - size * 0.5f)
        lineTo(centerX + size * 0.3f, centerY + size * 0.2f)
        lineTo(centerX + size * 0.6f, centerY - size * 0.3f)
        lineTo(centerX + size, centerY + size * 0.5f)
        close()
    }
    drawPath(path, color)
    drawPath(path, Color.Black, style = Stroke(width = 1f))
}

/**
 * Draw a name tag above an entity
 */
private fun DrawScope.drawNameTag(
    name: String,
    centerX: Float,
    topY: Float,
    textMeasurer: TextMeasurer,
    color: Color,
    zoom: Float
) {
    if (name.isBlank()) return

    val textStyle = TextStyle(
        fontSize = (11 * zoom).sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Default,
        color = color
    )

    val textLayoutResult = textMeasurer.measure(
        text = name,
        style = textStyle
    )

    val textWidth = textLayoutResult.size.width.toFloat()
    val textHeight = textLayoutResult.size.height.toFloat()

    // Background
    drawRect(
        color = Color.Black.copy(alpha = 0.6f),
        topLeft = Offset(centerX - textWidth / 2 - 4, topY - 2),
        size = Size(textWidth + 8, textHeight + 4)
    )

    // Text
    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = Offset(centerX - textWidth / 2, topY)
    )
}

/**
 * Draw combat level indicator
 */
private fun DrawScope.drawCombatLevel(
    level: Int,
    centerX: Float,
    topY: Float,
    textMeasurer: TextMeasurer,
    zoom: Float
) {
    val text = "Lvl-$level"
    val textStyle = TextStyle(
        fontSize = (9 * zoom).sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Default,
        color = Color(0xFF00FF00)
    )

    val textLayoutResult = textMeasurer.measure(
        text = text,
        style = textStyle
    )

    val textWidth = textLayoutResult.size.width.toFloat()

    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = Offset(centerX - textWidth / 2, topY)
    )
}

/**
 * Draw coordinate/debug info
 */
private fun DrawScope.drawCoordinateInfo(
    playerPosition: Position,
    mapRegion: MapRegion,
    textMeasurer: TextMeasurer
) {
    val debugText =
        "Pos: ${playerPosition.x}, ${playerPosition.y}, ${playerPosition.z} | Region: ${mapRegion.regionX}, ${mapRegion.regionY}"
    val textStyle = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Default,
        color = Color.White
    )

    val textLayoutResult = textMeasurer.measure(
        text = debugText,
        style = textStyle
    )

    // Background
    drawRect(
        color = Color.Black.copy(alpha = 0.7f),
        topLeft = Offset(8f, 8f),
        size = Size(textLayoutResult.size.width + 16f, textLayoutResult.size.height + 8f)
    )

    // Text
    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = Offset(16f, 12f)
    )
}

/**
 * Convert world coordinates to screen coordinates
 */
private fun worldToScreen(
    worldX: Int,
    worldY: Int,
    cameraX: Float,
    cameraY: Float,
    zoom: Float,
    canvasWidth: Float,
    canvasHeight: Float
): Pair<Float, Float> {
    val tileSize = GameCanvasConfig.TILE_SIZE * zoom
    val centerOffsetX = canvasWidth / 2
    val centerOffsetY = canvasHeight / 2

    val screenX = centerOffsetX + (worldX - cameraX) * tileSize
    val screenY = centerOffsetY + (cameraY - worldY) * tileSize // Y is inverted

    return screenX to screenY
}

/**
 * Convert world coordinates to screen coordinates (internal helper for entity detection)
 */
internal fun worldToScreenInternal(
    worldX: Int,
    worldY: Int,
    cameraX: Float,
    cameraY: Float,
    zoom: Float,
    canvasWidth: Float,
    canvasHeight: Float
): Pair<Float, Float> = worldToScreen(worldX, worldY, cameraX, cameraY, zoom, canvasWidth, canvasHeight)

/**
 * Convert screen coordinates to world coordinates
 */
private fun screenToWorld(
    screenPos: Offset,
    cameraX: Float,
    cameraY: Float,
    zoom: Float,
    canvasWidth: Float,
    canvasHeight: Float
): Pair<Int, Int> {
    val tileSize = GameCanvasConfig.TILE_SIZE * zoom
    val centerOffsetX = canvasWidth / 2
    val centerOffsetY = canvasHeight / 2

    val worldX = ((screenPos.x - centerOffsetX) / tileSize + cameraX).toInt()
    val worldY = (cameraY - (screenPos.y - centerOffsetY) / tileSize).toInt()

    return worldX to worldY
}

/**
 * Get procedural tile type based on coordinates (for demo/fallback)
 */
private fun getProceduralTile(x: Int, y: Int): TileType {
    val noise = (sin(x * 0.1) + cos(y * 0.1) + sin((x + y) * 0.05)).toFloat()

    return when {
        noise < -1.5f -> TileType.WATER
        noise < -1.0f -> TileType.SAND
        noise < 0.5f -> TileType.GRASS
        noise < 1.0f -> TileType.GRASS_LIGHT
        noise < 1.5f -> TileType.DIRT
        else -> TileType.STONE
    }
}

/**
 * Generate a demo tile map for testing
 */
private fun generateDemoTileMap(region: MapRegion): Map<Pair<Int, Int>, TileType> {
    val map = mutableMapOf<Pair<Int, Int>, TileType>()

    val baseX = region.baseX
    val baseY = region.baseY

    // Generate a varied landscape
    for (dy in -20..20) {
        for (dx in -20..20) {
            val x = baseX + dx
            val y = baseY + dy

            // Create some patterns
            val tile = when {
                // River running through
                dx in -1..1 && dy > 5 -> TileType.WATER
                dx in -2..2 && dy > 5 -> TileType.SAND

                // Road
                dy in -1..1 -> TileType.ROAD

                // Stone area
                dx > 10 && dy < -5 -> TileType.STONE

                // Default grass with variation
                else -> {
                    val hash = (x * 31 + y * 17) % 10
                    when (hash) {
                        0, 1 -> TileType.GRASS_LIGHT
                        2 -> TileType.GRASS_DARK
                        else -> TileType.GRASS
                    }
                }
            }

            map[x to y] = tile
        }
    }

    return map
}
