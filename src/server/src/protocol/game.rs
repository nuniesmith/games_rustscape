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

use tracing::{debug, trace, warn};

use crate::crypto::IsaacPair;
use crate::error::Result;
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

/// Game packet handler
pub struct GamePacketHandler {
    // Future: add references to world, player manager, etc.
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
    pub fn process(&self, packet: &IncomingGamePacket) -> Result<Option<Vec<OutgoingGamePacket>>> {
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
                    Ok(None)
                } else {
                    warn!(opcode = packet.opcode, "Unknown game packet");
                    Ok(None)
                }
            }
        }
    }

    /// Handle keep-alive packet
    fn handle_keepalive(
        &self,
        _packet: &IncomingGamePacket,
    ) -> Result<Option<Vec<OutgoingGamePacket>>> {
        trace!("Keep-alive received");
        Ok(None)
    }

    /// Handle window focus change
    fn handle_focus_change(
        &self,
        packet: &IncomingGamePacket,
    ) -> Result<Option<Vec<OutgoingGamePacket>>> {
        let mut buffer = packet.buffer();
        let focused = buffer.read_ubyte() == 1;
        trace!(focused = focused, "Focus change");
        Ok(None)
    }

    /// Handle chat message
    fn handle_chat(&self, packet: &IncomingGamePacket) -> Result<Option<Vec<OutgoingGamePacket>>> {
        let mut buffer = packet.buffer();

        // Chat format: effects (2 bytes), message (huffman encoded)
        if buffer.remaining() < 2 {
            return Ok(None);
        }

        let _effects = buffer.read_ushort();
        let message_data = buffer.read_bytes(buffer.remaining());

        // TODO: Decode huffman-encoded message
        debug!(message_len = message_data.len(), "Chat message received");

        Ok(None)
    }

    /// Handle walk/movement
    fn handle_walk(&self, packet: &IncomingGamePacket) -> Result<Option<Vec<OutgoingGamePacket>>> {
        let mut buffer = packet.buffer();

        if buffer.remaining() < 5 {
            return Ok(None);
        }

        // Read base coordinates
        let _first_step_x = buffer.read_short_le();
        let _first_step_y = buffer.read_short_a();
        let _running = buffer.read_byte_s() == 1;

        // Read additional waypoints if present
        let _num_waypoints = (buffer.remaining() / 2) as usize;

        trace!("Walk request");

        Ok(None)
    }

    /// Handle command (::command)
    fn handle_command(
        &self,
        packet: &IncomingGamePacket,
    ) -> Result<Option<Vec<OutgoingGamePacket>>> {
        let mut buffer = packet.buffer();
        let command = buffer.read_string();

        debug!(command = %command, "Command received");

        // TODO: Process commands
        // Common commands: pos, tele, item, npc, object, etc.

        Ok(None)
    }

    /// Handle map region loaded confirmation
    fn handle_map_loaded(
        &self,
        _packet: &IncomingGamePacket,
    ) -> Result<Option<Vec<OutgoingGamePacket>>> {
        trace!("Map region loaded");
        Ok(None)
    }

    /// Handle mouse click
    fn handle_mouse_click(
        &self,
        packet: &IncomingGamePacket,
    ) -> Result<Option<Vec<OutgoingGamePacket>>> {
        let mut buffer = packet.buffer();

        if buffer.remaining() < 4 {
            return Ok(None);
        }

        let _packed_data = buffer.read_int();
        // Contains: time since last click, right-click flag, x, y

        Ok(None)
    }

    /// Handle button click
    fn handle_button_click(
        &self,
        packet: &IncomingGamePacket,
    ) -> Result<Option<Vec<OutgoingGamePacket>>> {
        let mut buffer = packet.buffer();

        if buffer.remaining() < 2 {
            return Ok(None);
        }

        let button_id = buffer.read_ushort();
        debug!(button_id = button_id, "Button click");

        Ok(None)
    }

    /// Handle close interface
    fn handle_close_interface(
        &self,
        _packet: &IncomingGamePacket,
    ) -> Result<Option<Vec<OutgoingGamePacket>>> {
        trace!("Close interface");
        Ok(None)
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
}
