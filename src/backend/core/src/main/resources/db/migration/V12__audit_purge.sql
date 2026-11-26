-- The audit trail's purge (NFR-258, ADR-041, ADR-075, ADR-110, ADR-149): the one way an audit record
-- is ever deleted. Flyway runs this as dispatchflow_owner, which owns both functions, so the purge
-- runs with the owner's rights while app, which may neither update nor delete a record (V11), may
-- only call it. Both bodies are SQL-standard, so PostgreSQL binds their names when they are
-- created, not when they run; the tables and functions are qualified, pg_catalog's included, so the
-- migrating session's search_path can't bind one to anything else either.

-- The instant before which a record is past its retention: the same Israel local time a year
-- earlier, as AuditRecord.retentionCutoff computes it (ADR-149 decision 5). It converts to the zone
-- and back by name, so the session's TimeZone never matters, and a year before 29 February is 28
-- February. A local time the clocks skipped or repeated could stand for two instants, and the
-- cutoff is the earlier, so no record goes before its year is up. PostgreSQL reads such a time as
-- standard time, the later instant, so the cutoff is the least of three readings: the local time
-- itself, and the local time an hour either side, moved back by that hour. Israel's clocks move by
-- an hour, months apart, so one of the three is always the earlier instant, and none is earlier
-- than it. Nobody is granted the function: only the purge, running as its owner, calls it.
CREATE FUNCTION audit.retention_cutoff(asked_at timestamptz) RETURNS timestamptz
    LANGUAGE sql
    STABLE
    STRICT
    PARALLEL SAFE
    RETURN (
        SELECT LEAST(
            pg_catalog.timezone('Asia/Jerusalem', year_back.local_cutoff),
            pg_catalog.timezone('Asia/Jerusalem', year_back.local_cutoff - interval '1 hour') + interval '1 hour',
            pg_catalog.timezone('Asia/Jerusalem', year_back.local_cutoff + interval '1 hour') - interval '1 hour')
        FROM (SELECT pg_catalog.timezone('Asia/Jerusalem', asked_at) - interval '1 year') AS year_back (local_cutoff));

REVOKE ALL ON FUNCTION audit.retention_cutoff(timestamptz) FROM PUBLIC;

-- Deletes every audit record made before the cutoff of the database's own now(), the named records
-- with it by cascade, and returns how many records it deleted. It takes no date, so no caller can
-- pass one (ADR-041). It runs as its owner, with its search_path fixed to the catalog alone. Its
-- cutoff is a STABLE expression, so the DELETE can read audit_record_at.
CREATE FUNCTION audit.purge_expired_records() RETURNS integer
    LANGUAGE sql
    SECURITY DEFINER
    SET search_path = pg_catalog, pg_temp
BEGIN ATOMIC
    WITH purged AS (
        DELETE FROM audit.audit_record
        WHERE at < audit.retention_cutoff(pg_catalog.now())
        RETURNING 1
    )
    SELECT pg_catalog.count(*)::integer FROM purged;
END;

-- V11 already keeps every new function from PUBLIC; this says so for the one function app may run,
-- the only one on Audit it may (ADR-041, ADR-075).
REVOKE ALL ON FUNCTION audit.purge_expired_records() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION audit.purge_expired_records() TO app;
