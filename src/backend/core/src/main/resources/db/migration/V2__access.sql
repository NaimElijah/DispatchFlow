-- Access's tables (ADR-126, ADR-127): the UserAccount aggregate with its roles and credential
-- links, the Session aggregate with its refresh tokens and contact times, and the CompanyProfile.
-- No grants yet (ADR-127 decision 8).

-- An account is never deleted: anonymizing it keeps the row, because plans and audit records refer
-- to it (NFR-258, NFR-260), so its children need no ON DELETE CASCADE.
CREATE TABLE access.user_account (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    email text,
    email_set_by uuid,
    email_set_at timestamptz,
    password_hash text,
    state text NOT NULL,
    deactivated_at timestamptz,
    anonymized_at timestamptz,
    language text NOT NULL,
    generation bigint NOT NULL,
    terms_accepted_at timestamptz,
    CONSTRAINT user_account_state_known CHECK (state IN ('ACTIVE', 'DEACTIVATED', 'ANONYMIZED')),
    CONSTRAINT user_account_state_times CHECK (
        (state = 'ACTIVE' AND deactivated_at IS NULL AND anonymized_at IS NULL)
        OR (state = 'DEACTIVATED' AND deactivated_at IS NOT NULL AND anonymized_at IS NULL)
        OR (state = 'ANONYMIZED' AND deactivated_at IS NOT NULL AND anonymized_at IS NOT NULL)),
    CONSTRAINT user_account_email_set_whole CHECK ((email_set_by IS NULL) = (email_set_at IS NULL)),
    CONSTRAINT user_account_language_known CHECK (language IN ('HEBREW', 'ENGLISH')),
    -- One account per canonical address (BR-45); an anonymized account holds none, and NULLs are
    -- distinct. The persistence adapter answers a violation of it, by name, with EmailInUse.
    CONSTRAINT user_account_email_unique UNIQUE (email)
);

-- The retention job finds the accounts whose deactivation is due (NFR-258).
CREATE INDEX user_account_deactivated_at ON access.user_account (deactivated_at) WHERE state = 'DEACTIVATED';

CREATE TABLE access.user_account_role (
    user_account_id uuid NOT NULL REFERENCES access.user_account (id),
    role text NOT NULL,
    PRIMARY KEY (user_account_id, role),
    CONSTRAINT user_account_role_known CHECK (role IN ('ADMIN', 'DISPATCHER', 'DRIVER'))
);

-- The active Admins read under lock (BR-19, FR-291) and the Admins FR-389's notice goes to.
CREATE INDEX user_account_role_admin ON access.user_account_role (user_account_id) WHERE role = 'ADMIN';

CREATE TABLE access.credential_link (
    id uuid PRIMARY KEY,
    user_account_id uuid NOT NULL REFERENCES access.user_account (id),
    position integer NOT NULL,
    kind text NOT NULL,
    new_email text,
    requested_by uuid,
    token_hash text NOT NULL,
    issued_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    status text NOT NULL,
    CONSTRAINT credential_link_kind_known CHECK (kind IN ('SET_PASSWORD', 'RECOVERY', 'EMAIL_CHANGE')),
    CONSTRAINT credential_link_status_known CHECK (status IN ('OPEN', 'SPENT', 'VOIDED')),
    -- Only an email-change link carries the address it confirms and who asked (FR-387, FR-388).
    CONSTRAINT credential_link_email_change_whole CHECK (
        (kind = 'EMAIL_CHANGE') = (new_email IS NOT NULL)
        AND (kind = 'EMAIL_CHANGE') = (requested_by IS NOT NULL)),
    CONSTRAINT credential_link_position UNIQUE (user_account_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- A link is found by its token's hash (NFR-211), and the retention job reads when links were sent.
CREATE INDEX credential_link_token_hash ON access.credential_link (token_hash);
CREATE INDEX credential_link_issued_at ON access.credential_link (issued_at);
CREATE INDEX credential_link_open ON access.credential_link (user_account_id) WHERE status = 'OPEN';

-- A session is deleted 30 days after it ends (NFR-258), its tokens and contact times with it.
CREATE TABLE access.session (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    account uuid NOT NULL,
    account_generation bigint NOT NULL,
    generation bigint NOT NULL,
    state text NOT NULL,
    revoked_at timestamptz,
    opened_at timestamptz NOT NULL,
    last_password_at timestamptz NOT NULL,
    CONSTRAINT session_state_known CHECK (state IN ('LIVE', 'REVOKED')),
    CONSTRAINT session_revoked_has_time CHECK ((state = 'REVOKED') = (revoked_at IS NOT NULL))
);

-- An account's revocation finds its sessions (NFR-90), and BR-133 reads an account's contacts.
CREATE INDEX session_account ON access.session (account, account_generation);

CREATE TABLE access.refresh_token (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES access.session (id) ON DELETE CASCADE,
    position integer NOT NULL,
    token_hash text NOT NULL,
    issued_at timestamptz NOT NULL,
    state text NOT NULL,
    state_at timestamptz,
    successor uuid,
    CONSTRAINT refresh_token_state_known CHECK (state IN ('CURRENT', 'EXCHANGED', 'REPLACED', 'SUPERSEDED')),
    -- Every state but current is dated, and only an exchanged token names its successor (NFR-178).
    CONSTRAINT refresh_token_state_has_time CHECK ((state = 'CURRENT') = (state_at IS NULL)),
    CONSTRAINT refresh_token_exchanged_has_successor CHECK ((state = 'EXCHANGED') = (successor IS NOT NULL)),
    CONSTRAINT refresh_token_position UNIQUE (session_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- A refresh or a sign-out finds its session by the presented token's hash (NFR-88, NFR-89).
CREATE INDEX refresh_token_token_hash ON access.refresh_token (token_hash);
-- The retention job reads each session's current token to date its end (NFR-258).
CREATE INDEX refresh_token_current ON access.refresh_token (session_id) WHERE state = 'CURRENT';

CREATE TABLE access.session_contact (
    session_id uuid NOT NULL REFERENCES access.session (id) ON DELETE CASCADE,
    position integer NOT NULL,
    contacted_at timestamptz NOT NULL,
    CONSTRAINT session_contact_position UNIQUE (session_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- BR-133's contact window and the retention job's drop read the times (NFR-280).
CREATE INDEX session_contact_contacted_at ON access.session_contact (contacted_at);

-- The one row of the company's profile, under CompanyProfile.KEY.
CREATE TABLE access.company_profile (
    id uuid PRIMARY KEY,
    version bigint NOT NULL,
    name text NOT NULL,
    language text NOT NULL,
    default_window_opens time NOT NULL,
    default_window_closes time NOT NULL,
    default_service_time bigint,
    CONSTRAINT company_profile_one_row CHECK (id = '9ef759c1-f6c0-401c-b963-140ab41b4be2'),
    CONSTRAINT company_profile_language_known CHECK (language IN ('HEBREW', 'ENGLISH'))
);
