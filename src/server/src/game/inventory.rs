//! Inventory module
//!
//! This module handles player inventory storage and operations:
//! - Inventory item storage (28 slots)
//! - Add and remove operations
//! - Item stacking (for stackable items)
//! - Slot management (swap, insert)

use serde::{Deserialize, Serialize};

/// Maximum number of inventory slots (standard RS inventory)
pub const INVENTORY_SIZE: usize = 28;

/// Maximum stack size for stackable items
pub const MAX_STACK_SIZE: u32 = 2_147_483_647; // i32::MAX as u32

/// A single item in the inventory
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
pub struct InventoryItem {
    /// Item ID (0 = empty slot)
    pub item_id: u16,
    /// Item amount (1 for non-stackable, 1+ for stackable)
    pub amount: u32,
}

impl InventoryItem {
    /// Create a new inventory item
    pub fn new(item_id: u16, amount: u32) -> Self {
        Self { item_id, amount }
    }

    /// Create an empty slot
    pub fn empty() -> Self {
        Self {
            item_id: 0,
            amount: 0,
        }
    }

    /// Check if this slot is empty
    pub fn is_empty(&self) -> bool {
        self.item_id == 0 || self.amount == 0
    }

    /// Check if this is a valid item
    pub fn is_valid(&self) -> bool {
        self.item_id > 0 && self.amount > 0
    }
}

impl Default for InventoryItem {
    fn default() -> Self {
        Self::empty()
    }
}

/// Player inventory storage
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Inventory {
    /// Inventory slots (0-27)
    slots: [InventoryItem; INVENTORY_SIZE],
}

impl Default for Inventory {
    fn default() -> Self {
        Self::new()
    }
}

impl Inventory {
    /// Create a new empty inventory
    pub fn new() -> Self {
        Self {
            slots: [InventoryItem::empty(); INVENTORY_SIZE],
        }
    }

    /// Create inventory from a vector of optional items (for loading from persistence)
    pub fn from_items(items: &[Option<(i32, i32)>]) -> Self {
        let mut inventory = Self::new();
        for (slot, item) in items.iter().enumerate() {
            if slot >= INVENTORY_SIZE {
                break;
            }
            if let Some((id, amount)) = item {
                if *id > 0 && *amount > 0 {
                    inventory.slots[slot] = InventoryItem::new(*id as u16, *amount as u32);
                }
            }
        }
        inventory
    }

    /// Get the number of items in the inventory (non-empty slots)
    pub fn item_count(&self) -> usize {
        self.slots.iter().filter(|s| s.is_valid()).count()
    }

    /// Get the number of free slots
    pub fn free_slots(&self) -> usize {
        INVENTORY_SIZE - self.item_count()
    }

    /// Check if the inventory is full
    pub fn is_full(&self) -> bool {
        self.free_slots() == 0
    }

    /// Check if the inventory is empty
    pub fn is_empty(&self) -> bool {
        self.item_count() == 0
    }

    /// Get an item at a specific slot
    pub fn get(&self, slot: usize) -> Option<&InventoryItem> {
        if slot >= INVENTORY_SIZE {
            return None;
        }
        let item = &self.slots[slot];
        if item.is_valid() {
            Some(item)
        } else {
            None
        }
    }

    /// Get an item at a specific slot (even if empty)
    pub fn get_slot(&self, slot: usize) -> Option<&InventoryItem> {
        self.slots.get(slot)
    }

    /// Get a mutable reference to an item at a specific slot
    pub fn get_mut(&mut self, slot: usize) -> Option<&mut InventoryItem> {
        if slot >= INVENTORY_SIZE {
            return None;
        }
        Some(&mut self.slots[slot])
    }

    /// Find the first slot containing a specific item ID
    pub fn find_item(&self, item_id: u16) -> Option<usize> {
        self.slots
            .iter()
            .position(|s| s.item_id == item_id && s.is_valid())
    }

    /// Find all slots containing a specific item ID
    pub fn find_all_items(&self, item_id: u16) -> Vec<usize> {
        self.slots
            .iter()
            .enumerate()
            .filter(|(_, s)| s.item_id == item_id && s.is_valid())
            .map(|(i, _)| i)
            .collect()
    }

    /// Find the first empty slot
    pub fn find_empty_slot(&self) -> Option<usize> {
        self.slots.iter().position(|s| s.is_empty())
    }

    /// Get the total amount of a specific item across all slots
    pub fn count_item(&self, item_id: u16) -> u64 {
        self.slots
            .iter()
            .filter(|s| s.item_id == item_id && s.is_valid())
            .map(|s| s.amount as u64)
            .sum()
    }

    /// Check if the inventory contains at least the specified amount of an item
    pub fn contains(&self, item_id: u16, amount: u32) -> bool {
        self.count_item(item_id) >= amount as u64
    }

    /// Add an item to the inventory
    /// For stackable items, it will try to stack with existing items first
    /// Returns Ok(slot) on success, Err(remaining) if couldn't add all
    pub fn add(&mut self, item_id: u16, amount: u32, stackable: bool) -> Result<usize, u32> {
        if item_id == 0 || amount == 0 {
            return Err(amount);
        }

        if stackable {
            self.add_stackable(item_id, amount)
        } else {
            self.add_non_stackable(item_id, amount)
        }
    }

    /// Add a stackable item (tries to stack with existing, then uses new slot)
    fn add_stackable(&mut self, item_id: u16, amount: u32) -> Result<usize, u32> {
        // First, try to find existing stack
        if let Some(slot) = self.find_item(item_id) {
            let item = &mut self.slots[slot];
            let new_amount = (item.amount as u64) + (amount as u64);
            if new_amount <= MAX_STACK_SIZE as u64 {
                item.amount = new_amount as u32;
                return Ok(slot);
            } else {
                // Stack would overflow, add what we can
                let can_add = MAX_STACK_SIZE - item.amount;
                item.amount = MAX_STACK_SIZE;
                return Err(amount - can_add);
            }
        }

        // No existing stack, find empty slot
        if let Some(slot) = self.find_empty_slot() {
            self.slots[slot] = InventoryItem::new(item_id, amount.min(MAX_STACK_SIZE));
            if amount > MAX_STACK_SIZE {
                return Err(amount - MAX_STACK_SIZE);
            }
            return Ok(slot);
        }

        Err(amount)
    }

    /// Add a non-stackable item (each item takes one slot)
    fn add_non_stackable(&mut self, item_id: u16, mut amount: u32) -> Result<usize, u32> {
        let mut first_slot = None;

        while amount > 0 {
            if let Some(slot) = self.find_empty_slot() {
                self.slots[slot] = InventoryItem::new(item_id, 1);
                if first_slot.is_none() {
                    first_slot = Some(slot);
                }
                amount -= 1;
            } else {
                // No more empty slots
                return if let Some(slot) = first_slot {
                    if amount > 0 {
                        Err(amount)
                    } else {
                        Ok(slot)
                    }
                } else {
                    Err(amount)
                };
            }
        }

        first_slot.ok_or(0)
    }

    /// Add an item to a specific slot (overwrites existing)
    pub fn set(&mut self, slot: usize, item_id: u16, amount: u32) -> Result<(), InventoryError> {
        if slot >= INVENTORY_SIZE {
            return Err(InventoryError::InvalidSlot);
        }

        if item_id == 0 || amount == 0 {
            self.slots[slot] = InventoryItem::empty();
        } else {
            self.slots[slot] = InventoryItem::new(item_id, amount);
        }

        Ok(())
    }

    /// Remove an amount of an item from a specific slot
    /// Returns the actual amount removed
    pub fn remove(&mut self, slot: usize, amount: u32) -> Result<u32, InventoryError> {
        if slot >= INVENTORY_SIZE {
            return Err(InventoryError::InvalidSlot);
        }

        let item = &mut self.slots[slot];
        if item.is_empty() {
            return Err(InventoryError::EmptySlot);
        }

        let removed = amount.min(item.amount);
        item.amount -= removed;

        if item.amount == 0 {
            *item = InventoryItem::empty();
        }

        Ok(removed)
    }

    /// Remove a specific amount of an item by ID (from any slot)
    /// Returns the actual amount removed
    pub fn remove_item(&mut self, item_id: u16, mut amount: u32) -> u32 {
        let mut total_removed = 0u32;

        for slot in 0..INVENTORY_SIZE {
            if amount == 0 {
                break;
            }

            let item = &mut self.slots[slot];
            if item.item_id == item_id && item.is_valid() {
                let to_remove = amount.min(item.amount);
                item.amount -= to_remove;
                amount -= to_remove;
                total_removed += to_remove;

                if item.amount == 0 {
                    *item = InventoryItem::empty();
                }
            }
        }

        total_removed
    }

    /// Remove all of a specific item from the inventory
    /// Returns the total amount removed
    pub fn remove_all_of(&mut self, item_id: u16) -> u32 {
        self.remove_item(item_id, u32::MAX)
    }

    /// Clear a specific slot
    pub fn clear_slot(&mut self, slot: usize) -> Result<InventoryItem, InventoryError> {
        if slot >= INVENTORY_SIZE {
            return Err(InventoryError::InvalidSlot);
        }

        let item = self.slots[slot];
        self.slots[slot] = InventoryItem::empty();
        Ok(item)
    }

    /// Clear the entire inventory
    pub fn clear(&mut self) {
        self.slots = [InventoryItem::empty(); INVENTORY_SIZE];
    }

    /// Swap two slots
    pub fn swap(&mut self, slot1: usize, slot2: usize) -> Result<(), InventoryError> {
        if slot1 >= INVENTORY_SIZE || slot2 >= INVENTORY_SIZE {
            return Err(InventoryError::InvalidSlot);
        }

        if slot1 == slot2 {
            return Ok(());
        }

        self.slots.swap(slot1, slot2);
        Ok(())
    }

    /// Insert an item at a slot, shifting other items
    pub fn insert(&mut self, from_slot: usize, to_slot: usize) -> Result<(), InventoryError> {
        if from_slot >= INVENTORY_SIZE || to_slot >= INVENTORY_SIZE {
            return Err(InventoryError::InvalidSlot);
        }

        if from_slot == to_slot {
            return Ok(());
        }

        let item = self.slots[from_slot];

        if from_slot < to_slot {
            // Shift items left
            for i in from_slot..to_slot {
                self.slots[i] = self.slots[i + 1];
            }
        } else {
            // Shift items right
            for i in (to_slot..from_slot).rev() {
                self.slots[i + 1] = self.slots[i];
            }
        }

        self.slots[to_slot] = item;
        Ok(())
    }

    /// Compact the inventory (move all items to the front)
    pub fn compact(&mut self) {
        let mut write_idx = 0;

        for read_idx in 0..INVENTORY_SIZE {
            if self.slots[read_idx].is_valid() {
                if read_idx != write_idx {
                    self.slots[write_idx] = self.slots[read_idx];
                    self.slots[read_idx] = InventoryItem::empty();
                }
                write_idx += 1;
            }
        }
    }

    /// Get all items for sending to client
    pub fn get_all_items(&self) -> Vec<(usize, u16, u32)> {
        self.slots
            .iter()
            .enumerate()
            .filter(|(_, s)| s.is_valid())
            .map(|(slot, item)| (slot, item.item_id, item.amount))
            .collect()
    }

    /// Get items as a slice
    pub fn as_slice(&self) -> &[InventoryItem] {
        &self.slots
    }

    /// Convert to persistence format
    pub fn to_persistence_format(&self) -> Vec<Option<(i32, i32)>> {
        self.slots
            .iter()
            .map(|item| {
                if item.is_valid() {
                    Some((item.item_id as i32, item.amount as i32))
                } else {
                    None
                }
            })
            .collect()
    }

    /// Check if there's room for an item
    /// For stackable items, checks if there's an existing stack or empty slot
    /// For non-stackable items, checks if there are enough empty slots
    pub fn has_room_for(&self, item_id: u16, amount: u32, stackable: bool) -> bool {
        if stackable {
            // Check if we already have this item and can stack more
            if let Some(slot) = self.find_item(item_id) {
                let current = self.slots[slot].amount;
                if (current as u64) + (amount as u64) <= MAX_STACK_SIZE as u64 {
                    return true;
                }
            }
            // Otherwise need an empty slot
            self.find_empty_slot().is_some()
        } else {
            // Need 'amount' empty slots
            self.free_slots() >= amount as usize
        }
    }
}

/// Inventory item info for client communication
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InventoryItemInfo {
    pub slot: u8,
    pub item_id: u16,
    pub amount: u32,
}

/// Inventory operation errors
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum InventoryError {
    /// Inventory is full
    InventoryFull,
    /// Invalid slot index
    InvalidSlot,
    /// Slot is empty
    EmptySlot,
    /// Invalid amount (0 or negative)
    InvalidAmount,
    /// Item not found
    ItemNotFound,
    /// Not enough of the item
    InsufficientItems,
    /// Stack overflow (would exceed max stack)
    StackOverflow,
}

impl std::fmt::Display for InventoryError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            InventoryError::InventoryFull => write!(f, "Inventory is full"),
            InventoryError::InvalidSlot => write!(f, "Invalid inventory slot"),
            InventoryError::EmptySlot => write!(f, "Inventory slot is empty"),
            InventoryError::InvalidAmount => write!(f, "Invalid amount"),
            InventoryError::ItemNotFound => write!(f, "Item not found in inventory"),
            InventoryError::InsufficientItems => write!(f, "Not enough of that item"),
            InventoryError::StackOverflow => write!(f, "Cannot stack that many items"),
        }
    }
}

impl std::error::Error for InventoryError {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_inventory_item_creation() {
        let item = InventoryItem::new(1234, 5);
        assert_eq!(item.item_id, 1234);
        assert_eq!(item.amount, 5);
        assert!(item.is_valid());
        assert!(!item.is_empty());
    }

    #[test]
    fn test_inventory_item_empty() {
        let item = InventoryItem::empty();
        assert_eq!(item.item_id, 0);
        assert_eq!(item.amount, 0);
        assert!(!item.is_valid());
        assert!(item.is_empty());
    }

    #[test]
    fn test_inventory_creation() {
        let inv = Inventory::new();
        assert_eq!(inv.item_count(), 0);
        assert_eq!(inv.free_slots(), 28);
        assert!(inv.is_empty());
        assert!(!inv.is_full());
    }

    #[test]
    fn test_inventory_add_stackable() {
        let mut inv = Inventory::new();

        // Add first stack
        let result = inv.add(1234, 100, true);
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), 0); // First slot

        // Add more to same stack
        let result = inv.add(1234, 50, true);
        assert!(result.is_ok());
        assert_eq!(inv.get(0).unwrap().amount, 150);

        // Only one slot used
        assert_eq!(inv.item_count(), 1);
    }

    #[test]
    fn test_inventory_add_non_stackable() {
        let mut inv = Inventory::new();

        // Add 5 non-stackable items
        let result = inv.add(1234, 5, false);
        assert!(result.is_ok());

        // Should use 5 slots
        assert_eq!(inv.item_count(), 5);

        // Each slot should have amount 1
        for i in 0..5 {
            let item = inv.get(i).unwrap();
            assert_eq!(item.item_id, 1234);
            assert_eq!(item.amount, 1);
        }
    }

    #[test]
    fn test_inventory_full() {
        let mut inv = Inventory::new();

        // Fill inventory with non-stackable items
        for i in 0..28 {
            let _ = inv.set(i, (i + 1) as u16, 1);
        }

        assert!(inv.is_full());
        assert_eq!(inv.free_slots(), 0);

        // Try to add more
        let result = inv.add(9999, 1, false);
        assert!(result.is_err());
        assert_eq!(result.unwrap_err(), 1); // 1 item couldn't be added
    }

    #[test]
    fn test_inventory_remove() {
        let mut inv = Inventory::new();
        let _ = inv.add(1234, 100, true);

        // Remove some
        let result = inv.remove(0, 30);
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), 30);
        assert_eq!(inv.get(0).unwrap().amount, 70);

        // Remove all remaining
        let result = inv.remove(0, 70);
        assert!(result.is_ok());
        assert!(inv.get(0).is_none()); // Slot should be empty now
    }

    #[test]
    fn test_inventory_remove_item_by_id() {
        let mut inv = Inventory::new();

        // Add items to multiple slots (non-stackable)
        let _ = inv.add(1234, 5, false);

        // Remove 3 of them
        let removed = inv.remove_item(1234, 3);
        assert_eq!(removed, 3);
        assert_eq!(inv.count_item(1234), 2);
    }

    #[test]
    fn test_inventory_swap() {
        let mut inv = Inventory::new();
        let _ = inv.set(0, 1111, 1);
        let _ = inv.set(5, 2222, 1);

        // Swap slots 0 and 5
        let result = inv.swap(0, 5);
        assert!(result.is_ok());

        assert_eq!(inv.get(0).unwrap().item_id, 2222);
        assert_eq!(inv.get(5).unwrap().item_id, 1111);
    }

    #[test]
    fn test_inventory_compact() {
        let mut inv = Inventory::new();
        let _ = inv.set(2, 1111, 1);
        let _ = inv.set(5, 2222, 1);
        let _ = inv.set(10, 3333, 1);

        inv.compact();

        // Items should now be at slots 0, 1, 2
        assert_eq!(inv.get(0).unwrap().item_id, 1111);
        assert_eq!(inv.get(1).unwrap().item_id, 2222);
        assert_eq!(inv.get(2).unwrap().item_id, 3333);
        assert!(inv.get(3).is_none());
    }

    #[test]
    fn test_inventory_contains() {
        let mut inv = Inventory::new();
        let _ = inv.add(1234, 100, true);

        assert!(inv.contains(1234, 50));
        assert!(inv.contains(1234, 100));
        assert!(!inv.contains(1234, 101));
        assert!(!inv.contains(9999, 1));
    }

    #[test]
    fn test_inventory_find_item() {
        let mut inv = Inventory::new();
        let _ = inv.set(5, 1234, 10);

        assert_eq!(inv.find_item(1234), Some(5));
        assert_eq!(inv.find_item(9999), None);
    }

    #[test]
    fn test_inventory_has_room_for() {
        let mut inv = Inventory::new();

        // Empty inventory has room
        assert!(inv.has_room_for(1234, 100, true));
        assert!(inv.has_room_for(1234, 28, false));
        assert!(!inv.has_room_for(1234, 29, false)); // Too many non-stackable

        // Fill most slots
        for i in 0..27 {
            let _ = inv.set(i, (i + 1) as u16, 1);
        }

        // One slot left
        assert!(inv.has_room_for(9999, 1, false));
        assert!(!inv.has_room_for(9999, 2, false));
        assert!(inv.has_room_for(9999, 1000, true)); // Stackable only needs 1 slot
    }

    #[test]
    fn test_inventory_insert() {
        let mut inv = Inventory::new();
        let _ = inv.set(0, 1111, 1);
        let _ = inv.set(1, 2222, 1);
        let _ = inv.set(2, 3333, 1);

        // Move item from slot 0 to slot 2
        let result = inv.insert(0, 2);
        assert!(result.is_ok());

        // Items should now be: 2222, 3333, 1111
        assert_eq!(inv.get(0).unwrap().item_id, 2222);
        assert_eq!(inv.get(1).unwrap().item_id, 3333);
        assert_eq!(inv.get(2).unwrap().item_id, 1111);
    }

    #[test]
    fn test_inventory_from_persistence() {
        let items: Vec<Option<(i32, i32)>> = vec![Some((1234, 100)), None, Some((5678, 50))];

        let inv = Inventory::from_items(&items);

        assert_eq!(inv.get(0).unwrap().item_id, 1234);
        assert_eq!(inv.get(0).unwrap().amount, 100);
        assert!(inv.get(1).is_none());
        assert_eq!(inv.get(2).unwrap().item_id, 5678);
        assert_eq!(inv.get(2).unwrap().amount, 50);
    }

    #[test]
    fn test_inventory_to_persistence() {
        let mut inv = Inventory::new();
        let _ = inv.set(0, 1234, 100);
        let _ = inv.set(2, 5678, 50);

        let persisted = inv.to_persistence_format();

        assert_eq!(persisted.len(), 28);
        assert_eq!(persisted[0], Some((1234, 100)));
        assert_eq!(persisted[1], None);
        assert_eq!(persisted[2], Some((5678, 50)));
    }

    #[test]
    fn test_inventory_clear() {
        let mut inv = Inventory::new();
        let _ = inv.add(1234, 10, true);
        let _ = inv.add(5678, 5, false);

        assert!(!inv.is_empty());

        inv.clear();

        assert!(inv.is_empty());
        assert_eq!(inv.item_count(), 0);
    }

    #[test]
    fn test_inventory_stack_overflow() {
        let mut inv = Inventory::new();

        // Add max stack
        let _ = inv.add(1234, MAX_STACK_SIZE, true);
        assert_eq!(inv.get(0).unwrap().amount, MAX_STACK_SIZE);

        // Try to add more - should fail with overflow
        let result = inv.add(1234, 100, true);
        assert!(result.is_err());
        assert_eq!(result.unwrap_err(), 100); // All 100 couldn't be added
    }
}
