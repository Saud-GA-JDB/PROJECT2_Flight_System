\pset pager off
BEGIN READ ONLY;
SELECT current_database(), current_user,
       (SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()) AS tls_enabled;
SELECT (SELECT count(*) FROM users) AS users,
       (SELECT count(*) FROM flights) AS flights,
       (SELECT count(*) FROM bookings) AS bookings;
SELECT table_name, column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND ((table_name = 'users' AND column_name LIKE '%recovery_question%')
    OR (table_name = 'maintenance_records' AND column_name IN
        ('cancellation_reason', 'cancelled_at', 'cancelled_by_user_id'))
    OR (table_name = 'flights' AND column_name LIKE 'clearance_%'))
ORDER BY table_name, column_name;
SELECT conname, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE connamespace = 'public'::regnamespace
  AND conname IN ('audit_logs_action_check', 'audit_logs_entity_type_check',
                 'maintenance_cancellation_check');
SELECT count(*) AS image_references FROM user_profiles WHERE image_url IS NOT NULL;
ROLLBACK;
