//! Packet buffer implementation
//!
//! Provides a byte buffer with RuneScape-specific read/write operations including:
//! - Standard integer types (byte, short, int, long)
//! - Little-endian variants
//! - RS-specific encodings (byte A/C/S, short A, etc.)
//! - Smart encoding for variable-length integers
//! - Bit access mode for efficient flag packing
//! - String encoding

use bytes::{BufMut, BytesMut};

/// Maximum packet size (64KB)
pub const MAX_PACKET_SIZE: usize = 65535;

/// Packet buffer for reading and writing game protocol data
#[derive(Debug, Clone)]
pub struct PacketBuffer {
    /// Internal byte buffer
    data: BytesMut,
    /// Current read position
    read_pos: usize,
    /// Bit access position (in bits)
    bit_pos: usize,
    /// Whether currently in bit access mode
    in_bit_mode: bool,
}

impl PacketBuffer {
    /// Create a new empty packet buffer
    pub fn new() -> Self {
        Self {
            data: BytesMut::new(),
            read_pos: 0,
            bit_pos: 0,
            in_bit_mode: false,
        }
    }

    /// Create a packet buffer with a specific capacity
    pub fn with_capacity(capacity: usize) -> Self {
        Self {
            data: BytesMut::with_capacity(capacity),
            read_pos: 0,
            bit_pos: 0,
            in_bit_mode: false,
        }
    }

    /// Create a packet buffer from existing bytes
    pub fn from_bytes(bytes: &[u8]) -> Self {
        Self {
            data: BytesMut::from(bytes),
            read_pos: 0,
            bit_pos: 0,
            in_bit_mode: false,
        }
    }

    /// Create a packet buffer by wrapping a BytesMut
    pub fn wrap(data: BytesMut) -> Self {
        Self {
            data,
            read_pos: 0,
            bit_pos: 0,
            in_bit_mode: false,
        }
    }

    // ============ Properties ============

    /// Get the current read position
    #[inline]
    pub fn read_position(&self) -> usize {
        self.read_pos
    }

    /// Set the read position
    #[inline]
    pub fn set_read_position(&mut self, pos: usize) {
        self.read_pos = pos;
    }

    /// Get the current write position (end of buffer)
    #[inline]
    pub fn write_position(&self) -> usize {
        self.data.len()
    }

    /// Get the total length of the buffer
    #[inline]
    pub fn len(&self) -> usize {
        self.data.len()
    }

    /// Check if the buffer is empty
    #[inline]
    pub fn is_empty(&self) -> bool {
        self.data.is_empty()
    }

    /// Get the number of bytes remaining to read
    #[inline]
    pub fn remaining(&self) -> usize {
        self.data.len().saturating_sub(self.read_pos)
    }

    /// Check if there are bytes remaining to read
    #[inline]
    pub fn has_remaining(&self) -> bool {
        self.remaining() > 0
    }

    /// Get a reference to the underlying bytes
    #[inline]
    pub fn as_bytes(&self) -> &[u8] {
        &self.data
    }

    /// Get a mutable reference to the underlying bytes
    #[inline]
    pub fn as_bytes_mut(&mut self) -> &mut [u8] {
        &mut self.data
    }

    /// Get the underlying BytesMut
    #[inline]
    pub fn into_inner(self) -> BytesMut {
        self.data
    }

    /// Clear the buffer and reset positions
    pub fn clear(&mut self) {
        self.data.clear();
        self.read_pos = 0;
        self.bit_pos = 0;
        self.in_bit_mode = false;
    }

    /// Reset read position to start
    pub fn reset(&mut self) {
        self.read_pos = 0;
        self.bit_pos = 0;
        self.in_bit_mode = false;
    }

    /// Skip a number of bytes when reading
    pub fn skip(&mut self, count: usize) {
        self.read_pos = (self.read_pos + count).min(self.data.len());
    }

    // ============ Reading Methods (Big-Endian) ============

    /// Read a signed byte
    pub fn read_byte(&mut self) -> i8 {
        if self.read_pos >= self.data.len() {
            return 0;
        }
        let value = self.data[self.read_pos] as i8;
        self.read_pos += 1;
        value
    }

    /// Read an unsigned byte
    pub fn read_ubyte(&mut self) -> u8 {
        if self.read_pos >= self.data.len() {
            return 0;
        }
        let value = self.data[self.read_pos];
        self.read_pos += 1;
        value
    }

    /// Read a signed big-endian short (2 bytes)
    pub fn read_short(&mut self) -> i16 {
        let b1 = self.read_ubyte() as i16;
        let b2 = self.read_ubyte() as i16;
        (b1 << 8) | b2
    }

    /// Read an unsigned big-endian short (2 bytes)
    pub fn read_ushort(&mut self) -> u16 {
        let b1 = self.read_ubyte() as u16;
        let b2 = self.read_ubyte() as u16;
        (b1 << 8) | b2
    }

    /// Read a 24-bit integer (3 bytes, big-endian)
    pub fn read_int24(&mut self) -> i32 {
        let b1 = self.read_ubyte() as i32;
        let b2 = self.read_ubyte() as i32;
        let b3 = self.read_ubyte() as i32;
        (b1 << 16) | (b2 << 8) | b3
    }

    /// Read a signed big-endian int (4 bytes)
    pub fn read_int(&mut self) -> i32 {
        let b1 = self.read_ubyte() as i32;
        let b2 = self.read_ubyte() as i32;
        let b3 = self.read_ubyte() as i32;
        let b4 = self.read_ubyte() as i32;
        (b1 << 24) | (b2 << 16) | (b3 << 8) | b4
    }

    /// Read an unsigned big-endian int (4 bytes)
    pub fn read_uint(&mut self) -> u32 {
        self.read_int() as u32
    }

    /// Read a signed big-endian long (8 bytes)
    pub fn read_long(&mut self) -> i64 {
        let high = self.read_int() as i64;
        let low = self.read_int() as u32 as i64;
        (high << 32) | low
    }

    /// Read an unsigned big-endian long (8 bytes)
    pub fn read_ulong(&mut self) -> u64 {
        self.read_long() as u64
    }

    // ============ Reading Methods (Little-Endian) ============

    /// Read a signed little-endian short (2 bytes)
    pub fn read_short_le(&mut self) -> i16 {
        let b1 = self.read_ubyte() as i16;
        let b2 = self.read_ubyte() as i16;
        (b2 << 8) | b1
    }

    /// Read an unsigned little-endian short (2 bytes)
    pub fn read_ushort_le(&mut self) -> u16 {
        let b1 = self.read_ubyte() as u16;
        let b2 = self.read_ubyte() as u16;
        (b2 << 8) | b1
    }

    /// Read a signed little-endian int (4 bytes)
    pub fn read_int_le(&mut self) -> i32 {
        let b1 = self.read_ubyte() as i32;
        let b2 = self.read_ubyte() as i32;
        let b3 = self.read_ubyte() as i32;
        let b4 = self.read_ubyte() as i32;
        (b4 << 24) | (b3 << 16) | (b2 << 8) | b1
    }

    // ============ RS-Specific Reading Methods ============

    /// Read byte A (value - 128)
    pub fn read_byte_a(&mut self) -> u8 {
        self.read_ubyte().wrapping_sub(128)
    }

    /// Read byte C (negated)
    pub fn read_byte_c(&mut self) -> i8 {
        -(self.read_byte())
    }

    /// Read byte S (128 - value)
    pub fn read_byte_s(&mut self) -> u8 {
        (128u8).wrapping_sub(self.read_ubyte())
    }

    /// Read short A (big-endian with A modifier on second byte)
    pub fn read_short_a(&mut self) -> u16 {
        let b1 = self.read_ubyte() as u16;
        let b2 = self.read_ubyte().wrapping_sub(128) as u16;
        (b1 << 8) | b2
    }

    /// Read little-endian short A
    pub fn read_short_le_a(&mut self) -> u16 {
        let b1 = self.read_ubyte().wrapping_sub(128) as u16;
        let b2 = self.read_ubyte() as u16;
        (b2 << 8) | b1
    }

    /// Read int variant 1 (middle-endian 1)
    pub fn read_int_v1(&mut self) -> i32 {
        let b1 = self.read_ubyte() as i32;
        let b2 = self.read_ubyte() as i32;
        let b3 = self.read_ubyte() as i32;
        let b4 = self.read_ubyte() as i32;
        (b3 << 24) | (b4 << 16) | (b1 << 8) | b2
    }

    /// Read int variant 2 (middle-endian 2)
    pub fn read_int_v2(&mut self) -> i32 {
        let b1 = self.read_ubyte() as i32;
        let b2 = self.read_ubyte() as i32;
        let b3 = self.read_ubyte() as i32;
        let b4 = self.read_ubyte() as i32;
        (b2 << 24) | (b1 << 16) | (b4 << 8) | b3
    }

    /// Read a smart value (1 or 2 bytes depending on magnitude)
    pub fn read_smart(&mut self) -> u16 {
        let peek = self.peek_ubyte();
        if peek < 128 {
            self.read_ubyte() as u16
        } else {
            self.read_ushort() - 32768
        }
    }

    /// Read a signed smart value
    pub fn read_smart_signed(&mut self) -> i16 {
        let peek = self.peek_ubyte();
        if peek < 128 {
            self.read_ubyte() as i16 - 64
        } else {
            (self.read_ushort() as i32 - 49152) as i16
        }
    }

    /// Read a big smart value (2 or 4 bytes)
    pub fn read_big_smart(&mut self) -> i32 {
        let peek = self.peek_byte();
        if peek < 0 {
            self.read_int() & 0x7fffffff
        } else {
            let value = self.read_ushort();
            if value == 32767 {
                -1
            } else {
                value as i32
            }
        }
    }

    /// Peek at the next byte without advancing position
    pub fn peek_byte(&self) -> i8 {
        if self.read_pos >= self.data.len() {
            return 0;
        }
        self.data[self.read_pos] as i8
    }

    /// Peek at the next unsigned byte without advancing position
    pub fn peek_ubyte(&self) -> u8 {
        if self.read_pos >= self.data.len() {
            return 0;
        }
        self.data[self.read_pos]
    }

    // ============ String Reading ============

    /// Read a null-terminated string
    pub fn read_string(&mut self) -> String {
        let mut bytes = Vec::new();
        loop {
            let b = self.read_ubyte();
            if b == 0 {
                break;
            }
            bytes.push(b);
        }
        String::from_utf8_lossy(&bytes).into_owned()
    }

    /// Read a Jagex-style string (0 marker, then null-terminated)
    pub fn read_string_jagex(&mut self) -> Option<String> {
        let marker = self.read_ubyte();
        if marker != 0 {
            return None;
        }
        Some(self.read_string())
    }

    /// Read a specific number of bytes
    pub fn read_bytes(&mut self, length: usize) -> Vec<u8> {
        let end = (self.read_pos + length).min(self.data.len());
        let bytes = self.data[self.read_pos..end].to_vec();
        self.read_pos = end;
        bytes
    }

    /// Read bytes into an existing slice
    pub fn read_bytes_into(&mut self, dest: &mut [u8]) {
        let len = dest.len().min(self.remaining());
        dest[..len].copy_from_slice(&self.data[self.read_pos..self.read_pos + len]);
        self.read_pos += len;
    }

    // ============ Writing Methods (Big-Endian) ============

    /// Write a signed byte
    pub fn write_byte(&mut self, value: i8) {
        self.data.put_i8(value);
    }

    /// Write an unsigned byte
    pub fn write_ubyte(&mut self, value: u8) {
        self.data.put_u8(value);
    }

    /// Write a signed big-endian short (2 bytes)
    pub fn write_short(&mut self, value: i16) {
        self.data.put_i16(value);
    }

    /// Write an unsigned big-endian short (2 bytes)
    pub fn write_ushort(&mut self, value: u16) {
        self.data.put_u16(value);
    }

    /// Write a 24-bit integer (3 bytes, big-endian)
    pub fn write_int24(&mut self, value: i32) {
        self.write_ubyte(((value >> 16) & 0xff) as u8);
        self.write_ubyte(((value >> 8) & 0xff) as u8);
        self.write_ubyte((value & 0xff) as u8);
    }

    /// Write a signed big-endian int (4 bytes)
    pub fn write_int(&mut self, value: i32) {
        self.data.put_i32(value);
    }

    /// Write an unsigned big-endian int (4 bytes)
    pub fn write_uint(&mut self, value: u32) {
        self.data.put_u32(value);
    }

    /// Write a signed big-endian long (8 bytes)
    pub fn write_long(&mut self, value: i64) {
        self.data.put_i64(value);
    }

    /// Write an unsigned big-endian long (8 bytes)
    pub fn write_ulong(&mut self, value: u64) {
        self.data.put_u64(value);
    }

    // ============ Writing Methods (Little-Endian) ============

    /// Write a signed little-endian short (2 bytes)
    pub fn write_short_le(&mut self, value: i16) {
        self.data.put_i16_le(value);
    }

    /// Write an unsigned little-endian short (2 bytes)
    pub fn write_ushort_le(&mut self, value: u16) {
        self.data.put_u16_le(value);
    }

    /// Write a signed little-endian int (4 bytes)
    pub fn write_int_le(&mut self, value: i32) {
        self.data.put_i32_le(value);
    }

    // ============ RS-Specific Writing Methods ============

    /// Write byte A (value + 128)
    pub fn write_byte_a(&mut self, value: u8) {
        self.write_ubyte(value.wrapping_add(128));
    }

    /// Write byte C (negated)
    pub fn write_byte_c(&mut self, value: i8) {
        self.write_byte(-value);
    }

    /// Write byte S (128 - value)
    pub fn write_byte_s(&mut self, value: u8) {
        self.write_ubyte((128u8).wrapping_sub(value));
    }

    /// Write short A (big-endian with A modifier on second byte)
    pub fn write_short_a(&mut self, value: u16) {
        self.write_ubyte(((value >> 8) & 0xff) as u8);
        self.write_ubyte((value as u8).wrapping_add(128));
    }

    /// Write little-endian short A
    pub fn write_short_le_a(&mut self, value: u16) {
        self.write_ubyte((value as u8).wrapping_add(128));
        self.write_ubyte(((value >> 8) & 0xff) as u8);
    }

    /// Write int variant 1 (middle-endian 1)
    pub fn write_int_v1(&mut self, value: i32) {
        self.write_ubyte(((value >> 8) & 0xff) as u8);
        self.write_ubyte((value & 0xff) as u8);
        self.write_ubyte(((value >> 24) & 0xff) as u8);
        self.write_ubyte(((value >> 16) & 0xff) as u8);
    }

    /// Write int variant 2 (middle-endian 2)
    pub fn write_int_v2(&mut self, value: i32) {
        self.write_ubyte(((value >> 16) & 0xff) as u8);
        self.write_ubyte(((value >> 24) & 0xff) as u8);
        self.write_ubyte((value & 0xff) as u8);
        self.write_ubyte(((value >> 8) & 0xff) as u8);
    }

    /// Write a smart value (1 or 2 bytes depending on magnitude)
    pub fn write_smart(&mut self, value: u16) {
        if value < 128 {
            self.write_ubyte(value as u8);
        } else {
            self.write_ushort(value + 32768);
        }
    }

    // ============ String Writing ============

    /// Write a null-terminated string
    pub fn write_string(&mut self, value: &str) {
        self.data.extend_from_slice(value.as_bytes());
        self.write_ubyte(0);
    }

    /// Write a Jagex-style string (0 marker, then null-terminated)
    pub fn write_string_jagex(&mut self, value: &str) {
        self.write_ubyte(0);
        self.write_string(value);
    }

    /// Write raw bytes
    pub fn write_bytes(&mut self, bytes: &[u8]) {
        self.data.extend_from_slice(bytes);
    }

    /// Write bytes in reverse order
    pub fn write_bytes_reversed(&mut self, bytes: &[u8]) {
        for &b in bytes.iter().rev() {
            self.write_ubyte(b);
        }
    }

    // ============ Bit Access ============

    /// Enter bit access mode for writing (appends to end)
    pub fn start_bit_access(&mut self) {
        // For writing, start at the current end of buffer
        // For reading (when read_pos < len), start at current read position
        if self.read_pos < self.data.len() {
            self.bit_pos = self.read_pos * 8;
        } else {
            self.bit_pos = self.data.len() * 8;
        }
        self.in_bit_mode = true;
    }

    /// Exit bit access mode
    pub fn end_bit_access(&mut self) {
        let byte_pos = (self.bit_pos + 7) / 8;
        // Pad buffer to byte boundary if needed
        while self.data.len() < byte_pos {
            self.data.put_u8(0);
        }
        self.in_bit_mode = false;
    }

    /// Read bits from the buffer
    pub fn read_bits(&mut self, count: usize) -> u32 {
        if !self.in_bit_mode {
            panic!("Not in bit access mode");
        }

        let mut byte_pos = self.bit_pos / 8;
        let mut bit_offset = 8 - (self.bit_pos % 8);
        let mut value = 0u32;
        let mut remaining = count;

        self.bit_pos += count;

        while remaining > bit_offset {
            value |= ((self.data.get(byte_pos).copied().unwrap_or(0) as u32)
                & ((1 << bit_offset) - 1))
                << (remaining - bit_offset);
            remaining -= bit_offset;
            byte_pos += 1;
            bit_offset = 8;
        }

        if remaining == bit_offset {
            value |=
                (self.data.get(byte_pos).copied().unwrap_or(0) as u32) & ((1 << bit_offset) - 1);
        } else {
            value |= ((self.data.get(byte_pos).copied().unwrap_or(0) as u32)
                >> (bit_offset - remaining))
                & ((1 << remaining) - 1);
        }

        value
    }

    /// Write bits to the buffer
    pub fn write_bits(&mut self, count: usize, value: u32) {
        if !self.in_bit_mode {
            panic!("Not in bit access mode");
        }

        let mut byte_pos = self.bit_pos / 8;
        let mut bit_offset = 8 - (self.bit_pos % 8);
        let mut remaining = count;
        let mut val = value;

        self.bit_pos += count;

        // Ensure buffer has enough space
        while self.data.len() <= byte_pos + (count / 8) + 1 {
            self.data.put_u8(0);
        }

        while remaining > bit_offset {
            let mask = (1 << bit_offset) - 1;
            self.data[byte_pos] &= !(mask as u8);
            self.data[byte_pos] |= ((val >> (remaining - bit_offset)) & mask) as u8;
            remaining -= bit_offset;
            byte_pos += 1;
            bit_offset = 8;
        }

        if remaining == bit_offset {
            let mask = (1 << bit_offset) - 1;
            self.data[byte_pos] &= !(mask as u8);
            self.data[byte_pos] |= (val & mask) as u8;
        } else {
            let mask = ((1 << remaining) - 1) << (bit_offset - remaining);
            self.data[byte_pos] &= !(mask as u8);
            self.data[byte_pos] |=
                ((val & ((1 << remaining) - 1)) << (bit_offset - remaining)) as u8;
        }
    }

    /// Get the number of bits needed to represent a value
    pub fn bits_needed(value: u32) -> usize {
        if value == 0 {
            return 1;
        }
        32 - value.leading_zeros() as usize
    }
}

impl Default for PacketBuffer {
    fn default() -> Self {
        Self::new()
    }
}

impl From<Vec<u8>> for PacketBuffer {
    fn from(vec: Vec<u8>) -> Self {
        Self::from_bytes(&vec)
    }
}

impl From<&[u8]> for PacketBuffer {
    fn from(slice: &[u8]) -> Self {
        Self::from_bytes(slice)
    }
}

impl AsRef<[u8]> for PacketBuffer {
    fn as_ref(&self) -> &[u8] {
        &self.data
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_basic_read_write() {
        let mut buf = PacketBuffer::new();

        buf.write_byte(42);
        buf.write_ubyte(255);
        buf.write_short(1234);
        buf.write_int(987654);
        buf.write_long(123456789012345);

        buf.reset();

        assert_eq!(buf.read_byte(), 42);
        assert_eq!(buf.read_ubyte(), 255);
        assert_eq!(buf.read_short(), 1234);
        assert_eq!(buf.read_int(), 987654);
        assert_eq!(buf.read_long(), 123456789012345);
    }

    #[test]
    fn test_little_endian() {
        let mut buf = PacketBuffer::new();

        buf.write_short_le(0x1234);
        buf.write_int_le(0x12345678);

        buf.reset();

        assert_eq!(buf.read_short_le(), 0x1234);
        assert_eq!(buf.read_int_le(), 0x12345678);
    }

    #[test]
    fn test_rs_specific_encodings() {
        let mut buf = PacketBuffer::new();

        buf.write_byte_a(100);
        buf.write_byte_s(50);
        buf.write_short_a(0x1234);

        buf.reset();

        assert_eq!(buf.read_byte_a(), 100);
        assert_eq!(buf.read_byte_s(), 50);
        assert_eq!(buf.read_short_a(), 0x1234);
    }

    #[test]
    fn test_smart_encoding() {
        let mut buf = PacketBuffer::new();

        buf.write_smart(50); // Should be 1 byte
        buf.write_smart(200); // Should be 2 bytes

        buf.reset();

        assert_eq!(buf.read_smart(), 50);
        assert_eq!(buf.read_smart(), 200);
    }

    #[test]
    fn test_string() {
        let mut buf = PacketBuffer::new();

        buf.write_string("Hello, World!");
        buf.write_string_jagex("Jagex String");

        buf.reset();

        assert_eq!(buf.read_string(), "Hello, World!");
        assert_eq!(buf.read_string_jagex(), Some("Jagex String".to_string()));
    }

    #[test]
    fn test_bit_access() {
        let mut buf = PacketBuffer::new();

        buf.start_bit_access();
        buf.write_bits(1, 1); // 1 bit: 1
        buf.write_bits(5, 15); // 5 bits: 15
        buf.write_bits(11, 1234); // 11 bits: 1234
        buf.end_bit_access();

        buf.reset();
        buf.start_bit_access();

        assert_eq!(buf.read_bits(1), 1);
        assert_eq!(buf.read_bits(5), 15);
        assert_eq!(buf.read_bits(11), 1234);
    }

    #[test]
    fn test_int24() {
        let mut buf = PacketBuffer::new();

        buf.write_int24(0x123456);

        buf.reset();

        assert_eq!(buf.read_int24(), 0x123456);
    }

    #[test]
    fn test_middle_endian() {
        let mut buf = PacketBuffer::new();

        buf.write_int_v1(0x12345678);
        buf.write_int_v2(0xABCDEF01u32 as i32);

        buf.reset();

        assert_eq!(buf.read_int_v1(), 0x12345678);
        assert_eq!(buf.read_int_v2() as u32, 0xABCDEF01);
    }

    #[test]
    fn test_bytes() {
        let mut buf = PacketBuffer::new();
        let data = [1, 2, 3, 4, 5];

        buf.write_bytes(&data);

        buf.reset();

        assert_eq!(buf.read_bytes(5), data.to_vec());
    }

    #[test]
    fn test_remaining() {
        let mut buf = PacketBuffer::new();
        buf.write_int(12345);

        buf.reset();
        assert_eq!(buf.remaining(), 4);

        buf.read_short();
        assert_eq!(buf.remaining(), 2);

        buf.read_short();
        assert_eq!(buf.remaining(), 0);
        assert!(!buf.has_remaining());
    }
}
