#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: deploy.sh <immutable-image-tag> <protected-env-file>" >&2
  exit 2
fi

image_tag="$1"
env_file="$2"
[[ "$image_tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$|^sha-[0-9a-f]{7,40}$ ]] || {
  echo "image tag must be an immutable release or commit SHA tag" >&2
  exit 2
}
[[ -f "$env_file" ]] || {
  echo "protected environment file not found: $env_file" >&2
  exit 2
}

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
workspace="$(cd -- "$script_dir/../.." && pwd)"
record_file="${RELEASE_RECORD_FILE:-/var/lib/devguide/releases.log}"

if [[ "${SKIP_PREDEPLOY_BACKUP:-}" != "yes" ]]; then
  bash "$script_dir/backup-production.sh"
fi

export IMAGE_TAG="$image_tag"
compose=(docker compose --env-file "$env_file" -f "$workspace/docker-compose.yml" -f "$workspace/docker-compose.prod.yml" --profile app)
"${compose[@]}" config --quiet
"${compose[@]}" pull backend frontend
"${compose[@]}" up -d postgres backend frontend
"${compose[@]}" ps
bash "$script_dir/verify-deployment.sh" "${FRONTEND_BASE_URL:-https://devguide.into7ou.is-a.dev}"

mkdir -p -- "$(dirname -- "$record_file")"
printf '%s tag=%s commit=%s result=success\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$image_tag" "${RELEASE_COMMIT_SHA:-unknown}" >>"$record_file"
echo "release recorded in $record_file"
