# Rustscape Project TODO

## Overview

This document tracks progress on both the **Kotlin Multiplatform Client** and the **Rust Server**, along with asset organization recommendations.

**Last Updated:** Current session - All major game systems implemented!

---

# Server (Rust)

## Current Status Summary

The Rust server has comprehensive game systems implemented:
- ✅ Full bank system with tabs, placeholders, and operations
- ✅ Inventory system with stackable/non-stackable support
- ✅ Bank-Inventory integration (deposit/withdraw)
- ✅ Item definitions with equipment slots and properties (~80 common items)
- ✅ Equipment system with bonuses and requirements
- ✅ Ground items with visibility lifecycle
- ✅ Player persistence to PostgreSQL (inventory, bank, equipment)
- ✅ Periodic auto-save (configurable, default 5 minutes)
- ✅ WebSocket support for browser clients
- ✅ Cache loading system for sprites
- ✅ Bank packet protocol (opcodes 248-251)

---

## Server TODOs

### ✅ COMPLETE

#### 1. Inventory System ✅
**Status:** Implemented  
**Location:** `src/server/src/game/inventory.rs`

Implemented:
- [x] `InventoryItem` struct with item_id and amount
- [x] `Inventory` struct with 28 slots
- [x] Add/remove operations for stackable and non-stackable items
- [x] Slot swap and insert operations
- [x] Compact (move items to front) operation
- [x] Stack overflow protection (max 2,147,483,647)
- [x] Persistence format conversion
- [x] Integration with `Player` struct
- [x] Full test coverage

#### 2. Inventory-Bank Integration ✅
**Status:** Implemented  
**Location:** `src/server/src/protocol/game.rs`

Implemented:
- [x] Bank withdraw adds items to inventory
- [x] Bank deposit removes items from inventory
- [x] Deposit all inventory items
- [x] Item drop removes from inventory
- [x] Inventory swap (slot reordering)
- [x] Inventory update packets sent to client
- [x] Proper validation (slot bounds, item ID matching)
- [x] Error handling and user messages

#### 3. Item Definitions ✅
**Status:** Implemented  
**Location:** `src/server/src/game/item.rs`

Implemented:
- [x] `ItemDefinition` struct with all RS properties
- [x] `EquipmentSlot` enum (Head, Cape, Amulet, Weapon, Body, Shield, Legs, Hands, Feet, Ring, Ammo)
- [x] `WeaponType` enum for combat calculations
- [x] `ItemDefinitionStore` global singleton with lazy initialization
- [x] Stackability checks used in bank/inventory operations
- [x] Equipment slot queries
- [x] Two-handed weapon detection
- [x] High/low alchemy value calculations
- [x] Note/unnote item ID mappings
- [x] ~80 common items pre-loaded (weapons, armor, runes, food, tools, etc.)
- [x] Builder pattern for item creation
- [x] Search by name functionality
- [x] Common item ID constants (`item_ids` module)

#### 4. Bank Update Packets ✅
**Status:** Implemented  
**Location:** `src/server/src/protocol/game.rs`

Implemented:
- [x] `build_bank_open` - Send bank interface open packet with capacity
- [x] `build_bank_settings` - Send note mode, placeholders, withdraw-X settings
- [x] `build_bank_tab_info` - Send tab sizes for all 9 tabs
- [x] `build_bank_full_update` - Send all bank items with tab/slot/amount/placeholder info
- [x] `build_bank_slot_update` - Send single slot updates after operations
- [x] `build_bank_slot_clear` - Clear a bank slot
- [x] Bank open command (`::bank`) now sends full bank data to client
- [x] Withdraw operations send bank slot updates
- [x] Deposit operations send bank slot updates  
- [x] Move item operations send affected slot updates
- [x] Deposit-all sends full bank update
- [x] Settings changes (note mode, withdraw mode) send settings packet
- [x] New opcodes: `BankOpen` (248), `BankUpdate` (249), `BankSettings` (250), `BankTabInfo` (251)
- [x] Unit tests for all bank packet builders

#### 5. Equipment System ✅
**Status:** Implemented  
**Location:** `src/server/src/game/equipment.rs`

Implemented:
- [x] `Equipment` struct with 14 slots (head, cape, amulet, weapon, body, shield, legs, hands, feet, ring, ammo)
- [x] `EquipmentItem` struct with item_id and amount
- [x] Equip item handler (moves from inventory to equipment)
- [x] Unequip item handler (moves from equipment to inventory)
- [x] Two-handed weapon handling (auto-unequip shield when equipping 2H weapon)
- [x] Shield equip handling (auto-unequip 2H weapon when equipping shield)
- [x] Equipment requirement validation (skill level checks)
- [x] Equipment bonuses calculation (cached, 14 bonus types)
- [x] Attack/defence bonus getters by style (stab, slash, crush, magic, ranged)
- [x] Strength, ranged strength, magic damage, prayer bonus getters
- [x] Attack speed calculation from weapon
- [x] Ammo consumption support (`remove_amount`)
- [x] Equipment weight calculation
- [x] Persistence format conversion
- [x] Equipment update packets (`build_equipment_full_update`, `build_equipment_slot_update`)
- [x] Integration with Player struct
- [x] Test commands: `::item`, `::equipment`, `::equipstats`, `::clearinv`
- [x] Full test coverage (26 tests)

#### 6. Ground Items ✅
**Status:** Implemented  
**Location:** `src/server/src/game/ground_item.rs`

Implemented:
- [x] `GroundItem` struct with id, item_id, amount, location, owner, spawn_tick, visibility
- [x] `GroundItemManager` for tracking all ground items in the world
- [x] Visibility lifecycle: Private → Public → Despawn (configurable tick timers)
- [x] `ItemVisibility` enum: Private, Public, Spawned
- [x] Region-based item indexing for efficient spatial queries
- [x] Ground item spawning when dropping items (`handle_item_drop` updated)
- [x] Permanent/respawning item support for world spawns
- [x] Ground item pickup with visibility checks
- [x] Tick processing for visibility transitions and despawns
- [x] `GroundItemEvent` system for spawn/remove/became-public events
- [x] `build_ground_item_spawn` packet builder (local and full coordinate versions)
- [x] `build_ground_item_remove` packet builder (local and full coordinate versions)
- [x] Statistics tracking (`GroundItemStats`)
- [x] Test commands: `::drop`, `::pickup`
- [x] Full test coverage (23 tests)

#### 7. Persistence Improvements ✅
**Status:** Implemented  
**Priority:** Medium  
**Location:** `src/server/src/game/player.rs`, `src/server/src/game/persistence.rs`, `src/server/src/game/world.rs`

Implemented:
- [x] Save inventory to database
- [x] Load inventory from database
- [x] Save bank to database (flattened tab structure)
- [x] Load bank from database (items loaded to tab 0)
- [x] Save equipment to database (14 slots)
- [x] Load equipment from database
- [x] `Player::to_player_data()` converts all containers
- [x] `Player::from_player_data()` loads all containers
- [x] `Bank::to_persistence_format()` and `Bank::from_persistence_format()` helpers
- [x] Full roundtrip tests for all containers (11 new tests)
- [x] Periodic auto-save every 5 minutes (configurable via `autosave_interval_secs`)
- [x] Auto-save on server shutdown (final save before exit)
- [x] New players created during autosave get full state saved
- [x] Configurable autosave interval in `ServerConfig` (default: 300 seconds)
- [x] Detailed logging of save statistics (saved/created/failed counts, elapsed time)

---

### TODO - Server

#### 8. Full Item Definitions from Cache
**Status:** TODO  
**Priority:** Medium

Needed:
- [ ] Parse item definitions from RS cache files (index 2, archive 10)
- [ ] Load all ~22,000 item definitions at startup
- [ ] Parse item options (inventory actions, ground actions)
- [ ] Parse model and sprite references
- [ ] Map noted items to their base items
- [ ] Cache item definition lookup for performance

#### 9. NPC System
**Status:** TODO  
**Priority:** High

Needed:
- [ ] `NpcDefinition` struct with combat stats, options, animations
- [ ] `Npc` entity with position, health, combat state
- [ ] `NpcManager` for world NPC tracking
- [ ] NPC spawn system with respawn timers
- [ ] NPC movement (patrol routes, wander areas)
- [ ] NPC interaction handlers (talk, attack, pickpocket)
- [ ] NPC combat AI (aggression, target selection)
- [ ] NPC update packets for client sync

#### 10. Combat System
**Status:** TODO  
**Priority:** High

Needed:
- [ ] Combat formulas (accuracy, damage, defence)
- [ ] Attack styles (accurate, aggressive, defensive, controlled)
- [ ] Special attacks
- [ ] Prayer effects on combat
- [ ] Hitpoints and death handling
- [ ] Combat XP rewards
- [ ] Multi-combat zones

#### 11. Skills & XP System
**Status:** Partial (skills exist, XP gain not implemented)  
**Priority:** Medium

Needed:
- [ ] XP gain from actions (woodcutting, mining, combat, etc.)
- [ ] Level up notifications and effects
- [ ] Skill-gated content checks
- [ ] Skill cape requirements

#### 12. Object/World Interaction
**Status:** TODO  
**Priority:** Medium

Needed:
- [ ] Object definitions from cache
- [ ] Object spawn/despawn system
- [ ] Object interaction handlers (doors, ladders, banks, etc.)
- [ ] Resource gathering (trees, rocks, fishing spots)
- [ ] Object state changes (depleted trees, opened doors)

---

# Client (Kotlin Multiplatform)

## Current Status Summary

The KMP client has comprehensive UI and networking:
- ✅ Full sprite atlas system (`RSAtlas.kt`)
- ✅ Font/text styling system (`RSFonts.kt`)
- ✅ Context menu actions wired to server packets
- ✅ WebSoundManager with synthesized RS-style sounds
- ✅ Pre-extracted sprites (~3,800+ PNG files)
- ✅ Bank UI with full server packet integration
- ✅ Inventory with drag-and-drop
- ✅ Equipment screen
- ✅ Minimap with terrain data

---

## Client TODOs

### ✅ COMPLETE

#### 1. Load Real Sprites from Assets ✅
**Location:** `assets/rendering/sprites/*.png`

Implemented:
- [x] `RSSpriteLoader.kt` - Common sprite loading infrastructure
- [x] `WebSpriteLoader.kt` - WASM platform loader (HTTP fetch)
- [x] `DesktopSpriteLoader.kt` - Desktop platform loader (filesystem)
- [x] Preloading of common sprites (skill icons, UI elements)

#### 2. Add Audio Unlock User Gesture Flow ✅
**Location:** `WebSoundManager.kt`, `App.kt`, `AudioUnlockOverlay.kt`

#### 3. Integrate Real Pixel Font ✅
**Location:** `composeApp/src/commonMain/composeResources/font/`, `RSFonts.kt`

#### 4. Minimap with Real Map Data ✅
**Location:** `RSMinimap.kt`, `GameScreen.kt`, `MapTerrainData.kt`

#### 5. Inventory Drag-and-Drop ✅
**Location:** `RSInventory.kt`, `GameScreen.kt`

#### 6. Equipment Screen with Real Items ✅
**Location:** `RSInventory.kt`, `GameScreen.kt`

#### 7. Item Tooltips ✅
**Location:** `RSInventory.kt`, `RSTooltip.kt`

#### 8. Bank UI ✅
**Status:** Implemented  
**Location:** `composeApp/.../RSBank.kt`, `shared/.../GameState.kt`, `shared/.../GameClient.kt`

Implemented:
- [x] Bank interface component (`RSBankPanel`)
- [x] Tab system (9 tabs with selection)
- [x] Search/filter functionality
- [x] Deposit/withdraw quantities (1, 5, 10, All, X)
- [x] Placeholder slots visualization
- [x] Note conversion toggle
- [x] Bank state management (`BankState` class)
- [x] Server packet opcodes defined (248-251 in `Packets.kt`)
- [x] Packet handlers in `GameClient` (handleBankOpen, handleBankUpdate, handleBankSettings, handleBankTabInfo)
- [x] `GameState` bank fields and methods (openBank, closeBank, updateBankSettings, setBankItems, updateBankSlot)
- [x] `GameStateListener` bank callbacks
- [x] GameScreen integration - syncs GameState bank data to UI BankState
- [x] Demo items for testing when server data not available

---

### TODO - Client

#### 9. Equipment Packet Integration
**Status:** TODO  
**Priority:** High

Needed:
- [ ] Handle equipment update packets from server
- [ ] Sync equipment state to UI
- [ ] Equipment slot click sends equip/unequip packets
- [ ] Show equipment bonuses panel

#### 10. Ground Item Rendering
**Status:** TODO  
**Priority:** Medium

Needed:
- [ ] Handle ground item spawn/remove packets
- [ ] Render ground items on game canvas
- [ ] Ground item right-click menu (Take, Examine)
- [ ] Item pile stacking display

#### 11. NPC Rendering
**Status:** TODO  
**Priority:** High

Needed:
- [ ] Handle NPC update packets
- [ ] Render NPCs on game canvas
- [ ] NPC right-click menu (Attack, Talk-to, etc.)
- [ ] NPC health bars
- [ ] NPC animations

#### 12. Lazy-Load UI Modules
**Priority:** Low

- [ ] Split UI into loadable chunks
- [ ] Load bank UI only when opened
- [ ] Load settings panel on demand
- [ ] Reduce initial WASM bundle size

#### 13. Chat System Enhancements
**Priority:** Low

- [ ] Chat effects parsing (wave:, glow:, flash:, etc.)
- [ ] Autocomplete for commands
- [ ] Chat history (up/down arrows)
- [ ] Clickable player names

---

## Technical Debt

- [ ] Replace placeholder player index hashing with real server indices
- [ ] Add proper error handling for network disconnects
- [ ] Implement reconnection logic
- [ ] Add loading states for async operations
- [ ] Unit tests for packet encoding/decoding

---

# Test Coverage Summary

## Server Tests
- **Total:** 357+ unit tests passing
- **Integration:** 19 integration tests passing
- **Key modules tested:**
  - Inventory: Full coverage
  - Bank: Full coverage including persistence roundtrip
  - Equipment: 26 tests
  - Ground Items: 23 tests
  - Player persistence: 11 roundtrip tests
  - Packet builders: All tested

## Client Tests
- Kotlin compilation verified for WASM and Desktop targets
- Manual testing via browser client

---

# Asset Organization

## Current Structure

```
assets/
├── rendering/
│   ├── README.md
│   └── sprites/           # ~3,800 pre-extracted PNG sprites
│       ├── 0.png
│       ├── 1.png
│       ├── ...
│       └── 3796.png
└── *.png                   # Login screen backgrounds
```

## Recommended Structure

For a complete game, consider organizing assets like this:

```
assets/
├── cache/                     # Raw RS cache files (if distributing)
│   ├── main_file_cache.dat2
│   └── main_file_cache.idx*
│
├── definitions/               # Extracted definition data (JSON)
│   ├── items.json            # All item definitions
│   ├── npcs.json             # All NPC definitions
│   ├── objects.json          # All object definitions
│   └── animations.json       # Animation definitions
│
├── maps/                      # Map data
│   ├── regions/              # Region terrain data
│   │   ├── 12850.json        # Lumbridge region
│   │   └── ...
│   ├── collision/            # Collision maps
│   └── xteas/                # Map decryption keys
│       └── keys.json
│
├── models/                    # 3D models (if doing 3D rendering)
│   ├── items/
│   ├── npcs/
│   ├── objects/
│   └── players/
│
├── music/                     # Background music
│   ├── tracks/               # Individual music tracks
│   │   ├── 1.ogg            # Or .mp3, .wav
│   │   └── ...
│   └── regions.json          # Region → track mapping
│
├── rendering/
│   ├── sprites/              # UI sprites (current location)
│   ├── textures/             # Ground/wall textures
│   └── fonts/                # Bitmap fonts
│
├── sounds/                    # Sound effects
│   ├── effects/              # Action sounds
│   └── ambient/              # Environmental sounds
│
└── data/                      # Game configuration
    ├── shops.json            # Shop inventories
    ├── spawns.json           # NPC/item spawn locations
    └── drops.json            # NPC drop tables
```

## Asset Loading Strategy

### For WASM/Browser Client:
1. **Sprites**: Serve from `/sprites/` via HTTP (current approach works well)
2. **Definitions**: Bundle critical ones, lazy-load others via API
3. **Music**: Stream on-demand, don't preload
4. **Maps**: Load regions as player moves

### For Desktop Client:
1. **All assets**: Load from local filesystem
2. **Cache validation**: Check for updates on startup

### For Server:
1. **Definitions**: Load at startup into memory (items, npcs, objects)
2. **Maps**: Load on-demand as players enter regions
3. **No sprites/music**: Server doesn't need rendering assets

---

# File Reference

## Server Files

| File | Purpose |
|------|---------|
| `src/server/src/game/inventory.rs` | Inventory system (28 slots) |
| `src/server/src/game/bank.rs` | Bank system (9 tabs, 800 slots) |
| `src/server/src/game/item.rs` | Item definitions and properties |
| `src/server/src/game/player.rs` | Player entity and management |
| `src/server/src/game/equipment.rs` | Equipment system (14 slots, bonuses) |
| `src/server/src/game/ground_item.rs` | Ground items (visibility, lifecycle) |
| `src/server/src/game/persistence.rs` | Database save/load |
| `src/server/src/game/world.rs` | Game world, tick loop, autosave |
| `src/server/src/protocol/game.rs` | Game packet handlers |
| `src/server/src/cache/mod.rs` | Cache loading system |
| `src/server/src/config.rs` | Server configuration |
| `src/server/src/state.rs` | Application state |

## Client Files

| File | Purpose |
|------|---------|
| `RSAtlas.kt` | Sprite atlas system |
| `RSSpriteLoader.kt` | Sprite loading infrastructure |
| `RSFonts.kt` | Text styles, colors, fonts |
| `GameClient.kt` | Network protocol, packet handlers |
| `GameState.kt` | Client game state (skills, inventory, bank) |
| `Packets.kt` | Packet opcodes and sizes |
| `RSMinimap.kt` | Minimap with terrain |
| `RSInventory.kt` | Inventory/equipment UI |
| `RSBank.kt` | Bank interface UI |
| `RSTooltip.kt` | RS-style tooltips |
| `GameScreen.kt` | Main game viewport |

---

# Quick Start Commands

```bash
# Server
cd src/server
cargo run                    # Run server
cargo test                   # Run all tests (357+)
cargo test game::inventory   # Run inventory tests
cargo test game::bank        # Run bank tests
cargo test game::player      # Run player/persistence tests

# KMP Client (development)
cd src/clients
./gradlew composeApp:wasmJsBrowserDevelopmentRun

# Build production WASM
./gradlew composeApp:wasmJsBrowserProductionWebpack

# Run desktop client
./gradlew composeApp:run

# Compile check (fast)
./gradlew composeApp:compileKotlinWasmJs
```

---

# Priority Order - Next Steps

## Completed ✅
1. ~~**Bank Update Packets** (server)~~ ✅
2. ~~**Equipment System** (server)~~ ✅
3. ~~**Ground Items** (server)~~ ✅
4. ~~**Persistence** (server)~~ ✅ - Save/load inventory, bank, equipment
5. ~~**Periodic Auto-save** (server)~~ ✅ - Timer-based save every 5 minutes
6. ~~**Bank UI** (client)~~ ✅ - Full bank interface with server packet integration

## Next Up
7. **NPC System** (server) - Essential for gameplay
8. **Combat System** (server) - Core game mechanic
9. **Full Item Definitions** (server) - Complete item database (~22k items)
10. **Equipment Packet Integration** (client) - Sync equipment UI with server
11. **NPC Rendering** (client) - Display NPCs in game world
12. **Ground Item Rendering** (client) - Display dropped items

## Lower Priority
13. **Skills & XP System** (server)
14. **Object/World Interaction** (server)
15. **Lazy-Load UI Modules** (client)
16. **Chat System Enhancements** (client)

---

# Architecture Notes

## Packet Flow (Bank Example)
```
Server                              Client
  │                                    │
  │ ←── BANK_OPEN request ───────────  │
  │                                    │
  │ ──── BankOpen (248) ────────────→  │  GameClient.handleBankOpen()
  │ ──── BankSettings (250) ────────→  │  GameClient.handleBankSettings()
  │ ──── BankTabInfo (251) ─────────→  │  GameClient.handleBankTabInfo()
  │ ──── BankUpdate (249) ──────────→  │  GameClient.handleBankUpdate()
  │                                    │
  │                                    │  GameState updates → BankState syncs
  │                                    │  RSBankPanel renders
  │                                    │
  │ ←── BANK_WITHDRAW ──────────────   │
  │ ──── BankUpdate (slot) ─────────→  │
  │ ──── InventoryUpdate ───────────→  │
```

## Persistence Flow
```
Player Action → Container Modified → Tick Counter
                                          │
                                          ↓
                              (Every 500 ticks / 5 min)
                                          │
                                          ↓
                              autosave_players()
                                          │
                    ┌─────────────────────┼─────────────────────┐
                    ↓                     ↓                     ↓
            Player.to_player_data()  (for each online player)
                    │
                    ↓
            PlayerPersistence.save()
                    │
        ┌───────────┼───────────┐
        ↓           ↓           ↓
    Inventory    Bank      Equipment
    (28 slots)  (496 slots) (14 slots)
        │           │           │
        └───────────┴───────────┘
                    │
                    ↓
              PostgreSQL
```
