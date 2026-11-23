-- =====================================================================
-- V3: Roles, users (employees) and project assignments (FRD 3 and 7)
-- Roles follow the architecture diagram (3 user groups) and the FRD (8 roles).
-- Login fields (password etc.) are added later, in the login step.
-- Never edit this file after it has run.
-- =====================================================================

-- ---------- ROLES ----------
CREATE TABLE roles (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code         VARCHAR(40)  NOT NULL UNIQUE,      -- fixed code used by the program, e.g. SUPER_ADMIN
    name         VARCHAR(100) NOT NULL,             -- name shown on screen
    user_group   VARCHAR(30)  NOT NULL,             -- the 3 user types from the architecture diagram
    description  TEXT,
    sort_order   INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_roles_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT chk_roles_group CHECK (user_group IN ('SUPER_ADMIN', 'PROJECT_MANAGER', 'SUPPORT_USER'))
);

INSERT INTO roles (code, name, user_group, description, sort_order) VALUES
    ('SUPER_ADMIN',       'Super Admin',       'SUPER_ADMIN',     'Complete platform administration',                 1),
    ('MANAGEMENT_VIEWER', 'Management Viewer', 'SUPER_ADMIN',     'Views dashboards and management reports (read-only)', 2),
    ('CLIENT_ADMIN',      'Client Admin',      'PROJECT_MANAGER', 'Manages assigned client information and projects', 3),
    ('PROJECT_MANAGER',   'Project Manager',   'PROJECT_MANAGER', 'Manages assigned projects and support operations', 4),
    ('TEAM_LEADER',       'Team Leader',       'PROJECT_MANAGER', 'Manages team members and ticket allocation',       5),
    ('L3_SUPPORT',        'L3 Support',        'SUPPORT_USER',    'Handles technical and development-level issues',   6),
    ('L2_SUPPORT',        'L2 Support',        'SUPPORT_USER',    'Handles advanced support and escalations',         7),
    ('L1_SUPPORT',        'L1 Support',        'SUPPORT_USER',    'Handles first-level support tickets',              8);


-- ---------- USERS (employees) ----------
CREATE TABLE users (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_code        VARCHAR(30)  NOT NULL,            -- Employee ID (company number)
    full_name            VARCHAR(150) NOT NULL,
    email                VARCHAR(254) NOT NULL,            -- also the future login name
    contact_number       VARCHAR(30),
    designation          VARCHAR(100),
    department           VARCHAR(100),
    skill_set            TEXT,
    role_id              BIGINT       NOT NULL REFERENCES roles (id),
    reporting_manager_id BIGINT       REFERENCES users (id),
    shift_start          TIME,                             -- e.g. 09:30
    shift_end            TIME,                             -- e.g. 18:30 (may be past midnight for night shifts)
    weekly_off           VARCHAR(30),                      -- e.g. SAT,SUN
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version              INTEGER      NOT NULL DEFAULT 0,

    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_users_name_not_blank CHECK (btrim(full_name) <> ''),
    CONSTRAINT chk_users_code_not_blank CHECK (btrim(employee_code) <> ''),
    CONSTRAINT chk_users_not_own_manager CHECK (reporting_manager_id IS NULL OR reporting_manager_id <> id),
    CONSTRAINT chk_users_weekly_off CHECK (weekly_off IS NULL
        OR weekly_off ~ '^(MON|TUE|WED|THU|FRI|SAT|SUN)(,(MON|TUE|WED|THU|FRI|SAT|SUN)){0,6}$')
);

-- Employee ID and email must be unique (email ignoring capital/small letters)
CREATE UNIQUE INDEX uq_users_employee_code ON users (lower(employee_code));
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));
CREATE INDEX ix_users_role_id ON users (role_id);
CREATE INDEX ix_users_reporting_manager_id ON users (reporting_manager_id);


-- ---------- PROJECT ASSIGNMENTS (who works on which project) ----------
CREATE TABLE project_assignments (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id     BIGINT      NOT NULL REFERENCES projects (id),
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    project_role   VARCHAR(30) NOT NULL,   -- this person's job ON THIS PROJECT
    support_level  VARCHAR(2),             -- L1 / L2 / L3 on this project (can differ per project)
    active         BOOLEAN     NOT NULL DEFAULT TRUE,  -- removed people are set to FALSE (history is kept)
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT chk_pa_project_role CHECK (project_role IN ('PROJECT_MANAGER', 'TEAM_LEADER', 'MEMBER')),
    CONSTRAINT chk_pa_support_level CHECK (support_level IS NULL OR support_level IN ('L1', 'L2', 'L3')),
    CONSTRAINT uq_pa_project_user UNIQUE (project_id, user_id)
);

CREATE INDEX ix_pa_user_id ON project_assignments (user_id);


-- ---------- CLIENTS: Account Manager (FRD 5.2), now that users exist ----------
ALTER TABLE clients ADD COLUMN account_manager_id BIGINT REFERENCES users (id);
CREATE INDEX ix_clients_account_manager_id ON clients (account_manager_id);