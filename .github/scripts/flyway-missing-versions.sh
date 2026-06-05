#!/usr/bin/env bash
# Prints, one per line, the Flyway versions that should be added to the CI
# compatibility matrix: the latest patch of every major.minor strictly greater
# than the highest currently-tested major.minor (pinned default in
# gradle.properties UNION the version list in .github/flyway-versions.json), up
# to Flyway's latest release, including new majors. Patch releases of
# already-tested minors and pre-release versions are ignored. Prints nothing
# when up to date.
#
# Overridable inputs (for tests): METADATA_FILE, PROPS, VERSIONS_FILE.
set -euo pipefail

METADATA_URL="https://repo1.maven.org/maven2/org/flywaydb/flyway-core/maven-metadata.xml"
PROPS="${PROPS:-gradle.properties}"
VERSIONS_FILE="${VERSIONS_FILE:-.github/flyway-versions.json}"

if [ -n "${METADATA_FILE:-}" ]; then
  raw="$(cat "$METADATA_FILE")"
else
  raw="$(curl -fsSL "$METADATA_URL")"
fi

# Release versions only (X.Y.Z), ascending.
available="$(printf '%s' "$raw" \
  | grep -oE '<version>[^<]+</version>' \
  | sed -E 's:</?version>::g' \
  | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' \
  | sort -V)"

# Tested set = pinned default + the compatibility version list. Both sources must
# be readable; a missing/broken versions file is a hard error (otherwise we would
# silently propose versions that are already tested). A legitimately empty list
# yields empty output and is fine.
[ -f "$VERSIONS_FILE" ] || { echo "versions file not found: $VERSIONS_FILE" >&2; exit 1; }
pinned="$(grep -E '^flywayVersion=' "$PROPS" | head -n1 | cut -d= -f2- | tr -d '[:space:]'"'"'"')"
matrix="$(jq -r '.[]' "$VERSIONS_FILE")"
tested="$(printf '%s\n%s\n' "$pinned" "$matrix" | sed '/^$/d')"

# Tested major.minor pairs, and the highest of them.
# shellcheck disable=SC2086
tested_minors="$(printf '%s\n' $tested | awk -F. '{print $1"."$2}' | sort -u)"
highest="$(printf '%s\n' $tested_minors | sort -t. -k1,1n -k2,2n | tail -n1)"
highest_major="${highest%%.*}"
highest_minor="${highest#*.}"

# For each available major.minor strictly greater than the highest tested one,
# emit its latest patch.
# shellcheck disable=SC2086
for mm in $(printf '%s\n' "$available" | awk -F. '{print $1"."$2}' | sort -u -t. -k1,1n -k2,2n); do
  major="${mm%%.*}"; minor="${mm#*.}"
  if (( major < highest_major )); then continue; fi
  if (( major == highest_major && minor <= highest_minor )); then continue; fi
  # shellcheck disable=SC2086
  if printf '%s\n' $tested_minors | grep -qx "$mm"; then continue; fi
  printf '%s\n' "$available" | grep -E "^${major}\.${minor}\." | sort -V | tail -n1
done
