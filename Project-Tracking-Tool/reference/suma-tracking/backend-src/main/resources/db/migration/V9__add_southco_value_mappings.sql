-- =====================================================================
-- V9: SouthCo (Keypanion) status / priority words, approved by the user on 5 Nov 2026
--   Open       -> PENDING_L2   (as in Codility: "Open" = escalated to Tier 2)
--   Pending    -> PENDING_USER (we answered; waiting for the customer's reply)
--   In Process -> IN_PROGRESS
--   Closed     -> CLOSED
--   also: Solved -> RESOLVED, Hold -> ON_HOLD, Merged -> MERGED
--   Urgent -> CRITICAL, High -> HIGH, Medium -> MEDIUM, Normal -> MEDIUM, Low -> LOW
-- Never edit this file after it has run.
-- =====================================================================

INSERT INTO ticket_value_mappings (project_id, field, source_value, target_code)
SELECT p.id, m.field, m.source_value, m.target_code
FROM projects p
JOIN clients c ON c.id = p.client_id
CROSS JOIN (VALUES
    ('STATUS',   'Open',       'PENDING_L2'),
    ('STATUS',   'Pending',    'PENDING_USER'),
    ('STATUS',   'In Process', 'IN_PROGRESS'),
    ('STATUS',   'Closed',     'CLOSED'),
    ('STATUS',   'Solved',     'RESOLVED'),
    ('STATUS',   'Hold',       'ON_HOLD'),
    ('STATUS',   'Merged',     'MERGED'),
    ('PRIORITY', 'Urgent',     'CRITICAL'),
    ('PRIORITY', 'High',       'HIGH'),
    ('PRIORITY', 'Medium',     'MEDIUM'),
    ('PRIORITY', 'Normal',     'MEDIUM'),
    ('PRIORITY', 'Low',        'LOW')
) AS m(field, source_value, target_code)
WHERE p.project_name = 'SouthCo' AND c.client_name = 'SouthCo';