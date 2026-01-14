#!/bin/bash
# Rustscape - Run Rust Server Stack
#
# This script starts the Rust game server with its supporting services.
# Usage: ./scripts/run-rust.sh [command]
#
# Commands:
#   start     - Start the Rust server stack (default)
#   stop      - Stop the Rust server stack
#   restart   - Restart the Rust server stack
#   logs      - View logs from all services
#   status    - Show status of all services
#   build     - Rebuild the Rust server image
#   clean     - Stop and remove all containers and volumes

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

print_banner() {
    echo ""
    echo "╔══════════════════════════════════════════════╗"
    echo "║        Rustscape - Rust Server Stack         ║"
    echo "╚══════════════════════════════════════════════╝"
    echo ""
}

start_services() {
    log_info "Starting Rust server stack..."

    # Start database first
    docker compose up -d database
    log_info "Waiting for database to be healthy..."

    # Wait for database
    until docker compose exec -T database mysqladmin ping -h localhost -ujordan -p123456 --silent 2>/dev/null; do
        sleep 2
    done
    log_success "Database is ready"

    # Start Rust server and nginx
    docker compose --profile rust up -d

    log_success "Rust server stack started!"
    echo ""
    log_info "Services available at:"
    echo "  - Web Client:    http://localhost:8088"
    echo "  - WebSocket:     ws://localhost:43599/ws (direct) or ws://localhost:8088/ws (proxied)"
    echo "  - Game TCP:      localhost:43597 (base), localhost:43598 (world 1)"
    echo "  - Management:    localhost:5556"
    echo "  - Database:      localhost:3306"
    echo ""
}

stop_services() {
    log_info "Stopping Rust server stack..."
    docker compose --profile rust down
    log_success "Rust server stack stopped"
}

restart_services() {
    stop_services
    start_services
}

show_logs() {
    docker compose --profile rust logs -f
}

show_status() {
    log_info "Service status:"
    echo ""
    docker compose --profile rust ps
}

build_server() {
    log_info "Building Rust server image..."
    docker compose --profile rust build rust-server
    log_success "Build complete"
}

clean_all() {
    log_warn "This will remove all containers and volumes for the Rust stack!"
    read -p "Are you sure? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        log_info "Stopping and removing containers..."
        docker compose --profile rust down -v
        log_success "Cleanup complete"
    else
        log_info "Cancelled"
    fi
}

# Parse command
COMMAND="${1:-start}"

print_banner

case "$COMMAND" in
    start)
        start_services
        ;;
    stop)
        stop_services
        ;;
    restart)
        restart_services
        ;;
    logs)
        show_logs
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
    *)
        echo "Usage: $0 {start|stop|restart|logs|status|build|clean}"
        exit 1
        ;;
esac
