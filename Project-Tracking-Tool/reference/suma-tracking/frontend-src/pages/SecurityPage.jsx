import { Fragment, useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { useCan } from '../permissions.js'
import { Confirm, Drawer, Field, PageHead, Toast } from '../components.jsx'
import { FIX_MESSAGE, rules } from '../validation.js'
import { fmtDateTime } from '../format.js'

const RIGHTS = {
  SUPER_ADMIN: 'Everything: clients, projects, employees, imports, settings',
  MANAGEMENT_VIEWER: 'Sees all dashboards and reports, changes nothing',
  CLIENT_ADMIN: 'Manages their clients and those projects',
  PROJECT_MANAGER: 'Manages their projects, team and imports',
  TEAM_LEADER: 'Sees their projects, assigns tickets',
  L3_SUPPORT: 'Works tickets of their projects',
  L2_SUPPORT: 'Works tickets of their projects',
  L1_SUPPORT: 'Works tickets of their projects',
}

export default function SecurityPage() {
  const can = useCan()
  const canRoles = can('ROLE_MANAGE')
  const canAudit = can('AUDIT_VIEW')
  const [tab, setTab] = useState(canRoles ? 'matrix' : 'audit')
  const [roles, setRoles] = useState([])
  const [audit, setAudit] = useState([])
  const [entity, setEntity] = useState('')
  const [error, setError] = useState('')

  const [creating, setCreating] = useState(false)
  const [toast, setToast] = useState('')
  const loadRoles = useCallback(() => { if (canRoles) api.roles().then(setRoles).catch((e) => setError(e.message)) }, [canRoles])
  useEffect(() => { loadRoles() }, [loadRoles])
  useEffect(() => {
    if (tab !== 'audit') return
    api.audit({ entityType: entity, limit: 200 }).then(setAudit).catch((e) => setError(e.message))
  }, [tab, entity])

  return (
    <div className="page">
      <PageHead title="Security" text="Roles, exactly what each role may do, and the audit log of every change.">
        {canRoles && tab === 'roles' && <button className="btn primary" type="button" onClick={() => setCreating(true)}>Create role</button>}
      </PageHead>
      <div className="segmented" role="tablist">
        {canRoles && <button type="button" role="tab" aria-selected={tab === 'matrix'} className={tab === 'matrix' ? 'on' : ''} onClick={() => setTab('matrix')}>Permission matrix</button>}
        {canRoles && <button type="button" role="tab" aria-selected={tab === 'roles'} className={tab === 'roles' ? 'on' : ''} onClick={() => setTab('roles')}>Roles</button>}
        {canAudit && <button type="button" role="tab" aria-selected={tab === 'audit'} className={tab === 'audit' ? 'on' : ''} onClick={() => setTab('audit')}>Audit log</button>}
      </div>
      {tab === 'matrix' && canRoles && <PermissionMatrix onToast={setToast} />}
      {error && <div className="alert error" role="alert">{error}</div>}
      {tab === 'roles' && (
        <section className="panel flush">
          <div className="table-wrap">
            <table className="table grid-table">
              <thead><tr><th scope="col">Role</th><th scope="col">Group</th><th scope="col">What they can do</th></tr></thead>
              <tbody>
                {roles.map((r) => (
                  <tr key={r.code}>
                    <td className="strong">{r.name}</td>
                    <td>{r.userGroup.replace('_', ' ').toLowerCase()}</td>
                    <td>{RIGHTS[r.code] || r.description || (r.userGroup === 'SUPPORT_USER' ? 'Works tickets of their projects' : 'Leads the projects they are on')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="fine pad">A new role (for example Project Lead or Manager) works like one of two types: a lead type sees and leads the projects they are on; an agent type works tickets of their projects.</p>
        </section>
      )}
      {tab === 'audit' && (
        <section className="panel flush">
          <div className="toolbar flat">
            <label className="field">
              <span>Show</span>
              <select value={entity} onChange={(e) => setEntity(e.target.value)}>
                <option value="">Everything</option>
                <option value="CLIENT">Clients</option>
                <option value="PROJECT">Projects</option>
                <option value="USER">Employees</option>
                <option value="PROJECT_ASSIGNMENT">Team changes</option>
                <option value="TICKET">Tickets</option>
                <option value="IMPORT_BATCH">Imports</option>
                <option value="ROLE">Roles and permissions</option>
                <option value="SETTINGS">Settings</option>
              </select>
            </label>
          </div>
          <div className="table-wrap">
            <table className="table grid-table">
              <thead><tr><th scope="col">When</th><th scope="col">Who</th><th scope="col">What</th><th scope="col">Record</th><th scope="col">From</th></tr></thead>
              <tbody>
                {audit.map((a) => (
                  <tr key={a.id}>
                    <td className="nowrap">{fmtDateTime(a.occurredAt)}</td>
                    <td>{a.actorEmail || 'System'}</td>
                    <td>{a.action.replaceAll('_', ' ').toLowerCase()}</td>
                    <td>{a.entityType.replaceAll('_', ' ').toLowerCase()} {a.entityId}</td>
                    <td className="muted">{a.source.toLowerCase()}</td>
                  </tr>
                ))}
                {audit.length === 0 && <tr><td colSpan={5} className="empty-row">Nothing recorded yet.</td></tr>}
              </tbody>
            </table>
          </div>
        </section>
      )}
      {creating && (
        <RoleForm onClose={() => setCreating(false)} onSaved={(r) => { setCreating(false); setToast(`Role ${r.name} created`); loadRoles() }} />
      )}
      <Toast text={toast} onDone={() => setToast('')} />
    </div>
  )
}

function RoleForm({ onClose, onSaved }) {
  const [f, setF] = useState({ name: '', userGroup: 'PROJECT_MANAGER', description: '' })
  const [error, setError] = useState('')
  const [nameErr, setNameErr] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })

  async function save(e) {
    e.preventDefault()
    setError('')
    const nameError = rules.title('Role name', true)(f.name)
    const descError = rules.maxLen('Description', 500)(f.description)
    setNameErr(nameError)
    if (nameError || descError) { setError(nameError ? FIX_MESSAGE : descError); return }
    setBusy(true)
    try {
      onSaved(await api.createRole({ ...f, name: f.name.trim(), description: f.description.trim() }))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Drawer title="Create role" onClose={onClose}
      footer={(
        <>
          <button className="btn ghost" type="button" onClick={onClose}>Cancel</button>
          <button className="btn primary" type="submit" form="role-form" disabled={busy}>{busy ? 'Saving...' : 'Add'}</button>
        </>
      )}>
      <form id="role-form" className="form" onSubmit={save} noValidate>
        <Field label="Role name *" error={nameErr}>
          <input value={f.name} onChange={(e) => { set('name')(e); if (nameErr) setNameErr(rules.title('Role name', true)(e.target.value)) }}
            onBlur={() => setNameErr(rules.title('Role name', true)(f.name))} maxLength={100} placeholder="e.g. Project Lead" aria-invalid={Boolean(nameErr)} />
        </Field>
        <Field label="Works like">
          <select value={f.userGroup} onChange={set('userGroup')}>
            <option value="PROJECT_MANAGER">Lead or manager: sees and leads the projects they are on</option>
            <option value="SUPPORT_USER">Support agent: works tickets of their projects</option>
          </select>
        </Field>
        <Field label="Description"><textarea rows={3} value={f.description} onChange={set('description')} maxLength={500} /></Field>
        {error && <div className="alert error" role="alert">{error}</div>}
      </form>
    </Drawer>
  )
}

// ================================================================ Step 39: permission matrix

/**
 * Every role x every permission. Ticked = the role may do it.
 * Super Admin is always complete and locked. A permission marked "fixed" can be removed from a role,
 * but can only be given to the roles that have it by default, because the server's own rules for it are fixed.
 * Saving writes to the audit log. "Reset" puts a role back to its defaults.
 */
function PermissionMatrix({ onToast }) {
  const [data, setData] = useState(null)
  const [draft, setDraft] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [resetting, setResetting] = useState(null)

  const load = useCallback(() => {
    setError('')
    api.rbacMatrix().then((m) => {
      setData(m)
      const d = {}
      m.roles.forEach((r) => { d[r.code] = new Set(r.permissions) })
      setDraft(d)
    }).catch((e) => setError(e.message))
  }, [])
  useEffect(() => { load() }, [load])

  if (error && !data) return <div className="alert error" role="alert">{error}</div>
  if (!data) return <div className="panel"><p className="muted">Loading the permission matrix...</p></div>

  const modules = []
  data.permissions.forEach((p) => {
    let m = modules.find((x) => x.name === p.module)
    if (!m) { m = { name: p.module, items: [] }; modules.push(m) }
    m.items.push(p)
  })
  const changedRoles = data.roles.filter((r) => {
    if (r.locked) return false
    const a = draft[r.code]
    return a.size !== r.permissions.length || r.permissions.some((p) => !a.has(p))
  })

  function toggle(role, perm) {
    const next = new Set(draft[role.code])
    if (next.has(perm.code)) next.delete(perm.code)
    else next.add(perm.code)
    setDraft({ ...draft, [role.code]: next })
  }

  async function save() {
    setBusy(true)
    setError('')
    try {
      for (const r of changedRoles) {
        await api.saveRolePermissions(r.code, [...draft[r.code]])
      }
      onToast(`Permissions saved for ${changedRoles.map((r) => r.name).join(', ')}`)
      load()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  async function reset(role) {
    setBusy(true)
    setError('')
    try {
      await api.resetRolePermissions(role.code)
      onToast(`${role.name} is back to its default permissions`)
      setResetting(null)
      load()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="rbac-bar">
        <div className="rbac-legend">
          <span><span className="rbac-box on" aria-hidden="true" /> Allowed</span>
          <span><span className="rbac-box" aria-hidden="true" /> Not allowed</span>
          <span><span className="rbac-box fixed" aria-hidden="true" /> Fixed: only roles that have it by default</span>
          <span><span className="rbac-dot" aria-hidden="true" /> Changed from the default</span>
        </div>
        <div className="rbac-actions">
          {changedRoles.length > 0 && <span className="muted small">{changedRoles.length} role{changedRoles.length > 1 ? 's' : ''} changed</span>}
          <button className="btn ghost" type="button" disabled={busy || !changedRoles.length} onClick={load}>Undo</button>
          <button className="btn primary" type="button" disabled={busy || !changedRoles.length} onClick={save}>{busy ? 'Saving...' : 'Save changes'}</button>
        </div>
      </div>
      {error && <div className="alert error" role="alert">{error}</div>}
      <section className="panel flush">
        <div className="table-wrap rbac-wrap">
          <table className="rbac-table">
            <thead>
              <tr>
                <th scope="col" className="rbac-perm-col">Permission</th>
                {data.roles.map((r) => (
                  <th scope="col" key={r.code} className="rbac-role">
                    <div className="rbac-role-name">{r.name}</div>
                    <div className="rbac-role-meta">
                      {r.activeUsers} {r.activeUsers === 1 ? 'person' : 'people'}
                      {!r.builtIn && <span className="tag">Custom</span>}
                    </div>
                    {r.locked ? <div className="rbac-role-meta"><span className="tag">Always all</span></div>
                      : r.customised && (
                        <button type="button" className="link-btn" onClick={() => setResetting(r)} disabled={busy}>Reset</button>
                      )}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {modules.map((m) => (
                <Fragment key={m.name}>
                  <tr className="rbac-module"><th scope="rowgroup" colSpan={data.roles.length + 1}>{m.name}</th></tr>
                  {m.items.map((p) => (
                    <tr key={p.code}>
                      <th scope="row" className="rbac-perm-col">
                        <div className="rbac-perm-name">{p.name}{!p.extendable && <span className="rbac-fixed-tag">fixed</span>}</div>
                        <div className="rbac-perm-desc">{p.description}</div>
                      </th>
                      {data.roles.map((r) => {
                        const on = r.locked || draft[r.code].has(p.code)
                        const available = r.locked || p.extendable || r.defaults.includes(p.code)
                        const isDefault = r.defaults.includes(p.code)
                        const differs = !r.locked && on !== isDefault
                        return (
                          <td key={r.code} className="rbac-cell">
                            {available ? (
                              <button type="button" role="checkbox" aria-checked={on} disabled={r.locked || busy}
                                className={`rbac-box${on ? ' on' : ''}${r.locked ? ' locked' : ''}`}
                                aria-label={`${r.name}: ${p.name}`} onClick={() => toggle(r, p)}>
                                {on && <svg width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 12l5 5L20 7" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" /></svg>}
                              </button>
                            ) : (
                              <span className="rbac-box fixed" title="Fixed: the server's rules allow this only for roles that have it by default" aria-label={`${r.name}: ${p.name} not available`} />
                            )}
                            {differs && <span className="rbac-dot" title="Changed from the default" />}
                          </td>
                        )
                      })}
                    </tr>
                  ))}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      <p className="fine">
        Permissions decide WHAT a role may do. WHICH records a person sees is decided by the projects they are on:
        everyone except Super Admin and Management Viewer sees only the clients, projects and tickets they belong to.
        The server checks every request, so typing a page address or calling the API directly does not get around it.
      </p>
      {resetting && (
        <Confirm
          title={`Reset ${resetting.name}?`}
          message={`${resetting.name} gets its default permissions back. ${resetting.activeUsers} people have this role.`}
          confirmLabel="Reset"
          onCancel={() => setResetting(null)}
          onConfirm={() => reset(resetting)}
        />
      )}
    </>
  )
}