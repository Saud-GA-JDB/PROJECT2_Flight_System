"""Read deployment logs with configured secret values redacted before output."""
import json
import re
import subprocess
import sys

base = ['sudo', 'docker', 'compose', '--env-file', 'runtime.env', '-f', 'compose.yml']
config = json.loads(subprocess.check_output(base + ['config', '--format', 'json']))
secrets = set()
for service in config['services'].values():
    for key, value in service.get('environment', {}).items():
        if value and re.search(r'PASSWORD|SECRET|KEY|TOKEN|MAIL_USERNAME', key):
            secrets.add(str(value))
            secrets.add(str(value).replace('$$', '$'))
for name in sys.argv[1:]:
    if name not in config['services']:
        raise SystemExit('Unknown service')
    logs = subprocess.run(base + ['logs', '--no-color', '--tail', '100', name],
                          capture_output=True, text=True)
    output = logs.stdout + logs.stderr
    for secret in sorted(secrets, key=len, reverse=True):
        output = output.replace(secret, '[REDACTED]')
    print(output)
