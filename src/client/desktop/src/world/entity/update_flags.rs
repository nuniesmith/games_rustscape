use std::collections::HashMap;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum UpdateFlag {
    Appearance,
    Chat,
    Movement,
}

pub struct UpdateFlags {
    flags: HashMap<UpdateFlag, bool>,
}

impl UpdateFlags {
    pub fn new() -> Self {
        Self {
            flags: HashMap::new(),
        }
    }
    
    pub fn set(&mut self, flag: UpdateFlag) {
        self.flags.insert(flag, true);
    }
    
    pub fn clear(&mut self, flag: UpdateFlag) {
        self.flags.insert(flag, false);
    }
    
    pub fn is_set(&self, flag: UpdateFlag) -> bool {
        *self.flags.get(&flag).unwrap_or(&false)
    }
    
    pub fn set_chat(&mut self) {
        self.set(UpdateFlag::Chat);
    }
    
    pub fn set_appearance(&mut self) {
        self.set(UpdateFlag::Appearance);
    }
    
    pub fn set_movement(&mut self) {
        self.set(UpdateFlag::Movement);
    }
}

