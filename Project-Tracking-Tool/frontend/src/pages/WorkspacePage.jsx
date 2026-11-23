import { useEffect, useState } from "react";
import {
  changeCurrentPassword,
  createProject,
  deleteProject,
  getProjects,
  updateCurrentProfile,
  updateProject,
} from "../auth/authService.js";
import TasksBoard from "./TasksBoard.jsx";
import TicketsBoard from "./TicketsBoard.jsx";

const PROJECT_STATUS_CLASSES = {
  "On track": "project-status--on-track",
  "At risk": "project-status--at-risk",
  Delayed: "project-status--delayed",
  Completed: "project-status--completed",
};

function can(user, permission) {
  return user.role === "Super User" || (user.permissions || []).includes(permission);
}

function PageMessage({ error, notice }) {
  return (
    <>
      {error && <p className="account-request__message account-request__message--error" role="alert">{error}</p>}
      {notice && <p className="account-request__message account-request__message--success" role="status">{notice}</p>}
    </>
  );
}

export default function WorkspacePage(props) {
  if (props.section === "tasks") return <TasksBoard user={props.user} />;
  if (props.section === "tickets") return <TicketsBoard user={props.user} />;
  return <ProjectsAndSettings {...props} />;
}

function ProjectsAndSettings({ section, user, onUserUpdate }) {
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [projectForm, setProjectForm] = useState({ name: "", description: "", status: "On track", due: "", leadId: String(user.id || "") });
  const [profileForm, setProfileForm] = useState({ name: user.name || "", email: user.email || "" });
  const [passwordForm, setPasswordForm] = useState({ currentPassword: "", newPassword: "", confirmPassword: "" });
  const [editingProjectId, setEditingProjectId] = useState("");

  async function load() {
    setError("");
    setLoading(true);
    try {
      if (section === "projects") {
        setProjects(await getProjects());
      }
    } catch (requestError) {
      setError(requestError.message || `Could not load ${section}.`);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, [section]);

  async function submitProject(event) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");
    const body = {
      ...projectForm,
      leadId: projectForm.leadId ? Number(projectForm.leadId) : null,
      due: projectForm.due || null,
    };
    try {
      if (editingProjectId) await updateProject(editingProjectId, body);
      else await createProject(body);
      setProjectForm({ name: "", description: "", status: "On track", due: "", leadId: String(user.id || "") });
      setEditingProjectId("");
      setNotice(editingProjectId ? "Project updated." : "Project created.");
      await load();
    } catch (requestError) {
      setError(requestError.message || "Could not save the project.");
    } finally {
      setSaving(false);
    }
  }

  function editProject(project) {
    setEditingProjectId(String(project.id));
    setProjectForm({
      name: project.name,
      description: project.description || "",
      status: project.status,
      due: project.due || "",
      leadId: "",
    });
  }

  async function removeProject(project) {
    if (!window.confirm(`Delete "${project.name}"?`)) return;
    try {
      await deleteProject(project.id);
      setNotice("Project deleted.");
      await load();
    } catch (requestError) {
      setError(requestError.message || "Could not delete the project.");
    }
  }

  async function submitProfile(event) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const updated = await updateCurrentProfile(profileForm);
      onUserUpdate(updated);
      setProfileForm({ name: updated.name, email: updated.email });
      setNotice("Profile settings saved.");
    } catch (requestError) {
      setError(requestError.message || "Could not save your profile.");
    } finally {
      setSaving(false);
    }
  }

  async function submitPasswordChange(event) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setNotice("");
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      setError("New password and confirmation do not match.");
      setSaving(false);
      return;
    }
    try {
      const response = await changeCurrentPassword(passwordForm);
      setPasswordForm({ currentPassword: "", newPassword: "", confirmPassword: "" });
      setNotice(response.message || "Password changed successfully.");
    } catch (requestError) {
      setError(requestError.message || "Could not change your password.");
    } finally {
      setSaving(false);
    }
  }

  if (section === "settings") {
    return (
      <section className="panel panel--wide workspace-panel">
        <div className="panel__head"><div><h2>Settings</h2><p className="muted">Manage your profile and password.</p></div></div>
        <PageMessage error={error} notice={notice} />
        <div className="settings-sections">
          <form className="workspace-form workspace-form--narrow" onSubmit={submitProfile}>
            <h3>Profile</h3>
            <label className="field"><span>Full name</span><input required maxLength={120} value={profileForm.name} onChange={(event) => setProfileForm({ ...profileForm, name: event.target.value })} /></label>
            <label className="field"><span>Email</span><input type="email" required maxLength={254} value={profileForm.email} onChange={(event) => setProfileForm({ ...profileForm, email: event.target.value })} /></label>
            <button className="btn btn--primary" disabled={saving}>{saving ? "Saving..." : "Save profile"}</button>
          </form>
          <form className="workspace-form workspace-form--narrow workspace-form--password" onSubmit={submitPasswordChange}>
            <h3>Change password</h3>
            <label className="field"><span>Current password</span><input type="password" autoComplete="current-password" required maxLength={72} value={passwordForm.currentPassword} onChange={(event) => setPasswordForm({ ...passwordForm, currentPassword: event.target.value })} /></label>
            <label className="field"><span>New password (12-72 characters)</span><input type="password" autoComplete="new-password" required minLength={12} maxLength={72} value={passwordForm.newPassword} onChange={(event) => setPasswordForm({ ...passwordForm, newPassword: event.target.value })} /></label>
            <label className="field"><span>Confirm new password</span><input type="password" autoComplete="new-password" required minLength={12} maxLength={72} value={passwordForm.confirmPassword} onChange={(event) => setPasswordForm({ ...passwordForm, confirmPassword: event.target.value })} /></label>
            <button className="btn btn--primary" disabled={saving}>{saving ? "Updating..." : "Change password"}</button>
          </form>
        </div>
      </section>
    );
  }

  return (
    <section className={`panel panel--wide workspace-panel${section === "projects" ? " projects-workspace-panel" : ""}`}>
      <div className={`panel__head${section === "projects" ? " projects-panel__head" : ""}`}>
        <div><h2>Projects</h2><p className="muted">Records are stored in PostgreSQL and scoped by your account permissions.</p></div>
        <button className="btn btn-outline-secondary" onClick={load} disabled={loading}>Refresh</button>
      </div>
      <PageMessage error={error} notice={notice} />
      {loading ? <p className="empty">Loading {section}...</p> : (
        <>
          {section === "projects" && (
            <>
              {(editingProjectId ? can(user, "projects.edit") : can(user, "projects.create")) && (
                <form className="projects-form card mb-4" onSubmit={submitProject}>
                  <div className="card-body p-3 p-lg-4">
                    <div className="row g-3 align-items-end">
                      <label className="field col-12 col-md-6 col-xl-4">
                        <span className="form-label">Project name</span>
                        <input className="form-control" required maxLength={160} value={projectForm.name} onChange={(event) => setProjectForm({ ...projectForm, name: event.target.value })} />
                      </label>
                      <label className="field col-12 col-md-6 col-xl-3">
                        <span className="form-label">Status</span>
                        <select className="form-select" value={projectForm.status} onChange={(event) => setProjectForm({ ...projectForm, status: event.target.value })}>{["On track", "At risk", "Delayed", "Completed"].map((item) => <option key={item}>{item}</option>)}</select>
                      </label>
                      <label className="field col-12 col-md-6 col-xl-3">
                        <span className="form-label">Due date</span>
                        <input className="form-control" type="date" value={projectForm.due} onChange={(event) => setProjectForm({ ...projectForm, due: event.target.value })} />
                      </label>
                      <label className="field col-12">
                        <span className="form-label">Description</span>
                        <textarea className="form-control" rows="2" maxLength={5000} value={projectForm.description} onChange={(event) => setProjectForm({ ...projectForm, description: event.target.value })} />
                      </label>
                      <div className="col-12 d-flex flex-wrap justify-content-end gap-2">
                        <button className="btn btn-primary btn--primary px-4" disabled={saving}>{saving ? "Saving..." : editingProjectId ? "Update project" : "Create project"}</button>
                        {editingProjectId && <button className="btn btn-outline-secondary" type="button" onClick={() => { setEditingProjectId(""); setProjectForm({ name: "", description: "", status: "On track", due: "", leadId: String(user.id || "") }); }}>Cancel</button>}
                      </div>
                    </div>
                  </div>
                </form>
              )}
              {projects.length ? (
                <div className="projects-table-wrap table-responsive rounded-3 border">
                  <table className="table table-hover align-middle mb-0 projects-table">
                    <thead>
                      <tr><th scope="col">Project</th><th scope="col">Lead</th><th scope="col">Status</th><th scope="col">Progress</th><th scope="col">Due date</th><th scope="col">Tasks</th><th scope="col">Actions</th></tr>
                    </thead>
                    <tbody>
                      {projects.map((project) => {
                        const progress = Math.min(100, Math.max(0, Number(project.progress) || 0));
                        return (
                          <tr key={project.id}>
                            <td>
                              <strong className="d-block">{project.name}</strong>
                              {project.description && <span className="project-description d-block small mt-1" title={project.description}>{project.description}</span>}
                            </td>
                            <td>{project.lead || "—"}</td>
                            <td><span className={`badge rounded-pill project-status ${PROJECT_STATUS_CLASSES[project.status] || "project-status--default"}`}>{project.status}</span></td>
                            <td>
                              <div className="d-flex align-items-center gap-2">
                                <div className="progress flex-grow-1" role="progressbar" aria-label={`${project.name} progress`} aria-valuenow={progress} aria-valuemin="0" aria-valuemax="100">
                                  <div className="progress-bar" style={{ width: `${progress}%` }} />
                                </div>
                                <span className="small fw-semibold">{progress}%</span>
                              </div>
                            </td>
                            <td>{project.due || "—"}</td>
                            <td>{project.done}/{project.tasks}</td>
                            <td>
                              <div className="project-actions d-flex flex-wrap gap-2">
                                {can(user, "projects.edit") && <button className="btn btn-sm btn-outline-secondary" onClick={() => editProject(project)}>Edit</button>}
                                {can(user, "projects.delete") && <button className="btn btn-sm btn-outline-danger" onClick={() => removeProject(project)}>Delete</button>}
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              ) : <p className="alert alert-light border mb-0">No projects are available.</p>}
            </>
          )}

        </>
      )}
    </section>
  );
}
