#!/usr/bin/env bash
set -Eeuo pipefail

required=(OCI_BUCKET_NAME AGE_RECIPIENT)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "missing required environment variable: $name" >&2
    exit 2
  fi
done

for command_name in docker age oci jq sort tail; do
  command -v "$command_name" >/dev/null || {
    echo "missing required command: $command_name" >&2
    exit 2
  }
done

container="${BACKUP_CONTAINER:-techstack-postgres}"
timestamp="$(date -u +%Y-%m-%dT%H%M%SZ)"
day="$(date -u +%Y-%m-%d)"
week="$(date -u +%G-W%V)"
weekday="$(date -u +%u)"
encrypted_file="$(mktemp --suffix=.dump.age)"

cleanup() {
  rm -f -- "$encrypted_file"
}
trap cleanup EXIT

oci_args=(--bucket-name "$OCI_BUCKET_NAME")
[[ -n "${OCI_NAMESPACE:-}" ]] && oci_args+=(--namespace-name "$OCI_NAMESPACE")
[[ -n "${OCI_REGION:-}" ]] && oci_args+=(--region "$OCI_REGION")
[[ -n "${OCI_CLI_PROFILE:-}" ]] && oci_args+=(--profile "$OCI_CLI_PROFILE")
[[ -n "${OCI_CLI_CONFIG_FILE:-}" ]] && oci_args+=(--config-file "$OCI_CLI_CONFIG_FILE")

echo "creating encrypted PostgreSQL backup"
docker exec "$container" sh -lc 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges' |
  age --recipient "$AGE_RECIPIENT" --output "$encrypted_file"
test -s "$encrypted_file"

upload_object() {
  local object_name="$1"
  oci os object put "${oci_args[@]}" --name "$object_name" --file "$encrypted_file" --force >/dev/null
  echo "uploaded encrypted object: $object_name"
}

prune_prefix() {
  local prefix="$1"
  local keep="$2"
  mapfile -t objects < <(
    oci os object list "${oci_args[@]}" --prefix "$prefix/" --all --query 'data[].name' --output json |
      jq -r '.[]' |
      sort -r
  )
  if (( ${#objects[@]} <= keep )); then
    return
  fi
  for object_name in "${objects[@]:keep}"; do
    [[ "$object_name" == "$prefix/"* ]] || {
      echo "refusing to delete object outside prefix: $object_name" >&2
      exit 3
    }
    oci os object delete "${oci_args[@]}" --name "$object_name" --force
    echo "deleted expired object: $object_name"
  done
}

upload_object "daily/devguide-$timestamp.dump.age"
prune_prefix daily 7

if [[ "$weekday" == "7" ]]; then
  upload_object "weekly/devguide-$week-$day.dump.age"
  prune_prefix weekly 4
fi

echo "backup completed; plaintext was streamed directly into age and never stored on the host"
