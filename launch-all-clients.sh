#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if ! command -v nix >/dev/null 2>&1; then
  echo "error: nix is required to run all clients" >&2
  exit 127
fi

pids=()

cleanup() {
  for pid in "${pids[@]}"; do
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
    fi
  done
}

trap cleanup INT TERM EXIT

start_client() {
  local name="$1"
  shift

  echo "Starting ${name}..."
  "$@" &
  pids+=("$!")
}

start_client "Fabric 26.1" nix run .#runFabric26_1Client
start_client "Fabric 1.21.11" nix run .#runFabric1_21_11Client
start_client "NeoForge 1.21.1" nix run .#runNeoForgeClient

status=0

for pid in "${pids[@]}"; do
  if ! wait "$pid"; then
    status=1
  fi
done

trap - INT TERM EXIT
exit "$status"
