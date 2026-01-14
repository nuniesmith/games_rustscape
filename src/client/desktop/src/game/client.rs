use anyhow::Result;
use crate::util::config::Config;
use crate::world::entity::player::Player;
use log::{debug, error, info, warn};
use tokio::time::{self, Duration};
use serde::{Deserialize, Serialize};
use std::collections::VecDeque;

pub struct GameClient {
    config: Config,
    player: Player,
    chat_messages: VecDeque<String>,
}

impl GameClient {
    pub fn new(config: Config) -> Result<Self> {
        Ok(Self {
            config,
            player: Player::new(0, "".to_string()),
            chat_messages: VecDeque::with_capacity(100),
        })
    }
    
    pub async fn connect(&mut self) -> Result<()> {
        info!("Connecting to game server {}:{} (simulated)", self.config.server_host, self.config.server_port);
        
        // This is a stub - no actual connection in this simplified example
        
        // Add welcome message to chat
        self.add_chat_message("Connected to 2009scape server (simulated)".to_string());
        
        Ok(())
    }
    
    pub async fn login(&mut self, username: &str, password: &str) -> Result<()> {
        // This is a stub - no actual login in this simplified example
        self.player = Player::new(1, username.to_string());
        
        info!("Logged in as {} (simulated)", username);
        
        // Add welcome message to chat
        self.add_chat_message(format!("Welcome to 2009scape, {}!", username));
        
        Ok(())
    }
    
    pub async fn run(&mut self) -> Result<()> {
        info!("Starting game loop (simplified)");
        
        let mut interval = time::interval(Duration::from_millis(600));
        
        // Just run a few cycles for demonstration
        for _ in 0..10 {
            interval.tick().await;
            
            // Process player movement
            self.player.process_movement();
            
            // Add some demo messages
            if self.chat_messages.len() < 3 {
                self.add_chat_message("Server: Welcome to the game world.".to_string());
            }
        }
        
        info!("Game loop ended");
        Ok(())
    }
    
    pub fn player(&self) -> &Player {
        &self.player
    }
    
    pub fn player_mut(&mut self) -> &mut Player {
        &mut self.player
    }
    
    pub async fn move_to(&mut self, x: u16, y: u16) -> Result<()> {
        // Add target location to player's movement queue
        self.player.add_movement_point(crate::world::location::Location::new(x, y, self.player.location().z));
        
        Ok(())
    }
    
    pub async fn send_chat_message(&mut self, message: &str) -> Result<()> {
        self.add_chat_message(format!("{}: {}", self.player.username(), message));
        
        Ok(())
    }
    
    pub async fn interact(&mut self, target_type: &str, target_id: u32, action: &str) -> Result<()> {
        self.add_chat_message(format!("Interacting with {} {} using action: {}", target_type, target_id, action));
        
        Ok(())
    }
    
    pub fn add_chat_message(&mut self, message: String) {
        // Add message to queue
        self.chat_messages.push_back(message);
        
        // Keep queue at reasonable size
        while self.chat_messages.len() > 100 {
            self.chat_messages.pop_front();
        }
    }
    
    pub fn connection_mut(&mut self) -> &mut ConnectionStub {
        &mut CONNECTION_STUB
    }
}

// A stub for the connection
static mut CONNECTION_STUB: ConnectionStub = ConnectionStub {};

pub struct ConnectionStub {}

impl ConnectionStub {
    pub async fn login(&mut self, _username: &str, _password: &str) -> Result<crate::net::packet_handlers::login::LoginResponse> {
        // Simulate success
        Ok(crate::net::packet_handlers::login::LoginResponse::Success)
    }
    
    pub async fn disconnect(&mut self) -> Result<()> {
        Ok(())
    }
}

