package com.rustscape.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rustscape.client.game.*
import com.rustscape.client.ui.components.CanvasClickEvent
import com.rustscape.client.ui.components.GameCanvas
import com.rustscape.client.ui.components.GameCanvasState
import com.rustscape.client.ui.components.GameEntity
import com.rustscape.client.ui.theme.RustscapeColors
import com.rustscape.client.ui.theme.getRightsColor

/**
 * Main game screen composable
 * Contains the game viewport with canvas rendering, chat panel, skills panel, and minimap
 */
@Composable
fun GameScreen(
    gameState: GameState,
    onLogout: () -> Unit,
    onSendChat: (String) -> Unit,
    onSendCommand: (String) -> Unit,
    onWalkTo: (Int, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    // Canvas state for rendering
    val canvasState = remember { GameCanvasState() }

    // Handle tile clicks - send walk command
    val handleTileClick: (CanvasClickEvent) -> Unit = { event ->
        onWalkTo(event.worldX, event.worldY)
        gameState.addMessage("Walking to (${event.worldX}, ${event.worldY})", MessageType.GAME)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RustscapeColors.BackgroundDark)
    ) {
        // Main game layout
        Row(modifier = Modifier.fillMaxSize()) {
            // Left side: Game viewport + Chat
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Game canvas viewport
                GameCanvas(
                    gameState = gameState,
                    canvasState = canvasState,
                    onTileClick = handleTileClick,
                    onEntityClick = { entity ->
                        gameState.addMessage("Clicked on: ${entity.name}", MessageType.GAME)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(2.dp, RustscapeColors.Border)
                )

                // Chat panel at bottom
                ChatPanel(
                    messages = gameState.messages,
                    playerName = gameState.playerName,
                    onSendMessage = { message ->
                        if (message.startsWith("::")) {
                            onSendCommand(message.substring(2))
                        } else {
                            onSendChat(message)
                        }
                    },
                    modifier = Modifier
                        .height(180.dp)
                        .fillMaxWidth()
                )
            }

            // Right side: Minimap + Skills + Logout
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(RustscapeColors.Background)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Minimap
                MinimapPanel(
                    mapRegion = gameState.mapRegion,
                    position = gameState.position,
                    canvasState = canvasState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )

                // Player info bar
                PlayerInfoBar(
                    playerName = gameState.playerName,
                    rights = gameState.rights,
                    combatLevel = gameState.getCombatLevel(),
                    modifier = Modifier.fillMaxWidth()
                )

                // Run energy bar
                EnergyBar(
                    energy = gameState.runEnergy,
                    isRunning = gameState.isRunning,
                    modifier = Modifier.fillMaxWidth()
                )

                // Skills panel
                SkillsPanel(
                    skills = gameState.skills.toList(),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                // Zoom controls
                ZoomControls(
                    currentZoom = canvasState.zoom,
                    onZoomIn = { canvasState.zoom = (canvasState.zoom * 1.2f).coerceAtMost(3f) },
                    onZoomOut = { canvasState.zoom = (canvasState.zoom / 1.2f).coerceAtLeast(0.5f) },
                    onZoomReset = { canvasState.zoom = 1f },
                    modifier = Modifier.fillMaxWidth()
                )

                // Logout button
                LogoutButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Chat panel for displaying and sending messages
 */
@Composable
private fun ChatPanel(
    messages: List<ChatMessage>,
    playerName: String,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .background(RustscapeColors.ChatBackground)
            .border(2.dp, RustscapeColors.Border)
            .padding(8.dp)
    ) {
        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(messages) { message ->
                ChatMessageItem(message = message)
            }

            // Placeholder when empty
            if (messages.isEmpty()) {
                item {
                    Text(
                        text = "Welcome to Rustscape! Type a message or use ::command",
                        color = RustscapeColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Player name label
            Text(
                text = "$playerName:",
                color = RustscapeColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            // Text input
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Type a message...",
                        color = RustscapeColors.TextMuted,
                        fontSize = 14.sp
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RustscapeColors.Primary,
                    unfocusedBorderColor = RustscapeColors.Border,
                    cursorColor = RustscapeColors.Primary,
                    focusedTextColor = RustscapeColors.TextWhite,
                    unfocusedTextColor = RustscapeColors.TextSecondary
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    }
                )
            )

            // Send button
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText)
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(RustscapeColors.Primary, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = RustscapeColors.OnPrimary
                )
            }
        }
    }
}

/**
 * Individual chat message item
 */
@Composable
private fun ChatMessageItem(message: ChatMessage) {
    val textColor = when (message.messageType) {
        MessageType.GAME -> RustscapeColors.TextCyan
        MessageType.PUBLIC_CHAT -> RustscapeColors.TextPrimary
        MessageType.PRIVATE_MESSAGE_IN -> RustscapeColors.TextPurple
        MessageType.PRIVATE_MESSAGE_OUT -> RustscapeColors.TextPurple
        MessageType.TRADE_REQUEST -> RustscapeColors.TextGreen
        MessageType.CLAN_CHAT -> RustscapeColors.TextOrange
        else -> RustscapeColors.TextSecondary
    }

    Text(
        text = message.text,
        color = textColor,
        fontSize = 13.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Minimap panel showing player position and region
 */
@Composable
private fun MinimapPanel(
    mapRegion: MapRegion,
    position: Position,
    canvasState: GameCanvasState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(RustscapeColors.MinimapBackground)
            .border(2.dp, RustscapeColors.Border, RoundedCornerShape(8.dp))
    ) {
        // Map background with gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            RustscapeColors.MapGreen,
                            RustscapeColors.MapGreen.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        // Minimap dots/markers would go here
        // For now, show player position indicator

        // Player dot in center
        Box(
            modifier = Modifier
                .size(8.dp)
                .align(Alignment.Center)
                .background(RustscapeColors.MapYellow, CircleShape)
                .border(1.dp, Color.Black, CircleShape)
        )

        // Compass indicator
        Text(
            text = "N",
            color = RustscapeColors.TextWhite,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
        )

        // Region text overlay
        Text(
            text = "Region: ${mapRegion.regionX}, ${mapRegion.regionY}",
            color = RustscapeColors.TextWhite,
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )

        // Coordinates
        Text(
            text = "${position.x}, ${position.y}",
            color = RustscapeColors.TextWhite,
            fontSize = 8.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
        )
    }
}

/**
 * Player info bar showing name, rights, and combat level
 */
@Composable
private fun PlayerInfoBar(
    playerName: String,
    rights: PlayerRights,
    combatLevel: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(RustscapeColors.PanelBackground, RoundedCornerShape(4.dp))
            .border(1.dp, RustscapeColors.Border, RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Rights icon + name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Rights crown
            if (rights != PlayerRights.PLAYER) {
                Text(
                    text = when (rights) {
                        PlayerRights.MODERATOR -> "🥈"
                        PlayerRights.ADMINISTRATOR -> "👑"
                        PlayerRights.OWNER -> "💎"
                        else -> ""
                    },
                    fontSize = 14.sp
                )
            }

            Text(
                text = playerName,
                color = getRightsColor(rights.id),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        // Combat level
        Text(
            text = "Cmb: $combatLevel",
            color = RustscapeColors.TextSecondary,
            fontSize = 12.sp
        )
    }
}

/**
 * Run energy bar
 */
@Composable
private fun EnergyBar(
    energy: Int,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(RustscapeColors.PanelBackground, RoundedCornerShape(4.dp))
            .border(1.dp, RustscapeColors.Border, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isRunning) "Run" else "Walk",
                color = RustscapeColors.TextSecondary,
                fontSize = 12.sp
            )
            Text(
                text = "$energy%",
                color = RustscapeColors.EnergyBar,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = { energy / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = RustscapeColors.EnergyBar,
            trackColor = RustscapeColors.BackgroundDark
        )
    }
}

/**
 * Skills panel showing all player skills
 */
@Composable
private fun SkillsPanel(
    skills: List<Skill>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(RustscapeColors.PanelBackground, RoundedCornerShape(4.dp))
            .border(1.dp, RustscapeColors.Border, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        // Header
        Text(
            text = "Skills",
            color = RustscapeColors.TextGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Skills grid
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Show combat skills first
            val combatSkills = skills.filter { it.id in 0..6 }
            val otherSkills = skills.filter { it.id > 6 }

            items(combatSkills) { skill ->
                SkillRow(skill = skill)
            }

            item {
                HorizontalDivider(
                    color = RustscapeColors.Border,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(otherSkills) { skill ->
                SkillRow(skill = skill)
            }
        }
    }
}

/**
 * Individual skill row
 */
@Composable
private fun SkillRow(skill: Skill) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = skill.name,
            color = RustscapeColors.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${skill.level}",
            color = RustscapeColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Zoom controls for the game canvas
 */
@Composable
private fun ZoomControls(
    currentZoom: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(RustscapeColors.PanelBackground, RoundedCornerShape(4.dp))
            .border(1.dp, RustscapeColors.Border, RoundedCornerShape(4.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Zoom out
        IconButton(
            onClick = onZoomOut,
            modifier = Modifier.size(32.dp)
        ) {
            Text(
                text = "−",
                color = RustscapeColors.TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Zoom level display
        Text(
            text = "${(currentZoom * 100).toInt()}%",
            color = RustscapeColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Reset zoom
        TextButton(
            onClick = onZoomReset,
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(
                text = "Reset",
                color = RustscapeColors.TextMuted,
                fontSize = 10.sp
            )
        }

        // Zoom in
        IconButton(
            onClick = onZoomIn,
            modifier = Modifier.size(32.dp)
        ) {
            Text(
                text = "+",
                color = RustscapeColors.TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Logout button
 */
@Composable
private fun LogoutButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RustscapeColors.Error.copy(alpha = 0.8f),
            contentColor = RustscapeColors.TextWhite
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ExitToApp,
            contentDescription = "Logout",
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Logout",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
