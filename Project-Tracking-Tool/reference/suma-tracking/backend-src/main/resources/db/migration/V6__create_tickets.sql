-- =====================================================================
-- V6: Centralised tickets (FRD 8; original 23 Excel columns)
--  - ticket_statuses  : the 8 standard statuses, grouped for dashboards
--  - ticket_priorities: Critical / High / Medium / Low
--  - tickets          : one row per ticket, from any source (Zendesk,
--                       Keypanion, Excel import, manual, ...)
--  - ticket_history   : add-only history of every status change,
--                       assignment and field change (FR-046, FR-050)
-- Mapping of the 23 Excel columns is written next to each column.
-- Never edit this file after it has run.
-- =====================================================================

-- A project's client can never change (V1 + app rule). This lets tickets
-- store both and the database guarantees they always match.
ALTER TABLE projects ADD CONSTRAINT uq_projects_id_client UNIQUE (id, client_id);


-- ---------- STATUSES ----------
CREATE TABLE ticket_statuses (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(40)  NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    status_group  VARCHAR(20)  NOT NULL,           -- for KPI cards: OPEN, IN_PROGRESS, PENDING, RESOLVED, CLOSED
    is_final      BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_ticket_status_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT chk_ticket_status_group CHECK (status_group IN ('OPEN', 'IN_PROGRESS', 'PENDING', 'RESOLVED', 'CLOSED'))
);

INSERT INTO ticket_statuses (code, name, status_group, is_final, sort_order) VALUES
    ('OPEN',                'Open',                  'OPEN',        FALSE, 1),
    ('IN_PROGRESS',         'In Progress',           'IN_PROGRESS', FALSE, 2),
    ('PENDING_USER',        'Pending - User',        'PENDING',     FALSE, 3),
    ('PENDING_L1',          'Pending - L1',          'PENDING',     FALSE, 4),
    ('PENDING_L2',          'Pending - L2',          'PENDING',     FALSE, 5),
    ('PENDING_THIRD_PARTY', 'Pending - Third Party', 'PENDING',     FALSE, 6),
    ('RESOLVED',            'Resolved',              'RESOLVED',    FALSE, 7),
    ('CLOSED',              'Closed',                'CLOSED',      TRUE,  8);


-- ---------- PRIORITIES ----------
CREATE TABLE ticket_priorities (
    code        VARCHAR(20)  PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    sort_order  INTEGER      NOT NULL,

    CONSTRAINT chk_ticket_priority_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,19}$')
);

INSERT INTO ticket_priorities (code, name, sort_order) VALUES
    ('CRITICAL', 'Critical', 1),
    ('HIGH',     'High',     2),
    ('MEDIUM',   'Medium',   3),
    ('LOW',      'Low',      4);


-- ---------- TICKETS ----------
CREATE TABLE tickets (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,  -- FRD Internal Ticket ID (shown as TK-000001)
    client_id             BIGINT       NOT NULL REFERENCES clients (id),     -- FRD Client
    project_id            BIGINT       NOT NULL,                             -- FRD Project
    source_system         VARCHAR(30)  NOT NULL,                             -- FRD Ticket Source: ZENDESK, KEYPANION, EXCEL_IMPORT, MANUAL, ...
    external_ticket_id    VARCHAR(100),                                      -- Excel 2 "Ticket ID" / FRD External Ticket ID

    subject               VARCHAR(500),                                      -- FRD Subject
    description           TEXT,                                              -- FRD Description
    category              VARCHAR(100),                                      -- Excel 20 Category / FRD Category
    subcategory           VARCHAR(100),                                      -- Excel 21 Subcategory
    issue_type            VARCHAR(100),                                      -- Excel 17 Issue Type
    topic                 VARCHAR(200),                                      -- Excel 19 Topic
    plan                  VARCHAR(100),                                      -- Excel 18 Plan
    priority_code         VARCHAR(20)  REFERENCES ticket_priorities (code),  -- Excel 15 Priority / FRD Priority
    status_id             BIGINT       NOT NULL REFERENCES ticket_statuses (id), -- Excel 13 Status / FRD Status

    assignee_user_id      BIGINT       REFERENCES users (id),                -- Excel 6 Assignee / FRD Assigned Employee
    assignee_name_raw     VARCHAR(150),                                      --   (original text when no employee matches)
    closed_by_user_id     BIGINT       REFERENCES users (id),                -- Excel 12 Closed By
    closed_by_name_raw    VARCHAR(150),
    requester_name        VARCHAR(200),                                      -- Excel 22 Requester
    requester_role        VARCHAR(100),                                      -- Excel 23 Requester Role
    channel               VARCHAR(50),                                       -- Excel 16 Source (email / phone / portal ...; meaning to confirm)
    escalated             BOOLEAN      NOT NULL DEFAULT FALSE,               -- Excel 8 Escalated (Yes/No)
    satisfaction_rating   VARCHAR(50),                                       -- Excel 14 Satisfaction Rating

    created_at_source     TIMESTAMPTZ  NOT NULL,                             -- Excel 3+5 Date/Time of Creation / FRD Created Date
    updated_at_source     TIMESTAMPTZ,                                       -- FRD Updated Date (last update in the source system)
    first_response_at     TIMESTAMPTZ,                                       -- for SLA (first response)
    resolved_at           TIMESTAMPTZ,                                       -- FRD Resolution Date
    closed_at             TIMESTAMPTZ,                                       -- Excel 9+10 Date/Time of Closure
    sla_due_at            TIMESTAMPTZ,                                       -- FRD SLA Due Date

    -- Original text of columns we also calculate ourselves (kept to compare and to report differences):
    timeframe_raw         VARCHAR(50),                                       -- Excel 4 Timeframe
    day_raw               VARCHAR(20),                                       -- Excel 7 Day
    resolution_time_raw   VARCHAR(20),                                       -- Excel 11 Resolution Time (hh:mm:ss)
    import_row_number     INTEGER,                                           -- Excel 1 Sr.no (row number in the import file)
    import_batch_id       BIGINT,                                            -- which import created/updated it (table comes later)

    extra_fields          JSONB,                                             -- source-specific fields (only where useful)

    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),               -- when OUR system stored it
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version               INTEGER      NOT NULL DEFAULT 0,

    -- The project must belong to the same client (database-guaranteed client isolation).
    CONSTRAINT fk_tickets_project_client FOREIGN KEY (project_id, client_id) REFERENCES projects (id, client_id),
    CONSTRAINT chk_tickets_source_system CHECK (source_system ~ '^[A-Z][A-Z0-9_]{1,29}$'),
    CONSTRAINT chk_tickets_external_id_not_blank CHECK (external_ticket_id IS NULL OR btrim(external_ticket_id) <> '')
);

-- Duplicate protection (FR-047, FR-059): the same external ticket number from the same
-- system cannot be stored twice for the same project. (Different projects/systems may
-- reuse the same numbers, so the project and system are part of the rule.)
CREATE UNIQUE INDEX uq_tickets_project_source_external
    ON tickets (project_id, source_system, external_ticket_id)
    WHERE external_ticket_id IS NOT NULL;

CREATE INDEX ix_tickets_client ON tickets (client_id);
CREATE INDEX ix_tickets_project ON tickets (project_id);
CREATE INDEX ix_tickets_status ON tickets (status_id);
CREATE INDEX ix_tickets_assignee ON tickets (assignee_user_id);
CREATE INDEX ix_tickets_priority ON tickets (priority_code);
CREATE INDEX ix_tickets_created_at_source ON tickets (created_at_source);
CREATE INDEX ix_tickets_external_id ON tickets (external_ticket_id);


-- ---------- TICKET HISTORY (add-only) ----------
CREATE TABLE ticket_history (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id           BIGINT       NOT NULL REFERENCES tickets (id),
    occurred_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    event_type          VARCHAR(30)  NOT NULL,     -- CREATED, STATUS_CHANGED, ASSIGNED, UPDATED, IMPORTED, SYNCED
    field_name          VARCHAR(60),               -- which field changed (for UPDATED)
    old_value           TEXT,
    new_value           TEXT,
    changed_by_user_id  BIGINT       REFERENCES users (id),
    source              VARCHAR(20)  NOT NULL DEFAULT 'USER',
    note                VARCHAR(500),

    CONSTRAINT chk_ticket_history_event CHECK (event_type IN
        ('CREATED', 'STATUS_CHANGED', 'ASSIGNED', 'UPDATED', 'IMPORTED', 'SYNCED')),
    CONSTRAINT chk_ticket_history_source CHECK (source IN ('USER', 'IMPORT', 'INTEGRATION', 'SYSTEM'))
);

CREATE INDEX ix_ticket_history_ticket ON ticket_history (ticket_id, occurred_at);

CREATE TRIGGER trg_ticket_history_no_update_delete
    BEFORE UPDATE OR DELETE ON ticket_history
    FOR EACH ROW EXECUTE FUNCTION audit.block_changes();

CREATE TRIGGER trg_ticket_history_no_truncate
    BEFORE TRUNCATE ON ticket_history
    FOR EACH STATEMENT EXECUTE FUNCTION audit.block_changes();