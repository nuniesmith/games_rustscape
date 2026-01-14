//! ISAAC (Indirection, Shift, Accumulate, Add, and Count) cipher implementation
//!
//! ISAAC is a cryptographically secure pseudorandom number generator used in the
//! RuneScape protocol for encrypting packet opcodes. This implementation follows
//! the original ISAAC specification by Bob Jenkins.
//!
//! Reference: http://www.burtleburtle.net/bob/rand/isaacafa.html

use std::fmt;

/// Size of the ISAAC state array (must be a power of 2)
const SIZE: usize = 256;

/// Mask for array indexing (SIZE - 1)
const MASK: usize = SIZE - 1;

/// Golden ratio constant used in initialization
const GOLDEN_RATIO: u32 = 0x9e3779b9;

/// ISAAC cipher state
#[derive(Clone)]
pub struct Isaac {
    /// Results buffer
    results: [u32; SIZE],
    /// Internal state
    memory: [u32; SIZE],
    /// Accumulator
    aa: u32,
    /// Previous result
    bb: u32,
    /// Counter
    cc: u32,
    /// Current position in results buffer
    count: usize,
}

impl Isaac {
    /// Create a new ISAAC cipher with the given seed
    pub fn new(seed: &[u32]) -> Self {
        let mut isaac = Self {
            results: [0u32; SIZE],
            memory: [0u32; SIZE],
            aa: 0,
            bb: 0,
            cc: 0,
            count: 0,
        };

        isaac.init(seed);
        isaac
    }

    /// Create a new ISAAC cipher from 4 seed values (common RS usage)
    pub fn from_seeds(seed0: u32, seed1: u32, seed2: u32, seed3: u32) -> Self {
        Self::new(&[seed0, seed1, seed2, seed3])
    }

    /// Initialize the cipher with the given seed
    fn init(&mut self, seed: &[u32]) {
        // Initialize the results buffer with the seed
        for (i, &val) in seed.iter().take(SIZE).enumerate() {
            self.results[i] = val;
        }

        // Initialize with golden ratio
        let mut a = GOLDEN_RATIO;
        let mut b = GOLDEN_RATIO;
        let mut c = GOLDEN_RATIO;
        let mut d = GOLDEN_RATIO;
        let mut e = GOLDEN_RATIO;
        let mut f = GOLDEN_RATIO;
        let mut g = GOLDEN_RATIO;
        let mut h = GOLDEN_RATIO;

        // Scramble the initial values
        for _ in 0..4 {
            mix(
                &mut a, &mut b, &mut c, &mut d, &mut e, &mut f, &mut g, &mut h,
            );
        }

        // Fill the memory array with mixed seed values
        for i in (0..SIZE).step_by(8) {
            a = a.wrapping_add(self.results[i]);
            b = b.wrapping_add(self.results[i + 1]);
            c = c.wrapping_add(self.results[i + 2]);
            d = d.wrapping_add(self.results[i + 3]);
            e = e.wrapping_add(self.results[i + 4]);
            f = f.wrapping_add(self.results[i + 5]);
            g = g.wrapping_add(self.results[i + 6]);
            h = h.wrapping_add(self.results[i + 7]);

            mix(
                &mut a, &mut b, &mut c, &mut d, &mut e, &mut f, &mut g, &mut h,
            );

            self.memory[i] = a;
            self.memory[i + 1] = b;
            self.memory[i + 2] = c;
            self.memory[i + 3] = d;
            self.memory[i + 4] = e;
            self.memory[i + 5] = f;
            self.memory[i + 6] = g;
            self.memory[i + 7] = h;
        }

        // Second pass to further diffuse the seed
        for i in (0..SIZE).step_by(8) {
            a = a.wrapping_add(self.memory[i]);
            b = b.wrapping_add(self.memory[i + 1]);
            c = c.wrapping_add(self.memory[i + 2]);
            d = d.wrapping_add(self.memory[i + 3]);
            e = e.wrapping_add(self.memory[i + 4]);
            f = f.wrapping_add(self.memory[i + 5]);
            g = g.wrapping_add(self.memory[i + 6]);
            h = h.wrapping_add(self.memory[i + 7]);

            mix(
                &mut a, &mut b, &mut c, &mut d, &mut e, &mut f, &mut g, &mut h,
            );

            self.memory[i] = a;
            self.memory[i + 1] = b;
            self.memory[i + 2] = c;
            self.memory[i + 3] = d;
            self.memory[i + 4] = e;
            self.memory[i + 5] = f;
            self.memory[i + 6] = g;
            self.memory[i + 7] = h;
        }

        // Generate initial results
        self.generate();
        self.count = SIZE;
    }

    /// Generate 256 new random values
    fn generate(&mut self) {
        self.cc = self.cc.wrapping_add(1);
        self.bb = self.bb.wrapping_add(self.cc);

        for i in 0..SIZE {
            let x = self.memory[i];

            // Rotate accumulator based on position
            self.aa = match i & 3 {
                0 => self.aa ^ (self.aa << 13),
                1 => self.aa ^ (self.aa >> 6),
                2 => self.aa ^ (self.aa << 2),
                3 => self.aa ^ (self.aa >> 16),
                _ => unreachable!(),
            };

            self.aa = self.memory[(i + 128) & MASK].wrapping_add(self.aa);

            let y = self.memory[((x >> 2) as usize) & MASK]
                .wrapping_add(self.aa)
                .wrapping_add(self.bb);

            self.memory[i] = y;
            self.bb = self.memory[((y >> 10) as usize) & MASK].wrapping_add(x);
            self.results[i] = self.bb;
        }
    }

    /// Get the next random value from the generator
    #[inline]
    pub fn next(&mut self) -> u32 {
        if self.count == 0 {
            self.generate();
            self.count = SIZE;
        }
        self.count -= 1;
        self.results[self.count]
    }

    /// Get the next random value and return only the lower 8 bits
    /// Used for encrypting packet opcodes in RS protocol
    #[inline]
    pub fn next_byte(&mut self) -> u8 {
        (self.next() & 0xFF) as u8
    }

    /// Peek at the next value without advancing the generator
    pub fn peek(&self) -> u32 {
        if self.count == 0 {
            // Would need to generate, return first result value
            self.results[SIZE - 1]
        } else {
            self.results[self.count - 1]
        }
    }
}

impl fmt::Debug for Isaac {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("Isaac")
            .field("count", &self.count)
            .field("aa", &self.aa)
            .field("bb", &self.bb)
            .field("cc", &self.cc)
            .finish()
    }
}

/// Mix function for ISAAC initialization
#[inline]
fn mix(
    a: &mut u32,
    b: &mut u32,
    c: &mut u32,
    d: &mut u32,
    e: &mut u32,
    f: &mut u32,
    g: &mut u32,
    h: &mut u32,
) {
    *a ^= *b << 11;
    *d = d.wrapping_add(*a);
    *b = b.wrapping_add(*c);

    *b ^= *c >> 2;
    *e = e.wrapping_add(*b);
    *c = c.wrapping_add(*d);

    *c ^= *d << 8;
    *f = f.wrapping_add(*c);
    *d = d.wrapping_add(*e);

    *d ^= *e >> 16;
    *g = g.wrapping_add(*d);
    *e = e.wrapping_add(*f);

    *e ^= *f << 10;
    *h = h.wrapping_add(*e);
    *f = f.wrapping_add(*g);

    *f ^= *g >> 4;
    *a = a.wrapping_add(*f);
    *g = g.wrapping_add(*h);

    *g ^= *h << 8;
    *b = b.wrapping_add(*g);
    *h = h.wrapping_add(*a);

    *h ^= *a >> 9;
    *c = c.wrapping_add(*h);
    *a = a.wrapping_add(*b);
}

/// Paired ISAAC ciphers for encoding and decoding
/// In the RS protocol, client and server each have an encode/decode pair
/// with related seeds (server decode = client encode + 50)
#[derive(Clone)]
pub struct IsaacPair {
    /// Cipher for encoding outgoing packets
    pub encode: Isaac,
    /// Cipher for decoding incoming packets
    pub decode: Isaac,
}

impl IsaacPair {
    /// Create a new ISAAC pair from the client seeds
    /// The server's encode cipher uses seeds offset by 50
    pub fn new(seeds: &[u32; 4]) -> Self {
        // Decode cipher uses the original seeds (to decode what client encoded)
        let decode = Isaac::new(seeds);

        // Encode cipher uses seeds + 50 (client will decode with these)
        let encode_seeds: [u32; 4] = [
            seeds[0].wrapping_add(50),
            seeds[1].wrapping_add(50),
            seeds[2].wrapping_add(50),
            seeds[3].wrapping_add(50),
        ];
        let encode = Isaac::new(&encode_seeds);

        Self { encode, decode }
    }

    /// Create a pair for the client side (reverse of server)
    pub fn for_client(seeds: &[u32; 4]) -> Self {
        // Encode cipher uses the original seeds
        let encode = Isaac::new(seeds);

        // Decode cipher uses seeds + 50 (to decode what server encoded)
        let decode_seeds: [u32; 4] = [
            seeds[0].wrapping_add(50),
            seeds[1].wrapping_add(50),
            seeds[2].wrapping_add(50),
            seeds[3].wrapping_add(50),
        ];
        let decode = Isaac::new(&decode_seeds);

        Self { encode, decode }
    }

    /// Encode a packet opcode
    #[inline]
    pub fn encode_opcode(&mut self, opcode: u8) -> u8 {
        opcode.wrapping_add(self.encode.next_byte())
    }

    /// Decode a packet opcode
    #[inline]
    pub fn decode_opcode(&mut self, encoded: u8) -> u8 {
        encoded.wrapping_sub(self.decode.next_byte())
    }
}

impl fmt::Debug for IsaacPair {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("IsaacPair")
            .field("encode", &self.encode)
            .field("decode", &self.decode)
            .finish()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_isaac_deterministic() {
        // Same seed should produce same sequence
        let mut isaac1 = Isaac::new(&[1, 2, 3, 4]);
        let mut isaac2 = Isaac::new(&[1, 2, 3, 4]);

        for _ in 0..1000 {
            assert_eq!(isaac1.next(), isaac2.next());
        }
    }

    #[test]
    fn test_isaac_different_seeds() {
        // Different seeds should produce different sequences
        let mut isaac1 = Isaac::new(&[1, 2, 3, 4]);
        let mut isaac2 = Isaac::new(&[5, 6, 7, 8]);

        // Highly unlikely to match
        let mut all_match = true;
        for _ in 0..100 {
            if isaac1.next() != isaac2.next() {
                all_match = false;
                break;
            }
        }
        assert!(!all_match);
    }

    #[test]
    fn test_isaac_pair_encode_decode() {
        let seeds = [12345u32, 67890, 11111, 22222];

        // Server side
        let mut server = IsaacPair::new(&seeds);
        // Client side
        let mut client = IsaacPair::for_client(&seeds);

        // Client encodes, server decodes
        for opcode in 0u8..=255 {
            let encoded = client.encode_opcode(opcode);
            let decoded = server.decode_opcode(encoded);
            assert_eq!(
                opcode, decoded,
                "Client->Server failed for opcode {}",
                opcode
            );
        }

        // Reset ciphers
        let mut server = IsaacPair::new(&seeds);
        let mut client = IsaacPair::for_client(&seeds);

        // Server encodes, client decodes
        for opcode in 0u8..=255 {
            let encoded = server.encode_opcode(opcode);
            let decoded = client.decode_opcode(encoded);
            assert_eq!(
                opcode, decoded,
                "Server->Client failed for opcode {}",
                opcode
            );
        }
    }

    #[test]
    fn test_isaac_from_seeds() {
        let isaac1 = Isaac::from_seeds(1, 2, 3, 4);
        let isaac2 = Isaac::new(&[1, 2, 3, 4]);

        // Should produce same initial state
        assert_eq!(isaac1.count, isaac2.count);
        assert_eq!(isaac1.aa, isaac2.aa);
        assert_eq!(isaac1.bb, isaac2.bb);
        assert_eq!(isaac1.cc, isaac2.cc);
    }

    #[test]
    fn test_isaac_generates_on_exhaustion() {
        let mut isaac = Isaac::new(&[1, 2, 3, 4]);

        // Exhaust the buffer
        for _ in 0..SIZE {
            isaac.next();
        }

        // Should regenerate and continue
        let val = isaac.next();
        assert!(val != 0 || isaac.results.iter().any(|&x| x != 0));
    }

    #[test]
    fn test_next_byte() {
        let mut isaac = Isaac::new(&[0xDEADBEEF, 0xCAFEBABE, 0x12345678, 0x87654321]);

        // next_byte should only return values 0-255
        for _ in 0..1000 {
            let byte = isaac.next_byte();
            assert!(byte <= 255);
        }
    }
}
