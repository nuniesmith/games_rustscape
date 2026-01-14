//! REST API module for the Rustscape game server
//!
//! This module provides HTTP endpoints for:
//! - User authentication (login, register, logout)
//! - Session management
//! - Account management
//!
//! The API is built with Axum and integrates with PostgreSQL for persistence
//! and Redis for session caching.

pub mod auth;
pub mod error;
pub mod middleware;
pub mod response;

use std::sync::Arc;

use axum::{
    routing::{get, post},
    Router,
};
use tower_http::{
    cors::{Any, CorsLayer},
    trace::TraceLayer,
};

use crate::api::auth::AuthState;

/// API version prefix
pub const API_VERSION: &str = "v1";

/// Shared API state
#[derive(Clone)]
pub struct ApiState {
    /// Authentication state (DB pool, Redis, JWT config)
    pub auth: Arc<AuthState>,
}

impl ApiState {
    /// Create a new API state
    pub fn new(auth: AuthState) -> Self {
        Self {
            auth: Arc::new(auth),
        }
    }
}

/// Create the API router with all endpoints
pub fn create_router(state: ApiState) -> Router {
    // Auth routes
    let auth_routes = Router::new()
        .route("/register", post(auth::handlers::register))
        .route("/login", post(auth::handlers::login))
        .route("/logout", post(auth::handlers::logout))
        .route("/session", get(auth::handlers::get_session))
        .route("/check-username", get(auth::handlers::check_username))
        .route("/refresh", post(auth::handlers::refresh_token));

    // Health check route
    let health_routes = Router::new()
        .route("/", get(health_check))
        .route("/ready", get(readiness_check));

    // Combine all routes under the API version prefix
    let api_routes = Router::new()
        .nest("/auth", auth_routes)
        .nest("/health", health_routes);

    // Create the main router
    Router::new()
        .nest(&format!("/api/{}", API_VERSION), api_routes)
        // Add CORS middleware
        .layer(
            CorsLayer::new()
                .allow_origin(Any)
                .allow_methods(Any)
                .allow_headers(Any)
                .expose_headers(Any),
        )
        // Add request tracing
        .layer(TraceLayer::new_for_http())
        // Add state
        .with_state(state)
}

/// Health check endpoint
async fn health_check() -> &'static str {
    "OK"
}

/// Readiness check endpoint (checks database and Redis connectivity)
async fn readiness_check(
    axum::extract::State(state): axum::extract::State<ApiState>,
) -> Result<&'static str, error::ApiError> {
    // Check database connection
    state.auth.check_database().await?;

    // Check Redis connection
    state.auth.check_redis().await?;

    Ok("Ready")
}
