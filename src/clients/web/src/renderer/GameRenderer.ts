/**
 * GameRenderer - PixiJS-based 2D game renderer
 *
 * Handles all visual rendering for the Rustscape web client including:
 * - Game world rendering with sprites
 * - Player and NPC rendering
 * - UI elements (minimap, chat, skills panel)
 * - Particle effects and animations
 */

import {
    Application,
    Container,
    Graphics,
    Text,
    TextStyle,
    Sprite,
    Texture,
    Assets,
} from "pixi.js";

// Game constants
const GAME_WIDTH = 765;
const GAME_HEIGHT = 503;
const TILE_SIZE = 32;
const MINIMAP_SIZE = 150;

// Skill names for display
const SKILL_NAMES = [
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
    "Runecrafting",
    "Hunter",
    "Construction",
    "Summoning",
    "Dungeoneering",
];

// Color palette
const COLORS = {
    background: 0x1a1a2e,
    backgroundLight: 0x16213e,
    backgroundDark: 0x0f3460,
    primary: 0xe94560,
    primaryLight: 0xff6b6b,
    text: 0xffffff,
    textMuted: 0x888888,
    textYellow: 0xffff00,
    textCyan: 0x00ffff,
    textGold: 0xffd700,
    textSilver: 0xc0c0c0,
    mapGreen: 0x2d5a27,
    chatBackground: 0x000000,
    panelBackground: 0x000000,
};

/**
 * Game state interface
 */
export interface GameState {
    playerName: string;
    playerIndex: number;
    rights: number;
    member: boolean;
    skills: { level: number; xp: number }[];
    messages: { text: string; timestamp: number }[];
    playerOptions: string[];
    mapRegion: { x: number; y: number } | null;
    position: { x: number; y: number; z: number };
}

/**
 * Sprite sheet definition
 */
interface SpriteSheet {
    texture: Texture;
    frames: Map<
        string,
        { x: number; y: number; width: number; height: number }
    >;
}

/**
 * Main game renderer class
 */
export class GameRenderer {
    private app: Application | null = null;
    private canvas: HTMLCanvasElement;
    private initialized = false;

    // Containers for organizing render layers
    private worldContainer: Container | null = null;
    private entityContainer: Container | null = null;
    private uiContainer: Container | null = null;
    private chatContainer: Container | null = null;

    // UI elements
    private minimapGraphics: Graphics | null = null;
    private skillsPanel: Container | null = null;
    private chatPanel: Container | null = null;
    private playerSprite: Container | null = null;
    private playerNameText: Text | null = null;

    // Loaded assets
    private sprites: Map<string, Texture> = new Map();
    private loadedAssets = false;

    // Current game state
    private gameState: GameState = {
        playerName: "",
        playerIndex: 0,
        rights: 0,
        member: false,
        skills: Array(25)
            .fill(null)
            .map(() => ({ level: 1, xp: 0 })),
        messages: [],
        playerOptions: [],
        mapRegion: null,
        position: { x: 3222, y: 3222, z: 0 },
    };

    constructor(canvas: HTMLCanvasElement) {
        this.canvas = canvas;
    }

    /**
     * Initialize the PixiJS application
     */
    async init(): Promise<void> {
        if (this.initialized) return;

        console.log("Initializing PixiJS renderer...");

        // Create PixiJS application
        this.app = new Application();

        await this.app.init({
            canvas: this.canvas,
            width: GAME_WIDTH,
            height: GAME_HEIGHT,
            backgroundColor: COLORS.background,
            antialias: true,
            resolution: window.devicePixelRatio || 1,
            autoDensity: true,
        });

        // Create render layers
        this.worldContainer = new Container();
        this.entityContainer = new Container();
        this.uiContainer = new Container();
        this.chatContainer = new Container();

        this.app.stage.addChild(this.worldContainer);
        this.app.stage.addChild(this.entityContainer);
        this.app.stage.addChild(this.uiContainer);
        this.app.stage.addChild(this.chatContainer);

        // Load assets
        await this.loadAssets();

        // Create UI elements
        this.createWorld();
        this.createPlayer();
        this.createMinimap();
        this.createSkillsPanel();
        this.createChatPanel();

        this.initialized = true;
        console.log("PixiJS renderer initialized");
    }

    /**
     * Load game assets
     */
    private async loadAssets(): Promise<void> {
        console.log("Loading game assets...");

        try {
            // Try to load sprite sheets from server
            // For now, we'll use placeholder graphics
            // In production, these would be extracted from the game cache

            // Placeholder: Create basic textures programmatically
            this.createPlaceholderTextures();

            this.loadedAssets = true;
            console.log("Assets loaded (using placeholders)");
        } catch (error) {
            console.warn("Failed to load assets, using placeholders:", error);
            this.createPlaceholderTextures();
            this.loadedAssets = true;
        }
    }

    /**
     * Create placeholder textures for development
     */
    private createPlaceholderTextures(): void {
        // Player texture
        const playerGraphics = new Graphics();
        playerGraphics.circle(16, 16, 15);
        playerGraphics.fill(COLORS.primary);
        playerGraphics.circle(12, 12, 5);
        playerGraphics.fill(COLORS.primaryLight);

        // Convert graphics to texture would require additional setup
        // For now, we'll use Graphics directly
    }

    /**
     * Create the game world background
     */
    private createWorld(): void {
        if (!this.worldContainer) return;

        // Create gradient background
        const bg = new Graphics();

        // Draw gradient manually (PixiJS 8 approach)
        const gradientSteps = 20;
        const stepHeight = (GAME_HEIGHT - 100) / gradientSteps;

        for (let i = 0; i < gradientSteps; i++) {
            const ratio = i / gradientSteps;
            const color = this.lerpColor(
                COLORS.backgroundLight,
                COLORS.backgroundDark,
                ratio,
            );
            bg.rect(0, i * stepHeight, GAME_WIDTH, stepHeight + 1);
            bg.fill(color);
        }

        this.worldContainer.addChild(bg);

        // Draw grid lines
        const grid = new Graphics();
        grid.stroke({ width: 1, color: 0xffffff, alpha: 0.05 });

        // Vertical lines
        for (let x = 0; x < GAME_WIDTH; x += 50) {
            grid.moveTo(x, 0);
            grid.lineTo(x, GAME_HEIGHT - 100);
        }

        // Horizontal lines
        for (let y = 0; y < GAME_HEIGHT - 100; y += 50) {
            grid.moveTo(0, y);
            grid.lineTo(GAME_WIDTH, y);
        }

        grid.stroke();
        this.worldContainer.addChild(grid);
    }

    /**
     * Create the player sprite and name
     */
    private createPlayer(): void {
        if (!this.entityContainer) return;

        this.playerSprite = new Container();

        const playerX = GAME_WIDTH / 2;
        const playerY = (GAME_HEIGHT - 100) / 2;

        // Player shadow
        const shadow = new Graphics();
        shadow.ellipse(0, 15, 20, 8);
        shadow.fill({ color: 0x000000, alpha: 0.3 });
        this.playerSprite.addChild(shadow);

        // Player body
        const body = new Graphics();
        body.circle(0, 0, 15);
        body.fill(COLORS.primary);
        this.playerSprite.addChild(body);

        // Player highlight
        const highlight = new Graphics();
        highlight.circle(-4, -4, 5);
        highlight.fill(COLORS.primaryLight);
        this.playerSprite.addChild(highlight);

        this.playerSprite.x = playerX;
        this.playerSprite.y = playerY;

        this.entityContainer.addChild(this.playerSprite);

        // Player name
        const nameStyle = new TextStyle({
            fontFamily: "Arial",
            fontSize: 14,
            fontWeight: "bold",
            fill: COLORS.textCyan,
        });

        this.playerNameText = new Text({
            text: this.gameState.playerName || "Player",
            style: nameStyle,
        });
        this.playerNameText.anchor.set(0.5, 1);
        this.playerNameText.x = playerX;
        this.playerNameText.y = playerY - 25;

        this.entityContainer.addChild(this.playerNameText);
    }

    /**
     * Create the minimap display
     */
    private createMinimap(): void {
        if (!this.uiContainer) return;

        const minimapContainer = new Container();
        minimapContainer.x = GAME_WIDTH - MINIMAP_SIZE - 10;
        minimapContainer.y = 10;

        // Background
        const bg = new Graphics();
        bg.rect(0, 0, MINIMAP_SIZE, MINIMAP_SIZE);
        bg.fill({ color: COLORS.panelBackground, alpha: 0.7 });
        bg.stroke({ width: 2, color: COLORS.primary });
        minimapContainer.addChild(bg);

        // Map area (green)
        const mapArea = new Graphics();
        mapArea.rect(5, 5, MINIMAP_SIZE - 10, MINIMAP_SIZE - 10);
        mapArea.fill(COLORS.mapGreen);
        minimapContainer.addChild(mapArea);

        // Player dot
        const playerDot = new Graphics();
        playerDot.circle(MINIMAP_SIZE / 2, MINIMAP_SIZE / 2, 3);
        playerDot.fill(COLORS.text);
        minimapContainer.addChild(playerDot);

        // Store reference for updates
        this.minimapGraphics = new Graphics();
        minimapContainer.addChild(this.minimapGraphics);

        // Region text (will be updated)
        const regionStyle = new TextStyle({
            fontFamily: "Arial",
            fontSize: 10,
            fill: COLORS.text,
        });

        const regionText = new Text({
            text: "Region: --",
            style: regionStyle,
        });
        regionText.anchor.set(0.5, 0);
        regionText.x = MINIMAP_SIZE / 2;
        regionText.y = MINIMAP_SIZE - 15;
        regionText.name = "regionText";
        minimapContainer.addChild(regionText);

        this.uiContainer.addChild(minimapContainer);
    }

    /**
     * Create the skills panel
     */
    private createSkillsPanel(): void {
        if (!this.uiContainer) return;

        this.skillsPanel = new Container();
        this.skillsPanel.x = GAME_WIDTH - 160;
        this.skillsPanel.y = MINIMAP_SIZE + 20;

        // Background
        const bg = new Graphics();
        bg.rect(0, 0, 160, 220);
        bg.fill({ color: COLORS.panelBackground, alpha: 0.7 });
        bg.stroke({ width: 2, color: COLORS.primary });
        this.skillsPanel.addChild(bg);

        // Title
        const titleStyle = new TextStyle({
            fontFamily: "Arial",
            fontSize: 12,
            fontWeight: "bold",
            fill: COLORS.primary,
        });

        const title = new Text({ text: "Skills", style: titleStyle });
        title.x = 10;
        title.y = 8;
        this.skillsPanel.addChild(title);

        // Skills list (will be updated dynamically)
        this.updateSkillsDisplay();

        this.uiContainer.addChild(this.skillsPanel);
    }

    /**
     * Update skills display
     */
    private updateSkillsDisplay(): void {
        if (!this.skillsPanel) return;

        // Remove old skill texts
        const toRemove: Text[] = [];
        this.skillsPanel.children.forEach((child) => {
            if (child instanceof Text && child.name?.startsWith("skill_")) {
                toRemove.push(child);
            }
        });
        toRemove.forEach((child) => this.skillsPanel?.removeChild(child));

        // Skill styles
        const skillNameStyle = new TextStyle({
            fontFamily: "Arial",
            fontSize: 10,
            fill: COLORS.textMuted,
        });

        const skillLevelStyle = new TextStyle({
            fontFamily: "Arial",
            fontSize: 10,
            fill: COLORS.text,
        });

        // Show first 10 skills
        const skillsToShow = Math.min(10, this.gameState.skills.length);
        for (let i = 0; i < skillsToShow; i++) {
            const skill = this.gameState.skills[i];
            const y = 30 + i * 18;

            // Skill name
            const nameText = new Text({
                text: SKILL_NAMES[i] || `Skill ${i}`,
                style: skillNameStyle,
            });
            nameText.x = 10;
            nameText.y = y;
            nameText.name = `skill_name_${i}`;
            this.skillsPanel.addChild(nameText);

            // Skill level
            const levelText = new Text({
                text: skill.level.toString(),
                style: skillLevelStyle,
            });
            levelText.anchor.set(1, 0);
            levelText.x = 150;
            levelText.y = y;
            levelText.name = `skill_level_${i}`;
            this.skillsPanel.addChild(levelText);
        }

        // Combat level
        const combatLevel = this.calculateCombatLevel();
        const combatStyle = new TextStyle({
            fontFamily: "Arial",
            fontSize: 11,
            fontWeight: "bold",
            fill: COLORS.textGold,
        });

        // Remove old combat text
        const oldCombat = this.skillsPanel.children.find(
            (c) => c.name === "combat_level",
        );
        if (oldCombat) this.skillsPanel.removeChild(oldCombat);

        const combatText = new Text({
            text: `Combat: ${combatLevel}`,
            style: combatStyle,
        });
        combatText.anchor.set(0.5, 0);
        combatText.x = 80;
        combatText.y = 200;
        combatText.name = "combat_level";
        this.skillsPanel.addChild(combatText);
    }

    /**
     * Calculate combat level
     */
    private calculateCombatLevel(): number {
        const att = this.gameState.skills[0]?.level || 1;
        const str = this.gameState.skills[2]?.level || 1;
        const def = this.gameState.skills[1]?.level || 1;
        const hp = this.gameState.skills[3]?.level || 10;
        const prayer = this.gameState.skills[5]?.level || 1;

        return Math.floor(
            (def + hp + Math.floor(prayer / 2)) * 0.25 + (att + str) * 0.325,
        );
    }

    /**
     * Create the chat panel
     */
    private createChatPanel(): void {
        if (!this.chatContainer) return;

        this.chatPanel = new Container();
        this.chatPanel.y = GAME_HEIGHT - 100;

        // Background
        const bg = new Graphics();
        bg.rect(0, 0, GAME_WIDTH, 100);
        bg.fill({ color: COLORS.chatBackground, alpha: 0.8 });
        this.chatPanel.addChild(bg);

        // Top border
        const border = new Graphics();
        border.moveTo(0, 0);
        border.lineTo(GAME_WIDTH, 0);
        border.stroke({ width: 2, color: COLORS.primary });
        this.chatPanel.addChild(border);

        this.chatContainer.addChild(this.chatPanel);
    }

    /**
     * Update chat messages display
     */
    private updateChatDisplay(): void {
        const chatPanel = this.chatPanel;
        if (!chatPanel) return;

        // Remove old message texts
        const toRemove: Text[] = [];
        chatPanel.children.forEach((child) => {
            if (child instanceof Text && child.name?.startsWith("msg_")) {
                toRemove.push(child);
            }
        });
        toRemove.forEach((child) => chatPanel.removeChild(child));

        // Message style
        const msgStyle = new TextStyle({
            fontFamily: "Arial",
            fontSize: 12,
            fill: COLORS.textYellow,
        });

        // Show last 5 messages
        const visibleMessages = this.gameState.messages.slice(-5);

        if (visibleMessages.length === 0) {
            // Placeholder text
            const placeholder = new Text({
                text: "Chat messages will appear here...",
                style: new TextStyle({
                    fontFamily: "Arial",
                    fontSize: 12,
                    fill: 0x666666,
                }),
            });
            placeholder.x = 10;
            placeholder.y = 20;
            placeholder.name = "msg_placeholder";
            chatPanel.addChild(placeholder);
        } else {
            visibleMessages.forEach((msg, i) => {
                const msgText = new Text({
                    text: msg.text,
                    style: msgStyle,
                });
                msgText.x = 10;
                msgText.y = 20 + i * 16;
                msgText.name = `msg_${i}`;
                chatPanel.addChild(msgText);
            });
        }
    }

    /**
     * Update minimap region display
     */
    private updateMinimapRegion(): void {
        if (!this.uiContainer) return;

        // Find the minimap container
        const minimapContainer = this.uiContainer.children[0] as Container;
        if (!minimapContainer) return;

        // Find and update region text
        const regionText = minimapContainer.children.find(
            (c) => c.name === "regionText",
        ) as Text;
        if (regionText && this.gameState.mapRegion) {
            regionText.text = `Region: ${this.gameState.mapRegion.x}, ${this.gameState.mapRegion.y}`;
        }
    }

    /**
     * Update player display (name, crown, etc.)
     */
    private updatePlayerDisplay(): void {
        if (!this.playerNameText || !this.entityContainer) return;

        // Update name
        this.playerNameText.text = this.gameState.playerName || "Player";

        // Update color based on rights
        const color = this.getRightsColor(this.gameState.rights);
        this.playerNameText.style.fill = color;

        // Update or create crown icon
        const existingCrown = this.entityContainer.children.find(
            (c) => c.name === "crown",
        ) as Text;
        if (existingCrown) {
            this.entityContainer.removeChild(existingCrown);
        }

        if (this.gameState.rights >= 1) {
            const crownStyle = new TextStyle({
                fontFamily: "Arial",
                fontSize: 12,
                fill:
                    this.gameState.rights >= 2
                        ? COLORS.textGold
                        : COLORS.textSilver,
            });

            const crown = new Text({
                text: this.gameState.rights >= 2 ? "👑" : "⚔️",
                style: crownStyle,
            });
            crown.anchor.set(0.5, 1);
            crown.x = this.playerNameText.x - 40;
            crown.y = this.playerNameText.y;
            crown.name = "crown";
            this.entityContainer.addChild(crown);
        }
    }

    /**
     * Get color for player rights level
     */
    private getRightsColor(rights: number): number {
        switch (rights) {
            case 2:
                return COLORS.textGold; // Admin
            case 1:
                return COLORS.textSilver; // Mod
            default:
                return COLORS.textCyan; // Normal
        }
    }

    /**
     * Linear interpolation between two colors
     */
    private lerpColor(color1: number, color2: number, ratio: number): number {
        const r1 = (color1 >> 16) & 0xff;
        const g1 = (color1 >> 8) & 0xff;
        const b1 = color1 & 0xff;

        const r2 = (color2 >> 16) & 0xff;
        const g2 = (color2 >> 8) & 0xff;
        const b2 = color2 & 0xff;

        const r = Math.round(r1 + (r2 - r1) * ratio);
        const g = Math.round(g1 + (g2 - g1) * ratio);
        const b = Math.round(b1 + (b2 - b1) * ratio);

        return (r << 16) | (g << 8) | b;
    }

    /**
     * Update game state and refresh display
     */
    updateGameState(state: Partial<GameState>): void {
        // Merge state
        Object.assign(this.gameState, state);

        // Update displays
        if (state.skills) {
            this.updateSkillsDisplay();
        }

        if (state.messages) {
            this.updateChatDisplay();
        }

        if (state.mapRegion) {
            this.updateMinimapRegion();
        }

        if (state.playerName !== undefined || state.rights !== undefined) {
            this.updatePlayerDisplay();
        }
    }

    /**
     * Get current game state
     */
    getGameState(): GameState {
        return { ...this.gameState };
    }

    /**
     * Start the render loop
     */
    start(): void {
        if (!this.app) return;

        // PixiJS 8 handles the render loop automatically
        // We just need to update our displays
        this.updateChatDisplay();
        this.updateSkillsDisplay();
        this.updatePlayerDisplay();

        console.log("Renderer started");
    }

    /**
     * Stop the renderer
     */
    stop(): void {
        if (this.app) {
            this.app.stop();
        }
        console.log("Renderer stopped");
    }

    /**
     * Destroy the renderer and clean up resources
     */
    destroy(): void {
        if (this.app) {
            this.app.destroy(true, { children: true, texture: true });
            this.app = null;
        }

        this.sprites.clear();
        this.initialized = false;
        this.loadedAssets = false;

        console.log("Renderer destroyed");
    }

    /**
     * Check if renderer is initialized
     */
    isInitialized(): boolean {
        return this.initialized;
    }

    /**
     * Resize the renderer
     */
    resize(width: number, height: number): void {
        if (this.app) {
            this.app.renderer.resize(width, height);
        }
    }

    /**
     * Add a chat message
     */
    addMessage(text: string): void {
        this.gameState.messages.push({
            text,
            timestamp: Date.now(),
        });

        // Keep only last 100 messages
        if (this.gameState.messages.length > 100) {
            this.gameState.messages = this.gameState.messages.slice(-100);
        }

        this.updateChatDisplay();
    }

    /**
     * Update a skill
     */
    updateSkill(skillId: number, level: number, xp: number): void {
        if (skillId >= 0 && skillId < this.gameState.skills.length) {
            this.gameState.skills[skillId] = { level, xp };
            this.updateSkillsDisplay();
        }
    }

    /**
     * Set map region
     */
    setMapRegion(x: number, y: number): void {
        this.gameState.mapRegion = { x, y };
        this.updateMinimapRegion();
    }

    /**
     * Set player info
     */
    setPlayerInfo(name: string, rights: number, member: boolean): void {
        this.gameState.playerName = name;
        this.gameState.rights = rights;
        this.gameState.member = member;
        this.updatePlayerDisplay();
    }
}

// Export default instance creator
export function createRenderer(canvas: HTMLCanvasElement): GameRenderer {
    return new GameRenderer(canvas);
}

export default GameRenderer;
