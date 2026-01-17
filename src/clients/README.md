# Rustscape KMP Client

A Kotlin Multiplatform (KMP) client for Rustscape, using Compose Multiplatform for shared UI across desktop and web platforms.

## Project Structure

```
src/clients/
├── build.gradle.kts          # Root build configuration
├── settings.gradle.kts       # Project settings
├── gradle.properties         # Gradle/Kotlin configuration
├── gradle/
│   └── libs.versions.toml    # Version catalog for dependencies
│
├── shared/                   # Shared Kotlin code (no UI)
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/       # Cross-platform code
│       │   └── kotlin/com/rustscape/client/
│       │       ├── protocol/ # ByteBuffer, Isaac cipher, Packets
│       │       ├── game/     # GameState, Skills, Position
│       │       ├── network/  # GameClient, WebSocket handling
│       │       ├── auth/     # Authentication services
│       │       └── cache/    # Game cache loading
│       ├── desktopMain/      # JVM-specific implementations
│       ├── wasmJsMain/       # Browser/WASM-specific code
│       └── iosMain/          # iOS-specific code (future)
│
├── composeApp/               # Compose Multiplatform UI
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/       # Shared UI components
│       │   └── kotlin/com/rustscape/client/ui/
│       │       ├── App.kt              # Root composable
│       │       ├── theme/              # RustscapeTheme, colors
│       │       ├── screens/            # LoginScreen, GameScreen
│       │       └── components/         # Reusable RS-style UI components
│       ├── desktopMain/      # Desktop entry point (main.kt)
│       └── wasmJsMain/       # Web/WASM entry point (main.kt)
│
└── deploy/                   # Deployment configs
    ├── nginx.conf            # Nginx configuration
    ├── compress-assets.sh    # Asset compression script
    └── README.md             # Deployment guide
```

## Prerequisites

- **JDK 17+** (for building and running desktop)
- **Gradle 8.x** (included via wrapper)

## Building

### Desktop (Linux/Windows/macOS)

```bash
# Run the desktop application in development mode
./gradlew :composeApp:run

# Build distributable packages
./gradlew :composeApp:packageAppImage      # Universal Linux AppImage
./gradlew :composeApp:packageDeb           # Debian/Ubuntu .deb
./gradlew :composeApp:packageRpm           # Fedora/RHEL .rpm
./gradlew :composeApp:packageMsi           # Windows installer
./gradlew :composeApp:packageDmg           # macOS disk image

# Build all distributions
./gradlew :composeApp:packageAll
```

### Web (Browser/WASM)

```bash
# Development server with hot reload
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Production build
./gradlew :composeApp:wasmJsBrowserDistribution

# Output: composeApp/build/dist/wasmJs/productionExecutable/
```

## Docker Deployment

The KMP WASM client is built and served automatically by the nginx Docker container:

```bash
# From project root
docker compose build nginx
docker compose up -d nginx

# Access at:
# https://localhost:8443/  (HTTPS)
# http://localhost:8088/   (HTTP, localhost only)
```

## Configuration

### Desktop

Configuration can be passed via system properties:

```bash
./gradlew :composeApp:run \
    -Drustscape.server.host=localhost \
    -Drustscape.server.port=43594 \
    -Drustscape.debug=true
```

### Web

Configuration is read from URL parameters:

```
index.html?host=game.example.com&port=443&debug=true
```

## Development

### Running Tests

```bash
# All tests
./gradlew check

# Shared module tests only
./gradlew :shared:allTests

# Desktop tests
./gradlew :shared:desktopTest
```

### Code Structure

#### Protocol Layer (`shared/protocol/`)

- **ByteBuffer.kt** - Binary data reading/writing with RS-specific methods
- **Isaac.kt** - ISAAC cipher for packet opcode encryption
- **Packets.kt** - Server/Client opcodes and packet definitions

#### Game State (`shared/game/`)

- **GameState.kt** - Central state management (skills, position, messages)
- Skills, Position, MapRegion, ChatMessage data classes

#### Network (`shared/network/`)

- **GameClient.kt** - Abstract WebSocket client with login flow
- Platform-specific implementations in `desktopMain` and `wasmJsMain`

#### UI (`composeApp/ui/`)

- **App.kt** - Root composable with screen navigation
- **LoginScreen.kt** - Login/Register forms
- **GameScreen.kt** - Game viewport, chat, skills, minimap
- **RustscapeTheme.kt** - RS-style color palette and typography

#### Components (`composeApp/ui/components/`)

- **RSComponents.kt** - Stone buttons, scroll panels, orbs, tabs
- **RSContextMenu.kt** - RS-style right-click context menus
- **RSChatEffects.kt** - Chat text effects (wave, scroll, flash)
- **RSSounds.kt** - Sound system with WebAudio
- **RSSprites.kt** - Procedural placeholder sprites
- **RSFriendsPanel.kt** - Friends list UI

## Platform Support

| Platform | Status | Build Target |
|----------|--------|--------------|
| Linux Desktop | ✅ Ready | AppImage, .deb, .rpm |
| Windows Desktop | ✅ Ready | .msi, .exe |
| macOS Desktop | ✅ Ready | .dmg, .pkg |
| Web (WASM) | ✅ Ready | Static files |
| Android | 🚧 Planned | .apk, .aab |
| iOS | 🚧 Planned | .ipa |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Compose UI Layer                         │
│  (LoginScreen, GameScreen, RS Components)                   │
├─────────────────────────────────────────────────────────────┤
│                    Application Layer                        │
│  (App.kt, AppState, Navigation)                            │
├─────────────────────────────────────────────────────────────┤
│                    Shared Business Logic                    │
│  (GameState, GameClient, Protocol)                         │
├──────────────────────┬──────────────────────────────────────┤
│   Desktop (JVM)      │        Web (WASM/JS)                 │
│   Ktor CIO Engine    │        Browser WebSocket             │
└──────────────────────┴──────────────────────────────────────┘
```

## Features

### Implemented
- ✅ RS-style UI components (stone buttons, scroll panels, orbs)
- ✅ Right-click context menus with entity detection
- ✅ Chat text effects (wave, scroll, flash, glow, etc.)
- ✅ Sound system with settings panel
- ✅ Friends list panel
- ✅ Skills, Combat, Equipment, Prayer, Magic panels
- ✅ Minimap with HP/Prayer/Run orbs
- ✅ WebSocket game client with ISAAC cipher

### Planned
- 🚧 Real sprite atlas (replace procedural placeholders)
- 🚧 RS pixel font integration
- 🚧 Server packet handling for context menu actions
- 🚧 Bank UI with tabs
- 🚧 Inventory drag-and-drop
- 🚧 Minimap with real map data

## License

MIT License - See LICENSE file in project root.