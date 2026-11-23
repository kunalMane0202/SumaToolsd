# Merge Report

## 1. The two projects

| | **ConnectingProjects-updated** (base) | **suma-tracking** |
|---|---|---|
| Frontend | React 18 + Vite 5, Bootstrap, Recharts, lucide | React 19 + Vite 8, plain CSS, Recharts |
| Backend | Spring Boot 3.3.5, Java 17, JWT (stateless), package `com.connectprojects.backend` | Spring Boot 4.1, Java 21, DB-backed sessions, package `com.sumasoft.tracking` |
| Database | PostgreSQL `connect_projects`, Flyway V1–V6, 19 tables | PostgreSQL `suma_tracking`, Flyway V1–V16, different schema |
| Roles | Super User, Admin, Project Lead, Team Member + **per-user permission list** | ~10 fixed roles (Super Admin, Client Admin, L1–L3 …), role-based rules |
| Strengths | Per-user permissions enforced server-side, projects/tasks/tickets/reports, project configuration + SLAs, monthly-log Excel import, scheduled e-mail reports, account-request workflow, password reset, dashboard visualisations | Attendance with breaks, SLA engine (business calendar), automation rules, knowledge base, Excel ticket import, audit log, notification engine, clients page, landing page, security page |
| Also contained | Obsolete Node/Express backend (`connect-projects-backend/`), an old zip snapshot, `.env` with real SMTP credentials, `DashboardService.java.backup` | Git history, build output (`target/`) |

The two backends have **different APIs, authentication models, role models and schemas**. They cannot be combined by copying files; one must win. As instructed, `ConnectingProjects-updated` is the backend, unchanged in architecture.

## 2. Feature comparison

| Module | Base | suma-tracking | Result |
|---|---|---|---|
| Login / auth | JWT, forgot/reset password, account requests | Session login, change password | Base kept |
| Dashboard | Permission-scoped KPIs + charts | KPI dashboard | Base kept |
| Projects | CRUD + config (shifts, priorities, categories, SLA) | CRUD + team assignment | Base kept |
| Tasks | Full CRUD, assignment, board | – (no tasks) | Base kept |
| Tickets | CRUD, assign, resolve, close, history, SLA hours | Richer tickets, import, SLA engine | Base kept |
| Team / users | Super User CRUD, permissions editor | Team page, roles | Base kept |
| Reports | Daily/weekly/monthly, exports, schedules, monthly-log import | Simple reports page | Base kept |
| Notifications | none | Backend notification engine | **Added** (see §3) |
| Profile / settings | Profile + password | Settings page | Base kept |
| Permissions | Per-user, server-enforced | Role-based | Base kept (matches your spec) |
| Attendance, SLA engine, automation rules, knowledge base, ticket Excel import, audit log, integrations, landing page | – | Present | **Not merged** (see §4) |

## 3. What was merged / changed

**Features taken from suma-tracking**
- Notifications module (concept): new `Notifications` page and top-bar badge. Alerts (overdue / due-soon tasks, assigned tickets, SLA breach or due within 4 h, unassigned tickets, resolved tickets) are computed from the live `/api/tasks` and `/api/tickets` responses, so they respect each user's permissions and need **no backend change**. Read state is per-browser (localStorage).
- Persistent light/dark theme preference.

**Frontend improvements**
- Notification page and badge; theme persisted.
- Expired/invalid JWT (HTTP 401) now signs the user out instead of leaving a broken screen.
- API base URL configurable via `VITE_API_BASE_URL` (the old README described this but the code ignored it).
- Frontend moved to `frontend/`, package renamed, `.env.example` and `.gitignore` added.
- Static check: every relative import and named export resolves; no unused source or CSS files.

**Backend changes (configuration only; no Java logic changed)**
- `DATABASE_PASSWORD` default `Admin@123` removed; `JWT_SECRET` default removed → the app now refuses to start without real values.
- Default Super User e-mail (a personal address) removed; supplied via `BOOTSTRAP_SUPERUSER_EMAIL`.
- `spring.config.import` loads `../.env` / `./.env` so a plain `mvn spring-boot:run` works.

**Removed (after checking purpose)**
| Removed | Why |
|---|---|
| `connect-projects-backend/` (Node/Express + `data/db.json`) | README states it is unused; Spring Boot implements the same routes (auth, dashboard, project, report, task, ticket, user). Contained demo users with password hashes. |
| `OpsVista-project.zip` | Older snapshot of the same project. |
| `backend/.../DashboardService.java.backup` | Backup copy of a live file. |
| `.github/modernize/**`, `backend/.github/**` | Tool-generated Java-upgrade hook scripts, not part of the app. |
| `.env` | Contained real SMTP credentials. **Rotate that Gmail app password.** |
| `target/`, `.git/`, build output | Generated. |
| Duplicate `startup` VS Code tasks | Kept a single `tasks.json`. |

## 4. Not merged, and why (needs your decision)

Attendance, SLA business-calendar engine, automation rules, knowledge base, Excel ticket import, audit log, integrations and the landing page exist only in suma-tracking and depend on **its** backend (Spring Boot 4 / Java 21, different tables and role model). Bringing them across means adding new controllers, entities and Flyway migrations to the base backend and mapping them onto the per-user permission catalogue. That is a large backend change that goes beyond the "minimum necessary" rule, and I could not compile or run Java in this environment to test it, so I did not guess. Their complete source is preserved in `reference/suma-tracking/` (not part of the build) so they can be ported module by module. Tell me which ones you want first.

## 5. Final structure

See the tree in `README.md`.
