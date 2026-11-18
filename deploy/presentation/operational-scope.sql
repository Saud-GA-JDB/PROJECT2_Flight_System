SELECT 'operators',id,name FROM airport_operators;
SELECT 'maintenance_providers',id,name FROM maintenance_providers;
SELECT 'maintenance_work',id,airplane_id,provider_id,technician_id,status,description FROM maintenance_records;
SELECT 'inspection',id,flight_id,airport_id,inspector_id,status FROM flight_inspections;
SELECT 'reviews',id,airplane_id,status FROM airplane_requests;
SELECT 'reference_counts',a.id,count(DISTINCT f.id) flights,count(DISTINCT m.id) maintenance FROM airplanes a LEFT JOIN flights f ON f.airplane_id=a.id LEFT JOIN maintenance_records m ON m.airplane_id=a.id GROUP BY a.id ORDER BY a.id;
SELECT 'fixture_crews',c.flight_id,c.employee_id,c.duty FROM crew_assignments c WHERE c.flight_id IN(7,8,9,10,11,126,127);
SELECT 'employees', 'airline',id,airline_id,airline_role FROM airline_employees
UNION ALL SELECT 'employees','airport',id,airport_id,airport_role FROM airport_employees
UNION ALL SELECT 'employees','maintenance',id,provider_id,maintenance_role FROM maintenance_employees;
SELECT 'schema',table_name,string_agg(column_name,',' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema='public' GROUP BY table_name;
SELECT 'active_scheduler',current_setting('TimeZone');
