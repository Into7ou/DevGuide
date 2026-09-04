#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${ALLOW_DATABASE_RESTORE:-}" != "yes" ]]; then
  echo "refusing restore: set ALLOW_DATABASE_RESTORE=yes after stopping the application and confirming the target" >&2
  exit 2
fi
if [[ $# -ne 2 ]]; then
  echo "usage: restore-production.sh <encrypted-backup.dump.age> <age-private-key-file>" >&2
  exit 2
fi

encrypted_backup="$1"
identity_file="$2"
container="${BACKUP_CONTAINER:-techstack-postgres}"
[[ -f "$encrypted_backup" && -f "$identity_file" ]] || {
  echo "backup or identity file does not exist" >&2
  exit 2
}

echo "restoring into the database configured inside container $container"
age --decrypt --identity "$identity_file" "$encrypted_backup" |
  docker exec -i "$container" sh -lc 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --no-privileges'
echo "restore completed; start the backend and verify Flyway, counts, login, and PGvector queries"
