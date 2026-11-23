# Project Tracking Tool

A project, task, ticket and reporting platform with role/permission management.

- **Frontend:** React 18 + Vite (`frontend/`)
- **Backend:** Spring Boot 3.3 / Java 17, JWT authentication (`backend/`)
- **Database:** PostgreSQL 16, schema managed by Flyway (`backend/src/main/resources/db/migration`)

> Read `docs/TESTING-REPORT.md` first: it states exactly what has and has not been verified.

## Modules

Login & password reset · Dashboard · Projects · Project Management (clients, leads, shifts, priorities, categories, SLAs) · Tasks · Tickets (assign / resolve / close / history) · Reports & Analytics (daily/weekly/monthly, monthly-log Excel import, scheduled e-mail reports) · Project Analytics · Notifications · Profile & Settings · Account requests · User management · Per-user permissions.

## Permission model

- **Super User**: always has every permission; creates, edits and deletes users; grants and revokes individual permissions.
- **Admin / Project Lead / Team Member**: can only do what the Super User has individually ticked under *User permissions*. The role name alone grants nothing. Every API call is checked on the server (`CurrentUserService.require`), and the menu is hidden in the UI to match.

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| Java JDK | 17+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 18+ (20 or 22 recommended) | `node -v` |
| PostgreSQL | 14+ (16 recommended) **or** Docker Desktop | `psql --version` |

## Quick start

### 1. Configure secrets

```bash
cp .env.example .env          # Windows PowerShell: Copy-Item .env.example .env
```

Open `.env` and replace **every** `change-me…` value (database password, Super User password, `JWT_SECRET`). Never commit `.env`.

### 2. Database (pick one)

**A. Docker (easiest)** – from the project root:

```bash
docker compose up --build        # starts PostgreSQL *and* the backend on :8080
```

Then skip step 3 and go to step 4.

**B. Local PostgreSQL** – see `docs/DATABASE-SETUP.md` for the full beginner guide. In short:

```bash
psql -U postgres -v db_password=YourDbPassword -f database/create-database.sql
```

Use the same password as `DATABASE_PASSWORD` in `.env`.

### 3. Start the backend (only for option B)

```bash
cd backend
mvn spring-boot:run
```

The backend reads `../.env` automatically. On first start Flyway creates all tables and the Super User from `BOOTSTRAP_SUPERUSER_*` is created. Wait for `Started ConnectProjectsApplication`.

### 4. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

### 5. Open the app

| What | URL |
|---|---|
| Application | http://localhost:5173 |
| API | http://localhost:8080/api |

Sign in with `BOOTSTRAP_SUPERUSER_USERNAME` / `BOOTSTRAP_SUPERUSER_PASSWORD` from your `.env`.

## Typical first steps

1. Sign in as Super User → **User Management** → create users.
2. **User permissions** → tick exactly what each user may do → *Save permissions*.
3. **Project Management** → add clients, projects, leads, SLAs → then use Tasks and Tickets.

## Environment variables

See `.env.example` (every variable is commented). Frontend: `frontend/.env.example` (`VITE_API_BASE_URL`, optional).

## Production build

```bash
cd frontend && npm run build     # output in frontend/dist (SPA routing config: netlify.toml)
cd backend  && mvn package       # runs unit tests, builds target/account-api-1.0.0.jar
```

Set `VITE_API_BASE_URL` at frontend build time and `CORS_ALLOWED_ORIGINS` on the API for a split deployment.

## Project layout

```
Project-Tracking-Tool/
├── README.md  .env.example  docker-compose.yml  .gitignore
├── backend/                Spring Boot API (pom.xml, Dockerfile, src/)
├── frontend/               React app (package.json, vite.config.js, src/)
├── database/               create-database.sql, verify.sql
├── docs/                   DATABASE-SETUP.md, MERGE-REPORT.md, TESTING-REPORT.md
└── reference/suma-tracking Source of the second project's modules that were not merged
```

## Troubleshooting

- `Could not resolve placeholder 'DATABASE_PASSWORD'` → `.env` is missing or not in the project root.
- `password authentication failed` → `.env` password differs from the one used in `create-database.sql`.
- Login works but pages say "Sign in is required" → token expired (8 h); sign in again.
- Browser CORS error → add your frontend origin to `CORS_ALLOWED_ORIGINS`.
- More database help: `docs/DATABASE-SETUP.md`.
