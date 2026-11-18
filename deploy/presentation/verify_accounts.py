"""Login once per presentation account; verify role and GET-only workspace APIs.

Credentials travel only in SSH stdin, never command arguments or logs.
"""
import argparse
import base64
import json
from pathlib import Path
import subprocess

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--host', required=True, help='EC2 hostname or IP address')
parser.add_argument('--key', required=True, help='Path to your local SSH private key')
args=parser.parse_args()
root=Path(__file__).resolve().parent
manifest=json.loads((root/'accounts.private.json').read_text())
payload=base64.b64encode(json.dumps({'password':manifest['password'],'accounts':manifest['accounts'],'primaryFlights':manifest['primaryFlights']}).encode()).decode()
remote=r'''
import base64,json,sys,urllib.request,urllib.error
data=json.loads(base64.b64decode('PAYLOAD'))
def call(path,token=None,body=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    req=urllib.request.Request('http://127.0.0.1:8085'+path,headers=headers,data=json.dumps(body).encode() if body else None)
    with urllib.request.urlopen(req,timeout=30) as response:
        raw=response.read().decode()
        try: return json.loads(raw)
        except ValueError: return raw
results=[]; tokens={}; checks=[]
for a in data['accounts']:
    result={'id':a['id'],'role':a['role'],'employeeRole':a['subrole']}
    try:
        token=call('/auth/users/login',body={'email':a['email'],'password':data['password']})['message']
        me=call('/auth/users/me',token)
        assert me['userId']==a['id'] and me['role']==a['role'] and me['status']=='ACTIVE' and me['active']
        assert me['fName']+' '+me['lName']==a['name']
        if a['subrole']:
            employee=call('/maintenanceEmployees/me' if a['role']=='MAINTENANCE_EMPLOYEE' else '/employees/me',token)
            assert employee['employeeRole']==a['subrole']
        tokens[a['email'].split('@')[0]]=token
        result['passed']=True
    except urllib.error.HTTPError as e: result.update(passed=False,error='HTTP '+str(e.code))
    except Exception as e: result.update(passed=False,error=type(e).__name__)
    results.append(result)
    print(json.dumps({'account':result}),flush=True)

paths={
'ahmed.almansoori':['/flights/search?size=20','/bookings','/flights/910016','/bookings/910001','/payments/bookings/910001'],
'fatima.alsayed':['/faaadmin/airplaneRequests','/faaadmin/airlines','/airports','/faaadmin/airNavigationProviders?page=0&size=20'],
'hassan.mahmood':['/airlineAdmin/airplanes','/airlineAdmin/employees','/bookings','/airlineAdmin/flights?fromDate=2026-11-19&toDate=2026-11-19&page=0&size=20','/airlineAdmin/flights?status=PENDING_CREW','/airlineAdmin/dashboard?fromDate=2026-11-01&toDate=2026-11-30','/airlineAdmin/maintenanceProviders','/airlineAdmin/contracts','/airlineAdmin/flights/910007/operations'],
'noor.khalid':['/airlineAdmin/airplanes','/airlineAdmin/employees','/airlineAdmin/flights?page=0&size=20','/airlineAdmin/dashboard?fromDate=2026-11-01&toDate=2026-11-30'],
'mohammed.rashid':['/airportAdmin/inspections','/airportAdmin/employees','/airportAdmin/flights?fromDate=2026-11-19&toDate=2026-11-19'],
'zainab.ali':['/airportInspector/inspections'],
'ibrahim.hassan':['/maintenanceAdmin/maintenanceRequests','/maintenanceAdmin/technicians','/maintenanceAdmin/contracts'],
'khalid.salman':['/maintenanceTechnician/maintenanceRecords'],
'yusuf.abdulla':['/pilot/flights?size=100','/pilot/flights/910007','/pilot/flights/910007/readiness','/pilot/flights/910010/readiness','/pilot/flights/910001/controlEvents'],
'abdulla.khalifa':['/atc/facility','/atc/flights?size=100','/atc/requests','/atc/flights/910007/readiness?operation=DEPARTURE','/atc/flights/910007/controlEvents'],
'abdulrahman.yousif':['/atcAdmin/controllers?page=0&size=20','/atc/facility','/atc/requests']
}
for name,endpoints in paths.items():
    for path in endpoints:
        result={'account':name,'path':path}
        try:
            body=call(path,tokens[name]); result['passed']=True
            if isinstance(body,list): result['items']=len(body)
            if isinstance(body,dict):
                for key in ['totalElements','totalPages','requestAllowed','approvalAllowed','actionAllowed','status']:
                    if key in body: result[key]=body[key]
                for key,value in body.items():
                    if isinstance(value,list): result[key+'Count']=len(value)
                if 'readiness' in path: result['readiness']=body
        except urllib.error.HTTPError as e: result.update(passed=False,error='HTTP '+str(e.code))
        except Exception as e: result.update(passed=False,error=type(e).__name__)
        checks.append(result); print(json.dumps({'workspace':result}),flush=True)
summary={'accounts':len(results),'accountFailures':sum(not r['passed'] for r in results),'workspaceChecks':len(checks),'workspaceFailures':sum(not r['passed'] for r in checks)}
print(json.dumps({'summary':summary}),flush=True)
sys.exit(1 if summary['accountFailures'] or summary['workspaceFailures'] else 0)
'''.replace('PAYLOAD',payload)
process=subprocess.Popen(['ssh','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','ConnectTimeout=10','-i',args.key,'ec2-user@'+args.host,'python3 -'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
process.stdin.write(remote.encode()); process.stdin.close()
lines=[]
for line in process.stdout:
    decoded=line.decode(); lines.append(decoded); print(decoded,end='',flush=True)
errors=process.stderr.read().decode()
if errors: print(errors)
code=process.wait()
(root/'verification.jsonl').write_text(''.join(lines))
raise SystemExit(code)
