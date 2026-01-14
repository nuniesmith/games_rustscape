//! API error types and response handling
//!
//! This module provides standardized error types for the REST API,
//! ensuring consistent error responses across all endpoints.

use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde::Serialize;
use std::fmt;

/// API error response body
#[derive(Debug, Serialize)]
pub struct ErrorResponse {
    /// Whether the request was successful (always false for errors)
    pub success: bool,
    /// Error message
    pub message: String,
    /// Optional error code for programmatic handling
    #[serde(skip_serializing_if = "Option::is_none")]
    pub code: Option<String>,
    /// Optional field-level validation errors
    #[serde(skip_serializing_if = "Option::is_none")]
    pub errors: Option<std::collections::HashMap<String, String>>,
}

/// API error type
#[derive(Debug)]
pub enum ApiError {
    // Authentication errors
    InvalidCredentials,
    UsernameTaken,
    EmailTaken,
    InvalidToken,
    TokenExpired,
    AccountDisabled,
    AccountLocked,
    TooManyAttempts,
    Unauthorized,

    // Validation errors
    ValidationError(std::collections::HashMap<String, String>),
    InvalidInput(String),

    // Database errors
    DatabaseError(String),
    NotFound(String),

    // External service errors
    RedisError(String),

    // Server errors
    InternalError(String),
}

impl fmt::Display for ApiError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ApiError::InvalidCredentials => write!(f, "Invalid username or password"),
            ApiError::UsernameTaken => write!(f, "Username is already taken"),
            ApiError::EmailTaken => write!(f, "Email is already registered"),
            ApiError::InvalidToken => write!(f, "Invalid or malformed token"),
            ApiError::TokenExpired => write!(f, "Token has expired"),
            ApiError::AccountDisabled => write!(f, "Account is disabled"),
            ApiError::AccountLocked => write!(f, "Account is temporarily locked"),
            ApiError::TooManyAttempts => {
                write!(f, "Too many login attempts. Please try again later")
            }
            ApiError::Unauthorized => write!(f, "Authentication required"),
            ApiError::ValidationError(_) => write!(f, "Validation failed"),
            ApiError::InvalidInput(msg) => write!(f, "{}", msg),
            ApiError::DatabaseError(msg) => write!(f, "Database error: {}", msg),
            ApiError::NotFound(resource) => write!(f, "{} not found", resource),
            ApiError::RedisError(msg) => write!(f, "Cache error: {}", msg),
            ApiError::InternalError(msg) => write!(f, "Internal error: {}", msg),
        }
    }
}

impl std::error::Error for ApiError {}

impl ApiError {
    /// Get the HTTP status code for this error
    pub fn status_code(&self) -> StatusCode {
        match self {
            ApiError::InvalidCredentials => StatusCode::UNAUTHORIZED,
            ApiError::UsernameTaken => StatusCode::CONFLICT,
            ApiError::EmailTaken => StatusCode::CONFLICT,
            ApiError::InvalidToken => StatusCode::UNAUTHORIZED,
            ApiError::TokenExpired => StatusCode::UNAUTHORIZED,
            ApiError::AccountDisabled => StatusCode::FORBIDDEN,
            ApiError::AccountLocked => StatusCode::TOO_MANY_REQUESTS,
            ApiError::TooManyAttempts => StatusCode::TOO_MANY_REQUESTS,
            ApiError::Unauthorized => StatusCode::UNAUTHORIZED,
            ApiError::ValidationError(_) => StatusCode::BAD_REQUEST,
            ApiError::InvalidInput(_) => StatusCode::BAD_REQUEST,
            ApiError::DatabaseError(_) => StatusCode::INTERNAL_SERVER_ERROR,
            ApiError::NotFound(_) => StatusCode::NOT_FOUND,
            ApiError::RedisError(_) => StatusCode::INTERNAL_SERVER_ERROR,
            ApiError::InternalError(_) => StatusCode::INTERNAL_SERVER_ERROR,
        }
    }

    /// Get the error code for this error
    pub fn error_code(&self) -> &'static str {
        match self {
            ApiError::InvalidCredentials => "INVALID_CREDENTIALS",
            ApiError::UsernameTaken => "USERNAME_TAKEN",
            ApiError::EmailTaken => "EMAIL_TAKEN",
            ApiError::InvalidToken => "INVALID_TOKEN",
            ApiError::TokenExpired => "TOKEN_EXPIRED",
            ApiError::AccountDisabled => "ACCOUNT_DISABLED",
            ApiError::AccountLocked => "ACCOUNT_LOCKED",
            ApiError::TooManyAttempts => "TOO_MANY_ATTEMPTS",
            ApiError::Unauthorized => "UNAUTHORIZED",
            ApiError::ValidationError(_) => "VALIDATION_ERROR",
            ApiError::InvalidInput(_) => "INVALID_INPUT",
            ApiError::DatabaseError(_) => "DATABASE_ERROR",
            ApiError::NotFound(_) => "NOT_FOUND",
            ApiError::RedisError(_) => "CACHE_ERROR",
            ApiError::InternalError(_) => "INTERNAL_ERROR",
        }
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let status = self.status_code();
        let code = self.error_code().to_string();
        let message = self.to_string();

        let errors = match &self {
            ApiError::ValidationError(errs) => Some(errs.clone()),
            _ => None,
        };

        let body = ErrorResponse {
            success: false,
            message,
            code: Some(code),
            errors,
        };

        (status, Json(body)).into_response()
    }
}

// Conversion implementations for common error types

impl From<sqlx::Error> for ApiError {
    fn from(err: sqlx::Error) -> Self {
        tracing::error!("Database error: {:?}", err);

        match err {
            sqlx::Error::RowNotFound => ApiError::NotFound("Resource".to_string()),
            sqlx::Error::Database(db_err) => {
                // Check for unique constraint violations
                if let Some(code) = db_err.code() {
                    if code == "23505" {
                        // PostgreSQL unique violation
                        let detail = db_err.message();
                        if detail.contains("username") {
                            return ApiError::UsernameTaken;
                        }
                        if detail.contains("email") {
                            return ApiError::EmailTaken;
                        }
                    }
                }
                ApiError::DatabaseError(db_err.message().to_string())
            }
            _ => ApiError::DatabaseError(err.to_string()),
        }
    }
}

impl From<redis::RedisError> for ApiError {
    fn from(err: redis::RedisError) -> Self {
        tracing::error!("Redis error: {:?}", err);
        ApiError::RedisError(err.to_string())
    }
}

impl From<jsonwebtoken::errors::Error> for ApiError {
    fn from(err: jsonwebtoken::errors::Error) -> Self {
        tracing::warn!("JWT error: {:?}", err);
        match err.kind() {
            jsonwebtoken::errors::ErrorKind::ExpiredSignature => ApiError::TokenExpired,
            _ => ApiError::InvalidToken,
        }
    }
}

impl From<bcrypt::BcryptError> for ApiError {
    fn from(err: bcrypt::BcryptError) -> Self {
        tracing::error!("Bcrypt error: {:?}", err);
        ApiError::InternalError("Password hashing failed".to_string())
    }
}

impl From<validator::ValidationErrors> for ApiError {
    fn from(err: validator::ValidationErrors) -> Self {
        let mut errors = std::collections::HashMap::new();

        for (field, field_errors) in err.field_errors() {
            if let Some(first_error) = field_errors.first() {
                let message = first_error
                    .message
                    .as_ref()
                    .map(|m| m.to_string())
                    .unwrap_or_else(|| format!("Invalid value for {}", field));
                errors.insert(field.to_string(), message);
            }
        }

        ApiError::ValidationError(errors)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_error_status_codes() {
        assert_eq!(
            ApiError::InvalidCredentials.status_code(),
            StatusCode::UNAUTHORIZED
        );
        assert_eq!(ApiError::UsernameTaken.status_code(), StatusCode::CONFLICT);
        assert_eq!(
            ApiError::TooManyAttempts.status_code(),
            StatusCode::TOO_MANY_REQUESTS
        );
        assert_eq!(
            ApiError::InternalError("test".to_string()).status_code(),
            StatusCode::INTERNAL_SERVER_ERROR
        );
    }

    #[test]
    fn test_error_display() {
        assert_eq!(
            ApiError::InvalidCredentials.to_string(),
            "Invalid username or password"
        );
        assert_eq!(
            ApiError::NotFound("User".to_string()).to_string(),
            "User not found"
        );
    }
}
