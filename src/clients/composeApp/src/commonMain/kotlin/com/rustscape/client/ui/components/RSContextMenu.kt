package com.rustscape.client.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Context menu option with colored text (RS-style)
 */
data class ContextMenuOption(
    val text: String,
    val target: String? = null,  // Target name (e.g., player name, item name)
    val color: Color = RSColors.TextYellow,
    val targetColor: Color = RSColors.TextCyan,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * RS-style context menu state holder
 */
class ContextMenuState {
    var isVisible by mutableStateOf(false)
    var position by mutableStateOf(Offset.Zero)
    var options by mutableStateOf<List<ContextMenuOption>>(emptyList())
    var title by mutableStateOf<String?>(null)

    fun show(x: Float, y: Float, menuOptions: List<ContextMenuOption>, menuTitle: String? = null) {
        position = Offset(x, y)
        options = menuOptions
        title = menuTitle
        isVisible = true
    }

    fun hide() {
        isVisible = false
        options = emptyList()
        title = null
    }
}

/**
 * Remember a context menu state
 */
@Composable
fun rememberContextMenuState(): ContextMenuState {
    return remember { ContextMenuState() }
}

/**
 * Classic RuneScape-style right-click context menu
 * Features:
 * - Dark semi-transparent background
 * - Yellow action text with cyan target names
 * - Hover highlight effect
 * - Cancel option at bottom
 */
@Composable
fun RSContextMenu(
    state: ContextMenuState,
    modifier: Modifier = Modifier
) {
    if (state.isVisible) {
        Popup(
            offset = IntOffset(state.position.x.toInt(), state.position.y.toInt()),
            onDismissRequest = { state.hide() },
            properties = PopupProperties(
                focusable = true,
                dismissOnClickOutside = true,
                dismissOnBackPress = true
            )
        ) {
            RSContextMenuContent(
                title = state.title,
                options = state.options,
                onDismiss = { state.hide() },
                modifier = modifier
            )
        }
    }
}

/**
 * Context menu content panel
 */
@Composable
private fun RSContextMenuContent(
    title: String?,
    options: List<ContextMenuOption>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(2.dp))
            .clip(RoundedCornerShape(2.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xE6494034),
                        Color(0xE63D3429),
                        Color(0xE6302820)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6B5D4D),
                        Color(0xFF4A3F35),
                        Color(0xFF3A3025)
                    )
                ),
                shape = RoundedCornerShape(2.dp)
            )
            .widthIn(min = 120.dp, max = 250.dp)
    ) {
        // Title header (if provided)
        if (title != null) {
            RSContextMenuHeader(title = title)
        }

        // Menu options
        options.forEach { option ->
            RSContextMenuItem(
                option = option,
                onDismiss = onDismiss
            )
        }

        // Separator before Cancel
        RSContextMenuDivider()

        // Cancel option (always present)
        RSContextMenuItem(
            option = ContextMenuOption(
                text = "Cancel",
                color = RSColors.TextWhite,
                onClick = {}
            ),
            onDismiss = onDismiss
        )
    }
}

/**
 * Context menu header with title
 */
@Composable
private fun RSContextMenuHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2B2117))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = title,
            color = RSColors.TextOrange,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
    RSContextMenuDivider()
}

/**
 * Individual context menu item with hover effect
 */
@Composable
private fun RSContextMenuItem(
    option: ContextMenuOption,
    onDismiss: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = option.enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    option.onClick()
                    onDismiss()
                }
            )
            .background(
                if (isHovered && option.enabled) {
                    Color(0xFF5A4D3D)
                } else {
                    Color.Transparent
                }
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Action text
        Text(
            text = option.text,
            color = if (option.enabled) option.color else option.color.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )

        // Target name (if present)
        if (option.target != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = option.target,
                color = if (option.enabled) option.targetColor else option.targetColor.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

/**
 * Divider line between menu sections
 */
@Composable
private fun RSContextMenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF5A4D3D),
                        Color(0xFF5A4D3D),
                        Color.Transparent
                    )
                )
            )
    )
}

/**
 * Modifier extension to add right-click context menu support
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.rsContextMenu(
    state: ContextMenuState,
    optionsProvider: () -> List<ContextMenuOption>,
    title: String? = null
): Modifier {
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press) {
                    val change = event.changes.firstOrNull()
                    if (change != null && event.button == PointerButton.Secondary) {
                        val position = change.position
                        state.show(
                            x = position.x,
                            y = position.y,
                            menuOptions = optionsProvider(),
                            menuTitle = title
                        )
                        change.consume()
                    }
                }
            }
        }
    }
}

/**
 * Pre-built context menu options for common RS interactions
 */
object RSContextMenuOptions {

    /**
     * Player interaction options
     */
    fun forPlayer(
        playerName: String,
        onFollow: () -> Unit = {},
        onTrade: () -> Unit = {},
        onChallenge: () -> Unit = {},
        onReport: () -> Unit = {}
    ): List<ContextMenuOption> = listOf(
        ContextMenuOption(
            text = "Follow",
            target = playerName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextWhite,
            onClick = onFollow
        ),
        ContextMenuOption(
            text = "Trade with",
            target = playerName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextWhite,
            onClick = onTrade
        ),
        ContextMenuOption(
            text = "Challenge",
            target = playerName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextWhite,
            onClick = onChallenge
        ),
        ContextMenuOption(
            text = "Report",
            target = playerName,
            color = RSColors.TextRed,
            targetColor = RSColors.TextWhite,
            onClick = onReport
        )
    )

    /**
     * NPC interaction options
     */
    fun forNpc(
        npcName: String,
        onTalk: (() -> Unit)? = null,
        onAttack: (() -> Unit)? = null,
        onPickpocket: (() -> Unit)? = null,
        onExamine: () -> Unit = {}
    ): List<ContextMenuOption> = buildList {
        if (onTalk != null) {
            add(
                ContextMenuOption(
                    text = "Talk-to",
                    target = npcName,
                    color = RSColors.TextYellow,
                    targetColor = RSColors.TextCyan,
                    onClick = onTalk
                )
            )
        }
        if (onAttack != null) {
            add(
                ContextMenuOption(
                    text = "Attack",
                    target = npcName,
                    color = RSColors.TextYellow,
                    targetColor = RSColors.TextCyan,
                    onClick = onAttack
                )
            )
        }
        if (onPickpocket != null) {
            add(
                ContextMenuOption(
                    text = "Pickpocket",
                    target = npcName,
                    color = RSColors.TextYellow,
                    targetColor = RSColors.TextCyan,
                    onClick = onPickpocket
                )
            )
        }
        add(
            ContextMenuOption(
                text = "Examine",
                target = npcName,
                color = RSColors.TextYellow,
                targetColor = RSColors.TextCyan,
                onClick = onExamine
            )
        )
    }

    /**
     * Ground item interaction options
     */
    fun forGroundItem(
        itemName: String,
        onTake: () -> Unit = {},
        onExamine: () -> Unit = {}
    ): List<ContextMenuOption> = listOf(
        ContextMenuOption(
            text = "Take",
            target = itemName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextOrange,
            onClick = onTake
        ),
        ContextMenuOption(
            text = "Examine",
            target = itemName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextOrange,
            onClick = onExamine
        )
    )

    /**
     * Inventory item interaction options
     */
    fun forInventoryItem(
        itemName: String,
        onUse: () -> Unit = {},
        onDrop: () -> Unit = {},
        onExamine: () -> Unit = {},
        customOptions: List<ContextMenuOption> = emptyList()
    ): List<ContextMenuOption> = buildList {
        add(
            ContextMenuOption(
                text = "Use",
                target = itemName,
                color = RSColors.TextYellow,
                targetColor = RSColors.TextOrange,
                onClick = onUse
            )
        )
        addAll(customOptions)
        add(
            ContextMenuOption(
                text = "Drop",
                target = itemName,
                color = RSColors.TextYellow,
                targetColor = RSColors.TextOrange,
                onClick = onDrop
            )
        )
        add(
            ContextMenuOption(
                text = "Examine",
                target = itemName,
                color = RSColors.TextYellow,
                targetColor = RSColors.TextOrange,
                onClick = onExamine
            )
        )
    }

    /**
     * Game object interaction options
     */
    fun forGameObject(
        objectName: String,
        primaryAction: String = "Use",
        onPrimaryAction: () -> Unit = {},
        onExamine: () -> Unit = {},
        additionalOptions: List<ContextMenuOption> = emptyList()
    ): List<ContextMenuOption> = buildList {
        add(
            ContextMenuOption(
                text = primaryAction,
                target = objectName,
                color = RSColors.TextYellow,
                targetColor = RSColors.TextCyan,
                onClick = onPrimaryAction
            )
        )
        addAll(additionalOptions)
        add(
            ContextMenuOption(
                text = "Examine",
                target = objectName,
                color = RSColors.TextYellow,
                targetColor = RSColors.TextCyan,
                onClick = onExamine
            )
        )
    }

    /**
     * Walk here option (usually first in list when clicking ground)
     */
    fun walkHere(onClick: () -> Unit): ContextMenuOption = ContextMenuOption(
        text = "Walk here",
        color = RSColors.TextYellow,
        onClick = onClick
    )

    /**
     * Bank item interaction options (for items in the bank)
     */
    fun forBankItem(
        itemName: String,
        onWithdraw1: () -> Unit = {},
        onWithdraw5: () -> Unit = {},
        onWithdraw10: () -> Unit = {},
        onWithdrawAll: () -> Unit = {},
        onWithdrawX: () -> Unit = {},
        onWithdrawAllButOne: () -> Unit = {},
        onExamine: () -> Unit = {}
    ): List<ContextMenuOption> = listOf(
        ContextMenuOption(
            text = "Withdraw-1",
            target = itemName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextOrange,
            onClick = onWithdraw1
        ),
        ContextMenuOption(
            text = "Withdraw-5",
            target = itemName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextOrange,
            onClick = onWithdraw5
        ),
        ContextMenuOption(
            text = "Withdraw-10",
            target = itemName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextOrange,
            onClick = onWithdraw10
        ),
        ContextMenuOption(
            text = "Withdraw-All",
            target = itemName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextOrange,
            onClick = onWithdrawAll
        ),
        ContextMenuOption(
            text = "Withdraw-X",
            target = itemName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextOrange,
            onClick = onWithdrawX
        ),
        ContextMenuOption(
            text = "Withdraw-All-but-1",
            target = itemName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextOrange,
            onClick = onWithdrawAllButOne
        ),
        ContextMenuOption(
            text = "Examine",
            target = itemName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextOrange,
            onClick = onExamine
        )
    )

    /**
     * Inventory item options when bank is open (deposit options)
     */
    fun forInventoryItemWithBank(
        itemName: String,
        onDeposit1: () -> Unit = {},
        onDeposit5: () -> Unit = {},
        onDeposit10: () -> Unit = {},
        onDepositAll: () -> Unit = {},
        onDepositX: () -> Unit = {},
        onExamine: () -> Unit = {}
    ): List<ContextMenuOption> = listOf(
        ContextMenuOption(
            text = "Deposit-1",
            target = itemName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextOrange,
            onClick = onDeposit1
        ),
        ContextMenuOption(
            text = "Deposit-5",
            target = itemName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextOrange,
            onClick = onDeposit5
        ),
        ContextMenuOption(
            text = "Deposit-10",
            target = itemName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextOrange,
            onClick = onDeposit10
        ),
        ContextMenuOption(
            text = "Deposit-All",
            target = itemName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextOrange,
            onClick = onDepositAll
        ),
        ContextMenuOption(
            text = "Deposit-X",
            target = itemName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextOrange,
            onClick = onDepositX
        ),
        ContextMenuOption(
            text = "Examine",
            target = itemName,
            color = RSColors.TextYellow,
            targetColor = RSColors.TextOrange,
            onClick = onExamine
        )
    )

    /**
     * Equipment item options (for worn items)
     */
    fun forEquipmentItem(
        itemName: String,
        onRemove: () -> Unit = {},
        onOperate: (() -> Unit)? = null,
        onExamine: () -> Unit = {}
    ): List<ContextMenuOption> = buildList {
        add(
            ContextMenuOption(
                text = "Remove",
                target = itemName,
                color = RSColors.TextYellow,
                targetColor = RSColors.TextOrange,
                onClick = onRemove
            )
        )
        if (onOperate != null) {
            add(
                ContextMenuOption(
                    text = "Operate",
                    target = itemName,
                    color = RSColors.TextYellow,
                    targetColor = RSColors.TextOrange,
                    onClick = onOperate
                )
            )
        }
        add(
            ContextMenuOption(
                text = "Examine",
                target = itemName,
                color = RSColors.TextYellow,
                targetColor = RSColors.TextOrange,
                onClick = onExamine
            )
        )
    }
}
