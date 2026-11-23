-- Every stored list's positions start at 0 (ADR-127 decision 1, #249). Hibernate rebuilds an
-- ordered list from its rows' positions and pads each missing one with null, which every root's
-- load check refuses as a gap (StoredLists). A negative position never gets that far: Hibernate
-- throws IndexOutOfBoundsException while it loads the list, so the schema refuses one instead.
-- Hibernate's own draft of the mapping (just core-schema-draft) has this check on each column.

ALTER TABLE access.credential_link
    ADD CONSTRAINT credential_link_not_negative CHECK (position >= 0);
ALTER TABLE access.refresh_token ADD CONSTRAINT refresh_token_not_negative CHECK (position >= 0);

ALTER TABLE audit.audit_record_named_record
    ADD CONSTRAINT audit_record_named_record_not_negative CHECK (position >= 0);

ALTER TABLE fleet.depot ADD CONSTRAINT depot_not_negative CHECK (position >= 0);
ALTER TABLE fleet.roster_entry ADD CONSTRAINT roster_entry_not_negative CHECK (position >= 0);
ALTER TABLE fleet.unavailable_vehicle
    ADD CONSTRAINT unavailable_vehicle_not_negative CHECK (position >= 0);
ALTER TABLE fleet.vehicle ADD CONSTRAINT vehicle_not_negative CHECK (position >= 0);

ALTER TABLE liveops.delivery_outcome
    ADD CONSTRAINT delivery_outcome_not_negative CHECK (position >= 0);
ALTER TABLE liveops.held_report ADD CONSTRAINT held_report_not_negative CHECK (position >= 0);
ALTER TABLE liveops.route_run_pending_stop
    ADD CONSTRAINT route_run_pending_stop_not_negative CHECK (position >= 0);

ALTER TABLE messaging.outgoing_message
    ADD CONSTRAINT outgoing_message_not_negative CHECK (position >= 0);
ALTER TABLE messaging.rating_request_comment
    ADD CONSTRAINT rating_request_comment_not_negative CHECK (position >= 0);

ALTER TABLE orders.import_row ADD CONSTRAINT import_row_not_negative CHECK (position >= 0);

ALTER TABLE planning.plan_drop ADD CONSTRAINT plan_drop_not_negative CHECK (position >= 0);
ALTER TABLE planning.plan_excluded_vehicle
    ADD CONSTRAINT plan_excluded_vehicle_not_negative CHECK (position >= 0);
ALTER TABLE planning.plan_pin ADD CONSTRAINT plan_pin_not_negative CHECK (position >= 0);
ALTER TABLE planning.plan_redo ADD CONSTRAINT plan_redo_not_negative CHECK (position >= 0);
ALTER TABLE planning.plan_revision_route
    ADD CONSTRAINT plan_revision_route_not_negative CHECK (position >= 0);
ALTER TABLE planning.plan_revision_stop
    ADD CONSTRAINT plan_revision_stop_not_negative CHECK (position >= 0);
ALTER TABLE planning.plan_undo ADD CONSTRAINT plan_undo_not_negative CHECK (position >= 0);
ALTER TABLE planning.plan_violation
    ADD CONSTRAINT plan_violation_not_negative CHECK (position >= 0);
ALTER TABLE planning.planned_stop ADD CONSTRAINT planned_stop_not_negative CHECK (position >= 0);
ALTER TABLE planning.reoptimization_proposal_changed_order
    ADD CONSTRAINT reoptimization_proposal_changed_order_not_negative CHECK (position >= 0);
ALTER TABLE planning.reoptimization_proposal_driver_change
    ADD CONSTRAINT reoptimization_proposal_driver_change_not_negative CHECK (position >= 0);
ALTER TABLE planning.reoptimization_proposal_drop
    ADD CONSTRAINT reoptimization_proposal_drop_not_negative CHECK (position >= 0);
ALTER TABLE planning.reoptimization_proposal_excluded_vehicle
    ADD CONSTRAINT reoptimization_proposal_excluded_vehicle_not_negative CHECK (position >= 0);
ALTER TABLE planning.reoptimization_proposal_goods_hand_over
    ADD CONSTRAINT reoptimization_proposal_goods_hand_over_not_negative CHECK (position >= 0);
ALTER TABLE planning.reoptimization_proposal_late_stop
    ADD CONSTRAINT reoptimization_proposal_late_stop_not_negative CHECK (position >= 0);
ALTER TABLE planning.reoptimization_proposal_route
    ADD CONSTRAINT reoptimization_proposal_route_not_negative CHECK (position >= 0);
ALTER TABLE planning.reoptimization_proposal_stop
    ADD CONSTRAINT reoptimization_proposal_stop_not_negative CHECK (position >= 0);
ALTER TABLE planning.reoptimization_proposal_vehicle_without_position
    ADD CONSTRAINT reoptimization_proposal_vehicle_without_position_not_negative CHECK (position >= 0);
ALTER TABLE planning.reoptimization_proposal_withdrawal
    ADD CONSTRAINT reoptimization_proposal_withdrawal_not_negative CHECK (position >= 0);
ALTER TABLE planning.route ADD CONSTRAINT route_not_negative CHECK (position >= 0);
ALTER TABLE planning.solve_job_drop
    ADD CONSTRAINT solve_job_drop_not_negative CHECK (position >= 0);
ALTER TABLE planning.solve_job_excluded_vehicle
    ADD CONSTRAINT solve_job_excluded_vehicle_not_negative CHECK (position >= 0);
ALTER TABLE planning.solve_job_route
    ADD CONSTRAINT solve_job_route_not_negative CHECK (position >= 0);
ALTER TABLE planning.solve_job_stop
    ADD CONSTRAINT solve_job_stop_not_negative CHECK (position >= 0);
ALTER TABLE planning.solve_job_violation
    ADD CONSTRAINT solve_job_violation_not_negative CHECK (position >= 0);

ALTER TABLE reporting.baseline_added_order
    ADD CONSTRAINT baseline_added_order_not_negative CHECK (position >= 0);
ALTER TABLE reporting.baseline_planned_order
    ADD CONSTRAINT baseline_planned_order_not_negative CHECK (position >= 0);
ALTER TABLE reporting.baseline_withdrawal
    ADD CONSTRAINT baseline_withdrawal_not_negative CHECK (position >= 0);
