#!/bin/bash
set -e
echo "Starting 2009scape database initialization..."

# Wait for MySQL to be ready
echo "Waiting for MySQL to be ready..."
until mysqladmin ping -h localhost -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --silent; do
  echo "MySQL not ready yet... waiting 5 seconds"
  sleep 5
done

# Check if global database exists
echo "Checking if database needs initialization..."
DB_EXISTS=$(mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SHOW DATABASES LIKE 'global';" | grep -c "global" || echo "0")
if [ "$DB_EXISTS" = "0" ]; then
  echo "Global database doesn't exist. Creating it..."
  mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "CREATE DATABASE global;"
  # Grant permissions to the user
  mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "GRANT ALL PRIVILEGES ON global.* TO '$MYSQL_USER'@'%';"
  mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "FLUSH PRIVILEGES;"
  
  # Import initial database structure
  # Check if SQL files exist before importing them
  if [ -f "/docker-entrypoint-initdb.d/global.sql" ]; then
    echo "Importing global.sql..."
    mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" global < /docker-entrypoint-initdb.d/global.sql
  elif [ -f "/home/jordan/2009scape/server/db_exports/global.sql" ]; then
    echo "Importing global.sql from alternative location..."
    mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" global < /home/jordan/2009scape/server/db_exports/global.sql
  else
    echo "Warning: global.sql not found."
  fi
  
  if [ -f "/docker-entrypoint-initdb.d/testuser.sql" ]; then
    echo "Importing testuser.sql..."
    mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" global < /docker-entrypoint-initdb.d/testuser.sql
  elif [ -f "/home/jordan/2009scape/server/db_exports/testuser.sql" ]; then
    echo "Importing testuser.sql from alternative location..."
    mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" global < /home/jordan/2009scape/server/db_exports/testuser.sql
  else
    echo "Warning: testuser.sql not found."
  fi
  
  echo "Database initialized!"
fi

# Check if members table exists before trying to add admin user
MEMBERS_EXISTS=$(mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'global' AND table_name = 'members';" | grep -c "1" || echo "0")
if [ "$MEMBERS_EXISTS" = "0" ]; then
  echo "Creating members table..."
  mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" global -e "
  CREATE TABLE IF NOT EXISTS members (
    UID int(11) UNSIGNED NOT NULL AUTO_INCREMENT,
    username varchar(15) DEFAULT NULL,
    password varchar(100) DEFAULT NULL,
    credits int(5) NOT NULL DEFAULT 0,
    rights int(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (UID)
  );"
fi

# Create admin user if it doesn't exist
echo "Checking for admin user..."
ADMIN_EXISTS=$(mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -N -B -e "SELECT COUNT(*) FROM global.members WHERE username='admin';" 2>/dev/null || echo "0")
if [ "$ADMIN_EXISTS" = "0" ]; then
  echo "Creating admin user..."
  # Password is 'admin123' - in production you should use a secure password
  mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" global -e "INSERT INTO members (username, password, credits, rights) VALUES ('admin', 'e3274be5c857fb42ab72d786e281b4b8', 1000, 2);"
  echo "Admin user created!"
else
  echo "Admin user already exists."
fi

echo "Database initialization completed!"

## config/mysql/init/01-initialize-database.sql
-- Create user and grant privileges
CREATE USER IF NOT EXISTS '${MYSQL_USER}'@'%' IDENTIFIED BY '${MYSQL_PASSWORD}';
GRANT ALL PRIVILEGES ON `${MYSQL_DATABASE}`.* TO '${MYSQL_USER}'@'%';
FLUSH PRIVILEGES;

-- Create basic tables for 2009scape
CREATE TABLE IF NOT EXISTS `players` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `username` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `credits` int(11) DEFAULT 0,
  `client_rights` int(11) DEFAULT 0,
  `donator_credits` int(11) DEFAULT 0,
  `friends` text DEFAULT NULL,
  `ignores` text DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `game_logs` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `player_id` int(11) NOT NULL,
  `timestamp` timestamp NOT NULL DEFAULT current_timestamp(),
  `action` varchar(255) NOT NULL,
  `details` text DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `player_id` (`player_id`),
  CONSTRAINT `game_logs_ibfk_1` FOREIGN KEY (`player_id`) REFERENCES `players` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;