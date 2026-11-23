import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { Drawer, PageHead, Toast } from '../components.jsx'
import { fmtDateTime, fmtNumber } from '../format.js'

/** Automation (Step 37, Super Admin): switch rules on/off, run now, and see what every run did. */
export default function AutomationPage() {
  const [rules, setRules] = useState([])
  const [runs, setRuns] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')
  const [toast, setToast] = useState('')
  const [openRun, setOpenRun] = useState(null)

  const load = useCallback(() => {
    api.automationRules().then((r) => setRules(r || [])).catch((e) => setError(e.message))
    api.automationRuns().then((r) => setRuns(r || [])).catch((e) => setError(e.message))
  }, [])
  useEffect(() => { load() }, [load])

  async function toggle(rule) {
    setError('')
    setBusy(rule.code)
    try {
      const updated = await api.setAutomationRule(rule.code, !rule.enabled)
      setRules((list) => list.map((r) => (r.code === rule.code ? updated : r)))
      setToast(`${rule.name} switched ${updated.enabled ? 'on' : 'off'}`)
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy('')
    }
  }

  async function runNow() {
    setError('')
    setBusy('run')
    try {
      const run = await api.runAutomation()
      setToast(`Run finished: ${run.notificationsCreated} notifications, ${run.ticketsEscalated} escalated, ${run.ticketsAssigned} assigned`)
      load()
      setOpenRun(run)
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy('')
    }
  }

  return (
    <div className="page narrow">
      <PageHead title="Automation" text="Rules that run by themselves every 5 minutes. Switch each one on or off. Every action is logged below.">
        <button className="btn primary" type="button" onClick={runNow} disabled={busy === 'run'}>{busy === 'run' ? 'Running...' : 'Run now'}</button>
      </PageHead>
      {error && <div className="alert error" role="alert">{error}</div>}

      <section className="panel flush">
        <ul className="rule-list">
          {rules.map((r) => (
            <li key={r.code}>
              <div>
                <div className="strong">{r.name}</div>
                <p className="muted small">{r.description}</p>
                {r.updatedBy && <p className="fine">Last changed {fmtDateTime(r.updatedAt)} by {r.updatedBy}</p>}
              </div>
              <button type="button" role="switch" aria-checked={r.enabled} aria-label={r.name} disabled={busy === r.code}
                className={`switch${r.enabled ? ' on' : ''}`} onClick={() => toggle(r)}>
                <span className="switch-knob" aria-hidden="true" />
                <span className="switch-text">{r.enabled ? 'On' : 'Off'}</span>
              </button>
            </li>
          ))}
        </ul>
      </section>

      <section className="panel flush">
        <div className="panel-head sla-pad"><h2>Run history</h2><p>The last 50 runs. Click a run to see exactly what it did.</p></div>
        <div className="table-wrap">
          <table className="table grid-table clickable">
            <thead>
              <tr>
                <th scope="col">Started</th><th scope="col">How</th><th scope="col">Result</th><th scope="col">Tickets checked</th>
                <th scope="col">Notifications</th><th scope="col">Escalated</th><th scope="col">Assigned</th>
              </tr>
            </thead>
            <tbody>
              {runs.length === 0 && <tr><td colSpan={7} className="empty-row">No runs yet. The first automatic run starts 2 minutes after the backend starts, or press Run now.</td></tr>}
              {runs.map((r) => (
                <tr key={r.id} onClick={() => setOpenRun(r)} tabIndex={0} onKeyDown={(e) => { if (e.key === 'Enter') setOpenRun(r) }}>
                  <td className="nowrap">{fmtDateTime(r.startedAt)}</td>
                  <td>{r.triggerType === 'MANUAL' ? `Run now${r.startedBy ? ` (${r.startedBy})` : ''}` : 'Automatic'}</td>
                  <td><span className={`run-status ${r.status.toLowerCase()}`}>{r.status === 'SUCCESS' ? 'Done' : r.status === 'FAILED' ? 'Problem' : 'Running'}</span></td>
                  <td>{fmtNumber(r.ticketsChecked)}</td>
                  <td>{fmtNumber(r.notificationsCreated)}</td>
                  <td>{fmtNumber(r.ticketsEscalated)}</td>
                  <td>{fmtNumber(r.ticketsAssigned)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
      {openRun && <RunDetails run={openRun} onClose={() => setOpenRun(null)} />}
      <Toast text={toast} onDone={() => setToast('')} />
    </div>
  )
}

function RunDetails({ run, onClose }) {
  const [actions, setActions] = useState(null)
  const [error, setError] = useState('')
  useEffect(() => {
    api.automationActions(run.id).then((a) => setActions(a || [])).catch((e) => setError(e.message))
  }, [run.id])
  return (
    <Drawer title={`Run of ${fmtDateTime(run.startedAt)}`} onClose={onClose} wide>
      {run.message && <div className={`alert ${run.status === 'FAILED' ? 'error' : 'ok'}`} role="status">{run.message}</div>}
      <p className="muted">
        {fmtNumber(run.ticketsChecked)} tickets checked, {fmtNumber(run.notificationsCreated)} notifications,
        {' '}{fmtNumber(run.ticketsEscalated)} escalated, {fmtNumber(run.ticketsAssigned)} assigned.
      </p>
      {error && <div className="alert error" role="alert">{error}</div>}
      {actions && actions.length === 0 && <p className="muted">This run had nothing to do: no ticket needed an alert, escalation or assignment.</p>}
      {actions && actions.length > 0 && (
        <div className="table-wrap">
          <table className="table grid-table">
            <thead><tr><th scope="col">Time</th><th scope="col">Rule</th><th scope="col">Ticket</th><th scope="col">What happened</th></tr></thead>
            <tbody>
              {actions.map((a) => (
                <tr key={a.id}>
                  <td className="nowrap">{fmtDateTime(a.createdAt)}</td>
                  <td>{a.ruleName}</td>
                  <td>{a.ticketNumber || '-'}</td>
                  <td>{a.detail}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Drawer>
  )
}