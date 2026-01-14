//! Error handling module
//!
//! Defines custom error types for the Rustscape server.

use std::io;

use thiserror::Error;

/// Main error type for the Rustscape server
#[derive(Error, Debug)]
pub enum RustscapeError {
    /// Network-related errors
    #[error("Network error: {0}")]
    Network(#[from] NetworkError),

    /// Protocol-related errors
    #[error("Protocol error: {0}")]
    Protocol(#[from] ProtocolError),

    /// Cache-related errors
    #[error("Cache error: {0}")]
    Cache(#[from] CacheError),

    /// Authentication errors
    #[error("Authentication error: {0}")]
    Auth(#[from] AuthError),

    /// Game logic errors
    #[error("Game error: {0}")]
    Game(#[from] GameError),

    /// Database errors
    #[error("Database error: {0}")]
    Database(#[from] sqlx::Error),

    /// I/O errors
    #[error("I/O error: {0}")]
    Io(#[from] io::Error),

    /// Configuration errors
    #[error("Configuration error: {0}")]
    Config(String),

    /// Generic internal error
    #[error("Internal error: {0}")]
    Internal(String),
}

/// Network-specific errors
#[derive(Error, Debug)]
pub enum NetworkError {
    #[error("Connection closed")]
    ConnectionClosed,

    #[error("Connection timeout")]
    Timeout,

    #[error("Connection refused")]
    Refused,

    #[error("Invalid address: {0}")]
    InvalidAddress(String),

    #[error("WebSocket error: {0}")]
    WebSocket(String),

    #[error("Too many connections from {0}")]
    TooManyConnections(String),

    #[error("Session not found: {0}")]
    SessionNotFound(u64),

    #[error("Write buffer full")]
    WriteBufferFull,

    #[error("Read error: {0}")]
    ReadError(String),

    #[error("Write error: {0}")]
    WriteError(String),
}

/// Protocol-specific errors
#[derive(Error, Debug)]
pub enum ProtocolError {
    #[error("Invalid opcode: {0}")]
    InvalidOpcode(u8),

    #[error("Invalid packet size: expected {expected}, got {actual}")]
    InvalidPacketSize { expected: usize, actual: usize },

    #[error("Invalid revision: expected {expected}, got {actual}")]
    InvalidRevision { expected: u32, actual: u32 },

    #[error("Malformed packet: {0}")]
    MalformedPacket(String),

    #[error("Invalid handshake")]
    InvalidHandshake,

    #[error("Unexpected packet in state {state}: opcode {opcode}")]
    UnexpectedPacket { state: String, opcode: u8 },

    #[error("Packet too large: {size} bytes (max: {max})")]
    PacketTooLarge { size: usize, max: usize },

    #[error("Invalid string encoding")]
    InvalidStringEncoding,

    #[error("ISAAC cipher error: {0}")]
    IsaacError(String),

    #[error("RSA decryption failed")]
    RsaDecryptionFailed,

    #[error("Invalid RSA block")]
    InvalidRsaBlock,
}

/// Cache-specific errors
#[derive(Error, Debug)]
pub enum CacheError {
    #[error("Cache not found at: {0}")]
    NotFound(String),

    #[error("Invalid cache format")]
    InvalidFormat,

    #[error("Index {index} not found")]
    IndexNotFound { index: u8 },

    #[error("Archive {archive} not found in index {index}")]
    ArchiveNotFound { index: u8, archive: u16 },

    #[error("Decompression failed: {0}")]
    DecompressionFailed(String),

    #[error("Invalid checksum: expected {expected}, got {actual}")]
    InvalidChecksum { expected: u32, actual: u32 },

    #[error("Cache file corrupted: {0}")]
    Corrupted(String),

    #[error("Cache version mismatch")]
    VersionMismatch,
}

/// Authentication-specific errors
#[derive(Error, Debug, Clone)]
pub enum AuthError {
    #[error("Invalid credentials")]
    InvalidCredentials,

    #[error("Account disabled")]
    AccountDisabled,

    #[error("Account locked")]
    AccountLocked,

    #[error("Already logged in")]
    AlreadyLoggedIn,

    #[error("Session expired")]
    SessionExpired,

    #[error("Invalid session ID")]
    InvalidSessionId,

    #[error("World full")]
    WorldFull,

    #[error("Login limit exceeded")]
    LoginLimitExceeded,

    #[error("Login server offline")]
    LoginServerOffline,

    #[error("Game updated, please refresh client")]
    GameUpdated,

    #[error("Too many login attempts")]
    TooManyAttempts,

    #[error("IP banned")]
    IpBanned,

    #[error("Invalid username format")]
    InvalidUsername,

    #[error("Invalid password format")]
    InvalidPassword,

    #[error("Registration failed: {0}")]
    RegistrationFailed(String),
}

/// Game logic errors
#[derive(Error, Debug)]
pub enum GameError {
    #[error("Player not found: {0}")]
    PlayerNotFound(String),

    #[error("Invalid player state: {0}")]
    InvalidPlayerState(String),

    #[error("Invalid action: {0}")]
    InvalidAction(String),

    #[error("Invalid item: {0}")]
    InvalidItem(i32),

    #[error("Invalid NPC: {0}")]
    InvalidNpc(i32),

    #[error("Invalid object: {0}")]
    InvalidObject(i32),

    #[error("Invalid location: ({x}, {y}, {z})")]
    InvalidLocation { x: i32, y: i32, z: i32 },

    #[error("Inventory full")]
    InventoryFull,

    #[error("Not enough {0}")]
    InsufficientResources(String),

    #[error("Level requirement not met: need level {required} in {skill}")]
    LevelRequirement { skill: String, required: u8 },

    #[error("Quest requirement not met: {0}")]
    QuestRequirement(String),

    #[error("Item requirement not met: need {0}")]
    ItemRequirement(String),

    #[error("Out of range")]
    OutOfRange,

    #[error("World not ready")]
    WorldNotReady,

    #[error("Region not loaded: ({x}, {y})")]
    RegionNotLoaded { x: i32, y: i32 },
}

/// Result type alias for Rustscape operations
pub type Result<T> = std::result::Result<T, RustscapeError>;

/// Response codes for login protocol
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum LoginResponse {
    /// Exchange keys and continue
    ExchangeKeys = 0,
    /// Delay login (wait 2 seconds)
    Delay = 1,
    /// Successful login
    Success = 2,
    /// Invalid username or password
    InvalidCredentials = 3,
    /// Account is disabled
    AccountDisabled = 4,
    /// Account is already logged in
    AlreadyLoggedIn = 5,
    /// Game has been updated
    GameUpdated = 6,
    /// World is full
    WorldFull = 7,
    /// Login server offline
    LoginServerOffline = 8,
    /// Login limit exceeded
    LoginLimitExceeded = 9,
    /// Bad session ID
    BadSessionId = 10,
    /// Login server rejected session
    LoginServerRejected = 11,
    /// Need members account
    MembersAccount = 12,
    /// Could not complete login
    CouldNotCompleteLogin = 13,
    /// Server being updated
    ServerUpdating = 14,
    /// Too many incorrect logins
    TooManyIncorrectLogins = 16,
    /// Standing in members area
    StandingInMembersArea = 17,
    /// Account locked
    AccountLocked = 18,
    /// Closed beta
    ClosedBeta = 19,
    /// Invalid login server
    InvalidLoginServer = 20,
    /// Profile transfer
    ProfileTransfer = 21,
}

impl LoginResponse {
    pub fn as_u8(self) -> u8 {
        self as u8
    }
}

impl From<AuthError> for LoginResponse {
    fn from(err: AuthError) -> Self {
        match err {
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
            AuthError::IpBanned => LoginResponse::AccountLocked,
            _ => LoginResponse::CouldNotCompleteLogin,
        }
    }
}

/// JS5 response codes
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum Js5Response {
    /// Success
    Ok = 0,
    /// Revision mismatch - client needs to update
    OutOfDate = 6,
    /// Server is busy
    ServerBusy = 7,
    /// IP limit exceeded
    IpLimit = 9,
}

impl Js5Response {
    pub fn as_u8(self) -> u8 {
        self as u8
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_login_response_conversion() {
        assert_eq!(LoginResponse::Success.as_u8(), 2);
        assert_eq!(LoginResponse::InvalidCredentials.as_u8(), 3);
        assert_eq!(LoginResponse::WorldFull.as_u8(), 7);
    }

    #[test]
    fn test_auth_error_to_login_response() {
        let response: LoginResponse = AuthError::InvalidCredentials.into();
        assert_eq!(response, LoginResponse::InvalidCredentials);

        let response: LoginResponse = AuthError::WorldFull.into();
        assert_eq!(response, LoginResponse::WorldFull);
    }

    #[test]
    fn test_js5_response() {
        assert_eq!(Js5Response::Ok.as_u8(), 0);
        assert_eq!(Js5Response::OutOfDate.as_u8(), 6);
    }

    #[test]
    fn test_error_display() {
        let err = NetworkError::ConnectionClosed;
        assert_eq!(err.to_string(), "Connection closed");

        let err = ProtocolError::InvalidOpcode(42);
        assert_eq!(err.to_string(), "Invalid opcode: 42");

        let err = CacheError::ArchiveNotFound {
            index: 5,
            archive: 100,
        };
        assert_eq!(err.to_string(), "Archive 100 not found in index 5");
    }
}
