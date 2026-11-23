-- =====================================================================
-- V14 (Step 41): Attendance and working hours.
--   work_schedules       hours per person (default 9 h shift including 1 h break; Super Admin changes it)
--   attendance_days      one row per person per working day (India date)
--   attendance_breaks    each break taken in a day
--   attendance_events    every sign-in, punch and break, with IP address and device
--   attendance_devices   devices each person has used (to spot a new or shared laptop)
--   attendance_alerts    each attendance alert is sent only once
-- Nothing is deleted. A forgotten punch-out is closed by a manager with a note, and audited.
-- Never edit this file after it has run.
-- =====================================================================

INSERT INTO system_settings (setting_key, setting_value) VALUES
 ('attendance.defaultShiftMinutes', '540'),
 ('attendance.defaultBreakMinutes', '60')
ON CONFLICT (setting_key) DO NOTHING;

CREATE TABLE work_schedules (
    user_id                  BIGINT PRIMARY KEY REFERENCES users (id),
    shift_minutes            INTEGER NOT NULL CHECK (shift_minutes BETWEEN 60 AND 960),
    break_minutes            INTEGER NOT NULL CHECK (break_minutes BETWEEN 0 AND 240),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_user_id       BIGINT REFERENCES users (id),
    CONSTRAINT work_schedules_break_fits CHECK (break_minutes < shift_minutes)
);

CREATE TABLE attendance_days (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT NOT NULL REFERENCES users (id),
    work_date            DATE NOT NULL,
    punch_in_at          TIMESTAMPTZ NOT NULL,
    punch_out_at         TIMESTAMPTZ,
    status               VARCHAR(20) NOT NULL DEFAULT 'WORKING' CHECK (status IN ('WORKING', 'ON_BREAK', 'CLOSED')),
    shift_minutes        INTEGER NOT NULL,
    break_minutes        INTEGER NOT NULL,
    worked_minutes       INTEGER,
    break_taken_minutes  INTEGER,
    marked               BOOLEAN,
    flags                VARCHAR(400) NOT NULL DEFAULT '',
    closed_by_user_id    BIGINT REFERENCES users (id),
    close_note           VARCHAR(300),
    CONSTRAINT attendance_one_day UNIQUE (user_id, work_date)
);
CREATE INDEX idx_attendance_days_date ON attendance_days (work_date);
CREATE INDEX idx_attendance_days_open ON attendance_days (status) WHERE status <> 'CLOSED';

CREATE TABLE attendance_breaks (
    id          BIGSERIAL PRIMARY KEY,
    day_id      BIGINT NOT NULL REFERENCES attendance_days (id),
    started_at  TIMESTAMPTZ NOT NULL,
    ended_at    TIMESTAMPTZ
);
CREATE INDEX idx_attendance_breaks_day ON attendance_breaks (day_id);

CREATE TABLE attendance_events (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users (id),
    day_id        BIGINT REFERENCES attendance_days (id),
    kind          VARCHAR(20) NOT NULL CHECK (kind IN ('SIGN_IN', 'PUNCH_IN', 'BREAK_START', 'BREAK_END', 'PUNCH_OUT', 'MANAGER_CLOSE')),
    happened_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address    VARCHAR(64),
    device_id     VARCHAR(64),
    device_label  VARCHAR(120),
    user_agent    VARCHAR(400),
    flags         VARCHAR(200) NOT NULL DEFAULT ''
);
CREATE INDEX idx_attendance_events_user_time ON attendance_events (user_id, happened_at DESC);
CREATE INDEX idx_attendance_events_device ON attendance_events (device_id, happened_at DESC);

CREATE TABLE attendance_devices (
    user_id       BIGINT NOT NULL REFERENCES users (id),
    device_id     VARCHAR(64) NOT NULL,
    device_label  VARCHAR(120),
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_ip       VARCHAR(64),
    PRIMARY KEY (user_id, device_id)
);

CREATE TABLE attendance_alerts (
    day_id      BIGINT NOT NULL REFERENCES attendance_days (id),
    alert_kind  VARCHAR(30) NOT NULL,
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (day_id, alert_kind)
);

-- Who may see and manage other people's attendance. Super Admin always can; the Super Admin can
-- give these to Client Admin, Project Manager, Team Leader (or any role) in the Permission matrix.
-- They only ever cover people who share a project with the viewer.
INSERT INTO permissions (code, module, name, description, extendable, sort_order) VALUES
 ('ATTENDANCE_VIEW', 'Attendance', 'View team attendance', 'See punch times, breaks, alerts, IP addresses and devices of people on own projects.', TRUE, 245),
 ('ATTENDANCE_MANAGE', 'Attendance', 'Manage working hours', 'Change shift and break hours, and close a day someone forgot to punch out of.', TRUE, 246);

-- A guide for the Knowledge Base
INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES (
 'attendance-punch-in', 'Getting started', 'Punch in, breaks and punch out',
 'Start your working day, take your break and finish the day, so your attendance is marked.',
 'attendance punch in punch out break lunch timer hours shift time tracking clock in clock out late break overtime marked device ip haazri',
 25,
$kb$## Start your day
1. Sign in. A big glowing "Punch in" button shows at the top of every page until you press it.
2. Press Punch in. A bell rings and your day clock starts.

## Your break
1. Press Start break when you go for lunch or tea. The break card shows how much break time you have left.
2. Press End break when you are back.
- Your break is usually 1 hour in a 9 hour day. The Super Admin can change this for you.
- If your break goes over the time, the card turns red and your manager gets an alert.

## Finish your day
1. Press Punch out. A bell rings and the day is saved.

## Compulsory break
Your attendance is marked only when you have taken your full break. If you work for a long time without a break, the clock reminds you that your break is due.

## Devices and IP address
Every punch saves the computer and the network (IP address) it came from. A new laptop, a new network, or two people punching from the same computer is flagged for the Super Admin.

Warning: Punch only for yourself, from your own computer. Punching for someone else is always found.

Tip: Forgot to punch out? Ask your manager. They can close the day with a note.$kb$);

INSERT INTO kb_article_versions (article_id, version_no, category, title, summary, body, keywords, status)
SELECT id, 1, category, title, summary, body, keywords, status FROM kb_articles WHERE slug = 'attendance-punch-in';
