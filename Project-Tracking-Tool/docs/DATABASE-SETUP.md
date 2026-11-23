# Database Setup Guide (PostgreSQL)

## What the application uses

| Item | Value |
|---|---|
| Database | PostgreSQL (16 used by docker-compose; 14+ works) |
| Database name | `connect_projects` (default) |
| User | `connect_projects` (default) |
| Password | **you choose**; stored only in `.env` |
| Connection URL | `jdbc:postgresql://localhost:5432/connect_projects` |
| Schema management | Flyway migrations V1–V6, `spring.jpa.hibernate.ddl-auto=validate` |

Tables (19): `app_users`, `app_user_permissions`, `account_requests`, `clients`, `projects`, `project_team_leaders`, `tasks`, `tickets`, `ticket_history`, `ticket_categories`, `ticket_subcategories`, `ticket_priorities`, `support_shifts`, `slas`, `report_schedules`, `report_schedule_recipients`, `report_email_deliveries`, `monthly_log_imports`, `monthly_log_rows` (+ `flyway_schema_history`).

## 1. Install PostgreSQL

- **Windows:** download the installer from https://www.postgresql.org/download/windows/ . Keep port `5432`, set a password for the `postgres` administrator and remember it. Tick *Command Line Tools* so `psql` is available.
- **macOS:** `brew install postgresql@16 && brew services start postgresql@16`
- **Ubuntu/Debian:** `sudo apt install postgresql postgresql-client && sudo systemctl start postgresql`
- **No install at all:** use Docker (`docker compose up --build`) and skip to section 5.

## 2. Create the database and user

From the project root (Windows: use the *SQL Shell (psql)* or a terminal where `psql` is on PATH):

```bash
psql -U postgres -v db_password=YourStrongDbPassword -f database/create-database.sql
```

(On Linux you may need `sudo -u postgres psql ...`.) This creates the role `connect_projects` and the database `connect_projects`. The password is passed on the command line and is not stored in the script.

Prefer other names? Edit the two names in the script **and** `DATABASE_USERNAME` / `DATABASE_URL` in `.env`.

## 3. Configure the backend

```bash
cp .env.example .env
```

Set in `.env`:

```
DATABASE_URL=jdbc:postgresql://localhost:5432/connect_projects
DATABASE_USERNAME=connect_projects
DATABASE_PASSWORD=YourStrongDbPassword
```

The backend loads `.env` automatically (`spring.config.import`). Alternatively export the same names as real environment variables.

## 4. How tables are created

You do **not** run any table scripts. On start, Flyway applies `V1…V6` in order and records them in `flyway_schema_history`. Hibernate only *validates* the result; it never changes the schema. New schema changes must be added as a new `V7__…sql` file, never by editing an old one.

## 5. Initial Super User (securely)

No password is stored in any file in the repository. On first start, if no Super User exists, the backend creates one from:

```
BOOTSTRAP_SUPERUSER_USERNAME   3-30 chars: letters, digits . _ -
BOOTSTRAP_SUPERUSER_PASSWORD   12-72 characters (stored BCrypt-hashed)
BOOTSTRAP_SUPERUSER_NAME
BOOTSTRAP_SUPERUSER_EMAIL
```

If these are missing on a fresh database the backend refuses to start and says which are missing. After the first login change the password in **Settings**; later changes to the `.env` values do nothing because the user already exists.

## 6. Verify the connection

```bash
pg_isready -h localhost -p 5432
psql -h localhost -U connect_projects -d connect_projects -f database/verify.sql
```

Expect six rows in `flyway_schema_history` (all `success = t`) and one `SUPER_USER` row in `app_users`. With the API running, `http://localhost:8080/api/auth/login` answers (a GET returns 401/405, which proves the server is up).

## 7. Troubleshooting

| Error | Cause / fix |
|---|---|
| `Connection refused` | PostgreSQL is not running or uses another port. Start the service; check `DATABASE_URL`. |
| `password authentication failed for user "connect_projects"` | `.env` password differs from the one given to `create-database.sql`. Reset: `psql -U postgres -c "ALTER ROLE connect_projects PASSWORD 'new'"`. |
| `database "connect_projects" does not exist` | Run section 2. |
| `Could not resolve placeholder 'DATABASE_PASSWORD'` | `.env` missing / wrong folder; it belongs in the project root. |
| `Schema-validation: missing table` | A migration did not run; check `flyway_schema_history`, or drop and recreate the **empty** dev database. |
| `Flyway ... checksum mismatch` | An applied migration file was edited. Restore the original file. |
| `No Super User exists. Set BOOTSTRAP_SUPERUSER_…` | Fill the four `BOOTSTRAP_SUPERUSER_*` values. |
| Port 5432 already in use (Docker) | Stop the local PostgreSQL service or change the left side of `5432:5432` in `docker-compose.yml`. |
| `role "connect_projects" already exists` | The script was run before; use `ALTER ROLE` above instead. |

## Reset a development database (destroys data)

```bash
psql -U postgres -c "DROP DATABASE connect_projects"
psql -U postgres -c "CREATE DATABASE connect_projects OWNER connect_projects"
```
