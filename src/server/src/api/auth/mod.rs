//! Authentication API module
//!
//! This module provides HTTP endpoints for user authentication:
//! - POST /api/v1/auth/register - Create new user account
//! - POST /api/v1/auth/login - Authenticate and get JWT token
//! - POST /api/v1/auth/logout - Invalidate session
//! - GET /api/v1/auth/session - Validate current session
//! - GET /api/v1/auth/check-username - Check username availability
//! - POST /api/v1/auth/refresh - Refresh access token

pub mod handlers;
mod jwt;
mod password;
mod queries;

use deadpool_redis::{Config as RedisConfig, Pool as RedisPool, Runtime};
use jsonwebtoken::{DecodingKey, EncodingKey};
use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;
use tracing::{error, info};

use crate::api::error::ApiError;
use crate::api::middleware::Claims;
use crate::config::ServerConfig;

/// JWT configuration
#[derive(Clone)]
pub struct JwtConfig {
    /// Secret key for signing tokens
    pub secret: String,
    /// Access token expiration time in seconds
    pub access_token_expiry: i64,
    /// Refresh token expiration time in seconds
    pub refresh_token_expiry: i64,
    /// Token issuer
    pub issuer: String,
}

impl Default for JwtConfig {
    fn default() -> Self {
        Self {
            // In production, use a strong random secret from environment
            secret: std::env::var("RUSTSCAPE_JWT_SECRET")
                .unwrap_or_else(|_| "rustscape-dev-secret-change-in-production".to_string()),
            access_token_expiry: 86400,   // 24 hours
            refresh_token_expiry: 604800, // 7 days
            issuer: "rustscape".to_string(),
        }
    }
}

/// Authentication service state
pub struct AuthState {
    /// PostgreSQL connection pool
    pub db: PgPool,
    /// Redis connection pool
    pub redis: RedisPool,
    /// JWT configuration
    pub jwt_config: JwtConfig,
    /// JWT encoding key
    encoding_key: EncodingKey,
    /// JWT decoding key
    decoding_key: DecodingKey,
    /// Whether to allow new registrations
    pub registration_enabled: bool,
    /// Minimum password length
    pub min_password_length: usize,
    /// Maximum login attempts before lockout
    pub max_login_attempts: i32,
    /// Lockout duration in seconds
    pub lockout_duration: i64,
    /// Bcrypt cost factor
    pub bcrypt_cost: u32,
}

impl AuthState {
    /// Create a new auth state from server configuration
    pub async fn new(config: &ServerConfig) -> Result<Self, ApiError> {
        // Create PostgreSQL connection pool
        let database_url = format!(
            "postgres://{}:{}@{}:{}/{}",
            config.database.username,
            config.database.password,
            config.database.host,
            config.database.port,
            config.database.database
        );

        let db = PgPoolOptions::new()
            .max_connections(config.database.pool_size)
            .connect(&database_url)
            .await
            .map_err(|e| {
                error!("Failed to connect to PostgreSQL: {}", e);
                ApiError::DatabaseError(format!("Failed to connect to database: {}", e))
            })?;

        info!(
            "Connected to PostgreSQL at {}:{}",
            config.database.host, config.database.port
        );

        // Create Redis connection pool
        let redis_url = format!(
            "redis://{}:{}",
            std::env::var("RUSTSCAPE_REDIS_HOST").unwrap_or_else(|_| "localhost".to_string()),
            std::env::var("RUSTSCAPE_REDIS_PORT").unwrap_or_else(|_| "6379".to_string())
        );

        let redis_config = RedisConfig::from_url(&redis_url);
        let redis = redis_config
            .create_pool(Some(Runtime::Tokio1))
            .map_err(|e| {
                error!("Failed to create Redis pool: {}", e);
                ApiError::RedisError(format!("Failed to create Redis pool: {}", e))
            })?;

        info!("Redis pool created for {}", redis_url);

        // Create JWT config
        let jwt_config = JwtConfig::default();
        let encoding_key = EncodingKey::from_secret(jwt_config.secret.as_bytes());
        let decoding_key = DecodingKey::from_secret(jwt_config.secret.as_bytes());

        // Get auth config from environment or use defaults
        let registration_enabled = std::env::var("RUSTSCAPE_AUTH_REGISTRATION_ENABLED")
            .map(|v| v == "true" || v == "1")
            .unwrap_or(true);
        let min_password_length = std::env::var("RUSTSCAPE_AUTH_MIN_PASSWORD_LENGTH")
            .ok()
            .and_then(|v| v.parse().ok())
            .unwrap_or(6);
        let max_login_attempts = std::env::var("RUSTSCAPE_AUTH_MAX_LOGIN_ATTEMPTS")
            .ok()
            .and_then(|v| v.parse().ok())
            .unwrap_or(5);
        let lockout_duration = std::env::var("RUSTSCAPE_AUTH_LOCKOUT_DURATION")
            .ok()
            .and_then(|v| v.parse().ok())
            .unwrap_or(900); // 15 minutes
        let bcrypt_cost = std::env::var("RUSTSCAPE_AUTH_BCRYPT_COST")
            .ok()
            .and_then(|v| v.parse().ok())
            .unwrap_or(12);

        Ok(Self {
            db,
            redis,
            jwt_config,
            encoding_key,
            decoding_key,
            registration_enabled,
            min_password_length,
            max_login_attempts,
            lockout_duration,
            bcrypt_cost,
        })
    }

    /// Check database connectivity
    pub async fn check_database(&self) -> Result<(), ApiError> {
        sqlx::query("SELECT 1")
            .fetch_one(&self.db)
            .await
            .map_err(|e| ApiError::DatabaseError(format!("Database health check failed: {}", e)))?;
        Ok(())
    }

    /// Check Redis connectivity
    pub async fn check_redis(&self) -> Result<(), ApiError> {
        let mut conn =
            self.redis.get().await.map_err(|e| {
                ApiError::RedisError(format!("Failed to get Redis connection: {}", e))
            })?;

        redis::cmd("PING")
            .query_async::<_, String>(&mut conn)
            .await
            .map_err(|e| ApiError::RedisError(format!("Redis health check failed: {}", e)))?;

        Ok(())
    }

    /// Generate a new access token for a user
    pub fn generate_access_token(
        &self,
        user_id: uuid::Uuid,
        username: &str,
        rights: i16,
    ) -> Result<String, ApiError> {
        jwt::generate_token(
            &self.encoding_key,
            user_id,
            username,
            rights,
            self.jwt_config.access_token_expiry,
            "access",
        )
    }

    /// Generate a new refresh token for a user
    pub fn generate_refresh_token(
        &self,
        user_id: uuid::Uuid,
        username: &str,
        rights: i16,
    ) -> Result<String, ApiError> {
        jwt::generate_token(
            &self.encoding_key,
            user_id,
            username,
            rights,
            self.jwt_config.refresh_token_expiry,
            "refresh",
        )
    }

    /// Validate a JWT token and return claims
    pub async fn validate_token(&self, token: &str) -> Result<Claims, ApiError> {
        let claims = jwt::validate_token(&self.decoding_key, token)?;

        // Optionally check if token is blacklisted in Redis
        let key = format!("rustscape:blacklist:{}", token);
        let mut conn =
            self.redis.get().await.map_err(|e| {
                ApiError::RedisError(format!("Failed to get Redis connection: {}", e))
            })?;

        let is_blacklisted: bool = redis::cmd("EXISTS")
            .arg(&key)
            .query_async(&mut conn)
            .await
            .unwrap_or(false);

        if is_blacklisted {
            return Err(ApiError::InvalidToken);
        }

        Ok(claims)
    }

    /// Blacklist a token (for logout)
    pub async fn blacklist_token(&self, token: &str, expires_in: i64) -> Result<(), ApiError> {
        let key = format!("rustscape:blacklist:{}", token);
        let mut conn =
            self.redis.get().await.map_err(|e| {
                ApiError::RedisError(format!("Failed to get Redis connection: {}", e))
            })?;

        redis::cmd("SETEX")
            .arg(&key)
            .arg(expires_in)
            .arg("1")
            .query_async::<_, ()>(&mut conn)
            .await
            .map_err(|e| ApiError::RedisError(format!("Failed to blacklist token: {}", e)))?;

        Ok(())
    }

    /// Store session in Redis for quick lookup
    pub async fn store_session(
        &self,
        user_id: uuid::Uuid,
        token: &str,
        expires_in: i64,
    ) -> Result<(), ApiError> {
        let key = format!("rustscape:session:{}", user_id);
        let mut conn =
            self.redis.get().await.map_err(|e| {
                ApiError::RedisError(format!("Failed to get Redis connection: {}", e))
            })?;

        redis::cmd("SETEX")
            .arg(&key)
            .arg(expires_in)
            .arg(token)
            .query_async::<_, ()>(&mut conn)
            .await
            .map_err(|e| ApiError::RedisError(format!("Failed to store session: {}", e)))?;

        Ok(())
    }

    /// Remove session from Redis
    pub async fn remove_session(&self, user_id: uuid::Uuid) -> Result<(), ApiError> {
        let key = format!("rustscape:session:{}", user_id);
        let mut conn =
            self.redis.get().await.map_err(|e| {
                ApiError::RedisError(format!("Failed to get Redis connection: {}", e))
            })?;

        redis::cmd("DEL")
            .arg(&key)
            .query_async::<_, ()>(&mut conn)
            .await
            .map_err(|e| ApiError::RedisError(format!("Failed to remove session: {}", e)))?;

        Ok(())
    }

    /// Get access token expiry in seconds
    pub fn access_token_expiry(&self) -> i64 {
        self.jwt_config.access_token_expiry
    }

    /// Get refresh token expiry in seconds
    pub fn refresh_token_expiry(&self) -> i64 {
        self.jwt_config.refresh_token_expiry
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_jwt_config_default() {
        let config = JwtConfig::default();
        assert_eq!(config.access_token_expiry, 86400);
        assert_eq!(config.refresh_token_expiry, 604800);
        assert_eq!(config.issuer, "rustscape");
    }
}
