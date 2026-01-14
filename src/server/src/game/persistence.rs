//! Player persistence module
//!
//! Handles saving and loading player data to/from PostgreSQL.
//! This includes:
//! - Player position and stats
//! - Skills and experience
//! - Inventory, bank, and equipment
//! - Settings and preferences
//! - Friends and ignore lists

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::postgres::PgPool;
use sqlx::FromRow;
use tracing::{debug, error, info};
use uuid::Uuid;

use crate::error::{GameError, Result, RustscapeError};

/// Skill IDs matching the RS protocol
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum SkillId {
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

impl SkillId {
    /// Get all skill IDs
    pub fn all() -> &'static [SkillId] {
        &[
            SkillId::Attack,
            SkillId::Defence,
            SkillId::Strength,
            SkillId::Hitpoints,
            SkillId::Ranged,
            SkillId::Prayer,
            SkillId::Magic,
            SkillId::Cooking,
            SkillId::Woodcutting,
            SkillId::Fletching,
            SkillId::Fishing,
            SkillId::Firemaking,
            SkillId::Crafting,
            SkillId::Smithing,
            SkillId::Mining,
            SkillId::Herblore,
            SkillId::Agility,
            SkillId::Thieving,
            SkillId::Slayer,
            SkillId::Farming,
            SkillId::Runecrafting,
            SkillId::Hunter,
            SkillId::Construction,
            SkillId::Summoning,
            SkillId::Dungeoneering,
        ]
    }

    /// Get skill name
    pub fn name(&self) -> &'static str {
        match self {
            SkillId::Attack => "Attack",
            SkillId::Defence => "Defence",
            SkillId::Strength => "Strength",
            SkillId::Hitpoints => "Hitpoints",
            SkillId::Ranged => "Ranged",
            SkillId::Prayer => "Prayer",
            SkillId::Magic => "Magic",
            SkillId::Cooking => "Cooking",
            SkillId::Woodcutting => "Woodcutting",
            SkillId::Fletching => "Fletching",
            SkillId::Fishing => "Fishing",
            SkillId::Firemaking => "Firemaking",
            SkillId::Crafting => "Crafting",
            SkillId::Smithing => "Smithing",
            SkillId::Mining => "Mining",
            SkillId::Herblore => "Herblore",
            SkillId::Agility => "Agility",
            SkillId::Thieving => "Thieving",
            SkillId::Slayer => "Slayer",
            SkillId::Farming => "Farming",
            SkillId::Runecrafting => "Runecrafting",
            SkillId::Hunter => "Hunter",
            SkillId::Construction => "Construction",
            SkillId::Summoning => "Summoning",
            SkillId::Dungeoneering => "Dungeoneering",
        }
    }

    /// Get skill from ID
    pub fn from_id(id: u8) -> Option<Self> {
        match id {
            0 => Some(SkillId::Attack),
            1 => Some(SkillId::Defence),
            2 => Some(SkillId::Strength),
            3 => Some(SkillId::Hitpoints),
            4 => Some(SkillId::Ranged),
            5 => Some(SkillId::Prayer),
            6 => Some(SkillId::Magic),
            7 => Some(SkillId::Cooking),
            8 => Some(SkillId::Woodcutting),
            9 => Some(SkillId::Fletching),
            10 => Some(SkillId::Fishing),
            11 => Some(SkillId::Firemaking),
            12 => Some(SkillId::Crafting),
            13 => Some(SkillId::Smithing),
            14 => Some(SkillId::Mining),
            15 => Some(SkillId::Herblore),
            16 => Some(SkillId::Agility),
            17 => Some(SkillId::Thieving),
            18 => Some(SkillId::Slayer),
            19 => Some(SkillId::Farming),
            20 => Some(SkillId::Runecrafting),
            21 => Some(SkillId::Hunter),
            22 => Some(SkillId::Construction),
            23 => Some(SkillId::Summoning),
            24 => Some(SkillId::Dungeoneering),
            _ => None,
        }
    }
}

/// Container types for items
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ContainerType {
    Inventory,
    Bank,
    Equipment,
}

impl ContainerType {
    /// Get database string representation
    pub fn as_str(&self) -> &'static str {
        match self {
            ContainerType::Inventory => "inventory",
            ContainerType::Bank => "bank",
            ContainerType::Equipment => "equipment",
        }
    }

    /// Parse from database string
    pub fn from_str(s: &str) -> Option<Self> {
        match s.to_lowercase().as_str() {
            "inventory" => Some(ContainerType::Inventory),
            "bank" => Some(ContainerType::Bank),
            "equipment" => Some(ContainerType::Equipment),
            _ => None,
        }
    }

    /// Get container capacity
    pub fn capacity(&self) -> usize {
        match self {
            ContainerType::Inventory => 28,
            ContainerType::Bank => 496,
            ContainerType::Equipment => 14,
        }
    }
}

/// Player position in the game world
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct Position {
    pub x: i32,
    pub y: i32,
    pub z: i16,
}

impl Position {
    /// Create a new position
    pub fn new(x: i32, y: i32, z: i16) -> Self {
        Self { x, y, z }
    }

    /// Default spawn position (Lumbridge)
    pub fn default_spawn() -> Self {
        Self {
            x: 3222,
            y: 3222,
            z: 0,
        }
    }

    /// Get the region X coordinate
    pub fn region_x(&self) -> i32 {
        self.x >> 3
    }

    /// Get the region Y coordinate
    pub fn region_y(&self) -> i32 {
        self.y >> 3
    }
}

impl Default for Position {
    fn default() -> Self {
        Self::default_spawn()
    }
}

/// A single skill's data
#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
pub struct Skill {
    pub id: u8,
    pub level: u8,
    pub xp: i32,
}

impl Skill {
    /// Create a new skill with default values
    pub fn new(id: u8) -> Self {
        let default_level = if id == SkillId::Hitpoints as u8 {
            10
        } else {
            1
        };
        let default_xp = if id == SkillId::Hitpoints as u8 {
            1154
        } else {
            0
        };

        Self {
            id,
            level: default_level,
            xp: default_xp,
        }
    }

    /// Calculate level from XP
    pub fn level_for_xp(xp: i32) -> u8 {
        let mut level = 1u8;
        let mut points = 0i32;

        for lvl in 1..=99u8 {
            points += (lvl as i32 + 300 * (2f64.powf(lvl as f64 / 7.0) as i32)) / 4;
            if points > xp {
                break;
            }
            level = lvl + 1;
        }

        level.min(99)
    }

    /// Get XP required for a level
    pub fn xp_for_level(level: u8) -> i32 {
        if level <= 1 {
            return 0;
        }

        let mut points = 0i32;
        for lvl in 1..level {
            points += (lvl as i32 + 300 * (2f64.powf(lvl as f64 / 7.0) as i32)) / 4;
        }
        points
    }
}

/// An item in a container
#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
pub struct Item {
    pub id: i32,
    pub amount: i32,
}

impl Item {
    /// Create a new item
    pub fn new(id: i32, amount: i32) -> Self {
        Self { id, amount }
    }

    /// Check if this is a valid item
    pub fn is_valid(&self) -> bool {
        self.id > 0 && self.amount > 0
    }
}

/// Player appearance data
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Appearance {
    pub gender: u8,
    pub head: u8,
    pub beard: u8,
    pub chest: u8,
    pub arms: u8,
    pub hands: u8,
    pub legs: u8,
    pub feet: u8,
    pub hair_color: u8,
    pub torso_color: u8,
    pub legs_color: u8,
    pub feet_color: u8,
    pub skin_color: u8,
}

impl Default for Appearance {
    fn default() -> Self {
        Self {
            gender: 0, // Male
            head: 0,
            beard: 10,
            chest: 18,
            arms: 26,
            hands: 33,
            legs: 36,
            feet: 42,
            hair_color: 0,
            torso_color: 0,
            legs_color: 0,
            feet_color: 0,
            skin_color: 0,
        }
    }
}

/// Complete player data for persistence
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PlayerData {
    /// Player UUID
    pub id: Uuid,
    /// User UUID
    pub user_id: Uuid,
    /// Display name
    pub display_name: String,
    /// Current position
    pub position: Position,
    /// Combat level
    pub combat_level: i16,
    /// Total level
    pub total_level: i32,
    /// Total XP
    pub total_xp: i64,
    /// Run energy (0-10000)
    pub run_energy: i32,
    /// Special attack energy (0-1000)
    pub special_energy: i32,
    /// Player appearance
    pub appearance: Appearance,
    /// Skills
    pub skills: Vec<Skill>,
    /// Inventory items
    pub inventory: Vec<Option<Item>>,
    /// Bank items
    pub bank: Vec<Option<Item>>,
    /// Equipment
    pub equipment: Vec<Option<Item>>,
    /// Time played in seconds
    pub time_played: i64,
    /// Last save timestamp
    pub last_saved: Option<DateTime<Utc>>,
}

impl PlayerData {
    /// Create a new player with default values
    pub fn new(user_id: Uuid, display_name: String) -> Self {
        // Initialize all skills
        let skills: Vec<Skill> = (0..=24).map(Skill::new).collect();

        // Calculate total level and XP
        let total_level: i32 = skills.iter().map(|s| s.level as i32).sum();
        let total_xp: i64 = skills.iter().map(|s| s.xp as i64).sum();

        // Calculate combat level
        let combat_level = Self::calculate_combat_level(&skills);

        Self {
            id: Uuid::new_v4(),
            user_id,
            display_name,
            position: Position::default_spawn(),
            combat_level,
            total_level,
            total_xp,
            run_energy: 10000,
            special_energy: 1000,
            appearance: Appearance::default(),
            skills,
            inventory: vec![None; 28],
            bank: vec![None; 496],
            equipment: vec![None; 14],
            time_played: 0,
            last_saved: None,
        }
    }

    /// Calculate combat level from skills
    pub fn calculate_combat_level(skills: &[Skill]) -> i16 {
        let attack = skills
            .iter()
            .find(|s| s.id == 0)
            .map(|s| s.level)
            .unwrap_or(1) as f64;
        let defence = skills
            .iter()
            .find(|s| s.id == 1)
            .map(|s| s.level)
            .unwrap_or(1) as f64;
        let strength = skills
            .iter()
            .find(|s| s.id == 2)
            .map(|s| s.level)
            .unwrap_or(1) as f64;
        let hitpoints = skills
            .iter()
            .find(|s| s.id == 3)
            .map(|s| s.level)
            .unwrap_or(10) as f64;
        let ranged = skills
            .iter()
            .find(|s| s.id == 4)
            .map(|s| s.level)
            .unwrap_or(1) as f64;
        let prayer = skills
            .iter()
            .find(|s| s.id == 5)
            .map(|s| s.level)
            .unwrap_or(1) as f64;
        let magic = skills
            .iter()
            .find(|s| s.id == 6)
            .map(|s| s.level)
            .unwrap_or(1) as f64;
        let summoning = skills
            .iter()
            .find(|s| s.id == 23)
            .map(|s| s.level)
            .unwrap_or(1) as f64;

        let base =
            (defence + hitpoints + (prayer / 2.0).floor() + (summoning / 2.0).floor()) * 0.25;
        let melee = (attack + strength) * 0.325;
        let range = (ranged * 1.5).floor() * 0.325;
        let mage = (magic * 1.5).floor() * 0.325;

        let combat = base + melee.max(range).max(mage);
        combat.floor() as i16
    }

    /// Get a skill by ID
    pub fn get_skill(&self, skill_id: u8) -> Option<&Skill> {
        self.skills.iter().find(|s| s.id == skill_id)
    }

    /// Get a mutable skill by ID
    pub fn get_skill_mut(&mut self, skill_id: u8) -> Option<&mut Skill> {
        self.skills.iter_mut().find(|s| s.id == skill_id)
    }

    /// Add XP to a skill
    pub fn add_xp(&mut self, skill_id: u8, xp: i32) -> Option<(u8, u8)> {
        // Find the skill index first
        let skill_idx = self.skills.iter().position(|s| s.id == skill_id)?;

        // Get old level before modification
        let old_level = self.skills[skill_idx].level;

        // Update the skill
        self.skills[skill_idx].xp = (self.skills[skill_idx].xp + xp).min(200_000_000);
        self.skills[skill_idx].level = Skill::level_for_xp(self.skills[skill_idx].xp);

        let new_level = self.skills[skill_idx].level;

        // Update totals
        self.total_xp = self.skills.iter().map(|s| s.xp as i64).sum();
        self.total_level = self.skills.iter().map(|s| s.level as i32).sum();

        // Recalculate combat if combat skill changed
        if skill_id <= 6 || skill_id == 23 {
            self.combat_level = Self::calculate_combat_level(&self.skills);
        }

        if new_level > old_level {
            Some((old_level, new_level))
        } else {
            None
        }
    }

    /// Get an item from a container
    pub fn get_item(&self, container: ContainerType, slot: usize) -> Option<&Item> {
        let items = match container {
            ContainerType::Inventory => &self.inventory,
            ContainerType::Bank => &self.bank,
            ContainerType::Equipment => &self.equipment,
        };

        items.get(slot).and_then(|i| i.as_ref())
    }

    /// Set an item in a container
    pub fn set_item(&mut self, container: ContainerType, slot: usize, item: Option<Item>) -> bool {
        let items = match container {
            ContainerType::Inventory => &mut self.inventory,
            ContainerType::Bank => &mut self.bank,
            ContainerType::Equipment => &mut self.equipment,
        };

        if slot < items.len() {
            items[slot] = item;
            true
        } else {
            false
        }
    }
}

/// Database row for player data
#[derive(Debug, FromRow)]
struct PlayerRow {
    id: Uuid,
    user_id: Uuid,
    display_name: String,
    coord_x: i32,
    coord_y: i32,
    coord_z: i16,
    combat_level: i16,
    total_level: i32,
    total_xp: i64,
    run_energy: i32,
    special_energy: i32,
    #[allow(dead_code)]
    gender: i16,
    appearance: serde_json::Value,
    time_played: i64,
    updated_at: DateTime<Utc>,
}

/// Database row for skill data
#[derive(Debug, FromRow)]
struct SkillRow {
    skill_id: i16,
    level: i16,
    xp: i32,
}

/// Database row for item data
#[derive(Debug, FromRow)]
struct ItemRow {
    container_type: String,
    slot: i32,
    item_id: i32,
    amount: i32,
}

/// Player persistence service
pub struct PlayerPersistence {
    pool: PgPool,
}

impl PlayerPersistence {
    /// Create a new persistence service
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }

    /// Load player data by user ID
    pub async fn load_by_user_id(&self, user_id: Uuid) -> Result<Option<PlayerData>> {
        // Load player row
        let player_row: Option<PlayerRow> = sqlx::query_as(
            r#"
            SELECT id, user_id, display_name, coord_x, coord_y, coord_z,
                   combat_level, total_level, total_xp, run_energy, special_energy,
                   gender, appearance, time_played, updated_at
            FROM players
            WHERE user_id = $1
            "#,
        )
        .bind(user_id)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| {
            error!("Failed to load player: {}", e);
            RustscapeError::Game(GameError::DatabaseError(e.to_string()))
        })?;

        let player_row = match player_row {
            Some(row) => row,
            None => return Ok(None),
        };

        let player_id = player_row.id;

        // Load skills
        let skill_rows: Vec<SkillRow> = sqlx::query_as(
            r#"
            SELECT skill_id, level, xp
            FROM player_skills
            WHERE player_id = $1
            ORDER BY skill_id
            "#,
        )
        .bind(player_id)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| {
            error!("Failed to load skills: {}", e);
            RustscapeError::Game(GameError::DatabaseError(e.to_string()))
        })?;

        // Load items
        let item_rows: Vec<ItemRow> = sqlx::query_as(
            r#"
            SELECT container_type, slot, item_id, amount
            FROM player_inventory
            WHERE player_id = $1
            ORDER BY container_type, slot
            "#,
        )
        .bind(player_id)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| {
            error!("Failed to load inventory: {}", e);
            RustscapeError::Game(GameError::DatabaseError(e.to_string()))
        })?;

        // Build PlayerData
        let mut player_data = PlayerData {
            id: player_row.id,
            user_id: player_row.user_id,
            display_name: player_row.display_name,
            position: Position::new(player_row.coord_x, player_row.coord_y, player_row.coord_z),
            combat_level: player_row.combat_level,
            total_level: player_row.total_level,
            total_xp: player_row.total_xp,
            run_energy: player_row.run_energy,
            special_energy: player_row.special_energy,
            appearance: serde_json::from_value(player_row.appearance).unwrap_or_default(),
            skills: Vec::new(),
            inventory: vec![None; 28],
            bank: vec![None; 496],
            equipment: vec![None; 14],
            time_played: player_row.time_played,
            last_saved: Some(player_row.updated_at),
        };

        // Populate skills
        player_data.skills = (0..=24)
            .map(|id| {
                skill_rows
                    .iter()
                    .find(|r| r.skill_id == id as i16)
                    .map(|r| Skill {
                        id: r.skill_id as u8,
                        level: r.level as u8,
                        xp: r.xp,
                    })
                    .unwrap_or_else(|| Skill::new(id))
            })
            .collect();

        // Populate items
        for item_row in item_rows {
            let container = match item_row.container_type.as_str() {
                "inventory" => &mut player_data.inventory,
                "bank" => &mut player_data.bank,
                "equipment" => &mut player_data.equipment,
                _ => continue,
            };

            let slot = item_row.slot as usize;
            if slot < container.len() {
                container[slot] = Some(Item::new(item_row.item_id, item_row.amount));
            }
        }

        debug!(
            player_id = %player_data.id,
            display_name = %player_data.display_name,
            "Loaded player data"
        );

        Ok(Some(player_data))
    }

    /// Save player data
    pub async fn save(&self, player: &PlayerData) -> Result<()> {
        let appearance_json =
            serde_json::to_value(&player.appearance).unwrap_or(serde_json::json!({}));

        // Update player row
        sqlx::query(
            r#"
            UPDATE players SET
                display_name = $1,
                coord_x = $2,
                coord_y = $3,
                coord_z = $4,
                combat_level = $5,
                total_level = $6,
                total_xp = $7,
                run_energy = $8,
                special_energy = $9,
                gender = $10,
                appearance = $11,
                time_played = $12,
                updated_at = NOW()
            WHERE id = $13
            "#,
        )
        .bind(&player.display_name)
        .bind(player.position.x)
        .bind(player.position.y)
        .bind(player.position.z)
        .bind(player.combat_level)
        .bind(player.total_level)
        .bind(player.total_xp)
        .bind(player.run_energy)
        .bind(player.special_energy)
        .bind(player.appearance.gender as i16)
        .bind(&appearance_json)
        .bind(player.time_played)
        .bind(player.id)
        .execute(&self.pool)
        .await
        .map_err(|e| {
            error!("Failed to save player: {}", e);
            RustscapeError::Game(GameError::DatabaseError(e.to_string()))
        })?;

        // Update skills
        for skill in &player.skills {
            sqlx::query(
                r#"
                INSERT INTO player_skills (player_id, skill_id, level, xp)
                VALUES ($1, $2, $3, $4)
                ON CONFLICT (player_id, skill_id)
                DO UPDATE SET level = $3, xp = $4
                "#,
            )
            .bind(player.id)
            .bind(skill.id as i16)
            .bind(skill.level as i16)
            .bind(skill.xp)
            .execute(&self.pool)
            .await
            .map_err(|e| {
                error!("Failed to save skill {}: {}", skill.id, e);
                RustscapeError::Game(GameError::DatabaseError(e.to_string()))
            })?;
        }

        // Save inventory items
        self.save_container(player.id, ContainerType::Inventory, &player.inventory)
            .await?;
        self.save_container(player.id, ContainerType::Bank, &player.bank)
            .await?;
        self.save_container(player.id, ContainerType::Equipment, &player.equipment)
            .await?;

        debug!(
            player_id = %player.id,
            display_name = %player.display_name,
            "Saved player data"
        );

        Ok(())
    }

    /// Save a container's items
    async fn save_container(
        &self,
        player_id: Uuid,
        container: ContainerType,
        items: &[Option<Item>],
    ) -> Result<()> {
        let container_str = container.as_str();

        // Delete existing items
        sqlx::query(
            r#"
            DELETE FROM player_inventory
            WHERE player_id = $1 AND container_type = $2
            "#,
        )
        .bind(player_id)
        .bind(container_str)
        .execute(&self.pool)
        .await
        .map_err(|e| {
            error!("Failed to clear container {}: {}", container_str, e);
            RustscapeError::Game(GameError::DatabaseError(e.to_string()))
        })?;

        // Insert non-empty slots
        for (slot, item) in items.iter().enumerate() {
            if let Some(item) = item {
                if item.is_valid() {
                    sqlx::query(
                        r#"
                        INSERT INTO player_inventory (player_id, container_type, slot, item_id, amount)
                        VALUES ($1, $2, $3, $4, $5)
                        "#,
                    )
                    .bind(player_id)
                    .bind(container_str)
                    .bind(slot as i32)
                    .bind(item.id)
                    .bind(item.amount)
                    .execute(&self.pool)
                    .await
                    .map_err(|e| {
                        error!("Failed to save item at slot {}: {}", slot, e);
                        RustscapeError::Game(GameError::DatabaseError(e.to_string()))
                    })?;
                }
            }
        }

        Ok(())
    }

    /// Delete player data
    pub async fn delete(&self, player_id: Uuid) -> Result<()> {
        sqlx::query("DELETE FROM players WHERE id = $1")
            .bind(player_id)
            .execute(&self.pool)
            .await
            .map_err(|e| {
                error!("Failed to delete player: {}", e);
                RustscapeError::Game(GameError::DatabaseError(e.to_string()))
            })?;

        info!(player_id = %player_id, "Deleted player data");
        Ok(())
    }

    /// Check if a user has a player
    pub async fn exists_for_user(&self, user_id: Uuid) -> Result<bool> {
        let count: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM players WHERE user_id = $1")
            .bind(user_id)
            .fetch_one(&self.pool)
            .await
            .map_err(|e| {
                error!("Failed to check player existence: {}", e);
                RustscapeError::Game(GameError::DatabaseError(e.to_string()))
            })?;

        Ok(count.0 > 0)
    }

    /// Create a new player for a user and save to database
    /// Returns the created PlayerData with its new ID
    pub async fn create_for_user(&self, user_id: Uuid, display_name: String) -> Result<PlayerData> {
        // Create new player with default values
        let player = PlayerData::new(user_id, display_name.clone());
        let appearance_json =
            serde_json::to_value(&player.appearance).unwrap_or(serde_json::json!({}));

        // Insert player row
        sqlx::query(
            r#"
            INSERT INTO players (
                id, user_id, display_name,
                coord_x, coord_y, coord_z,
                combat_level, total_level, total_xp,
                run_energy, special_energy,
                gender, appearance, time_played
            ) VALUES (
                $1, $2, $3,
                $4, $5, $6,
                $7, $8, $9,
                $10, $11,
                $12, $13, $14
            )
            "#,
        )
        .bind(player.id)
        .bind(player.user_id)
        .bind(&player.display_name)
        .bind(player.position.x)
        .bind(player.position.y)
        .bind(player.position.z)
        .bind(player.combat_level)
        .bind(player.total_level)
        .bind(player.total_xp)
        .bind(player.run_energy)
        .bind(player.special_energy)
        .bind(player.appearance.gender as i16)
        .bind(&appearance_json)
        .bind(player.time_played)
        .execute(&self.pool)
        .await
        .map_err(|e| {
            error!("Failed to create player: {}", e);
            RustscapeError::Game(GameError::DatabaseError(e.to_string()))
        })?;

        // Insert default skills
        for skill in &player.skills {
            sqlx::query(
                r#"
                INSERT INTO player_skills (player_id, skill_id, level, xp)
                VALUES ($1, $2, $3, $4)
                "#,
            )
            .bind(player.id)
            .bind(skill.id as i16)
            .bind(skill.level as i16)
            .bind(skill.xp)
            .execute(&self.pool)
            .await
            .map_err(|e| {
                error!("Failed to create skill {}: {}", skill.id, e);
                RustscapeError::Game(GameError::DatabaseError(e.to_string()))
            })?;
        }

        info!(
            player_id = %player.id,
            user_id = %user_id,
            display_name = %display_name,
            "Created new player"
        );

        Ok(player)
    }

    /// Load or create player data for a user
    /// If the player doesn't exist, creates a new one with the given display name
    pub async fn load_or_create(&self, user_id: Uuid, display_name: String) -> Result<PlayerData> {
        // Try to load existing player
        if let Some(player) = self.load_by_user_id(user_id).await? {
            debug!(
                player_id = %player.id,
                user_id = %user_id,
                "Loaded existing player"
            );
            return Ok(player);
        }

        // Create new player
        self.create_for_user(user_id, display_name).await
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_skill_level_calculation() {
        assert_eq!(Skill::level_for_xp(0), 1);
        assert_eq!(Skill::level_for_xp(74), 1);
        assert_eq!(Skill::level_for_xp(75), 2);
        assert_eq!(Skill::level_for_xp(149), 2);
        assert_eq!(Skill::level_for_xp(150), 3);
        assert_eq!(Skill::level_for_xp(1059), 10);
        assert_eq!(Skill::level_for_xp(1060), 11);
        assert_eq!(Skill::level_for_xp(13_034_431), 99);
        assert_eq!(Skill::level_for_xp(200_000_000), 99);
    }

    #[test]
    fn test_skill_xp_for_level() {
        assert_eq!(Skill::xp_for_level(1), 0);
        assert_eq!(Skill::xp_for_level(2), 75);
        assert_eq!(Skill::xp_for_level(3), 150);
        // Level 99 requires approximately 13 million XP
        assert!(Skill::xp_for_level(99) > 13_000_000);
    }

    #[test]
    fn test_default_spawn() {
        let pos = Position::default_spawn();
        assert_eq!(pos.x, 3222);
        assert_eq!(pos.y, 3222);
        assert_eq!(pos.z, 0);
    }

    #[test]
    fn test_combat_level_calculation() {
        // All level 1 skills except HP at 10
        let skills: Vec<Skill> = (0..=24).map(Skill::new).collect();
        let combat = PlayerData::calculate_combat_level(&skills);
        assert_eq!(combat, 3); // Base combat level
    }

    #[test]
    fn test_player_data_creation() {
        let user_id = Uuid::new_v4();
        let player = PlayerData::new(user_id, "TestPlayer".to_string());

        assert_eq!(player.user_id, user_id);
        assert_eq!(player.display_name, "TestPlayer");
        assert_eq!(player.skills.len(), 25);
        assert_eq!(player.inventory.len(), 28);
        assert_eq!(player.bank.len(), 496);
        assert_eq!(player.equipment.len(), 14);
    }

    #[test]
    fn test_add_xp() {
        let user_id = Uuid::new_v4();
        let mut player = PlayerData::new(user_id, "TestPlayer".to_string());

        // Add XP to Attack (skill 0)
        let level_up = player.add_xp(0, 100);
        assert!(level_up.is_some()); // Should level up from 1 to 2

        let attack = player.get_skill(0).unwrap();
        assert_eq!(attack.xp, 100);
        assert_eq!(attack.level, 2);
    }

    #[test]
    fn test_item_operations() {
        let user_id = Uuid::new_v4();
        let mut player = PlayerData::new(user_id, "TestPlayer".to_string());

        // Set an item
        let item = Item::new(1234, 5);
        assert!(player.set_item(ContainerType::Inventory, 0, Some(item)));

        // Get the item back
        let retrieved = player.get_item(ContainerType::Inventory, 0);
        assert!(retrieved.is_some());
        let retrieved = retrieved.unwrap();
        assert_eq!(retrieved.id, 1234);
        assert_eq!(retrieved.amount, 5);

        // Invalid slot
        assert!(!player.set_item(ContainerType::Inventory, 100, Some(item)));
    }

    #[test]
    fn test_container_type() {
        assert_eq!(ContainerType::Inventory.as_str(), "inventory");
        assert_eq!(ContainerType::Bank.capacity(), 496);
        assert_eq!(
            ContainerType::from_str("equipment"),
            Some(ContainerType::Equipment)
        );
    }

    #[test]
    fn test_skill_id() {
        assert_eq!(SkillId::Attack.name(), "Attack");
        assert_eq!(SkillId::from_id(3), Some(SkillId::Hitpoints));
        assert_eq!(SkillId::from_id(255), None);
        assert_eq!(SkillId::all().len(), 25);
    }
}
