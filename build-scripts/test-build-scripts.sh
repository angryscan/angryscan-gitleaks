#!/usr/bin/env bash
set -euo pipefail

# Minimal "tests" for build scripts: validate argument parsing and error messaging.
# These tests intentionally avoid performing actual builds (no Go/toolchain required).

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_fails_with() {
  local expected="$1"
  shift
  local out=""
  set +e
  out="$("$@" 2>&1)"
  local rc=$?
  set -e
  if [ $rc -eq 0 ]; then
    fail "Expected command to fail, but it succeeded: $*"
  fi
  if [[ "$out" != *"$expected"* ]]; then
    echo "$out" >&2
    fail "Expected output to contain: $expected"
  fi
}

assert_fails_with "unsupported architecture" bash "${ROOT_DIR}/build-scripts/build-linux.sh" "nope"
assert_fails_with "unsupported architecture" bash "${ROOT_DIR}/build-scripts/build-windows.sh" "nope"

echo "OK: build script argument validation"

