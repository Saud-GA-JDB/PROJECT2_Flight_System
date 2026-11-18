#!/usr/bin/env bash
set -euo pipefail
cd /opt/sauds-flight-system
test -s runtime.env
test "$(stat -c %a runtime.env)" = 600
test -s certs/global-bundle.pem
compose=(sudo docker compose --env-file runtime.env -f compose.yml)
"${compose[@]}" config --quiet
# Bound startup pressure on the 2 GiB host. No database restore or schema edits.
for service in redis kafka identity aviation maintenance notification gateway; do
  "${compose[@]}" up -d --no-deps --wait --wait-timeout 420 "$service"
done
"${compose[@]}" ps
free -m
sudo docker stats --no-stream --format '{{.Name}} {{.MemUsage}} {{.CPUPerc}}'
