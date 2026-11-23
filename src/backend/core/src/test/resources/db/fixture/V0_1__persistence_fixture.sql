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
    CONSTRAINT ledger_status_known CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE TABLE persistence_fixture.ledger_entry (
    id uuid PRIMARY KEY,
    ledger_id uuid NOT NULL REFERENCES persistence_fixture.ledger (id),
    entry_index integer NOT NULL,
    amount numeric(9, 3) NOT NULL
);
