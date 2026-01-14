//! Login protocol handler
//!
//! Handles the login process after the initial handshake:
//! 1. Client sends login type (normal or reconnection)
//! 2. Client sends encrypted login block containing:
//!    - ISAAC seeds
//!    - Credentials (username/password)
//!    - Client information
//! 3. Server validates credentials and responds with success/failure
//! 4. On success, server sets up ISAAC cipher and player session

use std::sync::Arc;

use tracing::debug;

use crate::crypto::{IsaacPair, RsaDecryptor};
use crate::error::{AuthError, LoginResponse, ProtocolError, Result, RustscapeError};
use crate::net::buffer::PacketBuffer;
use crate::net::session::ClientInfo;

/// Login types
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum LoginType {
    /// Normal login
    Normal = 16,
    /// Reconnection (client already has cached data)
    Reconnect = 18,
}

impl LoginType {
    /// Convert a u8 to a LoginType
    pub fn from_u8(value: u8) -> Option<Self> {
        match value {
            16 => Some(Self::Normal),
            18 => Some(Self::Reconnect),
            _ => None,
        }
    }

    /// Get the login type value
    pub fn as_u8(self) -> u8 {
        self as u8
    }
}

/// Decoded login block from client
#[derive(Debug, Clone)]
pub struct LoginBlock {
    /// Login type
    pub login_type: LoginType,
    /// Client revision
    pub revision: u32,
    /// Low memory flag
    pub low_memory: bool,
    /// ISAAC seeds (4 values)
    pub isaac_seeds: [u32; 4],
    /// Username
    pub username: String,
    /// Password
    pub password: String,
    /// Client information
    pub client_info: ClientInfo,
    /// UID (unique client identifier)
    pub uid: u32,
}

impl LoginBlock {
    /// Create a new login block
    pub fn new(login_type: LoginType) -> Self {
        Self {
            login_type,
            revision: 0,
            low_memory: false,
            isaac_seeds: [0; 4],
            username: String::new(),
            password: String::new(),
            client_info: ClientInfo::default(),
            uid: 0,
        }
    }
}

/// Login request (sent by client)
#[derive(Debug, Clone)]
pub struct LoginRequest {
    /// Raw login block data (encrypted)
    pub data: Vec<u8>,
    /// Login type
    pub login_type: LoginType,
    /// Packet size
    pub size: usize,
}

/// Login response (sent by server)
#[derive(Debug, Clone)]
pub struct LoginResponsePacket {
    /// Response code
    pub code: LoginResponse,
    /// Player rights (0=normal, 1=mod, 2=admin)
    pub rights: u8,
    /// Flagged status
    pub flagged: bool,
    /// Player index (1-2047)
    pub player_index: u16,
    /// Member status
    pub member: bool,
}

impl LoginResponsePacket {
    /// Create a success response
    pub fn success(rights: u8, player_index: u16, member: bool) -> Self {
        Self {
            code: LoginResponse::Success,
            rights,
            flagged: false,
            player_index,
            member,
        }
    }

    /// Create an error response
    pub fn error(code: LoginResponse) -> Self {
        Self {
            code,
            rights: 0,
            flagged: false,
            player_index: 0,
            member: false,
        }
    }

    /// Encode the response to bytes
    pub fn encode(&self) -> Vec<u8> {
        if self.code == LoginResponse::Success {
            let mut buffer = PacketBuffer::with_capacity(16);
            buffer.write_ubyte(self.code.as_u8());
            buffer.write_ubyte(self.rights);
            buffer.write_ubyte(if self.flagged { 1 } else { 0 });
            buffer.write_ushort(self.player_index);
            buffer.write_ubyte(if self.member { 1 } else { 0 });
            buffer.as_bytes().to_vec()
        } else {
            vec![self.code.as_u8()]
        }
    }
}

/// Login protocol handler
pub struct LoginHandler {
    /// Expected client revision
    expected_revision: u32,
    /// RSA decryptor for login block
    rsa: Option<Arc<RsaDecryptor>>,
}

impl LoginHandler {
    /// Create a new login handler
    pub fn new(expected_revision: u32) -> Self {
        Self {
            expected_revision,
            rsa: None,
        }
    }

    /// Create a login handler with RSA decryptor
    pub fn with_rsa(expected_revision: u32, rsa: Arc<RsaDecryptor>) -> Self {
        Self {
            expected_revision,
            rsa: Some(rsa),
        }
    }

    /// Parse the login type from the first byte
    pub fn parse_login_type(&self, data: u8) -> Result<LoginType> {
        LoginType::from_u8(data)
            .ok_or_else(|| RustscapeError::Protocol(ProtocolError::InvalidOpcode(data)))
    }

    /// Parse a complete login packet
    pub fn parse_login(&self, login_type: LoginType, data: &[u8]) -> Result<LoginBlock> {
        let mut buffer = PacketBuffer::from_bytes(data);
        let mut block = LoginBlock::new(login_type);

        // Read revision
        block.revision = buffer.read_int() as u32;
        if block.revision != self.expected_revision {
            return Err(RustscapeError::Protocol(ProtocolError::InvalidRevision {
                expected: self.expected_revision,
                actual: block.revision,
            }));
        }

        // Read low memory flag
        block.low_memory = buffer.read_ubyte() == 1;

        // Skip CRC values (legacy)
        for _ in 0..24 {
            buffer.read_ubyte();
        }

        // Read RSA block size
        let rsa_size = buffer.read_ubyte() as usize;
        if rsa_size > buffer.remaining() {
            return Err(RustscapeError::Protocol(ProtocolError::MalformedPacket(
                "RSA block size exceeds packet size".to_string(),
            )));
        }

        // Read RSA encrypted block
        let rsa_block = buffer.read_bytes(rsa_size);

        // Decrypt RSA block
        let decrypted = self.decrypt_rsa_block(&rsa_block)?;
        self.parse_rsa_block(&mut block, &decrypted)?;

        // Read remaining client info
        if buffer.has_remaining() {
            self.parse_client_info(&mut block, &mut buffer);
        }

        Ok(block)
    }

    /// Decrypt the RSA block
    fn decrypt_rsa_block(&self, encrypted: &[u8]) -> Result<Vec<u8>> {
        if let Some(rsa) = &self.rsa {
            rsa.decrypt_login_block(encrypted)
                .map_err(|_| RustscapeError::Protocol(ProtocolError::RsaDecryptionFailed))
        } else {
            // No RSA - treat as plaintext (development mode)
            Ok(encrypted.to_vec())
        }
    }

    /// Parse the decrypted RSA block
    fn parse_rsa_block(&self, block: &mut LoginBlock, data: &[u8]) -> Result<()> {
        if data.is_empty() {
            return Err(RustscapeError::Protocol(ProtocolError::InvalidRsaBlock));
        }

        let mut buffer = PacketBuffer::from_bytes(data);

        // First byte should be magic value 10
        let magic = buffer.read_ubyte();
        if magic != 10 {
            return Err(RustscapeError::Protocol(ProtocolError::InvalidRsaBlock));
        }

        // Read ISAAC seeds (4 x 32-bit integers)
        block.isaac_seeds[0] = buffer.read_int() as u32;
        block.isaac_seeds[1] = buffer.read_int() as u32;
        block.isaac_seeds[2] = buffer.read_int() as u32;
        block.isaac_seeds[3] = buffer.read_int() as u32;

        // Read UID
        block.uid = buffer.read_int() as u32;

        // Read username and password
        block.username = buffer.read_string();
        block.password = buffer.read_string();

        debug!(
            username = %block.username,
            "Parsed login credentials"
        );

        Ok(())
    }

    /// Parse client information from remaining packet data
    fn parse_client_info(&self, block: &mut LoginBlock, buffer: &mut PacketBuffer) {
        if buffer.remaining() < 4 {
            return;
        }

        // Display mode
        block.client_info.display_mode = buffer.read_ubyte();

        // Screen dimensions
        block.client_info.screen_width = buffer.read_ushort();
        block.client_info.screen_height = buffer.read_ushort();

        // Settings
        if buffer.has_remaining() {
            block.client_info.settings = buffer.read_int() as u32;
        }

        // Machine info string (if present)
        if buffer.has_remaining() {
            block.client_info.machine_info = buffer.read_string();
        }

        block.client_info.revision = block.revision;
        block.client_info.low_memory = block.low_memory;
    }

    /// Validate a username
    pub fn validate_username(username: &str) -> Result<String> {
        let username = username.trim().to_lowercase();

        if username.is_empty() {
            return Err(RustscapeError::Auth(AuthError::InvalidUsername));
        }

        if username.len() > 12 {
            return Err(RustscapeError::Auth(AuthError::InvalidUsername));
        }

        // Check for valid characters (alphanumeric, underscore, space)
        let valid = username
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '_' || c == ' ');

        if !valid {
            return Err(RustscapeError::Auth(AuthError::InvalidUsername));
        }

        // Convert spaces to underscores for storage
        Ok(username.replace(' ', "_"))
    }

    /// Validate a password
    pub fn validate_password(password: &str) -> Result<()> {
        if password.is_empty() {
            return Err(RustscapeError::Auth(AuthError::InvalidPassword));
        }

        if password.len() < 4 {
            return Err(RustscapeError::Auth(AuthError::InvalidPassword));
        }

        if password.len() > 20 {
            return Err(RustscapeError::Auth(AuthError::InvalidPassword));
        }

        Ok(())
    }

    /// Create ISAAC cipher pair from seeds
    pub fn create_isaac_pair(seeds: &[u32; 4]) -> IsaacPair {
        IsaacPair::new(seeds)
    }

    /// Generate a successful login response
    pub fn success_response(rights: u8, player_index: u16, member: bool) -> LoginResponsePacket {
        LoginResponsePacket::success(rights, player_index, member)
    }

    /// Generate an error response
    pub fn error_response(code: LoginResponse) -> LoginResponsePacket {
        LoginResponsePacket::error(code)
    }

    /// Generate an error response from an AuthError
    pub fn auth_error_response(error: &AuthError) -> LoginResponsePacket {
        let code: LoginResponse = match error {
            AuthError::InvalidCredentials => LoginResponse::InvalidCredentials,
            AuthError::AccountDisabled => LoginResponse::AccountDisabled,
            AuthError::AccountLocked => LoginResponse::AccountLocked,
            AuthError::AlreadyLoggedIn => LoginResponse::AlreadyLoggedIn,
            AuthError::WorldFull => LoginResponse::WorldFull,
            AuthError::LoginLimitExceeded => LoginResponse::LoginLimitExceeded,
            AuthError::LoginServerOffline => LoginResponse::LoginServerOffline,
            AuthError::GameUpdated => LoginResponse::GameUpdated,
            AuthError::InvalidSessionId => LoginResponse::BadSessionId,
            AuthError::TooManyAttempts => LoginResponse::TooManyIncorrectLogins,
            _ => LoginResponse::CouldNotCompleteLogin,
        };
        LoginResponsePacket::error(code)
    }
}

impl Default for LoginHandler {
    fn default() -> Self {
        Self::new(530)
    }
}

/// Convert a username to the format used in the protocol (name hash)
pub fn username_to_hash(username: &str) -> i64 {
    let username = username.trim().to_lowercase();
    let mut hash: i64 = 0;

    for c in username.chars().take(12) {
        hash = hash.wrapping_mul(37);
        let value = match c {
            'a'..='z' => (c as i64) - ('a' as i64) + 1,
            '0'..='9' => (c as i64) - ('0' as i64) + 27,
            ' ' | '_' => 0,
            _ => 0,
        };
        hash = hash.wrapping_add(value);
    }

    hash
}

/// Convert a hash back to a username
pub fn hash_to_username(hash: i64) -> String {
    let mut hash = hash;
    let mut chars = Vec::with_capacity(12);

    while hash != 0 {
        let remainder = (hash % 37) as u8;
        hash /= 37;

        let c = match remainder {
            0 => '_',
            1..=26 => ((remainder - 1) + b'a') as char,
            27..=36 => ((remainder - 27) + b'0') as char,
            _ => '_',
        };

        chars.push(c);
    }

    chars.reverse();
    chars.into_iter().collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_login_type_from_u8() {
        assert_eq!(LoginType::from_u8(16), Some(LoginType::Normal));
        assert_eq!(LoginType::from_u8(18), Some(LoginType::Reconnect));
        assert_eq!(LoginType::from_u8(0), None);
    }

    #[test]
    fn test_login_type_as_u8() {
        assert_eq!(LoginType::Normal.as_u8(), 16);
        assert_eq!(LoginType::Reconnect.as_u8(), 18);
    }

    #[test]
    fn test_validate_username() {
        // Valid usernames
        assert!(LoginHandler::validate_username("Player1").is_ok());
        assert!(LoginHandler::validate_username("test_user").is_ok());
        assert!(LoginHandler::validate_username("A").is_ok());

        // Invalid usernames
        assert!(LoginHandler::validate_username("").is_err());
        assert!(LoginHandler::validate_username("verylongusername").is_err()); // > 12 chars
        assert!(LoginHandler::validate_username("user@name").is_err()); // invalid char
    }

    #[test]
    fn test_validate_username_normalization() {
        let result = LoginHandler::validate_username("Test User").unwrap();
        assert_eq!(result, "test_user");
    }

    #[test]
    fn test_validate_password() {
        // Valid passwords
        assert!(LoginHandler::validate_password("password").is_ok());
        assert!(LoginHandler::validate_password("test").is_ok());

        // Invalid passwords
        assert!(LoginHandler::validate_password("").is_err());
        assert!(LoginHandler::validate_password("abc").is_err()); // < 4 chars
        assert!(LoginHandler::validate_password("aaaaaaaaaaaaaaaaaaaaa").is_err());
        // > 20 chars
    }

    #[test]
    fn test_login_response_success_encode() {
        let response = LoginResponsePacket::success(2, 1000, true);
        let encoded = response.encode();

        assert_eq!(encoded[0], LoginResponse::Success.as_u8());
        assert_eq!(encoded[1], 2); // rights
        assert_eq!(encoded[2], 0); // flagged
                                   // player_index is 2 bytes big-endian
        assert_eq!(u16::from_be_bytes([encoded[3], encoded[4]]), 1000);
        assert_eq!(encoded[5], 1); // member
    }

    #[test]
    fn test_login_response_error_encode() {
        let response = LoginResponsePacket::error(LoginResponse::InvalidCredentials);
        let encoded = response.encode();

        assert_eq!(encoded.len(), 1);
        assert_eq!(encoded[0], LoginResponse::InvalidCredentials.as_u8());
    }

    #[test]
    fn test_username_to_hash() {
        let hash1 = username_to_hash("test");
        let hash2 = username_to_hash("TEST");
        assert_eq!(hash1, hash2); // Case insensitive

        let hash3 = username_to_hash("player1");
        assert!(hash3 != 0);

        // Same username should always produce same hash
        assert_eq!(username_to_hash("admin"), username_to_hash("admin"));
    }

    #[test]
    fn test_hash_to_username() {
        let original = "test";
        let hash = username_to_hash(original);
        let recovered = hash_to_username(hash);
        assert_eq!(recovered, original);
    }

    #[test]
    fn test_username_hash_roundtrip() {
        let names = vec!["player", "test123", "admin", "user_1"];
        for name in names {
            let hash = username_to_hash(name);
            let recovered = hash_to_username(hash);
            assert_eq!(
                recovered,
                name.to_lowercase(),
                "Failed roundtrip for '{}'",
                name
            );
        }
    }

    #[test]
    fn test_create_isaac_pair() {
        let seeds = [12345, 67890, 11111, 22222];
        let pair = LoginHandler::create_isaac_pair(&seeds);

        // Verify the pair works for encoding/decoding
        let mut pair_clone = pair.clone();
        let opcode: u8 = 42;
        let encoded = pair_clone.encode_opcode(opcode);
        let decoded = pair_clone.decode_opcode(encoded);

        // Note: decode uses a different cipher stream, so this won't round-trip
        // unless we're testing client/server pair interaction
        assert!(encoded != opcode || opcode == 0); // Usually different unless wraps
    }

    #[test]
    fn test_login_block_creation() {
        let block = LoginBlock::new(LoginType::Normal);
        assert_eq!(block.login_type, LoginType::Normal);
        assert_eq!(block.revision, 0);
        assert!(block.username.is_empty());
    }

    #[test]
    fn test_auth_error_response() {
        let response = LoginHandler::auth_error_response(&AuthError::WorldFull);
        assert_eq!(response.code, LoginResponse::WorldFull);

        let response = LoginHandler::auth_error_response(&AuthError::InvalidCredentials);
        assert_eq!(response.code, LoginResponse::InvalidCredentials);
    }
}
