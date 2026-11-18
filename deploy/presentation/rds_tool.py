"""Run an explicitly selected SQL file over the existing private SSH connection.

Credentials stay on EC2. Default transactions are read-only; --write is explicit.
"""
import argparse
import base64
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('sql_file', type=Path)
parser.add_argument('--write', action='store_true')
parser.add_argument('--output', type=Path)
parser.add_argument('--host', required=True, help='EC2 hostname or IP address')
parser.add_argument('--key', required=True, help='Path to your local SSH private key')
args = parser.parse_args()
sql = args.sql_file.read_text(encoding='utf-8-sig')
if not args.write:
    sql = 'BEGIN READ ONLY;\n' + sql + '\nROLLBACK;'
encoded = base64.b64encode(sql.encode()).decode()
remote = '''import base64,json,os,subprocess
from pathlib import Path
from urllib.parse import urlsplit
root=Path('/opt/sauds-flight-system')
container=json.loads(subprocess.check_output(['sudo','docker','inspect','sauds-flight-ec2-identity-1']))[0]
settings=dict(e.split('=',1) for e in container['Config']['Env'])
database=urlsplit(settings['SPRING_DATASOURCE_URL'].removeprefix('jdbc:'))
if database.scheme != 'postgresql' or not database.hostname or not database.path.strip('/'):
    raise SystemExit('Identity must have a PostgreSQL JDBC URL with a host and database.')
env=os.environ.copy()
env.update(PGHOST=database.hostname,PGPORT=str(database.port or 5432),PGDATABASE=database.path.lstrip('/'),PGUSER=settings['SPRING_DATASOURCE_USERNAME'],PGPASSWORD=settings['SPRING_DATASOURCE_PASSWORD'],PGSSLMODE='verify-full',PGSSLROOTCERT=str(root/'certs/global-bundle.pem'),PGCONNECT_TIMEOUT='10',PGAPPNAME='presentation-data-preparation')
result=subprocess.run(['psql','-X','-q','-A','-t','-v','ON_ERROR_STOP=1'],input=base64.b64decode('ENCODED'),env=env,capture_output=True)
output=(result.stdout+result.stderr).decode()
for key,value in settings.items():
    if value and any(s in key for s in ('PASSWORD','SECRET','KEY','TOKEN')): output=output.replace(value,'[REDACTED]')
print(output,end='')
raise SystemExit(result.returncode)
'''.replace('ENCODED', encoded)
result = subprocess.run(['ssh','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','ConnectTimeout=10','-i',args.key,'ec2-user@'+args.host,'python3 -'],input=remote.encode(),capture_output=True)
if args.output:
    args.output.write_bytes(result.stdout)
else:
    print(result.stdout.decode(),end='')
print(result.stderr.decode(),end='')
raise SystemExit(result.returncode)
