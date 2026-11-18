-- Identify only explicitly labelled test identities; omit security and personal identifiers.
SELECT u.id,u.email_address,p.f_name,p.l_name,u.role
FROM users u JOIN user_profiles p ON p.user_id=u.id
WHERE u.id NOT IN(15,16,17,18,19,20,21)
AND (lower(u.email_address) ~ '(postman|test|demo|sample)' OR lower(concat(p.f_name,' ',p.l_name)) ~ '(postman|test|demo|sample)');
SELECT 'remaining_sample_text','airplane_requests',id,review_reason FROM airplane_requests WHERE lower(review_reason) ~ '(postman|test|demo|sample|sse|email extraction)';
SELECT 'remaining_sample_text','maintenance_records',id,findings FROM maintenance_records WHERE lower(concat(findings,' ',cancellation_reason)) ~ '(postman|test|demo|sample|sse|microservice)';
