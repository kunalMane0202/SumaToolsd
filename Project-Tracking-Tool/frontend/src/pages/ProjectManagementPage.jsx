import { useEffect, useMemo, useState } from "react";
import {
  Building2, CalendarDays, Clock, FolderKanban, Layers, Pencil, Plus, Power, RefreshCw,
  Search, ShieldCheck, Tags, Timer, Trash2, TriangleAlert, Flag,
} from "lucide-react";
import {
  addProjectSubcategory,
  createProject,
  createProjectConfiguration,
  createProjectManagementClient,
  deleteProjectConfiguration,
  getProjectConfiguration,
  getProjectManagementClients,
  getProjectManagementLeads,
  getProjects,
  saveProjectSla,
  setProjectActive,
  updateProject,
} from "../auth/authService.js";
import { Avatar, Drawer, EmptyState, Modal, Notice, Toggle } from "../components/ui.jsx";
import "./ProjectManagementPage.css";

const DAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
const STATUSES = ["On track", "At risk", "Delayed", "Completed"];
const STATUS_TONE = { "On track": "ok", "At risk": "warn", Delayed: "bad", Completed: "done" };
const STANDARD_PRIORITIES = [
  { name: "Critical", level: 1, color: "#C62828" },
  { name: "High", level: 2, color: "#E65100" },
  { name: "Medium", level: 3, color: "#F2A900" },
  { name: "Low", level: 4, color: "#2E7D32" },
];
const EMPTY_CONFIG = { shifts: [], priorities: [], categories: [], slas: [] };
const EMPTY_PROJECT = { name: "", description: "", status: "On track", due: "", clientId: "", projectManagerId: "", teamLeaderIds: [], active: true };
const NO_CLIENT = "none";
const DRAWER_TABS = [
  ["overview", "Overview", FolderKanban],
  ["shifts", "Support hours", Clock],
  ["priorities", "Priorities", Flag],
  ["categories", "Categories", Tags],
  ["slas", "SLAs", Timer],
];

function allowed(user, permission) {
  return user.role === "Super User" || (user.permissions || []).includes(permission);
}
const hhmm = (value) => (value ? String(value).slice(0, 5) : "");
function minutesLabel(total) {
  const minutes = Number(total);
  if (!minutes || minutes < 1) return "";
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  const rest = minutes % 60;
  return [days && `${days}d`, hours && `${hours}h`, rest && `${rest}m`].filter(Boolean).join(" ");
}
function formatDue(due) {
  if (!due) return "No due date";
  const date = new Date(`${due}T00:00:00`);
  return Number.isNaN(date.getTime()) ? due : date.toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" });
}
function managerName(project) {
  return project.projectManager?.name || (project.lead && project.lead !== "Unassigned" ? project.lead : "");
}

function StatusPill({ status }) {
  return <span className={`pm-pill pm-pill--${STATUS_TONE[status] || "muted"}`}>{status || "Not set"}</span>;
}

function AvatarStack({ names, max = 3 }) {
  if (!names.length) return <span className="pm-muted">—</span>;
  return (
    <span className="pm-stack">
      {names.slice(0, max).map((name) => <Avatar key={name} name={name} size="sm" />)}
      {names.length > max && <span className="pm-stack__more">+{names.length - max}</span>}
    </span>
  );
}

export default function ProjectManagementPage({ user }) {
  const [projects, setProjects] = useState([]);
  const [clients, setClients] = useState([]);
  const [leads, setLeads] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [query, setQuery] = useState("");
  const [clientFilter, setClientFilter] = useState("all");
  const [statusFilter, setStatusFilter] = useState("all");
  const [activeFilter, setActiveFilter] = useState("all");
  const [projectDialog, setProjectDialog] = useState(null); // { mode, project?, form }
  const [clientDialog, setClientDialog] = useState(null);
  const [drawerId, setDrawerId] = useState("");
  const [drawerTab, setDrawerTab] = useState("overview");
  const [config, setConfig] = useState(EMPTY_CONFIG);
  const [configLoading, setConfigLoading] = useState(false);

  const canCreate = allowed(user, "projects.create");
  const canEdit = allowed(user, "projects.edit");
  const isLead = user.role === "Project Lead";
  const drawerProject = projects.find((item) => String(item.id) === String(drawerId));

  async function loadAll() {
    setLoading(true);
    setError("");
    try {
      const [projectRows, clientRows, leadRows] = await Promise.all([getProjects(), getProjectManagementClients(), getProjectManagementLeads()]);
      setProjects(projectRows);
      setClients(clientRows);
      setLeads(leadRows);
    } catch (requestError) {
      setError(requestError.message || "Could not load project setup.");
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { loadAll(); }, []);

  useEffect(() => {
    if (!notice) return undefined;
    const timer = setTimeout(() => setNotice(""), 4000);
    return () => clearTimeout(timer);
  }, [notice]);

  async function loadConfig(projectId) {
    setConfigLoading(true);
    try {
      setConfig(await getProjectConfiguration(projectId));
      return true;
    } catch (requestError) {
      setError(requestError.message || "Could not load project configuration.");
      return false;
    } finally {
      setConfigLoading(false);
    }
  }
  useEffect(() => {
    if (drawerId) loadConfig(drawerId);
    else setConfig(EMPTY_CONFIG);
  }, [drawerId]);

  /* ---------- derived data ---------- */
  const metrics = useMemo(() => ({
    total: projects.length,
    active: projects.filter((item) => item.active !== false && item.status !== "Completed").length,
    risk: projects.filter((item) => ["At risk", "Delayed"].includes(item.status)).length,
    inactive: projects.filter((item) => item.active === false).length,
    clients: clients.length,
  }), [projects, clients]);

  const filtersOn = Boolean(query.trim()) || clientFilter !== "all" || statusFilter !== "all" || activeFilter !== "all";
  const groups = useMemo(() => {
    const needle = query.trim().toLowerCase();
    const matches = (item) => {
      const text = `${item.name} ${item.client?.name || ""} ${managerName(item)} ${item.description || ""}`.toLowerCase();
      if (needle && !text.includes(needle)) return false;
      if (statusFilter !== "all" && item.status !== statusFilter) return false;
      if (activeFilter === "active" && item.active === false) return false;
      if (activeFilter === "inactive" && item.active !== false) return false;
      return true;
    };
    const byClient = new Map();
    clients.forEach((client) => byClient.set(String(client.id), { key: String(client.id), client, items: [] }));
    byClient.set(NO_CLIENT, { key: NO_CLIENT, client: null, items: [] });
    projects.forEach((item) => {
      const key = item.client?.id ? String(item.client.id) : NO_CLIENT;
      if (!byClient.has(key)) byClient.set(key, { key, client: item.client, items: [] });
      if (matches(item)) byClient.get(key).items.push(item);
    });
    return [...byClient.values()]
      .filter((group) => (clientFilter === "all" || group.key === clientFilter))
      .filter((group) => group.items.length || (!filtersOn && group.client))
      .sort((a, b) => (a.client ? 0 : 1) - (b.client ? 0 : 1) || (a.client?.name || "").localeCompare(b.client?.name || ""));
  }, [projects, clients, query, clientFilter, statusFilter, activeFilter, filtersOn]);

  /* ---------- project create / edit ---------- */
  function openCreate(clientId = "") {
    setProjectDialog({ mode: "create", form: { ...EMPTY_PROJECT, clientId: clientId ? String(clientId) : "" } });
  }
  function openEdit(project) {
    setProjectDialog({
      mode: "edit",
      project,
      form: {
        name: project.name,
        description: project.description || "",
        status: project.status || "On track",
        due: project.due || "",
        clientId: String(project.client?.id || ""),
        projectManagerId: String(project.projectManager?.id || ""),
        teamLeaderIds: (project.teamLeaders || []).map((leader) => String(leader.id)),
        active: project.active !== false,
      },
    });
  }
  function patchForm(patch) {
    setProjectDialog((current) => ({ ...current, form: { ...current.form, ...patch } }));
  }

  async function submitProject(event) {
    event.preventDefault();
    const { mode, project, form } = projectDialog;
    setSaving(true);
    setError("");
    const body = {
      name: form.name.trim(),
      description: form.description.trim(),
      status: form.status,
      due: form.due || null,
      clientId: form.clientId ? Number(form.clientId) : null,
      teamLeaderIds: form.teamLeaderIds.map(Number),
      active: form.active,
    };
    if (!isLead && form.projectManagerId) {
      body.leadId = Number(form.projectManagerId);
      body.projectManagerId = Number(form.projectManagerId);
    }
    try {
      if (mode === "edit") await updateProject(project.id, body);
      else await createProject(body);
      setProjects(await getProjects());
      setProjectDialog(null);
      setNotice(mode === "edit" ? "Project updated." : "Project created.");
    } catch (requestError) {
      setError(requestError.message || "Could not save the project.");
    } finally {
      setSaving(false);
    }
  }

  async function toggleActive(project, next) {
    setError("");
    const previous = projects;
    setProjects((rows) => rows.map((row) => (row.id === project.id ? { ...row, active: next } : row)));
    try {
      await setProjectActive(project.id, next);
      setNotice(`${project.name} ${next ? "activated" : "deactivated"}.`);
    } catch (requestError) {
      setProjects(previous);
      setError(requestError.message || "Could not change the project status.");
    }
  }

  async function submitClient(event) {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      const client = await createProjectManagementClient({ name: clientDialog.name.trim(), industry: clientDialog.industry.trim() });
      setClients((rows) => [...rows, client].sort((a, b) => a.name.localeCompare(b.name)));
      setClientDialog(null);
      setNotice(`Client "${client.name}" added. You can now create projects under it.`);
    } catch (requestError) {
      setError(requestError.message || "Could not add the client.");
    } finally {
      setSaving(false);
    }
  }

  /* ---------- project configuration (drawer) ---------- */
  async function mutate(action, success) {
    setSaving(true);
    setError("");
    try {
      await action();
      if (await loadConfig(drawerId)) setNotice(success);
      return true;
    } catch (requestError) {
      setError(requestError.message || "Could not save the change.");
      return false;
    } finally {
      setSaving(false);
    }
  }

  const [shiftForm, setShiftForm] = useState({
    name: "", days: ["MON", "TUE", "WED", "THU", "FRI"], startTime: "09:00", endTime: "17:00",
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
  });
  const [priorityForm, setPriorityForm] = useState({ name: "", level: 1, color: "#C62828" });
  const [categoryName, setCategoryName] = useState("");
  const [subNames, setSubNames] = useState({});

  async function addShift(event) {
    event.preventDefault();
    const ok = await mutate(() => createProjectConfiguration(drawerId, "shifts", {
      name: shiftForm.name.trim(),
      daysOfWeek: DAYS.filter((day) => shiftForm.days.includes(day)).join(","),
      startTime: shiftForm.startTime,
      endTime: shiftForm.endTime,
      timezone: shiftForm.timezone,
    }), "Support shift added.");
    if (ok) setShiftForm((current) => ({ ...current, name: "" }));
  }
  async function addPriority(event) {
    event.preventDefault();
    const ok = await mutate(() => createProjectConfiguration(drawerId, "priorities", { ...priorityForm, name: priorityForm.name.trim(), level: Number(priorityForm.level) }), "Priority added.");
    if (ok) setPriorityForm((current) => ({ ...current, name: "", level: Math.min(10, config.priorities.length + 2) }));
  }
  async function addStandardPriorities() {
    for (const priority of STANDARD_PRIORITIES) {
      if (config.priorities.some((item) => item.name.toLowerCase() === priority.name.toLowerCase())) continue;
      if (!(await mutate(() => createProjectConfiguration(drawerId, "priorities", priority), `${priority.name} priority added.`))) break;
    }
  }
  async function addCategory(event) {
    event.preventDefault();
    const ok = await mutate(() => createProjectConfiguration(drawerId, "categories", { name: categoryName.trim() }), "Category added.");
    if (ok) setCategoryName("");
  }
  async function addSubcategory(event, categoryId) {
    event.preventDefault();
    const name = (subNames[categoryId] || "").trim();
    if (!name) return;
    const ok = await mutate(() => addProjectSubcategory(drawerId, categoryId, name), "Subcategory added.");
    if (ok) setSubNames((current) => ({ ...current, [categoryId]: "" }));
  }
  async function submitSla(event, priorityId) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const responseMinutes = Number(form.get("responseMinutes"));
    const resolutionMinutes = Number(form.get("resolutionMinutes"));
    if (resolutionMinutes < responseMinutes) {
      setError("Resolution time must be at least the response time.");
      return;
    }
    await mutate(() => saveProjectSla(drawerId, { priorityId, responseMinutes, resolutionMinutes, businessHoursOnly: form.get("businessHoursOnly") === "on" }), "SLA saved.");
  }

  const setupCounts = [
    { label: "Support shifts", count: config.shifts.length, tab: "shifts", icon: Clock },
    { label: "Priorities", count: config.priorities.length, tab: "priorities", icon: Flag },
    { label: "Categories", count: config.categories.length, tab: "categories", icon: Tags },
    { label: "SLAs", count: config.slas.length, tab: "slas", icon: Timer },
  ];

  /* ---------- render ---------- */
  if (loading && !projects.length) return <section className="panel panel--wide"><p className="empty">Loading project management...</p></section>;

  return (
    <section className="pm" aria-label="Project management">
      <header className="pm-head">
        <div>
          <span className="pm-eyebrow">Workspace / Project management</span>
          <h2>Client projects</h2>
          <p>Create projects under each client, assign the team, and configure support hours, priorities, categories and SLAs.</p>
        </div>
        <div className="pm-head__actions">
          <button type="button" className="pm-btn" onClick={loadAll} disabled={loading}><RefreshCw size={15} /> Refresh</button>
          {canCreate && <button type="button" className="pm-btn" onClick={() => setClientDialog({ name: "", industry: "" })}><Building2 size={15} /> New client</button>}
          {canCreate && <button type="button" className="pm-btn pm-btn--primary" onClick={() => openCreate()}><Plus size={15} /> New project</button>}
        </div>
      </header>

      <Notice error={error} notice={notice} onDismiss={() => { setError(""); setNotice(""); }} />

      <div className="pm-kpis">
        <article><span className="pm-kpi__icon"><FolderKanban size={18} /></span><div><small>Total projects</small><strong>{metrics.total}</strong></div></article>
        <article><span className="pm-kpi__icon pm-kpi__icon--ok"><Power size={18} /></span><div><small>Active</small><strong>{metrics.active}</strong></div></article>
        <article><span className={`pm-kpi__icon ${metrics.risk ? "pm-kpi__icon--bad" : ""}`}><TriangleAlert size={18} /></span><div><small>At risk / delayed</small><strong>{metrics.risk}</strong></div></article>
        <article><span className="pm-kpi__icon pm-kpi__icon--muted"><Layers size={18} /></span><div><small>Inactive</small><strong>{metrics.inactive}</strong></div></article>
        <article><span className="pm-kpi__icon pm-kpi__icon--warn"><Building2 size={18} /></span><div><small>Clients</small><strong>{metrics.clients}</strong></div></article>
      </div>

      <div className="pm-toolbar">
        <label className="pm-search">
          <Search size={16} aria-hidden="true" />
          <span className="sr-only">Search projects</span>
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search by project, client or manager" />
        </label>
        <label className="pm-select"><span className="sr-only">Client</span>
          <select value={clientFilter} onChange={(event) => setClientFilter(event.target.value)}>
            <option value="all">All clients</option>
            {clients.map((client) => <option key={client.id} value={String(client.id)}>{client.name}</option>)}
            <option value={NO_CLIENT}>No client</option>
          </select>
        </label>
        <label className="pm-select"><span className="sr-only">Status</span>
          <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
            <option value="all">All statuses</option>
            {STATUSES.map((status) => <option key={status}>{status}</option>)}
          </select>
        </label>
        <div className="pm-seg" role="group" aria-label="Activation filter">
          {[["all", "All"], ["active", "Active"], ["inactive", "Inactive"]].map(([value, label]) => (
            <button type="button" key={value} className={activeFilter === value ? "is-on" : ""} aria-pressed={activeFilter === value} onClick={() => setActiveFilter(value)}>{label}</button>
          ))}
        </div>
      </div>

      {!clients.length && !projects.length ? (
        <EmptyState icon={Building2} title="No clients or projects yet" text="Start by adding a client, then create one or more projects under it." action={canCreate && <button type="button" className="pm-btn pm-btn--primary" onClick={() => setClientDialog({ name: "", industry: "" })}><Plus size={15} /> Add your first client</button>} />
      ) : !groups.length ? (
        <EmptyState icon={Search} title="No projects match your filters" text="Try a different search term or clear the filters." action={<button type="button" className="pm-btn" onClick={() => { setQuery(""); setClientFilter("all"); setStatusFilter("all"); setActiveFilter("all"); }}>Clear filters</button>} />
      ) : (
        <div className="pm-groups">
          {groups.map((group) => (
            <section className="pm-group" key={group.key} aria-label={group.client?.name || "Projects without a client"}>
              <header className="pm-group__head">
                <span className="pm-group__logo"><Building2 size={18} /></span>
                <div className="pm-group__title">
                  <h3>{group.client?.name || "No client assigned"}</h3>
                  <p>{group.client?.industry ? `${group.client.industry} · ` : ""}{group.items.length} {group.items.length === 1 ? "project" : "projects"}</p>
                </div>
                {canCreate && group.client && <button type="button" className="pm-btn pm-btn--sm" onClick={() => openCreate(group.client.id)}><Plus size={14} /> Add project</button>}
              </header>
              {group.items.length ? (
                <div className="pm-table-wrap">
                  <table className="pm-table">
                    <thead><tr><th>Project</th><th>Project manager</th><th>Team leaders</th><th>Status</th><th>Progress</th><th>Due</th><th>Active</th><th className="pm-right">Actions</th></tr></thead>
                    <tbody>
                      {group.items.map((item) => {
                        const progress = Math.min(100, Math.max(0, Number(item.progress) || 0));
                        const manager = managerName(item);
                        const isInactive = item.active === false;
                        return (
                          <tr key={item.id} className={isInactive ? "is-inactive" : ""}>
                            <td data-label="Project"><button type="button" className="pm-link" onClick={() => { setDrawerTab("overview"); setDrawerId(String(item.id)); }}>{item.name}</button>{item.description && <small title={item.description}>{item.description}</small>}</td>
                            <td data-label="Manager"><span className="pm-person"><Avatar name={manager} size="sm" />{manager || <span className="pm-muted">Unassigned</span>}</span></td>
                            <td data-label="Team leaders"><AvatarStack names={(item.teamLeaders || []).map((leader) => leader.name)} /></td>
                            <td data-label="Status"><StatusPill status={item.status} /></td>
                            <td data-label="Progress"><div className="pm-progress" role="progressbar" aria-valuenow={progress} aria-valuemin="0" aria-valuemax="100" aria-label={`${item.name} progress`}><span><i style={{ width: `${progress}%` }} /></span><small>{progress}%</small></div></td>
                            <td data-label="Due" className="pm-nowrap"><span className="pm-due"><CalendarDays size={13} />{formatDue(item.due)}</span></td>
                            <td data-label="Active"><Toggle checked={!isInactive} disabled={!canEdit} label={`${isInactive ? "Activate" : "Deactivate"} ${item.name}`} onChange={(next) => toggleActive(item, next)} /></td>
                            <td data-label="Actions" className="pm-right pm-nowrap">
                              <button type="button" className="pm-btn pm-btn--sm" onClick={() => { setDrawerTab("overview"); setDrawerId(String(item.id)); }}><ShieldCheck size={14} /> Configure</button>
                              {canEdit && <button type="button" className="pm-icon-btn" aria-label={`Edit ${item.name}`} onClick={() => openEdit(item)}><Pencil size={15} /></button>}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="pm-group__empty">No projects under this client yet.{canCreate && group.client && <button type="button" className="pm-textbtn" onClick={() => openCreate(group.client.id)}>Create the first one</button>}</div>
              )}
            </section>
          ))}
        </div>
      )}

      {/* ---------- project dialog ---------- */}
      {projectDialog && (
        <Modal
          title={projectDialog.mode === "edit" ? "Edit project" : "Create project"}
          subtitle={projectDialog.mode === "edit" ? projectDialog.project.name : "Add a project under a client and assign its team."}
          onClose={() => setProjectDialog(null)}
          size="lg"
          footer={<>
            <button type="button" className="pm-btn" onClick={() => setProjectDialog(null)}>Cancel</button>
            <button type="submit" form="pm-project-form" className="pm-btn pm-btn--primary" disabled={saving}>{saving ? "Saving..." : projectDialog.mode === "edit" ? "Save changes" : "Create project"}</button>
          </>}
        >
          <form id="pm-project-form" className="pm-form" onSubmit={submitProject}>
            <label className="pm-field pm-span-2"><span>Project name *</span><input required maxLength={160} value={projectDialog.form.name} onChange={(event) => patchForm({ name: event.target.value })} placeholder="e.g. Service desk transformation" /></label>
            <label className="pm-field"><span>Client</span>
              <select value={projectDialog.form.clientId} onChange={(event) => patchForm({ clientId: event.target.value })}>
                <option value="">No client</option>
                {clients.map((client) => <option key={client.id} value={client.id}>{client.name}</option>)}
              </select>
            </label>
            <label className="pm-field"><span>Project manager</span>
              <select value={projectDialog.form.projectManagerId} disabled={isLead} onChange={(event) => patchForm({ projectManagerId: event.target.value })}>
                <option value="">{isLead ? "You (Project Lead)" : "Assign to me"}</option>
                {projectDialog.mode === "edit" && projectDialog.project.projectManager && !leads.some((lead) => String(lead.id) === String(projectDialog.project.projectManager.id)) && (
                  <option value={projectDialog.project.projectManager.id}>{projectDialog.project.projectManager.name} (current)</option>
                )}
                {leads.map((lead) => <option key={lead.id} value={lead.id}>{lead.name}</option>)}
              </select>
            </label>
            <label className="pm-field"><span>Status</span>
              <select value={projectDialog.form.status} onChange={(event) => patchForm({ status: event.target.value })}>{STATUSES.map((status) => <option key={status}>{status}</option>)}</select>
            </label>
            <label className="pm-field"><span>Due date</span><input type="date" value={projectDialog.form.due} onChange={(event) => patchForm({ due: event.target.value })} /></label>
            <label className="pm-field pm-span-2"><span>Description</span><textarea rows="3" maxLength={5000} value={projectDialog.form.description} onChange={(event) => patchForm({ description: event.target.value })} placeholder="What is this project delivering for the client?" /></label>
            <fieldset className="pm-span-2 pm-chips">
              <legend>Team leaders</legend>
              {leads.length ? leads.map((lead) => {
                const checked = projectDialog.form.teamLeaderIds.includes(String(lead.id));
                return (
                  <label key={lead.id} className={checked ? "is-on" : ""}>
                    <input type="checkbox" checked={checked} onChange={(event) => patchForm({ teamLeaderIds: event.target.checked ? [...projectDialog.form.teamLeaderIds, String(lead.id)] : projectDialog.form.teamLeaderIds.filter((id) => id !== String(lead.id)) })} />
                    <Avatar name={lead.name} size="sm" />{lead.name}
                  </label>
                );
              }) : <span className="pm-muted">No Project Lead accounts are available yet.</span>}
            </fieldset>
            <div className="pm-span-2 pm-switchrow">
              <div><strong>Project is active</strong><small>Inactive projects stay on record but are hidden from active counts.</small></div>
              <Toggle checked={projectDialog.form.active} onChange={(next) => patchForm({ active: next })} label="Project is active" />
            </div>
          </form>
        </Modal>
      )}

      {/* ---------- client dialog ---------- */}
      {clientDialog && (
        <Modal title="Add a client" subtitle="Clients group the projects you manage for them." size="sm" onClose={() => setClientDialog(null)}
          footer={<>
            <button type="button" className="pm-btn" onClick={() => setClientDialog(null)}>Cancel</button>
            <button type="submit" form="pm-client-form" className="pm-btn pm-btn--primary" disabled={saving}>{saving ? "Adding..." : "Add client"}</button>
          </>}>
          <form id="pm-client-form" className="pm-form pm-form--single" onSubmit={submitClient}>
            <label className="pm-field"><span>Client name *</span><input required maxLength={160} value={clientDialog.name} onChange={(event) => setClientDialog({ ...clientDialog, name: event.target.value })} /></label>
            <label className="pm-field"><span>Industry (optional)</span><input maxLength={160} value={clientDialog.industry} onChange={(event) => setClientDialog({ ...clientDialog, industry: event.target.value })} /></label>
          </form>
        </Modal>
      )}

      {/* ---------- configuration drawer ---------- */}
      {drawerProject && (
        <Drawer
          title={drawerProject.name}
          subtitle={`${drawerProject.client?.name || "No client assigned"} · Managed by ${managerName(drawerProject) || "Unassigned"}`}
          badge={<StatusPill status={drawerProject.status} />}
          onClose={() => setDrawerId("")}
          tabs={
            <div className="pm-tabs" role="tablist" aria-label="Project configuration">
              {DRAWER_TABS.map(([key, label, Icon]) => (
                <button type="button" role="tab" key={key} aria-selected={drawerTab === key} className={drawerTab === key ? "is-on" : ""} onClick={() => setDrawerTab(key)}><Icon size={15} />{label}</button>
              ))}
            </div>
          }
        >
          {configLoading ? <p className="pm-muted">Loading configuration...</p> : (
            <div className="pm-drawer-content">
              {drawerTab === "overview" && (
                <>
                  <div className="pm-card pm-switchrow">
                    <div><strong>{drawerProject.active === false ? "Project is inactive" : "Project is active"}</strong><small>Activate or deactivate this project without deleting its data.</small></div>
                    <Toggle checked={drawerProject.active !== false} disabled={!canEdit} label="Project active" onChange={(next) => toggleActive(drawerProject, next)} />
                  </div>
                  <div className="pm-card">
                    <h4>Team</h4>
                    <div className="pm-team">
                      <div><small>Project manager</small><span className="pm-person"><Avatar name={managerName(drawerProject)} />{managerName(drawerProject) || <span className="pm-muted">Unassigned</span>}</span></div>
                      <div><small>Team leaders</small>
                        {(drawerProject.teamLeaders || []).length ? <ul>{drawerProject.teamLeaders.map((leader) => <li key={leader.id}><span className="pm-person"><Avatar name={leader.name} size="sm" />{leader.name}</span></li>)}</ul> : <span className="pm-muted">None assigned</span>}
                      </div>
                    </div>
                    {canEdit && <button type="button" className="pm-btn pm-btn--sm" onClick={() => openEdit(drawerProject)}><Pencil size={14} /> Edit project &amp; team</button>}
                  </div>
                  <div className="pm-card">
                    <h4>Setup progress</h4>
                    <div className="pm-setup">
                      {setupCounts.map((item) => (
                        <button type="button" key={item.label} className={item.count ? "is-done" : ""} onClick={() => setDrawerTab(item.tab)}>
                          <item.icon size={16} /><strong>{item.count}</strong><span>{item.label}</span>
                        </button>
                      ))}
                    </div>
                    <p className="pm-muted">{setupCounts.every((item) => item.count) ? "This project is fully configured." : "Add the missing items to complete this project's service setup."}</p>
                  </div>
                </>
              )}

              {drawerTab === "shifts" && (
                <>
                  {canEdit && (
                    <form className="pm-card pm-form" onSubmit={addShift}>
                      <h4 className="pm-span-2">Add a support shift</h4>
                      <label className="pm-field pm-span-2"><span>Shift name</span><input required maxLength={120} value={shiftForm.name} onChange={(event) => setShiftForm({ ...shiftForm, name: event.target.value })} placeholder="e.g. Business hours" /></label>
                      <fieldset className="pm-span-2 pm-days"><legend>Days</legend>
                        {DAYS.map((day) => { const on = shiftForm.days.includes(day); return (
                          <label key={day} className={on ? "is-on" : ""}><input type="checkbox" checked={on} onChange={(event) => setShiftForm({ ...shiftForm, days: event.target.checked ? [...shiftForm.days, day] : shiftForm.days.filter((item) => item !== day) })} />{day}</label>
                        ); })}
                      </fieldset>
                      <label className="pm-field"><span>Starts</span><input type="time" required value={shiftForm.startTime} onChange={(event) => setShiftForm({ ...shiftForm, startTime: event.target.value })} /></label>
                      <label className="pm-field"><span>Ends</span><input type="time" required value={shiftForm.endTime} onChange={(event) => setShiftForm({ ...shiftForm, endTime: event.target.value })} /></label>
                      <label className="pm-field pm-span-2"><span>Time zone</span><input required maxLength={80} value={shiftForm.timezone} onChange={(event) => setShiftForm({ ...shiftForm, timezone: event.target.value })} /></label>
                      <div className="pm-span-2 pm-right"><button className="pm-btn pm-btn--primary" disabled={saving || !shiftForm.days.length}><Plus size={15} /> Add shift</button></div>
                    </form>
                  )}
                  {config.shifts.map((shift) => (
                    <div className="pm-card pm-row" key={shift.id}>
                      <span className="pm-row__icon"><Clock size={17} /></span>
                      <div className="pm-row__main"><strong>{shift.name}</strong><small>{hhmm(shift.startTime)} – {hhmm(shift.endTime)} · {shift.timezone}</small>
                        <span className="pm-daychips">{DAYS.map((day) => <i key={day} className={(shift.daysOfWeek || "").split(",").includes(day) ? "is-on" : ""}>{day.slice(0, 1)}</i>)}</span>
                      </div>
                      {canEdit && <button type="button" className="pm-icon-btn pm-icon-btn--danger" aria-label={`Remove ${shift.name}`} disabled={saving} onClick={() => mutate(() => deleteProjectConfiguration(drawerId, "shifts", shift.id), "Shift removed.")}><Trash2 size={15} /></button>}
                    </div>
                  ))}
                  {!config.shifts.length && <EmptyState icon={Clock} title="No support hours yet" text="Define the shifts during which this project is supported." />}
                </>
              )}

              {drawerTab === "priorities" && (
                <>
                  {canEdit && (
                    <form className="pm-card pm-form" onSubmit={addPriority}>
                      <h4 className="pm-span-2">Add a priority</h4>
                      <label className="pm-field"><span>Name</span><input required maxLength={80} value={priorityForm.name} onChange={(event) => setPriorityForm({ ...priorityForm, name: event.target.value })} /></label>
                      <label className="pm-field"><span>Level (1 is highest)</span><input type="number" min="1" max="10" required value={priorityForm.level} onChange={(event) => setPriorityForm({ ...priorityForm, level: event.target.value })} /></label>
                      <label className="pm-field"><span>Color</span><input className="pm-color" type="color" value={priorityForm.color} onChange={(event) => setPriorityForm({ ...priorityForm, color: event.target.value })} /></label>
                      <div className="pm-right pm-actions-end"><button type="button" className="pm-btn" disabled={saving} onClick={addStandardPriorities}>Add standard four</button><button className="pm-btn pm-btn--primary" disabled={saving}><Plus size={15} /> Add priority</button></div>
                    </form>
                  )}
                  {[...config.priorities].sort((a, b) => a.level - b.level).map((priority) => (
                    <div className="pm-card pm-row" key={priority.id}>
                      <i className="pm-dot" style={{ backgroundColor: priority.color }} />
                      <div className="pm-row__main"><strong>{priority.name}</strong><small>Level {priority.level}</small></div>
                      {canEdit && <button type="button" className="pm-icon-btn pm-icon-btn--danger" aria-label={`Remove ${priority.name}`} disabled={saving} onClick={() => mutate(() => deleteProjectConfiguration(drawerId, "priorities", priority.id), "Priority removed.")}><Trash2 size={15} /></button>}
                    </div>
                  ))}
                  {!config.priorities.length && <EmptyState icon={Flag} title="No priorities yet" text="Priorities drive ticket urgency and SLA targets." />}
                </>
              )}

              {drawerTab === "categories" && (
                <>
                  {canEdit && (
                    <form className="pm-card pm-inline" onSubmit={addCategory}>
                      <label className="pm-field"><span>New ticket category</span><input required maxLength={120} value={categoryName} onChange={(event) => setCategoryName(event.target.value)} placeholder="e.g. Network" /></label>
                      <button className="pm-btn pm-btn--primary" disabled={saving}><Plus size={15} /> Add category</button>
                    </form>
                  )}
                  {config.categories.map((category) => (
                    <article className="pm-card" key={category.id}>
                      <header className="pm-row">
                        <span className="pm-row__icon"><Tags size={16} /></span>
                        <div className="pm-row__main"><strong>{category.name}</strong><small>{category.subcategories.length} {category.subcategories.length === 1 ? "subcategory" : "subcategories"}</small></div>
                        {canEdit && <button type="button" className="pm-icon-btn pm-icon-btn--danger" aria-label={`Remove ${category.name}`} disabled={saving} onClick={() => mutate(() => deleteProjectConfiguration(drawerId, "categories", category.id), "Category removed.")}><Trash2 size={15} /></button>}
                      </header>
                      <div className="pm-tags">
                        {category.subcategories.map((sub) => (
                          <span key={sub.id}>{sub.name}{canEdit && <button type="button" aria-label={`Remove ${sub.name}`} disabled={saving} onClick={() => mutate(() => deleteProjectConfiguration(drawerId, "categories", sub.id, category.id), "Subcategory removed.")}>×</button>}</span>
                        ))}
                        {!category.subcategories.length && <small className="pm-muted">No subcategories</small>}
                      </div>
                      {canEdit && (
                        <form className="pm-inline pm-inline--tight" onSubmit={(event) => addSubcategory(event, category.id)}>
                          <input aria-label={`New subcategory for ${category.name}`} maxLength={120} placeholder="Add subcategory" value={subNames[category.id] || ""} onChange={(event) => setSubNames({ ...subNames, [category.id]: event.target.value })} />
                          <button className="pm-btn pm-btn--sm" disabled={saving}>Add</button>
                        </form>
                      )}
                    </article>
                  ))}
                  {!config.categories.length && <EmptyState icon={Tags} title="No ticket categories yet" text="Categories and subcategories help route and report on tickets." />}
                </>
              )}

              {drawerTab === "slas" && (
                <>
                  {!config.priorities.length && <EmptyState icon={Timer} title="Add priorities first" text="SLA targets are set per priority." action={<button type="button" className="pm-btn" onClick={() => setDrawerTab("priorities")}>Go to priorities</button>} />}
                  {[...config.priorities].sort((a, b) => a.level - b.level).map((priority) => {
                    const sla = config.slas.find((item) => item.priorityId === priority.id);
                    return (
                      <form className="pm-card pm-sla" key={`${priority.id}-${sla?.id || "new"}-${sla?.responseMinutes}-${sla?.resolutionMinutes}`} onSubmit={(event) => submitSla(event, priority.id)}>
                        <div className="pm-sla__head"><i className="pm-dot" style={{ backgroundColor: priority.color }} /><strong>{priority.name}</strong>{sla ? <span className="pm-pill pm-pill--ok">Configured</span> : <span className="pm-pill pm-pill--muted">Default</span>}</div>
                        <label className="pm-field"><span>Response (minutes)</span><input name="responseMinutes" type="number" min="1" required defaultValue={sla?.responseMinutes ?? 60} disabled={!canEdit} /><small>{minutesLabel(sla?.responseMinutes ?? 60)}</small></label>
                        <label className="pm-field"><span>Resolution (minutes)</span><input name="resolutionMinutes" type="number" min="1" required defaultValue={sla?.resolutionMinutes ?? 480} disabled={!canEdit} /><small>{minutesLabel(sla?.resolutionMinutes ?? 480)}</small></label>
                        <label className="pm-check"><input name="businessHoursOnly" type="checkbox" defaultChecked={sla?.businessHoursOnly ?? true} disabled={!canEdit} /> Count business hours only</label>
                        {canEdit && <div className="pm-actions-end">{sla && <button type="button" className="pm-btn pm-btn--sm" disabled={saving} onClick={() => mutate(() => deleteProjectConfiguration(drawerId, "slas", sla.id), "SLA removed.")}>Remove</button>}<button className="pm-btn pm-btn--primary pm-btn--sm" disabled={saving}>Save SLA</button></div>}
                      </form>
                    );
                  })}
                </>
              )}
            </div>
          )}
        </Drawer>
      )}
    </section>
  );
}
