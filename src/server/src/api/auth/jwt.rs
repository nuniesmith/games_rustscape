//! JWT token generation and validation
//!
//! This module provides functions for creating and validating JWT tokens
//! for user authentication.

use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, Validation};
use uuid::Uuid;

use crate::api::error::ApiError;
use crate::api::middleware::Claims;

/// Generate a new JWT token
pub fn generate_token(
    encoding_key: &EncodingKey,
    user_id: Uuid,
    username: &str,
    rights: i16,
    expires_in_seconds: i64,
    token_type: &str,
) -> Result<String, ApiError> {
    let now = chrono::Utc::now().timestamp();

    let claims = Claims {
        sub: user_id.to_string(),
        username: username.to_string(),
        rights,
        iat: now,
        exp: now + expires_in_seconds,
        token_type: token_type.to_string(),
    };

    encode(&Header::default(), &claims, encoding_key)
        .map_err(|e| ApiError::InternalError(format!("Failed to generate token: {}", e)))
}

/// Validate a JWT token and extract claims
pub fn validate_token(decoding_key: &DecodingKey, token: &str) -> Result<Claims, ApiError> {
    let mut validation = Validation::default();
    validation.validate_exp = true;
    validation.leeway = 60; // 60 seconds leeway for clock skew

    let token_data = decode::<Claims>(token, decoding_key, &validation)?;

    // Check if token is expired (redundant with validation, but explicit)
    if token_data.claims.is_expired() {
        return Err(ApiError::TokenExpired);
    }

    Ok(token_data.claims)
}

/// Decode a token without validation (for inspection only)
pub fn decode_token_unsafe(token: &str) -> Result<Claims, ApiError> {
    let mut validation = Validation::default();
    validation.insecure_disable_signature_validation();
    validation.validate_exp = false;

    // We need a dummy key since we're not actually validating
    let dummy_key = DecodingKey::from_secret(b"");

    let token_data = decode::<Claims>(token, &dummy_key, &validation)
        .map_err(|_| ApiError::InvalidToken)?;

    Ok(token_data.claims)
}

/// Extract user ID from token without full validation
/// Useful for logout where we want to blacklist even expired tokens
#[allow(dead_code)]
pub fn extract_user_id_from_token(token: &str) -> Result<Uuid, ApiError> {
    let claims = decode_token_unsafe(token)?;
    claims.user_id()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_keys() -> (EncodingKey, DecodingKey) {
        let secret = "test-secret-key-for-jwt-testing";
        (
            EncodingKey::from_secret(secret.as_bytes()),
            DecodingKey::from_secret(secret.as_bytes()),
        )
    }

    #[test]
    fn test_generate_and_validate_token() {
        let (encoding_key, decoding_key) = test_keys();
        let user_id = Uuid::new_v4();
        let username = "testuser";
        let rights = 0;

        let token =
            generate_token(&encoding_key, user_id, username, rights, 3600, "access").unwrap();

        assert!(!token.is_empty());

        let claims = validate_token(&decoding_key, &token).unwrap();

        assert_eq!(claims.sub, user_id.to_string());
        assert_eq!(claims.username, username);
        assert_eq!(claims.rights, rights);
        assert_eq!(claims.token_type, "access");
    }

    #[test]
    fn test_expired_token() {
        let (encoding_key, decoding_key) = test_keys();
        let user_id = Uuid::new_v4();

        // Generate a token that's already expired
        let token =
            generate_token(&encoding_key, user_id, "testuser", 0, -100, "access").unwrap();

        let result = validate_token(&decoding_key, &token);
        assert!(result.is_err());
    }

    #[test]
    fn test_invalid_signature() {
        let (encoding_key, _) = test_keys();
        let user_id = Uuid::new_v4();

        let token =
            generate_token(&encoding_key, user_id, "testuser", 0, 3600, "access").unwrap();

        // Use a different key for decoding
        let wrong_key = DecodingKey::from_secret(b"wrong-secret");
        let result = validate_token(&wrong_key, &token);

        assert!(result.is_err());
    }

    #[test]
    fn test_decode_token_unsafe() {
        let (encoding_key, _) = test_keys();
        let user_id = Uuid::new_v4();
        let username = "testuser";

        let token =
            generate_token(&encoding_key, user_id, username, 2, 3600, "access").unwrap();

        let claims = decode_token_unsafe(&token).unwrap();

        assert_eq!(claims.username, username);
        assert_eq!(claims.rights, 2);
    }

    #[test]
    fn test_extract_user_id_from_token() {
        let (encoding_key, _) = test_keys();
        let user_id = Uuid::new_v4();

        let token =
            generate_token(&encoding_key, user_id, "testuser", 0, 3600, "access").unwrap();

        let extracted_id = extract_user_id_from_token(&token).unwrap();

        assert_eq!(extracted_id, user_id);
    }

    #[test]
    fn test_refresh_token() {
        let (encoding_key, decoding_key) = test_keys();
        let user_id = Uuid::new_v4();

        let token =
            generate_token(&encoding_key, user_id, "testuser", 0, 604800, "refresh").unwrap();

        let claims = validate_token(&decoding_key, &token).unwrap();

        assert!(claims.is_refresh_token());
        assert!(!claims.is_access_token());
    }

    #[test]
    fn test_malformed_token() {
        let (_, decoding_key) = test_keys();

        let result = validate_token(&decoding_key, "not.a.valid.token");
        assert!(result.is_err());

        let result = validate_token(&decoding_key, "");
        assert!(result.is_err());

        let result = validate_token(&decoding_key, "garbage");
        assert!(result.is_err());
    }
}
