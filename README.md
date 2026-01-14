# 🦀 Rustscape

A modern implementation of a 2009-era MMORPG game server and client, written in Rust with WebSocket support for browser-based clients.

## Project Status

- **Server**: ✅ Rust implementation compiles and passes all tests
- **Web Client**: ✅ TypeScript client with WebSocket support and PixiJS rendering
- **Desktop Client**: 📋 Planned

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Node.js 20+ (for web client development)
- Rust 1.85+ (for server development)

### Running with Docker

```bash
# Start the server stack
./run.sh start

# Or with dev tools (pgAdmin, Redis Commander)
./run.sh dev

# View logs
./run.sh logs

# Check status
./run.sh status

# Stop services
./run.sh stop
```

### Access Points

| Service | URL | Description |
|---------|-----|-------------|
| Web Client | http://localhost:8088 | Browser-based game client |
| WebSocket (proxied) | ws://localhost:8088/ws | Game connection via nginx |
| WebSocket (direct) | ws://localhost:43599 | Direct WebSocket to server |
| Game Server (TCP) | localhost:43597-43598 | Native client connections |
| Management API | localhost:5556 | Server administration |
| PostgreSQL | localhost:5433 | Database (dev access) |
| Redis | localhost:6380 | Cache (dev access) |

### Dev Tools (with `./run.sh dev`)

| Tool | URL | Credentials |
|------|-----|-------------|
| pgAdmin | http://localhost:5050 | admin@rustscape.local / admin |
| Redis Commander | http://localhost:8081 | - |

## Project Structure

```
rustscape/
├── src/
│   ├── client/web/        # TypeScript browser client
│   └── server/            # Rust game server
├── config/
│   ├── nginx/             # Nginx configuration
│   ├── postgres/          # PostgreSQL initialization
│   ├── redis/             # Redis configuration
│   └── ssl/               # SSL certificates
├── docker/
│   ├── nginx/             # Nginx Dockerfile (builds web client)
│   └── server/            # Rust server Dockerfile
├── scripts/
│   └── run.sh             # Server stack management
├── cache/                 # Game cache files
├── archive/               # Legacy Java code (reference only)
├── run.sh                 # Main entry point
└── docker-compose.yml     # Docker service definitions
```

## Technology Stack

### Server
- **Rust** - High-performance, memory-safe game server
- **Tokio** - Async runtime for networking
- **PostgreSQL** - Primary database for player data
- **Redis** - Session caching and pub/sub

### Web Client
- **TypeScript** - Type-safe client code
- **Vite** - Fast build tooling
- **PixiJS** - 2D rendering engine

### Infrastructure
- **Docker Compose** - Container orchestration
- **Nginx** - Reverse proxy and static file serving

## Server Architecture

### Network Stack
- **TCP Transport**: Native client connections
- **WebSocket Transport**: Browser client connections
- **Session Management**: Connection state, ISAAC cipher pairs

### Protocol Handlers
- **Handshake**: Initial connection negotiation
- **JS5**: Cache file serving with priority queuing
- **Login**: RSA-encrypted credential exchange
- **Game**: In-game packet handling

### Cryptography
- **ISAAC Cipher**: Stream cipher for packet encryption
- **RSA**: Public key encryption for login credentials

## Configuration

### Server Configuration (`config/server.toml`)

```toml
server_name = "Rustscape"
world_id = 1
game_port = 43594
websocket_port = 43596
management_port = 5555
dev_mode = true

[database]
type = "postgres"
host = "localhost"
port = 5432
database = "rustscape"
username = "rustscape"
password = "rustscape_dev_password"

[redis]
host = "localhost"
port = 6379

[auth]
registration_enabled = true
min_password_length = 6
bcrypt_cost = 12
```

### Environment Variables

```bash
# Server
RUST_LOG=info,rustscape_server=debug
RUSTSCAPE_DEV_MODE=true
RUSTSCAPE_WORLD_ID=1

# PostgreSQL
RUSTSCAPE_DATABASE_HOST=postgres
RUSTSCAPE_DATABASE_PORT=5432
RUSTSCAPE_DATABASE_USER=rustscape
RUSTSCAPE_DATABASE_PASSWORD=rustscape_dev_password
RUSTSCAPE_DATABASE_NAME=rustscape

# Redis
RUSTSCAPE_REDIS_HOST=redis
RUSTSCAPE_REDIS_PORT=6379
```

## Development

### Building the Rust Server Locally

```bash
cd src/server
cargo build --release
cargo test
cargo run --release
```

### Web Client Development

```bash
cd src/client/web
npm install
npm run dev -- --host  # Dev server at http://localhost:5173
npm run build          # Production build
```

### Docker Commands

```bash
# Start services
./run.sh start

# Start with dev tools
./run.sh dev

# View logs
./run.sh logs

# Check status
./run.sh status

# Rebuild images
./run.sh build

# Stop services
./run.sh stop

# Clean up (removes volumes)
./run.sh clean
```

## Docker Services

| Service | Description |
|---------|-------------|
| `postgres` | PostgreSQL 16 database |
| `redis` | Redis 7 cache |
| `server` | Rust game server |
| `nginx` | Web client + reverse proxy |
| `pgadmin` | PostgreSQL admin (dev profile) |
| `redis-commander` | Redis admin (dev profile) |

## Database Schema

The PostgreSQL schema includes:
- `users` - User accounts and authentication
- `sessions` - Active sessions
- `players` - Player characters
- `player_skills` - Skill levels and XP
- `player_inventory` - Item storage
- `worlds` - World/server configuration
- `audit_logs` - Security audit trail

Default test users:
- `admin` / `admin123` (admin rights)
- `testuser` / `test123` (regular user)

## Testing

```bash
cd src/server
cargo test  # Run all tests
```

## Roadmap

- [x] Docker infrastructure (PostgreSQL, Redis, Nginx)
- [x] Rust server with WebSocket support
- [x] Protocol implementation (handshake, login, JS5)
- [x] TypeScript web client with PixiJS
- [x] Database schema and migrations
- [x] Server-side auth API endpoints (Axum REST API with JWT, PostgreSQL, Redis)
- [x] ISAAC cipher in web client (packet opcode encryption/decryption)
- [x] Asset pipeline (sprite extraction CLI tool, nginx serving, manifests)
- [x] Full game packet parsing (incoming/outgoing packet handlers)
- [x] Player persistence (PostgreSQL save/load for skills, inventory, position)

## License

MIT License - See [LICENSE](LICENSE) for details.