//! Game protocol handler
//!
//! Handles in-game packet processing after login. This includes:
//! - Player movement and actions
//! - Chat and commands
//! - Interface interactions
//! - Combat and skills
//! - World interactions (NPCs, objects, items)
//!
//! All in-game packets are encrypted with ISAAC cipher.

use std::sync::Arc;

use tracing::{debug, info, trace, warn};

use crate::crypto::IsaacPair;
use crate::error::Result;
use crate::game::bank::BankError;
use crate::game::equipment::{Equipment, EquipmentError, EQUIPMENT_SLOT_COUNT};
use crate::game::inventory::{InventoryError, INVENTORY_SIZE};
use crate::game::item::{get_equipment_slot, is_equippable, is_stackable};
use crate::game::player::{Location, Player};
use crate::net::buffer::PacketBuffer;

/// Incoming packet sizes (0 = variable byte, -1 = variable short, >0 = fixed)
/// This is a subset of common packets for revision 530
pub const INCOMING_PACKET_SIZES: [i16; 256] = {
    let mut sizes = [-3i16; 256]; // -3 = unknown/unhandled

    // Common incoming packets (client -> server)
    sizes[0] = 0; // Keep-alive/ping
    sizes[3] = 1; // Window focus change
    sizes[4] = -1; // Chat message
    sizes[14] = 8; // Walk to position
    sizes[17] = 2; // NPC examine
    sizes[21] = 2; // Item examine
    sizes[39] = 6; // Object action 1
    sizes[41] = 4; // Item equip
    sizes[42] = 4; // Item unequip
    sizes[43] = 11; // Bank withdraw
    sizes[52] = -1; // Command
    sizes[55] = 1; // Bank tab select
    sizes[77] = 0; // Map region loaded
    sizes[86] = 4; // Mouse click
    sizes[98] = 8; // Walk here
    sizes[115] = 1; // Bank withdraw mode
    sizes[116] = -1; // Bank search
    sizes[117] = 1; // Bank note mode
    sizes[121] = -1; // Mouse movement
    sizes[129] = 0; // Bank deposit all
    sizes[145] = 4; // Item drop
    sizes[150] = 6; // Item action 1
    sizes[164] = 2; // Button click
    sizes[185] = 0; // Bank close
    sizes[195] = 0; // Bank deposit equipment
    sizes[210] = 0; // Close interface
    sizes[214] = 5; // Bank move item
    sizes[233] = 8; // Bank deposit
    sizes[236] = 6; // Ground item action
    sizes[243] = 4; // Inventory swap

    sizes
};

/// Outgoing packet opcodes (server -> client)
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum OutgoingOpcode {
    /// Player update
    PlayerUpdate = 81,
    /// NPC update
    NpcUpdate = 65,
    /// Map region update
    MapRegion = 73,
    /// Dynamic map region
    DynamicMapRegion = 166,
    /// Interface open
    InterfaceOpen = 118,
    /// Close interface
    InterfaceClose = 130,
    /// Chat message
    ChatMessage = 4,
    /// System message
    SystemMessage = 253,
    /// Player option (right-click)
    PlayerOption = 104,
    /// Run energy update
    RunEnergy = 110,
    /// Weight update
    Weight = 174,
    /// Skills update
    SkillUpdate = 134,
    /// Inventory update
    InventoryUpdate = 53,
    /// Ground item spawn
    GroundItemSpawn = 44,
    /// Ground item remove
    GroundItemRemove = 64,
    /// Object spawn
    ObjectSpawn = 151,
    /// Object remove
    ObjectRemove = 101,
    /// NPC spawn
    NpcSpawn = 23,
    /// Projectile spawn
    ProjectileSpawn = 117,
    /// Graphics effect
    GraphicsEffect = 95,
    /// Animation
    Animation = 128,
    /// Sound effect
    SoundEffect = 12,
    /// Music track
    MusicTrack = 121,
    /// Logout
    Logout = 86,
    /// Bank interface open
    BankOpen = 248,
    /// Bank contents update (full or partial)
    BankUpdate = 249,
    /// Bank settings (note mode, placeholders, etc.)
    BankSettings = 250,
    /// Bank tab info (tab sizes)
    BankTabInfo = 251,
}

impl OutgoingOpcode {
    /// Get the opcode value
    pub fn as_u8(self) -> u8 {
        self as u8
    }
}

/// Incoming game packet
#[derive(Debug, Clone)]
pub struct IncomingGamePacket {
    /// Packet opcode (decrypted)
    pub opcode: u8,
    /// Packet data
    pub data: Vec<u8>,
}

impl IncomingGamePacket {
    /// Create a new incoming packet
    pub fn new(opcode: u8, data: Vec<u8>) -> Self {
        Self { opcode, data }
    }

    /// Get a packet buffer for reading the data
    pub fn buffer(&self) -> PacketBuffer {
        PacketBuffer::from_bytes(&self.data)
    }
}

/// Outgoing game packet
#[derive(Debug, Clone)]
pub struct OutgoingGamePacket {
    /// Packet opcode
    pub opcode: u8,
    /// Packet data
    pub data: Vec<u8>,
    /// Whether this packet has variable length
    pub variable_length: bool,
}

impl OutgoingGamePacket {
    /// Create a new fixed-length outgoing packet
    pub fn fixed(opcode: u8, data: Vec<u8>) -> Self {
        Self {
            opcode,
            data,
            variable_length: false,
        }
    }

    /// Create a new variable-length outgoing packet
    pub fn variable(opcode: u8, data: Vec<u8>) -> Self {
        Self {
            opcode,
            data,
            variable_length: true,
        }
    }

    /// Encode the packet for sending (with ISAAC encryption)
    pub fn encode(&self, isaac: &mut IsaacPair) -> Vec<u8> {
        let encoded_opcode = isaac.encode_opcode(self.opcode);

        if self.variable_length {
            // Variable length packet: opcode + size + data
            let mut buffer = PacketBuffer::with_capacity(3 + self.data.len());
            buffer.write_ubyte(encoded_opcode);

            if self.data.len() < 256 {
                // Variable byte
                buffer.write_ubyte(self.data.len() as u8);
            } else {
                // Variable short
                buffer.write_ushort(self.data.len() as u16);
            }

            buffer.write_bytes(&self.data);
            buffer.as_bytes().to_vec()
        } else {
            // Fixed length packet: opcode + data
            let mut buffer = PacketBuffer::with_capacity(1 + self.data.len());
            buffer.write_ubyte(encoded_opcode);
            buffer.write_bytes(&self.data);
            buffer.as_bytes().to_vec()
        }
    }

    /// Encode without ISAAC encryption (for testing)
    pub fn encode_raw(&self) -> Vec<u8> {
        if self.variable_length {
            let mut buffer = PacketBuffer::with_capacity(3 + self.data.len());
            buffer.write_ubyte(self.opcode);

            if self.data.len() < 256 {
                buffer.write_ubyte(self.data.len() as u8);
            } else {
                buffer.write_ushort(self.data.len() as u16);
            }

            buffer.write_bytes(&self.data);
            buffer.as_bytes().to_vec()
        } else {
            let mut buffer = PacketBuffer::with_capacity(1 + self.data.len());
            buffer.write_ubyte(self.opcode);
            buffer.write_bytes(&self.data);
            buffer.as_bytes().to_vec()
        }
    }
}

/// Movement request from client
#[derive(Debug, Clone)]
pub struct MovementRequest {
    /// Destination X coordinate
    pub dest_x: u16,
    /// Destination Y coordinate
    pub dest_y: u16,
    /// Whether the player is running
    pub running: bool,
    /// Path waypoints (intermediate steps)
    pub waypoints: Vec<(i8, i8)>,
}

/// Result of processing a game packet
#[derive(Debug)]
pub struct PacketResult {
    /// Response packets to send to the client
    pub responses: Vec<OutgoingGamePacket>,
    /// Movement request if this was a walk packet
    pub movement: Option<MovementRequest>,
    /// Chat message to broadcast
    pub chat_message: Option<String>,
    /// Command to execute
    pub command: Option<String>,
}

impl PacketResult {
    /// Create an empty result
    pub fn empty() -> Self {
        Self {
            responses: Vec::new(),
            movement: None,
            chat_message: None,
            command: None,
        }
    }

    /// Create a result with responses
    pub fn with_responses(responses: Vec<OutgoingGamePacket>) -> Self {
        Self {
            responses,
            movement: None,
            chat_message: None,
            command: None,
        }
    }

    /// Create a result with a movement request
    pub fn with_movement(movement: MovementRequest) -> Self {
        Self {
            responses: Vec::new(),
            movement: Some(movement),
            chat_message: None,
            command: None,
        }
    }

    /// Create a result with a command
    pub fn with_command(command: String) -> Self {
        Self {
            responses: Vec::new(),
            movement: None,
            chat_message: None,
            command: Some(command),
        }
    }

    /// Create a result with a chat message
    pub fn with_chat(message: String) -> Self {
        Self {
            responses: Vec::new(),
            movement: None,
            chat_message: Some(message),
            command: None,
        }
    }
}

/// Game packet handler
pub struct GamePacketHandler {
    // Stateless handler - player context is passed to process methods
}

impl GamePacketHandler {
    /// Create a new game packet handler
    pub fn new() -> Self {
        Self {}
    }

    /// Get the expected size for an incoming packet
    pub fn get_packet_size(&self, opcode: u8) -> i16 {
        INCOMING_PACKET_SIZES[opcode as usize]
    }

    /// Check if an opcode is valid/handled
    pub fn is_valid_opcode(&self, opcode: u8) -> bool {
        INCOMING_PACKET_SIZES[opcode as usize] != -3
    }

    /// Process an incoming game packet
    pub fn process(&self, packet: &IncomingGamePacket) -> Result<PacketResult> {
        trace!(
            opcode = packet.opcode,
            size = packet.data.len(),
            "Processing game packet"
        );

        match packet.opcode {
            0 => self.handle_keepalive(packet),
            3 => self.handle_focus_change(packet),
            4 => self.handle_chat(packet),
            14 | 98 => self.handle_walk(packet),
            52 => self.handle_command(packet),
            77 => self.handle_map_loaded(packet),
            86 => self.handle_mouse_click(packet),
            164 => self.handle_button_click(packet),
            210 => self.handle_close_interface(packet),
            // Bank and inventory packets need player context - handled in process_with_player
            41 | 43 | 55 | 115 | 116 | 117 | 129 | 145 | 185 | 195 | 214 | 233 | 243 => {
                debug!(
                    opcode = packet.opcode,
                    "Bank/inventory packet requires player context"
                );
                Ok(PacketResult::empty())
            }
            _ => {
                if self.is_valid_opcode(packet.opcode) {
                    debug!(opcode = packet.opcode, "Unimplemented game packet");
                    Ok(PacketResult::empty())
                } else {
                    warn!(opcode = packet.opcode, "Unknown game packet");
                    Ok(PacketResult::empty())
                }
            }
        }
    }

    /// Process a packet with player context
    /// This version can update player state directly
    pub fn process_with_player(
        &self,
        packet: &IncomingGamePacket,
        player: &Arc<Player>,
    ) -> Result<PacketResult> {
        // Handle bank and inventory packets that need player context
        match packet.opcode {
            41 => return self.handle_item_equip(packet, player),
            42 => return self.handle_item_unequip(packet, player),
            43 => return self.handle_bank_withdraw(packet, player),
            55 => return self.handle_bank_tab_select(packet, player),
            115 => return self.handle_bank_withdraw_mode(packet, player),
            116 => return self.handle_bank_search(packet, player),
            117 => return self.handle_bank_note_mode(packet, player),
            129 => return self.handle_bank_deposit_all(packet, player),
            145 => return self.handle_item_drop(packet, player),
            185 => return self.handle_bank_close(packet, player),
            195 => return self.handle_bank_deposit_equipment(packet, player),
            214 => return self.handle_bank_move_item(packet, player),
            233 => return self.handle_bank_deposit(packet, player),
            243 => return self.handle_inventory_swap(packet, player),
            _ => {}
        }

        let result = self.process(packet)?;

        // Apply movement if present
        if let Some(ref movement) = result.movement {
            self.apply_movement(player, movement);
        }

        // Handle commands with player context
        if let Some(ref command) = result.command {
            return self.execute_command(player, command);
        }

        Ok(result)
    }

    /// Apply movement to a player
    fn apply_movement(&self, player: &Arc<Player>, movement: &MovementRequest) {
        let current = player.location();
        let dest = Location::new(movement.dest_x, movement.dest_y, current.z);

        // Set running state
        *player.running.write() = movement.running;

        // For now, just teleport to the destination
        // In a full implementation, this would queue the movement path
        // and process it tick by tick
        player.set_location(dest);

        debug!(
            player = %player.username(),
            from = %current,
            to = %dest,
            running = movement.running,
            waypoints = movement.waypoints.len(),
            "Player movement processed"
        );
    }

    /// Execute a command with player context
    fn execute_command(&self, player: &Arc<Player>, command: &str) -> Result<PacketResult> {
        let parts: Vec<&str> = command.split_whitespace().collect();
        if parts.is_empty() {
            return Ok(PacketResult::empty());
        }

        let cmd = parts[0].to_lowercase();
        let args = &parts[1..];

        info!(
            player = %player.username(),
            command = %cmd,
            args = ?args,
            "Executing command"
        );

        match cmd.as_str() {
            "pos" | "mypos" | "coords" => {
                let loc = player.location();
                let message = format!(
                    "Position: {} (region {}, {})",
                    loc,
                    loc.region_x(),
                    loc.region_y()
                );
                Ok(PacketResult::with_responses(vec![build_system_message(
                    &message,
                )]))
            }
            "tele" | "teleport" => {
                if args.len() >= 2 {
                    if let (Ok(x), Ok(y)) = (args[0].parse::<u16>(), args[1].parse::<u16>()) {
                        let z = args.get(2).and_then(|s| s.parse::<u8>().ok()).unwrap_or(0);
                        let dest = Location::new(x, y, z);
                        player.teleport(dest);

                        let message = format!("Teleported to {}", dest);
                        return Ok(PacketResult::with_responses(vec![
                            build_system_message(&message),
                            build_map_region(dest),
                        ]));
                    }
                }
                Ok(PacketResult::with_responses(vec![build_system_message(
                    "Usage: ::tele x y [z]",
                )]))
            }
            "setlevel" => {
                if args.len() >= 2 {
                    if let (Ok(skill_id), Ok(level)) =
                        (args[0].parse::<u8>(), args[1].parse::<u8>())
                    {
                        if skill_id < 25 && level >= 1 && level <= 99 {
                            let mut skills = player.skills.write();
                            skills.levels[skill_id as usize] = level;
                            drop(skills);

                            let message = format!("Set skill {} to level {}", skill_id, level);
                            return Ok(PacketResult::with_responses(vec![
                                build_system_message(&message),
                                build_skill_update(skill_id, level, 0),
                            ]));
                        }
                    }
                }
                Ok(PacketResult::with_responses(vec![build_system_message(
                    "Usage: ::setlevel skill_id level",
                )]))
            }
            "energy" => {
                let energy = args
                    .first()
                    .and_then(|s| s.parse::<u8>().ok())
                    .unwrap_or(100);
                *player.run_energy.write() = energy.min(100);
                Ok(PacketResult::with_responses(vec![
                    build_system_message(&format!("Run energy set to {}", energy)),
                    build_run_energy(energy),
                ]))
            }
            "bank" => {
                // Open the bank interface
                *player.bank_open.write() = true;

                // Get bank data for packets
                let bank = player.bank.read();
                let total_items = bank.total_items();
                let capacity = bank.capacity;

                // Build the packets to send
                let bank_open_packet = build_bank_open(capacity as u16);
                let bank_settings_packet = build_bank_settings(
                    bank.withdraw_as_note,
                    bank.placeholders_enabled,
                    bank.withdraw_x_amount,
                );
                let bank_tab_info_packet = build_bank_tab_info(&bank);
                let bank_contents_packet = build_bank_full_update(&bank);

                drop(bank);

                info!(
                    player = %player.username(),
                    items = total_items,
                    capacity = capacity,
                    "Player opened bank"
                );

                Ok(PacketResult::with_responses(vec![
                    bank_open_packet,
                    bank_settings_packet,
                    bank_tab_info_packet,
                    bank_contents_packet,
                ]))
            }
            "bankgive" | "addbank" => {
                // Add an item to the bank for testing: ::bankgive item_id amount
                if args.len() >= 2 {
                    if let (Ok(item_id), Ok(amount)) =
                        (args[0].parse::<u16>(), args[1].parse::<u32>())
                    {
                        let mut bank = player.bank.write();
                        match bank.deposit(item_id, amount, None) {
                            Ok((tab, slot)) => {
                                return Ok(PacketResult::with_responses(vec![
                                    build_system_message(&format!(
                                        "Added {} x item {} to bank (tab {}, slot {})",
                                        amount, item_id, tab, slot
                                    )),
                                ]));
                            }
                            Err(e) => {
                                return Ok(PacketResult::with_responses(vec![
                                    build_system_message(&format!("Failed to add to bank: {}", e)),
                                ]));
                            }
                        }
                    }
                }
                Ok(PacketResult::with_responses(vec![build_system_message(
                    "Usage: ::bankgive item_id amount",
                )]))
            }
            "bankinfo" => {
                // Show bank information
                let bank = player.bank.read();
                let messages = vec![
                    format!("Bank Info:"),
                    format!("  Total items: {}", bank.total_items()),
                    format!(
                        "  Slots used: {}/{}",
                        bank.total_slots_used(),
                        bank.capacity
                    ),
                    format!("  Note mode: {}", bank.withdraw_as_note),
                    format!("  Placeholders: {}", bank.placeholders_enabled),
                ];
                let responses: Vec<_> = messages.iter().map(|m| build_system_message(m)).collect();
                Ok(PacketResult::with_responses(responses))
            }
            "item" | "give" => {
                // Give an item to inventory: ::item item_id [amount]
                if !args.is_empty() {
                    if let Ok(item_id) = args[0].parse::<u16>() {
                        let amount = args.get(1).and_then(|a| a.parse().ok()).unwrap_or(1u32);
                        let stackable = is_stackable(item_id);

                        let mut inventory = player.inventory.write();
                        match inventory.add(item_id, amount, stackable) {
                            Ok(slot) => {
                                let inv_update = build_player_inventory_update(&inventory);
                                return Ok(PacketResult::with_responses(vec![
                                    inv_update,
                                    build_system_message(&format!(
                                        "Added {} x item {} to slot {}",
                                        amount, item_id, slot
                                    )),
                                ]));
                            }
                            Err(_) => {
                                return Ok(PacketResult::with_responses(vec![
                                    build_system_message("Inventory is full."),
                                ]));
                            }
                        }
                    }
                }
                Ok(PacketResult::with_responses(vec![build_system_message(
                    "Usage: ::item item_id [amount]",
                )]))
            }
            "equipstats" | "stats" => {
                // Show equipment bonuses
                let mut equipment = player.equipment.write();
                let bonuses = equipment.calculate_bonuses();

                let messages = vec![
                    "Equipment Bonuses:".to_string(),
                    format!(
                        "  Attack: Stab={} Slash={} Crush={} Magic={} Range={}",
                        bonuses[0], bonuses[1], bonuses[2], bonuses[3], bonuses[4]
                    ),
                    format!(
                        "  Defence: Stab={} Slash={} Crush={} Magic={} Range={}",
                        bonuses[5], bonuses[6], bonuses[7], bonuses[8], bonuses[9]
                    ),
                    format!(
                        "  Other: Strength={} RangeStr={} MagicDmg={} Prayer={}",
                        bonuses[10], bonuses[11], bonuses[12], bonuses[13]
                    ),
                ];
                let responses: Vec<_> = messages.iter().map(|m| build_system_message(m)).collect();
                Ok(PacketResult::with_responses(responses))
            }
            "equipment" | "worn" => {
                // Show what's currently equipped
                let equipment = player.equipment.read();
                let items = equipment.get_all_items();

                if items.is_empty() {
                    return Ok(PacketResult::with_responses(vec![build_system_message(
                        "You have nothing equipped.",
                    )]));
                }

                let mut messages = vec!["Equipped items:".to_string()];
                for (slot, item_id, amount) in items {
                    let slot_name = match slot {
                        0 => "Head",
                        1 => "Cape",
                        2 => "Amulet",
                        3 => "Weapon",
                        4 => "Body",
                        5 => "Shield",
                        7 => "Legs",
                        9 => "Hands",
                        10 => "Feet",
                        12 => "Ring",
                        13 => "Ammo",
                        _ => "Unknown",
                    };
                    messages.push(format!("  {}: {} x{}", slot_name, item_id, amount));
                }
                let responses: Vec<_> = messages.iter().map(|m| build_system_message(m)).collect();
                Ok(PacketResult::with_responses(responses))
            }
            "clearinv" => {
                // Clear inventory
                let mut inventory = player.inventory.write();
                *inventory = crate::game::inventory::Inventory::new();
                let inv_update = build_player_inventory_update(&inventory);
                Ok(PacketResult::with_responses(vec![
                    inv_update,
                    build_system_message("Inventory cleared."),
                ]))
            }
            "drop" | "spawn" => {
                // Spawn a ground item at player's location: ::drop item_id [amount]
                if !args.is_empty() {
                    if let Ok(item_id) = args[0].parse::<u16>() {
                        let amount = args.get(1).and_then(|a| a.parse().ok()).unwrap_or(1u32);
                        let location = player.location.read().clone();

                        // Send ground item spawn packet
                        let spawn_packet = build_ground_item_spawn(item_id, amount, &location);

                        info!(
                            player = %player.username(),
                            item_id = item_id,
                            amount = amount,
                            location = %location,
                            "Spawned ground item via command"
                        );

                        return Ok(PacketResult::with_responses(vec![
                            spawn_packet,
                            build_system_message(&format!(
                                "Spawned {} x item {} at your location",
                                amount, item_id
                            )),
                        ]));
                    }
                }
                Ok(PacketResult::with_responses(vec![build_system_message(
                    "Usage: ::drop item_id [amount]",
                )]))
            }
            "pickup" | "take" => {
                // Remove a ground item (simulate pickup): ::pickup item_id
                if !args.is_empty() {
                    if let Ok(item_id) = args[0].parse::<u16>() {
                        let location = player.location.read().clone();

                        // Send ground item remove packet
                        let remove_packet = build_ground_item_remove(item_id, &location);

                        return Ok(PacketResult::with_responses(vec![
                            remove_packet,
                            build_system_message(&format!(
                                "Removed ground item {} at your location",
                                item_id
                            )),
                        ]));
                    }
                }
                Ok(PacketResult::with_responses(vec![build_system_message(
                    "Usage: ::pickup item_id",
                )]))
            }
            "help" | "commands" => {
                let messages = vec![
                    "Available commands:",
                    "::pos - Show current position",
                    "::tele x y [z] - Teleport to coordinates",
                    "::setlevel skill_id level - Set skill level",
                    "::energy [amount] - Set run energy",
                    "::item item_id [amount] - Add item to inventory",
                    "::clearinv - Clear inventory",
                    "::equipment - Show equipped items",
                    "::equipstats - Show equipment bonuses",
                    "::drop item_id [amount] - Spawn ground item",
                    "::pickup item_id - Remove ground item",
                    "::bank - Open bank interface",
                    "::bankgive item_id amount - Add item to bank",
                    "::bankinfo - Show bank information",
                ];
                let responses: Vec<_> = messages.iter().map(|m| build_system_message(m)).collect();
                Ok(PacketResult::with_responses(responses))
            }
            _ => Ok(PacketResult::with_responses(vec![build_system_message(
                &format!("Unknown command: {}", cmd),
            )])),
        }
    }

    /// Handle keep-alive packet
    fn handle_keepalive(&self, _packet: &IncomingGamePacket) -> Result<PacketResult> {
        trace!("Keep-alive received");
        Ok(PacketResult::empty())
    }

    /// Handle window focus change
    fn handle_focus_change(&self, packet: &IncomingGamePacket) -> Result<PacketResult> {
        let mut buffer = packet.buffer();
        let focused = buffer.read_ubyte() == 1;
        trace!(focused = focused, "Focus change");
        Ok(PacketResult::empty())
    }

    /// Handle chat message
    fn handle_chat(&self, packet: &IncomingGamePacket) -> Result<PacketResult> {
        let mut buffer = packet.buffer();

        // Chat format: effects (2 bytes), message (huffman encoded)
        if buffer.remaining() < 2 {
            return Ok(PacketResult::empty());
        }

        let _effects = buffer.read_ushort();
        let message_data = buffer.read_bytes(buffer.remaining());

        // TODO: Decode huffman-encoded message
        debug!(message_len = message_data.len(), "Chat message received");

        // For now, just acknowledge receipt
        Ok(PacketResult::empty())
    }

    /// Handle walk/movement
    fn handle_walk(&self, packet: &IncomingGamePacket) -> Result<PacketResult> {
        let mut buffer = packet.buffer();

        if buffer.remaining() < 5 {
            return Ok(PacketResult::empty());
        }

        // Read base coordinates
        // The format varies slightly between opcode 14 and 98
        let first_step_x: u16;
        let first_step_y: u16;
        let running: bool;

        if packet.opcode == 14 {
            // Walk to position (click on minimap)
            first_step_x = buffer.read_ushort_le();
            first_step_y = buffer.read_short_a();
            running = buffer.read_byte_s() == 1;
        } else {
            // Walk here (click on game screen) - opcode 98
            first_step_x = buffer.read_ushort_le();
            first_step_y = buffer.read_short_a();
            running = buffer.read_byte_s() == 1;
        }

        // Read additional waypoints if present
        let mut waypoints = Vec::new();
        while buffer.remaining() >= 2 {
            let dx = buffer.read_byte() as i8;
            let dy = buffer.read_byte() as i8;
            waypoints.push((dx, dy));
        }

        debug!(
            dest_x = first_step_x,
            dest_y = first_step_y,
            running = running,
            waypoints = waypoints.len(),
            "Walk request received"
        );

        Ok(PacketResult::with_movement(MovementRequest {
            dest_x: first_step_x,
            dest_y: first_step_y,
            running,
            waypoints,
        }))
    }

    /// Handle command (::command)
    fn handle_command(&self, packet: &IncomingGamePacket) -> Result<PacketResult> {
        let mut buffer = packet.buffer();
        let command = buffer.read_string();

        debug!(command = %command, "Command received");

        Ok(PacketResult::with_command(command))
    }

    /// Handle map region loaded confirmation
    fn handle_map_loaded(&self, _packet: &IncomingGamePacket) -> Result<PacketResult> {
        trace!("Map region loaded");
        Ok(PacketResult::empty())
    }

    /// Handle mouse click
    fn handle_mouse_click(&self, packet: &IncomingGamePacket) -> Result<PacketResult> {
        let mut buffer = packet.buffer();

        if buffer.remaining() < 4 {
            return Ok(PacketResult::empty());
        }

        let _packed_data = buffer.read_int();
        // Contains: time since last click, right-click flag, x, y

        Ok(PacketResult::empty())
    }

    /// Handle button click
    fn handle_button_click(&self, packet: &IncomingGamePacket) -> Result<PacketResult> {
        let mut buffer = packet.buffer();

        if buffer.remaining() < 2 {
            return Ok(PacketResult::empty());
        }

        let button_id = buffer.read_ushort();
        debug!(button_id = button_id, "Button click");

        Ok(PacketResult::empty())
    }

    /// Handle close interface
    fn handle_close_interface(&self, _packet: &IncomingGamePacket) -> Result<PacketResult> {
        trace!("Close interface");
        Ok(PacketResult::empty())
    }

    // ============ Bank Handlers ============

    /// Handle bank close packet
    fn handle_bank_close(
        &self,
        _packet: &IncomingGamePacket,
        player: &Arc<Player>,
    ) -> Result<PacketResult> {
        *player.bank_open.write() = false;
        debug!(player = %player.username(), "Bank closed");
        Ok(PacketResult::empty())
    }

    /// Handle bank withdraw packet
    fn handle_bank_withdraw(
        &self,
        packet: &IncomingGamePacket,
        player: &Arc<Player>,
    ) -> Result<PacketResult> {
        let mut buffer = packet.buffer();

        if buffer.remaining() < 11 {
            return Ok(PacketResult::empty());
        }

        let slot = buffer.read_ushort_le();
        let item_id = buffer.read_ushort_le();
        let amount = buffer.read_uint();
        let as_note = buffer.read_ubyte() == 1;

        debug!(
            player = %player.username(),
            slot = slot,
            item_id = item_id,
            amount = amount,
            as_note = as_note,
            "Bank withdraw request"
        );

        // Check if inventory has room first
        // Check item definitions for stackability
        let stackable = is_stackable(item_id);
        {
            let inventory = player.inventory.read();
            if !inventory.has_room_for(item_id, amount, stackable) {
                return Ok(PacketResult::with_responses(vec![build_system_message(
                    "You don't have enough inventory space.",
                )]));
            }
        }

        // Perform the withdrawal
        let mut bank = player.bank.write();

        // Find which tab contains this slot (for now, assume tab 0 and slot is global)
        // In a full implementation, we'd track which tab is selected
        let tab = 0;

        match bank.withdraw(tab, slot as usize, amount) {
            Ok((withdrawn_item_id, withdrawn_amount)) => {
                // Get bank slot update info before releasing lock
                let bank_slot_item = bank
                    .get_item(tab, slot as usize)
                    .map(|item| (item.item_id, item.amount, item.placeholder))
                    .unwrap_or((0, 0, false));

                // Release bank lock before acquiring inventory lock
                drop(bank);

                // Add item to inventory
                let mut inventory = player.inventory.write();
                match inventory.add(withdrawn_item_id, withdrawn_amount, stackable) {
                    Ok(inv_slot) => {
                        // Get the updated item info for the packet
                        let (item_id, item_amount) = inventory
                            .get(inv_slot)
                            .map(|item| (item.item_id, item.amount))
                            .unwrap_or((0, 0));

                        info!(
                            player = %player.username(),
                            item_id = withdrawn_item_id,
                            amount = withdrawn_amount,
                            inv_slot = inv_slot,
                            "Withdrew from bank to inventory"
                        );

                        // Send inventory update and bank slot update packets
                        Ok(PacketResult::with_responses(vec![
                            build_inventory_slot_update(
                                CONTAINER_INVENTORY,
                                inv_slot as u16,
                                item_id,
                                item_amount,
                            ),
                            build_bank_slot_update(
                                tab as u8,
                                slot,
                                bank_slot_item.0,
                                bank_slot_item.1,
                                bank_slot_item.2,
                            ),
                        ]))
                    }
                    Err(remaining) => {
                        // Partial withdrawal - some items couldn't fit
                        // In a real implementation, we'd put the remaining back in bank
                        warn!(
                            player = %player.username(),
                            item_id = withdrawn_item_id,
                            remaining = remaining,
                            "Partial withdrawal - inventory full"
                        );
                        Ok(PacketResult::with_responses(vec![build_system_message(
                            "Your inventory is full.",
                        )]))
                    }
                }
            }
            Err(e) => {
                let msg = match e {
                    BankError::EmptySlot => "That slot is empty.",
                    BankError::InsufficientItems => "Not enough of that item.",
                    BankError::InvalidSlot => "Invalid bank slot.",
                    _ => "Cannot withdraw that item.",
                };
                Ok(PacketResult::with_responses(vec![build_system_message(
                    msg,
                )]))
            }
        }
    }

    /// Handle bank deposit packet
    fn handle_bank_deposit(
        &self,
        packet: &IncomingGamePacket,
        player: &Arc<Player>,
    ) -> Result<PacketResult> {
        let mut buffer = packet.buffer();

        if buffer.remaining() < 8 {
            return Ok(PacketResult::empty());
        }

        let inventory_slot = buffer.read_ushort_le();
        let item_id = buffer.read_ushort_le();
        let amount = buffer.read_uint();

        debug!(
            player = %player.username(),
            slot = inventory_slot,
            item_id = item_id,
            amount = amount,
            "Bank deposit request"
        );

        // Validate inventory slot
        if inventory_slot as usize >= INVENTORY_SIZE {
            return Ok(PacketResult::with_responses(vec![build_system_message(
                "Invalid inventory slot.",
            )]));
        }

        // Check if the item exists in inventory at the specified slot
        let (actual_item_id, available_amount) = {
            let inventory = player.inventory.read();
            match inventory.get(inventory_slot as usize) {
                Some(item) if item.item_id == item_id => (item.item_id, item.amount),
                Some(item) => {
                    warn!(
                        player = %player.username(),
                        expected = item_id,
                        actual = item.item_id,
                        "Item ID mismatch in deposit"
                    );
                    return Ok(PacketResult::with_responses(vec![build_system_message(
                        "Item not found.",
                    )]));
                }
                None => {
                    return Ok(PacketResult::with_responses(vec![build_system_message(
                        "That slot is empty.",
                    )]));
                }
            }
        };

        // Calculate actual amount to deposit
        let deposit_amount = if amount == u32::MAX {
            available_amount // "All"
        } else {
            amount.min(available_amount)
        };

        // Try to deposit to bank first
        let mut bank = player.bank.write();

        match bank.deposit(actual_item_id, deposit_amount, None) {
            Ok((tab, slot)) => {
                // Release bank lock before acquiring inventory lock
                drop(bank);

                // Remove from inventory
                let mut inventory = player.inventory.write();
                let removed = inventory.remove(inventory_slot as usize, deposit_amount);

                match removed {
                    Ok(removed_amount) => {
                        // Get updated slot info (may be empty or have remaining items)
                        let (item_id, item_amount) = inventory
                            .get(inventory_slot as usize)
                            .map(|item| (item.item_id, item.amount))
                            .unwrap_or((0, 0));

                        info!(
                            player = %player.username(),
                            item_id = actual_item_id,
                            amount = removed_amount,
                            tab = tab,
                            slot = slot,
                            "Deposited to bank from inventory"
                        );

                        // Get the bank slot info for the update packet
                        let bank = player.bank.read();
                        let bank_slot_info = bank
                            .get_item(tab, slot)
                            .map(|item| (item.item_id, item.amount, item.placeholder))
                            .unwrap_or((actual_item_id, removed_amount, false));
                        drop(bank);

                        // Send inventory update and bank slot update packets
                        Ok(PacketResult::with_responses(vec![
                            build_inventory_slot_update(
                                CONTAINER_INVENTORY,
                                inventory_slot,
                                item_id,
                                item_amount,
                            ),
                            build_bank_slot_update(
                                tab as u8,
                                slot as u16,
                                bank_slot_info.0,
                                bank_slot_info.1,
                                bank_slot_info.2,
                            ),
                        ]))
                    }
                    Err(e) => {
                        // This shouldn't happen since we validated above
                        warn!(
                            player = %player.username(),
                            error = ?e,
                            "Failed to remove item from inventory after bank deposit"
                        );
                        Ok(PacketResult::with_responses(vec![build_system_message(
                            "Something went wrong.",
                        )]))
                    }
                }
            }
            Err(e) => {
                let msg = match e {
                    BankError::BankFull => "Your bank is full.",
                    BankError::InvalidAmount => "Invalid amount.",
                    _ => "Cannot deposit that item.",
                };
                Ok(PacketResult::with_responses(vec![build_system_message(
                    msg,
                )]))
            }
        }
    }

    /// Handle bank deposit all inventory packet
    fn handle_bank_deposit_all(
        &self,
        _packet: &IncomingGamePacket,
        player: &Arc<Player>,
    ) -> Result<PacketResult> {
        debug!(player = %player.username(), "Bank deposit all inventory");

        // Collect all items from inventory first
        let items_to_deposit: Vec<(usize, u16, u32)> = {
            let inventory = player.inventory.read();
            inventory.get_all_items()
        };

        if items_to_deposit.is_empty() {
            return Ok(PacketResult::with_responses(vec![build_system_message(
                "You have nothing to deposit.",
            )]));
        }

        let mut deposited_count = 0u32;
        let mut failed_count = 0u32;

        // Deposit each item
        for (slot, item_id, amount) in items_to_deposit {
            let mut bank = player.bank.write();
            match bank.deposit(item_id, amount, None) {
                Ok(_) => {
                    drop(bank);
                    let mut inventory = player.inventory.write();
                    if inventory.remove(slot, amount).is_ok() {
                        deposited_count += 1;
                    }
                }
                Err(BankError::BankFull) => {
                    failed_count += 1;
                    // Bank is full, stop trying
                    break;
                }
                Err(_) => {
                    failed_count += 1;
                }
            }
        }

        info!(
            player = %player.username(),
            deposited = deposited_count,
            failed = failed_count,
            "Deposit all inventory"
        );

        let msg = if failed_count > 0 {
            format!(
                "Deposited {} item(s). {} couldn't be deposited (bank full).",
                deposited_count, failed_count
            )
        } else {
            format!("Deposited {} item(s).", deposited_count)
        };

        // Send full inventory update and full bank update since multiple slots changed
        let inventory = player.inventory.read();
        let bank = player.bank.read();
        Ok(PacketResult::with_responses(vec![
            build_player_inventory_update(&inventory),
            build_bank_full_update(&bank),
            build_system_message(&msg),
        ]))
    }

    /// Handle bank deposit equipment packet
    fn handle_bank_deposit_equipment(
        &self,
        _packet: &IncomingGamePacket,
        player: &Arc<Player>,
    ) -> Result<PacketResult> {
        debug!(player = %player.username(), "Bank deposit all equipment");
        // TODO: Iterate through equipment and deposit all items
        Ok(PacketResult::with_responses(vec![build_system_message(
            "Deposited all worn items.",
        )]))
    }

    /// Handle bank tab select packet
    fn handle_bank_tab_select(
        &self,
        packet: &IncomingGamePacket,
        player: &Arc<Player>,
    ) -> Result<PacketResult> {
        let mut buffer = packet.buffer();

        if buffer.remaining() < 1 {
            return Ok(PacketResult::empty());
        }

        let tab = buffer.read_ubyte();
        debug!(player = %player.username(), tab = tab, "Bank tab selected");
        // Tab selection is client-side state; server just acknowledges
        Ok(PacketResult::empty())
    }

    /// Handle bank move item packet
    fn handle_bank_move_item(
        &self,
        packet: &IncomingGamePacket,
        player: &Arc<Player>,
    ) -> Result<PacketResult> {
        let mut buffer = packet.buffer();

        if buffer.remaining() < 5 {
            return Ok(PacketResult::empty());
        }

        let from_slot = buffer.read_ushort_le();
        let to_slot = buffer.read_ushort_le();
        let mode = buffer.read_ubyte();

        debug!(
            player = %player.username(),
            from_slot = from_slot,
            to_slot = to_slot,
            mode = mode,
            "Bank move item"
        );

        let mut bank = player.bank.write();
        // For simplicity, assume all items are in tab 0 for now
        match bank.move_item(0, from_slot as usize, 0, to_slot as usize) {
            Ok(()) => {
                // Get the updated slot data after the move
                let from_item = bank
                    .get_item(0, from_slot as usize)
                    .map(|i| (i.item_id, i.amount, i.placeholder))
                    .unwrap_or((0, 0, false));
                let to_item = bank
                    .get_item(0, to_slot as usize)
                    .map(|i| (i.item_id, i.amount, i.placeholder))
                    .unwrap_or((0, 0, false));

                // Send updates for both affected slots
                Ok(PacketResult::with_responses(vec![
                    build_bank_slot_update(0, from_slot, from_item.0, from_item.1, from_item.2),
                    build_bank_slot_update(0, to_slot, to_item.0, to_item.1, to_item.2),
                ]))
            }
            Err(e) => {
                debug!(error = %e, "Bank move failed");
                Ok(PacketResult::empty())
            }
        }
    }

    /// Handle bank search packet
    fn handle_bank_search(
        &self,
        packet: &IncomingGamePacket,
        player: &Arc<Player>,
    ) -> Result<PacketResult> {
        let mut buffer = packet.buffer();
        let query = buffer.read_string();

        debug!(player = %player.username(), query = %query, "Bank search");
        // Search is handled client-side with server-provided item data
        Ok(PacketResult::empty())
    }

    /// Handle bank note mode packet
    fn handle_bank_note_mode(
        &self,
        packet: &IncomingGamePacket,
        player: &Arc<Player>,
    ) -> Result<PacketResult> {
        let mut buffer = packet.buffer();

        if buffer.remaining() < 1 {
            return Ok(PacketResult::empty());
        }

        let enabled = buffer.read_ubyte() == 1;
        debug!(player = %player.username(), enabled = enabled, "Bank note mode");

        let mut bank = player.bank.write();
        bank.set_note_mode(enabled);

        // Send settings update to confirm
        let settings_packet = build_bank_settings(
            bank.withdraw_as_note,
            bank.placeholders_enabled,
            bank.withdraw_x_amount,
        );
        Ok(PacketResult::with_responses(vec![settings_packet]))
    }

    /// Handle bank withdraw mode packet
    fn handle_bank_withdraw_mode(
        &self,
        packet: &IncomingGamePacket,
        player: &Arc<Player>,
    ) -> Result<PacketResult> {
        let mut buffer = packet.buffer();

        if buffer.remaining() < 1 {
            return Ok(PacketResult::empty());
        }

        let mode = buffer.read_ubyte();
        debug!(player = %player.username(), mode = mode, "Bank withdraw mode");
        // 0 = single, 1 = 5, 2 = 10, 3 = X, 4 = All
        // Store the withdraw-X amount if mode is X
        let bank = player.bank.read();
        let settings_packet = build_bank_settings(
            bank.withdraw_as_note,
            bank.placeholders_enabled,
            bank.withdraw_x_amount,
        );
        Ok(PacketResult::with_responses(vec![settings_packet]))
    }

    // ============ Inventory Handlers ============

    /// Handle item drop packet
    fn handle_item_drop(
        &self,
        packet: &IncomingGamePacket,
        player: &Arc<Player>,
    ) -> Result<PacketResult> {
        let mut buffer = packet.buffer();

        if buffer.remaining() < 4 {
            return Ok(PacketResult::empty());
        }

        let slot = buffer.read_ushort_le();
        let item_id = buffer.read_ushort_le();

        debug!(
            player = %player.username(),
            slot = slot,
            item_id = item_id,
            "Item drop"
        );

        // Validate slot
        if slot as usize >= INVENTORY_SIZE {
            return Ok(PacketResult::empty());
        }

        // Remove from inventory
        let dropped_item = {
            let mut inventory = player.inventory.write();

            // Verify the item at the slot matches what client expects
            match inventory.get(slot as usize) {
                Some(item) if item.item_id == item_id => {
                    let amount = item.amount;
                    let id = item.item_id;
                    // Clear the slot
                    let _ = inventory.clear_slot(slot as usize);
                    Some((id, amount))
                }
                Some(item) => {
                    warn!(
                        player = %player.username(),
                        expected = item_id,
                        actual = item.item_id,
                        "Item ID mismatch in drop"
                    );
                    None
                }
                None => {
                    debug!(
                        player = %player.username(),
                        slot = slot,
                        "Tried to drop from empty slot"
                    );
                    None
                }
            }
        };

        if let Some((dropped_id, dropped_amount)) = dropped_item {
            let location = player.location.read().clone();

            info!(
                player = %player.username(),
                item_id = dropped_id,
                amount = dropped_amount,
                slot = slot,
                location = %location,
                "Dropped item from inventory"
            );

            // Send inventory update and ground item spawn packet
            // The ground item spawn packet tells the client to render the item on the ground
            // Note: In a full implementation, the GroundItemManager would track this item
            // and handle visibility transitions. For now, we send the packet directly.
            Ok(PacketResult::with_responses(vec![
                build_inventory_clear_slot(CONTAINER_INVENTORY, slot),
                build_ground_item_spawn(dropped_id, dropped_amount, &location),
            ]))
        } else {
            Ok(PacketResult::empty())
        }
    }

    /// Handle item equip packet
    fn handle_item_equip(
        &self,
        packet: &IncomingGamePacket,
        player: &Arc<Player>,
    ) -> Result<PacketResult> {
        let mut buffer = packet.buffer();

        if buffer.remaining() < 4 {
            return Ok(PacketResult::empty());
        }

        let inv_slot = buffer.read_ushort_le();
        let item_id = buffer.read_ushort_le();

        debug!(
            player = %player.username(),
            slot = inv_slot,
            item_id = item_id,
            "Item equip request"
        );

        // Validate inventory slot
        if inv_slot as usize >= INVENTORY_SIZE {
            return Ok(PacketResult::with_responses(vec![build_system_message(
                "Invalid inventory slot.",
            )]));
        }

        // Check if item is equippable
        if !is_equippable(item_id) {
            return Ok(PacketResult::with_responses(vec![build_system_message(
                "You can't equip that.",
            )]));
        }

        // Get the equipment slot for this item
        let equipment_slot = match get_equipment_slot(item_id) {
            Some(slot) => slot,
            None => {
                return Ok(PacketResult::with_responses(vec![build_system_message(
                    "You can't equip that.",
                )]));
            }
        };

        // Verify the item exists in inventory at the specified slot
        let (actual_item_id, item_amount) = {
            let inventory = player.inventory.read();
            match inventory.get(inv_slot as usize) {
                Some(item) if item.item_id == item_id => (item.item_id, item.amount),
                Some(item) => {
                    warn!(
                        player = %player.username(),
                        expected = item_id,
                        actual = item.item_id,
                        "Item ID mismatch in equip"
                    );
                    return Ok(PacketResult::with_responses(vec![build_system_message(
                        "Item not found.",
                    )]));
                }
                None => {
                    return Ok(PacketResult::with_responses(vec![build_system_message(
                        "That slot is empty.",
                    )]));
                }
            }
        };

        // Get skill levels for requirement checking
        let skill_levels: Vec<u8> = {
            let skills = player.skills.read();
            skills.levels.to_vec()
        };

        // Try to equip the item
        let mut equipment = player.equipment.write();
        match equipment.equip(actual_item_id, item_amount, &skill_levels) {
            Ok(unequipped_items) => {
                drop(equipment);

                // Remove item from inventory
                let mut inventory = player.inventory.write();
                let _ = inventory.clear_slot(inv_slot as usize);

                // Add any unequipped items back to inventory
                let mut responses = Vec::new();
                let _stackable = is_stackable(actual_item_id);

                for unequipped in &unequipped_items {
                    if unequipped.item_id > 0 {
                        let item_stackable = is_stackable(unequipped.item_id);
                        match inventory.add(unequipped.item_id, unequipped.amount, item_stackable) {
                            Ok(_) => {}
                            Err(_) => {
                                // Inventory full - in a real implementation, we'd prevent the equip
                                // or drop the item. For now, just warn.
                                warn!(
                                    player = %player.username(),
                                    item_id = unequipped.item_id,
                                    "Could not return unequipped item to inventory"
                                );
                            }
                        }
                    }
                }

                // Build response packets
                // Send full inventory update (simpler than tracking individual slots)
                responses.push(build_player_inventory_update(&inventory));
                drop(inventory);

                // Send full equipment update
                let equipment = player.equipment.read();
                responses.push(build_equipment_full_update(&equipment));

                info!(
                    player = %player.username(),
                    item_id = actual_item_id,
                    slot = ?equipment_slot,
                    unequipped_count = unequipped_items.len(),
                    "Equipped item"
                );

                Ok(PacketResult::with_responses(responses))
            }
            Err(e) => {
                let msg = match e {
                    EquipmentError::NotEquippable => "You can't equip that.",
                    EquipmentError::RequirementsNotMet => {
                        "You don't meet the requirements to wear this."
                    }
                    EquipmentError::ItemNotFound => "Item not found.",
                    _ => "You can't equip that.",
                };
                Ok(PacketResult::with_responses(vec![build_system_message(
                    msg,
                )]))
            }
        }
    }

    /// Handle item unequip packet
    fn handle_item_unequip(
        &self,
        packet: &IncomingGamePacket,
        player: &Arc<Player>,
    ) -> Result<PacketResult> {
        let mut buffer = packet.buffer();

        if buffer.remaining() < 4 {
            return Ok(PacketResult::empty());
        }

        let equip_slot = buffer.read_ushort_le();
        let item_id = buffer.read_ushort_le();

        debug!(
            player = %player.username(),
            slot = equip_slot,
            item_id = item_id,
            "Item unequip request"
        );

        // Validate equipment slot
        if equip_slot as usize >= EQUIPMENT_SLOT_COUNT {
            return Ok(PacketResult::with_responses(vec![build_system_message(
                "Invalid equipment slot.",
            )]));
        }

        // Check if inventory has room
        {
            let inventory = player.inventory.read();
            let equipment = player.equipment.read();

            if let Some(equipped) = equipment.get(equip_slot as usize) {
                if equipped.item_id > 0 {
                    let stackable = is_stackable(equipped.item_id);
                    if !inventory.has_room_for(equipped.item_id, equipped.amount, stackable) {
                        return Ok(PacketResult::with_responses(vec![build_system_message(
                            "You don't have enough inventory space.",
                        )]));
                    }
                }
            }
        }

        // Perform the unequip
        let mut equipment = player.equipment.write();
        match equipment.unequip(equip_slot as usize) {
            Ok(unequipped_item) => {
                drop(equipment);

                // Add to inventory
                let mut inventory = player.inventory.write();
                let stackable = is_stackable(unequipped_item.item_id);

                match inventory.add(unequipped_item.item_id, unequipped_item.amount, stackable) {
                    Ok(_) => {
                        info!(
                            player = %player.username(),
                            item_id = unequipped_item.item_id,
                            slot = equip_slot,
                            "Unequipped item"
                        );

                        // Send updates
                        let inv_update = build_player_inventory_update(&inventory);
                        drop(inventory);

                        let equipment = player.equipment.read();
                        let equip_update = build_equipment_full_update(&equipment);

                        Ok(PacketResult::with_responses(vec![inv_update, equip_update]))
                    }
                    Err(_) => {
                        // Put item back in equipment (shouldn't happen since we checked)
                        let mut equipment = player.equipment.write();
                        let _ = equipment.get_mut(equip_slot as usize).map(|slot| {
                            *slot = unequipped_item;
                        });

                        Ok(PacketResult::with_responses(vec![build_system_message(
                            "You don't have enough inventory space.",
                        )]))
                    }
                }
            }
            Err(e) => {
                let msg = match e {
                    EquipmentError::SlotEmpty => "Nothing to unequip.",
                    EquipmentError::InvalidSlot => "Invalid equipment slot.",
                    _ => "Cannot unequip that.",
                };
                Ok(PacketResult::with_responses(vec![build_system_message(
                    msg,
                )]))
            }
        }
    }

    /// Handle inventory swap packet
    fn handle_inventory_swap(
        &self,
        packet: &IncomingGamePacket,
        player: &Arc<Player>,
    ) -> Result<PacketResult> {
        let mut buffer = packet.buffer();

        if buffer.remaining() < 4 {
            return Ok(PacketResult::empty());
        }

        let from_slot = buffer.read_ushort_le();
        let to_slot = buffer.read_ushort_le();

        debug!(
            player = %player.username(),
            from_slot = from_slot,
            to_slot = to_slot,
            "Inventory swap"
        );

        // Perform the swap
        let mut inventory = player.inventory.write();
        match inventory.swap(from_slot as usize, to_slot as usize) {
            Ok(()) => {
                // Get the items at both slots after swap
                let from_item = inventory
                    .get(from_slot as usize)
                    .map(|i| (i.item_id, i.amount))
                    .unwrap_or((0, 0));
                let to_item = inventory
                    .get(to_slot as usize)
                    .map(|i| (i.item_id, i.amount))
                    .unwrap_or((0, 0));

                trace!(
                    player = %player.username(),
                    from_slot = from_slot,
                    to_slot = to_slot,
                    "Inventory swap successful"
                );

                // Send inventory updates for both affected slots
                Ok(PacketResult::with_responses(vec![
                    build_inventory_slot_update(
                        CONTAINER_INVENTORY,
                        from_slot,
                        from_item.0,
                        from_item.1,
                    ),
                    build_inventory_slot_update(CONTAINER_INVENTORY, to_slot, to_item.0, to_item.1),
                ]))
            }
            Err(InventoryError::InvalidSlot) => {
                warn!(
                    player = %player.username(),
                    from_slot = from_slot,
                    to_slot = to_slot,
                    "Invalid inventory swap slots"
                );
                Ok(PacketResult::empty())
            }
            Err(e) => {
                warn!(
                    player = %player.username(),
                    error = ?e,
                    "Inventory swap failed"
                );
                Ok(PacketResult::empty())
            }
        }
    }
}

impl Default for GamePacketHandler {
    fn default() -> Self {
        Self::new()
    }
}

/// Build a system message packet
pub fn build_system_message(message: &str) -> OutgoingGamePacket {
    let mut buffer = PacketBuffer::with_capacity(message.len() + 2);
    buffer.write_string(message);
    OutgoingGamePacket::variable(
        OutgoingOpcode::SystemMessage.as_u8(),
        buffer.as_bytes().to_vec(),
    )
}

/// Build a logout packet
pub fn build_logout() -> OutgoingGamePacket {
    OutgoingGamePacket::fixed(OutgoingOpcode::Logout.as_u8(), vec![])
}

/// Build a map region packet
pub fn build_map_region(location: Location) -> OutgoingGamePacket {
    let mut buffer = PacketBuffer::with_capacity(4);
    // Send region coordinates (location / 8)
    let region_x = (location.x >> 3) as u16;
    let region_y = (location.y >> 3) as u16;
    buffer.write_ushort(region_x);
    buffer.write_ushort(region_y);
    OutgoingGamePacket::fixed(
        OutgoingOpcode::MapRegion.as_u8(),
        buffer.as_bytes().to_vec(),
    )
}

/// Build a skill update packet
pub fn build_skill_update(skill_id: u8, level: u8, xp: i32) -> OutgoingGamePacket {
    let mut buffer = PacketBuffer::with_capacity(7);
    buffer.write_ubyte(skill_id);
    buffer.write_ubyte(level);
    buffer.write_int(xp);
    OutgoingGamePacket::fixed(
        OutgoingOpcode::SkillUpdate.as_u8(),
        buffer.as_bytes().to_vec(),
    )
}

/// Build a run energy update packet
pub fn build_run_energy(energy: u8) -> OutgoingGamePacket {
    let mut buffer = PacketBuffer::with_capacity(1);
    buffer.write_ubyte(energy);
    OutgoingGamePacket::fixed(
        OutgoingOpcode::RunEnergy.as_u8(),
        buffer.as_bytes().to_vec(),
    )
}

/// Build a player option packet (right-click menu)
pub fn build_player_option(slot: u8, text: &str, priority: bool) -> OutgoingGamePacket {
    let mut buffer = PacketBuffer::with_capacity(text.len() + 4);
    buffer.write_ubyte(slot);
    buffer.write_string(text);
    buffer.write_ubyte(if priority { 1 } else { 0 });
    OutgoingGamePacket::variable(
        OutgoingOpcode::PlayerOption.as_u8(),
        buffer.as_bytes().to_vec(),
    )
}

/// Build an inventory update packet for a single slot
/// Format: container_id (2), slot (2), item_id (2), amount (4)
pub fn build_inventory_slot_update(
    container_id: u16,
    slot: u16,
    item_id: u16,
    amount: u32,
) -> OutgoingGamePacket {
    let mut buffer = PacketBuffer::with_capacity(10);
    buffer.write_ushort(container_id);
    buffer.write_ushort(slot);
    buffer.write_ushort(item_id);
    buffer.write_uint(amount);
    OutgoingGamePacket::fixed(
        OutgoingOpcode::InventoryUpdate.as_u8(),
        buffer.as_bytes().to_vec(),
    )
}

/// Build a full inventory update packet
/// Format: container_id (2), count (2), then for each item: slot (2), item_id (2), amount (4)
/// Container IDs: 93 = inventory, 95 = bank, 94 = equipment
pub fn build_inventory_full_update(
    container_id: u16,
    items: &[(u16, u16, u32)], // (slot, item_id, amount)
) -> OutgoingGamePacket {
    let mut buffer = PacketBuffer::with_capacity(4 + items.len() * 8);
    buffer.write_ushort(container_id);
    buffer.write_ushort(items.len() as u16);
    for (slot, item_id, amount) in items {
        buffer.write_ushort(*slot);
        buffer.write_ushort(*item_id);
        buffer.write_uint(*amount);
    }
    OutgoingGamePacket::variable(
        OutgoingOpcode::InventoryUpdate.as_u8(),
        buffer.as_bytes().to_vec(),
    )
}

/// Container ID for player inventory
pub const CONTAINER_INVENTORY: u16 = 93;

/// Container ID for equipment
pub const CONTAINER_EQUIPMENT: u16 = 94;

/// Container ID for bank
pub const CONTAINER_BANK: u16 = 95;

/// Build a full player inventory update packet
pub fn build_player_inventory_update(
    inventory: &crate::game::inventory::Inventory,
) -> OutgoingGamePacket {
    let items: Vec<(u16, u16, u32)> = inventory
        .as_slice()
        .iter()
        .enumerate()
        .map(|(slot, item)| (slot as u16, item.item_id, item.amount))
        .collect();
    build_inventory_full_update(CONTAINER_INVENTORY, &items)
}

/// Build an inventory clear slot packet (item_id = 0, amount = 0)
pub fn build_inventory_clear_slot(container_id: u16, slot: u16) -> OutgoingGamePacket {
    build_inventory_slot_update(container_id, slot, 0, 0)
}

// ============ Bank Packet Builders ============

/// Build a bank open interface packet
/// Format: capacity (2)
pub fn build_bank_open(capacity: u16) -> OutgoingGamePacket {
    let mut buffer = PacketBuffer::with_capacity(2);
    buffer.write_ushort(capacity);
    OutgoingGamePacket::fixed(OutgoingOpcode::BankOpen.as_u8(), buffer.as_bytes().to_vec())
}

/// Build a bank settings packet
/// Format: note_mode (1), placeholders_enabled (1), withdraw_x_amount (4)
pub fn build_bank_settings(
    note_mode: bool,
    placeholders_enabled: bool,
    withdraw_x_amount: u32,
) -> OutgoingGamePacket {
    let mut buffer = PacketBuffer::with_capacity(6);
    buffer.write_ubyte(if note_mode { 1 } else { 0 });
    buffer.write_ubyte(if placeholders_enabled { 1 } else { 0 });
    buffer.write_uint(withdraw_x_amount);
    OutgoingGamePacket::fixed(
        OutgoingOpcode::BankSettings.as_u8(),
        buffer.as_bytes().to_vec(),
    )
}

/// Build a bank tab info packet (sizes of each tab)
/// Format: tab_count (1), then for each tab: item_count (2)
pub fn build_bank_tab_info(bank: &crate::game::bank::Bank) -> OutgoingGamePacket {
    use crate::game::bank::BANK_TAB_COUNT;

    let mut buffer = PacketBuffer::with_capacity(1 + BANK_TAB_COUNT * 2);
    buffer.write_ubyte(BANK_TAB_COUNT as u8);

    for tab in &bank.tabs {
        buffer.write_ushort(tab.items.len() as u16);
    }

    OutgoingGamePacket::fixed(
        OutgoingOpcode::BankTabInfo.as_u8(),
        buffer.as_bytes().to_vec(),
    )
}

/// Build a full bank update packet (all items across all tabs)
/// Format: total_items (2), then for each item: tab (1), slot (2), item_id (2), amount (4), placeholder (1)
pub fn build_bank_full_update(bank: &crate::game::bank::Bank) -> OutgoingGamePacket {
    let all_items = bank.get_all_items();

    let mut buffer = PacketBuffer::with_capacity(2 + all_items.len() * 10);
    buffer.write_ushort(all_items.len() as u16);

    for item in &all_items {
        buffer.write_ubyte(item.tab);
        buffer.write_ushort(item.slot);
        buffer.write_ushort(item.item_id);
        buffer.write_uint(item.amount);
        buffer.write_ubyte(if item.placeholder { 1 } else { 0 });
    }

    OutgoingGamePacket::variable(
        OutgoingOpcode::BankUpdate.as_u8(),
        buffer.as_bytes().to_vec(),
    )
}

/// Build a single bank slot update packet
/// Format: tab (1), slot (2), item_id (2), amount (4), placeholder (1)
pub fn build_bank_slot_update(
    tab: u8,
    slot: u16,
    item_id: u16,
    amount: u32,
    placeholder: bool,
) -> OutgoingGamePacket {
    let mut buffer = PacketBuffer::with_capacity(10);
    buffer.write_ubyte(tab);
    buffer.write_ushort(slot);
    buffer.write_ushort(item_id);
    buffer.write_uint(amount);
    buffer.write_ubyte(if placeholder { 1 } else { 0 });
    OutgoingGamePacket::fixed(
        OutgoingOpcode::BankUpdate.as_u8(),
        buffer.as_bytes().to_vec(),
    )
}

/// Build a bank slot clear packet (for when an item is fully withdrawn)
pub fn build_bank_slot_clear(tab: u8, slot: u16) -> OutgoingGamePacket {
    build_bank_slot_update(tab, slot, 0, 0, false)
}

// ============ Equipment Packet Builders ============

/// Build a full equipment update packet
/// Format: slot_count (1), then for each slot: slot (1), item_id (2), amount (4)
pub fn build_equipment_full_update(equipment: &Equipment) -> OutgoingGamePacket {
    let items: Vec<(u16, u16, u32)> = equipment
        .as_slice()
        .iter()
        .enumerate()
        .map(|(slot, item)| (slot as u16, item.item_id, item.amount))
        .collect();
    build_inventory_full_update(CONTAINER_EQUIPMENT, &items)
}

/// Build an equipment slot update packet
pub fn build_equipment_slot_update(slot: u8, item_id: u16, amount: u32) -> OutgoingGamePacket {
    build_inventory_slot_update(CONTAINER_EQUIPMENT, slot as u16, item_id, amount)
}

/// Build an equipment slot clear packet
pub fn build_equipment_slot_clear(slot: u8) -> OutgoingGamePacket {
    build_equipment_slot_update(slot, 0, 0)
}

/// Build equipment bonuses packet (for equipment stats interface)
/// Format: 14 x i16 bonuses
pub fn build_equipment_bonuses(bonuses: &[i16; 14]) -> OutgoingGamePacket {
    let mut buffer = PacketBuffer::with_capacity(28);
    for bonus in bonuses {
        buffer.write_short(*bonus);
    }
    OutgoingGamePacket::fixed(
        OutgoingOpcode::InventoryUpdate.as_u8(), // Re-use inventory update opcode with special container
        buffer.as_bytes().to_vec(),
    )
}

// ============ Ground Item Packet Builders ============

/// Build a ground item spawn packet
/// Format: item_id (2), amount (4), local_x (1), local_y (1), plane (1)
/// Local coordinates are relative to the player's current region base
pub fn build_ground_item_spawn(
    item_id: u16,
    amount: u32,
    location: &Location,
) -> OutgoingGamePacket {
    let mut buffer = PacketBuffer::with_capacity(9);
    buffer.write_ushort(item_id);
    buffer.write_uint(amount);
    // Local coordinates within chunk (0-63 for each)
    buffer.write_ubyte(location.local_x());
    buffer.write_ubyte(location.local_y());
    buffer.write_ubyte(location.z);
    OutgoingGamePacket::fixed(
        OutgoingOpcode::GroundItemSpawn.as_u8(),
        buffer.as_bytes().to_vec(),
    )
}

/// Build a ground item spawn packet with full coordinates
/// Used when sending to players who might be in different regions
pub fn build_ground_item_spawn_full(
    item_id: u16,
    amount: u32,
    x: u16,
    y: u16,
    z: u8,
) -> OutgoingGamePacket {
    let mut buffer = PacketBuffer::with_capacity(11);
    buffer.write_ushort(item_id);
    buffer.write_uint(amount);
    buffer.write_ushort(x);
    buffer.write_ushort(y);
    buffer.write_ubyte(z);
    OutgoingGamePacket::variable(
        OutgoingOpcode::GroundItemSpawn.as_u8(),
        buffer.as_bytes().to_vec(),
    )
}

/// Build a ground item remove packet
/// Format: item_id (2), local_x (1), local_y (1), plane (1)
pub fn build_ground_item_remove(item_id: u16, location: &Location) -> OutgoingGamePacket {
    let mut buffer = PacketBuffer::with_capacity(5);
    buffer.write_ushort(item_id);
    buffer.write_ubyte(location.local_x());
    buffer.write_ubyte(location.local_y());
    buffer.write_ubyte(location.z);
    OutgoingGamePacket::fixed(
        OutgoingOpcode::GroundItemRemove.as_u8(),
        buffer.as_bytes().to_vec(),
    )
}

/// Build a ground item remove packet with full coordinates
pub fn build_ground_item_remove_full(item_id: u16, x: u16, y: u16, z: u8) -> OutgoingGamePacket {
    let mut buffer = PacketBuffer::with_capacity(7);
    buffer.write_ushort(item_id);
    buffer.write_ushort(x);
    buffer.write_ushort(y);
    buffer.write_ubyte(z);
    OutgoingGamePacket::variable(
        OutgoingOpcode::GroundItemRemove.as_u8(),
        buffer.as_bytes().to_vec(),
    )
}

/// Build ground item update packet (for amount changes, e.g., stacking on ground)
pub fn build_ground_item_update(
    old_item_id: u16,
    new_item_id: u16,
    new_amount: u32,
    location: &Location,
) -> OutgoingGamePacket {
    // Remove old, spawn new
    let mut buffer = PacketBuffer::with_capacity(14);
    // This is a compound packet - first remove, then spawn
    buffer.write_ushort(old_item_id);
    buffer.write_ushort(new_item_id);
    buffer.write_uint(new_amount);
    buffer.write_ubyte(location.local_x());
    buffer.write_ubyte(location.local_y());
    buffer.write_ubyte(location.z);
    OutgoingGamePacket::fixed(
        OutgoingOpcode::GroundItemSpawn.as_u8(),
        buffer.as_bytes().to_vec(),
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_packet_sizes() {
        assert_eq!(INCOMING_PACKET_SIZES[0], 0); // Keep-alive
        assert_eq!(INCOMING_PACKET_SIZES[3], 1); // Focus change
        assert_eq!(INCOMING_PACKET_SIZES[77], 0); // Map loaded
    }

    #[test]
    fn test_handler_valid_opcode() {
        let handler = GamePacketHandler::new();
        assert!(handler.is_valid_opcode(0)); // Keep-alive
        assert!(handler.is_valid_opcode(3)); // Focus change
        assert!(!handler.is_valid_opcode(255)); // Unknown
    }

    #[test]
    fn test_outgoing_packet_encode_raw() {
        let packet = OutgoingGamePacket::fixed(10, vec![1, 2, 3]);
        let encoded = packet.encode_raw();

        assert_eq!(encoded[0], 10); // opcode
        assert_eq!(encoded[1], 1); // data
        assert_eq!(encoded[2], 2);
        assert_eq!(encoded[3], 3);
    }

    #[test]
    fn test_outgoing_packet_variable_encode_raw() {
        let data = vec![1, 2, 3, 4, 5];
        let packet = OutgoingGamePacket::variable(20, data);
        let encoded = packet.encode_raw();

        assert_eq!(encoded[0], 20); // opcode
        assert_eq!(encoded[1], 5); // length
        assert_eq!(encoded[2], 1); // data start
    }

    #[test]
    fn test_incoming_packet() {
        let packet = IncomingGamePacket::new(3, vec![1]);
        assert_eq!(packet.opcode, 3);

        let mut buffer = packet.buffer();
        assert_eq!(buffer.read_ubyte(), 1);
    }

    #[test]
    fn test_build_system_message() {
        let packet = build_system_message("Hello, World!");
        assert!(packet.variable_length);
        assert!(!packet.data.is_empty());
    }

    #[test]
    fn test_build_logout() {
        let packet = build_logout();
        assert!(!packet.variable_length);
        assert!(packet.data.is_empty());
    }

    #[test]
    fn test_build_map_region() {
        let loc = Location::new(3222, 3222, 0);
        let packet = build_map_region(loc);
        assert!(!packet.variable_length);
        assert_eq!(packet.data.len(), 4);
    }

    #[test]
    fn test_build_skill_update() {
        let packet = build_skill_update(0, 99, 13034431);
        assert!(!packet.variable_length);
        assert_eq!(packet.data.len(), 6); // 1 + 1 + 4
    }

    #[test]
    fn test_build_run_energy() {
        let packet = build_run_energy(75);
        assert!(!packet.variable_length);
        assert_eq!(packet.data.len(), 1);
    }

    #[test]
    fn test_process_keepalive() {
        let handler = GamePacketHandler::new();
        let packet = IncomingGamePacket::new(0, vec![]);

        let result = handler.process(&packet);
        assert!(result.is_ok());
    }

    #[test]
    fn test_process_focus_change() {
        let handler = GamePacketHandler::new();
        let packet = IncomingGamePacket::new(3, vec![1]);

        let result = handler.process(&packet);
        assert!(result.is_ok());
    }

    #[test]
    fn test_process_walk() {
        let handler = GamePacketHandler::new();
        // Create a minimal walk packet: x (2 bytes), y (2 bytes), running (1 byte)
        let mut buffer = PacketBuffer::with_capacity(5);
        buffer.write_ushort_le(3222);
        buffer.write_short_a(3218);
        buffer.write_byte_s(0);

        let packet = IncomingGamePacket::new(14, buffer.as_bytes().to_vec());
        let result = handler.process(&packet).unwrap();

        assert!(result.movement.is_some());
        let movement = result.movement.unwrap();
        assert_eq!(movement.dest_x, 3222);
        assert_eq!(movement.dest_y, 3218);
        assert!(!movement.running);
    }

    #[test]
    fn test_packet_result_empty() {
        let result = PacketResult::empty();
        assert!(result.responses.is_empty());
        assert!(result.movement.is_none());
        assert!(result.chat_message.is_none());
        assert!(result.command.is_none());
    }

    #[test]
    fn test_packet_result_with_movement() {
        let movement = MovementRequest {
            dest_x: 100,
            dest_y: 200,
            running: true,
            waypoints: vec![],
        };
        let result = PacketResult::with_movement(movement);
        assert!(result.movement.is_some());
        assert_eq!(result.movement.as_ref().unwrap().dest_x, 100);
    }

    #[test]
    fn test_build_inventory_slot_update() {
        let packet = build_inventory_slot_update(CONTAINER_INVENTORY, 5, 1234, 100);
        assert!(!packet.variable_length);
        assert_eq!(packet.opcode, OutgoingOpcode::InventoryUpdate.as_u8());
        assert_eq!(packet.data.len(), 10); // 2 + 2 + 2 + 4
    }

    #[test]
    fn test_build_inventory_full_update() {
        let items = vec![(0, 1234, 100), (1, 5678, 50), (5, 9999, 1)];
        let packet = build_inventory_full_update(CONTAINER_INVENTORY, &items);
        assert!(packet.variable_length);
        assert_eq!(packet.opcode, OutgoingOpcode::InventoryUpdate.as_u8());
        // 2 (container_id) + 2 (count) + 3 * 8 (items) = 28
        assert_eq!(packet.data.len(), 28);
    }

    #[test]
    fn test_build_inventory_clear_slot() {
        let packet = build_inventory_clear_slot(CONTAINER_INVENTORY, 10);
        assert!(!packet.variable_length);
        // Should have item_id = 0 and amount = 0
        assert_eq!(packet.data.len(), 10);
    }

    #[test]
    fn test_container_ids() {
        assert_eq!(CONTAINER_INVENTORY, 93);
        assert_eq!(CONTAINER_EQUIPMENT, 94);
        assert_eq!(CONTAINER_BANK, 95);
    }

    #[test]
    fn test_build_bank_open() {
        let packet = build_bank_open(800);
        assert_eq!(packet.opcode, OutgoingOpcode::BankOpen.as_u8());
        assert_eq!(packet.data.len(), 2);
    }

    #[test]
    fn test_build_bank_settings() {
        let packet = build_bank_settings(true, false, 50);
        assert_eq!(packet.opcode, OutgoingOpcode::BankSettings.as_u8());
        assert_eq!(packet.data.len(), 6);
        assert_eq!(packet.data[0], 1); // note_mode = true
        assert_eq!(packet.data[1], 0); // placeholders = false
    }

    #[test]
    fn test_build_bank_slot_update() {
        let packet = build_bank_slot_update(0, 5, 995, 1000, false);
        assert_eq!(packet.opcode, OutgoingOpcode::BankUpdate.as_u8());
        assert_eq!(packet.data.len(), 10);
        assert_eq!(packet.data[0], 0); // tab
    }

    #[test]
    fn test_build_bank_slot_clear() {
        let packet = build_bank_slot_clear(0, 10);
        assert_eq!(packet.opcode, OutgoingOpcode::BankUpdate.as_u8());
        // Should have item_id = 0, amount = 0, placeholder = false
    }

    #[test]
    fn test_build_bank_full_update() {
        let bank = crate::game::bank::Bank::new();
        let packet = build_bank_full_update(&bank);
        assert_eq!(packet.opcode, OutgoingOpcode::BankUpdate.as_u8());
        // Empty bank should have 2 bytes (item count = 0)
        assert_eq!(packet.data.len(), 2);
    }

    #[test]
    fn test_build_bank_tab_info() {
        let bank = crate::game::bank::Bank::new();
        let packet = build_bank_tab_info(&bank);
        assert_eq!(packet.opcode, OutgoingOpcode::BankTabInfo.as_u8());
        // 1 byte for tab count + 9 tabs * 2 bytes each = 19 bytes
        assert_eq!(packet.data.len(), 19);
    }

    #[test]
    fn test_build_equipment_full_update() {
        let equipment = crate::game::equipment::Equipment::new();
        let packet = build_equipment_full_update(&equipment);
        assert_eq!(packet.opcode, OutgoingOpcode::InventoryUpdate.as_u8());
        // Container ID (2) + count (2) + 14 slots * 8 bytes each
        assert_eq!(packet.data.len(), 4 + 14 * 8);
    }

    #[test]
    fn test_build_equipment_slot_update() {
        let packet = build_equipment_slot_update(3, 1277, 1); // Weapon slot, bronze sword
        assert_eq!(packet.opcode, OutgoingOpcode::InventoryUpdate.as_u8());
        assert_eq!(packet.data.len(), 10);
    }

    #[test]
    fn test_build_equipment_slot_clear() {
        let packet = build_equipment_slot_clear(3); // Clear weapon slot
        assert_eq!(packet.opcode, OutgoingOpcode::InventoryUpdate.as_u8());
    }

    #[test]
    fn test_build_ground_item_spawn() {
        let location = crate::game::player::Location::new(3222, 3222, 0);
        let packet = build_ground_item_spawn(995, 1000, &location);
        assert_eq!(packet.opcode, OutgoingOpcode::GroundItemSpawn.as_u8());
        assert_eq!(packet.data.len(), 9);
    }

    #[test]
    fn test_build_ground_item_spawn_full() {
        let packet = build_ground_item_spawn_full(995, 1000, 3222, 3222, 0);
        assert_eq!(packet.opcode, OutgoingOpcode::GroundItemSpawn.as_u8());
        assert_eq!(packet.data.len(), 11);
    }

    #[test]
    fn test_build_ground_item_remove() {
        let location = crate::game::player::Location::new(3222, 3222, 0);
        let packet = build_ground_item_remove(995, &location);
        assert_eq!(packet.opcode, OutgoingOpcode::GroundItemRemove.as_u8());
        assert_eq!(packet.data.len(), 5);
    }

    #[test]
    fn test_build_ground_item_remove_full() {
        let packet = build_ground_item_remove_full(995, 3222, 3222, 0);
        assert_eq!(packet.opcode, OutgoingOpcode::GroundItemRemove.as_u8());
        assert_eq!(packet.data.len(), 7);
    }
}
