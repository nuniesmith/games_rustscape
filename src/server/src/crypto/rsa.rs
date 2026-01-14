//! RSA encryption/decryption module
//!
//! Handles RSA operations for the login protocol. The client encrypts sensitive
//! login data (ISAAC seeds, password) with the server's public key, and the
//! server decrypts it with its private key.
//!
//! RS uses 1024-bit RSA keys with PKCS#1 v1.5 padding (or raw RSA in some versions).

use std::fmt;

use anyhow::{Context, Result};
use num_bigint::BigUint;

/// RSA key pair for encryption/decryption
#[derive(Clone)]
pub struct RsaKeyPair {
    /// RSA modulus (N)
    pub modulus: BigUint,
    /// RSA private exponent (D)
    pub private_exponent: BigUint,
    /// RSA public exponent (E) - typically 65537
    pub public_exponent: BigUint,
}

impl RsaKeyPair {
    /// Create a new RSA key pair from hex strings
    pub fn from_hex(modulus: &str, private_exponent: &str, public_exponent: u64) -> Result<Self> {
        let modulus =
            BigUint::parse_bytes(modulus.as_bytes(), 16).context("Failed to parse RSA modulus")?;

        let private_exponent = BigUint::parse_bytes(private_exponent.as_bytes(), 16)
            .context("Failed to parse RSA private exponent")?;

        let public_exponent = BigUint::from(public_exponent);

        Ok(Self {
            modulus,
            private_exponent,
            public_exponent,
        })
    }

    /// Create a key pair from raw BigUint values
    pub fn new(modulus: BigUint, private_exponent: BigUint, public_exponent: BigUint) -> Self {
        Self {
            modulus,
            private_exponent,
            public_exponent,
        }
    }

    /// Get the key size in bits
    pub fn key_size_bits(&self) -> usize {
        self.modulus.bits() as usize
    }

    /// Get the key size in bytes
    pub fn key_size_bytes(&self) -> usize {
        (self.key_size_bits() + 7) / 8
    }
}

impl fmt::Debug for RsaKeyPair {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("RsaKeyPair")
            .field("key_size_bits", &self.key_size_bits())
            .field("public_exponent", &self.public_exponent)
            // Don't log the private key!
            .finish()
    }
}

/// RSA decryptor for handling incoming encrypted login blocks
pub struct RsaDecryptor {
    key_pair: RsaKeyPair,
}

impl RsaDecryptor {
    /// Create a new RSA decryptor with the given key pair
    pub fn new(key_pair: RsaKeyPair) -> Self {
        Self { key_pair }
    }

    /// Create from hex strings (convenience constructor)
    pub fn from_hex(modulus: &str, private_exponent: &str, public_exponent: u64) -> Result<Self> {
        let key_pair = RsaKeyPair::from_hex(modulus, private_exponent, public_exponent)?;
        Ok(Self::new(key_pair))
    }

    /// Decrypt an RSA-encrypted block
    ///
    /// The input should be a big-endian encoded integer that was encrypted
    /// with the corresponding public key.
    pub fn decrypt(&self, ciphertext: &[u8]) -> Result<Vec<u8>> {
        if ciphertext.is_empty() {
            anyhow::bail!("Empty ciphertext");
        }

        // Convert ciphertext bytes to BigUint (big-endian)
        let cipher_int = BigUint::from_bytes_be(ciphertext);

        // Perform RSA decryption: plaintext = ciphertext^d mod n
        let plain_int = cipher_int.modpow(&self.key_pair.private_exponent, &self.key_pair.modulus);

        // Convert back to bytes
        let plaintext = plain_int.to_bytes_be();

        Ok(plaintext)
    }

    /// Decrypt and validate the RSA block from login protocol
    ///
    /// The decrypted block should start with a magic byte (10) to verify
    /// successful decryption.
    pub fn decrypt_login_block(&self, ciphertext: &[u8]) -> Result<Vec<u8>> {
        let plaintext = self.decrypt(ciphertext)?;

        if plaintext.is_empty() {
            anyhow::bail!("Decrypted RSA block is empty");
        }

        // The first byte should be the magic value 10
        if plaintext[0] != 10 {
            anyhow::bail!(
                "Invalid RSA block magic byte: expected 10, got {}",
                plaintext[0]
            );
        }

        Ok(plaintext)
    }

    /// Get the underlying key pair
    pub fn key_pair(&self) -> &RsaKeyPair {
        &self.key_pair
    }

    /// Get expected encrypted block size
    pub fn block_size(&self) -> usize {
        self.key_pair.key_size_bytes()
    }
}

impl fmt::Debug for RsaDecryptor {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("RsaDecryptor")
            .field("key_pair", &self.key_pair)
            .finish()
    }
}

/// RSA encryptor for encrypting data with the public key
/// (Used for testing and potentially for server->client encrypted data)
pub struct RsaEncryptor {
    key_pair: RsaKeyPair,
}

impl RsaEncryptor {
    /// Create a new RSA encryptor with the given key pair
    pub fn new(key_pair: RsaKeyPair) -> Self {
        Self { key_pair }
    }

    /// Encrypt a plaintext block
    ///
    /// The plaintext should be smaller than the modulus.
    pub fn encrypt(&self, plaintext: &[u8]) -> Result<Vec<u8>> {
        if plaintext.is_empty() {
            anyhow::bail!("Empty plaintext");
        }

        // Convert plaintext bytes to BigUint (big-endian)
        let plain_int = BigUint::from_bytes_be(plaintext);

        // Verify plaintext is smaller than modulus
        if plain_int >= self.key_pair.modulus {
            anyhow::bail!("Plaintext too large for RSA modulus");
        }

        // Perform RSA encryption: ciphertext = plaintext^e mod n
        let cipher_int = plain_int.modpow(&self.key_pair.public_exponent, &self.key_pair.modulus);

        // Convert back to bytes, padded to key size
        let mut ciphertext = cipher_int.to_bytes_be();

        // Pad to key size if necessary
        let key_bytes = self.key_pair.key_size_bytes();
        if ciphertext.len() < key_bytes {
            let mut padded = vec![0u8; key_bytes - ciphertext.len()];
            padded.append(&mut ciphertext);
            ciphertext = padded;
        }

        Ok(ciphertext)
    }

    /// Get expected encrypted block size
    pub fn block_size(&self) -> usize {
        self.key_pair.key_size_bytes()
    }
}

impl fmt::Debug for RsaEncryptor {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("RsaEncryptor")
            .field("key_pair", &self.key_pair)
            .finish()
    }
}

/// Generate RSA keys from string components
/// This is a helper for loading keys from configuration
pub fn load_rsa_keys(
    modulus_hex: &str,
    private_exponent_hex: &str,
    public_exponent: u64,
) -> Result<(RsaDecryptor, RsaEncryptor)> {
    let key_pair = RsaKeyPair::from_hex(modulus_hex, private_exponent_hex, public_exponent)?;

    let decryptor = RsaDecryptor::new(key_pair.clone());
    let encryptor = RsaEncryptor::new(key_pair);

    Ok((decryptor, encryptor))
}

#[cfg(test)]
mod tests {
    use super::*;

    // Test RSA key pair (small keys for testing only - never use in production!)
    // These are 512-bit keys for fast testing
    const TEST_MODULUS: &str = "d7e9b8c7a6f5e4d3c2b1a09f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c5b";
    const TEST_PRIVATE_EXP: &str = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b";

    fn create_test_key_pair() -> RsaKeyPair {
        // Use small numbers for testing
        let modulus = BigUint::from(3233u32); // Small modulus for testing: 61 * 53
        let private_exponent = BigUint::from(2753u32); // d
        let public_exponent = BigUint::from(17u32); // e

        RsaKeyPair::new(modulus, private_exponent, public_exponent)
    }

    #[test]
    fn test_rsa_encrypt_decrypt() {
        let key_pair = create_test_key_pair();
        let encryptor = RsaEncryptor::new(key_pair.clone());
        let decryptor = RsaDecryptor::new(key_pair);

        // Test with small plaintext
        let plaintext = vec![65u8]; // 'A' = 65
        let ciphertext = encryptor.encrypt(&plaintext).unwrap();
        let decrypted = decryptor.decrypt(&ciphertext).unwrap();

        assert_eq!(plaintext, decrypted);
    }

    #[test]
    fn test_rsa_key_pair_from_hex() {
        // Just test that parsing works with valid hex
        let key_pair = RsaKeyPair::from_hex(
            "ff", // modulus
            "ee", // private exponent
            65537,
        );
        assert!(key_pair.is_ok());
    }

    #[test]
    fn test_rsa_key_pair_invalid_hex() {
        let result = RsaKeyPair::from_hex("not_valid_hex!", "ee", 65537);
        assert!(result.is_err());
    }

    #[test]
    fn test_decrypt_empty_ciphertext() {
        let key_pair = create_test_key_pair();
        let decryptor = RsaDecryptor::new(key_pair);

        let result = decryptor.decrypt(&[]);
        assert!(result.is_err());
    }

    #[test]
    fn test_encrypt_empty_plaintext() {
        let key_pair = create_test_key_pair();
        let encryptor = RsaEncryptor::new(key_pair);

        let result = encryptor.encrypt(&[]);
        assert!(result.is_err());
    }

    #[test]
    fn test_login_block_validation() {
        let key_pair = create_test_key_pair();
        let encryptor = RsaEncryptor::new(key_pair.clone());
        let decryptor = RsaDecryptor::new(key_pair);

        // Valid login block starts with 10
        let valid_block = vec![10u8];
        let encrypted = encryptor.encrypt(&valid_block).unwrap();
        let result = decryptor.decrypt_login_block(&encrypted);
        assert!(result.is_ok());
        assert_eq!(result.unwrap()[0], 10);

        // Invalid login block (doesn't start with 10)
        let invalid_block = vec![5u8];
        let encrypted = encryptor.encrypt(&invalid_block).unwrap();
        let result = decryptor.decrypt_login_block(&encrypted);
        assert!(result.is_err());
    }

    #[test]
    fn test_key_size() {
        let key_pair = create_test_key_pair();
        // 3233 fits in 12 bits
        assert!(key_pair.key_size_bits() <= 12);
        assert!(key_pair.key_size_bytes() <= 2);
    }

    #[test]
    fn test_rsa_with_larger_numbers() {
        // Test with verified valid RSA keys
        // p = 61, q = 53, n = p*q = 3233
        // φ(n) = (p-1)(q-1) = 60*52 = 3120
        // e = 17 (coprime to 3120)
        // d = 2753 (since 17*2753 = 46801 = 15*3120 + 1, so 17*2753 ≡ 1 mod 3120)
        let modulus = BigUint::from(3233u64);
        let private_exponent = BigUint::from(2753u64);
        let public_exponent = BigUint::from(17u64);

        let key_pair = RsaKeyPair::new(modulus, private_exponent, public_exponent);
        let encryptor = RsaEncryptor::new(key_pair.clone());
        let decryptor = RsaDecryptor::new(key_pair);

        // Test encryption/decryption with a small value (must be < modulus)
        // Using value 65 (0x41, ASCII 'A')
        let plaintext = vec![0x41];
        let ciphertext = encryptor.encrypt(&plaintext).unwrap();
        let decrypted = decryptor.decrypt(&ciphertext).unwrap();

        assert_eq!(plaintext, decrypted);
    }

    #[test]
    fn test_load_rsa_keys() {
        let result = load_rsa_keys("ff", "ee", 65537);
        assert!(result.is_ok());

        let (decryptor, encryptor) = result.unwrap();
        assert_eq!(decryptor.block_size(), encryptor.block_size());
    }
}
