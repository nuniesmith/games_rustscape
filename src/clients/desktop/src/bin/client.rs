//! Rustscape Desktop Client Binary
//!
//! Entry point for the desktop client application.

use anyhow::Result;
use log::{info, LevelFilter};
use rustscape_client::game::client::GameClient;
use rustscape_client::util::config::load_config;
use rustscape_client::util::logger;

#[tokio::main]
async fn main() -> Result<()> {
    // Initialize logger
    logger::init_logger(LevelFilter::Info);
    info!("Starting Rustscape Desktop Client");

    // Load configuration
    let config = load_config()?;

    // Create and initialize game client
    let mut client = GameClient::new(config)?;

    // Connect to server
    client.connect().await?;

    // Run game loop
    client.run().await?;

    Ok(())
}
