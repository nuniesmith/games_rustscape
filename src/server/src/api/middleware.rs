//! API middleware for authentication and request processing
//!
//! This module provides middleware for:
//! - JWT token extraction and validation
//! - User authentication from headers
//! - Request logging and tracing

use axum::{extract::FromRequestParts, http::request::Parts, RequestPartsExt};
use axum_extra::{
    headers::{authorization::Bearer, Authorization},
    TypedHeader,
};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use super::error::ApiError;
use crate::api::ApiState;

/// JWT claims structure
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Claims {
    /// Subject (user ID)
    pub sub: String,
    /// Username
    pub username: String,
    /// User rights level
    pub rights: i16,
    /// Issued at timestamp (Unix)
    pub iat: i64,
    /// Expiration timestamp (Unix)
    pub exp: i64,
    /// Token type (access or refresh)
    #[serde(default = "default_token_type")]
    pub token_type: String,
}

fn default_token_type() -> String {
    "access".to_string()
}

impl Claims {
    /// Create new access token claims
    pub fn new_access(user_id: Uuid, username: &str, rights: i16, expires_in_seconds: i64) -> Self {
        let now = chrono::Utc::now().timestamp();
        Self {
            sub: user_id.to_string(),
            username: username.to_string(),
            rights,
            iat: now,
            exp: now + expires_in_seconds,
            token_type: "access".to_string(),
        }
    }

    /// Create new refresh token claims
    pub fn new_refresh(
        user_id: Uuid,
        username: &str,
        rights: i16,
        expires_in_seconds: i64,
    ) -> Self {
        let now = chrono::Utc::now().timestamp();
        Self {
            sub: user_id.to_string(),
            username: username.to_string(),
            rights,
            iat: now,
            exp: now + expires_in_seconds,
            token_type: "refresh".to_string(),
        }
    }

    /// Get user ID as UUID
    pub fn user_id(&self) -> Result<Uuid, ApiError> {
        Uuid::parse_str(&self.sub).map_err(|_| ApiError::InvalidToken)
    }

    /// Check if this is an access token
    pub fn is_access_token(&self) -> bool {
        self.token_type == "access"
    }

    /// Check if this is a refresh token
    pub fn is_refresh_token(&self) -> bool {
        self.token_type == "refresh"
    }

    /// Check if the token is expired
    pub fn is_expired(&self) -> bool {
        chrono::Utc::now().timestamp() > self.exp
    }
}

/// Authenticated user information extracted from JWT
#[derive(Debug, Clone)]
pub struct AuthenticatedUser {
    /// User ID
    pub id: Uuid,
    /// Username
    pub username: String,
    /// User rights level
    pub rights: i16,
    /// JWT claims
    pub claims: Claims,
}

impl AuthenticatedUser {
    /// Create from JWT claims
    pub fn from_claims(claims: Claims) -> Result<Self, ApiError> {
        let id = claims.user_id()?;
        Ok(Self {
            id,
            username: claims.username.clone(),
            rights: claims.rights,
            claims,
        })
    }

    /// Check if user is admin
    pub fn is_admin(&self) -> bool {
        self.rights >= 2
    }

    /// Check if user is moderator or higher
    pub fn is_moderator(&self) -> bool {
        self.rights >= 1
    }
}

/// Extractor for authenticated users (required authentication)
///
/// This extractor will return an error if no valid token is provided.
#[axum::async_trait]
impl FromRequestParts<ApiState> for AuthenticatedUser {
    type Rejection = ApiError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &ApiState,
    ) -> Result<Self, Self::Rejection> {
        // Try to extract the Authorization header
        let TypedHeader(Authorization(bearer)) = parts
            .extract::<TypedHeader<Authorization<Bearer>>>()
            .await
            .map_err(|_| ApiError::Unauthorized)?;

        // Validate the token
        let claims = state.auth.validate_token(bearer.token()).await?;

        // Ensure it's an access token
        if !claims.is_access_token() {
            return Err(ApiError::InvalidToken);
        }

        // Create authenticated user
        AuthenticatedUser::from_claims(claims)
    }
}

/// Optional authenticated user extractor
///
/// This extractor will return `None` if no token is provided,
/// but will error if an invalid token is provided.
#[derive(Debug, Clone)]
pub struct OptionalUser(pub Option<AuthenticatedUser>);

#[axum::async_trait]
impl FromRequestParts<ApiState> for OptionalUser {
    type Rejection = ApiError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &ApiState,
    ) -> Result<Self, Self::Rejection> {
        // Try to extract the Authorization header
        let auth_header = parts.extract::<TypedHeader<Authorization<Bearer>>>().await;

        match auth_header {
            Ok(TypedHeader(Authorization(bearer))) => {
                // Token provided, validate it
                let claims = state.auth.validate_token(bearer.token()).await?;

                if !claims.is_access_token() {
                    return Err(ApiError::InvalidToken);
                }

                let user = AuthenticatedUser::from_claims(claims)?;
                Ok(OptionalUser(Some(user)))
            }
            Err(_) => {
                // No token provided, that's okay for optional auth
                Ok(OptionalUser(None))
            }
        }
    }
}

/// Extract bearer token from request without validation
///
/// Useful for logout endpoint where we need the token but don't need full validation
pub async fn extract_bearer_token(parts: &mut Parts) -> Option<String> {
    parts
        .extract::<TypedHeader<Authorization<Bearer>>>()
        .await
        .ok()
        .map(|TypedHeader(Authorization(bearer))| bearer.token().to_string())
}

/// Admin-only middleware guard
#[derive(Debug, Clone)]
pub struct AdminUser(pub AuthenticatedUser);

#[axum::async_trait]
impl FromRequestParts<ApiState> for AdminUser {
    type Rejection = ApiError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &ApiState,
    ) -> Result<Self, Self::Rejection> {
        let user = AuthenticatedUser::from_request_parts(parts, state).await?;

        if !user.is_admin() {
            return Err(ApiError::Unauthorized);
        }

        Ok(AdminUser(user))
    }
}

/// Moderator or higher middleware guard
#[derive(Debug, Clone)]
pub struct ModeratorUser(pub AuthenticatedUser);

#[axum::async_trait]
impl FromRequestParts<ApiState> for ModeratorUser {
    type Rejection = ApiError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &ApiState,
    ) -> Result<Self, Self::Rejection> {
        let user = AuthenticatedUser::from_request_parts(parts, state).await?;

        if !user.is_moderator() {
            return Err(ApiError::Unauthorized);
        }

        Ok(ModeratorUser(user))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_claims_new_access() {
        let user_id = Uuid::new_v4();
        let claims = Claims::new_access(user_id, "testuser", 0, 3600);

        assert_eq!(claims.sub, user_id.to_string());
        assert_eq!(claims.username, "testuser");
        assert_eq!(claims.rights, 0);
        assert!(claims.is_access_token());
        assert!(!claims.is_refresh_token());
        assert!(!claims.is_expired());
    }

    #[test]
    fn test_claims_new_refresh() {
        let user_id = Uuid::new_v4();
        let claims = Claims::new_refresh(user_id, "testuser", 0, 86400 * 7);

        assert!(claims.is_refresh_token());
        assert!(!claims.is_access_token());
    }

    #[test]
    fn test_claims_expired() {
        let user_id = Uuid::new_v4();
        let mut claims = Claims::new_access(user_id, "testuser", 0, 3600);

        // Set expiration to the past
        claims.exp = chrono::Utc::now().timestamp() - 100;

        assert!(claims.is_expired());
    }

    #[test]
    fn test_claims_user_id() {
        let user_id = Uuid::new_v4();
        let claims = Claims::new_access(user_id, "testuser", 0, 3600);

        assert_eq!(claims.user_id().unwrap(), user_id);
    }

    #[test]
    fn test_authenticated_user_from_claims() {
        let user_id = Uuid::new_v4();
        let claims = Claims::new_access(user_id, "testuser", 2, 3600);

        let user = AuthenticatedUser::from_claims(claims).unwrap();

        assert_eq!(user.id, user_id);
        assert_eq!(user.username, "testuser");
        assert_eq!(user.rights, 2);
        assert!(user.is_admin());
        assert!(user.is_moderator());
    }

    #[test]
    fn test_user_permissions() {
        let user_id = Uuid::new_v4();

        // Normal user
        let claims = Claims::new_access(user_id, "normal", 0, 3600);
        let user = AuthenticatedUser::from_claims(claims).unwrap();
        assert!(!user.is_admin());
        assert!(!user.is_moderator());

        // Moderator
        let claims = Claims::new_access(user_id, "mod", 1, 3600);
        let user = AuthenticatedUser::from_claims(claims).unwrap();
        assert!(!user.is_admin());
        assert!(user.is_moderator());

        // Admin
        let claims = Claims::new_access(user_id, "admin", 2, 3600);
        let user = AuthenticatedUser::from_claims(claims).unwrap();
        assert!(user.is_admin());
        assert!(user.is_moderator());
    }
}
