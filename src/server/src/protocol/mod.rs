//! Protocol module
//!
//! This module contains all protocol-related implementations for the Rustscape server:
//! - Handshake protocol (initial connection negotiation)
//! - JS5 protocol (cache file serving)
//! - Login protocol (authentication and session setup)
//! - Game protocol (in-game packet handling)

pub mod game;
pub mod handshake;
pub mod js5;
pub mod login;
pub mod login_init;
pub mod packets;
