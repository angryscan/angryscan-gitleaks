#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${ROOT_DIR}/build/out/windows-amd64"

mkdir -p "${OUT_DIR}"

echo "Building Windows (amd64) shared library..."
CGO_ENABLED=1 GOOS=windows GOARCH=amd64 \
  go build -buildmode=c-shared -o "${OUT_DIR}/libgitleaks.dll" "${ROOT_DIR}/cgo"

# Go generates libgitleaks.h next to the library output.
# We also copy our stable header for interop convenience.
cp -f "${ROOT_DIR}/cgo/cgo.h" "${OUT_DIR}/cgo.h"

echo "OK: ${OUT_DIR}"


