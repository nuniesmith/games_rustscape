//! Player module
//!
//! Manages player entities and their state including:
//! - Player data (stats, inventory, equipment)
//! - Player position and movement
//! - Player actions and interactions
//! - Player session association

use std::sync::atomic::{AtomicU16, AtomicU64, Ordering};
use std::sync::Arc;

use dashmap::DashMap;
use parking_lot::RwLock;
use tracing::{debug, info};
use uuid::Uuid;

use crate::error::{GameError, Result, RustscapeError};
use crate::game::persistence::{
    Appearance as PersistenceAppearance, PlayerData, Position, Skill as PersistenceSkill,
};
use crate::net::session::SessionId;

/// Maximum player index value
pub const MAX_PLAYER_INDEX: u16 = 2047;

/// Player rights/privilege levels
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
#[repr(u8)]
pub enum PlayerRights {
    /// Normal player
    #[default]
    Normal = 0,
    /// Player moderator
    Moderator = 1,
    /// Administrator
    Administrator = 2,
}

impl PlayerRights {
    /// Get the rights level value
    pub fn as_u8(self) -> u8 {
        self as u8
    }

    /// Convert from u8
    pub fn from_u8(value: u8) -> Self {
        match value {
            0 => Self::Normal,
            1 => Self::Moderator,
            2 => Self::Administrator,
            _ => Self::Normal,
        }
    }

    /// Check if this player can use moderator commands
    pub fn is_moderator(&self) -> bool {
        matches!(self, Self::Moderator | Self::Administrator)
    }

    /// Check if this player can use admin commands
    pub fn is_admin(&self) -> bool {
        matches!(self, Self::Administrator)
    }
}

/// Player location in the game world
#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct Location {
    /// X coordinate
    pub x: u16,
    /// Y coordinate
    pub y: u16,
    /// Z (height) level (0-3)
    pub z: u8,
}

impl Location {
    /// Create a new location
    pub fn new(x: u16, y: u16, z: u8) -> Self {
        Self { x, y, z }
    }

    /// Get the region X coordinate
    pub fn region_x(&self) -> u16 {
        self.x >> 6
    }

    /// Get the region Y coordinate
    pub fn region_y(&self) -> u16 {
        self.y >> 6
    }

    /// Get the chunk X coordinate (within region)
    pub fn chunk_x(&self) -> u8 {
        ((self.x >> 3) & 0x7) as u8
    }

    /// Get the chunk Y coordinate (within region)
    pub fn chunk_y(&self) -> u8 {
        ((self.y >> 3) & 0x7) as u8
    }

    /// Get the local X coordinate (within chunk)
    pub fn local_x(&self) -> u8 {
        (self.x & 0x7) as u8
    }

    /// Get the local Y coordinate (within chunk)
    pub fn local_y(&self) -> u8 {
        (self.y & 0x7) as u8
    }

    /// Calculate the distance to another location
    pub fn distance_to(&self, other: &Location) -> f64 {
        if self.z != other.z {
            return f64::MAX;
        }
        let dx = (self.x as i32 - other.x as i32).abs() as f64;
        let dy = (self.y as i32 - other.y as i32).abs() as f64;
        (dx * dx + dy * dy).sqrt()
    }

    /// Check if within distance of another location
    pub fn within_distance(&self, other: &Location, distance: u16) -> bool {
        if self.z != other.z {
            return false;
        }
        let dx = (self.x as i32 - other.x as i32).abs();
        let dy = (self.y as i32 - other.y as i32).abs();
        dx <= distance as i32 && dy <= distance as i32
    }

    /// Get the region ID (for map region packets)
    pub fn region_id(&self) -> u32 {
        ((self.region_x() as u32) << 8) | (self.region_y() as u32)
    }
}

impl std::fmt::Display for Location {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "({}, {}, {})", self.x, self.y, self.z)
    }
}

/// Player appearance (for model rendering)
#[derive(Debug, Clone, Default)]
pub struct Appearance {
    /// Gender (0 = male, 1 = female)
    pub gender: u8,
    /// Head model ID
    pub head: u16,
    /// Torso model ID
    pub torso: u16,
    /// Arms model ID
    pub arms: u16,
    /// Hands model ID
    pub hands: u16,
    /// Legs model ID
    pub legs: u16,
    /// Feet model ID
    pub feet: u16,
    /// Beard model ID (male only)
    pub beard: u16,
    /// Hair color
    pub hair_color: u8,
    /// Torso color
    pub torso_color: u8,
    /// Legs color
    pub legs_color: u8,
    /// Feet color
    pub feet_color: u8,
    /// Skin color
    pub skin_color: u8,
}

impl Appearance {
    /// Create default male appearance
    pub fn default_male() -> Self {
        Self {
            gender: 0,
            head: 0,
            torso: 18,
            arms: 26,
            hands: 33,
            legs: 36,
            feet: 42,
            beard: 10,
            hair_color: 0,
            torso_color: 0,
            legs_color: 0,
            feet_color: 0,
            skin_color: 0,
        }
    }

    /// Create default female appearance
    pub fn default_female() -> Self {
        Self {
            gender: 1,
            head: 45,
            torso: 56,
            arms: 61,
            hands: 67,
            legs: 70,
            feet: 79,
            beard: 0,
            hair_color: 0,
            torso_color: 0,
            legs_color: 0,
            feet_color: 0,
            skin_color: 0,
        }
    }
}

/// Skill IDs
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum Skill {
    Attack = 0,
    Defence = 1,
    Strength = 2,
    Hitpoints = 3,
    Ranged = 4,
    Prayer = 5,
    Magic = 6,
    Cooking = 7,
    Woodcutting = 8,
    Fletching = 9,
    Fishing = 10,
    Firemaking = 11,
    Crafting = 12,
    Smithing = 13,
    Mining = 14,
    Herblore = 15,
    Agility = 16,
    Thieving = 17,
    Slayer = 18,
    Farming = 19,
    Runecrafting = 20,
    Hunter = 21,
    Construction = 22,
    Summoning = 23,
    Dungeoneering = 24,
}

impl Skill {
    /// Total number of skills
    pub const COUNT: usize = 25;

    /// Get the skill ID value
    pub fn as_u8(self) -> u8 {
        self as u8
    }

    /// Convert from u8
    pub fn from_u8(value: u8) -> Option<Self> {
        match value {
            0 => Some(Self::Attack),
            1 => Some(Self::Defence),
            2 => Some(Self::Strength),
            3 => Some(Self::Hitpoints),
            4 => Some(Self::Ranged),
            5 => Some(Self::Prayer),
            6 => Some(Self::Magic),
            7 => Some(Self::Cooking),
            8 => Some(Self::Woodcutting),
            9 => Some(Self::Fletching),
            10 => Some(Self::Fishing),
            11 => Some(Self::Firemaking),
            12 => Some(Self::Crafting),
            13 => Some(Self::Smithing),
            14 => Some(Self::Mining),
            15 => Some(Self::Herblore),
            16 => Some(Self::Agility),
            17 => Some(Self::Thieving),
            18 => Some(Self::Slayer),
            19 => Some(Self::Farming),
            20 => Some(Self::Runecrafting),
            21 => Some(Self::Hunter),
            22 => Some(Self::Construction),
            23 => Some(Self::Summoning),
            24 => Some(Self::Dungeoneering),
            _ => None,
        }
    }
}

/// Player skills data
#[derive(Debug, Clone)]
pub struct Skills {
    /// Current levels (can be boosted/drained)
    pub levels: [u8; Skill::COUNT],
    /// Experience points
    pub experience: [i32; Skill::COUNT],
}

impl Default for Skills {
    fn default() -> Self {
        let mut levels = [1u8; Skill::COUNT];
        let mut experience = [0i32; Skill::COUNT];

        // Hitpoints starts at level 10
        levels[Skill::Hitpoints as usize] = 10;
        experience[Skill::Hitpoints as usize] = 1154; // XP for level 10

        Self { levels, experience }
    }
}

impl Skills {
    /// Get the level for a skill
    pub fn level(&self, skill: Skill) -> u8 {
        self.levels[skill as usize]
    }

    /// Set the level for a skill
    pub fn set_level(&mut self, skill: Skill, level: u8) {
        self.levels[skill as usize] = level;
    }

    /// Get the experience for a skill
    pub fn experience(&self, skill: Skill) -> i32 {
        self.experience[skill as usize]
    }

    /// Add experience to a skill
    pub fn add_experience(&mut self, skill: Skill, xp: i32) {
        let idx = skill as usize;
        self.experience[idx] = self.experience[idx].saturating_add(xp);
        // TODO: Recalculate level based on XP
    }

    /// Get the total level
    pub fn total_level(&self) -> u32 {
        self.levels.iter().map(|&l| l as u32).sum()
    }

    /// Get the combat level
    pub fn combat_level(&self) -> u8 {
        let attack = self.level(Skill::Attack) as f64;
        let strength = self.level(Skill::Strength) as f64;
        let defence = self.level(Skill::Defence) as f64;
        let hitpoints = self.level(Skill::Hitpoints) as f64;
        let prayer = self.level(Skill::Prayer) as f64;
        let ranged = self.level(Skill::Ranged) as f64;
        let magic = self.level(Skill::Magic) as f64;
        let summoning = self.level(Skill::Summoning) as f64;

        let base = (defence + hitpoints + (prayer / 2.0).floor() + (summoning / 2.0).floor()) / 4.0;
        let melee = (attack + strength) * 0.325;
        let range = ranged * 0.4875;
        let mage = magic * 0.4875;

        (base + melee.max(range).max(mage)).floor() as u8
    }
}

/// A player entity in the game
pub struct Player {
    /// Player index (1-2047)
    pub index: u16,
    /// Associated session ID
    pub session_id: SessionId,
    /// Database user UUID (from users table, for persistence)
    pub user_id: Option<Uuid>,
    /// Username
    pub username: String,
    /// Display name (can differ from username)
    pub display_name: String,
    /// Player rights
    pub rights: RwLock<PlayerRights>,
    /// Current location
    pub location: RwLock<Location>,
    /// Previous location (for delta updates)
    pub previous_location: RwLock<Location>,
    /// Player appearance
    pub appearance: RwLock<Appearance>,
    /// Player skills
    pub skills: RwLock<Skills>,
    /// Whether the player is a member
    pub member: RwLock<bool>,
    /// Run energy (0-100)
    pub run_energy: RwLock<u8>,
    /// Whether running is enabled
    pub running: RwLock<bool>,
    /// Last activity timestamp (tick number)
    pub last_activity: AtomicU64,
}

impl Player {
    /// Create a new player
    pub fn new(index: u16, session_id: SessionId, username: String) -> Self {
        let display_name = username.replace('_', " ");

        Self {
            index,
            session_id,
            user_id: None,
            username,
            display_name,
            rights: RwLock::new(PlayerRights::Normal),
            location: RwLock::new(Location::new(3222, 3222, 0)), // Lumbridge
            previous_location: RwLock::new(Location::new(3222, 3222, 0)),
            appearance: RwLock::new(Appearance::default_male()),
            skills: RwLock::new(Skills::default()),
            member: RwLock::new(false),
            run_energy: RwLock::new(100),
            running: RwLock::new(false),
            last_activity: AtomicU64::new(0),
        }
    }

    /// Create a player from persisted PlayerData
    pub fn from_player_data(
        index: u16,
        session_id: SessionId,
        data: &PlayerData,
        rights: PlayerRights,
        member: bool,
    ) -> Self {
        // Convert appearance (persistence uses u8, player uses u16 for body parts)
        let appearance = Appearance {
            gender: data.appearance.gender,
            head: data.appearance.head as u16,
            torso: data.appearance.chest as u16,
            arms: data.appearance.arms as u16,
            hands: data.appearance.hands as u16,
            legs: data.appearance.legs as u16,
            feet: data.appearance.feet as u16,
            beard: data.appearance.beard as u16,
            hair_color: data.appearance.hair_color,
            torso_color: data.appearance.torso_color,
            legs_color: data.appearance.legs_color,
            feet_color: data.appearance.feet_color,
            skin_color: data.appearance.skin_color,
        };

        // Convert skills
        let mut skills = Skills::default();
        for skill in &data.skills {
            if let Some(skill_enum) = Skill::from_u8(skill.id) {
                let idx = skill_enum.as_u8() as usize;
                if idx < Skill::COUNT {
                    skills.levels[idx] = skill.level;
                    skills.experience[idx] = skill.xp;
                }
            }
        }

        let location = Location::new(
            data.position.x.clamp(0, u16::MAX as i32) as u16,
            data.position.y.clamp(0, u16::MAX as i32) as u16,
            data.position.z as u8,
        );

        debug!(
            index = index,
            username = %data.display_name,
            location = %location,
            "Created player from persisted data"
        );

        Self {
            index,
            session_id,
            user_id: Some(data.user_id),
            username: data.display_name.to_lowercase().replace(' ', "_"),
            display_name: data.display_name.clone(),
            rights: RwLock::new(rights),
            location: RwLock::new(location),
            previous_location: RwLock::new(location),
            appearance: RwLock::new(appearance),
            skills: RwLock::new(skills),
            member: RwLock::new(member),
            run_energy: RwLock::new((data.run_energy / 100).min(100) as u8),
            running: RwLock::new(false),
            last_activity: AtomicU64::new(0),
        }
    }

    /// Convert the player's current state to PlayerData for persistence
    pub fn to_player_data(&self, player_id: uuid::Uuid, user_id: uuid::Uuid) -> PlayerData {
        let location = self.location.read();
        let appearance = self.appearance.read();
        let skills = self.skills.read();

        // Convert appearance (player uses u16, persistence uses u8 for body parts)
        let persist_appearance = PersistenceAppearance {
            gender: appearance.gender,
            head: appearance.head as u8,
            beard: appearance.beard as u8,
            chest: appearance.torso as u8,
            arms: appearance.arms as u8,
            hands: appearance.hands as u8,
            legs: appearance.legs as u8,
            feet: appearance.feet as u8,
            hair_color: appearance.hair_color,
            torso_color: appearance.torso_color,
            legs_color: appearance.legs_color,
            feet_color: appearance.feet_color,
            skin_color: appearance.skin_color,
        };

        // Convert skills
        let persist_skills: Vec<PersistenceSkill> = (0..Skill::COUNT)
            .map(|i| PersistenceSkill {
                id: i as u8,
                level: skills.levels[i],
                xp: skills.experience[i],
            })
            .collect();

        let total_level: i32 = persist_skills.iter().map(|s| s.level as i32).sum();
        let total_xp: i64 = persist_skills.iter().map(|s| s.xp as i64).sum();
        let combat_level = skills.combat_level() as i16;

        PlayerData {
            id: player_id,
            user_id,
            display_name: self.display_name.clone(),
            position: Position::new(location.x as i32, location.y as i32, location.z as i16),
            combat_level,
            total_level,
            total_xp,
            run_energy: (*self.run_energy.read() as i32) * 100,
            special_energy: 1000, // TODO: Track special energy
            appearance: persist_appearance,
            skills: persist_skills,
            inventory: vec![None; 28], // TODO: Track inventory
            bank: vec![None; 496],     // TODO: Track bank
            equipment: vec![None; 14], // TODO: Track equipment
            time_played: 0,            // TODO: Track time played
            last_saved: None,
        }
    }

    /// Get the player's username
    pub fn username(&self) -> &str {
        &self.username
    }

    /// Get the player's display name
    pub fn display_name(&self) -> &str {
        &self.display_name
    }

    /// Get the player's rights
    pub fn rights(&self) -> PlayerRights {
        *self.rights.read()
    }

    /// Set the player's rights
    pub fn set_rights(&self, rights: PlayerRights) {
        *self.rights.write() = rights;
    }

    /// Get the player's location
    pub fn location(&self) -> Location {
        *self.location.read()
    }

    /// Set the player's location
    pub fn set_location(&self, location: Location) {
        *self.previous_location.write() = *self.location.read();
        *self.location.write() = location;
    }

    /// Teleport the player to a location
    pub fn teleport(&self, location: Location) {
        *self.location.write() = location;
        *self.previous_location.write() = location;
    }

    /// Update the last activity tick
    pub fn touch(&self, tick: u64) {
        self.last_activity.store(tick, Ordering::SeqCst);
    }

    /// Get the combat level
    pub fn combat_level(&self) -> u8 {
        self.skills.read().combat_level()
    }

    /// Get the total level
    pub fn total_level(&self) -> u32 {
        self.skills.read().total_level()
    }
}

impl std::fmt::Debug for Player {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Player")
            .field("index", &self.index)
            .field("username", &self.username)
            .field("session_id", &self.session_id)
            .field("location", &self.location())
            .field("rights", &self.rights())
            .finish()
    }
}

/// Player manager - handles player lifecycle and indexing
pub struct PlayerManager {
    /// Map of player index to player
    players: DashMap<u16, Arc<Player>>,
    /// Map of username to player index
    username_to_index: DashMap<String, u16>,
    /// Map of session ID to player index
    session_to_index: DashMap<SessionId, u16>,
    /// Next available player index
    next_index: AtomicU16,
    /// Maximum player count
    max_players: u16,
}

impl PlayerManager {
    /// Create a new player manager
    pub fn new(max_players: u16) -> Self {
        Self {
            players: DashMap::new(),
            username_to_index: DashMap::new(),
            session_to_index: DashMap::new(),
            next_index: AtomicU16::new(1), // Index 0 is reserved
            max_players: max_players.min(MAX_PLAYER_INDEX),
        }
    }

    /// Register a new player
    pub fn register(&self, session_id: SessionId, username: String) -> Result<Arc<Player>> {
        // Check if already registered
        if self
            .username_to_index
            .contains_key(&username.to_lowercase())
        {
            return Err(RustscapeError::Game(GameError::InvalidPlayerState(
                "Player already registered".to_string(),
            )));
        }

        // Find an available index
        let index = self.allocate_index()?;

        // Create player
        let player = Arc::new(Player::new(index, session_id, username.clone()));

        // Register in maps
        self.players.insert(index, player.clone());
        self.username_to_index
            .insert(username.to_lowercase(), index);
        self.session_to_index.insert(session_id, index);

        info!(
            index = index,
            username = %username,
            session_id = session_id,
            "Player registered"
        );

        Ok(player)
    }

    /// Register a player from persisted PlayerData
    pub fn register_from_data(
        &self,
        session_id: SessionId,
        data: &PlayerData,
        rights: PlayerRights,
        member: bool,
    ) -> Result<Arc<Player>> {
        let username = data.display_name.to_lowercase().replace(' ', "_");

        // Check if already registered
        if self.username_to_index.contains_key(&username) {
            return Err(RustscapeError::Game(GameError::InvalidPlayerState(
                "Player already registered".to_string(),
            )));
        }

        // Find an available index
        let index = self.allocate_index()?;

        // Create player from persisted data
        let player = Arc::new(Player::from_player_data(
            index, session_id, data, rights, member,
        ));

        // Register in maps
        self.players.insert(index, player.clone());
        self.username_to_index.insert(username.clone(), index);
        self.session_to_index.insert(session_id, index);

        info!(
            index = index,
            username = %data.display_name,
            session_id = session_id,
            "Player registered from persisted data"
        );

        Ok(player)
    }

    /// Unregister a player
    pub fn unregister(&self, index: u16) {
        if let Some((_, player)) = self.players.remove(&index) {
            self.username_to_index
                .remove(&player.username.to_lowercase());
            self.session_to_index.remove(&player.session_id);

            info!(
                index = index,
                username = %player.username,
                "Player unregistered"
            );
        }
    }

    /// Get a player by index
    pub fn get(&self, index: u16) -> Option<Arc<Player>> {
        self.players.get(&index).map(|r| r.clone())
    }

    /// Get a player by username
    pub fn get_by_username(&self, username: &str) -> Option<Arc<Player>> {
        let username_lower = username.to_lowercase();
        self.username_to_index
            .get(&username_lower)
            .and_then(|idx| self.get(*idx))
    }

    /// Get a player by session ID
    pub fn get_by_session(&self, session_id: SessionId) -> Option<Arc<Player>> {
        self.session_to_index
            .get(&session_id)
            .and_then(|idx| self.get(*idx))
    }

    /// Get the player count
    pub fn count(&self) -> usize {
        self.players.len()
    }

    /// Check if the server is full
    pub fn is_full(&self) -> bool {
        self.count() >= self.max_players as usize
    }

    /// Allocate a player index
    fn allocate_index(&self) -> Result<u16> {
        // Try to get the next sequential index
        for _ in 0..self.max_players {
            let index = self.next_index.fetch_add(1, Ordering::SeqCst);

            // Wrap around if needed
            if index > self.max_players {
                self.next_index.store(1, Ordering::SeqCst);
                continue;
            }

            // Check if index is available
            if !self.players.contains_key(&index) {
                return Ok(index);
            }
        }

        Err(RustscapeError::Game(GameError::WorldNotReady))
    }

    /// Iterate over all players
    pub fn for_each<F>(&self, mut f: F)
    where
        F: FnMut(&Player),
    {
        for entry in self.players.iter() {
            f(&entry);
        }
    }
}

impl Default for PlayerManager {
    fn default() -> Self {
        Self::new(MAX_PLAYER_INDEX)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_location() {
        let loc = Location::new(3222, 3222, 0);
        assert_eq!(loc.x, 3222);
        assert_eq!(loc.y, 3222);
        assert_eq!(loc.z, 0);
    }

    #[test]
    fn test_location_distance() {
        let loc1 = Location::new(0, 0, 0);
        let loc2 = Location::new(3, 4, 0);

        assert!((loc1.distance_to(&loc2) - 5.0).abs() < 0.001);
    }

    #[test]
    fn test_location_different_planes() {
        let loc1 = Location::new(0, 0, 0);
        let loc2 = Location::new(0, 0, 1);

        assert_eq!(loc1.distance_to(&loc2), f64::MAX);
        assert!(!loc1.within_distance(&loc2, 100));
    }

    #[test]
    fn test_skills_default() {
        let skills = Skills::default();
        assert_eq!(skills.level(Skill::Attack), 1);
        assert_eq!(skills.level(Skill::Hitpoints), 10);
    }

    #[test]
    fn test_skills_combat_level() {
        let skills = Skills::default();
        let combat = skills.combat_level();
        assert!(combat >= 3); // Minimum combat level with default stats
    }

    #[test]
    fn test_player_creation() {
        let player = Player::new(1, 100, "TestPlayer".to_string());
        assert_eq!(player.index, 1);
        assert_eq!(player.username, "TestPlayer");
        assert_eq!(player.display_name, "TestPlayer");
    }

    #[test]
    fn test_player_rights() {
        let player = Player::new(1, 100, "Test".to_string());
        assert_eq!(player.rights(), PlayerRights::Normal);

        player.set_rights(PlayerRights::Administrator);
        assert_eq!(player.rights(), PlayerRights::Administrator);
        assert!(player.rights().is_admin());
    }

    #[test]
    fn test_player_manager() {
        let manager = PlayerManager::new(100);
        assert_eq!(manager.count(), 0);
        assert!(!manager.is_full());

        let player = manager.register(1, "TestPlayer".to_string()).unwrap();
        assert_eq!(player.index, 1);
        assert_eq!(manager.count(), 1);

        let found = manager.get_by_username("testplayer").unwrap();
        assert_eq!(found.index, 1);

        manager.unregister(1);
        assert_eq!(manager.count(), 0);
    }

    #[test]
    fn test_player_manager_duplicate() {
        let manager = PlayerManager::new(100);

        manager.register(1, "TestPlayer".to_string()).unwrap();
        let result = manager.register(2, "TestPlayer".to_string());

        assert!(result.is_err());
    }
}
