//! Game client module
//!
//! Contains the main game client that manages connection, player state, and game loop.

use anyhow::Result;
use log::info;
use std::collections::VecDeque;
use tokio::time::{self, Duration};

use crate::net::packet_handlers::login::LoginResponse;
use crate::util::config::Config;
use crate::world::entity::player::Player;
use crate::world::location::Location;

/// Connection to the game server
pub struct Connection {
    connected: bool,
    host: String,
    port: u16,
}

impl Connection {
    /// Create a new connection
    pub fn new(host: String, port: u16) -> Self {
        Self {
            connected: false,
            host,
            port,
        }
    }

    /// Attempt to connect to the server
    pub async fn connect(&mut self) -> Result<()> {
        // Stub implementation - actual networking would go here
        info!("Connecting to {}:{} (simulated)", self.host, self.port);
        self.connected = true;
        Ok(())
    }

    /// Login to the server
    pub async fn login(&mut self, _username: &str, _password: &str) -> Result<LoginResponse> {
        // Stub implementation - actual login protocol would go here
        if self.connected {
            Ok(LoginResponse::Success)
        } else {
            Ok(LoginResponse::Unknown(0))
        }
    }

    /// Disconnect from the server
    pub async fn disconnect(&mut self) -> Result<()> {
        self.connected = false;
        Ok(())
    }

    /// Check if connected
    pub fn is_connected(&self) -> bool {
        self.connected
    }
}

/// Main game client
pub struct GameClient {
    config: Config,
    connection: Connection,
    player: Player,
    chat_messages: VecDeque<String>,
}

impl GameClient {
    /// Create a new game client
    pub fn new(config: Config) -> Result<Self> {
        let connection = Connection::new(config.server_host.clone(), config.server_port);

        Ok(Self {
            config,
            connection,
            player: Player::new(0, String::new()),
            chat_messages: VecDeque::with_capacity(100),
        })
    }

    /// Connect to the game server
    pub async fn connect(&mut self) -> Result<()> {
        self.connection.connect().await?;
        self.add_chat_message("Connected to Rustscape server".to_string());
        Ok(())
    }

    /// Login with username and password
    pub async fn login(&mut self, username: &str, password: &str) -> Result<()> {
        let response = self.connection.login(username, password).await?;

        match response {
            LoginResponse::Success => {
                self.player = Player::new(1, username.to_string());
                info!("Logged in as {}", username);
                self.add_chat_message(format!("Welcome to Rustscape, {}!", username));
            }
            LoginResponse::InvalidCredentials => {
                self.add_chat_message("Invalid username or password.".to_string());
            }
            LoginResponse::AccountDisabled => {
                self.add_chat_message("Your account has been disabled.".to_string());
            }
            LoginResponse::WorldFull => {
                self.add_chat_message("The world is full. Please try again later.".to_string());
            }
            LoginResponse::Unknown(code) => {
                self.add_chat_message(format!("Login failed with code: {}", code));
            }
        }

        Ok(())
    }

    /// Run the game loop
    pub async fn run(&mut self) -> Result<()> {
        info!("Starting game loop");

        let mut interval = time::interval(Duration::from_millis(600));

        // Run a few cycles for demonstration
        for _ in 0..10 {
            interval.tick().await;

            // Process player movement
            self.player.process_movement();

            // Add demo message on first tick
            if self.chat_messages.len() < 3 {
                self.add_chat_message("Server: Welcome to the game world.".to_string());
            }
        }

        info!("Game loop ended");
        Ok(())
    }

    /// Get a reference to the player
    pub fn player(&self) -> &Player {
        &self.player
    }

    /// Get a mutable reference to the player
    pub fn player_mut(&mut self) -> &mut Player {
        &mut self.player
    }

    /// Get a reference to the connection
    pub fn connection(&self) -> &Connection {
        &self.connection
    }

    /// Get a mutable reference to the connection
    pub fn connection_mut(&mut self) -> &mut Connection {
        &mut self.connection
    }

    /// Move the player to a position
    pub async fn move_to(&mut self, x: u16, y: u16) -> Result<()> {
        let z = self.player.location().z;
        self.player.add_movement_point(Location::new(x, y, z));
        Ok(())
    }

    /// Send a chat message
    pub async fn send_chat_message(&mut self, message: &str) -> Result<()> {
        self.add_chat_message(format!("{}: {}", self.player.username(), message));
        Ok(())
    }

    /// Interact with a target
    pub async fn interact(
        &mut self,
        target_type: &str,
        target_id: u32,
        action: &str,
    ) -> Result<()> {
        self.add_chat_message(format!(
            "Interacting with {} {} using action: {}",
            target_type, target_id, action
        ));
        Ok(())
    }

    /// Add a chat message to the queue
    pub fn add_chat_message(&mut self, message: String) {
        self.chat_messages.push_back(message);

        // Keep queue at reasonable size
        while self.chat_messages.len() > 100 {
            self.chat_messages.pop_front();
        }
    }

    /// Get chat messages
    pub fn chat_messages(&self) -> &VecDeque<String> {
        &self.chat_messages
    }

    /// Get config
    pub fn config(&self) -> &Config {
        &self.config
    }
}

