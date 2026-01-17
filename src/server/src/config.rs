//! Server configuration module
//!
//! Handles loading and parsing of server configuration from files and environment variables.

use std::env;
use std::path::PathBuf;

use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};

/// Server configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ServerConfig {
    /// Path to the configuration file
    #[serde(skip)]
    pub config_path: PathBuf,

    /// Server name displayed to players
    #[serde(default = "default_server_name")]
    pub server_name: String,

    /// World ID (1-255)
    #[serde(default = "default_world_id")]
    pub world_id: u8,

    /// Base game port (TCP)
    #[serde(default = "default_game_port")]
    pub game_port: u16,

    /// WebSocket port for browser clients
    #[serde(default = "default_websocket_port")]
    pub websocket_port: u16,

    /// Management API port
    #[serde(default = "default_management_port")]
    pub management_port: u16,

    /// Path to the game cache
    #[serde(default = "default_cache_path")]
    pub cache_path: PathBuf,

    /// Path to data files (configs, scripts, etc.)
    #[serde(default = "default_data_path")]
    pub data_path: PathBuf,

    /// Maximum number of players
    #[serde(default = "default_max_players")]
    pub max_players: u32,

    /// Game tick rate in milliseconds
    #[serde(default = "default_tick_rate")]
    pub tick_rate_ms: u64,

    /// Autosave interval in seconds (0 to disable)
    #[serde(default = "default_autosave_interval")]
    pub autosave_interval_secs: u64,

    /// Database configuration
    #[serde(default)]
    pub database: DatabaseConfig,

    /// RSA private key configuration
    #[serde(default)]
    pub rsa: RsaConfig,

    /// Development mode flag
    #[serde(default)]
    pub dev_mode: bool,

    /// Enable debug logging
    #[serde(default)]
    pub debug: bool,

    /// Enable the watchdog timer
    #[serde(default = "default_true")]
    pub watchdog_enabled: bool,

    /// Watchdog timeout in seconds
    #[serde(default = "default_watchdog_timeout")]
    pub watchdog_timeout: u64,
}

/// Database configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DatabaseConfig {
    /// Database host
    #[serde(default = "default_db_host")]
    pub host: String,

    /// Database port
    #[serde(default = "default_db_port")]
    pub port: u16,

    /// Database name
    #[serde(default = "default_db_name")]
    pub database: String,

    /// Database username
    #[serde(default = "default_db_user")]
    pub username: String,

    /// Database password
    #[serde(default)]
    pub password: String,

    /// Maximum connection pool size
    #[serde(default = "default_pool_size")]
    pub pool_size: u32,
}

/// RSA key configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RsaConfig {
    /// RSA modulus (N) as hex string
    #[serde(default = "default_rsa_modulus")]
    pub modulus: String,

    /// RSA private exponent (D) as hex string
    #[serde(default = "default_rsa_private_exponent")]
    pub private_exponent: String,

    /// RSA public exponent (E) - typically 65537
    #[serde(default = "default_rsa_public_exponent")]
    pub public_exponent: u64,
}

// Default value functions
fn default_server_name() -> String {
    "Rustscape".to_string()
}

fn default_world_id() -> u8 {
    1
}

fn default_game_port() -> u16 {
    43594
}

fn default_websocket_port() -> u16 {
    43596
}

fn default_management_port() -> u16 {
    5555
}

fn default_cache_path() -> PathBuf {
    PathBuf::from("./data/cache")
}

fn default_data_path() -> PathBuf {
    PathBuf::from("./data")
}

fn default_max_players() -> u32 {
    2000
}

fn default_tick_rate() -> u64 {
    600 // 600ms = standard RS tick rate
}

fn default_autosave_interval() -> u64 {
    300 // 5 minutes = 300 seconds
}

fn default_true() -> bool {
    true
}

fn default_watchdog_timeout() -> u64 {
    7200 // 2 hours in seconds
}

fn default_db_host() -> String {
    "localhost".to_string()
}

fn default_db_port() -> u16 {
    3306
}

fn default_db_name() -> String {
    "rustscape".to_string()
}

fn default_db_user() -> String {
    "rustscape".to_string()
}

fn default_pool_size() -> u32 {
    10
}

// Default RSA keys (DEVELOPMENT ONLY - replace in production!)
fn default_rsa_modulus() -> String {
    // 1024-bit RSA modulus for development
    "d5c9d9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9d5b8d9c9c9".to_string()
}

fn default_rsa_private_exponent() -> String {
    // Private exponent for development
    "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6".to_string()
}

fn default_rsa_public_exponent() -> u64 {
    65537
}

impl Default for DatabaseConfig {
    fn default() -> Self {
        Self {
            host: default_db_host(),
            port: default_db_port(),
            database: default_db_name(),
            username: default_db_user(),
            password: String::new(),
            pool_size: default_pool_size(),
        }
    }
}

impl Default for RsaConfig {
    fn default() -> Self {
        Self {
            modulus: default_rsa_modulus(),
            private_exponent: default_rsa_private_exponent(),
            public_exponent: default_rsa_public_exponent(),
        }
    }
}

impl Default for ServerConfig {
    fn default() -> Self {
        Self {
            config_path: PathBuf::from("config/server.toml"),
            server_name: default_server_name(),
            world_id: default_world_id(),
            game_port: default_game_port(),
            websocket_port: default_websocket_port(),
            management_port: default_management_port(),
            cache_path: default_cache_path(),
            data_path: default_data_path(),
            max_players: default_max_players(),
            tick_rate_ms: default_tick_rate(),
            autosave_interval_secs: default_autosave_interval(),
            database: DatabaseConfig::default(),
            rsa: RsaConfig::default(),
            dev_mode: false,
            debug: false,
            watchdog_enabled: default_true(),
            watchdog_timeout: default_watchdog_timeout(),
        }
    }
}

impl ServerConfig {
    /// Load configuration from file and environment variables
    pub async fn load() -> Result<Self> {
        // Determine config path from environment or use default
        let config_path = env::var("RUSTSCAPE_CONFIG")
            .map(PathBuf::from)
            .unwrap_or_else(|_| PathBuf::from("config/server.toml"));

        // Try to load from file
        let mut config = if config_path.exists() {
            let content = tokio::fs::read_to_string(&config_path)
                .await
                .with_context(|| {
                    format!("Failed to read config file: {}", config_path.display())
                })?;

            toml::from_str(&content).with_context(|| {
                format!("Failed to parse config file: {}", config_path.display())
            })?
        } else {
            tracing::warn!(
                "Config file not found at {}, using defaults",
                config_path.display()
            );
            Self::default()
        };

        config.config_path = config_path;

        // Override with environment variables
        config.apply_env_overrides();

        // Validate configuration
        config.validate()?;

        Ok(config)
    }

    /// Apply environment variable overrides
    fn apply_env_overrides(&mut self) {
        if let Ok(val) = env::var("RUSTSCAPE_SERVER_NAME") {
            self.server_name = val;
        }
        if let Ok(val) = env::var("RUSTSCAPE_WORLD_ID") {
            if let Ok(id) = val.parse() {
                self.world_id = id;
            }
        }
        if let Ok(val) = env::var("RUSTSCAPE_GAME_PORT") {
            if let Ok(port) = val.parse() {
                self.game_port = port;
            }
        }
        if let Ok(val) = env::var("RUSTSCAPE_WEBSOCKET_PORT") {
            if let Ok(port) = val.parse() {
                self.websocket_port = port;
            }
        }
        if let Ok(val) = env::var("RUSTSCAPE_MANAGEMENT_PORT") {
            if let Ok(port) = val.parse() {
                self.management_port = port;
            }
        }
        if let Ok(val) = env::var("RUSTSCAPE_CACHE_PATH") {
            self.cache_path = PathBuf::from(val);
        }
        if let Ok(val) = env::var("RUSTSCAPE_DATA_PATH") {
            self.data_path = PathBuf::from(val);
        }
        if let Ok(val) = env::var("RUSTSCAPE_MAX_PLAYERS") {
            if let Ok(max) = val.parse() {
                self.max_players = max;
            }
        }
        if let Ok(val) = env::var("RUSTSCAPE_DEV_MODE") {
            self.dev_mode = val.to_lowercase() == "true" || val == "1";
        }
        if let Ok(val) = env::var("RUSTSCAPE_DEBUG") {
            self.debug = val.to_lowercase() == "true" || val == "1";
        }

        // Database overrides (RUSTSCAPE_DATABASE_* takes precedence over MYSQL_*)
        if let Ok(val) = env::var("MYSQL_HOST") {
            self.database.host = val;
        }
        if let Ok(val) = env::var("RUSTSCAPE_DATABASE_HOST") {
            self.database.host = val;
        }
        if let Ok(val) = env::var("MYSQL_PORT") {
            if let Ok(port) = val.parse() {
                self.database.port = port;
            }
        }
        if let Ok(val) = env::var("RUSTSCAPE_DATABASE_PORT") {
            if let Ok(port) = val.parse() {
                self.database.port = port;
            }
        }
        if let Ok(val) = env::var("MYSQL_DATABASE") {
            self.database.database = val;
        }
        if let Ok(val) = env::var("RUSTSCAPE_DATABASE_NAME") {
            self.database.database = val;
        }
        if let Ok(val) = env::var("MYSQL_USER") {
            self.database.username = val;
        }
        if let Ok(val) = env::var("RUSTSCAPE_DATABASE_USER") {
            self.database.username = val;
        }
        if let Ok(val) = env::var("MYSQL_PASSWORD") {
            self.database.password = val;
        }
        if let Ok(val) = env::var("RUSTSCAPE_DATABASE_PASSWORD") {
            self.database.password = val;
        }

        // RSA overrides (from secure environment)
        if let Ok(val) = env::var("RUSTSCAPE_RSA_MODULUS") {
            self.rsa.modulus = val;
        }
        if let Ok(val) = env::var("RUSTSCAPE_RSA_PRIVATE_EXPONENT") {
            self.rsa.private_exponent = val;
        }
    }

    /// Validate the configuration
    fn validate(&self) -> Result<()> {
        // World ID must be 1-255
        if self.world_id == 0 {
            anyhow::bail!("World ID must be between 1 and 255");
        }

        // Ports must be unique
        if self.game_port == self.websocket_port {
            anyhow::bail!("Game port and WebSocket port must be different");
        }
        if self.game_port == self.management_port || self.websocket_port == self.management_port {
            anyhow::bail!("Management port must be different from game and WebSocket ports");
        }

        // Max players must be reasonable
        if self.max_players == 0 || self.max_players > 10000 {
            anyhow::bail!("Max players must be between 1 and 10000");
        }

        // Tick rate must be reasonable
        if self.tick_rate_ms < 100 || self.tick_rate_ms > 5000 {
            anyhow::bail!("Tick rate must be between 100ms and 5000ms");
        }

        Ok(())
    }

    /// Get the actual game port (base port + world ID)
    pub fn actual_game_port(&self) -> u16 {
        self.game_port + self.world_id as u16
    }

    /// Get the database connection URL
    pub fn database_url(&self) -> String {
        format!(
            "mysql://{}:{}@{}:{}/{}",
            self.database.username,
            self.database.password,
            self.database.host,
            self.database.port,
            self.database.database
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_config() {
        let config = ServerConfig::default();
        assert_eq!(config.server_name, "Rustscape");
        assert_eq!(config.world_id, 1);
        assert_eq!(config.game_port, 43594);
        assert_eq!(config.websocket_port, 43596);
        assert_eq!(config.tick_rate_ms, 600);
        assert_eq!(config.autosave_interval_secs, 300);
    }

    #[test]
    fn test_actual_game_port() {
        let mut config = ServerConfig::default();
        config.world_id = 1;
        assert_eq!(config.actual_game_port(), 43595);

        config.world_id = 2;
        assert_eq!(config.actual_game_port(), 43596);
    }

    #[test]
    fn test_validation() {
        let mut config = ServerConfig::default();

        // Valid config should pass
        assert!(config.validate().is_ok());

        // Invalid world ID
        config.world_id = 0;
        assert!(config.validate().is_err());
        config.world_id = 1;

        // Duplicate ports
        config.websocket_port = config.game_port;
        assert!(config.validate().is_err());
    }
}
