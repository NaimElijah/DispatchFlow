-- Orders' tables (ADR-126, ADR-127): the Customer, Order and ImportBatch aggregates. No grants yet
-- (ADR-127 decision 8). Nothing deletes a customer, an order or an import, so no foreign key
-- cascades, and no foreign key joins two aggregates: an order names its customer by ID only.

-- A customer, keyed by its ID. Its name, phone, address and note are null once it is anonymized,
-- which the load check enforces; normalized_address is the address's matching form (FR-310), kept
-- beside it for candidatesAt's lookup.
CREATE TABLE orders.customer (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    name text,
    phone text,
    address text,
    normalized_address text,
    note text,
    location_kind text NOT NULL,
    location_latitude double precision,
    location_longitude double precision,
    match_quality text,
    default_window_starts_at time NOT NULL,
    default_window_ends_at time NOT NULL,
    default_service_time_seconds bigint,
    state text NOT NULL,
    state_since timestamptz,
    created_at timestamptz NOT NULL,
    CONSTRAINT customer_normalized_with_address CHECK ((address IS NULL) = (normalized_address IS NULL)),
    CONSTRAINT customer_location_kind_known CHECK (location_kind IN ('MISSING', 'GEOCODED', 'HAND_PLACED')),
    CONSTRAINT customer_location_point_whole CHECK ((location_latitude IS NULL) = (location_longitude IS NULL)),
    CONSTRAINT customer_location_has_point CHECK ((location_kind = 'MISSING') = (location_latitude IS NULL)),
    CONSTRAINT customer_geocoded_has_quality CHECK ((location_kind = 'GEOCODED') = (match_quality IS NOT NULL)),
    CONSTRAINT customer_match_quality_known CHECK (match_quality IN ('EXACT', 'PARTIAL', 'AMBIGUOUS')),
    CONSTRAINT customer_state_known CHECK (state IN ('ACTIVE', 'RETIRED', 'ANONYMIZED')),
    CONSTRAINT customer_state_since CHECK ((state = 'ACTIVE') = (state_since IS NULL))
);

-- An import row's match (FR-60, FR-310): only an active customer is a candidate.
CREATE INDEX customer_active_by_address ON orders.customer (normalized_address) WHERE state = 'ACTIVE';

-- The retention job's lookups (BR-98, NFR-257): retired customers by retirement, active ones by
-- creation.
CREATE INDEX customer_retired_since ON orders.customer (state_since) WHERE state = 'RETIRED';
CREATE INDEX customer_active_created ON orders.customer (created_at) WHERE state = 'ACTIVE';

-- A customer's doubts about its location (FR-63), each flag once.
CREATE TABLE orders.customer_address_flag (
    customer_id uuid NOT NULL REFERENCES orders.customer (id),
    flag text NOT NULL,
    PRIMARY KEY (customer_id, flag),
    CONSTRAINT customer_address_flag_known CHECK (flag IN (
        'NO_MATCH', 'PARTIAL_MATCH', 'AMBIGUOUS_MATCH', 'OUTSIDE_SERVICE_AREA', 'FAR_FROM_ROAD', 'CREATED_BY_IMPORT'))
);

-- An order, keyed by its ID; "order" is reserved in PostgreSQL, so the table is customer_order. Its
-- window's two columns are TimeWindow's, and its demand columns ADR-061's. Its recipient, note,
-- access details and reference are null once it is anonymized, which the load check enforces.
CREATE TABLE orders.customer_order (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    customer uuid NOT NULL,
    date date NOT NULL,
    starts_at timestamp(6) NOT NULL,
    ends_at timestamp(6) NOT NULL,
    weight_kg numeric(8, 2) NOT NULL,
    volume_l numeric(9, 2) NOT NULL,
    service_time_seconds bigint NOT NULL,
    priority integer NOT NULL,
    leave_at_door boolean NOT NULL,
    reference text,
    recipient_name text,
    recipient_phone text,
    note text,
    floor text,
    apartment text,
    entrance_code text,
    attempt integer NOT NULL,
    state text NOT NULL,
    cancelled_at timestamptz,
    anonymized boolean NOT NULL,
    last_changed_at timestamptz NOT NULL,
    CONSTRAINT customer_order_state_known CHECK (state IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT customer_order_cancelled_at CHECK ((state = 'CANCELLED') = (cancelled_at IS NOT NULL))
);

-- A date's uncancelled orders, read under the date's lock (BR-101, BR-113) and copied (FR-62).
CREATE INDEX customer_order_uncancelled_by_date ON orders.customer_order (date) WHERE state <> 'CANCELLED';

-- A customer's orders: its last delivery date (ADR-102) and those not yet anonymized (BR-43).
CREATE INDEX customer_order_by_customer ON orders.customer_order (customer, date);

-- The retention job's lookup of the orders not yet anonymized (NFR-257).
CREATE INDEX customer_order_not_anonymized ON orders.customer_order (date) WHERE NOT anonymized;

-- An uploaded file, previewed and then confirmed, discarded or expired (ADR-086).
CREATE TABLE orders.import_batch (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    hash text NOT NULL,
    encoding text NOT NULL,
    previewed_at timestamptz NOT NULL,
    state text NOT NULL,
    state_since timestamptz,
    CONSTRAINT import_batch_encoding_known CHECK (encoding IN ('UTF_8', 'WINDOWS_1255')),
    CONSTRAINT import_batch_state_known CHECK (state IN ('PREVIEWED', 'CONFIRMED', 'DISCARDED', 'EXPIRED')),
    CONSTRAINT import_batch_state_since CHECK ((state = 'PREVIEWED') = (state_since IS NULL))
);

-- The hourly sweep's lookup of the open previews (FR-399).
CREATE INDEX import_batch_previewed_at ON orders.import_batch (previewed_at) WHERE state = 'PREVIEWED';

-- A preview's rows, values in row order (ADR-127 decision 3); a batch that leaves the preview keeps
-- none (NFR-31). Each list of errors is JSON text (ADR-127 decision 7). A parsed row has its line's
-- columns, which an unreadable row leaves null, and an unreadable row has its refused fields.
CREATE TABLE orders.import_row (
    import_batch_id uuid NOT NULL REFERENCES orders.import_batch (id),
    position integer NOT NULL,
    row_number integer NOT NULL,
    kind text NOT NULL,
    errors text NOT NULL,
    field_errors text,
    address text,
    name text,
    phone text,
    date date,
    starts_at timestamp(6),
    ends_at timestamp(6),
    weight_kg numeric(8, 2),
    volume_l numeric(9, 2),
    service_time_seconds bigint,
    priority integer,
    leave_at_door boolean,
    reference text,
    recipient_name text,
    recipient_phone text,
    note text,
    floor text,
    apartment text,
    entrance_code text,
    CONSTRAINT import_row_kind_known CHECK (kind IN ('PARSED', 'UNREADABLE')),
    CONSTRAINT import_row_unreadable_has_field_errors CHECK ((kind = 'UNREADABLE') = (field_errors IS NOT NULL)),
    CONSTRAINT import_row_parsed_has_line CHECK ((kind = 'PARSED') = (
        address IS NOT NULL AND date IS NOT NULL AND weight_kg IS NOT NULL AND volume_l IS NOT NULL
        AND priority IS NOT NULL AND leave_at_door IS NOT NULL)),
    CONSTRAINT import_row_unreadable_has_no_line CHECK (kind = 'PARSED' OR num_nonnulls(
        address, name, phone, date, starts_at, ends_at, weight_kg, volume_l, service_time_seconds, priority,
        leave_at_door, reference, recipient_name, recipient_phone, note, floor, apartment, entrance_code) = 0),
    CONSTRAINT import_row_window_whole CHECK ((starts_at IS NULL) = (ends_at IS NULL)),
    CONSTRAINT import_row_position UNIQUE (import_batch_id, position) DEFERRABLE INITIALLY DEFERRED
);
