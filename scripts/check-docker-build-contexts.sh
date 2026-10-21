#!/usr/bin/env bash
set -eo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
created_dirs=()
probe_files=()

cleanup() {
  local path
  local index
  for path in "${probe_files[@]}"; do
    rm -f "$path"
  done
  for ((index = ${#created_dirs[@]} - 1; index >= 0; index--)); do
    rmdir "${created_dirs[index]}" 2>/dev/null || true
  done
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if ! command -v docker >/dev/null 2>&1; then
  printf 'Docker CLI is required for this check.\n' >&2
  exit 1
fi
if ! docker version --format '{{.Server.Version}}' >/dev/null 2>&1; then
  printf 'Docker CLI cannot reach a Docker daemon; start/configure Docker and retry.\n' >&2
  exit 1
fi

ensure_generated_dir() {
  local directory=$1
  if [[ -e "$directory" ]]; then
    if [[ ! -d "$directory" ]]; then
      printf 'Expected generated-output directory, found a non-directory: %s\n' "$directory" >&2
      return 1
    fi
  else
    mkdir "$directory"
    created_dirs+=("$directory")
  fi
}

make_directory_probe() {
  local directory=$1
  local context_path=$2
  local probe
  ensure_generated_dir "$directory"
  probe=$(mktemp "$directory/.docker-context-probe.XXXXXX")
  probe_files+=("$probe")
  probe_path="$context_path/${probe##*/}"
}

require_copy_failure() {
  local context=$1
  local source_path=$2
  local label=$3
  local output
  local dockerfile
  dockerfile=$(printf 'FROM scratch\nCOPY %s /probe\n' "$source_path")

  if output=$(printf '%s\n' "$dockerfile" |
    docker build --quiet --no-cache --file - "$context" 2>&1); then
    printf 'FAIL: Docker copied excluded probe %s from context %s\n' "$source_path" "$label" >&2
    return 1
  fi
  if ! grep -Fq "$source_path" <<<"$output" ||
    ! grep -Eiq 'not found|no such file|excluded by .dockerignore' <<<"$output"; then
    printf 'FAIL: Docker build failed for an unexpected reason while checking %s:\n%s\n' \
      "$label" "$output" >&2
    return 1
  fi
  printf 'PASS: Docker cannot COPY %s from %s context\n' "$source_path" "$label"
}

require_copy_success() {
  local context=$1
  local label=$2
  local dockerfile=$3
  local output
  if ! output=$(printf '%s\n' "$dockerfile" |
    docker build --quiet --no-cache --file - "$context" 2>&1); then
    printf 'FAIL: Docker could not COPY required inputs from %s context:\n%s\n' \
      "$label" "$output" >&2
    return 1
  fi
  printf 'PASS: Docker can COPY representative required inputs from %s context\n' "$label"
}

frontend_context="$repo_root/frontend"
make_directory_probe "$frontend_context/node_modules" node_modules
frontend_node_modules=$probe_path
make_directory_probe "$frontend_context/dist" dist
frontend_dist=$probe_path

make_directory_probe "$repo_root/backend/build" backend/build
backend_build=$probe_path
make_directory_probe "$repo_root/backend/.gradle" backend/.gradle
backend_gradle=$probe_path

openapi_path="$repo_root/contracts/openapi.yaml"
if [[ -e "$openapi_path" || -L "$openapi_path" ]]; then
  printf 'Using existing contracts/openapi.yaml as the generated-artifact probe.\n'
else
  if ! (set -o noclobber; printf 'temporary Docker context probe\n' >"$openapi_path"); then
    printf 'Could not create disposable contracts/openapi.yaml probe without overwriting existing data.\n' >&2
    exit 1
  fi
  probe_files+=("$openapi_path")
fi

require_copy_failure "$frontend_context" "$frontend_node_modules" frontend
require_copy_failure "$frontend_context" "$frontend_dist" frontend
require_copy_success "$frontend_context" frontend $'FROM scratch\nCOPY package.json /required/package.json\nCOPY package-lock.json /required/package-lock.json\nCOPY src/App.vue /required/App.vue\nCOPY nginx.conf /required/nginx.conf'

require_copy_failure "$repo_root" "$backend_build" backend
require_copy_failure "$repo_root" "$backend_gradle" backend
require_copy_failure "$repo_root" contracts/openapi.yaml backend
require_copy_success "$repo_root" backend $'FROM scratch\nCOPY backend/build.gradle /required/build.gradle\nCOPY backend/gradlew /required/gradlew\nCOPY checkstyle.xml /required/checkstyle.xml\nCOPY backend/src /required/src'

printf 'All Docker build-context inclusion/exclusion checks passed.\n'
