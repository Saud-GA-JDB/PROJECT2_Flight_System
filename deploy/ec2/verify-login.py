"""Run on EC2. Receive one login via stdin; report only non-sensitive results."""
import json
from pathlib import Path
import sys
import urllib.error
import urllib.request


def main():
    credentials = json.load(sys.stdin)
    result = {'login': 'not checked', 'profile': 'not checked', 'flights': 'not checked'}
    stage = 'login'
    try:
        request = urllib.request.Request(
            'http://127.0.0.1:8085/auth/users/login',
            data=json.dumps(credentials).encode(),
            headers={'Content-Type': 'application/json'}, method='POST')
        with urllib.request.urlopen(request, timeout=30) as response:
            token = json.load(response)['message']
            result['login'] = response.status
        credentials = None
        for stage, path in [('profile', '/auth/users/me'), ('flights', '/flights/search?size=10')]:
            request = urllib.request.Request(
                'http://127.0.0.1:8085' + path,
                headers={'Authorization': 'Bearer ' + token})
            with urllib.request.urlopen(request, timeout=30) as response:
                body = json.load(response)
                result[stage] = response.status
                if stage == 'flights':
                    result['flightSearchTotal'] = body.get('totalElements')
        result['passed'] = True
    except urllib.error.HTTPError as error:
        result[stage] = error.code
        result['passed'] = False
    except Exception:
        result[stage] = 'request or response failure'
        result['passed'] = False
    Path('/opt/sauds-flight-system/login-verification.json').write_text(json.dumps(result) + '\n')
    print(json.dumps(result))
    return 0 if result['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
