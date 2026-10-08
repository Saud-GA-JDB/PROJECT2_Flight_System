"""Build the presentation artifacts without contacting or changing the application."""
import json
import re
from copy import deepcopy
from pathlib import Path

OUT = Path(__file__).parent
env = dict(faaEmail='flighttest.faa@mailsac.com', faaPassword='TestPassword1',
    adminEmail='sauds.presentation.admin@mailsac.com', adminCpr='980100001', adminPassword='980100001',
    customerEmail='sauds.presentation.customer@mailsac.com', customerCpr='980100002', customerPassword='DemoPassword1',
    airlineName='Presentation Airways', airlineCode='PX', approvedRegistration='A9C-DEMO01', deniedRegistration='A9C-DEMO02',
    verificationCode='', faaToken='', adminToken='', customerToken='', airlineId='', airplaneId='', deniedAirplaneId='',
    requestId='', deniedRequestId='', flightId='', flightNumber='', customerId='', bookingId='', nearBookingId='', nearFlightId='',
    nearFlightNumber='', departure='', arrival='', nearDeparture='', nearArrival='', otherUserId='15', foreignAirplaneId='900003')
collections=[]
def fill_example_values(value):
    """Show real demo inputs; retain only values created by earlier requests."""
    if isinstance(value, str):
        return re.sub(r'\{\{([^{}]+)\}\}', lambda match: str(env[match[1]]) if env.get(match[1]) else match[0], value)
    if isinstance(value, dict):
        return {key: fill_example_values(item) for key, item in value.items()}
    if isinstance(value, list):
        return [fill_example_values(item) for item in value]
    return value

def collection(name, role):
    c={'info':{'name':name,'schema':'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
       'description':'Run requests top to bottom with the Presentation environment selected. Expected outcomes are source-derived; read PRESENTATION.md. State-changing examples create only identified demo records.'},
       'auth':{'type':'bearer','bearer':[{'key':'token','value':'{{'+role+'Token}}','type':'string'}]},'item':[]}
    collections.append(c)
    return c
def req(c,name,method,path,status=200,body=None,why='',message=None,role=None,save=None,script='',pre='',form=False,multipart=False):
    path = fill_example_values(path)
    body = fill_example_values(body)
    r={'method':method,'header':[],'url':'http://localhost:8080'+path,'description':why+'\nExpected HTTP '+str(status)+'.'}
    if role is not None:
        r['auth']={'type':'noauth'} if role=='none' else {'type':'bearer','bearer':[{'key':'token','value':'{{'+role+'Token}}','type':'string'}]}
    if body is not None:
        if multipart:
            r['body']={'mode':'formdata','formdata':[{'key':'request','value':json.dumps(body,indent=2),'type':'text','contentType':'application/json'}, {'key':'image','type':'file','src':[], 'disabled':True}]}
        elif form:
            r['body']={'mode':'urlencoded','urlencoded':[{'key':k,'value':str(v),'type':'text'} for k,v in body.items()]}
        else:
            r['header']=[{'key':'Content-Type','value':'application/json'}]
            r['body']={'mode':'raw','raw':json.dumps(body,indent=2),'options':{'raw':{'language':'json'}}}
    lines=[f'pm.test("HTTP {status}", () => pm.response.to.have.status({status}));']
    if message:
        lines.append('pm.test("Validation reason", () => pm.expect(pm.response.text()).to.include('+json.dumps(message)+'));')
    if save:
        for key,field in save.items():
            lines.append(f'if (pm.response.code === {status}) {{ const value = pm.response.json().{field}; pm.test("Save {key}", () => pm.expect(value).to.not.equal(undefined)); if (value !== undefined) pm.environment.set("{key}", value); }}')
    if script: lines.append(f'if (pm.response.code === {status}) {{\n{script}\n}}')
    item={'name':f'{len(c["item"])+1:02d} | {name} [{status}]','request':r,'event':[{'listen':'test','script':{'type':'text/javascript','exec':lines}}]}
    if pre: item['event'].insert(0,{'listen':'prerequest','script':{'type':'text/javascript','exec':pre.splitlines()}})
    c['item'].append(item)
    return item
def login(c,role,password=None,name=None):
    return req(c,name or 'Login as '+role,'POST','/auth/users/login',body={'email':'{{'+role+'Email}}','password':password or '{{'+role+'Password}}'},role='none',save={role+'Token':'message'},why='Authentication checks credentials and account state. JWT is returned in message and stored in the shared environment.')
airline={'name':'{{airlineName}}','airlineCode':'{{airlineCode}}','headquartersCountry':'Bahrain'}
admin={'emailAddress':'{{adminEmail}}','cpr':'{{adminCpr}}','fName':'Demo','lName':'Admin','phoneNumberOpeningCode':'+973','phoneNumber':'36002001','hireDate':'2026-01-01','salary':1500,'securityQuestion':'Birth city?','securityQuestionAnswer':'manama'}
plane={'registrationNumber':'{{approvedRegistration}}','model':'Airbus A320-200','standardSeatCapacity':10,'firstClassSeatCapacity':2,'maxMileage':6000}
flight={'scheduledDeparture':'{{departure}}','scheduledArrival':'{{arrival}}','originAirportIataCode':'BAH','arrivalAirportIataCode':'DXB'}
setup={'password':'{{customerPassword}}','fName':'Demo','lName':'Customer','phoneNumber':'36002002','phoneNumberOpeningCode':'+973','securityQuestion':'Birth city?','securityQuestionAnswer':'manama'}

c=collection('01 - FAA - Airline and administrator','faa'); login(c,'faa')
req(c,'Reject missing airline name','POST','/faaadmin/airlines',400,{**airline,'name':''},form=True,message='required fields',why='Service rejects null or blank name, code or country before saving.')
req(c,'Create Presentation Airways','POST','/faaadmin/airlines',body=airline,form=True,save={'airlineId':'id'},why='Controller binds form parameters, not a JSON body. Airline name and code must each be unique.')
req(c,'Reject duplicate airline','POST','/faaadmin/airlines',409,airline,form=True,message='Already Exists')
req(c,'List airlines','GET','/faaadmin/airlines',why='FAA role required. Includes airplane IDs for later steps.')
for key,value,msg in [('cpr','12','9 digits'),('emailAddress','bad','Email Format'),('fName','','required'),('phoneNumber','1','Invalid phone'),('salary',0,'greater than zero'),('hireDate','not-a-date','yyyy-MM-dd'),('securityQuestionAnswer','','required')]:
    req(c,'Reject admin '+key,'POST','/faaadmin/airlines/{{airlineId}}/addAirlineAdmin',400,{**admin,key:value},message=msg,why='Only one field is invalid; other fields match the valid administrator request.')
req(c,'Assign airline administrator','POST','/faaadmin/airlines/{{airlineId}}/addAirlineAdmin',201,admin,why='Creates an active airline ADMIN with CPR as initial password. Validates CPR, email, names, phone via libphonenumber, salary > 0, ISO hire date, security question and answer, unique email/CPR, and existing airline.')
req(c,'Reject duplicate administrator','POST','/faaadmin/airlines/{{airlineId}}/addAirlineAdmin',409,admin,message='already exists')

c=collection('02 - Airline - Fleet and activation requests','admin'); login(c,'admin')
req(c,'Reject airline admin using FAA route','GET','/faaadmin/airlines',403,message='not allowed')
for key,value in [('registrationNumber',''),('model',''),('standardSeatCapacity',0),('firstClassSeatCapacity',0),('maxMileage',0)]:
    req(c,'Reject airplane '+key,'POST','/airlineAdmin/airplanes',422,{**plane,key:value},message='Failed',why='Both seat classes and mileage must be positive; model and registration cannot be blank.')
req(c,'Add airplane for approval','POST','/airlineAdmin/airplanes',201,plane,why='New airplane belongs to the logged-in admin airline and starts GROUNDED. Sends email.')
req(c,'Reject duplicate registration','POST','/airlineAdmin/airplanes',409,plane,message='already exists')
req(c,'Add airplane for denial','POST','/airlineAdmin/airplanes',201,{**plane,'registrationNumber':'{{deniedRegistration}}'})
req(c,'FAA lookup - save airplane IDs','GET','/faaadmin/airlines',role='faa',script='''const airline = pm.response.json().find(a => String(a.id) === String(pm.environment.get('airlineId')));
for (const [registration, key] of [['approvedRegistration','airplaneId'],['deniedRegistration','deniedAirplaneId']]) {
 const plane = airline && airline.airplanesList.find(p => p.registrationNumber === pm.environment.get(registration));
 pm.test('Find '+key, () => pm.expect(plane).to.be.an('object'));
 if (plane) pm.environment.set(key, plane.id);
}''',why='Creation response omits airplane ID. FAA airline listing exposes airplanesList with IDs; resolve by exact demo registration.')
dates="""if (!pm.environment.get('departure')) {
 const d = new Date(); d.setUTCDate(d.getUTCDate()+7); d.setUTCHours(12,0,0,0);
 pm.environment.set('departure',d.toISOString().slice(0,19));
 pm.environment.set('arrival',new Date(d.getTime()+3600000).toISOString().slice(0,19));
 const n = new Date(); n.setUTCDate(n.getUTCDate()+1); n.setUTCHours(12,0,0,0);
 pm.environment.set('nearDeparture',n.toISOString().slice(0,19));
 pm.environment.set('nearArrival',new Date(n.getTime()+3600000).toISOString().slice(0,19));
}"""
req(c,'Reject flight on grounded airplane','POST','/airlineAdmin/airplanes/{{airplaneId}}/addFlight',400,flight,message='grounded',pre=dates)
req(c,'Reject activation of another airline airplane','POST','/airlineAdmin/airplanes/{{foreignAirplaneId}}/requestActivation',400,message='owned by the airline')
req(c,'Request activation - approval example','POST','/airlineAdmin/airplanes/{{airplaneId}}/requestActivation',201,save={'requestId':'requestId'})
req(c,'Reject duplicate pending request','POST','/airlineAdmin/airplanes/{{airplaneId}}/requestActivation',400,message='pending activation request')
req(c,'Request activation - denial example','POST','/airlineAdmin/airplanes/{{deniedAirplaneId}}/requestActivation',201,save={'deniedRequestId':'requestId'})

c=collection('03 - FAA - Approve and deny airplanes','faa'); login(c,'faa')
req(c,'List pending requests','GET','/faaadmin/airplaneRequests')
review='/faaadmin/airplaneRequests/{{requestId}}'
req(c,'Reject PENDING as review decision','PUT',review,400,{'status':'PENDING'},message='ACCEPTED or DENIED')
req(c,'Reject denial without reason','PUT',review,400,{'status':'DENIED','reviewReason':''},message='reason is required')
req(c,'Reject reason over 500 characters','PUT',review,400,{'status':'ACCEPTED','reviewReason':'x'*501},message='500 characters')
req(c,'Accept airplane','PUT',review,body={'status':'ACCEPTED','reviewReason':'Presentation safety checks passed.'},why='Marks request ACCEPTED and airplane ACTIVE, records reviewer/time, and sends email.')
req(c,'Reject reviewing twice','PUT',review,400,{'status':'ACCEPTED'},message='already been reviewed')
req(c,'Deny second airplane','PUT','/faaadmin/airplaneRequests/{{deniedRequestId}}',body={'status':'DENIED','reviewReason':'Presentation example: maintenance documents missing.'},why='Denied airplane remains GROUNDED.')
req(c,'Reject activating active airplane','POST','/airlineAdmin/airplanes/{{airplaneId}}/requestActivation',400,role='admin',message='already active')
req(c,'Reject resubmission within seven days','POST','/airlineAdmin/airplanes/{{deniedAirplaneId}}/requestActivation',400,role='admin',message='wait 7 days')

c=collection('04 - Airline - Schedule flights','admin'); login(c,'admin')
for title,changes,msg in [('missing airport',{'originAirportIataCode':''},'required fields'),('past departure',{'scheduledDeparture':'2020-01-01T12:00:00'},'future'),('unknown airport',{'arrivalAirportIataCode':'ZZZ'},'valid arrival airport'),('arrival before departure',{'scheduledArrival':'2020-01-01T12:00:00'},'before Departure'),('equal times',{'scheduledArrival':'{{departure}}'},'same as Departure'),('same airports',{'arrivalAirportIataCode':'BAH'},"can't be the same")]:
    req(c,'Reject '+title,'POST','/airlineAdmin/airplanes/{{airplaneId}}/addFlight',400,{**flight,**changes},message=msg)
req(c,'Reject another airline airplane','POST','/airlineAdmin/airplanes/{{foreignAirplaneId}}/addFlight',400,flight,message='owned by the airline')
req(c,'Create main flight - seven days ahead','POST','/airlineAdmin/airplanes/{{airplaneId}}/addFlight',201,flight,save={'flightNumber':'flightNumber'})
req(c,'Reject overlapping schedule','POST','/airlineAdmin/airplanes/{{airplaneId}}/addFlight',400,flight,message='at least 2 hours',why='Non-cancelled flights need a two-hour gap before or after another flight.')
req(c,'Create flight for 48-hour cancellation example','POST','/airlineAdmin/airplanes/{{airplaneId}}/addFlight',201,{**flight,'scheduledDeparture':'{{nearDeparture}}','scheduledArrival':'{{nearArrival}}'},save={'nearFlightNumber':'flightNumber'})
req(c,'Find demo flights and save IDs','GET','/flights/search?airlineCode={{airlineCode}}&size=100',script="""for (const [number,key] of [['flightNumber','flightId'],['nearFlightNumber','nearFlightId']]) {
 const f=pm.response.json().content.find(x=>x.flightNumber===pm.environment.get(number));
 pm.test('Find '+key,()=>pm.expect(f).to.be.an('object')); if(f) pm.environment.set(key,f.id);
}""")

c=collection('05 - Customer - Registration and setup','customer')
registration={'email':'{{customerEmail}}','cpr':'{{customerCpr}}'}
req(c,'Reject invalid email','POST','/auth/users/register/email',400,{**registration,'email':'bad'},role='none',message='Email Format')
req(c,'Reject invalid CPR','POST','/auth/users/register/email',400,{**registration,'cpr':'123'},role='none',message='9 digits')
req(c,'Request verification email - then open inbox','POST','/auth/users/register/email',body=registration,role='none',why='Code expires in ten minutes. Copy it from the demo customer inbox into environment verificationCode. Pause here; do not blindly run the whole collection.')
req(c,'Reject duplicate pending registration','POST','/auth/users/register/email',400,registration,role='none',message='already pending')
req(c,'Reject one wrong verification code','POST','/auth/users/verification',400,{'email':'{{customerEmail}}','code':'000000'},role='none',message='Incorrect verification code',why='Run once only. Three failures exhaust verification attempts; 000000 cannot be generated by this server.')
req(c,'Verify email - enter code first','POST','/auth/users/verification',body={'email':'{{customerEmail}}','code':'{{verificationCode}}'},role='none',pre="if (!pm.environment.get('verificationCode')) throw new Error('Enter the email verificationCode in the selected environment first.');")
login(c,'customer','{{customerCpr}}','Login before setup using CPR')
req(c,'Reject protected access before setup','GET','/flights/search',403,why='SecurityConfiguration requires ACCOUNT_ACTIVE; verified account still has SETUP_REQUIRED status.')
for key,val,msg in [('password','weak','Invalid Password'),('fName','','required'),('phoneNumber','1','Invalid phone'),('securityQuestionAnswer','','required')]:
    req(c,'Reject setup '+key,'POST','/auth/users/setup',400,{**setup,key:val},message=msg)
req(c,'Complete setup and save customer ID','POST','/auth/users/setup',body=setup,save={'customerId':'id'},why='Activates account, hashes password and security answer, saves customer profile and removes pending registration.')
login(c,'customer')
req(c,'Reject repeating setup','POST','/auth/users/setup',400,setup,message='does not require setup')
req(c,'Reject registered email and CPR','POST','/auth/users/register/email',409,registration,role='none',message='already exists')

c=collection('06 - Flights - Search and validation','customer'); login(c,'customer')
req(c,'Search demo airline flights','GET','/flights/search?airlineCode={{airlineCode}}&originAirport=BAH&destinationAirport=DXB&seatType=standard&page=1&size=10&sort=scheduledDeparture,asc',why='Returns active flights on active airplanes with seats and departure over five minutes away. Other optional filters: date (yyyy-MM-dd), originCity, destinationCity, originCountry, destinationCountry.')
for key,value,msg in [('date','invalid','valid date'),('date','2020-01-01','future date'),('seatType','economy','firstClass or standard'),('page','abc','whole numbers'),('page','0','at least 1'),('size','101','between 1 and 100'),('sort','price,asc','sort by'),('sort','flightNumber,up','asc or desc'),('sort','flightNumber','sort=field')]:
    req(c,'Reject '+key+'='+value,'GET','/flights/search?'+key+'='+value,400,message=msg)
req(c,'Reject unauthenticated search','GET','/flights/search',403,role='none',why='Search is protected in the current security configuration.')

c=collection('07 - Bookings - Reserve view and cancel','customer'); login(c,'customer')
book='/customer/{{customerId}}/flights/{{flightId}}/'
req(c,'Reject invalid seat class','POST',book+'economy/1/book',400,message='firstClass or standard')
req(c,'Reject seat outside capacity','POST',book+'standard/11/book',400,message='valid seat number')
req(c,'Reject customer booking for someone else','POST','/customer/{{otherUserId}}/flights/{{flightId}}/standard/2/book',403,message='only airline admin')
req(c,'Book standard seat 1','POST',book+'standard/1/book',201,save={'bookingId':'id'},why='Uses path parameters, no JSON. Checks user/flight existence, flight state, five-minute cutoff, class, seat bounds/capacity/occupancy, airplane state, ownership and role. Sends email and decrements available seats.')
req(c,'Reject already booked seat','POST',book+'standard/1/book',400,message='already booked')
req(c,'View own bookings','GET','/bookings')
req(c,'Reject customer using airline booking search','GET','/bookings/search',403,message='not allowed')
req(c,'Airline admin searches bookings','GET','/bookings/search?userId={{customerId}}&flightId={{flightId}}&status=BOOKED',role='admin',why='Results are restricted to the logged-in administrator airline.')
req(c,'Cancel main booking','DELETE','/bookings/{{bookingId}}',why='Customer is more than 48 hours from departure. Sets CANCELLED, restores seat, sends email.')
req(c,'Reject repeated cancellation','DELETE','/bookings/{{bookingId}}',400,message='only cancel active bookings')
req(c,'Book flight leaving tomorrow','POST','/customer/{{customerId}}/flights/{{nearFlightId}}/standard/1/book',201,save={'nearBookingId':'id'})
req(c,'Reject customer cancellation within 48 hours','DELETE','/bookings/{{nearBookingId}}',417,message='48 hours')
req(c,'Airline admin cancels within 48 hours','DELETE','/bookings/{{nearBookingId}}',role='admin',why='Owning airline administrator can cancel for customers without the customer 48-hour limit.')

c=collection('08 - Account - Profile passwords and deactivation','customer'); login(c,'customer')
for title,body,status,msg in [('invalid phone',{'phoneNumber':'1'},400,'Invalid phone'),('unpaired security question',{'securityQuestion':'City?'},400,'required together'),('editing protected name',{'fName':'Other'},403,'Only FAA admins')]:
    req(c,'Reject '+title,'PUT','/auth/users/updateProfile',status,body,multipart=True,message=msg)
req(c,'Update own phone','PUT','/auth/users/updateProfile',body={'phoneNumber':'36002003','phoneNumberOpeningCode':'+973'},multipart=True,why='Multipart request part has application/json content type. Optional image is disabled; choose a local file to demonstrate uploads separately.')
req(c,'Reject customer updating another profile','PUT','/auth/users/updateProfile?userId={{otherUserId}}',403,{'phoneNumber':'36002003'},multipart=True,message='Only FAA admins')
req(c,'FAA updates demo customer name','PUT','/auth/users/updateProfile?userId={{customerId}}',body={'fName':'Presentation'},role='faa',multipart=True)
req(c,'Reject reactivation through profile route','PUT','/auth/users/updateProfile?userId={{customerId}}',400,{'active':True},role='faa',multipart=True,message='only allows account deactivation')
req(c,'Reject weak new password','POST','/auth/users/changePassword',422,{'newPassword':'weak'},message='Invalid Password',why='Actual Passay rules: 8–30 characters, at least one uppercase and digit, no whitespace. Error message incorrectly says maximum 20.')
req(c,'Change demo password','POST','/auth/users/changePassword',body={'newPassword':'DemoChanged2'},script="pm.environment.set('customerPassword','DemoChanged2');")
login(c,'customer','DemoChanged2')
req(c,'Reject wrong reset answer','POST','/auth/users/forgetPassword',401,{'email':'{{customerEmail}}','securityQuestionAnswer':'wrong'},message='incorrect')
req(c,'Reset demo customer password to CPR','POST','/auth/users/forgetPassword',body={'email':'{{customerEmail}}','securityQuestionAnswer':' MANAMA '},script="pm.environment.set('customerPassword',pm.environment.get('customerCpr'));",why='Requires bearer authentication. Answer is trimmed, lowercased and checked with BCrypt. Sends password reset email.')
login(c,'customer','{{customerCpr}}')
req(c,'Restore demo password','POST','/auth/users/changePassword',body={'newPassword':'DemoPassword1'},script="pm.environment.set('customerPassword','DemoPassword1');")
req(c,'FAA deactivates demo customer - run last','PUT','/auth/users/updateProfile?userId={{customerId}}',body={'active':False},role='faa',multipart=True,why='Do this after customer demonstrations. No reactivation endpoint exists; cleanup/recreation is needed.')
req(c,'Reject login for deactivated account','POST','/auth/users/login',401,{'email':'{{customerEmail}}','password':'{{customerPassword}}'},role='none',message='inactive')

c=collection('09 - Notifications - Open alongside collection 02','faa'); login(c,'faa')
i=req(c,'Open FAA event stream - keep tab open','GET','/notifications',why='Open before sending activation request in collection 02. Expect connected event, keep-alive comments every 25 seconds and airplane-activation-requested after successful commit. Stream times out after five minutes; reconnect if needed. Run manually, outside a collection runner.')
i['request']['header']=[{'key':'Accept','value':'text/event-stream'}]
i['event']=[]
req(c,'Reject unauthenticated subscription','GET','/notifications',403,role='none')

# Keep the short presentation in sync with the full examples, preserving role overrides.
flow = [
    (0, ['Login as faa', 'Create Presentation Airways', 'Reject duplicate airline',
         'Reject admin cpr', 'Assign airline administrator']),
    (1, ['Login as admin', 'Reject airline admin using FAA route',
         'Reject airplane standardSeatCapacity', 'Add airplane for approval',
         'Add airplane for denial', 'FAA lookup - save airplane IDs',
         'Reject flight on grounded airplane', 'Request activation - approval example',
         'Reject duplicate pending request']),
    (2, ['Login as faa', 'List pending requests', 'Reject denial without reason', 'Accept airplane']),
    (3, ['Login as admin', 'Reject past departure', 'Create main flight - seven days ahead',
         'Reject overlapping schedule', 'Create flight for 48-hour cancellation example',
         'Find demo flights and save IDs']),
    (4, ['Request verification email - then open inbox', 'Verify email - enter code first',
         'Login before setup using CPR', 'Reject protected access before setup',
         'Complete setup and save customer ID', 'Login as customer']),
    (5, ['Search demo airline flights', 'Reject page=0']),
    (6, ['Reject invalid seat class', 'Book standard seat 1', 'Reject already booked seat',
         'View own bookings', 'Cancel main booking']),
]
short = {
    'info': {'name': '00 - Main presentation - Complete flow',
             'schema': 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
             'description': 'The short presentation flow in one collection. Select the Presentation environment and send requests top to bottom. Tokens and IDs are captured automatically. Pause after requesting verification email and enter verificationCode before continuing. Uses the same demo records as collections 01–09: choose either flow on a clean demo dataset; do not run both creation flows without cleanup.'},
    'item': [],
}
for index, names in flow:
    source = collections[index]
    by_name = {re.sub(r'^\d+ \| | \[\d+\]$', '', item['name']): item for item in source['item']}
    for name in names:
        item = deepcopy(by_name[name])
        item['name'] = re.sub(r'^\d+', f'{len(short["item"])+1:02d}', item['name'])
        if 'auth' not in item['request']:
            item['request']['auth'] = deepcopy(source['auth'])
        short['item'].append(item)
(OUT/'00-main-presentation.postman_collection.json').write_text(json.dumps(short,indent=2)+'\n',encoding='utf-8')

for c in collections:
    filename=c['info']['name'].split(' - ')[0]+'-'+c['info']['name'].split(' - ')[1].lower()+'.postman_collection.json'
    (OUT/filename).write_text(json.dumps(c,indent=2)+'\n',encoding='utf-8')
(OUT/'Presentation.postman_environment.json').write_text(json.dumps({'name':'Sauds Flight System - Presentation','values':[{'key':k,'value':v,'enabled':True,'type':'secret' if 'Token' in k or 'Password' in k or 'Cpr' in k or k=='verificationCode' else 'default'} for k,v in env.items()], '_postman_variable_scope':'environment'},indent=2)+'\n',encoding='utf-8')
print(f'Built {len(collections)} full collections plus a {len(short["item"])}-request main presentation collection and shared environment.')
