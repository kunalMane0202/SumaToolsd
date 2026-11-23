-- =====================================================================
-- V2: The 4 original projects, each under a client with the same name.
-- Client names are temporary (same as the project name). They can be
-- renamed later in the app. Never edit this file after it has run.
-- =====================================================================

INSERT INTO clients (client_name, remarks) VALUES
    ('Suma Tool', 'Initial client. Name is the same as the project name for now.'),
    ('Hero',      'Initial client. Name is the same as the project name for now.'),
    ('Codility',  'Initial client. Name is the same as the project name for now.'),
    ('SouthCo',   'Initial client. Name is the same as the project name for now.');

INSERT INTO projects (client_id, project_name, external_ticket_system)
SELECT c.id, p.project_name, p.external_ticket_system
FROM (VALUES
        ('Suma Tool', 'Suma Tool', NULL),
        ('Hero',      'Hero',      NULL),
        ('Codility',  'Codility',  'ZENDESK'),
        ('SouthCo',   'SouthCo',   'KEYPANION')
     ) AS p(client_name, project_name, external_ticket_system)
JOIN clients c ON c.client_name = p.client_name;