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

    local service=$1
    if [ -n "$service" ]; then
        print_info "Building service: $service"
        $COMPOSE_CMD build $service
    else
        print_info "Building all services..."
        $COMPOSE_CMD build
    fi
    print_success "Build complete."
}

# Start services
start() {
    print_header "Starting Rustscape Services"
    ensure_prerequisites

    local service=$1
    if [ -n "$service" ]; then
        print_info "Starting service: $service"
        $COMPOSE_CMD up -d $service
    else
        print_info "Starting all services..."
        $COMPOSE_CMD up -d
    fi

    print_success "Services started."
    echo ""
    print_info "Access points:"
    echo "  • Web Client (noVNC):  http://localhost:6080"
    echo "  • Nginx Proxy:         http://localhost:80"
    echo "  • Game Server:         localhost:43594-43596"
    echo "  • Database:            localhost:3306"
    echo ""
    print_info "Use './run.sh logs' to view logs"
    print_info "Use './run.sh status' to check service health"
}

# Stop services
stop() {
    print_header "Stopping Rustscape Services"

    local service=$1
    if [ -n "$service" ]; then
        print_info "Stopping service: $service"
        $COMPOSE_CMD stop $service
    else
        print_info "Stopping all services..."
        $COMPOSE_CMD stop
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

    local flags=$1
    if [ "$flags" == "-v" ] || [ "$flags" == "--volumes" ]; then
        print_warning "Removing containers AND volumes (data will be lost)..."
        $COMPOSE_CMD down -v
    else
        print_info "Removing containers (volumes preserved)..."
        $COMPOSE_CMD down
    fi
    print_success "Containers removed."
}

# View logs
logs() {
    local service=$1
    local follow=${2:-"-f"}

    if [ -n "$service" ]; then
        $COMPOSE_CMD logs $follow $service
    else
        $COMPOSE_CMD logs $follow
    fi
}

# Show service status
status() {
    print_header "Service Status"

    echo -e "${CYAN}Container Status:${NC}"
    $COMPOSE_CMD ps

    echo ""
    echo -e "${CYAN}Health Checks:${NC}"

    # Check each service
    local services=("app" "database" "client" "nginx")
    for svc in "${services[@]}"; do
        local container="${PROJECT_NAME}_${svc}"
        if [ "$svc" == "database" ]; then
            container="${PROJECT_NAME}_db"
        fi

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
    local service=$1
    shift
    local cmd=${@:-"/bin/sh"}

    if [ -z "$service" ]; then
        print_error "Usage: ./run.sh exec <service> [command]"
        exit 1
    fi

    print_info "Executing in $service: $cmd"
    $COMPOSE_CMD exec $service $cmd
}

# Build and start (full deploy)
deploy() {
    print_header "Deploying Rustscape"
    ensure_prerequisites

    print_info "Building images..."
    $COMPOSE_CMD build

    print_info "Starting services..."
    $COMPOSE_CMD up -d

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
        $COMPOSE_CMD down -v --rmi all 2>/dev/null || true

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
    $COMPOSE_CMD pull
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

# Test WebSocket connection
test_ws() {
    print_header "Testing WebSocket Connection"

    local host=${1:-"localhost"}
    local port=${2:-"43596"}

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

# Show help
show_help() {
    echo ""
    echo -e "${CYAN}Rustscape Docker Environment Manager${NC}"
    echo ""
    echo "Usage: ./run.sh [command] [options]"
    echo ""
    echo "Commands:"
    echo "  start [service]     Start all services or a specific service"
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
    echo ""
    echo "Web Client Commands:"
    echo "  webclient:build     Build the TypeScript web client"
    echo "  webclient:dev       Start web client dev server"
    echo "  test:ws [host] [port] Test WebSocket connection"
    echo ""
    echo "Examples:"
    echo "  ./run.sh start              # Start all services"
    echo "  ./run.sh start app          # Start only the game server"
    echo "  ./run.sh logs app           # View game server logs"
    echo "  ./run.sh exec app /bin/bash # Shell into game server container"
    echo "  ./run.sh webclient:dev      # Start web client dev server"
    echo "  ./run.sh test:ws            # Test WebSocket to localhost:43596"
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
