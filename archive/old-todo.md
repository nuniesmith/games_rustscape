# WebSocket Bridge & JS5 Integration Project

## Project Overview

This document describes the work done to integrate a browser-capable web client with the 2009Scape Rust game server. The goal was to allow players to connect to the game server directly from a web browser using WebSockets, without requiring a Java client installation.

## Architecture

### High-Level Components

```
┌─────────────────┐     WebSocket      ┌─────────────────────────────────────┐
│  Browser Client │◄──────────────────►│         Rust Game Server            │
│  (TypeScript)   │    ws://host:43596 │                                     │
└─────────────────┘                    │  ┌─────────────────────────────────┐│
                                       │  │      WebSocket Server           ││
                                       │  │  (src/server/src/net/websocket) ││
                                       │  └───────────────┬─────────────────┘│
                                       │                  │                  │
                                       │  ┌───────────────▼─────────────────┐│
                                       │  │    WebSocketSessionManager      ││
                                       │  │  (maps session_id -> data_tx)   ││
                                       │  └───────────────┬─────────────────┘│
                                       │                  │                  │
                                       │  ┌───────────────▼─────────────────┐│
                                       │  │      Session / Transport        ││
                                       │  │  (same logic as TCP clients)    ││
                                       │  └─────────────────────────────────┘│
                                       └─────────────────────────────────────┘
```

### Port Configuration

| Port  | Service                          |
|-------|----------------------------------|
| 43594 | Base game server port            |
| 43595 | Game server (world 1)            |
| 43596 | WebSocket server (TCP port + 1)  |
| 5555  | Management/admin API             |
| 80    | nginx (web client hosting)       |
| 6080  | noVNC web interface              |
| 5900  | VNC port                         |

## Rust Server Components

### WebSocket Server (`src/server/src/net/websocket.rs`)

The WebSocket server accepts browser connections and emits events:

```rust
pub enum WebSocketEvent {
    Connected { session_id: u64, addr: SocketAddr, send_tx: mpsc::Sender<Vec<u8>> },
    Data { session_id: u64, data: Vec<u8> },
    Disconnected { session_id: u64 },
    Error { session_id: u64, error: String },
}
```

### WebSocket Transport (`src/server/src/net/transport/websocket.rs`)

Key method for creating transports with a data channel:

```rust
impl WebSocketTransport {
    /// Creates a new WebSocketTransport with a data channel for receiving data
    /// Returns (transport, data_sender) where data_sender is used to forward
    /// incoming WebSocket data to the transport's receive buffer
    pub fn with_data_channel(
        session_id: u64,
        addr: SocketAddr,
        send_tx: mpsc::Sender<Vec<u8>>,
    ) -> (Self, mpsc::Sender<Vec<u8>>)
}
```

### WebSocketSessionManager (`src/server/src/main.rs`)

Maps session IDs to data senders, routing incoming WebSocket data to the correct session:

```rust
struct WebSocketSessionManager {
    sessions: HashMap<u64, mpsc::Sender<Vec<u8>>>,
}

impl WebSocketSessionManager {
    fn add_session(&mut self, session_id: u64, data_tx: mpsc::Sender<Vec<u8>>);
    fn remove_session(&mut self, session_id: u64);
    fn send_data(&self, session_id: u64, data: Vec<u8>) -> Result<()>;
}
```

### Data Flow

1. Browser connects via WebSocket to port 43596
2. `WebSocketServer` emits `WebSocketEvent::Connected` with a `send_tx` channel
3. `WebSocketTransport::with_data_channel()` creates transport + `data_sender`
4. `WebSocketSessionManager` stores `session_id -> data_sender` mapping
5. When `WebSocketEvent::Data` arrives, manager routes it via the `data_sender`
6. Session reads from transport's receive channel (same as TCP sessions)
7. Session writes to transport, which sends via `send_tx` to WebSocket

## TypeScript Web Client

### Directory Structure

```
src/client/web/
├── package.json
├── tsconfig.json
├── vite.config.ts
├── index.html              # Main game client
├── test.html               # Manual WebSocket testing UI
├── cache-browser.html      # Visual cache definition browser
├── assets/                 # Static assets
├── scripts/
│   └── test-websocket.js   # Node.js e2e test script
└── src/
    ├── main.ts             # Application entry point
    ├── protocol/           # Network protocol
    │   ├── index.ts
    │   ├── ByteBuffer.ts   # Binary buffer utilities
    │   ├── Connection.ts   # WebSocket connection handler
    │   ├── Isaac.ts        # ISAAC cipher implementation
    │   ├── Isaac.test.ts   # ISAAC unit tests
    │   ├── Rsa.ts          # RSA encryption (BigInt)
    │   ├── Js5Client.ts    # JS5 cache protocol client
    │   └── packets/
    │       ├── IncomingPackets.ts
    │       └── OutgoingPackets.ts
    ├── cache/              # Cache parsing
    │   ├── index.ts
    │   ├── CacheManager.ts      # High-level cache API
    │   ├── Archive.ts           # Archive container parsing
    │   ├── ReferenceTable.ts    # Index reference tables
    │   ├── ItemDefinition.ts    # Item definition decoder
    │   ├── NpcDefinition.ts     # NPC definition decoder
    │   ├── ObjectDefinition.ts  # Object definition decoder
    │   ├── ModelDecoder.ts      # 3D model decoder
    │   ├── AnimationDecoder.ts  # Animation decoder
    │   └── SpriteDecoder.ts     # Sprite image decoder
    ├── game/
    │   ├── index.ts
    │   ├── GameClient.ts   # Main game controller
    │   └── Player.ts       # Player entity
    ├── render/
    │   ├── index.ts
    │   └── Renderer.ts     # Canvas 2D renderer
    └── ui/                 # UI components
```

### Dependencies

```json
{
  "dependencies": {
    "pako": "^2.1.0",        // GZIP decompression
    "seek-bzip": "^2.0.0",   // BZIP2 decompression
    "lzma": "^2.3.2"         // LZMA decompression
  },
  "devDependencies": {
    "@types/node": "^20.10.0",
    "@types/pako": "^2.0.3",
    "typescript": "^5.3.0",
    "vite": "^5.0.0",
    "vitest": "^1.0.0",
    "ws": "^8.14.0"
  }
}
```

### Key Classes

#### ByteBuffer

Binary buffer for reading/writing RS protocol data:

```typescript
class ByteBuffer {
    // Reading
    readByte(): number
    readUByte(): number
    readShort(): number
    readUShort(): number
    readInt(): number
    readLong(): bigint
    readString(): string
    readSmart(): number
    readBytes(length: number): Uint8Array
    
    // Writing
    writeByte(value: number): void
    writeShort(value: number): void
    writeInt(value: number): void
    writeLong(value: bigint): void
    writeString(value: string): void
    writeBytes(data: Uint8Array): void
    
    // Bit access
    startBitAccess(): void
    readBits(count: number): number
    writeBits(count: number, value: number): void
    endBitAccess(): void
}
```

#### Js5Client

JS5 cache protocol implementation:

```typescript
class Js5Client {
    constructor(host: string, port: number, revision: number, useWebSocket?: boolean)
    
    connect(): Promise<void>
    disconnect(): void
    
    // Handshake (opcode 15)
    performHandshake(): Promise<number>
    
    // File requests
    requestFile(index: number, archive: number, priority?: boolean): Promise<Js5Response>
    requestChecksumTable(): Promise<Js5Response>
    requestFiles(requests: Js5Request[]): Promise<Js5Response[]>
}

interface Js5Response {
    index: number
    archive: number
    compression: Js5CompressionType
    data: Uint8Array
    version?: number
}

enum Js5CompressionType {
    NONE = 0,
    BZIP2 = 1,
    GZIP = 2,
    LZMA = 3
}
```

#### CacheManager

High-level cache management:

```typescript
class CacheManager {
    items: Map<number, ItemDefinition>
    npcs: Map<number, NpcDefinition>
    objects: Map<number, ObjectDefinition>
    
    connect(host: string, port: number): Promise<void>
    disconnect(): void
    
    loadItemDefinitions(options?: LoadOptions): Promise<void>
    loadNpcDefinitions(options?: LoadOptions): Promise<void>
    loadObjectDefinitions(options?: LoadOptions): Promise<void>
    
    getStats(): CacheStats
}

interface LoadOptions {
    onProgress?: (loaded: number, total: number, message: string) => void
}
```

#### ISAAC Cipher

ISAAC stream cipher for packet encryption:

```typescript
class IsaacCipher {
    constructor(seed: number[])
    nextInt(): number
}

class IsaacPair {
    constructor(seed: number[])
    encodeOpcode(opcode: number): number
    decodeOpcode(opcode: number): number
}
```

#### RSA Encryption

RSA for login block encryption:

```typescript
// Uses BigInt for arbitrary precision arithmetic
function rsaEncrypt(data: Uint8Array, modulus: string, exponent: string): Uint8Array

// Development keys (512-bit, NOT SECURE)
const DEV_MODULUS = "..."
const DEV_EXPONENT = "65537"

// Production should use 1024-bit keys generated via scripts/generate_rsa_keys.sh
```

### Decompression

```typescript
// GZIP (using pako)
function decompressGzip(data: Uint8Array): Uint8Array

// BZIP2 (using seek-bzip, data needs "BZh1" header prepended)
function decompressBzip2(data: Uint8Array): Uint8Array

// LZMA (using lzma package)
function decompressLzma(data: Uint8Array): Uint8Array

// Auto-detect and decompress JS5 response
function decompressJs5Data(response: Js5Response): Uint8Array
```

### Definition Decoders

#### ItemDefinition

```typescript
interface ItemDefinition {
    id: number
    name: string
    value: number
    stackable: boolean
    membersOnly: boolean
    groundOptions: (string | null)[]
    inventoryOptions: (string | null)[]
    inventoryModelId: number
    maleEquipModelId: number
    femaleEquipModelId: number
    maleEquipModelId2: number
    femaleEquipModelId2: number
    zoom2d: number
    xan2d: number
    yan2d: number
    xof2d: number
    yof2d: number
    noteId: number
    noteTemplateId: number
    teamId: number
    // ... additional properties
}
```

#### NpcDefinition

```typescript
interface NpcDefinition {
    id: number
    name: string
    combatLevel: number
    size: number
    options: (string | null)[]
    standAnimation: number
    walkAnimation: number
    modelIds: number[]
    headModelIds: number[]
    recolorFrom: number[]
    recolorTo: number[]
    isMinimapVisible: boolean
    isClickable: boolean
    // ... additional properties
}
```

#### ObjectDefinition

```typescript
interface ObjectDefinition {
    id: number
    name: string
    width: number
    length: number
    solid: boolean
    impenetrable: boolean
    interactive: boolean
    options: (string | null)[]
    modelIds: number[]
    modelTypes: number[]
    animationId: number
    // ... additional properties
}
```

## Test Files

### test.html

Manual WebSocket testing interface:

- Connection panel (host, port, connect/disconnect)
- Handshake testing (JS5 handshake opcode 15, Login handshake opcode 14)
- JS5 file request panel (index, archive, priority)
- Raw data sender (hex input)
- Response log with hex display
- Connection statistics

### cache-browser.html

Visual cache definition browser:

- Connection panel
- Load Items / Load NPCs buttons with progress bars
- Search functionality
- Results list with ID and name
- Detail panel showing all definition properties
- Statistics display (items loaded, NPCs loaded, bytes downloaded)

### scripts/test-websocket.js

Node.js end-to-end test script:

```javascript
// Tests run:
// 1. WebSocket connection test
// 2. JS5 handshake test (opcode 15, revision 530)
// 3. JS5 file request test (checksum table, index 255, archive 255)
// 4. Login handshake test (opcode 14, extracts server key)

// Usage:
// npm run test:ws                    # Uses localhost:43596
// npm run test:ws:verbose host port  # Custom host/port
```

## Docker Configuration

### docker-compose.yml Services

| Service  | Container Name    | Purpose                        |
|----------|-------------------|--------------------------------|
| app      | 2009scape_app     | Rust game server               |
| database | 2009scape_db      | MySQL 8.0 database             |
| client   | 2009scape_client  | Desktop client via noVNC       |
| nginx    | 2009scape_nginx   | Web server for browser client  |

### Dockerfile: nginx (Multi-stage build)

```dockerfile
# Stage 1: Build TypeScript web client
FROM node:20-alpine AS builder
WORKDIR /build
COPY ./src/client/web/package.json ./src/client/web/package-lock.json ./
RUN npm ci
COPY ./src/client/web/ ./
RUN npm run build

# Stage 2: Nginx with built assets
FROM nginx:latest
COPY ./config/nginx/nginx.conf /etc/nginx/nginx.conf
COPY ./config/nginx/conf.d /etc/nginx/conf.d
COPY ./config/ssl /etc/nginx/ssl
COPY --from=builder /build/dist /usr/share/nginx/html/web-client
EXPOSE 80
```

### nginx Configuration

The nginx config serves multiple virtual hosts:

- `localhost` / `_` (default): TypeScript web client
- `2009scape.local`: noVNC client proxy
- `server.2009scape.local`: Game server info
- `db.2009scape.local`: Database info

WebSocket proxy configuration:

```nginx
location /ws {
    proxy_pass http://app:43596;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 3600s;
    proxy_send_timeout 3600s;
}
```

## CI/CD Pipeline

### GitHub Actions Workflow (`.github/workflows/ci.yml`)

```yaml
jobs:
  rust:
    # Formatting check (cargo fmt)
    # Clippy lints (cargo clippy)
    # Build (cargo build)
    # Unit tests (cargo test)
    # Doc tests (cargo test --doc)

  typescript:
    # Install dependencies (npm ci)
    # Type check (npm run typecheck)
    # Unit tests (npm run test:run)

  integration:
    # Build server
    # Start server in background
    # Run WebSocket e2e tests (npm run test:ws)
    # Check for dev keys in production configs

  security:
    # Scan for hardcoded keys
    # Verify use_dev_keys = false in production
```

### Dependabot Configuration (`.github/dependabot.yml`)

Configured for:
- Cargo (Rust dependencies)
- npm (TypeScript dependencies)
- GitHub Actions
- Docker

## Security Considerations

### RSA Keys

- Development uses 512-bit test keys (NOT SECURE)
- Production must use 1024-bit keys
- Generate with: `scripts/generate_rsa_keys.sh ./rsa_keys`
- Private exponent stored in environment variables, not in code
- Public modulus can be embedded in client

### WebSocket Security

- Production should use WSS (WebSocket Secure) over TLS
- Terminate TLS at nginx/load balancer
- Use valid CA-signed certificates

### Rate Limiting

- Connection limits per IP
- Request rate limits
- Request size limits

## Issues Discovered & Fixed

### During Development

1. **npm dependency `bz2` failed** → Replaced with `seek-bzip`
2. **CommonJS/ESM mismatch in test script** → Converted to ES modules
3. **nginx Lua module not available** → Removed `access_by_lua_block`
4. **nginx healthcheck used `wget`** → Changed to `curl`
5. **Missing SSL certificates** → Created self-signed certs in `config/ssl`
6. **WebSocket data not reaching sessions** → Implemented `WebSocketTransport::with_data_channel()` pattern
7. **node_modules owned by root (Docker)** → Fix with `sudo chown -R $USER:$USER node_modules`

## Running the Project

### Development (Vite)

```bash
cd src/client/web
npm install
npm run dev -- --host  # --host flag for WSL access from Windows
```

Access at `http://localhost:5173` or `http://<wsl-ip>:5173`

### Docker (Full Stack)

```bash
./run.sh start
```

Access at `http://localhost` (nginx) or `http://localhost:8080` (client)

### Testing

```bash
# WebSocket e2e tests
npm run test:ws

# TypeScript type check
npm run typecheck

# Unit tests
npm run test:run
```

## Next Steps (Recommended)

### High Priority

1. **Replace development RSA keys** with secure 1024-bit production keys
2. **Deploy WebSockets over TLS (WSS)** for browser clients
3. **Add rate limiting** and connection limits

### Medium Priority

4. Integrate model & animation decoders into renderer
5. Expand cache decoders (maps, interfaces, textures)
6. Harden CI pipeline

### Lower Priority

7. Add observability (metrics, structured logs, traces)
8. Expand test coverage
9. Add headless browser e2e tests

## Test Results (Last Run)

```
╔════════════════════════════════════════════════╗
║   2009scape WebSocket Bridge Test Suite        ║
╚════════════════════════════════════════════════╝

─── Test 1: WebSocket Connection ───
✓ Connected to ws://localhost:43596

─── Test 2: JS5 Handshake ───
✓ JS5 handshake successful (response=0)

─── Test 3: JS5 File Request ───
✓ JS5 file request successful

─── Test 4: Login Handshake ───
✓ Login handshake successful

═══════════════════════════════════════════════════
✓ Passed:  4
Failed:  0
All tests passed! ✓
```
