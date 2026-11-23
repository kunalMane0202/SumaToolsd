import { useCallback, useEffect, useMemo, useState } from 'react'
import { api } from '../api.js'
import { useCan } from '../permissions.js'
import { Confirm, Drawer, EditIcon, Field, IconButton, PageHead, Toast } from '../components.jsx'
import { fmtMinutes, fmtNumber } from '../format.js'
import { FIX_MESSAGE, digitsOnly, rules, useValidatedForm } from '../validation.js'

const DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']
const EMPTY = {
  employeeCode: '', fullName: '', email: '', contactNumber: '', designation: '', department: '', skillSet: '',
  roleCode: 'L1_SUPPORT', reportingManagerId: '', shiftStart: '', shiftEnd: '', weeklyOff: [],
}

/**
 * Step 39: USER_MANAGE (Super Admin) gets the full employee screen.
 * WORKLOAD_VIEW (leads, Management Viewer) gets a read-only view of the people on their own projects.
 */
export default function TeamPage() {
  const can = useCan()
  return can('USER_MANAGE') ? <TeamAdmin /> : <TeamView />
}

function TeamView() {
  const [rows, setRows] = useState(null)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  useEffect(() => { api.workload().then((r) => setRows(r || [])).catch((e) => setError(e.message)) }, [])
  const shown = useMemo(() => {
    const q = query.trim().toLowerCase()
    return (rows || []).filter((u) => !q || `${u.fullName} ${u.employeeCode} ${u.roleName}`.toLowerCase().includes(q))
  }, [rows, query])
  return (
    <div className="page">
      <PageHead title="Team Management" text="The people on your projects and their open work. Only the Super Admin adds or changes employees.">
        <input className="search" type="search" placeholder="Search name, code or role" value={query} onChange={(e) => setQuery(e.target.value)} />
      </PageHead>
      {error && <div className="alert error" role="alert">{error}</div>}
      <section className="panel flush">
        <div className="table-wrap">
          <table className="table grid-table">
            <thead>
              <tr>
                <th scope="col">Employee</th><th scope="col">Code</th><th scope="col">Role</th><th scope="col">Projects</th>
                <th scope="col" className="num">Open</th><th scope="col" className="num">Waiting</th>
                <th scope="col" className="num">Escalated open</th><th scope="col" className="num">Done, 30 days</th>
                <th scope="col" className="num">Oldest open</th><th scope="col">Status</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((w) => (
                <tr key={w.userId} className={w.status !== 'ACTIVE' ? 'removed' : ''}>
                  <td className="strong">{w.fullName}</td>
                  <td>{w.employeeCode || '-'}</td>
                  <td>{w.roleName}</td>
                  <td>{Array.isArray(w.projects) ? w.projects.join(', ') : (w.projects || '-')}</td>
                  <td className="num">{fmtNumber(w.open || 0)}</td>
                  <td className="num">{fmtNumber(w.waiting || 0)}</td>
                  <td className="num">{fmtNumber(w.escalatedOpen || 0)}</td>
                  <td className="num">{fmtNumber(w.doneLast30Days || 0)}</td>
                  <td className="num">{w.oldestOpenMinutes != null ? fmtMinutes(w.oldestOpenMinutes) : '-'}</td>
                  <td>{w.status === 'ACTIVE' ? <span className="tag ok">Active</span> : <span className="tag">Inactive</span>}</td>
                </tr>
              ))}
              {rows && shown.length === 0 && <tr><td colSpan={10} className="empty-row">Nobody found on your projects.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}

function TeamAdmin() {
  const [users, setUsers] = useState([])
  const [roles, setRoles] = useState([])
  const [projects, setProjects] = useState([])
  const [workload, setWorkload] = useState({})
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(null)
  const [toggling, setToggling] = useState(null)
  const [toast, setToast] = useState('')
  const [query, setQuery] = useState('')

  const load = useCallback(() => {
    api.users().then((u) => setUsers(u || [])).catch((e) => setError(e.message))
  }, [])

  useEffect(() => {
    load()
    api.roles().then(setRoles).catch(() => setRoles([]))
    api.projects().then((p) => setProjects((p || []).filter((x) => x.status === 'ACTIVE'))).catch(() => setProjects([]))
    api.workload().then((rows) => {
      const m = {}
      ;(rows || []).forEach((w) => { m[w.userId] = w })
      setWorkload(m)
    }).catch(() => setWorkload({}))
  }, [load])

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase()
    return users.filter((u) => !q || `${u.fullName} ${u.email} ${u.employeeCode} ${u.roleName}`.toLowerCase().includes(q))
  }, [users, query])

  async function toggle() {
    const u = toggling
    try {
      if (u.status === 'ACTIVE') await api.deactivateUser(u.id)
      else await api.activateUser(u.id)
      setToast(u.status === 'ACTIVE' ? `${u.fullName} deactivated` : `${u.fullName} activated`)
      setToggling(null)
      load()
    } catch (e) {
      setError(e.message)
      setToggling(null)
    }
  }

  return (
    <div className="page">
      <PageHead title="Team Management" text="Employees, their roles, shifts and weekly offs, which projects they work on, and their workload.">
        <input className="search" type="search" placeholder="Search name, email or code" value={query} onChange={(e) => setQuery(e.target.value)} />
        <button className="btn primary" type="button" onClick={() => setEditing({})}>Create employee</button>
      </PageHead>

      {error && <div className="alert error" role="alert" onClick={() => setError('')}>{error}</div>}

      <section className="panel flush">
        <div className="table-wrap">
          <table className="table grid-table">
            <thead>
              <tr>
                <th scope="col" className="num">Sr. no</th>
                <th scope="col">Employee</th>
                <th scope="col">Code</th>
                <th scope="col">Role</th>
                <th scope="col">Shift</th>
                <th scope="col">Weekly off</th>
                <th scope="col" className="num">Open</th>
                <th scope="col" className="num">Waiting</th>
                <th scope="col" className="num">Escalated open</th>
                <th scope="col" className="num">Done, 30 days</th>
                <th scope="col" className="num">Oldest open</th>
                <th scope="col">Status</th>
                <th scope="col" className="actions-col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((u, i) => {
                const w = workload[u.id]
                return (
                  <tr key={u.id} className={u.status !== 'ACTIVE' ? 'removed' : ''}>
                    <td className="num">{i + 1}</td>
                    <td><div className="strong">{u.fullName}</div><div className="muted small">{u.email}</div></td>
                    <td>{u.employeeCode}</td>
                    <td>{u.roleName}</td>
                    <td className="nowrap">{u.shiftStart && u.shiftEnd ? `${u.shiftStart.slice(0, 5)} to ${u.shiftEnd.slice(0, 5)}` : <span className="muted">-</span>}</td>
                    <td>{u.weeklyOff ? u.weeklyOff.replaceAll(',', ', ') : <span className="muted">-</span>}</td>
                    <td className="num">{fmtNumber(w?.open || 0)}</td>
                    <td className="num">{fmtNumber(w?.waiting || 0)}</td>
                    <td className="num">{fmtNumber(w?.escalatedOpen || 0)}</td>
                    <td className="num">{fmtNumber(w?.doneLast30Days || 0)}</td>
                    <td className="num">{w?.oldestOpenMinutes != null ? fmtMinutes(w.oldestOpenMinutes) : '-'}</td>
                    <td>{u.status === 'ACTIVE' ? <span className="tag ok">Active</span> : <span className="tag">Inactive</span>}</td>
                    <td className="actions-col">
                      <IconButton label={`Edit ${u.fullName}`} onClick={() => setEditing(u)}><EditIcon /></IconButton>
                      <button className="btn small" type="button" onClick={() => setToggling(u)}>
                        {u.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                      </button>
                    </td>
                  </tr>
                )
              })}
              {shown.length === 0 && <tr><td colSpan={13} className="empty-row">No employees found.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
      <p className="fine">Workload counts tickets assigned to the employee in this system. Imported tickets link to an employee when the name in the sheet matches someone on that project team: after adding people to a team, import the sheet again.</p>

      {editing && (
        <EmployeeForm
          user={editing.id ? editing : null}
          roles={roles}
          users={users}
          projects={projects}
          onClose={() => setEditing(null)}
          onSaved={(msg) => { setToast(msg); load() }}
        />
      )}
      {toggling && (
        <Confirm
          title={toggling.status === 'ACTIVE' ? 'Deactivate this employee?' : 'Activate this employee?'}
          message={toggling.status === 'ACTIVE'
            ? `${toggling.fullName} will no longer be able to log in. Their tickets and history are kept.`
            : `${toggling.fullName} will be able to log in again.`}
          confirmLabel={toggling.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
          danger={toggling.status === 'ACTIVE'}
          onConfirm={toggle}
          onCancel={() => setToggling(null)}
        />
      )}
      <Toast text={toast} onDone={() => setToast('')} />
    </div>
  )
}

function employeeSchema() {
  return {
    fullName: [rules.personName('Full name')],
    employeeCode: [rules.empCode('Employee code')],
    email: [rules.email('Company email', true)],
    contactNumber: [rules.phone('Contact number', false)],
    designation: [rules.title('Designation')],
    department: [rules.title('Department')],
    skillSet: [rules.maxLen('Skills', 500)],
  }
}

function EmployeeForm({ user, roles, users, projects, onClose, onSaved }) {
  const [current, setCurrent] = useState(user)
  const form = useValidatedForm(user ? {
    employeeCode: user.employeeCode || '', fullName: user.fullName || '', email: user.email || '',
    contactNumber: user.contactNumber || '', designation: user.designation || '', department: user.department || '',
    skillSet: user.skillSet || '', roleCode: user.roleCode, reportingManagerId: user.reportingManagerId ? String(user.reportingManagerId) : '',
    shiftStart: user.shiftStart ? user.shiftStart.slice(0, 5) : '', shiftEnd: user.shiftEnd ? user.shiftEnd.slice(0, 5) : '',
    weeklyOff: user.weeklyOff ? user.weeklyOff.split(',') : [],
  } : EMPTY, employeeSchema)
  const { values: f, errors, setErrors, blur } = form
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  // Step 40: a new employee gets the login password in the same step
  const [pw, setPw] = useState('')
  const [pwErr, setPwErr] = useState('')
  const [login, setLogin] = useState(null)
  const set = (k) => (e) => form.setValue(k, e.target.value)
  const bad = (k) => Boolean(errors[k])
  const checkPw = (v) => (!v ? 'Give the employee a login password (press Generate).'
    : v.length < 10 || !/[A-Za-z]/.test(v) || !/[0-9]/.test(v) || /\s/.test(v)
      ? 'At least 10 characters, with at least one letter and one number, and no spaces.' : '')

  function toggleDay(d) {
    form.setValue('weeklyOff', f.weeklyOff.includes(d) ? f.weeklyOff.filter((x) => x !== d) : [...f.weeklyOff, d])
  }

  async function save(e) {
    e.preventDefault()
    setError('')
    const okForm = form.validateAll()
    const pwProblem = current ? '' : checkPw(pw)
    setPwErr(pwProblem)
    if (!okForm || pwProblem) { setError(FIX_MESSAGE); return }
    const body = {
      ...f,
      fullName: f.fullName.trim(),
      employeeCode: f.employeeCode.trim(),
      email: f.email.trim().toLowerCase(),
      designation: f.designation.trim(),
      department: f.department.trim(),
      skillSet: f.skillSet.trim(),
      reportingManagerId: f.reportingManagerId ? Number(f.reportingManagerId) : null,
      shiftStart: f.shiftStart || null,
      shiftEnd: f.shiftEnd || null,
      weeklyOff: DAYS.filter((d) => f.weeklyOff.includes(d)).join(',') || null,
      contactNumber: f.contactNumber.trim() || null,
    }
    setBusy(true)
    try {
      const saved = current ? await api.updateUser(current.id, body) : await api.createUser(body)
      if (!current) {
        setCurrent(saved)
        try {
          await api.resetPassword(saved.id, pw)
          setLogin({ name: saved.fullName, email: saved.email || body.email, password: pw, role: saved.roleName })
          onSaved(`${saved.fullName} added with a login password. Now give them their projects.`)
        } catch (pwError) {
          setError(`${saved.fullName} was added, but the password was not set: ${pwError.message} Set it below in Login password.`)
          onSaved(`${saved.fullName} added`)
        }
        setPw('')
        return
      }
      onSaved(`${saved.fullName} updated`)
      setCurrent(saved)
    } catch (err) {
      setError(err.message)
      setErrors(err.fieldErrors || {})
    } finally {
      setBusy(false)
    }
  }

  return (
    <Drawer
      title={current ? `Edit ${current.fullName}` : 'Create employee'}
      onClose={onClose}
      wide
      footer={(
        <>
          <button className="btn ghost" type="button" onClick={onClose}>Close</button>
          <button className="btn primary" type="submit" form="emp-form" disabled={busy}>
            {busy ? 'Saving...' : current ? 'Update' : 'Add'}
          </button>
        </>
      )}
    >
      <form id="emp-form" className="form two-col" onSubmit={save} noValidate>
        <Field label="Full name *" error={errors.fullName}><input value={f.fullName} onChange={set('fullName')} onBlur={blur('fullName')} maxLength={150} placeholder="e.g. Himanshu Thorat" aria-invalid={bad('fullName')} /></Field>
        <Field label="Employee code *" error={errors.employeeCode}><input value={f.employeeCode} onChange={set('employeeCode')} onBlur={blur('employeeCode')} maxLength={30} placeholder="e.g. 1001" aria-invalid={bad('employeeCode')} /></Field>
        <Field label="Company email (login name) *" error={errors.email}><input type="email" value={f.email} onChange={set('email')} onBlur={blur('email')} maxLength={254} placeholder="name@sumasoft.com" autoComplete="off" aria-invalid={bad('email')} /></Field>
        <Field label="Contact number" error={errors.contactNumber} hint="10 digits, numbers only">
          <input value={f.contactNumber} onChange={(e) => form.setValue('contactNumber', digitsOnly(e.target.value))} onBlur={blur('contactNumber')}
            maxLength={10} inputMode="numeric" placeholder="9876543210" aria-invalid={bad('contactNumber')} />
        </Field>
        {!current && (
          <div className={`field span-2${pwErr ? ' invalid' : ''}`}>
            <span>Login password *</span>
            <div className="inline-form">
              <input type="text" value={pw} autoComplete="new-password" spellCheck="false" maxLength={100}
                onChange={(e) => { setPw(e.target.value); if (pwErr) setPwErr(checkPw(e.target.value)) }}
                placeholder="Press Generate, or type one" aria-invalid={Boolean(pwErr)} />
              <button className="btn" type="button" onClick={() => { const g = generatePassword(); setPw(g); setPwErr('') }}>Generate</button>
            </div>
            {pwErr ? <small className="field-error" role="alert">{'\u26a0 '}{pwErr}</small>
              : <small className="hint">A temporary password. The employee must choose their own at the first sign-in.</small>}
          </div>
        )}
        <Field label="Role *" error={errors.roleCode}>
          <select value={f.roleCode} onChange={set('roleCode')}>
            {roles.map((r) => <option key={r.code} value={r.code}>{r.name}</option>)}
          </select>
        </Field>
        <Field label="Reporting manager">
          <select value={f.reportingManagerId} onChange={set('reportingManagerId')}>
            <option value="">None</option>
            {users.filter((u) => !current || u.id !== current.id).map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}
          </select>
        </Field>
        <Field label="Designation" error={errors.designation}><input value={f.designation} onChange={set('designation')} onBlur={blur('designation')} maxLength={100} placeholder="e.g. Support Engineer" aria-invalid={bad('designation')} /></Field>
        <Field label="Department" error={errors.department}><input value={f.department} onChange={set('department')} onBlur={blur('department')} maxLength={100} placeholder="e.g. Customer Support" aria-invalid={bad('department')} /></Field>
        <Field label="Shift starts" error={errors.shiftStart}><input type="time" value={f.shiftStart} onChange={set('shiftStart')} /></Field>
        <Field label="Shift ends" error={errors.shiftEnd}><input type="time" value={f.shiftEnd} onChange={set('shiftEnd')} /></Field>
        <div className="field span-2">
          <span>Weekly off</span>
          <div className="day-picks">
            {DAYS.map((d) => (
              <button key={d} type="button" className={f.weeklyOff.includes(d) ? 'on' : ''} aria-pressed={f.weeklyOff.includes(d)} onClick={() => toggleDay(d)}>
                {d.charAt(0) + d.slice(1).toLowerCase()}
              </button>
            ))}
          </div>
        </div>
        <Field label="Skills" error={errors.skillSet}><input value={f.skillSet} onChange={set('skillSet')} onBlur={blur('skillSet')} maxLength={500} placeholder="e.g. Zendesk, account management" aria-invalid={bad('skillSet')} /></Field>
        {error && <div className="alert error span-2" role="alert">{error}</div>}
      </form>
      {login && <LoginCard login={login} />}

      {current && <ProjectAllocation user={current} projects={projects} />}
      {current && <TempPassword user={current} />}
      {!current && <p className="fine">Press Add: the employee and their login are created together. Then give them their projects here.</p>}
    </Drawer>
  )
}

function ProjectAllocation({ user, projects }) {
  const [assigned, setAssigned] = useState([])
  const [pick, setPick] = useState({ projectId: '', projectRole: 'MEMBER', supportLevel: 'L1' })
  const [error, setError] = useState('')

  const load = useCallback(() => {
    api.userProjects(user.id).then((a) => setAssigned((a || []).filter((x) => x.active))).catch((e) => setError(e.message))
  }, [user.id])
  useEffect(() => { load() }, [load])

  async function add() {
    setError('')
    if (!pick.projectId) { setError('Choose a project'); return }
    try {
      await api.assignToProject(pick.projectId, {
        userId: user.id, projectRole: pick.projectRole, supportLevel: pick.projectRole === 'MEMBER' ? pick.supportLevel : null,
      })
      load()
    } catch (e) {
      setError(e.message)
    }
  }

  async function remove(a) {
    setError('')
    try {
      await api.removeFromProject(a.projectId, user.id)
      load()
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <section className="sub-section">
      <h3>Projects</h3>
      <ul className="chips">
        {assigned.map((a) => (
          <li key={a.id}>
            <span>{a.projectName}: {a.projectRole === 'MEMBER' ? a.supportLevel : a.projectRole === 'TEAM_LEADER' ? 'Team Leader' : 'Project Manager'}</span>
            <button type="button" onClick={() => remove(a)} aria-label={`Remove from ${a.projectName}`}>{'\u00d7'}</button>
          </li>
        ))}
        {assigned.length === 0 && <li className="muted plain">Not on any project yet.</li>}
      </ul>
      <div className="inline-form">
        <select value={pick.projectId} onChange={(e) => setPick({ ...pick, projectId: e.target.value })} aria-label="Project">
          <option value="">Choose project</option>
          {projects.map((p) => <option key={p.id} value={p.id}>{p.projectName}</option>)}
        </select>
        <select value={pick.projectRole} onChange={(e) => setPick({ ...pick, projectRole: e.target.value })} aria-label="Job on project">
          <option value="MEMBER">Member</option>
          <option value="TEAM_LEADER">Team Leader</option>
          <option value="PROJECT_MANAGER">Project Manager</option>
        </select>
        {pick.projectRole === 'MEMBER' && (
          <select value={pick.supportLevel} onChange={(e) => setPick({ ...pick, supportLevel: e.target.value })} aria-label="Support level">
            <option>L1</option><option>L2</option><option>L3</option>
          </select>
        )}
        <button className="btn" type="button" onClick={add}>Allocate</button>
      </div>
      {error && <div className="alert error" role="alert">{error}</div>}
    </section>
  )
}

/** A random temporary password: 12 characters, letters and digits, no look-alike characters. */
function generatePassword() {
  const letters = 'ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz'
  const digits = '23456789'
  const all = letters + digits
  const buf = new Uint32Array(12)
  crypto.getRandomValues(buf)
  let s = letters[buf[0] % letters.length] + digits[buf[1] % digits.length]
  for (let i = 2; i < 12; i++) s += all[buf[i] % all.length]
  return s
}

/** Shown once after creating an employee: what to give them privately. */
function LoginCard({ login }) {
  const [copied, setCopied] = useState(false)
  const text = `Suma Tracking sign-in\nPage: ${window.location.origin}/#/login\nEmail: ${login.email}\nTemporary password: ${login.password}\nYou will choose your own password at the first sign-in.`
  async function copy() {
    try { await navigator.clipboard.writeText(text); setCopied(true); setTimeout(() => setCopied(false), 2000) } catch { setCopied(false) }
  }
  return (
    <div className="login-card" role="status">
      <div className="login-card-head">
        <strong>Login details for {login.name}</strong>
        <span className="tag">{login.role}</span>
      </div>
      <dl>
        <div><dt>Sign-in page</dt><dd>{window.location.origin}/#/login</dd></div>
        <div><dt>Email</dt><dd>{login.email}</dd></div>
        <div><dt>Temporary password</dt><dd className="mono">{login.password}</dd></div>
      </dl>
      <div className="login-card-foot">
        <span className="muted small">Give these privately. This password is shown only now.</span>
        <button className="btn small" type="button" onClick={copy}>{copied ? 'Copied' : 'Copy details'}</button>
      </div>
    </div>
  )
}

function TempPassword({ user }) {
  const [pw, setPw] = useState('')
  const [done, setDone] = useState('')
  const [error, setError] = useState('')

  function generate() {
    const letters = 'ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz'
    const digits = '23456789'
    const all = letters + digits
    const buf = new Uint32Array(12)
    crypto.getRandomValues(buf)
    let s = letters[buf[0] % letters.length] + digits[buf[1] % digits.length]
    for (let i = 2; i < 12; i++) s += all[buf[i] % all.length]
    setPw(s)
  }

  async function save() {
    setError('')
    setDone('')
    if (pw.length < 10 || !/[A-Za-z]/.test(pw) || !/[0-9]/.test(pw) || /\s/.test(pw)) {
      setError('Temporary password must be at least 10 characters with at least one letter and one digit, and no spaces.')
      return
    }
    try {
      await api.resetPassword(user.id, pw)
      setDone(`Temporary password set. Give it to ${user.fullName} privately; they must change it at first login.`)
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <section className="sub-section">
      <h3>Login password</h3>
      <p className="fine">Set a temporary password. The employee signs in with their email and chooses their own password.</p>
      <div className="inline-form">
        <input value={pw} onChange={(e) => setPw(e.target.value)} placeholder="At least 10 characters, a letter and a digit" aria-label="Temporary password" />
        <button className="btn" type="button" onClick={generate}>Generate</button>
        <button className="btn primary" type="button" onClick={save} disabled={!pw}>Set password</button>
      </div>
      {done && <div className="alert ok" role="status">{done}</div>}
      {error && <div className="alert error" role="alert">{error}</div>}
    </section>
  )
}