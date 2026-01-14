# Rustscape KMP Client

A Kotlin Multiplatform (KMP) client for Rustscape, using Compose Multiplatform for shared UI across desktop and web platforms.

## Project Structure

```
kmp/
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
└── composeApp/               # Compose Multiplatform UI
    ├── build.gradle.kts
    └── src/
        ├── commonMain/       # Shared UI components
        │   └── kotlin/com/rustscape/client/ui/
        │       ├── App.kt              # Root composable
        │       ├── theme/              # RustscapeTheme, colors
        │       ├── screens/            # LoginScreen, GameScreen
        │       └── components/         # Reusable UI components
        ├── desktopMain/      # Desktop entry point (main.kt)
        └── wasmJsMain/       # Web/WASM entry point (main.kt)
```

## Prerequisites

- **JDK 17+** (for building and running desktop)
- **Gradle 8.x** (included via wrapper)
- **Node.js 18+** (for web builds, optional)

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

# Build Linux tar.gz (custom task)
./gradlew :composeApp:packageLinuxTarGz

# Build all distributions
./gradlew :composeApp:packageAll
```

### Web (Browser/WASM)

```bash
# Development server with hot reload
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Production build
./gradlew :composeApp:wasmJsBrowserDistribution

# Output: build/dist/wasmJs/productionExecutable/
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

## Platform Support

| Platform | Status | Build Target |
|----------|--------|--------------|
| Linux Desktop | ✅ Ready | AppImage, .deb, .rpm, tar.gz |
| Windows Desktop | ✅ Ready | .msi, .exe |
| macOS Desktop | ✅ Ready | .dmg, .pkg |
| Web (WASM) | ✅ Ready | Static files |
| Android | 🚧 Planned | .apk, .aab |
| iOS | 🚧 Planned | .ipa |

## Adding New Platforms

### Android

1. Add Android target to `shared/build.gradle.kts`:
   ```kotlin
   android {
       compileSdk = 34
       defaultConfig {
           minSdk = 24
       }
   }
   ```

2. Create `composeApp/src/androidMain/` with Android-specific entry point

3. Add Android dependencies in version catalog

### iOS

1. iOS targets are already configured in `shared/build.gradle.kts`
2. Create `composeApp/src/iosMain/` with iOS-specific code
3. Use Xcode for final app packaging

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Compose UI Layer                         │
│  (LoginScreen, GameScreen, Components)                      │
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

## Migrating from TypeScript/Rust Clients

The KMP client is designed to replace both the existing TypeScript web client and Rust desktop client with a single shared codebase.

Key equivalents:
- `ByteBuffer.ts` → `shared/protocol/ByteBuffer.kt`
- `Isaac.ts` → `shared/protocol/Isaac.kt`
- `GameRenderer.ts` → `composeApp/ui/screens/GameScreen.kt`
- `index.ts` → `composeApp/ui/App.kt`

## License

MIT License - See LICENSE file in project root.