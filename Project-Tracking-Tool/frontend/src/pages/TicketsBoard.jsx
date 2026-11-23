import { useEffect, useMemo, useState } from "react";
import { ArrowUpRight, CheckCheck, CheckCircle2, Clock, Eye, Hourglass, Inbox, Lock, PauseCircle, Plus, RefreshCw, Search, Siren, Trash2, UserX } from "lucide-react";
import { assignTicket, closeTicket, createTicket, deleteTicket, getAssignees, getTickets, resolveTicket, updateTicket } from "../auth/authService.js";
import { Avatar, Drawer, EmptyState, Modal, Notice } from "../components/ui.jsx";
import "./Workspace-modern.css";

const PRIORITIES = ["Low", "Medium", "High", "Critical"];
const OPEN_STATUSES = ["Open", "In Progress", "Pending"];
const FINISHED = ["Resolved", "Closed"];
const HOUR = 3600000;
const DAY = 24 * HOUR;
const EMPTY_FORM = { subject: "", description: "", category: "General", priority: "Medium", assigneeId: "" };
const AGE_BUCKETS = [
  { key: "lt1", label: "Under 24 hours", test: (age) => age < DAY },
  { key: "d1_3", label: "1 – 3 days", test: (age) => age >= DAY && age < 3 * DAY },
  { key: "d3_7", label: "3 – 7 days", test: (age) => age >= 3 * DAY && age < 7 * DAY },
  { key: "gt7", label: "Over 7 days", test: (age) => age >= 7 * DAY },
];

function allowed(user, permission) {
  return user.role === "Super User" || (user.permissions || []).includes(permission);
}
function span(ms) {
  const total = Math.max(0, Math.round(ms / 60000));
  if (total < 1) return "<1m";
  const days = Math.floor(total / 1440);
  const hours = Math.floor((total % 1440) / 60);
  const minutes = total % 60;
  if (days) return `${days}d ${hours}h`;
  if (hours) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}
const when = (iso) => (iso ? new Date(iso).toLocaleString(undefined, { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—");

function slaState(dueIso, doneIso, now, finishedAtIso) {
  const due = new Date(dueIso).getTime();
  if (Number.isNaN(due)) return { tone: "none", label: "—", breached: false };
  const done = doneIso || finishedAtIso;
  if (done) {
    const ok = new Date(done).getTime() <= due;
    return { tone: ok ? "ok" : "bad", label: ok ? "Met" : "Missed", breached: !ok };
  }
  if (now > due) return { tone: "bad", label: `${span(now - due)} overdue`, breached: true };
  const left = due - now;
  return { tone: left < 4 * HOUR ? "warn" : "ok", label: `${span(left)} left`, breached: false };
}

function describe(ticket, now) {
  const created = new Date(ticket.createdAt).getTime();
  const finished = FINISHED.includes(ticket.status);
  const endIso = ticket.closedAt || ticket.resolvedAt;
  const age = (finished && endIso ? new Date(endIso).getTime() : now) - created;
  const response = slaState(ticket.responseDueAt, ticket.firstResponseAt, now, finished ? ticket.firstResponseAt || ticket.resolvedAt : null);
  const resolution = slaState(ticket.resolutionDueAt, ticket.resolvedAt, now, ticket.status === "Closed" ? ticket.closedAt : null);
  const needsEscalation = !finished && (response.breached || resolution.breached || age >= 7 * DAY);
  const bucket = AGE_BUCKETS.find((item) => item.test(age))?.key;
  return { age, finished, response, resolution, needsEscalation, bucket };
}

export default function TicketsBoard({ user }) {
  const [tickets, setTickets] = useState([]);
  const [people, setPeople] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [now, setNow] = useState(() => Date.now());
  const [view, setView] = useState("all");
  const [ageFilter, setAgeFilter] = useState("all");
  const [priorityFilter, setPriorityFilter] = useState("all");
  const [assigneeFilter, setAssigneeFilter] = useState("all");
  const [categoryFilter, setCategoryFilter] = useState("all");
  const [query, setQuery] = useState("");
  const [createForm, setCreateForm] = useState(null);
  const [detailId, setDetailId] = useState("");
  const [escalation, setEscalation] = useState(null);

  const canCreate = allowed(user, "tickets.create");
  const canAssign = allowed(user, "tickets.assign");
  const canUpdate = allowed(user, "tickets.update");
  const canResolve = allowed(user, "tickets.resolve");
  const canClose = allowed(user, "tickets.close");
  const canDelete = allowed(user, "tickets.delete");

  async function load(silent = false) {
    if (!silent) setLoading(true);
    setError("");
    try {
      const [rows, peopleRows] = await Promise.all([getTickets(), canAssign ? getAssignees().catch(() => []) : Promise.resolve([])]);
      setTickets(rows);
      setPeople(peopleRows);
      setNow(Date.now());
    } catch (requestError) {
      setError(requestError.message || "Could not load tickets.");
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60000);
    return () => clearInterval(timer);
  }, []);
  useEffect(() => {
    if (!notice) return undefined;
    const timer = setTimeout(() => setNotice(""), 4000);
    return () => clearTimeout(timer);
  }, [notice]);

  const rows = useMemo(() => tickets.map((ticket) => ({ ticket, info: describe(ticket, now) })), [tickets, now]);
  const counts = useMemo(() => ({
    all: rows.length,
    open: rows.filter(({ ticket }) => ticket.status === "Open").length,
    progress: rows.filter(({ ticket }) => ticket.status === "In Progress").length,
    pending: rows.filter(({ ticket }) => ticket.status === "Pending").length,
    escalate: rows.filter(({ info }) => info.needsEscalation).length,
    unassigned: rows.filter(({ ticket, info }) => !ticket.assigneeId && !info.finished).length,
    resolved: rows.filter(({ ticket }) => ticket.status === "Resolved").length,
    closed: rows.filter(({ ticket }) => ticket.status === "Closed").length,
  }), [rows]);
  const ageing = useMemo(() => AGE_BUCKETS.map((bucket) => ({ ...bucket, count: rows.filter(({ info }) => !info.finished && info.bucket === bucket.key).length })), [rows]);
  const maxAge = Math.max(1, ...ageing.map((item) => item.count));
  const categories = useMemo(() => [...new Set(tickets.map((ticket) => ticket.category).filter(Boolean))].sort(), [tickets]);

  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return rows
      .filter(({ ticket, info }) => {
        if (view === "open" && ticket.status !== "Open") return false;
        if (view === "progress" && ticket.status !== "In Progress") return false;
        if (view === "pending" && ticket.status !== "Pending") return false;
        if (view === "escalate" && !info.needsEscalation) return false;
        if (view === "unassigned" && (ticket.assigneeId || info.finished)) return false;
        if (view === "resolved" && ticket.status !== "Resolved") return false;
        if (view === "closed" && ticket.status !== "Closed") return false;
        if (ageFilter !== "all" && (info.finished || info.bucket !== ageFilter)) return false;
        if (priorityFilter !== "all" && ticket.priority !== priorityFilter) return false;
        if (assigneeFilter === "none" && ticket.assigneeId) return false;
        if (assigneeFilter !== "all" && assigneeFilter !== "none" && String(ticket.assigneeId) !== assigneeFilter) return false;
        if (categoryFilter !== "all" && ticket.category !== categoryFilter) return false;
        if (needle && !`${ticket.ticketNumber} ${ticket.subject} ${ticket.category} ${ticket.assignee}`.toLowerCase().includes(needle)) return false;
        return true;
      })
      .sort((a, b) => a.info.finished - b.info.finished || b.info.needsEscalation - a.info.needsEscalation || b.info.age - a.info.age);
  }, [rows, view, ageFilter, priorityFilter, assigneeFilter, categoryFilter, query]);

  const detail = rows.find(({ ticket }) => String(ticket.id) === String(detailId));

  async function run(action, success) {
    setSaving(true);
    setError("");
    try {
      await action();
      setNotice(success);
      await load(true);
      return true;
    } catch (requestError) {
      setError(requestError.message || "Could not complete that action.");
      return false;
    } finally {
      setSaving(false);
    }
  }
  const assign = (ticket, assigneeId) => assigneeId && run(() => assignTicket(ticket.id, Number(assigneeId)), `${ticket.ticketNumber} assigned to ${people.find((p) => String(p.id) === String(assigneeId))?.name || "the selected person"}.`);
  const setStatus = (ticket, status) => run(() => updateTicket(ticket.id, { status }), `${ticket.ticketNumber} marked ${status}.`);
  const resolve = (ticket) => run(() => resolveTicket(ticket.id), `${ticket.ticketNumber} resolved.`);
  const close = (ticket) => run(() => closeTicket(ticket.id), `${ticket.ticketNumber} closed.`);
  async function remove(ticket) {
    if (!window.confirm(`Delete ticket ${ticket.ticketNumber}? This cannot be undone.`)) return;
    if (await run(() => deleteTicket(ticket.id), `${ticket.ticketNumber} deleted.`)) setDetailId("");
  }

  function openEscalation(ticket) {
    const next = PRIORITIES[Math.min(PRIORITIES.length - 1, PRIORITIES.indexOf(ticket.priority) + 1)];
    setEscalation({ ticket, assigneeId: "", raise: ticket.priority !== "Critical" && canUpdate, nextPriority: next });
  }
  async function confirmEscalation(event) {
    event.preventDefault();
    const { ticket, assigneeId, raise, nextPriority } = escalation;
    const ok = await run(async () => {
      if (raise && nextPriority !== ticket.priority) {
        await updateTicket(ticket.id, { priority: nextPriority });
      }
      if (assigneeId) {
        await assignTicket(ticket.id, Number(assigneeId));
      }
    }, `${ticket.ticketNumber} escalated.`);
    if (ok) setEscalation(null);
  }
  async function submitCreate(event) {
    event.preventDefault();
    const ok = await run(() => createTicket({
      ...createForm,
      subject: createForm.subject.trim(),
      category: createForm.category.trim() || "General",
      assigneeId: canAssign && createForm.assigneeId ? Number(createForm.assigneeId) : null,
    }), "Ticket created.");
    if (ok) setCreateForm(null);
  }

  const cards = [
    ["all", "All tickets", counts.all, Inbox, ""],
    ["open", "Open", counts.open, Clock, ""],
    ["progress", "In progress", counts.progress, Hourglass, "warn"],
    ["pending", "Pending", counts.pending, PauseCircle, "warn"],
    ["escalate", "Needs escalation", counts.escalate, Siren, counts.escalate ? "bad" : ""],
    ["unassigned", "Unassigned", counts.unassigned, UserX, counts.unassigned ? "warn" : ""],
    ["resolved", "Resolved", counts.resolved, CheckCircle2, "ok"],
    ["closed", "Closed", counts.closed, Lock, "muted"],
  ];
  const filtersOn = ageFilter !== "all" || priorityFilter !== "all" || assigneeFilter !== "all" || categoryFilter !== "all" || query.trim() || view !== "all";
  const clearFilters = () => { setView("all"); setAgeFilter("all"); setPriorityFilter("all"); setAssigneeFilter("all"); setCategoryFilter("all"); setQuery(""); };

  function actionButtons(ticket, info, compact) {
    const cls = compact ? "wk-icon-btn" : "wk-btn wk-btn--sm";
    const label = (text) => (compact ? null : text);
    return (
      <>
        {!info.finished && (canAssign || canUpdate) && <button type="button" className={`${cls} wk-icon-btn--warn`} title="Escalate" aria-label={`Escalate ${ticket.ticketNumber}`} disabled={saving} onClick={() => openEscalation(ticket)}><ArrowUpRight size={15} />{label("Escalate")}</button>}
        {!info.finished && canResolve && <button type="button" className={cls} title="Resolve" aria-label={`Resolve ${ticket.ticketNumber}`} disabled={saving} onClick={() => resolve(ticket)}><CheckCircle2 size={15} />{label("Resolve")}</button>}
        {ticket.status === "Resolved" && canClose && <button type="button" className={cls} title="Close" aria-label={`Close ${ticket.ticketNumber}`} disabled={saving} onClick={() => close(ticket)}><CheckCheck size={15} />{label("Close")}</button>}
        {canDelete && <button type="button" className={`${cls} wk-icon-btn--danger`} title="Delete" aria-label={`Delete ${ticket.ticketNumber}`} disabled={saving} onClick={() => remove(ticket)}><Trash2 size={15} />{label("Delete")}</button>}
      </>
    );
  }

  const assigneeSelect = (ticket, id) => (canAssign ? (
    <select id={id} className="wk-select" aria-label={`Assignee for ${ticket.ticketNumber}`} value={ticket.assigneeId || ""} disabled={saving || FINISHED.includes(ticket.status)} onChange={(event) => assign(ticket, event.target.value)}>
      <option value="" disabled>Unassigned</option>
      {people.map((person) => <option key={person.id} value={person.id}>{person.name}</option>)}
    </select>
  ) : <span>{ticket.assignee || "Unassigned"}</span>);
  const statusControl = (ticket) => (canUpdate && !FINISHED.includes(ticket.status) ? (
    <select className={`wk-select wk-select--t-${ticket.status.replace(" ", "").toLowerCase()}`} aria-label={`Status for ${ticket.ticketNumber}`} value={ticket.status} disabled={saving} onChange={(event) => setStatus(ticket, event.target.value)}>
      {OPEN_STATUSES.map((status) => <option key={status}>{status}</option>)}
    </select>
  ) : <span className={`wk-pill wk-pill--t-${ticket.status.replace(" ", "").toLowerCase()}`}>{ticket.status}</span>);

  return (
    <section className="wk" aria-label="Ticket management">
      <header className="wk-head">
        <div>
          <span className="wk-eyebrow">Workspace / Ticket management</span>
          <h2>Ticket management</h2>
          <p>Monitor every ticket, track ageing and SLAs, assign or reassign owners, and escalate what is stuck.</p>
        </div>
        <div className="wk-head__actions">
          <button type="button" className="wk-btn" onClick={() => load()} disabled={loading}><RefreshCw size={15} /> Refresh</button>
          {canCreate && <button type="button" className="wk-btn wk-btn--primary" onClick={() => setCreateForm({ ...EMPTY_FORM })}><Plus size={15} /> New ticket</button>}
        </div>
      </header>

      <Notice error={error} notice={notice} onDismiss={() => { setError(""); setNotice(""); }} />

      <div className="wk-statgrid" role="tablist" aria-label="Ticket views">
        {cards.map(([key, label, count, Icon, tone]) => (
          <button type="button" role="tab" aria-selected={view === key} key={key} className={`wk-stat${view === key ? " is-on" : ""}${tone ? ` wk-stat--${tone}` : ""}`} onClick={() => setView(key)}>
            <span className="wk-stat__icon"><Icon size={17} /></span><strong>{count}</strong><small>{label}</small>
          </button>
        ))}
      </div>

      <div className="wk-ageing" aria-label="Ticket ageing">
        <div className="wk-ageing__title"><strong>Ticket ageing</strong><small>Unresolved tickets by time since creation</small></div>
        <div className="wk-ageing__bars">
          {ageing.map((bucket) => (
            <button type="button" key={bucket.key} className={`wk-bar wk-bar--${bucket.key}${ageFilter === bucket.key ? " is-on" : ""}`} aria-pressed={ageFilter === bucket.key} onClick={() => setAgeFilter(ageFilter === bucket.key ? "all" : bucket.key)}>
              <span className="wk-bar__label">{bucket.label}</span>
              <span className="wk-bar__track"><i style={{ width: `${(bucket.count / maxAge) * 100}%` }} /></span>
              <strong>{bucket.count}</strong>
            </button>
          ))}
        </div>
      </div>

      <div className="wk-toolbar">
        <label className="wk-search"><Search size={16} aria-hidden="true" /><span className="sr-only">Search tickets</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search ticket number, subject, category or owner" /></label>
        <label className="wk-filter"><span className="sr-only">Priority</span><select value={priorityFilter} onChange={(event) => setPriorityFilter(event.target.value)}><option value="all">All priorities</option>{PRIORITIES.map((item) => <option key={item}>{item}</option>)}</select></label>
        <label className="wk-filter"><span className="sr-only">Category</span><select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}><option value="all">All categories</option>{categories.map((item) => <option key={item}>{item}</option>)}</select></label>
        {canAssign && <label className="wk-filter"><span className="sr-only">Assignee</span><select value={assigneeFilter} onChange={(event) => setAssigneeFilter(event.target.value)}><option value="all">All owners</option><option value="none">Unassigned</option>{people.map((person) => <option key={person.id} value={String(person.id)}>{person.name}</option>)}</select></label>}
        {filtersOn && <button type="button" className="wk-btn wk-btn--sm" onClick={clearFilters}>Clear filters</button>}
      </div>

      {loading && !tickets.length ? <p className="wk-muted">Loading tickets...</p> : !visible.length ? (
        <EmptyState icon={Inbox} title={tickets.length ? "No tickets match this view" : "No tickets yet"} text={tickets.length ? "Change the view or clear your filters." : "Tickets raised for your projects will show up here."}
          action={filtersOn ? <button type="button" className="wk-btn" onClick={clearFilters}>Clear filters</button> : canCreate && <button type="button" className="wk-btn wk-btn--primary" onClick={() => setCreateForm({ ...EMPTY_FORM })}><Plus size={15} /> New ticket</button>} />
      ) : (
        <div className="wk-tablecard">
          <div className="wk-tablewrap">
            <table className="wk-table">
              <thead><tr><th>Ticket</th><th>Priority</th><th>Owner</th><th>Age</th><th>Response SLA</th><th>Resolution SLA</th><th>Status</th><th className="wk-right">Actions</th></tr></thead>
              <tbody>
                {visible.map(({ ticket, info }) => (
                  <tr key={ticket.id} className={info.needsEscalation ? "is-hot" : ""}>
                    <td data-label="Ticket">
                      <button type="button" className="wk-link" onClick={() => setDetailId(String(ticket.id))}>{ticket.subject}</button>
                      <small>{ticket.ticketNumber} · {ticket.category || "General"}</small>
                    </td>
                    <td data-label="Priority"><span className={`wk-pill wk-pill--p-${ticket.priority.toLowerCase()}`}>{ticket.priority}</span></td>
                    <td data-label="Owner"><span className="wk-owner">{assigneeSelect(ticket, `own-${ticket.id}`)}</span></td>
                    <td data-label="Age" className="wk-nowrap">
                      {info.finished ? <span className="wk-age wk-age--done" title={`Created ${when(ticket.createdAt)}`}>{ticket.status} in {span(info.age)}</span>
                        : <span className={`wk-age wk-age--${info.bucket}`} title={`Created ${when(ticket.createdAt)}`}>{span(info.age)}</span>}
                    </td>
                    <td data-label="Response SLA" className="wk-nowrap"><span className={`wk-sla wk-sla--${info.response.tone}`}>{info.response.label}</span></td>
                    <td data-label="Resolution SLA" className="wk-nowrap"><span className={`wk-sla wk-sla--${info.resolution.tone}`}>{info.resolution.label}</span></td>
                    <td data-label="Status">{statusControl(ticket)}</td>
                    <td data-label="Actions" className="wk-right wk-nowrap">
                      <button type="button" className="wk-icon-btn" title="View details" aria-label={`View ${ticket.ticketNumber}`} onClick={() => setDetailId(String(ticket.id))}><Eye size={15} /></button>
                      {actionButtons(ticket, info, true)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <footer className="wk-tablefoot">Showing {visible.length} of {tickets.length} tickets · ages update every minute</footer>
        </div>
      )}

      {detail && (
        <Drawer
          title={detail.ticket.subject}
          subtitle={`${detail.ticket.ticketNumber} · ${detail.ticket.category || "General"} · raised by ${detail.ticket.createdBy || "unknown"}`}
          badge={<span className={`wk-pill wk-pill--t-${detail.ticket.status.replace(" ", "").toLowerCase()}`}>{detail.ticket.status}</span>}
          onClose={() => setDetailId("")}
        >
          <div className="wk-detail">
            {detail.info.needsEscalation && <div className="wk-alert"><Siren size={16} /> This ticket has breached an SLA or been open for 7+ days. Consider escalating it.</div>}
            <div className="wk-card wk-detail__grid">
              <div><small>Priority</small><span className={`wk-pill wk-pill--p-${detail.ticket.priority.toLowerCase()}`}>{detail.ticket.priority}</span></div>
              <div><small>Status</small>{statusControl(detail.ticket)}</div>
              <div className="wk-detail__owner"><small>Owner</small><span className="wk-owner"><Avatar name={detail.ticket.assignee} size="sm" />{assigneeSelect(detail.ticket, `detail-owner-${detail.ticket.id}`)}</span></div>
              <div><small>{detail.info.finished ? "Time to close" : "Age"}</small><strong>{span(detail.info.age)}</strong></div>
              <div><small>Response SLA</small><span className={`wk-sla wk-sla--${detail.info.response.tone}`}>{detail.info.response.label}</span><em>Due {when(detail.ticket.responseDueAt)}</em></div>
              <div><small>Resolution SLA</small><span className={`wk-sla wk-sla--${detail.info.resolution.tone}`}>{detail.info.resolution.label}</span><em>Due {when(detail.ticket.resolutionDueAt)}</em></div>
            </div>
            {detail.ticket.description && <div className="wk-card"><h4>Description</h4><p className="wk-desc">{detail.ticket.description}</p></div>}
            <div className="wk-card">
              <h4>Lifecycle</h4>
              <dl className="wk-life">
                <div><dt>Created</dt><dd>{when(detail.ticket.createdAt)}</dd></div>
                <div><dt>First response</dt><dd>{when(detail.ticket.firstResponseAt)}</dd></div>
                <div><dt>Resolved</dt><dd>{when(detail.ticket.resolvedAt)}</dd></div>
                <div><dt>Closed</dt><dd>{when(detail.ticket.closedAt)}</dd></div>
              </dl>
            </div>
            <div className="wk-card">
              <h4>Activity</h4>
              {(detail.ticket.history || []).length ? (
                <ol className="wk-timeline">
                  {[...detail.ticket.history].sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp)).map((entry) => (
                    <li key={entry.id}><i /><div><strong>{entry.action}</strong><p>{entry.details}</p><small>{entry.user} · {when(entry.timestamp)}</small></div></li>
                  ))}
                </ol>
              ) : <p className="wk-muted">No activity recorded yet.</p>}
            </div>
            <div className="wk-actions-row">{actionButtons(detail.ticket, detail.info, false)}</div>
          </div>
        </Drawer>
      )}

      {escalation && (
        <Modal title="Escalate ticket" subtitle={`${escalation.ticket.ticketNumber} · ${escalation.ticket.subject}`} size="sm" onClose={() => setEscalation(null)}
          footer={<><button type="button" className="wk-btn" onClick={() => setEscalation(null)}>Cancel</button><button type="submit" form="wk-escalate-form" className="wk-btn wk-btn--primary" disabled={saving || (!escalation.assigneeId && !escalation.raise)}>{saving ? "Escalating..." : "Escalate"}</button></>}>
          <form id="wk-escalate-form" className="wk-form wk-form--single" onSubmit={confirmEscalation}>
            {canAssign && (
              <label className="wk-field"><span>Escalate to</span>
                <select value={escalation.assigneeId} onChange={(event) => setEscalation({ ...escalation, assigneeId: event.target.value })}>
                  <option value="">Keep current owner ({escalation.ticket.assignee || "unassigned"})</option>
                  {people.filter((person) => String(person.id) !== String(escalation.ticket.assigneeId)).map((person) => <option key={person.id} value={person.id}>{person.name}</option>)}
                </select>
              </label>
            )}
            {canUpdate && escalation.ticket.priority !== "Critical" && (
              <label className="wk-checkrow"><input type="checkbox" checked={escalation.raise} onChange={(event) => setEscalation({ ...escalation, raise: event.target.checked })} /> Raise priority from <b>{escalation.ticket.priority}</b> to <b>{escalation.nextPriority}</b></label>
            )}
            {escalation.ticket.priority === "Critical" && <p className="wk-muted">This ticket is already Critical, so only reassignment is available.</p>}
            <p className="wk-muted">Reassignment is recorded in the ticket's activity history.</p>
          </form>
        </Modal>
      )}

      {createForm && (
        <Modal title="New ticket" subtitle="Log a ticket and optionally assign it to an owner." size="md" onClose={() => setCreateForm(null)}
          footer={<><button type="button" className="wk-btn" onClick={() => setCreateForm(null)}>Cancel</button><button type="submit" form="wk-ticket-form" className="wk-btn wk-btn--primary" disabled={saving}>{saving ? "Creating..." : "Create ticket"}</button></>}>
          <form id="wk-ticket-form" className="wk-form" onSubmit={submitCreate}>
            <label className="wk-field wk-span-2"><span>Subject *</span><input required maxLength={240} value={createForm.subject} onChange={(event) => setCreateForm({ ...createForm, subject: event.target.value })} /></label>
            <label className="wk-field"><span>Category</span><input list="wk-categories" maxLength={100} value={createForm.category} onChange={(event) => setCreateForm({ ...createForm, category: event.target.value })} /><datalist id="wk-categories">{categories.map((item) => <option key={item} value={item} />)}</datalist></label>
            <label className="wk-field"><span>Priority</span><select value={createForm.priority} onChange={(event) => setCreateForm({ ...createForm, priority: event.target.value })}>{PRIORITIES.map((item) => <option key={item}>{item}</option>)}</select></label>
            {canAssign && <label className="wk-field wk-span-2"><span>Assign to</span><select value={createForm.assigneeId} onChange={(event) => setCreateForm({ ...createForm, assigneeId: event.target.value })}><option value="">Unassigned</option>{people.map((person) => <option key={person.id} value={person.id}>{person.name}</option>)}</select></label>}
            <label className="wk-field wk-span-2"><span>Description</span><textarea rows="4" maxLength={5000} value={createForm.description} onChange={(event) => setCreateForm({ ...createForm, description: event.target.value })} /></label>
          </form>
        </Modal>
      )}
    </section>
  );
}
