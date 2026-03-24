#!/bin/sh
set -eu

mkdir -p /app/logs /data/trophy-storage
chown -R spring:spring /app/logs /data/trophy-storage

JAVA_BIN="$(command -v java)"

exec ./wait-for-mysql.sh db 3306 su -s /bin/sh spring -c "exec $JAVA_BIN $JAVA_OPTS -jar /app/app.jar"
