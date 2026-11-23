import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { PageHead } from '../components.jsx'
import { addDays, fmtMinutes, fmtNumber, fmtPercent, monthStart, todayIso } from '../format.js'

export default function ReportsPage() {
  const [from, setFrom] = useState(monthStart(todayIso()))
  const [to, setTo] = useState(todayIso())
  const [rows, setRows] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let alive = true
    setLoading(true)
    setError('')
    api.projects().then(async (projects) => {
      const out = []
      for (const p of projects || []) {
        const d = await api.dashboard({ projectId: p.id, from, to })
        out.push({ project: p, s: d.summary, agents: d.byAgent })
      }
      if (alive) setRows(out)
    }).catch((e) => { if (alive) setError(e.message) })
      .finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [from, to])

  function downloadCsv() {
    const head = ['Client', 'Project', 'Tickets', 'Done', 'Waiting', 'Open or in progress', 'Escalated', 'Typical solve time (min)', 'Average solve time (min)']
    const lines = rows.map(({ project: p, s }) => [p.clientName, p.projectName, s.total, s.done, s.pending, s.open + s.inProgress, s.escalated,
      s.medianResolutionMinutes ?? '', s.avgResolutionMinutes ?? ''])
    const csv = [head, ...lines].map((l) => l.map((v) => `"${String(v).replaceAll('"', '""')}"`).join(',')).join('\r\n')
    const blob = new Blob([`\ufeff${csv}`], { type: 'text/csv;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `project-report-${from}-to-${to}.csv`
    a.click()
    URL.revokeObjectURL(a.href)
  }

  return (
    <div className="page">
      <PageHead title="Reports" text="Project-wise ticket report for any period. Download it for Excel.">
        <div className="dates">
          <input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} aria-label="From" />
          <span>to</span>
          <input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} aria-label="To" />
        </div>
        <button className="btn" type="button" onClick={() => { setFrom(addDays(todayIso(), -29)); setTo(todayIso()) }}>Last 30 days</button>
        <button className="btn primary" type="button" onClick={downloadCsv} disabled={!rows.length}>Download CSV</button>
      </PageHead>
      {error && <div className="alert error" role="alert">{error}</div>}
      <section className={`panel flush${loading ? ' dim' : ''}`}>
        <div className="table-wrap">
          <table className="table grid-table">
            <thead>
              <tr>
                <th scope="col">Client</th>
                <th scope="col">Project</th>
                <th scope="col" className="num">Tickets</th>
                <th scope="col" className="num">Done</th>
                <th scope="col" className="num">Waiting</th>
                <th scope="col" className="num">Open</th>
                <th scope="col" className="num">Escalated</th>
                <th scope="col" className="num">Typical solve time</th>
                <th scope="col">Busiest agent</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(({ project: p, s, agents }) => (
                <tr key={p.id}>
                  <td>{p.clientName}</td>
                  <td className="strong">{p.projectName}</td>
                  <td className="num">{fmtNumber(s.total)}</td>
                  <td className="num">{fmtNumber(s.done)} <span className="muted">{fmtPercent(s.done, s.total)}</span></td>
                  <td className="num">{fmtNumber(s.pending)}</td>
                  <td className="num">{fmtNumber(s.open + s.inProgress)}</td>
                  <td className="num">{fmtNumber(s.escalated)}</td>
                  <td className="num">{fmtMinutes(s.medianResolutionMinutes)}</td>
                  <td>{agents[0] ? `${agents[0].name} (${agents[0].total})` : <span className="muted">-</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}