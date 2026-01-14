//! Rustscape Game Server
//!
//! A Rust implementation of a 530 revision RSPS server with WebSocket support
//! for browser-based clients.

mod auth;
mod cache;
mod config;
mod crypto;
mod error;
mod game;
mod net;
mod protocol;

use std::net::SocketAddr;
use std::sync::Arc;

use anyhow::Result;
use tokio::net::TcpListener;
use tokio::signal;
use tokio::sync::broadcast;
use tracing::{error, info, warn};
use tracing_subscriber::{fmt, EnvFilter};

use crate::auth::AuthService;
use crate::cache::CacheStore;
use crate::config::ServerConfig;
use crate::crypto::RsaDecryptor;
use crate::game::world::GameWorld;
use crate::net::handler::ConnectionHandler;
use crate::net::session::SessionManager;

/// Server version
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// Server revision (must match client)
pub const REVISION: u32 = 530;

/// Application state shared across all connections
pub struct AppState {
    pub config: ServerConfig,
    pub session_manager: SessionManager,
    pub cache: Arc<CacheStore>,
    pub world: Arc<GameWorld>,
    pub rsa: Option<Arc<RsaDecryptor>>,
    pub auth: Arc<AuthService>,
    pub shutdown_tx: broadcast::Sender<()>,
}

impl AppState {
    pub fn new(config: ServerConfig, shutdown_tx: broadcast::Sender<()>) -> Result<Self> {
        let cache = Arc::new(CacheStore::new(&config.cache_path)?);
        let world = Arc::new(GameWorld::new(config.world_id)?);

        // Initialize RSA decryptor from config
        let rsa = match RsaDecryptor::from_hex(
            &config.rsa.modulus,
            &config.rsa.private_exponent,
            config.rsa.public_exponent,
        ) {
            Ok(decryptor) => {
                info!(
                    "RSA decryptor initialized (key size: {} bits)",
                    decryptor.key_pair().key_size_bits()
                );
                Some(Arc::new(decryptor))
            }
            Err(e) => {
                warn!("Failed to initialize RSA decryptor: {}. Login will use dev mode (no encryption).", e);
                None
            }
        };

        // Initialize auth service
        let auth = Arc::new(AuthService::new(config.dev_mode));
        if config.dev_mode {
            info!("Auth service running in DEVELOPMENT mode - all logins accepted");
        }

        Ok(Self {
            config,
            session_manager: SessionManager::new(),
            cache,
            world,
            rsa,
            auth,
            shutdown_tx,
        })
    }
}

#[tokio::main]
async fn main() -> Result<()> {
    // Initialize logging
    init_logging();

    info!("╔══════════════════════════════════════════════╗");
    info!("║        Rustscape Game Server v{}          ║", VERSION);
    info!("║          Revision: {}                       ║", REVISION);
    info!("╚══════════════════════════════════════════════╝");

    // Load configuration
    let config = ServerConfig::load().await?;
    info!(
        "Configuration loaded from: {}",
        config.config_path.display()
    );

    // Create shutdown channel
    let (shutdown_tx, _) = broadcast::channel::<()>(1);

    // Initialize application state
    let state = Arc::new(AppState::new(config.clone(), shutdown_tx.clone())?);
    info!("Application state initialized");

    // Start the game world tick
    let world_state = state.clone();
    let mut world_shutdown_rx = shutdown_tx.subscribe();
    tokio::spawn(async move {
        world_state.world.run(&mut world_shutdown_rx).await;
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

    info!("Server startup complete!");
    info!("World {} is ready for connections", config.world_id);

    // Wait for shutdown signal
    wait_for_shutdown(shutdown_tx.clone()).await;

    info!("Shutting down server...");

    // Wait for handlers to finish
    let _ = tokio::join!(game_handle, ws_handle);

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
