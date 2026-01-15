//! Player synchronization manager
//!
//! Handles building and sending player update packets to all connected clients.
//! Each game tick, this builds a player update packet (opcode 81) for each
//! connected player containing:
//! - Their own movement/teleport
//! - Other players entering/leaving their viewport
//! - Update blocks for all visible players (appearance, animation, chat, etc.)

use std::collections::{HashMap, HashSet};
use std::sync::Arc;

use parking_lot::RwLock;
use tracing::{debug, trace, warn};

use crate::error::Result;
use crate::game::player::{Appearance, Location, Player, PlayerManager};
use crate::net::buffer::PacketBuffer;

use super::update_flags::PlayerUpdateData;

/// Maximum number of local players a client can track
pub const MAX_LOCAL_PLAYERS: usize = 255;

/// View distance in tiles (how far players can see each other)
pub const VIEW_DISTANCE: u16 = 15;

/// Player update packet opcode
pub const PLAYER_UPDATE_OPCODE: u8 = 81;

/// Synchronization configuration
#[derive(Debug, Clone)]
pub struct SyncConfig {
    /// Maximum local players per client
    pub max_local_players: usize,
    /// View distance in tiles
    pub view_distance: u16,
    /// Whether to send appearance on first add
    pub send_appearance_on_add: bool,
}

impl Default for SyncConfig {
    fn default() -> Self {
        Self {
            max_local_players: MAX_LOCAL_PLAYERS,
            view_distance: VIEW_DISTANCE,
            send_appearance_on_add: true,
        }
    }
}

/// Movement type for player updates
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MovementType {
    /// No movement this tick
    None,
    /// Walking (1 tile)
    Walk(u8), // direction 0-7
    /// Running (2 tiles)
    Run(u8, u8), // two directions
    /// Teleport to new location
    Teleport,
}

/// Per-player synchronization state
#[derive(Debug, Clone)]
pub struct PlayerSyncState {
    /// Player index this state belongs to
    pub player_index: u16,
    /// Set of player indices in this player's local list
    pub local_players: HashSet<u16>,
    /// Players being added this tick
    pub players_to_add: Vec<u16>,
    /// Players being removed this tick
    pub players_to_remove: Vec<u16>,
    /// Current update data for this player
    pub update_data: PlayerUpdateData,
    /// Movement this tick
    pub movement: MovementType,
    /// Whether player teleported this tick
    pub teleported: bool,
    /// Whether appearance needs to be sent (new player or changed)
    pub appearance_updated: bool,
    /// Last known location (for delta calculation)
    pub last_location: Location,
}

impl PlayerSyncState {
    /// Create new sync state for a player
    pub fn new(player_index: u16, location: Location) -> Self {
        Self {
            player_index,
            local_players: HashSet::with_capacity(MAX_LOCAL_PLAYERS),
            players_to_add: Vec::with_capacity(32),
            players_to_remove: Vec::with_capacity(32),
            update_data: PlayerUpdateData::new(),
            movement: MovementType::None,
            teleported: false,
            appearance_updated: true, // Send appearance on first update
            last_location: location,
        }
    }

    /// Reset per-tick state
    pub fn reset_tick(&mut self) {
        self.players_to_add.clear();
        self.players_to_remove.clear();
        self.update_data.reset();
        self.movement = MovementType::None;
        self.teleported = false;
        self.appearance_updated = false;
    }

    /// Check if a player is in local list
    pub fn has_local_player(&self, index: u16) -> bool {
        self.local_players.contains(&index)
    }

    /// Add a player to local list
    pub fn add_local_player(&mut self, index: u16) {
        if self.local_players.len() < MAX_LOCAL_PLAYERS {
            self.local_players.insert(index);
            self.players_to_add.push(index);
        }
    }

    /// Remove a player from local list
    pub fn remove_local_player(&mut self, index: u16) {
        if self.local_players.remove(&index) {
            self.players_to_remove.push(index);
        }
    }

    /// Get local player count
    pub fn local_player_count(&self) -> usize {
        self.local_players.len()
    }
}

/// Player synchronization manager
///
/// Manages sync state for all players and builds update packets
pub struct PlayerSyncManager {
    /// Configuration
    config: SyncConfig,
    /// Sync state per player (indexed by player index)
    states: RwLock<HashMap<u16, PlayerSyncState>>,
}

impl PlayerSyncManager {
    /// Create a new sync manager
    pub fn new() -> Self {
        Self::with_config(SyncConfig::default())
    }

    /// Create with custom configuration
    pub fn with_config(config: SyncConfig) -> Self {
        Self {
            config,
            states: RwLock::new(HashMap::new()),
        }
    }

    /// Register a new player for synchronization
    pub fn register(&self, player: &Player) {
        let state = PlayerSyncState::new(player.index, player.location());
        self.states.write().insert(player.index, state);
        debug!(player_index = player.index, "Registered player for sync");
    }

    /// Unregister a player from synchronization
    pub fn unregister(&self, player_index: u16) {
        // Remove from all other players' local lists
        {
            let mut states = self.states.write();
            for (_, state) in states.iter_mut() {
                state.local_players.remove(&player_index);
            }
            states.remove(&player_index);
        }
        debug!(player_index = player_index, "Unregistered player from sync");
    }

    /// Flag a player's appearance as updated
    pub fn flag_appearance_update(&self, player_index: u16) {
        if let Some(state) = self.states.write().get_mut(&player_index) {
            state.update_data.flag_appearance();
            state.appearance_updated = true;
        }
    }

    /// Set animation for a player
    pub fn set_animation(&self, player_index: u16, animation_id: i16, delay: u8) {
        if let Some(state) = self.states.write().get_mut(&player_index) {
            state.update_data.set_animation(animation_id, delay);
        }
    }

    /// Set graphics for a player
    pub fn set_graphics(&self, player_index: u16, graphics_id: u16, height: u16, delay: u16) {
        if let Some(state) = self.states.write().get_mut(&player_index) {
            state.update_data.set_graphics(graphics_id, height, delay);
        }
    }

    /// Set chat for a player
    pub fn set_chat(&self, player_index: u16, effects: u16, rights: u8, message: Vec<u8>) {
        if let Some(state) = self.states.write().get_mut(&player_index) {
            state.update_data.set_chat(effects, rights, message);
        }
    }

    /// Set hit for a player
    pub fn set_hit(
        &self,
        player_index: u16,
        damage: u16,
        hit_type: u8,
        current_hp: u16,
        max_hp: u16,
    ) {
        if let Some(state) = self.states.write().get_mut(&player_index) {
            state
                .update_data
                .set_hit(damage, hit_type, current_hp, max_hp);
        }
    }

    /// Set force chat for a player
    pub fn set_force_chat(&self, player_index: u16, text: String) {
        if let Some(state) = self.states.write().get_mut(&player_index) {
            state.update_data.set_force_chat(text);
        }
    }

    /// Mark a player as teleported
    pub fn set_teleported(&self, player_index: u16) {
        if let Some(state) = self.states.write().get_mut(&player_index) {
            state.teleported = true;
            state.movement = MovementType::Teleport;
        }
    }

    /// Set walking movement for a player
    pub fn set_walk(&self, player_index: u16, direction: u8) {
        if let Some(state) = self.states.write().get_mut(&player_index) {
            state.movement = MovementType::Walk(direction);
        }
    }

    /// Set running movement for a player
    pub fn set_run(&self, player_index: u16, dir1: u8, dir2: u8) {
        if let Some(state) = self.states.write().get_mut(&player_index) {
            state.movement = MovementType::Run(dir1, dir2);
        }
    }

    /// Process synchronization for all players
    ///
    /// This should be called once per game tick. It:
    /// 1. Updates local player lists based on proximity
    /// 2. Builds player update packets for each player
    /// 3. Returns a map of player_index -> encoded packet data
    pub fn process_tick(&self, players: &PlayerManager) -> HashMap<u16, Vec<u8>> {
        let mut packets = HashMap::new();

        // First pass: detect movement/teleports by comparing locations
        self.detect_movement(players);

        // Second pass: update local player lists based on proximity
        self.update_local_players(players);

        // Third pass: build update packets
        let states = self.states.read();
        for (&player_index, state) in states.iter() {
            if let Some(player) = players.get(player_index) {
                match self.build_update_packet(&player, state, players, &states) {
                    Ok(packet_data) => {
                        packets.insert(player_index, packet_data);
                    }
                    Err(e) => {
                        warn!(
                            player_index = player_index,
                            error = %e,
                            "Failed to build player update packet"
                        );
                    }
                }
            }
        }

        // Fourth pass: update last known locations and reset tick state
        drop(states);
        let mut states = self.states.write();
        for (player_index, state) in states.iter_mut() {
            if let Some(player) = players.get(*player_index) {
                state.last_location = player.location();
            }
            state.reset_tick();
        }

        packets
    }

    /// Detect movement by comparing current location to last known location
    fn detect_movement(&self, players: &PlayerManager) {
        let mut states = self.states.write();

        for (player_index, state) in states.iter_mut() {
            let current_location = match players.get(*player_index) {
                Some(p) => p.location(),
                None => continue,
            };

            // Check if location changed
            if current_location.x != state.last_location.x
                || current_location.y != state.last_location.y
                || current_location.z != state.last_location.z
            {
                let dx = (current_location.x as i32 - state.last_location.x as i32).abs();
                let dy = (current_location.y as i32 - state.last_location.y as i32).abs();
                let dz = (current_location.z as i32 - state.last_location.z as i32).abs();

                // If moved more than 2 tiles or changed height, it's a teleport
                // Otherwise check for walk/run
                if dz > 0 || dx > 2 || dy > 2 {
                    state.movement = MovementType::Teleport;
                    state.teleported = true;
                    trace!(
                        player = *player_index,
                        from = %state.last_location,
                        to = %current_location,
                        "Player teleported"
                    );
                } else if dx <= 1 && dy <= 1 && (dx > 0 || dy > 0) {
                    // Walking - calculate direction
                    let dir = self.calculate_direction(
                        state.last_location.x,
                        state.last_location.y,
                        current_location.x,
                        current_location.y,
                    );
                    state.movement = MovementType::Walk(dir);
                } else if dx <= 2 && dy <= 2 {
                    // Running - for now treat as teleport if > 1 tile
                    // A proper implementation would track the intermediate step
                    state.movement = MovementType::Teleport;
                    state.teleported = true;
                }
            }
        }
    }

    /// Calculate movement direction from one tile to an adjacent tile
    fn calculate_direction(&self, from_x: u16, from_y: u16, to_x: u16, to_y: u16) -> u8 {
        let dx = to_x as i32 - from_x as i32;
        let dy = to_y as i32 - from_y as i32;

        // Direction mappings for RS2:
        // 0 = NW, 1 = N, 2 = NE
        // 3 = W,       4 = E
        // 5 = SW, 6 = S, 7 = SE
        match (dx, dy) {
            (-1, 1) => 0,  // NW
            (0, 1) => 1,   // N
            (1, 1) => 2,   // NE
            (-1, 0) => 3,  // W
            (1, 0) => 4,   // E
            (-1, -1) => 5, // SW
            (0, -1) => 6,  // S
            (1, -1) => 7,  // SE
            _ => 1,        // Default to N
        }
    }

    /// Update local player lists based on proximity
    fn update_local_players(&self, players: &PlayerManager) {
        let mut states = self.states.write();
        let player_indices: Vec<u16> = states.keys().copied().collect();

        for &player_index in &player_indices {
            let player_location = match players.get(player_index) {
                Some(p) => p.location(),
                None => continue,
            };

            // Get mutable reference to this player's state
            let state = match states.get_mut(&player_index) {
                Some(s) => s,
                None => continue,
            };

            // Check each other player for visibility
            for &other_index in &player_indices {
                if other_index == player_index {
                    continue; // Skip self
                }

                let other_location = match players.get(other_index) {
                    Some(p) => p.location(),
                    None => {
                        // Player no longer exists, remove from local list
                        state.remove_local_player(other_index);
                        continue;
                    }
                };

                let in_range =
                    player_location.within_distance(&other_location, self.config.view_distance);

                if in_range {
                    // Should be in local list
                    if !state.has_local_player(other_index) {
                        state.add_local_player(other_index);
                        trace!(
                            player = player_index,
                            other = other_index,
                            "Adding player to local list"
                        );
                    }
                } else {
                    // Should not be in local list
                    if state.has_local_player(other_index) {
                        state.remove_local_player(other_index);
                        trace!(
                            player = player_index,
                            other = other_index,
                            "Removing player from local list"
                        );
                    }
                }
            }
        }
    }

    /// Build a player update packet for a specific player
    fn build_update_packet(
        &self,
        player: &Arc<Player>,
        state: &PlayerSyncState,
        players: &PlayerManager,
        all_states: &HashMap<u16, PlayerSyncState>,
    ) -> Result<Vec<u8>> {
        let mut main_buffer = PacketBuffer::with_capacity(2048);
        let mut update_buffer = PacketBuffer::with_capacity(4096);

        // Start bit access for the main buffer
        let mut bit_buffer = BitBuffer::new();

        // === Local Player Update (self) ===
        self.write_local_player_update(
            &mut bit_buffer,
            &mut update_buffer,
            player,
            state,
            all_states,
        )?;

        // === Other Players Update ===
        // First, write updates for existing local players
        self.write_other_players_update(
            &mut bit_buffer,
            &mut update_buffer,
            player,
            state,
            players,
            all_states,
        )?;

        // Finish bit buffer and write to main
        bit_buffer.finish(&mut main_buffer);

        // Append update blocks
        main_buffer.write_bytes(update_buffer.as_bytes());

        // Build final packet with opcode and variable length
        let mut final_packet = PacketBuffer::with_capacity(main_buffer.len() + 3);
        final_packet.write_ubyte(PLAYER_UPDATE_OPCODE);
        // Variable short length
        let len = main_buffer.len() as u16;
        final_packet.write_ushort(len);
        final_packet.write_bytes(main_buffer.as_bytes());

        Ok(final_packet.as_bytes().to_vec())
    }

    /// Write the local player (self) update section
    fn write_local_player_update(
        &self,
        bits: &mut BitBuffer,
        updates: &mut PacketBuffer,
        player: &Arc<Player>,
        state: &PlayerSyncState,
        _all_states: &HashMap<u16, PlayerSyncState>,
    ) -> Result<()> {
        let has_update = state.update_data.has_updates() || state.appearance_updated;

        // Check if we need to write anything for the local player
        if state.movement == MovementType::None && !has_update {
            // No update needed
            bits.write(1, 0);
        } else {
            // Update needed
            bits.write(1, 1);

            match state.movement {
                MovementType::None => {
                    // No movement, just update block
                    bits.write(2, 0);
                }
                MovementType::Walk(dir) => {
                    // Walking
                    bits.write(2, 1);
                    bits.write(3, dir as u32);
                    bits.write(1, if has_update { 1 } else { 0 });
                }
                MovementType::Run(dir1, dir2) => {
                    // Running
                    bits.write(2, 2);
                    bits.write(3, dir1 as u32);
                    bits.write(3, dir2 as u32);
                    bits.write(1, if has_update { 1 } else { 0 });
                }
                MovementType::Teleport => {
                    // Teleport
                    bits.write(2, 3);
                    let loc = player.location();
                    bits.write(2, loc.z as u32);
                    bits.write(1, 1); // Local teleport flag
                    bits.write(1, if has_update { 1 } else { 0 });
                    bits.write(7, loc.local_x() as u32);
                    bits.write(7, loc.local_y() as u32);
                }
            }

            // Write update block if needed
            if has_update {
                self.write_update_block(updates, player, state)?;
            }
        }

        Ok(())
    }

    /// Write updates for other players in local list
    fn write_other_players_update(
        &self,
        bits: &mut BitBuffer,
        updates: &mut PacketBuffer,
        _player: &Arc<Player>,
        state: &PlayerSyncState,
        players: &PlayerManager,
        all_states: &HashMap<u16, PlayerSyncState>,
    ) -> Result<()> {
        // Write current local player count (excluding self)
        bits.write(8, state.local_players.len() as u32);

        // Update existing local players
        for &other_index in &state.local_players {
            // Check if player should be removed
            if state.players_to_remove.contains(&other_index) {
                // Remove this player
                bits.write(1, 1); // Has update
                bits.write(2, 3); // Remove type
                continue;
            }

            // Get other player and their state
            let (other_player, other_state) =
                match (players.get(other_index), all_states.get(&other_index)) {
                    (Some(p), Some(s)) => (p, s),
                    _ => {
                        // Player doesn't exist, mark for removal
                        bits.write(1, 1);
                        bits.write(2, 3);
                        continue;
                    }
                };

            let other_has_update = other_state.update_data.has_updates();

            // Check movement type
            match other_state.movement {
                MovementType::None => {
                    if other_has_update {
                        bits.write(1, 1); // Has update
                        bits.write(2, 0); // No movement
                    } else {
                        bits.write(1, 0); // No update
                    }
                }
                MovementType::Walk(dir) => {
                    bits.write(1, 1); // Has update
                    bits.write(2, 1); // Walk
                    bits.write(3, dir as u32);
                    bits.write(1, if other_has_update { 1 } else { 0 });
                }
                MovementType::Run(dir1, dir2) => {
                    bits.write(1, 1); // Has update
                    bits.write(2, 2); // Run
                    bits.write(3, dir1 as u32);
                    bits.write(3, dir2 as u32);
                    bits.write(1, if other_has_update { 1 } else { 0 });
                }
                MovementType::Teleport => {
                    // For teleport, we remove and re-add the player
                    bits.write(1, 1);
                    bits.write(2, 3); // Remove
                    continue;
                }
            }

            // Write update block if needed
            if other_has_update {
                self.write_update_block(updates, &other_player, other_state)?;
            }
        }

        // Add new players
        for &add_index in &state.players_to_add {
            let (other_player, other_state) =
                match (players.get(add_index), all_states.get(&add_index)) {
                    (Some(p), Some(s)) => (p, s),
                    _ => continue,
                };

            let other_loc = other_player.location();
            let player_loc = state.last_location;

            // Calculate position delta
            let dx = (other_loc.x as i32 - player_loc.x as i32) as i16;
            let dy = (other_loc.y as i32 - player_loc.y as i32) as i16;

            // Write add player
            bits.write(11, add_index as u32); // Player index
            bits.write(1, 1); // Has update (always send appearance for new players)
            bits.write(1, 1); // Discard walking queue
            bits.write(5, (dy & 0x1F) as u32); // Y offset (5 bits, signed)
            bits.write(5, (dx & 0x1F) as u32); // X offset (5 bits, signed)

            // Always write appearance for newly added players
            let mut update_state = other_state.clone();
            if self.config.send_appearance_on_add {
                update_state.appearance_updated = true;
            }
            self.write_update_block(updates, &other_player, &update_state)?;
        }

        // End of added players marker
        bits.write(11, 2047);

        Ok(())
    }

    /// Write the update block for a player
    fn write_update_block(
        &self,
        buffer: &mut PacketBuffer,
        player: &Arc<Player>,
        state: &PlayerSyncState,
    ) -> Result<()> {
        let flags = &state.update_data.flags;
        let mut mask = flags.to_mask();

        // If appearance is flagged via state, add it
        if state.appearance_updated {
            mask |= 0x10; // Appearance flag
        }

        // Write mask (1 or 2 bytes depending on value)
        if mask >= 0x100 {
            mask |= 0x40; // Extended mask flag
            buffer.write_ubyte((mask & 0xFF) as u8);
            buffer.write_ubyte((mask >> 8) as u8);
        } else {
            buffer.write_ubyte(mask as u8);
        }

        // Write update blocks in the correct order for RS2 530
        // Order matters - client expects specific sequence

        // Graphics (0x100)
        if flags.needs_graphics() {
            if let Some(ref gfx) = state.update_data.graphics {
                buffer.write_ushort_le(gfx.id);
                let settings = ((gfx.height as u32) << 16) | (gfx.delay as u32);
                buffer.write_int(settings as i32);
            }
        }

        // Animation (0x8)
        if flags.needs_animation() {
            if let Some(ref anim) = state.update_data.animation {
                buffer.write_ushort_le(anim.id as u16);
                buffer.write_ubyte(anim.delay);
            }
        }

        // Force chat (0x4)
        if flags.needs_force_chat() {
            if let Some(ref text) = state.update_data.force_chat {
                buffer.write_string(text);
            }
        }

        // Chat (0x80)
        if flags.needs_chat() {
            if let Some(ref chat) = state.update_data.chat {
                buffer.write_ushort_le(chat.effects);
                buffer.write_ubyte(chat.rights);
                buffer.write_ubyte(chat.message.len() as u8);
                // Write message bytes in reverse order (RS2 quirk)
                for &b in chat.message.iter().rev() {
                    buffer.write_ubyte(b);
                }
            }
        }

        // Face entity (0x1)
        if flags.needs_face_entity() {
            if let Some(index) = state.update_data.face_entity {
                buffer.write_ushort_le(index);
            }
        }

        // Appearance (0x10)
        if state.appearance_updated || flags.needs_appearance() {
            self.write_appearance_block(buffer, player)?;
        }

        // Face coordinate (0x2)
        if flags.needs_face_coordinate() {
            if let Some(ref coord) = state.update_data.face_coordinate {
                buffer.write_ushort_le(coord.x);
                buffer.write_ushort_le(coord.y);
            }
        }

        // Hit (0x20)
        if flags.needs_hit() {
            if let Some(ref hit) = state.update_data.hit {
                buffer.write_ubyte(hit.damage as u8);
                buffer.write_ubyte(hit.hit_type);
                buffer.write_ubyte(hit.current_hp as u8);
                buffer.write_ubyte(hit.max_hp as u8);
            }
        }

        // Hit 2 (0x200)
        if flags.needs_hit_2() {
            if let Some(ref hit) = state.update_data.hit_2 {
                buffer.write_ubyte(hit.damage as u8);
                buffer.write_ubyte(hit.hit_type);
                buffer.write_ubyte(hit.current_hp as u8);
                buffer.write_ubyte(hit.max_hp as u8);
            }
        }

        Ok(())
    }

    /// Write the appearance update block
    fn write_appearance_block(
        &self,
        buffer: &mut PacketBuffer,
        player: &Arc<Player>,
    ) -> Result<()> {
        let mut appearance_buffer = PacketBuffer::with_capacity(128);

        let appearance = player.appearance.read();
        let _rights = player.rights.read();

        // Gender (0 = male, 1 = female)
        appearance_buffer.write_ubyte(appearance.gender);

        // Skull icon (-1 = none)
        appearance_buffer.write_byte(-1);

        // Prayer icon (-1 = none)
        appearance_buffer.write_byte(-1);

        // Equipment/appearance slots
        // This is complex - for now, write default appearance without equipment
        self.write_appearance_slots(&mut appearance_buffer, &appearance)?;

        // Colors
        appearance_buffer.write_ubyte(appearance.hair_color);
        appearance_buffer.write_ubyte(appearance.torso_color);
        appearance_buffer.write_ubyte(appearance.legs_color);
        appearance_buffer.write_ubyte(appearance.feet_color);
        appearance_buffer.write_ubyte(appearance.skin_color);

        // Animation IDs (idle, walk, etc.)
        appearance_buffer.write_ushort(808); // Stand
        appearance_buffer.write_ushort(823); // Stand turn
        appearance_buffer.write_ushort(819); // Walk
        appearance_buffer.write_ushort(820); // Turn 180
        appearance_buffer.write_ushort(821); // Turn 90 CW
        appearance_buffer.write_ushort(822); // Turn 90 CCW
        appearance_buffer.write_ushort(824); // Run

        // Display name
        appearance_buffer.write_long(string_to_long(&player.display_name));

        // Combat level
        let combat_level = player.combat_level();
        appearance_buffer.write_ubyte(combat_level as u8);

        // Skill level (for skill-based worlds, 0 otherwise)
        appearance_buffer.write_ushort(0);

        // Hidden (0 = visible)
        appearance_buffer.write_ubyte(0);

        // Write the appearance block with length prefix
        let appearance_data = appearance_buffer.as_bytes();
        buffer.write_ubyte(appearance_data.len() as u8);
        buffer.write_bytes_reversed(appearance_data);

        Ok(())
    }

    /// Write the appearance slots (head, chest, arms, etc.)
    fn write_appearance_slots(
        &self,
        buffer: &mut PacketBuffer,
        appearance: &Appearance,
    ) -> Result<()> {
        // Equipment slots (0-11), then appearance
        // Format: 0 = nothing, 512+ = item, 256+ = body part

        // Slot 0: Head (hat/helm) - use appearance if no equipment
        buffer.write_ushort(256 + appearance.head);

        // Slot 1: Cape - none
        buffer.write_ubyte(0);

        // Slot 2: Amulet - none
        buffer.write_ubyte(0);

        // Slot 3: Weapon - none
        buffer.write_ubyte(0);

        // Slot 4: Chest
        buffer.write_ushort(256 + appearance.torso);

        // Slot 5: Shield - none
        buffer.write_ubyte(0);

        // Slot 6: Arms (full body hides this)
        buffer.write_ushort(256 + appearance.arms);

        // Slot 7: Legs
        buffer.write_ushort(256 + appearance.legs);

        // Slot 8: Hair (helm hides this) - use head appearance
        buffer.write_ushort(256 + appearance.head);

        // Slot 9: Hands
        buffer.write_ushort(256 + appearance.hands);

        // Slot 10: Feet
        buffer.write_ushort(256 + appearance.feet);

        // Slot 11: Beard (helm may hide this)
        if appearance.gender == 0 {
            buffer.write_ushort(256 + appearance.beard);
        } else {
            buffer.write_ubyte(0);
        }

        Ok(())
    }
}

impl Default for PlayerSyncManager {
    fn default() -> Self {
        Self::new()
    }
}

/// Bit buffer for writing bit-packed data
struct BitBuffer {
    data: Vec<u8>,
    bit_position: usize,
}

impl BitBuffer {
    fn new() -> Self {
        Self {
            data: vec![0; 4096],
            bit_position: 0,
        }
    }

    /// Write n bits with the given value
    fn write(&mut self, num_bits: usize, value: u32) {
        let mut byte_pos = self.bit_position >> 3;
        let mut bit_offset = 8 - (self.bit_position & 7);
        self.bit_position += num_bits;

        // Ensure we have enough space
        while byte_pos + (num_bits >> 3) + 1 >= self.data.len() {
            self.data.resize(self.data.len() * 2, 0);
        }

        let mut remaining = num_bits;
        let val = value;

        while remaining > bit_offset {
            self.data[byte_pos] &= !(((1 << bit_offset) - 1) as u8);
            self.data[byte_pos] |=
                ((val >> (remaining - bit_offset)) & ((1 << bit_offset) - 1)) as u8;
            remaining -= bit_offset;
            byte_pos += 1;
            bit_offset = 8;
        }

        if remaining == bit_offset {
            self.data[byte_pos] &= !(((1 << bit_offset) - 1) as u8);
            self.data[byte_pos] |= (val & ((1 << bit_offset) - 1)) as u8;
        } else {
            self.data[byte_pos] &= !((((1 << remaining) - 1) << (bit_offset - remaining)) as u8);
            self.data[byte_pos] |=
                ((val & ((1 << remaining) - 1)) << (bit_offset - remaining)) as u8;
        }
    }

    /// Finish writing and copy to packet buffer
    fn finish(&self, buffer: &mut PacketBuffer) {
        let byte_count = (self.bit_position + 7) >> 3;
        buffer.write_bytes(&self.data[..byte_count]);
    }
}

/// Convert a string to a 64-bit long for name encoding
fn string_to_long(s: &str) -> i64 {
    let mut result: i64 = 0;
    let chars: Vec<char> = s.chars().take(12).collect();

    for c in chars {
        result *= 37;
        if c >= 'A' && c <= 'Z' {
            result += (c as i64) - 64;
        } else if c >= 'a' && c <= 'z' {
            result += (c as i64) - 96;
        } else if c >= '0' && c <= '9' {
            result += (c as i64) - 21;
        }
    }

    while result % 37 == 0 && result != 0 {
        result /= 37;
    }

    result
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sync_config_default() {
        let config = SyncConfig::default();
        assert_eq!(config.max_local_players, MAX_LOCAL_PLAYERS);
        assert_eq!(config.view_distance, VIEW_DISTANCE);
        assert!(config.send_appearance_on_add);
    }

    #[test]
    fn test_player_sync_state_new() {
        let state = PlayerSyncState::new(1, Location::new(3222, 3222, 0));
        assert_eq!(state.player_index, 1);
        assert!(state.local_players.is_empty());
        assert_eq!(state.movement, MovementType::None);
        assert!(!state.teleported);
        assert!(state.appearance_updated); // True on creation
    }

    #[test]
    fn test_player_sync_state_reset_tick() {
        let mut state = PlayerSyncState::new(1, Location::new(3222, 3222, 0));
        state.add_local_player(2);
        state.movement = MovementType::Walk(0);
        state.teleported = true;

        state.reset_tick();

        // Local players should NOT be cleared
        assert!(state.has_local_player(2));
        // But per-tick state should be reset
        assert!(state.players_to_add.is_empty());
        assert_eq!(state.movement, MovementType::None);
        assert!(!state.teleported);
    }

    #[test]
    fn test_player_sync_state_local_players() {
        let mut state = PlayerSyncState::new(1, Location::new(3222, 3222, 0));

        assert!(!state.has_local_player(2));
        state.add_local_player(2);
        assert!(state.has_local_player(2));
        assert_eq!(state.local_player_count(), 1);

        state.remove_local_player(2);
        assert!(!state.has_local_player(2));
        assert_eq!(state.local_player_count(), 0);
    }

    #[test]
    fn test_bit_buffer_write() {
        let mut buffer = BitBuffer::new();
        buffer.write(1, 1);
        buffer.write(2, 3);
        buffer.write(5, 31);

        // Should have written 8 bits total
        assert_eq!(buffer.bit_position, 8);
    }

    #[test]
    fn test_string_to_long() {
        let result = string_to_long("Player");
        assert!(result > 0);

        // Same input should give same output
        assert_eq!(string_to_long("Test"), string_to_long("Test"));

        // Different inputs should give different outputs
        assert_ne!(string_to_long("Test"), string_to_long("Other"));
    }

    #[test]
    fn test_string_to_long_special_chars() {
        // Spaces and special chars should be handled
        let with_space = string_to_long("Player 1");
        let with_number = string_to_long("Player1");
        assert_ne!(with_space, with_number);
    }

    #[test]
    fn test_movement_type_equality() {
        assert_eq!(MovementType::None, MovementType::None);
        assert_eq!(MovementType::Walk(0), MovementType::Walk(0));
        assert_ne!(MovementType::Walk(0), MovementType::Walk(1));
        assert_ne!(MovementType::Walk(0), MovementType::Run(0, 0));
    }

    #[test]
    fn test_sync_manager_creation() {
        let manager = PlayerSyncManager::new();
        assert!(manager.states.read().is_empty());
    }
}
