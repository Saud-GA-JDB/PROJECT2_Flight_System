"""EC2-only PostgreSQL client wrapper; credentials remain in process memory."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
from urllib.parse import urlsplit

parser = argparse.ArgumentParser()
parser.add_argument('operation', choices=['inspect', 'backup', 'apply'])
parser.add_argument('--sql-file')
args = parser.parse_args()
os.umask(0o077)
root = Path('/opt/sauds-flight-system')
container = json.loads(subprocess.check_output(
    ['sudo', 'docker', 'inspect', 'sauds-flight-ec2-identity-1']))[0]
settings = dict(entry.split('=', 1) for entry in container['Config']['Env'])
database = urlsplit(settings['SPRING_DATASOURCE_URL'].removeprefix('jdbc:'))
if database.scheme != 'postgresql' or not database.hostname or not database.path.strip('/'):
    raise SystemExit('Identity must have a PostgreSQL JDBC URL with a host and database.')
env = os.environ.copy()
env.update(PGHOST=database.hostname,
           PGPORT=str(database.port or 5432), PGDATABASE=database.path.lstrip('/'),
           PGUSER=settings['SPRING_DATASOURCE_USERNAME'],
           PGPASSWORD=settings['SPRING_DATASOURCE_PASSWORD'],
           PGSSLMODE='verify-full', PGSSLROOTCERT=str(root / 'certs/global-bundle.pem'),
           PGCONNECT_TIMEOUT='10', PGAPPNAME='flight-deployment-verification')
if args.operation == 'backup':
    backup = root / 'backups' / ('aviation-before-upgrade-' +
             datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '.backup')
    backup.parent.mkdir(mode=0o700, exist_ok=True)
    command = ['pg_dump', '--format=custom', '--no-owner', '--no-privileges', '--file', str(backup)]
elif args.operation == 'inspect':
    command = ['psql', '-X', '-v', 'ON_ERROR_STOP=1', '-f', str(root / 'inspect-schema.sql')]
else:
    if not args.sql_file:
        raise SystemExit('An explicitly selected SQL file is required.')
    sql = (root / args.sql_file).resolve()
    if sql.parent != root or sql.suffix != '.sql':
        raise SystemExit('SQL must be a reviewed file in the deployment directory.')
    command = ['psql', '-X', '-v', 'ON_ERROR_STOP=1', '-f', str(sql)]
result = subprocess.run(command, env=env, capture_output=True, text=True)
output = result.stdout + result.stderr
for key, value in settings.items():
    if value and any(part in key for part in ('PASSWORD', 'SECRET', 'KEY', 'TOKEN')):
        output = output.replace(value, '[REDACTED]')
print(output, end='')
if result.returncode == 0 and args.operation == 'backup':
    subprocess.run(['pg_restore', '--list', str(backup)], check=True, stdout=subprocess.DEVNULL)
    print('Backup:', backup)
    print('SHA256:', hashlib.sha256(backup.read_bytes()).hexdigest())
raise SystemExit(result.returncode)
