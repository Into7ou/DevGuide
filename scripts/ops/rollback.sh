#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${ROLLBACK_DB_COMPATIBLE:-}" != "yes" ]]; then
  echo "rollback blocked: review Flyway changes and set ROLLBACK_DB_COMPATIBLE=yes only when the current database is backward compatible" >&2
  exit 2
fi
if [[ $# -ne 2 ]]; then
  echo "usage: rollback.sh <previous-immutable-tag> <protected-env-file>" >&2
  exit 2
fi

export SKIP_PREDEPLOY_BACKUP=yes
bash "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/deploy.sh" "$1" "$2"
echo "application rollback completed; if the schema was incompatible, restore the pre-deploy backup instead"
