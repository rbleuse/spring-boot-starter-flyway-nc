#!/usr/bin/env bash
set -euo pipefail

version="${1:-}"

if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([-.][0-9A-Za-z.-]+)?$ ]]; then
  echo "Invalid Flyway version: $version" >&2
  exit 1
fi

printf '%s\n' "$version"
