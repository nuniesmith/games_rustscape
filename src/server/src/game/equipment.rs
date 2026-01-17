//! Equipment module
//!
//! This module handles player equipment:
//! - 14 equipment slots (head, cape, amulet, weapon, body, shield, legs, hands, feet, ring, ammo, etc.)
//! - Equip and unequip operations
//! - Equipment bonuses calculation
//! - Two-handed weapon handling
//! - Equipment requirement validation

use serde::{Deserialize, Serialize};

use super::item::{get_item, is_equippable, EquipmentSlot, ItemDefinition};

/// Number of equipment slots
pub const EQUIPMENT_SLOT_COUNT: usize = 14;

/// Equipment slot indices (matching RS protocol)
pub mod slot_index {
    pub const HEAD: usize = 0;
    pub const CAPE: usize = 1;
    pub const AMULET: usize = 2;
    pub const WEAPON: usize = 3;
    pub const BODY: usize = 4;
    pub const SHIELD: usize = 5;
    pub const _UNUSED_6: usize = 6;
    pub const LEGS: usize = 7;
    pub const _UNUSED_8: usize = 8;
    pub const HANDS: usize = 9;
    pub const FEET: usize = 10;
    pub const _UNUSED_11: usize = 11;
    pub const RING: usize = 12;
    pub const AMMO: usize = 13;
}

/// Bonus indices for equipment stats
pub mod bonus_index {
    pub const STAB_ATTACK: usize = 0;
    pub const SLASH_ATTACK: usize = 1;
    pub const CRUSH_ATTACK: usize = 2;
    pub const MAGIC_ATTACK: usize = 3;
    pub const RANGE_ATTACK: usize = 4;
    pub const STAB_DEFENCE: usize = 5;
    pub const SLASH_DEFENCE: usize = 6;
    pub const CRUSH_DEFENCE: usize = 7;
    pub const MAGIC_DEFENCE: usize = 8;
    pub const RANGE_DEFENCE: usize = 9;
    pub const STRENGTH: usize = 10;
    pub const RANGED_STRENGTH: usize = 11;
    pub const MAGIC_DAMAGE: usize = 12;
    pub const PRAYER: usize = 13;
}

/// A single equipment slot
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq, Eq)]
pub struct EquipmentItem {
    /// Item ID (0 = empty)
    pub item_id: u16,
    /// Amount (for stackable items like arrows)
    pub amount: u32,
}

impl EquipmentItem {
    /// Create a new equipment item
    pub fn new(item_id: u16, amount: u32) -> Self {
        Self { item_id, amount }
    }

    /// Create an empty equipment slot
    pub fn empty() -> Self {
        Self {
            item_id: 0,
            amount: 0,
        }
    }

    /// Check if this slot is empty
    pub fn is_empty(&self) -> bool {
        self.item_id == 0
    }

    /// Check if this slot contains an item
    pub fn has_item(&self) -> bool {
        self.item_id > 0 && self.amount > 0
    }
}

impl Default for EquipmentItem {
    fn default() -> Self {
        Self::empty()
    }
}

/// Player equipment container
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Equipment {
    /// Equipment slots (14 total)
    slots: [EquipmentItem; EQUIPMENT_SLOT_COUNT],
    /// Cached total bonuses (recalculated when equipment changes)
    #[serde(skip)]
    cached_bonuses: [i16; 14],
    /// Whether the bonus cache is valid
    #[serde(skip)]
    bonuses_dirty: bool,
}

impl Default for Equipment {
    fn default() -> Self {
        Self::new()
    }
}

impl Equipment {
    /// Create new empty equipment
    pub fn new() -> Self {
        Self {
            slots: [EquipmentItem::empty(); EQUIPMENT_SLOT_COUNT],
            cached_bonuses: [0; 14],
            bonuses_dirty: true,
        }
    }

    /// Get an item in a slot
    pub fn get(&self, slot: usize) -> Option<&EquipmentItem> {
        if slot < EQUIPMENT_SLOT_COUNT {
            Some(&self.slots[slot])
        } else {
            None
        }
    }

    /// Get an item in a slot mutably
    pub fn get_mut(&mut self, slot: usize) -> Option<&mut EquipmentItem> {
        if slot < EQUIPMENT_SLOT_COUNT {
            self.bonuses_dirty = true;
            Some(&mut self.slots[slot])
        } else {
            None
        }
    }

    /// Get item by equipment slot enum
    pub fn get_by_slot(&self, slot: EquipmentSlot) -> &EquipmentItem {
        &self.slots[slot as usize]
    }

    /// Check if a slot is empty
    pub fn is_slot_empty(&self, slot: usize) -> bool {
        self.slots
            .get(slot)
            .map(|item| item.is_empty())
            .unwrap_or(true)
    }

    /// Get the weapon slot item
    pub fn weapon(&self) -> &EquipmentItem {
        &self.slots[slot_index::WEAPON]
    }

    /// Get the shield slot item
    pub fn shield(&self) -> &EquipmentItem {
        &self.slots[slot_index::SHIELD]
    }

    /// Check if wearing a two-handed weapon
    pub fn has_two_handed_weapon(&self) -> bool {
        let weapon = &self.slots[slot_index::WEAPON];
        if weapon.is_empty() {
            return false;
        }

        get_item(weapon.item_id)
            .map(|def| def.two_handed)
            .unwrap_or(false)
    }

    /// Equip an item to the appropriate slot
    /// Returns the items that were unequipped (if any)
    pub fn equip(
        &mut self,
        item_id: u16,
        amount: u32,
        skill_levels: &[u8],
    ) -> Result<Vec<EquipmentItem>, EquipmentError> {
        // Check if item is equippable
        if !is_equippable(item_id) {
            return Err(EquipmentError::NotEquippable);
        }

        // Get item definition
        let item_def = get_item(item_id).ok_or(EquipmentError::ItemNotFound)?;

        // Get the equipment slot
        let slot = item_def
            .equipment_slot
            .ok_or(EquipmentError::NotEquippable)?;
        let slot_index = slot as usize;

        // Check requirements
        if !self.meets_requirements(&item_def, skill_levels) {
            return Err(EquipmentError::RequirementsNotMet);
        }

        let mut unequipped = Vec::new();

        // Handle two-handed weapons
        if item_def.two_handed {
            // Unequip shield if present
            if !self.slots[slot_index::SHIELD].is_empty() {
                unequipped.push(self.slots[slot_index::SHIELD]);
                self.slots[slot_index::SHIELD] = EquipmentItem::empty();
            }
        }

        // If equipping a shield, check if we have a two-handed weapon
        if slot == EquipmentSlot::Shield && self.has_two_handed_weapon() {
            // Unequip the two-handed weapon
            unequipped.push(self.slots[slot_index::WEAPON]);
            self.slots[slot_index::WEAPON] = EquipmentItem::empty();
        }

        // Unequip current item in slot (if any)
        if !self.slots[slot_index].is_empty() {
            // Check if it's the same stackable item (like ammo)
            let current = &self.slots[slot_index];
            if current.item_id == item_id && item_def.stackable {
                // Add to existing stack
                let new_amount = current.amount.saturating_add(amount);
                self.slots[slot_index].amount = new_amount;
                self.bonuses_dirty = true;
                return Ok(unequipped);
            }

            unequipped.push(self.slots[slot_index]);
        }

        // Equip the new item
        self.slots[slot_index] = EquipmentItem::new(item_id, amount);
        self.bonuses_dirty = true;

        Ok(unequipped)
    }

    /// Unequip an item from a slot
    /// Returns the unequipped item
    pub fn unequip(&mut self, slot: usize) -> Result<EquipmentItem, EquipmentError> {
        if slot >= EQUIPMENT_SLOT_COUNT {
            return Err(EquipmentError::InvalidSlot);
        }

        if self.slots[slot].is_empty() {
            return Err(EquipmentError::SlotEmpty);
        }

        let item = self.slots[slot];
        self.slots[slot] = EquipmentItem::empty();
        self.bonuses_dirty = true;

        Ok(item)
    }

    /// Unequip an item by slot enum
    pub fn unequip_slot(&mut self, slot: EquipmentSlot) -> Result<EquipmentItem, EquipmentError> {
        self.unequip(slot as usize)
    }

    /// Remove a specific amount from a slot (for ammo consumption)
    pub fn remove_amount(&mut self, slot: usize, amount: u32) -> Result<u32, EquipmentError> {
        if slot >= EQUIPMENT_SLOT_COUNT {
            return Err(EquipmentError::InvalidSlot);
        }

        let item = &mut self.slots[slot];
        if item.is_empty() {
            return Err(EquipmentError::SlotEmpty);
        }

        let removed = amount.min(item.amount);
        item.amount = item.amount.saturating_sub(amount);

        if item.amount == 0 {
            *item = EquipmentItem::empty();
        }

        self.bonuses_dirty = true;
        Ok(removed)
    }

    /// Check if the player meets the requirements for an item
    pub fn meets_requirements(&self, item_def: &ItemDefinition, skill_levels: &[u8]) -> bool {
        for &(skill_id, required_level) in &item_def.requirements {
            if let Some(&current_level) = skill_levels.get(skill_id as usize) {
                if current_level < required_level {
                    return false;
                }
            } else {
                // Unknown skill, assume requirement not met
                return false;
            }
        }
        true
    }

    /// Calculate total equipment bonuses
    pub fn calculate_bonuses(&mut self) -> &[i16; 14] {
        if !self.bonuses_dirty {
            return &self.cached_bonuses;
        }

        // Reset bonuses
        self.cached_bonuses = [0; 14];

        // Sum bonuses from all equipped items
        for item in &self.slots {
            if item.is_empty() {
                continue;
            }

            if let Some(def) = get_item(item.item_id) {
                for i in 0..14 {
                    self.cached_bonuses[i] = self.cached_bonuses[i].saturating_add(def.bonuses[i]);
                }
            }
        }

        self.bonuses_dirty = false;
        &self.cached_bonuses
    }

    /// Get cached bonuses (may be stale if equipment changed)
    pub fn bonuses(&self) -> &[i16; 14] {
        &self.cached_bonuses
    }

    /// Force recalculation of bonuses on next access
    pub fn invalidate_bonuses(&mut self) {
        self.bonuses_dirty = true;
    }

    /// Get attack bonus for a specific style
    pub fn attack_bonus(&mut self, style: AttackStyle) -> i16 {
        self.calculate_bonuses();
        match style {
            AttackStyle::Stab => self.cached_bonuses[bonus_index::STAB_ATTACK],
            AttackStyle::Slash => self.cached_bonuses[bonus_index::SLASH_ATTACK],
            AttackStyle::Crush => self.cached_bonuses[bonus_index::CRUSH_ATTACK],
            AttackStyle::Magic => self.cached_bonuses[bonus_index::MAGIC_ATTACK],
            AttackStyle::Ranged => self.cached_bonuses[bonus_index::RANGE_ATTACK],
        }
    }

    /// Get defence bonus for a specific style
    pub fn defence_bonus(&mut self, style: AttackStyle) -> i16 {
        self.calculate_bonuses();
        match style {
            AttackStyle::Stab => self.cached_bonuses[bonus_index::STAB_DEFENCE],
            AttackStyle::Slash => self.cached_bonuses[bonus_index::SLASH_DEFENCE],
            AttackStyle::Crush => self.cached_bonuses[bonus_index::CRUSH_DEFENCE],
            AttackStyle::Magic => self.cached_bonuses[bonus_index::MAGIC_DEFENCE],
            AttackStyle::Ranged => self.cached_bonuses[bonus_index::RANGE_DEFENCE],
        }
    }

    /// Get melee strength bonus
    pub fn strength_bonus(&mut self) -> i16 {
        self.calculate_bonuses();
        self.cached_bonuses[bonus_index::STRENGTH]
    }

    /// Get ranged strength bonus
    pub fn ranged_strength_bonus(&mut self) -> i16 {
        self.calculate_bonuses();
        self.cached_bonuses[bonus_index::RANGED_STRENGTH]
    }

    /// Get magic damage bonus (percentage)
    pub fn magic_damage_bonus(&mut self) -> i16 {
        self.calculate_bonuses();
        self.cached_bonuses[bonus_index::MAGIC_DAMAGE]
    }

    /// Get prayer bonus
    pub fn prayer_bonus(&mut self) -> i16 {
        self.calculate_bonuses();
        self.cached_bonuses[bonus_index::PRAYER]
    }

    /// Get current attack speed (from weapon, or default 4 for unarmed)
    pub fn attack_speed(&self) -> u8 {
        let weapon = &self.slots[slot_index::WEAPON];
        if weapon.is_empty() {
            return 4; // Default unarmed speed
        }

        get_item(weapon.item_id)
            .map(|def| {
                if def.attack_speed > 0 {
                    def.attack_speed
                } else {
                    4
                }
            })
            .unwrap_or(4)
    }

    /// Get all equipped items for sending to client
    pub fn get_all_items(&self) -> Vec<(usize, u16, u32)> {
        self.slots
            .iter()
            .enumerate()
            .filter(|(_, item)| item.has_item())
            .map(|(slot, item)| (slot, item.item_id, item.amount))
            .collect()
    }

    /// Get all slots (including empty) for full update
    pub fn as_slice(&self) -> &[EquipmentItem; EQUIPMENT_SLOT_COUNT] {
        &self.slots
    }

    /// Check if any equipment is worn
    pub fn is_empty(&self) -> bool {
        self.slots.iter().all(|item| item.is_empty())
    }

    /// Count number of equipped items
    pub fn count(&self) -> usize {
        self.slots.iter().filter(|item| item.has_item()).count()
    }

    /// Calculate total weight of equipped items
    pub fn total_weight(&self) -> f32 {
        self.slots
            .iter()
            .filter(|item| item.has_item())
            .filter_map(|item| get_item(item.item_id))
            .map(|def| def.weight)
            .sum()
    }

    /// Convert to persistence format
    pub fn to_persistence_format(&self) -> Vec<(usize, u16, u32)> {
        self.get_all_items()
    }

    /// Load from persistence format
    pub fn from_persistence_format(items: &[(usize, u16, u32)]) -> Self {
        let mut equipment = Self::new();
        for &(slot, item_id, amount) in items {
            if slot < EQUIPMENT_SLOT_COUNT && item_id > 0 {
                equipment.slots[slot] = EquipmentItem::new(item_id, amount);
            }
        }
        equipment.bonuses_dirty = true;
        equipment
    }
}

/// Attack style for bonus calculations
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AttackStyle {
    Stab,
    Slash,
    Crush,
    Magic,
    Ranged,
}

/// Equipment operation errors
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EquipmentError {
    /// Item cannot be equipped
    NotEquippable,
    /// Item not found in definitions
    ItemNotFound,
    /// Invalid equipment slot
    InvalidSlot,
    /// Slot is empty (for unequip)
    SlotEmpty,
    /// Player doesn't meet requirements
    RequirementsNotMet,
    /// Cannot equip (inventory full, etc.)
    CannotEquip,
}

impl std::fmt::Display for EquipmentError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::NotEquippable => write!(f, "This item cannot be equipped"),
            Self::ItemNotFound => write!(f, "Item not found"),
            Self::InvalidSlot => write!(f, "Invalid equipment slot"),
            Self::SlotEmpty => write!(f, "That slot is empty"),
            Self::RequirementsNotMet => write!(f, "You don't meet the requirements to wear this"),
            Self::CannotEquip => write!(f, "You cannot equip this item"),
        }
    }
}

impl std::error::Error for EquipmentError {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_equipment_item_creation() {
        let item = EquipmentItem::new(1277, 1); // Bronze sword
        assert_eq!(item.item_id, 1277);
        assert_eq!(item.amount, 1);
        assert!(!item.is_empty());
        assert!(item.has_item());
    }

    #[test]
    fn test_equipment_item_empty() {
        let item = EquipmentItem::empty();
        assert_eq!(item.item_id, 0);
        assert_eq!(item.amount, 0);
        assert!(item.is_empty());
        assert!(!item.has_item());
    }

    #[test]
    fn test_equipment_new() {
        let equipment = Equipment::new();
        assert!(equipment.is_empty());
        assert_eq!(equipment.count(), 0);
    }

    #[test]
    fn test_equipment_get_slot() {
        let equipment = Equipment::new();
        let slot = equipment.get(slot_index::WEAPON);
        assert!(slot.is_some());
        assert!(slot.unwrap().is_empty());

        let invalid = equipment.get(100);
        assert!(invalid.is_none());
    }

    #[test]
    fn test_equipment_slot_empty() {
        let equipment = Equipment::new();
        assert!(equipment.is_slot_empty(slot_index::HEAD));
        assert!(equipment.is_slot_empty(slot_index::WEAPON));
        assert!(equipment.is_slot_empty(slot_index::SHIELD));
    }

    #[test]
    fn test_equipment_unequip_empty_slot() {
        let mut equipment = Equipment::new();
        let result = equipment.unequip(slot_index::WEAPON);
        assert!(matches!(result, Err(EquipmentError::SlotEmpty)));
    }

    #[test]
    fn test_equipment_unequip_invalid_slot() {
        let mut equipment = Equipment::new();
        let result = equipment.unequip(100);
        assert!(matches!(result, Err(EquipmentError::InvalidSlot)));
    }

    #[test]
    fn test_equipment_get_all_items_empty() {
        let equipment = Equipment::new();
        let items = equipment.get_all_items();
        assert!(items.is_empty());
    }

    #[test]
    fn test_equipment_as_slice() {
        let equipment = Equipment::new();
        let slots = equipment.as_slice();
        assert_eq!(slots.len(), EQUIPMENT_SLOT_COUNT);
    }

    #[test]
    fn test_attack_speed_unarmed() {
        let equipment = Equipment::new();
        assert_eq!(equipment.attack_speed(), 4);
    }

    #[test]
    fn test_equipment_default_bonuses() {
        let mut equipment = Equipment::new();
        let bonuses = equipment.calculate_bonuses();
        assert_eq!(bonuses.len(), 14);
        assert!(bonuses.iter().all(|&b| b == 0));
    }

    #[test]
    fn test_equipment_persistence_roundtrip() {
        let mut equipment = Equipment::new();
        // Manually set a slot for testing
        equipment.slots[slot_index::HEAD] = EquipmentItem::new(1155, 1); // Bronze full helm

        let persistence = equipment.to_persistence_format();
        assert_eq!(persistence.len(), 1);
        assert_eq!(persistence[0], (slot_index::HEAD, 1155, 1));

        let restored = Equipment::from_persistence_format(&persistence);
        assert_eq!(restored.slots[slot_index::HEAD].item_id, 1155);
        assert_eq!(restored.slots[slot_index::HEAD].amount, 1);
    }

    #[test]
    fn test_equipment_count() {
        let mut equipment = Equipment::new();
        assert_eq!(equipment.count(), 0);

        equipment.slots[slot_index::HEAD] = EquipmentItem::new(1155, 1);
        assert_eq!(equipment.count(), 1);

        equipment.slots[slot_index::BODY] = EquipmentItem::new(1117, 1);
        assert_eq!(equipment.count(), 2);
    }

    #[test]
    fn test_equipment_total_weight_empty() {
        let equipment = Equipment::new();
        assert_eq!(equipment.total_weight(), 0.0);
    }

    #[test]
    fn test_remove_amount() {
        let mut equipment = Equipment::new();
        equipment.slots[slot_index::AMMO] = EquipmentItem::new(882, 100); // Bronze arrows

        let removed = equipment.remove_amount(slot_index::AMMO, 50);
        assert!(removed.is_ok());
        assert_eq!(removed.unwrap(), 50);
        assert_eq!(equipment.slots[slot_index::AMMO].amount, 50);
    }

    #[test]
    fn test_remove_amount_all() {
        let mut equipment = Equipment::new();
        equipment.slots[slot_index::AMMO] = EquipmentItem::new(882, 10);

        let removed = equipment.remove_amount(slot_index::AMMO, 10);
        assert!(removed.is_ok());
        assert_eq!(removed.unwrap(), 10);
        assert!(equipment.slots[slot_index::AMMO].is_empty());
    }

    #[test]
    fn test_remove_amount_overflow() {
        let mut equipment = Equipment::new();
        equipment.slots[slot_index::AMMO] = EquipmentItem::new(882, 10);

        let removed = equipment.remove_amount(slot_index::AMMO, 20);
        assert!(removed.is_ok());
        assert_eq!(removed.unwrap(), 10); // Only removed what was available
        assert!(equipment.slots[slot_index::AMMO].is_empty());
    }

    #[test]
    fn test_attack_style_enum() {
        assert_eq!(AttackStyle::Stab as usize, 0);
        assert_eq!(AttackStyle::Ranged as usize, 4);
    }

    #[test]
    fn test_equipment_error_display() {
        assert_eq!(
            EquipmentError::NotEquippable.to_string(),
            "This item cannot be equipped"
        );
        assert_eq!(
            EquipmentError::RequirementsNotMet.to_string(),
            "You don't meet the requirements to wear this"
        );
    }

    #[test]
    fn test_slot_indices() {
        assert_eq!(slot_index::HEAD, 0);
        assert_eq!(slot_index::CAPE, 1);
        assert_eq!(slot_index::AMULET, 2);
        assert_eq!(slot_index::WEAPON, 3);
        assert_eq!(slot_index::BODY, 4);
        assert_eq!(slot_index::SHIELD, 5);
        assert_eq!(slot_index::LEGS, 7);
        assert_eq!(slot_index::HANDS, 9);
        assert_eq!(slot_index::FEET, 10);
        assert_eq!(slot_index::RING, 12);
        assert_eq!(slot_index::AMMO, 13);
    }

    #[test]
    fn test_bonus_indices() {
        assert_eq!(bonus_index::STAB_ATTACK, 0);
        assert_eq!(bonus_index::PRAYER, 13);
    }
}
