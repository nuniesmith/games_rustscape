//! API response types for consistent JSON responses
//!
//! This module provides standardized response types for the REST API,
//! ensuring consistent response structures across all endpoints.

use serde::Serialize;

/// Standard API response wrapper
#[derive(Debug, Serialize)]
pub struct ApiResponse<T: Serialize> {
    /// Whether the request was successful
    pub success: bool,
    /// Response message
    pub message: String,
    /// Response data (if any)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<T>,
}

impl<T: Serialize> ApiResponse<T> {
    /// Create a successful response with data
    pub fn success(message: impl Into<String>, data: T) -> Self {
        Self {
            success: true,
            message: message.into(),
            data: Some(data),
        }
    }

    /// Create a successful response without data
    pub fn success_message(message: impl Into<String>) -> ApiResponse<()> {
        ApiResponse {
            success: true,
            message: message.into(),
            data: None,
        }
    }
}

/// Authentication response returned after login/register
#[derive(Debug, Serialize)]
pub struct AuthResponse {
    /// Whether authentication was successful
    pub success: bool,
    /// Response message
    pub message: String,
    /// JWT access token (if successful)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub token: Option<String>,
    /// Refresh token (if successful and remember me is enabled)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub refresh_token: Option<String>,
    /// User information (if successful)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub user: Option<UserInfo>,
    /// Token expiration time in seconds
    #[serde(skip_serializing_if = "Option::is_none")]
    pub expires_in: Option<i64>,
}

impl AuthResponse {
    /// Create a successful auth response
    pub fn success(token: String, user: UserInfo, expires_in: i64) -> Self {
        Self {
            success: true,
            message: "Authentication successful".to_string(),
            token: Some(token),
            refresh_token: None,
            user: Some(user),
            expires_in: Some(expires_in),
        }
    }

    /// Create a successful auth response with refresh token
    pub fn success_with_refresh(
        token: String,
        refresh_token: String,
        user: UserInfo,
        expires_in: i64,
    ) -> Self {
        Self {
            success: true,
            message: "Authentication successful".to_string(),
            token: Some(token),
            refresh_token: Some(refresh_token),
            user: Some(user),
            expires_in: Some(expires_in),
        }
    }

    /// Create a registration success response
    pub fn registered(token: String, user: UserInfo, expires_in: i64) -> Self {
        Self {
            success: true,
            message: "Registration successful".to_string(),
            token: Some(token),
            refresh_token: None,
            user: Some(user),
            expires_in: Some(expires_in),
        }
    }
}

/// User information returned in auth responses
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UserInfo {
    /// User's unique identifier
    pub id: String,
    /// Username
    pub username: String,
    /// Email address
    pub email: String,
    /// User rights level (0=normal, 1=mod, 2=admin)
    pub rights: i16,
    /// Whether the user is a member
    pub is_member: bool,
    /// Account creation timestamp (ISO 8601)
    pub created_at: String,
    /// Last login timestamp (ISO 8601)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub last_login_at: Option<String>,
}

/// Session validation response
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SessionInfo {
    /// Whether the session is valid
    pub valid: bool,
    /// User information (if session is valid)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub user: Option<UserInfo>,
    /// Session expiration timestamp (ISO 8601)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub expires_at: Option<String>,
}

impl SessionInfo {
    /// Create a valid session response
    pub fn valid(user: UserInfo, expires_at: String) -> Self {
        Self {
            valid: true,
            user: Some(user),
            expires_at: Some(expires_at),
        }
    }

    /// Create an invalid session response
    pub fn invalid() -> Self {
        Self {
            valid: false,
            user: None,
            expires_at: None,
        }
    }
}

/// Username availability check response
#[derive(Debug, Serialize)]
pub struct UsernameCheckResponse {
    /// Whether the username is available
    pub available: bool,
    /// Optional message explaining availability
    #[serde(skip_serializing_if = "Option::is_none")]
    pub message: Option<String>,
}

impl UsernameCheckResponse {
    /// Create an available response
    pub fn available() -> Self {
        Self {
            available: true,
            message: None,
        }
    }

    /// Create an unavailable response with reason
    pub fn unavailable(reason: impl Into<String>) -> Self {
        Self {
            available: false,
            message: Some(reason.into()),
        }
    }
}

/// Token refresh response
#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RefreshResponse {
    /// Whether refresh was successful
    pub success: bool,
    /// New access token
    #[serde(skip_serializing_if = "Option::is_none")]
    pub token: Option<String>,
    /// Token expiration time in seconds
    #[serde(skip_serializing_if = "Option::is_none")]
    pub expires_in: Option<i64>,
}

impl RefreshResponse {
    /// Create a successful refresh response
    pub fn success(token: String, expires_in: i64) -> Self {
        Self {
            success: true,
            token: Some(token),
            expires_in: Some(expires_in),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_api_response_success() {
        let response = ApiResponse::success("Test message", vec![1, 2, 3]);
        assert!(response.success);
        assert_eq!(response.message, "Test message");
        assert!(response.data.is_some());
    }

    #[test]
    fn test_session_info_valid() {
        let user = UserInfo {
            id: "123".to_string(),
            username: "testuser".to_string(),
            email: "test@example.com".to_string(),
            rights: 0,
            is_member: false,
            created_at: "2024-01-01T00:00:00Z".to_string(),
            last_login_at: None,
        };
        let session = SessionInfo::valid(user, "2024-01-02T00:00:00Z".to_string());
        assert!(session.valid);
        assert!(session.user.is_some());
    }

    #[test]
    fn test_session_info_invalid() {
        let session = SessionInfo::invalid();
        assert!(!session.valid);
        assert!(session.user.is_none());
    }

    #[test]
    fn test_username_check_response() {
        let available = UsernameCheckResponse::available();
        assert!(available.available);

        let unavailable = UsernameCheckResponse::unavailable("Username already taken");
        assert!(!unavailable.available);
        assert_eq!(
            unavailable.message,
            Some("Username already taken".to_string())
        );
    }

    #[test]
    fn test_auth_response_serialization() {
        let user = UserInfo {
            id: "uuid-123".to_string(),
            username: "testuser".to_string(),
            email: "test@example.com".to_string(),
            rights: 0,
            is_member: false,
            created_at: "2024-01-01T00:00:00Z".to_string(),
            last_login_at: Some("2024-01-15T12:00:00Z".to_string()),
        };
        let response = AuthResponse::success("jwt.token.here".to_string(), user, 86400);

        let json = serde_json::to_string(&response).unwrap();
        assert!(json.contains("\"success\":true"));
        assert!(json.contains("\"token\":\"jwt.token.here\""));
        assert!(json.contains("\"isMember\":false")); // camelCase
    }
}
