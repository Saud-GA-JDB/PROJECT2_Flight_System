"""Build a manual presentation collection; never sends API requests."""
import json
import re
from copy import deepcopy
from pathlib import Path

root = Path(__file__).parent
items = []

def add(file, names):
    source = json.loads((root / file).read_text(encoding='utf-8'))
    for name in names:
        matches = [i for i in source['item'] if re.sub(r'^\d+ \| | \[\d+\]$', '', i['name']) == name]
        # Repeated customer logins represent different literal passwords.
        for original in matches:
            item = deepcopy(original)
            item['request'].setdefault('auth', deepcopy(source['auth']))
            item['event'] = [{'listen': 'test', 'script': {'type': 'text/javascript', 'exec': [
                line for event in original.get('event', []) if event['listen'] == 'test'
                for line in event['script']['exec'] if line.startswith('pm.test(')
            ]}}]
            item['request']['description'] = 'Send manually. Fill IDs and bearer tokens yourself. ' + re.search(r'Expected HTTP \d+\.', original['request']['description'])[0]
            items.append(item)
            if name != 'Login as customer':
                break

add('05-customer.postman_collection.json', ['Reject invalid CPR', 'Request verification email - then open inbox',
    'Verify email - enter code first', 'Login before setup using CPR', 'Reject protected access before setup',
    'Complete setup and save customer ID', 'Login as customer'])
add('06-flights.postman_collection.json', ['Reject unauthenticated search'])
add('02-airline.postman_collection.json', ['Reject airline admin using FAA route'])
items[-1]['name'] = 'Customer cannot access FAA endpoint [403]'
items[-1]['request']['auth']['bearer'][0]['value'] = '{{customerToken}}'
add('08-account.postman_collection.json', ['Update own phone', 'Reject customer updating another profile'])
items[-2]['name'] = 'Update own profile and upload picture [200]'
items[-2]['request']['body']['formdata'][1]['disabled'] = False
items[-2]['request']['description'] += ' Select your picture in Body > form-data > image before sending.'
add('01-faa.postman_collection.json', ['Login as faa', 'Create Presentation Airways', 'Reject duplicate airline', 'Assign airline administrator'])
add('02-airline.postman_collection.json', ['Login as admin', 'Reject airplane standardSeatCapacity',
    'Add airplane for approval', 'FAA lookup - save airplane IDs'])
add('09-notifications.postman_collection.json', ['Open FAA event stream - keep tab open'])
items[-1]['request']['description'] = 'Open in a separate tab and leave connected while sending the next activation request. Expect connected and then airplane-activation-requested. Reconnect after five minutes if necessary.'
add('02-airline.postman_collection.json', ['Request activation - approval example'])
add('03-faa.postman_collection.json', ['List pending requests', 'Reject denial without reason', 'Accept airplane'])
add('04-airline.postman_collection.json', ['Reject past departure', 'Create main flight - seven days ahead', 'Reject overlapping schedule'])
add('06-flights.postman_collection.json', ['Search demo airline flights', 'Reject page=0'])
add('07-bookings.postman_collection.json', ['Book standard seat 1', 'Reject already booked seat', 'View own bookings', 'Cancel main booking', 'Reject repeated cancellation'])
add('06-flights.postman_collection.json', ['Search demo airline flights'])
items[-1]['name'] = 'Search again - cancelled seat is available [200]'
add('08-account.postman_collection.json', ['Reject weak new password', 'Change demo password'])
# Use the matching literal password at each stage, without environment mutation scripts.
login_source = json.loads((root/'05-customer.postman_collection.json').read_text())
customer_login = next(i for i in login_source['item'] if ' | Login as customer [' in i['name'])
for password, label in [('DemoChanged2', 'Login with changed password')]:
    item = deepcopy(customer_login)
    item['name'] = label + ' [200]'
    item['request']['body']['raw'] = json.dumps({'email':'sauds.presentation.customer@mailsac.com','password':password}, indent=2)
    item['event'] = []
    items.append(item)
add('08-account.postman_collection.json', ['Reset demo customer password to CPR'])
item = deepcopy(items[-2])
item['name'] = 'Login after recovery using CPR [200]'
item['request']['body']['raw'] = json.dumps({'email':'sauds.presentation.customer@mailsac.com','password':'980100002'}, indent=2)
items.append(item)
add('08-account.postman_collection.json', ['Restore demo password', 'FAA deactivates demo customer - run last', 'Reject login for deactivated account'])

literals = {'departure':'2026-11-15T12:00:00', 'arrival':'2026-11-15T13:00:00', 'verificationCode':'123456'}
for index, item in enumerate(items, 1):
    item['name'] = f'{index:02d} | ' + re.sub(r'^\d+ \| ', '', item['name']).replace('seven days ahead', '15 November 2026').replace('save airplane IDs','view airplane IDs').replace('save customer ID','view customer ID')
    encoded = json.dumps(item)
    for key, value in literals.items():
        encoded = encoded.replace('{{'+key+'}}', value)
    items[index-1] = json.loads(encoded)
    if '/verification' in item['request']['url']:
        items[index-1]['request']['description'] += ' Replace sample code 123456 in the JSON body with the actual code received by email.'
    if '/login' in item['request']['url']:
        items[index-1]['request']['description'] = 'Use the literal credentials shown. Copy the returned message JWT into the matching token variable manually. ' + re.search(r'\[\d+\]', item['name'])[0]

collection = {'info': {'name': 'Requirements presentation - Manual IDs and tokens',
    'schema':'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
    'description':'Run in order on a clean demo dataset. Uses the existing demo identities recorded in DEMO-MANIFEST.json. All inputs and flight dates are literal. Set only IDs and tokens in collection Variables; select No environment to avoid old environment values overriding them. Copy user ID from setup, airline ID from creation, airplane ID from FAA airline listing, request ID from activation, flight ID from search, and booking ID from booking. Flight date is 15 November 2026; change it manually if presenting after that date. Enter the actual emailed verification code in the verification JSON and select a local picture for upload. Open SSE separately while submitting activation. No automatic token/ID capture or date generation.'},
    'item':items}
refs = sorted(set(re.findall(r'\{\{([^{}]+)\}\}', json.dumps(collection))))
assert all(key.endswith('Id') or key.endswith('Token') for key in refs), refs
collection['variable'] = [{'key':key,'value':'','type':'string'} for key in refs]
(root/'Requirements-presentation.postman_collection.json').write_text(json.dumps(collection,indent=2)+'\n', encoding='utf-8')
print(f'Created {len(items)} requests; manual variables: {", ".join(refs)}')
