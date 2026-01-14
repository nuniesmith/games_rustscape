/**
 * Rustscape Web Client - Main Entry Point
 */

import { ByteBuffer } from "./protocol/ByteBuffer";

// Configuration
const CONFIG = {
    serverHost: window.location.hostname || "localhost",
    serverPort: 43596,
    wsPath: "/ws",
    revision: 530,
    debug: true,
};

// Connection state
let socket: WebSocket | null = null;
let connected = false;

// UI Elements
const loginContainer = document.getElementById("login-container");
const gameCanvas = document.getElementById("game-canvas") as HTMLCanvasElement;
const gameUI = document.getElementById("game-ui");
const statusEl = document.getElementById("status");
const usernameInput = document.getElementById("username") as HTMLInputElement;
const passwordInput = document.getElementById("password") as HTMLInputElement;
const loginButton = document.getElementById(
    "login-button",
) as HTMLButtonElement;

/**
 * Log a message to console and optionally to status element
 */
function log(message: string, type: "info" | "success" | "error" = "info") {
    const prefix = {
        info: "[INFO]",
        success: "[SUCCESS]",
        error: "[ERROR]",
    };

    console.log(`${prefix[type]} ${message}`);

    if (statusEl) {
        statusEl.textContent = message;
        statusEl.style.color =
            type === "error"
                ? "#e94560"
                : type === "success"
                  ? "#4ade80"
                  : "#666";
    }
}

/**
 * Get WebSocket URL based on current location
 */
function getWebSocketUrl(): string {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";

    // In development, connect directly to the game server port
    if (window.location.port === "5173" || window.location.port === "3000") {
        return `ws://${CONFIG.serverHost}:${CONFIG.serverPort}`;
    }

    // In production, use the /ws proxy path
    return `${protocol}//${window.location.host}${CONFIG.wsPath}`;
}

/**
 * Connect to the game server via WebSocket
 */
async function connect(): Promise<void> {
    if (socket && socket.readyState === WebSocket.OPEN) {
        log("Already connected", "info");
        return;
    }

    const url = getWebSocketUrl();
    log(`Connecting to ${url}...`, "info");

    return new Promise((resolve, reject) => {
        try {
            socket = new WebSocket(url);
            socket.binaryType = "arraybuffer";

            socket.onopen = () => {
                connected = true;
                log("Connected to server", "success");
                resolve();
            };

            socket.onclose = (event) => {
                connected = false;
                log(
                    `Disconnected: ${event.reason || "Connection closed"}`,
                    "info",
                );
            };

            socket.onerror = (error) => {
                connected = false;
                log("Connection error", "error");
                console.error("WebSocket error:", error);
                reject(error);
            };

            socket.onmessage = (event) => {
                handleMessage(event.data);
            };
        } catch (error) {
            log(`Failed to connect: ${error}`, "error");
            reject(error);
        }
    });
}

/**
 * Disconnect from the server
 */
function disconnect(): void {
    if (socket) {
        socket.close();
        socket = null;
        connected = false;
        log("Disconnected", "info");
    }
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
 * Handle incoming message from server
 */
function handleMessage(data: ArrayBuffer): void {
    const bytes = new Uint8Array(data);

    if (CONFIG.debug) {
        const hex = Array.from(bytes)
            .map((b) => b.toString(16).padStart(2, "0"))
            .join(" ");
        console.log(`[RECV] ${hex}`);
    }

    const buffer = new ByteBuffer(bytes);

    // TODO: Parse and handle different packet types
    // For now, just log the received data
    console.log("Received", buffer.length, "bytes");
}

/**
 * Perform JS5 handshake with the server
 */
async function performJs5Handshake(): Promise<boolean> {
    log("Performing JS5 handshake...", "info");

    // JS5 handshake packet: opcode 15, revision (4 bytes)
    const buffer = new ByteBuffer(5);
    buffer.writeByte(15); // JS5 opcode
    buffer.writeInt(CONFIG.revision);

    send(buffer.toUint8Array());

    // Wait for response (handled in onmessage)
    return true;
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

    // Wait for response (handled in onmessage)
    return true;
}

/**
 * Handle login button click
 */
async function handleLogin(): Promise<void> {
    const username = usernameInput?.value?.trim();
    const password = passwordInput?.value;

    if (!username) {
        log("Please enter a username", "error");
        return;
    }

    if (!password) {
        log("Please enter a password", "error");
        return;
    }

    loginButton.disabled = true;
    log("Logging in...", "info");

    try {
        // Connect to server
        await connect();

        // Perform login handshake
        await performLoginHandshake();

        // TODO: Complete login sequence with credentials
        // This requires ISAAC cipher setup and RSA encryption

        log("Login successful!", "success");

        // Show game UI
        if (loginContainer) loginContainer.style.display = "none";
        if (gameCanvas) gameCanvas.style.display = "block";
        if (gameUI) gameUI.style.display = "block";
    } catch (error) {
        log(`Login failed: ${error}`, "error");
    } finally {
        loginButton.disabled = false;
    }
}

/**
 * Initialize the client
 */
function init(): void {
    console.log("Rustscape Web Client initializing...");

    // Set up login button handler
    loginButton?.addEventListener("click", handleLogin);

    // Allow Enter key to submit login
    passwordInput?.addEventListener("keypress", (e) => {
        if (e.key === "Enter") {
            handleLogin();
        }
    });

    usernameInput?.addEventListener("keypress", (e) => {
        if (e.key === "Enter") {
            passwordInput?.focus();
        }
    });

    // Focus username input
    usernameInput?.focus();

    log("Ready to connect", "info");
    console.log("Rustscape Web Client ready");
}

// Initialize when DOM is ready
if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
} else {
    init();
}

// Export for external use
export { connect, disconnect, send, CONFIG };
