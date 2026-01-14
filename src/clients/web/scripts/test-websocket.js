/**
 * Rustscape WebSocket Bridge Test Suite
 *
 * Tests WebSocket connectivity and protocol handshakes with the game server.
 *
 * Usage:
 *   node scripts/test-websocket.js [host] [port]
 *
 * Examples:
 *   node scripts/test-websocket.js                    # Uses localhost:43596
 *   node scripts/test-websocket.js localhost 43596   # Explicit host/port
 */

import WebSocket from "ws";

// Configuration
const HOST = process.argv[2] || "localhost";
const PORT = parseInt(process.argv[3] || "43596", 10);
const REVISION = 530;
const TIMEOUT = 5000;

// ANSI colors for output
const colors = {
    reset: "\x1b[0m",
    red: "\x1b[31m",
    green: "\x1b[32m",
    yellow: "\x1b[33m",
    blue: "\x1b[34m",
    cyan: "\x1b[36m",
    gray: "\x1b[90m",
};

// Test results
const results = {
    passed: 0,
    failed: 0,
    tests: [],
};

/**
 * Print colored output
 */
function print(message, color = "reset") {
    console.log(`${colors[color]}${message}${colors.reset}`);
}

function printHeader(title) {
    print(`\n─── ${title} ───`, "cyan");
}

function printInfo(message) {
    print(`ℹ ${message}`, "blue");
}

function printSuccess(message) {
    print(`✓ ${message}`, "green");
}

function printError(message) {
    print(`✗ ${message}`, "red");
}

function toHex(data) {
    return Array.from(new Uint8Array(data))
        .map((b) => b.toString(16).padStart(2, "0"))
        .join(" ");
}

/**
 * Create a WebSocket connection with timeout
 */
function createConnection(url) {
    return new Promise((resolve, reject) => {
        const timeout = setTimeout(() => {
            reject(new Error("Connection timeout"));
        }, TIMEOUT);

        const ws = new WebSocket(url);

        ws.on("open", () => {
            clearTimeout(timeout);
            resolve(ws);
        });

        ws.on("error", (err) => {
            clearTimeout(timeout);
            reject(err);
        });

        ws.on("close", (code, reason) => {
            printInfo(
                `Connection closed: code=${code}, reason=${reason || "none"}`,
            );
        });
    });
}

/**
 * Send data and wait for response
 */
function sendAndReceive(ws, data, timeout = TIMEOUT) {
    return new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
            reject(new Error("Response timeout"));
        }, timeout);

        ws.once("message", (response) => {
            clearTimeout(timer);
            resolve(Buffer.from(response));
        });

        const buffer = Buffer.from(data);
        printInfo(`Sending: ${toHex(buffer)}`);
        ws.send(buffer);
    });
}

/**
 * Test 1: WebSocket Connection
 */
async function testConnection() {
    printHeader("Test 1: WebSocket Connection");
    const url = `ws://${HOST}:${PORT}`;
    printInfo(`Connecting to ${url}...`);

    try {
        const ws = await createConnection(url);
        printSuccess(`Connected to ${url}`);
        ws.close();
        return { success: true };
    } catch (error) {
        printError(`Connection failed: ${error.message}`);
        return { success: false, error: error.message };
    }
}

/**
 * Test 2: JS5 Handshake
 */
async function testJs5Handshake() {
    printHeader("Test 2: JS5 Handshake");
    const url = `ws://${HOST}:${PORT}`;

    try {
        printInfo(`Connecting to ${url}...`);
        const ws = await createConnection(url);
        printSuccess(`Connected to ${url}`);

        printInfo("Testing JS5 handshake...");

        // JS5 handshake: opcode 15, revision (4 bytes big-endian)
        const handshake = Buffer.alloc(5);
        handshake[0] = 15; // JS5 opcode
        handshake.writeInt32BE(REVISION, 1);

        const response = await sendAndReceive(ws, handshake);
        printInfo(`Received: ${toHex(response)}`);

        ws.close();

        if (response.length >= 1 && response[0] === 0) {
            printSuccess(`JS5 handshake successful (response=${response[0]})`);
            return { success: true };
        } else {
            printError(`JS5 handshake failed (response=${response[0]})`);
            return {
                success: false,
                error: `Unexpected response: ${response[0]}`,
            };
        }
    } catch (error) {
        printError(`JS5 handshake failed: ${error.message}`);
        return { success: false, error: error.message };
    }
}

/**
 * Test 3: JS5 File Request
 */
async function testJs5FileRequest() {
    printHeader("Test 3: JS5 File Request");
    const url = `ws://${HOST}:${PORT}`;

    try {
        printInfo(`Connecting to ${url}...`);
        const ws = await createConnection(url);
        printSuccess(`Connected to ${url}`);

        // First, perform JS5 handshake
        const handshake = Buffer.alloc(5);
        handshake[0] = 15;
        handshake.writeInt32BE(REVISION, 1);

        const handshakeResponse = await sendAndReceive(ws, handshake);
        if (handshakeResponse[0] !== 0) {
            ws.close();
            printError("JS5 handshake failed, cannot test file request");
            return { success: false, error: "Handshake failed" };
        }

        printInfo("Testing JS5 file request (checksum table)...");

        // JS5 file request: priority (1), index (1), archive (2)
        // Checksum table: index=255, archive=255
        const request = Buffer.alloc(4);
        request[0] = 1; // Priority (1 = urgent, 0 = normal)
        request[1] = 255; // Index
        request.writeUInt16BE(255, 2); // Archive

        const response = await sendAndReceive(ws, request, 10000);
        printInfo(
            `Received: ${toHex(response.slice(0, Math.min(response.length, 32)))}${response.length > 32 ? "..." : ""}`,
        );

        ws.close();

        if (response.length >= 8) {
            const index = response[0];
            const archive = (response[1] << 8) | response[2];
            const settings = response[3];
            const compression = settings & 0x7f;
            const priority = (settings & 0x80) !== 0;
            const length = response.readUInt32BE(4);

            printInfo(`  Index: ${index}`);
            printInfo(`  Archive: ${archive}`);
            printInfo(`  Compression: ${compression}`);
            printInfo(`  Priority: ${priority}`);
            printInfo(`  Length: ${length}`);
            printInfo(`  Total bytes: ${response.length}`);

            printSuccess("JS5 file request successful");
            return { success: true };
        } else {
            printError(`Unexpected response length: ${response.length}`);
            return {
                success: false,
                error: `Unexpected response length: ${response.length}`,
            };
        }
    } catch (error) {
        printError(`JS5 file request failed: ${error.message}`);
        return { success: false, error: error.message };
    }
}

/**
 * Test 4: JS5 Reference Table Request
 */
async function testJs5ReferenceTable() {
    printHeader("Test 4: JS5 Reference Table Request");
    const url = `ws://${HOST}:${PORT}`;

    try {
        printInfo(`Connecting to ${url}...`);
        const ws = await createConnection(url);
        printSuccess(`Connected to ${url}`);

        // First, perform JS5 handshake
        const handshake = Buffer.alloc(5);
        handshake[0] = 15;
        handshake.writeInt32BE(REVISION, 1);

        const handshakeResponse = await sendAndReceive(ws, handshake);
        if (handshakeResponse[0] !== 0) {
            ws.close();
            printError(
                "JS5 handshake failed, cannot test reference table request",
            );
            return { success: false, error: "Handshake failed" };
        }

        printInfo(
            "Testing JS5 reference table request (index 255, archive 0)...",
        );

        // JS5 file request: priority (1), index (1), archive (2)
        // Reference table for index 0: index=255, archive=0
        const request = Buffer.alloc(4);
        request[0] = 1; // Priority (1 = urgent, 0 = normal)
        request[1] = 255; // Index (255 = reference tables)
        request.writeUInt16BE(0, 2); // Archive (0 = reference table for index 0)

        const response = await sendAndReceive(ws, request, 10000);
        printInfo(
            `Received: ${toHex(response.slice(0, Math.min(response.length, 32)))}${response.length > 32 ? "..." : ""}`,
        );

        ws.close();

        if (response.length >= 8) {
            const index = response[0];
            const archive = (response[1] << 8) | response[2];
            const settings = response[3];
            const compression = settings & 0x7f;
            const priority = (settings & 0x80) !== 0;
            const length = response.readUInt32BE(4);

            printInfo(`  Index: ${index}`);
            printInfo(`  Archive: ${archive}`);
            printInfo(`  Compression: ${compression}`);
            printInfo(`  Priority: ${priority}`);
            printInfo(`  Length: ${length}`);
            printInfo(`  Total bytes: ${response.length}`);

            if (index === 255 && archive === 0) {
                printSuccess("JS5 reference table request successful");
                return { success: true };
            } else {
                printError(`Unexpected index/archive: ${index}/${archive}`);
                return {
                    success: false,
                    error: `Unexpected index/archive: ${index}/${archive}`,
                };
            }
        } else {
            printError(`Unexpected response length: ${response.length}`);
            return {
                success: false,
                error: `Unexpected response length: ${response.length}`,
            };
        }
    } catch (error) {
        printError(`JS5 reference table request failed: ${error.message}`);
        return { success: false, error: error.message };
    }
}

/**
 * Test 5: Login Handshake
 */
async function testLoginHandshake() {
    printHeader("Test 5: Login Handshake");
    const url = `ws://${HOST}:${PORT}`;

    try {
        printInfo(`Connecting to ${url}...`);
        const ws = await createConnection(url);
        printSuccess(`Connected to ${url}`);

        printInfo("Testing login handshake...");

        // Login handshake: opcode 14, revision (4 bytes big-endian)
        const handshake = Buffer.alloc(5);
        handshake[0] = 14; // Login opcode
        handshake.writeInt32BE(REVISION, 1);

        const response = await sendAndReceive(ws, handshake);
        printInfo(`Received: ${toHex(response)}`);

        ws.close();

        if (response.length >= 9 && response[0] === 0) {
            // Extract server key (8 bytes after response code)
            const serverKey = response.readBigUInt64BE(1);
            printInfo(`  Server key: ${serverKey}`);
            printSuccess("Login handshake successful");
            return { success: true };
        } else if (response.length >= 1) {
            const responseCodes = {
                0: "Success",
                1: "Delay - wait 2 seconds",
                2: "Success",
                3: "Invalid credentials",
                4: "Account disabled",
                5: "Already logged in",
                6: "Game updated",
                7: "World full",
                8: "Login server offline",
                9: "Login limit exceeded",
                10: "Bad session ID",
            };
            const msg =
                responseCodes[response[0]] || `Unknown (${response[0]})`;
            printInfo(`  Response: ${msg}`);

            if (response[0] === 0 || response[0] === 2) {
                printSuccess("Login handshake successful");
                return { success: true };
            } else {
                printError(`Login handshake returned: ${msg}`);
                return { success: false, error: msg };
            }
        } else {
            printError("Empty response");
            return { success: false, error: "Empty response" };
        }
    } catch (error) {
        printError(`Login handshake failed: ${error.message}`);
        return { success: false, error: error.message };
    }
}

/**
 * Test 6: Full Login Flow (Dev Mode)
 *
 * In dev mode, the server treats the RSA block as plaintext.
 * This tests the complete login sequence including:
 * - Login handshake (opcode 14)
 * - Login type (16 = normal)
 * - Login packet with simulated RSA block
 */
async function testFullLogin() {
    printHeader("Test 6: Full Login Flow (Dev Mode)");
    const url = `ws://${HOST}:${PORT}`;

    try {
        printInfo(`Connecting to ${url}...`);
        const ws = await createConnection(url);
        printSuccess(`Connected to ${url}`);

        // Step 1: Login handshake (opcode 14)
        printInfo("Step 1: Sending login handshake...");
        const handshake = Buffer.alloc(5);
        handshake[0] = 14; // Login opcode
        handshake.writeInt32BE(REVISION, 1);

        const handshakeResponse = await sendAndReceive(ws, handshake);
        printInfo(`Handshake response: ${toHex(handshakeResponse)}`);

        if (handshakeResponse.length < 9 || handshakeResponse[0] !== 0) {
            ws.close();
            printError("Login handshake failed");
            return { success: false, error: "Handshake failed" };
        }

        const serverKey = handshakeResponse.readBigUInt64BE(1);
        printInfo(`  Server key: ${serverKey}`);
        printSuccess("Login handshake successful");

        // Step 2: Send login type (16 = normal login)
        printInfo("Step 2: Sending login type...");
        const loginType = Buffer.alloc(1);
        loginType[0] = 16; // Normal login

        ws.send(loginType);
        printInfo(`Sent login type: ${toHex(loginType)}`);

        // Small delay to let server process
        await new Promise((resolve) => setTimeout(resolve, 50));

        // Step 3: Build and send login packet
        printInfo("Step 3: Building login packet...");

        // Build the RSA block (dev mode - plaintext)
        // Format: magic(1) + isaac_seeds(16) + uid(4) + password(string)
        const password = "testpass";
        const rsaBlockData = Buffer.alloc(1 + 16 + 4 + password.length + 1);
        let offset = 0;

        // Magic byte
        rsaBlockData[offset++] = 10;

        // ISAAC seeds (4 x 32-bit integers)
        rsaBlockData.writeInt32BE(12345, offset);
        offset += 4;
        rsaBlockData.writeInt32BE(67890, offset);
        offset += 4;
        rsaBlockData.writeInt32BE(11111, offset);
        offset += 4;
        rsaBlockData.writeInt32BE(22222, offset);
        offset += 4;

        // UID
        rsaBlockData.writeInt32BE(1, offset);
        offset += 4;

        // Password (null-terminated string)
        rsaBlockData.write(password, offset);
        offset += password.length;
        rsaBlockData[offset++] = 0; // null terminator

        const rsaBlock = rsaBlockData.slice(0, offset);

        // Build username
        const username = "testplayer";

        // Build the full login packet
        // Format: revision(4) + low_memory(1) + rsa_size(2) + rsa_block + username(string)
        const loginPacketData = Buffer.alloc(
            4 + 1 + 2 + rsaBlock.length + username.length + 1,
        );
        let loginOffset = 0;

        // Revision
        loginPacketData.writeInt32BE(REVISION, loginOffset);
        loginOffset += 4;

        // Low memory flag
        loginPacketData[loginOffset++] = 0;

        // RSA block size
        loginPacketData.writeUInt16BE(rsaBlock.length, loginOffset);
        loginOffset += 2;

        // RSA block
        rsaBlock.copy(loginPacketData, loginOffset);
        loginOffset += rsaBlock.length;

        // Username (null-terminated)
        loginPacketData.write(username, loginOffset);
        loginOffset += username.length;
        loginPacketData[loginOffset++] = 0;

        const loginPacket = loginPacketData.slice(0, loginOffset);

        // Build final packet with size prefix
        const finalPacket = Buffer.alloc(2 + loginPacket.length);
        finalPacket.writeUInt16BE(loginPacket.length, 0);
        loginPacket.copy(finalPacket, 2);

        printInfo(`Login packet size: ${loginPacket.length}`);
        printInfo(
            `Sending: ${toHex(finalPacket.slice(0, Math.min(finalPacket.length, 48)))}${finalPacket.length > 48 ? "..." : ""}`,
        );

        const loginResponse = await sendAndReceive(ws, finalPacket);
        printInfo(`Login response: ${toHex(loginResponse)}`);

        ws.close();

        // Parse response
        if (loginResponse.length >= 1) {
            const responseCode = loginResponse[0];
            const responseCodes = {
                0: "Exchange Keys",
                1: "Delay",
                2: "Success",
                3: "Invalid credentials",
                4: "Account disabled",
                5: "Already logged in",
                6: "Game updated",
                7: "World full",
                8: "Login server offline",
                9: "Login limit exceeded",
                10: "Bad session ID",
            };

            const msg =
                responseCodes[responseCode] || `Unknown (${responseCode})`;
            printInfo(`  Response code: ${responseCode} (${msg})`);

            if (responseCode === 2 && loginResponse.length >= 6) {
                // Success response
                const rights = loginResponse[1];
                const flagged = loginResponse[2];
                const playerIndex = loginResponse.readUInt16BE(3);
                const member = loginResponse[5];

                printInfo(`  Rights: ${rights}`);
                printInfo(`  Flagged: ${flagged}`);
                printInfo(`  Player index: ${playerIndex}`);
                printInfo(`  Member: ${member}`);

                printSuccess("Full login successful!");
                return { success: true };
            } else if (responseCode === 2) {
                printSuccess("Login accepted (short response)");
                return { success: true };
            } else {
                printError(`Login failed: ${msg}`);
                return { success: false, error: msg };
            }
        } else {
            printError("Empty response");
            return { success: false, error: "Empty response" };
        }
    } catch (error) {
        printError(`Full login test failed: ${error.message}`);
        return { success: false, error: error.message };
    }
}

/**
 * Run all tests
 */
async function runTests() {
    console.log("\n╔════════════════════════════════════════════════╗");
    console.log("║   Rustscape WebSocket Bridge Test Suite        ║");
    console.log("╚════════════════════════════════════════════════╝\n");

    printInfo(`Target: ws://${HOST}:${PORT}`);
    printInfo(`Protocol version: ${REVISION}`);

    const tests = [
        { name: "WebSocket Connection", fn: testConnection },
        { name: "JS5 Handshake", fn: testJs5Handshake },
        { name: "JS5 File Request", fn: testJs5FileRequest },
        { name: "JS5 Reference Table", fn: testJs5ReferenceTable },
        { name: "Login Handshake", fn: testLoginHandshake },
        { name: "Full Login Flow", fn: testFullLogin },
    ];

    for (const test of tests) {
        try {
            const result = await test.fn();
            results.tests.push({ name: test.name, ...result });
            if (result.success) {
                results.passed++;
            } else {
                results.failed++;
            }
        } catch (error) {
            results.tests.push({
                name: test.name,
                success: false,
                error: error.message,
            });
            results.failed++;
        }
    }

    // Print summary
    console.log("\n═══════════════════════════════════════════════════");
    console.log("                    Test Summary                    ");
    console.log("═══════════════════════════════════════════════════\n");

    printSuccess(`Passed:  ${results.passed}`);
    if (results.failed > 0) {
        printError(`Failed:  ${results.failed}`);
    } else {
        print(`Failed:  ${results.failed}`, "gray");
    }

    console.log("");
    if (results.failed === 0) {
        printSuccess("All tests passed! ✓");
    } else {
        printError(`${results.failed} test(s) failed!`);
        process.exit(1);
    }
}

// Run tests
runTests().catch((error) => {
    printError(`Test suite failed: ${error.message}`);
    process.exit(1);
});
