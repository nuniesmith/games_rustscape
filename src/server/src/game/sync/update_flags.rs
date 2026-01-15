//! Update flags for player synchronization
//!
//! Tracks what aspects of a player have changed and need to be
//! sent to other players in the update packet.

use bitflags::bitflags;

bitflags! {
    /// Flags indicating what player data needs to be synchronized
    #[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
    pub struct UpdateFlags: u16 {
        /// No updates needed
        const NONE = 0;
        /// Appearance has changed (equipment, body, colors)
        const APPEARANCE = 1 << 0;
        /// Animation is playing
        const ANIMATION = 1 << 1;
        /// Graphics/spotanim effect
        const GRAPHICS = 1 << 2;
        /// Chat message
        const CHAT = 1 << 3;
        /// Face entity (NPC or player)
        const FACE_ENTITY = 1 << 4;
        /// Face coordinate/direction
        const FACE_COORDINATE = 1 << 5;
        /// Hit/damage splat
        const HIT = 1 << 6;
        /// Secondary hit splat
        const HIT_2 = 1 << 7;
        /// Forced movement (cutscenes, etc.)
        const FORCED_MOVEMENT = 1 << 8;
        /// Force chat (overhead text)
        const FORCE_CHAT = 1 << 9;
        /// Temporary movement speed change
        const MOVE_SPEED = 1 << 10;
    }
}

impl Default for UpdateFlags {
    fn default() -> Self {
        Self::NONE
    }
}

impl UpdateFlags {
    /// Check if any updates are flagged
    pub fn has_update(&self) -> bool {
        !self.is_empty()
    }

    /// Check if appearance update is needed
    pub fn needs_appearance(&self) -> bool {
        self.contains(Self::APPEARANCE)
    }

    /// Check if animation update is needed
    pub fn needs_animation(&self) -> bool {
        self.contains(Self::ANIMATION)
    }

    /// Check if graphics update is needed
    pub fn needs_graphics(&self) -> bool {
        self.contains(Self::GRAPHICS)
    }

    /// Check if chat update is needed
    pub fn needs_chat(&self) -> bool {
        self.contains(Self::CHAT)
    }

    /// Check if face entity update is needed
    pub fn needs_face_entity(&self) -> bool {
        self.contains(Self::FACE_ENTITY)
    }

    /// Check if face coordinate update is needed
    pub fn needs_face_coordinate(&self) -> bool {
        self.contains(Self::FACE_COORDINATE)
    }

    /// Check if hit update is needed
    pub fn needs_hit(&self) -> bool {
        self.contains(Self::HIT)
    }

    /// Check if secondary hit update is needed
    pub fn needs_hit_2(&self) -> bool {
        self.contains(Self::HIT_2)
    }

    /// Check if forced movement update is needed
    pub fn needs_forced_movement(&self) -> bool {
        self.contains(Self::FORCED_MOVEMENT)
    }

    /// Check if force chat update is needed
    pub fn needs_force_chat(&self) -> bool {
        self.contains(Self::FORCE_CHAT)
    }

    /// Clear all flags
    pub fn clear(&mut self) {
        *self = Self::NONE;
    }

    /// Get the mask byte for the update block header
    /// The order and values here must match the client's expected format
    pub fn to_mask(&self) -> u16 {
        let mut mask: u16 = 0;

        // These bit positions must match what the client expects
        // for RS2 revision 530
        if self.contains(Self::GRAPHICS) {
            mask |= 0x100;
        }
        if self.contains(Self::ANIMATION) {
            mask |= 0x8;
        }
        if self.contains(Self::FORCED_MOVEMENT) {
            mask |= 0x400;
        }
        if self.contains(Self::FORCE_CHAT) {
            mask |= 0x4;
        }
        if self.contains(Self::CHAT) {
            mask |= 0x80;
        }
        if self.contains(Self::FACE_ENTITY) {
            mask |= 0x1;
        }
        if self.contains(Self::APPEARANCE) {
            mask |= 0x10;
        }
        if self.contains(Self::FACE_COORDINATE) {
            mask |= 0x2;
        }
        if self.contains(Self::HIT) {
            mask |= 0x20;
        }
        if self.contains(Self::HIT_2) {
            mask |= 0x200;
        }
        if self.contains(Self::MOVE_SPEED) {
            mask |= 0x800;
        }

        mask
    }
}

/// Animation data for update packet
#[derive(Debug, Clone, Default)]
pub struct AnimationUpdate {
    /// Animation ID (-1 for reset)
    pub id: i16,
    /// Animation delay in client ticks
    pub delay: u8,
}

impl AnimationUpdate {
    pub fn new(id: i16, delay: u8) -> Self {
        Self { id, delay }
    }

    pub fn reset() -> Self {
        Self { id: -1, delay: 0 }
    }
}

/// Graphics/SpotAnim data for update packet
#[derive(Debug, Clone, Default)]
pub struct GraphicsUpdate {
    /// Graphics ID
    pub id: u16,
    /// Height (0 = ground level, 100 = normal standing)
    pub height: u16,
    /// Delay before starting
    pub delay: u16,
}

impl GraphicsUpdate {
    pub fn new(id: u16, height: u16, delay: u16) -> Self {
        Self { id, height, delay }
    }
}

/// Hit/damage splat data
#[derive(Debug, Clone, Default)]
pub struct HitUpdate {
    /// Damage amount
    pub damage: u16,
    /// Hit type (0=miss, 1=hit, 2=poison, 3=disease, etc.)
    pub hit_type: u8,
    /// Current HP
    pub current_hp: u16,
    /// Maximum HP
    pub max_hp: u16,
}

impl HitUpdate {
    pub fn new(damage: u16, hit_type: u8, current_hp: u16, max_hp: u16) -> Self {
        Self {
            damage,
            hit_type,
            current_hp,
            max_hp,
        }
    }
}

/// Chat message data for update packet
#[derive(Debug, Clone, Default)]
pub struct ChatUpdate {
    /// Chat effects (color, animation)
    pub effects: u16,
    /// Player rights level
    pub rights: u8,
    /// Compressed message data
    pub message: Vec<u8>,
}

impl ChatUpdate {
    pub fn new(effects: u16, rights: u8, message: Vec<u8>) -> Self {
        Self {
            effects,
            rights,
            message,
        }
    }
}

/// Face coordinate data
#[derive(Debug, Clone, Default)]
pub struct FaceCoordinateUpdate {
    /// X coordinate to face (doubled for precision)
    pub x: u16,
    /// Y coordinate to face (doubled for precision)
    pub y: u16,
}

impl FaceCoordinateUpdate {
    pub fn new(x: u16, y: u16) -> Self {
        Self { x, y }
    }

    /// Create from tile coordinates (will double them for client)
    pub fn from_tile(x: u16, y: u16) -> Self {
        Self {
            x: x * 2 + 1,
            y: y * 2 + 1,
        }
    }
}

/// Forced movement data (for cutscenes, obstacles, etc.)
#[derive(Debug, Clone, Default)]
pub struct ForcedMovementUpdate {
    /// Start X offset from current position
    pub start_x: u8,
    /// Start Y offset
    pub start_y: u8,
    /// End X offset
    pub end_x: u8,
    /// End Y offset
    pub end_y: u8,
    /// Start tick (client cycle)
    pub start_cycle: u16,
    /// End tick
    pub end_cycle: u16,
    /// Direction to face
    pub direction: u8,
}

/// Complete update data for a player this tick
#[derive(Debug, Clone, Default)]
pub struct PlayerUpdateData {
    /// Update flags
    pub flags: UpdateFlags,
    /// Animation data (if flagged)
    pub animation: Option<AnimationUpdate>,
    /// Graphics data (if flagged)
    pub graphics: Option<GraphicsUpdate>,
    /// Primary hit data (if flagged)
    pub hit: Option<HitUpdate>,
    /// Secondary hit data (if flagged)
    pub hit_2: Option<HitUpdate>,
    /// Chat message (if flagged)
    pub chat: Option<ChatUpdate>,
    /// Face entity index (if flagged) - 65535 = reset
    pub face_entity: Option<u16>,
    /// Face coordinate (if flagged)
    pub face_coordinate: Option<FaceCoordinateUpdate>,
    /// Force chat text (if flagged)
    pub force_chat: Option<String>,
    /// Forced movement (if flagged)
    pub forced_movement: Option<ForcedMovementUpdate>,
}

impl PlayerUpdateData {
    /// Create empty update data
    pub fn new() -> Self {
        Self::default()
    }

    /// Check if there are any updates
    pub fn has_updates(&self) -> bool {
        self.flags.has_update()
    }

    /// Set appearance update flag
    pub fn flag_appearance(&mut self) {
        self.flags |= UpdateFlags::APPEARANCE;
    }

    /// Set animation
    pub fn set_animation(&mut self, id: i16, delay: u8) {
        self.animation = Some(AnimationUpdate::new(id, delay));
        self.flags |= UpdateFlags::ANIMATION;
    }

    /// Clear animation
    pub fn clear_animation(&mut self) {
        self.animation = Some(AnimationUpdate::reset());
        self.flags |= UpdateFlags::ANIMATION;
    }

    /// Set graphics
    pub fn set_graphics(&mut self, id: u16, height: u16, delay: u16) {
        self.graphics = Some(GraphicsUpdate::new(id, height, delay));
        self.flags |= UpdateFlags::GRAPHICS;
    }

    /// Set hit splat
    pub fn set_hit(&mut self, damage: u16, hit_type: u8, current_hp: u16, max_hp: u16) {
        self.hit = Some(HitUpdate::new(damage, hit_type, current_hp, max_hp));
        self.flags |= UpdateFlags::HIT;
    }

    /// Set secondary hit splat
    pub fn set_hit_2(&mut self, damage: u16, hit_type: u8, current_hp: u16, max_hp: u16) {
        self.hit_2 = Some(HitUpdate::new(damage, hit_type, current_hp, max_hp));
        self.flags |= UpdateFlags::HIT_2;
    }

    /// Set chat message
    pub fn set_chat(&mut self, effects: u16, rights: u8, message: Vec<u8>) {
        self.chat = Some(ChatUpdate::new(effects, rights, message));
        self.flags |= UpdateFlags::CHAT;
    }

    /// Set face entity
    pub fn set_face_entity(&mut self, index: u16) {
        self.face_entity = Some(index);
        self.flags |= UpdateFlags::FACE_ENTITY;
    }

    /// Clear face entity
    pub fn clear_face_entity(&mut self) {
        self.face_entity = Some(65535);
        self.flags |= UpdateFlags::FACE_ENTITY;
    }

    /// Set face coordinate
    pub fn set_face_coordinate(&mut self, x: u16, y: u16) {
        self.face_coordinate = Some(FaceCoordinateUpdate::from_tile(x, y));
        self.flags |= UpdateFlags::FACE_COORDINATE;
    }

    /// Set force chat
    pub fn set_force_chat(&mut self, text: String) {
        self.force_chat = Some(text);
        self.flags |= UpdateFlags::FORCE_CHAT;
    }

    /// Set forced movement
    pub fn set_forced_movement(&mut self, movement: ForcedMovementUpdate) {
        self.forced_movement = Some(movement);
        self.flags |= UpdateFlags::FORCED_MOVEMENT;
    }

    /// Clear all update data for next tick
    pub fn reset(&mut self) {
        self.flags.clear();
        self.animation = None;
        self.graphics = None;
        self.hit = None;
        self.hit_2 = None;
        self.chat = None;
        self.face_entity = None;
        self.face_coordinate = None;
        self.force_chat = None;
        self.forced_movement = None;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_update_flags_default() {
        let flags = UpdateFlags::default();
        assert!(!flags.has_update());
        assert_eq!(flags.to_mask(), 0);
    }

    #[test]
    fn test_update_flags_appearance() {
        let mut flags = UpdateFlags::NONE;
        flags |= UpdateFlags::APPEARANCE;
        assert!(flags.has_update());
        assert!(flags.needs_appearance());
        assert_eq!(flags.to_mask(), 0x10);
    }

    #[test]
    fn test_update_flags_multiple() {
        let flags = UpdateFlags::APPEARANCE | UpdateFlags::ANIMATION | UpdateFlags::CHAT;
        assert!(flags.needs_appearance());
        assert!(flags.needs_animation());
        assert!(flags.needs_chat());
        assert!(!flags.needs_graphics());
    }

    #[test]
    fn test_update_flags_clear() {
        let mut flags = UpdateFlags::APPEARANCE | UpdateFlags::HIT;
        assert!(flags.has_update());
        flags.clear();
        assert!(!flags.has_update());
    }

    #[test]
    fn test_player_update_data_new() {
        let data = PlayerUpdateData::new();
        assert!(!data.has_updates());
    }

    #[test]
    fn test_player_update_data_appearance() {
        let mut data = PlayerUpdateData::new();
        data.flag_appearance();
        assert!(data.has_updates());
        assert!(data.flags.needs_appearance());
    }

    #[test]
    fn test_player_update_data_animation() {
        let mut data = PlayerUpdateData::new();
        data.set_animation(808, 0);
        assert!(data.has_updates());
        assert!(data.flags.needs_animation());
        assert_eq!(data.animation.as_ref().unwrap().id, 808);
    }

    #[test]
    fn test_player_update_data_reset() {
        let mut data = PlayerUpdateData::new();
        data.flag_appearance();
        data.set_animation(808, 0);
        data.set_hit(10, 1, 90, 100);
        assert!(data.has_updates());

        data.reset();
        assert!(!data.has_updates());
        assert!(data.animation.is_none());
        assert!(data.hit.is_none());
    }

    #[test]
    fn test_face_coordinate_from_tile() {
        let coord = FaceCoordinateUpdate::from_tile(100, 200);
        assert_eq!(coord.x, 201); // 100 * 2 + 1
        assert_eq!(coord.y, 401); // 200 * 2 + 1
    }

    #[test]
    fn test_animation_reset() {
        let anim = AnimationUpdate::reset();
        assert_eq!(anim.id, -1);
        assert_eq!(anim.delay, 0);
    }
}
