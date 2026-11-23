-- Audit's tables (ADR-126, ADR-127): the append-only trail of audit records (NFR-23, NFR-93), each
-- holding only IDs, codes, enums, numbers and instants (NFR-94, ADR-110). No grants yet, and no purge
-- function: both come with ADR-041's roles and the migrate step (ADR-127 decision 8).

-- One audit record, keyed by a random UUID the adapter draws, so two records with the same content
-- are two rows (ADR-127 decision 6).
CREATE TABLE audit.audit_record (
    id uuid PRIMARY KEY,
    at timestamptz NOT NULL,
    account uuid,
    session uuid,
    app text,
    app_version text,
    operation text NOT NULL,
    outcome text NOT NULL,
    error_code text,
    trace_id text NOT NULL,
    -- A client declares its app and its version together, or neither (ADR-110).
    CONSTRAINT audit_record_app_whole CHECK ((app IS NULL) = (app_version IS NULL)),
    CONSTRAINT audit_record_app_known CHECK (app IN ('WEB', 'PHONE')),
    CONSTRAINT audit_record_outcome_known CHECK (outcome IN ('SUCCEEDED', 'REFUSED')),
    CONSTRAINT audit_record_refused_has_code CHECK ((outcome = 'REFUSED') = (error_code IS NOT NULL))
);

-- The purge deletes the records older than a year (NFR-258, ADR-075).
CREATE INDEX audit_record_at ON audit.audit_record (at);

-- The records an audit record names, in the order given (NFR-93), deleted with it.
CREATE TABLE audit.audit_record_named_record (
    audit_record_id uuid NOT NULL REFERENCES audit.audit_record (id) ON DELETE CASCADE,
    position integer NOT NULL,
    kind text NOT NULL,
    record_id uuid NOT NULL,
    CONSTRAINT audit_record_named_record_kind_known CHECK (kind IN (
        'ACCOUNT', 'SESSION', 'DEPOT', 'VEHICLE', 'CUSTOMER', 'ORDER', 'IMPORT_BATCH', 'PLAN', 'ROUTE', 'STOP',
        'JOB', 'PROPOSAL', 'ALERT', 'RATING_REQUEST')),
    CONSTRAINT audit_record_named_record_position UNIQUE (audit_record_id, position)
        DEFERRABLE INITIALLY DEFERRED
);
