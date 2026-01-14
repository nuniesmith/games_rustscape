//! Connection handler module
//!
//! Handles the lifecycle of client connections including:
//! - Initial connection setup (TCP or WebSocket)
//! - Protocol handshake (JS5 or Login)
//! - Message routing based on connection state
//! - RSA decryption and ISAAC cipher initialization
//! - Game packet processing with ISAAC decryption
//! - Graceful disconnection

use std::net::SocketAddr;
use std::sync::Arc;

use crate::error::Result;
use tokio::net::TcpStream;
use tokio_tungstenite::accept_async;
use tracing::{debug, info, trace, warn};

use crate::crypto::IsaacPair;
use crate::error::{
    AuthError, Js5Response, LoginResponse, NetworkError, ProtocolError, RustscapeError,
};
use crate::net::buffer::PacketBuffer;
use crate::net::session::{ClientInfo, SessionState};
use crate::net::transport::{BufferedTransport, UnifiedTransport};
use crate::protocol::game::{GamePacketHandler, IncomingGamePacket, INCOMING_PACKET_SIZES};
use crate::protocol::handshake::HandshakeOpcode;
use crate::protocol::login::LoginType;
use crate::protocol::login_init;
use crate::AppState;
use crate::REVISION;

/// Maximum packet size (64KB)
const MAX_PACKET_SIZE: usize = 65535;

/// Read timeout in seconds
const READ_TIMEOUT_SECS: u64 = 30;

/// Connection handler for processing client connections
pub struct ConnectionHandler {
    /// Shared application state
    state: Arc<AppState>,
    /// Whether this handler expects WebSocket connections
    is_websocket: bool,
}

impl ConnectionHandler {
    /// Create a new connection handler
    pub fn new(state: Arc<AppState>, is_websocket: bool) -> Self {
        Self {
            state,
            is_websocket,
        }
    }

    /// Handle a TCP connection (native client)
    pub async fn handle_tcp(&self, stream: TcpStream, addr: SocketAddr) -> Result<()> {
        debug!(address = %addr, "Handling TCP connection");

        // Set TCP options
        stream.set_nodelay(true)?;

        // Create transport
        let transport = UnifiedTransport::tcp(stream);
        let transport = BufferedTransport::new(transport);

        // Create session
        let session = self.state.session_manager.create_session(addr, false)?;

        // Handle the connection
        self.handle_connection(transport, session.id).await
    }

    /// Handle a WebSocket connection (browser client)
    pub async fn handle_websocket(&self, stream: TcpStream, addr: SocketAddr) -> Result<()> {
        debug!(address = %addr, "Handling WebSocket connection");

        // Set TCP options before upgrade
        stream.set_nodelay(true)?;

        // Perform WebSocket handshake
        let ws_stream = accept_async(stream)
            .await
            .map_err(|e| RustscapeError::Network(NetworkError::WebSocket(e.to_string())))?;

        info!(address = %addr, "WebSocket connection established");

        // Create transport
        let transport = UnifiedTransport::websocket(ws_stream);
        let transport = BufferedTransport::new(transport);

        // Create session
        let session = self.state.session_manager.create_session(addr, true)?;

        // Handle the connection
        self.handle_connection(transport, session.id).await
    }

    /// Main connection handling loop
    async fn handle_connection(
        &self,
        mut transport: BufferedTransport,
        session_id: u64,
    ) -> Result<()> {
        let session =
            self.state.session_manager.get(session_id).ok_or_else(|| {
                RustscapeError::Network(NetworkError::SessionNotFound(session_id))
            })?;

        debug!(
            session_id = session_id,
            address = %session.address,
            "Starting connection handler"
        );

        // Main processing loop
        let result = self.process_connection(&mut transport, session_id).await;

        // Cleanup
        debug!(session_id = session_id, "Connection handler ending");

        // Release player index if the session had one
        if let Some(session) = self.state.session_manager.get(session_id) {
            if let Some(player_index) = session.player_index() {
                self.state.auth.release_player_index(player_index);
                debug!(
                    session_id = session_id,
                    player_index = player_index,
                    "Released player index"
                );
            }
        }

        self.state.session_manager.remove(session_id);

        // Attempt graceful shutdown
        if let Err(e) = transport.shutdown().await {
            trace!(session_id = session_id, error = %e, "Error during transport shutdown");
        }

        result
    }

    /// Process messages based on connection state
    async fn process_connection(
        &self,
        transport: &mut BufferedTransport,
        session_id: u64,
    ) -> Result<()> {
        loop {
            let session = match self.state.session_manager.get(session_id) {
                Some(s) => s,
                None => {
                    debug!(session_id = session_id, "Session no longer exists");
                    break;
                }
            };

            // Update activity timestamp
            session.touch();

            // Check if session is still active
            if !session.is_active() {
                debug!(session_id = session_id, "Session no longer active");
                break;
            }

            // Process based on current state
            let state = session.state();
            let result = match state {
                SessionState::Connected => self.handle_handshake(transport, session_id).await,
                SessionState::Js5 => self.handle_js5(transport, session_id).await,
                SessionState::LoginHandshake => {
                    self.handle_login_handshake(transport, session_id).await
                }
                SessionState::LoggingIn => self.handle_login(transport, session_id).await,
                SessionState::InGame => self.handle_game(transport, session_id).await,
                SessionState::Disconnecting | SessionState::Disconnected => {
                    debug!(session_id = session_id, state = %state, "Session disconnecting");
                    break;
                }
            };

            // Handle errors
            if let Err(e) = result {
                match &e {
                    RustscapeError::Network(NetworkError::ConnectionClosed) => {
                        debug!(session_id = session_id, "Connection closed");
                        break;
                    }
                    RustscapeError::Protocol(ProtocolError::InvalidRevision {
                        expected,
                        actual,
                    }) => {
                        warn!(
                            session_id = session_id,
                            expected = expected,
                            actual = actual,
                            "Client revision mismatch"
                        );
                        // Send appropriate error response based on state
                        let _ = self.send_revision_mismatch(transport, state).await;
                        break;
                    }
                    _ => {
                        warn!(session_id = session_id, error = %e, "Connection error");
                        break;
                    }
                }
            }
        }

        Ok(())
    }

    /// Handle initial handshake (determines if JS5 or Login)
    async fn handle_handshake(
        &self,
        transport: &mut BufferedTransport,
        session_id: u64,
    ) -> Result<()> {
        // Read opcode
        let opcode = transport.read_byte().await?;
        trace!(
            session_id = session_id,
            opcode = opcode,
            "Received handshake opcode"
        );

        match HandshakeOpcode::from_u8(opcode) {
            Some(HandshakeOpcode::Js5) => self.handle_js5_handshake(transport, session_id).await,
            Some(HandshakeOpcode::Login) => self.handle_login_init(transport, session_id).await,
            Some(HandshakeOpcode::WorldList) => self.handle_world_list(transport, session_id).await,
            _ => {
                warn!(
                    session_id = session_id,
                    opcode = opcode,
                    "Unknown handshake opcode"
                );
                Err(RustscapeError::Protocol(ProtocolError::InvalidOpcode(
                    opcode,
                )))
            }
        }
    }

    /// Handle JS5 handshake
    async fn handle_js5_handshake(
        &self,
        transport: &mut BufferedTransport,
        session_id: u64,
    ) -> Result<()> {
        let session =
            self.state.session_manager.get(session_id).ok_or_else(|| {
                RustscapeError::Network(NetworkError::SessionNotFound(session_id))
            })?;

        // Read revision (4 bytes, big-endian)
        let data = transport.read_exact(4).await?;
        let revision = u32::from_be_bytes([data[0], data[1], data[2], data[3]]);

        debug!(
            session_id = session_id,
            revision = revision,
            "JS5 handshake"
        );

        // Validate revision
        if revision != REVISION {
            warn!(
                session_id = session_id,
                expected = REVISION,
                actual = revision,
                "JS5 revision mismatch"
            );
            // Send error response
            transport.write(&[Js5Response::OutOfDate.as_u8()]).await?;
            transport.flush().await?;
            return Err(RustscapeError::Protocol(ProtocolError::InvalidRevision {
                expected: REVISION,
                actual: revision,
            }));
        }

        // Send success response
        transport.write(&[Js5Response::Ok.as_u8()]).await?;
        transport.flush().await?;

        // Transition to JS5 state
        session.set_state(SessionState::Js5);

        info!(session_id = session_id, "JS5 handshake successful");
        Ok(())
    }

    /// Handle login initialization (opcode 14)
    async fn handle_login_init(
        &self,
        transport: &mut BufferedTransport,
        session_id: u64,
    ) -> Result<()> {
        let session =
            self.state.session_manager.get(session_id).ok_or_else(|| {
                RustscapeError::Network(NetworkError::SessionNotFound(session_id))
            })?;

        // Read revision (4 bytes, big-endian)
        let data = transport.read_exact(4).await?;
        let revision = u32::from_be_bytes([data[0], data[1], data[2], data[3]]);

        debug!(session_id = session_id, revision = revision, "Login init");

        // Validate revision
        if revision != REVISION {
            warn!(
                session_id = session_id,
                expected = REVISION,
                actual = revision,
                "Login revision mismatch"
            );
            // Send error response
            transport
                .write(&[LoginResponse::GameUpdated.as_u8()])
                .await?;
            transport.flush().await?;
            return Err(RustscapeError::Protocol(ProtocolError::InvalidRevision {
                expected: REVISION,
                actual: revision,
            }));
        }

        // Generate server key
        let server_key: u64 = rand::random();
        session.set_server_key(server_key);

        // Send response: status byte (0 = success) + server key (8 bytes)
        let mut response = PacketBuffer::with_capacity(9);
        response.write_ubyte(LoginResponse::ExchangeKeys.as_u8());
        response.write_ulong(server_key);

        transport.write(response.as_bytes()).await?;
        transport.flush().await?;

        // Transition to login handshake state
        session.set_state(SessionState::LoginHandshake);

        info!(
            session_id = session_id,
            "Login init successful, awaiting credentials"
        );
        Ok(())
    }

    /// Handle world list request (opcode 255)
    async fn handle_world_list(
        &self,
        transport: &mut BufferedTransport,
        session_id: u64,
    ) -> Result<()> {
        // Read update stamp (4 bytes)
        let data = transport.read_exact(4).await?;
        let update_stamp = i32::from_be_bytes([data[0], data[1], data[2], data[3]]);

        debug!(
            session_id = session_id,
            update_stamp = update_stamp,
            "World list request"
        );

        // Build world list response
        // For now, send a simple response with just our world
        let mut response = PacketBuffer::with_capacity(128);

        // Response format varies by client version
        // This is a simplified version
        response.write_ubyte(1); // Success
        response.write_ubyte(1); // Number of worlds

        // World entry
        response.write_ushort(self.state.config.world_id as u16); // World ID
        response.write_int(0); // Flags
        response.write_string(&self.state.config.server_name); // Name
        response.write_string("localhost"); // Address
        response.write_ushort(self.state.session_manager.player_count() as u16); // Player count

        transport.write(response.as_bytes()).await?;
        transport.flush().await?;

        Ok(())
    }

    /// Handle JS5 file requests
    async fn handle_js5(&self, transport: &mut BufferedTransport, session_id: u64) -> Result<()> {
        let session =
            self.state.session_manager.get(session_id).ok_or_else(|| {
                RustscapeError::Network(NetworkError::SessionNotFound(session_id))
            })?;

        // Read opcode
        let opcode = transport.read_byte().await?;

        match opcode as u8 {
            0 | 1 => {
                // File request: priority (opcode), index (1), archive (2)
                let priority = opcode == 1;
                let data = transport.read_exact(3).await?;
                let index = data[0];
                let archive = u16::from_be_bytes([data[1], data[2]]);

                trace!(
                    session_id = session_id,
                    index = index,
                    archive = archive,
                    priority = priority,
                    "JS5 file request"
                );

                // Get file data from cache and send response
                self.send_js5_file(transport, session_id, index, archive, priority)
                    .await?;
            }
            2 | 3 => {
                // Music/logged in status - just acknowledge
                let _data = transport.read_exact(3).await?;
            }
            4 => {
                // Encryption key
                let data = transport.read_exact(3).await?;
                let key = data[0];
                session.set_js5_encryption(key);

                // Verify remaining bytes are 0
                if data[1] != 0 || data[2] != 0 {
                    warn!(session_id = session_id, "Invalid JS5 encryption request");
                    return Err(RustscapeError::Protocol(ProtocolError::MalformedPacket(
                        "Invalid JS5 encryption request".to_string(),
                    )));
                }
            }
            5 | 9 => {
                // Connection info - read 4 bytes and ignore
                let _data = transport.read_exact(4).await?;
            }
            6 => {
                // Some kind of init - read 3 bytes
                let _data = transport.read_exact(3).await?;
            }
            7 => {
                // Close connection request
                let _data = transport.read_exact(3).await?;
                return Err(RustscapeError::Network(NetworkError::ConnectionClosed));
            }
            _ => {
                // Unknown opcode - try to read 3 bytes to stay in sync
                let _data = transport.read_exact(3).await?;
                warn!(
                    session_id = session_id,
                    opcode = opcode,
                    "Unknown JS5 opcode"
                );
            }
        }

        Ok(())
    }

    /// Send a JS5 file response
    async fn send_js5_file(
        &self,
        transport: &mut BufferedTransport,
        session_id: u64,
        index: u8,
        archive: u16,
        priority: bool,
    ) -> Result<()> {
        // Special case: index 255, archive 255 = checksum table
        let data = if index == 255 && archive == 255 {
            self.state.cache.get_checksum_table()?
        } else {
            self.state.cache.get_file(index, archive as u32)?
        };

        // Build response
        let mut response = PacketBuffer::with_capacity(data.len() + 8);

        // Header: index (1), archive (2), compression info (1), size (4)
        response.write_ubyte(index);
        response.write_ushort(archive);

        // For checksum table (255/255), send raw data
        if index == 255 && archive == 255 {
            // Settings byte: compression type (0) | priority flag
            let settings = if priority { 0x80 } else { 0x00 };
            response.write_ubyte(settings);
            response.write_int(data.len() as i32);

            // Write data with block markers every 512 bytes
            let mut offset = 0;
            for (i, &byte) in data.iter().enumerate() {
                if offset == 512 {
                    response.write_ubyte(0xFF); // Block marker
                    offset = 1;
                }
                response.write_ubyte(byte);
                offset += 1;
            }
        } else {
            // Regular file - include compression header from cache
            if data.len() >= 5 {
                let compression = data[0];
                let length = i32::from_be_bytes([data[1], data[2], data[3], data[4]]);

                let settings = compression | if priority { 0x80 } else { 0x00 };
                response.write_ubyte(settings);
                response.write_int(length);

                // Write remaining data with block markers
                let mut offset = 8; // Header takes 8 bytes (3 + 1 + 4)
                for &byte in &data[5..] {
                    if offset == 512 {
                        response.write_ubyte(0xFF);
                        offset = 1;
                    }
                    response.write_ubyte(byte);
                    offset += 1;
                }
            }
        }

        transport.write(response.as_bytes()).await?;
        transport.flush().await?;

        trace!(
            session_id = session_id,
            index = index,
            archive = archive,
            size = response.len(),
            "Sent JS5 file"
        );

        Ok(())
    }

    /// Handle login handshake (waiting for login type)
    async fn handle_login_handshake(
        &self,
        transport: &mut BufferedTransport,
        session_id: u64,
    ) -> Result<()> {
        let session =
            self.state.session_manager.get(session_id).ok_or_else(|| {
                RustscapeError::Network(NetworkError::SessionNotFound(session_id))
            })?;

        // Read login type
        let login_type_byte = transport.read_byte().await? as u8;

        debug!(
            session_id = session_id,
            login_type = login_type_byte,
            "Login type received"
        );

        // Parse and validate login type
        let login_type = LoginType::from_u8(login_type_byte).ok_or_else(|| {
            warn!(
                session_id = session_id,
                login_type = login_type_byte,
                "Unknown login type"
            );
            RustscapeError::Protocol(ProtocolError::InvalidOpcode(login_type_byte))
        })?;

        // Store login type for later use and transition to logging in state
        debug!(
            session_id = session_id,
            login_type = ?login_type,
            "Valid login type, transitioning to LoggingIn"
        );
        session.set_state(SessionState::LoggingIn);

        Ok(())
    }

    /// Handle login credentials - RSA decryption and ISAAC setup
    async fn handle_login(&self, transport: &mut BufferedTransport, session_id: u64) -> Result<()> {
        let session =
            self.state.session_manager.get(session_id).ok_or_else(|| {
                RustscapeError::Network(NetworkError::SessionNotFound(session_id))
            })?;

        // Read login packet size (2 bytes)
        let size_data = transport.read_exact(2).await?;
        let packet_size = u16::from_be_bytes([size_data[0], size_data[1]]) as usize;

        if packet_size > MAX_PACKET_SIZE {
            return Err(RustscapeError::Protocol(ProtocolError::PacketTooLarge {
                size: packet_size,
                max: MAX_PACKET_SIZE,
            }));
        }

        if packet_size < 10 {
            return Err(RustscapeError::Protocol(ProtocolError::MalformedPacket(
                "Login packet too small".to_string(),
            )));
        }

        // Read login packet
        let login_data = transport.read_exact(packet_size).await?;
        let mut buffer = PacketBuffer::from_bytes(&login_data);

        // Parse login block
        let revision = buffer.read_int() as u32;
        if revision != REVISION {
            transport
                .write(&[LoginResponse::GameUpdated.as_u8()])
                .await?;
            transport.flush().await?;
            return Err(RustscapeError::Protocol(ProtocolError::InvalidRevision {
                expected: REVISION,
                actual: revision,
            }));
        }

        // Read low memory flag
        let low_memory = buffer.read_ubyte() == 1;

        // Read RSA block size
        let rsa_size = buffer.read_ushort() as usize;
        if rsa_size == 0 || rsa_size > buffer.remaining() {
            return Err(RustscapeError::Protocol(ProtocolError::MalformedPacket(
                format!(
                    "Invalid RSA block size: {} (remaining: {})",
                    rsa_size,
                    buffer.remaining()
                ),
            )));
        }

        // Read RSA encrypted block
        let rsa_block = buffer.read_bytes(rsa_size);

        // Decrypt RSA block
        let decrypted_block = self.decrypt_rsa_block(&rsa_block)?;

        // Parse the decrypted RSA block
        let (isaac_seeds, uid, password) = self.parse_rsa_block(&decrypted_block)?;

        debug!(
            session_id = session_id,
            uid = uid,
            "RSA block decrypted successfully"
        );

        // Read username from remaining data (after RSA block)
        let username = if buffer.has_remaining() {
            let name = buffer.read_string();
            if name.is_empty() {
                format!("Player{}", session_id)
            } else {
                name
            }
        } else {
            format!("Player{}", session_id)
        };

        // Parse optional client info
        let client_info = self.parse_client_info(&mut buffer, revision, low_memory);

        debug!(
            session_id = session_id,
            username = %username,
            "Processing login credentials"
        );

        // Check if already logged in
        if self.state.session_manager.is_logged_in(&username) {
            transport
                .write(&[LoginResponse::AlreadyLoggedIn.as_u8()])
                .await?;
            transport.flush().await?;
            return Err(RustscapeError::Auth(AuthError::AlreadyLoggedIn));
        }

        // Authenticate with auth service
        let auth_result = match self.state.auth.authenticate(&username, &password) {
            Ok(result) => result,
            Err(e) => {
                let response_code = match e {
                    RustscapeError::Auth(ref auth_err) => LoginResponse::from(auth_err.clone()),
                    _ => LoginResponse::CouldNotCompleteLogin,
                };
                transport.write(&[response_code.as_u8()]).await?;
                transport.flush().await?;
                return Err(e);
            }
        };

        // Set up ISAAC ciphers for packet encryption
        let isaac_pair = IsaacPair::new(&isaac_seeds);
        session.set_isaac(isaac_pair);

        // Register username and store session info
        self.state
            .session_manager
            .register_username(session_id, &username);

        session.set_username(username.clone());
        session.set_player_index(auth_result.player_index);

        if let Some(info) = client_info {
            session.set_client_info(info);
        }

        // Send success response
        let mut response = PacketBuffer::with_capacity(16);
        response.write_ubyte(LoginResponse::Success.as_u8());
        response.write_ubyte(auth_result.account.rights);
        response.write_ubyte(0); // Flagged status
        response.write_ushort(auth_result.player_index);
        response.write_ubyte(if auth_result.account.member { 1 } else { 0 });

        transport.write(response.as_bytes()).await?;
        transport.flush().await?;

        // Transition to in-game state
        session.set_state(SessionState::InGame);

        info!(
            session_id = session_id,
            username = %username,
            player_index = auth_result.player_index,
            rights = auth_result.account.rights,
            "Login successful - ISAAC ciphers initialized"
        );

        // Send login initialization packets (map region, skills, etc.)
        let init_packets = login_init::build_login_init_packets(
            auth_result.player_index,
            auth_result.account.rights,
            auth_result.account.member,
            None, // Don't use ISAAC for init packets yet (client expects unencrypted first batch)
        );

        for packet in init_packets {
            transport.write(&packet).await?;
        }
        transport.flush().await?;

        debug!(session_id = session_id, "Login initialization packets sent");

        Ok(())
    }

    /// Decrypt the RSA block from login packet
    fn decrypt_rsa_block(&self, encrypted: &[u8]) -> Result<Vec<u8>> {
        if let Some(rsa) = &self.state.rsa {
            // Use RSA decryption
            rsa.decrypt_login_block(encrypted).map_err(|e| {
                warn!("RSA decryption failed: {}", e);
                RustscapeError::Protocol(ProtocolError::RsaDecryptionFailed)
            })
        } else {
            // Dev mode - treat as plaintext
            debug!("RSA not configured, treating login block as plaintext (dev mode)");
            Ok(encrypted.to_vec())
        }
    }

    /// Parse the decrypted RSA block to extract ISAAC seeds, UID, and password
    fn parse_rsa_block(&self, data: &[u8]) -> Result<([u32; 4], u32, String)> {
        if data.is_empty() {
            return Err(RustscapeError::Protocol(ProtocolError::InvalidRsaBlock));
        }

        let mut buffer = PacketBuffer::from_bytes(data);

        // First byte should be magic value 10
        let magic = buffer.read_ubyte();
        if magic != 10 {
            warn!("Invalid RSA block magic: expected 10, got {}", magic);
            return Err(RustscapeError::Protocol(ProtocolError::InvalidRsaBlock));
        }

        // Read ISAAC seeds (4 x 32-bit integers)
        if buffer.remaining() < 16 {
            return Err(RustscapeError::Protocol(ProtocolError::MalformedPacket(
                "RSA block too small for ISAAC seeds".to_string(),
            )));
        }

        let isaac_seeds = [
            buffer.read_int() as u32,
            buffer.read_int() as u32,
            buffer.read_int() as u32,
            buffer.read_int() as u32,
        ];

        // Read UID (unique client identifier)
        let uid = if buffer.remaining() >= 4 {
            buffer.read_int() as u32
        } else {
            0
        };

        // Read password
        let password = if buffer.has_remaining() {
            buffer.read_string()
        } else {
            String::new()
        };

        debug!(
            isaac_seeds = ?isaac_seeds,
            uid = uid,
            password_len = password.len(),
            "Parsed RSA block"
        );

        Ok((isaac_seeds, uid, password))
    }

    /// Parse client information from remaining login packet data
    fn parse_client_info(
        &self,
        buffer: &mut PacketBuffer,
        revision: u32,
        low_memory: bool,
    ) -> Option<ClientInfo> {
        if buffer.remaining() < 4 {
            return None;
        }

        let mut info = ClientInfo::default();
        info.revision = revision;
        info.low_memory = low_memory;

        // Display mode
        info.display_mode = buffer.read_ubyte();

        // Screen dimensions (if available)
        if buffer.remaining() >= 4 {
            info.screen_width = buffer.read_ushort();
            info.screen_height = buffer.read_ushort();
        }

        // Settings
        if buffer.remaining() >= 4 {
            info.settings = buffer.read_int() as u32;
        }

        // Machine info string (if present)
        if buffer.has_remaining() {
            info.machine_info = buffer.read_string();
        }

        Some(info)
    }

    /// Handle in-game packets
    async fn handle_game(&self, transport: &mut BufferedTransport, session_id: u64) -> Result<()> {
        let session =
            self.state.session_manager.get(session_id).ok_or_else(|| {
                RustscapeError::Network(NetworkError::SessionNotFound(session_id))
            })?;

        // Read packet opcode (encrypted with ISAAC)
        let encoded_opcode = transport.read_byte().await?;
        let opcode = session.decode_opcode(encoded_opcode as u8);

        trace!(
            session_id = session_id,
            opcode = opcode,
            encoded = encoded_opcode,
            "Received game packet"
        );

        // Look up packet size from protocol definitions
        let packet_size = INCOMING_PACKET_SIZES[opcode as usize];

        // Read packet data based on size type
        let data = match packet_size {
            // Fixed size packet
            size if size > 0 => transport.read_exact(size as usize).await?,
            // Variable byte (1-byte length prefix)
            0 => {
                vec![] // Zero-length packet
            }
            -1 => {
                let len = transport.read_byte().await? as usize;
                if len > 0 {
                    transport.read_exact(len).await?
                } else {
                    vec![]
                }
            }
            // Variable short (2-byte length prefix)
            -2 => {
                let len = {
                    let len_bytes = transport.read_exact(2).await?;
                    u16::from_be_bytes([len_bytes[0], len_bytes[1]]) as usize
                };
                if len > 0 {
                    transport.read_exact(len).await?
                } else {
                    vec![]
                }
            }
            // Unknown packet - try to skip gracefully
            _ => {
                trace!(
                    session_id = session_id,
                    opcode = opcode,
                    "Unknown packet opcode, cannot determine size"
                );
                return Ok(());
            }
        };

        // Create incoming packet and process with game handler
        let packet = IncomingGamePacket::new(opcode, data);
        let handler = GamePacketHandler::new();

        match handler.process(&packet) {
            Ok(Some(responses)) => {
                // Send any response packets
                for response in responses {
                    let encoded = session
                        .with_isaac(|isaac| response.encode(isaac))
                        .unwrap_or_else(|| response.encode_raw());
                    transport.write(&encoded).await?;
                }
                transport.flush().await?;
            }
            Ok(None) => {
                // No response needed
            }
            Err(e) => {
                warn!(
                    session_id = session_id,
                    opcode = opcode,
                    error = %e,
                    "Error processing game packet"
                );
            }
        }

        Ok(())
    }

    /// Send revision mismatch error based on current state
    async fn send_revision_mismatch(
        &self,
        transport: &mut BufferedTransport,
        state: SessionState,
    ) -> Result<()> {
        let response = match state {
            SessionState::Connected | SessionState::Js5 => Js5Response::OutOfDate.as_u8(),
            _ => LoginResponse::GameUpdated.as_u8(),
        };

        transport.write(&[response]).await?;
        transport.flush().await?;
        Ok(())
    }
}
