-- Required by the existing Identity model and docs/API-GUIDE.md.
-- Apply only after a verified backup. Does not replace existing rows or schema.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
DO $migration$
DECLARE
    before_hash text;
    after_hash text;
BEGIN
    LOCK TABLE public.users IN ACCESS EXCLUSIVE MODE;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users'
          AND column_name IN ('recovery_question_code_hash',
                              'recovery_question_expires_at',
                              'failed_recovery_question_attempts')
    ) THEN
        RAISE EXCEPTION 'Recovery columns already exist; inspect rather than replaying this upgrade';
    END IF;
    SELECT md5(string_agg(to_jsonb(u)::text, E'\n' ORDER BY id))
    INTO before_hash FROM public.users u;

    ALTER TABLE public.users
        ADD COLUMN recovery_question_code_hash varchar(255),
        ADD COLUMN recovery_question_expires_at timestamp(6) with time zone,
        ADD COLUMN failed_recovery_question_attempts integer NOT NULL DEFAULT 0;

    SELECT md5(string_agg((to_jsonb(u) - 'recovery_question_code_hash'
                          - 'recovery_question_expires_at'
                          - 'failed_recovery_question_attempts')::text, E'\n' ORDER BY id))
    INTO after_hash FROM public.users u;
    IF before_hash IS DISTINCT FROM after_hash THEN
        RAISE EXCEPTION 'Existing user data changed; rolling back';
    END IF;
    IF EXISTS (SELECT 1 FROM public.users
               WHERE recovery_question_code_hash IS NOT NULL
                  OR recovery_question_expires_at IS NOT NULL
                  OR failed_recovery_question_attempts <> 0) THEN
        RAISE EXCEPTION 'Unexpected initial recovery state; rolling back';
    END IF;
END
$migration$;
COMMIT;
