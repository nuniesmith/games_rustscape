//! Authentication API endpoint handlers
//!
//! This module provides HTTP handlers for:
//! - POST /api/v1/auth/register - Create new user account
//! - POST /api/v1/auth/login - Authenticate and get JWT token
//! - POST /api/v1/auth/logout - Invalidate session
//! - GET /api/v1/auth/session - Validate current session
//! - GET /api/v1/auth/check-username - Check username availability
//! - POST /api/v1/auth/refresh - Refresh access token

use axum::{
    extract::{Query, State},
    http::{HeaderMap, StatusCode},
    Json,
};
use chrono::Utc;
use serde::Deserialize;
use tracing::{info, warn};
use validator::Validate;

use crate::api::error::ApiError;
use crate::api::middleware::AuthenticatedUser;
use crate::api::response::{
    AuthResponse, RefreshResponse, SessionInfo, UserInfo, UsernameCheckResponse,
};
use crate::api::ApiState;

use super::password::{hash_password, verify_password, PasswordRequirements};
use super::queries;

/// Registration request body
#[derive(Debug, Deserialize, Validate)]
pub struct RegisterRequest {
    #[validate(length(min = 1, max = 12, message = "Username must be 1-12 characters"))]
    #[validate(regex(
        path = "USERNAME_REGEX",
        message = "Username can only contain letters, numbers, and underscores"
    ))]
    pub username: String,

    #[validate(email(message = "Please enter a valid email address"))]
    pub email: String,

    #[validate(length(min = 6, message = "Password must be at least 6 characters"))]
    pub password: String,
}

use once_cell::sync::Lazy;

static USERNAME_REGEX: Lazy<regex::Regex> =
    Lazy::new(|| regex::Regex::new(r"^[a-zA-Z0-9_]+$").unwrap());

/// Login request body
#[derive(Debug, Deserialize)]
pub struct LoginRequest {
    pub username: String,
    pub password: String,
    #[serde(default)]
    pub remember: bool,
}

/// Refresh token request body
#[derive(Debug, Deserialize)]
pub struct RefreshRequest {
    pub refresh_token: String,
}

/// Username check query parameters
#[derive(Debug, Deserialize)]
pub struct UsernameCheckQuery {
    pub username: String,
}

/// Extract client IP from headers
fn extract_client_ip(headers: &HeaderMap) -> Option<String> {
    // Check for forwarded headers first (from nginx/proxy)
    if let Some(forwarded) = headers.get("x-forwarded-for") {
        if let Ok(value) = forwarded.to_str() {
            // Take the first IP in the list
            return value.split(',').next().map(|s| s.trim().to_string());
        }
    }

    if let Some(real_ip) = headers.get("x-real-ip") {
        if let Ok(value) = real_ip.to_str() {
            return Some(value.to_string());
        }
    }

    None
}

/// Extract user agent from headers
fn extract_user_agent(headers: &HeaderMap) -> Option<String> {
    headers
        .get("user-agent")
        .and_then(|v| v.to_str().ok())
        .map(|s| s.to_string())
}

/// Convert UserRecord to UserInfo response
fn user_record_to_info(user: &queries::UserRecord) -> UserInfo {
    UserInfo {
        id: user.id.to_string(),
        username: user.username.clone(),
        email: user.email.clone(),
        rights: user.rights,
        is_member: user.is_member,
        created_at: user.created_at.to_rfc3339(),
        last_login_at: user.last_login_at.map(|dt| dt.to_rfc3339()),
    }
}

/// POST /api/v1/auth/register
///
/// Create a new user account
pub async fn register(
    State(state): State<ApiState>,
    headers: HeaderMap,
    Json(payload): Json<RegisterRequest>,
) -> Result<Json<AuthResponse>, ApiError> {
    // Check if registration is enabled
    if !state.auth.registration_enabled {
        return Err(ApiError::InvalidInput(
            "Registration is currently disabled".to_string(),
        ));
    }

    // Validate input
    payload.validate()?;

    // Additional password validation
    let password_reqs = PasswordRequirements {
        min_length: state.auth.min_password_length,
        ..Default::default()
    };

    if let Err(errors) = password_reqs.validate(&payload.password) {
        return Err(ApiError::InvalidInput(errors.join(", ")));
    }

    // Check username availability
    if !queries::is_username_available(&state.auth.db, &payload.username).await? {
        return Err(ApiError::UsernameTaken);
    }

    // Check email availability
    if !queries::is_email_available(&state.auth.db, &payload.email).await? {
        return Err(ApiError::EmailTaken);
    }

    // Hash the password
    let password_hash = hash_password(&payload.password, state.auth.bcrypt_cost)?;

    // Create the user
    let user = queries::create_user(
        &state.auth.db,
        &payload.username,
        &payload.email,
        &password_hash,
    )
    .await?;

    info!(
        user_id = %user.id,
        username = %user.username,
        "New user registered"
    );

    // Log the registration in audit
    let client_ip = extract_client_ip(&headers);
    queries::log_audit(
        &state.auth.db,
        Some(user.id),
        "user_registered",
        Some("user"),
        Some(user.id),
        None,
        None,
        client_ip.as_deref(),
    )
    .await?;

    // Generate access token
    let access_token = state
        .auth
        .generate_access_token(user.id, &user.username, user.rights)?;

    // Store session in Redis
    state
        .auth
        .store_session(user.id, &access_token, state.auth.access_token_expiry())
        .await?;

    // Update last login
    queries::update_last_login(&state.auth.db, user.id).await?;

    let user_info = user_record_to_info(&user);
    let expires_in = state.auth.access_token_expiry();

    Ok(Json(AuthResponse::registered(
        access_token,
        user_info,
        expires_in,
    )))
}

/// POST /api/v1/auth/login
///
/// Authenticate a user and return a JWT token
pub async fn login(
    State(state): State<ApiState>,
    headers: HeaderMap,
    Json(payload): Json<LoginRequest>,
) -> Result<Json<AuthResponse>, ApiError> {
    let client_ip = extract_client_ip(&headers);
    let user_agent = extract_user_agent(&headers);

    // Find user by username
    let user = queries::find_user_by_username(&state.auth.db, &payload.username)
        .await?
        .ok_or(ApiError::InvalidCredentials)?;

    // Check if account is locked
    if user.is_locked() {
        warn!(
            user_id = %user.id,
            username = %user.username,
            "Login attempt on locked account"
        );

        // Record failed attempt
        queries::record_login_attempt(
            &state.auth.db,
            user.id,
            client_ip.as_deref(),
            user_agent.as_deref(),
            false,
            Some("account_locked"),
        )
        .await?;

        return Err(ApiError::AccountLocked);
    }

    // Check if account is banned
    if user.is_currently_banned() {
        warn!(
            user_id = %user.id,
            username = %user.username,
            "Login attempt on banned account"
        );

        queries::record_login_attempt(
            &state.auth.db,
            user.id,
            client_ip.as_deref(),
            user_agent.as_deref(),
            false,
            Some("account_banned"),
        )
        .await?;

        return Err(ApiError::AccountDisabled);
    }

    // Verify password
    let password_valid = verify_password(&payload.password, &user.password_hash)?;

    if !password_valid {
        warn!(
            user_id = %user.id,
            username = %user.username,
            "Failed login attempt - invalid password"
        );

        // Increment failed attempts
        queries::increment_failed_login_attempts(
            &state.auth.db,
            user.id,
            state.auth.max_login_attempts,
            state.auth.lockout_duration,
        )
        .await?;

        // Record failed attempt
        queries::record_login_attempt(
            &state.auth.db,
            user.id,
            client_ip.as_deref(),
            user_agent.as_deref(),
            false,
            Some("invalid_password"),
        )
        .await?;

        return Err(ApiError::InvalidCredentials);
    }

    // Success! Generate tokens
    let access_token = state
        .auth
        .generate_access_token(user.id, &user.username, user.rights)?;

    // Generate refresh token if "remember me" is checked
    let refresh_token = if payload.remember {
        Some(
            state
                .auth
                .generate_refresh_token(user.id, &user.username, user.rights)?,
        )
    } else {
        None
    };

    // Store session in Redis
    state
        .auth
        .store_session(user.id, &access_token, state.auth.access_token_expiry())
        .await?;

    // Create session in database
    let expires_at = Utc::now() + chrono::Duration::seconds(state.auth.access_token_expiry());
    queries::create_session(
        &state.auth.db,
        user.id,
        &access_token,
        client_ip.as_deref(),
        user_agent.as_deref(),
        None, // world_id - set when they actually join a world
        expires_at,
    )
    .await?;

    // Update last login and reset failed attempts
    queries::update_last_login(&state.auth.db, user.id).await?;

    // Record successful login
    queries::record_login_attempt(
        &state.auth.db,
        user.id,
        client_ip.as_deref(),
        user_agent.as_deref(),
        true,
        None,
    )
    .await?;

    info!(
        user_id = %user.id,
        username = %user.username,
        "User logged in successfully"
    );

    let user_info = user_record_to_info(&user);
    let expires_in = state.auth.access_token_expiry();

    let response = if let Some(refresh) = refresh_token {
        AuthResponse::success_with_refresh(access_token, refresh, user_info, expires_in)
    } else {
        AuthResponse::success(access_token, user_info, expires_in)
    };

    Ok(Json(response))
}

/// POST /api/v1/auth/logout
///
/// Invalidate the current session
pub async fn logout(
    State(state): State<ApiState>,
    headers: HeaderMap,
) -> Result<StatusCode, ApiError> {
    // Extract the token from the Authorization header
    if let Some(auth_header) = headers.get("authorization") {
        if let Ok(auth_str) = auth_header.to_str() {
            if let Some(token) = auth_str.strip_prefix("Bearer ") {
                // Try to decode the token to get user info (even if expired)
                if let Ok(claims) = super::jwt::decode_token_unsafe(token) {
                    if let Ok(user_id) = claims.user_id() {
                        // Remove session from Redis
                        state.auth.remove_session(user_id).await?;

                        // Blacklist the token
                        let remaining_time = claims.exp - Utc::now().timestamp();
                        if remaining_time > 0 {
                            state.auth.blacklist_token(token, remaining_time).await?;
                        }

                        // Invalidate session in database
                        queries::invalidate_session(&state.auth.db, token).await?;

                        info!(user_id = %user_id, "User logged out");
                    }
                }
            }
        }
    }

    Ok(StatusCode::NO_CONTENT)
}

/// GET /api/v1/auth/session
///
/// Validate the current session and return user info
pub async fn get_session(
    State(state): State<ApiState>,
    user: AuthenticatedUser,
) -> Result<Json<SessionInfo>, ApiError> {
    // Fetch fresh user data from database
    let user_record = queries::find_user_by_id(&state.auth.db, user.id)
        .await?
        .ok_or(ApiError::NotFound("User".to_string()))?;

    // Check if account is still active
    if user_record.is_currently_banned() {
        return Err(ApiError::AccountDisabled);
    }

    let user_info = user_record_to_info(&user_record);
    let expires_at = chrono::DateTime::from_timestamp(user.claims.exp, 0)
        .map(|dt| dt.to_rfc3339())
        .unwrap_or_default();

    Ok(Json(SessionInfo::valid(user_info, expires_at)))
}

/// GET /api/v1/auth/check-username
///
/// Check if a username is available
pub async fn check_username(
    State(state): State<ApiState>,
    Query(query): Query<UsernameCheckQuery>,
) -> Result<Json<UsernameCheckResponse>, ApiError> {
    let username = query.username.trim();

    // Validate username format
    if username.is_empty() {
        return Ok(Json(UsernameCheckResponse::unavailable(
            "Username is required",
        )));
    }

    if username.len() > 12 {
        return Ok(Json(UsernameCheckResponse::unavailable(
            "Username must be 12 characters or less",
        )));
    }

    if !USERNAME_REGEX.is_match(username) {
        return Ok(Json(UsernameCheckResponse::unavailable(
            "Username can only contain letters, numbers, and underscores",
        )));
    }

    // Check availability
    let available = queries::is_username_available(&state.auth.db, username).await?;

    if available {
        Ok(Json(UsernameCheckResponse::available()))
    } else {
        Ok(Json(UsernameCheckResponse::unavailable(
            "Username is already taken",
        )))
    }
}

/// POST /api/v1/auth/refresh
///
/// Refresh an access token using a refresh token
pub async fn refresh_token(
    State(state): State<ApiState>,
    Json(payload): Json<RefreshRequest>,
) -> Result<Json<RefreshResponse>, ApiError> {
    // Validate the refresh token
    let claims = state.auth.validate_token(&payload.refresh_token).await?;

    // Ensure it's a refresh token
    if !claims.is_refresh_token() {
        return Err(ApiError::InvalidToken);
    }

    let user_id = claims.user_id()?;

    // Verify user still exists and is active
    let user = queries::find_user_by_id(&state.auth.db, user_id)
        .await?
        .ok_or(ApiError::NotFound("User".to_string()))?;

    if user.is_currently_banned() {
        return Err(ApiError::AccountDisabled);
    }

    // Generate new access token
    let new_access_token =
        state
            .auth
            .generate_access_token(user.id, &user.username, user.rights)?;

    // Store new session
    state
        .auth
        .store_session(user.id, &new_access_token, state.auth.access_token_expiry())
        .await?;

    info!(user_id = %user.id, "Token refreshed");

    Ok(Json(RefreshResponse::success(
        new_access_token,
        state.auth.access_token_expiry(),
    )))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_username_regex() {
        assert!(USERNAME_REGEX.is_match("testuser"));
        assert!(USERNAME_REGEX.is_match("Test_User123"));
        assert!(USERNAME_REGEX.is_match("a"));
        assert!(!USERNAME_REGEX.is_match("test user"));
        assert!(!USERNAME_REGEX.is_match("test-user"));
        assert!(!USERNAME_REGEX.is_match("test@user"));
        assert!(!USERNAME_REGEX.is_match(""));
    }

    #[test]
    fn test_extract_client_ip_forwarded() {
        let mut headers = HeaderMap::new();
        headers.insert("x-forwarded-for", "192.168.1.1, 10.0.0.1".parse().unwrap());

        let ip = extract_client_ip(&headers);
        assert_eq!(ip, Some("192.168.1.1".to_string()));
    }

    #[test]
    fn test_extract_client_ip_real_ip() {
        let mut headers = HeaderMap::new();
        headers.insert("x-real-ip", "192.168.1.100".parse().unwrap());

        let ip = extract_client_ip(&headers);
        assert_eq!(ip, Some("192.168.1.100".to_string()));
    }

    #[test]
    fn test_extract_client_ip_none() {
        let headers = HeaderMap::new();
        let ip = extract_client_ip(&headers);
        assert!(ip.is_none());
    }
}
