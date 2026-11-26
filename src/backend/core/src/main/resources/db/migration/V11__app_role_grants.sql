-- What app, the role the core connects as, may do on the tables of V2 to V10 (ADR-041, ADR-145).
-- Flyway runs as dispatchflow_owner, which owns every schema and table, so app gets only what its
-- adapters run, table by table, and from here on each migration grants on the tables it creates
-- (ADR-127 decision 8). Saving a root through VersionedSave and every SELECT ... FOR UPDATE take a
-- row lock, which needs UPDATE. A foreign key's check and its ON DELETE CASCADE run as the table's
-- owner, and an identity column's default needs no grant on its sequence, so app needs neither.

GRANT USAGE ON SCHEMA access, audit, fleet, liveops, messaging, orders, planning, reporting TO app;

-- PostgreSQL lets every role execute a new function unless its owner says otherwise, so no function
-- the owner creates from here on is open to all: the migration that creates one grants EXECUTE to
-- whoever may call it, as tech_stack.md section 6.3 has it for the audit trail's purge.
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

-- Changed and removed through their root.
GRANT SELECT, INSERT, UPDATE, DELETE ON
    access.credential_link,
    access.session,
    access.refresh_token,
    fleet.driver_weekly_shift,
    fleet.unavailable_vehicle,
    messaging.stop_messages,
    messaging.rating_request_comment,
    planning.route,
    planning.planned_stop,
    planning.plan_drop,
    planning.plan_excluded_vehicle,
    planning.plan_pin,
    planning.plan_violation,
    planning.plan_undo,
    planning.plan_redo,
    reporting.baseline_planned_order
    TO app;

-- Changed, never deleted. An outgoing message goes only with its stop's messages, by cascade.
GRANT SELECT, INSERT, UPDATE ON
    access.user_account,
    access.company_profile,
    fleet.driver,
    fleet.fleet_register,
    fleet.depot,
    fleet.vehicle,
    fleet.day_roster,
    fleet.roster_entry,
    liveops.delivery,
    liveops.delivery_outcome,
    liveops.held_report,
    liveops.route_run,
    liveops.alert,
    messaging.outgoing_message,
    messaging.rating_request,
    messaging.messaging_settings,
    orders.customer,
    orders.customer_order,
    orders.import_batch,
    planning.solve_job,
    planning.reoptimization_proposal,
    planning.planning_settings,
    planning.plan,
    reporting.baseline
    TO app;

-- Added and removed, never changed: a set's rows, which Hibernate replaces instead of updating, the
-- kept positions and replies that are purged, and an import's rows, which go once the import is
-- confirmed, discarded or expired; the import itself is never deleted.
GRANT SELECT, INSERT, DELETE ON
    access.user_account_role,
    access.session_contact,
    orders.customer_address_flag,
    orders.import_row,
    liveops.kept_position,
    messaging.inbound_reply
    TO app;

-- Appended once and kept as written.
GRANT SELECT, INSERT ON
    liveops.route_run_pending_stop,
    messaging.counted_answer,
    messaging.opt_out,
    reporting.baseline_outcome,
    reporting.baseline_withdrawal,
    reporting.baseline_added_order,
    planning.solve_job_route,
    planning.solve_job_stop,
    planning.solve_job_drop,
    planning.solve_job_violation,
    planning.solve_job_excluded_vehicle,
    planning.reoptimization_proposal_route,
    planning.reoptimization_proposal_stop,
    planning.reoptimization_proposal_drop,
    planning.reoptimization_proposal_excluded_vehicle,
    planning.reoptimization_proposal_changed_order,
    planning.reoptimization_proposal_driver_change,
    planning.reoptimization_proposal_goods_hand_over,
    planning.reoptimization_proposal_late_stop,
    planning.reoptimization_proposal_vehicle_without_position,
    planning.reoptimization_proposal_withdrawal
    TO app;

-- The audit trail: written and read, never changed or deleted by the core (ADR-041). Its purge
-- function comes with #180, in a migration of its own.
GRANT SELECT, INSERT ON audit.audit_record, audit.audit_record_named_record TO app;

-- A published plan's revisions: written, and read by nothing yet.
GRANT INSERT ON planning.plan_revision, planning.plan_revision_route, planning.plan_revision_stop TO app;
