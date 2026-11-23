CREATE TABLE monthly_log_imports (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    uploaded_by VARCHAR(30) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    sheet_name VARCHAR(80) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    row_count INTEGER NOT NULL CHECK (row_count > 0),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_monthly_log_imports_project_uploaded
    ON monthly_log_imports (project_id, uploaded_at DESC);

CREATE TABLE monthly_log_rows (
    id BIGSERIAL PRIMARY KEY,
    import_id BIGINT NOT NULL REFERENCES monthly_log_imports(id) ON DELETE CASCADE,
    source_row INTEGER NOT NULL,
    search_text TEXT NOT NULL,
    data_json TEXT NOT NULL,
    CONSTRAINT uq_monthly_log_rows_source UNIQUE (import_id, source_row)
);

CREATE INDEX ix_monthly_log_rows_import_source
    ON monthly_log_rows (import_id, source_row);
