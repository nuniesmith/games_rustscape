//! Ground items module
//!
//! This module handles items dropped on the ground:
//! - Ground item storage with location tracking
//! - Visibility lifecycle (private → public → despawn)
//! - Ground item spawn and pickup
//! - Region-based item queries for efficient visibility checks

use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};

use parking_lot::RwLock;
use serde::{Deserialize, Serialize};
use tracing::{debug, info, trace};

use super::player::Location;

/// How long an item stays private (only visible to owner) in ticks
/// 100 ticks = 60 seconds at 600ms per tick
pub const PRIVATE_VISIBILITY_TICKS: u64 = 100;

/// How long an item stays public before despawning in ticks
/// 200 ticks = 120 seconds (2 minutes)
pub const PUBLIC_VISIBILITY_TICKS: u64 = 200;

/// Total lifetime of a dropped item (private + public)
pub const TOTAL_ITEM_LIFETIME_TICKS: u64 = PRIVATE_VISIBILITY_TICKS + PUBLIC_VISIBILITY_TICKS;

/// Maximum ground items per region (to prevent spam)
pub const MAX_ITEMS_PER_REGION: usize = 256;

/// Maximum ground items in the world
pub const MAX_GROUND_ITEMS: usize = 65536;

/// View distance for ground items (in tiles)
pub const GROUND_ITEM_VIEW_DISTANCE: u16 = 32;

/// Unique identifier for a ground item
pub type GroundItemId = u64;

/// Visibility state of a ground item
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ItemVisibility {
    /// Only visible to the owner
    Private,
    /// Visible to everyone
    Public,
    /// Spawned item (always visible, respawns)
    Spawned,
}

impl ItemVisibility {
    /// Check if this item is visible to a specific player
    pub fn is_visible_to(&self, viewer_index: u16, owner_index: Option<u16>) -> bool {
        match self {
            ItemVisibility::Private => owner_index == Some(viewer_index),
            ItemVisibility::Public | ItemVisibility::Spawned => true,
        }
    }
}

/// A ground item in the world
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GroundItem {
    /// Unique ID for this ground item instance
    pub id: GroundItemId,
    /// Item ID
    pub item_id: u16,
    /// Item amount
    pub amount: u32,
    /// World location
    pub location: Location,
    /// Player index who dropped this item (None for spawned items)
    pub owner_index: Option<u16>,
    /// Tick when the item was spawned
    pub spawn_tick: u64,
    /// Current visibility state
    pub visibility: ItemVisibility,
    /// Tick when item should become public (for private items)
    pub public_tick: u64,
    /// Tick when item should despawn
    pub despawn_tick: u64,
    /// Whether this item has been picked up
    pub picked_up: bool,
    /// Respawn delay for spawned items (0 = no respawn)
    pub respawn_delay: u64,
    /// For respawning items: tick when it should respawn
    pub respawn_tick: Option<u64>,
}

impl GroundItem {
    /// Create a new dropped ground item
    pub fn new_dropped(
        id: GroundItemId,
        item_id: u16,
        amount: u32,
        location: Location,
        owner_index: u16,
        current_tick: u64,
    ) -> Self {
        Self {
            id,
            item_id,
            amount,
            location,
            owner_index: Some(owner_index),
            spawn_tick: current_tick,
            visibility: ItemVisibility::Private,
            public_tick: current_tick + PRIVATE_VISIBILITY_TICKS,
            despawn_tick: current_tick + TOTAL_ITEM_LIFETIME_TICKS,
            picked_up: false,
            respawn_delay: 0,
            respawn_tick: None,
        }
    }

    /// Create a spawned ground item (always public, can respawn)
    pub fn new_spawned(
        id: GroundItemId,
        item_id: u16,
        amount: u32,
        location: Location,
        respawn_delay: u64,
    ) -> Self {
        Self {
            id,
            item_id,
            amount,
            location,
            owner_index: None,
            spawn_tick: 0,
            visibility: ItemVisibility::Spawned,
            public_tick: 0,
            despawn_tick: u64::MAX, // Spawned items don't despawn
            picked_up: false,
            respawn_delay,
            respawn_tick: None,
        }
    }

    /// Check if this item is visible to a player
    pub fn is_visible_to(&self, viewer_index: u16) -> bool {
        if self.picked_up {
            return false;
        }
        if self.respawn_tick.is_some() {
            // Item is waiting to respawn
            return false;
        }
        self.visibility
            .is_visible_to(viewer_index, self.owner_index)
    }

    /// Check if this item should become public
    pub fn should_become_public(&self, current_tick: u64) -> bool {
        self.visibility == ItemVisibility::Private && current_tick >= self.public_tick
    }

    /// Check if this item should despawn
    pub fn should_despawn(&self, current_tick: u64) -> bool {
        if self.visibility == ItemVisibility::Spawned {
            return false; // Spawned items don't despawn
        }
        current_tick >= self.despawn_tick
    }

    /// Check if this item should respawn
    pub fn should_respawn(&self, current_tick: u64) -> bool {
        if let Some(respawn_tick) = self.respawn_tick {
            current_tick >= respawn_tick
        } else {
            false
        }
    }

    /// Make this item public
    pub fn make_public(&mut self) {
        if self.visibility == ItemVisibility::Private {
            self.visibility = ItemVisibility::Public;
        }
    }

    /// Mark as picked up (for respawning items)
    pub fn pick_up(&mut self, current_tick: u64) {
        self.picked_up = true;
        if self.respawn_delay > 0 {
            self.respawn_tick = Some(current_tick + self.respawn_delay);
        }
    }

    /// Respawn the item (for spawned items)
    pub fn respawn(&mut self) {
        self.picked_up = false;
        self.respawn_tick = None;
    }

    /// Get the region ID for this item's location
    pub fn region_id(&self) -> u32 {
        self.location.region_id()
    }

    /// Check if within view distance of a location
    pub fn within_view_distance(&self, other: &Location) -> bool {
        self.location
            .within_distance(other, GROUND_ITEM_VIEW_DISTANCE)
    }
}

/// Result of a ground item operation
#[derive(Debug, Clone)]
pub enum GroundItemResult {
    /// Item was successfully spawned
    Spawned(GroundItemId),
    /// Item was successfully picked up
    PickedUp { item_id: u16, amount: u32 },
    /// Item became public
    BecamePublic(GroundItemId),
    /// Item despawned
    Despawned(GroundItemId),
    /// Item respawned
    Respawned(GroundItemId),
    /// Operation failed
    Failed(GroundItemError),
}

/// Ground item operation errors
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum GroundItemError {
    /// Item not found
    NotFound,
    /// Item not visible to player
    NotVisible,
    /// Already picked up
    AlreadyPickedUp,
    /// Region is full
    RegionFull,
    /// World limit reached
    WorldFull,
    /// Invalid location
    InvalidLocation,
}

impl std::fmt::Display for GroundItemError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::NotFound => write!(f, "Item not found"),
            Self::NotVisible => write!(f, "You can't see that item"),
            Self::AlreadyPickedUp => write!(f, "Someone already took that"),
            Self::RegionFull => write!(f, "Too many items in this area"),
            Self::WorldFull => write!(f, "Too many items in the world"),
            Self::InvalidLocation => write!(f, "Invalid location"),
        }
    }
}

impl std::error::Error for GroundItemError {}

/// Event emitted when ground item state changes
#[derive(Debug, Clone)]
pub enum GroundItemEvent {
    /// Item spawned (send spawn packet)
    Spawn {
        item: GroundItem,
        /// If Some, only send to this player (private item)
        only_for: Option<u16>,
    },
    /// Item removed (send remove packet)
    Remove {
        id: GroundItemId,
        location: Location,
        item_id: u16,
        /// If Some, only send to this player
        only_for: Option<u16>,
    },
    /// Item became public (send spawn to all except owner)
    BecamePublic { item: GroundItem, owner_index: u16 },
}

/// Manages all ground items in the world
pub struct GroundItemManager {
    /// All ground items by ID
    items: RwLock<HashMap<GroundItemId, GroundItem>>,
    /// Items indexed by region for spatial queries
    items_by_region: RwLock<HashMap<u32, Vec<GroundItemId>>>,
    /// Next item ID to assign
    next_id: AtomicU64,
    /// Events generated this tick (to be processed)
    pending_events: RwLock<Vec<GroundItemEvent>>,
}

impl Default for GroundItemManager {
    fn default() -> Self {
        Self::new()
    }
}

impl GroundItemManager {
    /// Create a new ground item manager
    pub fn new() -> Self {
        Self {
            items: RwLock::new(HashMap::with_capacity(1024)),
            items_by_region: RwLock::new(HashMap::with_capacity(256)),
            next_id: AtomicU64::new(1),
            pending_events: RwLock::new(Vec::with_capacity(64)),
        }
    }

    /// Generate a new unique item ID
    fn next_id(&self) -> GroundItemId {
        self.next_id.fetch_add(1, Ordering::SeqCst)
    }

    /// Get the total number of ground items
    pub fn count(&self) -> usize {
        self.items.read().len()
    }

    /// Spawn a dropped item
    pub fn spawn_dropped(
        &self,
        item_id: u16,
        amount: u32,
        location: Location,
        owner_index: u16,
        current_tick: u64,
    ) -> Result<GroundItemId, GroundItemError> {
        // Check world limit
        if self.count() >= MAX_GROUND_ITEMS {
            return Err(GroundItemError::WorldFull);
        }

        let region_id = location.region_id();

        // Check region limit
        {
            let regions = self.items_by_region.read();
            if let Some(region_items) = regions.get(&region_id) {
                if region_items.len() >= MAX_ITEMS_PER_REGION {
                    return Err(GroundItemError::RegionFull);
                }
            }
        }

        let id = self.next_id();
        let item =
            GroundItem::new_dropped(id, item_id, amount, location, owner_index, current_tick);

        // Add to items map
        {
            let mut items = self.items.write();
            items.insert(id, item.clone());
        }

        // Add to region index
        {
            let mut regions = self.items_by_region.write();
            regions.entry(region_id).or_insert_with(Vec::new).push(id);
        }

        // Queue spawn event (private - only for owner)
        {
            let mut events = self.pending_events.write();
            events.push(GroundItemEvent::Spawn {
                item,
                only_for: Some(owner_index),
            });
        }

        debug!(
            id = id,
            item_id = item_id,
            amount = amount,
            location = %location,
            owner = owner_index,
            "Spawned dropped ground item"
        );

        Ok(id)
    }

    /// Spawn a permanent/respawning item
    pub fn spawn_permanent(
        &self,
        item_id: u16,
        amount: u32,
        location: Location,
        respawn_delay: u64,
    ) -> Result<GroundItemId, GroundItemError> {
        if self.count() >= MAX_GROUND_ITEMS {
            return Err(GroundItemError::WorldFull);
        }

        let region_id = location.region_id();

        let id = self.next_id();
        let item = GroundItem::new_spawned(id, item_id, amount, location, respawn_delay);

        {
            let mut items = self.items.write();
            items.insert(id, item.clone());
        }

        {
            let mut regions = self.items_by_region.write();
            regions.entry(region_id).or_insert_with(Vec::new).push(id);
        }

        // Queue spawn event (public - for everyone)
        {
            let mut events = self.pending_events.write();
            events.push(GroundItemEvent::Spawn {
                item,
                only_for: None,
            });
        }

        debug!(
            id = id,
            item_id = item_id,
            amount = amount,
            location = %location,
            "Spawned permanent ground item"
        );

        Ok(id)
    }

    /// Pick up a ground item
    pub fn pick_up(
        &self,
        id: GroundItemId,
        player_index: u16,
        current_tick: u64,
    ) -> Result<(u16, u32), GroundItemError> {
        let mut items = self.items.write();

        let item = items.get_mut(&id).ok_or(GroundItemError::NotFound)?;

        if item.picked_up {
            return Err(GroundItemError::AlreadyPickedUp);
        }

        if !item.is_visible_to(player_index) {
            return Err(GroundItemError::NotVisible);
        }

        let item_id = item.item_id;
        let amount = item.amount;
        let location = item.location;
        let visibility = item.visibility;
        let owner = item.owner_index;

        // Mark as picked up
        item.pick_up(current_tick);

        // Determine who to send remove packet to
        let only_for = match visibility {
            ItemVisibility::Private => owner,
            _ => None, // Public/spawned - send to everyone
        };

        // If it's a permanent item that respawns, don't remove from maps
        // Otherwise, remove it
        if item.respawn_delay == 0 {
            let region_id = item.region_id();
            drop(items);

            // Remove from items map
            {
                let mut items = self.items.write();
                items.remove(&id);
            }

            // Remove from region index
            {
                let mut regions = self.items_by_region.write();
                if let Some(region_items) = regions.get_mut(&region_id) {
                    region_items.retain(|&item_id| item_id != id);
                }
            }
        } else {
            drop(items);
        }

        // Queue remove event
        {
            let mut events = self.pending_events.write();
            events.push(GroundItemEvent::Remove {
                id,
                location,
                item_id,
                only_for,
            });
        }

        info!(
            id = id,
            item_id = item_id,
            amount = amount,
            player = player_index,
            "Ground item picked up"
        );

        Ok((item_id, amount))
    }

    /// Process tick - handle visibility changes, despawns, respawns
    pub fn process_tick(&self, current_tick: u64) -> Vec<GroundItemEvent> {
        let mut events_to_emit = Vec::new();
        let mut items_to_remove = Vec::new();
        let mut items_to_make_public = Vec::new();
        let mut items_to_respawn = Vec::new();

        // First pass: identify items that need updates
        {
            let items = self.items.read();
            for (&id, item) in items.iter() {
                if item.picked_up {
                    // Check for respawn
                    if item.should_respawn(current_tick) {
                        items_to_respawn.push(id);
                    }
                    continue;
                }

                if item.should_despawn(current_tick) {
                    items_to_remove.push(id);
                } else if item.should_become_public(current_tick) {
                    items_to_make_public.push(id);
                }
            }
        }

        // Second pass: apply updates
        {
            let mut items = self.items.write();

            // Handle despawns
            for id in &items_to_remove {
                if let Some(item) = items.remove(id) {
                    events_to_emit.push(GroundItemEvent::Remove {
                        id: *id,
                        location: item.location,
                        item_id: item.item_id,
                        only_for: if item.visibility == ItemVisibility::Private {
                            item.owner_index
                        } else {
                            None
                        },
                    });

                    // Remove from region index
                    let region_id = item.region_id();
                    let mut regions = self.items_by_region.write();
                    if let Some(region_items) = regions.get_mut(&region_id) {
                        region_items.retain(|&item_id| item_id != *id);
                    }

                    trace!(id = *id, item_id = item.item_id, "Ground item despawned");
                }
            }

            // Handle public transition
            for id in &items_to_make_public {
                if let Some(item) = items.get_mut(id) {
                    let owner_index = item.owner_index.unwrap_or(0);
                    item.make_public();
                    events_to_emit.push(GroundItemEvent::BecamePublic {
                        item: item.clone(),
                        owner_index,
                    });

                    trace!(
                        id = *id,
                        item_id = item.item_id,
                        "Ground item became public"
                    );
                }
            }

            // Handle respawns
            for id in &items_to_respawn {
                if let Some(item) = items.get_mut(id) {
                    item.respawn();
                    events_to_emit.push(GroundItemEvent::Spawn {
                        item: item.clone(),
                        only_for: None,
                    });

                    trace!(id = *id, item_id = item.item_id, "Ground item respawned");
                }
            }
        }

        // Store events and return
        if !events_to_emit.is_empty() {
            let mut pending = self.pending_events.write();
            pending.extend(events_to_emit.clone());
        }

        events_to_emit
    }

    /// Get pending events and clear the queue
    pub fn drain_events(&self) -> Vec<GroundItemEvent> {
        let mut events = self.pending_events.write();
        std::mem::take(&mut *events)
    }

    /// Get all items in a region visible to a player
    pub fn get_items_in_region(&self, region_id: u32, viewer_index: u16) -> Vec<GroundItem> {
        let items = self.items.read();
        let regions = self.items_by_region.read();

        let Some(region_items) = regions.get(&region_id) else {
            return Vec::new();
        };

        region_items
            .iter()
            .filter_map(|&id| items.get(&id))
            .filter(|item| item.is_visible_to(viewer_index))
            .cloned()
            .collect()
    }

    /// Get all items within view distance of a location visible to a player
    pub fn get_items_near(&self, location: &Location, viewer_index: u16) -> Vec<GroundItem> {
        let items = self.items.read();

        items
            .values()
            .filter(|item| item.is_visible_to(viewer_index) && item.within_view_distance(location))
            .cloned()
            .collect()
    }

    /// Get a specific item by ID
    pub fn get(&self, id: GroundItemId) -> Option<GroundItem> {
        self.items.read().get(&id).cloned()
    }

    /// Check if an item exists and is visible to a player
    pub fn is_visible(&self, id: GroundItemId, viewer_index: u16) -> bool {
        self.items
            .read()
            .get(&id)
            .map(|item| item.is_visible_to(viewer_index))
            .unwrap_or(false)
    }

    /// Get items at a specific tile location
    pub fn get_items_at(&self, location: &Location, viewer_index: u16) -> Vec<GroundItem> {
        let items = self.items.read();

        items
            .values()
            .filter(|item| {
                item.location.x == location.x
                    && item.location.y == location.y
                    && item.location.z == location.z
                    && item.is_visible_to(viewer_index)
            })
            .cloned()
            .collect()
    }

    /// Find an item at a location by item ID
    pub fn find_item_at(
        &self,
        location: &Location,
        item_id: u16,
        viewer_index: u16,
    ) -> Option<GroundItem> {
        let items = self.items.read();

        items
            .values()
            .find(|item| {
                item.location.x == location.x
                    && item.location.y == location.y
                    && item.location.z == location.z
                    && item.item_id == item_id
                    && item.is_visible_to(viewer_index)
            })
            .cloned()
    }

    /// Clear all ground items (for server shutdown or testing)
    pub fn clear(&self) {
        self.items.write().clear();
        self.items_by_region.write().clear();
        self.pending_events.write().clear();
        info!("Cleared all ground items");
    }

    /// Get statistics about ground items
    pub fn stats(&self) -> GroundItemStats {
        let items = self.items.read();
        let regions = self.items_by_region.read();

        let mut private_count = 0;
        let mut public_count = 0;
        let mut spawned_count = 0;
        let mut picked_up_count = 0;

        for item in items.values() {
            if item.picked_up {
                picked_up_count += 1;
            } else {
                match item.visibility {
                    ItemVisibility::Private => private_count += 1,
                    ItemVisibility::Public => public_count += 1,
                    ItemVisibility::Spawned => spawned_count += 1,
                }
            }
        }

        GroundItemStats {
            total: items.len(),
            private: private_count,
            public: public_count,
            spawned: spawned_count,
            awaiting_respawn: picked_up_count,
            regions: regions.len(),
        }
    }
}

/// Statistics about ground items
#[derive(Debug, Clone)]
pub struct GroundItemStats {
    pub total: usize,
    pub private: usize,
    pub public: usize,
    pub spawned: usize,
    pub awaiting_respawn: usize,
    pub regions: usize,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_location() -> Location {
        Location::new(3222, 3222, 0)
    }

    #[test]
    fn test_ground_item_creation() {
        let item = GroundItem::new_dropped(1, 995, 1000, test_location(), 1, 0);
        assert_eq!(item.id, 1);
        assert_eq!(item.item_id, 995);
        assert_eq!(item.amount, 1000);
        assert_eq!(item.visibility, ItemVisibility::Private);
        assert!(!item.picked_up);
    }

    #[test]
    fn test_ground_item_spawned() {
        let item = GroundItem::new_spawned(1, 995, 100, test_location(), 50);
        assert_eq!(item.visibility, ItemVisibility::Spawned);
        assert_eq!(item.respawn_delay, 50);
        assert_eq!(item.despawn_tick, u64::MAX);
    }

    #[test]
    fn test_visibility_private() {
        let item = GroundItem::new_dropped(1, 995, 100, test_location(), 5, 0);

        // Owner can see it
        assert!(item.is_visible_to(5));
        // Others cannot
        assert!(!item.is_visible_to(6));
        assert!(!item.is_visible_to(1));
    }

    #[test]
    fn test_visibility_public() {
        let mut item = GroundItem::new_dropped(1, 995, 100, test_location(), 5, 0);
        item.make_public();

        // Everyone can see it
        assert!(item.is_visible_to(5));
        assert!(item.is_visible_to(6));
        assert!(item.is_visible_to(1));
    }

    #[test]
    fn test_visibility_spawned() {
        let item = GroundItem::new_spawned(1, 995, 100, test_location(), 50);

        // Everyone can see spawned items
        assert!(item.is_visible_to(1));
        assert!(item.is_visible_to(100));
    }

    #[test]
    fn test_should_become_public() {
        let item = GroundItem::new_dropped(1, 995, 100, test_location(), 5, 0);

        assert!(!item.should_become_public(0));
        assert!(!item.should_become_public(PRIVATE_VISIBILITY_TICKS - 1));
        assert!(item.should_become_public(PRIVATE_VISIBILITY_TICKS));
        assert!(item.should_become_public(PRIVATE_VISIBILITY_TICKS + 1));
    }

    #[test]
    fn test_should_despawn() {
        let item = GroundItem::new_dropped(1, 995, 100, test_location(), 5, 0);

        assert!(!item.should_despawn(0));
        assert!(!item.should_despawn(TOTAL_ITEM_LIFETIME_TICKS - 1));
        assert!(item.should_despawn(TOTAL_ITEM_LIFETIME_TICKS));
        assert!(item.should_despawn(TOTAL_ITEM_LIFETIME_TICKS + 100));
    }

    #[test]
    fn test_spawned_no_despawn() {
        let item = GroundItem::new_spawned(1, 995, 100, test_location(), 50);

        // Spawned items never despawn
        assert!(!item.should_despawn(0));
        assert!(!item.should_despawn(1_000_000));
    }

    #[test]
    fn test_pickup_and_respawn() {
        let mut item = GroundItem::new_spawned(1, 995, 100, test_location(), 50);

        // Pick up at tick 100
        item.pick_up(100);
        assert!(item.picked_up);
        assert_eq!(item.respawn_tick, Some(150)); // 100 + 50

        // Not visible after pickup
        assert!(!item.is_visible_to(1));

        // Should respawn at tick 150
        assert!(!item.should_respawn(149));
        assert!(item.should_respawn(150));

        // Respawn
        item.respawn();
        assert!(!item.picked_up);
        assert!(item.respawn_tick.is_none());
        assert!(item.is_visible_to(1));
    }

    #[test]
    fn test_manager_spawn_dropped() {
        let manager = GroundItemManager::new();
        let result = manager.spawn_dropped(995, 1000, test_location(), 1, 0);

        assert!(result.is_ok());
        let id = result.unwrap();
        assert_eq!(manager.count(), 1);

        let item = manager.get(id).unwrap();
        assert_eq!(item.item_id, 995);
        assert_eq!(item.amount, 1000);
    }

    #[test]
    fn test_manager_spawn_permanent() {
        let manager = GroundItemManager::new();
        let result = manager.spawn_permanent(995, 100, test_location(), 50);

        assert!(result.is_ok());
        let id = result.unwrap();
        assert_eq!(manager.count(), 1);

        let item = manager.get(id).unwrap();
        assert_eq!(item.visibility, ItemVisibility::Spawned);
    }

    #[test]
    fn test_manager_pickup() {
        let manager = GroundItemManager::new();
        let id = manager
            .spawn_dropped(995, 1000, test_location(), 1, 0)
            .unwrap();

        // Owner can pick up
        let result = manager.pick_up(id, 1, 100);
        assert!(result.is_ok());

        let (item_id, amount) = result.unwrap();
        assert_eq!(item_id, 995);
        assert_eq!(amount, 1000);

        // Item is removed (no respawn)
        assert!(manager.get(id).is_none());
    }

    #[test]
    fn test_manager_pickup_not_visible() {
        let manager = GroundItemManager::new();
        let id = manager
            .spawn_dropped(995, 1000, test_location(), 1, 0)
            .unwrap();

        // Non-owner cannot pick up private item
        let result = manager.pick_up(id, 2, 100);
        assert!(matches!(result, Err(GroundItemError::NotVisible)));
    }

    #[test]
    fn test_manager_process_tick_public() {
        let manager = GroundItemManager::new();
        let id = manager
            .spawn_dropped(995, 1000, test_location(), 1, 0)
            .unwrap();

        // Process tick at public time
        let events = manager.process_tick(PRIVATE_VISIBILITY_TICKS);

        // Should have a BecamePublic event
        assert!(!events.is_empty());
        assert!(matches!(events[0], GroundItemEvent::BecamePublic { .. }));

        // Item should now be public
        let item = manager.get(id).unwrap();
        assert_eq!(item.visibility, ItemVisibility::Public);
    }

    #[test]
    fn test_manager_process_tick_despawn() {
        let manager = GroundItemManager::new();
        let id = manager
            .spawn_dropped(995, 1000, test_location(), 1, 0)
            .unwrap();

        // Process tick at despawn time
        let events = manager.process_tick(TOTAL_ITEM_LIFETIME_TICKS);

        // Should have a Remove event
        assert!(!events.is_empty());
        assert!(matches!(events[0], GroundItemEvent::Remove { .. }));

        // Item should be removed
        assert!(manager.get(id).is_none());
    }

    #[test]
    fn test_manager_get_items_near() {
        let manager = GroundItemManager::new();
        let loc1 = Location::new(3222, 3222, 0);
        let loc2 = Location::new(3225, 3225, 0);
        let loc_far = Location::new(3300, 3300, 0);

        manager.spawn_dropped(995, 100, loc1, 1, 0).unwrap();
        manager.spawn_dropped(996, 200, loc2, 1, 0).unwrap();
        manager.spawn_dropped(997, 300, loc_far, 1, 0).unwrap();

        let items = manager.get_items_near(&loc1, 1);
        assert_eq!(items.len(), 2); // Only loc1 and loc2 within view distance
    }

    #[test]
    fn test_manager_stats() {
        let manager = GroundItemManager::new();

        manager
            .spawn_dropped(995, 100, test_location(), 1, 0)
            .unwrap();
        manager
            .spawn_permanent(996, 200, test_location(), 50)
            .unwrap();

        let stats = manager.stats();
        assert_eq!(stats.total, 2);
        assert_eq!(stats.private, 1);
        assert_eq!(stats.spawned, 1);
    }

    #[test]
    fn test_ground_item_error_display() {
        assert_eq!(GroundItemError::NotFound.to_string(), "Item not found");
        assert_eq!(
            GroundItemError::AlreadyPickedUp.to_string(),
            "Someone already took that"
        );
    }

    #[test]
    fn test_item_visibility_enum() {
        assert!(ItemVisibility::Public.is_visible_to(1, None));
        assert!(ItemVisibility::Public.is_visible_to(1, Some(2)));
        assert!(ItemVisibility::Spawned.is_visible_to(100, None));
        assert!(ItemVisibility::Private.is_visible_to(5, Some(5)));
        assert!(!ItemVisibility::Private.is_visible_to(5, Some(6)));
    }
}
