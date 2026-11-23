-- Reporting's tables (ADR-126, ADR-127): the Baseline aggregate, and how each plan's publication
-- baseline was settled. A baseline holds no personal data and stays as long as its plan (NFR-152,
-- ADR-109), so nothing here is deleted. No grants yet (ADR-127 decision 8).

-- Each plan is settled once (FR-39): by a computed baseline, or by the optimizer's refusal, whose
-- cause this row keeps. The key makes a second settlement impossible, and a baseline's row refers to
-- its plan's row as a computed one, so no plan holds a baseline and a refusal.
CREATE TABLE reporting.baseline_outcome (
    plan_id uuid PRIMARY KEY,
    outcome text NOT NULL,
    refusal_cause text,
    CONSTRAINT baseline_outcome_known CHECK (outcome IN ('COMPUTED', 'REFUSED')),
    CONSTRAINT baseline_outcome_refused_has_cause CHECK ((outcome = 'REFUSED') = (refusal_cause IS NOT NULL)),
    CONSTRAINT baseline_outcome_refusal_cause_known CHECK (
        refusal_cause IN ('SOLVER_FAILED', 'NO_SOLUTION_WITHIN_BUDGET', 'VALIDATOR_REJECTED')),
    -- The target of a baseline's reference, which names the outcome it must have.
    CONSTRAINT baseline_outcome_with_its_outcome UNIQUE (plan_id, outcome)
);

-- A plan's baseline, keyed by the plan's UUID (ADR-127 decision 5). Its outcome column is not
-- mapped: it is always COMPUTED, and exists so the reference below can require that.
CREATE TABLE reporting.baseline (
    id uuid PRIMARY KEY,
    outcome text NOT NULL DEFAULT 'COMPUTED',
    version bigint NOT NULL,
    -- Whole metres.
    distance bigint NOT NULL,
    -- Nanoseconds, so a fraction of a second is kept exactly.
    driver_hours bigint NOT NULL,
    cause text NOT NULL,
    computed_at timestamptz NOT NULL,
    CONSTRAINT baseline_is_computed CHECK (outcome = 'COMPUTED'),
    CONSTRAINT baseline_settles_its_plan FOREIGN KEY (id, outcome)
        REFERENCES reporting.baseline_outcome (plan_id, outcome),
    CONSTRAINT baseline_cause_known CHECK (cause IN ('PUBLISHED', 'RECOMPUTED'))
);

-- Every order of the baseline's day, with its window and its service: a served order has a start,
-- a dropped one none.
CREATE TABLE reporting.baseline_planned_order (
    baseline_id uuid NOT NULL REFERENCES reporting.baseline (id),
    position integer NOT NULL,
    order_id uuid NOT NULL,
    starts_at timestamp(6) NOT NULL,
    ends_at timestamp(6) NOT NULL,
    service text NOT NULL,
    start_date date,
    start_seconds bigint,
    CONSTRAINT baseline_planned_order_service_known CHECK (service IN ('SERVED', 'DROPPED')),
    CONSTRAINT baseline_planned_order_served_has_start CHECK ((service = 'SERVED') = (start_date IS NOT NULL)),
    CONSTRAINT baseline_planned_order_start_whole CHECK ((start_date IS NULL) = (start_seconds IS NULL)),
    CONSTRAINT baseline_planned_order_position UNIQUE (baseline_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- The vehicles withdrawn so far, in the order first withdrawn (FR-271).
CREATE TABLE reporting.baseline_withdrawal (
    baseline_id uuid NOT NULL REFERENCES reporting.baseline (id),
    position integer NOT NULL,
    vehicle uuid NOT NULL,
    cause text NOT NULL,
    CONSTRAINT baseline_withdrawal_cause_known CHECK (cause IN ('VEHICLE_UNAVAILABLE', 'NO_DRIVER', 'SHIFT_RAN_OUT')),
    CONSTRAINT baseline_withdrawal_position UNIQUE (baseline_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- The orders added so far, in the order first added.
CREATE TABLE reporting.baseline_added_order (
    baseline_id uuid NOT NULL REFERENCES reporting.baseline (id),
    position integer NOT NULL,
    order_id uuid NOT NULL,
    CONSTRAINT baseline_added_order_position UNIQUE (baseline_id, position) DEFERRABLE INITIALLY DEFERRED
);
