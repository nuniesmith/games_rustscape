## scripts/server/wait-for-mysql.sh
#!/bin/bash
# Wait for MySQL to be ready
echo "Waiting for MySQL to be available..."
until mysqladmin ping -h "${MYSQL_HOST:-localhost}" -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" --silent; do
    echo "MySQL is unavailable - sleeping"
    sleep 2
done
echo "MySQL is up - executing command"
exec "$@"