## scripts/server/wait-for-db-init.sh
#!/bin/bash
# Wait for database initialization to complete
echo "Waiting for database initialization to complete..."
until [ -f /var/run/db-initialized ]; do
    echo "Database initialization not complete - sleeping"
    sleep 5
done
echo "Database initialization complete - executing command"
exec "$@"