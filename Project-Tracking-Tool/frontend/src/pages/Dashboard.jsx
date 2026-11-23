
import { useState, useEffect } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  Bell,
  BriefcaseBusiness,
  ChartNoAxesCombined,
  FileBarChart2,
  FolderKanban,
  LayoutDashboard,
  ListTodo,
  LogOut,
  Moon,
  Settings2,
  ShieldCheck,
  SlidersHorizontal,
  Sun,
  Ticket,
  UserRound,
  UsersRound,
} from "lucide-react";
import brandImage from "../assets/opsvista-brand.png";
import {
  freestylePageVariants,
  freestyleSpring,
  freestyleStaggerVariants,
} from "../motionPresets.js";

import "./Dashboard.css";
import "./Dashboard-premium-overrides.css";
import "./theme-extras.css";
import "./Notifications.css";
import "./Dashboard-modern.css";

import DashboardVisualizations, {
  AnimatedStat,
} from "./DashboardVisualizations.jsx";

import {
  approveAccountRequest,
  getAccountRequests,
  rejectAccountRequest,
  getUsers,
  createUser,
  deleteUser,
  updateUserPermissions,
  getDashboard,
} from "../auth/authService.js";

import ReportsPage from "./ReportsPage.jsx";
import ProjectAnalyticsPage from "./ProjectAnalyticsPage.jsx";
import ProjectManagementPage from "./ProjectManagementPage.jsx";
import WorkspacePage from "./WorkspacePage.jsx";
import NotificationsPage, { useNotifications } from "./NotificationsPage.jsx";

/* =========================================================
   PERMISSIONS
========================================================= */

const PERMISSION_GROUPS = [
  {
    title: "Dashboard",
    permissions: [
      { key: "dashboard.view", label: "View Dashboard" },
    ],
  },
  {
    title: "Projects",
    permissions: [
      { key: "projects.view", label: "View Projects" },
      { key: "projects.create", label: "Create Projects" },
      { key: "projects.edit", label: "Edit Projects" },
      { key: "projects.delete", label: "Delete Projects" },
    ],
  },
  {
    title: "Tasks",
    permissions: [
      { key: "tasks.view", label: "View Tasks" },
      { key: "tasks.create", label: "Create Tasks" },
      { key: "tasks.edit", label: "Edit Tasks" },
      { key: "tasks.delete", label: "Delete Tasks" },
    ],
  },
  {
    title: "Ticket Management",
    permissions: [
      { key: "tickets.view", label: "View Tickets" },
      { key: "tickets.create", label: "Create Tickets" },
      { key: "tickets.assign", label: "Assign Tickets" },
      { key: "tickets.update", label: "Update Tickets" },
      { key: "tickets.resolve", label: "Resolve Tickets" },
      { key: "tickets.close", label: "Close Tickets" },
      { key: "tickets.delete", label: "Delete Tickets" },
    ],
  },
  {
    title: "Reports & Analytics",
    permissions: [
      { key: "reports.view", label: "View Reports & Analytics" },
      { key: "reports.generate", label: "Generate Reports" },
      { key: "reports.export", label: "Export Excel, PDF and CSV" },
      { key: "reports.schedule", label: "Schedule Email Reports" },
    ],
  },
  {
    title: "User Management",
    permissions: [
      { key: "users.view", label: "View Users" },
      { key: "users.create", label: "Create Users" },
      { key: "users.edit", label: "Edit Users" },
      { key: "users.delete", label: "Delete Users" },
    ],
  },
  {
    title: "Settings",
    permissions: [
      { key: "settings.view", label: "View Settings" },
      { key: "settings.edit", label: "Edit Settings" },
    ],
  },
];

/* =========================================================
   NAVIGATION
========================================================= */

const NAV = [
  { key: "dashboard", label: "Dashboard", permission: "dashboard.view" },
  { key: "projects", label: "Projects", permission: "projects.view" },
  {
    key: "project-management",
    label: "Project Management",
    permission: "projects.view",
  },
  { key: "tasks", label: "My tasks", permission: "tasks.view" },
  {
    key: "tickets",
    label: "Ticket Management",
    permission: "tickets.view",
  },
  {
    key: "reports",
    label: "Reports & analytics",
    permission: "reports.view",
  },
  {
    key: "project-analytics",
    label: "Project analytics",
    permission: "reports.view",
  },
  { key: "users", label: "Account requests", superUserOnly: true },
  {
    key: "user-management",
    label: "User Management",
    superUserOnly: true,
  },
  {
    key: "permissions",
    label: "User permissions",
    superUserOnly: true,
  },
  { key: "notifications", label: "Notifications", alwaysVisible: true },
  { key: "settings", label: "Settings", alwaysVisible: true },
];

const NAV_ICONS = {
  dashboard: LayoutDashboard,
  projects: FolderKanban,
  "project-management": SlidersHorizontal,
  tasks: ListTodo,
  tickets: Ticket,
  reports: FileBarChart2,
  "project-analytics": ChartNoAxesCombined,
  users: UserRound,
  "user-management": UsersRound,
  permissions: ShieldCheck,
  notifications: Bell,
  settings: Settings2,
};

/* =========================================================
   HELPERS
========================================================= */

const fmtDate = (iso) => {
  if (!iso) return "—";

  const date = new Date(`${iso.slice(0, 10)}T00:00:00`);

  return Number.isNaN(date.getTime())
    ? "—"
    : date.toLocaleDateString("en-IN", {
        day: "numeric",
        month: "short",
      });
};

const daysLeft = (iso) => {
  if (!iso) return Infinity;

  const target = new Date(`${iso.slice(0, 10)}T00:00:00`);

  if (Number.isNaN(target.getTime())) return Infinity;

  const today = new Date();
  today.setHours(0, 0, 0, 0);

  return Math.ceil((target - today) / 86400000);
};

const statusClass = (status = "Unknown") =>
  "tag tag--" + String(status).toLowerCase().replace(/\s+/g, "-");

const canUser = (user, permission) =>
  user?.role === "Super User" ||
  (user?.permissions || []).includes(permission);

/* =========================================================
   ACCOUNT REQUESTS
========================================================= */

function AccountRequests({ user }) {
  const [requests, setRequests] = useState([]);
  const [passwords, setPasswords] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function refreshRequests() {
    setError("");

    try {
      const data = await getAccountRequests();

      setRequests(
        Array.isArray(data) ? data : data.requests || []
      );
    } catch (err) {
      setError(err.message || "Could not load account enquiries.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    refreshRequests();
  }, []);

  async function reviewRequest(request, approve) {
    setError("");
    setNotice("");

    try {
      if (approve) {
        const password = passwords[request.id] ?? "";

        if (password.length < 12 || password.length > 72) {
          throw new Error(
            "Set a temporary password between 12 and 72 characters."
          );
        }

        await approveAccountRequest(user.token, request.id, password);

        setPasswords((current) => ({
          ...current,
          [request.id]: "",
        }));

        setNotice(
          `Account created for ${request.name}. Share the temporary password securely.`
        );
      } else {
        await rejectAccountRequest(user.token, request.id);

        setNotice(
          `Enquiry from ${request.name} was declined.`
        );
      }

      await refreshRequests();
    } catch (err) {
      setError(err.message || "Could not review this enquiry.");
    }
  }

  return (
    <section className="panel panel--wide">
      <div className="panel__head">
        <h2>Pending account enquiries</h2>

        <button
          className="btn"
          onClick={refreshRequests}
          disabled={loading}
        >
          Refresh
        </button>
      </div>

      {error && (
        <p
          className="account-request__message account-request__message--error"
          role="alert"
        >
          {error}
        </p>
      )}

      {notice && (
        <p
          className="account-request__message account-request__message--success"
          role="status"
        >
          {notice}
        </p>
      )}

      {loading ? (
        <p className="empty">Loading enquiries...</p>
      ) : requests.length === 0 ? (
        <p className="empty">
          There are no pending account enquiries.
        </p>
      ) : (
        <ul className="account-requests">
          {requests.map((request) => (
            <li className="account-request" key={request.id}>
              <div className="account-request__details">
                <h3>{request.name}</h3>

                <p>
                  <strong>Username:</strong> {request.username}
                </p>

                <p>
                  <strong>Email:</strong> {request.email}
                </p>

                <p className="muted">
                  Received{" "}
                  {new Date(request.submittedAt).toLocaleString()}
                </p>
              </div>

              <div className="account-request__actions">
                <label className="field">
                  <span>
                    Temporary password (12-72 characters)
                  </span>

                  <input
                    type="password"
                    autoComplete="new-password"
                    maxLength={72}
                    value={passwords[request.id] ?? ""}
                    onChange={(event) =>
                      setPasswords((current) => ({
                        ...current,
                        [request.id]: event.target.value,
                      }))
                    }
                  />
                </label>

                <div className="account-request__buttons">
                  <button
                    className="btn btn--primary"
                    onClick={() => reviewRequest(request, true)}
                  >
                    Approve and create account
                  </button>

                  <button
                    className="btn"
                    onClick={() => reviewRequest(request, false)}
                  >
                    Decline
                  </button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      <p className="account-request__note">
        New accounts are created as Project Leads. The applicant is
        not emailed automatically; share the temporary password securely.
      </p>
    </section>
  );
}

/* =========================================================
   USER MANAGEMENT
========================================================= */

function UserManagement() {
  const [users, setUsers] = useState([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [showForm, setShowForm] = useState(false);

  const [form, setForm] = useState({
    name: "",
    username: "",
    email: "",
    password: "",
    role: "Team Member",
  });

  async function loadUsers() {
    setError("");

    try {
      const data = await getUsers();

      setUsers(
        Array.isArray(data) ? data : data.users || []
      );
    } catch (err) {
      setError(err.message || "Could not load users.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadUsers();
  }, []);

  const filteredUsers = users.filter((item) => {
    const term = search.toLowerCase();

   return (
  (item.name || "").toLowerCase().includes(term) ||
  (item.username || "").toLowerCase().includes(term) ||
  (item.email || "").toLowerCase().includes(term) ||
  (item.role || "").toLowerCase().includes(term)
);
  });

  function updateField(event) {
    setForm((current) => ({
      ...current,
      [event.target.name]: event.target.value,
    }));
  }

  async function handleCreate(event) {
    event.preventDefault();

    setSaving(true);
    setError("");
    setNotice("");

    try {
      await createUser(form);

      setForm({
        name: "",
        username: "",
        email: "",
        password: "",
        role: "Team Member",
      });

      setShowForm(false);
      setNotice("User created successfully.");

      await loadUsers();
    } catch (err) {
      setError(err.message || "Could not create user.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(item) {
    if (item.role === "Super User") return;

    const confirmed = window.confirm(
      `Are you sure you want to delete ${item.name || item.username}?`
    );

    if (!confirmed) return;

    setError("");
    setNotice("");

    try {
      await deleteUser(item.id);

      setNotice("User deleted successfully.");

      await loadUsers();
    } catch (err) {
      setError(err.message || "Could not delete user.");
    }
  }

  return (
    <section className="panel panel--wide user-permissions-panel">
      <div className="panel__head">
        <div>
          <h2>User Management</h2>
          <p className="muted">
            Create, search and manage user accounts.
          </p>
        </div>

        <button
          className="btn btn--primary"
          onClick={() => setShowForm((current) => !current)}
        >
          {showForm ? "Cancel" : "+ Create User"}
        </button>
      </div>

      {error && (
        <p
          className="account-request__message account-request__message--error"
          role="alert"
        >
          {error}
        </p>
      )}

      {notice && (
        <p
          className="account-request__message account-request__message--success"
          role="status"
        >
          {notice}
        </p>
      )}

      {showForm && (
        <form className="permission-group" onSubmit={handleCreate}>
          <h3>Create New User</h3>

          <label className="field">
            <span>Full name</span>
            <input
              name="name"
              value={form.name}
              onChange={updateField}
              required
            />
          </label>

          <label className="field">
            <span>Username</span>
            <input
              name="username"
              value={form.username}
              onChange={updateField}
              required
            />
          </label>

          <label className="field">
            <span>Email address</span>
            <input
              name="email"
              type="email"
              value={form.email}
              onChange={updateField}
              required
            />
          </label>

          <label className="field">
            <span>Temporary password (12-72 characters)</span>
            <input
              name="password"
              type="password"
              minLength={12}
              maxLength={72}
              autoComplete="new-password"
              value={form.password}
              onChange={updateField}
              required
            />
          </label>

          <label className="field">
            <span>Role</span>
            <select
              name="role"
              value={form.role}
              onChange={updateField}
            >
              <option value="Super User">Super User</option>
              <option value="Admin">Admin</option>
              <option value="Manager">Manager</option>
              <option value="Project Lead">Project Lead</option>
              <option value="L1 Support">L1 Support</option>
              <option value="L2 Support">L2 Support</option>
              <option value="L3 Support">L3 Support</option>
              <option value="Team Member">Team Member</option>
            </select>
          </label>

          {form.role === "Super User" && (
            <p className="muted">
              Super Users have full access to all areas and user administration.
            </p>
          )}

          <button
            className="btn btn--primary"
            type="submit"
            disabled={saving}
          >
            {saving ? "Creating..." : "Create User"}
          </button>
        </form>
      )}

      <label className="field">
        <span>Search users</span>

        <input
          type="search"
          placeholder="Search by name,email,username or role"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </label>

      {loading ? (
        <p className="empty">Loading users...</p>
      ) : filteredUsers.length === 0 ? (
        <p className="empty">No users found.</p>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Username</th>
                <th>Email Address</th>
                <th>Role</th>
                <th>Permissions</th>
                <th>Action</th>
              </tr>
            </thead>

          <tbody>
          {filteredUsers.map((item) => (
            <tr key={item.id}>
              <td>
                <strong>{item.name || "—"}</strong>
              </td>

              <td>{item.username || "—"}</td>

              {/* NEW EMAIL ADDRESS COLUMN */}
              <td>{item.email || "N/A"}</td>

              <td>{item.role}</td>

              <td>
                {item.role === "Super User"
                  ? "Full access"
                  : (item.permissions || []).length}
              </td>

              <td>
                {item.role === "Super User" ? (
                  <span className="muted">Protected</span>
                ) : (
                  <button
                    className="btn"
                    onClick={() => handleDelete(item)}
                  >
                    Delete
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

/* =========================================================
   USER PERMISSIONS
========================================================= */

function UserPermissions() {
  const [users, setUsers] = useState([]);
  const [selectedId, setSelectedId] = useState("");
  const [selectedPermissions, setSelectedPermissions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function loadUsers() {
    setError("");

    try {
      const data = await getUsers();

      const list = Array.isArray(data)
        ? data
        : data.users || [];

      setUsers(list);

      if (selectedId) {
        const selected = list.find(
          (item) => String(item.id) === String(selectedId)
        );

        if (selected) {
          setSelectedPermissions(selected.permissions || []);
        }
      } else {
        const firstUser = list.find(
          (item) => item.role !== "Super User"
        );

        if (firstUser) {
          setSelectedId(String(firstUser.id));
          setSelectedPermissions(firstUser.permissions || []);
        }
      }
    } catch (err) {
      setError(err.message || "Could not load users.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadUsers();
  }, []);

  const selectedUser = users.find(
    (item) => String(item.id) === String(selectedId)
  );

  const isSuperUser = selectedUser?.role === "Super User";

  function togglePermission(key) {
    setSelectedPermissions((current) =>
      current.includes(key)
        ? current.filter((permission) => permission !== key)
        : [...current, key]
    );
  }

  function selectAllPermissions() {
    setSelectedPermissions(
      PERMISSION_GROUPS.flatMap((group) =>
        group.permissions.map((permission) => permission.key)
      )
    );
  }

  function clearPermissions() {
    setSelectedPermissions([]);
  }

  async function savePermissions() {
    if (!selectedUser || isSuperUser) return;

    setSaving(true);
    setError("");
    setNotice("");

    try {
      const result = await updateUserPermissions(
        selectedUser.id,
        selectedPermissions
      );

      const updatedUser = result.user || result;

      setUsers((current) =>
        current.map((item) =>
          item.id === selectedUser.id
            ? {
                ...item,
                ...updatedUser,
                permissions:
                  updatedUser.permissions || selectedPermissions,
              }
            : item
        )
      );

      setNotice(
        `Permissions updated successfully for ${
          selectedUser.name || selectedUser.username
        }.`
      );
    } catch (err) {
      setError(err.message || "Could not save permissions.");
    } finally {
      setSaving(false);
    }
  }

  function handleUserChange(id) {
    setSelectedId(id);

    const selected = users.find(
      (item) => String(item.id) === String(id)
    );

    setSelectedPermissions(selected?.permissions || []);
    setError("");
    setNotice("");
  }

  return (
    <section className="panel panel--wide user-permissions-panel card">
      <div className="panel__head card-header">
        <div>
          <h2>User permissions</h2>
          <p className="muted">
            Grant or revoke access to individual modules and actions.
          </p>
        </div>

        <button
          className="btn btn-outline-secondary"
          onClick={loadUsers}
          disabled={loading || saving}
        >
          Refresh users
        </button>
      </div>

      <div className="card-body">
        {error && (
          <p
            className="account-request__message account-request__message--error alert alert-danger"
            role="alert"
          >
            {error}
          </p>
        )}

        {notice && (
          <p
            className="account-request__message account-request__message--success alert alert-success"
            role="status"
          >
            {notice}
          </p>
        )}

        {loading ? (
          <p className="empty">Loading users...</p>
        ) : users.filter(
            (item) => item.role !== "Super User"
          ).length === 0 ? (
          <p className="empty">
            No other user accounts are available yet.
          </p>
        ) : (
          <>
            <label className="field permission-user-select">
              <span className="form-label">Select user</span>

              <select
                className="form-select"
                value={selectedId}
                onChange={(event) =>
                  handleUserChange(event.target.value)
                }
              >
                {users
                  .filter((item) => item.role !== "Super User")
                  .map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name || item.username} — {item.role}
                    </option>
                  ))}
              </select>
            </label>

            {selectedUser && (
              <>
                <div className="permission-user-info card bg-light border-0">
                  <div className="card-body row g-3">
                    <div className="col-12 col-sm-6 col-lg-3">
                      <span className="permission-user-label">
                        Name
                      </span>
                      <strong>{selectedUser.name || "—"}</strong>
                    </div>

                    <div className="col-12 col-sm-6 col-lg-3">
                      <span className="permission-user-label">
                        Username
                      </span>
                      <strong>{selectedUser.username}</strong>
                    </div>

                    <div className="col-12 col-sm-6 col-lg-3">
                      <span className="permission-user-label">
                        Role
                      </span>
                      <strong>{selectedUser.role}</strong>
                    </div>

                    <div className="col-12 col-sm-6 col-lg-3">
                      <span className="permission-user-label">
                        Granted permissions
                      </span>

                      <strong>
                        <span className="badge text-bg-primary">
                          {selectedPermissions.length}
                        </span>
                      </strong>
                    </div>
                  </div>
                </div>

                <div className="permission-actions">
                  <button
                    className="btn btn-outline-secondary"
                    onClick={selectAllPermissions}
                  >
                    Grant all
                  </button>

                  <button
                    className="btn btn-outline-secondary"
                    onClick={clearPermissions}
                  >
                    Revoke all
                  </button>
                </div>

                {PERMISSION_GROUPS.map((group) => (
                  <div
                    className="permission-group card"
                    key={group.title}
                  >
                    <div className="card-header">
                      <h3>{group.title}</h3>
                    </div>

                    <div className="permission-list card-body row row-cols-1 row-cols-md-2 g-2">
                      {group.permissions.map((permission) => (
                        <label
                          className="permission-item form-check col"
                          key={permission.key}
                        >
                          <input
                            className="form-check-input"
                            type="checkbox"
                            checked={selectedPermissions.includes(
                              permission.key
                            )}
                            onChange={() =>
                              togglePermission(permission.key)
                            }
                          />

                          <span className="form-check-label">
                            {permission.label}
                          </span>
                        </label>
                      ))}
                    </div>
                  </div>
                ))}

                <div className="permission-save">
                  <button
                    className="btn btn-primary"
                    onClick={savePermissions}
                    disabled={saving}
                  >
                    {saving
                      ? "Saving..."
                      : "Save permissions"}
                  </button>
                </div>
              </>
            )}
          </>
        )}
      </div>
    </section>
  );
}

/* =========================================================
   DASHBOARD SMALL COMPONENTS
========================================================= */

function Stat({ label, value, note, tone }) {
  return (
    <div className={`stat ${tone ? "stat--" + tone : ""}`}>
      <p className="stat__value">{value}</p>
      <p className="stat__label">{label}</p>
      {note && <p className="stat__note">{note}</p>}
    </div>
  );
}

function ProgressBar({ value = 0, status = "Unknown" }) {
  const safeValue = Math.max(
    0,
    Math.min(100, Number(value) || 0)
  );

  return (
    <div
      className="bar"
      role="progressbar"
      aria-valuenow={safeValue}
      aria-valuemin={0}
      aria-valuemax={100}
    >
      <span
        className={`bar__fill bar__fill--${status
          .toLowerCase()
          .replace(/\s+/g, "-")}`}
        style={{ width: `${safeValue}%` }}
      />
    </div>
  );
}

/* =========================================================
   DASHBOARD HOME
========================================================= */


function DashboardHome({
  user,
  query,
  filter,
  onNavigate,
  onFilter,
}) {
  const [data, setData] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;

    getDashboard()
      .then((result) => {
        if (active) setData(result);
      })
      .catch((requestError) => {
        if (active) {
          setError(
            requestError.message ||
              "Could not load the dashboard."
          );
        }
      });

    return () => {
      active = false;
    };
  }, []);

  if (error) {
    return (
      <section className="panel">
        <h2>Dashboard unavailable</h2>
        <p className="empty" role="alert">
          {error}
        </p>
      </section>
    );
  }

  if (!data) {
    return (
      <section className="panel">
        <p className="empty">Loading dashboard...</p>
      </section>
    );
  }

  const allProjects = Array.isArray(data.projects)
    ? data.projects
    : [];

  const tasks = Array.isArray(data.tasks)
    ? data.tasks
    : [];

  const workload = Array.isArray(data.workload)
    ? data.workload
    : [];

  const activity = Array.isArray(data.activity)
    ? data.activity
    : [];

  const totals = data.totals || {};

  // Project search and filtering
  const filteredProjects = allProjects.filter((project) => {
    if (filter !== "All" && project.status !== filter) {
      return false;
    }

    return (
      !query.trim() ||
      (project.name || "")
        .toLowerCase()
        .includes(query.trim().toLowerCase())
    );
  });

  // Projects requiring attention
  const atRisk = allProjects.filter((project) =>
    ["At risk", "Delayed"].includes(project.status)
  );

  // Tasks due within the next seven days
  const dueThisWeek = tasks.filter((task) => {
    const remaining = daysLeft(task.due);
    return remaining >= 0 && remaining <= 7;
  }).length;

  return (
    <>
      {/* ATTENTION SECTION */}

      {atRisk.length > 0 && (
        <section
          className="attention"
          aria-label="Projects needing attention"
        >
          <h2 className="attention__title">
            {atRisk.length} projects need attention
          </h2>

          <ul className="attention__list">
            {atRisk.map((project) => (
              <li key={project.id}>
                <span className={statusClass(project.status)}>
                  {project.status}
                </span>

                <strong>{project.name}</strong>

                <span className="muted">
                  {daysLeft(project.due) < 0
                    ? `${Math.abs(daysLeft(project.due))} days past due`
                    : `due in ${daysLeft(project.due)} days`}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* ANIMATED STATISTICS */}

      <motion.section
        className="stats"
        aria-label="Dashboard summary"
        initial="hidden"
        animate="visible"
        variants={freestyleStaggerVariants}
      >
        <AnimatedStat
          label="Total Projects"
          value={totals.projects || 0}
          note={`${totals.active || 0} active projects`}
          tone="blue"
        />

        <AnimatedStat
          label="Open Tasks"
          value={totals.tasksOpen || 0}
          note="Across accessible projects"
          tone="purple"
        />

        <AnimatedStat
          label="Due This Week"
          value={dueThisWeek}
          note="Assigned tasks"
          tone="orange"
        />

        <AnimatedStat
          label="Overdue Projects"
          value={totals.overdue || 0}
          note="Projects requiring attention"
          tone="red"
        />
      </motion.section>

      {/* CHARTS AND VISUALIZATIONS */}

      <DashboardVisualizations
        projects={allProjects}
        tasks={tasks}
        workload={workload}
        userRole={user.role}
      />

      {/* MAIN DASHBOARD GRID */}

      <div className="grid">

        {/* PROJECTS OVERVIEW */}

        <section className="panel panel--wide">
          <div className="panel__head">
            <h2>Projects Overview</h2>

            <div
              className="chips"
              role="group"
              aria-label="Filter by status"
            >
              {[
                "All",
                "On track",
                "At risk",
                "Delayed",
                "Completed",
              ].map((status) => (
                <button
                  key={status}
                  className={`chip ${
                    filter === status ? "is-on" : ""
                  }`}
                  aria-pressed={filter === status}
                  onClick={() => onFilter(status)}
                >
                  {status}
                </button>
              ))}
            </div>
          </div>

          {filteredProjects.length === 0 ? (
            <p className="empty">
              No projects match this filter.
            </p>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Project</th>
                    <th>Lead</th>
                    <th>Status</th>
                    <th>Progress</th>
                    <th>Due Date</th>
                  </tr>
                </thead>

                <tbody>
                  {filteredProjects.map((project) => (
                    <tr key={project.id}>
                      <td>
                        <strong>{project.name}</strong>

                        <span className="muted block">
                          {project.done || 0} of{" "}
                          {project.tasks || 0} tasks done
                        </span>
                      </td>

                      <td>
                        {project.lead || "Unassigned"}
                      </td>

                      <td>
                        <span
                          className={statusClass(project.status)}
                        >
                          {project.status}
                        </span>
                      </td>

                      <td className="progress-cell">
                        <ProgressBar
                          value={project.progress}
                          status={project.status}
                        />

                        <span className="pct">
                          {project.progress || 0}%
                        </span>
                      </td>

                      <td>{fmtDate(project.due)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        {/* MY TASKS */}

        <section className="panel">
          <div className="panel__head">
            <h2>My Tasks</h2>

            <button
              className="link"
              onClick={() => onNavigate("tasks")}
            >
              View all tasks
            </button>
          </div>

          {tasks.length === 0 ? (
            <p className="empty">
              No tasks are assigned to you.
            </p>
          ) : (
            <ul className="tasks">
              {tasks.slice(0, 5).map((task) => {
                const remaining = daysLeft(task.due);

                return (
                  <li key={task.id} className="task">
                    <span aria-hidden="true">•</span>

                    <div>
                      <span className="task__title">
                        {task.title}
                      </span>

                      <span className="muted block">
                        {task.project}
                      </span>
                    </div>

                    <span
                      className={`due ${
                        remaining < 0 ? "due--hot" : ""
                      }`}
                    >
                      {remaining < 0
                        ? "Overdue"
                        : remaining === 0
                        ? "Today"
                        : fmtDate(task.due)}
                    </span>
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        {/* RECENT TICKET ACTIVITY */}

        <section className="panel">
          <div className="panel__head">
            <h2>Recent Ticket Activity</h2>
          </div>

          {activity.length === 0 ? (
            <p className="empty">
              No ticket activity yet.
            </p>
          ) : (
            <ul className="feed">
              {activity.map((item) => (
                <li key={item.id}>
                  <p>
                    <strong>{item.who}</strong>{" "}
                    {item.what}{" "}
                    <em>{item.target}</em>
                  </p>

                  <span className="muted">
                    {item.timestamp
                      ? new Date(item.timestamp).toLocaleString()
                      : "Time unavailable"}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>

      </div>
    </>
  );
}

/* =========================================================
   SIDEBAR
========================================================= */

function Sidebar({ user, active, onNavigate }) {
  const permissions = user.permissions || [];
  const isSuperUser = user.role === "Super User";

  const initials = (user.name || user.username || "U")
    .split(" ")
    .map((word) => word[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();

  const visibleNav = NAV.filter((item) => {
    if (item.superUserOnly) return isSuperUser;

    if (isSuperUser) return true;

    return item.alwaysVisible ||
      permissions.includes(item.permission);
  });

  return (
    <aside className="sidebar d-flex">
      <div className="brand">
        <img
          className="brand__image"
          src={brandImage}
          alt="OpsVista"
        />
      </div>

      <nav aria-label="Main">
        <ul className="sidebar-nav list-group list-group-flush">
          {visibleNav.map((item) => (
            <li
              className="sidebar-nav__item list-group-item"
              key={item.key}
            >
              <motion.button
                className={`nav__item ${
                  active === item.key ? "is-active" : ""
                }`}
                aria-current={
                  active === item.key ? "page" : undefined
                }
                onClick={() => onNavigate(item.key)}
                whileHover={{ x: 3 }}
                whileTap={{ scale: 0.97 }}
                transition={freestyleSpring}
              >
                {active === item.key && (
                  <motion.span
                    className="nav__active-indicator"
                    layoutId="sidebar-active-indicator"
                    transition={freestyleSpring}
                    aria-hidden="true"
                  />
                )}
                {(() => {
                  const Icon = NAV_ICONS[item.key] || BriefcaseBusiness;
                  return <Icon className="nav__icon" size={18} strokeWidth={1.8} aria-hidden="true" />;
                })()}
                {item.label}
              </motion.button>
            </li>
          ))}
        </ul>
      </nav>

      <div className="sidebar__foot card">
        <div
          className="avatar rounded-circle bg-warning text-dark fw-bold"
          aria-hidden="true"
        >
          {initials}
        </div>

        <div className="sidebar__who">
          <p className="sidebar__user">
            {user.name || user.username}
          </p>

          <p className="sidebar__role">
            {user.role}
          </p>
        </div>
      </div>
    </aside>
  );
}

/* =========================================================
   MAIN DASHBOARD
========================================================= */

export default function Dashboard({
  user,
  onLogout,
  onUserUpdate,
}) {
  const role = user.role;
  const permissions = user.permissions || [];
  const isSuperUser = role === "Super User";

  const visibleNav = NAV.filter((item) => {
    if (item.superUserOnly) return isSuperUser;

    if (item.alwaysVisible) return true;

    return isSuperUser ||
      permissions.includes(item.permission);
  });

  const firstPage = visibleNav[0]?.key || "no-access";

  const [active, setActive] = useState(firstPage);
  const [filter, setFilter] = useState("All");
  const [query, setQuery] = useState("");
  const [darkMode, setDarkMode] = useState(() => {
    try {
      const savedTheme = localStorage.getItem("pt.theme");
      return savedTheme === null || savedTheme === "dark";
    } catch {
      return true;
    }
  });
  const notifications = useNotifications(user);

  useEffect(() => {
    try {
      localStorage.setItem("pt.theme", darkMode ? "dark" : "light");
    } catch {
      /* theme preference just won't persist */
    }
  }, [darkMode]);

  useEffect(() => {
    if (!visibleNav.some((item) => item.key === active)) {
      setActive(firstPage);
    }
  }, [active, firstPage]);

  const pageTitles = {
    dashboard: "Dashboard",
    projects: "Projects",
    "project-management": "Project Management",
    tasks: "My Tasks",
    tickets: "Ticket Management",
    reports: "Reports & Analytics",
    "project-analytics": "Project Analytics",
    users: "Account Requests",
    permissions: "User Permissions",
    notifications: "Notifications",
    settings: "Settings",
    "no-access": "Access Not Assigned",
    "user-management": "User Management",
  };

  return (
    <div
      className={`shell container-fluid p-0 min-vh-100 ${
        darkMode ? "theme-dark" : ""
      }`}
    >
      <Sidebar
        user={user}
        active={active}
        onNavigate={setActive}
      />

      <div className="main d-flex flex-column">
        {/* TOPBAR */}

        <header className="topbar d-flex justify-content-between align-items-center">
          <div>
            <h1 className="topbar__title">
              {pageTitles[active] || "Dashboard"}
            </h1>

            <p className="topbar__sub">
              {new Date().toLocaleDateString(undefined, {
                weekday: "long",
                day: "numeric",
                month: "long",
                year: "numeric",
              })}
            </p>
          </div>

          <div className="topbar__tools d-flex align-items-center flex-wrap gap-3">
            {active === "dashboard" && (
              <>
                <label className="search">
                  <span className="sr-only">
                    Search projects
                  </span>

                  <input
                    type="search"
                    placeholder="Search projects"
                    value={query}
                    onChange={(event) =>
                      setQuery(event.target.value)
                    }
                  />
                </label>

                {canUser(user, "projects.create") && (
                  <button
                    className="btn btn--primary"
                    onClick={() => setActive("projects")}
                  >
                    New Project
                  </button>
                )}
              </>
            )}

            <button
              className="btn btn-outline-secondary topbar__icon-button"
              onClick={() => setActive("notifications")}
              aria-label={`Notifications, ${notifications.unread} unread`}
            >
              <Bell size={17} aria-hidden="true" />
              <span>Notifications</span>
              {notifications.unread > 0 && (
                <span className="bell-badge">{notifications.unread}</span>
              )}
            </button>

            <button
              className="btn btn-outline-secondary topbar__icon-button"
              onClick={() =>
                setDarkMode((current) => !current)
              }
              aria-pressed={darkMode}
            >
              {darkMode ? <Sun size={17} aria-hidden="true" /> : <Moon size={17} aria-hidden="true" />}
              <span>{darkMode ? "Light mode" : "Dark mode"}</span>
            </button>

            <button
              className="topbar__logout topbar__icon-button btn btn-outline-secondary"
              onClick={onLogout}
            >
              <LogOut size={17} aria-hidden="true" />
              <span>Log out</span>
            </button>
          </div>
        </header>

        {/* PAGE CONTENT */}

        <main className="content container-fluid">
          <AnimatePresence mode="wait" initial={false}>
          <motion.div
            className="page-transition"
            key={active}
            variants={freestylePageVariants}
            initial="hidden"
            animate="visible"
            exit="exit"
          >
          {active === "users" && isSuperUser ? (
            <AccountRequests user={user} />
          ) : active === "user-management" && isSuperUser ? (
            <UserManagement />
          ) : active === "permissions" && isSuperUser ? (
            <UserPermissions />
          ) : active === "reports" &&
            canUser(user, "reports.view") ? (
            <ReportsPage user={user} />
          ) : active === "project-analytics" &&
            canUser(user, "reports.view") ? (
            <ProjectAnalyticsPage user={user} />
          ) : active === "project-management" &&
            canUser(user, "projects.view") ? (
            <ProjectManagementPage user={user} />
          ) : active === "dashboard" &&
            canUser(user, "dashboard.view") ? (
            <DashboardHome
              user={user}
              query={query}
              filter={filter}
              onNavigate={setActive}
              onFilter={setFilter}
            />
          ) : active === "notifications" ? (
            <NotificationsPage
              notifications={notifications}
              onNavigate={setActive}
            />
          ) : active === "no-access" ? (
            <section className="panel">
              <h2>No access assigned</h2>

              <p className="empty">
                Please contact your Super User to request access
                to a module.
              </p>
            </section>
          ) : ["tickets", "settings", "tasks", "projects"].includes(
              active
            ) ? (
            <WorkspacePage
              section={active}
              user={user}
              onUserUpdate={onUserUpdate}
            />
          ) : (
            <section className="panel">
              <h2>Page not found</h2>
            </section>
          )}
          </motion.div>
          </AnimatePresence>
        </main>
      </div>
    </div>
  );
}
