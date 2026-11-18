"""Prompt locally for the RDS password and send runtime settings over SSH.

No secret is printed, added to argv, or written to a new local plaintext file.
The existing ignored root .env supplies application integration credentials.
"""
import argparse
import json
import re
from pathlib import Path
import subprocess
import tkinter as tk
from tkinter import messagebox, simpledialog


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--host', required=True)
    parser.add_argument('--key', required=True)
    parser.add_argument('--image-tag', required=True)
    parser.add_argument('--db-host', required=True, help='RDS DNS hostname (no URL or port)')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9.-]*', args.db_host):
        parser.error('--db-host must be a DNS hostname')
    if not re.fullmatch(r'[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}', args.image_tag):
        parser.error('--image-tag must be a valid Docker image tag')
    root = tk.Tk()
    root.withdraw()
    root.attributes('-topmost', True)
    password = simpledialog.askstring(
        'Sauds Flight System: private RDS password',
        'Enter the database password for the selected RDS host.\n'
        'It will be sent through SSH to the EC2 private runtime.env.\n'
        'It will not appear in chat, logs, or command arguments.',
        show='*', parent=root)
    if not password:
        root.destroy()
        return 2
    if '\n' in password or '\r' in password:
        messagebox.showerror('Configuration not saved', 'A single-line password is required.', parent=root)
        return 2
    repo = Path(__file__).resolve().parents[2]
    allowed = {'FRONTEND_ORIGINS', 'IDENTITY_SERVICE_KEY', 'NOTIFICATION_SERVICE_KEY',
               'MAINTENANCE_NOTIFICATION_SERVICE_KEY', 'JWT_SECRET', 'JWT_EXPIRATION_MS',
               'STRIPE_SECRET_KEY', 'STRIPE_SUCCESS_URL', 'STRIPE_CANCEL_URL',
               'SPRING_MAIL_HOST', 'SPRING_MAIL_PORT', 'SPRING_MAIL_USERNAME', 'SPRING_MAIL_PASSWORD'}
    lines = [line for line in (repo / '.env').read_text(encoding='utf-8-sig').splitlines()
             if line.partition('=')[0] in allowed]
    escaped = json.dumps(password.replace('$', '$$'), ensure_ascii=False)
    lines += [f'IMAGE_TAG={args.image_tag}', f'RDS_HOST={args.db_host}', 'APP_SCHEDULING_ENABLED=false',
              'SPRING_DATASOURCE_USERNAME=postgres', f'SPRING_DATASOURCE_PASSWORD={escaped}']
    command = ('set -eu; cd /opt/sauds-flight-system; umask 077; '
               'set -C; cat > runtime.env; chmod 600 runtime.env; '
               'sudo docker compose --env-file runtime.env -f compose.yml config --quiet')
    result = subprocess.run(
        ['ssh', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes',
         '-i', args.key, 'ec2-user@' + args.host, command],
        input=('\n'.join(lines) + '\n').encode(), stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL, creationflags=subprocess.CREATE_NO_WINDOW)
    password = escaped = lines = None
    if result.returncode:
        messagebox.showerror('Configuration needs inspection',
                             'The upload or validation failed. No secrets were logged.\n'
                             'An existing runtime.env is never overwritten.', parent=root)
    else:
        messagebox.showinfo('Configuration saved',
                            'Private runtime configuration saved on EC2 and Compose validation passed.', parent=root)
    root.destroy()
    return result.returncode


if __name__ == '__main__':
    raise SystemExit(main())
