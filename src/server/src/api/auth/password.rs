//! Password hashing and verification
//!
//! This module provides secure password hashing using bcrypt,
//! as well as password strength validation.

use bcrypt::{hash, verify};

use crate::api::error::ApiError;

/// Hash a password using bcrypt
pub fn hash_password(password: &str, cost: u32) -> Result<String, ApiError> {
    hash(password, cost).map_err(|e| {
        tracing::error!("Failed to hash password: {}", e);
        ApiError::InternalError("Failed to hash password".to_string())
    })
}

/// Verify a password against a bcrypt hash
pub fn verify_password(password: &str, hash: &str) -> Result<bool, ApiError> {
    verify(password, hash).map_err(|e| {
        tracing::error!("Failed to verify password: {}", e);
        ApiError::InternalError("Failed to verify password".to_string())
    })
}

/// Password strength requirements
#[derive(Debug, Clone)]
pub struct PasswordRequirements {
    /// Minimum password length
    pub min_length: usize,
    /// Maximum password length
    pub max_length: usize,
    /// Require at least one uppercase letter
    pub require_uppercase: bool,
    /// Require at least one lowercase letter
    pub require_lowercase: bool,
    /// Require at least one digit
    pub require_digit: bool,
    /// Require at least one special character
    pub require_special: bool,
}

impl Default for PasswordRequirements {
    fn default() -> Self {
        Self {
            min_length: 6,
            max_length: 128,
            require_uppercase: false,
            require_lowercase: false,
            require_digit: false,
            require_special: false,
        }
    }
}

impl PasswordRequirements {
    /// Create strict password requirements
    #[allow(dead_code)]
    pub fn strict() -> Self {
        Self {
            min_length: 8,
            max_length: 128,
            require_uppercase: true,
            require_lowercase: true,
            require_digit: true,
            require_special: false,
        }
    }

    /// Validate a password against these requirements
    pub fn validate(&self, password: &str) -> Result<(), Vec<String>> {
        let mut errors = Vec::new();

        if password.len() < self.min_length {
            errors.push(format!(
                "Password must be at least {} characters",
                self.min_length
            ));
        }

        if password.len() > self.max_length {
            errors.push(format!(
                "Password must be at most {} characters",
                self.max_length
            ));
        }

        if self.require_uppercase && !password.chars().any(|c| c.is_ascii_uppercase()) {
            errors.push("Password must contain at least one uppercase letter".to_string());
        }

        if self.require_lowercase && !password.chars().any(|c| c.is_ascii_lowercase()) {
            errors.push("Password must contain at least one lowercase letter".to_string());
        }

        if self.require_digit && !password.chars().any(|c| c.is_ascii_digit()) {
            errors.push("Password must contain at least one digit".to_string());
        }

        if self.require_special && !password.chars().any(|c| !c.is_alphanumeric()) {
            errors.push("Password must contain at least one special character".to_string());
        }

        if errors.is_empty() {
            Ok(())
        } else {
            Err(errors)
        }
    }
}

/// Calculate password strength score (0-4)
#[allow(dead_code)]
pub fn calculate_strength(password: &str) -> PasswordStrength {
    let mut score = 0;

    // Length checks
    if password.len() >= 6 {
        score += 1;
    }
    if password.len() >= 10 {
        score += 1;
    }
    if password.len() >= 14 {
        score += 1;
    }

    // Complexity checks
    let has_lower = password.chars().any(|c| c.is_ascii_lowercase());
    let has_upper = password.chars().any(|c| c.is_ascii_uppercase());
    let has_digit = password.chars().any(|c| c.is_ascii_digit());
    let has_special = password.chars().any(|c| !c.is_alphanumeric());

    if has_lower && has_upper {
        score += 1;
    }
    if has_digit {
        score += 1;
    }
    if has_special {
        score += 1;
    }

    // Normalize to 0-4
    let normalized = score.min(4);

    PasswordStrength {
        score: normalized,
        label: match normalized {
            0 => "Very Weak",
            1 => "Weak",
            2 => "Fair",
            3 => "Strong",
            4 => "Very Strong",
            _ => "Unknown",
        }
        .to_string(),
    }
}

/// Password strength result
#[derive(Debug, Clone)]
#[allow(dead_code)]
pub struct PasswordStrength {
    /// Strength score (0-4)
    pub score: u8,
    /// Human-readable strength label
    pub label: String,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_hash_and_verify_password() {
        let password = "test_password_123";
        let hashed = hash_password(password, 4).unwrap(); // Use low cost for tests

        assert!(verify_password(password, &hashed).unwrap());
        assert!(!verify_password("wrong_password", &hashed).unwrap());
    }

    #[test]
    fn test_hash_produces_different_results() {
        let password = "same_password";
        let hash1 = hash_password(password, 4).unwrap();
        let hash2 = hash_password(password, 4).unwrap();

        // Hashes should be different (different salts)
        assert_ne!(hash1, hash2);

        // But both should verify correctly
        assert!(verify_password(password, &hash1).unwrap());
        assert!(verify_password(password, &hash2).unwrap());
    }

    #[test]
    fn test_password_requirements_default() {
        let req = PasswordRequirements::default();

        assert!(req.validate("password").is_ok());
        assert!(req.validate("short").is_err()); // Too short (5 chars)
        assert!(req.validate("123456").is_ok()); // Just long enough
    }

    #[test]
    fn test_password_requirements_strict() {
        let req = PasswordRequirements::strict();

        // Missing uppercase
        assert!(req.validate("password1").is_err());

        // Missing lowercase
        assert!(req.validate("PASSWORD1").is_err());

        // Missing digit
        assert!(req.validate("Password").is_err());

        // Too short
        assert!(req.validate("Pass1").is_err());

        // Valid
        assert!(req.validate("Password1").is_ok());
        assert!(req.validate("MySecure123").is_ok());
    }

    #[test]
    fn test_password_requirements_max_length() {
        let req = PasswordRequirements {
            max_length: 20,
            ..Default::default()
        };

        // Too long
        let long_password = "a".repeat(25);
        assert!(req.validate(&long_password).is_err());

        // Just right
        let ok_password = "a".repeat(15);
        assert!(req.validate(&ok_password).is_ok());
    }

    #[test]
    fn test_calculate_strength_very_weak() {
        let strength = calculate_strength("abc");
        assert_eq!(strength.score, 0);
        assert_eq!(strength.label, "Very Weak");
    }

    #[test]
    fn test_calculate_strength_weak() {
        let strength = calculate_strength("password");
        assert!(strength.score >= 1);
    }

    #[test]
    fn test_calculate_strength_fair() {
        let strength = calculate_strength("Password1");
        assert!(strength.score >= 2);
    }

    #[test]
    fn test_calculate_strength_strong() {
        let strength = calculate_strength("MyPassword123!");
        assert!(strength.score >= 3);
    }

    #[test]
    fn test_calculate_strength_very_strong() {
        let strength = calculate_strength("MyV3ryL0ngP@ssword!");
        assert_eq!(strength.score, 4);
        assert_eq!(strength.label, "Very Strong");
    }

    #[test]
    fn test_empty_password() {
        let req = PasswordRequirements::default();
        assert!(req.validate("").is_err());

        let strength = calculate_strength("");
        assert_eq!(strength.score, 0);
    }

    #[test]
    fn test_password_with_spaces() {
        let req = PasswordRequirements::default();
        assert!(req.validate("password with spaces").is_ok());

        // Spaces count as special characters
        let strength = calculate_strength("Pass word 123");
        assert!(strength.score >= 3);
    }
}
