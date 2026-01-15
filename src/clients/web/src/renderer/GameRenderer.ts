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
const MAP_VIEWPORT_WIDTH = 13; // Tiles visible horizontally
const MAP_VIEWPORT_HEIGHT = 13; // Tiles visible vertically

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
    backgroundDark: 0x0f0f23,
    primary: 0xe94560,
    primaryLight: 0xff6b6b,
    text: 0xffffff,
    textMuted: 0xaaaaaa,
    textYellow: 0xffff00,
    textCyan: 0x00ffff,
    textGold: 0xffd700,
    textSilver: 0xc0c0c0,
    mapGreen: 0x2d5a27,
    chatBackground: 0x2a2a4a,
    panelBackground: 0x3a3a5a,
    // Ground tile colors for different terrain types
    grass: 0x228b22,
    grassLight: 0x32cd32,
    grassDark: 0x006400,
    dirt: 0x8b7355,
    dirtLight: 0xa0826d,
    dirtDark: 0x654321,
    stone: 0x708090,
    stoneLight: 0x778899,
    stoneDark: 0x4a5568,
    water: 0x1e90ff,
    waterLight: 0x4169e1,
    waterDark: 0x0000cd,
    sand: 0xf4a460,
    sandLight: 0xdeb887,
};

/**
 * Animation state for entities
 */
interface AnimationState {
    walking: boolean;
    direction: number; // 0=N, 1=NE, 2=E, 3=SE, 4=S, 5=SW, 6=W, 7=NW
    walkCycle: number; // 0-1 animation progress
    bobOffset: number; // Vertical bob for walking
    targetX: number;
    targetY: number;
    lastMoveTime: number;
}

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
    isMoving: boolean;
    runEnergy: number;
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
    private contextLost = false;
    private contextLostHandler: ((e: Event) => void) | null = null;
    private contextRestoredHandler: ((e: Event) => void) | null = null;

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
    private mapTiles: Graphics | null = null;
    private groundTextures: Map<string, Texture> = new Map();

    // Loaded assets
    private sprites: Map<string, Texture> = new Map();
    private loadedAssets = false;

    // Animation state
    private animationState: AnimationState = {
        walking: false,
        direction: 4, // South by default
        walkCycle: 0,
        bobOffset: 0,
        targetX: 0,
        targetY: 0,
        lastMoveTime: 0,
    };

    // Visual effects
    private walkingIndicator: Graphics | null = null;
    private destinationMarker: Graphics | null = null;
    private runEnergyBar: Graphics | null = null;

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
        isMoving: false,
        runEnergy: 100,
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

        // Set up WebGL context loss handlers before creating the app
        this.setupContextLossHandlers();

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
            powerPreference: "high-performance",
            preserveDrawingBuffer: false,
        });

        // Create render layers
        this.worldContainer = new Container();
        this.entityContainer = new Container();
        this.uiContainer = new Container();
        this.chatContainer = new Container();

        this.app.stage.addChild(this.worldContainer);
        this.app.stage.addChild(this.entityContainer);

        // Set up animation ticker
        this.app.ticker.add(this.animationTick.bind(this));
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
     * Set up WebGL context loss/restore handlers
     */
    private setupContextLossHandlers(): void {
        // Handler for context lost
        this.contextLostHandler = (e: Event) => {
            e.preventDefault();
            this.contextLost = true;
            console.warn(
                "WebGL context lost - renderer paused. Waiting for restore...",
            );

            // Stop the ticker to prevent render attempts
            if (this.app?.ticker) {
                this.app.ticker.stop();
            }
        };

        // Handler for context restored
        this.contextRestoredHandler = async () => {
            console.log("WebGL context restored - reinitializing renderer...");
            this.contextLost = false;

            try {
                // Restart the ticker
                if (this.app?.ticker) {
                    this.app.ticker.start();
                }

                // Recreate textures and sprites since they were lost
                this.createPlaceholderTextures();

                // Force a re-render
                if (this.app?.renderer) {
                    this.app.render();
                }

                console.log("WebGL context successfully restored");
            } catch (error) {
                console.error("Failed to restore WebGL context:", error);
            }
        };

        // Attach handlers to the canvas
        this.canvas.addEventListener(
            "webglcontextlost",
            this.contextLostHandler,
            false,
        );
        this.canvas.addEventListener(
            "webglcontextrestored",
            this.contextRestoredHandler,
            false,
        );
    }

    /**
     * Remove WebGL context loss handlers
     */
    private removeContextLossHandlers(): void {
        if (this.contextLostHandler) {
            this.canvas.removeEventListener(
                "webglcontextlost",
                this.contextLostHandler,
            );
            this.contextLostHandler = null;
        }
        if (this.contextRestoredHandler) {
            this.canvas.removeEventListener(
                "webglcontextrestored",
                this.contextRestoredHandler,
            );
            this.contextRestoredHandler = null;
        }
    }

    /**
     * Check if WebGL context is currently lost
     */
    isContextLost(): boolean {
        return this.contextLost;
    }

    /**
     * Load game assets
     */
    private async loadAssets(): Promise<void> {
        console.log("Loading game assets...");

        try {
            // Try to load sprite manifest from server
            const spritesLoaded = await this.loadSpritesFromServer();

            if (!spritesLoaded) {
                console.log("Server sprites not available, using placeholders");
                this.createPlaceholderTextures();
            }

            this.loadedAssets = true;
            console.log(
                "Assets loaded" +
                    (spritesLoaded ? " from server" : " (using placeholders)"),
            );
        } catch (error) {
            console.warn("Failed to load assets, using placeholders:", error);
            this.createPlaceholderTextures();
            this.loadedAssets = true;
        }
    }

    /**
     * Attempt to load sprites from the server
     */
    private async loadSpritesFromServer(): Promise<boolean> {
        try {
            // Check if sprite manifest exists
            // The extractor outputs: /sprites/sprites_manifest.json (for sprites index)
            const manifestUrl = "/sprites/sprites_manifest.json";
            const response = await fetch(manifestUrl);

            if (!response.ok) {
                console.log("Sprite manifest not found at", manifestUrl);
                return false;
            }

            const manifest = await response.json();
            console.log(`Loading ${manifest.count} sprites from server...`);

            // Load commonly used sprites
            const prioritySprites = [
                // UI elements
                { id: 0, name: "logo" },
                { id: 1, name: "button" },
                { id: 2, name: "icon" },
            ];

            let loadedCount = 0;
            for (const spriteInfo of manifest.sprites || []) {
                try {
                    const texture = await Assets.load(
                        `/sprites/${spriteInfo.path}`,
                    );
                    if (texture) {
                        this.sprites.set(
                            `sprite_${spriteInfo.id}_${spriteInfo.frame}`,
                            texture,
                        );
                        loadedCount++;
                    }
                } catch (e) {
                    // Skip sprites that fail to load
                }

                // Limit initial load to prevent long load times
                if (loadedCount >= 100) {
                    console.log(`Loaded ${loadedCount} priority sprites`);
                    break;
                }
            }

            console.log(`Loaded ${loadedCount} sprites from server`);
            return loadedCount > 0;
        } catch (error) {
            console.log("Could not load sprites from server:", error);
            return false;
        }
    }

    /**
     * Get a sprite texture by ID, with fallback to placeholder
     */
    getSprite(id: number, frame: number = 0): Texture | null {
        const key = `sprite_${id}_${frame}`;
        return this.sprites.get(key) || null;
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

        // Create ground tile textures procedurally
        this.createGroundTextures();
    }

    /**
     * Create procedural ground textures for map rendering
     */
    private createGroundTextures(): void {
        // We'll use Graphics objects for tile rendering
        // In production, these would be loaded from cache textures
        console.log("Ground textures initialized (procedural)");
    }

    /**
     * Get terrain color based on position (procedural generation)
     */
    private getTerrainColor(worldX: number, worldY: number): number {
        // Simple procedural terrain based on position
        // This creates a varied landscape without actual map data
        const noise1 = Math.sin(worldX * 0.1) * Math.cos(worldY * 0.1);
        const noise2 =
            Math.sin(worldX * 0.05 + 100) * Math.cos(worldY * 0.05 + 100);
        const combined = (noise1 + noise2) / 2;

        // Determine terrain type based on noise
        if (combined < -0.3) {
            // Water areas
            return COLORS.water;
        } else if (combined < -0.1) {
            // Sand/beach near water
            return COLORS.sand;
        } else if (combined < 0.2) {
            // Grass (most common)
            const variation = Math.abs(Math.sin(worldX * 0.3 + worldY * 0.2));
            if (variation < 0.3) return COLORS.grassDark;
            if (variation < 0.6) return COLORS.grass;
            return COLORS.grassLight;
        } else if (combined < 0.5) {
            // Dirt paths
            const variation = Math.abs(Math.cos(worldX * 0.4 + worldY * 0.3));
            if (variation < 0.5) return COLORS.dirt;
            return COLORS.dirtLight;
        } else {
            // Stone/rocky areas
            const variation = Math.abs(Math.sin(worldX * 0.5 + worldY * 0.5));
            if (variation < 0.5) return COLORS.stone;
            return COLORS.stoneDark;
        }
    }

    /**
     * Get tile height variation for 3D effect
     */
    private getTileHeight(worldX: number, worldY: number): number {
        const noise = Math.sin(worldX * 0.08) * Math.cos(worldY * 0.08);
        return Math.floor(noise * 3);
    }

    /**
     * Create the game world background
     */
    private createWorld(): void {
        if (!this.worldContainer) return;

        // Create sky gradient background
        const sky = new Graphics();
        const skyGradientSteps = 10;
        const skyHeight = 100;
        const stepHeight = skyHeight / skyGradientSteps;

        for (let i = 0; i < skyGradientSteps; i++) {
            const ratio = i / skyGradientSteps;
            const color = this.lerpColor(0x87ceeb, 0xb0e0e6, ratio);
            sky.rect(0, i * stepHeight, GAME_WIDTH - 180, stepHeight + 1);
            sky.fill(color);
        }
        this.worldContainer.addChild(sky);

        // Create the tile-based map container
        this.mapTiles = new Graphics();
        this.worldContainer.addChild(this.mapTiles);

        // Initial render of map tiles
        this.renderMapTiles();

        // Add subtle vignette effect around edges
        const vignette = new Graphics();
        vignette.rect(0, 0, GAME_WIDTH - 180, GAME_HEIGHT - 100);
        vignette.fill({ color: 0x000000, alpha: 0 });
        // Top edge shadow
        for (let i = 0; i < 20; i++) {
            vignette.rect(0, i, GAME_WIDTH - 180, 1);
            vignette.fill({ color: 0x000000, alpha: (20 - i) * 0.01 });
        }
        this.worldContainer.addChild(vignette);
    }

    /**
     * Render the visible map tiles based on player position
     */
    private renderMapTiles(): void {
        if (!this.mapTiles) return;

        this.mapTiles.clear();

        const centerX = this.gameState.position.x;
        const centerY = this.gameState.position.y;

        // Calculate viewport bounds
        const viewWidth = GAME_WIDTH - 180; // Account for side panel
        const viewHeight = GAME_HEIGHT - 100; // Account for chat panel

        // Calculate tile size to fit viewport (isometric-style)
        const tileWidth = Math.floor(viewWidth / MAP_VIEWPORT_WIDTH);
        const tileHeight = Math.floor(tileWidth * 0.6); // Slight perspective

        // Starting world coordinates
        const startX = centerX - Math.floor(MAP_VIEWPORT_WIDTH / 2);
        const startY = centerY - Math.floor(MAP_VIEWPORT_HEIGHT / 2);

        // Render tiles from back to front for proper layering
        for (let dy = 0; dy < MAP_VIEWPORT_HEIGHT; dy++) {
            for (let dx = 0; dx < MAP_VIEWPORT_WIDTH; dx++) {
                const worldX = startX + dx;
                const worldY = startY + dy;

                // Get terrain type and color
                const baseColor = this.getTerrainColor(worldX, worldY);
                const heightOffset = this.getTileHeight(worldX, worldY);

                // Calculate screen position
                const screenX = dx * tileWidth;
                const screenY = 100 + dy * tileHeight - heightOffset * 2;

                // Draw tile with slight 3D effect
                this.drawTile(
                    screenX,
                    screenY,
                    tileWidth,
                    tileHeight,
                    baseColor,
                    heightOffset,
                );
            }
        }

        // Draw grid overlay (optional, can be toggled)
        this.drawTileGrid(viewWidth, viewHeight, tileWidth, tileHeight);
    }

    /**
     * Draw a single map tile with pseudo-3D shading
     */
    private drawTile(
        x: number,
        y: number,
        width: number,
        height: number,
        color: number,
        heightOffset: number,
    ): void {
        if (!this.mapTiles) return;

        // Calculate shading based on position and height
        const shadeFactor = 0.8 + heightOffset * 0.05;
        const shadedColor = this.shadeColor(color, shadeFactor);

        // Draw main tile surface
        this.mapTiles.rect(x, y, width - 1, height - 1);
        this.mapTiles.fill(shadedColor);

        // Add highlight on top edge
        this.mapTiles.rect(x, y, width - 1, 2);
        this.mapTiles.fill({ color: 0xffffff, alpha: 0.1 });

        // Add shadow on bottom edge
        this.mapTiles.rect(x, y + height - 3, width - 1, 2);
        this.mapTiles.fill({ color: 0x000000, alpha: 0.15 });

        // Add some random vegetation/detail dots on grass tiles
        if (
            color === COLORS.grass ||
            color === COLORS.grassLight ||
            color === COLORS.grassDark
        ) {
            const detailCount = Math.floor(Math.random() * 3);
            for (let i = 0; i < detailCount; i++) {
                const dotX = x + 5 + Math.random() * (width - 10);
                const dotY = y + 5 + Math.random() * (height - 10);
                this.mapTiles.circle(dotX, dotY, 1);
                this.mapTiles.fill({ color: COLORS.grassDark, alpha: 0.5 });
            }
        }
    }

    /**
     * Draw tile grid overlay
     */
    private drawTileGrid(
        viewWidth: number,
        viewHeight: number,
        tileWidth: number,
        tileHeight: number,
    ): void {
        if (!this.mapTiles) return;

        // Very subtle grid lines
        this.mapTiles.stroke({ width: 1, color: 0x000000, alpha: 0.1 });

        // Vertical lines
        for (let x = 0; x <= viewWidth; x += tileWidth) {
            this.mapTiles.moveTo(x, 100);
            this.mapTiles.lineTo(x, 100 + MAP_VIEWPORT_HEIGHT * tileHeight);
        }

        // Horizontal lines
        for (let y = 0; y <= MAP_VIEWPORT_HEIGHT; y++) {
            this.mapTiles.moveTo(0, 100 + y * tileHeight);
            this.mapTiles.lineTo(viewWidth, 100 + y * tileHeight);
        }
    }

    /**
     * Apply shade/brightness to a color
     */
    private shadeColor(color: number, factor: number): number {
        const r = Math.min(255, Math.floor(((color >> 16) & 0xff) * factor));
        const g = Math.min(255, Math.floor(((color >> 8) & 0xff) * factor));
        const b = Math.min(255, Math.floor((color & 0xff) * factor));
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Update map when player position changes
     */
    updateMapPosition(x: number, y: number, z: number): void {
        this.gameState.position = { x, y, z };
        this.renderMapTiles();
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

        // Player body (outer ring)
        const bodyOuter = new Graphics();
        bodyOuter.circle(0, 0, 16);
        bodyOuter.fill({ color: 0x000000, alpha: 0.3 });
        this.playerSprite.addChild(bodyOuter);

        // Player body (main)
        const body = new Graphics();
        body.circle(0, 0, 15);
        body.fill(COLORS.primary);
        body.label = "playerBody";
        this.playerSprite.addChild(body);

        // Player highlight (gives 3D effect)
        const highlight = new Graphics();
        highlight.circle(-4, -4, 5);
        highlight.fill(COLORS.primaryLight);
        highlight.label = "playerHighlight";
        this.playerSprite.addChild(highlight);

        // Direction indicator (small arrow showing facing direction)
        const dirIndicator = new Graphics();
        dirIndicator.moveTo(0, -20);
        dirIndicator.lineTo(-5, -12);
        dirIndicator.lineTo(5, -12);
        dirIndicator.closePath();
        dirIndicator.fill({ color: COLORS.textCyan, alpha: 0.7 });
        dirIndicator.label = "directionIndicator";
        this.playerSprite.addChild(dirIndicator);

        // Walking indicator (pulsing circle under player when moving)
        this.walkingIndicator = new Graphics();
        this.walkingIndicator.label = "walkingIndicator";
        this.walkingIndicator.visible = false;
        this.entityContainer.addChild(this.walkingIndicator);

        // Destination marker (shows where player is walking to)
        this.destinationMarker = new Graphics();
        this.destinationMarker.label = "destinationMarker";
        this.destinationMarker.visible = false;
        this.entityContainer.addChild(this.destinationMarker);

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
        regionText.label = "regionText";
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
            nameText.label = `skill_name_${i}`;
            this.skillsPanel.addChild(nameText);

            // Skill level
            const levelText = new Text({
                text: skill.level.toString(),
                style: skillLevelStyle,
            });
            levelText.anchor.set(1, 0);
            levelText.x = 150;
            levelText.y = y;
            levelText.label = `skill_level_${i}`;
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
            (c) => c.label === "combat_level",
        );
        if (oldCombat) this.skillsPanel.removeChild(oldCombat);

        const combatText = new Text({
            text: `Combat: ${combatLevel}`,
            style: combatStyle,
        });
        combatText.anchor.set(0.5, 0);
        combatText.x = 80;
        combatText.y = 200;
        combatText.label = "combat_level";
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
            placeholder.label = "msg_placeholder";
            chatPanel.addChild(placeholder);
        } else {
            visibleMessages.forEach((msg, i) => {
                const msgText = new Text({
                    text: msg.text,
                    style: msgStyle,
                });
                msgText.x = 10;
                msgText.y = 20 + i * 16;
                msgText.label = `msg_${i}`;
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
            (c) => c.label === "regionText",
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
            (c) => c.label === "crown",
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
            crown.label = "crown";
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
     * Animation tick - called every frame
     */
    private animationTick(ticker: { deltaTime: number }): void {
        const delta = ticker.deltaTime / 60; // Normalize to seconds

        // Update walk cycle animation
        if (this.animationState.walking || this.gameState.isMoving) {
            this.animationState.walkCycle += delta * 8; // Walk speed
            if (this.animationState.walkCycle > 1) {
                this.animationState.walkCycle -= 1;
            }

            // Bob up and down while walking
            this.animationState.bobOffset =
                Math.sin(this.animationState.walkCycle * Math.PI * 2) * 3;

            // Apply bob to player sprite
            if (this.playerSprite) {
                this.playerSprite.y =
                    (GAME_HEIGHT - 100) / 2 + this.animationState.bobOffset;
            }

            // Update walking indicator
            this.updateWalkingIndicator();
        } else {
            // Smoothly return to normal position
            this.animationState.bobOffset *= 0.9;
            if (
                this.playerSprite &&
                Math.abs(this.animationState.bobOffset) > 0.1
            ) {
                this.playerSprite.y =
                    (GAME_HEIGHT - 100) / 2 + this.animationState.bobOffset;
            }
        }

        // Update direction indicator rotation
        this.updateDirectionIndicator();

        // Pulse effect on destination marker
        if (this.destinationMarker && this.destinationMarker.visible) {
            const pulse = 0.8 + Math.sin(Date.now() / 200) * 0.2;
            this.destinationMarker.scale.set(pulse);
        }
    }

    /**
     * Update the walking indicator effect
     */
    private updateWalkingIndicator(): void {
        if (!this.walkingIndicator || !this.playerSprite) return;

        this.walkingIndicator.clear();
        this.walkingIndicator.visible = this.gameState.isMoving;

        if (this.gameState.isMoving) {
            const pulse = 0.5 + Math.sin(Date.now() / 150) * 0.3;
            const radius = 25 + pulse * 10;

            // Expanding rings effect
            this.walkingIndicator.circle(0, 0, radius);
            this.walkingIndicator.stroke({
                width: 2,
                color: COLORS.textCyan,
                alpha: 0.3 * (1 - pulse),
            });

            this.walkingIndicator.circle(0, 0, radius * 0.7);
            this.walkingIndicator.stroke({
                width: 1,
                color: COLORS.textCyan,
                alpha: 0.5 * (1 - pulse),
            });

            this.walkingIndicator.x = this.playerSprite.x;
            this.walkingIndicator.y = this.playerSprite.y + 15;
        }
    }

    /**
     * Update the direction indicator arrow
     */
    private updateDirectionIndicator(): void {
        if (!this.playerSprite) return;

        const dirIndicator = this.playerSprite.children.find(
            (c) => c.label === "directionIndicator",
        );
        if (dirIndicator) {
            // Direction angles: 0=N, 1=NE, 2=E, 3=SE, 4=S, 5=SW, 6=W, 7=NW
            const angles = [0, 45, 90, 135, 180, 225, 270, 315];
            const targetAngle =
                (angles[this.animationState.direction] || 180) *
                (Math.PI / 180);
            dirIndicator.rotation = targetAngle;
        }
    }

    /**
     * Set player movement state
     */
    setMoving(isMoving: boolean, targetX?: number, targetY?: number): void {
        this.gameState.isMoving = isMoving;
        this.animationState.walking = isMoving;

        if (isMoving && targetX !== undefined && targetY !== undefined) {
            this.animationState.targetX = targetX;
            this.animationState.targetY = targetY;
            this.animationState.lastMoveTime = Date.now();

            // Show destination marker
            this.showDestinationMarker(targetX, targetY);

            // Calculate direction based on target
            this.updateFacingDirection(targetX, targetY);
        } else {
            // Hide destination marker when stopped
            if (this.destinationMarker) {
                this.destinationMarker.visible = false;
            }
        }
    }

    /**
     * Show destination marker at target position
     */
    private showDestinationMarker(worldX: number, worldY: number): void {
        if (!this.destinationMarker) return;

        this.destinationMarker.clear();

        // Draw an X marker
        this.destinationMarker.moveTo(-8, -8);
        this.destinationMarker.lineTo(8, 8);
        this.destinationMarker.moveTo(8, -8);
        this.destinationMarker.lineTo(-8, 8);
        this.destinationMarker.stroke({
            width: 3,
            color: COLORS.textYellow,
            alpha: 0.8,
        });

        // Draw circle around X
        this.destinationMarker.circle(0, 0, 12);
        this.destinationMarker.stroke({
            width: 2,
            color: COLORS.textYellow,
            alpha: 0.6,
        });

        // Position relative to player (simplified - center of screen offset)
        const playerX = this.gameState.position.x;
        const playerY = this.gameState.position.y;
        const offsetX = (worldX - playerX) * TILE_SIZE;
        const offsetY = (playerY - worldY) * TILE_SIZE; // Y is inverted

        this.destinationMarker.x = GAME_WIDTH / 2 + offsetX;
        this.destinationMarker.y = (GAME_HEIGHT - 100) / 2 + offsetY;
        this.destinationMarker.visible = true;
    }

    /**
     * Update facing direction based on movement target
     */
    private updateFacingDirection(targetX: number, targetY: number): void {
        const playerX = this.gameState.position.x;
        const playerY = this.gameState.position.y;
        const dx = targetX - playerX;
        const dy = targetY - playerY;

        // Calculate angle and convert to 8-direction
        const angle = Math.atan2(dy, dx) * (180 / Math.PI);

        // Convert angle to direction (0=N, 1=NE, 2=E, etc.)
        // Adjust because our coordinate system has Y inverted
        let direction: number;
        if (angle >= -22.5 && angle < 22.5)
            direction = 2; // E
        else if (angle >= 22.5 && angle < 67.5)
            direction = 1; // NE
        else if (angle >= 67.5 && angle < 112.5)
            direction = 0; // N
        else if (angle >= 112.5 && angle < 157.5)
            direction = 7; // NW
        else if (angle >= 157.5 || angle < -157.5)
            direction = 6; // W
        else if (angle >= -157.5 && angle < -112.5)
            direction = 5; // SW
        else if (angle >= -112.5 && angle < -67.5)
            direction = 4; // S
        else direction = 3; // SE

        this.animationState.direction = direction;
    }

    /**
     * Set player facing direction directly
     */
    setDirection(direction: number): void {
        this.animationState.direction = direction % 8;
    }

    /**
     * Update run energy display
     */
    updateRunEnergy(energy: number): void {
        this.gameState.runEnergy = Math.max(0, Math.min(100, energy));
        this.renderRunEnergyBar();
    }

    /**
     * Render run energy bar near minimap
     */
    private renderRunEnergyBar(): void {
        if (!this.uiContainer) return;

        if (!this.runEnergyBar) {
            this.runEnergyBar = new Graphics();
            this.runEnergyBar.label = "runEnergyBar";
            this.uiContainer.addChild(this.runEnergyBar);
        }

        this.runEnergyBar.clear();

        const barWidth = 100;
        const barHeight = 8;
        const x = GAME_WIDTH - MINIMAP_SIZE - 20;
        const y = MINIMAP_SIZE + 30;

        // Background
        this.runEnergyBar.rect(x, y, barWidth, barHeight);
        this.runEnergyBar.fill({ color: 0x333333, alpha: 0.8 });

        // Energy fill
        const fillWidth = (this.gameState.runEnergy / 100) * barWidth;
        const energyColor =
            this.gameState.runEnergy > 30
                ? 0x00ff00
                : this.gameState.runEnergy > 10
                  ? 0xffff00
                  : 0xff0000;
        this.runEnergyBar.rect(x, y, fillWidth, barHeight);
        this.runEnergyBar.fill({ color: energyColor, alpha: 0.9 });

        // Border
        this.runEnergyBar.rect(x, y, barWidth, barHeight);
        this.runEnergyBar.stroke({ width: 1, color: 0x666666 });

        // Label
        const label = this.uiContainer.children.find(
            (c) => c.label === "runEnergyLabel",
        ) as Text;
        if (!label) {
            const labelText = new Text({
                text: "Run",
                style: new TextStyle({
                    fontFamily: "Arial",
                    fontSize: 10,
                    fill: COLORS.text,
                }),
            });
            labelText.x = x;
            labelText.y = y - 12;
            labelText.label = "runEnergyLabel";
            this.uiContainer.addChild(labelText);
        }
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
        this.renderRunEnergyBar();

        console.log("Renderer started");
    }

    /**
     * Stop the renderer
     * Note: PixiJS 8 removed app.stop() - the ticker is managed differently
     */
    stop(): void {
        if (this.app && this.app.ticker) {
            this.app.ticker.stop();
        }
        console.log("Renderer stopped");
    }

    /**
     * Destroy the renderer and clean up resources
     */
    destroy(): void {
        // Remove WebGL context handlers first
        this.removeContextLossHandlers();

        if (this.app) {
            this.app.destroy(true, { children: true, texture: true });
            this.app = null;
        }

        this.sprites.clear();
        this.initialized = false;
        this.loadedAssets = false;
        this.contextLost = false;

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
        // Re-render map tiles when region changes
        this.renderMapTiles();
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
