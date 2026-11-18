"""Read-only checks against the existing EC2 containers and private RDS."""
import json
import os
from pathlib import Path
import subprocess
import sys

root = Path('/opt/sauds-flight-system')
if not (root / 'runtime.env').is_file() or (root / 'runtime.env').stat().st_mode & 0o077:
    sys.exit('Missing or insufficiently protected runtime.env; deployment stopped.')
expected_host = 'sauds-flight-db.cfs2s6ywajw1.eu-north-1.rds.amazonaws.com'
settings = None
for service in ('identity', 'aviation', 'maintenance', 'notification', 'gateway', 'kafka', 'redis'):
    container = json.loads(subprocess.check_output(['docker', 'inspect', f'sauds-flight-ec2-{service}-1']))[0]
    if container['State'].get('Health', {}).get('Status') != 'healthy':
        sys.exit(f'Existing {service} container is not healthy; deployment stopped.')
    env = dict(entry.split('=', 1) for entry in container['Config']['Env'])
    if service in ('identity', 'aviation', 'maintenance'):
        url = env.get('SPRING_DATASOURCE_URL', '')
        if not url.startswith(f'jdbc:postgresql://{expected_host}:5432/aviation?') or 'sslmode=verify-full' not in url:
            sys.exit(f'Unexpected RDS target/TLS setting for {service}; deployment stopped.')
        if env.get('SPRING_JPA_HIBERNATE_DDL_AUTO') != 'validate' or env.get('APP_SEED_ENABLED') != 'false':
            sys.exit(f'Unexpected schema/seed settings for {service}; deployment stopped.')
    if service == 'identity':
        settings = env

db_env = os.environ.copy()
db_env.update(PGHOST=expected_host, PGPORT='5432', PGDATABASE='aviation',
              PGUSER=settings['SPRING_DATASOURCE_USERNAME'], PGPASSWORD=settings['SPRING_DATASOURCE_PASSWORD'],
              PGSSLMODE='verify-full', PGSSLROOTCERT=str(root / 'certs/global-bundle.pem'),
              PGCONNECT_TIMEOUT='10', PGAPPNAME='jenkins-release-readiness')
query = """BEGIN READ ONLY;
SELECT json_build_object(
 'database', current_database(),
 'tls', (SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()),
 'clearance_constraint_validated', EXISTS (
  SELECT 1 FROM pg_constraint WHERE conrelid='public.flights'::regclass
  AND conname='flight_clearance_state_check' AND convalidated));
ROLLBACK;"""
result = subprocess.run(['psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-c', query],
                        env=db_env, capture_output=True, text=True)
if result.returncode:
    sys.exit('Read-only RDS check failed; connection details suppressed.')
state = json.loads(result.stdout.strip())
if state['database'] != 'aviation' or not state['tls']:
    sys.exit('RDS database/TLS verification failed; deployment stopped.')
if not state['clearance_constraint_validated']:
    sys.exit('DEPLOYMENT BLOCKED: RDS lacks validated flight_clearance_state_check. '
             'Review and apply a separate backed-up migration before releasing. No database changes were made.')
print('Existing services healthy; expected RDS database, verified TLS and clearance constraint confirmed.')
