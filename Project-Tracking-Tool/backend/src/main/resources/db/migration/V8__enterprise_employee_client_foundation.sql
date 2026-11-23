ALTER TABLE app_users
    ADD COLUMN employee_id VARCHAR(40),
    ADD COLUMN shift_name VARCHAR(120),
    ADD COLUMN weekly_off VARCHAR(40),
    ADD COLUMN reporting_manager_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uq_app_users_employee_id
    ON app_users(employee_id) WHERE employee_id IS NOT NULL;

ALTER TABLE clients
    ADD COLUMN code VARCHAR(40),
    ADD COLUMN contact_person VARCHAR(120),
    ADD COLUMN email VARCHAR(254),
    ADD COLUMN phone VARCHAR(40),
    ADD COLUMN company_name VARCHAR(160),
    ADD COLUMN address VARCHAR(500),
    ADD COLUMN account_manager_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX uq_clients_code
    ON clients(code) WHERE code IS NOT NULL;

CREATE TABLE teams (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    project_id BIGINT REFERENCES projects(id) ON DELETE CASCADE,
    team_leader_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_team_project_name UNIQUE (project_id, name)
);

CREATE TABLE team_members (
    team_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    primary_team BOOLEAN NOT NULL DEFAULT FALSE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (team_id, user_id)
);

CREATE INDEX ix_team_members_user ON team_members(user_id);
CREATE TABLE project_employee_assignments (
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    role VARCHAR(30),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, user_id)
);
