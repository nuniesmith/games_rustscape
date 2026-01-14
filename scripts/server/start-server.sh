#!/bin/bash
#
# Rustscape Game Server Startup Script
#

set -e

echo "=== Starting Rustscape Game Server ==="

# Configuration
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_USER="${MYSQL_USER:-jordan}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-123456}"
MYSQL_DATABASE="${MYSQL_DATABASE:-global}"

# Wait for database to be ready
wait_for_database() {
    echo "Waiting for database connection..."
    local retries=0
    local max_retries=60

    while [ $retries -lt $max_retries ]; do
        if mysqladmin ping -h "$MYSQL_HOST" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --silent 2>/dev/null; then
            echo "Database is ready!"
            return 0
        fi

        retries=$((retries + 1))
        echo "Database not ready yet (attempt $retries/$max_retries)..."
        sleep 2
    done

    echo "WARNING: Could not connect to database, starting server anyway..."
    return 0
}

# Start the game server
start_server() {
    cd /app/2009scape

    if [ -f "./run" ]; then
        echo "Starting 2009scape server..."
        chmod +x ./run
        exec ./run
    elif [ -f "Server/server.jar" ]; then
        echo "Starting server from JAR..."
        exec java ${JAVA_OPTS} -jar Server/server.jar
    else
        echo "ERROR: No server executable found!"
        echo "Contents of /app/2009scape:"
        ls -la /app/2009scape/
        exit 1
    fi
}

# Main
main() {
    wait_for_database
    start_server
}

main "$@"
