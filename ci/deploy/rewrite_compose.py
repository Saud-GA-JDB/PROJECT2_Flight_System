"""Replace only five application image references; retain private runtime configuration."""
import re
import sys
from pathlib import Path


def rewrite(compose, manifest):
    expected = {'aviation', 'identity', 'maintenance', 'notification', 'gateway'}
    images = {}
    for line in manifest.splitlines():
        service, image = line.split('\t')
        if service not in expected or service in images:
            raise ValueError('Unexpected or duplicate release service')
        pattern = rf'425989919753\.dkr\.ecr\.eu-north-1\.amazonaws\.com/sauds-flight-system-{service}@sha256:[0-9a-f]{{64}}'
        if not re.fullmatch(pattern, image):
            raise ValueError('Unexpected release image registry or digest')
        images[service] = image
    if images.keys() != expected:
        raise ValueError('Release must contain exactly five services')
    for service, image in images.items():
        pattern = rf'(?m)^(\s+image: )[\S]*sauds-flight-system-{service}(?::|@)[^\r\n]+$'
        compose, count = re.subn(pattern, lambda match: match[1] + image, compose)
        if count != 1:
            raise ValueError(f'Expected exactly one {service} image reference')
    return compose


if __name__ == '__main__':
    Path(sys.argv[3]).write_text(rewrite(Path(sys.argv[1]).read_text(), Path(sys.argv[2]).read_text()))
