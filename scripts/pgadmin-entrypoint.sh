#!/bin/sh
set -eu

python3 -c '
import json
import os
import sys

database = os.environ.get("POSTGRES_DB", "")
username = os.environ.get("POSTGRES_USER", "")
if not database.strip() or not username.strip():
    print("SubmIT pgAdmin provisioning requires nonblank POSTGRES_DB and POSTGRES_USER.", file=sys.stderr)
    raise SystemExit(2)

definition = {
    "Servers": {
        "1": {
            "Name": "SubmIT",
            "Group": "Servers",
            "Host": "database",
            "Port": 5432,
            "MaintenanceDB": database,
            "Username": username,
        }
    }
}
with open("/tmp/submit-pgadmin-servers.json", "w", encoding="utf-8") as server_file:
    json.dump(definition, server_file, ensure_ascii=True)
'
export PGADMIN_SERVER_JSON_FILE=/tmp/submit-pgadmin-servers.json

exec /entrypoint.sh "$@"
