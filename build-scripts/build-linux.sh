#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARCH="${1:-amd64}" # amd64 or arm64

case "${ARCH}" in
  amd64|arm64) ;;
  *)
    echo "Error: unsupported architecture '${ARCH}'. Supported: amd64, arm64" >&2
    exit 2
    ;;
esac

OUT_DIR="${ROOT_DIR}/build/out/linux-${ARCH}"

mkdir -p "${OUT_DIR}"

echo "Building Linux (${ARCH}) shared library..."

# For cross-compiling to arm64 on x86_64 hosts, Go needs an AArch64 C compiler.
# Allow override via CC env var; otherwise pick a reasonable default for arm64.
CC_FOR_BUILD="${CC:-}"
if [ -z "${CC_FOR_BUILD}" ] && [ "${ARCH}" = "arm64" ]; then
  if command -v aarch64-linux-gnu-gcc >/dev/null 2>&1; then
    CC_FOR_BUILD="aarch64-linux-gnu-gcc"
  else
    echo "Error: aarch64-linux-gnu-gcc not found for linux/arm64 build." >&2
    echo "Install it on Ubuntu/Debian: sudo apt-get install gcc-aarch64-linux-gnu" >&2
    exit 1
  fi
fi

if [ -n "${CC_FOR_BUILD}" ]; then
  CGO_ENABLED=1 GOOS=linux GOARCH="${ARCH}" CC="${CC_FOR_BUILD}" \
    go build -buildmode=c-shared -o "${OUT_DIR}/libgitleaks.so" "${ROOT_DIR}/cgo"
else
  CGO_ENABLED=1 GOOS=linux GOARCH="${ARCH}" \
    go build -buildmode=c-shared -o "${OUT_DIR}/libgitleaks.so" "${ROOT_DIR}/cgo"
fi

cp -f "${ROOT_DIR}/cgo/cgo.h" "${OUT_DIR}/cgo.h"

echo "OK: ${OUT_DIR}"


