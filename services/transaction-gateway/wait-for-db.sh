#!/bin/sh
set -eu

URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://postgres:5432/fraud_db}"
DB_USER="${SPRING_DATASOURCE_USERNAME:-fraud_app}"

REST="${URL#jdbc:postgresql://}"
HOST_PORT="${REST%%/*}"
DB_NAME="${REST#"$HOST_PORT"}"
DB_NAME="${DB_NAME#/}"
DB_NAME="${DB_NAME%%\?*}"

case "$HOST_PORT" in
  *:*)
    DB_HOST="${HOST_PORT%:*}"
    DB_PORT="${HOST_PORT##*:}"
    ;;
  *)
    DB_HOST="$HOST_PORT"
    DB_PORT="5432"
    ;;
 esac

if [ -z "$DB_NAME" ]; then
  DB_NAME="fraud_db"
fi

echo "Waiting for PostgreSQL at ${DB_HOST}:${DB_PORT} (${DB_NAME})..."
while ! pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; do
  sleep 2
done

echo "PostgreSQL ready, starting gateway..."
exec java -jar /app/app.jar
