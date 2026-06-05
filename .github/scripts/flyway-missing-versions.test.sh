#!/usr/bin/env bash
# Fixture-based test for flyway-missing-versions.sh. No network: METADATA_FILE,
# PROPS and WORKFLOW are injected.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

cat > "$tmp/metadata.xml" <<'XML'
<metadata>
  <versioning>
    <versions>
      <version>12.5.0</version>
      <version>12.6.0</version>
      <version>12.7.0</version>
      <version>12.8.0</version>
      <version>12.8.1</version>
      <version>12.9.0</version>
      <version>12.10.0</version>
      <version>12.10.2</version>
      <version>13.0.0</version>
      <version>13.1.0</version>
      <version>13.2.0-beta</version>
    </versions>
  </versioning>
</metadata>
XML

cat > "$tmp/gradle.properties" <<'PROPS'
flywayVersion=12.5.0
PROPS

cat > "$tmp/test.yaml" <<'YAML'
jobs:
  compatibility:
    strategy:
      matrix:
        flyway: ['12.6.0', '12.7.0', '12.8.0']
YAML

out="$(METADATA_FILE="$tmp/metadata.xml" PROPS="$tmp/gradle.properties" WORKFLOW="$tmp/test.yaml" \
  bash "$here/flyway-missing-versions.sh")"

# Latest patch per NEW minor above highest tested (12.8), incl. new major 13.x;
# 12.8.1 skipped (patch of tested minor); -beta excluded.
expected=$'12.9.0\n12.10.2\n13.0.0\n13.1.0'

if [ "$out" != "$expected" ]; then
  echo "FAIL"
  echo "--- expected ---"; printf '%s\n' "$expected"
  echo "--- got ---";      printf '%s\n' "$out"
  exit 1
fi
echo "PASS (happy path)"

# Regression: a missing workflow file must fail loudly (nonzero exit), NOT emit
# already-tested versions with exit 0.
if METADATA_FILE="$tmp/metadata.xml" PROPS="$tmp/gradle.properties" \
   WORKFLOW="$tmp/does-not-exist.yaml" \
   bash "$here/flyway-missing-versions.sh" >/dev/null 2>&1; then
  echo "FAIL: expected nonzero exit when the workflow file is missing"
  exit 1
fi
echo "PASS (missing-workflow guard)"
