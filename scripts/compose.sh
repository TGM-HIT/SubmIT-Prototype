#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
compose_file=$repo_root/compose.yaml

project_name=${COMPOSE_PROJECT_NAME-}
case "$project_name" in
  *[![:space:]]*) ;;
  *)
    printf '%s\n' \
      'Compose project is not configured. Set COMPOSE_PROJECT_NAME to a nonblank project name.' >&2
    exit 2
    ;;
esac

env_file=${SUBMIT_COMPOSE_ENV_FILE-"$repo_root/.env"}
case "$env_file" in
  '')
    printf '%s\n' \
      'Compose environment file is not configured. Set SUBMIT_COMPOSE_ENV_FILE to an existing file.' >&2
    exit 2
    ;;
  /*) ;;
  *) env_file=$repo_root/$env_file ;;
esac
if [ ! -f "$env_file" ]; then
  printf 'Compose environment file does not exist: %s\n' "$env_file" >&2
  exit 2
fi

for argument do
  case "$argument" in
    --project-name|--project-name=*|--env-file|--env-file=*|--file|--file=*|-p|-p?*|-f|-f?*)
      printf '%s\n' \
        'Do not override the wrapper-selected project, environment file, or Compose file.' >&2
      exit 2
      ;;
  esac
done

exec docker compose \
  --project-name "$project_name" \
  --env-file "$env_file" \
  --file "$compose_file" \
  "$@"
