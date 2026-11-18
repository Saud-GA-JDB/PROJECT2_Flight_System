-- The local schema snapshot lacks this documented flight-operation constraint.
-- Source: docs/API-GUIDE.md, "Flight-operation schema additions".
-- This overlay is restricted to the disposable CI database, not production.
DO $$
BEGIN
    IF current_database() <> 'ci_aviation' THEN
        RAISE EXCEPTION 'CI constraints may only be installed in ci_aviation';
    END IF;
END $$;

ALTER TABLE flights ADD CONSTRAINT flight_clearance_state_check CHECK (
    clearance_cycle >= 0 AND clearance_revision >= 0
    AND (clearance_operation IS NULL OR clearance_operation IN ('DEPARTURE', 'ARRIVAL'))
    AND ((clearance_status = 'NONE' AND clearance_cycle = 0 AND clearance_operation IS NULL
          AND clearance_facility_id IS NULL AND clearance_approved_at IS NULL AND clearance_expires_at IS NULL)
      OR (clearance_cycle > 0 AND clearance_operation IS NOT NULL AND clearance_facility_id IS NOT NULL
          AND ((clearance_status IN ('PENDING', 'REJECTED', 'WITHDRAWN') AND clearance_approved_at IS NULL AND clearance_expires_at IS NULL)
            OR (clearance_status IN ('APPROVED', 'REVOKED', 'CONSUMED') AND clearance_approved_at IS NOT NULL
                AND clearance_expires_at IS NOT NULL AND clearance_expires_at > clearance_approved_at))))
);
