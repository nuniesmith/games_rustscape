use anyhow::Result;
use serde::{Deserialize, Serialize};
use std::fs::File;
use std::io::Read;
use std::path::Path;

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct Config {
    pub server_host: String,
    pub server_port: u16,
    pub websocket_port: u16,
    pub cache_path: String,
    pub username: Option<String>,
    pub password: Option<String>,
    pub auto_login: bool,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            server_host: "localhost".to_string(),
            server_port: 43594,
            websocket_port: 8080,
            cache_path: "./cache".to_string(),
            username: None,
            password: None,
            auto_login: false,
        }
    }
}

pub fn load_config() -> Result<Config> {
    let config_path = Path::new("config/config.toml");
    
    if config_path.exists() {
        let mut file = File::open(config_path)?;
        let mut contents = String::new();
        file.read_to_string(&mut contents)?;
        
        let config: Config = toml::from_str(&contents)?;
        Ok(config)
    } else {
        // Create default config
        let config = Config::default();
        
        // Ensure directory exists
        std::fs::create_dir_all(config_path.parent().unwrap())?;
        
        // Save default config
        let toml = toml::to_string_pretty(&config)?;
        std::fs::write(config_path, toml)?;
        
        Ok(config)
    }
}

