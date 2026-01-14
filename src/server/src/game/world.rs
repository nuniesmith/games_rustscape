//! World module
//!
//! Manages the game world including:
//! - Game tick loop (600ms intervals)
//! - Entity updates (players, NPCs, objects)
//! - Region management
//! - World events and broadcasts

use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

use parking_lot::RwLock;
use tokio::sync::broadcast;
use tokio::time::{interval, MissedTickBehavior};
use tracing::{debug, error, info};

use crate::error::Result;

/// Standard game tick rate in milliseconds
pub const TICK_RATE_MS: u64 = 600;

/// Maximum players per world
pub const MAX_PLAYERS: usize = 2048;

/// Maximum NPCs per world
pub const MAX_NPCS: usize = 32768;

/// World settings
#[derive(Debug, Clone)]
pub struct WorldSettings {
    /// World ID (1-255)
    pub world_id: u8,
    /// World name
    pub name: String,
    /// Whether this is a members world
    pub members: bool,
    /// Whether PvP is enabled
    pub pvp: bool,
    /// Whether this world is in beta/development mode
    pub dev_mode: bool,
    /// Tick rate in milliseconds
    pub tick_rate_ms: u64,
    /// Maximum players allowed
    pub max_players: usize,
}

impl Default for WorldSettings {
    fn default() -> Self {
        Self {
            world_id: 1,
            name: "Rustscape".to_string(),
            members: false,
            pvp: false,
            dev_mode: true,
            tick_rate_ms: TICK_RATE_MS,
            max_players: MAX_PLAYERS,
        }
    }
}

impl WorldSettings {
    /// Create new world settings with a specific ID
    pub fn new(world_id: u8) -> Self {
        Self {
            world_id,
            ..Default::default()
        }
    }

    /// Set the world name
    pub fn with_name(mut self, name: impl Into<String>) -> Self {
        self.name = name.into();
        self
    }

    /// Set members-only flag
    pub fn with_members(mut self, members: bool) -> Self {
        self.members = members;
        self
    }

    /// Set PvP flag
    pub fn with_pvp(mut self, pvp: bool) -> Self {
        self.pvp = pvp;
        self
    }
}

/// World state
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WorldState {
    /// World is initializing
    Initializing,
    /// World is running normally
    Running,
    /// World is updating (countdown active)
    Updating,
    /// World is shutting down
    ShuttingDown,
    /// World has stopped
    Stopped,
}

impl WorldState {
    /// Check if the world is accepting new connections
    pub fn accepting_connections(&self) -> bool {
        matches!(self, WorldState::Running)
    }

    /// Check if the world is processing ticks
    pub fn is_active(&self) -> bool {
        matches!(self, WorldState::Running | WorldState::Updating)
    }
}

/// Game world - manages the game tick and entity lifecycle
pub struct GameWorld {
    /// World settings
    pub settings: WorldSettings,
    /// Current world state
    state: RwLock<WorldState>,
    /// Current tick number
    tick: AtomicU64,
    /// Whether the world is running
    running: AtomicBool,
    /// Time the world started
    start_time: RwLock<Option<Instant>>,
    /// Update countdown (ticks until shutdown)
    update_countdown: AtomicU64,
    /// Number of online players
    player_count: AtomicU64,
}

impl GameWorld {
    /// Create a new game world
    pub fn new(world_id: u8) -> Result<Self> {
        let settings = WorldSettings::new(world_id);
        Self::with_settings(settings)
    }

    /// Create a new game world with custom settings
    pub fn with_settings(settings: WorldSettings) -> Result<Self> {
        info!(
            world_id = settings.world_id,
            name = %settings.name,
            "Creating game world"
        );

        Ok(Self {
            settings,
            state: RwLock::new(WorldState::Initializing),
            tick: AtomicU64::new(0),
            running: AtomicBool::new(false),
            start_time: RwLock::new(None),
            update_countdown: AtomicU64::new(0),
            player_count: AtomicU64::new(0),
        })
    }

    /// Get the current world state
    pub fn state(&self) -> WorldState {
        *self.state.read()
    }

    /// Set the world state
    pub fn set_state(&self, new_state: WorldState) {
        let mut state = self.state.write();
        let old_state = *state;
        *state = new_state;
        info!(
            old_state = ?old_state,
            new_state = ?new_state,
            "World state changed"
        );
    }

    /// Get the current tick number
    pub fn tick(&self) -> u64 {
        self.tick.load(Ordering::SeqCst)
    }

    /// Check if the world is running
    pub fn is_running(&self) -> bool {
        self.running.load(Ordering::SeqCst)
    }

    /// Get the player count
    pub fn player_count(&self) -> u64 {
        self.player_count.load(Ordering::SeqCst)
    }

    /// Increment the player count
    pub fn increment_player_count(&self) {
        self.player_count.fetch_add(1, Ordering::SeqCst);
    }

    /// Decrement the player count
    pub fn decrement_player_count(&self) {
        self.player_count.fetch_sub(1, Ordering::SeqCst);
    }

    /// Get the uptime in seconds
    pub fn uptime_secs(&self) -> u64 {
        self.start_time
            .read()
            .map(|t| t.elapsed().as_secs())
            .unwrap_or(0)
    }

    /// Start an update countdown
    pub fn start_update(&self, ticks: u64) {
        self.update_countdown.store(ticks, Ordering::SeqCst);
        self.set_state(WorldState::Updating);
        info!(ticks = ticks, "Update countdown started");
    }

    /// Cancel an update countdown
    pub fn cancel_update(&self) {
        self.update_countdown.store(0, Ordering::SeqCst);
        self.set_state(WorldState::Running);
        info!("Update countdown cancelled");
    }

    /// Run the game world tick loop
    pub async fn run(&self, shutdown_rx: &mut broadcast::Receiver<()>) {
        info!(
            world_id = self.settings.world_id,
            tick_rate_ms = self.settings.tick_rate_ms,
            "Starting game world"
        );

        // Mark as running
        self.running.store(true, Ordering::SeqCst);
        *self.start_time.write() = Some(Instant::now());
        self.set_state(WorldState::Running);

        // Create tick interval
        let mut tick_interval = interval(Duration::from_millis(self.settings.tick_rate_ms));
        tick_interval.set_missed_tick_behavior(MissedTickBehavior::Skip);

        // Main game loop
        loop {
            tokio::select! {
                _ = tick_interval.tick() => {
                    if !self.is_running() {
                        break;
                    }

                    // Process game tick
                    if let Err(e) = self.process_tick().await {
                        error!(error = %e, "Error processing game tick");
                    }

                    // Check update countdown
                    let countdown = self.update_countdown.load(Ordering::SeqCst);
                    if countdown > 0 {
                        let new_countdown = countdown - 1;
                        self.update_countdown.store(new_countdown, Ordering::SeqCst);

                        if new_countdown == 0 {
                            info!("Update countdown reached zero, initiating shutdown");
                            break;
                        }
                    }
                }
                _ = shutdown_rx.recv() => {
                    info!("Received shutdown signal");
                    break;
                }
            }
        }

        // Cleanup
        self.running.store(false, Ordering::SeqCst);
        self.set_state(WorldState::Stopped);

        info!(
            total_ticks = self.tick(),
            uptime_secs = self.uptime_secs(),
            "Game world stopped"
        );
    }

    /// Process a single game tick
    async fn process_tick(&self) -> Result<()> {
        let tick_num = self.tick.fetch_add(1, Ordering::SeqCst);

        // Log periodically
        if tick_num % 1000 == 0 {
            debug!(
                tick = tick_num,
                players = self.player_count(),
                "Game tick milestone"
            );
        }

        // Process in order:
        // 1. Handle incoming packets (done in connection handlers)
        // 2. Process player actions
        // 3. Process NPC actions
        // 4. Process timers and events
        // 5. Update entity positions
        // 6. Send outgoing packets

        // TODO: Implement actual tick processing
        // For now, this is just a placeholder

        Ok(())
    }

    /// Broadcast a message to all players
    pub fn broadcast_message(&self, message: &str) {
        info!(message = %message, "Broadcasting message to all players");
        // TODO: Implement actual broadcasting
    }

    /// Get world info as a string
    pub fn info(&self) -> String {
        format!(
            "World {} ({}) - {} players - Tick {} - Uptime {}s",
            self.settings.world_id,
            self.settings.name,
            self.player_count(),
            self.tick(),
            self.uptime_secs()
        )
    }
}

impl std::fmt::Debug for GameWorld {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("GameWorld")
            .field("settings", &self.settings)
            .field("state", &self.state())
            .field("tick", &self.tick())
            .field("running", &self.is_running())
            .field("player_count", &self.player_count())
            .field("uptime_secs", &self.uptime_secs())
            .finish()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_world_settings_default() {
        let settings = WorldSettings::default();
        assert_eq!(settings.world_id, 1);
        assert_eq!(settings.tick_rate_ms, TICK_RATE_MS);
        assert!(!settings.members);
        assert!(!settings.pvp);
    }

    #[test]
    fn test_world_settings_builder() {
        let settings = WorldSettings::new(5)
            .with_name("Test World")
            .with_members(true)
            .with_pvp(true);

        assert_eq!(settings.world_id, 5);
        assert_eq!(settings.name, "Test World");
        assert!(settings.members);
        assert!(settings.pvp);
    }

    #[test]
    fn test_world_creation() {
        let world = GameWorld::new(1).unwrap();
        assert_eq!(world.settings.world_id, 1);
        assert_eq!(world.tick(), 0);
        assert!(!world.is_running());
        assert_eq!(world.player_count(), 0);
    }

    #[test]
    fn test_world_state() {
        let world = GameWorld::new(1).unwrap();
        assert_eq!(world.state(), WorldState::Initializing);

        world.set_state(WorldState::Running);
        assert_eq!(world.state(), WorldState::Running);
        assert!(world.state().accepting_connections());
        assert!(world.state().is_active());

        world.set_state(WorldState::Updating);
        assert!(!world.state().accepting_connections());
        assert!(world.state().is_active());

        world.set_state(WorldState::Stopped);
        assert!(!world.state().accepting_connections());
        assert!(!world.state().is_active());
    }

    #[test]
    fn test_player_count() {
        let world = GameWorld::new(1).unwrap();
        assert_eq!(world.player_count(), 0);

        world.increment_player_count();
        assert_eq!(world.player_count(), 1);

        world.increment_player_count();
        assert_eq!(world.player_count(), 2);

        world.decrement_player_count();
        assert_eq!(world.player_count(), 1);
    }

    #[test]
    fn test_update_countdown() {
        let world = GameWorld::new(1).unwrap();
        world.set_state(WorldState::Running);

        world.start_update(100);
        assert_eq!(world.state(), WorldState::Updating);

        world.cancel_update();
        assert_eq!(world.state(), WorldState::Running);
    }

    #[test]
    fn test_world_info() {
        let world = GameWorld::new(1).unwrap();
        let info = world.info();

        assert!(info.contains("World 1"));
        assert!(info.contains("Rustscape"));
    }
}
