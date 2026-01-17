package com.rustscape.client.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.rustscape.client.game.Inventory
import com.rustscape.client.game.Item
import kotlin.math.roundToInt

/**
 * Inventory item display data
 */
data class InventoryItemData(
    val slot: Int,
    val itemId: Int,
    val amount: Int,
    val name: String = "",
    val isStackable: Boolean = false,
    val isNoted: Boolean = false
) {
    val isEmpty: Boolean get() = itemId <= 0 || amount <= 0

    companion object {
        val EMPTY = InventoryItemData(-1, -1, 0)
    }
}

/**
 * Drag operation types
 */
enum class DragOperationType {
    SWAP,           // Swap two items
    MOVE,           // Move to empty slot
    DROP,           // Drop on ground
    USE,            // Use item on something
    EQUIP           // Equip item
}

/**
 * Drag target areas
 */
enum class DropTargetType {
    INVENTORY_SLOT,
    EQUIPMENT_SLOT,
    GROUND,
    BANK_SLOT,
    TRADE_SLOT
}

/**
 * Drop target information
 */
data class DropTarget(
    val type: DropTargetType,
    val slot: Int,
    val bounds: androidx.compose.ui.geometry.Rect
)

/**
 * Inventory drag state holder
 */
class InventoryDragState {
    // Currently dragging item
    var draggedItem by mutableStateOf<InventoryItemData?>(null)
        private set

    // Source slot of dragged item
    var sourceSlot by mutableStateOf(-1)
        private set

    // Current drag offset from source position
    var dragOffset by mutableStateOf(Offset.Zero)
        private set

    // Source position in root coordinates
    var sourcePosition by mutableStateOf(Offset.Zero)
        private set

    // Whether currently dragging
    val isDragging: Boolean get() = draggedItem != null

    // Highlighted drop target slot
    var highlightedSlot by mutableStateOf(-1)

    // Drop targets registered in the UI
    private val _dropTargets = mutableStateListOf<DropTarget>()
    val dropTargets: List<DropTarget> get() = _dropTargets

    /**
     * Start dragging an item
     */
    fun startDrag(item: InventoryItemData, slot: Int, position: Offset) {
        draggedItem = item
        sourceSlot = slot
        sourcePosition = position
        dragOffset = Offset.Zero
    }

    /**
     * Update drag position
     */
    fun updateDrag(offset: Offset) {
        dragOffset = offset

        // Find highlighted slot based on current position
        val currentPos = sourcePosition + dragOffset
        highlightedSlot = findDropSlot(currentPos)
    }

    /**
     * End drag operation
     */
    fun endDrag(): Pair<Int, Int>? {
        val result = if (isDragging && highlightedSlot >= 0 && highlightedSlot != sourceSlot) {
            Pair(sourceSlot, highlightedSlot)
        } else {
            null
        }

        draggedItem = null
        sourceSlot = -1
        dragOffset = Offset.Zero
        highlightedSlot = -1

        return result
    }

    /**
     * Cancel drag operation
     */
    fun cancelDrag() {
        draggedItem = null
        sourceSlot = -1
        dragOffset = Offset.Zero
        highlightedSlot = -1
    }

    /**
     * Register a drop target
     */
    fun registerDropTarget(target: DropTarget) {
        _dropTargets.removeAll { it.slot == target.slot && it.type == target.type }
        _dropTargets.add(target)
    }

    /**
     * Find which slot the current drag position is over
     */
    private fun findDropSlot(position: Offset): Int {
        for (target in _dropTargets) {
            if (target.type == DropTargetType.INVENTORY_SLOT &&
                target.bounds.contains(position)
            ) {
                return target.slot
            }
        }
        return -1
    }

    /**
     * Check if position is outside inventory (for dropping)
     */
    fun isOverGround(position: Offset): Boolean {
        val currentPos = sourcePosition + position
        return _dropTargets.none { it.bounds.contains(currentPos) }
    }
}

/**
 * Remember inventory drag state
 */
@Composable
fun rememberInventoryDragState(): InventoryDragState {
    return remember { InventoryDragState() }
}

/**
 * CompositionLocal for inventory drag state
 */
val LocalInventoryDragState = staticCompositionLocalOf<InventoryDragState?> { null }

/**
 * Inventory colors
 */
object InventoryColors {
    val SlotBackground = Color(0xFF3E3529)
    val SlotBorder = Color(0xFF5C4A36)
    val SlotHighlight = Color(0xFF6B5A46)
    val SlotSelected = Color(0xFFD4A84B)
    val SlotDragTarget = Color(0xFF4CAF50)
    val SlotDragSource = Color(0x40FFFFFF)
    val AmountText = Color(0xFFFFFF00)
    val AmountTextLarge = Color(0xFF00FF00)
    val AmountTextHuge = Color(0xFFFFFFFF)
}

/**
 * Enhanced inventory panel with drag-and-drop support
 *
 * @param inventory The inventory data
 * @param items Item display data for each slot
 * @param onItemClick Callback for left-click on item
 * @param onItemRightClick Callback for right-click on item (context menu)
 * @param onItemShiftClick Callback for shift-click on item (quick drop/use)
 * @param onItemSwap Callback when items are swapped via drag
 * @param onItemDrop Callback when item is dropped on ground
 * @param onItemUse Callback when item is used
 * @param modifier Modifier for the panel
 */
@Composable
fun RSInventoryPanel(
    inventory: Inventory,
    items: List<InventoryItemData> = emptyList(),
    onItemClick: (slot: Int, item: InventoryItemData) -> Unit = { _, _ -> },
    onItemRightClick: (slot: Int, item: InventoryItemData) -> Unit = { _, _ -> },
    onItemShiftClick: (slot: Int, item: InventoryItemData, action: ShiftClickAction) -> Unit = { _, _, _ -> },
    onItemSwap: (fromSlot: Int, toSlot: Int) -> Unit = { _, _ -> },
    onItemDrop: (slot: Int, item: InventoryItemData) -> Unit = { _, _ -> },
    onItemUse: (slot: Int, item: InventoryItemData) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val dragState = LocalInventoryDragState.current ?: rememberInventoryDragState()
    val soundManager = LocalSoundManager.current
    val keyboardModifiers = LocalKeyboardModifiers.current
    val shiftClickSettings = LocalShiftClickSettings.current

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            repeat(7) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(4) { col ->
                        val slot = row * 4 + col
                        val item = items.getOrNull(slot) ?: InventoryItemData.EMPTY

                        InventorySlot(
                            slot = slot,
                            item = item,
                            dragState = dragState,
                            onItemClick = { isShiftHeld ->
                                if (isShiftHeld && !item.isEmpty) {
                                    // Shift-click action
                                    val action =
                                        shiftClickSettings?.getItemAction(item.itemId, ShiftClickContext.INVENTORY)
                                            ?: ShiftClickAction.DROP
                                    if (action != ShiftClickAction.NONE) {
                                        onItemShiftClick(slot, item, action)
                                        // Play appropriate sound
                                        when (action) {
                                            ShiftClickAction.DROP -> soundManager?.play(RSSound.ITEM_DROP)
                                            ShiftClickAction.USE -> soundManager?.play(RSSound.BUTTON_CLICK)
                                            else -> {}
                                        }
                                    } else {
                                        onItemClick(slot, item)
                                    }
                                } else {
                                    onItemClick(slot, item)
                                }
                            },
                            onItemRightClick = { onItemRightClick(slot, item) },
                            onDragEnd = { fromSlot, toSlot ->
                                onItemSwap(fromSlot, toSlot)
                                soundManager?.play(RSSound.ITEM_MOVE)
                            },
                            modifier = Modifier.padding(1.dp)
                        )
                    }
                }
            }
        }

        // Dragged item overlay (rendered on top of everything)
        if (dragState.isDragging) {
            DraggedItemOverlay(
                item = dragState.draggedItem!!,
                offset = dragState.sourcePosition + dragState.dragOffset
            )
        }
    }
}

/**
 * Single inventory slot with drag-and-drop support and tooltip
 */
@Composable
private fun InventorySlot(
    slot: Int,
    item: InventoryItemData,
    dragState: InventoryDragState,
    onItemClick: (isShiftHeld: Boolean) -> Unit,
    onItemRightClick: () -> Unit,
    onDragEnd: (fromSlot: Int, toSlot: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Track keyboard modifiers for shift-click
    val keyboardModifiers = LocalKeyboardModifiers.current
    // Create tooltip data for this item
    val itemTooltip = if (!item.isEmpty && item.name.isNotEmpty()) {
        RSTooltips.forItem(
            itemName = item.name,
            isStackable = item.isStackable,
            amount = item.amount
        )
    } else {
        null
    }

    val density = LocalDensity.current
    var slotPosition by remember { mutableStateOf(Offset.Zero) }
    var slotSize by remember { mutableStateOf(Size.Zero) }

    // Animation for highlight state
    val isHighlighted = dragState.highlightedSlot == slot
    val isDragSource = dragState.sourceSlot == slot
    val highlightAlpha by animateFloatAsState(
        targetValue = if (isHighlighted) 1f else 0f,
        animationSpec = spring()
    )
    val sourceAlpha by animateFloatAsState(
        targetValue = if (isDragSource) 0.5f else 1f,
        animationSpec = spring()
    )

    val borderColor = when {
        isHighlighted -> InventoryColors.SlotDragTarget
        isDragSource -> InventoryColors.SlotSelected
        else -> InventoryColors.SlotBorder
    }

    // Wrap the slot in a tooltip if item has a name
    val slotContent: @Composable () -> Unit = {
        InventorySlotContent(
            slot = slot,
            item = item,
            dragState = dragState,
            isHighlighted = isHighlighted,
            isDragSource = isDragSource,
            highlightAlpha = highlightAlpha,
            sourceAlpha = sourceAlpha,
            borderColor = borderColor,
            onItemClick = onItemClick,
            onItemRightClick = onItemRightClick,
            onDragEnd = onDragEnd,
            modifier = modifier
        )
    }

    if (itemTooltip != null && !dragState.isDragging) {
        WithRSTooltip(
            tooltip = itemTooltip,
            position = TooltipPosition.CURSOR,
            showDelay = 400L
        ) {
            slotContent()
        }
    } else {
        slotContent()
    }
}

/**
 * Internal slot content without tooltip wrapper
 */
@Composable
private fun InventorySlotContent(
    slot: Int,
    item: InventoryItemData,
    dragState: InventoryDragState,
    isHighlighted: Boolean,
    isDragSource: Boolean,
    highlightAlpha: Float,
    sourceAlpha: Float,
    borderColor: Color,
    onItemClick: (isShiftHeld: Boolean) -> Unit,
    onItemRightClick: () -> Unit,
    onDragEnd: (fromSlot: Int, toSlot: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardModifiers = LocalKeyboardModifiers.current
    val density = LocalDensity.current
    var slotPosition by remember { mutableStateOf(Offset.Zero) }
    var slotSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .size(36.dp)
            .onGloballyPositioned { coordinates ->
                slotPosition = coordinates.positionInRoot()
                slotSize = Size(
                    coordinates.size.width.toFloat(),
                    coordinates.size.height.toFloat()
                )
                // Register as drop target
                dragState.registerDropTarget(
                    DropTarget(
                        type = DropTargetType.INVENTORY_SLOT,
                        slot = slot,
                        bounds = androidx.compose.ui.geometry.Rect(
                            slotPosition,
                            slotSize
                        )
                    )
                )
            }
            .drawBehind {
                // Slot background
                drawRoundRect(
                    color = InventoryColors.SlotBackground,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )

                // Inner shadow
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                    size = Size(size.width, size.height * 0.3f)
                )

                // Border
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                    style = Stroke(width = if (isHighlighted || isDragSource) 2f else 1f)
                )

                // Highlight overlay
                if (highlightAlpha > 0f) {
                    drawRoundRect(
                        color = InventoryColors.SlotDragTarget.copy(alpha = 0.3f * highlightAlpha),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                }
            }
            .alpha(sourceAlpha)
            .pointerInput(item, slot, keyboardModifiers) {
                detectTapGestures(
                    onTap = {
                        // Check if shift is held via our keyboard state
                        val isShiftHeld = keyboardModifiers?.isShiftHeld ?: false
                        onItemClick(isShiftHeld)
                    },
                    onLongPress = { onItemRightClick() }
                )
            }
            .pointerInput(item, slot) {
                if (!item.isEmpty) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragState.startDrag(
                                item = item,
                                slot = slot,
                                position = slotPosition + Offset(
                                    slotSize.width / 2,
                                    slotSize.height / 2
                                )
                            )
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragState.updateDrag(dragState.dragOffset + dragAmount)
                        },
                        onDragEnd = {
                            val result = dragState.endDrag()
                            if (result != null) {
                                onDragEnd(result.first, result.second)
                            }
                        },
                        onDragCancel = {
                            dragState.cancelDrag()
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Item content (only show if not being dragged)
        if (!item.isEmpty && !isDragSource) {
            ItemDisplay(item = item)
        }
    }
}

/**
 * Display an item (icon and amount)
 */
@Composable
private fun ItemDisplay(
    item: InventoryItemData,
    modifier: Modifier = Modifier,
    showAmount: Boolean = true
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Render item sprite using the ItemSpriteProvider system
        ItemSprite(
            itemId = item.itemId,
            size = 28.dp,
            showBackground = false
        )

        // Amount text (for stackable items or when amount > 1)
        if (showAmount && item.amount > 1) {
            val amountText = formatAmount(item.amount)
            val amountColor = when {
                item.amount >= 10_000_000 -> InventoryColors.AmountTextHuge  // Green for 10M+
                item.amount >= 100_000 -> InventoryColors.AmountTextLarge    // White for 100K+
                else -> InventoryColors.AmountText                           // Yellow for rest
            }

            Text(
                text = amountText,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = amountColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 2.dp)
            )
        }

        // Note indicator
        if (item.isNoted) {
            Text(
                text = "📄",
                fontSize = 8.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(1.dp)
            )
        }
    }
}

/**
 * Overlay showing the item being dragged
 */
@Composable
private fun DraggedItemOverlay(
    item: InventoryItemData,
    offset: Offset
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(offset.x.roundToInt() - 18, offset.y.roundToInt() - 18) }
            .size(36.dp)
            .scale(1.1f)
            .alpha(0.9f)
            .zIndex(1000f)
            .background(
                color = InventoryColors.SlotBackground.copy(alpha = 0.9f),
                shape = RoundedCornerShape(4.dp)
            )
            .border(2.dp, InventoryColors.SlotSelected, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        ItemDisplay(item = item)
    }
}

/**
 * Format item amount for display
 * RS-style: 1-99999 shown as-is, 100K+, 1M+, etc.
 */
private fun formatAmount(amount: Int): String {
    return when {
        amount >= 10_000_000 -> "${amount / 1_000_000}M"
        amount >= 100_000 -> "${amount / 1_000}K"
        amount >= 10_000 -> "${amount / 1_000}K"
        else -> amount.toString()
    }
}

/**
 * Simple inventory panel without drag-and-drop
 * Used when drag state is not needed
 */
@Composable
fun RSSimpleInventoryPanel(
    items: List<InventoryItemData> = emptyList(),
    onSlotClick: (slot: Int) -> Unit = {},
    onSlotRightClick: (slot: Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(7) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(4) { col ->
                    val slot = row * 4 + col
                    val item = items.getOrNull(slot) ?: InventoryItemData.EMPTY

                    RSInventorySlot(
                        modifier = Modifier.padding(1.dp),
                        onClick = { onSlotClick(slot) }
                    ) {
                        if (!item.isEmpty) {
                            ItemDisplay(item = item)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Equipment slot for the equipment panel
 */
@Composable
fun RSEquipmentSlot(
    slotType: EquipmentSlotType,
    item: InventoryItemData?,
    onClick: () -> Unit = {},
    onRightClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val slotSize = when (slotType) {
        EquipmentSlotType.HELMET,
        EquipmentSlotType.BODY,
        EquipmentSlotType.LEGS -> 40.dp

        EquipmentSlotType.WEAPON,
        EquipmentSlotType.SHIELD -> 40.dp

        else -> 36.dp
    }

    // Create tooltip for equipped item
    val itemTooltip = if (item != null && !item.isEmpty && item.name.isNotEmpty()) {
        RSTooltips.forItem(
            itemName = item.name,
            isStackable = item.isStackable,
            amount = item.amount
        )
    } else {
        null
    }

    val slotContent: @Composable () -> Unit = {
        EquipmentSlotContent(
            slotType = slotType,
            item = item,
            slotSize = slotSize,
            onClick = onClick,
            onRightClick = onRightClick,
            modifier = modifier
        )
    }

    if (itemTooltip != null) {
        WithRSTooltip(
            tooltip = itemTooltip,
            position = TooltipPosition.CURSOR,
            showDelay = 400L
        ) {
            slotContent()
        }
    } else {
        slotContent()
    }
}

/**
 * Internal equipment slot content without tooltip wrapper
 */
@Composable
private fun EquipmentSlotContent(
    slotType: EquipmentSlotType,
    item: InventoryItemData?,
    slotSize: Dp,
    onClick: () -> Unit,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(slotSize)
            .drawBehind {
                // Slot background with equipment-specific shape hint
                drawRoundRect(
                    color = InventoryColors.SlotBackground,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )

                // Inner shadow
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                    size = Size(size.width, size.height * 0.3f)
                )

                // Border
                drawRoundRect(
                    color = InventoryColors.SlotBorder,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                    style = Stroke(width = 1f)
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onRightClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (item != null && !item.isEmpty) {
            ItemDisplay(item = item, showAmount = false)
        } else {
            // Empty slot placeholder icon
            Text(
                text = slotType.icon,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.3f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Equipment slot types
 */
enum class EquipmentSlotType(val slot: Int, val icon: String) {
    HELMET(0, "🎩"),
    CAPE(1, "🧣"),
    AMULET(2, "📿"),
    WEAPON(3, "⚔️"),
    BODY(4, "👕"),
    SHIELD(5, "🛡️"),
    LEGS(7, "👖"),
    GLOVES(9, "🧤"),
    BOOTS(10, "👢"),
    RING(12, "💍"),
    AMMO(13, "🏹")
}

/**
 * Equipment panel with all slot positions
 */
@Composable
fun RSEquipmentPanel(
    equipment: Map<EquipmentSlotType, InventoryItemData?>,
    onSlotClick: (EquipmentSlotType) -> Unit = {},
    onSlotRightClick: (EquipmentSlotType) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: Helmet
            RSEquipmentSlot(
                slotType = EquipmentSlotType.HELMET,
                item = equipment[EquipmentSlotType.HELMET],
                onClick = { onSlotClick(EquipmentSlotType.HELMET) },
                onRightClick = { onSlotRightClick(EquipmentSlotType.HELMET) }
            )

            // Row 2: Cape, Amulet, Ammo
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RSEquipmentSlot(
                    slotType = EquipmentSlotType.CAPE,
                    item = equipment[EquipmentSlotType.CAPE],
                    onClick = { onSlotClick(EquipmentSlotType.CAPE) },
                    onRightClick = { onSlotRightClick(EquipmentSlotType.CAPE) }
                )
                RSEquipmentSlot(
                    slotType = EquipmentSlotType.AMULET,
                    item = equipment[EquipmentSlotType.AMULET],
                    onClick = { onSlotClick(EquipmentSlotType.AMULET) },
                    onRightClick = { onSlotRightClick(EquipmentSlotType.AMULET) }
                )
                RSEquipmentSlot(
                    slotType = EquipmentSlotType.AMMO,
                    item = equipment[EquipmentSlotType.AMMO],
                    onClick = { onSlotClick(EquipmentSlotType.AMMO) },
                    onRightClick = { onSlotRightClick(EquipmentSlotType.AMMO) }
                )
            }

            // Row 3: Weapon, Body, Shield
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RSEquipmentSlot(
                    slotType = EquipmentSlotType.WEAPON,
                    item = equipment[EquipmentSlotType.WEAPON],
                    onClick = { onSlotClick(EquipmentSlotType.WEAPON) },
                    onRightClick = { onSlotRightClick(EquipmentSlotType.WEAPON) }
                )
                RSEquipmentSlot(
                    slotType = EquipmentSlotType.BODY,
                    item = equipment[EquipmentSlotType.BODY],
                    onClick = { onSlotClick(EquipmentSlotType.BODY) },
                    onRightClick = { onSlotRightClick(EquipmentSlotType.BODY) }
                )
                RSEquipmentSlot(
                    slotType = EquipmentSlotType.SHIELD,
                    item = equipment[EquipmentSlotType.SHIELD],
                    onClick = { onSlotClick(EquipmentSlotType.SHIELD) },
                    onRightClick = { onSlotRightClick(EquipmentSlotType.SHIELD) }
                )
            }

            // Row 4: Legs
            RSEquipmentSlot(
                slotType = EquipmentSlotType.LEGS,
                item = equipment[EquipmentSlotType.LEGS],
                onClick = { onSlotClick(EquipmentSlotType.LEGS) },
                onRightClick = { onSlotRightClick(EquipmentSlotType.LEGS) }
            )

            // Row 5: Gloves, Boots, Ring
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RSEquipmentSlot(
                    slotType = EquipmentSlotType.GLOVES,
                    item = equipment[EquipmentSlotType.GLOVES],
                    onClick = { onSlotClick(EquipmentSlotType.GLOVES) },
                    onRightClick = { onSlotRightClick(EquipmentSlotType.GLOVES) }
                )
                RSEquipmentSlot(
                    slotType = EquipmentSlotType.BOOTS,
                    item = equipment[EquipmentSlotType.BOOTS],
                    onClick = { onSlotClick(EquipmentSlotType.BOOTS) },
                    onRightClick = { onSlotRightClick(EquipmentSlotType.BOOTS) }
                )
                RSEquipmentSlot(
                    slotType = EquipmentSlotType.RING,
                    item = equipment[EquipmentSlotType.RING],
                    onClick = { onSlotClick(EquipmentSlotType.RING) },
                    onRightClick = { onSlotRightClick(EquipmentSlotType.RING) }
                )
            }
        }
    }
}

/**
 * Convert game Inventory to display items
 */
fun Inventory.toDisplayItems(): List<InventoryItemData> {
    return (0 until capacity).map { slot ->
        val item = get(slot)
        if (item.isEmpty) {
            InventoryItemData.EMPTY.copy(slot = slot)
        } else {
            InventoryItemData(
                slot = slot,
                itemId = item.id,
                amount = item.amount
            )
        }
    }
}
