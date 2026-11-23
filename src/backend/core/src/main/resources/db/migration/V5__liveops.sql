-- Live Operations' tables (ADR-126, ADR-127): the Delivery aggregate with its outcome records and
-- held reports, the RouteRun aggregate with its pending stops, the Alert aggregate, and the position
-- record. No grants yet (ADR-127 decision 8).

-- A stop's delivery, keyed by the stop's UUID (ADR-127 decision 5). It is never deleted: retention
-- erases its personal data and keeps its records (NFR-31, ADR-107), so its children need no ON
-- DELETE CASCADE.
CREATE TABLE liveops.delivery (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    order_id uuid NOT NULL,
    attempt integer NOT NULL,
    arrival_driver uuid,
    arrival_route uuid,
    arrival_device_time timestamptz,
    arrival_device_clock_at_send timestamptz,
    arrival_received_at timestamptz,
    CONSTRAINT delivery_arrival_whole CHECK (
        (arrival_driver IS NULL) = (arrival_route IS NULL)
        AND (arrival_driver IS NULL) = (arrival_device_time IS NULL)
        AND (arrival_driver IS NULL) = (arrival_device_clock_at_send IS NULL)
        AND (arrival_driver IS NULL) = (arrival_received_at IS NULL))
);

-- Customers & Orders' StopProgress asks for one attempt's delivery (BR-92, FR-258).
CREATE INDEX delivery_order_attempt ON liveops.delivery (order_id, attempt);

-- A stop's outcome records: the original first, then each correction (FR-333). The outcome is a kind
-- plus the columns each kind needs, and so is the origin.
CREATE TABLE liveops.delivery_outcome (
    delivery_id uuid NOT NULL REFERENCES liveops.delivery (id),
    position integer NOT NULL,
    outcome text NOT NULL,
    left_at_door boolean,
    proof_kind text,
    proof_file uuid,
    proof_note text,
    failure_reason text,
    failure_text text,
    recorded_by uuid NOT NULL,
    fact_time timestamptz NOT NULL,
    origin text NOT NULL,
    route uuid,
    revision integer,
    report_device_time timestamptz,
    report_device_clock_at_send timestamptz,
    report_received_at timestamptz,
    served_after text,
    confirmed_report uuid,
    correction_reason text,
    corrected_at timestamptz,
    CONSTRAINT delivery_outcome_outcome_known CHECK (outcome IN ('DELIVERED', 'FAILED')),
    CONSTRAINT delivery_outcome_delivered_whole CHECK ((outcome = 'DELIVERED') = (left_at_door IS NOT NULL)),
    CONSTRAINT delivery_outcome_failed_whole CHECK ((outcome = 'FAILED') = (failure_reason IS NOT NULL)),
    CONSTRAINT delivery_outcome_proof_kind_known CHECK (proof_kind IN ('SIGNATURE', 'PHOTO')),
    -- Only a delivered outcome has a proof, and a proof's note goes with its file (NFR-184).
    CONSTRAINT delivery_outcome_proof_only_delivered CHECK (proof_kind IS NULL OR outcome = 'DELIVERED'),
    CONSTRAINT delivery_outcome_proof_file_has_kind CHECK (proof_file IS NULL OR proof_kind IS NOT NULL),
    CONSTRAINT delivery_outcome_proof_note_has_file CHECK (proof_note IS NULL OR proof_file IS NOT NULL),
    CONSTRAINT delivery_outcome_failure_reason_known CHECK (
        failure_reason IN ('CUSTOMER_ABSENT', 'WRONG_ADDRESS', 'REFUSED', 'NO_ACCESS', 'DAMAGED', 'OTHER')),
    CONSTRAINT delivery_outcome_failure_text_only_other CHECK (failure_text IS NULL OR failure_reason = 'OTHER'),
    CONSTRAINT delivery_outcome_origin_known CHECK (
        origin IN ('REPORTED', 'CONFIRMED_HELD', 'ON_BEHALF', 'CORRECTION')),
    CONSTRAINT delivery_outcome_route_named CHECK ((origin IN ('REPORTED', 'ON_BEHALF')) = (route IS NOT NULL)),
    -- A driver's report keeps the revision that entitled it and its time (BR-44, BR-110).
    CONSTRAINT delivery_outcome_report_whole CHECK (
        (origin = 'REPORTED') = (revision IS NOT NULL)
        AND (origin = 'REPORTED') = (report_device_time IS NOT NULL)
        AND (origin = 'REPORTED') = (report_device_clock_at_send IS NOT NULL)
        AND (origin = 'REPORTED') = (report_received_at IS NOT NULL)),
    CONSTRAINT delivery_outcome_served_after_known CHECK (
        served_after IN ('ORDER_CANCELLED', 'ORDER_RESCHEDULED', 'PLAN_CANCELLED')),
    CONSTRAINT delivery_outcome_served_after_only_reported CHECK (served_after IS NULL OR origin = 'REPORTED'),
    CONSTRAINT delivery_outcome_confirmation_named CHECK ((origin = 'CONFIRMED_HELD') = (confirmed_report IS NOT NULL)),
    -- A correction keeps its time; retention may erase its reason's text (NFR-31).
    CONSTRAINT delivery_outcome_correction_dated CHECK ((origin = 'CORRECTION') = (corrected_at IS NOT NULL)),
    CONSTRAINT delivery_outcome_reason_only_correction CHECK (correction_reason IS NULL OR origin = 'CORRECTION'),
    CONSTRAINT delivery_outcome_position UNIQUE (delivery_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- The retention job finds the records whose personal data it may erase (NFR-31, NFR-184).
CREATE INDEX delivery_outcome_personal_data ON liveops.delivery_outcome (delivery_id)
    WHERE proof_file IS NOT NULL OR failure_text IS NOT NULL OR correction_reason IS NOT NULL;

-- The reports held for a dispatcher, each keyed by its HeldReportId (BR-132). The outcome takes the
-- same columns as an outcome record's, and the state is a kind plus the decision's dispatcher and time.
CREATE TABLE liveops.held_report (
    id uuid PRIMARY KEY,
    delivery_id uuid NOT NULL REFERENCES liveops.delivery (id),
    position integer NOT NULL,
    outcome text NOT NULL,
    left_at_door boolean,
    proof_kind text,
    proof_file uuid,
    proof_note text,
    failure_reason text,
    failure_text text,
    driver uuid NOT NULL,
    route uuid NOT NULL,
    revision integer NOT NULL,
    device_time timestamptz NOT NULL,
    device_clock_at_send timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    overrides text,
    state text NOT NULL,
    state_by uuid,
    state_at timestamptz,
    CONSTRAINT held_report_outcome_known CHECK (outcome IN ('DELIVERED', 'FAILED')),
    CONSTRAINT held_report_delivered_whole CHECK ((outcome = 'DELIVERED') = (left_at_door IS NOT NULL)),
    CONSTRAINT held_report_failed_whole CHECK ((outcome = 'FAILED') = (failure_reason IS NOT NULL)),
    CONSTRAINT held_report_proof_kind_known CHECK (proof_kind IN ('SIGNATURE', 'PHOTO')),
    CONSTRAINT held_report_proof_only_delivered CHECK (proof_kind IS NULL OR outcome = 'DELIVERED'),
    CONSTRAINT held_report_proof_file_has_kind CHECK (proof_file IS NULL OR proof_kind IS NOT NULL),
    CONSTRAINT held_report_proof_note_has_file CHECK (proof_note IS NULL OR proof_file IS NOT NULL),
    CONSTRAINT held_report_failure_reason_known CHECK (
        failure_reason IN ('CUSTOMER_ABSENT', 'WRONG_ADDRESS', 'REFUSED', 'NO_ACCESS', 'DAMAGED', 'OTHER')),
    CONSTRAINT held_report_failure_text_only_other CHECK (failure_text IS NULL OR failure_reason = 'OTHER'),
    CONSTRAINT held_report_overrides_known CHECK (
        overrides IN ('ORDER_CANCELLED', 'ORDER_RESCHEDULED', 'PLAN_CANCELLED')),
    CONSTRAINT held_report_state_known CHECK (state IN ('OPEN', 'CONFIRMED', 'REFUSED_BY_DISPATCHER', 'OVERTAKEN')),
    -- Every decision is dated, and a dispatcher's names the dispatcher (BR-132, BR-134, BR-135).
    CONSTRAINT held_report_decision_dated CHECK ((state = 'OPEN') = (state_at IS NULL)),
    CONSTRAINT held_report_dispatcher_named CHECK (
        (state IN ('CONFIRMED', 'REFUSED_BY_DISPATCHER')) = (state_by IS NOT NULL)),
    CONSTRAINT held_report_position UNIQUE (delivery_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- The dispatcher's list of open held reports (BR-132, FR-157).
CREATE INDEX held_report_open ON liveops.held_report (delivery_id) WHERE state = 'OPEN';
-- The retention job finds the held reports whose personal data it may erase (NFR-31, NFR-184).
CREATE INDEX held_report_personal_data ON liveops.held_report (received_at)
    WHERE proof_file IS NOT NULL OR failure_text IS NOT NULL;

-- A route's run, keyed by the route's UUID (ADR-127 decision 5). The run doesn't hold its route's
-- delivery date; the adapter writes delivery_date right after the run's insert, so it is nullable.
CREATE TABLE liveops.route_run (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    started_by uuid NOT NULL,
    cause text NOT NULL,
    started_at timestamptz NOT NULL,
    status text NOT NULL,
    finished_at timestamptz,
    finished_by uuid,
    confirmed integer,
    delivery_date date,
    CONSTRAINT route_run_cause_known CHECK (cause IN ('DRIVER_STARTED', 'FIRST_REPORT', 'HELD_REPORT_CONFIRMED')),
    CONSTRAINT route_run_status_known CHECK (status IN ('STARTED', 'FINISHED')),
    CONSTRAINT route_run_finished_whole CHECK (
        (status = 'FINISHED') = (finished_at IS NOT NULL)
        AND (status = 'FINISHED') = (finished_by IS NOT NULL))
);

-- The runs of a date's routes (FR-244).
CREATE INDEX route_run_delivery_date ON liveops.route_run (delivery_date);

-- The stops a finished run left without an outcome, in the route's order (FR-329).
CREATE TABLE liveops.route_run_pending_stop (
    route_run_id uuid NOT NULL REFERENCES liveops.route_run (id),
    position integer NOT NULL,
    stop uuid NOT NULL,
    CONSTRAINT route_run_pending_stop_position UNIQUE (route_run_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- An alert, its subject a kind plus the columns each kind names (FR-157). The alert doesn't hold its
-- plan's delivery date; the adapter writes delivery_date right after the alert's insert, so it is
-- nullable.
CREATE TABLE liveops.alert (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    kind text NOT NULL,
    plan uuid NOT NULL,
    route uuid,
    stop uuid,
    revision integer,
    driver uuid,
    problem text,
    left_route uuid,
    held_report uuid,
    refused_fact text,
    rating_request uuid,
    dedupe_key text NOT NULL,
    raised_at timestamptz NOT NULL,
    state text NOT NULL,
    acknowledged_by uuid,
    acknowledged_at timestamptz,
    delivery_date date,
    CONSTRAINT alert_kind_known CHECK (kind IN (
        'STOP_AT_RISK', 'DRIVER_PROBLEM', 'ROUTE_NOT_STARTED', 'STOP_FAILED', 'ROUTE_CHANGE_UNCONFIRMED',
        'LATE_REPORT', 'REPORT_HELD', 'REPORT_REFUSED', 'ROUTE_FINISHED_WITH_PENDING_STOPS', 'LOW_RATING')),
    CONSTRAINT alert_route_named CHECK ((kind <> 'LOW_RATING') = (route IS NOT NULL)),
    CONSTRAINT alert_stop_named CHECK (
        (kind IN ('STOP_AT_RISK', 'STOP_FAILED', 'LATE_REPORT', 'REPORT_HELD', 'REPORT_REFUSED', 'LOW_RATING'))
        = (stop IS NOT NULL)),
    CONSTRAINT alert_revision_named CHECK (
        (kind IN ('STOP_AT_RISK', 'ROUTE_CHANGE_UNCONFIRMED')) = (revision IS NOT NULL)),
    CONSTRAINT alert_driver_named CHECK ((kind IN ('DRIVER_PROBLEM', 'REPORT_REFUSED')) = (driver IS NOT NULL)),
    CONSTRAINT alert_problem_named CHECK ((kind = 'DRIVER_PROBLEM') = (problem IS NOT NULL)),
    CONSTRAINT alert_problem_known CHECK (problem IN ('BREAKDOWN', 'RUNNING_LATE', 'VEHICLE_FULL')),
    CONSTRAINT alert_left_route_only_late CHECK (left_route IS NULL OR kind = 'LATE_REPORT'),
    CONSTRAINT alert_held_report_named CHECK ((kind = 'REPORT_HELD') = (held_report IS NOT NULL)),
    CONSTRAINT alert_refused_fact_named CHECK ((kind = 'REPORT_REFUSED') = (refused_fact IS NOT NULL)),
    CONSTRAINT alert_refused_fact_known CHECK (refused_fact IN ('ARRIVED', 'DELIVERED', 'FAILED')),
    CONSTRAINT alert_rating_request_named CHECK ((kind = 'LOW_RATING') = (rating_request IS NOT NULL)),
    CONSTRAINT alert_state_known CHECK (state IN ('OPEN', 'ACKNOWLEDGED')),
    CONSTRAINT alert_acknowledged_whole CHECK (
        (state = 'ACKNOWLEDGED') = (acknowledged_by IS NOT NULL)
        AND (state = 'ACKNOWLEDGED') = (acknowledged_at IS NOT NULL)),
    -- An alert is raised once per key, open or acknowledged (DL §5, ADR-107). The adapter takes the
    -- key's advisory lock and looks first, so this is the backstop, not the check.
    CONSTRAINT alert_dedupe_key_unique UNIQUE (dedupe_key)
);

-- The unacknowledged alerts of a date's plan (FR-261, BR-102).
CREATE INDEX alert_open_on ON liveops.alert (delivery_date) WHERE state = 'OPEN';

-- The positions routes kept, each a record keyed by a UUID the adapter draws (ADR-127 decision 6),
-- deleted 7 days after receipt (NFR-31).
CREATE TABLE liveops.kept_position (
    id uuid PRIMARY KEY,
    route uuid NOT NULL,
    driver uuid NOT NULL,
    latitude double precision NOT NULL,
    longitude double precision NOT NULL,
    recorded_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL
);

-- A route's last two positions place its vehicle for a re-optimization (ADR-050, BR-79).
CREATE INDEX kept_position_latest ON liveops.kept_position (route, recorded_at DESC, received_at DESC);
-- The retention job deletes by receipt time (NFR-31).
CREATE INDEX kept_position_received_at ON liveops.kept_position (received_at);
