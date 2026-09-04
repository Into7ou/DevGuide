#!/usr/bin/env bash
set -Eeuo pipefail

base_url="${1:-https://devguide.into7ou.is-a.dev}"
curl --fail --silent --show-error --location --max-time 20 "$base_url/" >/dev/null
health="$(curl --fail --silent --show-error --max-time 20 "$base_url/api/health")"
grep -q ''UP'' <<<"$health"
curl --fail --silent --show-error --max-time 20 "$base_url/api/v1/showcase/tech-stacks" >/dev/null
echo "deployment verification passed: $base_url"
