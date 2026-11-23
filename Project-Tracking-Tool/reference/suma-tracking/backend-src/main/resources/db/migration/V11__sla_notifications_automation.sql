-- =====================================================================
-- V11 (Step 37): SLA Management, Notifications and Automation.
--   sla_calendars      support hours per project (24x7 or business hours), warning level, pause rule
--   sla_targets        first response + resolution targets per project and priority
--   sla_holidays       days the SLA clock does not run (business hours mode)
--   sla_alerts_sent    each SLA alert is sent only once per ticket
--   notifications      in-app notifications per employee
--   ticket_assignment_seen  remembers who was last told about a ticket assignment
--   automation_rules   the switches on the Automation page
--   automation_runs    one row per automation run (every 5 minutes, or "Run now")
--   automation_actions what each run did (the execution log)
-- Never edit this file after it has run.
-- =====================================================================

CREATE TABLE sla_calendars (
    project_id          BIGINT PRIMARY KEY REFERENCES projects (id),
    hours_mode          VARCHAR(20) NOT NULL DEFAULT 'TWENTY_FOUR_SEVEN'
                        CHECK (hours_mode IN ('TWENTY_FOUR_SEVEN', 'BUSINESS_HOURS')),
    work_days           VARCHAR(40) NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI',
    day_start           TIME NOT NULL DEFAULT '09:00',
    day_end             TIME NOT NULL DEFAULT '18:00',
    warn_percent        INTEGER NOT NULL DEFAULT 80 CHECK (warn_percent BETWEEN 50 AND 95),
    pause_when_waiting  BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_user_id  BIGINT REFERENCES users (id),
    CONSTRAINT sla_calendars_hours_order CHECK (day_end > day_start)
);

CREATE TABLE sla_targets (
    id                      BIGSERIAL PRIMARY KEY,
    project_id              BIGINT NOT NULL REFERENCES projects (id),
    priority_code           VARCHAR(20) NOT NULL CHECK (priority_code IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    first_response_minutes  INTEGER NOT NULL CHECK (first_response_minutes BETWEEN 1 AND 43200),
    resolution_minutes      INTEGER NOT NULL CHECK (resolution_minutes BETWEEN 1 AND 129600),
    CONSTRAINT sla_targets_order CHECK (resolution_minutes >= first_response_minutes),
    CONSTRAINT sla_targets_one_per_priority UNIQUE (project_id, priority_code)
);

CREATE TABLE sla_holidays (
    id            BIGSERIAL PRIMARY KEY,
    project_id    BIGINT NOT NULL REFERENCES projects (id),
    holiday_date  DATE NOT NULL,
    name          VARCHAR(100) NOT NULL,
    CONSTRAINT sla_holidays_one_per_day UNIQUE (project_id, holiday_date)
);

-- Starting values for every existing project (they can be changed on the SLA page).
INSERT INTO sla_calendars (project_id) SELECT id FROM projects;

INSERT INTO sla_targets (project_id, priority_code, first_response_minutes, resolution_minutes)
SELECT p.id, v.code, v.response_min, v.resolution_min
FROM projects p
CROSS JOIN (VALUES ('CRITICAL', 60, 240),
                   ('HIGH', 120, 480),
                   ('MEDIUM', 240, 1440),
                   ('LOW', 480, 2880)) AS v (code, response_min, resolution_min);

CREATE TABLE sla_alerts_sent (
    ticket_id   BIGINT NOT NULL REFERENCES tickets (id),
    alert_kind  VARCHAR(30) NOT NULL CHECK (alert_kind IN ('RESPONSE_AT_RISK', 'RESPONSE_BREACHED',
                                                           'RESOLUTION_AT_RISK', 'RESOLUTION_BREACHED')),
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (ticket_id, alert_kind)
);

CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (id),
    kind        VARCHAR(40) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    body        VARCHAR(1000),
    ticket_id   BIGINT REFERENCES tickets (id),
    project_id  BIGINT REFERENCES projects (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at     TIMESTAMPTZ
);
CREATE INDEX idx_notifications_user_time ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE ticket_assignment_seen (
    ticket_id  BIGINT PRIMARY KEY REFERENCES tickets (id),
    user_id    BIGINT NOT NULL REFERENCES users (id),
    seen_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Assignments that exist today are not announced again.
INSERT INTO ticket_assignment_seen (ticket_id, user_id)
SELECT id, assignee_user_id FROM tickets WHERE assignee_user_id IS NOT NULL;

CREATE TABLE automation_rules (
    code                VARCHAR(40) PRIMARY KEY,
    name                VARCHAR(150) NOT NULL,
    description         VARCHAR(500) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order          INTEGER NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_user_id  BIGINT REFERENCES users (id)
);

INSERT INTO automation_rules (code, name, description, enabled, sort_order) VALUES
 ('SLA_ALERTS', 'SLA alerts',
  'Notify the assignee and the project leads when a ticket is about to break its SLA, and again when it breaks it. Super Admins are told about breaches.',
  TRUE, 1),
 ('ASSIGNMENT_ALERTS', 'Assignment alerts',
  'Notify an employee when an open ticket is assigned to them.',
  TRUE, 2),
 ('AUTO_ESCALATE_ON_BREACH', 'Auto-escalate on SLA breach',
  'When an open ticket breaks its resolution target, mark it escalated and move it to Pending - L2 (Tier 2), then tell the project leads.',
  FALSE, 3),
 ('AUTO_ASSIGN', 'Auto-assign new tickets',
  'Give each new unassigned open ticket to the active L1 member of the project who has the fewest open tickets and is not on a weekly off today.',
  FALSE, 4),
 ('DAILY_SUMMARY', 'Daily summary',
  'Every morning after 09:00 India time, send Super Admins and Project Managers yesterday''s numbers per project.',
  TRUE, 5);

CREATE TABLE automation_runs (
    id                     BIGSERIAL PRIMARY KEY,
    trigger_type           VARCHAR(20) NOT NULL CHECK (trigger_type IN ('SCHEDULE', 'MANUAL')),
    started_by_user_id     BIGINT REFERENCES users (id),
    started_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at            TIMESTAMPTZ,
    status                 VARCHAR(20) NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    tickets_checked        INTEGER NOT NULL DEFAULT 0,
    notifications_created  INTEGER NOT NULL DEFAULT 0,
    tickets_escalated      INTEGER NOT NULL DEFAULT 0,
    tickets_assigned       INTEGER NOT NULL DEFAULT 0,
    message                VARCHAR(1000)
);
CREATE INDEX idx_automation_runs_time ON automation_runs (started_at DESC);

CREATE TABLE automation_actions (
    id          BIGSERIAL PRIMARY KEY,
    run_id      BIGINT NOT NULL REFERENCES automation_runs (id),
    rule_code   VARCHAR(40) NOT NULL REFERENCES automation_rules (code),
    ticket_id   BIGINT REFERENCES tickets (id),
    action      VARCHAR(40) NOT NULL,
    detail      VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_automation_actions_run ON automation_actions (run_id);
CREATE INDEX idx_automation_actions_rule_time ON automation_actions (rule_code, created_at DESC);