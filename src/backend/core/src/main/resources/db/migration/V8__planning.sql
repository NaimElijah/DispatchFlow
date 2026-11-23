-- Planning's tables (ADR-126, ADR-127): the SolveJob, ReoptimizationProposal and PlanningSettings
-- aggregates. Every duration is stored as its nanoseconds, in a bigint (ADR-127). Jobs, proposals and settings are never deleted (DL §9.7), so
-- no foreign key cascades. No grants yet (ADR-127 decision 8).

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
