# 🦀 Rustscape

A modern implementation of a 2009-era MMORPG game server and client, being converted from Java to Rust and Kotlin KMP.

## Project Overview

Rustscape aims to recreate the classic 2009 RuneScape experience using modern technologies:

- **Server**: Rust (high-performance, memory-safe game server)
- **Desktop Client**: Rust (cross-platform native client)
- **Web Client**: TypeScript (browser-based client via WebSocket)
- **Mobile Client**: Kotlin KMP (planned - iOS/Android)

## Project Structure

```
rustscape/
├── src/
│   ├── client/
│   │   ├── desktop/       # Rust desktop client
│   │   └── web/           # TypeScript browser client
│   └── server/            # Rust game server (WIP)
├── config/                # Configuration files
│   ├── nginx/             # Nginx configuration
│   ├── mysql/             # Database initialization
│   └── ssl/               # SSL certificates
├── docker/                # Docker build files
│   ├── server/            # Game server Dockerfile
│   ├── client/            # Desktop client Dockerfile
│   └── nginx/             # Web server Dockerfile
├── archive/               # Legacy Java code (reference)
├── run.sh                 # Docker environment manager
└── docker-compose.yml     # Docker service definitions
```

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Node.js 18+ (for web client development)
- Rust 1.70+ (for native development)

### Running with Docker

```bash
# Start all services
./run.sh start

# Start specific service
./run.sh start app

# View logs
./run.sh logs

# Check status
./run.sh status

# Stop services
./run.sh stop

# Full rebuild and deploy
./run.sh deploy
```

### Access Points

| Service | URL | Description |
|---------|-----|-------------|
| Web Client | http://localhost | Browser-based game client |
| noVNC Client | http://localhost:6080 | Desktop client via browser |
| Game Server | localhost:43594-43596 | Direct game connection |
| WebSocket | ws://localhost:43596 | WebSocket for web client |
| Database | localhost:3306 | MySQL database |

### Web Client Development

```bash
cd src/client/web

# Install dependencies
npm install

# Start development server (with hot reload)
npm run dev -- --host

# Build for production
npm run build

# Run WebSocket tests
npm run test:ws
```

Access the dev server at `http://localhost:5173` or from Windows at `http://<wsl-ip>:5173`.

## Docker Services

| Service | Container | Description |
|---------|-----------|-------------|
| `app` | rustscape_app | Game server (Java, transitioning to Rust) |
| `database` | rustscape_db | MySQL 8.0 database |
| `client` | rustscape_client | Desktop client via noVNC |
| `nginx` | rustscape_nginx | Web server for browser client |

## Web Client Features

The TypeScript web client (`src/client/web/`) includes:

- **WebSocket Connection**: Connect to game server from browser
- **JS5 Protocol**: Cache file downloading and decompression
- **Login Protocol**: RSA-encrypted login with ISAAC cipher
- **Cache Decoders**: Item, NPC, Object definitions
- **Test Tools**: Manual WebSocket testing UI

### Test Pages

- `index.html` - Main game client
- `test.html` - WebSocket protocol testing
- `cache-browser.html` - Cache definition browser (planned)

## Architecture

### WebSocket Bridge

```
┌─────────────────┐     WebSocket      ┌─────────────────────────────┐
│  Browser Client │◄──────────────────►│      Rustscape Server       │
│  (TypeScript)   │    ws://host:43596 │                             │
└─────────────────┘                    │  ┌───────────────────────┐  │
                                       │  │   WebSocket Server    │  │
                                       │  └───────────┬───────────┘  │
                                       │              │              │
                                       │  ┌───────────▼───────────┐  │
                                       │  │   Session Handler     │  │
                                       │  └───────────────────────┘  │
                                       └─────────────────────────────┘
```

### Port Configuration

| Port | Service |
|------|---------|
| 43594 | Base game server port |
| 43595 | World 1 game port |
| 43596 | WebSocket server |
| 5555 | Management API |
| 80 | Nginx (web client) |
| 6080 | noVNC interface |
| 3306 | MySQL |

## Development Roadmap

### Phase 1: Infrastructure ✅
- [x] Docker environment setup
- [x] WebSocket bridge architecture
- [x] Web client foundation
- [x] Test tooling

### Phase 2: Protocol Implementation (In Progress)
- [x] ByteBuffer implementation
- [ ] ISAAC cipher
- [ ] RSA encryption
- [ ] JS5 client
- [ ] Login protocol

### Phase 3: Cache System
- [ ] Cache decompression (GZIP, BZIP2, LZMA)
- [ ] Reference table parsing
- [ ] Definition decoders (Items, NPCs, Objects)
- [ ] Model/Animation decoders

### Phase 4: Game Client
- [ ] Canvas renderer
- [ ] Game state management
- [ ] Player updates
- [ ] NPC rendering
- [ ] Map rendering

### Phase 5: Rust Server
- [ ] Session management
- [ ] World simulation
- [ ] Player persistence
- [ ] Combat system

## run.sh Commands

```bash
./run.sh start [service]     # Start services
./run.sh stop [service]      # Stop services
./run.sh restart [service]   # Restart services
./run.sh build [service]     # Build images
./run.sh deploy              # Build and start all
./run.sh down [-v]           # Remove containers (-v for volumes)
./run.sh logs [service]      # View logs
./run.sh status              # Show service status
./run.sh exec <svc> [cmd]    # Execute command in container
./run.sh webclient:build     # Build web client
./run.sh webclient:dev       # Start web client dev server
./run.sh test:ws [host] [port] # Test WebSocket connection
./run.sh clean               # Remove all containers/images/volumes
./run.sh help                # Show help
```

## Configuration

### Environment Variables

The game server accepts these environment variables:

```bash
MYSQL_HOST=database
MYSQL_USER=jordan
MYSQL_PASSWORD=123456
MYSQL_DATABASE=global
JAVA_OPTS=-Xmx6G -Xms1G
```

### Nginx Virtual Hosts

- `localhost` - TypeScript web client
- `rustscape.local` - noVNC client proxy
- `server.rustscape.local` - Server info
- `db.rustscape.local` - Database info

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests
5. Submit a pull request

## License

MIT License - See [LICENSE](LICENSE) for details.

## Acknowledgments

- Original 2009scape project
- RuneScape protocol documentation community
- Rust game development community