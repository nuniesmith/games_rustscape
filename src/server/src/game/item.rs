//! Item definitions module
//!
//! This module contains item definitions and properties used for:
//! - Determining if items are stackable
//! - Note/unnote conversion
//! - Equipment slot validation
//! - Item value calculations
//! - Weight calculations

use std::collections::HashMap;
use std::sync::OnceLock;

use serde::{Deserialize, Serialize};

/// Maximum item ID (revision 530 has ~22,000 items)
pub const MAX_ITEM_ID: u16 = 25000;

/// Equipment slot indices
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[repr(u8)]
pub enum EquipmentSlot {
    Head = 0,
    Cape = 1,
    Amulet = 2,
    Weapon = 3,
    Body = 4,
    Shield = 5,
    Legs = 7,
    Hands = 9,
    Feet = 10,
    Ring = 12,
    Ammo = 13,
}

impl EquipmentSlot {
    /// Total number of equipment slots
    pub const COUNT: usize = 14;

    /// Convert from u8
    pub fn from_u8(value: u8) -> Option<Self> {
        match value {
            0 => Some(Self::Head),
            1 => Some(Self::Cape),
            2 => Some(Self::Amulet),
            3 => Some(Self::Weapon),
            4 => Some(Self::Body),
            5 => Some(Self::Shield),
            7 => Some(Self::Legs),
            9 => Some(Self::Hands),
            10 => Some(Self::Feet),
            12 => Some(Self::Ring),
            13 => Some(Self::Ammo),
            _ => None,
        }
    }

    /// Get slot name
    pub fn name(&self) -> &'static str {
        match self {
            Self::Head => "Head",
            Self::Cape => "Cape",
            Self::Amulet => "Amulet",
            Self::Weapon => "Weapon",
            Self::Body => "Body",
            Self::Shield => "Shield",
            Self::Legs => "Legs",
            Self::Hands => "Hands",
            Self::Feet => "Feet",
            Self::Ring => "Ring",
            Self::Ammo => "Ammo",
        }
    }
}

/// Weapon type for combat calculations
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
pub enum WeaponType {
    #[default]
    None,
    Axe,
    Blunt,
    Bow,
    Claw,
    Crossbow,
    Gun,
    Pickaxe,
    Polearm,
    Scythe,
    Slash,
    Spear,
    Spiked,
    Stab,
    Staff,
    Thrown,
    TwoHanded,
    Whip,
    Unarmed,
}

/// Item definition containing all properties for an item type
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ItemDefinition {
    /// Item ID
    pub id: u16,
    /// Item name
    pub name: String,
    /// Item examine text/description
    pub description: String,
    /// Base value in coins
    pub value: u32,
    /// Whether the item stacks in inventory
    pub stackable: bool,
    /// Whether this is a noted version of an item
    pub noted: bool,
    /// Template ID for creating notes (usually 799)
    pub note_template_id: i32,
    /// The unnoted item ID (for noted items)
    pub note_linked_id: i32,
    /// Whether this is a members-only item
    pub members: bool,
    /// Whether this item can be traded
    pub tradeable: bool,
    /// Item weight in kg
    pub weight: f32,
    /// Inventory right-click options
    pub options: [Option<String>; 5],
    /// Ground item right-click options
    pub ground_options: [Option<String>; 5],
    /// Equipment slot (if equippable)
    pub equipment_slot: Option<EquipmentSlot>,
    /// Whether this is a two-handed weapon
    pub two_handed: bool,
    /// Weapon type
    pub weapon_type: WeaponType,
    /// Attack speed in game ticks (0 = not a weapon)
    pub attack_speed: u8,
    /// Equipment bonuses [stab_atk, slash_atk, crush_atk, magic_atk, range_atk,
    ///                    stab_def, slash_def, crush_def, magic_def, range_def,
    ///                    strength, ranged_str, magic_dmg, prayer]
    pub bonuses: [i16; 14],
    /// Required skill levels to equip [(skill_id, level), ...]
    pub requirements: Vec<(u8, u8)>,
}

impl Default for ItemDefinition {
    fn default() -> Self {
        Self {
            id: 0,
            name: "null".to_string(),
            description: String::new(),
            value: 1,
            stackable: false,
            noted: false,
            note_template_id: -1,
            note_linked_id: -1,
            members: false,
            tradeable: true,
            weight: 0.0,
            options: [None, None, None, None, Some("Drop".to_string())],
            ground_options: [None, None, Some("Take".to_string()), None, None],
            equipment_slot: None,
            two_handed: false,
            weapon_type: WeaponType::None,
            attack_speed: 0,
            bonuses: [0; 14],
            requirements: Vec::new(),
        }
    }
}

impl ItemDefinition {
    /// Create a new item definition
    pub fn new(id: u16, name: &str) -> Self {
        Self {
            id,
            name: name.to_string(),
            ..Default::default()
        }
    }

    /// Check if this item is valid (not null)
    pub fn is_valid(&self) -> bool {
        self.id > 0 && self.name != "null"
    }

    /// Check if this item is effectively stackable (stackable or noted)
    pub fn is_stackable(&self) -> bool {
        self.stackable || self.noted
    }

    /// Check if this item is equippable
    pub fn is_equippable(&self) -> bool {
        self.equipment_slot.is_some()
    }

    /// Check if this item is a weapon
    pub fn is_weapon(&self) -> bool {
        self.equipment_slot == Some(EquipmentSlot::Weapon)
    }

    /// Get high alchemy value (60% of value)
    pub fn high_alch_value(&self) -> u32 {
        (self.value as f64 * 0.6) as u32
    }

    /// Get low alchemy value (40% of value)
    pub fn low_alch_value(&self) -> u32 {
        (self.value as f64 * 0.4) as u32
    }

    /// Get the noted version's item ID (if this is unnoted and has a note)
    pub fn get_noted_id(&self) -> Option<u16> {
        if !self.noted && self.note_linked_id > 0 {
            Some(self.note_linked_id as u16)
        } else {
            None
        }
    }

    /// Get the unnoted version's item ID (if this is noted)
    pub fn get_unnoted_id(&self) -> Option<u16> {
        if self.noted && self.note_linked_id > 0 {
            Some(self.note_linked_id as u16)
        } else {
            None
        }
    }

    /// Builder method - set stackable
    pub fn stackable(mut self, stackable: bool) -> Self {
        self.stackable = stackable;
        self
    }

    /// Builder method - set value
    pub fn value(mut self, value: u32) -> Self {
        self.value = value;
        self
    }

    /// Builder method - set members
    pub fn members(mut self, members: bool) -> Self {
        self.members = members;
        self
    }

    /// Builder method - set tradeable
    pub fn tradeable(mut self, tradeable: bool) -> Self {
        self.tradeable = tradeable;
        self
    }

    /// Builder method - set weight
    pub fn weight(mut self, weight: f32) -> Self {
        self.weight = weight;
        self
    }

    /// Builder method - set equipment slot
    pub fn equipment_slot(mut self, slot: EquipmentSlot) -> Self {
        self.equipment_slot = Some(slot);
        self
    }

    /// Builder method - set two-handed
    pub fn two_handed(mut self, two_handed: bool) -> Self {
        self.two_handed = two_handed;
        self
    }

    /// Builder method - set noted info
    pub fn noted(mut self, noted: bool, linked_id: i32) -> Self {
        self.noted = noted;
        self.note_linked_id = linked_id;
        self
    }

    /// Builder method - set requirements
    pub fn requirements(mut self, reqs: Vec<(u8, u8)>) -> Self {
        self.requirements = reqs;
        self
    }

    /// Builder method - set description
    pub fn description(mut self, desc: &str) -> Self {
        self.description = desc.to_string();
        self
    }
}

/// Global item definition store
static ITEM_DEFINITIONS: OnceLock<ItemDefinitionStore> = OnceLock::new();

/// Item definition store
pub struct ItemDefinitionStore {
    items: HashMap<u16, ItemDefinition>,
    /// Mapping from item ID to noted item ID
    note_mappings: HashMap<u16, u16>,
}

impl Default for ItemDefinitionStore {
    fn default() -> Self {
        Self::new()
    }
}

impl ItemDefinitionStore {
    /// Create a new empty store
    pub fn new() -> Self {
        Self {
            items: HashMap::new(),
            note_mappings: HashMap::new(),
        }
    }

    /// Create store with common items pre-loaded
    pub fn with_common_items() -> Self {
        let mut store = Self::new();
        store.load_common_items();
        store
    }

    /// Load common item definitions
    /// In a full implementation, these would be loaded from the cache
    fn load_common_items(&mut self) {
        // Coins
        self.add(
            ItemDefinition::new(995, "Coins")
                .stackable(true)
                .value(1)
                .description("Lovely money!"),
        );

        // Bones
        self.add(
            ItemDefinition::new(526, "Bones")
                .value(1)
                .description("Eww it's a pile of bones."),
        );

        // Big bones
        self.add(
            ItemDefinition::new(532, "Big bones")
                .value(15)
                .description("These are big bones."),
        );

        // Logs
        self.add(
            ItemDefinition::new(1511, "Logs")
                .value(1)
                .description("Some logs."),
        );

        // Oak logs
        self.add(
            ItemDefinition::new(1521, "Oak logs")
                .value(20)
                .description("Logs cut from an oak tree."),
        );

        // Willow logs
        self.add(
            ItemDefinition::new(1519, "Willow logs")
                .value(40)
                .description("Logs cut from a willow tree."),
        );

        // Tinderbox
        self.add(
            ItemDefinition::new(590, "Tinderbox")
                .value(1)
                .description("Useful for lighting fires."),
        );

        // Bronze axe
        self.add(
            ItemDefinition::new(1351, "Bronze axe")
                .value(16)
                .equipment_slot(EquipmentSlot::Weapon)
                .description("A woodcutter's axe."),
        );

        // Iron axe
        self.add(
            ItemDefinition::new(1349, "Iron axe")
                .value(56)
                .equipment_slot(EquipmentSlot::Weapon)
                .requirements(vec![(0, 1)]) // 1 Attack
                .description("A woodcutter's axe."),
        );

        // Bronze sword
        self.add(
            ItemDefinition::new(1277, "Bronze sword")
                .value(26)
                .equipment_slot(EquipmentSlot::Weapon)
                .description("A bronze sword."),
        );

        // Iron sword
        self.add(
            ItemDefinition::new(1279, "Iron sword")
                .value(91)
                .equipment_slot(EquipmentSlot::Weapon)
                .requirements(vec![(0, 1)]) // 1 Attack
                .description("An iron sword."),
        );

        // Wooden shield
        self.add(
            ItemDefinition::new(1171, "Wooden shield")
                .value(20)
                .equipment_slot(EquipmentSlot::Shield)
                .description("A simple wooden shield."),
        );

        // Bronze full helm
        self.add(
            ItemDefinition::new(1155, "Bronze full helm")
                .value(44)
                .equipment_slot(EquipmentSlot::Head)
                .description("A bronze full helmet."),
        );

        // Bronze platebody
        self.add(
            ItemDefinition::new(1117, "Bronze platebody")
                .value(160)
                .equipment_slot(EquipmentSlot::Body)
                .description("Provides excellent protection."),
        );

        // Bronze platelegs
        self.add(
            ItemDefinition::new(1075, "Bronze platelegs")
                .value(80)
                .equipment_slot(EquipmentSlot::Legs)
                .description("These look pretty heavy."),
        );

        // Leather boots
        self.add(
            ItemDefinition::new(1061, "Leather boots")
                .value(6)
                .equipment_slot(EquipmentSlot::Feet)
                .description("Comfortable leather boots."),
        );

        // Leather gloves
        self.add(
            ItemDefinition::new(1059, "Leather gloves")
                .value(6)
                .equipment_slot(EquipmentSlot::Hands)
                .description("These protect my hands."),
        );

        // Shortbow
        self.add(
            ItemDefinition::new(841, "Shortbow")
                .value(50)
                .equipment_slot(EquipmentSlot::Weapon)
                .two_handed(true)
                .description("Short but effective."),
        );

        // Bronze arrows
        self.add(
            ItemDefinition::new(882, "Bronze arrow")
                .stackable(true)
                .value(1)
                .equipment_slot(EquipmentSlot::Ammo)
                .description("Arrows with bronze heads."),
        );

        // Iron arrows
        self.add(
            ItemDefinition::new(884, "Iron arrow")
                .stackable(true)
                .value(3)
                .equipment_slot(EquipmentSlot::Ammo)
                .description("Arrows with iron heads."),
        );

        // Amulet of strength
        self.add(
            ItemDefinition::new(1725, "Amulet of strength")
                .value(900)
                .equipment_slot(EquipmentSlot::Amulet)
                .description("An amulet of strength."),
        );

        // Gold ring
        self.add(
            ItemDefinition::new(1635, "Gold ring")
                .value(350)
                .equipment_slot(EquipmentSlot::Ring)
                .description("A plain gold ring."),
        );

        // Small fishing net
        self.add(
            ItemDefinition::new(303, "Small fishing net")
                .value(5)
                .description("Useful for catching small fish."),
        );

        // Raw shrimp
        self.add(
            ItemDefinition::new(317, "Raw shrimps")
                .value(1)
                .description("Some raw shrimps."),
        );

        // Shrimps (cooked)
        self.add(
            ItemDefinition::new(315, "Shrimps")
                .value(1)
                .description("Some nicely cooked shrimps."),
        );

        // Burnt shrimps
        self.add(
            ItemDefinition::new(323, "Burnt shrimp")
                .value(1)
                .tradeable(false)
                .description("Oops!"),
        );

        // Raw trout
        self.add(
            ItemDefinition::new(335, "Raw trout")
                .value(10)
                .description("A raw trout."),
        );

        // Trout
        self.add(
            ItemDefinition::new(333, "Trout")
                .value(10)
                .description("A nicely cooked trout."),
        );

        // Raw salmon
        self.add(
            ItemDefinition::new(331, "Raw salmon")
                .value(25)
                .description("A raw salmon."),
        );

        // Salmon
        self.add(
            ItemDefinition::new(329, "Salmon")
                .value(25)
                .description("A nicely cooked salmon."),
        );

        // Raw lobster
        self.add(
            ItemDefinition::new(377, "Raw lobster")
                .value(60)
                .description("A raw lobster."),
        );

        // Lobster
        self.add(
            ItemDefinition::new(379, "Lobster")
                .value(60)
                .description("A nicely cooked lobster."),
        );

        // Bread
        self.add(
            ItemDefinition::new(2309, "Bread")
                .value(12)
                .description("Nice fresh bread."),
        );

        // Cake
        self.add(
            ItemDefinition::new(1891, "Cake")
                .value(50)
                .description("A tasty cake."),
        );

        // Jug of water
        self.add(
            ItemDefinition::new(1937, "Jug of water")
                .value(1)
                .description("A jug of water."),
        );

        // Empty jug
        self.add(
            ItemDefinition::new(1935, "Jug")
                .value(1)
                .description("An empty jug."),
        );

        // Bucket
        self.add(
            ItemDefinition::new(1925, "Bucket")
                .value(2)
                .description("It's an empty bucket."),
        );

        // Bucket of water
        self.add(
            ItemDefinition::new(1929, "Bucket of water")
                .value(2)
                .description("It's a bucket of water."),
        );

        // Pot
        self.add(
            ItemDefinition::new(1931, "Pot")
                .value(1)
                .description("This pot is empty."),
        );

        // Pot of flour
        self.add(
            ItemDefinition::new(1933, "Pot of flour")
                .value(10)
                .description("A pot of flour."),
        );

        // Bronze pickaxe
        self.add(
            ItemDefinition::new(1265, "Bronze pickaxe")
                .value(1)
                .equipment_slot(EquipmentSlot::Weapon)
                .description("Used for mining."),
        );

        // Iron pickaxe
        self.add(
            ItemDefinition::new(1267, "Iron pickaxe")
                .value(140)
                .equipment_slot(EquipmentSlot::Weapon)
                .requirements(vec![(0, 1)]) // 1 Attack
                .description("Used for mining."),
        );

        // Copper ore
        self.add(
            ItemDefinition::new(436, "Copper ore")
                .value(5)
                .description("Some copper ore."),
        );

        // Tin ore
        self.add(
            ItemDefinition::new(438, "Tin ore")
                .value(4)
                .description("Some tin ore."),
        );

        // Iron ore
        self.add(
            ItemDefinition::new(440, "Iron ore")
                .value(17)
                .description("Some iron ore."),
        );

        // Coal
        self.add(
            ItemDefinition::new(453, "Coal")
                .value(45)
                .description("Some coal."),
        );

        // Gold ore
        self.add(
            ItemDefinition::new(444, "Gold ore")
                .value(75)
                .description("Some gold ore."),
        );

        // Bronze bar
        self.add(
            ItemDefinition::new(2349, "Bronze bar")
                .value(8)
                .description("It's a bar of bronze."),
        );

        // Iron bar
        self.add(
            ItemDefinition::new(2351, "Iron bar")
                .value(28)
                .description("It's a bar of iron."),
        );

        // Steel bar
        self.add(
            ItemDefinition::new(2353, "Steel bar")
                .value(75)
                .description("It's a bar of steel."),
        );

        // Gold bar
        self.add(
            ItemDefinition::new(2357, "Gold bar")
                .value(150)
                .description("It's a bar of gold."),
        );

        // Hammer
        self.add(
            ItemDefinition::new(2347, "Hammer")
                .value(1)
                .description("Good for hitting things."),
        );

        // Chisel
        self.add(
            ItemDefinition::new(1755, "Chisel")
                .value(1)
                .description("Good for detailed crafting."),
        );

        // Needle
        self.add(
            ItemDefinition::new(1733, "Needle")
                .value(1)
                .description("Good for sewing."),
        );

        // Thread
        self.add(
            ItemDefinition::new(1734, "Thread")
                .stackable(true)
                .value(1)
                .description("A spool of thread."),
        );

        // Leather
        self.add(
            ItemDefinition::new(1741, "Leather")
                .value(1)
                .description("It's a piece of leather."),
        );

        // Cowhide
        self.add(
            ItemDefinition::new(1739, "Cowhide")
                .value(2)
                .description("A cowhide."),
        );

        // Runes
        self.add(
            ItemDefinition::new(556, "Air rune")
                .stackable(true)
                .value(4)
                .description("One of the 4 basic elemental runes."),
        );

        self.add(
            ItemDefinition::new(558, "Mind rune")
                .stackable(true)
                .value(3)
                .description("Used for low level missiles."),
        );

        self.add(
            ItemDefinition::new(555, "Water rune")
                .stackable(true)
                .value(4)
                .description("One of the 4 basic elemental runes."),
        );

        self.add(
            ItemDefinition::new(557, "Earth rune")
                .stackable(true)
                .value(4)
                .description("One of the 4 basic elemental runes."),
        );

        self.add(
            ItemDefinition::new(554, "Fire rune")
                .stackable(true)
                .value(4)
                .description("One of the 4 basic elemental runes."),
        );

        self.add(
            ItemDefinition::new(559, "Body rune")
                .stackable(true)
                .value(3)
                .description("Used for curse spells."),
        );

        self.add(
            ItemDefinition::new(562, "Chaos rune")
                .stackable(true)
                .value(90)
                .description("Used for medium level missiles."),
        );

        self.add(
            ItemDefinition::new(560, "Death rune")
                .stackable(true)
                .value(180)
                .description("Used for high level missiles."),
        );

        self.add(
            ItemDefinition::new(565, "Blood rune")
                .stackable(true)
                .value(400)
                .description("Used for very high level missiles."),
        );

        self.add(
            ItemDefinition::new(561, "Nature rune")
                .stackable(true)
                .value(130)
                .description("Used for alchemy spells."),
        );

        self.add(
            ItemDefinition::new(563, "Law rune")
                .stackable(true)
                .value(240)
                .description("Used for teleport spells."),
        );

        // Staves
        self.add(
            ItemDefinition::new(1381, "Staff of air")
                .value(1500)
                .equipment_slot(EquipmentSlot::Weapon)
                .description("A staff that provides unlimited air runes."),
        );

        self.add(
            ItemDefinition::new(1383, "Staff of water")
                .value(1500)
                .equipment_slot(EquipmentSlot::Weapon)
                .description("A staff that provides unlimited water runes."),
        );

        self.add(
            ItemDefinition::new(1385, "Staff of earth")
                .value(1500)
                .equipment_slot(EquipmentSlot::Weapon)
                .description("A staff that provides unlimited earth runes."),
        );

        self.add(
            ItemDefinition::new(1387, "Staff of fire")
                .value(1500)
                .equipment_slot(EquipmentSlot::Weapon)
                .description("A staff that provides unlimited fire runes."),
        );

        // Wizard robe
        self.add(
            ItemDefinition::new(577, "Wizard robe")
                .value(15)
                .equipment_slot(EquipmentSlot::Legs)
                .description("A wizard's robe."),
        );

        // Wizard hat
        self.add(
            ItemDefinition::new(579, "Wizard hat")
                .value(2)
                .equipment_slot(EquipmentSlot::Head)
                .description("A wizard's hat."),
        );

        // Blue wizard robe (top)
        self.add(
            ItemDefinition::new(1027, "Blue wizard robe")
                .value(15)
                .equipment_slot(EquipmentSlot::Body)
                .description("A blue wizard's robe."),
        );

        // Cape
        self.add(
            ItemDefinition::new(1007, "Cape")
                .value(2)
                .equipment_slot(EquipmentSlot::Cape)
                .description("A simple cape."),
        );

        // Note: This is a subset of items. In a full implementation,
        // all ~22,000 items would be loaded from the cache.
    }

    /// Add an item definition
    pub fn add(&mut self, item: ItemDefinition) {
        // Build note mapping
        if !item.noted && item.note_linked_id > 0 {
            self.note_mappings
                .insert(item.id, item.note_linked_id as u16);
        }
        self.items.insert(item.id, item);
    }

    /// Get an item definition by ID
    pub fn get(&self, id: u16) -> Option<&ItemDefinition> {
        self.items.get(&id)
    }

    /// Check if an item exists
    pub fn exists(&self, id: u16) -> bool {
        self.items.contains_key(&id)
    }

    /// Check if an item is stackable
    pub fn is_stackable(&self, id: u16) -> bool {
        self.items
            .get(&id)
            .map(|i| i.is_stackable())
            .unwrap_or(false)
    }

    /// Check if an item is equippable
    pub fn is_equippable(&self, id: u16) -> bool {
        self.items
            .get(&id)
            .map(|i| i.is_equippable())
            .unwrap_or(false)
    }

    /// Get the equipment slot for an item
    pub fn get_equipment_slot(&self, id: u16) -> Option<EquipmentSlot> {
        self.items.get(&id).and_then(|i| i.equipment_slot)
    }

    /// Get the noted version of an item
    pub fn get_noted_id(&self, id: u16) -> Option<u16> {
        self.note_mappings.get(&id).copied()
    }

    /// Get the unnoted version of a noted item
    pub fn get_unnoted_id(&self, id: u16) -> Option<u16> {
        self.items.get(&id).and_then(|i| i.get_unnoted_id())
    }

    /// Check if an item is two-handed
    pub fn is_two_handed(&self, id: u16) -> bool {
        self.items.get(&id).map(|i| i.two_handed).unwrap_or(false)
    }

    /// Get item count
    pub fn len(&self) -> usize {
        self.items.len()
    }

    /// Check if empty
    pub fn is_empty(&self) -> bool {
        self.items.is_empty()
    }

    /// Search items by name
    pub fn search_by_name(&self, query: &str) -> Vec<&ItemDefinition> {
        let lower_query = query.to_lowercase();
        self.items
            .values()
            .filter(|i| i.name.to_lowercase().contains(&lower_query))
            .collect()
    }
}

/// Initialize the global item definition store
pub fn init_item_definitions() {
    let _ = ITEM_DEFINITIONS.set(ItemDefinitionStore::with_common_items());
}

/// Get the global item definition store
pub fn item_definitions() -> &'static ItemDefinitionStore {
    ITEM_DEFINITIONS.get_or_init(ItemDefinitionStore::with_common_items)
}

/// Convenience function to get an item definition
pub fn get_item(id: u16) -> Option<&'static ItemDefinition> {
    item_definitions().get(id)
}

/// Convenience function to check if an item is stackable
pub fn is_stackable(id: u16) -> bool {
    item_definitions().is_stackable(id)
}

/// Convenience function to check if an item is equippable
pub fn is_equippable(id: u16) -> bool {
    item_definitions().is_equippable(id)
}

/// Convenience function to get equipment slot
pub fn get_equipment_slot(id: u16) -> Option<EquipmentSlot> {
    item_definitions().get_equipment_slot(id)
}

/// Common item IDs
pub mod item_ids {
    pub const COINS: u16 = 995;
    pub const BONES: u16 = 526;
    pub const BIG_BONES: u16 = 532;
    pub const LOGS: u16 = 1511;
    pub const OAK_LOGS: u16 = 1521;
    pub const WILLOW_LOGS: u16 = 1519;
    pub const TINDERBOX: u16 = 590;
    pub const BRONZE_AXE: u16 = 1351;
    pub const IRON_AXE: u16 = 1349;
    pub const BRONZE_SWORD: u16 = 1277;
    pub const IRON_SWORD: u16 = 1279;
    pub const WOODEN_SHIELD: u16 = 1171;
    pub const BRONZE_PICKAXE: u16 = 1265;
    pub const IRON_PICKAXE: u16 = 1267;
    pub const SMALL_FISHING_NET: u16 = 303;
    pub const RAW_SHRIMPS: u16 = 317;
    pub const SHRIMPS: u16 = 315;
    pub const RAW_TROUT: u16 = 335;
    pub const TROUT: u16 = 333;
    pub const RAW_SALMON: u16 = 331;
    pub const SALMON: u16 = 329;
    pub const RAW_LOBSTER: u16 = 377;
    pub const LOBSTER: u16 = 379;
    pub const BRONZE_ARROW: u16 = 882;
    pub const IRON_ARROW: u16 = 884;
    pub const SHORTBOW: u16 = 841;
    pub const AIR_RUNE: u16 = 556;
    pub const MIND_RUNE: u16 = 558;
    pub const WATER_RUNE: u16 = 555;
    pub const EARTH_RUNE: u16 = 557;
    pub const FIRE_RUNE: u16 = 554;
    pub const BODY_RUNE: u16 = 559;
    pub const CHAOS_RUNE: u16 = 562;
    pub const DEATH_RUNE: u16 = 560;
    pub const BLOOD_RUNE: u16 = 565;
    pub const NATURE_RUNE: u16 = 561;
    pub const LAW_RUNE: u16 = 563;
    pub const HAMMER: u16 = 2347;
    pub const CHISEL: u16 = 1755;
    pub const COPPER_ORE: u16 = 436;
    pub const TIN_ORE: u16 = 438;
    pub const IRON_ORE: u16 = 440;
    pub const COAL: u16 = 453;
    pub const GOLD_ORE: u16 = 444;
    pub const BRONZE_BAR: u16 = 2349;
    pub const IRON_BAR: u16 = 2351;
    pub const STEEL_BAR: u16 = 2353;
    pub const GOLD_BAR: u16 = 2357;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_item_definition_creation() {
        let item = ItemDefinition::new(1234, "Test Item")
            .stackable(true)
            .value(100);

        assert_eq!(item.id, 1234);
        assert_eq!(item.name, "Test Item");
        assert!(item.stackable);
        assert_eq!(item.value, 100);
    }

    #[test]
    fn test_item_definition_default() {
        let item = ItemDefinition::default();
        assert_eq!(item.id, 0);
        assert_eq!(item.name, "null");
        assert!(!item.stackable);
        assert_eq!(item.value, 1);
    }

    #[test]
    fn test_item_definition_store() {
        let store = ItemDefinitionStore::with_common_items();
        assert!(!store.is_empty());

        // Check coins exist and are stackable
        let coins = store.get(item_ids::COINS);
        assert!(coins.is_some());
        assert!(coins.unwrap().is_stackable());

        // Check bronze sword exists and is equippable
        let sword = store.get(item_ids::BRONZE_SWORD);
        assert!(sword.is_some());
        assert!(sword.unwrap().is_equippable());
        assert_eq!(sword.unwrap().equipment_slot, Some(EquipmentSlot::Weapon));
    }

    #[test]
    fn test_global_item_definitions() {
        init_item_definitions();

        assert!(is_stackable(item_ids::COINS));
        assert!(!is_stackable(item_ids::BRONZE_SWORD));
        assert!(is_equippable(item_ids::BRONZE_SWORD));
        assert!(!is_equippable(item_ids::LOGS));
    }

    #[test]
    fn test_equipment_slot() {
        let store = ItemDefinitionStore::with_common_items();

        assert_eq!(
            store.get_equipment_slot(item_ids::BRONZE_SWORD),
            Some(EquipmentSlot::Weapon)
        );
        assert_eq!(
            store.get_equipment_slot(item_ids::WOODEN_SHIELD),
            Some(EquipmentSlot::Shield)
        );
        assert_eq!(store.get_equipment_slot(item_ids::LOGS), None);
    }

    #[test]
    fn test_high_alch_value() {
        let item = ItemDefinition::new(1, "Test").value(1000);
        assert_eq!(item.high_alch_value(), 600);
        assert_eq!(item.low_alch_value(), 400);
    }

    #[test]
    fn test_search_by_name() {
        let store = ItemDefinitionStore::with_common_items();

        let swords = store.search_by_name("sword");
        assert!(!swords.is_empty());
        assert!(swords.iter().any(|i| i.name.contains("sword")));

        let runes = store.search_by_name("rune");
        assert!(!runes.is_empty());
    }

    #[test]
    fn test_two_handed() {
        let store = ItemDefinitionStore::with_common_items();

        assert!(store.is_two_handed(item_ids::SHORTBOW));
        assert!(!store.is_two_handed(item_ids::BRONZE_SWORD));
    }

    #[test]
    fn test_equipment_slot_from_u8() {
        assert_eq!(EquipmentSlot::from_u8(0), Some(EquipmentSlot::Head));
        assert_eq!(EquipmentSlot::from_u8(3), Some(EquipmentSlot::Weapon));
        assert_eq!(EquipmentSlot::from_u8(5), Some(EquipmentSlot::Shield));
        assert_eq!(EquipmentSlot::from_u8(6), None); // Invalid slot
        assert_eq!(EquipmentSlot::from_u8(100), None);
    }
}
