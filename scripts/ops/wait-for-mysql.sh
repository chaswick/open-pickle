#!/bin/bash
# wait-for-mysql.sh

set -e

host="$1"
shift
port="$1"
shift

until bash -c "echo >/dev/tcp/$host/$port" 2>/dev/null; do
  >&2 echo "MySQL is unavailable - sleeping"
  sleep 1
done

>&2 echo "MySQL is up - executing command"
exec "$@"
