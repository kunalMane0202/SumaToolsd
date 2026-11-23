import { useEffect, useMemo, useState } from "react";
import {
  deleteMonthlyLogReport,
  getMonthlyLogImports,
  getMonthlyLogReport,
  getProjects,
  previewMonthlyLog,
  uploadMonthlyLog,
} from "../auth/authService.js";
import "./ProjectAnalyticsPage.css";

const PAGE_SIZE = 25;
const TICKET_ID_ALIASES = new Set(["ticketid", "ticketnumber", "ticketno"]);

function isTicketIdColumn(column) {
  return TICKET_ID_ALIASES.has(String(column).toLowerCase().replace(/[^a-z0-9]/g, ""));
}

function displayTicketId(row) {
  return String(row.ticketId ?? "").trim() || "No ticket ID available";
}

function canUser(user, permission) {
  return user.role === "Super User" || (user.permissions || []).includes(permission);
}

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(`${String(value).slice(0, 10)}T00:00:00`);
  return Number.isNaN(date.getTime())
    ? "—"
    : date.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
}

function formatTimestamp(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString();
}

function csvCell(value) {
  const text = String(value ?? "");
  const safeText = /^[\t\r ]*[=+\-@]/.test(text) ? `'${text}` : text;
  return `"${safeText.replace(/"/g, '""')}"`;
}

function downloadCsv(filename, rows) {
  const content = `\uFEFF${rows.map((row) => row.map(csvCell).join(",")).join("\r\n")}`;
  const blob = new Blob([content], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}

function downloadExcel(filename, rows) {
  const table = rows.map((row) => `<tr>${row.map((value) => {
    const text = String(value ?? "");
    const safeText = /^[\t\r ]*[=+\-@]/.test(text) ? `'${text}` : text;
    const escaped = safeText.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
    return `<td>${escaped}</td>`;
  }).join("")}</tr>`).join("");
  const workbook = `<html><head><meta charset="utf-8"></head><body><table>${table}</table></body></html>`;
  const blob = new Blob([workbook], { type: "application/vnd.ms-excel;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}

function makeFilename(value) {
  return String(value || "project-report").replace(/[^\w.-]+/g, "-").replace(/^-+|-+$/g, "");
}

function Breakdown({ title, items, emptyText }) {
  const max = Math.max(1, ...items.map((item) => item.count));
  return (
    <section className="card project-analytics-card">
      <div className="card-body">
        <h3 className="project-analytics-card__title">{title}</h3>
        {items.length ? (
          <ul className="project-analytics-breakdown">
            {items.slice(0, 7).map((item) => (
              <li key={item.label}>
                <div className="project-analytics-breakdown__label">
                  <span title={item.label}>{item.label}</span><strong>{item.count}</strong>
                </div>
                <div className="progress" role="progressbar" aria-label={`${item.label}: ${item.count}`}>
                  <div className="progress-bar" style={{ width: `${Math.max(3, (item.count / max) * 100)}%` }} />
                </div>
              </li>
            ))}
          </ul>
        ) : <p className="text-secondary small mb-0">{emptyText}</p>}
      </div>
    </section>
  );
}

export default function ProjectAnalyticsPage({ user }) {
  const [projects, setProjects] = useState([]);
  const [projectId, setProjectId] = useState("");
  const [imports, setImports] = useState([]);
  const [reportId, setReportId] = useState("");
  const [report, setReport] = useState(null);
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [loadingProjects, setLoadingProjects] = useState(true);
  const [loadingImports, setLoadingImports] = useState(false);
  const [loadingReport, setLoadingReport] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [importing, setImporting] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const selectedProject = projects.find((project) => String(project.id) === String(projectId));
  const canImport = canUser(user, "projects.edit") || canUser(user, "reports.generate");
  const canExport = canUser(user, "reports.export");
  const analysis = preview || report;
  const metadata = preview
    ? preview
    : report?.report
      ? { ...report.report, columns: Object.keys(report.rows?.[0]?.values || {}), warnings: [] }
      : null;
  const columns = metadata?.columns || [];
  const ticketIdColumn = columns.find(isTicketIdColumn);
  const visibleColumns = [
    ticketIdColumn || "Ticket ID",
    ...columns.filter((column) => column !== ticketIdColumn),
  ].slice(0, 8);
  const totalPages = report ? Math.max(1, Math.ceil(report.totalRows / PAGE_SIZE)) : 1;
  const slaRate = analysis?.summary?.resolutionSlaEligible
    ? Math.round((analysis.summary.resolutionSlaMet / analysis.summary.resolutionSlaEligible) * 100)
    : null;

  useEffect(() => {
    let active = true;
    getProjects()
      .then((rows) => {
        if (!active) return;
        setProjects(rows);
        setProjectId(rows[0] ? String(rows[0].id) : "");
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || "Could not load projects.");
      })
      .finally(() => {
        if (active) setLoadingProjects(false);
      });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let active = true;
    setImports([]);
    setReportId("");
    setReport(null);
    setPreview(null);
    setFile(null);
    setPage(0);
    setQuery("");
    if (!projectId) return () => { active = false; };

    setLoadingImports(true);
    setError("");
    getMonthlyLogImports(projectId)
      .then((rows) => {
        if (!active) return;
        setImports(rows);
        if (rows[0]) setReportId(String(rows[0].id));
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || "Could not load project imports.");
      })
      .finally(() => {
        if (active) setLoadingImports(false);
      });
    return () => { active = false; };
  }, [projectId]);

  useEffect(() => {
    let active = true;
    if (!projectId || !reportId) {
      setReport(null);
      return () => { active = false; };
    }

    setLoadingReport(true);
    getMonthlyLogReport(projectId, reportId, { query, page, pageSize: PAGE_SIZE })
      .then((data) => {
        if (active) setReport(data);
      })
      .catch((requestError) => {
        if (active) {
          setReport(null);
          setError(requestError.message || "Could not load the selected report.");
        }
      })
      .finally(() => {
        if (active) setLoadingReport(false);
      });
    return () => { active = false; };
  }, [projectId, reportId, query, page]);

  const statusItems = useMemo(() => analysis?.summary?.byStatus || [], [analysis]);

  function chooseFile(event) {
    setFile(event.target.files?.[0] || null);
    setPreview(null);
    setError("");
    setNotice("");
  }

  async function showPreview() {
    if (!file || !projectId) return;
    if (file.size > 10 * 1024 * 1024) {
      setError("The workbook exceeds the 10 MB upload limit.");
      return;
    }
    setPreviewing(true);
    setError("");
    setNotice("");
    setReportId("");
    setReport(null);
    try {
      setPreview(await previewMonthlyLog(projectId, file));
    } catch (requestError) {
      setError(requestError.message || "Could not preview the workbook.");
    } finally {
      setPreviewing(false);
    }
  }

  async function importWorkbook() {
    if (!file || !preview || !projectId) return;
    setImporting(true);
    setError("");
    setNotice("");
    try {
      const saved = await uploadMonthlyLog(projectId, file);
      const savedId = String(saved.report.id);
      setPreview(null);
      setFile(null);
      setReportId(savedId);
      setPage(0);
      setQuery("");
      setReport(saved);
      setImports((current) => [saved.report, ...current.filter((item) => String(item.id) !== savedId)]);
      setNotice("Workbook imported. Project analytics are ready.");
    } catch (requestError) {
      setError(requestError.message || "Could not import the workbook.");
    } finally {
      setImporting(false);
    }
  }

  async function removeImport(importItem) {
    if (!window.confirm(`Delete the imported workbook "${importItem.fileName}" and its stored rows?`)) return;
    setError("");
    setNotice("");
    try {
      await deleteMonthlyLogReport(projectId, importItem.id);
      const remaining = imports.filter((item) => String(item.id) !== String(importItem.id));
      setImports(remaining);
      setPage(0);
      setReportId((current) => String(current) === String(importItem.id) ? String(remaining[0]?.id || "") : current);
      setNotice("Imported workbook deleted.");
    } catch (requestError) {
      setError(requestError.message || "Could not delete the imported workbook.");
    }
  }

  async function exportReport(format) {
    if (!reportId || !projectId || !report) return;
    setExporting(true);
    setError("");
    try {
      const firstPage = await getMonthlyLogReport(projectId, reportId, { page: 0, pageSize: 100 });
      const allRows = [...firstPage.rows];
      const pageCount = Math.ceil(firstPage.totalRows / 100);
      for (let exportPage = 1; exportPage < pageCount; exportPage++) {
        const data = await getMonthlyLogReport(projectId, reportId, {
          page: exportPage,
          pageSize: 100,
        });
        allRows.push(...data.rows);
      }

      const sourceColumns = Object.keys(allRows[0]?.values || {});
      const sourceTicketIdColumn = sourceColumns.find(isTicketIdColumn);
      const exportColumns = [
        sourceTicketIdColumn || "Ticket ID",
        ...sourceColumns.filter((column) => column !== sourceTicketIdColumn),
      ];
      const summary = firstPage.summary;
      const rows = [
        ["OpsVista Project Analytics", selectedProject?.name || ""],
        ["Workbook", firstPage.report.fileName],
        ["Period", `${formatDate(firstPage.report.periodStart)} - ${formatDate(firstPage.report.periodEnd)}`],
        [],
        ["Metric", "Value"],
        ["Ticket rows", summary.totalTickets],
        ["Unique tickets", summary.uniqueTickets],
        ["Resolved tickets", summary.resolvedTickets],
        ["Open tickets", summary.openTickets],
        ["Escalated tickets", summary.escalatedTickets],
        ["Average resolution (hours)", summary.averageResolutionHours ?? "Not available"],
        ["Resolution SLA met", `${summary.resolutionSlaMet}/${summary.resolutionSlaEligible}`],
        [],
        ["Satisfaction rating", "Tickets"],
        ...summary.bySatisfactionRating.map((item) => [item.label, item.count]),
        [],
        ["Ticket rows"],
        ["Source row", ...exportColumns],
        ...allRows.map((row) => [
          row.sourceRow,
          ...exportColumns.map((column) => isTicketIdColumn(column)
            ? displayTicketId(row)
            : row.values[column] || ""),
        ]),
      ];
      const baseName = `${makeFilename(selectedProject?.name)}-analytics`;
      if (format === "excel") downloadExcel(`${baseName}.xls`, rows);
      else downloadCsv(`${baseName}.csv`, rows);
    } catch (requestError) {
      setError(requestError.message || "Could not export the report.");
    } finally {
      setExporting(false);
    }
  }

  if (loadingProjects) return <section className="panel"><p className="empty">Loading projects...</p></section>;

  return (
    <section className="panel panel--wide project-analytics-page">
      <header className="project-analytics-heading">
        <div>
          <span className="project-analytics-eyebrow">OPS ANALYTICS / WORKBOOKS</span>
          <h2>Project analytics</h2>
          <p className="text-secondary mb-0">Import audit workbooks, validate ticket data, and track project performance.</p>
        </div>
        <label className="project-analytics-project form-label mb-0">
          <span>Project</span>
          <select className="form-select" value={projectId} onChange={(event) => setProjectId(event.target.value)} disabled={!projects.length}>
            {projects.map((project) => <option value={project.id} key={project.id}>{project.name}</option>)}
          </select>
        </label>
      </header>

      {error && <div className="alert alert-danger py-2 mb-0" role="alert">{error}</div>}
      {notice && <div className="alert alert-success py-2 mb-0" role="status">{notice}</div>}

      {!projects.length ? (
        <div className="alert alert-light border mb-0">No projects are available to analyze.</div>
      ) : (
        <>
          {canImport && (
            <section className="card project-analytics-upload project-analytics-hide-print">
              <div className="card-body">
                <div className="project-analytics-section-heading">
                  <div><h3>Import project audit log</h3><p className="text-secondary mb-0">Upload an .xlsx file. OpsVista detects ticket columns and validates the data before saving.</p></div>
                  <span className="badge text-bg-light border">Up to 10 MB · 10,000 rows</span>
                </div>
                <div className="project-analytics-upload__controls">
                  <label className="form-label flex-grow-1 mb-0">
                    <span className="small fw-semibold d-block mb-2">Excel workbook</span>
                    <input className="form-control" type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={chooseFile} />
                  </label>
                  <button className="btn btn-primary fw-semibold" type="button" onClick={showPreview} disabled={!file || previewing || importing}>
                    {previewing ? "Checking workbook..." : "Preview & validate"}
                  </button>
                </div>
                <p className="project-analytics-help text-secondary mb-0">Required fields: creation date, assignee, and status. Ticket ID is optional; rows without one remain available in reports.</p>
              </div>
            </section>
          )}

          {preview && (
            <section className="card project-analytics-preview">
              <div className="card-body">
                <div className="project-analytics-section-heading">
                  <div><span className="badge text-bg-primary mb-2">PREVIEW · NOT SAVED</span><h3>{preview.fileName}</h3><p className="text-secondary mb-0">{preview.sheetName} · {formatDate(preview.periodStart)} – {formatDate(preview.periodEnd)} · {preview.rowCount.toLocaleString()} ticket rows</p></div>
                  <button className="btn btn-primary fw-semibold project-analytics-hide-print" type="button" onClick={importWorkbook} disabled={importing}>
                    {importing ? "Importing..." : "Confirm import"}
                  </button>
                </div>
                <div className="project-analytics-columns">
                  <strong>Automatically mapped columns</strong>
                  <div>{Object.entries(preview.columnMappings).map(([field, column]) => <span className="badge rounded-pill text-bg-light border" key={field}>{field}: {column}</span>)}</div>
                </div>
                {preview.warnings.length > 0 && (
                  <div className="alert alert-warning mt-3 mb-0">
                    <strong>{preview.warnings.length} data quality warning{preview.warnings.length === 1 ? "" : "s"}</strong>
                    <ul className="mb-0 mt-2">{preview.warnings.map((warning) => <li key={warning}>{warning}</li>)}</ul>
                    <p className="small mt-2 mb-0">Review these rows before confirming. You can still import the workbook.</p>
                  </div>
                )}
                <PreviewRows rows={preview.rows} columns={visibleColumns} />
              </div>
            </section>
          )}

          <section className="card project-analytics-history project-analytics-hide-print">
            <div className="card-body">
              <div className="project-analytics-section-heading">
                <div><h3>Imported workbooks</h3><p className="text-secondary mb-0">Saved ticket data for {selectedProject?.name || "this project"}.</p></div>
                <span className="badge rounded-pill text-bg-light border">{imports.length} imports</span>
              </div>
              {loadingImports ? <p className="text-secondary small mb-0">Loading imports...</p> : imports.length ? (
                <div className="table-responsive">
                  <table className="table table-hover align-middle mb-0">
                    <thead><tr><th>Workbook</th><th>Coverage</th><th>Rows</th><th>Imported</th><th>By</th><th /></tr></thead>
                    <tbody>{imports.map((item) => (
                      <tr className={String(item.id) === reportId ? "table-primary" : ""} key={item.id}>
                        <td><button className="btn btn-link p-0 text-start text-decoration-none fw-semibold" type="button" onClick={() => { setPreview(null); setPage(0); setQuery(""); setReportId(String(item.id)); }}>{item.fileName}</button></td>
                        <td>{formatDate(item.periodStart)} – {formatDate(item.periodEnd)}</td>
                        <td>{Number(item.rowCount).toLocaleString()}</td>
                        <td>{formatTimestamp(item.uploadedAt)}</td>
                        <td>{item.uploadedBy}</td>
                        <td>{canImport && <button className="btn btn-sm btn-outline-secondary" type="button" onClick={() => removeImport(item)}>Delete</button>}</td>
                      </tr>
                    ))}</tbody>
                  </table>
                </div>
              ) : <p className="text-secondary small mb-0">No workbooks imported for this project yet.</p>}
            </div>
          </section>

          {analysis && (
            <section className="project-analytics-report">
              <div className="project-analytics-report-heading">
                <div><span className="project-analytics-eyebrow">PROJECT PERFORMANCE</span><h3>{metadata?.fileName || "Ticket analytics"}</h3><p className="text-secondary mb-0">{formatDate(metadata?.periodStart)} – {formatDate(metadata?.periodEnd)} · {analysis.summary.totalTickets.toLocaleString()} rows</p></div>
                <div className="project-analytics-actions project-analytics-hide-print">
                  {canExport && reportId && <button className="btn btn-outline-secondary" type="button" onClick={() => exportReport("excel")} disabled={exporting || loadingReport}>{exporting ? "Preparing..." : "Export Excel"}</button>}
                  {canExport && reportId && <button className="btn btn-outline-secondary" type="button" onClick={() => exportReport("csv")} disabled={exporting || loadingReport}>Export CSV</button>}
                  {canExport && reportId && <button className="btn btn-outline-secondary" type="button" onClick={() => window.print()}>Print / Save PDF</button>}
                </div>
              </div>

              <div className="project-analytics-kpis">
                <Metric label="Ticket rows" value={analysis.summary.totalTickets} />
                <Metric label="Unique tickets" value={analysis.summary.uniqueTickets} />
                <Metric label="Resolved" value={analysis.summary.resolvedTickets} />
                <Metric label="Open" value={analysis.summary.openTickets} />
                <Metric label="Escalated" value={analysis.summary.escalatedTickets} />
                <Metric label="Avg. resolution" value={analysis.summary.averageResolutionHours == null ? "—" : `${analysis.summary.averageResolutionHours}h`} />
              </div>

              <div className="project-analytics-charts">
                <Breakdown title="Tickets by assignee" items={analysis.summary.byAssignee} emptyText="No assignee data was found." />
                <Breakdown title="Ticket status" items={statusItems} emptyText="No status data was found." />
                <Breakdown title="Tickets by priority" items={analysis.summary.byPriority} emptyText="No priority data was found." />
                <Breakdown title="Tickets closed by" items={analysis.summary.byClosedBy} emptyText="No closure data was found." />
                <Breakdown title="Satisfaction rating" items={analysis.summary.bySatisfactionRating} emptyText="No satisfaction ratings were found." />
              </div>

              <section className="card project-analytics-card">
                <div className="card-body">
                  <div className="project-analytics-section-heading"><div><h3>Resolution SLA</h3><p className="text-secondary mb-0">{analysis.summary.resolutionSlaNote}</p></div><strong className="project-analytics-sla-rate">{slaRate == null ? "N/A" : `${slaRate}%`}</strong></div>
                  <div className="project-analytics-sla-details"><span>{analysis.summary.resolutionSlaMet} met</span><span>{analysis.summary.resolutionSlaEligible} eligible</span><span>{analysis.summary.resolutionSlaUnscored} unscored</span></div>
                </div>
              </section>

              {analysis.summary.trends.length > 0 && (
                <section className="card project-analytics-card">
                  <div className="card-body">
                    <h3 className="project-analytics-card__title">Ticket activity trend</h3>
                    <div className="table-responsive"><table className="table table-sm mb-0"><thead><tr><th>Date</th><th>Created</th><th>Resolved</th></tr></thead>
                      <tbody>{analysis.summary.trends.map((item) => <tr key={item.date}><td>{formatDate(item.date)}</td><td>{item.created}</td><td>{item.closed}</td></tr>)}</tbody>
                    </table></div>
                  </div>
                </section>
              )}

              <section className="card project-analytics-card project-analytics-rows">
                <div className="card-body">
                  <div className="project-analytics-section-heading">
                    <div><h3>Ticket records</h3><p className="text-secondary mb-0">{preview ? "Showing the first 10 preview rows." : `${report?.totalRows?.toLocaleString() || 0} matching records`}</p></div>
                    {!preview && <label className="project-analytics-search project-analytics-hide-print"><span className="visually-hidden">Search ticket rows</span><input className="form-control form-control-sm" type="search" value={query} onChange={(event) => { setQuery(event.target.value); setPage(0); }} placeholder="Search imported rows..." /></label>}
                  </div>
                  {loadingReport && !preview ? <p className="text-secondary small mb-0">Loading ticket records...</p> : (
                    <PreviewRows rows={analysis.rows} columns={visibleColumns} />
                  )}
                  {!preview && report && report.totalRows > PAGE_SIZE && (
                    <div className="d-flex justify-content-between align-items-center mt-3 project-analytics-hide-print">
                      <span className="small text-secondary">Page {page + 1} of {totalPages}</span>
                      <div className="btn-group btn-group-sm">
                        <button className="btn btn-outline-secondary" type="button" disabled={page === 0 || loadingReport} onClick={() => setPage((current) => current - 1)}>Previous</button>
                        <button className="btn btn-outline-secondary" type="button" disabled={page + 1 >= totalPages || loadingReport} onClick={() => setPage((current) => current + 1)}>Next</button>
                      </div>
                    </div>
                  )}
                </div>
              </section>
            </section>
          )}
        </>
      )}
    </section>
  );
}

function Metric({ label, value }) {
  return <article className="card project-analytics-metric"><div className="card-body"><span>{label}</span><strong>{Number.isFinite(value) ? Number(value).toLocaleString() : value}</strong></div></article>;
}

function PreviewRows({ rows = [], columns = [] }) {
  if (!rows.length) return <p className="text-secondary small mb-0">No ticket rows are available.</p>;
  return (
    <div className="table-responsive project-analytics-table-wrap">
      <table className="table table-sm table-striped align-middle mb-0">
        <thead><tr><th>Row</th>{columns.map((column) => <th key={column}>{column}</th>)}</tr></thead>
        <tbody>{rows.map((row) => <tr key={row.sourceRow}><td>{row.sourceRow}</td>{columns.map((column) => <td key={column}>{isTicketIdColumn(column) ? displayTicketId(row) : row.values[column] || "—"}</td>)}</tr>)}</tbody>
      </table>
    </div>
  );
}
