//! Application state module
//!
//! Contains the shared state used across all server connections.

use std::sync::Arc;

use sqlx::postgres::PgPool;
use tokio::sync::broadcast;
use tracing::{info, warn};

use crate::auth::AuthService;
use crate::cache::CacheStore;
use crate::config::ServerConfig;
use crate::crypto::RsaDecryptor;
use crate::error::Result;
use crate::game::persistence::PlayerPersistence;
use crate::game::world::GameWorld;
use crate::net::session::SessionManager;

/// Application state shared across all connections
pub struct AppState {
    /// Server configuration
    pub config: ServerConfig,
    /// Session manager for tracking connected clients
    pub session_manager: SessionManager,
    /// Game cache store
    pub cache: Arc<CacheStore>,
    /// Game world state
    pub world: Arc<GameWorld>,
    /// RSA decryptor for login (None in dev mode)
    pub rsa: Option<Arc<RsaDecryptor>>,
    /// Authentication service
    pub auth: Arc<AuthService>,
    /// Player persistence service (None if DB not configured)
    pub persistence: Option<Arc<PlayerPersistence>>,
    /// Shutdown signal sender
    pub shutdown_tx: broadcast::Sender<()>,
}

impl AppState {
    /// Create a new application state without database persistence
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
                warn!(
                    "Failed to initialize RSA decryptor: {}. Login will use dev mode (no encryption).",
                    e
                );
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
            persistence: None,
            shutdown_tx,
        })
    }

    /// Create a new application state with database persistence
    pub fn with_persistence(
        config: ServerConfig,
        shutdown_tx: broadcast::Sender<()>,
        db_pool: PgPool,
    ) -> Result<Self> {
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
                warn!(
                    "Failed to initialize RSA decryptor: {}. Login will use dev mode (no encryption).",
                    e
                );
                None
            }
        };

        // Initialize auth service with database for production auth
        let auth = Arc::new(AuthService::with_database(config.dev_mode, db_pool.clone()));
        if config.dev_mode {
            info!("Auth service running in DEVELOPMENT mode - all logins accepted");
        } else {
            info!("Auth service running with DATABASE authentication");
        }

        // Initialize persistence service
        let persistence = Arc::new(PlayerPersistence::new(db_pool));
        info!("Player persistence service initialized");

        Ok(Self {
            config,
            session_manager: SessionManager::new(),
            cache,
            world,
            rsa,
            auth,
            persistence: Some(persistence),
            shutdown_tx,
        })
    }

    /// Check if persistence is enabled
    pub fn has_persistence(&self) -> bool {
        self.persistence.is_some()
    }

    /// Get a reference to the persistence service
    pub fn persistence(&self) -> Option<&Arc<PlayerPersistence>> {
        self.persistence.as_ref()
    }
}
