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
  
  # Import initial database structure from any SQL files in the init directory
  for f in /docker-entrypoint-initdb.d/*.sql; do
    if [ -f "$f" ]; then
      echo "Executing SQL file: $f"
      mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" global < "$f"
    fi
  done
  
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

# Create a flag file to indicate database initialization is complete
touch /var/run/db-initialized

echo "Database initialization completed!"
exit 0