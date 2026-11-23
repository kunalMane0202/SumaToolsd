import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { useCan } from '../permissions.js'
import { PageHead, Toast } from '../components.jsx'
import { FIX_MESSAGE } from '../validation.js'
import { fmtDateTime } from '../format.js'

const PRIORITIES = [['CRITICAL', 'Critical'], ['HIGH', 'High'], ['MEDIUM', 'Medium'], ['LOW', 'Low']]
const GROUP_LABEL = { OPEN: 'Open or in progress', IN_PROGRESS: 'Open or in progress', PENDING: 'Waiting', RESOLVED: 'Done', CLOSED: 'Done' }

/** Settings (Step 38): everyone changes their password here; the Super Admin also sets tool-wide settings. */
export default function SettingsPage({ user }) {
  const can = useCan()
  const isAdmin = can('SETTINGS_MANAGE')
  return (
    <div className="page narrow">
      <PageHead title="Settings" text={isAdmin ? 'Your account, and the settings of the whole tool.' : 'Your account.'} />
      <MyAccount user={user} />
      {isAdmin && <ToolSettings />}
      {isAdmin && <Reference />}
    </div>
  )
}

function MyAccount({ user }) {
  const [f, setF] = useState({ current: '', next: '', again: '' })
  const [errors, setErrors] = useState({})
  const [msg, setMsg] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  function check(v) {
    const e = {}
    if (!v.current) e.current = 'Enter your current password.'
    if (v.next.length < 10 || !/[A-Za-z]/.test(v.next) || !/[0-9]/.test(v.next) || /\s/.test(v.next)) {
      e.next = 'At least 10 characters, with at least one letter and one number, and no spaces.'
    } else if (v.next === v.current) e.next = 'The new password must be different from the current one.'
    if (!e.next && v.again !== v.next) e.again = 'The two new passwords are not the same.'
    return e
  }
  const set = (k) => (ev) => {
    const next = { ...f, [k]: ev.target.value }
    setF(next)
    if (Object.keys(errors).length) setErrors(check(next))
  }

  async function save(ev) {
    ev.preventDefault()
    setMsg('')
    setError('')
    const e = check(f)
    setErrors(e)
    if (Object.keys(e).length) { setError(FIX_MESSAGE); return }
    setBusy(true)
    try {
      await api.changePassword(f.current, f.next)
      setF({ current: '', next: '', again: '' })
      setMsg('Your password is changed. Use the new one next time you sign in.')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const field = (k, label, auto) => (
    <label className={`field${errors[k] ? ' invalid' : ''}`}>
      <span>{label}</span>
      <input type="password" autoComplete={auto} value={f[k]} onChange={set(k)} aria-invalid={Boolean(errors[k])} />
      {errors[k] && <small className="field-error" role="alert">{'\u26a0 '}{errors[k]}</small>}
    </label>
  )

  return (
    <section className="panel">
      <div className="panel-head"><h2>My account</h2></div>
      <dl className="set-facts">
        <div><dt>Name</dt><dd>{user.fullName}</dd></div>
        <div><dt>Login email</dt><dd>{user.email}</dd></div>
        <div><dt>Role</dt><dd>{user.roleName}</dd></div>
      </dl>
      <form className="form set-password" onSubmit={save} noValidate>
        <h3>Change password</h3>
        {field('current', 'Current password', 'current-password')}
        {field('next', 'New password', 'new-password')}
        {field('again', 'New password again', 'new-password')}
        {error && <div className="alert error" role="alert">{error}</div>}
        {msg && <div className="alert ok" role="status">{msg}</div>}
        <div><button className="btn primary" type="submit" disabled={busy}>{busy ? 'Saving...' : 'Change password'}</button></div>
      </form>
    </section>
  )
}

function ToolSettings() {
  const [list, setList] = useState(null)
  const [values, setValues] = useState({})
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [toast, setToast] = useState('')

  const load = useCallback(() => {
    api.settings().then((s) => {
      setList(s || [])
      const v = {}
      ;(s || []).forEach((x) => { v[x.key] = x.type === 'MINUTES' ? String(Math.round((Number(x.value) / 60) * 100) / 100) : x.value })
      setValues(v)
      setErrors({})
    }).catch((e) => setError(e.message))
  }, [])
  useEffect(() => { load() }, [load])

  const byKey = (k) => (list || []).find((x) => x.key === k)

  function check(v) {
    const e = {}
    ;(list || []).forEach((d) => {
      const raw = (v[d.key] || '').trim()
      if (d.type === 'TEXT') {
        if (raw.length < d.min || raw.length > d.max) e[d.key] = `${d.label} must be ${d.min} to ${d.max} characters.`
        else if (!/^[A-Za-z0-9][A-Za-z0-9 &.,'()/-]*$/.test(raw)) e[d.key] = `${d.label} can contain letters, numbers, spaces and & . , ' ( ) / - only.`
      } else if (d.type === 'TIMEZONE') {
        try { new Intl.DateTimeFormat('en', { timeZone: raw }); if (!raw) throw new Error() } catch { e[d.key] = `"${raw}" is not a valid time zone. Use a name like Asia/Kolkata.` }
      } else if (d.type === 'MINUTES') {
        const h = Number(raw)
        const minutes = Math.round(h * 60)
        if (!raw || Number.isNaN(h) || minutes < d.min) e[d.key] = 'Enter the time in hours, for example 4 or 0.5.'
        else if (minutes > d.max) e[d.key] = `At most ${d.max / 60} hours.`
      } else {
        const n = Number(raw)
        if (!/^[0-9]+$/.test(raw) || n < d.min || n > d.max) e[d.key] = `Enter a whole number from ${d.min} to ${d.max}.`
      }
    })
    PRIORITIES.forEach(([p, name]) => {
      const r = `sla.default.${p}.responseMinutes`
      const s = `sla.default.${p}.resolutionMinutes`
      if (!e[r] && !e[s] && Number(v[s]) < Number(v[r])) e[s] = `${name}: resolution cannot be shorter than first response.`
    })
    const sh = 'attendance.defaultShiftMinutes'
    const br = 'attendance.defaultBreakMinutes'
    if (v[sh] !== undefined && !e[sh] && !e[br] && Number(v[br]) >= Number(v[sh])) e[br] = 'The break must be shorter than the shift.'

    return e
  }

  function set(k, val) {
    const next = { ...values, [k]: val }
    setValues(next)
    if (Object.keys(errors).length) setErrors(check(next))
  }

  async function save() {
    setError('')
    const e = check(values)
    setErrors(e)
    if (Object.keys(e).length) { setError(FIX_MESSAGE); return }
    const out = {}
    list.forEach((d) => {
      const raw = values[d.key].trim()
      out[d.key] = d.type === 'MINUTES' ? String(Math.round(Number(raw) * 60)) : raw
    })
    setBusy(true)
    try {
      await api.saveSettings(out)
      setToast('Settings saved')
      load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (!list) return error ? <div className="alert error" role="alert">{error}</div> : null

  const input = (k, props = {}) => {
    const d = byKey(k)
    if (!d) return null
    return (
      <label className={`field${errors[k] ? ' invalid' : ''}`} key={k}>
        <span>{d.label}</span>
        <input value={values[k] || ''} onChange={(e) => set(k, e.target.value)} aria-invalid={Boolean(errors[k])} {...props} />
        {errors[k] ? <small className="field-error" role="alert">{'\u26a0 '}{errors[k]}</small> : d.help && <small className="hint">{d.help}</small>}
      </label>
    )
  }
  const last = list.filter((x) => x.updatedAt).sort((a, b) => (a.updatedAt < b.updatedAt ? 1 : -1))[0]

  return (
    <>
      <section className="panel">
        <div className="panel-head"><h2>Organisation</h2></div>
        <div className="form two-col">
          {input('company.name', { maxLength: 100 })}
          {input('project.defaultTimeZone', { maxLength: 50, placeholder: 'Asia/Kolkata' })}
        </div>
      </section>
      <section className="panel">
        <div className="panel-head"><h2>SLA starting targets</h2><p>In hours. A project gets these until its own targets are saved on the SLA page.</p></div>
        <div className="table-wrap">
          <table className="table sla-targets">
            <thead><tr><th scope="col">Priority</th><th scope="col">First response (hours)</th><th scope="col">Resolution (hours)</th></tr></thead>
            <tbody>
              {PRIORITIES.map(([p, name]) => (
                <tr key={p}>
                  <td><span className={`prio prio-${p.toLowerCase()}`}>{name}</span></td>
                  {['responseMinutes', 'resolutionMinutes'].map((kind) => {
                    const k = `sla.default.${p}.${kind}`
                    return (
                      <td key={k}>
                        <div className={`field${errors[k] ? ' invalid' : ''}`}>
                          <input type="number" min="0.25" step="0.25" value={values[k] || ''} aria-label={`${name} ${kind === 'responseMinutes' ? 'first response' : 'resolution'} hours`}
                            onChange={(e) => set(k, e.target.value)} />
                          {errors[k] && <small className="field-error" role="alert">{'\u26a0 '}{errors[k]}</small>}
                        </div>
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      <section className="panel">
        <div className="panel-head"><h2>Automation</h2></div>
        <div className="form two-col">
          {input('automation.alertLookbackDays', { inputMode: 'numeric', maxLength: 3 })}
          {input('automation.maxNotificationsPerRun', { inputMode: 'numeric', maxLength: 4 })}
          {input('automation.dailySummaryHour', { inputMode: 'numeric', maxLength: 2 })}
        </div>
      </section>
      <section className="panel">
        <div className="panel-head"><h2>Attendance</h2><p>In hours. Everyone gets these unless their own hours are set on the Attendance page.</p></div>
        <div className="form two-col">
          {input('attendance.defaultShiftMinutes', { inputMode: 'decimal', maxLength: 5 })}
          {input('attendance.defaultBreakMinutes', { inputMode: 'decimal', maxLength: 5 })}
        </div>
      </section>
      {error && <div className="alert error" role="alert">{error}</div>}
      <div className="sla-save">
        {last && <span className="muted small">Last changed {fmtDateTime(last.updatedAt)}{last.updatedBy ? ` by ${last.updatedBy}` : ''}</span>}
        <button className="btn ghost" type="button" onClick={load} disabled={busy}>Undo changes</button>
        <button className="btn primary" type="button" onClick={save} disabled={busy}>{busy ? 'Saving...' : 'Save settings'}</button>
      </div>
      <Toast text={toast} onDone={() => setToast('')} />
    </>
  )
}

function Reference() {
  const [statuses, setStatuses] = useState([])
  useEffect(() => { api.statuses().then((s) => setStatuses(s || [])).catch(() => setStatuses([])) }, [])
  return (
    <section className="panel">
      <div className="panel-head"><h2>Ticket statuses and priorities</h2><p>For reference. What each status means is explained in the Knowledge Base.</p></div>
      <div className="set-ref">
        <ul className="set-list">
          {statuses.map((s) => (
            <li key={s.code}><strong>{s.name}</strong> <span className="muted small">{GROUP_LABEL[s.statusGroup || s.group] || ''}</span></li>
          ))}
        </ul>
        <ul className="set-list">
          {PRIORITIES.map(([p, name]) => <li key={p}><span className={`prio prio-${p.toLowerCase()}`}>{name}</span></li>)}
        </ul>
      </div>
      <p className="fine"><a href="#/knowledge/ticket-statuses">Read: What each ticket status means</a></p>
    </section>
  )
}
