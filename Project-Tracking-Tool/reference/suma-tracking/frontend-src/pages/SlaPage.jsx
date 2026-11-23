import { useCallback, useEffect, useMemo, useState } from 'react'
import { api } from '../api.js'
import { useCan } from '../permissions.js'
import { PageHead, Toast } from '../components.jsx'
import { FIX_MESSAGE } from '../validation.js'
import { addDays, fmtDateTime, fmtMinutes, fmtNumber, todayIso } from '../format.js'

const PRIORITY_LABEL = { CRITICAL: 'Critical', HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low' }
const STATE_LABEL = { BREACHED: 'Breached', AT_RISK: 'At risk', ON_TRACK: 'On track', PAUSED: 'Paused' }
const DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']

/** SLA Management (Step 37): SLA % per project, tickets that need attention, and the targets. */
export default function SlaPage({ user }) {
  const can = useCan()
  const isAdmin = can('SLA_MANAGE')
  const [tab, setTab] = useState('overview')
  const [projects, setProjects] = useState([])

  useEffect(() => {
    api.projects().then((p) => setProjects((p || []).filter((x) => x.status !== 'CLOSED'))).catch(() => setProjects([]))
  }, [])

  return (
    <div className="page">
      <PageHead title="SLA Management" text="First response and resolution targets per project and priority, and the tickets that are about to break them." />
      <div className="segmented" role="tablist">
        <button type="button" role="tab" aria-selected={tab === 'overview'} className={tab === 'overview' ? 'on' : ''} onClick={() => setTab('overview')}>SLA overview</button>
        <button type="button" role="tab" aria-selected={tab === 'settings'} className={tab === 'settings' ? 'on' : ''} onClick={() => setTab('settings')}>Targets and support hours</button>
      </div>
      {tab === 'overview' && <Overview projects={projects} />}
      {tab === 'settings' && <SettingsTab projects={projects} canEdit={isAdmin} />}
    </div>
  )
}

// ================================================================ overview

function Overview({ projects }) {
  const [filters, setFilters] = useState({ projectId: '', from: addDays(todayIso(), -29), to: todayIso() })
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [only, setOnly] = useState('ALL')

  const load = useCallback(() => {
    if (filters.from && filters.to && filters.from > filters.to) { setError("'From' date cannot be after 'To' date."); return }
    setError('')
    setLoading(true)
    api.slaOverview(filters)
      .then((d) => setData({ ...d, projects: (d && d.projects) || [], attention: (d && d.attention) || [] }))
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }, [filters])
  useEffect(() => { load() }, [load])

  const set = (k) => (e) => setFilters({ ...filters, [k]: e.target.value })
  const attention = useMemo(() => (data ? data.attention.filter((a) => only === 'ALL' || a.state === only) : []), [data, only])
  const breachedCount = data ? data.attention.filter((a) => a.state === 'BREACHED').length : 0
  const riskCount = data ? data.attention.filter((a) => a.state === 'AT_RISK').length : 0

  return (
    <>
      <div className="filters">
        <label className="select">
          <span className="sr-only">Project</span>
          <select value={filters.projectId} onChange={set('projectId')} aria-label="Project">
            <option value="">All projects</option>
            {projects.map((p) => <option key={p.id} value={p.id}>{p.projectName}</option>)}
          </select>
        </label>
        <div className="dates">
          <label htmlFor="sla-from">Created from</label>
          <input id="sla-from" type="date" value={filters.from} max={filters.to} onChange={set('from')} />
          <label htmlFor="sla-to">to</label>
          <input id="sla-to" type="date" value={filters.to} min={filters.from} onChange={set('to')} />
        </div>
        <button className="btn" type="button" onClick={load} disabled={loading}>{loading ? 'Loading...' : 'Refresh'}</button>
        {data && <span className="muted small">Checked {fmtDateTime(data.generatedAt)}</span>}
      </div>
      {error && <div className="alert error" role="alert">{error}</div>}

      {data && data.projects.length === 0 && (
        <div className="empty"><strong>No tickets in these days</strong><p>Import a sheet in Integration Management, or choose other dates.</p></div>
      )}

      {data && data.projects.length > 0 && (
        <div className="sla-cards">
          {data.projects.map((p) => (
            <section className="panel sla-card" key={p.projectId}>
              <div className="sla-card-head">
                <h2>{p.projectName}</h2>
                <span className="tag">{p.hoursMode === 'BUSINESS_HOURS' ? 'Business hours' : '24 x 7'}</span>
              </div>
              <div className="sla-pcts">
                <Compliance label="First response met" value={p.responseCompliance} met={p.responseMet} total={p.responseMeasured} />
                <Compliance label="Resolved within SLA" value={p.resolutionCompliance} met={p.resolutionMet} total={p.resolutionMeasured} />
              </div>
              <div className="sla-open" aria-label="Open tickets now">
                <span className="sla-chip breached"><strong>{fmtNumber(p.openBreached)}</strong> breached</span>
                <span className="sla-chip at-risk"><strong>{fmtNumber(p.openAtRisk)}</strong> at risk</span>
                <span className="sla-chip on-track"><strong>{fmtNumber(p.openOnTrack)}</strong> on track</span>
                <span className="sla-chip paused"><strong>{fmtNumber(p.openPaused)}</strong> paused</span>
              </div>
            </section>
          ))}
        </div>
      )}

      {data && (
        <section className="panel flush">
          <div className="panel-head sla-att-head">
            <div>
              <h2>Open tickets that need attention</h2>
              <p>Open tickets of the last 90 days that broke, or are close to breaking, their first response or resolution target.</p>
            </div>
            <div className="segmented" role="tablist" aria-label="Show">
              <button type="button" className={only === 'ALL' ? 'on' : ''} aria-selected={only === 'ALL'} onClick={() => setOnly('ALL')}>All ({breachedCount + riskCount})</button>
              <button type="button" className={only === 'BREACHED' ? 'on' : ''} aria-selected={only === 'BREACHED'} onClick={() => setOnly('BREACHED')}>Breached ({breachedCount})</button>
              <button type="button" className={only === 'AT_RISK' ? 'on' : ''} aria-selected={only === 'AT_RISK'} onClick={() => setOnly('AT_RISK')}>At risk ({riskCount})</button>
            </div>
          </div>
          <div className="table-wrap">
            <table className="table grid-table">
              <thead>
                <tr>
                  <th scope="col">SLA</th><th scope="col">Ticket</th><th scope="col">Project</th><th scope="col">Priority</th>
                  <th scope="col">Status</th><th scope="col">Assignee</th><th scope="col">Clock</th>
                  <th scope="col">Used / target</th><th scope="col">Due</th>
                </tr>
              </thead>
              <tbody>
                {attention.length === 0 && (
                  <tr><td colSpan={9} className="empty-row">Nothing needs attention. Every open ticket is within its SLA.</td></tr>
                )}
                {attention.map((a) => (
                  <tr key={`${a.ticketId}-${a.clock}`}>
                    <td><span className={`sla-pill ${a.state === 'BREACHED' ? 'breached' : 'at-risk'}`}>{STATE_LABEL[a.state]}</span></td>
                    <td><a className="strong" href={`#/tickets?q=${encodeURIComponent(a.ticketNumber)}&open=1`}>{a.ticketNumber}</a>{a.subject && <div className="muted small clip">{a.subject}</div>}</td>
                    <td>{a.projectName}</td>
                    <td>{PRIORITY_LABEL[a.priority] || a.priority}</td>
                    <td>{a.statusName}</td>
                    <td>{a.assignee || <span className="muted">Unassigned</span>}</td>
                    <td>{a.clock === 'RESOLUTION' ? 'Resolution' : 'First response'}</td>
                    <td className="nowrap">
                      <div>{fmtMinutes(a.elapsedMinutes)} / {fmtMinutes(a.targetMinutes)}</div>
                      <div className="sla-meter" aria-hidden="true"><span style={{ width: `${Math.min(100, Math.round((a.elapsedMinutes / Math.max(1, a.targetMinutes)) * 100))}%` }} className={a.state === 'BREACHED' ? 'breached' : 'at-risk'} /></div>
                    </td>
                    <td className="nowrap">{fmtDateTime(a.dueAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="fine pad">
            How it is counted: the clock starts when the ticket was created. First response ends at the recorded first reply (for finished tickets without one, at resolution).
            Tickets waiting on the customer, a third party or on hold are paused when the project says so. SLA % counts finished tickets created in the chosen days.
          </p>
        </section>
      )}
    </>
  )
}

function Compliance({ label, value, met, total }) {
  const tone = value === null || value === undefined ? '' : value >= 90 ? 'good' : value >= 75 ? 'warn' : 'bad'
  return (
    <div className={`sla-pct ${tone}`}>
      <div className="stat-label">{label}</div>
      <div className="sla-pct-value">{value === null || value === undefined ? '-' : `${value}%`}</div>
      <div className="stat-note">{total ? `${fmtNumber(met)} of ${fmtNumber(total)} tickets` : 'No finished tickets yet'}</div>
    </div>
  )
}

// ================================================================ settings

function toHours(min) {
  return String(Math.round((min / 60) * 100) / 100)
}

function SettingsTab({ projects, canEdit }) {
  const [projectId, setProjectId] = useState('')
  const [form, setForm] = useState(null)
  const [meta, setMeta] = useState(null)
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [toast, setToast] = useState('')

  useEffect(() => {
    if (!projectId && projects.length) setProjectId(String(projects[0].id))
  }, [projects, projectId])

  const load = useCallback(() => {
    if (!projectId) return
    setError('')
    setErrors({})
    api.slaSettings(projectId).then((s) => {
      if (!s || !s.targets) { setError('Could not read the SLA settings of this project.'); return }
      setMeta(s)
      setForm({
        hoursMode: s.hoursMode,
        workDays: (s.workDays || '').split(',').filter(Boolean),
        dayStart: s.dayStart,
        dayEnd: s.dayEnd,
        warnPercent: String(s.warnPercent),
        pauseWhenWaiting: s.pauseWhenWaiting,
        targets: s.targets.map((t) => ({ priorityCode: t.priorityCode, response: toHours(t.firstResponseMinutes), resolution: toHours(t.resolutionMinutes) })),
        holidays: (s.holidays || []).map((h) => ({ date: h.date, name: h.name })),
      })
    }).catch((e) => setError(e.message))
  }, [projectId])
  useEffect(() => { load() }, [load])

  function validateForm(f) {
    const e = {}
    if (f.hoursMode === 'BUSINESS_HOURS') {
      if (!f.workDays.length) e.workDays = 'Choose at least one working day.'
      if (!f.dayStart || !f.dayEnd) e.hours = 'Enter both the day start and the day end.'
      else if (f.dayEnd <= f.dayStart) e.hours = 'Day end must be after day start (for example 09:00 to 18:00).'
    }
    const w = Number(f.warnPercent)
    if (!/^[0-9]+$/.test(f.warnPercent) || w < 50 || w > 95) e.warnPercent = 'Warning level must be a whole number from 50 to 95.'
    f.targets.forEach((t) => {
      const r = Number(t.response)
      const s = Number(t.resolution)
      const name = PRIORITY_LABEL[t.priorityCode]
      if (!t.response || Number.isNaN(r) || r <= 0) e[`r-${t.priorityCode}`] = `${name}: enter the first response time in hours (for example 1 or 0.5).`
      else if (r > 720) e[`r-${t.priorityCode}`] = `${name}: first response can be at most 720 hours (30 days).`
      if (!t.resolution || Number.isNaN(s) || s <= 0) e[`s-${t.priorityCode}`] = `${name}: enter the resolution time in hours (for example 4).`
      else if (s > 2160) e[`s-${t.priorityCode}`] = `${name}: resolution can be at most 2160 hours (90 days).`
      else if (!e[`r-${t.priorityCode}`] && s < r) e[`s-${t.priorityCode}`] = `${name}: resolution cannot be shorter than first response.`
    })
    const seen = new Set()
    f.holidays.forEach((h, i) => {
      if (!h.date) e[`h-${i}`] = 'Choose a date.'
      else if (seen.has(h.date)) e[`h-${i}`] = 'This date is already in the list.'
      else if (!h.name.trim()) e[`h-${i}`] = 'Enter the holiday name.'
      else if (h.name.trim().length > 100) e[`h-${i}`] = 'Holiday name is too long (maximum 100 characters).'
      if (h.date) seen.add(h.date)
    })
    return e
  }

  function update(next) {
    setForm(next)
    if (Object.keys(errors).length) setErrors(validateForm(next))
  }

  async function save() {
    setError('')
    const e = validateForm(form)
    setErrors(e)
    if (Object.keys(e).length) { setError(FIX_MESSAGE); return }
    const body = {
      hoursMode: form.hoursMode,
      workDays: DAYS.filter((d) => form.workDays.includes(d)).join(',') || 'MON',
      dayStart: form.dayStart,
      dayEnd: form.dayEnd,
      warnPercent: Number(form.warnPercent),
      pauseWhenWaiting: form.pauseWhenWaiting,
      targets: form.targets.map((t) => ({
        priorityCode: t.priorityCode,
        firstResponseMinutes: Math.max(1, Math.round(Number(t.response) * 60)),
        resolutionMinutes: Math.max(1, Math.round(Number(t.resolution) * 60)),
      })),
      holidays: form.holidays.map((h) => ({ date: h.date, name: h.name.trim() })),
    }
    setBusy(true)
    try {
      await api.saveSlaSettings(projectId, body)
      setToast('SLA settings saved')
      load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const ro = !canEdit
  return (
    <>
      <div className="filters">
        <label className="select">
          <span className="sr-only">Project</span>
          <select value={projectId} onChange={(e) => setProjectId(e.target.value)} aria-label="Project">
            {projects.map((p) => <option key={p.id} value={p.id}>{p.projectName}</option>)}
          </select>
        </label>
        {meta && meta.updatedAt && <span className="muted small">Last changed {fmtDateTime(meta.updatedAt)}{meta.updatedBy ? ` by ${meta.updatedBy}` : ''}</span>}
        {ro && <span className="tag">View only. The Super Admin changes SLA settings.</span>}
      </div>
      {error && <div className="alert error" role="alert">{error}</div>}
      {!form && !error && <div className="empty"><p>Loading...</p></div>}
      {form && (
        <div className="sla-settings">
          <section className="panel">
            <div className="panel-head"><h2>Targets per priority</h2><p>In hours. 0.5 = 30 minutes, 24 = one day of support time.</p></div>
            <div className="table-wrap">
              <table className="table sla-targets">
                <thead><tr><th scope="col">Priority</th><th scope="col">First response (hours)</th><th scope="col">Resolution (hours)</th></tr></thead>
                <tbody>
                  {form.targets.map((t, i) => (
                    <tr key={t.priorityCode}>
                      <td><span className={`prio prio-${t.priorityCode.toLowerCase()}`}>{PRIORITY_LABEL[t.priorityCode]}</span></td>
                      {['response', 'resolution'].map((k) => {
                        const err = errors[`${k === 'response' ? 'r' : 's'}-${t.priorityCode}`]
                        return (
                          <td key={k}>
                            <div className={`field${err ? ' invalid' : ''}`}>
                              <input type="number" min="0.25" step="0.25" inputMode="decimal" value={t[k]} disabled={ro}
                                aria-label={`${PRIORITY_LABEL[t.priorityCode]} ${k === 'response' ? 'first response' : 'resolution'} hours`}
                                aria-invalid={Boolean(err)}
                                onChange={(e) => update({ ...form, targets: form.targets.map((x, j) => (j === i ? { ...x, [k]: e.target.value } : x)) })} />
                              {Number(t[k]) > 0 && <small className="hint">{fmtMinutes(Number(t[k]) * 60)}</small>}
                              {err && <small className="field-error" role="alert">{'\u26a0 '}{err}</small>}
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
            <div className="panel-head"><h2>Support hours</h2><p>Which time counts towards the SLA.</p></div>
            <div className="form">
              <div className="segmented" role="radiogroup" aria-label="Support hours">
                <button type="button" role="radio" aria-checked={form.hoursMode === 'TWENTY_FOUR_SEVEN'} className={form.hoursMode === 'TWENTY_FOUR_SEVEN' ? 'on' : ''} disabled={ro}
                  onClick={() => update({ ...form, hoursMode: 'TWENTY_FOUR_SEVEN' })}>24 x 7 (every hour counts)</button>
                <button type="button" role="radio" aria-checked={form.hoursMode === 'BUSINESS_HOURS'} className={form.hoursMode === 'BUSINESS_HOURS' ? 'on' : ''} disabled={ro}
                  onClick={() => update({ ...form, hoursMode: 'BUSINESS_HOURS' })}>Business hours only</button>
              </div>
              {form.hoursMode === 'BUSINESS_HOURS' && (
                <>
                  <div className={`field${errors.workDays ? ' invalid' : ''}`}>
                    <span>Working days</span>
                    <div className="day-picks">
                      {DAYS.map((d) => (
                        <button key={d} type="button" disabled={ro} className={form.workDays.includes(d) ? 'on' : ''} aria-pressed={form.workDays.includes(d)}
                          onClick={() => update({ ...form, workDays: form.workDays.includes(d) ? form.workDays.filter((x) => x !== d) : [...form.workDays, d] })}>
                          {d.charAt(0) + d.slice(1).toLowerCase()}
                        </button>
                      ))}
                    </div>
                    {errors.workDays && <small className="field-error" role="alert">{'\u26a0 '}{errors.workDays}</small>}
                  </div>
                  <div className={`field${errors.hours ? ' invalid' : ''}`}>
                    <span>Day starts and ends ({meta.timeZone})</span>
                    <div className="inline-form">
                      <input type="time" value={form.dayStart} disabled={ro} aria-label="Day starts" onChange={(e) => update({ ...form, dayStart: e.target.value })} />
                      <span className="muted">to</span>
                      <input type="time" value={form.dayEnd} disabled={ro} aria-label="Day ends" onChange={(e) => update({ ...form, dayEnd: e.target.value })} />
                    </div>
                    {errors.hours && <small className="field-error" role="alert">{'\u26a0 '}{errors.hours}</small>}
                  </div>
                </>
              )}
              <div className={`field${errors.warnPercent ? ' invalid' : ''}`}>
                <span>Warn when this much of the target is used (%)</span>
                <input type="number" min="50" max="95" step="5" value={form.warnPercent} disabled={ro} aria-invalid={Boolean(errors.warnPercent)}
                  onChange={(e) => update({ ...form, warnPercent: e.target.value })} />
                {errors.warnPercent ? <small className="field-error" role="alert">{'\u26a0 '}{errors.warnPercent}</small>
                  : <small className="hint">At {form.warnPercent || 80}% the ticket shows as "At risk" and an alert is sent.</small>}
              </div>
              <label className="check">
                <input type="checkbox" checked={form.pauseWhenWaiting} disabled={ro} onChange={(e) => update({ ...form, pauseWhenWaiting: e.target.checked })} />
                Pause the resolution clock while the ticket waits on the customer, a third party or is on hold
              </label>
            </div>
          </section>

          {form.hoursMode === 'BUSINESS_HOURS' && (
            <section className="panel">
              <div className="panel-head"><h2>Holidays</h2><p>Days on which the SLA clock does not run for this project.</p></div>
              <div className="sla-holidays">
                {form.holidays.length === 0 && <p className="muted">No holidays yet.</p>}
                {form.holidays.map((h, i) => (
                  <div key={i} className={`sla-holiday${errors[`h-${i}`] ? ' invalid' : ''}`}>
                    <input type="date" value={h.date} disabled={ro} aria-label="Holiday date"
                      onChange={(e) => update({ ...form, holidays: form.holidays.map((x, j) => (j === i ? { ...x, date: e.target.value } : x)) })} />
                    <input type="text" value={h.name} maxLength={100} disabled={ro} placeholder="e.g. Diwali" aria-label="Holiday name"
                      onChange={(e) => update({ ...form, holidays: form.holidays.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)) })} />
                    {!ro && <button type="button" className="btn ghost" onClick={() => update({ ...form, holidays: form.holidays.filter((_, j) => j !== i) })}>Remove</button>}
                    {errors[`h-${i}`] && <small className="field-error" role="alert">{'\u26a0 '}{errors[`h-${i}`]}</small>}
                  </div>
                ))}
                {!ro && <button type="button" className="btn" onClick={() => update({ ...form, holidays: [...form.holidays, { date: '', name: '' }] })}>Add holiday</button>}
              </div>
            </section>
          )}

          {canEdit && (
            <div className="sla-save">
              <button className="btn ghost" type="button" onClick={load} disabled={busy}>Undo changes</button>
              <button className="btn primary" type="button" onClick={save} disabled={busy}>{busy ? 'Saving...' : 'Save SLA settings'}</button>
            </div>
          )}
        </div>
      )}
      <Toast text={toast} onDone={() => setToast('')} />
    </>
  )
}