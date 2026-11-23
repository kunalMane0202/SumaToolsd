-- Run after the backend has started once:
--   psql -h localhost -U connect_projects -d connect_projects -f database/verify.sql
SELECT version();
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT username, role, enabled FROM app_users ORDER BY id;   -- first row = your Super User
SELECT count(*) AS tables FROM information_schema.tables WHERE table_schema = 'public';
