#!/bin/bash
#
# Rustscape Management Server Startup Script
#

set -e

echo "=== Starting Rustscape Management Server ==="

# Configuration
MANAGEMENT_PORT="${MANAGEMENT_PORT:-5555}"

# Start the management server
start_management() {
    cd /app/2009scape

    # Check if management server exists
    if [ -f "Server/management.jar" ]; then
        echo "Starting management server on port $MANAGEMENT_PORT..."
        exec java -jar Server/management.jar --port "$MANAGEMENT_PORT"
    elif [ -f "management.jar" ]; then
        echo "Starting management server on port $MANAGEMENT_PORT..."
        exec java -jar management.jar --port "$MANAGEMENT_PORT"
    else
        echo "INFO: No separate management server found"
        echo "Management API should be available on the main server"
        # Keep the process running to satisfy supervisor
        exec tail -f /dev/null
    fi
}

# Main
main() {
    start_management
}

main "$@"
