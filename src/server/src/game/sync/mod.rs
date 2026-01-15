//! Player synchronization module
//!
//! Handles synchronization of player state to all connected clients.
//! This includes:
//! - Building player update packets (opcode 81)
//! - Tracking local player lists (who can see whom)
//! - Managing update flags (appearance, chat, animation, etc.)
//! - Broadcasting updates each game tick

pub mod player_sync;
pub mod update_flags;

pub use player_sync::{PlayerSyncManager, SyncConfig};
pub use update_flags::UpdateFlags;
