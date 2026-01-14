//! Database query functions for authentication operations
//!
//! This module provides PostgreSQL query functions for:
//! - User registration and lookup
//! - Login attempt tracking
//! - Session management
//! - Password updates

use chrono::{DateTime, Utc};
use sqlx::PgPool;
use uuid::Uuid;

use crate::api::error::ApiError;

/// User record from the database
#[derive(Debug, Clone, sqlx::FromRow)]
#[allow(dead_code)]
pub struct UserRecord {
    pub id: Uuid,
    pub username: String,
    pub username_lower: String,
    pub email: String,
    pub email_verified: bool,
    pub password_hash: String,
    pub rights: i16,
    pub is_member: bool,
    pub is_banned: bool,
    pub is_muted: bool,
    pub ban_expires_at: Option<DateTime<Utc>>,
    pub mute_expires_at: Option<DateTime<Utc>>,
    pub ban_reason: Option<String>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub last_login_at: Option<DateTime<Utc>>,
    pub email_verified_at: Option<DateTime<Utc>>,
    pub failed_login_attempts: i32,
    pub locked_until: Option<DateTime<Utc>>,
    pub password_changed_at: Option<DateTime<Utc>>,
}

impl UserRecord {
    /// Check if the account is currently locked
    pub fn is_locked(&self) -> bool {
        if let Some(locked_until) = self.locked_until {
            locked_until > Utc::now()
        } else {
            false
        }
    }

    /// Check if the account is currently banned
    pub fn is_currently_banned(&self) -> bool {
        if !self.is_banned {
            return false;
        }
        if let Some(ban_expires_at) = self.ban_expires_at {
            ban_expires_at > Utc::now()
        } else {
            true // Permanent ban
        }
    }
}

/// Find a user by username (case-insensitive)
pub async fn find_user_by_username(
    pool: &PgPool,
    username: &str,
) -> Result<Option<UserRecord>, ApiError> {
    let username_lower = username.to_lowercase();

    let user = sqlx::query_as::<_, UserRecord>(
        r#"
        SELECT
            id, username, username_lower, email, email_verified,
            password_hash, rights, is_member, is_banned, is_muted,
            ban_expires_at, mute_expires_at, ban_reason,
            created_at, updated_at, last_login_at, email_verified_at,
            failed_login_attempts, locked_until, password_changed_at
        FROM users
        WHERE username_lower = $1
        "#,
    )
    .bind(&username_lower)
    .fetch_optional(pool)
    .await?;

    Ok(user)
}

/// Find a user by ID
pub async fn find_user_by_id(pool: &PgPool, id: Uuid) -> Result<Option<UserRecord>, ApiError> {
    let user = sqlx::query_as::<_, UserRecord>(
        r#"
        SELECT
            id, username, username_lower, email, email_verified,
            password_hash, rights, is_member, is_banned, is_muted,
            ban_expires_at, mute_expires_at, ban_reason,
            created_at, updated_at, last_login_at, email_verified_at,
            failed_login_attempts, locked_until, password_changed_at
        FROM users
        WHERE id = $1
        "#,
    )
    .bind(id)
    .fetch_optional(pool)
    .await?;

    Ok(user)
}

/// Find a user by email
#[allow(dead_code)]
pub async fn find_user_by_email(
    pool: &PgPool,
    email: &str,
) -> Result<Option<UserRecord>, ApiError> {
    let email_lower = email.to_lowercase();

    let user = sqlx::query_as::<_, UserRecord>(
        r#"
        SELECT
            id, username, username_lower, email, email_verified,
            password_hash, rights, is_member, is_banned, is_muted,
            ban_expires_at, mute_expires_at, ban_reason,
            created_at, updated_at, last_login_at, email_verified_at,
            failed_login_attempts, locked_until, password_changed_at
        FROM users
        WHERE LOWER(email) = $1
        "#,
    )
    .bind(&email_lower)
    .fetch_optional(pool)
    .await?;

    Ok(user)
}

/// Check if a username is available
pub async fn is_username_available(pool: &PgPool, username: &str) -> Result<bool, ApiError> {
    let username_lower = username.to_lowercase();

    let count: (i64,) = sqlx::query_as(
        r#"
        SELECT COUNT(*) FROM users WHERE username_lower = $1
        "#,
    )
    .bind(&username_lower)
    .fetch_one(pool)
    .await?;

    Ok(count.0 == 0)
}

/// Check if an email is available
pub async fn is_email_available(pool: &PgPool, email: &str) -> Result<bool, ApiError> {
    let email_lower = email.to_lowercase();

    let count: (i64,) = sqlx::query_as(
        r#"
        SELECT COUNT(*) FROM users WHERE LOWER(email) = $1
        "#,
    )
    .bind(&email_lower)
    .fetch_one(pool)
    .await?;

    Ok(count.0 == 0)
}

/// Create a new user
pub async fn create_user(
    pool: &PgPool,
    username: &str,
    email: &str,
    password_hash: &str,
) -> Result<UserRecord, ApiError> {
    let username_lower = username.to_lowercase();
    let email_lower = email.to_lowercase();

    let user = sqlx::query_as::<_, UserRecord>(
        r#"
        INSERT INTO users (username, username_lower, email, password_hash)
        VALUES ($1, $2, $3, $4)
        RETURNING
            id, username, username_lower, email, email_verified,
            password_hash, rights, is_member, is_banned, is_muted,
            ban_expires_at, mute_expires_at, ban_reason,
            created_at, updated_at, last_login_at, email_verified_at,
            failed_login_attempts, locked_until, password_changed_at
        "#,
    )
    .bind(username)
    .bind(&username_lower)
    .bind(&email_lower)
    .bind(password_hash)
    .fetch_one(pool)
    .await?;

    Ok(user)
}

/// Update last login timestamp
pub async fn update_last_login(pool: &PgPool, user_id: Uuid) -> Result<(), ApiError> {
    sqlx::query(
        r#"
        UPDATE users
        SET last_login_at = NOW(), failed_login_attempts = 0, locked_until = NULL
        WHERE id = $1
        "#,
    )
    .bind(user_id)
    .execute(pool)
    .await?;

    Ok(())
}

/// Increment failed login attempts
pub async fn increment_failed_login_attempts(
    pool: &PgPool,
    user_id: Uuid,
    max_attempts: i32,
    lockout_duration_seconds: i64,
) -> Result<(), ApiError> {
    sqlx::query(
        r#"
        UPDATE users
        SET
            failed_login_attempts = failed_login_attempts + 1,
            locked_until = CASE
                WHEN failed_login_attempts + 1 >= $2
                THEN NOW() + INTERVAL '1 second' * $3
                ELSE locked_until
            END
        WHERE id = $1
        "#,
    )
    .bind(user_id)
    .bind(max_attempts)
    .bind(lockout_duration_seconds)
    .execute(pool)
    .await?;

    Ok(())
}

/// Reset failed login attempts
#[allow(dead_code)]
pub async fn reset_failed_login_attempts(pool: &PgPool, user_id: Uuid) -> Result<(), ApiError> {
    sqlx::query(
        r#"
        UPDATE users
        SET failed_login_attempts = 0, locked_until = NULL
        WHERE id = $1
        "#,
    )
    .bind(user_id)
    .execute(pool)
    .await?;

    Ok(())
}

/// Update a user's password
#[allow(dead_code)]
pub async fn update_password(
    pool: &PgPool,
    user_id: Uuid,
    password_hash: &str,
) -> Result<(), ApiError> {
    sqlx::query(
        r#"
        UPDATE users
        SET password_hash = $2, password_changed_at = NOW()
        WHERE id = $1
        "#,
    )
    .bind(user_id)
    .bind(password_hash)
    .execute(pool)
    .await?;

    Ok(())
}

/// Record a login attempt in history
pub async fn record_login_attempt(
    pool: &PgPool,
    user_id: Uuid,
    ip_address: Option<&str>,
    user_agent: Option<&str>,
    success: bool,
    failure_reason: Option<&str>,
) -> Result<(), ApiError> {
    sqlx::query(
        r#"
        INSERT INTO login_history (user_id, ip_address, user_agent, success, failure_reason)
        VALUES ($1, $2::inet, $3, $4, $5)
        "#,
    )
    .bind(user_id)
    .bind(ip_address)
    .bind(user_agent)
    .bind(success)
    .bind(failure_reason)
    .execute(pool)
    .await?;

    Ok(())
}

/// Create a new session in the database
pub async fn create_session(
    pool: &PgPool,
    user_id: Uuid,
    session_token: &str,
    ip_address: Option<&str>,
    user_agent: Option<&str>,
    world_id: Option<i16>,
    expires_at: DateTime<Utc>,
) -> Result<Uuid, ApiError> {
    let session: (Uuid,) = sqlx::query_as(
        r#"
        INSERT INTO sessions (user_id, session_token, ip_address, user_agent, world_id, expires_at)
        VALUES ($1, $2, $3::inet, $4, $5, $6)
        RETURNING id
        "#,
    )
    .bind(user_id)
    .bind(session_token)
    .bind(ip_address)
    .bind(user_agent)
    .bind(world_id)
    .bind(expires_at)
    .fetch_one(pool)
    .await?;

    Ok(session.0)
}

/// Invalidate a session
pub async fn invalidate_session(pool: &PgPool, session_token: &str) -> Result<(), ApiError> {
    sqlx::query(
        r#"
        UPDATE sessions
        SET is_active = FALSE
        WHERE session_token = $1
        "#,
    )
    .bind(session_token)
    .execute(pool)
    .await?;

    Ok(())
}

/// Invalidate all sessions for a user
#[allow(dead_code)]
pub async fn invalidate_all_sessions(pool: &PgPool, user_id: Uuid) -> Result<u64, ApiError> {
    let result = sqlx::query(
        r#"
        UPDATE sessions
        SET is_active = FALSE
        WHERE user_id = $1 AND is_active = TRUE
        "#,
    )
    .bind(user_id)
    .execute(pool)
    .await?;

    Ok(result.rows_affected())
}

/// Update session last activity
#[allow(dead_code)]
pub async fn update_session_activity(pool: &PgPool, session_token: &str) -> Result<(), ApiError> {
    sqlx::query(
        r#"
        UPDATE sessions
        SET last_activity_at = NOW()
        WHERE session_token = $1 AND is_active = TRUE
        "#,
    )
    .bind(session_token)
    .execute(pool)
    .await?;

    Ok(())
}

/// Clean up expired sessions
#[allow(dead_code)]
pub async fn cleanup_expired_sessions(pool: &PgPool) -> Result<u64, ApiError> {
    let result = sqlx::query(
        r#"
        DELETE FROM sessions
        WHERE expires_at < NOW() OR (is_active = FALSE AND last_activity_at < NOW() - INTERVAL '1 day')
        "#,
    )
    .execute(pool)
    .await?;

    Ok(result.rows_affected())
}

/// Log an audit event
pub async fn log_audit(
    pool: &PgPool,
    user_id: Option<Uuid>,
    action: &str,
    entity_type: Option<&str>,
    entity_id: Option<Uuid>,
    old_value: Option<serde_json::Value>,
    new_value: Option<serde_json::Value>,
    ip_address: Option<&str>,
) -> Result<(), ApiError> {
    sqlx::query(
        r#"
        INSERT INTO audit_log (user_id, action, entity_type, entity_id, old_value, new_value, ip_address)
        VALUES ($1, $2, $3, $4, $5, $6, $7::inet)
        "#,
    )
    .bind(user_id)
    .bind(action)
    .bind(entity_type)
    .bind(entity_id)
    .bind(old_value)
    .bind(new_value)
    .bind(ip_address)
    .execute(pool)
    .await?;

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_user_record_is_locked() {
        // Not locked
        let user = create_test_user(None);
        assert!(!user.is_locked());

        // Locked until future
        let user = create_test_user(Some(Utc::now() + chrono::Duration::hours(1)));
        assert!(user.is_locked());

        // Lock expired
        let user = create_test_user(Some(Utc::now() - chrono::Duration::hours(1)));
        assert!(!user.is_locked());
    }

    fn create_test_user(locked_until: Option<DateTime<Utc>>) -> UserRecord {
        UserRecord {
            id: Uuid::new_v4(),
            username: "testuser".to_string(),
            username_lower: "testuser".to_string(),
            email: "test@example.com".to_string(),
            email_verified: true,
            password_hash: "hash".to_string(),
            rights: 0,
            is_member: false,
            is_banned: false,
            is_muted: false,
            ban_expires_at: None,
            mute_expires_at: None,
            ban_reason: None,
            created_at: Utc::now(),
            updated_at: Utc::now(),
            last_login_at: None,
            email_verified_at: None,
            failed_login_attempts: 0,
            locked_until,
            password_changed_at: None,
        }
    }
}
