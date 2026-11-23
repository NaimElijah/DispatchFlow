-- Planning's tables (ADR-126, ADR-127): the SolveJob, ReoptimizationProposal, PlanningSettings and
-- Plan aggregates, and a plan's revisions. Every duration is stored as its nanoseconds, in a bigint (ADR-127). Jobs, proposals,
-- settings, plans and revisions are never deleted (DL §9.7), so no foreign key cascades; a plan's
-- route and stop rows are deleted only by the plan's own changes. No grants yet (ADR-127 decision 8).

-- One queued or running call to the optimizer, keyed by its JobId (ADR-051). Its phase is a kind
-- beside the times each phase keeps, and a failed job's reason.
CREATE TABLE planning.solve_job (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    plan_id uuid NOT NULL,
    lane text NOT NULL,
    solver text NOT NULL,
    solve_limit bigint NOT NULL,
    seed bigint NOT NULL,
    basis_revision integer,
    requested_at timestamptz NOT NULL,
    phase text NOT NULL,
    claimed_at timestamptz,
    search_started_at timestamptz,
    ended_at timestamptz,
    failure_reason text,
    CONSTRAINT solve_job_lane_known CHECK (lane IN ('DAILY', 'REOPTIMIZATION')),
    CONSTRAINT solve_job_basis_only_for_reoptimization CHECK ((lane = 'REOPTIMIZATION') = (basis_revision IS NOT NULL)),
    CONSTRAINT solve_job_phase_known CHECK (phase IN ('QUEUED', 'BUILDING_MATRIX', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT solve_job_claimed_has_claim CHECK (
        (phase IN ('BUILDING_MATRIX', 'RUNNING', 'SUCCEEDED')) = (claimed_at IS NOT NULL)),
    CONSTRAINT solve_job_searched_has_search CHECK ((phase IN ('RUNNING', 'SUCCEEDED')) = (search_started_at IS NOT NULL)),
    CONSTRAINT solve_job_ended_has_end CHECK ((phase IN ('SUCCEEDED', 'FAILED')) = (ended_at IS NOT NULL)),
    CONSTRAINT solve_job_failed_has_reason CHECK ((phase = 'FAILED') = (failure_reason IS NOT NULL)),
    CONSTRAINT solve_job_failure_reason_known CHECK (failure_reason IN (
        'QUEUED_TOO_LONG', 'MATRIX_TOO_LONG', 'OVERRAN', 'OPTIMIZER_UNAVAILABLE', 'ROAD_NETWORK_UNAVAILABLE',
        'INTERRUPTED', 'DATE_PASSED', 'NO_ORDERS', 'VALIDATOR_REJECTED', 'LOCK_NOT_HONOURED', 'SOLVER_FAILED',
        'NO_SOLUTION_WITHIN_BUDGET', 'REQUEST_REFUSED'))
);

-- One queued or started re-optimization per plan (BR-38), for a race the lane's lock missed.
CREATE UNIQUE INDEX solve_job_one_reoptimization_per_plan ON planning.solve_job (plan_id)
    WHERE lane = 'REOPTIMIZATION' AND phase IN ('QUEUED', 'BUILDING_MATRIX', 'RUNNING');
-- A worker claims its lane's oldest queued job, a request reads its lane's queue, and the watchdog
-- and startup recovery read every unfinished job, the oldest first (ADR-051, BR-112).
CREATE INDEX solve_job_unfinished ON planning.solve_job (lane, requested_at)
    WHERE phase IN ('QUEUED', 'BUILDING_MATRIX', 'RUNNING');

-- A failed job's violations: present exactly for a rejection or an unhonoured lock, which the job's
-- load check enforces, because a CHECK can't read the job's row.
CREATE TABLE planning.solve_job_violation (
    solve_job_id uuid NOT NULL REFERENCES planning.solve_job (id),
    position integer NOT NULL,
    rule text NOT NULL,
    route_id uuid,
    order_id uuid,
    vehicle_id uuid,
    driver_id uuid,
    exceeded_by bigint,
    kilograms numeric,
    litres numeric,
    CONSTRAINT solve_job_violation_position UNIQUE (solve_job_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- A succeeded daily job's result (ADR-101): its routes, their stops, its drops and its excluded
-- vehicles. A route with no stops has no times, so its plan's columns are all present or none.
CREATE TABLE planning.solve_job_route (
    solve_job_id uuid NOT NULL REFERENCES planning.solve_job (id),
    position integer NOT NULL,
    vehicle_id uuid NOT NULL,
    driver_id uuid NOT NULL,
    times_date date,
    leaves_at_seconds bigint,
    returns_at_seconds bigint,
    leg_to_end_travel bigint,
    leg_to_end_metres bigint,
    CONSTRAINT solve_job_route_plan_whole CHECK (
        num_nonnulls(times_date, leaves_at_seconds, returns_at_seconds, leg_to_end_travel, leg_to_end_metres) IN (0, 5)),
    CONSTRAINT solve_job_route_position UNIQUE (solve_job_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- Every route's stops, flattened into one table keyed by the route's position and the stop's own
-- (ADR-127 decision 3); position orders the whole list.
CREATE TABLE planning.solve_job_stop (
    solve_job_id uuid NOT NULL REFERENCES planning.solve_job (id),
    position integer NOT NULL,
    route_position integer NOT NULL,
    stop_position integer NOT NULL,
    order_id uuid NOT NULL,
    attempt integer NOT NULL,
    times_date date NOT NULL,
    arrival_seconds bigint NOT NULL,
    start_seconds bigint NOT NULL,
    departure_seconds bigint NOT NULL,
    leg_in_travel bigint NOT NULL,
    leg_in_metres bigint NOT NULL,
    CONSTRAINT solve_job_stop_position UNIQUE (solve_job_id, position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT solve_job_stop_on_route UNIQUE (solve_job_id, route_position, stop_position)
        DEFERRABLE INITIALLY DEFERRED
);

-- A drop's reason is a kind beside its fact's columns, each present exactly for the reasons that
-- have it (ADR-127 decision 3).
CREATE TABLE planning.solve_job_drop (
    solve_job_id uuid NOT NULL REFERENCES planning.solve_job (id),
    position integer NOT NULL,
    order_id uuid NOT NULL,
    attempt integer NOT NULL,
    reason text NOT NULL,
    pin_vehicle_id uuid,
    pin_driver_id uuid,
    pin_target_out boolean,
    weight numeric,
    volume numeric,
    largest_weight_capacity numeric,
    largest_volume_capacity numeric,
    fact_limit_date date,
    fact_limit_seconds bigint,
    earliest_date date,
    earliest_seconds bigint,
    CONSTRAINT solve_job_drop_reason_known CHECK (reason IN (
        'MISSING_LOCATION', 'NO_VEHICLE_AVAILABLE', 'UNREACHABLE_LOCATION', 'REMOVED_BY_DISPATCHER',
        'PIN_NOT_HONOURED', 'CAPACITY', 'TIME_WINDOW', 'SHIFT', 'DEPOT_HOURS', 'VEHICLE_LIMIT', 'DISPLACED')),
    CONSTRAINT solve_job_drop_pin_has_one_target CHECK (
        (reason = 'PIN_NOT_HONOURED') = (num_nonnulls(pin_vehicle_id, pin_driver_id) = 1)
        AND num_nonnulls(pin_vehicle_id, pin_driver_id) <= 1),
    CONSTRAINT solve_job_drop_pin_says_if_out CHECK ((reason = 'PIN_NOT_HONOURED') = (pin_target_out IS NOT NULL)),
    CONSTRAINT solve_job_drop_capacity_has_figures CHECK (
        (reason = 'CAPACITY') = (weight IS NOT NULL)
        AND num_nonnulls(weight, volume, largest_weight_capacity, largest_volume_capacity) IN (0, 4)),
    CONSTRAINT solve_job_drop_time_has_fact CHECK (
        (reason IN ('TIME_WINDOW', 'SHIFT', 'DEPOT_HOURS')) = (fact_limit_date IS NOT NULL)
        AND num_nonnulls(fact_limit_date, fact_limit_seconds, earliest_date, earliest_seconds) IN (0, 4)),
    CONSTRAINT solve_job_drop_position UNIQUE (solve_job_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE planning.solve_job_excluded_vehicle (
    solve_job_id uuid NOT NULL REFERENCES planning.solve_job (id),
    position integer NOT NULL,
    vehicle_id uuid NOT NULL,
    cause text NOT NULL,
    CONSTRAINT solve_job_excluded_vehicle_cause_known CHECK (cause IN (
        'DEPOT_WITHOUT_LOCATION', 'NO_ROAD_BETWEEN_DEPOTS', 'VEHICLE_UNAVAILABLE', 'NO_DRIVER', 'SHIFT_RAN_OUT',
        'ROUTE_FINISHED', 'WITHDRAWN')),
    CONSTRAINT solve_job_excluded_vehicle_position UNIQUE (solve_job_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- A re-optimization's result waiting for a dispatcher, keyed by its ProposalId. Its state is a kind
-- beside who declined it and when.
CREATE TABLE planning.reoptimization_proposal (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    plan_id uuid NOT NULL,
    job_id uuid NOT NULL,
    basis_revision integer NOT NULL,
    basis_facts_at timestamptz NOT NULL,
    objective_travel bigint NOT NULL,
    objective_drops bigint NOT NULL,
    objective_door_window bigint NOT NULL,
    objective_fixed_vehicles bigint NOT NULL,
    objective_lateness bigint NOT NULL,
    objective_orphaned_stops bigint NOT NULL,
    objective_moved_stops bigint NOT NULL,
    created_at timestamptz NOT NULL,
    state text NOT NULL,
    declined_by uuid,
    declined_at timestamptz,
    CONSTRAINT reoptimization_proposal_state_known CHECK (state IN ('READY', 'DECLINED')),
    CONSTRAINT reoptimization_proposal_declined_by_whom CHECK ((state = 'DECLINED') = (declined_by IS NOT NULL)),
    CONSTRAINT reoptimization_proposal_declined_when CHECK ((state = 'DECLINED') = (declined_at IS NOT NULL))
);

-- A proposal's routes and their stops, stored as a daily job's result is.
CREATE TABLE planning.reoptimization_proposal_route (
    proposal_id uuid NOT NULL REFERENCES planning.reoptimization_proposal (id),
    position integer NOT NULL,
    vehicle_id uuid NOT NULL,
    driver_id uuid NOT NULL,
    times_date date,
    leaves_at_seconds bigint,
    returns_at_seconds bigint,
    leg_to_end_travel bigint,
    leg_to_end_metres bigint,
    CONSTRAINT reoptimization_proposal_route_plan_whole CHECK (
        num_nonnulls(times_date, leaves_at_seconds, returns_at_seconds, leg_to_end_travel, leg_to_end_metres) IN (0, 5)),
    CONSTRAINT reoptimization_proposal_route_position UNIQUE (proposal_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE planning.reoptimization_proposal_stop (
    proposal_id uuid NOT NULL REFERENCES planning.reoptimization_proposal (id),
    position integer NOT NULL,
    route_position integer NOT NULL,
    stop_position integer NOT NULL,
    order_id uuid NOT NULL,
    attempt integer NOT NULL,
    times_date date NOT NULL,
    arrival_seconds bigint NOT NULL,
    start_seconds bigint NOT NULL,
    departure_seconds bigint NOT NULL,
    leg_in_travel bigint NOT NULL,
    leg_in_metres bigint NOT NULL,
    CONSTRAINT reoptimization_proposal_stop_position UNIQUE (proposal_id, position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT reoptimization_proposal_stop_on_route UNIQUE (proposal_id, route_position, stop_position)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE planning.reoptimization_proposal_drop (
    proposal_id uuid NOT NULL REFERENCES planning.reoptimization_proposal (id),
    position integer NOT NULL,
    order_id uuid NOT NULL,
    attempt integer NOT NULL,
    reason text NOT NULL,
    pin_vehicle_id uuid,
    pin_driver_id uuid,
    pin_target_out boolean,
    weight numeric,
    volume numeric,
    largest_weight_capacity numeric,
    largest_volume_capacity numeric,
    fact_limit_date date,
    fact_limit_seconds bigint,
    earliest_date date,
    earliest_seconds bigint,
    CONSTRAINT reoptimization_proposal_drop_reason_known CHECK (reason IN (
        'MISSING_LOCATION', 'NO_VEHICLE_AVAILABLE', 'UNREACHABLE_LOCATION', 'REMOVED_BY_DISPATCHER',
        'PIN_NOT_HONOURED', 'CAPACITY', 'TIME_WINDOW', 'SHIFT', 'DEPOT_HOURS', 'VEHICLE_LIMIT', 'DISPLACED')),
    CONSTRAINT reoptimization_proposal_drop_pin_has_one_target CHECK (
        (reason = 'PIN_NOT_HONOURED') = (num_nonnulls(pin_vehicle_id, pin_driver_id) = 1)
        AND num_nonnulls(pin_vehicle_id, pin_driver_id) <= 1),
    CONSTRAINT reoptimization_proposal_drop_pin_says_if_out CHECK (
        (reason = 'PIN_NOT_HONOURED') = (pin_target_out IS NOT NULL)),
    CONSTRAINT reoptimization_proposal_drop_capacity_has_figures CHECK (
        (reason = 'CAPACITY') = (weight IS NOT NULL)
        AND num_nonnulls(weight, volume, largest_weight_capacity, largest_volume_capacity) IN (0, 4)),
    CONSTRAINT reoptimization_proposal_drop_time_has_fact CHECK (
        (reason IN ('TIME_WINDOW', 'SHIFT', 'DEPOT_HOURS')) = (fact_limit_date IS NOT NULL)
        AND num_nonnulls(fact_limit_date, fact_limit_seconds, earliest_date, earliest_seconds) IN (0, 4)),
    CONSTRAINT reoptimization_proposal_drop_position UNIQUE (proposal_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE planning.reoptimization_proposal_excluded_vehicle (
    proposal_id uuid NOT NULL REFERENCES planning.reoptimization_proposal (id),
    position integer NOT NULL,
    vehicle_id uuid NOT NULL,
    cause text NOT NULL,
    CONSTRAINT reoptimization_proposal_excluded_vehicle_cause_known CHECK (cause IN (
        'DEPOT_WITHOUT_LOCATION', 'NO_ROAD_BETWEEN_DEPOTS', 'VEHICLE_UNAVAILABLE', 'NO_DRIVER', 'SHIFT_RAN_OUT',
        'ROUTE_FINISHED', 'WITHDRAWN')),
    CONSTRAINT reoptimization_proposal_excluded_vehicle_position UNIQUE (proposal_id, position)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE planning.reoptimization_proposal_withdrawal (
    proposal_id uuid NOT NULL REFERENCES planning.reoptimization_proposal (id),
    position integer NOT NULL,
    vehicle_id uuid NOT NULL,
    cause text NOT NULL,
    CONSTRAINT reoptimization_proposal_withdrawal_cause_known CHECK (
        cause IN ('VEHICLE_UNAVAILABLE', 'NO_DRIVER', 'SHIFT_RAN_OUT')),
    CONSTRAINT reoptimization_proposal_withdrawal_position UNIQUE (proposal_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE planning.reoptimization_proposal_late_stop (
    proposal_id uuid NOT NULL REFERENCES planning.reoptimization_proposal (id),
    position integer NOT NULL,
    order_id uuid NOT NULL,
    attempt integer NOT NULL,
    late bigint NOT NULL,
    CONSTRAINT reoptimization_proposal_late_stop_position UNIQUE (proposal_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE planning.reoptimization_proposal_vehicle_without_position (
    proposal_id uuid NOT NULL REFERENCES planning.reoptimization_proposal (id),
    position integer NOT NULL,
    vehicle_id uuid NOT NULL,
    CONSTRAINT reoptimization_proposal_vehicle_without_position_position UNIQUE (proposal_id, position)
        DEFERRABLE INITIALLY DEFERRED
);

-- Each driver change's driver, and every change's added, removed and reordered orders flattened into
-- one table keyed by the change's position, the list and the order's place in it (ADR-127 decision 3).
CREATE TABLE planning.reoptimization_proposal_driver_change (
    proposal_id uuid NOT NULL REFERENCES planning.reoptimization_proposal (id),
    position integer NOT NULL,
    driver_id uuid NOT NULL,
    CONSTRAINT reoptimization_proposal_driver_change_position UNIQUE (proposal_id, position)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE planning.reoptimization_proposal_changed_order (
    proposal_id uuid NOT NULL REFERENCES planning.reoptimization_proposal (id),
    position integer NOT NULL,
    driver_change integer NOT NULL,
    change text NOT NULL,
    order_id uuid NOT NULL,
    CONSTRAINT reoptimization_proposal_changed_order_change_known CHECK (change IN ('ADDED', 'REMOVED', 'REORDERED')),
    CONSTRAINT reoptimization_proposal_changed_order_position UNIQUE (proposal_id, position)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE planning.reoptimization_proposal_goods_hand_over (
    proposal_id uuid NOT NULL REFERENCES planning.reoptimization_proposal (id),
    position integer NOT NULL,
    order_id uuid NOT NULL,
    CONSTRAINT reoptimization_proposal_goods_hand_over_position UNIQUE (proposal_id, position)
        DEFERRABLE INITIALLY DEFERRED
);

-- The one row of the company's planning settings, under PlanningSettings.KEY. A fixed vehicle cost
-- is a kind beside the cost an enabled one has.
CREATE TABLE planning.planning_settings (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    morning_peak_start time NOT NULL,
    morning_peak_end time NOT NULL,
    morning_peak_factor numeric NOT NULL,
    evening_peak_start time NOT NULL,
    evening_peak_end time NOT NULL,
    evening_peak_factor numeric NOT NULL,
    off_peak_factor numeric NOT NULL,
    buffer_percent numeric NOT NULL,
    default_solve_limit bigint NOT NULL,
    fixed_vehicle_cost text NOT NULL,
    fixed_vehicle_cost_per_vehicle bigint,
    departure_allowance_minutes integer NOT NULL,
    CONSTRAINT planning_settings_one_row CHECK (id = '416e88e3-1d2b-4859-bc69-3a80ed927c46'),
    CONSTRAINT planning_settings_fixed_vehicle_cost_known CHECK (fixed_vehicle_cost IN ('OFF', 'ON')),
    CONSTRAINT planning_settings_enabled_cost_has_duration CHECK (
        (fixed_vehicle_cost = 'ON') = (fixed_vehicle_cost_per_vehicle IS NOT NULL))
);

-- One delivery date's plan, keyed by its PlanId (DL §4.4). Its status is a kind beside what each
-- status keeps: a solving plan's basis, the current revision of a published, completed or cancelled
-- plan, and when a plan was cancelled. Its applied solve is three columns, all of them or none.
CREATE TABLE planning.plan (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    delivery_date date NOT NULL,
    opened_at timestamptz NOT NULL,
    status text NOT NULL,
    solving_job_id uuid,
    solving_requested_at timestamptz,
    revision integer,
    cancelled_at timestamptz,
    applied_job_id uuid,
    applied_requested_at timestamptz,
    applied_at timestamptz,
    CONSTRAINT plan_status_known CHECK (
        status IN ('DRAFT', 'SOLVING', 'READY', 'PUBLISHED', 'COMPLETED', 'CANCELLED', 'ABANDONED')),
    CONSTRAINT plan_solving_has_basis CHECK (
        (status = 'SOLVING') = (solving_job_id IS NOT NULL)
        AND (solving_job_id IS NULL) = (solving_requested_at IS NULL)),
    CONSTRAINT plan_published_has_revision CHECK (
        (status IN ('PUBLISHED', 'COMPLETED', 'CANCELLED')) = (revision IS NOT NULL)),
    CONSTRAINT plan_cancelled_has_time CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    CONSTRAINT plan_applied_whole CHECK (num_nonnulls(applied_job_id, applied_requested_at, applied_at) IN (0, 3))
);

-- One uncancelled plan per date (BR-34). Adding a plan takes the date's advisory lock and reads the
-- date's plan first, so this index is the backstop for a write that skipped the lock.
CREATE UNIQUE INDEX plan_one_uncancelled_per_date ON planning.plan (delivery_date) WHERE status <> 'CANCELLED';
-- The midnight job reads a date's plans and their unfinished jobs (BR-102, BR-127, BR-128).
CREATE INDEX plan_by_date ON planning.plan (delivery_date);
CREATE INDEX solve_job_by_plan ON planning.solve_job (plan_id);

-- A plan's routes, in its order, each keyed by its RouteId. A route's times are five columns, all of
-- them or none, and its confirmed return a date and seconds, both or neither. A route a solve or an
-- acceptance leaves out is deleted with the stops no other route took.
CREATE TABLE planning.route (
    id uuid PRIMARY KEY,
    plan_id uuid NOT NULL REFERENCES planning.plan (id),
    position integer NOT NULL,
    route_index integer NOT NULL,
    vehicle_id uuid NOT NULL,
    driver_id uuid NOT NULL,
    route_lock text,
    check_state text NOT NULL,
    withdrawal_cause text,
    confirmed_return_date date,
    confirmed_return_seconds bigint,
    times_date date,
    leaves_at_seconds bigint,
    returns_at_seconds bigint,
    leg_to_end_travel bigint,
    leg_to_end_metres bigint,
    CONSTRAINT route_lock_known CHECK (route_lock IN ('WHOLE_ROUTE', 'STOP_SEQUENCE')),
    CONSTRAINT route_check_state_known CHECK (check_state IN ('CHECKED', 'NOT_YET_CHECKED')),
    CONSTRAINT route_withdrawal_cause_known CHECK (
        withdrawal_cause IN ('VEHICLE_UNAVAILABLE', 'NO_DRIVER', 'SHIFT_RAN_OUT')),
    CONSTRAINT route_confirmed_return_whole CHECK (
        num_nonnulls(confirmed_return_date, confirmed_return_seconds) IN (0, 2)),
    CONSTRAINT route_plan_whole CHECK (
        num_nonnulls(times_date, leaves_at_seconds, returns_at_seconds, leg_to_end_travel, leg_to_end_metres) IN (0, 5)),
    CONSTRAINT route_position UNIQUE (plan_id, position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT route_of_its_plan UNIQUE (id, plan_id),
    -- A plan's route indexes are unique (DL §4.4), which the load check also enforces. Deferred,
    -- because a solve inserts its routes before it deletes those they replace.
    CONSTRAINT route_index_once UNIQUE (plan_id, route_index) DEFERRABLE INITIALLY DEFERRED
);

-- The re-check job reads the ready and published plans with a route not yet checked (FR-395).
CREATE INDEX route_not_yet_checked ON planning.route (plan_id) WHERE check_state = 'NOT_YET_CHECKED';

-- A route's stops, in its order, each keyed by its StopId, which it keeps while it moves between
-- routes. The plan's own collection creates and deletes a stop, through plan_id, and its route's
-- list places it, through route_id and position; the foreign key keeps the two on one plan. It is
-- deferred, because Hibernate may insert a stop before its new route and delete a route before
-- the stops leaving with it. A stop's times are six columns, all of them or none.
CREATE TABLE planning.planned_stop (
    id uuid PRIMARY KEY,
    plan_id uuid NOT NULL REFERENCES planning.plan (id),
    route_id uuid NOT NULL,
    position integer NOT NULL,
    order_id uuid NOT NULL,
    attempt integer NOT NULL,
    times_date date,
    arrival_seconds bigint,
    start_seconds bigint,
    departure_seconds bigint,
    leg_in_travel bigint,
    leg_in_metres bigint,
    late text,
    CONSTRAINT planned_stop_plan_whole CHECK (
        num_nonnulls(times_date, arrival_seconds, start_seconds, departure_seconds, leg_in_travel, leg_in_metres)
        IN (0, 6)),
    CONSTRAINT planned_stop_late_known CHECK (late IN ('SERVED_LATE', 'SERVED_AFTER_CANCELLATION')),
    CONSTRAINT planned_stop_position UNIQUE (route_id, position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT planned_stop_on_a_route_of_its_plan FOREIGN KEY (route_id, plan_id)
        REFERENCES planning.route (id, plan_id) DEFERRABLE INITIALLY DEFERRED,
    -- A plan serves an order and attempt at most once (DL §4.4); the load check also keeps a served
    -- one out of the drops. Deferred, because Hibernate inserts a new stop before it deletes the one
    -- it replaces.
    CONSTRAINT planned_stop_order_once UNIQUE (plan_id, order_id, attempt) DEFERRABLE INITIALLY DEFERRED
);
-- A plan's stops, which its load fetches as one collection.
CREATE INDEX planned_stop_by_plan ON planning.planned_stop (plan_id);

-- A plan's drops, ordered by order and attempt, stored as a job's result stores its drops.
CREATE TABLE planning.plan_drop (
    plan_id uuid NOT NULL REFERENCES planning.plan (id),
    position integer NOT NULL,
    order_id uuid NOT NULL,
    attempt integer NOT NULL,
    reason text NOT NULL,
    pin_vehicle_id uuid,
    pin_driver_id uuid,
    pin_target_out boolean,
    weight numeric,
    volume numeric,
    largest_weight_capacity numeric,
    largest_volume_capacity numeric,
    fact_limit_date date,
    fact_limit_seconds bigint,
    earliest_date date,
    earliest_seconds bigint,
    CONSTRAINT plan_drop_reason_known CHECK (reason IN (
        'MISSING_LOCATION', 'NO_VEHICLE_AVAILABLE', 'UNREACHABLE_LOCATION', 'REMOVED_BY_DISPATCHER',
        'PIN_NOT_HONOURED', 'CAPACITY', 'TIME_WINDOW', 'SHIFT', 'DEPOT_HOURS', 'VEHICLE_LIMIT', 'DISPLACED')),
    CONSTRAINT plan_drop_pin_has_one_target CHECK (
        (reason = 'PIN_NOT_HONOURED') = (num_nonnulls(pin_vehicle_id, pin_driver_id) = 1)
        AND num_nonnulls(pin_vehicle_id, pin_driver_id) <= 1),
    CONSTRAINT plan_drop_pin_says_if_out CHECK ((reason = 'PIN_NOT_HONOURED') = (pin_target_out IS NOT NULL)),
    CONSTRAINT plan_drop_capacity_has_figures CHECK (
        (reason = 'CAPACITY') = (weight IS NOT NULL)
        AND num_nonnulls(weight, volume, largest_weight_capacity, largest_volume_capacity) IN (0, 4)),
    CONSTRAINT plan_drop_time_has_fact CHECK (
        (reason IN ('TIME_WINDOW', 'SHIFT', 'DEPOT_HOURS')) = (fact_limit_date IS NOT NULL)
        AND num_nonnulls(fact_limit_date, fact_limit_seconds, earliest_date, earliest_seconds) IN (0, 4)),
    CONSTRAINT plan_drop_position UNIQUE (plan_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE planning.plan_excluded_vehicle (
    plan_id uuid NOT NULL REFERENCES planning.plan (id),
    position integer NOT NULL,
    vehicle_id uuid NOT NULL,
    cause text NOT NULL,
    CONSTRAINT plan_excluded_vehicle_cause_known CHECK (cause IN (
        'DEPOT_WITHOUT_LOCATION', 'NO_ROAD_BETWEEN_DEPOTS', 'VEHICLE_UNAVAILABLE', 'NO_DRIVER', 'SHIFT_RAN_OUT',
        'ROUTE_FINISHED', 'WITHDRAWN')),
    CONSTRAINT plan_excluded_vehicle_position UNIQUE (plan_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- A plan's pins, ordered by order, each to a vehicle or a driver.
CREATE TABLE planning.plan_pin (
    plan_id uuid NOT NULL REFERENCES planning.plan (id),
    position integer NOT NULL,
    order_id uuid NOT NULL,
    vehicle_id uuid,
    driver_id uuid,
    CONSTRAINT plan_pin_one_target CHECK (num_nonnulls(vehicle_id, driver_id) = 1),
    CONSTRAINT plan_pin_position UNIQUE (plan_id, position) DEFERRABLE INITIALLY DEFERRED,
    -- An order has at most one pin (DL §4.4), which the load check also enforces. Deferred, because
    -- Hibernate rewrites the pins' rows in place, so one order's row can move before another leaves.
    CONSTRAINT plan_pin_order_once UNIQUE (plan_id, order_id) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE planning.plan_violation (
    plan_id uuid NOT NULL REFERENCES planning.plan (id),
    position integer NOT NULL,
    rule text NOT NULL,
    route_id uuid,
    order_id uuid,
    vehicle_id uuid,
    driver_id uuid,
    exceeded_by bigint,
    kilograms numeric,
    litres numeric,
    CONSTRAINT plan_violation_position UNIQUE (plan_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- A plan's undo journal (FR-145), oldest first. Each entry is a kind beside its parts' columns: a
-- move's stop, origin and destination; a removal's stop, order, attempt, origin and times; a
-- placement's stop, order, attempt, destination and the reason the order was dropped for, stored as a
-- drop's is; a pin's order and its target before and after; a lock's route and its lock before and
-- after. The redo journal below has the same shape.
CREATE TABLE planning.plan_undo (
    plan_id uuid NOT NULL REFERENCES planning.plan (id),
    position integer NOT NULL,
    kind text NOT NULL,
    stop_id uuid,
    order_id uuid,
    attempt integer,
    from_route_id uuid,
    from_position integer,
    to_route_id uuid,
    to_position integer,
    route_id uuid,
    times_date date,
    arrival_seconds bigint,
    start_seconds bigint,
    departure_seconds bigint,
    leg_in_travel bigint,
    leg_in_metres bigint,
    reason text,
    pin_vehicle_id uuid,
    pin_driver_id uuid,
    pin_target_out boolean,
    weight numeric,
    volume numeric,
    largest_weight_capacity numeric,
    largest_volume_capacity numeric,
    fact_limit_date date,
    fact_limit_seconds bigint,
    earliest_date date,
    earliest_seconds bigint,
    pin_before_vehicle_id uuid,
    pin_before_driver_id uuid,
    pin_after_vehicle_id uuid,
    pin_after_driver_id uuid,
    lock_before text,
    lock_after text,
    CONSTRAINT plan_undo_kind_known CHECK (
        kind IN ('MOVED_STOP', 'TOOK_OFF', 'PLACED', 'PINNED', 'UNPINNED', 'LOCKED', 'UNLOCKED')),
    CONSTRAINT plan_undo_stop_named CHECK ((kind IN ('MOVED_STOP', 'TOOK_OFF', 'PLACED')) = (stop_id IS NOT NULL)),
    CONSTRAINT plan_undo_order_named CHECK (
        (kind IN ('TOOK_OFF', 'PLACED', 'PINNED', 'UNPINNED')) = (order_id IS NOT NULL)
        AND (kind IN ('TOOK_OFF', 'PLACED')) = (attempt IS NOT NULL)),
    CONSTRAINT plan_undo_origin_named CHECK (
        (kind IN ('MOVED_STOP', 'TOOK_OFF')) = (from_route_id IS NOT NULL)
        AND (from_route_id IS NULL) = (from_position IS NULL)),
    CONSTRAINT plan_undo_destination_named CHECK (
        (kind IN ('MOVED_STOP', 'PLACED')) = (to_route_id IS NOT NULL)
        AND (to_route_id IS NULL) = (to_position IS NULL)),
    CONSTRAINT plan_undo_locked_route_named CHECK ((kind IN ('LOCKED', 'UNLOCKED')) = (route_id IS NOT NULL)),
    CONSTRAINT plan_undo_times_only_taken_off CHECK (
        num_nonnulls(times_date, arrival_seconds, start_seconds, departure_seconds, leg_in_travel, leg_in_metres)
        IN (0, 6)
        AND (kind = 'TOOK_OFF' OR times_date IS NULL)),
    CONSTRAINT plan_undo_reason_only_placed CHECK ((kind = 'PLACED') = (reason IS NOT NULL)),
    CONSTRAINT plan_undo_reason_known CHECK (reason IN (
        'MISSING_LOCATION', 'NO_VEHICLE_AVAILABLE', 'UNREACHABLE_LOCATION', 'REMOVED_BY_DISPATCHER',
        'PIN_NOT_HONOURED', 'CAPACITY', 'TIME_WINDOW', 'SHIFT', 'DEPOT_HOURS', 'VEHICLE_LIMIT', 'DISPLACED')),
    CONSTRAINT plan_undo_reason_pin_has_one_target CHECK (
        (reason IS NOT DISTINCT FROM 'PIN_NOT_HONOURED') = (num_nonnulls(pin_vehicle_id, pin_driver_id) = 1)
        AND num_nonnulls(pin_vehicle_id, pin_driver_id) <= 1
        AND (reason IS NOT DISTINCT FROM 'PIN_NOT_HONOURED') = (pin_target_out IS NOT NULL)),
    CONSTRAINT plan_undo_reason_capacity_has_figures CHECK (
        (reason IS NOT DISTINCT FROM 'CAPACITY') = (weight IS NOT NULL)
        AND num_nonnulls(weight, volume, largest_weight_capacity, largest_volume_capacity) IN (0, 4)),
    CONSTRAINT plan_undo_reason_time_has_fact CHECK (
        COALESCE(reason IN ('TIME_WINDOW', 'SHIFT', 'DEPOT_HOURS'), FALSE) = (fact_limit_date IS NOT NULL)
        AND num_nonnulls(fact_limit_date, fact_limit_seconds, earliest_date, earliest_seconds) IN (0, 4)),
    CONSTRAINT plan_undo_pin_after_only_pinned CHECK (
        (kind = 'PINNED') = (num_nonnulls(pin_after_vehicle_id, pin_after_driver_id) = 1)
        AND num_nonnulls(pin_after_vehicle_id, pin_after_driver_id) <= 1),
    CONSTRAINT plan_undo_pin_before_only_pins CHECK (
        num_nonnulls(pin_before_vehicle_id, pin_before_driver_id) <= 1
        AND (kind IN ('PINNED', 'UNPINNED') OR num_nonnulls(pin_before_vehicle_id, pin_before_driver_id) = 0)
        AND (kind <> 'UNPINNED' OR num_nonnulls(pin_before_vehicle_id, pin_before_driver_id) = 1)),
    CONSTRAINT plan_undo_lock_after_only_locked CHECK ((kind = 'LOCKED') = (lock_after IS NOT NULL)),
    CONSTRAINT plan_undo_lock_before_only_locks CHECK (
        (kind IN ('LOCKED', 'UNLOCKED') OR lock_before IS NULL) AND (kind <> 'UNLOCKED' OR lock_before IS NOT NULL)),
    CONSTRAINT plan_undo_locks_known CHECK (
        lock_before IN ('WHOLE_ROUTE', 'STOP_SEQUENCE') AND lock_after IN ('WHOLE_ROUTE', 'STOP_SEQUENCE')),
    CONSTRAINT plan_undo_position UNIQUE (plan_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- A plan's redo journal (FR-145), oldest first, in the undo journal's shape.
CREATE TABLE planning.plan_redo (
    plan_id uuid NOT NULL REFERENCES planning.plan (id),
    position integer NOT NULL,
    kind text NOT NULL,
    stop_id uuid,
    order_id uuid,
    attempt integer,
    from_route_id uuid,
    from_position integer,
    to_route_id uuid,
    to_position integer,
    route_id uuid,
    times_date date,
    arrival_seconds bigint,
    start_seconds bigint,
    departure_seconds bigint,
    leg_in_travel bigint,
    leg_in_metres bigint,
    reason text,
    pin_vehicle_id uuid,
    pin_driver_id uuid,
    pin_target_out boolean,
    weight numeric,
    volume numeric,
    largest_weight_capacity numeric,
    largest_volume_capacity numeric,
    fact_limit_date date,
    fact_limit_seconds bigint,
    earliest_date date,
    earliest_seconds bigint,
    pin_before_vehicle_id uuid,
    pin_before_driver_id uuid,
    pin_after_vehicle_id uuid,
    pin_after_driver_id uuid,
    lock_before text,
    lock_after text,
    CONSTRAINT plan_redo_kind_known CHECK (
        kind IN ('MOVED_STOP', 'TOOK_OFF', 'PLACED', 'PINNED', 'UNPINNED', 'LOCKED', 'UNLOCKED')),
    CONSTRAINT plan_redo_stop_named CHECK ((kind IN ('MOVED_STOP', 'TOOK_OFF', 'PLACED')) = (stop_id IS NOT NULL)),
    CONSTRAINT plan_redo_order_named CHECK (
        (kind IN ('TOOK_OFF', 'PLACED', 'PINNED', 'UNPINNED')) = (order_id IS NOT NULL)
        AND (kind IN ('TOOK_OFF', 'PLACED')) = (attempt IS NOT NULL)),
    CONSTRAINT plan_redo_origin_named CHECK (
        (kind IN ('MOVED_STOP', 'TOOK_OFF')) = (from_route_id IS NOT NULL)
        AND (from_route_id IS NULL) = (from_position IS NULL)),
    CONSTRAINT plan_redo_destination_named CHECK (
        (kind IN ('MOVED_STOP', 'PLACED')) = (to_route_id IS NOT NULL)
        AND (to_route_id IS NULL) = (to_position IS NULL)),
    CONSTRAINT plan_redo_locked_route_named CHECK ((kind IN ('LOCKED', 'UNLOCKED')) = (route_id IS NOT NULL)),
    CONSTRAINT plan_redo_times_only_taken_off CHECK (
        num_nonnulls(times_date, arrival_seconds, start_seconds, departure_seconds, leg_in_travel, leg_in_metres)
        IN (0, 6)
        AND (kind = 'TOOK_OFF' OR times_date IS NULL)),
    CONSTRAINT plan_redo_reason_only_placed CHECK ((kind = 'PLACED') = (reason IS NOT NULL)),
    CONSTRAINT plan_redo_reason_known CHECK (reason IN (
        'MISSING_LOCATION', 'NO_VEHICLE_AVAILABLE', 'UNREACHABLE_LOCATION', 'REMOVED_BY_DISPATCHER',
        'PIN_NOT_HONOURED', 'CAPACITY', 'TIME_WINDOW', 'SHIFT', 'DEPOT_HOURS', 'VEHICLE_LIMIT', 'DISPLACED')),
    CONSTRAINT plan_redo_reason_pin_has_one_target CHECK (
        (reason IS NOT DISTINCT FROM 'PIN_NOT_HONOURED') = (num_nonnulls(pin_vehicle_id, pin_driver_id) = 1)
        AND num_nonnulls(pin_vehicle_id, pin_driver_id) <= 1
        AND (reason IS NOT DISTINCT FROM 'PIN_NOT_HONOURED') = (pin_target_out IS NOT NULL)),
    CONSTRAINT plan_redo_reason_capacity_has_figures CHECK (
        (reason IS NOT DISTINCT FROM 'CAPACITY') = (weight IS NOT NULL)
        AND num_nonnulls(weight, volume, largest_weight_capacity, largest_volume_capacity) IN (0, 4)),
    CONSTRAINT plan_redo_reason_time_has_fact CHECK (
        COALESCE(reason IN ('TIME_WINDOW', 'SHIFT', 'DEPOT_HOURS'), FALSE) = (fact_limit_date IS NOT NULL)
        AND num_nonnulls(fact_limit_date, fact_limit_seconds, earliest_date, earliest_seconds) IN (0, 4)),
    CONSTRAINT plan_redo_pin_after_only_pinned CHECK (
        (kind = 'PINNED') = (num_nonnulls(pin_after_vehicle_id, pin_after_driver_id) = 1)
        AND num_nonnulls(pin_after_vehicle_id, pin_after_driver_id) <= 1),
    CONSTRAINT plan_redo_pin_before_only_pins CHECK (
        num_nonnulls(pin_before_vehicle_id, pin_before_driver_id) <= 1
        AND (kind IN ('PINNED', 'UNPINNED') OR num_nonnulls(pin_before_vehicle_id, pin_before_driver_id) = 0)
        AND (kind <> 'UNPINNED' OR num_nonnulls(pin_before_vehicle_id, pin_before_driver_id) = 1)),
    CONSTRAINT plan_redo_lock_after_only_locked CHECK ((kind = 'LOCKED') = (lock_after IS NOT NULL)),
    CONSTRAINT plan_redo_lock_before_only_locks CHECK (
        (kind IN ('LOCKED', 'UNLOCKED') OR lock_before IS NULL) AND (kind <> 'UNLOCKED' OR lock_before IS NOT NULL)),
    CONSTRAINT plan_redo_locks_known CHECK (
        lock_before IN ('WHOLE_ROUTE', 'STOP_SEQUENCE') AND lock_after IN ('WHOLE_ROUTE', 'STOP_SEQUENCE')),
    CONSTRAINT plan_redo_position UNIQUE (plan_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- A published plan's revisions (DL §4.4, ADR-059): each appended once when the plan is saved, never
-- changed, and never loaded with the plan. A revision is keyed by its plan and its number, with
-- every route of the plan in its order, and the routes' stops flattened into one table keyed by the
-- route's position and the stop's own (ADR-127 decision 3).
CREATE TABLE planning.plan_revision (
    plan_id uuid NOT NULL REFERENCES planning.plan (id),
    revision integer NOT NULL,
    effective_at timestamptz NOT NULL,
    cause text NOT NULL,
    proposal_id uuid,
    made_by uuid,
    PRIMARY KEY (plan_id, revision),
    CONSTRAINT plan_revision_cause_known CHECK (cause IN (
        'PUBLISHED', 'REOPTIMIZATION_ACCEPTED', 'HANDED_OVER', 'ORDER_CANCELLED', 'ORDER_RESCHEDULED',
        'CORRECTED_AFTER_PUT_BACK', 'LATE_OUTCOME')),
    CONSTRAINT plan_revision_proposal_only_accepted CHECK (
        (cause = 'REOPTIMIZATION_ACCEPTED') = (proposal_id IS NOT NULL))
);

CREATE TABLE planning.plan_revision_route (
    plan_id uuid NOT NULL,
    revision integer NOT NULL,
    position integer NOT NULL,
    route_id uuid NOT NULL,
    vehicle_id uuid NOT NULL,
    driver_id uuid NOT NULL,
    FOREIGN KEY (plan_id, revision) REFERENCES planning.plan_revision (plan_id, revision),
    CONSTRAINT plan_revision_route_position UNIQUE (plan_id, revision, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE planning.plan_revision_stop (
    plan_id uuid NOT NULL,
    revision integer NOT NULL,
    position integer NOT NULL,
    route_position integer NOT NULL,
    stop_position integer NOT NULL,
    stop_id uuid NOT NULL,
    order_id uuid NOT NULL,
    attempt integer NOT NULL,
    planned_arrival_date date,
    planned_arrival_seconds bigint,
    FOREIGN KEY (plan_id, revision) REFERENCES planning.plan_revision (plan_id, revision),
    CONSTRAINT plan_revision_stop_arrival_whole CHECK (
        num_nonnulls(planned_arrival_date, planned_arrival_seconds) IN (0, 2)),
    CONSTRAINT plan_revision_stop_position UNIQUE (plan_id, revision, position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT plan_revision_stop_on_route UNIQUE (plan_id, revision, route_position, stop_position)
        DEFERRABLE INITIALLY DEFERRED
);
