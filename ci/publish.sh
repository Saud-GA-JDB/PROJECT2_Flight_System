#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source ci/aws.sh
verify_publisher
if [ "${1:-}" = verify ]; then exit 0; fi
validate_tag
auth_dir=$(mktemp -d)
trap 'docker --config "$auth_dir" logout "$ECR_REGISTRY" >/dev/null 2>&1 || true; rm -f "$auth_dir/config.json"; rmdir "$auth_dir"' EXIT
aws_cli ecr get-login-password | docker --config "$auth_dir" login --username AWS --password-stdin "$ECR_REGISTRY"
for service in "${services[@]}"; do
  source_image="sauds-flight-system-$service:$IMAGE_TAG"
  revision=$(docker image inspect "$source_image" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')
  [ "$revision" = "${GIT_COMMIT:?}" ] || { echo "Unexpected image revision: $service" >&2; exit 1; }
  destination="$ECR_REGISTRY/sauds-flight-system-$service:$IMAGE_TAG"
  docker tag "$source_image" "$destination"
  docker --config "$auth_dir" push "$destination"
done
write_release_manifest
printf '%s\n' "$IMAGE_TAG" > target/release-tag.txt
echo "Published all five tested images with immutable tag $IMAGE_TAG."
