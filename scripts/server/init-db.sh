#!/bin/bash
#
# Database initialization script for Rustscape
# Waits for MySQL to be ready and initializes the database
#

set -e

# Configuration
MAX_RETRIES=30
RETRY_INTERVAL=2
MYSQL_USER="${MYSQL_USER:-jordan}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-123456}"
MYSQL_DATABASE="${MYSQL_DATABASE:-global}"
MYSQL_HOST="${MYSQL_HOST:-localhost}"

echo "=== Rustscape Database Initialization ==="

# Wait for MySQL to be ready
wait_for_mysql() {
    echo "Waiting for MySQL to be ready..."
    local retries=0

    while [ $retries -lt $MAX_RETRIES ]; do
        if mysqladmin ping -h "$MYSQL_HOST" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --silent 2>/dev/null; then
            echo "MySQL is ready!"
            return 0
        fi

        retries=$((retries + 1))
        echo "MySQL not ready yet (attempt $retries/$MAX_RETRIES)..."
        sleep $RETRY_INTERVAL
    done

    echo "ERROR: MySQL did not become ready in time"
    return 1
}

# Initialize the database
init_database() {
    echo "Checking database $MYSQL_DATABASE..."

    # Check if database exists
    if mysql -h "$MYSQL_HOST" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "USE $MYSQL_DATABASE" 2>/dev/null; then
        echo "Database $MYSQL_DATABASE already exists"
    else
        echo "Creating database $MYSQL_DATABASE..."
        mysql -h "$MYSQL_HOST" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS $MYSQL_DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
        echo "Database created successfully"
    fi

    # Run any SQL initialization scripts from /docker-entrypoint-initdb.d/
    if [ -d "/docker-entrypoint-initdb.d" ]; then
        for f in /docker-entrypoint-initdb.d/*.sql; do
            if [ -f "$f" ]; then
                echo "Running SQL script: $f"
                mysql -h "$MYSQL_HOST" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" < "$f"
                echo "Completed: $f"
            fi
        done
    fi
}

# Main execution
main() {
    wait_for_mysql
    init_database
    echo "=== Database initialization complete ==="
}

main "$@"
