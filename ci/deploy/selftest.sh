#!/usr/bin/env bash
# Run inside a disposable Python Linux container with no Docker socket or network.
set -euo pipefail
cd "$(dirname "$0")"
python3 -B -m unittest -v test_rewrite_compose.py
bash -n release.sh
test_root=$(mktemp -d)
mkdir -p "$test_root/scripts" "$test_root/production" "$test_root/bin"
cp rewrite_compose.py "$test_root/scripts/"
printf "print('Simulated read-only preflight passed')\n" > "$test_root/scripts/preflight.py"
sed "s|/opt/sauds-flight-system|$test_root/production|g" release.sh > "$test_root/scripts/release.sh"
printf 'name: sauds-flight-ec2\nservices:\n' > "$test_root/production/compose.yml"
for service in aviation identity maintenance notification gateway; do
  printf '  %s:\n    image: sauds-flight-system-%s:old\n    pull_policy: never\n' "$service" "$service" >> "$test_root/production/compose.yml"
  printf '%s\t425989919753.dkr.ecr.eu-north-1.amazonaws.com/sauds-flight-system-%s@sha256:%064d\n' "$service" "$service" 0 >> "$test_root/scripts/release-images.tsv"
done
cp "$test_root/production/compose.yml" "$test_root/original.yml"
printf 'PRIVATE_RUNTIME_SENTINEL=unchanged\n' > "$test_root/production/runtime.env"
chmod 600 "$test_root/production/runtime.env"
cat > "$test_root/bin/docker" <<'MOCK'
#!/usr/bin/env bash
echo "$*" >> "$TEST_ROOT/docker-calls.txt"
if [[ " $* " = *' login '* ]]; then cat >/dev/null; fi
if [[ " $* " = *' up '* && "${*: -1}" = maintenance && "${SIMULATE_FAILURE:-}" = yes && ! -f "$TEST_ROOT/failed-once" ]]; then
  touch "$TEST_ROOT/failed-once"
  exit 23
fi
MOCK
cat > "$test_root/bin/curl" <<'MOCK'
#!/usr/bin/env bash
case "${*: -1}" in
  */flights/search|*/auth/users/me) printf 401 ;;
  */internal/identity/users) printf 404 ;;
  */actuator/health) printf 200 ;;
  *) exit 1 ;;
esac
MOCK
chmod +x "$test_root/bin/docker" "$test_root/bin/curl"
export TEST_ROOT="$test_root" PATH="$test_root/bin:$PATH"
export SIMULATE_FAILURE=yes
set +e
printf 'synthetic-token\n' | bash "$test_root/scripts/release.sh" abcdef123456-1
failure_status=$?
set -e
test "$failure_status" = 23
cmp "$test_root/original.yml" "$test_root/production/compose.yml"
test "$(grep -c 'up .* identity$' "$test_root/docker-calls.txt")" = 2
test "$(cat "$test_root/production/runtime.env")" = 'PRIVATE_RUNTIME_SENTINEL=unchanged'
test ! -e "$test_root/production/releases/abcdef123456-1/docker-auth"
unset SIMULATE_FAILURE
printf 'synthetic-token\n' | bash "$test_root/scripts/release.sh" abcdef123456-2
test "$(grep -c '@sha256:' "$test_root/production/compose.yml")" = 5
test "$(cat "$test_root/production/current-release.txt")" = abcdef123456-2
test "$(cat "$test_root/production/runtime.env")" = 'PRIVATE_RUNTIME_SENTINEL=unchanged'
test ! -e "$test_root/production/releases/abcdef123456-2/docker-auth"
echo 'Release rewrite, failure rollback, successful release and credential cleanup simulations passed.'
