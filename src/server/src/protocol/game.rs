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
    sizes[41] = 6; // NPC action 1
    sizes[52] = -1; // Command
    sizes[77] = 0; // Map region loaded
    sizes[86] = 4; // Mouse click
    sizes[98] = 8; // Walk here
    sizes[121] = -1; // Mouse movement
    sizes[150] = 6; // Item action 1
    sizes[164] = 2; // Button click
    sizes[185] = 4; // Widget action
    sizes[210] = 0; // Close interface
    sizes[236] = 6; // Ground item action

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
            "help" | "commands" => {
                let messages = vec![
                    "Available commands:",
                    "::pos - Show current position",
                    "::tele x y [z] - Teleport to coordinates",
                    "::setlevel skill_id level - Set skill level",
                    "::energy [amount] - Set run energy",
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
}
