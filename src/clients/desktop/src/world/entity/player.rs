use std::collections::VecDeque;
use crate::world::location::{Location, Direction};

pub struct Player {
    index: u16,
    username: String,
    location: Location,
    walking_queue: VecDeque<Location>,
}

impl Player {
    pub fn new(index: u16, username: String) -> Self {
        Self {
            index,
            username,
            location: Location::new(3222, 3222, 0), // Default spawn location
            walking_queue: VecDeque::new(),
        }
    }
    
    pub fn index(&self) -> u16 {
        self.index
    }
    
    pub fn username(&self) -> &String {
        &self.username
    }
    
    pub fn location(&self) -> &Location {
        &self.location
    }
    
    pub fn process_movement(&mut self) {
        // Process next movement step
        if let Some(next) = self.walking_queue.pop_front() {
            // Update location
            self.location = next;
        }
    }
    
    pub fn add_movement_point(&mut self, location: Location) {
        self.walking_queue.push_back(location);
    }
}

pub struct ChatMessage {
    pub message: String,
    pub effects: u8,
}

