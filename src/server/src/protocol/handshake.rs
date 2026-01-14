//! Handshake protocol handler
//!
//! Handles the initial connection handshake between client and server.
//! The handshake determines what type of connection the client wants:
//! - JS5 (cache file serving)
//! - Login (game authentication)
//! - World list request

use crate::error::{ProtocolError, Result, RustscapeError};
use crate::net::buffer::PacketBuffer;

/// Handshake opcodes
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum HandshakeOpcode {
    /// Login handshake (opcode 14)
    Login = 14,
    /// JS5/cache handshake (opcode 15)
    Js5 = 15,
    /// Account creation (opcode 147)
    AccountCreate = 147,
    /// Account recovery (opcode 186)
    AccountRecover = 186,
    /// World list request (opcode 255)
    WorldList = 255,
}

impl HandshakeOpcode {
    /// Convert a u8 to a HandshakeOpcode
    pub fn from_u8(value: u8) -> Option<Self> {
        match value {
            14 => Some(Self::Login),
            15 => Some(Self::Js5),
            147 => Some(Self::AccountCreate),
            186 => Some(Self::AccountRecover),
            255 => Some(Self::WorldList),
            _ => None,
        }
    }

    /// Get the opcode value
    pub fn as_u8(self) -> u8 {
        self as u8
    }

    /// Get the name of this opcode
    pub fn name(&self) -> &'static str {
        match self {
            Self::Login => "Login",
            Self::Js5 => "JS5",
            Self::AccountCreate => "AccountCreate",
            Self::AccountRecover => "AccountRecover",
            Self::WorldList => "WorldList",
        }
    }
}

impl std::fmt::Display for HandshakeOpcode {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}({})", self.name(), self.as_u8())
    }
}

/// Handshake request data
#[derive(Debug, Clone)]
pub enum HandshakeRequest {
    /// JS5 handshake with client revision
    Js5 { revision: u32 },
    /// Login handshake with client revision
    Login { revision: u32 },
    /// World list request with update stamp
    WorldList { update_stamp: i32 },
    /// Account creation request
    AccountCreate { data: Vec<u8> },
    /// Account recovery request
    AccountRecover { data: Vec<u8> },
}

impl HandshakeRequest {
    /// Get the opcode for this request type
    pub fn opcode(&self) -> HandshakeOpcode {
        match self {
            Self::Js5 { .. } => HandshakeOpcode::Js5,
            Self::Login { .. } => HandshakeOpcode::Login,
            Self::WorldList { .. } => HandshakeOpcode::WorldList,
            Self::AccountCreate { .. } => HandshakeOpcode::AccountCreate,
            Self::AccountRecover { .. } => HandshakeOpcode::AccountRecover,
        }
    }
}

/// Handshake response data
#[derive(Debug, Clone)]
pub enum HandshakeResponse {
    /// JS5 success response
    Js5Success,
    /// JS5 error response
    Js5Error { code: u8 },
    /// Login success response with server key
    LoginSuccess { server_key: u64 },
    /// Login error response
    LoginError { code: u8 },
    /// World list response
    WorldList { data: Vec<u8> },
}

impl HandshakeResponse {
    /// Encode the response to bytes
    pub fn encode(&self) -> Vec<u8> {
        match self {
            Self::Js5Success => vec![0],
            Self::Js5Error { code } => vec![*code],
            Self::LoginSuccess { server_key } => {
                let mut buf = PacketBuffer::with_capacity(9);
                buf.write_ubyte(0); // Success code
                buf.write_ulong(*server_key);
                buf.as_bytes().to_vec()
            }
            Self::LoginError { code } => vec![*code],
            Self::WorldList { data } => data.clone(),
        }
    }
}

/// Handshake protocol handler
pub struct HandshakeHandler {
    /// Expected client revision
    expected_revision: u32,
}

impl HandshakeHandler {
    /// Create a new handshake handler
    pub fn new(expected_revision: u32) -> Self {
        Self { expected_revision }
    }

    /// Parse a handshake request from a buffer
    pub fn parse_request(&self, opcode: u8, data: &[u8]) -> Result<HandshakeRequest> {
        let hs_opcode = HandshakeOpcode::from_u8(opcode)
            .ok_or_else(|| RustscapeError::Protocol(ProtocolError::InvalidOpcode(opcode)))?;

        match hs_opcode {
            HandshakeOpcode::Js5 => {
                if data.len() < 4 {
                    return Err(RustscapeError::Protocol(ProtocolError::InvalidPacketSize {
                        expected: 4,
                        actual: data.len(),
                    }));
                }
                let revision = u32::from_be_bytes([data[0], data[1], data[2], data[3]]);
                Ok(HandshakeRequest::Js5 { revision })
            }
            HandshakeOpcode::Login => {
                if data.len() < 4 {
                    return Err(RustscapeError::Protocol(ProtocolError::InvalidPacketSize {
                        expected: 4,
                        actual: data.len(),
                    }));
                }
                let revision = u32::from_be_bytes([data[0], data[1], data[2], data[3]]);
                Ok(HandshakeRequest::Login { revision })
            }
            HandshakeOpcode::WorldList => {
                if data.len() < 4 {
                    return Err(RustscapeError::Protocol(ProtocolError::InvalidPacketSize {
                        expected: 4,
                        actual: data.len(),
                    }));
                }
                let update_stamp = i32::from_be_bytes([data[0], data[1], data[2], data[3]]);
                Ok(HandshakeRequest::WorldList { update_stamp })
            }
            HandshakeOpcode::AccountCreate => Ok(HandshakeRequest::AccountCreate {
                data: data.to_vec(),
            }),
            HandshakeOpcode::AccountRecover => Ok(HandshakeRequest::AccountRecover {
                data: data.to_vec(),
            }),
        }
    }

    /// Process a JS5 handshake and generate response
    pub fn process_js5(&self, request: &HandshakeRequest) -> HandshakeResponse {
        if let HandshakeRequest::Js5 { revision } = request {
            if *revision == self.expected_revision {
                HandshakeResponse::Js5Success
            } else {
                // Return "out of date" error code (6)
                HandshakeResponse::Js5Error { code: 6 }
            }
        } else {
            HandshakeResponse::Js5Error { code: 7 } // Server busy
        }
    }

    /// Process a login handshake and generate response
    pub fn process_login(&self, request: &HandshakeRequest) -> (HandshakeResponse, u64) {
        if let HandshakeRequest::Login { revision } = request {
            if *revision == self.expected_revision {
                // Generate random server key
                let server_key: u64 = rand::random();
                (HandshakeResponse::LoginSuccess { server_key }, server_key)
            } else {
                // Return "game updated" error code (6)
                (HandshakeResponse::LoginError { code: 6 }, 0)
            }
        } else {
            (HandshakeResponse::LoginError { code: 13 }, 0) // Could not complete login
        }
    }

    /// Validate a client revision
    pub fn validate_revision(&self, revision: u32) -> bool {
        revision == self.expected_revision
    }

    /// Get the expected revision
    pub fn expected_revision(&self) -> u32 {
        self.expected_revision
    }
}

impl Default for HandshakeHandler {
    fn default() -> Self {
        Self::new(530)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_handshake_opcode_from_u8() {
        assert_eq!(HandshakeOpcode::from_u8(14), Some(HandshakeOpcode::Login));
        assert_eq!(HandshakeOpcode::from_u8(15), Some(HandshakeOpcode::Js5));
        assert_eq!(
            HandshakeOpcode::from_u8(255),
            Some(HandshakeOpcode::WorldList)
        );
        assert_eq!(HandshakeOpcode::from_u8(99), None);
    }

    #[test]
    fn test_handshake_opcode_as_u8() {
        assert_eq!(HandshakeOpcode::Login.as_u8(), 14);
        assert_eq!(HandshakeOpcode::Js5.as_u8(), 15);
        assert_eq!(HandshakeOpcode::WorldList.as_u8(), 255);
    }

    #[test]
    fn test_parse_js5_request() {
        let handler = HandshakeHandler::new(530);
        let data = 530u32.to_be_bytes();

        let request = handler.parse_request(15, &data).unwrap();
        if let HandshakeRequest::Js5 { revision } = request {
            assert_eq!(revision, 530);
        } else {
            panic!("Expected JS5 request");
        }
    }

    #[test]
    fn test_parse_login_request() {
        let handler = HandshakeHandler::new(530);
        let data = 530u32.to_be_bytes();

        let request = handler.parse_request(14, &data).unwrap();
        if let HandshakeRequest::Login { revision } = request {
            assert_eq!(revision, 530);
        } else {
            panic!("Expected Login request");
        }
    }

    #[test]
    fn test_process_js5_success() {
        let handler = HandshakeHandler::new(530);
        let request = HandshakeRequest::Js5 { revision: 530 };

        let response = handler.process_js5(&request);
        assert!(matches!(response, HandshakeResponse::Js5Success));
    }

    #[test]
    fn test_process_js5_wrong_revision() {
        let handler = HandshakeHandler::new(530);
        let request = HandshakeRequest::Js5 { revision: 500 };

        let response = handler.process_js5(&request);
        if let HandshakeResponse::Js5Error { code } = response {
            assert_eq!(code, 6); // Out of date
        } else {
            panic!("Expected JS5 error");
        }
    }

    #[test]
    fn test_process_login_success() {
        let handler = HandshakeHandler::new(530);
        let request = HandshakeRequest::Login { revision: 530 };

        let (response, server_key) = handler.process_login(&request);
        assert!(matches!(response, HandshakeResponse::LoginSuccess { .. }));
        assert!(server_key != 0);
    }

    #[test]
    fn test_process_login_wrong_revision() {
        let handler = HandshakeHandler::new(530);
        let request = HandshakeRequest::Login { revision: 500 };

        let (response, server_key) = handler.process_login(&request);
        if let HandshakeResponse::LoginError { code } = response {
            assert_eq!(code, 6); // Game updated
        } else {
            panic!("Expected Login error");
        }
        assert_eq!(server_key, 0);
    }

    #[test]
    fn test_validate_revision() {
        let handler = HandshakeHandler::new(530);
        assert!(handler.validate_revision(530));
        assert!(!handler.validate_revision(500));
    }

    #[test]
    fn test_encode_js5_success() {
        let response = HandshakeResponse::Js5Success;
        let encoded = response.encode();
        assert_eq!(encoded, vec![0]);
    }

    #[test]
    fn test_encode_js5_error() {
        let response = HandshakeResponse::Js5Error { code: 6 };
        let encoded = response.encode();
        assert_eq!(encoded, vec![6]);
    }

    #[test]
    fn test_encode_login_success() {
        let response = HandshakeResponse::LoginSuccess { server_key: 12345 };
        let encoded = response.encode();
        assert_eq!(encoded.len(), 9);
        assert_eq!(encoded[0], 0); // Success code
    }

    #[test]
    fn test_handshake_request_opcode() {
        let js5 = HandshakeRequest::Js5 { revision: 530 };
        assert_eq!(js5.opcode(), HandshakeOpcode::Js5);

        let login = HandshakeRequest::Login { revision: 530 };
        assert_eq!(login.opcode(), HandshakeOpcode::Login);

        let world_list = HandshakeRequest::WorldList { update_stamp: 0 };
        assert_eq!(world_list.opcode(), HandshakeOpcode::WorldList);
    }
}

