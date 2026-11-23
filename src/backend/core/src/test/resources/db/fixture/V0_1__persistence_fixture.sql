-- Test only: the tables of the fixture aggregate that VersionedSaveIT saves (ADR-126). Versions
-- V0_* are reserved for test fixtures, so no real migration ever takes one.
CREATE SCHEMA persistence_fixture;

CREATE TABLE persistence_fixture.ledger (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    name text NOT NULL,
    status text NOT NULL,
    booked_for date NOT NULL,
    window_start timestamp(6) NOT NULL,
    window_end timestamp(6) NOT NULL,
    depot_latitude double precision,
    depot_longitude double precision,
    priority integer NOT NULL,
    plan uuid,
    phone text,
    cause text,
    CONSTRAINT ledger_status_known CHECK (status IN ('OPEN', 'CLOSED')),
    -- Lets VersionedSaveIT make the database refuse a row Hibernate writes.
    CONSTRAINT ledger_name_allowed CHECK (name <> 'Refused by the database')
);

CREATE TABLE persistence_fixture.ledger_entry (
    id uuid PRIMARY KEY,
    ledger_id uuid NOT NULL REFERENCES persistence_fixture.ledger (id),
    entry_index integer NOT NULL,
    amount numeric(9, 3) NOT NULL,
    attempt integer NOT NULL,
    volume numeric(9, 3),
    due_date date,
    due_seconds bigint,
    -- TimeWindow's own column names, which an entity that embeds one window needs no override for.
    starts_at timestamp(6),
    ends_at timestamp(6)
);
