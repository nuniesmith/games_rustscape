#!/bin/bash

###############################
# Rustscape Docker Environment Manager
# Usage: ./run.sh [command] [options]
###############################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Project configuration
PROJECT_NAME="rustscape"
COMPOSE_FILE="docker-compose.yml"

# Default profile (rust = new Rust server, java = legacy Java server, all = both)
DEFAULT_PROFILE="rust"

# Print colored output
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo ""
    echo -e "${CYAN}╔════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║${NC}  $1"
    echo -e "${CYAN}╚════════════════════════════════════════════════╝${NC}"
    echo ""
}

# Check if Docker is running
check_docker() {
    if ! docker info > /dev/null 2>&1; then
        print_error "Docker is not running. Please start Docker first."
        exit 1
    fi
}

# Check if docker-compose is available
check_compose() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed."
        exit 1
    fi
    # Check for docker compose (v2) or docker-compose (v1)
    if docker compose version > /dev/null 2>&1; then
        COMPOSE_CMD="docker compose"
    elif command -v docker-compose &> /dev/null; then
        COMPOSE_CMD="docker-compose"
    else
        print_error "Docker Compose is not installed."
        exit 1
    fi
}

# Get the profile to use
get_profile() {
    local profile="${RUSTSCAPE_PROFILE:-$DEFAULT_PROFILE}"
    echo "$profile"
}

# Ensure required directories and files exist
ensure_prerequisites() {
    print_info "Checking prerequisites..."

    # Create SSL directory if it doesn't exist
    if [ ! -d "config/ssl" ]; then
        print_warning "SSL directory not found. Creating with self-signed certificate..."
        mkdir -p config/ssl
        openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
            -keyout config/ssl/server.key \
            -out config/ssl/server.crt \
            -subj "/C=US/ST=State/L=City/O=Rustscape/CN=localhost" 2>/dev/null
        print_success "Self-signed SSL certificate created."
    fi

    # Create required directories
    mkdir -p config/mysql/init
    mkdir -p config/html

    # Create default HTML files if they don't exist
    if [ ! -f "config/html/index.html" ]; then
        cat > config/html/index.html << 'EOF'
<!DOCTYPE html>
<html>
<head><title>Rustscape</title></head>
<body>
<h1>Rustscape Server</h1>
<p>Server is running. Connect with your game client.</p>
</body>
</html>
EOF
    fi

    if [ ! -f "config/html/status.html" ]; then
        cat > config/html/status.html << 'EOF'
<!DOCTYPE html>
<html>
<head><title>Status</title></head>
<body><h1>OK</h1></body>
</html>
EOF
    fi

    print_success "Prerequisites checked."
}

# Build all services
build() {
    print_header "Building Docker Images"
    ensure_prerequisites

    local profile=$(get_profile)
    local service=$1

    if [ -n "$service" ]; then
        print_info "Building service: $service (profile: $profile)"
        $COMPOSE_CMD --profile $profile build $service
    else
        print_info "Building all services (profile: $profile)..."
        $COMPOSE_CMD --profile $profile build
    fi
    print_success "Build complete."
}

# Wait for database to be healthy
wait_for_database() {
    print_info "Waiting for database to be healthy..."
    local max_attempts=60
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if docker exec rustscape_db mysqladmin ping -h localhost -ujordan -p123456 --silent 2>/dev/null; then
            print_success "Database is ready!"
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done

    echo ""
    print_error "Database failed to become healthy after $max_attempts attempts"
    return 1
}

# Start services
start() {
    print_header "Starting Rustscape Services"
    ensure_prerequisites

    local profile=$(get_profile)
    local service=$1

    # Always build first to ensure latest code
    print_info "Building images (profile: $profile)..."
    if [ -n "$service" ]; then
        $COMPOSE_CMD --profile $profile build $service
    else
        $COMPOSE_CMD --profile $profile build
    fi

    # Start database first (it's always needed)
    print_info "Starting database..."
    $COMPOSE_CMD up -d database

    # Wait for database to be ready
    wait_for_database

    # Start remaining services
    if [ -n "$service" ]; then
        if [ "$service" != "database" ]; then
            print_info "Starting service: $service (profile: $profile)"
            $COMPOSE_CMD --profile $profile up -d $service
        fi
    else
        print_info "Starting all services (profile: $profile)..."
        $COMPOSE_CMD --profile $profile up -d
    fi

    print_success "Services started."
    echo ""

    # Show appropriate access points based on profile
    if [ "$profile" == "rust" ] || [ "$profile" == "all" ]; then
        print_info "Rust Server Access Points:"
        echo "  • Web Client:      http://localhost:8088"
        echo "  • WebSocket:       ws://localhost:8088/ws (proxied)"
        echo "  • WebSocket:       ws://localhost:43599 (direct)"
        echo "  • Game TCP:        localhost:43597-43598"
        echo "  • Management API:  localhost:5556"
    fi

    if [ "$profile" == "java" ] || [ "$profile" == "all" ]; then
        echo ""
        print_info "Java Server Access Points (Legacy):"
        echo "  • Web Client:      http://localhost"
        echo "  • Desktop Client:  http://localhost:6080 (noVNC)"
        echo "  • WebSocket:       ws://localhost:43596"
        echo "  • Game TCP:        localhost:43594-43595"
        echo "  • Management API:  localhost:5555"
    fi

    echo ""
    echo "  • Database:        localhost:3306"
    echo ""
    print_info "Use './run.sh logs' to view logs"
    print_info "Use './run.sh status' to check service health"
}

# Stop services
stop() {
    print_header "Stopping Rustscape Services"

    local profile=$(get_profile)
    local service=$1

    if [ -n "$service" ]; then
        print_info "Stopping service: $service"
        $COMPOSE_CMD --profile $profile stop $service
    else
        print_info "Stopping all services (profile: $profile)..."
        $COMPOSE_CMD --profile $profile stop
    fi
    print_success "Services stopped."
}

# Restart services
restart() {
    print_header "Restarting Rustscape Services"

    local service=$1
    stop $service
    start $service
}

# Stop and remove containers
down() {
    print_header "Removing Rustscape Containers"

    local profile=$(get_profile)
    local flags=$1

    if [ "$flags" == "-v" ] || [ "$flags" == "--volumes" ]; then
        print_warning "Removing containers AND volumes (data will be lost)..."
        $COMPOSE_CMD --profile $profile down -v
    else
        print_info "Removing containers (volumes preserved)..."
        $COMPOSE_CMD --profile $profile down
    fi
    print_success "Containers removed."
}

# View logs
logs() {
    local profile=$(get_profile)
    local service=$1
    local follow=${2:-"-f"}

    if [ -n "$service" ]; then
        $COMPOSE_CMD --profile $profile logs $follow $service
    else
        $COMPOSE_CMD --profile $profile logs $follow
    fi
}

# Show service status
status() {
    print_header "Service Status"

    local profile=$(get_profile)

    echo -e "${CYAN}Current Profile: $profile${NC}"
    echo ""
    echo -e "${CYAN}Container Status:${NC}"
    $COMPOSE_CMD --profile $profile ps

    echo ""
    echo -e "${CYAN}Health Checks:${NC}"

    # Define services based on profile
    local services=("database")
    if [ "$profile" == "rust" ] || [ "$profile" == "all" ]; then
        services+=("rust-server" "nginx-rust")
    fi
    if [ "$profile" == "java" ] || [ "$profile" == "all" ]; then
        services+=("app" "client" "nginx")
    fi

    for svc in "${services[@]}"; do
        local container="${PROJECT_NAME}_${svc}"
        # Handle special container names
        case "$svc" in
            "database") container="${PROJECT_NAME}_db" ;;
            "rust-server") container="${PROJECT_NAME}_rust_server" ;;
            "nginx-rust") container="${PROJECT_NAME}_nginx_rust" ;;
            "app") container="${PROJECT_NAME}_app" ;;
            "client") container="${PROJECT_NAME}_client" ;;
            "nginx") container="${PROJECT_NAME}_nginx" ;;
        esac

        local status=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "not running")
        local running=$(docker inspect --format='{{.State.Running}}' "$container" 2>/dev/null || echo "false")

        if [ "$running" == "true" ]; then
            if [ "$status" == "healthy" ]; then
                echo -e "  ${GREEN}●${NC} $svc: healthy"
            elif [ "$status" == "unhealthy" ]; then
                echo -e "  ${RED}●${NC} $svc: unhealthy"
            else
                echo -e "  ${YELLOW}●${NC} $svc: running (no healthcheck)"
            fi
        else
            echo -e "  ${RED}○${NC} $svc: not running"
        fi
    done
}

# Execute command in container
exec_cmd() {
    local profile=$(get_profile)
    local service=$1
    shift
    local cmd=${@:-"/bin/sh"}

    if [ -z "$service" ]; then
        print_error "Usage: ./run.sh exec <service> [command]"
        exit 1
    fi

    print_info "Executing in $service: $cmd"
    $COMPOSE_CMD --profile $profile exec $service $cmd
}

# Build and start (full deploy)
deploy() {
    print_header "Deploying Rustscape"
    ensure_prerequisites

    local profile=$(get_profile)

    print_info "Building images (profile: $profile)..."
    $COMPOSE_CMD --profile $profile build

    print_info "Starting database..."
    $COMPOSE_CMD up -d database
    wait_for_database

    print_info "Starting services (profile: $profile)..."
    $COMPOSE_CMD --profile $profile up -d

    print_success "Deployment complete!"
    status
}

# Clean up everything
clean() {
    print_header "Cleaning Up"

    print_warning "This will remove all containers, images, and volumes!"
    read -p "Are you sure? (y/N) " -n 1 -r
    echo

    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_info "Stopping and removing containers..."
        $COMPOSE_CMD --profile all down -v --rmi all 2>/dev/null || true

        print_info "Removing dangling images..."
        docker image prune -f

        print_success "Cleanup complete."
    else
        print_info "Cleanup cancelled."
    fi
}

# Pull latest images
pull() {
    print_header "Pulling Latest Images"
    local profile=$(get_profile)
    $COMPOSE_CMD --profile $profile pull
    print_success "Pull complete."
}

# Build web client
build_webclient() {
    print_header "Building Web Client"

    if [ ! -d "src/client/web" ]; then
        print_error "Web client directory not found at src/client/web"
        exit 1
    fi

    cd src/client/web

    if [ ! -f "package.json" ]; then
        print_error "package.json not found. Web client not initialized."
        exit 1
    fi

    print_info "Installing dependencies..."
    npm install

    print_info "Building..."
    npm run build

    print_success "Web client built successfully."
    cd - > /dev/null
}

# Run web client dev server
dev_webclient() {
    print_header "Starting Web Client Dev Server"

    if [ ! -d "src/client/web" ]; then
        print_error "Web client directory not found at src/client/web"
        exit 1
    fi

    cd src/client/web

    if [ ! -f "package.json" ]; then
        print_error "package.json not found. Web client not initialized."
        exit 1
    fi

    print_info "Installing dependencies..."
    npm install

    print_info "Starting dev server with --host flag..."
    print_info "Access at http://localhost:5173 or http://<your-ip>:5173"
    npm run dev -- --host
}

# Build and run Rust server locally (without Docker)
rust_local() {
    print_header "Building Rust Server Locally"

    if [ ! -d "src/server" ]; then
        print_error "Rust server directory not found at src/server"
        exit 1
    fi

    cd src/server

    print_info "Building Rust server..."
    cargo build --release

    print_info "Running tests..."
    cargo test

    print_success "Rust server built and tested successfully."
    print_info "Binary location: target/release/rustscape-server"
    cd - > /dev/null
}

# Run Rust server locally
rust_run() {
    print_header "Running Rust Server Locally"

    if [ ! -d "src/server" ]; then
        print_error "Rust server directory not found at src/server"
        exit 1
    fi

    cd src/server

    print_info "Starting Rust server..."
    RUST_LOG=info,rustscape_server=debug cargo run --release
}

# Test WebSocket connection
test_ws() {
    print_header "Testing WebSocket Connection"

    local profile=$(get_profile)
    local host=${1:-"localhost"}
    local port

    # Set default port based on profile
    if [ "$profile" == "rust" ]; then
        port=${2:-"43599"}
    else
        port=${2:-"43596"}
    fi

    if [ -f "src/client/web/scripts/test-websocket.js" ]; then
        cd src/client/web
        npm install 2>/dev/null || true
        node scripts/test-websocket.js $host $port
        cd - > /dev/null
    else
        print_error "Test script not found at src/client/web/scripts/test-websocket.js"
        exit 1
    fi
}

# Switch profile
set_profile() {
    local new_profile=$1

    if [ -z "$new_profile" ]; then
        print_error "Usage: ./run.sh profile <rust|java|all>"
        exit 1
    fi

    case "$new_profile" in
        rust|java|all)
            export RUSTSCAPE_PROFILE="$new_profile"
            print_success "Profile set to: $new_profile"
            print_info "Note: This only affects the current shell session."
            print_info "To make it permanent, add to your shell config:"
            echo "  export RUSTSCAPE_PROFILE=$new_profile"
            ;;
        *)
            print_error "Invalid profile: $new_profile"
            print_info "Valid profiles: rust, java, all"
            exit 1
            ;;
    esac
}

# Show help
show_help() {
    echo ""
    echo -e "${CYAN}Rustscape Docker Environment Manager${NC}"
    echo ""
    echo "Usage: ./run.sh [command] [options]"
    echo ""
    echo -e "${CYAN}Profiles:${NC}"
    echo "  rust    - Rust game server (default, recommended)"
    echo "  java    - Legacy Java game server"
    echo "  all     - Both servers running"
    echo ""
    echo "  Set profile: export RUSTSCAPE_PROFILE=<profile>"
    echo "  Current profile: $(get_profile)"
    echo ""
    echo -e "${CYAN}Docker Commands:${NC}"
    echo "  start [service]     Build and start all services or a specific service"
    echo "  stop [service]      Stop all services or a specific service"
    echo "  restart [service]   Restart all services or a specific service"
    echo "  build [service]     Build all images or a specific service"
    echo "  deploy              Build and start all services"
    echo "  down [-v]           Stop and remove containers (-v to remove volumes)"
    echo "  logs [service]      View logs (follows by default)"
    echo "  status              Show service status and health"
    echo "  exec <service> [cmd] Execute command in container (default: /bin/sh)"
    echo "  pull                Pull latest base images"
    echo "  clean               Remove all containers, images, and volumes"
    echo "  profile <name>      Set the profile (rust/java/all)"
    echo ""
    echo -e "${CYAN}Local Development Commands:${NC}"
    echo "  rust:build          Build Rust server locally"
    echo "  rust:run            Run Rust server locally (not in Docker)"
    echo "  webclient:build     Build the TypeScript web client"
    echo "  webclient:dev       Start web client dev server"
    echo "  test:ws [host] [port] Test WebSocket connection"
    echo ""
    echo -e "${CYAN}Examples:${NC}"
    echo "  ./run.sh start                    # Build and start Rust server stack"
    echo "  ./run.sh start rust-server        # Start only the Rust game server"
    echo "  ./run.sh logs rust-server         # View Rust server logs"
    echo "  ./run.sh exec rust-server /bin/sh # Shell into Rust server container"
    echo "  ./run.sh webclient:dev            # Start web client dev server"
    echo "  ./run.sh test:ws                  # Test WebSocket connection"
    echo ""
    echo -e "${CYAN}Using Legacy Java Server:${NC}"
    echo "  export RUSTSCAPE_PROFILE=java"
    echo "  ./run.sh start                    # Starts Java server stack"
    echo ""
    echo -e "${CYAN}Running Both Servers:${NC}"
    echo "  export RUSTSCAPE_PROFILE=all"
    echo "  ./run.sh start                    # Starts both servers"
    echo ""
}

# Main entry point
main() {
    # Change to script directory
    cd "$(dirname "$0")"

    check_docker
    check_compose

    case "$1" in
        start)
            start "$2"
            ;;
        stop)
            stop "$2"
            ;;
        restart)
            restart "$2"
            ;;
        build)
            build "$2"
            ;;
        deploy)
            deploy
            ;;
        down)
            down "$2"
            ;;
        logs)
            logs "$2" "$3"
            ;;
        status)
            status
            ;;
        exec)
            shift
            exec_cmd "$@"
            ;;
        pull)
            pull
            ;;
        clean)
            clean
            ;;
        profile)
            set_profile "$2"
            ;;
        rust:build)
            rust_local
            ;;
        rust:run)
            rust_run
            ;;
        webclient:build)
            build_webclient
            ;;
        webclient:dev)
            dev_webclient
            ;;
        test:ws)
            test_ws "$2" "$3"
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            if [ -n "$1" ]; then
                print_error "Unknown command: $1"
            fi
            show_help
            exit 1
            ;;
    esac
}

main "$@"
