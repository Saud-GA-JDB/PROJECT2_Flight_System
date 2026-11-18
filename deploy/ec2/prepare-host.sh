#!/usr/bin/env bash
set -euo pipefail

# Run on the existing Amazon Linux 2023 host; creates no AWS resources.
sudo install -d -m 700 -o ec2-user -g ec2-user /opt/sauds-flight-system
cd /opt/sauds-flight-system
mkdir -p certs uploads
curl --fail --silent --show-error --location \
  https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
  -o certs/global-bundle.pem
openssl crl2pkcs7 -nocrl -certfile certs/global-bundle.pem | \
  openssl pkcs7 -print_certs -noout >/dev/null
chmod 644 certs/global-bundle.pem

# A startup buffer on the existing encrypted root disk, not extra capacity.
if ! sudo swapon --show=NAME --noheadings | grep -qx /swapfile; then
  if sudo test -e /swapfile; then
    echo 'Existing inactive /swapfile needs inspection; refusing to overwrite.' >&2
    exit 1
  fi
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
fi
if ! grep -q '^/swapfile ' /etc/fstab; then
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/90-flight-swap.conf >/dev/null
sudo sysctl -p /etc/sysctl.d/90-flight-swap.conf

# Pinned official Compose release. Verify the vendor checksum before install.
compose_version=v5.6.0
compose_asset=docker-compose-linux-x86_64
compose_tmp=$(mktemp -d)
trap 'rm -f "$compose_tmp/$compose_asset" "$compose_tmp/$compose_asset.sha256"; rmdir "$compose_tmp"' EXIT
curl -fsSL "https://github.com/docker/compose/releases/download/$compose_version/$compose_asset" -o "$compose_tmp/$compose_asset"
curl -fsSL "https://github.com/docker/compose/releases/download/$compose_version/$compose_asset.sha256" -o "$compose_tmp/$compose_asset.sha256"
(cd "$compose_tmp" && sha256sum --check "$compose_asset.sha256")
sudo install -D -m 755 "$compose_tmp/$compose_asset" /usr/local/lib/docker/cli-plugins/docker-compose
sudo docker compose version
free -m
df -h /
