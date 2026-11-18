"""Local masked login input; send to the EC2 verification script over SSH."""
import argparse
import json
import subprocess
import tkinter as tk
from tkinter import simpledialog, messagebox


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--host', required=True)
    parser.add_argument('--key', required=True)
    args = parser.parse_args()
    root = tk.Tk()
    root.withdraw()
    root.attributes('-topmost', True)
    email = simpledialog.askstring(
        'Flight app verification: account email',
        'Enter an existing active flight application account email.\n'
        'This checks one login, your profile, and flight search on EC2.\n'
        'No email or payment will be sent.', parent=root)
    if not email:
        return 2
    password = simpledialog.askstring('Flight app verification: password',
                                    'Enter that flight application account password.\n'
                                    'This is not your RDS or AWS password.', show='*', parent=root)
    if not password:
        return 2
    result = subprocess.run(
        ['ssh', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-i', args.key,
         'ec2-user@' + args.host, 'umask 077; python3 /opt/sauds-flight-system/verify-login.py'],
        input=json.dumps({'email': email.strip(), 'password': password}).encode(),
        capture_output=True, timeout=120, creationflags=subprocess.CREATE_NO_WINDOW)
    email = password = None
    try:
        status = json.loads(result.stdout)
    except Exception:
        messagebox.showerror('Verification needs inspection',
                             'The private verification connection failed. No credentials were logged.', parent=root)
        return 1
    message = '\n'.join(f'{key}: {status.get(key)}' for key in ('login', 'profile', 'flights', 'flightSearchTotal'))
    if status.get('passed'):
        messagebox.showinfo('Backend verification passed', message, parent=root)
    else:
        messagebox.showerror('Backend verification did not pass', message + '\nNo automatic login retry was made.', parent=root)
    root.destroy()
    return result.returncode


if __name__ == '__main__':
    raise SystemExit(main())
