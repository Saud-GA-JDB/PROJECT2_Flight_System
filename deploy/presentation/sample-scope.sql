-- Deliberately exclude passwords, security answers, CPRs, phone numbers,
-- payment-provider identifiers, audit payloads, and non-sample user identities.
SELECT 'known_sample_accounts',u.id,u.email_address,u.role,u.status,
 ae.airline_role,ae.airline_id,ap.airport_role,ap.airport_id,
 me.maintenance_role,me.provider_id,atc.atc_role,atc.facility_id
FROM users u
LEFT JOIN airline_employees ae ON ae.user_id=u.id
LEFT JOIN airport_employees ap ON ap.user_id=u.id
LEFT JOIN maintenance_employees me ON me.user_id=u.id
LEFT JOIN atc_employees atc ON atc.user_id=u.id
WHERE u.email_address IN (
 'flighttest.customer@mailsac.com','flighttest.faa@mailsac.com','flighttest.airline@mailsac.com',
 'sara.customer@mailsac.com','omar.customer@mailsac.com','layla.customer@mailsac.com','noor.airline@mailsac.com',
 'gf.crew1@mailsac.com','gf.crew2@mailsac.com','gf.crew3@mailsac.com',
 'ek.crew1@mailsac.com','ek.crew2@mailsac.com','ek.crew3@mailsac.com',
 'airport.admin@mailsac.com','airport.inspector@mailsac.com',
 'maintenance.admin@mailsac.com','maintenance.technician@mailsac.com',
 'atc.bah.admin@example.com','atc.bah.controller@example.com',
 'atc.dxb.admin@example.com','atc.dxb.controller@example.com',
 'atc.doh.admin@example.com','atc.doh.controller@example.com') ORDER BY u.id;
SELECT 'flight_schedule',id,flight_number,airplane_id,origin_airport_id,destination_airport_id,scheduled_departure,status FROM flights ORDER BY id;
SELECT 'fleet',id,registration_number,model,airline_id,status FROM airplanes ORDER BY id;
SELECT 'airlines',id,airline_code,name FROM airline ORDER BY id;
SELECT 'airports',id,iata_code,operator_id FROM airports ORDER BY id;
SELECT 'sample_operators',id,name FROM airport_operators WHERE contact_email='seed.operator@mailsac.com';
SELECT 'sample_navigation',id,name FROM air_navigation_service_providers WHERE contact_email IN ('seed.navigation.gulf@example.com','seed.navigation.qatar@example.com');
SELECT 'sample_maintenance',id,name FROM maintenance_providers WHERE contact_email='seed.maintenance@mailsac.com';
SELECT 'tower',id,airport_id,provider_id,name FROM control_facilities;
SELECT 'counts',(SELECT count(*) FROM users),(SELECT count(*) FROM bookings),(SELECT count(*) FROM payments),(SELECT count(*) FROM maintenance_records),(SELECT count(*) FROM flight_inspections),(SELECT count(*) FROM flight_control_events);
SELECT 'payment_status',status,count(*) FROM payments GROUP BY status;
SELECT 'relationships',flight_id,count(*),count(*) FILTER(WHERE u.email_address NOT IN ('flighttest.customer@mailsac.com','sara.customer@mailsac.com','omar.customer@mailsac.com','layla.customer@mailsac.com')) AS nonseed_bookings FROM bookings b JOIN users u ON u.id=b.user_id GROUP BY flight_id;
