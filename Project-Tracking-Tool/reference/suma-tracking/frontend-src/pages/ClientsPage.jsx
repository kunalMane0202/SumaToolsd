import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { useCan } from '../permissions.js'
import { Confirm, DeleteIcon, Drawer, EditIcon, Field, IconButton, PageHead, Toast } from '../components.jsx'
import { FIX_MESSAGE, digitsOnly, rules, useValidatedForm } from '../validation.js'

const EMPTY = { name: '', designation: '', email: '', contactNumber: '', projectName: '', accountManagerAssigned: 'NO', accountManagerUserId: '' }

export default function ClientsPage() {
  // Step 39: CLIENT_MANAGE = every client (Super Admin). CLIENT_EDIT_ASSIGNED = only own clients (Client Admin).
  const can = useCan()
  const canManage = can('CLIENT_MANAGE')
  const [rows, setRows] = useState([])
  const [users, setUsers] = useState([])
  const [showRemoved, setShowRemoved] = useState(false)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(null) // null = closed, {} = add, row = edit
  const [deleting, setDeleting] = useState(null)
  const [busy, setBusy] = useState(false)
  const [toast, setToast] = useState('')

  const load = useCallback(() => {
    api.adminClients(showRemoved).then(setRows).catch((e) => setError(e.message))
  }, [showRemoved])

  useEffect(() => { load() }, [load])
  useEffect(() => {
    if (canManage) api.users().then((u) => setUsers((u || []).filter((x) => x.status === 'ACTIVE'))).catch(() => setUsers([]))
  }, [canManage])

  async function remove() {
    setBusy(true)
    try {
      await api.removeClient(deleting.id)
      setToast(`${deleting.name} deleted`)
      setDeleting(null)
      load()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  async function restore(row) {
    try {
      await api.restoreClient(row.id)
      setToast(`${row.name} restored`)
      load()
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <div className="page">
      <PageHead title="Client Management" text={canManage
        ? 'Add clients, keep their details up to date and assign account managers.'
        : 'The clients given to you. Keep their details up to date and add projects.'}>
        {canManage && (
          <label className="check">
            <input type="checkbox" checked={showRemoved} onChange={(e) => setShowRemoved(e.target.checked)} />
            Show deleted clients
          </label>
        )}
        {canManage && <button className="btn primary" type="button" onClick={() => setEditing({})}>Create client</button>}
      </PageHead>

      {error && <div className="alert error" role="alert" onClick={() => setError('')}>{error}</div>}

      <section className="panel flush">
        <div className="table-wrap">
          <table className="table grid-table">
            <thead>
              <tr>
                <th scope="col" className="num">Sr. no</th>
                <th scope="col">Client name</th>
                <th scope="col">Designation</th>
                <th scope="col">Company email</th>
                <th scope="col">Contact number</th>
                <th scope="col">Projects</th>
                <th scope="col">Account manager</th>
                <th scope="col" className="actions-col">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => (
                <tr key={r.id} className={r.status !== 'ACTIVE' ? 'removed' : ''}>
                  <td className="num">{i + 1}</td>
                  <td className="strong">{r.name}{r.status !== 'ACTIVE' && <span className="tag">Deleted</span>}</td>
                  <td>{r.designation || <span className="muted">-</span>}</td>
                  <td>{r.email || <span className="muted">-</span>}</td>
                  <td className="nowrap">{r.contactNumber || <span className="muted">-</span>}</td>
                  <td>{r.projects.length ? r.projects.join(', ') : <span className="muted">-</span>}</td>
                  <td>{r.accountManagerAssigned ? r.accountManagerName : <span className="muted">Not assigned</span>}</td>
                  <td className="actions-col">
                    {r.status === 'ACTIVE' ? (
                      <>
                        <IconButton label={`Edit ${r.name}`} onClick={() => setEditing(r)}><EditIcon /></IconButton>
                        {canManage && <IconButton label={`Delete ${r.name}`} kind="danger" onClick={() => setDeleting(r)}><DeleteIcon /></IconButton>}
                      </>
                    ) : canManage ? (
                      <button className="btn small" type="button" onClick={() => restore(r)}>Restore</button>
                    ) : null}
                  </td>
                </tr>
              ))}
              {rows.length === 0 && (
                <tr><td colSpan={8} className="empty-row">{canManage ? 'No clients yet. Press Create client to add the first one.' : 'No clients are given to you yet. The Super Admin links clients to Client Admins.'}</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {editing && (
        <ClientForm
          canManage={canManage}
          row={editing.id ? editing : null}
          users={users}
          onClose={() => setEditing(null)}
          onSaved={(saved, wasEdit) => {
            setEditing(null)
            setToast(wasEdit ? `${saved.name} updated` : `${saved.name} added`)
            load()
          }}
        />
      )}

      {deleting && (
        <Confirm
          title="Delete this client?"
          message={`Are you sure you want to delete ${deleting.name}? It disappears from this list. Its projects, tickets and history are kept, and you can restore it with "Show deleted clients".`}
          confirmLabel="Delete"
          danger
          busy={busy}
          onConfirm={remove}
          onCancel={() => setDeleting(null)}
        />
      )}
      <Toast text={toast} onDone={() => setToast('')} />
    </div>
  )
}

function clientSchema(v) {
  return {
    name: [rules.orgName('Name')],
    designation: [rules.title('Designation')],
    email: [rules.email('Company Email ID', true)],
    contactNumber: [rules.phone('Contact Number', true)],
    projectName: [rules.orgName('Project Name', false)],
    accountManagerUserId: v.accountManagerAssigned === 'YES' ? [rules.required('Account manager')] : [],
  }
}

function ClientForm({ row, users, canManage, onClose, onSaved }) {
  const form = useValidatedForm(row ? {
    name: row.name || '',
    designation: row.designation || '',
    email: row.email || '',
    contactNumber: row.contactNumber || '',
    projectName: '',
    accountManagerAssigned: row.accountManagerAssigned ? 'YES' : 'NO',
    accountManagerUserId: row.accountManagerUserId ? String(row.accountManagerUserId) : '',
  } : EMPTY, clientSchema)
  const { values: f, errors, setErrors, blur } = form
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => form.setValue(k, e.target.value)

  async function save(e) {
    e.preventDefault()
    setError('')
    if (!form.validateAll()) { setError(FIX_MESSAGE); return }
    const body = {
      name: f.name.trim(),
      designation: f.designation.trim(),
      email: f.email.trim().toLowerCase(),
      contactNumber: f.contactNumber.trim(),
      projectName: f.projectName.trim(),
      accountManagerAssigned: f.accountManagerAssigned === 'YES',
      accountManagerUserId: f.accountManagerAssigned === 'YES' && f.accountManagerUserId ? Number(f.accountManagerUserId) : null,
    }
    setBusy(true)
    try {
      const saved = row ? await api.updateClient(row.id, body) : await api.addClient(body)
      onSaved(saved, Boolean(row))
    } catch (err) {
      setError(err.message)
      setErrors(err.fieldErrors || {})
    } finally {
      setBusy(false)
    }
  }

  return (
    <Drawer
      title={row ? `Edit ${row.name}` : 'Create client'}
      onClose={onClose}
      footer={(
        <>
          <button className="btn ghost" type="button" onClick={onClose}>Cancel</button>
          <button className="btn primary" type="submit" form="client-form" disabled={busy}>
            {busy ? 'Saving...' : row ? 'Update' : 'Add'}
          </button>
        </>
      )}
    >
      <form id="client-form" className="form" onSubmit={save} noValidate>
        <Field label="Name *" error={errors.name}>
          <input value={f.name} onChange={set('name')} onBlur={blur('name')} maxLength={150} autoFocus aria-invalid={Boolean(errors.name)} />
        </Field>
        <Field label="Designation" error={errors.designation}>
          <input value={f.designation} onChange={set('designation')} onBlur={blur('designation')} maxLength={100} placeholder="e.g. IT Manager" aria-invalid={Boolean(errors.designation)} />
        </Field>
        <Field label="Company Email ID *" error={errors.email}>
          <input type="email" value={f.email} onChange={set('email')} onBlur={blur('email')} maxLength={254} placeholder="name@company.com" autoComplete="off" aria-invalid={Boolean(errors.email)} />
        </Field>
        <Field label="Contact Number *" error={errors.contactNumber} hint="10 digits, numbers only. Example: 9876543210">
          <input value={f.contactNumber} onChange={(e) => form.setValue('contactNumber', digitsOnly(e.target.value))} onBlur={blur('contactNumber')}
            maxLength={10} inputMode="numeric" placeholder="9876543210" aria-invalid={Boolean(errors.contactNumber)} />
        </Field>
        <Field label="Project Name" error={errors.projectName}
          hint={row && row.projects.length ? `Current projects: ${row.projects.join(', ')}. Type a new name to add another project.` : 'A new project with this name is created for the client.'}>
          <input value={f.projectName} onChange={set('projectName')} onBlur={blur('projectName')} maxLength={150} aria-invalid={Boolean(errors.projectName)} />
        </Field>
        {!canManage && (
          <Field label="Account manager" hint="Only the Super Admin changes the account manager.">
            <input value={row && row.accountManagerAssigned ? row.accountManagerName : 'Not assigned'} disabled />
          </Field>
        )}
        {canManage && (
        <Field label="Account Manager (Is Assigned)">
          <select value={f.accountManagerAssigned} onChange={(e) => { form.setValue('accountManagerAssigned', e.target.value); setErrors({ ...errors, accountManagerUserId: '' }) }}>
            <option value="NO">No</option>
            <option value="YES">Yes</option>
          </select>
        </Field>
        )}
        {canManage && f.accountManagerAssigned === 'YES' && (
          <Field label="Account manager *" error={errors.accountManagerUserId}>
            <select value={f.accountManagerUserId} onChange={set('accountManagerUserId')} onBlur={blur('accountManagerUserId')} aria-invalid={Boolean(errors.accountManagerUserId)}>
              <option value="">Choose an employee</option>
              {users.map((u) => <option key={u.id} value={u.id}>{u.fullName} ({u.roleName})</option>)}
            </select>
          </Field>
        )}
        {error && <div className="alert error" role="alert">{error}</div>}
      </form>
    </Drawer>
  )
}