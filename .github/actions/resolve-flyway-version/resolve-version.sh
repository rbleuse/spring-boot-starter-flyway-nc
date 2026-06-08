#!/usr/bin/env bash
set -euo pipefail

version="${1:-}"
properties_file="${2:?gradle.properties path is required}"

if [[ -z "$version" ]]; then
  version="$(
    awk -F= '$1 == "flywayVersion" { sub(/^[^=]*=/, ""); print; exit }' \
      "$properties_file"
  )"
fi

if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([-.][0-9A-Za-z.-]+)?$ ]]; then
  echo "Invalid Flyway version: $version" >&2
  exit 1
fi

printf '%s\n' "$version"
