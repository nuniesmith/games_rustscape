//! Login initialization module
//!
//! Handles sending the initial game state to clients after successful login.
//! This includes:
//! - Map region data
//! - Player update initialization
//! - Interface setup
//! - Initial skills, inventory, etc.
//!
//! The order of packets sent after login is critical for proper client initialization.

use tracing::{debug, info};

use crate::crypto::IsaacPair;
use crate::net::buffer::PacketBuffer;

/// Default spawn location coordinates
pub const DEFAULT_SPAWN_X: u16 = 3222;
pub const DEFAULT_SPAWN_Y: u16 = 3222;
pub const DEFAULT_SPAWN_Z: u8 = 0;

/// Opcodes for outgoing packets during login initialization
pub mod opcodes {
    /// Map region packet
    pub const MAP_REGION: u8 = 73;
    /// Dynamic map region packet
    pub const DYNAMIC_MAP_REGION: u8 = 166;
    /// Player update packet
    pub const PLAYER_UPDATE: u8 = 81;
    /// NPC update packet
    pub const NPC_UPDATE: u8 = 65;
    /// Set player option (right-click menu)
    pub const PLAYER_OPTION: u8 = 104;
    /// Run energy update
    pub const RUN_ENERGY: u8 = 110;
    /// Weight update
    pub const WEIGHT: u8 = 174;
    /// Skill update
    pub const SKILL_UPDATE: u8 = 134;
    /// System message
    pub const SYSTEM_MESSAGE: u8 = 253;
    /// Root interface
    pub const ROOT_INTERFACE: u8 = 118;
    /// Inventory interface
    pub const INVENTORY_INTERFACE: u8 = 53;
    /// Reset animations
    pub const RESET_ANIMS: u8 = 1;
    /// Set interface text
    pub const INTERFACE_TEXT: u8 = 171;
    /// Config value (varp)
    pub const CONFIG: u8 = 36;
    /// Config value (varpbit)
    pub const CONFIG_BIT: u8 = 115;
    /// Access mask
    pub const ACCESS_MASK: u8 = 3;
}

/// Initial player state for login
#[derive(Debug, Clone)]
pub struct InitialPlayerState {
    /// Player index (1-2047)
    pub player_index: u16,
    /// Spawn X coordinate
    pub x: u16,
    /// Spawn Y coordinate
    pub y: u16,
    /// Spawn Z (height level)
    pub z: u8,
    /// Run energy (0-100)
    pub run_energy: u8,
    /// Weight in grams
    pub weight: i16,
    /// Player rights (0=normal, 1=mod, 2=admin)
    pub rights: u8,
    /// Member status
    pub member: bool,
}

impl Default for InitialPlayerState {
    fn default() -> Self {
        Self {
            player_index: 1,
            x: DEFAULT_SPAWN_X,
            y: DEFAULT_SPAWN_Y,
            z: DEFAULT_SPAWN_Z,
            run_energy: 100,
            weight: 0,
            rights: 0,
            member: false,
        }
    }
}

impl InitialPlayerState {
    /// Create initial state for a new player
    pub fn new(player_index: u16, rights: u8, member: bool) -> Self {
        Self {
            player_index,
            rights,
            member,
            ..Default::default()
        }
    }

    /// Set spawn location
    pub fn with_location(mut self, x: u16, y: u16, z: u8) -> Self {
        self.x = x;
        self.y = y;
        self.z = z;
        self
    }

    /// Get region X coordinate
    pub fn region_x(&self) -> u16 {
        self.x >> 3
    }

    /// Get region Y coordinate
    pub fn region_y(&self) -> u16 {
        self.y >> 3
    }

    /// Get local X within region (0-103)
    pub fn local_x(&self) -> u8 {
        ((self.x - ((self.region_x() - 6) * 8)) as u8)
    }

    /// Get local Y within region (0-103)
    pub fn local_y(&self) -> u8 {
        ((self.y - ((self.region_y() - 6) * 8)) as u8)
    }
}

/// Login initialization packet builder
pub struct LoginInitializer {
    /// Packets to send (in order)
    packets: Vec<InitPacket>,
}

/// Individual initialization packet
struct InitPacket {
    opcode: u8,
    data: Vec<u8>,
    variable_length: bool,
}

impl LoginInitializer {
    /// Create a new login initializer
    pub fn new() -> Self {
        Self {
            packets: Vec::with_capacity(32),
        }
    }

    /// Build all initialization packets for a player
    pub fn build_init_sequence(&mut self, state: &InitialPlayerState) {
        // Clear any existing packets
        self.packets.clear();

        // 1. Map region - tells client where player is
        self.add_map_region(state);

        // 2. Player option menus (right-click options on other players)
        self.add_player_options();

        // 3. Reset animations
        self.add_reset_animations();

        // 4. Run energy
        self.add_run_energy(state.run_energy);

        // 5. Weight
        self.add_weight(state.weight);

        // 6. Skills (all 25)
        self.add_all_skills();

        // 7. Welcome message
        self.add_system_message("Welcome to Rustscape!");

        if state.rights >= 2 {
            self.add_system_message("You are logged in as an administrator.");
        }

        debug!(
            player_index = state.player_index,
            packets = self.packets.len(),
            "Built login init sequence"
        );
    }

    /// Get the packets to send (encoded with ISAAC if provided)
    pub fn get_packets(&self, isaac: Option<&mut IsaacPair>) -> Vec<Vec<u8>> {
        self.packets
            .iter()
            .map(|p| {
                let mut encoded = Vec::with_capacity(p.data.len() + 3);

                // Encode opcode (with ISAAC if available)
                let opcode = if let Some(ref mut cipher) = isaac.as_deref().cloned().as_mut() {
                    cipher.encode_opcode(p.opcode)
                } else {
                    p.opcode
                };

                encoded.push(opcode);

                if p.variable_length {
                    if p.data.len() < 256 {
                        encoded.push(p.data.len() as u8);
                    } else {
                        encoded.push((p.data.len() >> 8) as u8);
                        encoded.push(p.data.len() as u8);
                    }
                }

                encoded.extend_from_slice(&p.data);
                encoded
            })
            .collect()
    }

    /// Get packets as raw bytes without ISAAC encoding
    pub fn get_packets_raw(&self) -> Vec<Vec<u8>> {
        self.get_packets(None)
    }

    /// Add map region packet
    fn add_map_region(&mut self, state: &InitialPlayerState) {
        let mut buffer = PacketBuffer::with_capacity(18);

        // Region coordinates
        let region_x = state.region_x();
        let region_y = state.region_y();

        // Local player position within the region
        // Format: region_x, local_x (in bits), local_y (in bits), region_y, height
        buffer.write_ushort(region_x);
        buffer.write_ushort(region_y);

        // In revision 530, map region also includes map keys (XTEA)
        // For now, send zeros (unencrypted maps)
        for _ in 0..4 {
            // 4 regions around player
            for _ in 0..4 {
                // 4 keys per region
                buffer.write_int(0);
            }
        }

        self.packets.push(InitPacket {
            opcode: opcodes::MAP_REGION,
            data: buffer.as_bytes().to_vec(),
            variable_length: true,
        });
    }

    /// Add player right-click options
    fn add_player_options(&mut self) {
        // Common player options: Follow, Trade, etc.
        let options = [
            (1, false, "Follow"),
            (2, false, "Trade with"),
            (3, false, "Report"),
        ];

        for (slot, top, text) in options.iter() {
            let mut buffer = PacketBuffer::with_capacity(text.len() + 4);
            buffer.write_string(text);
            buffer.write_ubyte(*slot);
            buffer.write_ubyte(if *top { 1 } else { 0 });

            self.packets.push(InitPacket {
                opcode: opcodes::PLAYER_OPTION,
                data: buffer.as_bytes().to_vec(),
                variable_length: true,
            });
        }
    }

    /// Add reset animations packet
    fn add_reset_animations(&mut self) {
        self.packets.push(InitPacket {
            opcode: opcodes::RESET_ANIMS,
            data: vec![],
            variable_length: false,
        });
    }

    /// Add run energy update
    fn add_run_energy(&mut self, energy: u8) {
        self.packets.push(InitPacket {
            opcode: opcodes::RUN_ENERGY,
            data: vec![energy],
            variable_length: false,
        });
    }

    /// Add weight update
    fn add_weight(&mut self, weight: i16) {
        let mut buffer = PacketBuffer::with_capacity(2);
        buffer.write_short(weight);

        self.packets.push(InitPacket {
            opcode: opcodes::WEIGHT,
            data: buffer.as_bytes().to_vec(),
            variable_length: false,
        });
    }

    /// Add all skill updates (initialized to level 1)
    fn add_all_skills(&mut self) {
        // 25 skills in RS2 (530 revision)
        let skills = [
            (0, 1, 0),     // Attack
            (1, 1, 0),     // Defence
            (2, 1, 0),     // Strength
            (3, 10, 1154), // Hitpoints (starts at 10)
            (4, 1, 0),     // Ranged
            (5, 1, 0),     // Prayer
            (6, 1, 0),     // Magic
            (7, 1, 0),     // Cooking
            (8, 1, 0),     // Woodcutting
            (9, 1, 0),     // Fletching
            (10, 1, 0),    // Fishing
            (11, 1, 0),    // Firemaking
            (12, 1, 0),    // Crafting
            (13, 1, 0),    // Smithing
            (14, 1, 0),    // Mining
            (15, 1, 0),    // Herblore
            (16, 1, 0),    // Agility
            (17, 1, 0),    // Thieving
            (18, 1, 0),    // Slayer
            (19, 1, 0),    // Farming
            (20, 1, 0),    // Runecrafting
            (21, 1, 0),    // Hunter
            (22, 1, 0),    // Construction
            (23, 1, 0),    // Summoning
            (24, 1, 0),    // Dungeoneering (if applicable)
        ];

        for (skill_id, level, xp) in skills.iter() {
            self.add_skill_update(*skill_id, *level, *xp);
        }
    }

    /// Add a single skill update
    fn add_skill_update(&mut self, skill_id: u8, level: u8, xp: i32) {
        let mut buffer = PacketBuffer::with_capacity(6);
        buffer.write_ubyte(skill_id);
        buffer.write_ubyte(level);
        buffer.write_int(xp);

        self.packets.push(InitPacket {
            opcode: opcodes::SKILL_UPDATE,
            data: buffer.as_bytes().to_vec(),
            variable_length: false,
        });
    }

    /// Add a system message
    fn add_system_message(&mut self, message: &str) {
        let mut buffer = PacketBuffer::with_capacity(message.len() + 2);
        buffer.write_string(message);

        self.packets.push(InitPacket {
            opcode: opcodes::SYSTEM_MESSAGE,
            data: buffer.as_bytes().to_vec(),
            variable_length: true,
        });
    }

    /// Add a config value (varp)
    pub fn add_config(&mut self, id: u16, value: i32) {
        let mut buffer = PacketBuffer::with_capacity(6);
        buffer.write_ushort(id);
        buffer.write_int(value);

        self.packets.push(InitPacket {
            opcode: opcodes::CONFIG,
            data: buffer.as_bytes().to_vec(),
            variable_length: false,
        });
    }

    /// Get the number of packets
    pub fn packet_count(&self) -> usize {
        self.packets.len()
    }
}

impl Default for LoginInitializer {
    fn default() -> Self {
        Self::new()
    }
}

/// Build a minimal player update packet
///
/// This is a simplified version that just teleports the player to their position.
/// A full implementation would include appearance, equipment, animations, etc.
pub fn build_player_update(state: &InitialPlayerState) -> Vec<u8> {
    let mut buffer = PacketBuffer::with_capacity(64);

    // Player update uses bit packing
    // This is a minimal implementation

    // Start with update flags
    let local_x = state.local_x();
    let local_y = state.local_y();
    let height = state.z;

    // For initial login, we need to teleport the player
    // Teleport flag requires: height (2 bits), x (7 bits), y (7 bits)

    // Write player update header
    // Bit 0: has self update
    // If teleport: movement type = 3, then height (2), local_x (7), local_y (7)

    // For simplicity, write a basic player update
    // In a full implementation, this would be much more complex

    // Number of local players to update (initially 0 others)
    buffer.write_ubyte(0);

    // Self update section - position update
    // This is heavily simplified

    buffer.as_bytes().to_vec()
}

/// Build a complete initial login response
///
/// Returns all packets that should be sent after login success response.
pub fn build_login_init_packets(
    player_index: u16,
    rights: u8,
    member: bool,
    isaac: Option<&mut IsaacPair>,
) -> Vec<Vec<u8>> {
    let state = InitialPlayerState::new(player_index, rights, member);

    let mut initializer = LoginInitializer::new();
    initializer.build_init_sequence(&state);

    info!(
        player_index = player_index,
        packets = initializer.packet_count(),
        "Generated login init packets"
    );

    initializer.get_packets(isaac)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_initial_player_state_default() {
        let state = InitialPlayerState::default();
        assert_eq!(state.x, DEFAULT_SPAWN_X);
        assert_eq!(state.y, DEFAULT_SPAWN_Y);
        assert_eq!(state.z, DEFAULT_SPAWN_Z);
        assert_eq!(state.run_energy, 100);
    }

    #[test]
    fn test_initial_player_state_with_location() {
        let state = InitialPlayerState::default().with_location(3200, 3200, 1);
        assert_eq!(state.x, 3200);
        assert_eq!(state.y, 3200);
        assert_eq!(state.z, 1);
    }

    #[test]
    fn test_region_coordinates() {
        let state = InitialPlayerState::default().with_location(3222, 3222, 0);
        // 3222 >> 3 = 402
        assert_eq!(state.region_x(), 402);
        assert_eq!(state.region_y(), 402);
    }

    #[test]
    fn test_login_initializer_build() {
        let state = InitialPlayerState::new(1, 0, false);
        let mut initializer = LoginInitializer::new();
        initializer.build_init_sequence(&state);

        // Should have multiple packets
        assert!(initializer.packet_count() > 10);
    }

    #[test]
    fn test_login_initializer_get_packets_raw() {
        let state = InitialPlayerState::new(1, 0, false);
        let mut initializer = LoginInitializer::new();
        initializer.build_init_sequence(&state);

        let packets = initializer.get_packets_raw();
        assert!(!packets.is_empty());

        // First packet should be map region
        assert_eq!(packets[0][0], opcodes::MAP_REGION);
    }

    #[test]
    fn test_build_login_init_packets() {
        let packets = build_login_init_packets(1, 0, false, None);
        assert!(!packets.is_empty());
    }

    #[test]
    fn test_admin_welcome_message() {
        let state = InitialPlayerState::new(1, 2, true); // Admin
        let mut initializer = LoginInitializer::new();
        initializer.build_init_sequence(&state);

        let packets = initializer.get_packets_raw();

        // Should have admin message (look for system message packets)
        let system_messages: Vec<_> = packets
            .iter()
            .filter(|p| p[0] == opcodes::SYSTEM_MESSAGE)
            .collect();

        // Should have at least 2 messages (welcome + admin notice)
        assert!(system_messages.len() >= 2);
    }

    #[test]
    fn test_skill_packets() {
        let state = InitialPlayerState::new(1, 0, false);
        let mut initializer = LoginInitializer::new();
        initializer.build_init_sequence(&state);

        let packets = initializer.get_packets_raw();

        // Count skill update packets
        let skill_packets: Vec<_> = packets
            .iter()
            .filter(|p| p[0] == opcodes::SKILL_UPDATE)
            .collect();

        // Should have 25 skill packets (one per skill)
        assert_eq!(skill_packets.len(), 25);
    }
}
