-- =====================================================================
-- V1: Clients and Projects (FRD sections 5 and 6)
-- Flyway runs this file ONCE. Never edit it after it has run.
-- Any later change must go in a NEW file (V2__..., V3__...).
-- =====================================================================

-- ---------- CLIENTS ----------
CREATE TABLE clients (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,  -- system-generated Client ID (FR-017)
    client_name      VARCHAR(150) NOT NULL,
    company_name     VARCHAR(200),
    contact_person   VARCHAR(150),
    email            VARCHAR(254),
    contact_number   VARCHAR(30),
    address          TEXT,
    business_domain  VARCHAR(100),
    -- account_manager: added later as a link to the users table (when users exist)
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    onboarding_date  DATE,
    remarks          TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          INTEGER      NOT NULL DEFAULT 0,                  -- stops two people overwriting each other

    CONSTRAINT chk_clients_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_clients_name_not_blank CHECK (btrim(client_name) <> '')
);

-- Client names must be unique, ignoring capital/small letters ("Hero" = "hero")
CREATE UNIQUE INDEX uq_clients_name ON clients (lower(client_name));


-- ---------- PROJECTS ----------
CREATE TABLE projects (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,  -- Project ID
    client_id              BIGINT       NOT NULL REFERENCES clients (id),    -- every project belongs to one client
    project_name           VARCHAR(150) NOT NULL,
    description            TEXT,
    supports_l1            BOOLEAN      NOT NULL DEFAULT FALSE,              -- Support Level (L1/L2/L3)
    supports_l2            BOOLEAN      NOT NULL DEFAULT FALSE,
    supports_l3            BOOLEAN      NOT NULL DEFAULT FALSE,
    support_start_date     DATE,
    support_end_date       DATE,
    time_zone              VARCHAR(64)  NOT NULL DEFAULT 'Asia/Kolkata',     -- used for SLA and support hours
    external_ticket_system VARCHAR(50),                                      -- e.g. ZENDESK, KEYPANION, EXCEL_IMPORT
    status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                INTEGER      NOT NULL DEFAULT 0,
    -- Project Manager, Team Leader, employees, support hours, weekly offs,
    -- holidays and SLA are added in later files, as separate linked tables.

    CONSTRAINT chk_projects_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'CLOSED')),
    CONSTRAINT chk_projects_name_not_blank CHECK (btrim(project_name) <> ''),
    CONSTRAINT chk_projects_dates CHECK (support_end_date IS NULL OR support_start_date IS NULL
                                         OR support_end_date >= support_start_date)
);

-- The same project name cannot appear twice under the same client
CREATE UNIQUE INDEX uq_projects_client_name ON projects (client_id, lower(project_name));
-- Fast lookup of all projects of one client
CREATE INDEX ix_projects_client_id ON projects (client_id);