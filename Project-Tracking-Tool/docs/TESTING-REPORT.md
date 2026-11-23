# Testing Report

The merge was prepared in a sandbox that has **no network access for npm or Maven** and no Java dependency cache. Because of that, neither application could be installed, compiled or started there. Nothing below is claimed beyond what was actually run.

## Passed (actually executed)

| Check | Result |
|---|---|
| `database/create-database.sql` run on PostgreSQL 16 (role + database creation, password passed as variable) | Passed |
| Flyway migrations V1–V6 applied in order on an empty PostgreSQL 16 database with `ON_ERROR_STOP` | Passed, 19 tables created |
| Login as the new database user with the chosen password | Passed |
| All frontend `.js/.jsx` files parse (acorn + JSX) | Passed |
| Every relative import resolves and every named import has a matching export; no unused source/CSS files | Passed |
| Secret scan of the delivered tree (passwords, SMTP credentials, personal e-mail, bcrypt hashes) | None found |
| Backend permission enforcement reviewed in code: all project/task/ticket/report/dashboard/user services call `CurrentUserService.require(...)`; account requests and user management are Super User only; `/api/**` otherwise requires a valid JWT | Reviewed (not executed) |

## Not run – please run these on your machine

| Area | Command / action | Why it is not yet verified |
|---|---|---|
| Frontend install & production build | `cd frontend && npm install && npm run build` | npm registry unreachable |
| Backend build + unit tests | `cd backend && mvn package` | Maven repository unreachable |
| Backend start-up, Flyway on boot, Hibernate `validate`, Super User bootstrap | `mvn spring-boot:run` | needs the build above |
| `.env` loading via `spring.config.import` | start the backend | standard Spring Boot 3 feature, but untested here |
| Login/logout, user creation, permission grant/revoke, project/task/ticket CRUD, reports, notifications page, 401 auto sign-out | manual walk-through in the browser | needs running app |
| Restricted user gets 403 on forbidden API calls | log in as a user with few permissions and try the actions | needs running app |
| Responsive layout (desktop/tablet/mobile) and scrolling | resize the browser | not rendered here |
| Docker | `docker compose up --build` | Docker unavailable |

## Known limitations

- The Notifications page is derived from tasks/tickets in the browser; read/unread state is stored per browser, not in the database.
- UI styling is the base project's. Its stylesheets (`Dashboard.css`, `Dashboard-premium-overrides.css`, …) were **not** consolidated or redesigned because that cannot be done safely without viewing the result.
- Modules listed in `docs/MERGE-REPORT.md` §4 are not merged.
- Remove the real SMTP password from your original `.env` history: it was present in the uploaded zip.
