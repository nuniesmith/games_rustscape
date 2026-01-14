//! JS5 protocol handler
//!
//! The JS5 protocol is used for serving cache files to the client.
//! It handles file requests for game assets like models, textures,
//! maps, interfaces, etc.
//!
//! JS5 operates after a successful JS5 handshake and serves files
//! from the game cache based on index and archive IDs.

use std::collections::VecDeque;
use std::sync::Arc;

use tracing::{trace, warn};

use crate::cache::CacheStore;
use crate::error::{CacheError, ProtocolError, Result, RustscapeError};
use crate::net::buffer::PacketBuffer;

/// JS5 request opcodes
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum Js5Opcode {
    /// Normal priority file request
    FileRequestNormal = 0,
    /// High priority file request
    FileRequestPriority = 1,
    /// Client logged out
    LoggedOut = 2,
    /// Client logged in
    LoggedIn = 3,
    /// Set encryption key
    SetEncryption = 4,
    /// Connection info
    ConnectionInfo = 5,
    /// Connection info 2
    ConnectionInfo2 = 6,
    /// Close connection
    Close = 7,
    /// Connection info 3
    ConnectionInfo3 = 9,
}

impl Js5Opcode {
    /// Convert a u8 to a Js5Opcode
    pub fn from_u8(value: u8) -> Option<Self> {
        match value {
            0 => Some(Self::FileRequestNormal),
            1 => Some(Self::FileRequestPriority),
            2 => Some(Self::LoggedOut),
            3 => Some(Self::LoggedIn),
            4 => Some(Self::SetEncryption),
            5 => Some(Self::ConnectionInfo),
            6 => Some(Self::ConnectionInfo2),
            7 => Some(Self::Close),
            9 => Some(Self::ConnectionInfo3),
            _ => None,
        }
    }

    /// Get the opcode value
    pub fn as_u8(self) -> u8 {
        self as u8
    }

    /// Check if this is a file request opcode
    pub fn is_file_request(&self) -> bool {
        matches!(self, Self::FileRequestNormal | Self::FileRequestPriority)
    }
}

/// A JS5 file request
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Js5FileRequest {
    /// Cache index (0-254 for regular indices, 255 for reference table)
    pub index: u8,
    /// Archive ID within the index
    pub archive: u16,
    /// Whether this is a high priority request
    pub priority: bool,
}

impl Js5FileRequest {
    /// Create a new file request
    pub fn new(index: u8, archive: u16, priority: bool) -> Self {
        Self {
            index,
            archive,
            priority,
        }
    }

    /// Check if this is a checksum table request (index 255, archive 255)
    pub fn is_checksum_table(&self) -> bool {
        self.index == 255 && self.archive == 255
    }

    /// Check if this is a reference table request (index 255)
    pub fn is_reference_table(&self) -> bool {
        self.index == 255
    }
}

/// JS5 file response
#[derive(Debug, Clone)]
pub struct Js5FileResponse {
    /// Cache index
    pub index: u8,
    /// Archive ID
    pub archive: u16,
    /// Compression type (0 = none, 1 = bzip2, 2 = gzip, 3 = lzma)
    pub compression: u8,
    /// File data length
    pub length: u32,
    /// File data (compressed or uncompressed based on compression type)
    pub data: Vec<u8>,
    /// Whether this was a priority request
    pub priority: bool,
}

impl Js5FileResponse {
    /// Create a new file response
    pub fn new(
        index: u8,
        archive: u16,
        compression: u8,
        length: u32,
        data: Vec<u8>,
        priority: bool,
    ) -> Self {
        Self {
            index,
            archive,
            compression,
            length,
            data,
            priority,
        }
    }

    /// Encode the response to bytes for sending to the client
    ///
    /// The response format is:
    /// - index (1 byte)
    /// - archive (2 bytes, big-endian)
    /// - settings (1 byte): compression | (priority ? 0x80 : 0)
    /// - length (4 bytes, big-endian)
    /// - data (with 0xFF markers every 512 bytes)
    pub fn encode(&self) -> Vec<u8> {
        let mut buffer = PacketBuffer::with_capacity(self.data.len() + 16);

        // Header
        buffer.write_ubyte(self.index);
        buffer.write_ushort(self.archive);

        // Settings byte: compression type (bits 0-6) | priority flag (bit 7)
        let settings = self.compression | if self.priority { 0x80 } else { 0x00 };
        buffer.write_ubyte(settings);

        // Length
        buffer.write_uint(self.length);

        // Data with block markers
        // The client expects a 0xFF marker after every 512 bytes of payload
        let mut offset = 8; // We've already written 8 bytes (header)
        for &byte in &self.data {
            if offset == 512 {
                buffer.write_ubyte(0xFF);
                offset = 1;
            }
            buffer.write_ubyte(byte);
            offset += 1;
        }

        buffer.as_bytes().to_vec()
    }

    /// Encode for checksum table response (different format)
    pub fn encode_checksum_table(&self) -> Vec<u8> {
        let mut buffer = PacketBuffer::with_capacity(self.data.len() + 16);

        // Header for checksum table
        buffer.write_ubyte(255);
        buffer.write_ushort(255);

        // Settings byte (no compression for checksum table)
        let settings = if self.priority { 0x80 } else { 0x00 };
        buffer.write_ubyte(settings);

        // Length of data
        buffer.write_uint(self.data.len() as u32);

        // Data with block markers (starting at offset 10 for checksum table)
        let mut offset = 10;
        for &byte in &self.data {
            if offset == 512 {
                buffer.write_ubyte(0xFF);
                offset = 1;
            }
            buffer.write_ubyte(byte);
            offset += 1;
        }

        buffer.as_bytes().to_vec()
    }
}

/// JS5 request queue for managing file requests
#[derive(Debug, Default)]
pub struct Js5RequestQueue {
    /// High priority requests (processed first)
    priority_queue: VecDeque<Js5FileRequest>,
    /// Normal priority requests
    normal_queue: VecDeque<Js5FileRequest>,
}

impl Js5RequestQueue {
    /// Create a new request queue
    pub fn new() -> Self {
        Self {
            priority_queue: VecDeque::new(),
            normal_queue: VecDeque::new(),
        }
    }

    /// Add a file request to the queue
    pub fn push(&mut self, request: Js5FileRequest) {
        if request.priority {
            self.priority_queue.push_back(request);
        } else {
            self.normal_queue.push_back(request);
        }
    }

    /// Get the next request to process (priority requests first)
    pub fn pop(&mut self) -> Option<Js5FileRequest> {
        self.priority_queue
            .pop_front()
            .or_else(|| self.normal_queue.pop_front())
    }

    /// Check if the queue is empty
    pub fn is_empty(&self) -> bool {
        self.priority_queue.is_empty() && self.normal_queue.is_empty()
    }

    /// Get the total number of pending requests
    pub fn len(&self) -> usize {
        self.priority_queue.len() + self.normal_queue.len()
    }

    /// Clear all pending requests
    pub fn clear(&mut self) {
        self.priority_queue.clear();
        self.normal_queue.clear();
    }

    /// Clear only normal priority requests (called on client logout)
    pub fn clear_normal(&mut self) {
        self.normal_queue.clear();
    }
}

/// JS5 protocol handler
pub struct Js5Handler {
    /// Reference to the cache store
    cache: Arc<CacheStore>,
    /// Request queue
    queue: Js5RequestQueue,
    /// Encryption key (XOR key for file data)
    encryption_key: u8,
    /// Whether the client is logged in
    logged_in: bool,
}

impl Js5Handler {
    /// Create a new JS5 handler
    pub fn new(cache: Arc<CacheStore>) -> Self {
        Self {
            cache,
            queue: Js5RequestQueue::new(),
            encryption_key: 0,
            logged_in: false,
        }
    }

    /// Process a JS5 opcode and data
    pub fn process(&mut self, opcode: u8, data: &[u8]) -> Result<Option<Vec<u8>>> {
        let js5_opcode = Js5Opcode::from_u8(opcode);

        match js5_opcode {
            Some(Js5Opcode::FileRequestNormal) | Some(Js5Opcode::FileRequestPriority) => {
                let priority = opcode == 1;
                self.handle_file_request(data, priority)
            }
            Some(Js5Opcode::LoggedOut) => {
                self.logged_in = false;
                // Clear normal priority queue on logout
                self.queue.clear_normal();
                Ok(None)
            }
            Some(Js5Opcode::LoggedIn) => {
                self.logged_in = true;
                Ok(None)
            }
            Some(Js5Opcode::SetEncryption) => {
                if data.len() >= 3 {
                    self.encryption_key = data[0];
                    // Remaining 2 bytes should be 0
                    if data[1] != 0 || data[2] != 0 {
                        warn!("Invalid JS5 encryption request: non-zero padding");
                    }
                }
                Ok(None)
            }
            Some(Js5Opcode::ConnectionInfo)
            | Some(Js5Opcode::ConnectionInfo2)
            | Some(Js5Opcode::ConnectionInfo3) => {
                // Just acknowledge these, they contain client state info
                Ok(None)
            }
            Some(Js5Opcode::Close) => {
                // Client is closing connection
                Err(RustscapeError::Network(
                    crate::error::NetworkError::ConnectionClosed,
                ))
            }
            None => {
                warn!(opcode = opcode, "Unknown JS5 opcode");
                Ok(None)
            }
        }
    }

    /// Handle a file request
    fn handle_file_request(&mut self, data: &[u8], priority: bool) -> Result<Option<Vec<u8>>> {
        if data.len() < 3 {
            return Err(RustscapeError::Protocol(ProtocolError::InvalidPacketSize {
                expected: 3,
                actual: data.len(),
            }));
        }

        let index = data[0];
        let archive = u16::from_be_bytes([data[1], data[2]]);

        let request = Js5FileRequest::new(index, archive, priority);

        trace!(
            index = index,
            archive = archive,
            priority = priority,
            "JS5 file request"
        );

        // Process the request immediately and return the response
        let response = self.get_file_response(&request)?;
        let encoded = if request.is_checksum_table() {
            response.encode_checksum_table()
        } else {
            response.encode()
        };

        Ok(Some(encoded))
    }

    /// Get a file response for a request
    fn get_file_response(&self, request: &Js5FileRequest) -> Result<Js5FileResponse> {
        if request.is_checksum_table() {
            // Get checksum table
            let data = self.cache.get_checksum_table()?;
            Ok(Js5FileResponse::new(
                255,
                255,
                0, // No compression for checksum table
                data.len() as u32,
                data,
                request.priority,
            ))
        } else if request.is_reference_table() {
            // Get reference table for an index
            let data = self.cache.get_reference_table(request.archive as u8)?;
            self.build_response(request, &data)
        } else {
            // Get regular file
            let data = self.cache.get_file(request.index, request.archive as u32)?;
            self.build_response(request, &data)
        }
    }

    /// Build a file response from raw cache data
    fn build_response(&self, request: &Js5FileRequest, data: &[u8]) -> Result<Js5FileResponse> {
        if data.len() < 5 {
            return Err(RustscapeError::Cache(CacheError::Corrupted(format!(
                "File data too short for index {} archive {}",
                request.index, request.archive
            ))));
        }

        // Parse compression header from cache data
        let compression = data[0];
        let length = u32::from_be_bytes([data[1], data[2], data[3], data[4]]);

        // The rest is the actual file data (possibly compressed)
        let file_data = if data.len() > 5 {
            data[5..].to_vec()
        } else {
            Vec::new()
        };

        // Apply encryption if set
        let file_data = if self.encryption_key != 0 {
            file_data.iter().map(|&b| b ^ self.encryption_key).collect()
        } else {
            file_data
        };

        Ok(Js5FileResponse::new(
            request.index,
            request.archive,
            compression,
            length,
            file_data,
            request.priority,
        ))
    }

    /// Process the next queued request
    pub fn process_queue(&mut self) -> Result<Option<Vec<u8>>> {
        if let Some(request) = self.queue.pop() {
            let response = self.get_file_response(&request)?;
            let encoded = if request.is_checksum_table() {
                response.encode_checksum_table()
            } else {
                response.encode()
            };
            Ok(Some(encoded))
        } else {
            Ok(None)
        }
    }

    /// Queue a file request for later processing
    pub fn queue_request(&mut self, request: Js5FileRequest) {
        self.queue.push(request);
    }

    /// Check if there are pending requests
    pub fn has_pending(&self) -> bool {
        !self.queue.is_empty()
    }

    /// Get the number of pending requests
    pub fn pending_count(&self) -> usize {
        self.queue.len()
    }

    /// Get the encryption key
    pub fn encryption_key(&self) -> u8 {
        self.encryption_key
    }

    /// Check if the client is logged in
    pub fn is_logged_in(&self) -> bool {
        self.logged_in
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_js5_opcode_from_u8() {
        assert_eq!(Js5Opcode::from_u8(0), Some(Js5Opcode::FileRequestNormal));
        assert_eq!(Js5Opcode::from_u8(1), Some(Js5Opcode::FileRequestPriority));
        assert_eq!(Js5Opcode::from_u8(7), Some(Js5Opcode::Close));
        assert_eq!(Js5Opcode::from_u8(100), None);
    }

    #[test]
    fn test_js5_opcode_is_file_request() {
        assert!(Js5Opcode::FileRequestNormal.is_file_request());
        assert!(Js5Opcode::FileRequestPriority.is_file_request());
        assert!(!Js5Opcode::Close.is_file_request());
    }

    #[test]
    fn test_js5_file_request() {
        let request = Js5FileRequest::new(255, 255, true);
        assert!(request.is_checksum_table());
        assert!(request.is_reference_table());

        let request = Js5FileRequest::new(255, 5, false);
        assert!(!request.is_checksum_table());
        assert!(request.is_reference_table());

        let request = Js5FileRequest::new(5, 100, true);
        assert!(!request.is_checksum_table());
        assert!(!request.is_reference_table());
    }

    #[test]
    fn test_js5_request_queue() {
        let mut queue = Js5RequestQueue::new();
        assert!(queue.is_empty());

        // Add normal priority request
        queue.push(Js5FileRequest::new(1, 1, false));
        // Add high priority request
        queue.push(Js5FileRequest::new(2, 2, true));
        // Add another normal request
        queue.push(Js5FileRequest::new(3, 3, false));

        assert_eq!(queue.len(), 3);

        // Priority should come first
        let req = queue.pop().unwrap();
        assert_eq!(req.index, 2);
        assert!(req.priority);

        // Then normal requests in order
        let req = queue.pop().unwrap();
        assert_eq!(req.index, 1);

        let req = queue.pop().unwrap();
        assert_eq!(req.index, 3);

        assert!(queue.is_empty());
    }

    #[test]
    fn test_js5_request_queue_clear_normal() {
        let mut queue = Js5RequestQueue::new();

        queue.push(Js5FileRequest::new(1, 1, false));
        queue.push(Js5FileRequest::new(2, 2, true));
        queue.push(Js5FileRequest::new(3, 3, false));

        queue.clear_normal();

        // Only priority request should remain
        assert_eq!(queue.len(), 1);
        let req = queue.pop().unwrap();
        assert!(req.priority);
    }

    #[test]
    fn test_js5_file_response_encode() {
        let response = Js5FileResponse::new(
            5,
            100,
            1,    // bzip2 compression
            1000, // length
            vec![1, 2, 3, 4, 5],
            true,
        );

        let encoded = response.encode();

        // Check header
        assert_eq!(encoded[0], 5); // index
        assert_eq!(encoded[1], 0); // archive high byte
        assert_eq!(encoded[2], 100); // archive low byte
        assert_eq!(encoded[3], 0x81); // compression (1) | priority (0x80)
    }

    #[test]
    fn test_js5_response_block_markers() {
        // Create a response with more than 512 bytes of data
        let data: Vec<u8> = (0..600).map(|i| (i % 256) as u8).collect();
        let response = Js5FileResponse::new(1, 1, 0, data.len() as u32, data, false);

        let encoded = response.encode();

        // There should be a 0xFF marker after 512 bytes
        // Header is 8 bytes, so marker should be at position 512
        // But we need to account for the marker itself in the position
        // After 504 data bytes (512 - 8 header), we should see 0xFF
        let marker_pos = 512;
        assert_eq!(
            encoded[marker_pos], 0xFF,
            "Expected block marker at position {}",
            marker_pos
        );
    }
}
