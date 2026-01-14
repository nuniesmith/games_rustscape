//! Rustscape Game Server Library
//!
//! This library provides the core functionality for the Rustscape game server,
//! including cache handling, protocol implementations, and game logic.
//!
//! ## Modules
//!
//! - `cache` - Game cache reading and serving (sprites, models, maps, etc.)
//! - `config` - Server configuration management
//! - `crypto` - Cryptographic utilities (ISAAC, RSA, etc.)
//! - `error` - Error types and result definitions
//! - `game` - Game world and entity management
//! - `net` - Network handling and session management
//! - `protocol` - RS protocol implementation

pub mod api;
pub mod auth;
pub mod cache;
pub mod config;
pub mod crypto;
pub mod error;
pub mod game;
pub mod net;
pub mod protocol;
pub mod state;

// Re-export commonly used types
pub use config::ServerConfig;
pub use error::{Result, RustscapeError};
pub use state::AppState;

/// Server version
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// Server revision (must match client)
pub const REVISION: u32 = 530;
