package com.rustscape.client.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rustscape.client.game.Item

/**
 * Bank tab data
 */
data class BankTab(
    val index: Int,
    val name: String = "Tab ${index + 1}",
    val icon: Int? = null // Item ID to use as tab icon, null for default
)

/**
 * Bank item data for display
 */
data class BankItemData(
    val slot: Int,
    val itemId: Int,
    val amount: Int,
    val name: String = "",
    val tab: Int = 0,
    val placeholder: Boolean = false // Placeholder for withdrawn items
) {
    val isEmpty: Boolean get() = itemId <= 0 || amount <= 0

    companion object {
        val EMPTY = BankItemData(-1, -1, 0)
    }
}

/**
 * Bank state for managing bank UI
 */
class BankState {
    var isOpen by mutableStateOf(false)
        private set

    var selectedTab by mutableStateOf(0)
    var searchQuery by mutableStateOf("")
    var withdrawMode by mutableStateOf(WithdrawMode.ITEM)
    var withdrawAmount by mutableStateOf(WithdrawAmount.ONE)
    var showPlaceholders by mutableStateOf(false)
    var showAllItems by mutableStateOf(false) // Show items from all tabs

    // Bank contents - map of tab index to items
    private val _items = mutableStateMapOf<Int, List<BankItemData>>()
    val items: Map<Int, List<BankItemData>> get() = _items

    // Total bank capacity
    var capacity by mutableStateOf(800)
        private set

    // Used slots count
    val usedSlots: Int
        get() = _items.values.sumOf { tabItems ->
            tabItems.count { !it.isEmpty && !it.placeholder }
        }

    fun open() {
        isOpen = true
    }

    fun close() {
        isOpen = false
        searchQuery = ""
    }

    fun setItems(tab: Int, items: List<BankItemData>) {
        _items[tab] = items
    }

    fun addItem(tab: Int, item: BankItemData) {
        val currentItems = _items[tab]?.toMutableList() ?: mutableListOf()
        currentItems.add(item)
        _items[tab] = currentItems
    }

    fun removeItem(tab: Int, slot: Int) {
        val currentItems = _items[tab]?.toMutableList() ?: return
        val index = currentItems.indexOfFirst { it.slot == slot }
        if (index >= 0) {
            if (showPlaceholders) {
                // Replace with placeholder
                val item = currentItems[index]
                currentItems[index] = item.copy(amount = 0, placeholder = true)
            } else {
                currentItems.removeAt(index)
            }
            _items[tab] = currentItems
        }
    }

    fun updateCapacity(newCapacity: Int) {
        capacity = newCapacity
    }

    fun getFilteredItems(): List<BankItemData> {
        val allItems = if (showAllItems || searchQuery.isNotEmpty()) {
            _items.values.flatten()
        } else {
            _items[selectedTab] ?: emptyList()
        }

        return if (searchQuery.isEmpty()) {
            allItems.filter { !it.isEmpty || (it.placeholder && showPlaceholders) }
        } else {
            allItems.filter { item ->
                !item.isEmpty && item.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    fun clearSearch() {
        searchQuery = ""
    }
}

/**
 * Withdraw modes
 */
enum class WithdrawMode(val displayName: String) {
    ITEM("Item"),
    NOTE("Note")
}

/**
 * Withdraw amount options
 */
enum class WithdrawAmount(val displayName: String, val value: Int?) {
    ONE("1", 1),
    FIVE("5", 5),
    TEN("10", 10),
    ALL("All", null),
    X("X", null)
}

/**
 * Bank UI colors matching RS style
 */
object BankColors {
    val Background = Color(0xFF2B2217)
    val BackgroundDark = Color(0xFF1A1510)
    val Border = Color(0xFF5C4A36)
    val BorderLight = Color(0xFF8B7355)
    val TabActive = Color(0xFF4A3D2F)
    val TabInactive = Color(0xFF3A2F24)
    val TabHover = Color(0xFF5A4939)
    val SlotBackground = Color(0xFF3E3529)
    val SlotBorder = Color(0xFF5C4A36)
    val SlotHighlight = Color(0xFF6B5A46)
    val SlotPlaceholder = Color(0x40FFFFFF)
    val SearchBackground = Color(0xFF1A1510)
    val SearchText = Color(0xFFFFFFFF)
    val SearchPlaceholder = Color(0xFF808080)
    val ButtonBackground = Color(0xFF4A3D2F)
    val ButtonHover = Color(0xFF5A4939)
    val ButtonActive = Color(0xFFD4A84B)
    val ButtonText = Color(0xFFFFFFFF)
    val HeaderText = Color(0xFFD4A84B)
    val CapacityText = Color(0xFFFFFF00)
    val AmountText = Color(0xFFFFFF00)
    val AmountTextLarge = Color(0xFF00FF00)
}

/**
 * Remember bank state
 */
@Composable
fun rememberBankState(): BankState {
    return remember { BankState() }
}

/**
 * Full bank interface panel
 * This is a popup/overlay style panel that appears when interacting with a bank
 */
@Composable
fun RSBankPanel(
    state: BankState,
    tabs: List<BankTab> = (0..8).map { BankTab(it) },
    onClose: () -> Unit = {},
    onDeposit: (itemId: Int, amount: Int) -> Unit = { _, _ -> },
    onDepositAll: () -> Unit = {},
    onDepositEquipment: () -> Unit = {},
    onWithdraw: (itemId: Int, slot: Int, amount: Int, noted: Boolean) -> Unit = { _, _, _, _ -> },
    onTabSelect: (Int) -> Unit = {},
    onSearch: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val soundManager = LocalSoundManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x80000000))
            .pointerInput(Unit) {
                detectTapGestures {
                    // Close when clicking outside the panel
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Main bank panel
        Column(
            modifier = Modifier
                .width(488.dp)
                .height(340.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(BankColors.Background, BankColors.BackgroundDark)
                    ),
                    shape = RoundedCornerShape(4.dp)
                )
                .border(2.dp, BankColors.Border, RoundedCornerShape(4.dp))
                .pointerInput(Unit) {
                    // Consume clicks inside the panel
                    detectTapGestures { }
                }
        ) {
            // Header with title and close button
            BankHeader(
                usedSlots = state.usedSlots,
                capacity = state.capacity,
                onClose = {
                    soundManager?.play(RSSound.WINDOW_CLOSE)
                    state.close()
                    onClose()
                }
            )

            // Tab row
            BankTabRow(
                tabs = tabs,
                selectedTab = state.selectedTab,
                onTabSelect = { tab ->
                    soundManager?.play(RSSound.TAB_SWITCH)
                    state.selectedTab = tab
                    onTabSelect(tab)
                }
            )

            // Search bar
            BankSearchBar(
                query = state.searchQuery,
                onQueryChange = { query ->
                    state.searchQuery = query
                    onSearch(query)
                },
                onClear = {
                    state.clearSearch()
                    onSearch("")
                }
            )

            // Main bank grid
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                BankItemGrid(
                    items = state.getFilteredItems(),
                    showPlaceholders = state.showPlaceholders,
                    onItemClick = { item ->
                        if (!item.isEmpty) {
                            val amount = when (state.withdrawAmount) {
                                WithdrawAmount.ONE -> 1
                                WithdrawAmount.FIVE -> 5
                                WithdrawAmount.TEN -> 10
                                WithdrawAmount.ALL -> item.amount
                                WithdrawAmount.X -> 1 // Would normally show input dialog
                            }
                            soundManager?.play(RSSound.ITEM_MOVE)
                            onWithdraw(
                                item.itemId,
                                item.slot,
                                amount,
                                state.withdrawMode == WithdrawMode.NOTE
                            )
                        }
                    },
                    onItemRightClick = { item ->
                        // Would show context menu with options
                    }
                )
            }

            // Bottom controls
            BankControls(
                withdrawMode = state.withdrawMode,
                withdrawAmount = state.withdrawAmount,
                showPlaceholders = state.showPlaceholders,
                onWithdrawModeChange = { state.withdrawMode = it },
                onWithdrawAmountChange = { state.withdrawAmount = it },
                onPlaceholdersToggle = { state.showPlaceholders = it },
                onDepositAll = {
                    soundManager?.play(RSSound.ITEM_MOVE)
                    onDepositAll()
                },
                onDepositEquipment = {
                    soundManager?.play(RSSound.ITEM_MOVE)
                    onDepositEquipment()
                }
            )
        }
    }
}

/**
 * Bank header with title and close button
 */
@Composable
private fun BankHeader(
    usedSlots: Int,
    capacity: Int,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BankColors.TabActive)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Title
        Text(
            text = "Bank of RuneScape",
            color = BankColors.HeaderText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        // Capacity counter
        Text(
            text = "$usedSlots / $capacity",
            color = BankColors.CapacityText,
            fontSize = 12.sp
        )

        // Close button
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(Color(0xFFCC3333), RoundedCornerShape(2.dp))
                .border(1.dp, Color(0xFF992222), RoundedCornerShape(2.dp))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "X",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Bank tab row
 */
@Composable
private fun BankTabRow(
    tabs: List<BankTab>,
    selectedTab: Int,
    onTabSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(BankColors.BackgroundDark)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedTab
            var isHovered by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        when {
                            isSelected -> BankColors.TabActive
                            isHovered -> BankColors.TabHover
                            else -> BankColors.TabInactive
                        },
                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                    )
                    .border(
                        1.dp,
                        if (isSelected) BankColors.BorderLight else BankColors.Border,
                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                    )
                    .clickable { onTabSelect(index) }
                    .pointerInput(Unit) {
                        // Hover detection would go here
                    },
                contentAlignment = Alignment.Center
            ) {
                if (tab.icon != null) {
                    // Show item sprite as tab icon
                    ItemSprite(
                        itemId = tab.icon,
                        size = 20.dp
                    )
                } else {
                    // Show tab number
                    Text(
                        text = "${index + 1}",
                        color = if (isSelected) BankColors.HeaderText else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Bank search bar
 */
@Composable
private fun BankSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Search:",
            color = Color.White,
            fontSize = 11.sp
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .background(BankColors.SearchBackground, RoundedCornerShape(2.dp))
                .border(1.dp, BankColors.Border, RoundedCornerShape(2.dp))
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Enter item name...",
                    color = BankColors.SearchPlaceholder,
                    fontSize = 11.sp
                )
            }

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = BankColors.SearchText,
                    fontSize = 11.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Clear button
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(BankColors.ButtonBackground, RoundedCornerShape(2.dp))
                    .clickable { onClear() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "X",
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Bank item grid
 */
@Composable
private fun BankItemGrid(
    items: List<BankItemData>,
    showPlaceholders: Boolean,
    onItemClick: (BankItemData) -> Unit,
    onItemRightClick: (BankItemData) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(items) { item ->
            BankSlot(
                item = item,
                showPlaceholder = showPlaceholders,
                onClick = { onItemClick(item) },
                onRightClick = { onItemRightClick(item) }
            )
        }
    }
}

/**
 * Single bank slot
 */
@Composable
private fun BankSlot(
    item: BankItemData,
    showPlaceholder: Boolean,
    onClick: () -> Unit,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isHovered by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(36.dp)
            .background(
                if (isHovered) BankColors.SlotHighlight else BankColors.SlotBackground,
                RoundedCornerShape(2.dp)
            )
            .border(1.dp, BankColors.SlotBorder, RoundedCornerShape(2.dp))
            .clickable { onClick() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onRightClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            item.placeholder && showPlaceholder -> {
                // Show placeholder (grayed out item)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(BankColors.SlotPlaceholder, RoundedCornerShape(2.dp))
                ) {
                    ItemSprite(
                        itemId = item.itemId,
                        size = 28.dp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            !item.isEmpty -> {
                // Show item with amount
                Box(modifier = Modifier.fillMaxSize()) {
                    ItemSprite(
                        itemId = item.itemId,
                        size = 28.dp,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // Amount text
                    if (item.amount > 1) {
                        Text(
                            text = formatAmount(item.amount),
                            color = getAmountColor(item.amount),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bank bottom controls
 */
@Composable
private fun BankControls(
    withdrawMode: WithdrawMode,
    withdrawAmount: WithdrawAmount,
    showPlaceholders: Boolean,
    onWithdrawModeChange: (WithdrawMode) -> Unit,
    onWithdrawAmountChange: (WithdrawAmount) -> Unit,
    onPlaceholdersToggle: (Boolean) -> Unit,
    onDepositAll: () -> Unit,
    onDepositEquipment: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BankColors.BackgroundDark)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Withdraw mode and amount row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Withdraw mode toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Withdraw as:",
                    color = Color.White,
                    fontSize = 10.sp
                )

                WithdrawMode.entries.forEach { mode ->
                    BankButton(
                        text = mode.displayName,
                        isSelected = withdrawMode == mode,
                        onClick = { onWithdrawModeChange(mode) },
                        width = 40.dp
                    )
                }
            }

            // Placeholder toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Placeholders:",
                    color = Color.White,
                    fontSize = 10.sp
                )
                BankButton(
                    text = if (showPlaceholders) "On" else "Off",
                    isSelected = showPlaceholders,
                    onClick = { onPlaceholdersToggle(!showPlaceholders) },
                    width = 30.dp
                )
            }
        }

        // Withdraw amount row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Withdraw amount buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quantity:",
                    color = Color.White,
                    fontSize = 10.sp
                )

                WithdrawAmount.entries.forEach { amount ->
                    BankButton(
                        text = amount.displayName,
                        isSelected = withdrawAmount == amount,
                        onClick = { onWithdrawAmountChange(amount) },
                        width = 28.dp
                    )
                }
            }

            // Deposit buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BankButton(
                    text = "Deposit Inventory",
                    onClick = onDepositAll,
                    width = 90.dp
                )
                BankButton(
                    text = "Deposit Worn",
                    onClick = onDepositEquipment,
                    width = 80.dp
                )
            }
        }
    }
}

/**
 * Bank button component
 */
@Composable
private fun BankButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    width: Dp = 60.dp
) {
    var isHovered by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(width)
            .height(18.dp)
            .background(
                when {
                    isSelected -> BankColors.ButtonActive
                    isHovered -> BankColors.ButtonHover
                    else -> BankColors.ButtonBackground
                },
                RoundedCornerShape(2.dp)
            )
            .border(
                1.dp,
                if (isSelected) BankColors.HeaderText else BankColors.Border,
                RoundedCornerShape(2.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) BankColors.BackgroundDark else BankColors.ButtonText,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Format item amount for display
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
 * Get color for amount text based on value
 */
private fun getAmountColor(amount: Int): Color {
    return when {
        amount >= 10_000_000 -> Color(0xFF00FF00) // Green for millions
        amount >= 100_000 -> Color(0xFFFFFFFF) // White for 100k+
        else -> BankColors.AmountText // Yellow for normal
    }
}

/**
 * Compact bank display for sidebar (shows summary)
 */
@Composable
fun BankSummaryPanel(
    state: BankState,
    onOpenBank: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BankColors.Background, RoundedCornerShape(4.dp))
            .border(1.dp, BankColors.Border, RoundedCornerShape(4.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Bank",
            color = BankColors.HeaderText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Used: ${state.usedSlots} / ${state.capacity}",
            color = Color.White,
            fontSize = 12.sp
        )

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(BankColors.BackgroundDark, RoundedCornerShape(2.dp))
                .border(1.dp, BankColors.Border, RoundedCornerShape(2.dp))
        ) {
            val progress = state.usedSlots.toFloat() / state.capacity.coerceAtLeast(1)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(
                        when {
                            progress > 0.9f -> Color(0xFFFF4444)
                            progress > 0.7f -> Color(0xFFFFAA00)
                            else -> Color(0xFF44FF44)
                        },
                        RoundedCornerShape(2.dp)
                    )
            )
        }

        BankButton(
            text = "Open Bank",
            onClick = onOpenBank,
            width = 100.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
