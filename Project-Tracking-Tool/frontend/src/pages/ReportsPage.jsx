import { useEffect, useMemo, useState } from "react";
import {
  createReportSchedule,
  deleteReportSchedule,
  getReportEmailDeliveries,
  getMonthlyLogImports,
  getMonthlyLogReport,
  getProjects,
  getReportSchedules,
  getReportTickets,
} from "../auth/authService.js";
import "./ReportsPage.css";

const WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
const MAX_REPORT_RECIPIENTS = 10;
const OPEN_STATUSES = new Set(["Open", "In Progress", "Pending"]);
const REPORT_TABS = ["Overview", "Uploaded Log Analytics", "Employee Productivity", "SLA & Ticket Reports", "Pending & ageing", "Scheduled Reports"];

function localDateValue(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function getDateRange(period, dateValue) {
  const selected = new Date(`${dateValue}T00:00:00`);
  let start = new Date(selected);
  let end = new Date(selected);

  if (period === "weekly") {
    start.setDate(start.getDate() - ((start.getDay() + 6) % 7));
    end = new Date(start);
    end.setDate(end.getDate() + 6);
  } else if (period === "monthly") {
    start = new Date(selected.getFullYear(), selected.getMonth(), 1);
    end = new Date(selected.getFullYear(), selected.getMonth() + 1, 0);
  }

  start.setHours(0, 0, 0, 0);
  end.setHours(23, 59, 59, 999);
  return { start, end };
}

function inRange(value, range) {
  if (!value) return false;
  const timestamp = new Date(value).getTime();
  return Number.isFinite(timestamp) && timestamp >= range.start.getTime() && timestamp <= range.end.getTime();
}

function ageInDays(ticket, now) {
  const created = new Date(ticket.createdAt).getTime();
  if (!Number.isFinite(created)) return 0;
  return Math.max(0, Math.floor((now.getTime() - created) / 86400000));
}

function isWithinSla(ticket, kind, now) {
  const dueAt = ticket[kind === "response" ? "responseDueAt" : "resolutionDueAt"];
  if (!dueAt || !Number.isFinite(new Date(dueAt).getTime())) return null;

  const completedAt = kind === "response"
    ? ticket.firstResponseAt
    : ticket.resolvedAt || ticket.closedAt;

  if (completedAt) return new Date(completedAt).getTime() <= new Date(dueAt).getTime();
  return now.getTime() <= new Date(dueAt).getTime();
}

function formatDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isFinite(date.getTime())
    ? date.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" })
    : "—";
}

function formatDateTime(value) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isFinite(date.getTime())
    ? date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })
    : "—";
}

function getLogMonths(trends = []) {
  return [...new Set(trends
    .filter((item) => item.created > 0)
    .map((item) => String(item.date).slice(0, 7)))]
    .sort((a, b) => b.localeCompare(a));
}

function formatMonthLabel(value) {
  const [year, month] = value.split("-").map(Number);
  return new Date(year, month - 1, 1).toLocaleDateString(undefined, { month: "long", year: "numeric" });
}

function rateLabel(compliant, eligible) {
  return eligible ? `${Math.round((compliant / eligible) * 100)}%` : "N/A";
}

function ratePercent(compliant, eligible) {
  return eligible ? Math.round((compliant / eligible) * 100) : 0;
}

function escapeCell(value) {
  const text = spreadsheetCell(value);
  return text.replace(/"/g, '""');
}

function spreadsheetCell(value) {
  const text = String(value ?? "");
  return /^[\t\r ]*[=+\-@]/.test(text) ? `'${text}` : text;
}

function downloadFile(filename, content, mimeType) {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function makeReportRows(metrics, employees, pending, periodLabel) {
  return [
    ["OpsVista - Reports & Analytics", periodLabel],
    [],
    ["Summary", "Value"],
    ["Tickets created", metrics.total],
    ["Resolved or closed", metrics.resolved],
    ["Pending backlog", pending.length],
    ["Response SLA compliance", rateLabel(metrics.responseCompliant, metrics.responseEligible)],
    ["Resolution SLA compliance", rateLabel(metrics.resolutionCompliant, metrics.resolutionEligible)],
    [],
    ["Employee productivity", "Resolved", "Open assigned"],
    ...employees.map((employee) => [employee.name, employee.resolved, employee.open]),
    [],
    ["Pending and ageing tickets", "Subject", "Assignee", "Status", "Age (days)", "Created"],
    ...pending.map((ticket) => [
      ticket.ticketNumber || ticket.id,
      ticket.subject,
      ticket.assignee || "Unassigned",
      ticket.status,
      ticket.age,
      formatDate(ticket.createdAt),
    ]),
  ];
}

function getTrendBuckets(tickets, period, range) {
  const start = new Date(range.start);
  if (period === "daily") start.setDate(start.getDate() - 6);
  start.setHours(0, 0, 0, 0);

  const bucketDays = period === "monthly" ? 7 : 1;
  const buckets = [];
  let cursor = start;

  while (cursor <= range.end) {
    const bucketStart = new Date(cursor);
    const bucketEnd = new Date(cursor);
    bucketEnd.setDate(bucketEnd.getDate() + bucketDays);
    bucketEnd.setMilliseconds(bucketEnd.getMilliseconds() - 1);
    const effectiveEnd = bucketEnd < range.end ? bucketEnd : range.end;
    const inBucket = (value) => {
      const timestamp = new Date(value).getTime();
      return Number.isFinite(timestamp) && timestamp >= bucketStart.getTime() && timestamp <= effectiveEnd.getTime();
    };

    buckets.push({
      label: period === "monthly"
        ? `Week ${buckets.length + 1}`
        : bucketStart.toLocaleDateString(undefined, { weekday: "short" }),
      created: tickets.filter((ticket) => inBucket(ticket.createdAt)).length,
      resolved: tickets.filter((ticket) => inBucket(ticket.resolvedAt || ticket.closedAt)).length,
    });

    cursor = new Date(bucketEnd);
    cursor.setMilliseconds(cursor.getMilliseconds() + 1);
    cursor.setHours(0, 0, 0, 0);
    if (period === "daily" && buckets.length >= 7) break;
  }

  return buckets;
}

const STATUS_COLORS = {
  Open: "#1c4778",
  "In Progress": "#527ba8",
  Pending: "#c18a2e",
  Resolved: "#3d8b63",
  Closed: "#94a3b8",
};

function TicketTrendChart({ data }) {
  const maxValue = Math.max(1, ...data.flatMap((item) => [item.created, item.resolved]));

  return (
    <div className="reports-trend" role="img" aria-label="Tickets created and resolved over the report period">
      <div className="reports-trend__legend">
        <span><i className="reports-swatch reports-swatch--created" />Created</span>
        <span><i className="reports-swatch reports-swatch--resolved" />Resolved</span>
      </div>
      <div className="reports-trend__plot">
        {data.map((item, index) => (
          <div className="reports-trend__column" key={`${item.label}-${index}`}>
            <div className="reports-trend__bars">
              <span
                className="reports-trend__bar reports-trend__bar--created"
                style={{ height: `${item.created ? Math.max(4, item.created / maxValue * 100) : 0}%` }}
                title={`${item.created} created`}
              />
              <span
                className="reports-trend__bar reports-trend__bar--resolved"
                style={{ height: `${item.resolved ? Math.max(4, item.resolved / maxValue * 100) : 0}%` }}
                title={`${item.resolved} resolved`}
              />
            </div>
            <span className="reports-trend__label">{item.label}</span>
            <small>{item.created}/{item.resolved}</small>
          </div>
        ))}
      </div>
    </div>
  );
}

function TicketStatusChart({ data }) {
  const total = data.reduce((sum, item) => sum + item.count, 0);
  let offset = 0;
  const stops = data.filter((item) => item.count > 0).map((item) => {
    const from = offset;
    offset += (item.count / total) * 100;
    return `${item.color} ${from}% ${offset}%`;
  });

  return (
    <div className="reports-status-chart">
      <div
        className={`reports-donut${total ? "" : " reports-donut--empty"}`}
        style={total ? { background: `conic-gradient(${stops.join(", ")})` } : undefined}
        role="img"
        aria-label={total ? data.filter((item) => item.count).map((item) => `${item.status}: ${item.count}`).join(", ") : "No tickets"}
      >
        <div><strong>{total}</strong><span>tickets</span></div>
      </div>
      <ul className="reports-status-legend">
        {data.map((item) => (
          <li key={item.status}><i style={{ backgroundColor: item.color }} /><span>{item.status}</span><strong>{item.count}</strong></li>
        ))}
      </ul>
    </div>
  );
}

function AgeingChart({ buckets }) {
  const maxValue = Math.max(1, ...buckets.map((bucket) => bucket.count));
  return (
    <div className="reports-ageing-chart" role="img" aria-label="Pending tickets grouped by age">
      {buckets.map((bucket) => (
        <div className="reports-ageing-row" key={bucket.label}>
          <span>{bucket.label}</span>
          <div><i style={{ width: `${bucket.count ? Math.max(3, bucket.count / maxValue * 100) : 0}%` }} /></div>
          <strong>{bucket.count}</strong>
        </div>
      ))}
    </div>
  );
}

function MonthlyLogBreakdown({ title, items }) {
  const maxValue = Math.max(1, ...items.map((item) => item.count));
  const visibleItems = items.slice(0, 8);
  const remainingItems = items.slice(8);
  const renderItems = (rows, keyPrefix = "") => (
    <ul className="reports-log-breakdown">
      {rows.map((item, index) => (
        <li key={`${keyPrefix}${item.label}-${index}`}>
          <div className="reports-log-breakdown__label"><span title={item.label}>{item.label}</span><strong>{item.count.toLocaleString()}</strong></div>
          <div className="reports-log-breakdown__track" role="progressbar" aria-label={`${item.label}: ${item.count}`} aria-valuenow={item.count} aria-valuemin="0" aria-valuemax={maxValue}>
            <i style={{ width: `${Math.max(3, (item.count / maxValue) * 100)}%` }} />
          </div>
        </li>
      ))}
    </ul>
  );

  return (
    <section className="reports-card reports-log-breakdown-card">
      <div className="reports-card__heading"><div><h2>{title}</h2><p>{items.length.toLocaleString()} values</p></div></div>
      {items.length ? (
        <>
          {renderItems(visibleItems)}
          {remainingItems.length > 0 && (
            <details className="reports-log-breakdown__details">
              <summary>Show all {items.length.toLocaleString()} values</summary>
              {renderItems(remainingItems, "more-")}
            </details>
          )}
        </>
      ) : <p className="reports-empty">No data was found for this field.</p>}
    </section>
  );
}

function MonthlyLogTrendChart({ data }) {
  const maxValue = Math.max(1, ...data.flatMap((item) => [item.created, item.closed]));
  return (
    <section className="reports-card reports-log-trend-card">
      <div className="reports-card__heading">
        <div><h2>Created and resolved over time</h2><p>Ticket counts by date from the uploaded workbook.</p></div>
      </div>
      <div className="reports-log-trend__legend">
        <span><i className="reports-log-trend__swatch reports-log-trend__swatch--created" />Created</span>
        <span><i className="reports-log-trend__swatch reports-log-trend__swatch--resolved" />Resolved / closed</span>
      </div>
      {data.length ? (
        <div className="reports-log-trend__plot">
          {data.map((item) => (
            <div className="reports-log-trend__column" key={item.date} role="group" aria-label={`${formatDate(item.date)}: ${item.created} created, ${item.closed} resolved`} title={`${formatDate(item.date)}: ${item.created} created, ${item.closed} resolved`}>
              <div className="reports-log-trend__bars">
                <i aria-hidden="true" className="reports-log-trend__bar reports-log-trend__bar--created" style={{ height: `${item.created ? Math.max(4, (item.created / maxValue) * 100) : 0}%` }} />
                <i aria-hidden="true" className="reports-log-trend__bar reports-log-trend__bar--resolved" style={{ height: `${item.closed ? Math.max(4, (item.closed / maxValue) * 100) : 0}%` }} />
              </div>
              <span>{new Date(`${String(item.date).slice(0, 10)}T00:00:00`).toLocaleDateString(undefined, { day: "numeric", month: "short" })}</span>
              <small>{item.created}/{item.closed}</small>
            </div>
          ))}
        </div>
      ) : <p className="reports-empty">No dated ticket activity was found.</p>}
    </section>
  );
}

function ResolvedByEmployeeChart({ data }) {
  const maxValue = Math.max(1, ...data.map((item) => item.count));
  const visibleItems = data.slice(0, 8);
  const remainingItems = data.slice(8);
  const renderItems = (items, offset = 0) => (
    <ol className="reports-resolved-employee-list">
      {items.map((item, index) => (
        <li key={`${item.label}-${index}`}>
          <div className="reports-resolved-employee__label">
            <span className="reports-resolved-employee__rank">{offset + index + 1}</span>
            <span className="reports-resolved-employee__name" title={item.label}>{item.label}</span>
            <strong>{item.count.toLocaleString()}</strong>
          </div>
          <div className="reports-resolved-employee__track" role="progressbar" aria-label={`${item.label}: ${item.count} resolved tickets`} aria-valuenow={item.count} aria-valuemin="0" aria-valuemax={maxValue}>
            <i style={{ width: `${Math.max(3, (item.count / maxValue) * 100)}%` }} />
          </div>
        </li>
      ))}
    </ol>
  );

  if (!data.length) return <p className="reports-empty">No resolved tickets were found in this workbook.</p>;

  return (
    <div className="reports-resolved-employee-chart">
      {renderItems(visibleItems)}
      {remainingItems.length > 0 && (
        <details className="reports-log-breakdown__details">
          <summary>Show all {data.length.toLocaleString()} employees</summary>
          {renderItems(remainingItems, visibleItems.length)}
        </details>
      )}
    </div>
  );
}

export default function ReportsPage({ user }) {
  const [tickets, setTickets] = useState([]);
  const [schedules, setSchedules] = useState([]);
  const [deliveries, setDeliveries] = useState([]);
  const [logProjects, setLogProjects] = useState([]);
  const [logProjectId, setLogProjectId] = useState("");
  const [logImports, setLogImports] = useState([]);
  const [logReportId, setLogReportId] = useState("");
  const [logReport, setLogReport] = useState(null);
  const [logMonth, setLogMonth] = useState("");
  const [logMonths, setLogMonths] = useState([]);
  const [period, setPeriod] = useState("weekly");
  const [activeTab, setActiveTab] = useState("Overview");
  const [referenceDate, setReferenceDate] = useState(() => localDateValue(new Date()));
  const [now, setNow] = useState(() => new Date());
  const [loading, setLoading] = useState(true);
  const [loadingLogProjects, setLoadingLogProjects] = useState(true);
  const [loadingLogImports, setLoadingLogImports] = useState(false);
  const [loadingLogReport, setLoadingLogReport] = useState(false);
  const [savingSchedule, setSavingSchedule] = useState(false);
  const [removingId, setRemovingId] = useState("");
  const [refreshingDeliveries, setRefreshingDeliveries] = useState(false);
  const [error, setError] = useState("");
  const [logAnalyticsError, setLogAnalyticsError] = useState("");
  const [notice, setNotice] = useState("");
  const [scheduleForm, setScheduleForm] = useState({
    emails: [user.email || ""],
    frequency: "weekly",
    time: "09:00",
    weekday: String(new Date().getDay()),
    monthDay: "1",
  });

  const canExport = user.role === "Super User" || (user.permissions || []).includes("reports.export");
  const canSchedule = user.role === "Super User" || (user.permissions || []).includes("reports.schedule");
  const canViewProjectLogs = user.role === "Super User" || (user.permissions || []).includes("projects.view");
  const range = useMemo(() => getDateRange(period, referenceDate), [period, referenceDate]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60_000);
    let active = true;
    Promise.all([getReportTickets(), getReportSchedules(), getReportEmailDeliveries()])
      .then(([ticketData, scheduleData, deliveryData]) => {
        if (!active) return;
        setTickets(ticketData);
        setSchedules(scheduleData);
        setDeliveries(deliveryData);
      })
      .catch((requestError) => {
        if (active) setError(requestError.message || "Could not load reports.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    let active = true;
    if (!canViewProjectLogs) {
      setLoadingLogProjects(false);
      return () => { active = false; };
    }

    setLoadingLogProjects(true);
    setLogAnalyticsError("");
    getProjects()
      .then((rows) => {
        if (!active) return;
        setLogProjects(rows);
        setLogProjectId((current) => rows.some((project) => String(project.id) === current)
          ? current
          : String(rows[0]?.id || ""));
      })
      .catch((requestError) => {
        if (active) setLogAnalyticsError(requestError.message || "Could not load projects for uploaded log analytics.");
      })
      .finally(() => {
        if (active) setLoadingLogProjects(false);
      });

    return () => { active = false; };
  }, [canViewProjectLogs]);

  useEffect(() => {
    let active = true;
    setLogImports([]);
    setLogReportId("");
    setLogReport(null);
    setLogMonth("");
    setLogMonths([]);
    setLogAnalyticsError("");
    if (!logProjectId) {
      setLoadingLogImports(false);
      return () => { active = false; };
    }

    setLoadingLogImports(true);
    getMonthlyLogImports(logProjectId)
      .then((rows) => {
        if (!active) return;
        setLogImports(rows);
        setLogReportId(String(rows[0]?.id || ""));
        setLogMonth("");
        setLogMonths([]);
      })
      .catch((requestError) => {
        if (active) setLogAnalyticsError(requestError.message || "Could not load uploaded workbooks.");
      })
      .finally(() => {
        if (active) setLoadingLogImports(false);
      });

    return () => { active = false; };
  }, [logProjectId]);

  useEffect(() => {
    let active = true;
    setLogReport(null);
    setLogAnalyticsError("");
    if (!logProjectId || !logReportId) {
      setLoadingLogReport(false);
      return () => { active = false; };
    }

    setLoadingLogReport(true);
    getMonthlyLogReport(logProjectId, logReportId, { month: logMonth, page: 0, pageSize: 1 })
      .then((data) => {
        if (!active) return;
        setLogReport(data);
        if (!logMonth) setLogMonths(getLogMonths(data.summary?.trends));
      })
      .catch((requestError) => {
        if (active) setLogAnalyticsError(requestError.message || "Could not load the uploaded log summary.");
      })
      .finally(() => {
        if (active) setLoadingLogReport(false);
      });

    return () => { active = false; };
  }, [logProjectId, logReportId, logMonth]);

  const periodTickets = useMemo(
    () => tickets.filter((ticket) => inRange(ticket.createdAt, range)),
    [tickets, range],
  );
  const pendingTickets = useMemo(
    () => tickets
      .filter((ticket) => OPEN_STATUSES.has(ticket.status))
      .map((ticket) => ({ ...ticket, age: ageInDays(ticket, now) }))
      .sort((a, b) => b.age - a.age),
    [tickets, now],
  );
  const employeeRows = useMemo(() => {
    const employees = new Map();
    for (const ticket of tickets) {
      if (!["Resolved", "Closed"].includes(ticket.status) || !inRange(ticket.resolvedAt || ticket.closedAt, range)) continue;
      const name = ticket.assignee || "Unassigned";
      if (!employees.has(name)) employees.set(name, { name, resolved: 0, open: 0 });
      employees.get(name).resolved += 1;
    }
    for (const ticket of pendingTickets) {
      const name = ticket.assignee || "Unassigned";
      if (!employees.has(name)) employees.set(name, { name, resolved: 0, open: 0 });
      employees.get(name).open += 1;
    }
    return [...employees.values()].sort((a, b) => b.resolved - a.resolved || a.name.localeCompare(b.name));
  }, [tickets, pendingTickets, range]);
  const trendBuckets = useMemo(
    () => getTrendBuckets(tickets, period, range),
    [tickets, period, range],
  );
  const statusBuckets = useMemo(() => {
    const counts = new Map();
    for (const ticket of tickets) counts.set(ticket.status || "Unknown", (counts.get(ticket.status || "Unknown") || 0) + 1);
    return [...counts.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([status, count]) => ({ status, count, color: STATUS_COLORS[status] || "#64748b" }));
  }, [tickets]);
  const ageingBuckets = useMemo(() => [
    { label: "0–2 days", count: pendingTickets.filter((ticket) => ticket.age <= 2).length },
    { label: "3–7 days", count: pendingTickets.filter((ticket) => ticket.age >= 3 && ticket.age <= 7).length },
    { label: "8–14 days", count: pendingTickets.filter((ticket) => ticket.age >= 8 && ticket.age <= 14).length },
    { label: "15+ days", count: pendingTickets.filter((ticket) => ticket.age >= 15).length },
  ], [pendingTickets]);

  const metrics = useMemo(() => {
    const resolved = tickets.filter((ticket) => ["Resolved", "Closed"].includes(ticket.status) && inRange(ticket.resolvedAt || ticket.closedAt, range)).length;
    const responseResults = periodTickets.map((ticket) => isWithinSla(ticket, "response", now)).filter((value) => value !== null);
    const resolutionResults = periodTickets.map((ticket) => isWithinSla(ticket, "resolution", now)).filter((value) => value !== null);
    return {
      total: periodTickets.length,
      resolved,
      responseEligible: responseResults.length,
      responseCompliant: responseResults.filter(Boolean).length,
      resolutionEligible: resolutionResults.length,
      resolutionCompliant: resolutionResults.filter(Boolean).length,
    };
  }, [periodTickets, tickets, range, now]);

  const periodLabel = `${period.charAt(0).toUpperCase()}${period.slice(1)} report · ${formatDate(range.start)} – ${formatDate(range.end)}`;
  const overviewLogProject = logProjects.find((project) => String(project.id) === logProjectId);
  const overviewLogImport = logImports.find((item) => String(item.id) === logReportId);
  const averageAge = pendingTickets.length
    ? (pendingTickets.reduce((sum, ticket) => sum + ticket.age, 0) / pendingTickets.length).toFixed(1)
    : "0.0";
  const employeesWithResolutions = employeeRows.filter((employee) => employee.resolved > 0).length;
  const resolvedPerEmployee = employeesWithResolutions
    ? (metrics.resolved / employeesWithResolutions).toFixed(1)
    : "0.0";

  function scheduleDescription(schedule) {
    if (schedule.frequency === "weekly") return `Every ${WEEKDAYS[schedule.weekday]} at ${schedule.time}`;
    if (schedule.frequency === "monthly") return `Day ${schedule.monthDay} of each month at ${schedule.time}`;
    return `Every day at ${schedule.time}`;
  }

  async function refreshSchedules() {
    const data = await getReportSchedules();
    setSchedules(data);
  }

  async function refreshDeliveries() {
    setRefreshingDeliveries(true);
    setError("");
    try {
      setDeliveries(await getReportEmailDeliveries());
    } catch (requestError) {
      setError(requestError.message || "Could not load previous scheduled emails.");
    } finally {
      setRefreshingDeliveries(false);
    }
  }

  async function handleScheduleSubmit(event) {
    event.preventDefault();
    setError("");
    setNotice("");
    setSavingSchedule(true);
    try {
      await createReportSchedule({
        ...scheduleForm,
        emails: scheduleForm.emails.map((email) => email.trim()),
        weekday: Number(scheduleForm.weekday),
        monthDay: Number(scheduleForm.monthDay),
        timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
      });
      await refreshSchedules();
      setNotice("Email report schedule created.");
    } catch (requestError) {
      setError(requestError.message || "Could not create the email schedule.");
    } finally {
      setSavingSchedule(false);
    }
  }

  async function handleDeleteSchedule(scheduleId) {
    setError("");
    setNotice("");
    setRemovingId(scheduleId);
    try {
      await deleteReportSchedule(scheduleId);
      setSchedules((current) => current.filter((schedule) => schedule.id !== scheduleId));
      setNotice("Email report schedule removed.");
    } catch (requestError) {
      setError(requestError.message || "Could not remove the email schedule.");
    } finally {
      setRemovingId("");
    }
  }

  function exportReport(format) {
    const rows = makeReportRows(metrics, employeeRows, pendingTickets, periodLabel);
    const filename = `connect-projects-${period}-report-${referenceDate}`;
    if (format === "csv") {
      const csv = rows.map((row) => row.map((cell) => `"${escapeCell(cell)}"`).join(",")).join("\r\n");
      downloadFile(`${filename}.csv`, `\uFEFF${csv}`, "text/csv;charset=utf-8");
      return;
    }

    const table = rows.map((row) => `<tr>${row.map((cell) => `<td>${spreadsheetCell(cell).replace(/[&<>"']/g, (character) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    }[character]))}</td>`).join("")}</tr>`).join("");
    if (format === "excel") {
      const workbook = `<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel"><head><meta charset="utf-8"><!--[if gte mso 9]><xml><x:ExcelWorkbook><x:ExcelWorksheets><x:ExcelWorksheet><x:Name>Reports</x:Name><x:WorksheetOptions><x:DisplayGridlines/></x:WorksheetOptions></x:ExcelWorksheet></x:ExcelWorksheets></x:ExcelWorkbook></xml><![endif]--></head><body><table>${table}</table></body></html>`;
      downloadFile(`${filename}.xls`, workbook, "application/vnd.ms-excel;charset=utf-8");
      return;
    }
    window.print();
  }

  const chartDescription = period === "daily"
    ? "Daily totals across the seven days ending on the reference date."
    : period === "monthly"
      ? "Weekly totals during the selected month."
      : "Daily totals during the selected week.";

  const renderEmployeeReport = () => (
    <section className="reports-card">
      <div className="reports-card__heading">
        <div><h2>Employee productivity</h2><p>Tickets resolved during the selected period and current open assignments.</p></div>
      </div>
      {employeeRows.length ? (
        <div className="table-wrap">
          <table className="table reports-table">
            <thead><tr><th>Employee</th><th>Resolved in period</th><th>Open assigned</th><th>Resolved share</th></tr></thead>
            <tbody>{employeeRows.map((employee) => {
              const maxResolved = Math.max(1, ...employeeRows.map((item) => item.resolved));
              return <tr key={employee.name}><td>{employee.name}</td><td>{employee.resolved}</td><td>{employee.open}</td><td><span className="reports-bar"><span style={{ width: `${(employee.resolved / maxResolved) * 100}%` }} /></span></td></tr>;
            })}</tbody>
          </table>
        </div>
      ) : <p className="reports-empty">No ticket activity is available for this period.</p>}
    </section>
  );

  const renderSlaReport = () => (
    <>
      <section className="reports-card">
        <div className="reports-card__heading">
          <div><h2>SLA compliance</h2><p>Compliance is measured against each ticket’s response and resolution due times.</p></div>
        </div>
        <div className="reports-sla-grid">
          <div className="reports-sla-card">
            <span>First response</span><strong>{rateLabel(metrics.responseCompliant, metrics.responseEligible)}</strong>
            <div className="reports-sla-track" role="progressbar" aria-label="First response SLA compliance" aria-valuenow={ratePercent(metrics.responseCompliant, metrics.responseEligible)} aria-valuemin="0" aria-valuemax="100"><span style={{ width: `${ratePercent(metrics.responseCompliant, metrics.responseEligible)}%` }} /></div>
            <small>{metrics.responseCompliant} of {metrics.responseEligible} within target</small>
          </div>
          <div className="reports-sla-card">
            <span>Resolution</span><strong>{rateLabel(metrics.resolutionCompliant, metrics.resolutionEligible)}</strong>
            <div className="reports-sla-track" role="progressbar" aria-label="Resolution SLA compliance" aria-valuenow={ratePercent(metrics.resolutionCompliant, metrics.resolutionEligible)} aria-valuemin="0" aria-valuemax="100"><span style={{ width: `${ratePercent(metrics.resolutionCompliant, metrics.resolutionEligible)}%` }} /></div>
            <small>{metrics.resolutionCompliant} of {metrics.resolutionEligible} within target</small>
          </div>
        </div>
      </section>
      <div className="reports-charts-grid">
        <section className="reports-card reports-chart-panel">
          <div className="reports-card__heading"><div><h2>Ticket status</h2><p>Current ticket distribution.</p></div></div>
          {statusBuckets.length ? <TicketStatusChart data={statusBuckets} /> : <p className="reports-empty">No tickets are available for this account.</p>}
        </section>
        <section className="reports-card reports-chart-panel">
          <div className="reports-card__heading"><div><h2>Ticket activity</h2><p>{chartDescription}</p></div></div>
          <TicketTrendChart data={trendBuckets} />
        </section>
      </div>
    </>
  );

  const renderPendingReport = () => (
    <>
      <div className="reports-charts-grid reports-charts-grid--backlog">
        <section className="reports-card reports-chart-panel">
          <div className="reports-card__heading"><div><h2>Backlog ageing</h2><p>Open tickets grouped by age.</p></div></div>
          <AgeingChart buckets={ageingBuckets} />
        </section>
        <section className="reports-card reports-chart-panel reports-backlog-summary">
          <p className="reports-eyebrow">CURRENT BACKLOG</p>
          <strong>{pendingTickets.length}</strong>
          <span>pending tickets</span>
          <small>{averageAge} days average age</small>
        </section>
      </div>
      <section className="reports-card">
        <div className="reports-card__heading">
          <div><h2>Pending &amp; ageing tickets</h2><p>Current open backlog, sorted from oldest to newest.</p></div>
          <span className="reports-count">{pendingTickets.length} pending</span>
        </div>
        {pendingTickets.length ? (
          <div className="table-wrap">
            <table className="table reports-table reports-age-table">
              <thead><tr><th>Ticket</th><th>Subject</th><th>Assignee</th><th>Status</th><th>Age</th><th>Response SLA</th><th>Resolution SLA</th></tr></thead>
              <tbody>{pendingTickets.map((ticket) => {
                const response = isWithinSla(ticket, "response", now);
                const resolution = isWithinSla(ticket, "resolution", now);
                return <tr key={ticket.id}><td>{ticket.ticketNumber || ticket.id}</td><td>{ticket.subject}</td><td>{ticket.assignee || "Unassigned"}</td><td><span className="reports-status">{ticket.status}</span></td><td><strong className={ticket.age >= 7 ? "reports-age reports-age--high" : "reports-age"}>{ticket.age}d</strong></td><td>{response === null ? "—" : response ? "On track" : "Breached"}</td><td>{resolution === null ? "—" : resolution ? "On track" : "Breached"}</td></tr>;
              })}</tbody>
            </table>
          </div>
        ) : <p className="reports-empty">There are no pending tickets.</p>}
      </section>
    </>
  );

  const renderScheduleReport = () => canSchedule ? (
    <section className="reports-card reports-schedule">
      <div className="reports-card__heading">
        <div><h2>Scheduled email reports</h2><p>Send a recurring ticket summary to one or more email addresses.</p></div>
      </div>
      <form className="reports-schedule-form" onSubmit={handleScheduleSubmit}>
        <div className="reports-schedule-recipients">
          <span>Email addresses</span>
          {scheduleForm.emails.map((email, index) => (
            <div className="reports-schedule-recipient" key={index}>
              <input
                type="email"
                required
                aria-label={`Recipient email ${index + 1}`}
                value={email}
                onChange={(event) => setScheduleForm((current) => ({
                  ...current,
                  emails: current.emails.map((value, itemIndex) => itemIndex === index ? event.target.value : value),
                }))}
                placeholder="name@example.com"
              />
              {scheduleForm.emails.length > 1 && (
                <button
                  type="button"
                  className="reports-remove-recipient"
                  aria-label={`Remove recipient ${index + 1}`}
                  onClick={() => setScheduleForm((current) => ({
                    ...current,
                    emails: current.emails.filter((_, itemIndex) => itemIndex !== index),
                  }))}
                >Remove</button>
              )}
            </div>
          ))}
          <button
            type="button"
            className="reports-add-recipient"
            disabled={scheduleForm.emails.length >= MAX_REPORT_RECIPIENTS}
            onClick={() => setScheduleForm((current) => ({ ...current, emails: [...current.emails, ""] }))}
          >+ Add another email</button>
        </div>
        <label><span>Frequency</span><select value={scheduleForm.frequency} onChange={(event) => setScheduleForm({ ...scheduleForm, frequency: event.target.value })}><option value="daily">Daily</option><option value="weekly">Weekly</option><option value="monthly">Monthly</option></select></label>
        {scheduleForm.frequency === "weekly" && <label><span>Day</span><select value={scheduleForm.weekday} onChange={(event) => setScheduleForm({ ...scheduleForm, weekday: event.target.value })}>{WEEKDAYS.map((day, index) => <option value={index} key={day}>{day}</option>)}</select></label>}
        {scheduleForm.frequency === "monthly" && <label><span>Day of month</span><select value={scheduleForm.monthDay} onChange={(event) => setScheduleForm({ ...scheduleForm, monthDay: event.target.value })}>{Array.from({ length: 28 }, (_, index) => index + 1).map((day) => <option value={day} key={day}>{day}</option>)}</select></label>}
        <label><span>Send time</span><input type="time" required value={scheduleForm.time} onChange={(event) => setScheduleForm({ ...scheduleForm, time: event.target.value })} /></label>
        <button className="btn btn--primary" type="submit" disabled={savingSchedule}>{savingSchedule ? "Saving..." : "Schedule report"}</button>
      </form>
      <p className="reports-schedule-note">Times use your browser’s time zone. Automatic delivery requires MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD, and MAIL_FROM in the backend environment.</p>
      <div className="reports-history-section">
        <div className="reports-card__heading">
          <div><h2>Active schedules</h2><p>Recurring email reports you have configured.</p></div>
        </div>
        {schedules.length > 0 ? (
          <ul className="reports-schedule-list">
            {schedules.map((schedule) => (
              <li key={schedule.id}>
                <div><strong>{scheduleDescription(schedule)}</strong><span>{(schedule.emails || [schedule.email]).join(", ")} · {schedule.timeZone}</span><small>{schedule.lastSentAt ? `Last sent ${formatDate(schedule.lastSentAt)}` : "Not sent yet"}</small></div>
                <button className="btn" type="button" disabled={removingId === schedule.id} onClick={() => handleDeleteSchedule(schedule.id)}>{removingId === schedule.id ? "Removing..." : "Remove"}</button>
              </li>
            ))}
          </ul>
        ) : <p className="reports-empty">No email report schedules yet.</p>}
      </div>
      <div className="reports-history-section">
        <div className="reports-card__heading">
          <div><h2>Previous scheduled emails</h2><p>Successfully delivered report emails, newest first.</p></div>
          <button className="btn" type="button" onClick={refreshDeliveries} disabled={refreshingDeliveries}>{refreshingDeliveries ? "Refreshing..." : "Refresh history"}</button>
        </div>
        {deliveries.length ? (
          <ul className="reports-delivery-list">
            {deliveries.map((delivery) => (
              <li className="reports-delivery-row" key={delivery.id}>
                <span className="reports-file-chip">{delivery.frequency.slice(0, 3).toUpperCase()}</span>
                <div><strong>{delivery.frequency.charAt(0).toUpperCase() + delivery.frequency.slice(1)} report · {delivery.reportPeriod}</strong><span>{delivery.recipientEmail} · {formatDateTime(delivery.sentAt)}</span></div>
                <span className="reports-delivery-status">Sent</span>
              </li>
            ))}
          </ul>
        ) : <p className="reports-empty">No scheduled report emails have been sent yet.</p>}
      </div>
    </section>
  ) : (
    <section className="reports-card"><p className="reports-empty">You don’t have permission to manage scheduled reports.</p></section>
  );

  const renderUploadedLogAnalytics = () => {
    const selectedImport = logImports.find((item) => String(item.id) === logReportId);
    const summary = logReport?.summary;
    const resolutionSlaRate = summary?.resolutionSlaEligible
      ? Math.round((summary.resolutionSlaMet / summary.resolutionSlaEligible) * 100)
      : null;
    const breakdowns = summary ? [
      ["Tickets by assignee", summary.byAssignee],
      ["Tickets closed by", summary.byClosedBy],
      ["Ticket status", summary.byStatus],
      ["Priority", summary.byPriority],
      ["Source", summary.bySource],
      ["Issue type", summary.byIssueType],
      ["Plan", summary.byPlan],
      ["Topic", summary.byTopic],
      ["Category", summary.byCategory],
      ["Subcategory", summary.bySubcategory],
      ["Requester role", summary.byRequesterRole],
      ["Satisfaction rating", summary.bySatisfactionRating],
    ] : [];

    return (
      <div className="reports-uploaded-log">
        <section className="reports-card reports-log-selector-card">
          <div className="reports-card__heading">
            <div><h2>Uploaded workbook analytics</h2><p>Review saved ticket-log summaries and charts by project, import, and creation month.</p></div>
            <span className="reports-log-source-chip">Project analytics data</span>
          </div>
          <div className="reports-log-selectors">
            <label>
              <span>Project</span>
              <select className="form-select" value={logProjectId} onChange={(event) => {
                setLogReportId("");
                setLogMonth("");
                setLogMonths([]);
                setLogProjectId(event.target.value);
              }} disabled={loadingLogProjects || !logProjects.length}>
                {logProjects.map((project) => <option value={project.id} key={project.id}>{project.name}</option>)}
              </select>
            </label>
            <label>
              <span>Uploaded workbook</span>
              <select className="form-select" value={logReportId} onChange={(event) => {
                setLogMonth("");
                setLogMonths([]);
                setLogReportId(event.target.value);
              }} disabled={loadingLogImports || !logImports.length}>
                {logImports.map((item) => (
                  <option value={item.id} key={item.id}>
                    {item.fileName} · {formatDate(item.uploadedAt)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>Creation month</span>
              <select className="form-select" value={logMonth} onChange={(event) => setLogMonth(event.target.value)} disabled={!logMonths.length || loadingLogReport}>
                <option value="">All months</option>
                {logMonths.map((month) => <option value={month} key={month}>{formatMonthLabel(month)}</option>)}
              </select>
            </label>
          </div>
          {logAnalyticsError && <p className="reports-message reports-message--error" role="alert">{logAnalyticsError}</p>}
        </section>

        {loadingLogProjects ? (
          <section className="reports-card"><p className="reports-empty">Loading accessible projects...</p></section>
        ) : !logProjects.length ? (
          <section className="reports-card"><p className="reports-empty">No accessible projects are available for uploaded-log analytics.</p></section>
        ) : loadingLogImports ? (
          <section className="reports-card"><p className="reports-empty">Loading uploaded workbooks...</p></section>
        ) : !logImports.length ? (
          <section className="reports-card"><p className="reports-empty">No workbooks have been imported for this project yet. Upload a workbook in Project Analytics to see its summaries here.</p></section>
        ) : loadingLogReport ? (
          <section className="reports-card"><p className="reports-empty">Loading workbook analytics...</p></section>
        ) : summary ? (
          <>
            <div className="reports-log-import-meta">
              <span><strong>File</strong>{selectedImport?.fileName}</span>
              <span><strong>Worksheet</strong>{selectedImport?.sheetName || "—"}</span>
              <span><strong>Log period</strong>{logMonth ? formatMonthLabel(logMonth) : `${formatDate(selectedImport?.periodStart)} – ${formatDate(selectedImport?.periodEnd)}`}</span>
              <span><strong>Imported</strong>{formatDateTime(selectedImport?.uploadedAt)} · {selectedImport?.uploadedBy || "Unknown"}</span>
            </div>

            <section className="reports-kpis reports-log-kpis" aria-label="Uploaded workbook summary">
              <article className="reports-kpi reports-kpi--blue"><span>Ticket rows</span><strong>{summary.totalTickets.toLocaleString()}</strong><small>Imported records</small></article>
              <article className="reports-kpi reports-kpi--purple"><span>Unique tickets</span><strong>{summary.uniqueTickets.toLocaleString()}</strong><small>Distinct ticket IDs</small></article>
              <article className="reports-kpi reports-kpi--green"><span>Resolved</span><strong>{summary.resolvedTickets.toLocaleString()}</strong><small>Resolved or closed</small></article>
              <article className="reports-kpi reports-kpi--orange"><span>Open</span><strong>{summary.openTickets.toLocaleString()}</strong><small>Not marked resolved</small></article>
              <article className="reports-kpi reports-kpi--orange"><span>Escalated</span><strong>{summary.escalatedTickets.toLocaleString()}</strong><small>Flagged in the workbook</small></article>
              <article className="reports-kpi reports-kpi--blue"><span>Avg. resolution</span><strong>{summary.averageResolutionHours == null ? "N/A" : `${summary.averageResolutionHours}h`}</strong><small>Resolved rows with duration</small></article>
              <article className="reports-kpi reports-kpi--green"><span>Resolution SLA</span><strong>{resolutionSlaRate == null ? "N/A" : `${resolutionSlaRate}%`}</strong><small>{summary.resolutionSlaMet} of {summary.resolutionSlaEligible} eligible · {summary.resolutionSlaUnscored} unscored</small></article>
            </section>

            <MonthlyLogTrendChart data={summary.trends || []} />

            <section className="reports-log-breakdowns" aria-label="Uploaded workbook breakdown charts">
              {breakdowns.map(([title, items]) => <MonthlyLogBreakdown key={title} title={title} items={items || []} />)}
            </section>

            <section className="reports-card reports-log-notes">
              <div className="reports-card__heading"><div><h2>How to read these metrics</h2><p>Workbook fields limit which operational measures can be calculated.</p></div></div>
              <ul>
                <li><strong>Response SLA:</strong> {summary.responseSlaNote}</li>
                <li><strong>Resolution SLA:</strong> {summary.resolutionSlaNote}</li>
                <li><strong>Work breakdown:</strong> {summary.taskBreakdownNote}</li>
              </ul>
            </section>
          </>
        ) : !logAnalyticsError ? (
          <section className="reports-card"><p className="reports-empty">Select an uploaded workbook to view its analytics.</p></section>
        ) : null}
      </div>
    );
  };

  return (
    <div className="reports-page">
      <section className="reports-intro">
        <div>
          <p className="reports-eyebrow">CONNECT PROJECTS / ANALYTICS</p>
          <h2>Reports &amp; Analytics</h2>
          <p>Understand team performance and operational health at a glance.</p>
        </div>
        <div className="reports-toolbar">
          <label>
            <span>Report period</span>
            <select value={period} onChange={(event) => setPeriod(event.target.value)}>
              <option value="daily">Daily</option>
              <option value="weekly">Weekly</option>
              <option value="monthly">Monthly</option>
            </select>
          </label>
          <label>
            <span>Reference date</span>
            <input type="date" value={referenceDate} onChange={(event) => setReferenceDate(event.target.value)} />
          </label>
          {canExport && (
            <div className="reports-export-actions" aria-label="Export report">
              <button className="btn" onClick={() => exportReport("excel")}>Excel</button>
              <button className="btn" onClick={() => exportReport("pdf")}>PDF</button>
              <button className="btn btn--primary" onClick={() => exportReport("csv")}>CSV</button>
            </div>
          )}
        </div>
      </section>

      <div className="reports-tabs" role="tablist" aria-label="Report sections">
        {REPORT_TABS.filter((tab) => (tab !== "Scheduled Reports" || canSchedule)
          && (tab !== "Uploaded Log Analytics" || canViewProjectLogs)).map((tab) => (
          <button key={tab} type="button" role="tab" aria-selected={activeTab === tab} className={activeTab === tab ? "is-active" : ""} onClick={() => setActiveTab(tab)}>{tab}</button>
        ))}
      </div>

      {error && <p className="reports-message reports-message--error" role="alert">{error}</p>}
      {notice && <p className="reports-message reports-message--success" role="status">{notice}</p>}
      {loading ? (
        <section className="reports-card"><p className="reports-empty">Loading ticket reports...</p></section>
      ) : (
        <>
          <p className="reports-period">{periodLabel}</p>
          {activeTab === "Overview" && (
            <>
              <section className="reports-kpis" aria-label="Report summary">
                <article className="reports-kpi reports-kpi--purple"><span>Average resolved / employee</span><strong>{resolvedPerEmployee}</strong><small>Live tickets during this period</small></article>
                <article className="reports-kpi reports-kpi--green"><span>Response SLA</span><strong>{rateLabel(metrics.responseCompliant, metrics.responseEligible)}</strong><small>{metrics.responseEligible} measured tickets</small></article>
                <article className="reports-kpi reports-kpi--orange"><span>Pending tickets</span><strong>{pendingTickets.length}</strong><small>Current open backlog</small></article>
                <article className="reports-kpi reports-kpi--blue"><span>Average ageing</span><strong>{averageAge} days</strong><small>Across pending tickets</small></article>
              </section>
              {canViewProjectLogs && (
                <section className="reports-card reports-resolved-employee-card">
                  <div className="reports-card__heading">
                    <div>
                      <h2>Uploaded log · tickets resolved by employee</h2>
                      <p>
                        {overviewLogProject?.name || "Selected project"}
                        {overviewLogImport ? ` · ${overviewLogImport.fileName} · ${logMonth ? formatMonthLabel(logMonth) : `${formatDate(overviewLogImport.periodStart)} – ${formatDate(overviewLogImport.periodEnd)}`}` : " · Latest imported workbook"}
                      </p>
                    </div>
                    <button className="btn btn-sm btn-outline-secondary" type="button" onClick={() => setActiveTab("Uploaded Log Analytics")}>View log analytics</button>
                  </div>
                  {loadingLogProjects || loadingLogImports || loadingLogReport ? (
                    <p className="reports-empty">Loading resolved ticket details...</p>
                  ) : logAnalyticsError ? (
                    <p className="reports-message reports-message--error" role="alert">{logAnalyticsError}</p>
                  ) : logReport?.summary ? (
                    <ResolvedByEmployeeChart data={logReport.summary.byResolvedAssignee || []} />
                  ) : (
                    <p className="reports-empty">Import a workbook in Project Analytics to see resolved tickets by employee here.</p>
                  )}
                </section>
              )}
              <div className="reports-charts-grid reports-overview-grid">
                <section className="reports-card reports-chart-panel reports-chart-panel--wide">
                  <div className="reports-card__heading"><div><h2>Ticket activity</h2><p>{chartDescription}</p></div></div>
                  <TicketTrendChart data={trendBuckets} />
                </section>
                <section className="reports-card reports-chart-panel">
                  <div className="reports-card__heading"><div><h2>Ticket status</h2><p>Current ticket distribution.</p></div></div>
                  {statusBuckets.length ? <TicketStatusChart data={statusBuckets} /> : <p className="reports-empty">No tickets are available for this account.</p>}
                </section>
                <section className="reports-card reports-chart-panel">
                  <div className="reports-card__heading"><div><h2>Ticket ageing</h2><p>Open tickets grouped by age.</p></div></div>
                  <AgeingChart buckets={ageingBuckets} />
                </section>
                <section className="reports-card reports-quick-actions">
                  <div className="reports-card__heading"><div><h2>Quick actions</h2><p>Common reporting tasks.</p></div></div>
                  {canExport && <button type="button" onClick={() => exportReport("excel")}><strong>Export spreadsheet</strong><small>Excel-compatible report</small><span aria-hidden="true">›</span></button>}
                  {canExport && <button type="button" onClick={() => exportReport("pdf")}><strong>Print / save as PDF</strong><small>Export current report</small><span aria-hidden="true">›</span></button>}
                  {canSchedule && <button type="button" onClick={() => setActiveTab("Scheduled Reports")}><strong>Schedule reports</strong><small>Automated email delivery</small><span aria-hidden="true">›</span></button>}
                </section>
              </div>
            </>
          )}
          {activeTab === "Uploaded Log Analytics" && renderUploadedLogAnalytics()}
          {activeTab === "Employee Productivity" && renderEmployeeReport()}
          {activeTab === "SLA & Ticket Reports" && renderSlaReport()}
          {activeTab === "Pending & ageing" && renderPendingReport()}
          {activeTab === "Scheduled Reports" && renderScheduleReport()}
        </>
      )}
    </div>
  );
}
