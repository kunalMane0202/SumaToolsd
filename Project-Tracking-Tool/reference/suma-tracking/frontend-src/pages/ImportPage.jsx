import { useEffect, useMemo, useState } from 'react'
import { api } from '../api.js'
import { useCan } from '../permissions.js'
import { fmtDateTime, fmtNumber } from '../format.js'

export default function ImportPage({ embedded }) {
  const canRun = useCan()('IMPORT_RUN')
  const [projects, setProjects] = useState([])
  const [projectId, setProjectId] = useState('')
  const [file, setFile] = useState(null)
  const [sheet, setSheet] = useState('')
  const [busy, setBusy] = useState('')
  const [result, setResult] = useState(null)
  const [checkedKey, setCheckedKey] = useState('')
  const [error, setError] = useState('')
  const [history, setHistory] = useState([])

  useEffect(() => {
    api.projects().then((l) => {
      const active = (l || []).filter((p) => p.status === 'ACTIVE')
      setProjects(active)
      if (active.length) setProjectId(String(active[0].id))
    }).catch(() => setProjects([]))
  }, [])

  useEffect(() => {
    if (!projectId) return
    api.imports(projectId).then((l) => setHistory(l || [])).catch(() => setHistory([]))
  }, [projectId, result])

  const isWorkbook = file && /\.(xlsx|xls)$/i.test(file.name)
  const key = file ? `${projectId}|${file.name}|${file.size}|${file.lastModified}|${sheet.trim().toLowerCase()}` : ''
  const checkedThis = key && key === checkedKey

  async function run(mode) {
    setError('')
    if (!file) { setError('Choose the Excel or CSV file first.'); return }
    if (isWorkbook && !sheet.trim()) { setError('Type the sheet name, for example Nov26.'); return }
    setBusy(mode)
    try {
      const res = await api.importTickets({ file, projectId, mode, sheet: isWorkbook ? sheet.trim() : '' })
      setResult(res)
      if (mode === 'DRY_RUN' && res.status === 'COMPLETED') setCheckedKey(key)
      if (mode === 'IMPORT') setCheckedKey('')
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy('')
    }
  }

  const project = projects.find((p) => String(p.id) === String(projectId))

  return (
    <div className={embedded ? 'stack-gap' : 'page narrow'}>
      {!embedded && (
        <header className="page-head">
          <div>
            <h1>Import sheet</h1>
          </div>
        </header>
      )}
      <p className="muted">
        Upload the agents&apos; daily sheet. Check it first: nothing is saved. Then import: new tickets are created,
        changed tickets are updated, and the rest is left as it is. Uploading the same sheet again never makes duplicates.
      </p>

      {!canRun && <div className="alert info" role="status">Your role can see the import history but cannot upload sheets.</div>}
      {canRun && (
      <section className="panel">
        <div className="import-form">
          <label className="field">
            <span>Project</span>
            <select value={projectId} onChange={(e) => { setProjectId(e.target.value); setResult(null) }}>
              {projects.map((p) => (
                <option key={p.id} value={p.id}>{p.projectName}{p.externalTicketSystem ? ` (${p.externalTicketSystem})` : ''}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>File (.xlsx, .xls or .csv, up to 25 MB)</span>
            <input type="file" accept=".xlsx,.xls,.csv"
              onChange={(e) => { setFile(e.target.files?.[0] || null); setResult(null) }} />
          </label>
          <label className="field">
            <span>Sheet name {isWorkbook ? '' : '(only for Excel files)'}</span>
            <input type="text" placeholder="e.g. Nov26" value={sheet} disabled={file && !isWorkbook}
              onChange={(e) => setSheet(e.target.value)} />
          </label>
          <div className="import-actions">
            <button className="btn" type="button" disabled={!!busy || !projectId} onClick={() => run('DRY_RUN')}>
              {busy === 'DRY_RUN' ? 'Checking...' : 'Check file'}
            </button>
            <button className="btn primary" type="button" disabled={!!busy || !checkedThis} onClick={() => run('IMPORT')}
              title={checkedThis ? '' : 'Check this file first'}>
              {busy === 'IMPORT' ? 'Importing...' : 'Import tickets'}
            </button>
          </div>
          <p className="fine">
            {checkedThis
              ? `Checked. Press Import tickets to save into ${project?.projectName}.`
              : 'Import becomes available after a successful check of this exact file and sheet.'}
          </p>
        </div>
        {error && <div className="alert error" role="alert">{error}</div>}
      </section>
      )}

      {result && <ImportResult result={result} />}

      <section className="panel">
        <div className="panel-head"><h2>Earlier uploads for {project?.projectName || 'this project'}</h2></div>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">When</th>
                <th scope="col">Type</th>
                <th scope="col">File and sheet</th>
                <th scope="col" className="num">Rows</th>
                <th scope="col" className="num">Created</th>
                <th scope="col" className="num">Updated</th>
                <th scope="col" className="num">Skipped</th>
                <th scope="col">Result</th>
              </tr>
            </thead>
            <tbody>
              {history.map((b) => (
                <tr key={b.id}>
                  <td className="nowrap">{fmtDateTime(b.startedAt)}</td>
                  <td>{b.mode === 'IMPORT' ? 'Import' : 'Check'}</td>
                  <td className="clip">{b.fileName}{b.sheetName ? `, ${b.sheetName}` : ''}</td>
                  <td className="num">{fmtNumber(b.totalRows)}</td>
                  <td className="num">{fmtNumber(b.createdCount)}</td>
                  <td className="num">{fmtNumber(b.updatedCount)}</td>
                  <td className="num">{fmtNumber(b.errorCount)}</td>
                  <td>{b.status === 'COMPLETED' ? 'Done' : b.status === 'FAILED' ? <span className="text-bad">Failed</span> : 'Running'}</td>
                </tr>
              ))}
              {history.length === 0 && <tr><td colSpan={8} className="empty-row">No uploads yet for this project.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}

function ImportResult({ result }) {
  const [show, setShow] = useState('ERROR')
  const errors = result.issues.filter((i) => i.severity === 'ERROR')
  const warnings = result.issues.filter((i) => i.severity === 'WARNING')
  const list = show === 'ERROR' ? errors : warnings
  const grouped = useMemo(() => {
    const m = new Map()
    warnings.forEach((w) => {
      const kind = `${w.column || 'File'}: ${w.message.split("'")[0].replace(/\(.*$/, '').trim()}`
      m.set(kind, (m.get(kind) || 0) + 1)
    })
    return [...m.entries()].sort((a, b) => b[1] - a[1])
  }, [warnings])
  const wasCheck = result.mode === 'DRY_RUN'

  if (result.status === 'FAILED') {
    return (
      <section className="panel">
        <div className="alert error">The file could not be imported. {result.failureMessage}</div>
      </section>
    )
  }

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>{wasCheck ? 'Check result: nothing was saved' : 'Import finished'}</h2>
        <p>{result.fileName}{result.sheetName ? `, sheet ${result.sheetName}` : ''}, {fmtNumber(result.totalRows)} rows read</p>
      </div>
      <div className="result-grid">
        <ResultNum label={wasCheck ? 'Would be created' : 'Created'} value={result.createdCount} tone="good" />
        <ResultNum label={wasCheck ? 'Would be updated' : 'Updated'} value={result.updatedCount} />
        <ResultNum label="Unchanged" value={result.unchangedCount} />
        <ResultNum label="Rows skipped (errors)" value={result.errorCount} tone={result.errorCount ? 'bad' : ''} />
        <ResultNum label="Warnings" value={result.warningCount} tone={result.warningCount ? 'warn' : ''} />
      </div>

      {grouped.length > 0 && (
        <div className="kinds">
          <h3>What the warnings are about</h3>
          <ul>
            {grouped.map(([k, n]) => <li key={k}><strong>{n}</strong> {k}</li>)}
          </ul>
          <p className="fine">Warning rows are saved. Fix them in the sheet and import again to update them.</p>
        </div>
      )}

      {(errors.length > 0 || warnings.length > 0) && (
        <>
          <div className="segmented small" role="group" aria-label="Show">
            <button type="button" className={show === 'ERROR' ? 'on' : ''} onClick={() => setShow('ERROR')}>Errors ({errors.length})</button>
            <button type="button" className={show === 'WARNING' ? 'on' : ''} onClick={() => setShow('WARNING')}>Warnings ({warnings.length})</button>
          </div>
          <div className="table-wrap issues">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col" className="num">Row</th>
                  <th scope="col">Ticket</th>
                  <th scope="col">Column</th>
                  <th scope="col">What to fix</th>
                  <th scope="col">Cell value</th>
                </tr>
              </thead>
              <tbody>
                {list.map((i, n) => (
                  <tr key={n}>
                    <td className="num">{i.row || 'File'}</td>
                    <td>{i.ticketId || '-'}</td>
                    <td>{i.column || '-'}</td>
                    <td>{i.message}</td>
                    <td className="clip muted">{i.value || ''}</td>
                  </tr>
                ))}
                {list.length === 0 && <tr><td colSpan={5} className="empty-row">None.</td></tr>}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  )
}

function ResultNum({ label, value, tone }) {
  return (
    <div className={`result-num${tone ? ` ${tone}` : ''}`}>
      <strong>{fmtNumber(value)}</strong>
      <span>{label}</span>
    </div>
  )
}