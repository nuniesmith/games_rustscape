use anyhow::Result;
use rs2009_client::game::client::GameClient;
use rs2009_client::util::config::load_config;
use log::{info, LevelFilter};
use rs2009_client::util::logger;

#[tokio::main]
async fn main() -> Result<()> {
    // Initialize logger
    logger::init_logger(LevelFilter::Info);
    info!("Starting RS2009 Rust Client");

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

