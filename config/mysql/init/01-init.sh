#!/bin/bash
#
# MySQL initialization script for Rustscape
# This script runs when the MySQL container is first initialized
#

set -e

echo "=== Rustscape MySQL Initialization ==="

# Create database if it doesn't exist
mysql -u root -p"$MYSQL_ROOT_PASSWORD" <<-EOSQL
    -- Create the main database
    CREATE DATABASE IF NOT EXISTS \`${MYSQL_DATABASE:-global}\`
        CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

    -- Grant privileges to the application user
    GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE:-global}\`.* TO '${MYSQL_USER:-jordan}'@'%';

    -- Ensure the user can connect from any host
    GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE:-global}\`.* TO '${MYSQL_USER:-jordan}'@'localhost';

    FLUSH PRIVILEGES;

    -- Switch to the application database
    USE \`${MYSQL_DATABASE:-global}\`;

    -- Create players table if it doesn't exist
    CREATE TABLE IF NOT EXISTS players (
        id INT AUTO_INCREMENT PRIMARY KEY,
        username VARCHAR(32) NOT NULL UNIQUE,
        password_hash VARCHAR(255) NOT NULL,
        email VARCHAR(255),
        rights INT DEFAULT 0,
        banned BOOLEAN DEFAULT FALSE,
        muted BOOLEAN DEFAULT FALSE,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        last_login TIMESTAMP NULL,
        last_ip VARCHAR(45),
        INDEX idx_username (username),
        INDEX idx_email (email)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    -- Create player_data table for game state
    CREATE TABLE IF NOT EXISTS player_data (
        player_id INT PRIMARY KEY,
        data MEDIUMBLOB,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    -- Create logs table
    CREATE TABLE IF NOT EXISTS logs (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        log_type VARCHAR(50) NOT NULL,
        player_id INT,
        message TEXT,
        data JSON,
        ip_address VARCHAR(45),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_log_type (log_type),
        INDEX idx_player_id (player_id),
        INDEX idx_created_at (created_at),
        FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE SET NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    -- Create world_data table for persistent world state
    CREATE TABLE IF NOT EXISTS world_data (
        id INT AUTO_INCREMENT PRIMARY KEY,
        world_id INT NOT NULL,
        data_key VARCHAR(100) NOT NULL,
        data_value JSON,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        UNIQUE KEY uk_world_key (world_id, data_key),
        INDEX idx_world_id (world_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

EOSQL

echo "=== MySQL initialization complete ==="
