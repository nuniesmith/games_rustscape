//! Game module
//!
//! This module contains the core game logic for the Rustscape server:
//! - World management (game tick, entity updates)
//! - Player management (sessions, states, actions)
//! - Entity systems (NPCs, objects, ground items)
//! - Region/map management
//! - Combat and skills (future)

pub mod persistence;
pub mod player;
pub mod world;
