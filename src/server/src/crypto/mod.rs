//! Cryptography module
//!
//! This module provides cryptographic primitives used by the Rustscape server:
//! - ISAAC cipher for packet opcode encryption
//! - RSA for secure key exchange during login

pub mod isaac;
pub mod rsa;

// Re-export commonly used types
pub use isaac::IsaacPair;
pub use rsa::RsaDecryptor;
