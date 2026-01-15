/**
 * GameState - Client-side game state management
 *
 * This module manages all client-side game state including:
 * - Player information and position
 * - Skills and experience
 * - Inventory and equipment
 * - Chat messages
 * - Map regions
 */

// ============ Constants ============

/**
 * Skill IDs matching the RS protocol
 */
export const SkillId = {
    ATTACK: 0,
    DEFENCE: 1,
    STRENGTH: 2,
    HITPOINTS: 3,
    RANGED: 4,
    PRAYER: 5,
    MAGIC: 6,
    COOKING: 7,
    WOODCUTTING: 8,
    FLETCHING: 9,
    FISHING: 10,
    FIREMAKING: 11,
    CRAFTING: 12,
    SMITHING: 13,
    MINING: 14,
    HERBLORE: 15,
    AGILITY: 16,
    THIEVING: 17,
    SLAYER: 18,
    FARMING: 19,
    RUNECRAFT: 20,
    HUNTER: 21,
    CONSTRUCTION: 22,
    SUMMONING: 23,
    DUNGEONEERING: 24,
    SKILL_COUNT: 25,
} as const;

export const SKILL_NAMES = [
    "Attack",
    "Defence",
    "Strength",
    "Hitpoints",
    "Ranged",
    "Prayer",
    "Magic",
    "Cooking",
    "Woodcutting",
    "Fletching",
    "Fishing",
    "Firemaking",
    "Crafting",
    "Smithing",
    "Mining",
    "Herblore",
    "Agility",
    "Thieving",
    "Slayer",
    "Farming",
    "Runecraft",
    "Hunter",
    "Construction",
    "Summoning",
    "Dungeoneering",
];

/**
 * Player rights/privilege levels
 */
export enum PlayerRights {
    PLAYER = 0,
    MODERATOR = 1,
    ADMINISTRATOR = 2,
    OWNER = 3,
}

/**
 * Chat message types
 */
export enum MessageType {
    GAME = 0,
    PUBLIC_CHAT = 1,
    PRIVATE_MESSAGE_IN = 2,
    PRIVATE_MESSAGE_OUT = 3,
    TRADE_REQUEST = 4,
    FRIEND_STATUS = 5,
    CLAN_CHAT = 9,
    FILTERED = 109,
}

// ============ Interfaces ============

/**
 * Represents a skill with level and experience
 */
export interface Skill {
    id: number;
    level: number;
    experience: number;
    boostedLevel: number;
}

/**
 * Represents a position in the game world
 */
export interface Position {
    x: number;
    y: number;
    z: number;
}

/**
 * Represents the current map region
 */
export interface MapRegion {
    baseX: number;
    baseY: number;
}

/**
 * Represents a chat message
 */
export interface ChatMessage {
    text: string;
    sender?: string;
    type: MessageType;
    timestamp: number;
}

/**
 * Represents an item in inventory or equipment
 */
export interface Item {
    id: number;
    amount: number;
}

/**
 * Player option in right-click menu
 */
export interface PlayerOption {
    slot: number;
    text: string;
    priority: boolean;
}

/**
 * Represents another player in the game world
 */
export interface OtherPlayer {
    index: number;
    name: string;
    position: Position;
    combatLevel: number;
    appearance?: PlayerAppearance;
    animation?: number;
    lastUpdate: number;
}

/**
 * Player appearance data
 */
export interface PlayerAppearance {
    gender: number;
    headIcon: number;
    prayerIcon: number;
    equipmentSlots: number[];
    colors: number[];
}

/**
 * Listener interface for game state changes
 */
export interface GameStateListener {
    onSkillUpdated?(skillId: number, level: number, experience: number): void;
    onMessageReceived?(message: ChatMessage): void;
    onPositionChanged?(position: Position): void;
    onMapRegionChanged?(region: MapRegion): void;
    onPlayerOptionsChanged?(options: PlayerOption[]): void;
    onPlayerInfoChanged?(
        name: string,
        index: number,
        rights: PlayerRights,
        member: boolean,
    ): void;
    onInventoryChanged?(items: Item[]): void;
    onEquipmentChanged?(items: Item[]): void;
    onRunEnergyChanged?(energy: number): void;
    onWeightChanged?(weight: number): void;
    onOtherPlayerAdded?(player: OtherPlayer): void;
    onOtherPlayerRemoved?(playerIndex: number): void;
    onOtherPlayerUpdated?(player: OtherPlayer): void;
}

// ============ Helper Functions ============

/**
 * Get skill name by ID
 */
export function getSkillName(id: number): string {
    return SKILL_NAMES[id] ?? "Unknown";
}

/**
 * Calculate level from experience using RS formula
 */
export function calculateLevelFromXp(experience: number): number {
    let points = 0;
    for (let lvl = 1; lvl <= 99; lvl++) {
        points += Math.floor(lvl + 300 * Math.pow(2, lvl / 7));
        const output = Math.floor(points / 4);
        if (output >= experience) {
            return lvl;
        }
    }
    return 99;
}

/**
 * Get experience required for a specific level
 */
export function getExperienceForLevel(level: number): number {
    let points = 0;
    for (let lvl = 1; lvl < level; lvl++) {
        points += Math.floor(lvl + 300 * Math.pow(2, lvl / 7));
    }
    return Math.floor(points / 4);
}

/**
 * Create an empty item
 */
export function emptyItem(): Item {
    return { id: -1, amount: 0 };
}

/**
 * Check if an item is empty
 */
export function isItemEmpty(item: Item): boolean {
    return item.id <= 0 || item.amount <= 0;
}

/**
 * Create a default position
 */
export function defaultPosition(): Position {
    return { x: 0, y: 0, z: 0 };
}

/**
 * Create a default map region
 */
export function defaultMapRegion(): MapRegion {
    return { baseX: 0, baseY: 0 };
}

/**
 * Create a default skill
 */
export function createSkill(id: number, isHitpoints: boolean = false): Skill {
    const defaultLevel = isHitpoints ? 10 : 1;
    return {
        id,
        level: defaultLevel,
        experience: 0,
        boostedLevel: defaultLevel,
    };
}

// ============ Position Utilities ============

/**
 * Get region X coordinate from position
 */
export function getRegionX(position: Position): number {
    return position.x >> 6;
}

/**
 * Get region Y coordinate from position
 */
export function getRegionY(position: Position): number {
    return position.y >> 6;
}

/**
 * Get local X within region from position
 */
export function getLocalX(position: Position): number {
    return position.x & 63;
}

/**
 * Get local Y within region from position
 */
export function getLocalY(position: Position): number {
    return position.y & 63;
}

/**
 * Calculate distance between two positions
 */
export function distanceTo(from: Position, to: Position): number {
    const dx = Math.abs(from.x - to.x);
    const dy = Math.abs(from.y - to.y);
    return Math.max(dx, dy);
}

/**
 * Check if two positions are within a certain distance
 */
export function isWithinDistance(
    from: Position,
    to: Position,
    distance: number,
): boolean {
    return from.z === to.z && distanceTo(from, to) <= distance;
}

// ============ Inventory Class ============

/**
 * Represents an inventory container
 */
export class Inventory {
    private items: Item[];

    constructor(public readonly capacity: number = 28) {
        this.items = Array(capacity)
            .fill(null)
            .map(() => emptyItem());
    }

    get(slot: number): Item {
        if (slot >= 0 && slot < this.capacity) {
            return this.items[slot];
        }
        return emptyItem();
    }

    set(slot: number, item: Item): void {
        if (slot >= 0 && slot < this.capacity) {
            this.items[slot] = { ...item };
        }
    }

    clear(): void {
        for (let i = 0; i < this.capacity; i++) {
            this.items[i] = emptyItem();
        }
    }

    toArray(): Item[] {
        return this.items.map((item) => ({ ...item }));
    }

    get freeSlots(): number {
        return this.items.filter((item) => isItemEmpty(item)).length;
    }

    get usedSlots(): number {
        return this.items.filter((item) => !isItemEmpty(item)).length;
    }

    /**
     * Find first slot containing item with given ID
     */
    findItem(itemId: number): number {
        return this.items.findIndex((item) => item.id === itemId);
    }

    /**
     * Find first empty slot
     */
    findEmptySlot(): number {
        return this.items.findIndex((item) => isItemEmpty(item));
    }

    /**
     * Count total amount of item with given ID
     */
    countItem(itemId: number): number {
        return this.items
            .filter((item) => item.id === itemId)
            .reduce((sum, item) => sum + item.amount, 0);
    }
}

// ============ Main GameState Class ============

/**
 * Main game state container
 * This holds all the client-side game state that needs to be shared
 * across the UI and network layers.
 */
export class GameState {
    // Player info
    playerName: string = "";
    playerIndex: number = -1;
    rights: PlayerRights = PlayerRights.PLAYER;
    isMember: boolean = false;

    // Position and map
    position: Position = defaultPosition();
    mapRegion: MapRegion = defaultMapRegion();

    // Skills
    skills: Skill[] = [];

    // Energy and weight
    runEnergy: number = 100;
    weight: number = 0;
    isRunning: boolean = false;

    // Chat messages
    private _messages: ChatMessage[] = [];
    maxMessages: number = 100;

    // Player options (right-click menu)
    private _playerOptions: PlayerOption[] = [];

    // Inventory (28 slots)
    inventory: Inventory = new Inventory(28);

    // Equipment (11 slots)
    equipment: Inventory = new Inventory(11);

    // Other players in view (indexed by player index)
    private _otherPlayers: Map<number, OtherPlayer> = new Map();

    // State listeners for UI updates
    private listeners: GameStateListener[] = [];

    constructor() {
        this.initializeSkills();
    }

    private initializeSkills(): void {
        this.skills = [];
        for (let i = 0; i < SkillId.SKILL_COUNT; i++) {
            this.skills.push(createSkill(i, i === SkillId.HITPOINTS));
        }
    }

    // ============ Skill Methods ============

    getSkill(id: number): Skill | undefined {
        return this.skills[id];
    }

    updateSkill(id: number, level: number, experience: number): void {
        const skill = this.skills[id];
        if (skill) {
            skill.level = level;
            skill.experience = experience;
            skill.boostedLevel = level;
            this.notifyListeners((l) =>
                l.onSkillUpdated?.(id, level, experience),
            );
        }
    }

    getTotalLevel(): number {
        return this.skills.reduce((sum, skill) => sum + skill.level, 0);
    }

    getCombatLevel(): number {
        const attack = this.skills[SkillId.ATTACK]?.level ?? 1;
        const strength = this.skills[SkillId.STRENGTH]?.level ?? 1;
        const defence = this.skills[SkillId.DEFENCE]?.level ?? 1;
        const hitpoints = this.skills[SkillId.HITPOINTS]?.level ?? 10;
        const prayer = this.skills[SkillId.PRAYER]?.level ?? 1;
        const ranged = this.skills[SkillId.RANGED]?.level ?? 1;
        const magic = this.skills[SkillId.MAGIC]?.level ?? 1;

        const base = (defence + hitpoints + Math.floor(prayer / 2)) * 0.25;
        const melee = (attack + strength) * 0.325;
        const range = ranged * 0.4875;
        const mage = magic * 0.4875;

        return Math.floor(base + Math.max(melee, range, mage));
    }

    // ============ Message Methods ============

    get messages(): ChatMessage[] {
        return [...this._messages];
    }

    addMessage(message: ChatMessage): void {
        this._messages.unshift(message);
        while (this._messages.length > this.maxMessages) {
            this._messages.pop();
        }
        this.notifyListeners((l) => l.onMessageReceived?.(message));
    }

    addTextMessage(
        text: string,
        type: MessageType = MessageType.GAME,
        sender?: string,
    ): void {
        this.addMessage({
            text,
            sender,
            type,
            timestamp: Date.now(),
        });
    }

    clearMessages(): void {
        this._messages = [];
    }

    // ============ Position Methods ============

    setPosition(x: number, y: number, z: number = 0): void {
        this.position = { x, y, z };
        this.notifyListeners((l) => l.onPositionChanged?.(this.position));
    }

    setMapRegion(baseX: number, baseY: number): void {
        this.mapRegion = { baseX, baseY };
        this.notifyListeners((l) => l.onMapRegionChanged?.(this.mapRegion));
    }

    // ============ Player Option Methods ============

    get playerOptions(): PlayerOption[] {
        return [...this._playerOptions];
    }

    setPlayerOption(
        slot: number,
        text: string,
        priority: boolean = false,
    ): void {
        this._playerOptions = this._playerOptions.filter(
            (opt) => opt.slot !== slot,
        );
        if (text.trim()) {
            this._playerOptions.push({ slot, text, priority });
            this._playerOptions.sort((a, b) => a.slot - b.slot);
        }
        this.notifyListeners((l) =>
            l.onPlayerOptionsChanged?.([...this._playerOptions]),
        );
    }

    clearPlayerOptions(): void {
        this._playerOptions = [];
    }

    // ============ Player Info Methods ============

    setPlayerInfo(
        name: string,
        index: number,
        rights: PlayerRights,
        member: boolean,
    ): void {
        this.playerName = name;
        this.playerIndex = index;
        this.rights = rights;
        this.isMember = member;
        this.notifyListeners((l) =>
            l.onPlayerInfoChanged?.(name, index, rights, member),
        );
    }

    // ============ Energy Methods ============

    setRunEnergy(energy: number): void {
        this.runEnergy = Math.max(0, Math.min(100, energy));
        this.notifyListeners((l) => l.onRunEnergyChanged?.(this.runEnergy));
    }

    setWeight(weight: number): void {
        this.weight = weight;
        this.notifyListeners((l) => l.onWeightChanged?.(this.weight));
    }

    // ============ Inventory Methods ============

    updateInventorySlot(slot: number, itemId: number, amount: number): void {
        this.inventory.set(slot, { id: itemId, amount });
        this.notifyListeners((l) =>
            l.onInventoryChanged?.(this.inventory.toArray()),
        );
    }

    updateEquipmentSlot(slot: number, itemId: number, amount: number): void {
        this.equipment.set(slot, { id: itemId, amount });
        this.notifyListeners((l) =>
            l.onEquipmentChanged?.(this.equipment.toArray()),
        );
    }

    // ============ Other Players Methods ============

    /**
     * Get all other players in view
     */
    get otherPlayers(): OtherPlayer[] {
        return Array.from(this._otherPlayers.values());
    }

    /**
     * Get other player by index
     */
    getOtherPlayer(index: number): OtherPlayer | undefined {
        return this._otherPlayers.get(index);
    }

    /**
     * Add or update another player
     */
    addOrUpdateOtherPlayer(
        index: number,
        name: string,
        x: number,
        y: number,
        z: number,
        combatLevel: number,
        appearance?: PlayerAppearance,
    ): void {
        const existing = this._otherPlayers.get(index);
        const player: OtherPlayer = {
            index,
            name,
            position: { x, y, z },
            combatLevel,
            appearance: appearance ?? existing?.appearance,
            animation: existing?.animation,
            lastUpdate: Date.now(),
        };

        if (existing) {
            this._otherPlayers.set(index, player);
            this.notifyListeners((l) => l.onOtherPlayerUpdated?.(player));
        } else {
            this._otherPlayers.set(index, player);
            this.notifyListeners((l) => l.onOtherPlayerAdded?.(player));
        }
    }

    /**
     * Update another player's position
     */
    updateOtherPlayerPosition(
        index: number,
        x: number,
        y: number,
        z?: number,
    ): void {
        const player = this._otherPlayers.get(index);
        if (player) {
            player.position.x = x;
            player.position.y = y;
            if (z !== undefined) {
                player.position.z = z;
            }
            player.lastUpdate = Date.now();
            this.notifyListeners((l) => l.onOtherPlayerUpdated?.(player));
        }
    }

    /**
     * Update another player's animation
     */
    updateOtherPlayerAnimation(index: number, animationId: number): void {
        const player = this._otherPlayers.get(index);
        if (player) {
            player.animation = animationId;
            player.lastUpdate = Date.now();
            this.notifyListeners((l) => l.onOtherPlayerUpdated?.(player));
        }
    }

    /**
     * Remove another player from view
     */
    removeOtherPlayer(index: number): void {
        if (this._otherPlayers.delete(index)) {
            this.notifyListeners((l) => l.onOtherPlayerRemoved?.(index));
        }
    }

    /**
     * Clear all other players (e.g., on region change)
     */
    clearOtherPlayers(): void {
        const indices = Array.from(this._otherPlayers.keys());
        this._otherPlayers.clear();
        indices.forEach((index) => {
            this.notifyListeners((l) => l.onOtherPlayerRemoved?.(index));
        });
    }

    /**
     * Get count of other players in view
     */
    get otherPlayerCount(): number {
        return this._otherPlayers.size;
    }

    /**
     * Find other players within a distance
     */
    getPlayersWithinDistance(distance: number): OtherPlayer[] {
        return this.otherPlayers.filter((player) =>
            isWithinDistance(this.position, player.position, distance),
        );
    }

    // ============ Listener Management ============

    addListener(listener: GameStateListener): void {
        this.listeners.push(listener);
    }

    removeListener(listener: GameStateListener): void {
        const index = this.listeners.indexOf(listener);
        if (index >= 0) {
            this.listeners.splice(index, 1);
        }
    }

    private notifyListeners(
        action: (listener: GameStateListener) => void,
    ): void {
        this.listeners.forEach(action);
    }

    // ============ Reset ============

    reset(): void {
        this.playerName = "";
        this.playerIndex = -1;
        this.rights = PlayerRights.PLAYER;
        this.isMember = false;
        this.position = defaultPosition();
        this.mapRegion = defaultMapRegion();
        this.runEnergy = 100;
        this.weight = 0;
        this.isRunning = false;

        this.initializeSkills();
        this._messages = [];
        this._playerOptions = [];
        this.inventory.clear();
        this.equipment.clear();
        this._otherPlayers.clear();
    }

    // ============ Debug Methods ============

    /**
     * Get a summary of the current game state for debugging
     */
    toDebugString(): string {
        return `GameState {
  player: ${this.playerName} (index: ${this.playerIndex}, rights: ${PlayerRights[this.rights]})
  position: (${this.position.x}, ${this.position.y}, ${this.position.z})
  region: (${this.mapRegion.baseX}, ${this.mapRegion.baseY})
  combat: ${this.getCombatLevel()}, total: ${this.getTotalLevel()}
  energy: ${this.runEnergy}, weight: ${this.weight}
  messages: ${this._messages.length}
  inventory: ${this.inventory.usedSlots}/${this.inventory.capacity}
  otherPlayers: ${this._otherPlayers.size}
}`;
    }
}

// ============ Singleton Instance ============

/**
 * Global game state instance
 */
export const gameState = new GameState();

export default gameState;
