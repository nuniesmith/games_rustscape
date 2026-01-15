#!/bin/bash
# Rustscape - Run Server Stack
#
# This script starts the game server with its supporting services.
# Usage: ./scripts/run.sh [command]
#
# Commands:
#   start     - Start the server stack (default)
#   stop      - Stop the server stack
#   restart   - Restart the server stack
#   logs      - View logs from all services
#   status    - Show status of all services
#   build     - Rebuild the server images
#   clean     - Stop and remove all containers and volumes
#   dev       - Start with dev tools (pgAdmin, Redis Commander)
#   init      - Initialize/regenerate .env file
#   client    - Build the KMP client (web/desktop)
#   web       - Build and serve the web client only
#   sprites   - Extract sprites from game cache
#   cache     - Cache management commands

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
KMP_DIR="$PROJECT_DIR/src/clients/kmp"

cd "$PROJECT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${CYAN}[STEP]${NC} $1"
}

log_client() {
    echo -e "${MAGENTA}[CLIENT]${NC} $1"
}

print_banner() {
    echo ""
    echo "╔══════════════════════════════════════════════╗"
    echo "║           Rustscape Server Stack             ║"
    echo "╚══════════════════════════════════════════════╝"
    echo ""
}

# Generate a secure random string
generate_secret() {
    local length="${1:-64}"
    # Use /dev/urandom for cryptographically secure random bytes
    if command -v openssl &> /dev/null; then
        openssl rand -base64 "$length" | tr -d '\n/+=' | head -c "$length"
    elif [ -f /dev/urandom ]; then
        head -c "$length" /dev/urandom | base64 | tr -d '\n/+=' | head -c "$length"
    else
        # Fallback to $RANDOM (less secure, but works everywhere)
        local result=""
        local chars="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        for i in $(seq 1 "$length"); do
            result="${result}${chars:RANDOM%${#chars}:1}"
        done
        echo "$result"
    fi
}

# Generate a secure password with special characters
generate_password() {
    local length="${1:-32}"
    if command -v openssl &> /dev/null; then
        openssl rand -base64 "$length" | tr -d '\n' | head -c "$length"
    else
        generate_secret "$length"
    fi
}

# SSL certificate directory (Docker volume mount point)
SSL_DIR="$PROJECT_DIR/config/ssl"
SSL_VOLUME_NAME="rustscape_ssl_certs"

# Generate self-signed SSL certificates
generate_ssl_certs() {
    local force="${1:-false}"

    # Check if certs already exist
    if [ -f "$SSL_DIR/rustscape.crt" ] && [ -f "$SSL_DIR/rustscape.key" ] && [ "$force" != "true" ]; then
        log_info "SSL certificates already exist. Use 'ssl --force' to regenerate."
        return 0
    fi

    if ! command -v openssl &> /dev/null; then
        log_error "OpenSSL is not installed. Cannot generate SSL certificates."
        log_info "Install OpenSSL and try again, or provide your own certificates."
        return 1
    fi

    log_step "Generating self-signed SSL certificates..."

    # Create SSL directory if it doesn't exist
    mkdir -p "$SSL_DIR"

    # Generate private key and self-signed certificate
    # Valid for 365 days, with SAN for localhost and common local IPs
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
        -keyout "$SSL_DIR/rustscape.key" \
        -out "$SSL_DIR/rustscape.crt" \
        -subj "/C=US/ST=State/L=City/O=Rustscape/OU=Development/CN=localhost" \
        -addext "subjectAltName=DNS:localhost,DNS:*.localhost,DNS:rustscape.local,DNS:*.rustscape.local,IP:127.0.0.1,IP:192.168.1.1,IP:10.0.0.1" \
        2>/dev/null

    if [ $? -ne 0 ]; then
        # Fallback for older OpenSSL without -addext
        log_warn "Using fallback certificate generation (older OpenSSL)..."

        # Create a config file for SAN
        cat > "$SSL_DIR/openssl.cnf" << EOF
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
x509_extensions = v3_req

[dn]
C = US
ST = State
L = City
O = Rustscape
OU = Development
CN = localhost

[v3_req]
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
DNS.2 = *.localhost
DNS.3 = rustscape.local
DNS.4 = *.rustscape.local
IP.1 = 127.0.0.1
EOF

        openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
            -keyout "$SSL_DIR/rustscape.key" \
            -out "$SSL_DIR/rustscape.crt" \
            -config "$SSL_DIR/openssl.cnf" \
            2>/dev/null

        rm -f "$SSL_DIR/openssl.cnf"
    fi

    # Generate DH parameters for better security (optional but recommended)
    log_step "Generating DH parameters (this may take a moment)..."
    openssl dhparam -out "$SSL_DIR/dhparam.pem" 2048 2>/dev/null

    # Set proper permissions
    chmod 644 "$SSL_DIR/rustscape.crt"
    chmod 600 "$SSL_DIR/rustscape.key"
    chmod 644 "$SSL_DIR/dhparam.pem" 2>/dev/null || true

    log_success "SSL certificates generated successfully!"
    echo ""
    log_info "Certificate files:"
    echo "  - Certificate: $SSL_DIR/rustscape.crt"
    echo "  - Private Key: $SSL_DIR/rustscape.key"
    echo "  - DH Params:   $SSL_DIR/dhparam.pem"
    echo ""
    log_warn "These are self-signed certificates for development use."
    log_warn "Your browser will show a security warning - this is expected."
    log_info "For production, use certificates from Let's Encrypt or another CA."
    echo ""

    return 0
}

# Check if SSL certificates exist
check_ssl_certs() {
    if [ -f "$SSL_DIR/rustscape.crt" ] && [ -f "$SSL_DIR/rustscape.key" ]; then
        return 0
    fi
    return 1
}

# Ensure SSL certificates exist (generate if missing)
ensure_ssl_certs() {
    if ! check_ssl_certs; then
        log_warn "SSL certificates not found. Generating self-signed certificates..."
        generate_ssl_certs
    fi
}

# Check if .env file exists and is valid
check_env_file() {
    if [ ! -f "$PROJECT_DIR/.env" ]; then
        return 1
    fi

    # Check if required variables are set
    source "$PROJECT_DIR/.env" 2>/dev/null || return 1

    if [ -z "$POSTGRES_PASSWORD" ] || [ -z "$RUSTSCAPE_JWT_SECRET" ]; then
        return 1
    fi

    return 0
}

# Generate .env file with secure credentials
generate_env_file() {
    local force="${1:-false}"

    if [ -f "$PROJECT_DIR/.env" ] && [ "$force" != "true" ]; then
        log_info ".env file already exists. Use 'init --force' to regenerate."
        return 0
    fi

    log_step "Generating secure credentials..."

    # Generate secure random values
    local postgres_password=$(generate_password 32)
    local redis_password=$(generate_password 32)
    local jwt_secret=$(generate_secret 64)
    local pgadmin_password=$(generate_password 16)

    log_step "Creating .env file..."

    cat > "$PROJECT_DIR/.env" << EOF
# ============================================
# Rustscape Server Configuration
# ============================================
# This file was auto-generated by run.sh
# Generated at: $(date -u +"%Y-%m-%d %H:%M:%S UTC")
#
# WARNING: This file contains sensitive credentials.
# Do NOT commit this file to version control!
# ============================================

# --------------------------------------------
# PostgreSQL Database
# --------------------------------------------
POSTGRES_USER=rustscape
POSTGRES_PASSWORD=${postgres_password}
POSTGRES_DB=rustscape
POSTGRES_PORT=5432
POSTGRES_EXTERNAL_PORT=5433

# --------------------------------------------
# Redis Cache
# --------------------------------------------
REDIS_PASSWORD=${redis_password}
REDIS_PORT=6379
REDIS_EXTERNAL_PORT=6380

# --------------------------------------------
# Rustscape Server
# --------------------------------------------
RUSTSCAPE_DEV_MODE=true
RUSTSCAPE_DEBUG=false

# Database connection (uses values above)
RUSTSCAPE_DATABASE_TYPE=postgres
RUSTSCAPE_DATABASE_HOST=postgres
RUSTSCAPE_DATABASE_PORT=5432
RUSTSCAPE_DATABASE_USER=rustscape
RUSTSCAPE_DATABASE_PASSWORD=${postgres_password}
RUSTSCAPE_DATABASE_NAME=rustscape

# Redis connection
RUSTSCAPE_REDIS_HOST=redis
RUSTSCAPE_REDIS_PORT=6379
RUSTSCAPE_REDIS_PASSWORD=${redis_password}

# JWT Authentication (CRITICAL - keep this secret!)
RUSTSCAPE_JWT_SECRET=${jwt_secret}
RUSTSCAPE_JWT_ACCESS_EXPIRY=86400
RUSTSCAPE_JWT_REFRESH_EXPIRY=604800

# Authentication settings
RUSTSCAPE_AUTH_REGISTRATION_ENABLED=true
RUSTSCAPE_AUTH_MIN_PASSWORD_LENGTH=6
RUSTSCAPE_AUTH_MAX_LOGIN_ATTEMPTS=5
RUSTSCAPE_AUTH_LOCKOUT_DURATION=900
RUSTSCAPE_AUTH_BCRYPT_COST=12

# Server settings
RUSTSCAPE_WORLD_ID=1
RUSTSCAPE_WORLD_NAME=Rustscape
RUSTSCAPE_MAX_PLAYERS=2000

# Logging
RUST_LOG=info,rustscape_server=debug

# --------------------------------------------
# Nginx Web Server
# --------------------------------------------
NGINX_HTTP_PORT=8088
NGINX_HTTPS_PORT=8443

# --------------------------------------------
# Development Tools (optional)
# --------------------------------------------
# pgAdmin
PGADMIN_EMAIL=admin@rustscape.local
PGADMIN_PASSWORD=${pgadmin_password}
PGADMIN_PORT=5050

# Redis Commander
REDIS_COMMANDER_PORT=8081

# --------------------------------------------
# KMP Client Build
# --------------------------------------------
KMP_BUILD_WEB=true
KMP_BUILD_DESKTOP=false

# --------------------------------------------
# Timezone
# --------------------------------------------
TZ=America/New_York
EOF

    # Secure the file permissions (read/write for owner only)
    chmod 600 "$PROJECT_DIR/.env"

    log_success ".env file created successfully!"
    echo ""
    log_info "Generated credentials:"
    echo "  PostgreSQL Password: ${postgres_password:0:8}... (hidden)"
    echo "  Redis Password:      ${redis_password:0:8}... (hidden)"
    echo "  JWT Secret:          ${jwt_secret:0:8}... (hidden)"
    echo "  pgAdmin Password:    ${pgadmin_password}"
    echo ""
    log_warn "Save these credentials securely! They are stored in .env"
    log_warn "The .env file has been set to mode 600 (owner read/write only)"
}

# Ensure .env exists before starting services
ensure_env() {
    if ! check_env_file; then
        log_warn ".env file missing or incomplete!"
        generate_env_file
    fi

    # Source the .env file
    set -a
    source "$PROJECT_DIR/.env"
    set +a
}

# Check if Java/Gradle is available for KMP builds
check_java() {
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed. Required for KMP client builds."
        log_info "Install Java 17+ to build the KMP client."
        return 1
    fi

    local java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$java_version" -lt 17 ] 2>/dev/null; then
        log_warn "Java version $java_version detected. Java 17+ recommended."
    fi

    return 0
}

# Check if Gradle wrapper exists, download if needed
ensure_gradle_wrapper() {
    if [ ! -f "$KMP_DIR/gradlew" ]; then
        log_warn "Gradle wrapper not found in KMP directory"
        return 1
    fi

    if [ ! -f "$KMP_DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
        log_step "Downloading Gradle wrapper..."
        cd "$KMP_DIR"

        # Download gradle wrapper jar
        local wrapper_url="https://services.gradle.org/distributions/gradle-8.10-bin.zip"
        if command -v curl &> /dev/null; then
            mkdir -p gradle/wrapper
            curl -sL "https://raw.githubusercontent.com/gradle/gradle/v8.10.0/gradle/wrapper/gradle-wrapper.jar" -o gradle/wrapper/gradle-wrapper.jar 2>/dev/null || true
        fi

        cd "$PROJECT_DIR"
    fi

    # Make gradlew executable
    chmod +x "$KMP_DIR/gradlew" 2>/dev/null || true

    return 0
}

# Build KMP web client (WASM)
build_kmp_web() {
    log_client "Building KMP Web Client (WASM)..."

    if ! check_java; then
        return 1
    fi

    if ! ensure_gradle_wrapper; then
        log_error "Could not set up Gradle wrapper"
        return 1
    fi

    cd "$KMP_DIR"

    log_step "Running Gradle wasmJsBrowserDistribution..."
    ./gradlew :composeApp:wasmJsBrowserDistribution --no-daemon --warning-mode=summary || {
        log_error "KMP web build failed"
        cd "$PROJECT_DIR"
        return 1
    }

    # Copy built files to nginx serving directory
    local build_output="$KMP_DIR/composeApp/build/dist/wasmJs/productionExecutable"
    local nginx_dest="$PROJECT_DIR/src/clients/web/dist-kmp"

    if [ -d "$build_output" ]; then
        log_step "Copying build output to web directory..."
        mkdir -p "$nginx_dest"
        cp -r "$build_output/"* "$nginx_dest/"
        log_success "KMP web client built successfully!"
        echo "  Output: $nginx_dest"
    else
        log_warn "Build output not found at $build_output"
    fi

    cd "$PROJECT_DIR"
    return 0
}

# Build KMP desktop client
build_kmp_desktop() {
    local target="${1:-all}"

    log_client "Building KMP Desktop Client..."

    if ! check_java; then
        return 1
    fi

    if ! ensure_gradle_wrapper; then
        log_error "Could not set up Gradle wrapper"
        return 1
    fi

    cd "$KMP_DIR"

    case "$target" in
        linux|tar)
            log_step "Building Linux AppImage..."
            ./gradlew :composeApp:packageAppImage --no-daemon || {
                log_error "Linux build failed"
                cd "$PROJECT_DIR"
                return 1
            }
            log_step "Creating tar.gz distribution..."
            ./gradlew :composeApp:packageLinuxTarGz --no-daemon || true
            ;;
        deb)
            log_step "Building Debian package..."
            ./gradlew :composeApp:packageDeb --no-daemon
            ;;
        rpm)
            log_step "Building RPM package..."
            ./gradlew :composeApp:packageRpm --no-daemon
            ;;
        windows|msi)
            log_step "Building Windows installer..."
            ./gradlew :composeApp:packageMsi --no-daemon
            ;;
        macos|dmg)
            log_step "Building macOS bundle..."
            ./gradlew :composeApp:packageDmg --no-daemon
            ;;
        all)
            log_step "Building all desktop distributions..."
            ./gradlew :composeApp:packageAppImage --no-daemon || true
            ./gradlew :composeApp:packageLinuxTarGz --no-daemon || true
            # Only build platform-specific on that platform
            case "$(uname -s)" in
                Linux*)
                    ./gradlew :composeApp:packageDeb --no-daemon || true
                    ;;
                Darwin*)
                    ./gradlew :composeApp:packageDmg --no-daemon || true
                    ;;
                MINGW*|CYGWIN*|MSYS*)
                    ./gradlew :composeApp:packageMsi --no-daemon || true
                    ;;
            esac
            ;;
        *)
            log_error "Unknown desktop target: $target"
            log_info "Valid targets: linux, deb, rpm, windows, macos, all"
            cd "$PROJECT_DIR"
            return 1
            ;;
    esac

    log_success "KMP desktop client built!"
    echo "  Output: $KMP_DIR/composeApp/build/compose/binaries/"

    cd "$PROJECT_DIR"
    return 0
}

# Run KMP desktop client in development mode
run_kmp_desktop() {
    log_client "Running KMP Desktop Client..."

    if ! check_java; then
        return 1
    fi

    if ! ensure_gradle_wrapper; then
        log_error "Could not set up Gradle wrapper"
        return 1
    fi

    cd "$KMP_DIR"

    log_step "Starting desktop application..."
    ./gradlew :composeApp:run --no-daemon

    cd "$PROJECT_DIR"
}

# Run KMP web client development server
run_kmp_web_dev() {
    log_client "Starting KMP Web Development Server..."

    if ! check_java; then
        return 1
    fi

    if ! ensure_gradle_wrapper; then
        log_error "Could not set up Gradle wrapper"
        return 1
    fi

    cd "$KMP_DIR"

    log_step "Starting WASM development server..."
    log_info "Press Ctrl+C to stop"
    ./gradlew :composeApp:wasmJsBrowserDevelopmentRun --no-daemon --continuous

    cd "$PROJECT_DIR"
}

# Build both TypeScript and KMP web clients
build_all_web_clients() {
    log_step "Building all web clients..."

    # Build TypeScript client (existing)
    if [ -f "$PROJECT_DIR/src/clients/web/package.json" ]; then
        log_info "Building TypeScript web client..."
        cd "$PROJECT_DIR/src/clients/web"
        if command -v npm &> /dev/null; then
            npm ci --silent 2>/dev/null || npm install --silent
            npm run build
        else
            log_warn "npm not found, skipping TypeScript client build"
        fi
        cd "$PROJECT_DIR"
    fi

    # Build KMP client
    build_kmp_web

    log_success "All web clients built!"
}

wait_for_postgres() {
    log_info "Waiting for PostgreSQL to be healthy..."
    local retries=0
    local max_retries=30

    while [ $retries -lt $max_retries ]; do
        if docker compose exec -T postgres pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null 2>&1; then
            log_success "PostgreSQL is ready"
            return 0
        fi
        retries=$((retries + 1))
        echo -n "."
        sleep 2
    done
    echo ""
    log_error "PostgreSQL failed to become ready"
    return 1
}

wait_for_redis() {
    log_info "Waiting for Redis to be healthy..."
    local retries=0
    local max_retries=15

    while [ $retries -lt $max_retries ]; do
        if docker compose exec -T redis redis-cli ping >/dev/null 2>&1; then
            log_success "Redis is ready"
            return 0
        fi
        retries=$((retries + 1))
        echo -n "."
        sleep 1
    done
    echo ""
    log_error "Redis failed to become ready"
    return 1
}

start_services() {
    local skip_client_build="${1:-false}"
    ensure_env
    ensure_ssl_certs

    # Build KMP web client if enabled
    if [ "$skip_client_build" != "true" ] && [ "${KMP_BUILD_WEB:-true}" == "true" ]; then
        log_info "Building KMP web client before starting services..."
        build_kmp_web || log_warn "KMP build failed, continuing with existing files..."
    fi

    log_info "Stopping existing services..."
    docker compose down --remove-orphans 2>/dev/null || true

    log_info "Rebuilding Docker images..."
    docker compose build --no-cache server nginx

    log_info "Starting Rustscape server stack..."

    # Start infrastructure first (postgres, redis)
    docker compose up -d postgres redis

    # Wait for infrastructure
    wait_for_postgres
    wait_for_redis

    # Start server and nginx
    docker compose up -d server nginx

    log_success "Server stack started!"
    echo ""
    log_info "Services available at:"
    echo "  - Web Client (HTTP):   http://localhost:${NGINX_HTTP_PORT:-8088}/web-client/"
    echo "  - Web Client (HTTPS):  https://localhost:${NGINX_HTTPS_PORT:-8443}/web-client/"
    echo "  - KMP Client (HTTP):   http://localhost:${NGINX_HTTP_PORT:-8088}/kmp/"
    echo "  - KMP Client (HTTPS):  https://localhost:${NGINX_HTTPS_PORT:-8443}/kmp/"
    echo "  - WebSocket (WS):      ws://localhost:${NGINX_HTTP_PORT:-8088}/ws"
    echo "  - WebSocket (WSS):     wss://localhost:${NGINX_HTTPS_PORT:-8443}/ws"
    echo "  - Game TCP:            localhost:43597 (base), localhost:43598 (world 1)"
    echo "  - REST API (HTTP):     http://localhost:${NGINX_HTTP_PORT:-8088}/api/v1"
    echo "  - REST API (HTTPS):    https://localhost:${NGINX_HTTPS_PORT:-8443}/api/v1"
    echo "  - Management:          localhost:5556"
    echo "  - PostgreSQL:          localhost:${POSTGRES_EXTERNAL_PORT:-5433}"
    echo "  - Redis:               localhost:${REDIS_EXTERNAL_PORT:-6380}"
    echo ""
    log_warn "HTTPS uses self-signed certificates. Your browser will show a warning."
    echo ""
    log_info "API Endpoints:"
    echo "  - POST /api/v1/auth/register      - Create new account"
    echo "  - POST /api/v1/auth/login         - Login and get JWT"
    echo "  - POST /api/v1/auth/logout        - Logout"
    echo "  - GET  /api/v1/auth/session       - Check session"
    echo "  - POST /api/v1/auth/refresh       - Refresh JWT token"
    echo ""
}

start_dev() {
    ensure_env
    ensure_ssl_certs

    # Build KMP web client
    if [ "${KMP_BUILD_WEB:-true}" == "true" ]; then
        log_info "Building KMP web client..."
        build_kmp_web || log_warn "KMP build failed, continuing with existing files..."
    fi

    log_info "Stopping existing services..."
    docker compose --profile dev down --remove-orphans 2>/dev/null || true

    log_info "Rebuilding Docker images..."
    docker compose build --no-cache server nginx

    log_info "Starting Rustscape server stack with dev tools..."

    # Start infrastructure first
    docker compose up -d postgres redis

    # Wait for infrastructure
    wait_for_postgres
    wait_for_redis

    # Start server, nginx, and dev tools
    docker compose --profile dev up -d

    log_success "Server stack with dev tools started!"
    echo ""
    log_info "Services available at:"
    echo "  - Web Client (HTTP):   http://localhost:${NGINX_HTTP_PORT:-8088}/web-client/"
    echo "  - Web Client (HTTPS):  https://localhost:${NGINX_HTTPS_PORT:-8443}/web-client/"
    echo "  - KMP Client (HTTP):   http://localhost:${NGINX_HTTP_PORT:-8088}/kmp/"
    echo "  - KMP Client (HTTPS):  https://localhost:${NGINX_HTTPS_PORT:-8443}/kmp/"
    echo "  - WebSocket (WS):      ws://localhost:${NGINX_HTTP_PORT:-8088}/ws"
    echo "  - WebSocket (WSS):     wss://localhost:${NGINX_HTTPS_PORT:-8443}/ws"
    echo "  - Game TCP:            localhost:43597 (base), localhost:43598 (world 1)"
    echo "  - REST API (HTTP):     http://localhost:${NGINX_HTTP_PORT:-8088}/api/v1"
    echo "  - REST API (HTTPS):    https://localhost:${NGINX_HTTPS_PORT:-8443}/api/v1"
    echo "  - Management:          localhost:5556"
    echo "  - PostgreSQL:          localhost:${POSTGRES_EXTERNAL_PORT:-5433}"
    echo "  - Redis:               localhost:${REDIS_EXTERNAL_PORT:-6380}"
    echo ""
    log_warn "HTTPS uses self-signed certificates. Your browser will show a warning."
    echo ""
    log_info "Dev Tools:"
    echo "  - pgAdmin:         http://localhost:${PGADMIN_PORT:-5050}"
    echo "                     Email: ${PGADMIN_EMAIL:-admin@rustscape.local}"
    echo "                     Password: (see .env file)"
    echo "  - Redis Commander: http://localhost:${REDIS_COMMANDER_PORT:-8081}"
    echo ""
}

stop_services() {
    log_info "Stopping server stack..."
    docker compose --profile dev down
    log_success "Server stack stopped"
}

restart_services() {
    stop_services
    start_services
}

show_logs() {
    docker compose logs -f "$@"
}

show_status() {
    ensure_env
    log_info "Service status:"
    echo ""
    docker compose ps -a
}

build_server() {
    ensure_env

    # Build KMP clients if requested
    if [ "${KMP_BUILD_WEB:-true}" == "true" ]; then
        log_info "Building KMP web client..."
        build_kmp_web || log_warn "KMP build failed, continuing..."
    fi

    log_info "Building server images..."
    docker compose build server nginx
    log_success "Build complete"
}

clean_all() {
    log_warn "This will remove all containers and volumes!"
    log_warn "All data (users, players, etc.) will be DELETED!"
    read -p "Are you sure? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        log_info "Stopping and removing containers..."
        docker compose --profile dev down -v
        log_success "Cleanup complete"
    else
        log_info "Cancelled"
    fi
}

init_env() {
    local force="false"
    if [ "$1" == "--force" ] || [ "$1" == "-f" ]; then
        force="true"
    fi
    generate_env_file "$force"
}

# SSL certificate commands
ssl_command() {
    local subcmd="${1:-generate}"
    shift 2>/dev/null || true

    case "$subcmd" in
        generate|gen)
            local force="false"
            if [ "$1" == "--force" ] || [ "$1" == "-f" ]; then
                force="true"
            fi
            generate_ssl_certs "$force"
            ;;
        check|status)
            if check_ssl_certs; then
                log_success "SSL certificates are present"
                echo ""
                log_info "Certificate details:"
                openssl x509 -in "$SSL_DIR/rustscape.crt" -noout -subject -dates 2>/dev/null || echo "  Unable to read certificate"
                echo ""
                log_info "Files:"
                ls -la "$SSL_DIR/" 2>/dev/null || echo "  SSL directory not found"
            else
                log_warn "SSL certificates not found"
                log_info "Run '$0 ssl generate' to create self-signed certificates"
            fi
            ;;
        info)
            if check_ssl_certs; then
                log_info "Certificate information:"
                openssl x509 -in "$SSL_DIR/rustscape.crt" -noout -text 2>/dev/null | head -30
            else
                log_error "No certificate found"
            fi
            ;;
        help|--help|-h)
            echo "SSL certificate commands:"
            echo ""
            echo "  ssl generate [--force]  - Generate self-signed SSL certificates"
            echo "  ssl check               - Check if certificates exist"
            echo "  ssl info                - Show certificate details"
            echo ""
            echo "Options:"
            echo "  --force, -f             - Regenerate certificates even if they exist"
            echo ""
            echo "Certificate location: $SSL_DIR/"
            echo ""
            ;;
        *)
            log_error "Unknown SSL command: $subcmd"
            ssl_command help
            return 1
            ;;
    esac
}

# Cache directory
CACHE_DIR="$PROJECT_DIR/cache"
SPRITES_DIR="$PROJECT_DIR/src/clients/web/public/sprites"

# Assemble cache from split parts if needed
assemble_cache() {
    # Check if already assembled
    if [ -f "$CACHE_DIR/main_file_cache.dat2" ]; then
        return 0
    fi

    # Check for split parts
    local parts=$(ls "$CACHE_DIR"/main_file_cache.dat2.part_* 2>/dev/null | wc -l)
    if [ "$parts" -eq 0 ]; then
        return 1
    fi

    log_info "Assembling cache from $parts split parts..."
    cat "$CACHE_DIR"/main_file_cache.dat2.part_* > "$CACHE_DIR/main_file_cache.dat2"

    if [ $? -eq 0 ]; then
        local size=$(stat -c%s "$CACHE_DIR/main_file_cache.dat2" 2>/dev/null || stat -f%z "$CACHE_DIR/main_file_cache.dat2" 2>/dev/null)
        log_success "Assembled main_file_cache.dat2 ($size bytes)"
        return 0
    else
        log_error "Failed to assemble cache"
        return 1
    fi
}

# Check if cache files exist
check_cache_files() {
    if [ ! -d "$CACHE_DIR" ]; then
        return 1
    fi

    # Try to assemble from parts if dat2 doesn't exist
    if [ ! -f "$CACHE_DIR/main_file_cache.dat2" ]; then
        assemble_cache
    fi

    if [ ! -f "$CACHE_DIR/main_file_cache.dat2" ]; then
        return 1
    fi

    # Check for at least idx0
    if [ ! -f "$CACHE_DIR/main_file_cache.idx0" ]; then
        return 1
    fi

    return 0
}

# Set up cache directory structure
setup_cache() {
    log_step "Setting up cache directory..."

    mkdir -p "$CACHE_DIR"
    mkdir -p "$SPRITES_DIR"

    if check_cache_files; then
        log_success "Cache files found!"
        log_info "Cache directory: $CACHE_DIR"
    else
        log_warn "Cache directory created but cache files are missing!"
        echo ""
        log_info "To get game graphics, you need RuneScape revision 530 cache files."
        echo ""
        echo "Required files in $CACHE_DIR:"
        echo "  - main_file_cache.dat2"
        echo "  - main_file_cache.idx0 through idx255"
        echo ""
        log_info "You can obtain these from:"
        echo "  - OSRS cache (for 2007-era graphics)"
        echo "  - RuneScape cache archives online"
        echo "  - Extract from an existing client"
        echo ""
    fi
}

# Extract sprites from cache
extract_sprites() {
    log_step "Extracting sprites from game cache..."

    if ! check_cache_files; then
        log_error "Cache files not found!"
        log_info "Run '$0 cache setup' first and add your cache files."
        return 1
    fi

    # Build the extract_sprites tool if needed
    log_info "Building sprite extractor..."
    cd "$PROJECT_DIR/src/server"

    if ! cargo build --release --bin extract-sprites 2>/dev/null; then
        log_error "Failed to build sprite extractor"
        cd "$PROJECT_DIR"
        return 1
    fi

    cd "$PROJECT_DIR"

    # Create output directory
    mkdir -p "$SPRITES_DIR"

    # Run extraction
    log_info "Extracting sprites (this may take a while)..."
    ./src/server/target/release/extract-sprites \
        --cache "$CACHE_DIR" \
        --output "$SPRITES_DIR" \
        --verbose

    if [ $? -eq 0 ]; then
        log_success "Sprite extraction complete!"
        log_info "Sprites saved to: $SPRITES_DIR"

        # Count extracted sprites
        local count=$(find "$SPRITES_DIR" -name "*.png" 2>/dev/null | wc -l)
        log_info "Extracted $count sprite images"
    else
        log_error "Sprite extraction failed"
        return 1
    fi
}

# Check sprite extraction status
check_sprites() {
    log_info "Checking sprite status..."

    if [ ! -d "$SPRITES_DIR" ]; then
        log_warn "Sprites directory not found"
        log_info "Run '$0 sprites extract' to extract sprites"
        return 1
    fi

    local count=$(find "$SPRITES_DIR" -name "*.png" 2>/dev/null | wc -l)

    if [ "$count" -eq 0 ]; then
        log_warn "No sprites found"
        log_info "Run '$0 sprites extract' to extract sprites"
        return 1
    fi

    log_success "Found $count sprite images"
    log_info "Sprites location: $SPRITES_DIR"

    # Check for manifest files
    if [ -f "$SPRITES_DIR/sprites/manifest.json" ]; then
        log_info "UI sprites manifest: present"
    fi
    if [ -f "$SPRITES_DIR/textures/manifest.json" ]; then
        log_info "Textures manifest: present"
    fi
}

# Sprites command handler
sprites_command() {
    local subcmd="${1:-help}"
    shift 2>/dev/null || true

    case "$subcmd" in
        extract|ex)
            extract_sprites
            ;;
        status|check)
            check_sprites
            ;;
        help|--help|-h)
            echo "Sprite commands:"
            echo ""
            echo "  sprites extract    - Extract sprites from game cache"
            echo "  sprites status     - Check sprite extraction status"
            echo ""
            echo "Prerequisites:"
            echo "  1. Place cache files in ./cache/"
            echo "  2. Run 'sprites extract' to extract"
            echo ""
            ;;
        *)
            log_error "Unknown sprites command: $subcmd"
            sprites_command help
            return 1
            ;;
    esac
}

# Cache command handler
cache_command() {
    local subcmd="${1:-help}"
    shift 2>/dev/null || true

    case "$subcmd" in
        setup)
            setup_cache
            ;;
        check|status)
            if check_cache_files; then
                log_success "Cache files found!"
                log_info "Cache directory: $CACHE_DIR"
                ls -lh "$CACHE_DIR"/main_file_cache.* 2>/dev/null | head -5
                local idx_count=$(ls "$CACHE_DIR"/main_file_cache.idx* 2>/dev/null | wc -l)
                log_info "Index files: $idx_count"
            else
                log_error "Cache files not found!"
                log_info "Run '$0 cache setup' for instructions"
            fi
            ;;
        help|--help|-h)
            echo "Cache commands:"
            echo ""
            echo "  cache setup     - Set up cache directory structure"
            echo "  cache check     - Verify cache files exist"
            echo ""
            echo "Cache location: $CACHE_DIR"
            echo ""
            echo "Required files:"
            echo "  - main_file_cache.dat2"
            echo "  - main_file_cache.idx0 through idx255"
            echo ""
            ;;
        *)
            log_error "Unknown cache command: $subcmd"
            cache_command help
            return 1
            ;;
    esac
}

# Client build commands
client_command() {
    local subcmd="${1:-help}"
    shift 2>/dev/null || true

    case "$subcmd" in
        web)
            build_kmp_web
            ;;
        desktop)
            build_kmp_desktop "$@"
            ;;
        all)
            build_kmp_web
            build_kmp_desktop all
            ;;
        run)
            run_kmp_desktop
            ;;
        dev)
            run_kmp_web_dev
            ;;
        help|--help|-h)
            echo "Client build commands:"
            echo ""
            echo "  client web              - Build KMP web client (WASM)"
            echo "  client desktop [target] - Build KMP desktop client"
            echo "                            Targets: linux, deb, rpm, windows, macos, all"
            echo "  client all              - Build both web and desktop clients"
            echo "  client run              - Run desktop client in dev mode"
            echo "  client dev              - Run web client dev server with hot reload"
            echo ""
            ;;
        *)
            log_error "Unknown client command: $subcmd"
            client_command help
            return 1
            ;;
    esac
}

show_help() {
    echo "Usage: $0 [command] [options]"
    echo ""
    echo "Server Commands:"
    echo "  start         Start the server stack (default)"
    echo "  stop          Stop the server stack"
    echo "  restart       Restart the server stack"
    echo "  logs [svc]    View logs (optionally for specific service)"
    echo "  status        Show status of all services"
    echo "  build         Rebuild the server images (includes KMP client)"
    echo "  clean         Stop and remove all containers and volumes"
    echo "  dev           Start with dev tools (pgAdmin, Redis Commander)"
    echo "  init [--force] Initialize/regenerate .env file"
    echo ""
    echo "SSL Commands:"
    echo "  ssl generate [--force]  - Generate self-signed SSL certificates"
    echo "  ssl check               - Check certificate status"
    echo "  ssl info                - Show certificate details"
    echo ""
    echo "Client Commands:"
    echo "  client web              - Build KMP web client (WASM)"
    echo "  client desktop [target] - Build KMP desktop client"
    echo "  client all              - Build all clients"
    echo "  client run              - Run desktop client"
    echo "  client dev              - Run web dev server"
    echo ""
    echo "Cache/Asset Commands:"
    echo "  sprites extract         - Extract sprites from game cache"
    echo "  sprites status          - Check sprite extraction status"
    echo "  cache setup             - Set up cache directory structure"
    echo "  cache check             - Verify cache files exist"
    echo ""
    echo "Quick Commands:"
    echo "  web           Build and start with KMP web client"
    echo "  quick         Start without rebuilding clients"
    echo ""
    echo "Cache Setup:"
    echo "  Place your RuneScape 530 cache files in ./cache/"
    echo "  Required files: main_file_cache.dat2, main_file_cache.idx0-255"
    echo ""
    echo "Examples:"
    echo "  $0                       # Start the server (builds KMP client)"
    echo "  $0 quick                 # Start without building clients"
    echo "  $0 dev                   # Start with dev tools"
    echo "  $0 ssl generate          # Generate SSL certificates"
    echo "  $0 client web            # Build only the web client"
    echo "  $0 client desktop linux  # Build Linux desktop client"
    echo "  $0 logs server           # View server logs only"
    echo "  $0 init --force          # Regenerate .env with new secrets"
    echo ""
}

# Parse command
COMMAND="${1:-start}"
shift 2>/dev/null || true

print_banner

case "$COMMAND" in
    start)
        start_services
        ;;
    quick)
        start_services true
        ;;
    stop)
        stop_services
        ;;
    restart)
        restart_services
        ;;
    logs)
        show_logs "$@"
        ;;
    status)
        show_status
        ;;
    build)
        build_server
        ;;
    clean)
        clean_all
        ;;
    dev)
        start_dev
        ;;
    init)
        init_env "$@"
        ;;
    client)
        client_command "$@"
        ;;
    ssl)
        ssl_command "$@"
        ;;
    sprites)
        sprites_command "$@"
        ;;
    cache)
        cache_command "$@"
        ;;
    web)
        build_kmp_web
        start_services true
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        log_error "Unknown command: $COMMAND"
        echo ""
        show_help
        exit 1
        ;;
esac
