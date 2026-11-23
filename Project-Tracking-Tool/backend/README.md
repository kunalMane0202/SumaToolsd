# Backend (Spring Boot 3.3 / Java 17)

Run from this folder with `mvn spring-boot:run` after creating `../.env` from `../.env.example`.
Full instructions: see the root `README.md` and `docs/DATABASE-SETUP.md`.

API areas
- `/api/auth/*` login, profile, password change, forgot/reset password
- `/api/account-requests` public requests; Super User approve/reject
- `/api/users` Super User user management and permissions
- `/api/projects`, `/api/tasks`, `/api/tickets` permission-checked work management
- `/api/project-management/*`, `/api/projects/{id}/shifts|priorities|categories|slas` project setup
- `/api/dashboard` permission-scoped summaries
- `/api/reports/*`, `/api/projects/{id}/monthly-logs` reports, schedules, deliveries, monthly-log import
