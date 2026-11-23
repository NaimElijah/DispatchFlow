-- Fleet's tables (ADR-126, ADR-127): the Driver, FleetRegister and DayRoster aggregates. No grants
-- yet (ADR-127 decision 8). Nothing deletes a driver, the register or a roster, so no foreign key
-- cascades.

-- A driver, keyed by their account's UUID (ADR-127 decision 5). registration_order, which the
-- database draws on insert and the entity doesn't map, orders DriverRepository.registered().
CREATE TABLE fleet.driver (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    registration_order bigint GENERATED ALWAYS AS IDENTITY,
    status text NOT NULL,
    retired_at timestamptz,
    anonymized_at timestamptz,
    name text,
    phone text,
    default_shift_starts_at time NOT NULL,
    default_shift_ends_at time NOT NULL,
    default_vehicle uuid NOT NULL,
    CONSTRAINT driver_status_known CHECK (status IN ('ACTIVE', 'RETIRED', 'ANONYMIZED')),
    CONSTRAINT driver_active_has_no_times CHECK (status <> 'ACTIVE' OR retired_at IS NULL),
    CONSTRAINT driver_retired_has_retired_at CHECK (status <> 'RETIRED' OR retired_at IS NOT NULL),
    CONSTRAINT driver_anonymized_has_anonymized_at CHECK ((status = 'ANONYMIZED') = (anonymized_at IS NOT NULL)),
    CONSTRAINT driver_contact_whole CHECK ((name IS NULL) = (phone IS NULL)),
    -- Anonymizing a driver clears their name and phone (BR-98, NFR-258), which the load check also
    -- enforces.
    CONSTRAINT driver_anonymized_has_no_contact CHECK (status <> 'ANONYMIZED' OR (name IS NULL AND phone IS NULL))
);

-- The retention job's lookup of the retired drivers (BR-98, NFR-184).
CREATE INDEX driver_retired_at ON fleet.driver (retired_at) WHERE status = 'RETIRED';

-- A driver's weekly shift template (FR-79): at most one shift per weekday.
CREATE TABLE fleet.driver_weekly_shift (
    driver_id uuid NOT NULL REFERENCES fleet.driver (id),
    day text NOT NULL,
    starts_at time NOT NULL,
    ends_at time NOT NULL,
    PRIMARY KEY (driver_id, day),
    CONSTRAINT driver_weekly_shift_day_known CHECK (
        day IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'))
);

-- The one row of the company's register, under FleetRegister.KEY.
CREATE TABLE fleet.fleet_register (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    CONSTRAINT fleet_register_one_row CHECK (id = '10b6b5f9-ffc1-4b99-9eac-05d3d670a261')
);

CREATE TABLE fleet.depot (
    id uuid PRIMARY KEY,
    fleet_register_id uuid NOT NULL REFERENCES fleet.fleet_register (id),
    position integer NOT NULL,
    name text NOT NULL,
    latitude double precision,
    longitude double precision,
    opens time,
    closes time,
    retired_at timestamptz,
    CONSTRAINT depot_location_whole CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT depot_hours_whole CHECK ((opens IS NULL) = (closes IS NULL)),
    CONSTRAINT depot_position UNIQUE (fleet_register_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- A vehicle's capacity columns are ADR-061's.
CREATE TABLE fleet.vehicle (
    id uuid PRIMARY KEY,
    fleet_register_id uuid NOT NULL REFERENCES fleet.fleet_register (id),
    position integer NOT NULL,
    name text NOT NULL,
    plate text NOT NULL,
    weight_kg numeric(8, 2) NOT NULL,
    volume_l numeric(9, 2) NOT NULL,
    start_depot uuid NOT NULL,
    end_depot uuid NOT NULL,
    retired_at timestamptz,
    CONSTRAINT vehicle_position UNIQUE (fleet_register_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- A date's roster, keyed by a UUID beside its date, which is unique: a second roster for a date is
-- the race DayRosterRepository.add answers with StaleVersion (ADR-127 decision 5).
CREATE TABLE fleet.day_roster (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    date date NOT NULL,
    CONSTRAINT day_roster_one_per_date UNIQUE (date)
);

-- A roster's entries, values in the order the drivers were rostered (ADR-127 decision 3).
CREATE TABLE fleet.roster_entry (
    day_roster_id uuid NOT NULL REFERENCES fleet.day_roster (id),
    position integer NOT NULL,
    driver uuid NOT NULL,
    shift_date date NOT NULL,
    starts_at time NOT NULL,
    ends_at time NOT NULL,
    vehicle_choice text NOT NULL,
    vehicle uuid,
    availability text NOT NULL,
    CONSTRAINT roster_entry_vehicle_choice_known CHECK (vehicle_choice IN ('NOT_SET', 'NO_VEHICLE', 'ASSIGNED')),
    CONSTRAINT roster_entry_assigned_has_vehicle CHECK ((vehicle_choice = 'ASSIGNED') = (vehicle IS NOT NULL)),
    CONSTRAINT roster_entry_availability_known CHECK (availability IN ('AVAILABLE', 'UNAVAILABLE')),
    -- One entry per driver. Deferred, as the position is: Hibernate rewrites a list's rows one by one.
    CONSTRAINT roster_entry_driver_once UNIQUE (day_roster_id, driver) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT roster_entry_position UNIQUE (day_roster_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- The vehicles marked unavailable on a roster's date, in the order they were marked.
CREATE TABLE fleet.unavailable_vehicle (
    day_roster_id uuid NOT NULL REFERENCES fleet.day_roster (id),
    position integer NOT NULL,
    vehicle uuid NOT NULL,
    CONSTRAINT unavailable_vehicle_position UNIQUE (day_roster_id, position) DEFERRABLE INITIALLY DEFERRED
);
