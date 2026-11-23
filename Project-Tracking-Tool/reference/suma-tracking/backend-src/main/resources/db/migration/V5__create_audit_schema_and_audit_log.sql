-- =====================================================================
-- V5: Audit history (approved 4 Nov 2026: same PostgreSQL, separate
-- protected schema, add-only).
--  - Schema "audit" with table audit.audit_log.
--  - Rows can only be ADDED. UPDATE, DELETE and TRUNCATE are blocked by
--    database triggers, for every database user that goes through them.
--  - login_history (V4) gets the same add-only protection.
-- HONEST NOTE: this stops the application and normal users from changing
-- history. A database administrator can still remove triggers, so backups
-- and restricted database access remain necessary. It is not "tamper-proof".
-- Never edit this file after it has run.
-- =====================================================================

CREATE SCHEMA audit;

CREATE TABLE audit.audit_log (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor_user_id     BIGINT       REFERENCES public.users (id),  -- who did it (empty for the system)
    actor_email       VARCHAR(254),                              -- copied, so it stays readable later
    action            VARCHAR(60)  NOT NULL,                     -- e.g. CLIENT_CREATED, PROJECT_CLOSED
    entity_type       VARCHAR(40)  NOT NULL,                     -- CLIENT, PROJECT, USER, PROJECT_ASSIGNMENT, ...
    entity_id         BIGINT,
    client_id         BIGINT,                                    -- affected client (if any)
    project_id        BIGINT,                                    -- affected project (if any)
    source            VARCHAR(20)  NOT NULL DEFAULT 'USER',      -- USER, IMPORT, INTEGRATION, SYSTEM
    source_reference  VARCHAR(200),                              -- e.g. import batch number
    old_value         JSONB,                                     -- before the change (empty for "created")
    new_value         JSONB,                                     -- after the change
    ip_address        VARCHAR(45),

    CONSTRAINT chk_audit_action CHECK (action ~ '^[A-Z][A-Z0-9_]{1,59}$'),
    CONSTRAINT chk_audit_entity_type CHECK (entity_type ~ '^[A-Z][A-Z0-9_]{1,39}$'),
    CONSTRAINT chk_audit_source CHECK (source IN ('USER', 'IMPORT', 'INTEGRATION', 'SYSTEM'))
);

CREATE INDEX ix_audit_log_entity ON audit.audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_log_occurred_at ON audit.audit_log (occurred_at);
CREATE INDEX ix_audit_log_actor ON audit.audit_log (actor_user_id);
CREATE INDEX ix_audit_log_client ON audit.audit_log (client_id);
CREATE INDEX ix_audit_log_project ON audit.audit_log (project_id);


-- ---------- Add-only protection ----------
CREATE FUNCTION audit.block_changes() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% is not allowed on %.%: history is add-only', TG_OP, TG_TABLE_SCHEMA, TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER trg_audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit.audit_log
    FOR EACH ROW EXECUTE FUNCTION audit.block_changes();

CREATE TRIGGER trg_audit_log_no_truncate
    BEFORE TRUNCATE ON audit.audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit.block_changes();

CREATE TRIGGER trg_login_history_no_update_delete
    BEFORE UPDATE OR DELETE ON public.login_history
    FOR EACH ROW EXECUTE FUNCTION audit.block_changes();

CREATE TRIGGER trg_login_history_no_truncate
    BEFORE TRUNCATE ON public.login_history
    FOR EACH STATEMENT EXECUTE FUNCTION audit.block_changes();