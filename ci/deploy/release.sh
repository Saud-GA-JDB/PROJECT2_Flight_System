#!/usr/bin/env bash
set -euo pipefail
set +x
tag=${1:?Supply an immutable image tag}
[[ "$tag" =~ ^[0-9a-f]{12}-[0-9]+$ ]] || exit 2
script_dir=$(cd "$(dirname "$0")" && pwd)
cd /opt/sauds-flight-system
exec 9>.deployment.lock
flock -n 9 || { echo 'Another release is running' >&2; exit 1; }
python3 "$script_dir/preflight.py"
registry=425989919753.dkr.ecr.eu-north-1.amazonaws.com
release_dir="$PWD/releases/$tag"
[ ! -e "$release_dir" ] || { echo 'Release directory already exists; inspect previous attempt before retrying.' >&2; exit 1; }
umask 077
mkdir -p "$release_dir/docker-auth"
cp -p compose.yml "$release_dir/compose.before.yml"
cp "$script_dir/release-images.tsv" "$release_dir/"
python3 "$script_dir/rewrite_compose.py" compose.yml "$release_dir/release-images.tsv" "$release_dir/compose.next.yml"
compose=(docker compose --env-file runtime.env -f compose.yml)
services=(identity aviation maintenance notification gateway)
switched=false
cleanup() {
  docker --config "$release_dir/docker-auth" logout "$registry" >/dev/null 2>&1 || true
  rm -f "$release_dir/docker-auth/config.json"
  rmdir "$release_dir/docker-auth" || true
}
rollback() {
  original_status=$?
  trap - ERR
  if "$switched"; then
    echo 'Release failed; restoring previous image references.' >&2
    cp -p "$release_dir/compose.before.yml" .compose.rollback.yml
    mv -f .compose.rollback.yml compose.yml
    rollback_failed=false
    for service in "${services[@]}"; do
      "${compose[@]}" up -d --no-deps --pull never --wait --wait-timeout 420 "$service" || rollback_failed=true
    done
    if "$rollback_failed"; then echo 'ROLLBACK NEEDS ATTENTION: one or more previous services did not become healthy.' >&2; fi
  fi
  exit "$original_status"
}
trap cleanup EXIT
trap rollback ERR
docker --config "$release_dir/docker-auth" login --username AWS --password-stdin "$registry"
while IFS=$'\t' read -r service image; do
  docker --config "$release_dir/docker-auth" pull "$image"
done < "$release_dir/release-images.tsv"
docker compose --env-file runtime.env -f "$release_dir/compose.next.yml" --project-directory "$PWD" config --quiet
# runtime.env, certs, uploads, Kafka data and RDS are not edited by this release.
cp "$release_dir/compose.next.yml" .compose.release.next.yml
chmod --reference=compose.yml .compose.release.next.yml
switched=true
mv -f .compose.release.next.yml compose.yml
for service in "${services[@]}"; do
  "${compose[@]}" up -d --no-deps --pull never --wait --wait-timeout 420 "$service"
done
test "$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8085/actuator/health)" = 200
test "$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8085/flights/search)" = 401
test "$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8085/auth/users/me)" = 401
test "$(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8085/internal/identity/users)" = 404
for target in identity:8081 aviation:8080 maintenance:8083 notification:8084; do
  "${compose[@]}" exec -T gateway curl --fail --silent --max-time 10 "http://$target/v3/api-docs" >/dev/null
done
printf '%s\n' "$tag" > current-release.txt
"${compose[@]}" ps
echo "Release $tag is healthy. RDS schema and application settings were unchanged."
