#!/usr/bin/env bash
set -euo pipefail
cd /opt/sauds-flight-system
compose=(sudo docker compose --env-file runtime.env -f compose.yml)
"${compose[@]}" ps
test "$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8085/actuator/health)" = 200
test "$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8085/flights/search)" = 401
test "$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8085/auth/users/me)" = 401
test "$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8085/internal/identity/users)" = 404
for target in identity:8081 aviation:8080 maintenance:8083 notification:8084; do
  "${compose[@]}" exec -T gateway curl --fail --silent --max-time 10 "http://$target/v3/api-docs" >/dev/null
done
echo 'Gateway health, unauthenticated flight/profile rejection, internal-route isolation, and service HTTP checks passed.'
free -m
vmstat 1 5
sudo docker stats --no-stream --format '{{.Name}} {{.MemUsage}} {{.CPUPerc}}'
