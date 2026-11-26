-- Test only: what app, the role the tests' core connects as, runs on the fixture aggregate that
-- VersionedSaveIT saves (ADR-145). V0_1 creates its tables as dispatchflow_owner and grants nothing.
-- A ledger is never deleted, and its entries are added and reweighed but never removed.
GRANT USAGE ON SCHEMA persistence_fixture TO app;
GRANT SELECT, INSERT, UPDATE ON persistence_fixture.ledger, persistence_fixture.ledger_entry TO app;
