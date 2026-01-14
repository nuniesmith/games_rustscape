//! Packet definitions module
//!
//! Defines packet headers, sizes, and common packet structures used
//! throughout the Rustscape protocol.

use std::collections::HashMap;
use std::sync::OnceLock;

use crate::net::buffer::PacketBuffer;

/// Packet size type
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PacketSize {
    /// Fixed size packet
    Fixed(usize),
    /// Variable size (1 byte length prefix)
    VariableByte,
    /// Variable size (2 byte length prefix)
    VariableShort,
    /// Unknown/unhandled packet
    Unknown,
}

impl PacketSize {
    /// Get the fixed size value, if applicable
    pub fn fixed_size(&self) -> Option<usize> {
        match self {
            PacketSize::Fixed(size) => Some(*size),
            _ => None,
        }
    }

    /// Check if this is a variable length packet
    pub fn is_variable(&self) -> bool {
        matches!(self, PacketSize::VariableByte | PacketSize::VariableShort)
    }

    /// Check if this is a fixed length packet
    pub fn is_fixed(&self) -> bool {
        matches!(self, PacketSize::Fixed(_))
    }
}

/// Packet header type
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PacketHeader {
    /// No header (raw opcode only)
    None,
    /// Standard header (opcode)
    Standard,
    /// Header with byte-sized length
    Byte,
    /// Header with short-sized length
    Short,
}

/// Incoming packet trait
pub trait IncomingPacket: Sized {
    /// The packet opcode
    const OPCODE: u8;

    /// The packet size type
    const SIZE: PacketSize;

    /// Decode the packet from a buffer
    fn decode(buffer: &mut PacketBuffer) -> Result<Self, PacketDecodeError>;

    /// Get the opcode for this packet type
    fn opcode() -> u8 {
        Self::OPCODE
    }

    /// Get the size type for this packet
    fn size() -> PacketSize {
        Self::SIZE
    }
}

/// Outgoing packet trait
pub trait OutgoingPacket {
    /// The packet opcode
    const OPCODE: u8;

    /// The packet size type
    const SIZE: PacketSize;

    /// Encode the packet to a buffer
    fn encode(&self, buffer: &mut PacketBuffer);

    /// Get the opcode for this packet type
    fn opcode() -> u8 {
        Self::OPCODE
    }

    /// Get the size type for this packet
    fn size() -> PacketSize {
        Self::SIZE
    }

    /// Encode to a new buffer
    fn to_buffer(&self) -> PacketBuffer {
        let mut buffer = PacketBuffer::with_capacity(256);
        self.encode(&mut buffer);
        buffer
    }
}

/// Packet decode error
#[derive(Debug, Clone)]
pub enum PacketDecodeError {
    /// Not enough data in buffer
    InsufficientData { expected: usize, actual: usize },
    /// Invalid field value
    InvalidValue { field: String, value: String },
    /// Malformed packet structure
    Malformed(String),
}

impl std::fmt::Display for PacketDecodeError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            PacketDecodeError::InsufficientData { expected, actual } => {
                write!(
                    f,
                    "Insufficient data: expected {} bytes, got {}",
                    expected, actual
                )
            }
            PacketDecodeError::InvalidValue { field, value } => {
                write!(f, "Invalid value for field '{}': {}", field, value)
            }
            PacketDecodeError::Malformed(msg) => {
                write!(f, "Malformed packet: {}", msg)
            }
        }
    }
}

impl std::error::Error for PacketDecodeError {}

// ============ Common Incoming Packets ============

/// Keep-alive/ping packet (opcode 0)
#[derive(Debug, Clone, Default)]
pub struct KeepAlivePacket;

impl IncomingPacket for KeepAlivePacket {
    const OPCODE: u8 = 0;
    const SIZE: PacketSize = PacketSize::Fixed(0);

    fn decode(_buffer: &mut PacketBuffer) -> Result<Self, PacketDecodeError> {
        Ok(Self)
    }
}

/// Window focus change packet (opcode 3)
#[derive(Debug, Clone)]
pub struct FocusChangePacket {
    pub focused: bool,
}

impl IncomingPacket for FocusChangePacket {
    const OPCODE: u8 = 3;
    const SIZE: PacketSize = PacketSize::Fixed(1);

    fn decode(buffer: &mut PacketBuffer) -> Result<Self, PacketDecodeError> {
        if buffer.remaining() < 1 {
            return Err(PacketDecodeError::InsufficientData {
                expected: 1,
                actual: buffer.remaining(),
            });
        }
        Ok(Self {
            focused: buffer.read_ubyte() == 1,
        })
    }
}

/// Chat message packet (opcode 4)
#[derive(Debug, Clone)]
pub struct ChatPacket {
    pub effects: u16,
    pub message_data: Vec<u8>,
}

impl IncomingPacket for ChatPacket {
    const OPCODE: u8 = 4;
    const SIZE: PacketSize = PacketSize::VariableByte;

    fn decode(buffer: &mut PacketBuffer) -> Result<Self, PacketDecodeError> {
        if buffer.remaining() < 2 {
            return Err(PacketDecodeError::InsufficientData {
                expected: 2,
                actual: buffer.remaining(),
            });
        }
        let effects = buffer.read_ushort();
        let message_data = buffer.read_bytes(buffer.remaining());
        Ok(Self {
            effects,
            message_data,
        })
    }
}

/// Command packet (opcode 52)
#[derive(Debug, Clone)]
pub struct CommandPacket {
    pub command: String,
}

impl IncomingPacket for CommandPacket {
    const OPCODE: u8 = 52;
    const SIZE: PacketSize = PacketSize::VariableByte;

    fn decode(buffer: &mut PacketBuffer) -> Result<Self, PacketDecodeError> {
        Ok(Self {
            command: buffer.read_string(),
        })
    }
}

/// Map region loaded packet (opcode 77)
#[derive(Debug, Clone, Default)]
pub struct MapLoadedPacket;

impl IncomingPacket for MapLoadedPacket {
    const OPCODE: u8 = 77;
    const SIZE: PacketSize = PacketSize::Fixed(0);

    fn decode(_buffer: &mut PacketBuffer) -> Result<Self, PacketDecodeError> {
        Ok(Self)
    }
}

/// Close interface packet (opcode 210)
#[derive(Debug, Clone, Default)]
pub struct CloseInterfacePacket;

impl IncomingPacket for CloseInterfacePacket {
    const OPCODE: u8 = 210;
    const SIZE: PacketSize = PacketSize::Fixed(0);

    fn decode(_buffer: &mut PacketBuffer) -> Result<Self, PacketDecodeError> {
        Ok(Self)
    }
}

/// Button click packet (opcode 164)
#[derive(Debug, Clone)]
pub struct ButtonClickPacket {
    pub button_id: u16,
}

impl IncomingPacket for ButtonClickPacket {
    const OPCODE: u8 = 164;
    const SIZE: PacketSize = PacketSize::Fixed(2);

    fn decode(buffer: &mut PacketBuffer) -> Result<Self, PacketDecodeError> {
        if buffer.remaining() < 2 {
            return Err(PacketDecodeError::InsufficientData {
                expected: 2,
                actual: buffer.remaining(),
            });
        }
        Ok(Self {
            button_id: buffer.read_ushort(),
        })
    }
}

// ============ Common Outgoing Packets ============

/// System message packet (opcode 253)
#[derive(Debug, Clone)]
pub struct SystemMessagePacket {
    pub message: String,
}

impl OutgoingPacket for SystemMessagePacket {
    const OPCODE: u8 = 253;
    const SIZE: PacketSize = PacketSize::VariableByte;

    fn encode(&self, buffer: &mut PacketBuffer) {
        buffer.write_string(&self.message);
    }
}

impl SystemMessagePacket {
    pub fn new(message: impl Into<String>) -> Self {
        Self {
            message: message.into(),
        }
    }
}

/// Logout packet (opcode 86)
#[derive(Debug, Clone, Default)]
pub struct LogoutPacket;

impl OutgoingPacket for LogoutPacket {
    const OPCODE: u8 = 86;
    const SIZE: PacketSize = PacketSize::Fixed(0);

    fn encode(&self, _buffer: &mut PacketBuffer) {
        // No data
    }
}

/// Run energy update packet (opcode 110)
#[derive(Debug, Clone)]
pub struct RunEnergyPacket {
    pub energy: u8,
}

impl OutgoingPacket for RunEnergyPacket {
    const OPCODE: u8 = 110;
    const SIZE: PacketSize = PacketSize::Fixed(1);

    fn encode(&self, buffer: &mut PacketBuffer) {
        buffer.write_ubyte(self.energy);
    }
}

impl RunEnergyPacket {
    pub fn new(energy: u8) -> Self {
        Self { energy }
    }
}

/// Weight update packet (opcode 174)
#[derive(Debug, Clone)]
pub struct WeightPacket {
    pub weight: i16,
}

impl OutgoingPacket for WeightPacket {
    const OPCODE: u8 = 174;
    const SIZE: PacketSize = PacketSize::Fixed(2);

    fn encode(&self, buffer: &mut PacketBuffer) {
        buffer.write_short(self.weight);
    }
}

impl WeightPacket {
    pub fn new(weight: i16) -> Self {
        Self { weight }
    }
}

/// Skill update packet (opcode 134)
#[derive(Debug, Clone)]
pub struct SkillUpdatePacket {
    pub skill_id: u8,
    pub level: u8,
    pub experience: i32,
}

impl OutgoingPacket for SkillUpdatePacket {
    const OPCODE: u8 = 134;
    const SIZE: PacketSize = PacketSize::Fixed(6);

    fn encode(&self, buffer: &mut PacketBuffer) {
        buffer.write_ubyte(self.skill_id);
        buffer.write_ubyte(self.level);
        buffer.write_int(self.experience);
    }
}

impl SkillUpdatePacket {
    pub fn new(skill_id: u8, level: u8, experience: i32) -> Self {
        Self {
            skill_id,
            level,
            experience,
        }
    }
}

// ============ Packet Registry ============

/// Static packet size registry for incoming packets
static INCOMING_SIZES: OnceLock<HashMap<u8, PacketSize>> = OnceLock::new();

/// Get the incoming packet sizes registry
pub fn incoming_packet_sizes() -> &'static HashMap<u8, PacketSize> {
    INCOMING_SIZES.get_or_init(|| {
        let mut map = HashMap::new();

        // Register known packet sizes
        map.insert(0, PacketSize::Fixed(0)); // Keep-alive
        map.insert(3, PacketSize::Fixed(1)); // Focus change
        map.insert(4, PacketSize::VariableByte); // Chat
        map.insert(14, PacketSize::Fixed(8)); // Walk to position
        map.insert(17, PacketSize::Fixed(2)); // NPC examine
        map.insert(21, PacketSize::Fixed(2)); // Item examine
        map.insert(39, PacketSize::Fixed(6)); // Object action 1
        map.insert(41, PacketSize::Fixed(6)); // NPC action 1
        map.insert(52, PacketSize::VariableByte); // Command
        map.insert(77, PacketSize::Fixed(0)); // Map region loaded
        map.insert(86, PacketSize::Fixed(4)); // Mouse click
        map.insert(98, PacketSize::Fixed(8)); // Walk here
        map.insert(121, PacketSize::VariableByte); // Mouse movement
        map.insert(150, PacketSize::Fixed(6)); // Item action 1
        map.insert(164, PacketSize::Fixed(2)); // Button click
        map.insert(185, PacketSize::Fixed(4)); // Widget action
        map.insert(210, PacketSize::Fixed(0)); // Close interface
        map.insert(236, PacketSize::Fixed(6)); // Ground item action

        map
    })
}

/// Get the packet size for an opcode
pub fn get_packet_size(opcode: u8) -> PacketSize {
    incoming_packet_sizes()
        .get(&opcode)
        .copied()
        .unwrap_or(PacketSize::Unknown)
}

/// Check if a packet opcode is known
pub fn is_known_packet(opcode: u8) -> bool {
    incoming_packet_sizes().contains_key(&opcode)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_packet_size_fixed() {
        let size = PacketSize::Fixed(10);
        assert!(size.is_fixed());
        assert!(!size.is_variable());
        assert_eq!(size.fixed_size(), Some(10));
    }

    #[test]
    fn test_packet_size_variable() {
        let size = PacketSize::VariableByte;
        assert!(!size.is_fixed());
        assert!(size.is_variable());
        assert_eq!(size.fixed_size(), None);
    }

    #[test]
    fn test_keepalive_decode() {
        let mut buffer = PacketBuffer::new();
        let packet = KeepAlivePacket::decode(&mut buffer).unwrap();
        assert_eq!(KeepAlivePacket::OPCODE, 0);
    }

    #[test]
    fn test_focus_change_decode() {
        let mut buffer = PacketBuffer::from_bytes(&[1]);
        let packet = FocusChangePacket::decode(&mut buffer).unwrap();
        assert!(packet.focused);

        let mut buffer = PacketBuffer::from_bytes(&[0]);
        let packet = FocusChangePacket::decode(&mut buffer).unwrap();
        assert!(!packet.focused);
    }

    #[test]
    fn test_command_decode() {
        let mut buffer = PacketBuffer::new();
        buffer.write_string("test command");
        buffer.reset();

        let packet = CommandPacket::decode(&mut buffer).unwrap();
        assert_eq!(packet.command, "test command");
    }

    #[test]
    fn test_system_message_encode() {
        let packet = SystemMessagePacket::new("Hello, World!");
        let buffer = packet.to_buffer();

        assert!(!buffer.is_empty());
    }

    #[test]
    fn test_skill_update_encode() {
        let packet = SkillUpdatePacket::new(0, 99, 13034431);
        let buffer = packet.to_buffer();

        assert_eq!(buffer.len(), 6);
    }

    #[test]
    fn test_packet_registry() {
        assert_eq!(get_packet_size(0), PacketSize::Fixed(0));
        assert_eq!(get_packet_size(4), PacketSize::VariableByte);
        assert_eq!(get_packet_size(255), PacketSize::Unknown);

        assert!(is_known_packet(0));
        assert!(is_known_packet(52));
        assert!(!is_known_packet(255));
    }

    #[test]
    fn test_button_click_decode() {
        let mut buffer = PacketBuffer::new();
        buffer.write_ushort(1234);
        buffer.reset();

        let packet = ButtonClickPacket::decode(&mut buffer).unwrap();
        assert_eq!(packet.button_id, 1234);
    }

    #[test]
    fn test_insufficient_data_error() {
        let mut buffer = PacketBuffer::new();
        let result = FocusChangePacket::decode(&mut buffer);

        assert!(matches!(
            result,
            Err(PacketDecodeError::InsufficientData { .. })
        ));
    }
}
