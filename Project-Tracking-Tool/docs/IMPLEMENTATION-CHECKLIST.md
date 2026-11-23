# Enterprise Implementation Checklist

This checklist is based on the inspected React/Vite frontend, Spring Boot backend, PostgreSQL/Flyway schema, existing tests/documentation, and the preserved `reference/suma-tracking` source.

## Phase 1 baseline

### Architecture findings

- The frontend is React 18 with Vite, plain JavaScript/JSX, Recharts, and Lucide. It is served locally by Vite and is not currently included in Docker Compose.
- The backend is Spring Boot 3.3.5 on Java 17. Maven provides `pom.xml`, Spring Security, JPA, Flyway, JWT, mail, POI, PostgreSQL, and tests.
- PostgreSQL is the database. Flyway migrations V1-V6 currently create 19 tables, but the schema covers only authentication, basic projects/tasks/tickets, project configuration, reports, and monthly log imports.
- Authentication is JWT-based. Authorization is enforced with Super User bypasses plus per-user permissions.
- The role enum currently has `SUPER_USER`, `ADMIN`, `PROJECT_LEAD`, and `TEAM_MEMBER`; it does not include Manager, L1, L2, or L3.
- Existing Docker configuration starts PostgreSQL and the backend. The backend builds correctly and can connect to PostgreSQL.
- No backend test classes are present. The existing testing report documents static checks and unexecuted runtime checks, while this environment has now verified Docker, login, and frontend HTTP startup.
- The preserved reference contains useful source patterns for clients, roles, teams, SLA calendars, automation, knowledge, notifications, audit, attendance, imports, and settings, but it uses a different backend architecture and must be ported rather than copied wholesale.

## Module 1: Dashboard Management

**Existing frontend**
- `Dashboard.jsx`, `DashboardVisualizations.jsx`
- Permission-filtered navigation and database-backed dashboard cards/charts

**Existing backend**
- `GET /api/dashboard`
- `DashboardService` combines visible projects, tasks, ticket activity, and workload

**Existing database**
- `projects`, `tasks`, `tickets`, `ticket_history`, `app_users`

**Missing functionality**
- Active client, employee, received/resolved/pending/overdue/SLA-breach totals
- Team performance, daily/weekly/monthly summaries
- Date/client/project/team/priority filters
- Ticket priority/project/SLA charts
- Upcoming SLA deadlines and overdue alerts

**Required modifications**
- Add dashboard query/DTO aggregations and permission-scoped repository queries
- Add dashboard filter DTO and backend validation
- Extend dashboard frontend cards, charts, and filter controls
- Materialize or calculate KPIs from tickets, projects, clients, users, teams, and SLA tables

**Dependencies**
- Modules 2, 3, 4, 5, 6

**Testing**
- Repository aggregation tests, service permission tests, API response tests, date-range and filter tests, frontend chart/filter tests

## Module 2: Client Management

**Existing frontend**
- Client creation and project association are partly implemented in `ProjectManagementPage.jsx`

**Existing backend**
- `GET/POST /api/project-management/clients`
- `ClientEntity`, `ClientRepository`, `ProjectManagementService`

**Existing database**
- `clients(id, name, industry, active)`
- Projects reference `clients.id`

**Missing functionality**
- Client code, contact person, email, phone, company details, address
- Account manager assignment
- Client detail page/history/reports
- Edit and activate/deactivate APIs
- Duplicate prevention beyond client name uniqueness

**Required modifications**
- Extend client entity, migration, DTOs, service, and repository
- Add account-manager relationship and project/ticket report views
- Add edit/details/status frontend screens and confirmation states

**Dependencies**
- Module 3 for project and ticket association

**Testing**
- Unique-name/email/code tests, CRUD API tests, activation tests, permission tests, client-wise report tests

## Module 3: Project Management

**Existing frontend**
- `ProjectManagementPage.jsx`, legacy project CRUD in `WorkspacePage.jsx`
- Project status, client, project manager, team leaders, shifts, priorities, categories/subcategories, and SLAs

**Existing backend**
- `/api/projects`
- `/api/project-management`
- `/api/projects/{id}/shifts|priorities|categories|slas`

**Existing database**
- `projects`, `project_team_leaders`, `clients`, `support_shifts`, `ticket_priorities`, `ticket_categories`, `ticket_subcategories`, `slas`

**Missing functionality**
- Project code and normalized support-hour configuration
- Assigned employee allocation
- Complete edit/delete path and data validation coverage
- Project-specific team and shift relationships
- Business-calendar holiday linkage

**Required modifications**
- Add project code, support-hours, team assignments, and project contacts/configuration fields
- Add employee-to-project repository relationship
- Consolidate the duplicated project UI rather than maintaining disconnected project screens
- Complete delete/restore behavior and referential safety

**Dependencies**
- Modules 2, 4, 5, 6

**Testing**
- Project CRUD/status tests, client/FK tests, project-lead access tests, team assignment tests, configuration cascade tests

## Module 4: User and Team Management

**Existing frontend**
- Account request approval/rejection, user CRUD, and permission editor in `Dashboard.jsx`
- Profile/password settings in `WorkspacePage.jsx`

**Existing backend**
- `/api/users`, `/api/users/assignees`, `/api/account-requests`
- `/api/auth/me`, profile and password endpoints
- `UserService`, `AuthService`, `CurrentUserService`, `PermissionCatalog`

**Existing database**
- `app_users`, `app_user_permissions`, `account_requests`

**Missing functionality**
- Employee ID, manager hierarchy, project/team allocation, shifts, weekly offs, workload/productivity, account enable/disable, and login history
- Missing Manager, L1, L2, and L3 roles
- Dedicated team API and team UI

**Required modifications**
- Expand `UserRole` and migration constraints
- Add teams, team members, project assignments, shifts, weekly offs, and employee profile fields
- Add employee activation/deactivation rather than delete-only behavior
- Expand role/permission seed values and default matrix

**Dependencies**
- Modules 3, 5, 6

**Testing**
- Role constraint tests, permission matrix tests, team assignment tests, activation tests, workload queries, password-policy tests

## Module 5: Ticket Management

**Existing frontend**
- `TicketsBoard.jsx`
- Create, assign, status update, resolve, close, delete, filtering, ticket ageing, history display

**Existing backend**
- `/api/tickets` CRUD/assign/resolve/close
- `TicketEntity`, `TicketHistoryEntity`, `TicketService`

**Existing database**
- `tickets`, `ticket_history`

**Missing functionality**
- Ticket-to-client and ticket-to-project relationships
- Subcategory, team, escalation level, source platform, external ID
- Configurable statuses/priorities
- Comments, internal notes, attachments
- Reassignment history and unauthorized-scope protections
- Excel import and integration ingestion

**Required modifications**
- Extend ticket entity/migration and response DTO
- Add comments, notes, attachments, status/priority configuration, project/client/team relationships
- Add pagination/filter/sort and scope rules
- Integrate first-response and resolution timestamps with ticket actions

**Dependencies**
- Modules 2, 3, 4, 6, 7

**Testing**
- CRUD/status transition tests, assignment scope tests, history tests, attachment validation tests, pagination/filter tests

## Module 6: SLA Management

**Existing frontend**
- SLA configuration in project drawer
- Frontend SLA remaining/breach calculations in ticket and report screens

**Existing backend**
- Priority/project SLA entity and project configuration APIs
- Ticket response/resolution hours and due timestamps

**Existing database**
- `slas`, `ticket_priorities`, `support_shifts`

**Missing functionality**
- SLA by priority with project relationship is partial
- Business-calendar and holiday-aware calculation
- SLA tracking, compliance, breach alerts, escalation, response/resolution status
- Automatic monitoring

**Required modifications**
- Add calendars, holidays, SLA targets/tracking, alert history, and timezone/business-hours calculation service
- Replace simple `Instant.plusSeconds` due-date calculation
- Add scheduled monitoring and escalation hooks
- Expose SLA compliance and remaining time from backend

**Dependencies**
- Modules 3, 4, 5, 10

**Testing**
- Weekend/holiday/timezone tests, partial-hours tests, breach tests, first-response and resolution tests, scheduled-monitor tests

## Module 7: Integration Management

**Existing frontend**
- None

**Existing backend**
- None

**Existing database**
- No integration or integration-log tables

**Missing functionality**
- Salesforce/Jira/custom REST integrations
- Credentials, authentication, sync frequency, field mappings, status, retries, logs
- Secure adapter architecture

**Required modifications**
- Add integration, field mapping, log, and credential-reference entities/migrations
- Create adapters with encrypted credential configuration and no secret exposure
- Add sync jobs, retry status, and manual sync endpoints
- Add integration UI and operational status

**Dependencies**
- Modules 5, 13

**Testing**
- Mock-adapter communication tests, credential secrecy tests, mapping tests, retry/failure tests, disabled-integration tests

## Module 8: Workflow and Automation Management

**Existing frontend**
- No dedicated workflow UI

**Existing backend**
- No workflow-rule execution

**Existing database**
- No workflow tables

**Missing functionality**
- Trigger/condition/action rule builder
- Ticket assignment and categorization rules
- Pending reminders
- Approval workflows and execution logs

**Required modifications**
- Add rule, execution, and action-log entities/migrations
- Implement typed condition/action engine and rule priority
- Add scheduler/event listeners for ticket lifecycle
- Add rule management UI and execution history

**Dependencies**
- Modules 5, 6, 10

**Testing**
- Rule condition/action tests, priority/order tests, scheduler tests, failure isolation tests, audit tests

## Module 9: Reports and Analytics

**Existing frontend**
- `ReportsPage.jsx`, `ProjectAnalyticsPage.jsx`
- Daily/weekly/monthly ticket views, Excel/CSV exports, pending ageing, scheduled report UI, monthly-log import

**Existing backend**
- `/api/reports/tickets|schedules|deliveries`
- Monthly-log import APIs
- Excel export/report scheduling services

**Existing database**
- `report_schedules`, `report_schedule_recipients`, `report_email_deliveries`, `monthly_log_imports`, `monthly_log_rows`

**Missing functionality**
- Backend analytics/report aggregation endpoints
- PDF export
- Client/project/team/employee/status/priority filters
- Ticket ageing, first-response/resolution, SLA, and productivity reports
- Permission-scoped server-side filtering

**Required modifications**
- Add report query service and DTOs
- Use database aggregation/pagination rather than frontend-only transformations
- Add server-side CSV/XLSX/PDF export
- Complete schedule delivery implementation and delivery logs

**Dependencies**
- Modules 1, 2, 3, 4, 5, 6

**Testing**
- Date-range/filter tests, export content tests, schedule tests, recipient tests, permission tests

## Module 10: Notification and Escalation Management

**Existing frontend**
- Derived task/ticket notifications and unread state in `NotificationsPage.jsx`

**Existing backend**
- No persistent notification/escalation APIs
- SMTP supports password-reset and scheduled reports

**Existing database**
- No notification or escalation tables

**Missing functionality**
- Persistent assignment/reassignment/SLA/critical/pending/escalation notifications
- Delivery status and history
- Configurable escalation levels and recipients

**Required modifications**
- Add notification, recipient, delivery, and escalation entities/migrations
- Add notification service and event/listener/scheduler integration
- Move read/unread state from browser local storage to the backend
- Add escalation configuration and SMTP/template rendering

**Dependencies**
- Modules 5, 6, 8

**Testing**
- Assignment/reassignment tests, delivery status tests, escalation chain tests, email failure tests, read/unread tests

## Module 11: Knowledge Base and SOP Management

**Existing frontend**
- None

**Existing backend**
- None

**Existing database**
- No knowledge tables

**Missing functionality**
- Articles/SOPs/FAQs, versions, categories, projects, visibility, attachments, search, usage
- Role-based access and archived status

**Required modifications**
- Add article, version, category, attachment, feedback/usage entities/migrations
- Add search/filter/pagination APIs and secure upload handling
- Add knowledge frontend, project/category filters, and archive/delete flows

**Dependencies**
- Modules 2, 3, 4, 12

**Testing**
- CRUD/archive tests, visibility tests, upload validation tests, search tests, usage tracking tests

## Module 12: Security and Audit Management

**Existing frontend**
- Basic permissions and profile UI
- No audit/login-history or security management page

**Existing backend**
- JWT, BCrypt, password reset, permission enforcement
- No audit trail or failed-login monitoring

**Existing database**
- `app_users`, `app_user_permissions`, account requests
- No login, audit, or security configuration tables

**Missing functionality**
- Login/logout/failed-login history
- Activity/audit records
- Unauthorized access attempt monitoring
- Password/authentication policy enforcement
- Read-only audit UI

**Required modifications**
- Add login history and immutable audit-log entities/migrations
- Add login/logout/failed-auth event capture and sensitive-action listeners
- Enforce password policy and account lockout policy
- Add read-only security/audit frontend and permission checks

**Dependencies**
- All modules

**Testing**
- JWT/401/403 tests, failed-login tests, audit immutability tests, password-policy tests, role/permission tests

## Module 13: System Configuration

**Existing frontend**
- Settings currently contain profile/password only
- Project-level statuses and configurations are partly configurable

**Existing backend**
- Configuration is partly hardcoded in services and permissions catalog
- No centralized settings API

**Existing database**
- Project and ticket configuration tables
- No system settings, custom fields, email templates, widgets, calendar, or retention tables

**Missing functionality**
- Ticket statuses/priorities, custom fields
- Business calendars and holidays
- Email templates
- Dashboard widgets and retention policies

**Required modifications**
- Add settings and data-retention entities/migrations
- Replace hardcoded statuses/priorities/permission codes where configuration is required
- Add validation and Super User/Admin settings APIs
- Add centralized settings frontend

**Dependencies**
- All modules

**Testing**
- Settings validation tests, permission tests, runtime configuration reflection tests, retention-policy tests

## Implementation sequence

1. Upgrade/align foundation: Java 21, Docker frontend, test infrastructure, role expansion, migrations.
2. Complete Module 4 and Module 2.
3. Complete Module 3 with normalized project/team/employee relationships.
4. Complete Modules 5 and 6.
5. Complete Module 1 and Module 9 against real database aggregations.
6. Complete Modules 10 and 8.
7. Complete Modules 7, 11, 12, and 13.
8. Add cross-module tests, UI refinement, and full Docker/README validation.

## Phase 1 completion status

Phase 1 has identified the architecture, working modules, missing modules, dependencies, and test requirements. No application feature implementation has started yet, preserving existing behavior.
