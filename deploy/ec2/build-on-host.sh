#!/usr/bin/env bash
set -euo pipefail
cd /opt/sauds-flight-system
tag=${1:?Usage: bash build-on-host.sh IMAGE_TAG}
if [[ ! "$tag" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]; then
  echo 'Invalid Docker image tag.' >&2
  exit 1
fi
mkdir -p "build-$tag"
tar -xzf source.tar.gz -C "build-$tag"
cd "build-$tag"
# Workstation builds are preferred; this fallback avoids a slow image upload.
# Only this extracted build copy is modified, never application source.
sed -i '/WORKDIR \/build/a ENV MAVEN_OPTS="-Xmx512m -XX:+UseSerialGC"' Dockerfile
for service in gateway identity aviation maintenance notification; do
  module="$service-service"
  if [ "$service" = aviation ]; then module=.; fi
  sudo env DOCKER_BUILDKIT=0 docker build --memory=1400m --memory-swap=2g \
    --build-arg "SERVICE=$module" -t "sauds-flight-system-$service:$tag" .
done
sudo docker pull redis:8.2-alpine
sudo docker pull apache/kafka:4.1.2
touch /opt/sauds-flight-system/build-complete
