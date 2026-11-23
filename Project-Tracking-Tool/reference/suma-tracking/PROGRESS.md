# Suma Tracking: Progress and Decisions Log

## Current status (4 November 2026)
- Phase 1 software installed: DONE
- Phase 2 project setup: DONE
- Phase 3 database and backend foundation: DONE (clients, projects, roles, users, teams)
- Phase 4 login and permissions: DONE (d0f85fd, 4845ca8)
- Phase 7 audit history: DONE (bc292d6, 648061b)
- NEXT: Phase 6 ticket management (common ticket model, statuses, history, assignment)

## How to start the backend
1. cd C:\Dev\SumaTracking\backend
2. $env:SUMA_DB_PASSWORD = [System.Net.NetworkCredential]::new("", (Read-Host "suma_app password" -AsSecureString)).Password
3. .\mvnw.cmd spring-boot:run
Every API needs a login: POST /api/auth/login with email + password, then send "Authorization: Bearer <token>".

## Roles (APPROVED 4 Nov 2026: architecture diagram + FRD)
| Diagram user type | Roles |
|---|---|
| Super Admin | SUPER_ADMIN, MANAGEMENT_VIEWER (read-only) |
| Project Manager | CLIENT_ADMIN, PROJECT_MANAGER, TEAM_LEADER |
| Support User | L3_SUPPORT, L2_SUPPORT, L1_SUPPORT |

## Permissions (server-side, tested 4 Nov 2026)
- Super Admin: everything. Management Viewer: sees everything, changes nothing.
- Others see only projects they are on (and those clients). L1-L3: no client or employee lists.
- Client Admin: edit their clients; create / edit / activate / deactivate / close their clients' projects.
- A project's own Project Manager: edit that project and its team.
- Create / edit employees, reset passwords, revoke access, read audit: Super Admin only.
- Hidden records answer 404; visible-but-not-allowed answers 403.
- Simplification for now: a Client Admin "owns" a client by being on one of its projects. Linking via the Account Manager field comes later.

## Login security (tested)
- BCrypt (strength 12) password hashes; no plain passwords anywhere.
- Random 43-character token; only its SHA-256 fingerprint is stored.
- Session ends after 8 hours, 60 minutes idle, or logout. Admin can revoke all sessions.
- 5 wrong passwords lock the account for 15 minutes. Same error message for every failed login.
- Temporary passwords (first login, admin reset) must be changed before anything else.
- Password rules: 10+ characters, a letter and a digit, max 72 bytes, not the email.
- First Super Admin password: set once from terminal variables SUMA_BOOTSTRAP_ADMIN_EMAIL / SUMA_BOOTSTRAP_ADMIN_PASSWORD (works only when that admin has no password).

## Audit (tested)
- Schema audit, table audit.audit_log: who, what, when, client, project, old value, new value, source, IP.
- Add-only: database triggers block UPDATE / DELETE / TRUNCATE (also on login_history).
- Recorded: client, project, employee, team changes; password change / reset; access revoke.
- Recorded in the same transaction as the change (refused changes leave no record).
- Read: GET /api/audit (Super Admin only). No API exists to change or delete history.
- Honest note: a database administrator could still remove triggers, so backups and restricted DB access remain necessary.

## Database migrations (never edit a file after it has run)
| Version | What it does |
|---|---|
| V1 | clients, projects |
| V2 | Suma Tool, Hero, Codility (ZENDESK), SouthCo (KEYPANION) |
| V3 | roles (8), users, project_assignments, clients.account_manager_id |
| V4 | password / lock-out fields, user_sessions, login_history |
| V5 | audit schema, audit_log, add-only triggers |

## Test data (fake, example.com; keep for testing)
- Admin: TEST-001 test.admin@example.com (Super Admin, real password known only to the owner)
- TEST-002 Team Leader (Codility PM), TEST-003 L1 (INACTIVE), TEST-004 L2 (Hero L2, Codility L2), TEST-005 Management Viewer
- Client CL-0005 (INACTIVE), project PR-0005 (CLOSED)
- Before real use: create real employees and a real Super Admin, then deactivate the TEST accounts.

## Decisions
- Passwords never in files or chat; database password comes from SUMA_DB_PASSWORD.
- Only Flyway changes tables (ddl-auto=validate). Times stored in UTC; project time zone default Asia/Kolkata.
- App connects as suma_app, never as postgres. Nothing is ever deleted (deactivate / close / active=false).
- Client names = project names for now (user decision, 4 Nov 2026).
- Audit in same PostgreSQL, separate protected schema (approved 4 Nov 2026).
- External systems read-only first (approved 4 Nov 2026).