//! Networking module
//!
//! This module handles all network-related functionality for the Rustscape server:
//! - TCP socket handling for native clients
//! - WebSocket handling for browser clients
//! - Session management
//! - Connection lifecycle

pub mod buffer;
pub mod handler;
pub mod session;
pub mod transport;
