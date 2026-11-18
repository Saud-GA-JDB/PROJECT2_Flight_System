#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source ci/aws.sh
validate_tag
verify_publisher
write_release_manifest
[ "${DEPLOY_SSH_USER:?}" = ec2-user ] || { echo 'Unexpected deployment user' >&2; exit 1; }
remote=ec2-user@13.53.192.65
ssh_options=(-i "${DEPLOY_SSH_KEY:?}" -o BatchMode=yes -o IdentitiesOnly=yes
  -o ConnectTimeout=15 -o StrictHostKeyChecking=yes -o "UserKnownHostsFile=$PWD/ci/deploy/known_hosts")
# Execute read-only checks before sending registry credentials or deployment files.
ssh "${ssh_options[@]}" "$remote" 'sudo python3 -' < ci/deploy/preflight.py
if [ "${1:-}" = check ]; then
  echo "Release $IMAGE_TAG passed registry, SSH and RDS readiness checks; no deployment performed."
  exit 0
fi
[ "${1:-}" = release ] || { echo 'Usage: ci/deploy.sh check|release' >&2; exit 2; }
remote_dir="/home/ec2-user/flight-ci-release/$IMAGE_TAG"
ssh "${ssh_options[@]}" "$remote" "umask 077; mkdir -p '$remote_dir'"
scp "${ssh_options[@]}" ci/deploy/preflight.py ci/deploy/rewrite_compose.py ci/deploy/release.sh \
  target/release-images.tsv "$remote:$remote_dir/"
aws_cli ecr get-login-password | ssh "${ssh_options[@]}" "$remote" "sudo bash '$remote_dir/release.sh' '$IMAGE_TAG'"
