-- Creates DispatchFlow's two database roles (ADR-041, ADR-145). The postgres image runs every file in
-- /docker-entrypoint-initdb.d once, through psql as its superuser, and only while the data folder is
-- empty: a database that already exists keeps the roles it has, so a change here reaches it only
-- after `just db-reset` (#172). A run that stops leaves the data folder made but the roles missing,
-- and the next start skips this file, so remove the data folder before starting again. Outside the
-- image, run it once as a superuser connected to the core's database, which DBNAME names below.
-- Roles live outside every schema, so no migration can create them. The integration tests run this
-- same folder in their containers (DatabaseRoles).
--
-- Both passwords come from the container's environment (NFR-03), never from the repository. So the
-- postgres container that runs this file holds the owner's password too, one of ADR-145's two
-- exceptions to ADR-041's "only the migrate step holds it"; this file needs both only on that first
-- start.
\set ON_ERROR_STOP on

-- Statements below carry the passwords, so this session turns off every server setting that would
-- log a statement's text or its parse tree, a failed statement's included, short of a crash, which
-- logs the statement it died on (ADR-146). A module the server preloads to record statements, such
-- as pg_stat_statements, pgaudit or auto_explain, keeps a record of its own, which these settings
-- don't reach.
SET log_statement = 'none';
SET log_min_duration_statement = -1;
SET log_min_duration_sample = -1;
SET log_transaction_sample_rate = 0;
SET log_min_error_statement = panic;
-- Each debug_print_ setting logs a parse tree, a password's literal included, and a SET of one a
-- later version adds would fail on a server without it. So the script finds them by name.
DO $$
DECLARE
    setting text;
BEGIN
    FOR setting IN SELECT name FROM pg_catalog.pg_settings WHERE name LIKE 'debug\_print\_%' LOOP
        PERFORM pg_catalog.set_config(setting, 'off', false);
    END LOOP;
END $$;

\getenv owner_password DISPATCHFLOW_OWNER_PASSWORD
\getenv app_password DISPATCHFLOW_APP_PASSWORD

-- Without a password, PostgreSQL would create a role that can't log in and only warn, and the core
-- would fail far from here, so a missing or empty one stops the script before any role exists.
\if :{?owner_password}
\else
\set owner_password ''
\endif
\if :{?app_password}
\else
\set app_password ''
\endif
SELECT :'owner_password' = '' AS owner_password_missing, :'app_password' = '' AS app_password_missing,
    :'owner_password' = :'app_password' AS passwords_equal \gset
\if :owner_password_missing
DO $$ BEGIN RAISE EXCEPTION 'DISPATCHFLOW_OWNER_PASSWORD is not set'; END $$;
\endif
\if :app_password_missing
DO $$ BEGIN RAISE EXCEPTION 'DISPATCHFLOW_APP_PASSWORD is not set'; END $$;
\endif
-- With one password for both, the core's credential would log in as the owner.
\if :passwords_equal
DO $$ BEGIN RAISE EXCEPTION 'DISPATCHFLOW_OWNER_PASSWORD and DISPATCHFLOW_APP_PASSWORD must differ'; END $$;
\endif

-- The migrate step runs Flyway as this role, so it owns every schema and table the migrations create.
-- The attributes are PostgreSQL's defaults, spelled out because neither role may ever pass them.
CREATE ROLE dispatchflow_owner LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
    PASSWORD :'owner_password';

-- The core connects as app, which owns nothing: each migration grants it what its adapters run. An
-- owner can always grant itself back whatever was revoked, so the core must never be one.
CREATE ROLE app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
    PASSWORD :'app_password';

-- V1 creates the module schemas, which needs CREATE on the database, and Flyway keeps its history
-- table in public, where PostgreSQL 15 and later let no ordinary role create. That is all Flyway
-- needs: the database and public stay the superuser's, so the owner can't drop or rename the
-- database, change its settings, or take app's CONNECT away (ADR-145).
GRANT CREATE ON DATABASE :"DBNAME" TO dispatchflow_owner;
GRANT CREATE ON SCHEMA public TO dispatchflow_owner;

-- Those two grants let the owner put a function in public, or in a schema named after the
-- superuser, both on the superuser's default search_path, where it would run with the superuser's
-- rights the next time the superuser called one by an unqualified name. So in this database the
-- superuser looks up names in the catalog alone and qualifies everything else. That stops only a
-- name being captured: code the owner attached to its tables, views or types, a trigger, a default, a
-- constraint, an index expression, a rule or a function a view calls, still runs with the
-- superuser's rights when the superuser writes or reads them (ADR-146).
ALTER ROLE CURRENT_USER IN DATABASE :"DBNAME" SET search_path = pg_catalog, pg_temp;
