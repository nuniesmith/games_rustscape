package com.rustscape.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rustscape.client.game.*
import com.rustscape.client.ui.components.*
import com.rustscape.client.ui.components.ShiftClickAction
import com.rustscape.client.ui.components.LocalShiftClickSettings
import com.rustscape.client.ui.components.BankItemData
import com.rustscape.client.ui.components.BankTab
import com.rustscape.client.ui.components.RSBankPanel
import com.rustscape.client.ui.components.rememberBankState

/**
 * Sidebar tab definitions for the classic RS interface
 */
enum class SidebarTab(val icon: String, val tooltip: String) {
    COMBAT("⚔", "Combat Options"),
    SKILLS("📊", "Skills"),
    QUESTS("📜", "Quest List"),
    INVENTORY("🎒", "Inventory"),
    EQUIPMENT("👤", "Worn Equipment"),
    PRAYER("✝", "Prayer"),
    MAGIC("✨", "Magic Spellbook"),
    CLAN("👥", "Clan Chat"),
    FRIENDS("💬", "Friends List"),
    IGNORE("🚫", "Ignore List"),
    LOGOUT("🚪", "Logout"),
    SETTINGS("⚙", "Options"),
    EMOTES("😀", "Emotes"),
    MUSIC("🎵", "Music Player")
}

/**
 * Classic 2008-era RuneScape Game Screen
 * Features: Orbs for HP/Prayer/Run, tabbed sidebar, proper chat box
 */
/**
 * Bank operation callbacks for network integration
 */
data class BankCallbacks(
    val onClose: () -> Unit = {},
    val onWithdraw: (slot: Int, itemId: Int, amount: Int, asNote: Boolean) -> Unit = { _, _, _, _ -> },
    val onDeposit: (inventorySlot: Int, itemId: Int, amount: Int) -> Unit = { _, _, _ -> },
    val onDepositAll: () -> Unit = {},
    val onDepositEquipment: () -> Unit = {},
    val onTabSelect: (Int) -> Unit = {},
    val onSearch: (String) -> Unit = {},
    val onMoveItem: (fromSlot: Int, toSlot: Int) -> Unit = { _, _ -> },
    val onNoteMode: (Boolean) -> Unit = {},
    val onWithdrawMode: (Int) -> Unit = {}
)

/**
 * Inventory operation callbacks for network integration
 */
data class InventoryCallbacks(
    val onItemClick: (slot: Int, itemId: Int) -> Unit = { _, _ -> },
    val onItemDrop: (slot: Int, itemId: Int) -> Unit = { _, _ -> },
    val onItemEquip: (slot: Int, itemId: Int) -> Unit = { _, _ -> },
    val onItemUse: (slot: Int, itemId: Int) -> Unit = { _, _ -> },
    val onItemSwap: (fromSlot: Int, toSlot: Int) -> Unit = { _, _ -> },
    val onItemExamine: (itemId: Int) -> Unit = {}
)

@Composable
fun GameScreen(
    gameState: GameState,
    onLogout: () -> Unit,
    onSendChat: (String) -> Unit,
    onSendCommand: (String) -> Unit,
    onWalkTo: (Int, Int) -> Unit = { _, _ -> },
    onMinimapWalk: (Int, Int) -> Unit = { _, _ -> },
    onEntityInteraction: (GameEntity, String) -> Unit = { _, _ -> },
    onInventorySwap: (fromSlot: Int, toSlot: Int) -> Unit = { _, _ -> },
    onEquipmentClick: (slot: EquipmentSlotType) -> Unit = {},
    onBankOpen: () -> Unit = {},
    bankCallbacks: BankCallbacks = BankCallbacks(),
    inventoryCallbacks: InventoryCallbacks = InventoryCallbacks(),
    modifier: Modifier = Modifier
) {
    val canvasState = remember { GameCanvasState() }
    val minimapState = rememberMinimapState()

    // Generate demo terrain when player position changes
    // Also clear flag destination if player has reached it
    LaunchedEffect(gameState.position.x, gameState.position.y) {
        minimapState.generateDemoTerrain(gameState.position.x, gameState.position.y)

        // Clear flag destination if player has reached it (within 1 tile tolerance)
        minimapState.flagDestination?.let { flag ->
            val dx = kotlin.math.abs(gameState.position.x - flag.x)
            val dy = kotlin.math.abs(gameState.position.y - flag.y)
            if (dx <= 1 && dy <= 1) {
                minimapState.flagDestination = null
            }
        }

        // Add demo entities around the player
        minimapState.entities = listOf(
            MinimapEntity(gameState.position.x + 5, gameState.position.y + 3, MinimapIconType.NPC_YELLOW, "Banker"),
            MinimapEntity(
                gameState.position.x - 3,
                gameState.position.y + 7,
                MinimapIconType.NPC_YELLOW,
                "Shop keeper"
            ),
            MinimapEntity(gameState.position.x + 8, gameState.position.y - 2, MinimapIconType.NPC_RED, "Guard"),
            MinimapEntity(gameState.position.x - 6, gameState.position.y - 4, MinimapIconType.OTHER_PLAYER, "Zezima"),
            MinimapEntity(gameState.position.x + 2, gameState.position.y + 10, MinimapIconType.FRIEND, "Friend123"),
            MinimapEntity(gameState.position.x + 12, gameState.position.y + 5, MinimapIconType.ITEM, "Coins"),
            MinimapEntity(gameState.position.x - 10, gameState.position.y + 2, MinimapIconType.BANK, "Bank"),
            MinimapEntity(gameState.position.x + 15, gameState.position.y - 8, MinimapIconType.FISHING, "Fishing spot"),
        )
    }
    val contextMenuState = rememberContextMenuState()
    var selectedTab by remember { mutableStateOf(SidebarTab.INVENTORY) }
    var selectedChatTab by remember { mutableStateOf(ChatTab.ALL) }
    val soundManager = LocalSoundManager.current

    // Bank state
    val bankState = rememberBankState()

    // Sync GameState bank data to UI BankState
    // When server sends bank open packet, open the UI
    LaunchedEffect(gameState.bankOpen) {
        if (gameState.bankOpen && !bankState.isOpen) {
            bankState.open()
            bankState.updateCapacity(gameState.bankCapacity)
        } else if (!gameState.bankOpen && bankState.isOpen) {
            bankState.close()
        }
    }

    // Sync bank items from GameState to BankState
    LaunchedEffect(gameState.bankItems) {
        // Convert GameState BankItems to UI BankItemData and group by tab
        val itemsByTab = gameState.bankItems
            .map { item ->
                BankItemData(
                    slot = item.slot,
                    itemId = item.itemId,
                    amount = item.amount,
                    name = "", // Item name would come from definitions
                    tab = item.tab,
                    placeholder = item.placeholder
                )
            }
            .groupBy { it.tab }

        // Update each tab in the bank state
        for (tab in 0..8) {
            bankState.setItems(tab, itemsByTab[tab] ?: emptyList())
        }
    }

    // Sync bank settings from GameState to BankState
    LaunchedEffect(gameState.bankSettings.noteMode, gameState.bankSettings.placeholders) {
        bankState.withdrawMode = if (gameState.bankSettings.noteMode) {
            WithdrawMode.NOTE
        } else {
            WithdrawMode.ITEM
        }
        bankState.showPlaceholders = gameState.bankSettings.placeholders
    }

    // Initialize demo bank items (only if server hasn't sent data yet)
    LaunchedEffect(Unit) {
        // Only use demo items if the bank is empty (no server data yet)
        if (gameState.bankItems.isEmpty()) {
            val demoItems = listOf(
                BankItemData(0, 995, 1_500_000, "Coins", 0),
                BankItemData(1, 1511, 500, "Logs", 0),
                BankItemData(2, 1521, 250, "Oak logs", 0),
                BankItemData(3, 440, 1000, "Iron ore", 0),
                BankItemData(4, 2351, 500, "Iron bar", 0),
                BankItemData(5, 1127, 1, "Rune platebody", 0),
                BankItemData(6, 1079, 1, "Rune platelegs", 0),
                BankItemData(7, 1163, 1, "Rune full helm", 0),
                BankItemData(8, 1201, 1, "Rune kiteshield", 0),
                BankItemData(9, 1333, 1, "Rune scimitar", 0),
                BankItemData(10, 385, 100, "Shark", 1),
                BankItemData(11, 3024, 50, "Super restore(4)", 1),
                BankItemData(12, 560, 5000, "Death rune", 2),
                BankItemData(13, 565, 2000, "Blood rune", 2),
                BankItemData(14, 892, 10000, "Rune arrow", 2),
            )
            bankState.setItems(0, demoItems.filter { it.tab == 0 })
            bankState.setItems(1, demoItems.filter { it.tab == 1 })
            bankState.setItems(2, demoItems.filter { it.tab == 2 })
        }
    }

    // Populate demo entities for testing context menus
    LaunchedEffect(gameState.position) {
        val playerPos = gameState.position
        canvasState.entities = listOf(
            // Other players
            GameEntity.Player(
                position = Position(playerPos.x + 3, playerPos.y + 2, playerPos.z),
                name = "Zezima",
                rights = PlayerRights.PLAYER,
                combatLevel = 126,
                isLocalPlayer = false
            ),
            GameEntity.Player(
                position = Position(playerPos.x - 2, playerPos.y + 4, playerPos.z),
                name = "Mod Mark",
                rights = PlayerRights.MODERATOR,
                combatLevel = 138,
                isLocalPlayer = false
            ),
            // NPCs
            GameEntity.Npc(
                position = Position(playerPos.x + 5, playerPos.y - 3, playerPos.z),
                name = "Guard",
                id = 1,
                combatLevel = 21
            ),
            GameEntity.Npc(
                position = Position(playerPos.x - 4, playerPos.y - 2, playerPos.z),
                name = "Hans",
                id = 2,
                combatLevel = null // Non-combat NPC
            ),
            GameEntity.Npc(
                position = Position(playerPos.x + 2, playerPos.y + 5, playerPos.z),
                name = "Man",
                id = 3,
                combatLevel = 2
            ),
            // Ground items
            GameEntity.GroundItem(
                position = Position(playerPos.x + 1, playerPos.y - 1, playerPos.z),
                name = "Coins",
                itemId = 995,
                amount = 10000
            ),
            GameEntity.GroundItem(
                position = Position(playerPos.x - 3, playerPos.y + 1, playerPos.z),
                name = "Bronze sword",
                itemId = 1277,
                amount = 1
            ),
            // Game objects
            GameEntity.GameObject(
                position = Position(playerPos.x + 6, playerPos.y, playerPos.z),
                name = "Bank booth",
                objectId = 1,
                type = ObjectType.SCENERY
            ),
            GameEntity.GameObject(
                position = Position(playerPos.x - 5, playerPos.y - 4, playerPos.z),
                name = "Oak tree",
                objectId = 2,
                type = ObjectType.SCENERY
            ),
            GameEntity.GameObject(
                position = Position(playerPos.x, playerPos.y + 6, playerPos.z),
                name = "Door",
                objectId = 3,
                type = ObjectType.WALL
            )
        )
    }

    // Handle tile clicks (walking)
    val handleTileClick: (CanvasClickEvent) -> Unit = { event ->
        onWalkTo(event.worldX, event.worldY)
        if (!event.isRightClick) {
            gameState.addMessage("Walking to (${event.worldX}, ${event.worldY})", MessageType.GAME)
        }
    }

    // Handle entity clicks (left-click primary action)
    val handleEntityClick: (EntityClickEvent) -> Unit = { event ->
        if (!event.isRightClick) {
            // Left click performs primary action
            when (val entity = event.entity) {
                is GameEntity.Player -> {
                    if (!entity.isLocalPlayer) {
                        gameState.addMessage("Following ${entity.name}...", MessageType.GAME)
                        onEntityInteraction(entity, "follow")
                    }
                }

                is GameEntity.Npc -> {
                    if (entity.combatLevel != null) {
                        gameState.addMessage("Attacking ${entity.name}...", MessageType.GAME)
                        onEntityInteraction(entity, "attack")
                    } else {
                        gameState.addMessage("Talking to ${entity.name}...", MessageType.GAME)
                        onEntityInteraction(entity, "talk")
                    }
                }

                is GameEntity.GroundItem -> {
                    gameState.addMessage("Taking ${entity.name}...", MessageType.GAME)
                    soundManager?.play(RSSound.ITEM_PICKUP)
                    onEntityInteraction(entity, "take")
                }

                is GameEntity.GameObject -> {
                    gameState.addMessage("Using ${entity.name}...", MessageType.GAME)
                    onEntityInteraction(entity, "use")
                }
            }
        }
    }

    // Handle entity actions from context menu
    val handleEntityAction: (GameEntity, String) -> Unit = { entity, action ->
        when (action) {
            "follow" -> gameState.addMessage("Following ${entity.name}...", MessageType.GAME)
            "trade" -> gameState.addMessage("Sending trade request to ${entity.name}...", MessageType.GAME)
            "challenge" -> gameState.addMessage("Challenging ${entity.name} to a duel...", MessageType.GAME)
            "report" -> gameState.addMessage("Opening report interface for ${entity.name}...", MessageType.GAME)
            "talk" -> gameState.addMessage("Talking to ${entity.name}...", MessageType.GAME)
            "attack" -> gameState.addMessage("Attacking ${entity.name}!", MessageType.GAME)
            "pickpocket" -> gameState.addMessage("Attempting to pickpocket ${entity.name}...", MessageType.GAME)
            "take" -> gameState.addMessage("Taking ${entity.name}...", MessageType.GAME)
            "examine" -> {
                val examineText = when (entity) {
                    is GameEntity.Player -> "A ${if (entity.rights != PlayerRights.PLAYER) "${entity.rights.name.lowercase()} " else ""}player."
                    is GameEntity.Npc -> "It's ${
                        entity.name.let {
                            if (it.first().lowercaseChar() in "aeiou") "an" else "a"
                        }
                    } ${entity.name}."

                    is GameEntity.GroundItem -> "A${if (entity.amount > 1) " stack of ${entity.amount}" else ""} ${entity.name}."
                    is GameEntity.GameObject -> "It's ${
                        entity.name.let {
                            if (it.first().lowercaseChar() in "aeiou") "an" else "a"
                        }
                    } ${entity.name}."
                }
                gameState.addMessage(examineText, MessageType.GAME)
            }

            "open", "close" -> gameState.addMessage(
                "${action.replaceFirstChar { it.uppercase() }}ing ${entity.name}...",
                MessageType.GAME
            )

            "climb" -> gameState.addMessage("Climbing ${entity.name}...", MessageType.GAME)
            "chop down" -> gameState.addMessage("You swing your axe at the ${entity.name}...", MessageType.GAME)
            "mine" -> gameState.addMessage("You swing your pickaxe at the ${entity.name}...", MessageType.GAME)
            "fish" -> gameState.addMessage("You attempt to catch a fish...", MessageType.GAME)
            "pray" -> gameState.addMessage("You pray at the ${entity.name}...", MessageType.GAME)
            "use" -> gameState.addMessage("Using ${entity.name}...", MessageType.GAME)
            "smelt" -> gameState.addMessage("Smelting at the ${entity.name}...", MessageType.GAME)
            "smith" -> gameState.addMessage("Smithing at the ${entity.name}...", MessageType.GAME)
            "cook" -> gameState.addMessage("Cooking on the ${entity.name}...", MessageType.GAME)
            "search" -> gameState.addMessage("Searching the ${entity.name}...", MessageType.GAME)
            "read" -> gameState.addMessage("Reading the ${entity.name}...", MessageType.GAME)
            else -> gameState.addMessage(
                "${action.replaceFirstChar { it.uppercase() }} ${entity.name}...",
                MessageType.GAME
            )
        }
        onEntityInteraction(entity, action)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // Main game viewport (takes most of the screen)
        GameCanvas(
            gameState = gameState,
            canvasState = canvasState,
            contextMenuState = contextMenuState,
            onTileClick = handleTileClick,
            onEntityClick = handleEntityClick,
            onEntityAction = handleEntityAction,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 244.dp, bottom = 168.dp) // Space for sidebar and chat
        )

        // === RIGHT SIDEBAR ===
        Column(
            modifier = Modifier
                .width(244.dp)
                .fillMaxHeight()
                .align(Alignment.TopEnd)
                .background(Color(0xFF3E3529))
        ) {
            // Minimap area with orbs
            MinimapWithOrbs(
                gameState = gameState,
                minimapState = minimapState,
                onMinimapClick = { x, y ->
                    // Set flag destination and trigger walk
                    minimapState.flagDestination = com.rustscape.client.game.Position(x, y, gameState.position.z)
                    onMinimapWalk(x, y)
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Tab bar (top row)
            TabRow(
                tabs = listOf(
                    SidebarTab.COMBAT, SidebarTab.SKILLS, SidebarTab.QUESTS,
                    SidebarTab.INVENTORY, SidebarTab.EQUIPMENT, SidebarTab.PRAYER, SidebarTab.MAGIC
                ),
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.fillMaxWidth()
            )

            // Main panel content
            RSPanel(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                when (selectedTab) {
                    SidebarTab.INVENTORY -> InventoryPanel(
                        inventory = gameState.inventory,
                        onItemSwap = { fromSlot, toSlot ->
                            onInventorySwap(fromSlot, toSlot)
                            inventoryCallbacks.onItemSwap(fromSlot, toSlot)
                        },
                        onItemDrop = { slot, itemId ->
                            inventoryCallbacks.onItemDrop(slot, itemId)
                            gameState.addMessage("Dropping item...", MessageType.GAME)
                        },
                        onItemUse = { slot, itemId ->
                            inventoryCallbacks.onItemUse(slot, itemId)
                        },
                        onItemExamine = { itemId ->
                            inventoryCallbacks.onItemExamine(itemId)
                        },
                        bankOpen = bankState.isOpen,
                        onBankDeposit = { slot, itemId, amount ->
                            bankCallbacks.onDeposit(slot, itemId, amount)
                        }
                    )

                    SidebarTab.SKILLS -> SkillsPanel(skills = gameState.skills.toList())
                    SidebarTab.EQUIPMENT -> EquipmentPanelEnhanced(
                        onSlotClick = onEquipmentClick
                    )

                    SidebarTab.COMBAT -> CombatPanel(gameState)
                    SidebarTab.PRAYER -> PrayerPanel()
                    SidebarTab.MAGIC -> MagicPanel()
                    SidebarTab.QUESTS -> QuestsPanel()
                    SidebarTab.SETTINGS -> SettingsPanel()
                    SidebarTab.FRIENDS -> FriendsPanel()
                    SidebarTab.MUSIC -> MusicPanel()
                    else -> InventoryPanel()
                }
            }

            // Tab bar (bottom row)
            TabRow(
                tabs = listOf(
                    SidebarTab.CLAN, SidebarTab.FRIENDS, SidebarTab.IGNORE,
                    SidebarTab.LOGOUT, SidebarTab.SETTINGS, SidebarTab.EMOTES, SidebarTab.MUSIC
                ),
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    if (tab == SidebarTab.LOGOUT) {
                        onLogout()
                    } else {
                        selectedTab = tab
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // === BOTTOM CHAT BOX ===
        ChatBox(
            messages = gameState.messages,
            selectedTab = selectedChatTab,
            onTabSelected = { selectedChatTab = it },
            onSendMessage = { message ->
                if (message.startsWith("::")) {
                    // Check for bank command
                    if (message.substring(2).equals("bank", ignoreCase = true)) {
                        bankState.open()
                    } else {
                        onSendCommand(message.substring(2))
                    }
                } else {
                    onSendChat(message)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .width(519.dp) // Classic RS chat width
                .height(168.dp)
        )

        // === BANK PANEL OVERLAY ===
        if (bankState.isOpen) {
            RSBankPanel(
                state = bankState,
                tabs = (0..8).map { BankTab(it) },
                onClose = {
                    bankState.close()
                    bankCallbacks.onClose()
                },
                onDeposit = { itemId, amount ->
                    // Find the inventory slot for this item
                    val slot = gameState.inventory.findSlot(itemId)
                    if (slot >= 0) {
                        bankCallbacks.onDeposit(slot, itemId, amount)
                    }
                    gameState.addMessage("Depositing $amount x item...", MessageType.GAME)
                },
                onDepositAll = {
                    bankCallbacks.onDepositAll()
                    gameState.addMessage("Depositing all items...", MessageType.GAME)
                },
                onDepositEquipment = {
                    bankCallbacks.onDepositEquipment()
                    gameState.addMessage("Depositing worn equipment...", MessageType.GAME)
                },
                onWithdraw = { itemId, slot, amount, noted ->
                    bankCallbacks.onWithdraw(slot, itemId, amount, noted)
                    val noteText = if (noted) " (noted)" else ""
                    gameState.addMessage("Withdrawing $amount x item$noteText...", MessageType.GAME)
                },
                onTabSelect = { tab ->
                    bankCallbacks.onTabSelect(tab)
                },
                onSearch = { query ->
                    bankCallbacks.onSearch(query)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Minimap area with HP, Prayer, and Run orbs
 */
@Composable
private fun MinimapWithOrbs(
    gameState: GameState,
    minimapState: MinimapState,
    onMinimapClick: (Int, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(180.dp)
            .background(Color(0xFF3E3529))
    ) {
        // Enhanced Minimap with terrain rendering (centered)
        RSMinimapEnhanced(
            playerPosition = gameState.position,
            minimapState = minimapState,
            onMinimapClick = onMinimapClick,
            minimapSize = 152.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        )

        // HP Orb (top left) with tooltip
        val hpCurrent = gameState.skills.getOrNull(SkillType.HITPOINTS.ordinal)?.currentLevel ?: 99
        val hpMax = gameState.skills.getOrNull(SkillType.HITPOINTS.ordinal)?.level ?: 99
        WithRSTooltip(
            tooltip = RSTooltips.forOrb("Hitpoints", hpCurrent, hpMax),
            position = TooltipPosition.RIGHT,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 28.dp)
        ) {
            RSOrbButton(
                value = hpCurrent,
                maxValue = hpMax,
                orbType = OrbType.HEALTH,
                onClick = { /* Toggle HP display */ },
                size = 48.dp
            )
        }

        // Prayer Orb (left, below HP) with tooltip
        val prayerCurrent = gameState.skills.getOrNull(SkillType.PRAYER.ordinal)?.currentLevel ?: 99
        val prayerMax = gameState.skills.getOrNull(SkillType.PRAYER.ordinal)?.level ?: 99
        WithRSTooltip(
            tooltip = RSTooltips.forOrb("Prayer", prayerCurrent, prayerMax, "Click to toggle quick prayers"),
            position = TooltipPosition.RIGHT,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 82.dp)
        ) {
            RSOrbButton(
                value = prayerCurrent,
                maxValue = prayerMax,
                orbType = OrbType.PRAYER,
                onClick = { /* Quick prayers */ },
                size = 48.dp
            )
        }

        // Run Energy Orb (left, below Prayer) with tooltip
        WithRSTooltip(
            tooltip = RSTooltips.forOrb(
                "Run Energy",
                gameState.runEnergy,
                100,
                if (gameState.isRunning) "Running (click to walk)" else "Walking (click to run)"
            ),
            position = TooltipPosition.RIGHT,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 132.dp)
        ) {
            RSOrbButton(
                value = gameState.runEnergy,
                maxValue = 100,
                orbType = OrbType.RUN_ENERGY,
                onClick = { gameState.isRunning = !gameState.isRunning },
                size = 42.dp
            )
        }

        // Compass direction text
        Text(
            text = "N",
            color = RSColors.TextYellow,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 28.dp)
        )

        // World map button (top right) with tooltip
        WithRSTooltip(
            text = "World Map",
            position = TooltipPosition.LEFT,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(RSColors.StoneMid, RoundedCornerShape(4.dp))
                    .border(1.dp, RSColors.StoneBorder, RoundedCornerShape(4.dp))
                    .clickable { /* Open world map */ },
                contentAlignment = Alignment.Center
            ) {
                Text("🗺", fontSize = 16.sp)
            }
        }
    }
}

/**
 * Tab row for sidebar navigation with sound effects
 */
@Composable
private fun TabRow(
    tabs: List<SidebarTab>,
    selectedTab: SidebarTab,
    onTabSelected: (SidebarTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val soundManager = LocalSoundManager.current

    Row(
        modifier = modifier
            .height(36.dp)
            .background(Color(0xFF2B2117))
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEach { tab ->
            WithRSTooltip(
                text = tab.tooltip,
                position = TooltipPosition.BOTTOM,
                showDelay = 300L
            ) {
                RSSoundTabButton(
                    iconContent = {
                        Text(
                            text = tab.icon,
                            fontSize = 14.sp,
                            color = if (tab == selectedTab) RSColors.TextYellow else RSColors.TextWhite
                        )
                    },
                    isSelected = tab == selectedTab,
                    onClick = {
                        // Play logout sound for logout tab
                        if (tab == SidebarTab.LOGOUT) {
                            soundManager?.play(RSSound.LOGOUT)
                        }
                        onTabSelected(tab)
                    },
                    size = 32.dp,
                    sound = if (tab == SidebarTab.LOGOUT) RSSound.LOGOUT else RSSound.TAB_SWITCH
                )
            }
        }
    }
}

/**
 * Classic RS Chat Box with tabs
 */
@Composable
private fun ChatBox(
    messages: List<ChatMessage>,
    selectedTab: ChatTab,
    onTabSelected: (ChatTab) -> Unit,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Filter messages based on selected tab
    val filteredMessages = remember(messages, selectedTab) {
        when (selectedTab) {
            ChatTab.ALL -> messages
            ChatTab.GAME -> messages.filter { it.type == MessageType.GAME || it.type == MessageType.SYSTEM }
            ChatTab.PUBLIC -> messages.filter { it.type == MessageType.PUBLIC }
            ChatTab.PRIVATE -> messages.filter { it.type == MessageType.PRIVATE }
            ChatTab.CLAN -> messages.filter { it.type == MessageType.CLAN }
            ChatTab.TRADE -> messages.filter { it.type == MessageType.TRADE }
        }
    }

    Column(
        modifier = modifier
            .background(RSColors.ChatBackground)
            .border(2.dp, RSColors.ChatBorder)
    ) {
        // Chat tabs
        RSChatTabs(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected
        )

        // Messages area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(filteredMessages) { message ->
                ChatMessageRow(message)
            }
        }

        // Input area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(Color(0xFF4A4038))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = RSColors.TextYellow,
                    unfocusedTextColor = RSColors.TextYellow,
                    cursorColor = RSColors.TextYellow,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    }
                ),
                placeholder = {
                    Text(
                        "Press Enter to chat...",
                        color = RSColors.TextWhite.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            )
        }
    }
}

/**
 * Individual chat message row
 */
@Composable
private fun ChatMessageRow(message: ChatMessage) {
    val textColor = when (message.type) {
        MessageType.SYSTEM -> RSColors.TextCyan
        MessageType.GAME -> RSColors.TextWhite
        MessageType.PUBLIC -> RSColors.TextYellow
        MessageType.PRIVATE -> RSColors.PrayerCyan
        MessageType.CLAN -> Color(0xFF7F0000) // Dark red for clan
        MessageType.TRADE -> Color(0xFF800080) // Purple for trade
        MessageType.COMMAND -> RSColors.TextOrange
    }

    // Check if message has chat effects (for PUBLIC messages)
    val shouldParseEffects = message.type == MessageType.PUBLIC || message.type == MessageType.CLAN

    Row(modifier = Modifier.fillMaxWidth()) {
        // Timestamp (optional)
        if (message.timestamp != null) {
            Text(
                text = "[${message.timestamp}] ",
                color = RSColors.TextWhite.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }

        // Sender name (if applicable)
        if (message.sender != null) {
            Text(
                text = "${message.sender}: ",
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Message text - use RSChatText for effect support on public/clan messages
        if (shouldParseEffects) {
            RSChatText(
                text = message.text,
                fontSize = 11.sp,
                parseEffects = true
            )
        } else {
            Text(
                text = message.text,
                color = textColor,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Inventory panel - 28 slot grid (4x7) with drag-and-drop support
 */
@Composable
private fun InventoryPanel(
    inventory: com.rustscape.client.game.Inventory = com.rustscape.client.game.Inventory(28),
    onItemSwap: (fromSlot: Int, toSlot: Int) -> Unit = { _, _ -> },
    onItemClick: (slot: Int) -> Unit = {},
    onItemRightClick: (slot: Int) -> Unit = {},
    onItemDrop: (slot: Int, itemId: Int) -> Unit = { _, _ -> },
    onItemUse: (slot: Int, itemId: Int) -> Unit = { _, _ -> },
    onItemExamine: (itemId: Int) -> Unit = {},
    bankOpen: Boolean = false,
    onBankDeposit: (slot: Int, itemId: Int, amount: Int) -> Unit = { _, _, _ -> }
) {
    val dragState = rememberInventoryDragState()
    val shiftClickSettings = LocalShiftClickSettings.current

    // Generate demo items for display
    val demoItems = remember {
        listOf(
            InventoryItemData(0, 995, 12345, "Coins", isStackable = true),
            InventoryItemData(1, 1265, 1, "Bronze pickaxe"),
            InventoryItemData(2, 1351, 1, "Bronze axe"),
            InventoryItemData(3, 315, 5, "Shrimps"),
            InventoryItemData(4, 333, 10, "Trout"),
            InventoryItemData(5, 2309, 1, "Bread"),
            InventoryItemData(8, 1734, 1, "Amulet of glory"),
            InventoryItemData(12, 1323, 1, "Iron scimitar"),
            InventoryItemData(15, 1153, 1, "Iron full helm"),
            InventoryItemData(20, 995, 1000000, "Coins", isStackable = true),
        )
    }

    CompositionLocalProvider(LocalInventoryDragState provides dragState) {
        RSInventoryPanel(
            inventory = inventory,
            items = demoItems,
            onItemClick = { slot, item ->
                onItemClick(slot)
            },
            onItemRightClick = { slot, item ->
                onItemRightClick(slot)
            },
            onItemShiftClick = { slot, item, action ->
                when (action) {
                    ShiftClickAction.DROP -> onItemDrop(slot, item.itemId)
                    ShiftClickAction.USE -> onItemUse(slot, item.itemId)
                    ShiftClickAction.DEPOSIT -> {
                        if (bankOpen) {
                            onBankDeposit(slot, item.itemId, item.amount)
                        }
                    }

                    else -> {}
                }
            },
            onItemSwap = onItemSwap,
            onItemDrop = { slot, item ->
                onItemDrop(slot, item.itemId)
            },
            onItemUse = { slot, item ->
                onItemUse(slot, item.itemId)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Skills panel - classic grid layout
 */
@Composable
private fun SkillsPanel(skills: List<SkillInfo>) {
    val skillOrder = listOf(
        SkillType.ATTACK, SkillType.HITPOINTS, SkillType.MINING,
        SkillType.STRENGTH, SkillType.AGILITY, SkillType.SMITHING,
        SkillType.DEFENCE, SkillType.HERBLORE, SkillType.FISHING,
        SkillType.RANGED, SkillType.THIEVING, SkillType.COOKING,
        SkillType.PRAYER, SkillType.CRAFTING, SkillType.FIREMAKING,
        SkillType.MAGIC, SkillType.FLETCHING, SkillType.WOODCUTTING,
        SkillType.RUNECRAFT, SkillType.SLAYER, SkillType.FARMING,
        SkillType.CONSTRUCTION, SkillType.HUNTER, SkillType.SUMMONING
    )

    val skillColors = mapOf(
        SkillType.ATTACK to Color(0xFF9B0000),
        SkillType.STRENGTH to Color(0xFF00A000),
        SkillType.DEFENCE to Color(0xFF6090B0),
        SkillType.HITPOINTS to Color(0xFFB00000),
        SkillType.RANGED to Color(0xFF00B000),
        SkillType.PRAYER to Color(0xFFB0B000),
        SkillType.MAGIC to Color(0xFF4040B0),
        SkillType.COOKING to Color(0xFF8B4513),
        SkillType.WOODCUTTING to Color(0xFF228B22),
        SkillType.FLETCHING to Color(0xFF006400),
        SkillType.FISHING to Color(0xFF4682B4),
        SkillType.FIREMAKING to Color(0xFFFF4500),
        SkillType.CRAFTING to Color(0xFF8B4513),
        SkillType.SMITHING to Color(0xFF696969),
        SkillType.MINING to Color(0xFF708090),
        SkillType.HERBLORE to Color(0xFF2E8B57),
        SkillType.AGILITY to Color(0xFF4169E1),
        SkillType.THIEVING to Color(0xFF800080),
        SkillType.SLAYER to Color(0xFF2F4F4F),
        SkillType.FARMING to Color(0xFF32CD32),
        SkillType.RUNECRAFT to Color(0xFFFFD700),
        SkillType.HUNTER to Color(0xFF8B4513),
        SkillType.CONSTRUCTION to Color(0xFFD2691E),
        SkillType.SUMMONING to Color(0xFF00CED1)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        skillOrder.chunked(3).forEach { rowSkills ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowSkills.forEach { skill ->
                    val skillInfo = skills.getOrNull(skill.ordinal) ?: SkillInfo(skill.ordinal, 1, 0)
                    val xpToNext: Long = if (skillInfo.level < 99) {
                        (Skill.getExperienceForLevel(skillInfo.level + 1) - skillInfo.experience.toInt()).toLong()
                    } else 0L

                    WithRSTooltip(
                        tooltip = RSTooltips.forSkill(
                            skillName = skill.name.lowercase().replaceFirstChar { it.uppercase() },
                            currentLevel = skillInfo.currentLevel,
                            maxLevel = skillInfo.level,
                            experience = skillInfo.experience,
                            experienceToNextLevel = xpToNext
                        ),
                        position = TooltipPosition.LEFT,
                        showDelay = 200L
                    ) {
                        RSSkillIcon(
                            level = skillInfo.level,
                            experience = skillInfo.experience,
                            skillColor = skillColors[skill] ?: Color.Gray,
                            onClick = { /* Show skill details */ }
                        )
                    }
                }
            }
        }

        // Total level at bottom
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(RSColors.PanelBackgroundDark),
            contentAlignment = Alignment.Center
        ) {
            val totalLevel = skills.sumOf { it.level }
            RSText(
                text = "Total Level: $totalLevel",
                color = RSColors.TextYellow,
                fontSize = 11,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Equipment panel - paper doll layout (legacy)
 */
@Composable
private fun EquipmentPanel() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Head slot
        RSInventorySlot { Text("🎩", fontSize = 12.sp) }

        // Cape, Amulet, Ammo row
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RSInventorySlot { Text("🧣", fontSize = 12.sp) }
            RSInventorySlot { Text("📿", fontSize = 12.sp) }
            RSInventorySlot { Text("➡", fontSize = 12.sp) }
        }

        // Weapon, Body, Shield row
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RSInventorySlot { Text("⚔", fontSize = 12.sp) }
            RSInventorySlot { Text("👕", fontSize = 12.sp) }
            RSInventorySlot { Text("🛡", fontSize = 12.sp) }
        }

        // Legs slot
        RSInventorySlot { Text("👖", fontSize = 12.sp) }

        // Gloves, Boots, Ring row
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RSInventorySlot { Text("🧤", fontSize = 12.sp) }
            RSInventorySlot { Text("👟", fontSize = 12.sp) }
            RSInventorySlot { Text("💍", fontSize = 12.sp) }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Equipment stats button
        RSStoneButton(
            text = "Stats",
            onClick = { /* Show equipment stats */ },
            width = 80.dp,
            height = 24.dp
        )
    }
}

/**
 * Enhanced equipment panel using RSEquipmentPanel component
 */
@Composable
private fun EquipmentPanelEnhanced(
    onSlotClick: (EquipmentSlotType) -> Unit = {},
    onSlotRightClick: (EquipmentSlotType) -> Unit = {}
) {
    // Demo equipment items
    val demoEquipment = remember {
        mapOf(
            EquipmentSlotType.HELMET to InventoryItemData(0, 1153, 1, "Iron full helm"),
            EquipmentSlotType.BODY to InventoryItemData(4, 1115, 1, "Iron platebody"),
            EquipmentSlotType.LEGS to InventoryItemData(7, 1067, 1, "Iron platelegs"),
            EquipmentSlotType.WEAPON to InventoryItemData(3, 1323, 1, "Iron scimitar"),
            EquipmentSlotType.SHIELD to InventoryItemData(5, 1191, 1, "Iron kiteshield"),
            EquipmentSlotType.BOOTS to InventoryItemData(10, 4121, 1, "Iron boots"),
            EquipmentSlotType.GLOVES to InventoryItemData(9, 2902, 1, "Leather gloves"),
            EquipmentSlotType.AMULET to InventoryItemData(2, 1704, 1, "Amulet of glory"),
            EquipmentSlotType.CAPE to InventoryItemData(1, 1019, 1, "Black cape"),
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RSEquipmentPanel(
            equipment = demoEquipment,
            onSlotClick = onSlotClick,
            onSlotRightClick = onSlotRightClick,
            modifier = Modifier.weight(1f)
        )

        // Equipment stats button
        RSStoneButton(
            text = "Stats",
            onClick = { /* Show equipment stats */ },
            width = 80.dp,
            height = 24.dp
        )

        Spacer(modifier = Modifier.height(4.dp))
    }
}

/**
 * Combat options panel
 */
@Composable
private fun CombatPanel(gameState: GameState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RSText(
            text = "Combat Level: ${gameState.getCombatLevel()}",
            color = RSColors.TextYellow,
            fontSize = 14,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Attack styles
        val attackStyles = listOf("Accurate", "Aggressive", "Defensive", "Controlled")
        var selectedStyle by remember { mutableStateOf(0) }

        attackStyles.forEachIndexed { index, style ->
            RSStoneButton(
                text = style,
                onClick = { selectedStyle = index },
                enabled = true,
                width = 140.dp,
                height = 28.dp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Auto retaliate toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { /* Toggle auto retaliate */ }
        ) {
            Checkbox(
                checked = true,
                onCheckedChange = { /* Toggle */ },
                colors = CheckboxDefaults.colors(
                    checkedColor = RSColors.GoldMid,
                    uncheckedColor = RSColors.StoneMid
                )
            )
            RSText(
                text = "Auto Retaliate",
                color = RSColors.TextWhite,
                fontSize = 12
            )
        }
    }
}

/**
 * Prayer panel placeholder
 */
@Composable
private fun PrayerPanel() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        RSText(
            text = "Prayer Panel\n(Coming Soon)",
            color = RSColors.TextYellow,
            fontSize = 14
        )
    }
}

/**
 * Magic spellbook panel placeholder
 */
@Composable
private fun MagicPanel() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        RSText(
            text = "Magic Spellbook\n(Coming Soon)",
            color = RSColors.TextYellow,
            fontSize = 14
        )
    }
}

/**
 * Quest list panel placeholder
 */
@Composable
private fun QuestsPanel() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        RSText(
            text = "Quest List",
            color = RSColors.TextOrange,
            fontSize = 14,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Sample quests
        val quests = listOf(
            "Cook's Assistant" to true,
            "Dragon Slayer" to false,
            "Monkey Madness" to false,
            "Recipe for Disaster" to false
        )

        quests.forEach { (quest, completed) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (completed) "✓" else "•",
                    color = if (completed) RSColors.TextGreen else RSColors.TextRed,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = quest,
                    color = if (completed) RSColors.TextGreen else RSColors.TextWhite,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Settings panel with sound controls and other options
 */
@Composable
private fun SettingsPanel() {
    val soundManager = LocalSoundManager.current
    var settings by remember { mutableStateOf(soundManager?.settings ?: SoundSettings()) }

    // Update sound manager when settings change
    LaunchedEffect(settings) {
        soundManager?.updateSettings(settings)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RSText(
            text = "Settings",
            color = RSColors.TextOrange,
            fontSize = 14,
            fontWeight = FontWeight.Bold
        )

        // Sound enabled toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    settings = settings.copy(soundEnabled = !settings.soundEnabled)
                    if (settings.soundEnabled) {
                        soundManager?.play(RSSound.CHECKBOX_CHECK)
                    }
                }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sound Effects",
                color = RSColors.TextPrimary,
                fontSize = 12.sp
            )
            Text(
                text = if (settings.soundEnabled) "✓" else "✗",
                color = if (settings.soundEnabled) RSColors.TextGreen else RSColors.TextRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Music enabled toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    settings = settings.copy(musicEnabled = !settings.musicEnabled)
                    soundManager?.play(RSSound.CHECKBOX_CHECK)
                }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Music",
                color = RSColors.TextPrimary,
                fontSize = 12.sp
            )
            Text(
                text = if (settings.musicEnabled) "✓" else "✗",
                color = if (settings.musicEnabled) RSColors.TextGreen else RSColors.TextRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Master volume
        Text(
            text = "Master Volume: ${(settings.masterVolume * 100).toInt()}%",
            color = RSColors.TextWhite,
            fontSize = 11.sp
        )
        Slider(
            value = settings.masterVolume,
            onValueChange = { settings = settings.copy(masterVolume = it) },
            onValueChangeFinished = { soundManager?.play(RSSound.BUTTON_CLICK, 0.5f) },
            modifier = Modifier.fillMaxWidth().height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = RSColors.GoldMid,
                activeTrackColor = RSColors.GoldDark,
                inactiveTrackColor = RSColors.StoneDark
            )
        )

        // Interface volume
        Text(
            text = "Interface: ${(settings.interfaceVolume * 100).toInt()}%",
            color = if (settings.soundEnabled) RSColors.TextWhite else RSColors.TextWhite.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
        Slider(
            value = settings.interfaceVolume,
            onValueChange = { settings = settings.copy(interfaceVolume = it) },
            onValueChangeFinished = { soundManager?.play(RSSound.BUTTON_CLICK, 0.5f) },
            enabled = settings.soundEnabled,
            modifier = Modifier.fillMaxWidth().height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = RSColors.GoldMid,
                activeTrackColor = RSColors.GoldDark,
                inactiveTrackColor = RSColors.StoneDark,
                disabledThumbColor = RSColors.StoneMid,
                disabledActiveTrackColor = RSColors.StoneDark
            )
        )

        // Test button
        Spacer(modifier = Modifier.weight(1f))
        RSStoneButton(
            text = "Test Sound",
            onClick = { soundManager?.play(RSSound.LEVEL_UP) },
            width = 200.dp,
            height = 28.dp
        )
    }
}

/**
 * Friends list panel
 */
@Composable
private fun FriendsPanel() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        RSText(
            text = "Friends List",
            color = RSColors.TextOrange,
            fontSize = 14,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Sample friends
        val friends = listOf(
            Triple("Zezima", true, 302),
            Triple("Durial321", false, 0),
            Triple("Woox", true, 330),
            Triple("B0aty", true, 318),
            Triple("Lynx Titan", false, 0)
        )

        friends.forEach { (name, online, world) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Online indicator orb
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (online) RSColors.TextGreen else RSColors.TextRed)
                    )
                    Text(
                        text = name,
                        color = if (online) RSColors.TextGreen else RSColors.TextRed,
                        fontSize = 11.sp
                    )
                }
                if (online) {
                    Text(
                        text = "World $world",
                        color = RSColors.TextCyan,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Add friend button
        RSStoneButton(
            text = "Add Friend",
            onClick = { /* Add friend */ },
            width = 200.dp,
            height = 24.dp
        )
    }
}

/**
 * Music player panel
 */
@Composable
private fun MusicPanel() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        RSText(
            text = "Music Player",
            color = RSColors.TextOrange,
            fontSize = 14,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Now playing
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(RSColors.PanelBackgroundDark, RoundedCornerShape(4.dp))
                .border(1.dp, RSColors.StoneBorder, RoundedCornerShape(4.dp))
                .padding(8.dp)
        ) {
            Column {
                Text(
                    text = "Now Playing:",
                    color = RSColors.TextMuted,
                    fontSize = 10.sp
                )
                Text(
                    text = "Harmony",
                    color = RSColors.TextGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Track list
        val tracks = listOf(
            "Harmony" to true,
            "Autumn Voyage",
            "Sea Shanty 2",
            "Newbie Melody",
            "Flute Salad",
            "Garden"
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(tracks.size) { index ->
                val track = tracks[index]
                val (name, unlocked) = if (track is Pair<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    track as Pair<String, Boolean>
                } else {
                    (track as String) to true
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = unlocked) { /* Play track */ }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "♫",
                        color = if (unlocked) RSColors.TextGreen else RSColors.TextMuted,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = name,
                        color = if (unlocked) RSColors.TextWhite else RSColors.TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
