//! Session management module
//!
//! Manages client sessions including:
//! - Session lifecycle (creation, tracking, cleanup)
//! - Session state machine (handshake -> login -> game)
//! - Per-session data (ISAAC cipher, player info, etc.)
//! - Thread-safe session registry

use std::collections::HashMap;
use std::net::SocketAddr;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

use dashmap::DashMap;
use parking_lot::RwLock;
use tokio::sync::mpsc;
use tracing::{debug, info, warn};

use crate::crypto::IsaacPair;
use crate::error::{NetworkError, Result, RustscapeError};

/// Unique session identifier
pub type SessionId = u64;

/// Session state in the connection lifecycle
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum SessionState {
    /// Initial state - waiting for handshake
    Connected,
    /// JS5 handshake completed - serving cache files
    Js5,
    /// Login handshake initiated - waiting for credentials
    LoginHandshake,
    /// Login in progress - processing credentials
    LoggingIn,
    /// Fully authenticated and in-game
    InGame,
    /// Session is disconnecting
    Disconnecting,
    /// Session has been disconnected
    Disconnected,
}

impl SessionState {
    /// Check if the session is in a state where it can receive game packets
    pub fn can_receive_game_packets(&self) -> bool {
        matches!(self, SessionState::InGame)
    }

    /// Check if the session is still active (not disconnecting/disconnected)
    pub fn is_active(&self) -> bool {
        !matches!(
            self,
            SessionState::Disconnecting | SessionState::Disconnected
        )
    }

    /// Get a human-readable name for the state
    pub fn name(&self) -> &'static str {
        match self {
            SessionState::Connected => "Connected",
            SessionState::Js5 => "JS5",
            SessionState::LoginHandshake => "LoginHandshake",
            SessionState::LoggingIn => "LoggingIn",
            SessionState::InGame => "InGame",
            SessionState::Disconnecting => "Disconnecting",
            SessionState::Disconnected => "Disconnected",
        }
    }
}

impl std::fmt::Display for SessionState {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.name())
    }
}

/// Client information collected during login
#[derive(Debug, Clone, Default)]
pub struct ClientInfo {
    /// Client revision number
    pub revision: u32,
    /// Client display mode (0=SD, 1=HD, 2=resizable)
    pub display_mode: u8,
    /// Screen width
    pub screen_width: u16,
    /// Screen height
    pub screen_height: u16,
    /// Whether this is a low memory client
    pub low_memory: bool,
    /// Client settings/preferences bitfield
    pub settings: u32,
    /// Machine info string (OS, java version, etc.)
    pub machine_info: String,
}

/// A connected client session
pub struct Session {
    /// Unique session identifier
    pub id: SessionId,
    /// Remote address of the client
    pub address: SocketAddr,
    /// Current session state
    state: RwLock<SessionState>,
    /// Whether this is a WebSocket connection
    pub is_websocket: bool,
    /// ISAAC cipher pair for packet encryption (set after login)
    isaac: RwLock<Option<IsaacPair>>,
    /// Server key for login handshake
    server_key: AtomicU64,
    /// JS5 encryption key
    js5_encryption: RwLock<u8>,
    /// Username (set after login)
    username: RwLock<Option<String>>,
    /// Client information
    client_info: RwLock<Option<ClientInfo>>,
    /// Time of session creation
    pub created_at: Instant,
    /// Time of last activity
    last_activity: RwLock<Instant>,
    /// Associated player index (set when in-game)
    player_index: RwLock<Option<u16>>,
    /// Outbound message channel (for sending packets)
    outbound_tx: Option<mpsc::Sender<Vec<u8>>>,
}

impl Session {
    /// Create a new session
    pub fn new(id: SessionId, address: SocketAddr, is_websocket: bool) -> Self {
        let now = Instant::now();
        Self {
            id,
            address,
            state: RwLock::new(SessionState::Connected),
            is_websocket,
            isaac: RwLock::new(None),
            server_key: AtomicU64::new(0),
            js5_encryption: RwLock::new(0),
            username: RwLock::new(None),
            client_info: RwLock::new(None),
            created_at: now,
            last_activity: RwLock::new(now),
            player_index: RwLock::new(None),
            outbound_tx: None,
        }
    }

    /// Create a new session with an outbound channel
    pub fn with_channel(
        id: SessionId,
        address: SocketAddr,
        is_websocket: bool,
        outbound_tx: mpsc::Sender<Vec<u8>>,
    ) -> Self {
        let mut session = Self::new(id, address, is_websocket);
        session.outbound_tx = Some(outbound_tx);
        session
    }

    /// Get the IP address as a string (without port)
    pub fn ip(&self) -> String {
        self.address.ip().to_string()
    }

    /// Get the current session state
    pub fn state(&self) -> SessionState {
        *self.state.read()
    }

    /// Set the session state
    pub fn set_state(&self, new_state: SessionState) {
        let old_state = {
            let mut state = self.state.write();
            let old = *state;
            *state = new_state;
            old
        };
        debug!(
            session_id = self.id,
            old_state = %old_state,
            new_state = %new_state,
            "Session state changed"
        );
    }

    /// Transition to a new state if currently in the expected state
    pub fn transition_state(&self, expected: SessionState, new_state: SessionState) -> bool {
        let mut state = self.state.write();
        if *state == expected {
            *state = new_state;
            true
        } else {
            false
        }
    }

    /// Check if session is in a specific state
    pub fn is_state(&self, check_state: SessionState) -> bool {
        *self.state.read() == check_state
    }

    /// Check if session is active
    pub fn is_active(&self) -> bool {
        self.state().is_active()
    }

    /// Get the server key
    pub fn server_key(&self) -> u64 {
        self.server_key.load(Ordering::SeqCst)
    }

    /// Set the server key
    pub fn set_server_key(&self, key: u64) {
        self.server_key.store(key, Ordering::SeqCst);
    }

    /// Get the JS5 encryption key
    pub fn js5_encryption(&self) -> u8 {
        *self.js5_encryption.read()
    }

    /// Set the JS5 encryption key
    pub fn set_js5_encryption(&self, key: u8) {
        *self.js5_encryption.write() = key;
    }

    /// Set the ISAAC cipher pair
    pub fn set_isaac(&self, isaac: IsaacPair) {
        *self.isaac.write() = Some(isaac);
    }

    /// Get a mutable reference to the ISAAC cipher pair
    pub fn with_isaac<F, R>(&self, f: F) -> Option<R>
    where
        F: FnOnce(&mut IsaacPair) -> R,
    {
        self.isaac.write().as_mut().map(f)
    }

    /// Encode a packet opcode using ISAAC
    pub fn encode_opcode(&self, opcode: u8) -> u8 {
        self.isaac
            .write()
            .as_mut()
            .map(|isaac| isaac.encode_opcode(opcode))
            .unwrap_or(opcode)
    }

    /// Decode a packet opcode using ISAAC
    pub fn decode_opcode(&self, encoded: u8) -> u8 {
        self.isaac
            .write()
            .as_mut()
            .map(|isaac| isaac.decode_opcode(encoded))
            .unwrap_or(encoded)
    }

    /// Set the username
    pub fn set_username(&self, username: String) {
        *self.username.write() = Some(username);
    }

    /// Get the username
    pub fn username(&self) -> Option<String> {
        self.username.read().clone()
    }

    /// Set client information
    pub fn set_client_info(&self, info: ClientInfo) {
        *self.client_info.write() = Some(info);
    }

    /// Get client information
    pub fn client_info(&self) -> Option<ClientInfo> {
        self.client_info.read().clone()
    }

    /// Set the player index
    pub fn set_player_index(&self, index: u16) {
        *self.player_index.write() = Some(index);
    }

    /// Get the player index
    pub fn player_index(&self) -> Option<u16> {
        *self.player_index.read()
    }

    /// Update the last activity timestamp
    pub fn touch(&self) {
        *self.last_activity.write() = Instant::now();
    }

    /// Get the last activity time
    pub fn last_activity(&self) -> Instant {
        *self.last_activity.read()
    }

    /// Get the duration since last activity
    pub fn idle_duration(&self) -> Duration {
        self.last_activity().elapsed()
    }

    /// Check if the session has been idle too long
    pub fn is_idle(&self, max_idle: Duration) -> bool {
        self.idle_duration() > max_idle
    }

    /// Send data to the client (if channel is available)
    pub async fn send(&self, data: Vec<u8>) -> Result<()> {
        if let Some(tx) = &self.outbound_tx {
            tx.send(data)
                .await
                .map_err(|_| RustscapeError::Network(NetworkError::ConnectionClosed))?;
        }
        Ok(())
    }

    /// Try to send data without blocking
    pub fn try_send(&self, data: Vec<u8>) -> Result<()> {
        if let Some(tx) = &self.outbound_tx {
            tx.try_send(data)
                .map_err(|_| RustscapeError::Network(NetworkError::WriteBufferFull))?;
        }
        Ok(())
    }
}

impl std::fmt::Debug for Session {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Session")
            .field("id", &self.id)
            .field("address", &self.address)
            .field("state", &self.state())
            .field("is_websocket", &self.is_websocket)
            .field("username", &self.username())
            .field("created_at", &self.created_at)
            .field("idle_duration", &self.idle_duration())
            .finish()
    }
}

/// Thread-safe session manager
pub struct SessionManager {
    /// Map of session ID to session
    sessions: DashMap<SessionId, Arc<Session>>,
    /// Map of username to session ID (for logged-in users)
    username_to_session: DashMap<String, SessionId>,
    /// Map of IP address to list of session IDs (for connection limiting)
    ip_to_sessions: DashMap<String, Vec<SessionId>>,
    /// Next session ID to assign
    next_id: AtomicU64,
    /// Maximum sessions per IP
    max_per_ip: usize,
    /// Maximum idle time before disconnect
    max_idle_time: Duration,
}

impl SessionManager {
    /// Create a new session manager
    pub fn new() -> Self {
        Self {
            sessions: DashMap::new(),
            username_to_session: DashMap::new(),
            ip_to_sessions: DashMap::new(),
            next_id: AtomicU64::new(1),
            max_per_ip: 10,
            max_idle_time: Duration::from_secs(300), // 5 minutes
        }
    }

    /// Create a session manager with custom limits
    pub fn with_limits(max_per_ip: usize, max_idle_secs: u64) -> Self {
        Self {
            sessions: DashMap::new(),
            username_to_session: DashMap::new(),
            ip_to_sessions: DashMap::new(),
            next_id: AtomicU64::new(1),
            max_per_ip,
            max_idle_time: Duration::from_secs(max_idle_secs),
        }
    }

    /// Create a new session and register it
    pub fn create_session(&self, address: SocketAddr, is_websocket: bool) -> Result<Arc<Session>> {
        let ip = address.ip().to_string();

        // Check connection limit per IP
        let current_count = self.ip_to_sessions.get(&ip).map(|v| v.len()).unwrap_or(0);

        if current_count >= self.max_per_ip {
            warn!(
                ip = %ip,
                count = current_count,
                max = self.max_per_ip,
                "Connection limit exceeded for IP"
            );
            return Err(RustscapeError::Network(NetworkError::TooManyConnections(
                ip,
            )));
        }

        // Generate session ID
        let id = self.next_id.fetch_add(1, Ordering::SeqCst);

        // Create session
        let session = Arc::new(Session::new(id, address, is_websocket));

        // Register in maps
        self.sessions.insert(id, session.clone());
        self.ip_to_sessions.entry(ip).or_default().push(id);

        info!(
            session_id = id,
            address = %address,
            is_websocket = is_websocket,
            "Session created"
        );

        Ok(session)
    }

    /// Create a session with an outbound channel
    pub fn create_session_with_channel(
        &self,
        address: SocketAddr,
        is_websocket: bool,
        outbound_tx: mpsc::Sender<Vec<u8>>,
    ) -> Result<Arc<Session>> {
        let ip = address.ip().to_string();

        // Check connection limit per IP
        let current_count = self.ip_to_sessions.get(&ip).map(|v| v.len()).unwrap_or(0);

        if current_count >= self.max_per_ip {
            warn!(
                ip = %ip,
                count = current_count,
                max = self.max_per_ip,
                "Connection limit exceeded for IP"
            );
            return Err(RustscapeError::Network(NetworkError::TooManyConnections(
                ip,
            )));
        }

        // Generate session ID
        let id = self.next_id.fetch_add(1, Ordering::SeqCst);

        // Create session
        let session = Arc::new(Session::with_channel(
            id,
            address,
            is_websocket,
            outbound_tx,
        ));

        // Register in maps
        self.sessions.insert(id, session.clone());
        self.ip_to_sessions.entry(ip).or_default().push(id);

        info!(
            session_id = id,
            address = %address,
            is_websocket = is_websocket,
            "Session created with channel"
        );

        Ok(session)
    }

    /// Get a session by ID
    pub fn get(&self, id: SessionId) -> Option<Arc<Session>> {
        self.sessions.get(&id).map(|r| r.clone())
    }

    /// Get a session by username
    pub fn get_by_username(&self, username: &str) -> Option<Arc<Session>> {
        let username_lower = username.to_lowercase();
        self.username_to_session
            .get(&username_lower)
            .and_then(|id| self.get(*id))
    }

    /// Check if a username is currently logged in
    pub fn is_logged_in(&self, username: &str) -> bool {
        let username_lower = username.to_lowercase();
        self.username_to_session.contains_key(&username_lower)
    }

    /// Register a username for a session (called after successful login)
    pub fn register_username(&self, session_id: SessionId, username: &str) {
        let username_lower = username.to_lowercase();
        self.username_to_session
            .insert(username_lower.clone(), session_id);

        if let Some(session) = self.get(session_id) {
            session.set_username(username.to_string());
        }

        debug!(
            session_id = session_id,
            username = %username,
            "Username registered for session"
        );
    }

    /// Remove a session
    pub fn remove(&self, id: SessionId) {
        if let Some((_, session)) = self.sessions.remove(&id) {
            // Remove from username map
            if let Some(username) = session.username() {
                self.username_to_session.remove(&username.to_lowercase());
            }

            // Remove from IP map
            let ip = session.ip();
            if let Some(mut sessions) = self.ip_to_sessions.get_mut(&ip) {
                sessions.retain(|&sid| sid != id);
            }

            // Clean up empty IP entries
            self.ip_to_sessions.retain(|_, v| !v.is_empty());

            info!(
                session_id = id,
                username = ?session.username(),
                "Session removed"
            );
        }
    }

    /// Disconnect a session
    pub async fn disconnect(&self, id: SessionId) {
        if let Some(session) = self.get(id) {
            session.set_state(SessionState::Disconnecting);
            // Session cleanup will happen when the connection handler drops
        }
        self.remove(id);
    }

    /// Disconnect all sessions
    pub async fn disconnect_all(&self) {
        let ids: Vec<SessionId> = self.sessions.iter().map(|r| *r.key()).collect();
        for id in ids {
            self.disconnect(id).await;
        }
    }

    /// Get the count of active sessions
    pub fn count(&self) -> usize {
        self.sessions.len()
    }

    /// Get the count of sessions per state
    pub fn count_by_state(&self) -> HashMap<SessionState, usize> {
        let mut counts = HashMap::new();
        for session in self.sessions.iter() {
            *counts.entry(session.state()).or_insert(0) += 1;
        }
        counts
    }

    /// Get the count of logged-in players
    pub fn player_count(&self) -> usize {
        self.username_to_session.len()
    }

    /// Get list of all session IDs
    pub fn session_ids(&self) -> Vec<SessionId> {
        self.sessions.iter().map(|r| *r.key()).collect()
    }

    /// Cleanup idle sessions
    pub async fn cleanup_idle(&self) {
        let mut to_remove = Vec::new();

        for session in self.sessions.iter() {
            if session.is_idle(self.max_idle_time) && session.is_active() {
                debug!(
                    session_id = session.id,
                    idle_duration = ?session.idle_duration(),
                    "Session idle timeout"
                );
                to_remove.push(session.id);
            }
        }

        for id in to_remove {
            self.disconnect(id).await;
        }
    }

    /// Get sessions from a specific IP
    pub fn sessions_from_ip(&self, ip: &str) -> Vec<Arc<Session>> {
        self.ip_to_sessions
            .get(ip)
            .map(|ids| ids.iter().filter_map(|&id| self.get(id)).collect())
            .unwrap_or_default()
    }

    /// Iterate over all sessions
    pub fn for_each<F>(&self, f: F)
    where
        F: Fn(&Session),
    {
        for session in self.sessions.iter() {
            f(&session);
        }
    }

    /// Iterate over all in-game sessions
    pub fn for_each_in_game<F>(&self, f: F)
    where
        F: Fn(&Session),
    {
        for session in self.sessions.iter() {
            if session.state() == SessionState::InGame {
                f(&session);
            }
        }
    }
}

impl Default for SessionManager {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_address() -> SocketAddr {
        "127.0.0.1:12345".parse().unwrap()
    }

    #[test]
    fn test_session_creation() {
        let session = Session::new(1, test_address(), false);
        assert_eq!(session.id, 1);
        assert_eq!(session.state(), SessionState::Connected);
        assert!(!session.is_websocket);
        assert!(session.is_active());
    }

    #[test]
    fn test_session_state_transition() {
        let session = Session::new(1, test_address(), false);

        assert!(session.transition_state(SessionState::Connected, SessionState::Js5));
        assert_eq!(session.state(), SessionState::Js5);

        // Should fail - not in Connected state anymore
        assert!(!session.transition_state(SessionState::Connected, SessionState::LoginHandshake));
        assert_eq!(session.state(), SessionState::Js5);
    }

    #[test]
    fn test_session_state_active() {
        let session = Session::new(1, test_address(), false);

        assert!(session.is_active());

        session.set_state(SessionState::Disconnecting);
        assert!(!session.is_active());

        session.set_state(SessionState::Disconnected);
        assert!(!session.is_active());
    }

    #[test]
    fn test_session_manager_create() {
        let manager = SessionManager::new();
        let session = manager.create_session(test_address(), false).unwrap();

        assert_eq!(session.id, 1);
        assert_eq!(manager.count(), 1);
    }

    #[test]
    fn test_session_manager_get() {
        let manager = SessionManager::new();
        let session = manager.create_session(test_address(), false).unwrap();
        let id = session.id;

        let retrieved = manager.get(id).unwrap();
        assert_eq!(retrieved.id, id);
    }

    #[test]
    fn test_session_manager_remove() {
        let manager = SessionManager::new();
        let session = manager.create_session(test_address(), false).unwrap();
        let id = session.id;

        manager.remove(id);

        assert!(manager.get(id).is_none());
        assert_eq!(manager.count(), 0);
    }

    #[test]
    fn test_session_manager_username_lookup() {
        let manager = SessionManager::new();
        let session = manager.create_session(test_address(), false).unwrap();
        let id = session.id;

        manager.register_username(id, "TestUser");

        let found = manager.get_by_username("testuser").unwrap();
        assert_eq!(found.id, id);

        // Case insensitive
        let found = manager.get_by_username("TESTUSER").unwrap();
        assert_eq!(found.id, id);

        assert!(manager.is_logged_in("testuser"));
    }

    #[test]
    fn test_session_manager_ip_limit() {
        let manager = SessionManager::with_limits(2, 300);
        let addr1: SocketAddr = "192.168.1.1:12345".parse().unwrap();
        let addr2: SocketAddr = "192.168.1.1:12346".parse().unwrap();
        let addr3: SocketAddr = "192.168.1.1:12347".parse().unwrap();

        assert!(manager.create_session(addr1, false).is_ok());
        assert!(manager.create_session(addr2, false).is_ok());
        assert!(manager.create_session(addr3, false).is_err()); // Should fail - limit exceeded
    }

    #[test]
    fn test_session_touch() {
        let session = Session::new(1, test_address(), false);
        let initial = session.last_activity();

        std::thread::sleep(std::time::Duration::from_millis(10));
        session.touch();

        assert!(session.last_activity() > initial);
    }

    #[test]
    fn test_session_server_key() {
        let session = Session::new(1, test_address(), false);

        session.set_server_key(12345678);
        assert_eq!(session.server_key(), 12345678);
    }

    #[test]
    fn test_session_client_info() {
        let session = Session::new(1, test_address(), false);

        let info = ClientInfo {
            revision: 530,
            display_mode: 1,
            screen_width: 1920,
            screen_height: 1080,
            low_memory: false,
            settings: 0,
            machine_info: "Windows 10".to_string(),
        };

        session.set_client_info(info.clone());

        let retrieved = session.client_info().unwrap();
        assert_eq!(retrieved.revision, 530);
        assert_eq!(retrieved.screen_width, 1920);
    }

    #[test]
    fn test_session_count_by_state() {
        let manager = SessionManager::new();

        let s1 = manager
            .create_session("127.0.0.1:1".parse().unwrap(), false)
            .unwrap();
        let s2 = manager
            .create_session("127.0.0.1:2".parse().unwrap(), false)
            .unwrap();
        let s3 = manager
            .create_session("127.0.0.1:3".parse().unwrap(), false)
            .unwrap();

        s1.set_state(SessionState::InGame);
        s2.set_state(SessionState::InGame);
        // s3 stays Connected

        let counts = manager.count_by_state();
        assert_eq!(counts.get(&SessionState::InGame), Some(&2));
        assert_eq!(counts.get(&SessionState::Connected), Some(&1));
    }
}
