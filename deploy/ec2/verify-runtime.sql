\pset pager off
BEGIN READ ONLY;
SELECT (SELECT count(*) FROM users) AS users,
       (SELECT count(*) FROM flights) AS flights,
       (SELECT min(id) FROM flights) AS sample_flight_id,
       (SELECT count(*) FROM bookings) AS bookings,
       (SELECT count(*) FROM user_profiles WHERE image_url IS NOT NULL) AS image_references;
SELECT application_name, count(*) AS connections, bool_and(s.ssl) AS all_tls
FROM pg_stat_activity a
JOIN pg_stat_ssl s ON s.pid = a.pid
WHERE datname = current_database() AND application_name LIKE 'flight-%'
GROUP BY application_name ORDER BY application_name;
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'users'
  AND column_name IN ('recovery_question_code_hash', 'recovery_question_expires_at',
                      'failed_recovery_question_attempts')
ORDER BY column_name;
ROLLBACK;
