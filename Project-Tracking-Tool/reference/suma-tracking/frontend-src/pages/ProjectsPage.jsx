import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { useCan } from '../permissions.js'
import { Confirm, Drawer, EditIcon, Field, IconButton, PageHead, Toast } from '../components.jsx'
import { FIX_MESSAGE, rules, useValidatedForm } from '../validation.js'

const EMPTY = {
  clientId: '', projectName: '', description: '', supportsL1: true, supportsL2: false, supportsL3: false,
  supportStartDate: '', supportEndDate: '', timeZone: 'Asia/Kolkata', externalTicketSystem: '',
}

export default function ProjectsPage({ user }) {
  const [rows, setRows] = useState([])
  const [clients, setClients] = useState([])
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(null)
  const [action, setAction] = useState(null)
  const [toast, setToast] = useState('')
  const can = useCan()
  const isAdmin = can('PROJECT_MANAGE')

  const load = useCallback(() => {
    api.projects().then(setRows).catch((e) => setError(e.message))
  }, [])
  useEffect(() => {
    load()
    api.clients().then((c) => setClients((c || []).filter((x) => x.status === 'ACTIVE'))).catch(() => setClients([]))
  }, [load])

  async function runAction() {
    const { row, kind } = action
    try {
      if (kind === 'deactivate') await api.deactivateProject(row.id)
      if (kind === 'activate') await api.activateProject(row.id)
      if (kind === 'close') await api.closeProject(row.id)
      setToast(`${row.projectName}: ${kind === 'close' ? 'closed' : kind === 'activate' ? 'activated' : 'deactivated'}`)
      setAction(null)
      load()
    } catch (e) {
      setError(e.message)
      setAction(null)
    }
  }

  return (
    <div className="page">
      <PageHead title="Project Management" text="Projects of each client, their support levels, time zone and the ticketing tool they use.">
        {isAdmin && <button className="btn primary" type="button" onClick={() => setEditing({})}>Create project</button>}
      </PageHead>
      {error && <div className="alert error" role="alert" onClick={() => setError('')}>{error}</div>}
      <section className="panel flush">
        <div className="table-wrap">
          <table className="table grid-table">
            <thead>
              <tr>
                <th scope="col" className="num">Sr. no</th>
                <th scope="col">Project</th>
                <th scope="col">Client</th>
                <th scope="col">Ticketing tool</th>
                <th scope="col">Support levels</th>
                <th scope="col">Time zone</th>
                <th scope="col">Status</th>
                {isAdmin && <th scope="col" className="actions-col">Actions</th>}
              </tr>
            </thead>
            <tbody>
              {rows.map((p, i) => (
                <tr key={p.id} className={p.status !== 'ACTIVE' ? 'removed' : ''}>
                  <td className="num">{i + 1}</td>
                  <td className="strong">{p.projectName}<div className="muted small">{p.projectCode}</div></td>
                  <td>{p.clientName}</td>
                  <td>{p.externalTicketSystem || <span className="muted">Excel only</span>}</td>
                  <td>{['L1', 'L2', 'L3'].filter((l) => p[`supports${l}`]).join(', ') || <span className="muted">-</span>}</td>
                  <td>{p.timeZone}</td>
                  <td><span className={`tag${p.status === 'ACTIVE' ? ' ok' : ''}`}>{p.status.charAt(0) + p.status.slice(1).toLowerCase()}</span></td>
                  {isAdmin && <td className="actions-col">
                    {p.status !== 'CLOSED' && <IconButton label={`Edit ${p.projectName}`} onClick={() => setEditing(p)}><EditIcon /></IconButton>}
                    {p.status === 'ACTIVE' && <button className="btn small" type="button" onClick={() => setAction({ row: p, kind: 'deactivate' })}>Deactivate</button>}
                    {p.status === 'INACTIVE' && <button className="btn small" type="button" onClick={() => setAction({ row: p, kind: 'activate' })}>Activate</button>}
                    {p.status !== 'CLOSED' && <button className="btn small" type="button" onClick={() => setAction({ row: p, kind: 'close' })}>Close</button>}
                  </td>}
                </tr>
              ))}
              {rows.length === 0 && <tr><td colSpan={isAdmin ? 8 : 7} className="empty-row">No projects yet.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>

      {editing && (
        <ProjectForm row={editing.id ? editing : null} clients={clients} onClose={() => setEditing(null)}
          onSaved={(p, wasEdit) => { setEditing(null); setToast(`${p.projectName} ${wasEdit ? 'updated' : 'added'}`); load() }} />
      )}
      {action && (
        <Confirm
          title={`${action.kind.charAt(0).toUpperCase() + action.kind.slice(1)} ${action.row.projectName}?`}
          message={action.kind === 'close'
            ? 'A closed project cannot be changed or reopened. Its tickets and history stay.'
            : action.kind === 'deactivate' ? 'The project is paused: no imports or team changes until it is activated again.' : 'The project becomes active again.'}
          confirmLabel={action.kind.charAt(0).toUpperCase() + action.kind.slice(1)}
          danger={action.kind !== 'activate'}
          onConfirm={runAction}
          onCancel={() => setAction(null)}
        />
      )}
      <Toast text={toast} onDone={() => setToast('')} />
    </div>
  )
}

function validTimeZone(v) {
  const s = (v || '').trim()
  if (!s) return 'Time zone is required.'
  try {
    new Intl.DateTimeFormat('en', { timeZone: s })
    return ''
  } catch {
    return `"${s}" is not a valid time zone. Use a name like Asia/Kolkata or America/New_York.`
  }
}

function projectSchema(v) {
  return {
    clientId: [rules.required('Client')],
    projectName: [rules.orgName('Project name')],
    description: [rules.maxLen('Description', 1000)],
    supportsL1: [() => (v.supportsL1 || v.supportsL2 || v.supportsL3 ? '' : 'Choose at least one support level.')],
    externalTicketSystem: [rules.tool('Ticketing tool')],
    timeZone: [validTimeZone],
    supportEndDate: [rules.dateOrder('Support start date', 'Support end date', v.supportStartDate)],
  }
}

function ProjectForm({ row, clients, onClose, onSaved }) {
  // a new project starts with the default time zone from Settings
  useEffect(() => {
    if (row) return
    api.publicSettings().then((p) => {
      if (p && p.defaultTimeZone) form.setValues((v) => (v.timeZone === 'Asia/Kolkata' ? { ...v, timeZone: p.defaultTimeZone } : v))
    }).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  const form = useValidatedForm(row ? {
    clientId: String(row.clientId), projectName: row.projectName, description: row.description || '',
    supportsL1: row.supportsL1, supportsL2: row.supportsL2, supportsL3: row.supportsL3,
    supportStartDate: row.supportStartDate || '', supportEndDate: row.supportEndDate || '',
    timeZone: row.timeZone || 'Asia/Kolkata', externalTicketSystem: row.externalTicketSystem || '',
  } : EMPTY, projectSchema)
  const { values: f, errors, setErrors, blur } = form
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => form.setValue(k, e.target.type === 'checkbox' ? e.target.checked : e.target.value)
  const bad = (k) => Boolean(errors[k])

  function toggleLevel(k) {
    return (e) => {
      const next = { ...f, [k]: e.target.checked }
      form.setValues(next)
      setErrors({ ...errors, supportsL1: next.supportsL1 || next.supportsL2 || next.supportsL3 ? '' : 'Choose at least one support level.' })
    }
  }

  async function save(e) {
    e.preventDefault()
    setError('')
    if (!form.validateAll()) { setError(FIX_MESSAGE); return }
    const body = {
      ...f,
      clientId: Number(f.clientId),
      projectName: f.projectName.trim(),
      description: f.description.trim(),
      timeZone: f.timeZone.trim(),
      supportStartDate: f.supportStartDate || null,
      supportEndDate: f.supportEndDate || null,
      externalTicketSystem: f.externalTicketSystem.trim() ? f.externalTicketSystem.trim().toUpperCase() : null,
    }
    setBusy(true)
    try {
      const saved = row ? await api.updateProject(row.id, body) : await api.createProject(body)
      onSaved(saved, Boolean(row))
    } catch (err) {
      setError(err.message)
      setErrors(err.fieldErrors || {})
    } finally {
      setBusy(false)
    }
  }

  return (
    <Drawer title={row ? `Edit ${row.projectName}` : 'Create project'} onClose={onClose}
      footer={(
        <>
          <button className="btn ghost" type="button" onClick={onClose}>Cancel</button>
          <button className="btn primary" type="submit" form="project-form" disabled={busy}>{busy ? 'Saving...' : row ? 'Update' : 'Add'}</button>
        </>
      )}>
      <form id="project-form" className="form" onSubmit={save} noValidate>
        <Field label="Client *" error={errors.clientId}>
          <select value={f.clientId} onChange={set('clientId')} onBlur={blur('clientId')} disabled={Boolean(row)} aria-invalid={bad('clientId')}>
            <option value="">Choose client</option>
            {clients.map((c) => <option key={c.id} value={c.id}>{c.clientName}</option>)}
          </select>
        </Field>
        <Field label="Project name *" error={errors.projectName}><input value={f.projectName} onChange={set('projectName')} onBlur={blur('projectName')} maxLength={150} aria-invalid={bad('projectName')} /></Field>
        <Field label="Description" error={errors.description}><textarea rows={3} value={f.description} onChange={set('description')} onBlur={blur('description')} maxLength={1000} /></Field>
        <div className={`field${errors.supportsL1 ? ' invalid' : ''}`}>
          <span>Support levels *</span>
          <div className="checks">
            <label className="check"><input type="checkbox" checked={f.supportsL1} onChange={toggleLevel('supportsL1')} />L1</label>
            <label className="check"><input type="checkbox" checked={f.supportsL2} onChange={toggleLevel('supportsL2')} />L2</label>
            <label className="check"><input type="checkbox" checked={f.supportsL3} onChange={toggleLevel('supportsL3')} />L3</label>
          </div>
          {errors.supportsL1 && <small className="field-error" role="alert">{'\u26a0 '}{errors.supportsL1}</small>}
        </div>
        <Field label="Ticketing tool" error={errors.externalTicketSystem} hint="e.g. ZENDESK, KEYPANION. Leave empty if tickets come only from Excel.">
          <input value={f.externalTicketSystem} onChange={set('externalTicketSystem')} onBlur={blur('externalTicketSystem')} maxLength={50} aria-invalid={bad('externalTicketSystem')} />
        </Field>
        <Field label="Time zone *" error={errors.timeZone}><input value={f.timeZone} onChange={set('timeZone')} onBlur={blur('timeZone')} placeholder="Asia/Kolkata" aria-invalid={bad('timeZone')} /></Field>
        <Field label="Support start date" error={errors.supportStartDate}><input type="date" value={f.supportStartDate} onChange={set('supportStartDate')} /></Field>
        <Field label="Support end date" error={errors.supportEndDate}><input type="date" value={f.supportEndDate} onChange={set('supportEndDate')} onBlur={blur('supportEndDate')} aria-invalid={bad('supportEndDate')} /></Field>
        {error && <div className="alert error" role="alert">{error}</div>}
      </form>
    </Drawer>
  )
}