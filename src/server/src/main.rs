//! Rustscape Game Server
//!
//! A Rust implementation of a 530 revision RSPS server with WebSocket support
//! for browser-based clients and REST API for authentication.

use std::net::SocketAddr;
use std::sync::Arc;

use anyhow::Result;
use sqlx::postgres::PgPoolOptions;
use tokio::net::TcpListener;
use tokio::signal;
use tokio::sync::broadcast;
use tracing::{error, info, warn};
use tracing_subscriber::{fmt, EnvFilter};

use rustscape_server::api;
use rustscape_server::api::auth::AuthState;
use rustscape_server::api::ApiState;
use rustscape_server::config::ServerConfig;
use rustscape_server::net::handler::ConnectionHandler;
use rustscape_server::state::AppState;
use rustscape_server::{REVISION, VERSION};

#[tokio::main]
async fn main() -> Result<()> {
    // Initialize logging
    init_logging();

    info!("╔══════════════════════════════════════════════╗");
    info!("║        Rustscape Game Server v{}             ║", VERSION);
    info!("║          Revision: {}                        ║", REVISION);
    info!("╚══════════════════════════════════════════════╝");

    // Load configuration
    let config = ServerConfig::load().await?;
    info!(
        "Configuration loaded from: {}",
        config.config_path.display()
    );

    // Create shutdown channel
    let (shutdown_tx, _) = broadcast::channel::<()>(1);

    // Try to create database pool for player persistence
    let db_pool = create_database_pool(&config).await;

    // Initialize application state for game server (with or without persistence)
    let state = match &db_pool {
        Some(pool) => {
            info!("Initializing application state with database persistence");
            Arc::new(AppState::with_persistence(
                config.clone(),
                shutdown_tx.clone(),
                pool.clone(),
            )?)
        }
        None => {
            warn!("Initializing application state without database persistence");
            Arc::new(AppState::new(config.clone(), shutdown_tx.clone())?)
        }
    };
    info!("Application state initialized");

    // Initialize API state (with PostgreSQL and Redis)
    let api_state = match AuthState::new(&config).await {
        Ok(auth_state) => {
            info!("API authentication state initialized");
            Some(ApiState::new(auth_state))
        }
        Err(e) => {
            warn!(
                "Failed to initialize API state: {}. REST API will be disabled.",
                e
            );
            None
        }
    };

    // Start the game world tick (with persistence and sync if available)
    let world_state = state.clone();
    let world_persistence = db_pool.clone();
    let mut world_shutdown_rx = shutdown_tx.subscribe();
    tokio::spawn(async move {
        if let Some(pool) = world_persistence {
            let persistence = rustscape_server::game::persistence::PlayerPersistence::new(pool);
            world_state
                .world
                .run_with_sync(
                    &mut world_shutdown_rx,
                    Some(&persistence),
                    Some(&world_state.session_manager),
                )
                .await;
        } else {
            world_state
                .world
                .run_with_sync(
                    &mut world_shutdown_rx,
                    None,
                    Some(&world_state.session_manager),
                )
                .await;
        }
    });

    // Start TCP listener for game connections
    let game_addr: SocketAddr = format!("0.0.0.0:{}", config.game_port).parse()?;
    let game_listener = TcpListener::bind(game_addr).await?;
    info!("Game server listening on: {}", game_addr);

    // Start WebSocket listener for browser clients
    let ws_addr: SocketAddr = format!("0.0.0.0:{}", config.websocket_port).parse()?;
    let ws_listener = TcpListener::bind(ws_addr).await?;
    info!("WebSocket server listening on: {}", ws_addr);

    // Spawn game connection acceptor
    let game_state = state.clone();
    let mut game_shutdown_rx = shutdown_tx.subscribe();
    let game_handle = tokio::spawn(async move {
        accept_game_connections(game_listener, game_state, &mut game_shutdown_rx).await;
    });

    // Spawn WebSocket connection acceptor
    let ws_state = state.clone();
    let mut ws_shutdown_rx = shutdown_tx.subscribe();
    let ws_handle = tokio::spawn(async move {
        accept_websocket_connections(ws_listener, ws_state, &mut ws_shutdown_rx).await;
    });

    // Start HTTP API server if initialized
    let api_handle = if let Some(api_state) = api_state {
        let api_addr: SocketAddr = format!("0.0.0.0:{}", config.management_port).parse()?;
        let api_listener = TcpListener::bind(api_addr).await?;
        info!("REST API server listening on: {}", api_addr);

        let api_shutdown_rx = shutdown_tx.subscribe();
        Some(tokio::spawn(async move {
            run_api_server(api_listener, api_state, api_shutdown_rx).await;
        }))
    } else {
        None
    };

    info!("Server startup complete!");
    info!("World {} is ready for connections", config.world_id);

    // Wait for shutdown signal
    wait_for_shutdown(shutdown_tx.clone()).await;

    info!("Shutting down server...");

    // Wait for handlers to finish
    let _ = game_handle.await;
    let _ = ws_handle.await;
    if let Some(handle) = api_handle {
        let _ = handle.await;
    }

    // Cleanup
    state.session_manager.disconnect_all().await;
    info!("All sessions disconnected");

    info!("Server shutdown complete. Goodbye!");
    Ok(())
}

/// Initialize the logging/tracing system
fn init_logging() {
    let filter = EnvFilter::try_from_default_env()
        .unwrap_or_else(|_| EnvFilter::new("info,rustscape_server=debug"));

    fmt()
        .with_env_filter(filter)
        .with_target(true)
        .with_thread_ids(true)
        .with_file(true)
        .with_line_number(true)
        .with_level(true)
        .init();
}

/// Accept incoming game (TCP) connections
async fn accept_game_connections(
    listener: TcpListener,
    state: Arc<AppState>,
    shutdown_rx: &mut broadcast::Receiver<()>,
) {
    loop {
        tokio::select! {
            result = listener.accept() => {
                match result {
                    Ok((stream, addr)) => {
                        info!("New game connection from: {}", addr);
                        let handler = ConnectionHandler::new(state.clone(), false);
                        tokio::spawn(async move {
                            if let Err(e) = handler.handle_tcp(stream, addr).await {
                                warn!("Game connection error from {}: {}", addr, e);
                            }
                        });
                    }
                    Err(e) => {
                        error!("Failed to accept game connection: {}", e);
                    }
                }
            }
            _ = shutdown_rx.recv() => {
                info!("Game connection acceptor shutting down");
                break;
            }
        }
    }
}

/// Accept incoming WebSocket connections (for browser clients)
async fn accept_websocket_connections(
    listener: TcpListener,
    state: Arc<AppState>,
    shutdown_rx: &mut broadcast::Receiver<()>,
) {
    loop {
        tokio::select! {
            result = listener.accept() => {
                match result {
                    Ok((stream, addr)) => {
                        info!("New WebSocket connection from: {}", addr);
                        let handler = ConnectionHandler::new(state.clone(), true);
                        tokio::spawn(async move {
                            if let Err(e) = handler.handle_websocket(stream, addr).await {
                                warn!("WebSocket connection error from {}: {}", addr, e);
                            }
                        });
                    }
                    Err(e) => {
                        error!("Failed to accept WebSocket connection: {}", e);
                    }
                }
            }
            _ = shutdown_rx.recv() => {
                info!("WebSocket connection acceptor shutting down");
                break;
            }
        }
    }
}

/// Run the HTTP API server
async fn run_api_server(
    listener: TcpListener,
    state: ApiState,
    mut shutdown_rx: broadcast::Receiver<()>,
) {
    // Create the API router
    let router = api::create_router(state);

    // Serve with graceful shutdown
    info!("Starting REST API server...");

    // Use axum's serve with graceful shutdown
    let shutdown_signal = async move {
        let _ = shutdown_rx.recv().await;
        info!("REST API server shutting down");
    };

    axum::serve(listener, router)
        .with_graceful_shutdown(shutdown_signal)
        .await
        .unwrap_or_else(|e| error!("API server error: {}", e));
}

/// Create database pool for player persistence
async fn create_database_pool(config: &ServerConfig) -> Option<sqlx::PgPool> {
    let database_url = format!(
        "postgres://{}:{}@{}:{}/{}",
        config.database.username,
        config.database.password,
        config.database.host,
        config.database.port,
        config.database.database
    );

    match PgPoolOptions::new()
        .max_connections(10)
        .connect(&database_url)
        .await
    {
        Ok(pool) => {
            info!("Database pool created for player persistence");
            Some(pool)
        }
        Err(e) => {
            warn!(
                "Failed to create database pool: {}. Player persistence disabled.",
                e
            );
            None
        }
    }
}

/// Wait for shutdown signal (Ctrl+C or SIGTERM)
async fn wait_for_shutdown(shutdown_tx: broadcast::Sender<()>) {
    let ctrl_c = async {
        signal::ctrl_c()
            .await
            .expect("Failed to install Ctrl+C handler");
    };

    #[cfg(unix)]
    let terminate = async {
        signal::unix::signal(signal::unix::SignalKind::terminate())
            .expect("Failed to install SIGTERM handler")
            .recv()
            .await;
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {
            info!("Received Ctrl+C, initiating shutdown...");
        }
        _ = terminate => {
            info!("Received SIGTERM, initiating shutdown...");
        }
    }

    // Signal all tasks to shut down
    let _ = shutdown_tx.send(());
}
