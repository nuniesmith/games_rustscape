/**
 * Game module exports
 *
 * This module provides all game state management functionality.
 */

export {
    // Constants
    SkillId,
    SKILL_NAMES,
    PlayerRights,
    MessageType,

    // Types/Interfaces
    type Skill,
    type Position,
    type MapRegion,
    type ChatMessage,
    type Item,
    type PlayerOption,
    type GameStateListener,

    // Helper functions
    getSkillName,
    calculateLevelFromXp,
    getExperienceForLevel,
    emptyItem,
    isItemEmpty,
    defaultPosition,
    defaultMapRegion,
    createSkill,
    getRegionX,
    getRegionY,
    getLocalX,
    getLocalY,
    distanceTo,
    isWithinDistance,

    // Classes
    Inventory,
    GameState,

    // Singleton instance
    gameState,
} from "./GameState";

// Default export is the singleton game state
export { default } from "./GameState";
