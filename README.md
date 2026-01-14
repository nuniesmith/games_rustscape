# 🦀 Rustscape

A modern implementation of a 2009-era MMORPG game server and client, written in Rust with WebSocket support for browser-based clients.

## Project Status

**Server**: ✅ Rust implementation compiles and passes all 139 tests
**Web Client**: 🔄 TypeScript client with WebSocket support (in progress)
**Desktop Client**: 📋 Planned

## Project Overview

Rustscape aims to recreate the classic 2009 RuneScape experience using modern technologies:

- **Server**: Rust (high-performance, memory-safe game server)
- **Web Client**: TypeScript (browser-based client via WebSocket)
- **Desktop Client**: Rust (cross-platform native client - planned)
- **Mobile Client**: Kotlin KMP (iOS/Android - planned)

## Project Structure

```
rustscape/
├── src/
│   ├── client/
│   │   ├── desktop/       # Rust desktop client (planned)
│   │   └── web/           # TypeScript browser client
│   └── server/            # Rust game server
│       └── src/
│           ├── auth/      # Authentication service
│           ├── cache/     # Cache file serving
│           ├── crypto/    # ISAAC cipher, RSA encryption
│           ├── game/      # World, player management
│           ├── net/       # Networking, sessions, transport
│           └── protocol/  # Handshake, JS5, Login, Game packets
├── config/                # Configuration files
│   ├── nginx/             # Nginx configuration
│   ├── mysql/             # Database initialization
│   └── ssl/               # SSL certificates
├── docker/                # Docker build files
│   ├── server/            # Java server (legacy)
│   ├── server-rust/       # Rust server (new)
│   ├── nginx/             # Nginx for Java server
│   ├── nginx-rust/        # Nginx for Rust server
│   └── client/            # Desktop client (noVNC)
├── scripts/               # Utility scripts
│   └── run-rust.sh        # Rust server stack manager
├── archive/               # Legacy Java code (reference)
├── run.sh                 # Docker environment manager
└── docker-compose.yml     # Docker service definitions
```

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Node.js 18+ (for web client development)
- Rust 1.70+ (for server development)

### Building the Rust Server

```bash
cd src/server

# Check compilation
cargo check

# Build release binary
cargo build --release

# Run tests
cargo test

# Run the server
cargo run --release
```

### Running with Docker

The project supports two Docker profiles:

- `rust` - Rust game server stack (recommended for new development)
- `java` - Legacy Java game server stack
- `all` - Both servers running simultaneously

#### Rust Server Stack (Recommended)

```bash
# Start Rust server stack
./scripts/run-rust.sh start

# Or using docker compose directly
docker compose --profile rust up -d

# View logs
./scripts/run-rust.sh logs

# Check status
./scripts/run-rust.sh status

# Stop services
./scripts/run-rust.sh stop

# Rebuild after code changes
./scripts/run-rust.sh build
```

#### Legacy Java Server Stack

```bash
# Start Java server stack
./run.sh start

# Or using docker compose directly
docker compose --profile java up -d
```

### Access Points

#### Rust Server Stack

| Service | URL | Description |
|---------|-----|-------------|
| Web Client | http://localhost:8088 | Browser-based game client |
| WebSocket (direct) | ws://localhost:43599 | Direct WebSocket connection |
| WebSocket (proxied) | ws://localhost:8088/ws | Proxied through nginx |
| Game Server (TCP) | localhost:43597 | Base game port |
| Game Server (World 1) | localhost:43598 | World 1 game port |
| Management API | localhost:5556 | Server administration |
| Database | localhost:3307 | MySQL database |

#### Java Server Stack (Legacy)

| Service | URL | Description |
|---------|-----|-------------|
| Web Client | http://localhost | Browser-based game client |
| WebSocket | ws://localhost:43596 | WebSocket for web client |
| Game Server (TCP) | localhost:43595 | World 1 game connection |
| Management API | localhost:5555 | Server administration |
| Desktop Client (noVNC) | http://localhost:6080 | VNC-based desktop client |

## Server Architecture

The Rust server implements the full game protocol:

### Network Stack
- **TCP Transport**: Native client connections
- **WebSocket Transport**: Browser client connections via tokio-tungstenite
- **Unified Transport**: Abstraction layer for protocol handling
- **Session Management**: Connection state, ISAAC cipher pairs

### Protocol Handlers
- **Handshake**: Initial connection negotiation (opcodes 14, 15, 255)
- **JS5**: Cache file serving with priority queuing
- **Login**: RSA-encrypted credential exchange, ISAAC setup
- **Game**: In-game packet handling

### Cryptography
- **ISAAC Cipher**: Stream cipher for packet encryption/decryption
- **RSA**: Public key encryption for login credentials

### Authentication
- **In-Memory Auth**: Development mode (auto-accepts all logins)
- **Argon2 Hashing**: Secure password storage
- **Player Index**: Allocation and release for concurrent players

### Game Systems
- **World**: Game tick loop (600ms), player management
- **Players**: Index allocation, state tracking
- **Cache**: File serving with compression support (GZIP, BZIP2, LZMA)

## Configuration

Server configuration is in `config/server.toml`:

```toml
server_name = "Rustscape"
world_id = 1
game_port = 43594
websocket_port = 43596
management_port = 5555
cache_path = "./data/cache"
tick_rate_ms = 600
max_players = 2000
dev_mode = true

[database]
host = "localhost"
port = 3306
database = "rustscape"
username = "rustscape"
password = ""

[rsa]
modulus = "<hex>"
private_exponent = "<hex>"
public_exponent = 65537
```

### Environment Variables

The server supports extensive environment variable configuration:

```bash
# Server configuration
RUSTSCAPE_CONFIG=./config/server.toml
RUSTSCAPE_SERVER_NAME=Rustscape
RUSTSCAPE_WORLD_ID=1
RUSTSCAPE_GAME_PORT=43594
RUSTSCAPE_WEBSOCKET_PORT=43596
RUSTSCAPE_MANAGEMENT_PORT=5555
RUSTSCAPE_DEV_MODE=true
RUSTSCAPE_DEBUG=true

# Database (RUSTSCAPE_DATABASE_* takes precedence over MYSQL_*)
RUSTSCAPE_DATABASE_HOST=localhost
RUSTSCAPE_DATABASE_PORT=3306
RUSTSCAPE_DATABASE_NAME=rustscape
RUSTSCAPE_DATABASE_USER=rustscape
RUSTSCAPE_DATABASE_PASSWORD=secret

# Or use MYSQL_* variables
MYSQL_HOST=localhost
MYSQL_USER=rustscape
MYSQL_PASSWORD=secret
MYSQL_DATABASE=rustscape

# RSA keys (production only)
RUSTSCAPE_RSA_MODULUS=<hex>
RUSTSCAPE_RSA_PRIVATE_EXPONENT=<hex>
```

## Web Client Development

```bash
cd src/client/web

# Install dependencies
npm install

# Start development server
npm run dev -- --host

# Build for production
npm run build

# Run WebSocket tests
node scripts/test-websocket.js localhost 43596
```

## Docker Configuration

### Docker Compose Profiles

The `docker-compose.yml` uses profiles to manage different server configurations:

```yaml
# Rust server stack
docker compose --profile rust up -d

# Java server stack (legacy)
docker compose --profile java up -d

# Both servers (for migration testing)
docker compose --profile all up -d
```

### Services by Profile

| Service | Profile | Description |
|---------|---------|-------------|
| `rust-server` | rust, all | Rust game server |
| `nginx-rust` | rust, all | Nginx for Rust server (port 8088) |
| `app` | java, all | Java game server (legacy) |
| `nginx` | java, all | Nginx for Java server (port 80) |
| `client` | java, all | Desktop client via noVNC |
| `database` | (always) | MySQL database |

### Building Images

```bash
# Build Rust server only
docker compose --profile rust build rust-server

# Build all Rust profile services
docker compose --profile rust build

# Rebuild from scratch (no cache)
docker compose --profile rust build --no-cache
```

## Port Configuration

### Rust Server Stack

| Port | Service |
|------|---------|
| 43597 | Base game server port (host) → 43594 (container) |
| 43598 | World 1 game port (host) → 43595 (container) |
| 43599 | WebSocket server (host) → 43596 (container) |
| 5556 | Management API (host) → 5555 (container) |
| 8088 | Nginx web client |
| 3307 | MySQL (external port) |

### Java Server Stack

| Port | Service |
|------|---------|
| 43594 | Base game server port |
| 43595 | World 1 game port |
| 43596 | WebSocket server |
| 5555 | Management API |
| 80 | Nginx web client |
| 6080 | noVNC desktop client |
| 3306 | MySQL |

## Development Roadmap

### Phase 1: Infrastructure ✅
- [x] Docker environment setup
- [x] WebSocket bridge architecture
- [x] Web client foundation
- [x] Rust server skeleton

### Phase 2: Protocol Implementation ✅
- [x] PacketBuffer with RS-specific encodings
- [x] ISAAC cipher (with tests)
- [x] RSA encryption/decryption
- [x] Handshake handler
- [x] JS5 handler with queue
- [x] Login handler
- [x] Login initialization packets

### Phase 3: Network Layer ✅
- [x] TCP transport
- [x] WebSocket transport
- [x] Unified transport abstraction
- [x] Session management
- [x] Connection handler

### Phase 4: Authentication ✅
- [x] In-memory auth service
- [x] Argon2 password hashing
- [x] Player index allocation
- [x] Dev mode auto-accept

### Phase 5: Docker Integration ✅
- [x] Rust server Dockerfile
- [x] Docker Compose profiles
- [x] Nginx WebSocket proxy
- [x] Startup scripts

### Phase 6: Cache System (In Progress)
- [x] Cache store (stub implementation)
- [ ] Real Jagex cache parsing
- [ ] GZIP/BZIP2/LZMA decompression
- [ ] Reference table parsing

### Phase 7: Game Systems (Planned)
- [x] World tick loop (stub)
- [x] Player manager (stub)
- [ ] Full player state
- [ ] NPC system
- [ ] Map regions
- [ ] Combat system

### Phase 8: Database Integration (Planned)
- [ ] MySQL account authentication
- [ ] Player persistence
- [ ] World state

## Legacy Reference Code

The `archive/` directory contains the original implementations:

- **java-server/**: Full 2009scape server in Kotlin/Java
  - NioReactor networking
  - EventProducer pattern
  - Complete game logic
- **09launcher/**: Kotlin launcher with auto-update
- **java-launcher/**: Original Java launcher

These serve as reference for protocol details and game logic.

## Scripts

### run-rust.sh Commands

```bash
./scripts/run-rust.sh start     # Start the Rust server stack
./scripts/run-rust.sh stop      # Stop the Rust server stack
./scripts/run-rust.sh restart   # Restart the Rust server stack
./scripts/run-rust.sh logs      # View logs from all services
./scripts/run-rust.sh status    # Show status of all services
./scripts/run-rust.sh build     # Rebuild the Rust server image
./scripts/run-rust.sh clean     # Stop and remove all containers/volumes
```

### run.sh Commands (Legacy)

```bash
./run.sh start [service]     # Start services
./run.sh stop [service]      # Stop services
./run.sh restart [service]   # Restart services
./run.sh build [service]     # Build images
./run.sh deploy              # Build and start all
./run.sh logs [service]      # View logs
./run.sh status              # Show service status
./run.sh exec <svc> [cmd]    # Execute command in container
./run.sh test:ws [host] [port] # Test WebSocket connection
./run.sh clean               # Remove all containers/images/volumes
```

## Testing

The Rust server has comprehensive test coverage:

```bash
cd src/server
cargo test

# Current: 139 tests passing
```

Test coverage includes:
- ISAAC cipher operations
- RSA encryption/decryption
- PacketBuffer read/write operations
- Bit access modes
- Session management
- Protocol handlers
- Authentication service
- Login initialization
- World/player management

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run `cargo test` and `cargo clippy`
5. Submit a pull request

## License

MIT License - See [LICENSE](LICENSE) for details.

## Acknowledgments

- Original 2009scape project for reference implementation
- RuneScape protocol documentation community
- Rust async ecosystem (tokio, tungstenite)