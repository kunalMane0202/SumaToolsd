-- =====================================================================
-- V13 (Step 39): Role-based access control (RBAC) with permissions.
--   permissions           the list of things a person can be allowed to do
--   permission_defaults   what each built-in role may do by default
--                         (GROUP_PROJECT_MANAGER / GROUP_SUPPORT_USER = roles created later)
--   role_permission_sets  roles whose permissions the Super Admin has changed
--   role_permissions      the chosen permissions of those changed roles
-- Super Admin always has every permission; it cannot be changed.
-- A permission with extendable = FALSE can be removed from a role, but only given to roles
-- that have it by default (the server's own rules for it are fixed).
-- Every change is written to the audit log. Never edit this file after it has run.
-- =====================================================================

CREATE TABLE permissions (
    code         VARCHAR(60) PRIMARY KEY,
    module       VARCHAR(40) NOT NULL,
    name         VARCHAR(120) NOT NULL,
    description  VARCHAR(300) NOT NULL,
    extendable   BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order   INTEGER NOT NULL
);

CREATE TABLE permission_defaults (
    profile          VARCHAR(60) NOT NULL,
    permission_code  VARCHAR(60) NOT NULL REFERENCES permissions (code),
    PRIMARY KEY (profile, permission_code)
);

CREATE TABLE role_permission_sets (
    role_id             BIGINT PRIMARY KEY REFERENCES roles (id),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_user_id  BIGINT REFERENCES users (id)
);

CREATE TABLE role_permissions (
    role_id             BIGINT NOT NULL REFERENCES roles (id),
    permission_code     VARCHAR(60) NOT NULL REFERENCES permissions (code),
    granted_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by_user_id  BIGINT REFERENCES users (id),
    PRIMARY KEY (role_id, permission_code)
);
CREATE INDEX idx_role_permissions_role ON role_permissions (role_id);

INSERT INTO permissions (code, module, name, description, extendable, sort_order) VALUES
 ('DASHBOARD_VIEW', 'Dashboard', 'View dashboard', 'See ticket numbers and charts of own projects.', TRUE, 10),
 ('REPORT_VIEW', 'Reports', 'View and download reports', 'Open the Reports page and download CSV files for own projects.', TRUE, 20),
 ('TICKET_VIEW', 'Tickets', 'View tickets', 'Find and open tickets of own projects.', TRUE, 30),
 ('TICKET_CREATE', 'Tickets', 'Create tickets', 'Create a ticket in an own project.', FALSE, 40),
 ('TICKET_UPDATE', 'Tickets', 'Change ticket status', 'Change the status of a ticket. Support agents only on tickets assigned to them.', FALSE, 50),
 ('TICKET_ASSIGN', 'Tickets', 'Assign and reassign tickets', 'Give a ticket to a team member of the project.', FALSE, 60),
 ('TICKET_ESCALATE', 'Tickets', 'Escalate to Tier 2', 'Move a ticket to the L2 team. Agents only on tickets assigned to them.', FALSE, 70),
 ('IMPORT_VIEW', 'Integrations', 'View imports', 'Open Integration Management and see the import history.', FALSE, 80),
 ('IMPORT_RUN', 'Integrations', 'Upload Excel sheets', 'Check and import ticket sheets for own projects.', FALSE, 90),
 ('CLIENT_VIEW', 'Clients', 'View clients', 'See client names in lists, filters and reports.', TRUE, 100),
 ('CLIENT_EDIT_ASSIGNED', 'Clients', 'Edit own clients', 'Change the details of the clients given to the Client Admin.', FALSE, 110),
 ('CLIENT_MANAGE', 'Clients', 'Manage all clients', 'Add, edit, delete and restore any client and set account managers.', FALSE, 120),
 ('PROJECT_VIEW', 'Projects', 'View projects', 'Open Project Management for own projects.', TRUE, 130),
 ('PROJECT_MANAGE', 'Projects', 'Create and change projects', 'Create, edit, activate, deactivate and close projects.', FALSE, 140),
 ('PROJECT_TEAM_MANAGE', 'Projects', 'Manage project teams', 'Add employees to a project team or remove them.', FALSE, 150),
 ('WORKLOAD_VIEW', 'Team', 'View team and workload', 'See the team members of own projects and their open work.', TRUE, 160),
 ('USER_MANAGE', 'Team', 'Manage employees', 'Create and change employees, set passwords, activate and deactivate.', FALSE, 170),
 ('SLA_VIEW', 'SLA', 'View SLA', 'See SLA percentages and late tickets of own projects.', TRUE, 180),
 ('SLA_MANAGE', 'SLA', 'Change SLA targets', 'Change targets, support hours and holidays of own projects.', TRUE, 190),
 ('AUTOMATION_MANAGE', 'Automation', 'Manage automation', 'Switch automation rules on or off and run them.', FALSE, 200),
 ('KB_VIEW', 'Knowledge Base', 'Read the Knowledge Base', 'Search and read the help guides.', TRUE, 210),
 ('KB_MANAGE', 'Knowledge Base', 'Write guides', 'Add, edit and hide help guides.', TRUE, 220),
 ('AUDIT_VIEW', 'Security', 'View audit log', 'See the record of every change.', FALSE, 230),
 ('ROLE_MANAGE', 'Security', 'Manage roles and permissions', 'Create roles and change what each role may do.', FALSE, 240),
 ('SETTINGS_MANAGE', 'Settings', 'Change tool settings', 'Change company, SLA starting targets and automation limits.', FALSE, 250);

INSERT INTO permission_defaults (profile, permission_code) VALUES
 ('CLIENT_ADMIN', 'DASHBOARD_VIEW'),
 ('PROJECT_MANAGER', 'DASHBOARD_VIEW'),
 ('TEAM_LEADER', 'DASHBOARD_VIEW'),
 ('L1_SUPPORT', 'DASHBOARD_VIEW'),
 ('L2_SUPPORT', 'DASHBOARD_VIEW'),
 ('L3_SUPPORT', 'DASHBOARD_VIEW'),
 ('MANAGEMENT_VIEWER', 'DASHBOARD_VIEW'),
 ('CLIENT_ADMIN', 'REPORT_VIEW'),
 ('PROJECT_MANAGER', 'REPORT_VIEW'),
 ('TEAM_LEADER', 'REPORT_VIEW'),
 ('MANAGEMENT_VIEWER', 'REPORT_VIEW'),
 ('CLIENT_ADMIN', 'TICKET_VIEW'),
 ('PROJECT_MANAGER', 'TICKET_VIEW'),
 ('TEAM_LEADER', 'TICKET_VIEW'),
 ('L1_SUPPORT', 'TICKET_VIEW'),
 ('L2_SUPPORT', 'TICKET_VIEW'),
 ('L3_SUPPORT', 'TICKET_VIEW'),
 ('MANAGEMENT_VIEWER', 'TICKET_VIEW'),
 ('CLIENT_ADMIN', 'TICKET_CREATE'),
 ('PROJECT_MANAGER', 'TICKET_CREATE'),
 ('TEAM_LEADER', 'TICKET_CREATE'),
 ('L1_SUPPORT', 'TICKET_CREATE'),
 ('L2_SUPPORT', 'TICKET_CREATE'),
 ('L3_SUPPORT', 'TICKET_CREATE'),
 ('CLIENT_ADMIN', 'TICKET_UPDATE'),
 ('PROJECT_MANAGER', 'TICKET_UPDATE'),
 ('TEAM_LEADER', 'TICKET_UPDATE'),
 ('L1_SUPPORT', 'TICKET_UPDATE'),
 ('L2_SUPPORT', 'TICKET_UPDATE'),
 ('L3_SUPPORT', 'TICKET_UPDATE'),
 ('CLIENT_ADMIN', 'TICKET_ASSIGN'),
 ('PROJECT_MANAGER', 'TICKET_ASSIGN'),
 ('TEAM_LEADER', 'TICKET_ASSIGN'),
 ('CLIENT_ADMIN', 'TICKET_ESCALATE'),
 ('PROJECT_MANAGER', 'TICKET_ESCALATE'),
 ('TEAM_LEADER', 'TICKET_ESCALATE'),
 ('L1_SUPPORT', 'TICKET_ESCALATE'),
 ('CLIENT_ADMIN', 'IMPORT_VIEW'),
 ('PROJECT_MANAGER', 'IMPORT_VIEW'),
 ('CLIENT_ADMIN', 'IMPORT_RUN'),
 ('PROJECT_MANAGER', 'IMPORT_RUN'),
 ('CLIENT_ADMIN', 'CLIENT_VIEW'),
 ('PROJECT_MANAGER', 'CLIENT_VIEW'),
 ('TEAM_LEADER', 'CLIENT_VIEW'),
 ('MANAGEMENT_VIEWER', 'CLIENT_VIEW'),
 ('CLIENT_ADMIN', 'CLIENT_EDIT_ASSIGNED'),
 ('CLIENT_ADMIN', 'PROJECT_VIEW'),
 ('PROJECT_MANAGER', 'PROJECT_VIEW'),
 ('TEAM_LEADER', 'PROJECT_VIEW'),
 ('MANAGEMENT_VIEWER', 'PROJECT_VIEW'),
 ('CLIENT_ADMIN', 'WORKLOAD_VIEW'),
 ('PROJECT_MANAGER', 'WORKLOAD_VIEW'),
 ('TEAM_LEADER', 'WORKLOAD_VIEW'),
 ('MANAGEMENT_VIEWER', 'WORKLOAD_VIEW'),
 ('CLIENT_ADMIN', 'SLA_VIEW'),
 ('PROJECT_MANAGER', 'SLA_VIEW'),
 ('TEAM_LEADER', 'SLA_VIEW'),
 ('MANAGEMENT_VIEWER', 'SLA_VIEW'),
 ('CLIENT_ADMIN', 'KB_VIEW'),
 ('PROJECT_MANAGER', 'KB_VIEW'),
 ('TEAM_LEADER', 'KB_VIEW'),
 ('L1_SUPPORT', 'KB_VIEW'),
 ('L2_SUPPORT', 'KB_VIEW'),
 ('L3_SUPPORT', 'KB_VIEW'),
 ('MANAGEMENT_VIEWER', 'KB_VIEW'),
 ('GROUP_PROJECT_MANAGER', 'DASHBOARD_VIEW'),
 ('GROUP_PROJECT_MANAGER', 'REPORT_VIEW'),
 ('GROUP_PROJECT_MANAGER', 'TICKET_VIEW'),
 ('GROUP_PROJECT_MANAGER', 'TICKET_CREATE'),
 ('GROUP_PROJECT_MANAGER', 'TICKET_UPDATE'),
 ('GROUP_PROJECT_MANAGER', 'TICKET_ASSIGN'),
 ('GROUP_PROJECT_MANAGER', 'TICKET_ESCALATE'),
 ('GROUP_PROJECT_MANAGER', 'CLIENT_VIEW'),
 ('GROUP_PROJECT_MANAGER', 'PROJECT_VIEW'),
 ('GROUP_PROJECT_MANAGER', 'WORKLOAD_VIEW'),
 ('GROUP_PROJECT_MANAGER', 'SLA_VIEW'),
 ('GROUP_PROJECT_MANAGER', 'KB_VIEW'),
 ('GROUP_SUPPORT_USER', 'DASHBOARD_VIEW'),
 ('GROUP_SUPPORT_USER', 'TICKET_VIEW'),
 ('GROUP_SUPPORT_USER', 'TICKET_CREATE'),
 ('GROUP_SUPPORT_USER', 'TICKET_UPDATE'),
 ('GROUP_SUPPORT_USER', 'TICKET_ESCALATE'),
 ('GROUP_SUPPORT_USER', 'KB_VIEW');

-- Keep the Knowledge Base guide about roles true (a new version; the old one stays in the history).
UPDATE kb_articles SET
  summary = $kb$Each role has a set of permissions. The server checks them on every action, and people only see their own projects.$kb$,
  keywords = $kb$role roles permission permissions access rights rbac matrix who can see what super admin project manager team leader l1 l2 l3 management viewer client admin agent lead no access denied$kb$,
  body = $kb$## The 8 roles
- Super Admin: can do everything. This can never be changed, so the tool can never be locked.
- Client Admin: the clients given to them, their projects, tickets, imports, SLA and reports.
- Project Manager: their projects, tickets (also assign), imports, team workload, SLA and reports.
- Team Leader: their projects, tickets (also assign), team workload, SLA and reports.
- L1 Support: tickets of their projects. Changes and escalates only tickets assigned to them.
- L2 Support and L3 Support: tickets of their projects. Change only tickets assigned to them.
- Management Viewer: sees dashboards, tickets, projects, team, SLA and reports. Changes nothing.

## Permissions
Each role has a list of permissions, for example "Assign and reassign tickets" or "Upload Excel sheets". The Super Admin sees all of them in Security, Permission matrix: one row per permission, one column per role.

## Projects decide which records you see
Permissions say WHAT you may do. Your projects say WHERE. An employee sees only the clients, projects and tickets of the projects they are added to. Super Admin and Management Viewer see all projects.

## Safe on the server
The server checks the permission on every action. Typing a page address or calling the system directly does not get around it. A page your role may not open shows "No access".

## New roles
The Super Admin can create new roles, like Project Lead. A new role starts with the permissions of its type (lead type or agent type), and the Super Admin can change them in the Permission matrix.$kb$,
  version_no = version_no + 1,
  updated_at = now()
WHERE slug = 'roles-who-sees-what';

UPDATE kb_articles SET
  body = $kb$## Steps (Super Admin)
1. Click Security, then Roles.
2. Press Create role.
3. Type the Role name, for example Project Lead.
4. Choose Works like:
   - Lead or manager: sees and leads the projects they are on.
   - Support agent: works tickets of their projects.
5. Add a description if you want and press Add.

## Choose what the role may do
1. Click Security, then Permission matrix.
2. Find the new role's column.
3. Tick or untick the permissions.
4. Press Save changes.

Note: A permission marked "fixed" can be removed from any role, but can only be given to the roles that have it by default.

Tip: Press Reset under a role's name to give it back its default permissions.$kb$,
  version_no = version_no + 1,
  updated_at = now()
WHERE slug = 'create-role';

INSERT INTO kb_article_versions (article_id, version_no, category, title, summary, body, keywords, status)
SELECT id, version_no, category, title, summary, body, keywords, status FROM kb_articles
WHERE slug IN ('roles-who-sees-what', 'create-role')
  AND NOT EXISTS (SELECT 1 FROM kb_article_versions v WHERE v.article_id = kb_articles.id AND v.version_no = kb_articles.version_no);