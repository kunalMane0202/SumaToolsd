-- =====================================================================
-- V7: Statuses and value mappings approved by the user on 4 Nov 2026
--  - 2 new statuses used in the Codility Excel sheet: Merged, On Hold
--  - ticket_value_mappings: how words in an imported file / external
--    system are translated to our statuses and priorities, PER PROJECT
--    (the same word can mean different things in different teams).
--  - Codility (Zendesk) mappings, as approved:
--      Solved -> RESOLVED, Pending -> PENDING_USER,
--      Open -> PENDING_L2 (their "Open" = escalated to Tier 2),
--      Merged -> MERGED, Hold -> ON_HOLD, Closed -> CLOSED
--      Urgent -> CRITICAL, High -> HIGH, Normal -> MEDIUM, Low -> LOW
-- Never edit this file after it has run.
-- =====================================================================

INSERT INTO ticket_statuses (code, name, status_group, is_final, sort_order) VALUES
    ('ON_HOLD', 'On Hold', 'PENDING', FALSE, 9),
    ('MERGED',  'Merged',  'CLOSED',  TRUE,  10);


CREATE TABLE ticket_value_mappings (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id    BIGINT       NOT NULL REFERENCES projects (id),
    field         VARCHAR(20)  NOT NULL,          -- STATUS or PRIORITY
    source_value  VARCHAR(100) NOT NULL,          -- the word as written in the file / system
    target_code   VARCHAR(40)  NOT NULL,          -- our status code or priority code
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_tvm_field CHECK (field IN ('STATUS', 'PRIORITY')),
    CONSTRAINT chk_tvm_source_value_not_blank CHECK (btrim(source_value) <> '')
);

-- One mapping per word per field per project (capital/small letters and spaces around ignored)
CREATE UNIQUE INDEX uq_tvm_project_field_value
    ON ticket_value_mappings (project_id, field, lower(btrim(source_value)));

-- Codility (Zendesk) — approved 4 Nov 2026
INSERT INTO ticket_value_mappings (project_id, field, source_value, target_code)
SELECT p.id, m.field, m.source_value, m.target_code
FROM projects p
JOIN clients c ON c.id = p.client_id
CROSS JOIN (VALUES
    ('STATUS',   'Solved',  'RESOLVED'),
    ('STATUS',   'Pending', 'PENDING_USER'),
    ('STATUS',   'Open',    'PENDING_L2'),
    ('STATUS',   'Merged',  'MERGED'),
    ('STATUS',   'Hold',    'ON_HOLD'),
    ('STATUS',   'Closed',  'CLOSED'),
    ('PRIORITY', 'Urgent',  'CRITICAL'),
    ('PRIORITY', 'High',    'HIGH'),
    ('PRIORITY', 'Normal',  'MEDIUM'),
    ('PRIORITY', 'Low',     'LOW')
) AS m(field, source_value, target_code)
WHERE p.project_name = 'Codility' AND c.client_name = 'Codility';