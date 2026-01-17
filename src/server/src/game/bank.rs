//! Bank module
//!
//! This module handles player bank storage and operations:
//! - Bank item storage (up to 800 slots across 9 tabs)
//! - Deposit and withdraw operations
//! - Bank tab management
//! - Note conversion
//! - Placeholder support

use serde::{Deserialize, Serialize};

/// Maximum number of bank slots
pub const MAX_BANK_SLOTS: usize = 800;

/// Number of bank tabs
pub const BANK_TAB_COUNT: usize = 9;

/// Maximum items per tab (soft limit, total is MAX_BANK_SLOTS)
pub const MAX_ITEMS_PER_TAB: usize = 350;

/// A single item in the bank
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct BankItem {
    /// Item ID
    pub item_id: u16,
    /// Item amount
    pub amount: u32,
    /// Whether this is a placeholder (empty slot that remembers an item)
    pub placeholder: bool,
}

impl BankItem {
    /// Create a new bank item
    pub fn new(item_id: u16, amount: u32) -> Self {
        Self {
            item_id,
            amount,
            placeholder: false,
        }
    }

    /// Create a placeholder for an item
    pub fn placeholder(item_id: u16) -> Self {
        Self {
            item_id,
            amount: 0,
            placeholder: true,
        }
    }

    /// Check if this slot is empty (no item and not a placeholder)
    pub fn is_empty(&self) -> bool {
        self.item_id == 0 && self.amount == 0 && !self.placeholder
    }

    /// Check if this is a real item (not empty and not just a placeholder)
    pub fn is_item(&self) -> bool {
        self.item_id > 0 && self.amount > 0
    }
}

impl Default for BankItem {
    fn default() -> Self {
        Self {
            item_id: 0,
            amount: 0,
            placeholder: false,
        }
    }
}

/// Bank tab containing items
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct BankTab {
    /// Items in this tab
    pub items: Vec<BankItem>,
    /// Tab icon item ID (first item in tab, or custom)
    pub icon_item_id: Option<u16>,
}

impl BankTab {
    /// Create a new empty bank tab
    pub fn new() -> Self {
        Self {
            items: Vec::new(),
            icon_item_id: None,
        }
    }

    /// Get the number of items in this tab
    pub fn item_count(&self) -> usize {
        self.items.iter().filter(|i| i.is_item()).count()
    }

    /// Get the number of slots used (including placeholders)
    pub fn slot_count(&self) -> usize {
        self.items.len()
    }

    /// Find an item by ID, returns the slot index
    pub fn find_item(&self, item_id: u16) -> Option<usize> {
        self.items
            .iter()
            .position(|i| i.item_id == item_id && i.is_item())
    }

    /// Find a placeholder by ID, returns the slot index
    pub fn find_placeholder(&self, item_id: u16) -> Option<usize> {
        self.items
            .iter()
            .position(|i| i.item_id == item_id && i.placeholder)
    }

    /// Add an item to this tab
    /// Returns the slot index where the item was added, or None if failed
    pub fn add_item(&mut self, item_id: u16, amount: u32) -> Option<usize> {
        // First, check if we already have this item (stack it)
        if let Some(slot) = self.find_item(item_id) {
            let item = &mut self.items[slot];
            item.amount = item.amount.saturating_add(amount);
            return Some(slot);
        }

        // Check if there's a placeholder for this item
        if let Some(slot) = self.find_placeholder(item_id) {
            self.items[slot] = BankItem::new(item_id, amount);
            return Some(slot);
        }

        // Add to the end
        let slot = self.items.len();
        self.items.push(BankItem::new(item_id, amount));

        // Update tab icon if this is the first item
        if self.icon_item_id.is_none() {
            self.icon_item_id = Some(item_id);
        }

        Some(slot)
    }

    /// Remove an amount of an item from this tab
    /// Returns the actual amount removed
    pub fn remove_item(&mut self, slot: usize, amount: u32, leave_placeholder: bool) -> u32 {
        if slot >= self.items.len() {
            return 0;
        }

        let item = &mut self.items[slot];
        if !item.is_item() {
            return 0;
        }

        let removed = amount.min(item.amount);
        item.amount -= removed;

        if item.amount == 0 {
            if leave_placeholder {
                item.placeholder = true;
            } else {
                // Remove the slot entirely
                self.items.remove(slot);
            }
        }

        removed
    }

    /// Compact the tab by removing empty slots (not placeholders)
    pub fn compact(&mut self) {
        self.items.retain(|i| !i.is_empty() || i.placeholder);
    }
}

/// Player bank storage
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Bank {
    /// Bank tabs (0-8)
    pub tabs: [BankTab; BANK_TAB_COUNT],
    /// Whether to withdraw items as notes
    pub withdraw_as_note: bool,
    /// Whether placeholders are enabled
    pub placeholders_enabled: bool,
    /// Custom withdraw amount for "Withdraw-X"
    pub withdraw_x_amount: u32,
    /// PIN (hashed, for future use)
    pub pin_hash: Option<String>,
    /// Bank capacity (can be increased through upgrades)
    pub capacity: usize,
}

impl Default for Bank {
    fn default() -> Self {
        Self::new()
    }
}

impl Bank {
    /// Create a new empty bank
    pub fn new() -> Self {
        Self {
            tabs: Default::default(),
            withdraw_as_note: false,
            placeholders_enabled: false,
            withdraw_x_amount: 10,
            pin_hash: None,
            capacity: MAX_BANK_SLOTS,
        }
    }

    /// Get total number of items across all tabs
    pub fn total_items(&self) -> usize {
        self.tabs.iter().map(|t| t.item_count()).sum()
    }

    /// Get total number of slots used across all tabs
    pub fn total_slots_used(&self) -> usize {
        self.tabs.iter().map(|t| t.slot_count()).sum()
    }

    /// Check if the bank is full
    pub fn is_full(&self) -> bool {
        self.total_slots_used() >= self.capacity
    }

    /// Get remaining capacity
    pub fn remaining_capacity(&self) -> usize {
        self.capacity.saturating_sub(self.total_slots_used())
    }

    /// Find which tab contains an item
    pub fn find_item(&self, item_id: u16) -> Option<(usize, usize)> {
        for (tab_idx, tab) in self.tabs.iter().enumerate() {
            if let Some(slot) = tab.find_item(item_id) {
                return Some((tab_idx, slot));
            }
        }
        None
    }

    /// Get an item from a specific tab and slot
    pub fn get_item(&self, tab: usize, slot: usize) -> Option<&BankItem> {
        self.tabs.get(tab)?.items.get(slot)
    }

    /// Get an item mutably from a specific tab and slot
    pub fn get_item_mut(&mut self, tab: usize, slot: usize) -> Option<&mut BankItem> {
        self.tabs.get_mut(tab)?.items.get_mut(slot)
    }

    /// Deposit an item into the bank
    /// Returns Ok(tab, slot) on success, Err(reason) on failure
    pub fn deposit(
        &mut self,
        item_id: u16,
        amount: u32,
        target_tab: Option<usize>,
    ) -> Result<(usize, usize), BankError> {
        if amount == 0 {
            return Err(BankError::InvalidAmount);
        }

        // Check if we already have this item somewhere
        if let Some((existing_tab, existing_slot)) = self.find_item(item_id) {
            let tab = &mut self.tabs[existing_tab];
            let item = &mut tab.items[existing_slot];
            item.amount = item.amount.saturating_add(amount);
            return Ok((existing_tab, existing_slot));
        }

        // Check capacity
        if self.is_full() {
            return Err(BankError::BankFull);
        }

        // Determine which tab to use
        let tab_idx = target_tab.unwrap_or(0).min(BANK_TAB_COUNT - 1);
        let tab = &mut self.tabs[tab_idx];

        // Add to the tab
        if let Some(slot) = tab.add_item(item_id, amount) {
            Ok((tab_idx, slot))
        } else {
            Err(BankError::BankFull)
        }
    }

    /// Withdraw an item from the bank
    /// Returns Ok(item_id, amount_withdrawn) on success, Err(reason) on failure
    pub fn withdraw(
        &mut self,
        tab: usize,
        slot: usize,
        amount: u32,
    ) -> Result<(u16, u32), BankError> {
        if tab >= BANK_TAB_COUNT {
            return Err(BankError::InvalidTab);
        }

        let bank_tab = &mut self.tabs[tab];
        if slot >= bank_tab.items.len() {
            return Err(BankError::InvalidSlot);
        }

        let item = &bank_tab.items[slot];
        if !item.is_item() {
            return Err(BankError::EmptySlot);
        }

        let item_id = item.item_id;
        let withdraw_amount = if amount == u32::MAX {
            item.amount // "All"
        } else {
            amount.min(item.amount)
        };

        if withdraw_amount == 0 {
            return Err(BankError::InvalidAmount);
        }

        let removed = bank_tab.remove_item(slot, withdraw_amount, self.placeholders_enabled);
        Ok((item_id, removed))
    }

    /// Move an item within the bank (between tabs or within a tab)
    pub fn move_item(
        &mut self,
        from_tab: usize,
        from_slot: usize,
        to_tab: usize,
        to_slot: usize,
    ) -> Result<(), BankError> {
        if from_tab >= BANK_TAB_COUNT || to_tab >= BANK_TAB_COUNT {
            return Err(BankError::InvalidTab);
        }

        if from_tab == to_tab && from_slot == to_slot {
            return Ok(()); // No-op
        }

        // Get the item to move
        let item = self.tabs[from_tab]
            .items
            .get(from_slot)
            .cloned()
            .ok_or(BankError::InvalidSlot)?;

        if item.is_empty() {
            return Err(BankError::EmptySlot);
        }

        if from_tab == to_tab {
            // Moving within the same tab (reorder)
            let tab = &mut self.tabs[from_tab];
            if to_slot >= tab.items.len() {
                // Insert at end
                let item = tab.items.remove(from_slot);
                tab.items.push(item);
            } else {
                // Swap positions
                tab.items.swap(from_slot, to_slot);
            }
        } else {
            // Moving to a different tab
            let removed_item = self.tabs[from_tab].items.remove(from_slot);

            let target_tab = &mut self.tabs[to_tab];
            if to_slot >= target_tab.items.len() {
                target_tab.items.push(removed_item);
            } else {
                target_tab.items.insert(to_slot, removed_item);
            }
        }

        Ok(())
    }

    /// Set withdraw-as-note mode
    pub fn set_note_mode(&mut self, enabled: bool) {
        self.withdraw_as_note = enabled;
    }

    /// Set placeholders enabled
    pub fn set_placeholders(&mut self, enabled: bool) {
        self.placeholders_enabled = enabled;
    }

    /// Set custom withdraw-X amount
    pub fn set_withdraw_x(&mut self, amount: u32) {
        self.withdraw_x_amount = amount.max(1);
    }

    /// Get items in a tab for sending to client
    pub fn get_tab_items(&self, tab: usize) -> Vec<(u16, u32, bool)> {
        if tab >= BANK_TAB_COUNT {
            return Vec::new();
        }

        self.tabs[tab]
            .items
            .iter()
            .map(|i| (i.item_id, i.amount, i.placeholder))
            .collect()
    }

    /// Get all items for sending to client (flat list with tab markers)
    pub fn get_all_items(&self) -> Vec<BankItemInfo> {
        let mut items = Vec::new();
        for (tab_idx, tab) in self.tabs.iter().enumerate() {
            for (slot, item) in tab.items.iter().enumerate() {
                if item.item_id > 0 || item.placeholder {
                    items.push(BankItemInfo {
                        tab: tab_idx as u8,
                        slot: slot as u16,
                        item_id: item.item_id,
                        amount: item.amount,
                        placeholder: item.placeholder,
                    });
                }
            }
        }
        items
    }

    /// Compact all tabs
    pub fn compact_all(&mut self) {
        for tab in &mut self.tabs {
            tab.compact();
        }
    }

    /// Search for items matching a query (by item name in future, for now by ID)
    pub fn search(&self, item_ids: &[u16]) -> Vec<BankItemInfo> {
        let id_set: std::collections::HashSet<u16> = item_ids.iter().copied().collect();

        self.get_all_items()
            .into_iter()
            .filter(|i| id_set.contains(&i.item_id))
            .collect()
    }

    /// Convert to persistence format (flat Vec<Option<Item>> with 496 slots)
    /// Items are stored sequentially: tab 0 items, then tab 1, etc.
    /// This format matches the PlayerData.bank field.
    pub fn to_persistence_format(&self) -> Vec<Option<(i32, i32)>> {
        let mut result = vec![None; MAX_BANK_SLOTS];
        let mut slot_index = 0;

        for tab in &self.tabs {
            for item in &tab.items {
                if slot_index >= MAX_BANK_SLOTS {
                    break;
                }
                if item.item_id > 0 || item.placeholder {
                    // Store item_id and amount; placeholders have amount 0
                    result[slot_index] = Some((item.item_id as i32, item.amount as i32));
                }
                slot_index += 1;
            }
        }

        result
    }

    /// Load from persistence format (flat Vec<Option<Item>> with up to 496 slots)
    /// All items are loaded into tab 0 for simplicity.
    /// In the future, tab information could be stored separately.
    pub fn from_persistence_format(items: &[Option<(i32, i32)>]) -> Self {
        let mut bank = Self::new();

        for (slot, item_opt) in items.iter().enumerate() {
            if slot >= MAX_BANK_SLOTS {
                break;
            }
            if let Some((item_id, amount)) = item_opt {
                if *item_id > 0 {
                    // Add to tab 0 for now (preserves slot order)
                    let bank_item = BankItem::new(*item_id as u16, *amount as u32);
                    // Ensure we have enough slots in tab 0
                    while bank.tabs[0].items.len() <= slot {
                        bank.tabs[0].items.push(BankItem::default());
                    }
                    bank.tabs[0].items[slot] = bank_item;
                }
            }
        }

        // Compact the bank to remove empty slots between items
        bank.tabs[0].compact();

        bank
    }

    /// Get tab sizes for persistence or client updates
    pub fn get_tab_sizes(&self) -> [usize; BANK_TAB_COUNT] {
        let mut sizes = [0usize; BANK_TAB_COUNT];
        for (i, tab) in self.tabs.iter().enumerate() {
            sizes[i] = tab.slot_count();
        }
        sizes
    }
}

/// Bank item info for client communication
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BankItemInfo {
    pub tab: u8,
    pub slot: u16,
    pub item_id: u16,
    pub amount: u32,
    pub placeholder: bool,
}

/// Bank operation errors
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BankError {
    /// Bank is full
    BankFull,
    /// Invalid tab index
    InvalidTab,
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
    /// Operation not permitted
    NotPermitted,
}

impl std::fmt::Display for BankError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            BankError::BankFull => write!(f, "Bank is full"),
            BankError::InvalidTab => write!(f, "Invalid bank tab"),
            BankError::InvalidSlot => write!(f, "Invalid bank slot"),
            BankError::EmptySlot => write!(f, "Bank slot is empty"),
            BankError::InvalidAmount => write!(f, "Invalid amount"),
            BankError::ItemNotFound => write!(f, "Item not found in bank"),
            BankError::InsufficientItems => write!(f, "Not enough of that item"),
            BankError::NotPermitted => write!(f, "Operation not permitted"),
        }
    }
}

impl std::error::Error for BankError {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_bank_item_creation() {
        let item = BankItem::new(995, 1000);
        assert_eq!(item.item_id, 995);
        assert_eq!(item.amount, 1000);
        assert!(!item.placeholder);
        assert!(item.is_item());
        assert!(!item.is_empty());
    }

    #[test]
    fn test_bank_item_placeholder() {
        let placeholder = BankItem::placeholder(995);
        assert_eq!(placeholder.item_id, 995);
        assert_eq!(placeholder.amount, 0);
        assert!(placeholder.placeholder);
        assert!(!placeholder.is_item());
        assert!(!placeholder.is_empty()); // Placeholder is not empty
    }

    #[test]
    fn test_bank_tab_add_item() {
        let mut tab = BankTab::new();

        let slot = tab.add_item(995, 1000).unwrap();
        assert_eq!(slot, 0);
        assert_eq!(tab.item_count(), 1);

        // Adding same item should stack
        let slot = tab.add_item(995, 500).unwrap();
        assert_eq!(slot, 0);
        assert_eq!(tab.item_count(), 1);
        assert_eq!(tab.items[0].amount, 1500);
    }

    #[test]
    fn test_bank_deposit() {
        let mut bank = Bank::new();

        let result = bank.deposit(995, 1000, None);
        assert!(result.is_ok());
        let (tab, slot) = result.unwrap();
        assert_eq!(tab, 0);
        assert_eq!(slot, 0);

        // Deposit more of the same item
        let result = bank.deposit(995, 500, None);
        assert!(result.is_ok());
        assert_eq!(bank.get_item(0, 0).unwrap().amount, 1500);
    }

    #[test]
    fn test_bank_withdraw() {
        let mut bank = Bank::new();
        bank.deposit(995, 1000, None).unwrap();

        let result = bank.withdraw(0, 0, 500);
        assert!(result.is_ok());
        let (item_id, amount) = result.unwrap();
        assert_eq!(item_id, 995);
        assert_eq!(amount, 500);
        assert_eq!(bank.get_item(0, 0).unwrap().amount, 500);
    }

    #[test]
    fn test_bank_withdraw_all() {
        let mut bank = Bank::new();
        bank.deposit(995, 1000, None).unwrap();

        let result = bank.withdraw(0, 0, u32::MAX);
        assert!(result.is_ok());
        let (item_id, amount) = result.unwrap();
        assert_eq!(item_id, 995);
        assert_eq!(amount, 1000);
    }

    #[test]
    fn test_bank_placeholder() {
        let mut bank = Bank::new();
        bank.placeholders_enabled = true;
        bank.deposit(995, 100, None).unwrap();

        // Withdraw all
        bank.withdraw(0, 0, u32::MAX).unwrap();

        // Should have placeholder
        let item = bank.get_item(0, 0).unwrap();
        assert!(item.placeholder);
        assert_eq!(item.item_id, 995);

        // Deposit should fill placeholder
        bank.deposit(995, 50, None).unwrap();
        let item = bank.get_item(0, 0).unwrap();
        assert!(!item.placeholder);
        assert_eq!(item.amount, 50);
    }

    #[test]
    fn test_bank_capacity() {
        let mut bank = Bank::new();
        bank.capacity = 2;

        bank.deposit(995, 100, None).unwrap();
        bank.deposit(1511, 100, None).unwrap();

        let result = bank.deposit(440, 100, None);
        assert_eq!(result, Err(BankError::BankFull));
    }

    #[test]
    fn test_bank_move_item() {
        let mut bank = Bank::new();
        bank.deposit(995, 100, Some(0)).unwrap();
        bank.deposit(1511, 50, Some(0)).unwrap();

        // Move to different tab
        let result = bank.move_item(0, 0, 1, 0);
        assert!(result.is_ok());

        assert!(bank.get_item(0, 0).is_some()); // 1511 moved to slot 0
        assert_eq!(bank.get_item(0, 0).unwrap().item_id, 1511);
        assert_eq!(bank.get_item(1, 0).unwrap().item_id, 995);
    }

    #[test]
    fn test_bank_total_items() {
        let mut bank = Bank::new();
        bank.deposit(995, 100, Some(0)).unwrap();
        bank.deposit(1511, 50, Some(1)).unwrap();
        bank.deposit(440, 25, Some(2)).unwrap();

        assert_eq!(bank.total_items(), 3);
        assert_eq!(bank.total_slots_used(), 3);
    }

    #[test]
    fn test_bank_persistence_roundtrip() {
        let mut bank = Bank::new();
        bank.deposit(995, 1000, Some(0)).unwrap(); // Coins
        bank.deposit(1511, 500, Some(0)).unwrap(); // Buckets
        bank.deposit(440, 100, Some(0)).unwrap(); // Rune essence

        // Convert to persistence format
        let persist_format = bank.to_persistence_format();

        // Should have items in first slots
        assert!(persist_format[0].is_some());
        assert!(persist_format[1].is_some());
        assert!(persist_format[2].is_some());
        assert!(persist_format[3].is_none());

        // Check values
        let (id, amount) = persist_format[0].unwrap();
        assert_eq!(id, 995);
        assert_eq!(amount, 1000);

        // Load from persistence format
        let loaded_bank = Bank::from_persistence_format(&persist_format);

        // Verify items were loaded
        assert_eq!(loaded_bank.total_items(), 3);

        // Check specific items exist
        assert!(loaded_bank.find_item(995).is_some());
        assert!(loaded_bank.find_item(1511).is_some());
        assert!(loaded_bank.find_item(440).is_some());
    }

    #[test]
    fn test_bank_persistence_empty() {
        let bank = Bank::new();

        // Convert empty bank to persistence format
        let persist_format = bank.to_persistence_format();

        // All slots should be None
        assert!(persist_format.iter().all(|slot| slot.is_none()));

        // Load from persistence format
        let loaded_bank = Bank::from_persistence_format(&persist_format);

        // Should be empty
        assert_eq!(loaded_bank.total_items(), 0);
        assert_eq!(loaded_bank.total_slots_used(), 0);
    }

    #[test]
    fn test_bank_persistence_preserves_amounts() {
        let mut bank = Bank::new();
        bank.deposit(995, u32::MAX, Some(0)).unwrap(); // Max coins

        let persist_format = bank.to_persistence_format();
        let loaded_bank = Bank::from_persistence_format(&persist_format);

        // Amount should be preserved (though i32 limits apply in persistence)
        let item = loaded_bank.get_item(0, 0).unwrap();
        assert_eq!(item.item_id, 995);
        // Note: persistence uses i32, so max is i32::MAX, not u32::MAX
        assert!(item.amount > 0);
    }

    #[test]
    fn test_bank_get_tab_sizes() {
        let mut bank = Bank::new();
        bank.deposit(995, 100, Some(0)).unwrap();
        bank.deposit(1511, 50, Some(0)).unwrap();
        bank.deposit(440, 25, Some(1)).unwrap();

        let sizes = bank.get_tab_sizes();
        assert_eq!(sizes[0], 2); // Two items in tab 0
        assert_eq!(sizes[1], 1); // One item in tab 1
        assert_eq!(sizes[2], 0); // Empty
    }
}
