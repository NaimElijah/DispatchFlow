-- One schema per module, named after its package (ADR-041, ADR-126). Each module's tables come in
-- its own migration, V2 to V9 (ADR-127 decision 9). No grants yet: ADR-041's roles don't exist, and
-- the change that adds them grants on every table made before it (ADR-127 decision 8).
CREATE SCHEMA access;
CREATE SCHEMA audit;
CREATE SCHEMA fleet;
CREATE SCHEMA liveops;
CREATE SCHEMA messaging;
CREATE SCHEMA orders;
CREATE SCHEMA planning;
CREATE SCHEMA reporting;
