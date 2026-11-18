"""Build a bounded, transactional presentation dataset; never connects to a DB.

All people, schedules, maintenance findings and payments are fictional.
Existing seven repository seed accounts are renamed in place. Other users stay intact.
"""
import argparse
import datetime as dt
import json
from pathlib import Path
import secrets
import subprocess

ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--commit', action='store_true')
args = parser.parse_args()
private = ROOT / 'accounts.private.json'
if private.exists():
    credential = json.loads(private.read_text())
else:
    password = 'Flight!' + ''.join(secrets.choice('ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789') for _ in range(12))
    recovery = secrets.token_urlsafe(20)
    jars = [Path.home()/'.m2/repository/org/springframework/security/spring-security-crypto/7.1.1/spring-security-crypto-7.1.1.jar', Path.home()/'.m2/repository/commons-logging/commons-logging/1.3.6/commons-logging-1.3.6.jar']
    encoded = subprocess.check_output(['java','--class-path',';'.join(map(str,jars)),str(ROOT/'HashPassword.java')],input=(password+'\n'+recovery+'\n').encode()).decode().splitlines()
    credential = {'password':password,'passwordHash':encoded[0],'recoveryHash':encoded[1]}

sql = []
def emit(s): sql.append(s)
def lit(v):
    if v is None: return 'NULL'
    if isinstance(v,bool): return 'true' if v else 'false'
    if isinstance(v,(int,float)): return str(v)
    return "'"+str(v).replace("'","''")+"'"
def insert(table, **row): emit(f"INSERT INTO {table} ({','.join(row)}) VALUES ({','.join(lit(v) for v in row.values())});")
def update(table, where, **row): emit(f"UPDATE {table} SET "+','.join(f'{k}={lit(v)}' for k,v in row.items())+' WHERE '+where+';')
def stamp(day,hour=9,minute=0): return dt.datetime(2026,11,day,hour,minute).isoformat(' ')
NOW=stamp(18,20)
tables=['users','airline','airplanes','flights','airline_employees','airport_operators','airport_employees','maintenance_providers','maintenance_employees','air_navigation_service_providers','control_facilities','atc_employees','airline_maintenance_providers','crew_assignments','flight_inspections','maintenance_records','airplane_requests','bookings','payments','flight_control_events']
emit("BEGIN; SET LOCAL TIME ZONE 'Asia/Bahrain'; SET LOCAL lock_timeout='8s'; SET LOCAL statement_timeout='90s'; SELECT pg_advisory_xact_lock(20261119);")
emit('LOCK TABLE '+','.join(tables)+',user_profiles IN SHARE ROW EXCLUSIVE MODE;')
emit("DO $$ BEGIN IF (SELECT count(*) FROM users WHERE (id,email_address) IN ((15,'flighttest.customer@mailsac.com'),(16,'flighttest.faa@mailsac.com'),(17,'flighttest.airline@mailsac.com'),(18,'sara.customer@mailsac.com'),(19,'omar.customer@mailsac.com'),(20,'layla.customer@mailsac.com'),(21,'noor.airline@mailsac.com'))) <> 7 THEN RAISE EXCEPTION 'Seed accounts changed, or import already applied'; END IF; END $$;")
for table in tables:
    emit(f"DO $$ BEGIN IF EXISTS(SELECT 1 FROM {table} WHERE id >= 910000) THEN RAISE EXCEPTION 'Reserved presentation ID range occupied in {table}'; END IF; END $$;")
sample_users='15,16,17,18,19,20,21,25,26,211,212,213,214,362'
emit("DO $$ BEGIN IF (SELECT count(*) FROM users u JOIN user_profiles p ON p.user_id=u.id WHERE u.id IN(25,26,211,212,213,214,362) AND (lower(u.email_address) ~ '(postman|test|demo|sample)' OR lower(concat(p.f_name,' ',p.l_name)) ~ '(postman|test|demo|sample)'))<>7 THEN RAISE EXCEPTION 'Additional sample identities changed'; END IF; END $$;")
emit(f"CREATE TEMP TABLE untouched_users ON COMMIT DROP AS SELECT id,md5(row_to_json(u)::text) AS fingerprint FROM users u WHERE id NOT IN({sample_users});")
emit(f"CREATE TEMP TABLE untouched_profiles ON COMMIT DROP AS SELECT user_id,md5(row_to_json(p)::text) AS fingerprint FROM user_profiles p WHERE user_id NOT IN({sample_users});")
emit("CREATE TEMP TABLE untouched_bookings ON COMMIT DROP AS SELECT id,md5(row_to_json(b)::text) AS fingerprint FROM bookings b WHERE flight_id NOT IN(7,8,9,10,11);")

accounts=[]
def account(first,last,role,subrole=None,org=None,existing=None):
    uid=existing or (910001+sum(a['id']>=910000 for a in accounts))
    email=(first+'.'+last).lower().replace(' ','')+'@mailsec.com'
    entry=dict(id=uid,name=first+' '+last,email=email,role=role,subrole=subrole,organization=org)
    accounts.append(entry)
    values=dict(email_address=email,password=credential['passwordHash'],role=role,is_active=True,status=1,
        security_question='What is your recovery phrase?',security_question_answer=credential['recoveryHash'],failed_login_attempts=0,login_attempts_date=None,
        failed_password_reset_attempts=0,password_reset_attempts_date=None,failed_recovery_question_attempts=0,recovery_question_code_hash=None,recovery_question_expires_at=None)
    if existing:
        update('users',f'id={uid}',**values)
        update('user_profiles',f'user_id={uid}',f_name=first,l_name=last,phone_number_opening_code='+973',phone_number=f'36{990000+len(accounts):06d}',updated_at=NOW)
    else:
        insert('users',id=uid,**values)
        insert('user_profiles',user_id=uid,f_name=first,l_name=last,cpr=f'9909{len(accounts):05d}',phone_number_opening_code='+973',phone_number=f'36{990000+len(accounts):06d}',created_at=NOW,updated_at=NOW)
    return uid

ahmed=account('Ahmed','AlMansoori','CUSTOMER',existing=15)
faa=account('Fatima','AlSayed','FAA_ADMIN',existing=16)
gfadmin=account('Hassan','Mahmood','AIRLINE_EMPLOYEE','ADMIN','Gulf Air',17)
sara=account('Sara','Ahmed','CUSTOMER',existing=18)
omar=account('Omar','Ali','CUSTOMER',existing=19)
layla=account('Layla','Hassan','CUSTOMER',existing=20)
ekadmin=account('Noor','Khalid','AIRLINE_EMPLOYEE','ADMIN','Emirates',21)
account('Saeed','AlMarzooq','AIRLINE_EMPLOYEE','ADMIN','Emirates',25)
account('Amal','Saleh','CUSTOMER',existing=26)
account('Ahmed','Fakhro','MAINTENANCE_EMPLOYEE','ADMIN','Gulf Technical Services',211)
account('Omar','Khalfan','MAINTENANCE_EMPLOYEE','TECHNICIAN','Gulf Technical Services',212)
account('Sara','AlDoseri','AIRPORT_EMPLOYEE','ADMIN','Bahrain International Airport',213)
account('Khalid','AlBinali','AIRPORT_EMPLOYEE','INSPECTOR','Bahrain International Airport',214)
account('Bilal','Hassan','MAINTENANCE_EMPLOYEE','TECHNICIAN','AeroCare Bahrain',362)
customers=[ahmed,sara,omar,layla]
for first,last in [('Dana','Yousif'),('Mariam','Abdulla'),('Faisal','Salman'),('Huda','Jassim'),('Khalifa','Ebrahim'),('Reem','Nasser'),('Salman','Hamad'),('Aisha','Mubarak'),('Yasmin','Khalil'),('Nasser','Adel')]:
    customers.append(account(first,last,'CUSTOMER'))

# Replace only clearly labelled Postman/test organizations; retain IDs and all relationships.
update('airport_operators',"id=34 AND name LIKE 'Postman%'",name='Bahrain Airport Services',contact_email='operations.bahrain@mailsec.com')
for pid,name,email in [(12,'Gulf Technical Services','service.gulftechnical@mailsec.com'),(13,'Arabian Aircraft Engineering','service.arabianengineering@mailsec.com'),(14,'AeroCare Bahrain','service.aerocare@mailsec.com')]:
    update('maintenance_providers',f"id={pid} AND name LIKE 'Postman%'",name=name,contact_email=email)
update('airline',"id=8 AND name='Presentation Airwayss'",name='Pearl Aviation',airline_code='PA',headquarters_country='Bahrain')
update('airline',"id=10 AND name='Presentation Airways'",name='Horizon Gulf Airways',headquarters_country='Bahrain')
update('airline',"id=2 AND coalesce(trim(name),'')=''",name='Bahrain Regional Airways',airline_code='BR',headquarters_country='Bahrain')
update('airline',"id=3 AND name=' Kuwait Airways'",name='Kuwait Airways')
for offset,airport,name in [(1,2,'Dubai Airport Operations'),(2,3,'Doha Airport Operations')]:
    insert('airport_operators',id=910000+offset,name=name,contact_email=f'operations.{"dubai" if airport==2 else "doha"}@mailsec.com')
    update('airports',f'id={airport} AND operator_id IS NULL',operator_id=910000+offset)
for offset,airport,name in [(1,1,'Bahrain Air Navigation'),(2,2,'Emirates Air Navigation'),(3,3,'Qatar Air Navigation')]:
    insert('air_navigation_service_providers',id=910000+offset,name=name,contact_email=f'navigation.{["bahrain","dubai","doha"][offset-1]}@mailsec.com')
    insert('control_facilities',id=910000+offset,provider_id=910000+offset,airport_id=airport,name=['Bahrain Control Tower','Dubai Control Tower','Doha Control Tower'][offset-1],facility_type='TOWER')

def airline_staff(first,last,role,airline=1):
    uid=account(first,last,'AIRLINE_EMPLOYEE',role,'Gulf Air' if airline==1 else 'Emirates')
    insert('airline_employees',id=uid,user_id=uid,created_at=NOW,updated_at=NOW,airline_id=airline,airline_role=role,hire_date='2023-03-01',salary=2800 if role=='PILOT' else 950)
    return uid
team1=[airline_staff('Yusuf','Abdulla','PILOT'),airline_staff('Ali','Jassim','PILOT'),airline_staff('Maryam','Hamad','FLIGHT_ATTENDANT')]
team2=[airline_staff('Rashid','Ebrahim','PILOT'),airline_staff('Hamad','Saleh','PILOT'),airline_staff('Noura','Salman','FLIGHT_ATTENDANT')]
team3=[airline_staff('Khaled','Mansoor','PILOT',6),airline_staff('Tariq','Yousif','PILOT',6),airline_staff('Salma','Khalil','FLIGHT_ATTENDANT',6)]
gate=airline_staff('Rania','Hassan','GATE_RECEPTION')
def airport_staff(first,last,role):
    uid=account(first,last,'AIRPORT_EMPLOYEE',role,'Bahrain International Airport')
    insert('airport_employees',id=uid,user_id=uid,created_at=NOW,updated_at=NOW,airport_role=role,airport_id=1,operator_id=34)
    return uid
airportadmin=airport_staff('Mohammed','Rashid','ADMIN')
inspector=airport_staff('Zainab','Ali','INSPECTOR')
inspector2=airport_staff('Hessa','Abdulla','INSPECTOR')
baggage=airport_staff('Jassim','Khalifa','BAGGAGE_HANDLER')
def maintenance_staff(first,last,role):
    uid=account(first,last,'MAINTENANCE_EMPLOYEE',role,'Gulf Technical Services')
    insert('maintenance_employees',id=uid,user_id=uid,created_at=NOW,updated_at=NOW,maintenance_role=role,provider_id=12,license_number=f'BHR-AME-{uid-900000}' if role=='TECHNICIAN' else None)
    return uid
maintenanceadmin=maintenance_staff('Ibrahim','Hassan','ADMIN')
technician=maintenance_staff('Khalid','Salman','TECHNICIAN')
technician2=maintenance_staff('Ahmed','Nasser','TECHNICIAN')
controllers={}
for facility,first,last,role in [(1,'Abdulrahman','Yousif','ADMIN'),(1,'Abdulla','Khalifa','CONTROLLER'),(1,'Sara','Hamad','CONTROLLER'),(2,'Saeed','Rashid','ADMIN'),(2,'Mansoor','Ali','CONTROLLER'),(3,'Abdullah','Jaber','ADMIN'),(3,'Khalifa','Nasser','CONTROLLER')]:
    uid=account(first,last,'ATC_EMPLOYEE',role,['Bahrain','Dubai','Doha'][facility-1]+' Control Tower')
    insert('atc_employees',id=uid,user_id=uid,created_at=NOW,updated_at=NOW,employee_number=f'ATC-{["BAH","DXB","DOH"][facility-1]}-{uid-910000:03}',atc_role=role,facility_id=910000+facility)
    if role=='CONTROLLER': controllers.setdefault(facility,uid)

# Existing labelled fixtures are renamed, never deleted.
for pid,old,new,model in [(900003,'TEST-FREE-PLANE','A9C-RBA','Boeing 787-9'),(900008,'A9C-SF01','A9C-RBB','Airbus A320-200'),(900009,'A9C-SF02','A9C-RBC','Airbus A321neo'),(900010,'A9C-SF03','A9C-RBD','Airbus A320-200'),(900011,'A6-SF01','A6-RBA','Boeing 777-300ER'),(900019,'A9C-DEMO01','A6-RBB','Airbus A320-200'),(900020,'A9C-DEMO02','A6-RBC','Airbus A320-200'),(900082,'SSE-20261112','A9C-RBE','Airbus A320-200'),(900083,'EMAIL-20261112','A9C-RBF','Airbus A321neo')]:
    update('airplanes',f'id={pid} AND registration_number={lit(old)}',registration_number=new,model=model)
planes={}
for idx,(reg,model,airline,std,first,status) in enumerate([
 ('A9C-RBG','Airbus A320neo',1,138,12,'ACTIVE'),('A9C-RBH','Airbus A321neo',1,154,16,'ACTIVE'),
 ('A9C-RBI','Boeing 787-9',1,250,26,'ACTIVE'),('A9C-RBJ','Airbus A320-200',1,138,12,'ACTIVE'),
 ('A9C-RBK','Airbus A321neo',1,154,16,'GROUNDED'),('A9C-RBL','Boeing 787-9',1,250,26,'GROUNDED'),
 ('A6-RBD','Boeing 777-300ER',6,310,8,'ACTIVE'),('A6-RBE','Airbus A380-800',6,399,14,'ACTIVE')],1):
    pid=910000+idx
    planes[pid]=(airline,std,first,status)
    insert('airplanes',id=pid,registration_number=reg,model=model,airline_id=airline,standard_seats_capacity=std,first_class_seats_capacity=first,seat_capacity=std+first,max_mileage=12000 if 'Boeing' in model else 6500,added_at=stamp(1),status=status)
for idx,(provider,airline) in enumerate([(12,1),(12,6),(13,1),(14,1),(13,6)],1):
    emit(f'INSERT INTO airline_maintenance_providers(id,provider_id,airline_id) SELECT {910000+idx},{provider},{airline} WHERE NOT EXISTS(SELECT 1 FROM airline_maintenance_providers WHERE provider_id={provider} AND airline_id={airline});')

flights=[]
crew_id=910000
inspection_id=910000
def crew(fid,team,assigned):
    global crew_id
    for employee,duty in zip(team,['PILOT','SECONDARY_PILOT','FLIGHT_ATTENDANT']):
        crew_id+=1
        insert('crew_assignments',id=crew_id,flight_id=fid,employee_id=employee,duty=duty,assigned_at=assigned)
def inspection_record(fid,origin,status,created,completed=None,reason=None,assigned=True):
    global inspection_id
    inspection_id+=1
    # Airport inspectors may only inspect their own airport; only BAH has a staffed desk.
    insert('flight_inspections',id=inspection_id,flight_id=fid,airport_id=origin,inspector_id=inspector if assigned and origin==1 else None,status=status,created_at=created,completed_at=completed,reason=reason)
    return inspection_id
def flight(number,day,hour,origin,dest,plane=910001,duration=80,status='ACTIVE',inspection='PASSED',team=None,clearance=False):
    fid=910001+len(flights)
    dep=dt.datetime(2026,11,day,hour)
    arr=dep+dt.timedelta(minutes=duration)
    airline,std,first,_=planes[plane]
    team=team or (team3 if airline==6 else team1)
    price={2:48000,3:39000,4:62000,5:85000,6:56000,7:68000,8:210000,9:345000}.get(dest,52000)
    row=dict(id=fid,flight_number=number,airplane_id=plane,airline_id=airline,origin_airport_id=origin,destination_airport_id=dest,scheduled_departure=dep.isoformat(' '),scheduled_arrival=arr.isoformat(' '),status=status,standard_seats_count=std,first_class_seats_count=first,standard_price=price,first_class_price=price*3,currency='BHD',actual_departure=(dep+dt.timedelta(minutes=7)).isoformat(' ') if status=='CLOSED' else None,actual_arrival=(arr+dt.timedelta(minutes=3)).isoformat(' ') if status=='CLOSED' else None)
    insert('flights',**row)
    row['team']=team; row['inspection']=inspection; row['pendingClearance']=clearance
    flights.append(row)
    if status not in ('PENDING_CREW','CANCELLED'): crew(fid,team,stamp(min(day,18)-2))
    if inspection and origin==1:
        inspection_record(fid,origin,inspection,stamp(min(day,18)-1),stamp(min(day,18),8) if inspection!='PENDING' else None,'Exterior, flight controls, cabin safety equipment and documentation checked.' if inspection=='PASSED' else ('Hydraulic fluid seepage observed at left main landing gear; rectification required.' if inspection=='FAILED' else None),assigned=number!='GF154')
    return fid

# Historical flights provide completed trips and revenue; all route times are Bahrain time.
flight('GF152',15,8,1,2,team=team1,status='CLOSED')
flight('GF153',15,11,2,1,team=team1,status='CLOSED')
flight('GF170',17,8,1,3,plane=910002,team=team2,status='CLOSED',duration=55)
flight('GF171',17,11,3,1,plane=910002,team=team2,status='CLOSED',duration=55)
flight('EK836',17,14,2,1,plane=910007,team=team3,status='CLOSED')
flight('EK839',17,18,1,2,plane=910007,team=team3,status='CLOSED')
# Tomorrow's linked operational walkthrough.
ready=flight('GF150',19,10,1,2,clearance=True)
waiting=flight('GF172',19,13,1,3,plane=910002,team=team2,inspection='PENDING',duration=55)
unassigned=flight('GF154',19,16,1,2,plane=910004,status='PENDING_CREW',inspection='PENDING')
requestable=flight('GF180',19,20,1,6,plane=910003,team=team1,duration=65)
flight('GF510',19,23,1,7,plane=910004,status='CANCELLED',inspection=None,duration=90)
flight('EK840',19,9,2,1,plane=910007,team=team3,inspection=None)
flight('EK841',19,13,1,2,plane=910007,team=team3,clearance=True)
failed=flight('GF186',20,9,1,4,plane=910002,team=team2,inspection='FAILED',duration=80)
flight('GF187',20,13,4,1,plane=910002,team=team2,inspection=None,duration=80)
# Search and booking options comfortably beyond the 48-hour cancellation cutoff.
for day in range(21,27):
    dest=[2,3,4,6,7,5][day-21]
    flight(f'GF{220+(day-21)*2}',day,9,1,dest,plane=910001,team=team1,duration=100 if dest in(5,7) else 80)
    flight(f'GF{221+(day-21)*2}',day,13,dest,1,plane=910001,team=team1,inspection=None,duration=100 if dest in(5,7) else 80)
flight('GF002',23,18,1,8,plane=910003,team=team2,duration=440)
flight('GF003',24,8,8,1,plane=910003,team=team2,inspection=None,duration=400)
flight('EK842',22,9,2,1,plane=910007,team=team3,inspection=None)
flight('EK843',22,13,1,2,plane=910007,team=team3)

# Repair only known repository seed-flight history, preserving booking references and owners.
old_info=[(7,16,8,team1),(8,16,12,team1),(9,16,15,team1),(10,16,8,team3),(11,16,12,team3)]
for fid,day,hour,team in old_info:
    dep=dt.datetime(2026,11,day,hour); arr=dep+dt.timedelta(minutes=90)
    update('flights',f'id={fid}',scheduled_departure=dep.isoformat(' '),scheduled_arrival=arr.isoformat(' '),actual_departure=(dep+dt.timedelta(minutes=5)).isoformat(' '),actual_arrival=(arr+dt.timedelta(minutes=3)).isoformat(' '),status='CLOSED')
    emit(f"UPDATE bookings SET status='FINISHED',updated_at={lit(arr.isoformat(' '))} WHERE flight_id={fid} AND status='BOOKED';")
    crew(fid,team,stamp(14))
update('bookings',"id=900001 AND booking_ref='TEST-BOOKING-900001'",booking_ref='BH7Q2M')

# Varied maintenance workload and an FAA queue, with no pending work on active flight aircraft.
maintenance_rows=[
 (910005,'PENDING',technician,'Scheduled A-check: inspect engine inlet, landing gear and hydraulic lines.',None),
 (910006,'PENDING',None,'Cabin emergency equipment audit and oxygen bottle pressure verification.',None),
 (900082,'PENDING',technician2,'Investigate intermittent flight-deck air-conditioning indication.',None),
 (910001,'COMPLETED',technician,'Replace worn nose-wheel tyre and perform pressure/leak checks.','Tyre replaced, wheel torque verified and pressure stable after inspection.'),
 (910002,'COMPLETED',technician,'Inspect engine oil filters and review trend-monitoring readings.','Filters serviceable; readings within the maintenance programme limits.'),
 (910003,'COMPLETED',technician2,'Test emergency lighting and cabin exit signage.','All emergency lights and exit signs passed functional testing.'),
 (900003,'CANCELLED',technician,'Planned cabin trim inspection.','Rescheduled into the next consolidated maintenance visit.'),
 (900019,'PENDING',None,'Inspect brakes and landing gear following storage.',None)]
for idx,(plane,status,tech,desc,findings) in enumerate(maintenance_rows,1):
    insert('maintenance_records',id=910000+idx,airplane_id=plane,provider_id=12,technician_id=tech,status=status,description=desc,findings=findings if status=='COMPLETED' else None,created_at=stamp(12+idx%3),updated_at=stamp(15+idx%3),completed_at=stamp(15+idx%3) if status=='COMPLETED' else None,cancellation_reason=findings if status=='CANCELLED' else None,cancelled_at=stamp(17)+'+03' if status=='CANCELLED' else None,cancelled_by_user_id=maintenanceadmin if status=='CANCELLED' else None)
update('maintenance_records',"id=8 AND description LIKE 'Postman test:%'",description='Inspect engines and hydraulic systems before return to service.')
update('maintenance_records',"id=10 AND description LIKE 'SSE manual check%'",description='Cabin environmental system diagnostic inspection.')
update('maintenance_records',"id=10 AND status='CANCELLED'",cancellation_reason='Diagnostic visit consolidated with the next scheduled maintenance check.')
update('maintenance_records',"id=11 AND description LIKE 'Manual microservice check:%'",description='Inspect landing gear assemblies, fittings and hydraulic connections.')
update('airplane_requests',"id=900009 AND review_reason='Email extraction test only'",review_reason='Updated technical records and airworthiness documentation required.')
update('airplane_requests',"id IN(900002,900004) AND review_reason='Presentation safety checks passed.'",review_reason='Aircraft records reviewed and required safety checks passed.')
for idx,(plane,status,reason) in enumerate([(910005,'PENDING',None),(910006,'PENDING',None),(910001,'ACCEPTED','Airworthiness documentation and maintenance release reviewed.'),(910002,'ACCEPTED','Registration documents and technical records verified.'),(910004,'ACCEPTED','Return-to-service inspection and documentation accepted.'),(900019,'DENIED','Updated maintenance release and brake inspection report required.')],1):
    insert('airplane_requests',id=910000+idx,airplane_id=plane,requested_by_id=gfadmin if plane!=900019 else ekadmin,status=status,requested_at=stamp(10+idx),reviewed_at=stamp(12+idx) if status!='PENDING' else None,review_reason=reason,reviewed_by_user_id=faa if status!='PENDING' else None)

booking_id=910000; payment_id=910000
def booking(f,owner,seat,status,pay=None):
    global booking_id,payment_id
    booking_id+=1
    # Six-character locator without fixture labels.
    ref=''.join('ABCDEFGHJKLMNPQRSTUVWXYZ23456789'[(booking_id//(31**p))%31] for p in range(6))
    created=stamp(10+(booking_id%5),10)
    insert('bookings',id=booking_id,booking_ref=ref,user_id=owner,flight_id=f['id'],seat_number=seat,status=status,booked_at=created,updated_at=f['actual_arrival'] if status=='FINISHED' else (stamp(17,16) if status=='CANCELLED' else created))
    if pay:
        payment_id+=1
        insert('payments',id=payment_id,booking_id=booking_id,amount=f['first_class_price'] if seat.startswith('firstClass') else f['standard_price'],currency='BHD',status=pay,created_at=created,updated_at=created,version=0)
for i,f in enumerate(flights):
    if f['status']=='PENDING_CREW': continue
    for j in range(6+(i%4)):
        owner=customers[(i+j)%len(customers)]
        seat=('firstClass-' if j<2 else 'standard-')+str(j+1)
        status='FINISHED' if f['status']=='CLOSED' else ('CANCELLED' if f['status']=='CANCELLED' or j==5 else 'BOOKED')
        pay='PAID' if status=='FINISHED' or (status=='BOOKED' and j<3) else ('EXPIRED' if status=='CANCELLED' else None)
        booking(f,owner,seat,status,pay)
# Primary customer has explicit upcoming paid, unpaid/cancellable, cancelled, and completed trips.
for f,seat,status,pay in [(flights[0],'standard-30','FINISHED','PAID'),(flights[15],'standard-30','BOOKED',None),(flights[17],'firstClass-10','BOOKED','PAID'),(flights[19],'standard-30','CANCELLED','EXPIRED')]:
    booking(f,ahmed,seat,status,pay)
emit("UPDATE flights f SET standard_seats_count=a.standard_seats_capacity-(SELECT count(*) FROM bookings b WHERE b.flight_id=f.id AND b.status IN('BOOKED','FINISHED') AND b.seat_number LIKE 'standard-%'),first_class_seats_count=a.first_class_seats_capacity-(SELECT count(*) FROM bookings b WHERE b.flight_id=f.id AND b.status IN('BOOKED','FINISHED') AND b.seat_number LIKE 'firstClass-%') FROM airplanes a WHERE a.id=f.airplane_id AND (f.id>=910000 OR f.id IN(7,8,9,10,11));")

# Pending clearances persist overnight; approved clearances would expire before presentation.
event_id=910000
for f in flights:
    if f['pendingClearance']:
        update('flights',f"id={f['id']}",clearance_operation='DEPARTURE',clearance_status='PENDING',clearance_facility_id=910001,clearance_cycle=1,clearance_revision=1)
        event_id+=1
        insert('flight_control_events',id=event_id,flight_id=f['id'],actor_user_id=f['team'][0],facility_id=910001,cycle=1,revision=1,event_type='REQUESTED',operation='DEPARTURE',occurred_at=stamp(18,19)+'+03',reason=None)
# Historical departure events make the pilot and tower history pages useful.
for f in [v for v in flights if v['status']=='CLOSED' and v['origin_airport_id']==1]:
    dep=dt.datetime.fromisoformat(f['actual_departure'])
    for rev,(kind,minutes,actor) in enumerate([('REQUESTED',-20,f['team'][0]),('APPROVED',-10,controllers[1]),('TAKE_OFF',0,f['team'][0])],1):
        event_id+=1
        insert('flight_control_events',id=event_id,flight_id=f['id'],actor_user_id=actor,facility_id=910001,cycle=1,revision=rev,event_type=kind,operation='DEPARTURE',occurred_at=(dep+dt.timedelta(minutes=minutes)).isoformat(' ')+'+03',reason=None)
    # Arrival clearance and landing at configured destination towers.
    dest=f['destination_airport_id']; arr=dt.datetime.fromisoformat(f['actual_arrival'])
    for rev,(kind,minutes,actor) in enumerate([('REQUESTED',-20,f['team'][0]),('APPROVED',-10,controllers[dest]),('LANDED',0,f['team'][0])],4):
        event_id+=1
        insert('flight_control_events',id=event_id,flight_id=f['id'],actor_user_id=actor,facility_id=910000+dest,cycle=2,revision=rev,event_type=kind,operation='ARRIVAL',occurred_at=(arr+dt.timedelta(minutes=minutes)).isoformat(' ')+'+03',reason=None)
    update('flights',f"id={f['id']}",clearance_operation='ARRIVAL',clearance_status='CONSUMED',clearance_facility_id=910000+dest,clearance_cycle=2,clearance_revision=6,clearance_approved_at=(arr-dt.timedelta(minutes=10)).isoformat(' ')+'+03',clearance_expires_at=(arr+dt.timedelta(minutes=5)).isoformat(' ')+'+03')

# Fail atomically on broken relationships, conflicting occupancy or altered personal accounts.
emit("""DO $$ BEGIN
IF EXISTS(SELECT 1 FROM untouched_users p LEFT JOIN users u USING(id) WHERE p.fingerprint IS DISTINCT FROM md5(row_to_json(u)::text)) THEN RAISE EXCEPTION 'Unrelated account altered'; END IF;
IF EXISTS(SELECT 1 FROM untouched_profiles x LEFT JOIN user_profiles p USING(user_id) WHERE x.fingerprint IS DISTINCT FROM md5(row_to_json(p)::text)) THEN RAISE EXCEPTION 'Unrelated profile altered'; END IF;
IF EXISTS(SELECT 1 FROM untouched_bookings p LEFT JOIN bookings b USING(id) WHERE p.fingerprint IS DISTINCT FROM md5(row_to_json(b)::text)) THEN RAISE EXCEPTION 'Unrelated booking altered'; END IF;
IF EXISTS(SELECT 1 FROM bookings WHERE flight_id>=910000 AND status IN('BOOKED','FINISHED') GROUP BY flight_id,seat_number HAVING count(*)>1) THEN RAISE EXCEPTION 'Duplicate occupied seat'; END IF;
IF EXISTS(SELECT 1 FROM flights WHERE id>=910000 AND (standard_seats_count<0 OR first_class_seats_count<0 OR scheduled_arrival<=scheduled_departure)) THEN RAISE EXCEPTION 'Invalid flight capacity or schedule'; END IF;
IF EXISTS(SELECT 1 FROM flights a JOIN flights b ON a.id<b.id AND a.airplane_id=b.airplane_id AND a.scheduled_departure<b.scheduled_arrival AND b.scheduled_departure<a.scheduled_arrival WHERE a.status<>'CANCELLED' AND b.status<>'CANCELLED' AND (a.id>=910000 OR b.id>=910000)) THEN RAISE EXCEPTION 'Overlapping airplane schedules'; END IF;
IF EXISTS(SELECT 1 FROM crew_assignments a JOIN crew_assignments b ON a.employee_id=b.employee_id AND a.flight_id<b.flight_id JOIN flights fa ON fa.id=a.flight_id JOIN flights fb ON fb.id=b.flight_id WHERE (a.id>=910000 OR b.id>=910000) AND fa.status<>'CANCELLED' AND fb.status<>'CANCELLED' AND fa.scheduled_departure<fb.scheduled_arrival AND fb.scheduled_departure<fa.scheduled_arrival) THEN RAISE EXCEPTION 'Overlapping crew schedules'; END IF;
IF EXISTS(SELECT 1 FROM flights f WHERE f.id>=910000 AND f.status IN('ACTIVE','CLOSED') AND (SELECT count(DISTINCT c.duty) FROM crew_assignments c WHERE c.flight_id=f.id)<>3) THEN RAISE EXCEPTION 'Incomplete active crew'; END IF;
IF EXISTS(SELECT 1 FROM crew_assignments c JOIN airline_employees e ON e.id=c.employee_id JOIN flights f ON f.id=c.flight_id WHERE c.id>=910000 AND e.airline_id<>f.airline_id) THEN RAISE EXCEPTION 'Crew airline mismatch'; END IF;
IF EXISTS(SELECT 1 FROM flight_inspections i JOIN airport_employees e ON e.id=i.inspector_id WHERE i.id>=910000 AND (e.airport_id<>i.airport_id OR e.airport_role<>'INSPECTOR')) THEN RAISE EXCEPTION 'Inspector scope mismatch'; END IF;
END $$;""")
emit(f"SELECT 'presentation_accounts',count(*) FROM users WHERE id>=910000 OR id IN({sample_users}); SELECT 'new_flights',count(*) FROM flights WHERE id>=910000; SELECT 'new_bookings',count(*) FROM bookings WHERE id>=910000; SELECT 'new_payments',status,count(*) FROM payments WHERE id>=910000 GROUP BY status;")
if args.commit:
    # Identity counters advance only after all transactional checks have passed.
    for table in tables:
        emit(f"SELECT setval(pg_get_serial_sequence('{table}','id'),GREATEST((SELECT coalesce(max(id),1) FROM {table}),coalesce(pg_sequence_last_value(pg_get_serial_sequence('{table}','id')::regclass),1)),true);")
    emit('COMMIT;')
else: emit('ROLLBACK;')
(ROOT/'generated.sql').write_text('\n'.join(sql)+'\n',encoding='utf-8')
credential.update(accounts=accounts,primaryFlights=dict(ready=ready,inspection=waiting,crew=unassigned,requestable=requestable,failed=failed),flights=[{k:v for k,v in f.items() if k in('id','flight_number','scheduled_departure','status')} for f in flights])
private.write_text(json.dumps(credential,indent=2)+'\n')
print(json.dumps({'mode':'COMMIT' if args.commit else 'ROLLBACK','accounts':len(accounts),'newFlights':len(flights),'newBookings':booking_id-910000,'newPayments':payment_id-910000,'sqlStatements':len(sql)}))
