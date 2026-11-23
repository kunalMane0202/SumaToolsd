-- =====================================================================
-- V8: Excel / CSV import log (FRD 8: import validation, error reports,
--     duplicate detection; audit of imports)
--  - import_batches     : one row per uploaded file (check-only or real import)
--  - import_row_issues  : every problem found, row by row (ERROR = row not
--                         imported, WARNING = imported but needs a look)
--  - tickets.import_batch_id now points to the batch that last wrote the ticket
-- Batches can be updated (totals at the end) but never deleted.
-- Row issues are add-only.
-- Never edit this file after it has run.
-- =====================================================================

CREATE TABLE import_batches (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id           BIGINT       NOT NULL REFERENCES projects (id),
    mode                 VARCHAR(10)  NOT NULL,             -- DRY_RUN (check only) or IMPORT
    status               VARCHAR(12)  NOT NULL DEFAULT 'RUNNING',
    source_system        VARCHAR(30)  NOT NULL,             -- system the tickets come from, e.g. ZENDESK
    file_name            VARCHAR(255) NOT NULL,
    file_sha256          CHAR(64)     NOT NULL,             -- fingerprint of the file (same file = same value)
    file_size_bytes      BIGINT       NOT NULL,
    sheet_name           VARCHAR(100),
    uploaded_by_user_id  BIGINT       NOT NULL REFERENCES users (id),
    started_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at          TIMESTAMPTZ,
    total_rows           INTEGER      NOT NULL DEFAULT 0,
    created_count        INTEGER      NOT NULL DEFAULT 0,   -- new tickets
    updated_count        INTEGER      NOT NULL DEFAULT 0,   -- existing tickets changed
    unchanged_count      INTEGER      NOT NULL DEFAULT 0,   -- existing tickets, nothing new
    error_count          INTEGER      NOT NULL DEFAULT 0,   -- rows NOT imported
    warning_count        INTEGER      NOT NULL DEFAULT 0,
    failure_message      VARCHAR(500),                       -- when the whole file could not be read

    CONSTRAINT chk_import_mode CHECK (mode IN ('DRY_RUN', 'IMPORT')),
    CONSTRAINT chk_import_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_import_source_system CHECK (source_system ~ '^[A-Z][A-Z0-9_]{1,29}$'),
    CONSTRAINT chk_import_sha CHECK (file_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_import_counts CHECK (total_rows >= 0 AND created_count >= 0 AND updated_count >= 0
        AND unchanged_count >= 0 AND error_count >= 0 AND warning_count >= 0)
);

CREATE INDEX ix_import_batches_project ON import_batches (project_id, started_at);


CREATE TABLE import_row_issues (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id            BIGINT       NOT NULL REFERENCES import_batches (id),
    row_number          INTEGER      NOT NULL,              -- row in the file (header = row 1)
    external_ticket_id  VARCHAR(100),
    severity            VARCHAR(10)  NOT NULL,              -- ERROR or WARNING
    column_name         VARCHAR(60),
    message             VARCHAR(500) NOT NULL,
    raw_value           VARCHAR(500),                       -- what the cell contained

    CONSTRAINT chk_import_issue_severity CHECK (severity IN ('ERROR', 'WARNING'))
);

CREATE INDEX ix_import_row_issues_batch ON import_row_issues (batch_id, row_number);


ALTER TABLE tickets
    ADD CONSTRAINT fk_tickets_import_batch FOREIGN KEY (import_batch_id) REFERENCES import_batches (id);
CREATE INDEX ix_tickets_import_batch ON tickets (import_batch_id);


-- ---------- Protection ----------
CREATE TRIGGER trg_import_batches_no_delete
    BEFORE DELETE ON import_batches
    FOR EACH ROW EXECUTE FUNCTION audit.block_changes();

CREATE TRIGGER trg_import_batches_no_truncate
    BEFORE TRUNCATE ON import_batches
    FOR EACH STATEMENT EXECUTE FUNCTION audit.block_changes();

CREATE TRIGGER trg_import_row_issues_no_update_delete
    BEFORE UPDATE OR DELETE ON import_row_issues
    FOR EACH ROW EXECUTE FUNCTION audit.block_changes();

CREATE TRIGGER trg_import_row_issues_no_truncate
    BEFORE TRUNCATE ON import_row_issues
    FOR EACH STATEMENT EXECUTE FUNCTION audit.block_changes();