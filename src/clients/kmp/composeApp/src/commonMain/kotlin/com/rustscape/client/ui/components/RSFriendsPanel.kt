package com.rustscape.client.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Friend status enum
 */
enum class FriendStatus {
    ONLINE,
    OFFLINE,
    IN_GAME,      // Online and in-game
    IN_LOBBY,     // Online but in lobby/menu
    AWAY,         // Online but AFK
    BUSY          // Online but set to busy/DND
}

/**
 * Friend data class
 */
data class FriendInfo(
    val name: String,
    val status: FriendStatus = FriendStatus.OFFLINE,
    val world: Int? = null,          // World number if online
    val location: String? = null,    // Location/activity if available
    val rank: FriendRank = FriendRank.FRIEND,
    val lastOnline: String? = null   // Last online timestamp for offline friends
)

/**
 * Friend rank (for clan/friend priority)
 */
enum class FriendRank {
    FRIEND,
    RECRUIT,
    CORPORAL,
    SERGEANT,
    LIEUTENANT,
    CAPTAIN,
    GENERAL,
    OWNER
}

/**
 * Classic RS-style Friends List Panel
 */
@Composable
fun RSFriendsPanel(
    friends: List<FriendInfo>,
    onFriendClick: (FriendInfo) -> Unit = {},
    onAddFriend: (String) -> Unit = {},
    onRemoveFriend: (FriendInfo) -> Unit = {},
    onMessageFriend: (FriendInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val soundManager = LocalSoundManager.current
    var showAddDialog by remember { mutableStateOf(false) }
    var addFriendName by remember { mutableStateOf("") }

    // Sort friends: online first, then alphabetically
    val sortedFriends = remember(friends) {
        friends.sortedWith(
            compareBy<FriendInfo> { it.status == FriendStatus.OFFLINE }
                .thenBy { it.name.lowercase() }
        )
    }

    val onlineCount = friends.count { it.status != FriendStatus.OFFLINE }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
        // Header
        RSPanelHeaderRow(
            title = "Friends List",
            subtitle = "$onlineCount / ${friends.size} Online"
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Friends list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(RSColors.PanelBackgroundDark.copy(alpha = 0.5f))
                .padding(2.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(sortedFriends, key = { it.name }) { friend ->
                FriendListEntry(
                    friend = friend,
                    onClick = {
                        soundManager?.play(RSSound.BUTTON_CLICK)
                        onFriendClick(friend)
                    },
                    onMessage = {
                        soundManager?.play(RSSound.PRIVATE_MESSAGE)
                        onMessageFriend(friend)
                    }
                )
            }

            // Empty state
            if (friends.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No friends added yet",
                            color = RSColors.TextWhite.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Add friend section
        if (showAddDialog) {
            AddFriendDialog(
                name = addFriendName,
                onNameChange = { addFriendName = it },
                onAdd = {
                    if (addFriendName.isNotBlank()) {
                        soundManager?.play(RSSound.BUTTON_CLICK)
                        onAddFriend(addFriendName.trim())
                        addFriendName = ""
                        showAddDialog = false
                    } else {
                        soundManager?.play(RSSound.ERROR)
                    }
                },
                onCancel = {
                    soundManager?.play(RSSound.BUTTON_CLICK)
                    showAddDialog = false
                    addFriendName = ""
                }
            )
        } else {
            RSStoneButton(
                text = "Add Friend",
                onClick = {
                    soundManager?.play(RSSound.BUTTON_CLICK)
                    showAddDialog = true
                },
                width = 200.dp,
                height = 24.dp
            )
        }
    }
}

/**
 * Panel header row with title and optional subtitle
 */
@Composable
private fun RSPanelHeaderRow(
    title: String,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = RSColors.TextOrange,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = RSColors.TextWhite.copy(alpha = 0.7f),
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Individual friend list entry with status orb
 */
@Composable
private fun FriendListEntry(
    friend: FriendInfo,
    onClick: () -> Unit,
    onMessage: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered) RSColors.ChatTabActive else Color.Transparent,
        animationSpec = tween(100),
        label = "hoverBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(2.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Status orb
        StatusOrb(status = friend.status)

        // Name and world/location
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = friend.name,
                color = getStatusTextColor(friend.status),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // World or last online info
            val subText = when {
                friend.status != FriendStatus.OFFLINE && friend.world != null -> "World ${friend.world}"
                friend.status != FriendStatus.OFFLINE && friend.location != null -> friend.location
                friend.status == FriendStatus.OFFLINE && friend.lastOnline != null -> "Last: ${friend.lastOnline}"
                else -> null
            }

            if (subText != null) {
                Text(
                    text = subText,
                    color = RSColors.TextWhite.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Message button (only for online friends)
        if (friend.status != FriendStatus.OFFLINE && isHovered) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RSColors.StoneMid)
                    .clickable { onMessage() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "💬",
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Animated status orb indicator
 */
@Composable
private fun StatusOrb(
    status: FriendStatus,
    size: Int = 10
) {
    val orbColor = getStatusOrbColor(status)

    // Pulsing animation for online statuses
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val shouldPulse = status == FriendStatus.ONLINE || status == FriendStatus.IN_GAME

    Box(
        modifier = Modifier
            .size(size.dp)
            .drawBehind {
                // Glow effect for online
                if (shouldPulse) {
                    drawCircle(
                        color = orbColor.copy(alpha = pulseAlpha * 0.5f),
                        radius = this.size.minDimension / 2 + 2.dp.toPx()
                    )
                }

                // Main orb
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            orbColor.copy(alpha = 1f),
                            orbColor.copy(alpha = 0.7f)
                        )
                    ),
                    radius = this.size.minDimension / 2
                )

                // Highlight
                drawCircle(
                    color = Color.White.copy(alpha = 0.4f),
                    radius = this.size.minDimension / 4,
                    center = Offset(
                        this.size.width / 2 - 1.dp.toPx(),
                        this.size.height / 2 - 1.dp.toPx()
                    )
                )

                // Border
                drawCircle(
                    color = orbColor.copy(alpha = 0.8f),
                    radius = this.size.minDimension / 2,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
    )
}

/**
 * Add friend dialog/input
 */
@Composable
private fun AddFriendDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onAdd: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(RSColors.PanelBackgroundDark)
            .border(1.dp, RSColors.StoneBorder, RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Enter player name:",
            color = RSColors.TextWhite,
            fontSize = 10.sp
        )

        // Input field
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            textStyle = TextStyle(
                color = RSColors.TextYellow,
                fontSize = 12.sp
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF1A1A1A))
                .border(1.dp, RSColors.StoneBorder, RoundedCornerShape(2.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RSStoneButton(
                text = "Add",
                onClick = onAdd,
                width = 80.dp,
                height = 22.dp
            )

            RSStoneButton(
                text = "Cancel",
                onClick = onCancel,
                width = 80.dp,
                height = 22.dp
            )
        }
    }
}

/**
 * Get text color based on friend status
 */
private fun getStatusTextColor(status: FriendStatus): Color {
    return when (status) {
        FriendStatus.ONLINE -> RSColors.TextGreen
        FriendStatus.IN_GAME -> RSColors.TextGreen
        FriendStatus.IN_LOBBY -> RSColors.TextCyan
        FriendStatus.AWAY -> RSColors.TextYellow
        FriendStatus.BUSY -> RSColors.TextOrange
        FriendStatus.OFFLINE -> RSColors.TextRed
    }
}

/**
 * Get orb color based on friend status
 */
private fun getStatusOrbColor(status: FriendStatus): Color {
    return when (status) {
        FriendStatus.ONLINE -> Color(0xFF00FF00)      // Bright green
        FriendStatus.IN_GAME -> Color(0xFF00FF00)     // Bright green
        FriendStatus.IN_LOBBY -> Color(0xFF00FFFF)    // Cyan
        FriendStatus.AWAY -> Color(0xFFFFFF00)        // Yellow
        FriendStatus.BUSY -> Color(0xFFFF8000)        // Orange
        FriendStatus.OFFLINE -> Color(0xFFFF0000)     // Red
    }
}

/**
 * Ignore list panel (similar structure to friends)
 */
@Composable
fun RSIgnorePanel(
    ignoreList: List<String>,
    onRemove: (String) -> Unit = {},
    onAdd: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val soundManager = LocalSoundManager.current
    var showAddDialog by remember { mutableStateOf(false) }
    var addName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
        // Header
        RSPanelHeaderRow(
            title = "Ignore List",
            subtitle = "${ignoreList.size} Players"
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Ignore list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(RSColors.PanelBackgroundDark.copy(alpha = 0.5f))
                .padding(2.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(ignoreList.sorted()) { name ->
                IgnoreListEntry(
                    name = name,
                    onRemove = {
                        soundManager?.play(RSSound.BUTTON_CLICK)
                        onRemove(name)
                    }
                )
            }

            if (ignoreList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ignore list is empty",
                            color = RSColors.TextWhite.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Add to ignore section
        if (showAddDialog) {
            AddFriendDialog(
                name = addName,
                onNameChange = { addName = it },
                onAdd = {
                    if (addName.isNotBlank()) {
                        soundManager?.play(RSSound.BUTTON_CLICK)
                        onAdd(addName.trim())
                        addName = ""
                        showAddDialog = false
                    }
                },
                onCancel = {
                    showAddDialog = false
                    addName = ""
                }
            )
        } else {
            RSStoneButton(
                text = "Add Name",
                onClick = {
                    soundManager?.play(RSSound.BUTTON_CLICK)
                    showAddDialog = true
                },
                width = 200.dp,
                height = 24.dp
            )
        }
    }
}

/**
 * Individual ignore list entry
 */
@Composable
private fun IgnoreListEntry(
    name: String,
    onRemove: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isHovered) RSColors.ChatTabActive else Color.Transparent,
                RoundedCornerShape(2.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {}
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            color = RSColors.TextRed,
            fontSize = 11.sp
        )

        if (isHovered) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RSColors.StoneMid)
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    color = RSColors.TextRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
