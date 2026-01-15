/**
 * Rustscape Web Client - Main Entry Point
 *
 * Implements the RS protocol for WebSocket connections:
 * 1. Handshake (opcode 14 for login, 15 for JS5)
 * 2. Login type selection (16 for normal, 18 for reconnect)
 * 3. Login packet with credentials (RSA encrypted in production)
 * 4. ISAAC cipher setup for game packets
 */

import { ByteBuffer } from "./protocol/ByteBuffer";
import { IsaacPair } from "./protocol/Isaac";
import { AuthService } from "./auth/AuthService";
import { GameRenderer } from "./renderer/GameRenderer";
import {
    gameState,
    MessageType,
    PlayerRights,
    SKILL_NAMES as GAME_SKILL_NAMES,
} from "./game";

// Configuration
const CONFIG = {
    serverHost: window.location.hostname || "localhost",
    serverPort: 43596,
    wsPath: "/ws",
    revision: 530,
    debug: true,
    usePixiRenderer: true, // Toggle between PixiJS and Canvas2D
};

// Services
const authService = new AuthService();
let gameRenderer: GameRenderer | null = null;

// Connection state
let socket: WebSocket | null = null;
let connected = false;
let serverKey: bigint = 0n;
let isaac: IsaacPair | null = null; // ISAAC cipher for packet encryption
let loginState:
    | "disconnected"
    | "connecting"
    | "handshake"
    | "login_type"
    | "logging_in"
    | "logged_in" = "disconnected";

// Pending credentials for login
let pendingUsername = "";
let pendingPassword = "";

// Skill names for display (use from game module)
const SKILL_NAMES = GAME_SKILL_NAMES;

// UI Elements
const authContainer = document.getElementById("auth-container");
const gameCanvas = document.getElementById("game-canvas") as HTMLCanvasElement;
const gameUI = document.getElementById("game-ui");
const loadingOverlay = document.getElementById("loading-overlay");
const loadingProgressBar = document.getElementById("loading-progress-bar");

// Auth form elements
const loginForm = document.getElementById("login-form") as HTMLFormElement;
const registerForm = document.getElementById(
    "register-form",
) as HTMLFormElement;
const loginUsernameInput = document.getElementById(
    "login-username",
) as HTMLInputElement;
const loginPasswordInput = document.getElementById(
    "login-password",
) as HTMLInputElement;
const loginButton = document.getElementById(
    "login-button",
) as HTMLButtonElement;
const registerUsernameInput = document.getElementById(
    "register-username",
) as HTMLInputElement;
const registerEmailInput = document.getElementById(
    "register-email",
) as HTMLInputElement;
const registerPasswordInput = document.getElementById(
    "register-password",
) as HTMLInputElement;
const registerConfirmInput = document.getElementById(
    "register-confirm",
) as HTMLInputElement;
const registerButton = document.getElementById(
    "register-button",
) as HTMLButtonElement;
const loginStatus = document.getElementById("login-status");
const registerStatus = document.getElementById("register-status");
const passwordStrengthBar = document.getElementById("password-strength-bar");
const logoutButton = document.getElementById(
    "logout-button",
) as HTMLButtonElement;

// Tab elements
const authTabs = document.querySelectorAll(".auth-tab");
const loginPanel = document.getElementById("login-panel");
const registerPanel = document.getElementById("register-panel");

/**
 * Log a message to console and optionally to status element
 */
function log(
    message: string,
    type: "info" | "success" | "error" = "info",
): void {
    const prefix: Record<string, string> = {
        info: "[INFO]",
        success: "[SUCCESS]",
        error: "[ERROR]",
    };

    console.log(`${prefix[type]} ${message}`);
}

/**
 * Show status message in the specified element
 */
function showStatus(
    element: HTMLElement | null,
    message: string,
    type: "info" | "success" | "error" = "info",
): void {
    if (!element) return;
    element.textContent = message;
    element.className = `status ${type}`;
}

/**
 * Show loading overlay
 */
function showLoading(
    message: string = "Loading...",
    progress: number = 0,
): void {
    if (loadingOverlay) {
        loadingOverlay.classList.add("active");
        const loadingText = loadingOverlay.querySelector(".loading-text");
        if (loadingText) loadingText.textContent = message;
    }
    if (loadingProgressBar) {
        loadingProgressBar.style.width = `${progress}%`;
    }
}

/**
 * Hide loading overlay
 */
function hideLoading(): void {
    if (loadingOverlay) {
        loadingOverlay.classList.remove("active");
    }
}

/**
 * Get WebSocket URL based on current config
 */
function getWebSocketUrl(): string {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";

    // Check if we're on nginx proxy ports (8088 HTTP or 8443 HTTPS)
    const isNginxProxy =
        window.location.port === "8088" ||
        window.location.port === "8443" ||
        window.location.port === "";

    if (isNginxProxy) {
        return `${protocol}//${window.location.host}${CONFIG.wsPath}`;
    }

    // Direct connection for development
    return `${protocol}//${CONFIG.serverHost}:${CONFIG.serverPort}`;
}

/**
 * Connect to the game server
 */
async function connect(): Promise<void> {
    if (socket && socket.readyState === WebSocket.OPEN) {
        log("Already connected", "info");
        return;
    }

    const url = getWebSocketUrl();
    log(`Connecting to ${url}...`, "info");

    loginState = "connecting";

    return new Promise((resolve, reject) => {
        socket = new WebSocket(url);
        socket.binaryType = "arraybuffer";

        socket.onopen = () => {
            connected = true;
            loginState = "handshake";
            log("Connected to server", "success");
            resolve();
        };

        socket.onmessage = (event) => {
            handleMessage(event.data);
        };

        socket.onclose = (event) => {
            connected = false;
            const reason = event.reason || "Connection closed";
            log(`Disconnected: ${reason}`, "info");

            if (loginState === "logged_in") {
                // Show reconnect UI or handle disconnect
                handleDisconnect();
            }

            loginState = "disconnected";
        };

        socket.onerror = (error) => {
            log(`WebSocket error: ${error}`, "error");
            reject(error);
        };
    });
}

/**
 * Disconnect from the server
 */
function disconnect(): void {
    // Stop keep-alive ping
    stopPingInterval();

    // Reset ISAAC cipher
    isaac = null;

    if (gameRenderer) {
        gameRenderer.stop();
    }

    if (socket) {
        socket.close();
        socket = null;
        connected = false;
        loginState = "disconnected";
        log("Disconnected", "info");
    }
}

/**
 * Logout from both REST API and game server
 */
async function logout(): Promise<void> {
    log("Logging out...", "info");

    // Disconnect from game server first
    disconnect();

    // Then logout from REST API to invalidate the session token
    try {
        await authService.logout();
        log("REST API logout successful", "success");
    } catch (error) {
        log(`REST API logout failed: ${error}`, "error");
    }

    // Show auth screen
    handleDisconnect();

    // Clear the login form
    if (loginPasswordInput) {
        loginPasswordInput.value = "";
    }

    showStatus(loginStatus, "You have been logged out", "info");
}

/**
 * Handle disconnect from server
 */
function handleDisconnect(): void {
    // Stop keep-alive ping
    stopPingInterval();

    // Reset ISAAC cipher
    isaac = null;

    // Reset login state
    loginState = "disconnected";
    pendingUsername = "";
    pendingPassword = "";

    // Re-enable login button
    if (loginButton) {
        loginButton.disabled = false;
        loginButton.classList.remove("loading");
    }

    // Show auth container and background selector, hide game
    if (authContainer) authContainer.style.display = "block";
    const bgSelector = document.getElementById("bg-selector");
    if (bgSelector) bgSelector.style.display = "flex";
    if (gameCanvas) gameCanvas.style.display = "none";
    if (gameUI) gameUI.style.display = "none";

    showStatus(loginStatus, "Disconnected from server", "error");
}

/**
 * Send raw data to the server
 */
function send(data: Uint8Array): void {
    if (!socket || socket.readyState !== WebSocket.OPEN) {
        log("Not connected", "error");
        return;
    }

    socket.send(data);

    if (CONFIG.debug) {
        const hex = Array.from(data)
            .map((b) => b.toString(16).padStart(2, "0"))
            .join(" ");
        console.log(`[SEND] ${hex}`);
    }
}

/**
 * Send a game packet with ISAAC-encoded opcode
 * Use this for all game packets after login is complete
 *
 * @param opcode The raw packet opcode (0-255)
 * @param payload Optional payload data after the opcode
 */
function sendGamePacket(opcode: number, payload?: Uint8Array): void {
    if (!socket || socket.readyState !== WebSocket.OPEN) {
        log("Not connected", "error");
        return;
    }

    // Encode opcode using ISAAC if available
    let encodedOpcode = opcode;
    if (isaac && loginState === "logged_in") {
        encodedOpcode = isaac.encodeOpcode(opcode);
        if (CONFIG.debug) {
            console.log(`Encoding opcode: ${opcode} -> ${encodedOpcode}`);
        }
    }

    // Build the packet
    const packetSize = 1 + (payload ? payload.length : 0);
    const packet = new Uint8Array(packetSize);
    packet[0] = encodedOpcode;

    if (payload) {
        packet.set(payload, 1);
    }

    socket.send(packet);

    if (CONFIG.debug) {
        const hex = Array.from(packet)
            .map((b) => b.toString(16).padStart(2, "0"))
            .join(" ");
        console.log(
            `[SEND GAME] opcode=${opcode} (encoded=${encodedOpcode}), data: ${hex}`,
        );
    }
}

// =============================================================================
// Game Packet Senders (use these after login is complete)
// =============================================================================

/** Incoming packet opcodes (server -> client) */
const ServerOpcode = {
    PLAYER_UPDATE: 81,
    NPC_UPDATE: 65,
    MAP_REGION: 73,
    INTERFACE_OPEN: 118,
    INTERFACE_CLOSE: 130,
    CHAT_MESSAGE: 4,
    SYSTEM_MESSAGE: 253,
    RUN_ENERGY: 110,
    WEIGHT: 174,
    SKILL_UPDATE: 134,
    INVENTORY_UPDATE: 53,
    LOGOUT: 86,
} as const;

/** Outgoing packet opcodes (client -> server) */
const ClientOpcode = {
    KEEP_ALIVE: 0,
    WINDOW_FOCUS: 3,
    CHAT: 4,
    WALK: 14,
    NPC_EXAMINE: 17,
    ITEM_EXAMINE: 21,
    OBJECT_ACTION_1: 39,
    NPC_ACTION_1: 41,
    COMMAND: 52,
    MAP_LOADED: 77,
    MOUSE_CLICK: 86,
    WALK_HERE: 98,
    ITEM_ACTION_1: 150,
    BUTTON_CLICK: 164,
    CLOSE_INTERFACE: 210,
} as const;

/**
 * Send a keep-alive/ping packet to the server
 * Should be called periodically to maintain connection
 */
function sendPing(): void {
    sendGamePacket(ClientOpcode.KEEP_ALIVE);
}

/**
 * Send a walk packet to move the player
 * @param destX Destination X coordinate
 * @param destY Destination Y coordinate
 * @param running Whether to run (true) or walk (false)
 */
function sendWalk(
    destX: number,
    destY: number,
    running: boolean = false,
): void {
    const buffer = new ByteBuffer(8);
    buffer.writeShort(destX);
    buffer.writeShort(destY);
    buffer.writeByte(running ? 1 : 0);
    // Placeholder bytes for path data
    buffer.writeByte(0);
    buffer.writeShort(0);
    sendGamePacket(ClientOpcode.WALK, buffer.toUint8Array());
}

/**
 * Send a chat message
 * @param message The message to send
 */
function sendChatMessage(message: string): void {
    const encoder = new TextEncoder();
    const messageBytes = encoder.encode(message);

    const buffer = new ByteBuffer(messageBytes.length + 2);
    // Variable length packet - size prefix
    buffer.writeByte(messageBytes.length);
    buffer.writeBytes(messageBytes);

    sendGamePacket(ClientOpcode.CHAT, buffer.toUint8Array());
}

/**
 * Send a command (starts with ::)
 * @param command The command string (without the :: prefix)
 */
function sendCommand(command: string): void {
    const encoder = new TextEncoder();
    const commandBytes = encoder.encode(command);

    const buffer = new ByteBuffer(commandBytes.length + 2);
    buffer.writeByte(commandBytes.length);
    buffer.writeBytes(commandBytes);

    sendGamePacket(ClientOpcode.COMMAND, buffer.toUint8Array());
}

/**
 * Send a button click packet
 * @param buttonId The button/widget ID that was clicked
 */
function sendButtonClick(buttonId: number): void {
    const buffer = new ByteBuffer(2);
    buffer.writeShort(buttonId);
    sendGamePacket(ClientOpcode.BUTTON_CLICK, buffer.toUint8Array());
}

/**
 * Send a close interface packet
 */
function sendCloseInterface(): void {
    sendGamePacket(ClientOpcode.CLOSE_INTERFACE);
}

/**
 * Send map region loaded notification
 * Called after the client finishes loading a new map region
 */
function sendMapLoaded(): void {
    sendGamePacket(ClientOpcode.MAP_LOADED);
}

/**
 * Send window focus change
 * @param focused Whether the game window has focus
 */
function sendWindowFocus(focused: boolean): void {
    const buffer = new ByteBuffer(1);
    buffer.writeByte(focused ? 1 : 0);
    sendGamePacket(ClientOpcode.WINDOW_FOCUS, buffer.toUint8Array());
}

// Start ping interval when logged in
let pingInterval: ReturnType<typeof setInterval> | null = null;

function startPingInterval(): void {
    if (pingInterval) {
        clearInterval(pingInterval);
    }
    // Send ping every 30 seconds to keep connection alive
    pingInterval = setInterval(() => {
        if (loginState === "logged_in" && connected) {
            sendPing();
        }
    }, 30000);
}

function stopPingInterval(): void {
    if (pingInterval) {
        clearInterval(pingInterval);
        pingInterval = null;
    }
}

// =============================================================================
// Message Handling
// =============================================================================

/**
 * Handle incoming message from server
 */
function handleMessage(data: ArrayBuffer): void {
    const bytes = new Uint8Array(data);

    if (CONFIG.debug) {
        const hex = Array.from(bytes)
            .map((b) => b.toString(16).padStart(2, "0"))
            .join(" ");
        console.log(
            `[RECV] ${bytes.length} bytes: ${hex} (state: ${loginState})`,
        );
    }

    const buffer = new ByteBuffer(bytes);

    switch (loginState) {
        case "handshake":
            handleHandshakeResponse(buffer);
            break;
        case "login_type":
            console.log(
                `Unexpected data in login_type state: ${bytes.length} bytes`,
            );
            break;
        case "logging_in":
            handleLoginResponse(buffer);
            break;
        case "logged_in":
            handleGamePacket(buffer);
            break;
        default:
            console.log(
                `Received ${buffer.length} bytes in state: ${loginState}`,
            );
    }
}

/**
 * Handle handshake response from server
 */
function handleHandshakeResponse(buffer: ByteBuffer): void {
    if (buffer.length < 1) {
        log("Invalid handshake response", "error");
        return;
    }

    const responseCode = buffer.readUByte();
    console.log(`Handshake response code: ${responseCode}`);

    if (responseCode === 0) {
        // Success - read server key
        if (buffer.remaining >= 8) {
            serverKey = buffer.readLong();
            console.log(`Server key received: ${serverKey}`);
        }

        // Send login type and credentials
        loginState = "login_type";
        sendLoginTypeAndCredentials();
    } else {
        const errorMessages: Record<number, string> = {
            1: "Delay - wait and retry",
            2: "Successful login",
            3: "Invalid credentials",
            4: "Account disabled",
            5: "Account already logged in",
            6: "Game updated - please refresh",
            7: "World full",
            8: "Login server offline",
            9: "Login limit exceeded",
            10: "Bad session ID",
            11: "Login rejected",
            12: "Members-only world",
            13: "Could not complete login",
            14: "Server being updated",
            15: "Login attempts exceeded",
            16: "Members-only area",
            17: "Invalid login server",
            18: "Invalid login server",
            19: "Invalid login server",
            20: "Invalid login server",
            21: "Profile transfer in progress",
        };
        const message =
            errorMessages[responseCode] ||
            `Unknown response code: ${responseCode}`;
        log(`Handshake failed: ${message}`, "error");
        showStatus(loginStatus, message, "error");
        loginButton.disabled = false;
        disconnect();
    }
}

/**
 * Send login type and credentials
 */
function sendLoginTypeAndCredentials(): void {
    log("Sending login credentials...", "info");

    // Build the login packet
    const loginBlock = buildLoginPacket(pendingUsername, pendingPassword);

    // Send login type first (opcode 16 for normal login)
    const typePacket = new ByteBuffer(1);
    typePacket.writeByte(16);
    send(typePacket.toUint8Array());

    // Then send the login packet
    send(loginBlock);

    loginState = "logging_in";
}

/**
 * Build the login packet with credentials
 */
function buildLoginPacket(username: string, password: string): Uint8Array {
    // Generate ISAAC seeds for packet encryption
    const isaacSeeds: [number, number, number, number] = [
        Math.floor(Math.random() * 0xffffffff) >>> 0,
        Math.floor(Math.random() * 0xffffffff) >>> 0,
        Math.floor(Math.random() * 0xffffffff) >>> 0,
        Math.floor(Math.random() * 0xffffffff) >>> 0,
    ];

    // Initialize the ISAAC cipher pair for this session
    // Client uses original seeds for encoding, seeds+50 for decoding
    isaac = new IsaacPair(isaacSeeds);
    console.log("ISAAC cipher initialized with seeds:", isaacSeeds);

    // In dev mode, we build a plaintext login block
    const rsaBlock = new ByteBuffer(128);

    // Magic byte (10 for RSA block)
    rsaBlock.writeByte(10);

    // ISAAC seeds
    const serverKeyHigh = Number((serverKey >> 32n) & 0xffffffffn);
    const serverKeyLow = Number(serverKey & 0xffffffffn);

    rsaBlock.writeInt(isaacSeeds[0]);
    rsaBlock.writeInt(isaacSeeds[1]);
    rsaBlock.writeInt(isaacSeeds[2]);
    rsaBlock.writeInt(isaacSeeds[3]);
    rsaBlock.writeInt(serverKeyHigh);
    rsaBlock.writeInt(serverKeyLow);

    // UID (can be any value for now)
    rsaBlock.writeInt(Math.floor(Math.random() * 0xffffffff));

    // Password (null-terminated string) - username is sent outside RSA block
    rsaBlock.writeString(password);

    const rsaBlockData = rsaBlock.toUint8Array();

    // Build the outer login packet
    const loginPacket = new ByteBuffer(256);

    // Login packet structure (must match server expectation):
    // - Size (2 bytes, big-endian) - total size after this
    // - Revision (4 bytes)
    // - Low memory flag (1 byte)
    // - RSA block size (2 bytes, unsigned short)
    // - RSA block data
    // - Username (null-terminated string)

    const loginData = new ByteBuffer(256);
    loginData.writeInt(CONFIG.revision);
    loginData.writeByte(0); // Low memory flag (0 = normal, 1 = low memory)

    // RSA block size (2 bytes) and data
    loginData.writeShort(rsaBlockData.length);
    loginData.writeBytes(rsaBlockData);

    // Username after RSA block (null-terminated)
    loginData.writeString(username);

    const loginDataArray = loginData.toUint8Array();

    // Write size prefix
    loginPacket.writeShort(loginDataArray.length);
    loginPacket.writeBytes(loginDataArray);

    return loginPacket.toUint8Array();
}

/**
 * Handle login response from server
 */
function handleLoginResponse(buffer: ByteBuffer): void {
    if (buffer.length < 1) {
        log("Invalid login response - empty buffer", "error");
        return;
    }

    console.log(
        `Login response buffer: ${buffer.length} bytes, position: ${buffer.position}`,
    );

    const responseCode = buffer.readUByte();
    console.log(
        `Login response code: ${responseCode} (expected 2 for success)`,
    );

    if (responseCode === 2) {
        // Success! Parse the rest of the login response
        if (buffer.remaining < 5) {
            console.log(
                `Warning: Login response too short, only ${buffer.remaining} bytes remaining`,
            );
            // Still mark as successful since code was 2
            loginState = "logged_in";
            log("Login successful!", "success");
            transitionToGame();
            return;
        }

        const rights = buffer.readUByte();
        const flagged = buffer.readUByte();
        const playerIndex = buffer.readUShort();
        const member = buffer.readUByte();

        console.log(`Login successful! Details:`);
        console.log(`  - Rights: ${rights} (0=normal, 1=mod, 2=admin)`);
        console.log(`  - Flagged: ${flagged}`);
        console.log(`  - Player Index: ${playerIndex}`);
        console.log(`  - Member: ${member}`);

        // Update game state with player info
        const playerRights =
            rights === 2
                ? PlayerRights.ADMINISTRATOR
                : rights === 1
                  ? PlayerRights.MODERATOR
                  : PlayerRights.PLAYER;
        gameState.setPlayerInfo(
            pendingUsername,
            playerIndex,
            playerRights,
            member === 1,
        );

        // Update game renderer state
        if (gameRenderer) {
            gameRenderer.setPlayerInfo(pendingUsername, rights, member === 1);
        }

        loginState = "logged_in";
        log("Login successful!", "success");
        transitionToGame();

        // Handle any remaining data as game init packets
        if (buffer.remaining > 0) {
            console.log(
                `Received ${buffer.remaining} bytes of init data after login response`,
            );
            handleGamePacket(
                ByteBuffer.wrap(buffer.toUint8Array().slice(buffer.position)),
            );
        }
    } else if (responseCode === 0) {
        // Server might be sending another handshake response
        console.log(
            `Received response code 0 in logging_in state - unexpected`,
        );
        if (buffer.remaining >= 8) {
            const sk = buffer.readLong();
            console.log(`Contains server key: ${sk}`);
        }
    } else {
        // Error response
        const errorMessages: Record<number, string> = {
            1: "Delay - wait and retry",
            3: "Invalid credentials",
            4: "Account disabled",
            5: "Account already logged in",
            6: "Game updated - please refresh",
            7: "World full",
            8: "Login server offline",
            9: "Login limit exceeded",
            10: "Bad session ID",
            11: "Login rejected",
            12: "Members-only world",
            13: "Could not complete login",
            14: "Server being updated",
            20: "Invalid login server",
            21: "Profile transfer in progress",
        };
        const message =
            errorMessages[responseCode] ||
            `Unknown error code: ${responseCode}`;
        log(`Login failed: ${message}`, "error");
        showStatus(loginStatus, message, "error");

        // Re-enable login button
        if (loginButton) loginButton.disabled = false;
        disconnect();
    }
}

/**
 * Handle game packet
 */
function handleGamePacket(buffer: ByteBuffer): void {
    buffer.position = 0;
    const packetData = buffer.readBytes(buffer.length);

    if (packetData.length === 0) {
        return;
    }

    // Decode the opcode using ISAAC cipher if available
    let opcode = packetData[0];
    if (isaac) {
        opcode = isaac.decodeOpcode(opcode);
        if (CONFIG.debug) {
            console.log(`Decoded opcode: ${packetData[0]} -> ${opcode}`);
        }
    }

    if (CONFIG.debug && packetData.length <= 64) {
        const hex = Array.from(packetData)
            .map((b) => b.toString(16).padStart(2, "0"))
            .join(" ");
        console.log(
            `Game packet: opcode=${opcode} (0x${opcode.toString(16)}), size=${packetData.length}, data: ${hex}`,
        );
    }

    // Parse known packet types
    switch (opcode) {
        case 0x86: // Skill update (134)
            if (packetData.length >= 7) {
                const skillId = packetData[1];
                const level = packetData[2];
                const xp =
                    (packetData[3] << 24) |
                    (packetData[4] << 16) |
                    (packetData[5] << 8) |
                    packetData[6];

                // Update game state
                gameState.updateSkill(skillId, level, xp);

                // Update renderer
                if (gameRenderer) {
                    gameRenderer.updateSkill(skillId, level, xp);
                }
                console.log(
                    `Skill update: ${SKILL_NAMES[skillId] || skillId} = Level ${level}, XP ${xp}`,
                );
            }
            break;

        case 0xfd: // Game message (253)
            if (packetData.length > 2) {
                const messageBytes = packetData.slice(2);
                const nullIndex = messageBytes.indexOf(0);
                const text = new TextDecoder().decode(
                    messageBytes.slice(
                        0,
                        nullIndex > 0 ? nullIndex : messageBytes.length,
                    ),
                );
                if (text && text.trim().length > 0) {
                    // Update game state
                    gameState.addTextMessage(text, MessageType.GAME);

                    // Update renderer
                    if (gameRenderer) {
                        gameRenderer.addMessage(text);
                    }
                    console.log(`Game message added: "${text}"`);
                }
            }
            break;

        case 0x68: // Player option (104)
            if (packetData.length > 2) {
                const slot = packetData[1];
                const priority = packetData[packetData.length - 1] === 1;
                const optionBytes = packetData.slice(2, packetData.length - 1);
                const nullIndex = optionBytes.indexOf(0);
                const optionText = new TextDecoder().decode(
                    optionBytes.slice(
                        0,
                        nullIndex > 0 ? nullIndex : optionBytes.length,
                    ),
                );
                if (optionText) {
                    // Update game state
                    gameState.setPlayerOption(slot, optionText, priority);
                    console.log(
                        `Player option added: slot=${slot}, text="${optionText}", priority=${priority}`,
                    );
                }
            }
            break;

        case 0x49: // Map rebuild / region (73)
            if (packetData.length >= 5) {
                const regionX = (packetData[1] << 8) | packetData[2];
                const regionY = (packetData[3] << 8) | packetData[4];

                // Update game state
                gameState.setMapRegion(regionX, regionY);

                // Update renderer
                if (gameRenderer) {
                    gameRenderer.setMapRegion(regionX, regionY);
                }
                console.log(`Map region: ${regionX}, ${regionY}`);
            }
            break;

        case 0x6e: // Run energy (110)
            if (packetData.length >= 2) {
                const energy = packetData[1];
                gameState.setRunEnergy(energy);
                // Update renderer with run energy
                if (gameRenderer) {
                    gameRenderer.updateRunEnergy(energy);
                }
                console.log(`Run energy: ${energy}`);
            }
            break;

        case 0xae: // Weight (174)
            if (packetData.length >= 3) {
                const weight = (packetData[1] << 8) | packetData[2];
                gameState.setWeight(weight);
                console.log(`Weight: ${weight}`);
            }
            break;

        case ServerOpcode.LOGOUT: // Logout (86)
            // Logout packet should have no data (size 0)
            // If it has data, it's likely a misinterpreted skill update
            if (packetData.length <= 1) {
                log("Server requested logout", "info");
                gameState.reset();
                logout();
            }
            break;

        default:
            // Unknown packet - just log it
            break;
    }
}

/**
 * Transition from auth screen to game
 */
async function transitionToGame(): Promise<void> {
    showLoading("Loading game...", 50);

    // Start keep-alive ping interval
    startPingInterval();

    // Hide auth container and background selector
    if (authContainer) authContainer.style.display = "none";
    const bgSelector = document.getElementById("bg-selector");
    if (bgSelector) bgSelector.style.display = "none";

    // Show and set up game canvas
    if (gameCanvas) {
        gameCanvas.style.display = "block";
        gameCanvas.width = 765;
        gameCanvas.height = 503;
    }

    // Initialize PixiJS renderer
    if (CONFIG.usePixiRenderer && gameCanvas) {
        try {
            gameRenderer = new GameRenderer(gameCanvas);
            await gameRenderer.init();
            gameRenderer.setPlayerInfo(pendingUsername, 0, false);
            gameRenderer.start();
            showLoading("Loading game...", 100);
        } catch (error) {
            console.error("Failed to initialize PixiJS renderer:", error);
            // Fall back to showing canvas at least
        }
    }

    // Show game UI
    if (gameUI) gameUI.style.display = "block";

    // Re-enable login button for potential reconnect
    if (loginButton) loginButton.disabled = false;

    hideLoading();

    // Set up window focus tracking
    window.addEventListener("focus", () => {
        if (loginState === "logged_in") {
            sendWindowFocus(true);
        }
    });
    window.addEventListener("blur", () => {
        if (loginState === "logged_in") {
            sendWindowFocus(false);
        }
    });

    // Send map loaded notification
    sendMapLoaded();
}

/**
 * Perform login handshake with the server
 */
async function performLoginHandshake(): Promise<boolean> {
    log("Performing login handshake...", "info");

    // Login handshake packet: opcode 14, revision (4 bytes)
    const buffer = new ByteBuffer(5);
    buffer.writeByte(14); // Login opcode
    buffer.writeInt(CONFIG.revision);

    send(buffer.toUint8Array());

    // Response will be handled in handleMessage -> handleHandshakeResponse
    return true;
}

/**
 * Handle login form submission
 *
 * Flow:
 * 1. First validate credentials via REST API for better error messages
 * 2. If valid, connect to game server via WebSocket
 * 3. Perform game protocol login handshake
 */
async function handleLoginSubmit(event: Event): Promise<void> {
    event.preventDefault();

    const username = loginUsernameInput?.value?.trim();
    const password = loginPasswordInput?.value;

    if (!username) {
        showStatus(loginStatus, "Please enter a username", "error");
        return;
    }

    if (!password) {
        showStatus(loginStatus, "Please enter a password", "error");
        return;
    }

    loginButton.disabled = true;
    loginButton.classList.add("loading");
    showStatus(loginStatus, "Authenticating...", "info");

    try {
        // Step 1: Validate credentials via REST API first
        // This gives us better error messages (account locked, banned, etc.)
        log("Validating credentials via REST API...", "info");
        const authResponse = await authService.login({
            username,
            password,
            remember: false,
        });

        if (!authResponse.success) {
            // REST API rejected the credentials - show the specific error
            showStatus(
                loginStatus,
                authResponse.message || "Invalid credentials",
                "error",
            );
            loginButton.disabled = false;
            loginButton.classList.remove("loading");
            return;
        }

        log("REST API authentication successful", "success");

        // Step 2: Store credentials for game protocol login
        pendingUsername = username;
        pendingPassword = password;

        showStatus(loginStatus, "Connecting to game server...", "info");

        // Step 3: Connect to game server via WebSocket
        await connect();

        // Step 4: Perform game protocol login handshake
        await performLoginHandshake();

        // The rest of the login flow is handled asynchronously
        // via handleMessage -> handleHandshakeResponse -> sendLoginTypeAndCredentials
    } catch (error) {
        const errorMessage =
            error instanceof Error ? error.message : String(error);
        showStatus(loginStatus, `Login failed: ${errorMessage}`, "error");
        loginButton.disabled = false;
        loginButton.classList.remove("loading");

        // Disconnect if we connected but failed later
        if (connected) {
            disconnect();
        }
    }
}

/**
 * Handle registration form submission
 */
async function handleRegisterSubmit(event: Event): Promise<void> {
    event.preventDefault();

    const username = registerUsernameInput?.value?.trim();
    const email = registerEmailInput?.value?.trim();
    const password = registerPasswordInput?.value;
    const confirm = registerConfirmInput?.value;
    const termsAccepted = (
        document.getElementById("accept-terms") as HTMLInputElement
    )?.checked;

    // Validation
    if (!username || username.length < 1 || username.length > 12) {
        showStatus(registerStatus, "Username must be 1-12 characters", "error");
        return;
    }

    if (!/^[a-zA-Z0-9_]+$/.test(username)) {
        showStatus(
            registerStatus,
            "Username can only contain letters, numbers, and underscores",
            "error",
        );
        return;
    }

    if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        showStatus(
            registerStatus,
            "Please enter a valid email address",
            "error",
        );
        return;
    }

    if (!password || password.length < 6) {
        showStatus(
            registerStatus,
            "Password must be at least 6 characters",
            "error",
        );
        return;
    }

    if (password !== confirm) {
        showStatus(registerStatus, "Passwords do not match", "error");
        return;
    }

    if (!termsAccepted) {
        showStatus(
            registerStatus,
            "You must accept the Terms of Service",
            "error",
        );
        return;
    }

    registerButton.disabled = true;
    registerButton.classList.add("loading");
    showStatus(registerStatus, "Creating account...", "info");

    try {
        const response = await authService.register({
            username,
            email,
            password,
        });

        if (response.success) {
            showStatus(
                registerStatus,
                "Account created! You can now log in.",
                "success",
            );

            // Switch to login tab and pre-fill username
            switchToTab("login");
            if (loginUsernameInput) loginUsernameInput.value = username;
            if (loginPasswordInput) loginPasswordInput.focus();
        } else {
            showStatus(
                registerStatus,
                response.message || "Registration failed",
                "error",
            );
        }
    } catch (error) {
        showStatus(
            registerStatus,
            "Registration failed. Please try again.",
            "error",
        );
    } finally {
        registerButton.disabled = false;
        registerButton.classList.remove("loading");
    }
}

/**
 * Switch between login and register tabs
 */
function switchToTab(tabName: string): void {
    // Update tab buttons
    authTabs.forEach((tab) => {
        if (tab.getAttribute("data-tab") === tabName) {
            tab.classList.add("active");
        } else {
            tab.classList.remove("active");
        }
    });

    // Update panels
    if (tabName === "login") {
        loginPanel?.classList.add("active");
        registerPanel?.classList.remove("active");
    } else {
        loginPanel?.classList.remove("active");
        registerPanel?.classList.add("active");
    }
}

/**
 * Update password strength indicator
 */
function updatePasswordStrength(): void {
    const password = registerPasswordInput?.value || "";
    const { score, label } = AuthService.getPasswordStrength(password);

    if (passwordStrengthBar) {
        passwordStrengthBar.className = "password-strength-bar";
        if (score === 1) passwordStrengthBar.classList.add("weak");
        else if (score === 2) passwordStrengthBar.classList.add("medium");
        else if (score === 3) passwordStrengthBar.classList.add("strong");
    }
}

/**
 * Initialize the client
 */
function init(): void {
    console.log("Rustscape Web Client initializing...");
    console.log(`Revision: ${CONFIG.revision}`);
    console.log(`WebSocket URL: ${getWebSocketUrl()}`);

    // Set up login form handler
    loginForm?.addEventListener("submit", handleLoginSubmit);

    // Set up registration form handler
    registerForm?.addEventListener("submit", handleRegisterSubmit);

    // Set up tab switching
    authTabs.forEach((tab) => {
        tab.addEventListener("click", () => {
            const tabName = tab.getAttribute("data-tab");
            if (tabName) switchToTab(tabName);
        });
    });

    // Set up password strength indicator
    registerPasswordInput?.addEventListener("input", updatePasswordStrength);

    // Set up password confirmation validation
    registerConfirmInput?.addEventListener("input", () => {
        const password = registerPasswordInput?.value;
        const confirm = registerConfirmInput?.value;
        const group = registerConfirmInput?.closest(".form-group");

        if (confirm && password !== confirm) {
            group?.classList.add("error");
        } else {
            group?.classList.remove("error");
        }
    });

    // Allow Enter key to move between fields
    loginUsernameInput?.addEventListener("keypress", (e) => {
        if (e.key === "Enter") {
            e.preventDefault();
            loginPasswordInput?.focus();
        }
    });

    // Set up logout button handler
    logoutButton?.addEventListener("click", () => {
        logout();
    });

    // Focus username input
    loginUsernameInput?.focus();

    log("Ready to connect", "info");
    console.log("Rustscape Web Client ready");
}

// Initialize when DOM is ready
if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
} else {
    init();
}

// Export for debugging
(window as unknown as { rustscape: object }).rustscape = {
    connect,
    disconnect,
    logout,
    authService,
    gameRenderer: () => gameRenderer,
    config: CONFIG,
};
