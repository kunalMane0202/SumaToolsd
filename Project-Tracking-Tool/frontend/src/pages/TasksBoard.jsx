import { useEffect, useMemo, useState } from "react";
import { CalendarDays, CheckCircle2, Circle, ClipboardList, Hourglass, ListChecks, Plus, RefreshCw, Search, Trash2, TriangleAlert, Users } from "lucide-react";
import { createTask, deleteTask, getProjects, getTaskAssignees, getTasks, updateTask } from "../auth/authService.js";
import { Avatar, EmptyState, Modal, Notice } from "../components/ui.jsx";
import "./Workspace-modern.css";

const TASK_STATUSES = ["To do", "In progress", "Completed"];
const PRIORITIES = ["Low", "Medium", "High", "Critical"];
const PRIORITY_RANK = { Critical: 0, High: 1, Medium: 2, Low: 3 };
const EMPTY_FORM = { title: "", projectId: "", assigneeId: "", due: "", priority: "Medium", status: "To do" };

function allowed(user, permission) {
  return user.role === "Super User" || (user.permissions || []).includes(permission);
}
function todayIso() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}
function dueInfo(task) {
  if (!task.due) return { label: "No due date", tone: "none" };
  const date = new Date(`${task.due}T00:00:00`);
  const label = date.toLocaleDateString(undefined, { day: "numeric", month: "short" });
  if (task.status === "Completed") return { label, tone: "done" };
  const today = todayIso();
  if (task.due < today) {
    const days = Math.round((new Date(`${today}T00:00:00`) - date) / 86400000);
    return { label: `${label} · ${days}d overdue`, tone: "bad" };
  }
  if (task.due === today) return { label: "Due today", tone: "warn" };
  return { label, tone: "none" };
}
const isOverdue = (task) => task.status !== "Completed" && task.due && task.due < todayIso();

export default function TasksBoard({ user }) {
  const [tasks, setTasks] = useState([]);
  const [projects, setProjects] = useState([]);
  const [people, setPeople] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [scope, setScope] = useState("all"); // all | mine | unassigned | overdue
  const [view, setView] = useState("list"); // list | people
  const [query, setQuery] = useState("");
  const [projectFilter, setProjectFilter] = useState("all");
  const [priorityFilter, setPriorityFilter] = useState("all");
  const [statusFilter, setStatusFilter] = useState("all");
  const [dialog, setDialog] = useState(null);

  const isMember = user.role === "Team Member";
  const canCreate = allowed(user, "tasks.create");
  const canEdit = allowed(user, "tasks.edit");
  const canDelete = allowed(user, "tasks.delete");
  const canAssign = (user.role === "Super User" || user.role === "Admin") && (canCreate || canEdit);

  async function load() {
    setLoading(true);
    setError("");
    try {
      const [taskRows, projectRows, peopleRows] = await Promise.all([
        getTasks(),
        getProjects().catch(() => []),
        canAssign ? getTaskAssignees().catch(() => []) : Promise.resolve([]),
      ]);
      setTasks(taskRows);
      setProjects(projectRows);
      setPeople(peopleRows);
    } catch (requestError) {
      setError(requestError.message || "Could not load tasks.");
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); }, []);
  useEffect(() => {
    if (!notice) return undefined;
    const timer = setTimeout(() => setNotice(""), 3500);
    return () => clearTimeout(timer);
  }, [notice]);

  const projectIdByName = useMemo(() => new Map(projects.map((project) => [project.name, project.id])), [projects]);
  const mine = (task) => task.assigneeId != null && String(task.assigneeId) === String(user.id);

  const counts = useMemo(() => ({
    total: tasks.length,
    todo: tasks.filter((task) => task.status === "To do").length,
    active: tasks.filter((task) => task.status === "In progress").length,
    done: tasks.filter((task) => task.status === "Completed").length,
    overdue: tasks.filter(isOverdue).length,
    mine: tasks.filter(mine).length,
    unassigned: tasks.filter((task) => !task.assigneeId).length,
  }), [tasks]); // eslint-disable-line react-hooks/exhaustive-deps

  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return tasks
      .filter((task) => {
        if (scope === "mine" && !mine(task)) return false;
        if (scope === "unassigned" && task.assigneeId) return false;
        if (scope === "overdue" && !isOverdue(task)) return false;
        if (projectFilter !== "all" && task.project !== projectFilter) return false;
        if (priorityFilter !== "all" && task.priority !== priorityFilter) return false;
        if (statusFilter !== "all" && task.status !== statusFilter) return false;
        if (needle && !`${task.title} ${task.project} ${task.assignee}`.toLowerCase().includes(needle)) return false;
        return true;
      })
      .sort((a, b) => (a.status === "Completed") - (b.status === "Completed")
        || (a.due || "9999").localeCompare(b.due || "9999")
        || (PRIORITY_RANK[a.priority] ?? 9) - (PRIORITY_RANK[b.priority] ?? 9));
  }, [tasks, scope, projectFilter, priorityFilter, statusFilter, query]); // eslint-disable-line react-hooks/exhaustive-deps

  const grouped = useMemo(() => {
    const map = new Map();
    visible.forEach((task) => {
      const key = task.assigneeId ? String(task.assigneeId) : "none";
      if (!map.has(key)) map.set(key, { key, name: task.assignee || "", items: [] });
      map.get(key).items.push(task);
    });
    return [...map.values()].sort((a, b) => (a.key === "none") - (b.key === "none") || a.name.localeCompare(b.name));
  }, [visible]);

  function payload(task, patch = {}) {
    return {
      title: task.title,
      projectId: projectIdByName.get(task.project) || null,
      assigneeId: task.assigneeId || null,
      due: task.due || null,
      priority: task.priority || "Medium",
      status: task.status,
      ...patch,
    };
  }
  async function change(task, patch, success) {
    setError("");
    const previous = tasks;
    setTasks((rows) => rows.map((row) => (row.id === task.id ? { ...row, ...patch.local } : row)));
    try {
      await updateTask(task.id, payload(task, patch.api));
      setNotice(success);
      await load();
    } catch (requestError) {
      setTasks(previous);
      setError(requestError.message || "Could not update the task.");
    }
  }
  const setStatus = (task, status) => change(task, { api: { status }, local: { status } }, `“${task.title}” moved to ${status}.`);
  function setAssignee(task, value) {
    const person = people.find((item) => String(item.id) === String(value));
    return change(task, { api: { assigneeId: value ? Number(value) : null }, local: { assigneeId: value ? Number(value) : null, assignee: person?.name || "" } },
      person ? `Assigned to ${person.name}.` : "Task unassigned.");
  }
  async function remove(task) {
    if (!window.confirm(`Delete "${task.title}"?`)) return;
    try {
      await deleteTask(task.id);
      setNotice("Task deleted.");
      await load();
    } catch (requestError) {
      setError(requestError.message || "Could not delete the task.");
    }
  }
  async function submit(event) {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      await createTask({
        ...dialog,
        title: dialog.title.trim(),
        projectId: dialog.projectId ? Number(dialog.projectId) : null,
        assigneeId: canAssign && dialog.assigneeId ? Number(dialog.assigneeId) : null,
        due: dialog.due || null,
      });
      const person = people.find((item) => String(item.id) === String(dialog.assigneeId));
      setDialog(null);
      setNotice(person ? `Task created and assigned to ${person.name}.` : "Task created.");
      await load();
    } catch (requestError) {
      setError(requestError.message || "Could not create the task.");
    } finally {
      setSaving(false);
    }
  }

  const tabs = [
    ["all", isMember ? "All my tasks" : "All tasks", counts.total],
    ...(!isMember ? [["mine", "Assigned to me", counts.mine], ["unassigned", "Unassigned", counts.unassigned]] : []),
    ["overdue", "Overdue", counts.overdue],
  ];

  function renderTask(task) {
    const due = dueInfo(task);
    const done = task.status === "Completed";
    return (
      <li key={task.id} className={`wk-task${done ? " is-done" : ""}`}>
        <button type="button" className="wk-check" disabled={!canEdit} aria-label={done ? `Reopen ${task.title}` : `Complete ${task.title}`} onClick={() => setStatus(task, done ? "To do" : "Completed")}>
          {done ? <CheckCircle2 size={22} /> : <Circle size={22} />}
        </button>
        <div className="wk-task__main">
          <strong>{task.title}</strong>
          <div className="wk-task__meta">
            {task.project ? <span className="wk-chip"><ListChecks size={12} />{task.project}</span> : <span className="wk-chip wk-chip--muted">No project</span>}
            <span className={`wk-pill wk-pill--p-${(task.priority || "medium").toLowerCase()}`}>{task.priority}</span>
            <span className={`wk-due wk-due--${due.tone}`}><CalendarDays size={13} />{due.label}</span>
          </div>
        </div>
        <div className="wk-task__assignee">
          <Avatar name={task.assignee} />
          {canAssign && canEdit ? (
            <select className="wk-select" aria-label={`Assign “${task.title}” to`} value={task.assigneeId || ""} onChange={(event) => setAssignee(task, event.target.value)}>
              <option value="">Unassigned</option>
              {people.map((person) => <option key={person.id} value={person.id}>{person.name}</option>)}
            </select>
          ) : <span className="wk-task__name">{task.assignee || "Unassigned"}</span>}
        </div>
        <div className="wk-task__status">
          {canEdit ? (
            <select className={`wk-select wk-select--status wk-select--s-${task.status === "Completed" ? "done" : task.status === "In progress" ? "active" : "todo"}`} aria-label={`Status of ${task.title}`} value={task.status} onChange={(event) => setStatus(task, event.target.value)}>
              {TASK_STATUSES.map((status) => <option key={status}>{status}</option>)}
            </select>
          ) : <span className="wk-pill wk-pill--muted">{task.status}</span>}
          {canDelete && <button type="button" className="wk-icon-btn wk-icon-btn--danger" aria-label={`Delete ${task.title}`} onClick={() => remove(task)}><Trash2 size={15} /></button>}
        </div>
      </li>
    );
  }

  return (
    <section className="wk" aria-label="My tasks">
      <header className="wk-head">
        <div>
          <span className="wk-eyebrow">Workspace / {isMember ? "My tasks" : "Tasks"}</span>
          <h2>{isMember ? "My tasks" : "Tasks & assignments"}</h2>
          <p>{isMember ? "Everything assigned to you, ordered by what is due first." : canAssign ? "Create tasks and assign each one to the right person on your team." : "Tasks across the projects you lead. Only Admins and Super Users can change who a task is assigned to."}</p>
        </div>
        <div className="wk-head__actions">
          <button type="button" className="wk-btn" onClick={load} disabled={loading}><RefreshCw size={15} /> Refresh</button>
          {canCreate && <button type="button" className="wk-btn wk-btn--primary" onClick={() => setDialog({ ...EMPTY_FORM })}><Plus size={15} /> New task</button>}
        </div>
      </header>

      <Notice error={error} notice={notice} onDismiss={() => { setError(""); setNotice(""); }} />

      <div className="wk-kpis">
        <article><span className="wk-kpi__icon"><ClipboardList size={18} /></span><div><small>Total</small><strong>{counts.total}</strong></div></article>
        <article><span className="wk-kpi__icon wk-kpi__icon--muted"><Circle size={18} /></span><div><small>To do</small><strong>{counts.todo}</strong></div></article>
        <article><span className="wk-kpi__icon wk-kpi__icon--warn"><Hourglass size={18} /></span><div><small>In progress</small><strong>{counts.active}</strong></div></article>
        <article><span className="wk-kpi__icon wk-kpi__icon--ok"><CheckCircle2 size={18} /></span><div><small>Completed</small><strong>{counts.done}</strong></div></article>
        <article><span className={`wk-kpi__icon ${counts.overdue ? "wk-kpi__icon--bad" : "wk-kpi__icon--muted"}`}><TriangleAlert size={18} /></span><div><small>Overdue</small><strong>{counts.overdue}</strong></div></article>
      </div>

      <div className="wk-tabs" role="tablist" aria-label="Task scope">
        {tabs.map(([key, label, count]) => (
          <button type="button" role="tab" key={key} aria-selected={scope === key} className={scope === key ? "is-on" : ""} onClick={() => setScope(key)}>{label}<span>{count}</span></button>
        ))}
      </div>

      <div className="wk-toolbar">
        <label className="wk-search"><Search size={16} aria-hidden="true" /><span className="sr-only">Search tasks</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search tasks, projects or people" /></label>
        <label className="wk-filter"><span className="sr-only">Project</span>
          <select value={projectFilter} onChange={(event) => setProjectFilter(event.target.value)}><option value="all">All projects</option>{[...new Set(tasks.map((task) => task.project).filter(Boolean))].sort().map((name) => <option key={name}>{name}</option>)}</select>
        </label>
        <label className="wk-filter"><span className="sr-only">Priority</span>
          <select value={priorityFilter} onChange={(event) => setPriorityFilter(event.target.value)}><option value="all">All priorities</option>{PRIORITIES.map((item) => <option key={item}>{item}</option>)}</select>
        </label>
        <label className="wk-filter"><span className="sr-only">Status</span>
          <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}><option value="all">All statuses</option>{TASK_STATUSES.map((item) => <option key={item}>{item}</option>)}</select>
        </label>
        {!isMember && (
          <div className="wk-seg" role="group" aria-label="Layout">
            <button type="button" className={view === "list" ? "is-on" : ""} aria-pressed={view === "list"} onClick={() => setView("list")}><ListChecks size={14} /> List</button>
            <button type="button" className={view === "people" ? "is-on" : ""} aria-pressed={view === "people"} onClick={() => setView("people")}><Users size={14} /> By person</button>
          </div>
        )}
      </div>

      {loading && !tasks.length ? <p className="wk-muted">Loading tasks...</p> : !visible.length ? (
        <EmptyState icon={ClipboardList} title={tasks.length ? "No tasks match your filters" : "No tasks yet"} text={tasks.length ? "Try clearing a filter or switching tabs." : isMember ? "Tasks assigned to you will appear here." : "Create the first task and assign it to a teammate."}
          action={canCreate && !tasks.length && <button type="button" className="wk-btn wk-btn--primary" onClick={() => setDialog({ ...EMPTY_FORM })}><Plus size={15} /> New task</button>} />
      ) : view === "people" && !isMember ? (
        <div className="wk-people">
          {grouped.map((group) => (
            <section className="wk-person" key={group.key}>
              <header>
                <Avatar name={group.name} size="lg" />
                <div><h3>{group.name || "Unassigned"}</h3><p>{group.items.length} {group.items.length === 1 ? "task" : "tasks"} · {group.items.filter((task) => task.status === "Completed").length} completed · {group.items.filter(isOverdue).length} overdue</p></div>
              </header>
              <ul className="wk-list">{group.items.map(renderTask)}</ul>
            </section>
          ))}
        </div>
      ) : (
        <ul className="wk-list wk-list--card">{visible.map(renderTask)}</ul>
      )}

      {dialog && (
        <Modal title="Create task" subtitle={canAssign ? "Choose who is responsible for this task." : "This task will be assigned to you."} onClose={() => setDialog(null)} size="md"
          footer={<><button type="button" className="wk-btn" onClick={() => setDialog(null)}>Cancel</button><button type="submit" form="wk-task-form" className="wk-btn wk-btn--primary" disabled={saving}>{saving ? "Creating..." : "Create task"}</button></>}>
          <form id="wk-task-form" className="wk-form" onSubmit={submit}>
            <label className="wk-field wk-span-2"><span>Task title *</span><input required maxLength={240} value={dialog.title} onChange={(event) => setDialog({ ...dialog, title: event.target.value })} placeholder="What needs to be done?" /></label>
            <label className="wk-field"><span>Project</span><select value={dialog.projectId} onChange={(event) => setDialog({ ...dialog, projectId: event.target.value })}><option value="">No project</option>{projects.map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}</select></label>
            <label className="wk-field"><span>Assign to</span>
              {canAssign ? (
                <select value={dialog.assigneeId} onChange={(event) => setDialog({ ...dialog, assigneeId: event.target.value })}><option value="">Unassigned</option>{people.map((person) => <option key={person.id} value={person.id}>{person.name}</option>)}</select>
              ) : <input disabled value={user.name || "You"} readOnly />}
            </label>
            <label className="wk-field"><span>Due date</span><input type="date" value={dialog.due} onChange={(event) => setDialog({ ...dialog, due: event.target.value })} /></label>
            <label className="wk-field"><span>Priority</span><select value={dialog.priority} onChange={(event) => setDialog({ ...dialog, priority: event.target.value })}>{PRIORITIES.map((item) => <option key={item}>{item}</option>)}</select></label>
            <label className="wk-field"><span>Status</span><select value={dialog.status} onChange={(event) => setDialog({ ...dialog, status: event.target.value })}>{TASK_STATUSES.map((item) => <option key={item}>{item}</option>)}</select></label>
          </form>
        </Modal>
      )}
    </section>
  );
}
