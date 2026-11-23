-- One-time local PostgreSQL setup (only needed when NOT using docker-compose).
-- Run as the PostgreSQL administrator, passing your chosen password on the command line:
--
--   psql -U postgres -v db_password=YourStrongPasswordHere -f database/create-database.sql
--
-- The password is supplied at run time so it is never stored in this file.
-- Tables are NOT created here: the backend creates and upgrades them automatically
-- through the Flyway migrations in backend/src/main/resources/db/migration.

CREATE ROLE connect_projects LOGIN PASSWORD :'db_password';
CREATE DATABASE connect_projects OWNER connect_projects ENCODING 'UTF8';
