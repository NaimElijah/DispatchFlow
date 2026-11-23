-- Recipient Messaging's tables (ADR-126, ADR-127): the StopMessages, RatingRequest and
-- MessagingSettings aggregates, the reply answers counted toward the daily cap, and the two records,
-- the reply log and the opt-out history. No grants yet (ADR-127 decision 8).

-- A stop's messages, keyed by the stop's UUID (ADR-127 decision 5). A stop is deleted with its
-- messages once their retention ends (NFR-184).
CREATE TABLE messaging.stop_messages (
    id uuid PRIMARY KEY,
    version bigint NOT NULL
);

CREATE TABLE messaging.outgoing_message (
    id uuid PRIMARY KEY,
    stop_messages_id uuid NOT NULL REFERENCES messaging.stop_messages (id) ON DELETE CASCADE,
    position integer NOT NULL,
    type text NOT NULL,
    sequence integer NOT NULL,
    phone text NOT NULL,
    text text NOT NULL,
    arrival_sent timestamptz,
    asks_for_rating boolean NOT NULL,
    triggered_at timestamptz NOT NULL,
    state text NOT NULL,
    state_since timestamptz NOT NULL,
    provider_reference text,
    drop_cause text,
    CONSTRAINT outgoing_message_type_known CHECK (
        type IN ('OUT_FOR_DELIVERY', 'ARRIVING_SOON', 'TIME_CHANGED', 'DELIVERY_CHANGED', 'MISSED_YOU', 'DELIVERED')),
    CONSTRAINT outgoing_message_state_known CHECK (
        state IN ('PENDING', 'SENDING', 'ACCEPTED', 'UNKNOWN', 'DROPPED')),
    CONSTRAINT outgoing_message_accepted_has_reference CHECK ((state = 'ACCEPTED') = (provider_reference IS NOT NULL)),
    CONSTRAINT outgoing_message_dropped_has_cause CHECK ((state = 'DROPPED') = (drop_cause IS NOT NULL)),
    CONSTRAINT outgoing_message_drop_cause_known CHECK (
        drop_cause IN ('STALE', 'STALE_WHILE_SENDING', 'REFUSED', 'OUTCOME_UNKNOWN', 'OPTED_OUT')),
    -- Each message is unique by its stop, type and sequence, so it is sent at most once (NFR-191).
    CONSTRAINT outgoing_message_once UNIQUE (stop_messages_id, type, sequence),
    CONSTRAINT outgoing_message_position UNIQUE (stop_messages_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- The sender's lookup of the messages it may claim, the oldest trigger first.
CREATE INDEX outgoing_message_claimable ON messaging.outgoing_message (triggered_at)
    WHERE state IN ('PENDING', 'UNKNOWN');
-- An opt-out's confirmation reads when a phone was last messaged (FR-363).
CREATE INDEX outgoing_message_accepted_to ON messaging.outgoing_message (phone, state_since)
    WHERE state = 'ACCEPTED';
-- The daily cap reads the messages that reached their state on a day (NFR-196).
CREATE INDEX outgoing_message_state_since ON messaging.outgoing_message (state_since);

-- The reply answers counted toward a day's cap (NFR-196, ADR-121 P24), each once by its own ID.
CREATE TABLE messaging.counted_answer (
    answer_id uuid PRIMARY KEY,
    day date NOT NULL
);

CREATE INDEX counted_answer_day ON messaging.counted_answer (day);

CREATE TABLE messaging.rating_request (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    stop uuid NOT NULL,
    accepted_at timestamptz NOT NULL,
    phone text,
    rating integer,
    rated_at timestamptz,
    reminder text NOT NULL,
    thank_you text NOT NULL,
    removed_at timestamptz,
    CONSTRAINT rating_request_rated_whole CHECK ((rating IS NULL) = (rated_at IS NULL)),
    CONSTRAINT rating_request_reminder_known CHECK (reminder IN ('NOT_YET', 'SENT', 'SKIPPED')),
    CONSTRAINT rating_request_thank_you_known CHECK (thank_you IN ('NOT_YET', 'SENT', 'SKIPPED'))
);

-- A reply finds the latest request sent to its phone (FR-371).
CREATE INDEX rating_request_open_for ON messaging.rating_request (phone, accepted_at) WHERE phone IS NOT NULL;

CREATE TABLE messaging.rating_request_comment (
    rating_request_id uuid NOT NULL REFERENCES messaging.rating_request (id),
    position integer NOT NULL,
    comment text NOT NULL,
    received_at timestamptz NOT NULL,
    CONSTRAINT rating_request_comment_position UNIQUE (rating_request_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- The one row of the company's settings, under MessagingSettings.KEY.
CREATE TABLE messaging.messaging_settings (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    messages text NOT NULL,
    rating_question text NOT NULL,
    dispatch_phone text NOT NULL,
    CONSTRAINT messaging_settings_one_row CHECK (id = '2d238743-e3ee-4341-a69e-eecd5c010419'),
    CONSTRAINT messaging_settings_messages_known CHECK (messages IN ('ON', 'OFF')),
    CONSTRAINT messaging_settings_rating_question_known CHECK (rating_question IN ('ON', 'OFF'))
);

-- The replies kept, each once by the provider's identifier (NFR-199), deleted after 90 days (NFR-184).
CREATE TABLE messaging.inbound_reply (
    provider_id text PRIMARY KEY,
    phone text NOT NULL,
    text text NOT NULL,
    received_at timestamptz NOT NULL
);

CREATE INDEX inbound_reply_received_at ON messaging.inbound_reply (received_at);

-- The phones' opt-out history, kept until the company leaves (NFR-202). A phone's latest entry is
-- the one appended last (ADR-108), which the database numbers in appended.
CREATE TABLE messaging.opt_out (
    id uuid PRIMARY KEY,
    appended bigint GENERATED ALWAYS AS IDENTITY,
    phone_hash text NOT NULL,
    change text NOT NULL,
    made_at timestamptz NOT NULL,
    dispatcher uuid,
    CONSTRAINT opt_out_change_known CHECK (change IN ('OPTED_OUT', 'OPTED_IN')),
    -- Only a dispatcher removes an opt-out (FR-380).
    CONSTRAINT opt_out_opt_in_has_dispatcher CHECK (change = 'OPTED_OUT' OR dispatcher IS NOT NULL)
);

CREATE INDEX opt_out_history ON messaging.opt_out (phone_hash, appended);
