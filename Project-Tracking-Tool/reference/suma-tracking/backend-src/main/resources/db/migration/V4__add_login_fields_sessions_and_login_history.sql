-- =====================================================================
-- V4: Login support (FRD 3: FR-005, FR-006, FR-007; FR-096)
--  - users get a password HASH (never the real password), lock-out and
--    "must change password" fields
--  - user_sessions: one row per login. Only a SHA-256 fingerprint of the
--    token is stored, so a stolen database copy cannot be used to log in.
--  - login_history: every login attempt, successful or not.
-- Never edit this file after it has run.
-- =====================================================================

ALTER TABLE users
    ADD COLUMN password_hash        VARCHAR(100),                     -- BCrypt hash, never the real password
    ADD COLUMN password_changed_at  TIMESTAMPTZ,
    ADD COLUMN must_change_password BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN failed_login_count   INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN locked_until         TIMESTAMPTZ,                      -- temporary lock after too many wrong passwords
    ADD COLUMN last_login_at        TIMESTAMPTZ;

ALTER TABLE users
    ADD CONSTRAINT chk_users_failed_login_count CHECK (failed_login_count >= 0);


-- ---------- LOGIN SESSIONS ----------
CREATE TABLE user_sessions (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    token_hash    CHAR(64)     NOT NULL,          -- SHA-256 fingerprint (hex) of the token
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ  NOT NULL,          -- hard end of the session
    last_used_at  TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,                    -- set on logout or when an admin cancels access
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(300),

    CONSTRAINT uq_user_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_user_sessions_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_user_sessions_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_user_sessions_user_id ON user_sessions (user_id);


-- ---------- LOGIN HISTORY (FR-005) ----------
CREATE TABLE login_history (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT       REFERENCES users (id),   -- empty if the email did not match anyone
    email_attempted  VARCHAR(254) NOT NULL,
    event            VARCHAR(30)  NOT NULL,
    ip_address       VARCHAR(45),
    user_agent       VARCHAR(300),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_login_history_event CHECK (event IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILED', 'LOGIN_LOCKED', 'LOGIN_INACTIVE', 'LOGIN_NO_PASSWORD',
        'LOGOUT', 'PASSWORD_CHANGED', 'PASSWORD_RESET', 'ACCESS_REVOKED'))
);

CREATE INDEX ix_login_history_user_id ON login_history (user_id);
CREATE INDEX ix_login_history_created_at ON login_history (created_at);