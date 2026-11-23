import { useEffect, useMemo, useState } from 'react'
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { api } from '../api.js'
import { useChartColors } from '../theme.jsx'
import {
  GROUP_COLORS, GROUP_LABELS, addDays, fmtDay, fmtMinutes, fmtNumber, fmtPercent, monthStart, todayIso,
} from '../format.js'

const PERIODS = [
  { key: 'today', label: 'Today' },
  { key: '7', label: '7 days' },
  { key: 'month', label: 'This month' },
  { key: '30', label: '30 days' },
  { key: '90', label: '90 days' },
  { key: 'custom', label: 'Custom' },
]

function rangeFor(key, custom) {
  const today = todayIso()
  switch (key) {
    case 'today': return { from: today, to: today }
    case '7': return { from: addDays(today, -6), to: today }
    case 'month': return { from: monthStart(today), to: today }
    case '90': return { from: addDays(today, -89), to: today }
    case 'custom': return custom
    default: return { from: addDays(today, -29), to: today }
  }
}

export default function DashboardPage() {
  const cc = useChartColors()
  const [projects, setProjects] = useState([])
  // A link like #/dashboard?project=3 (from Clients & projects) opens one project directly.
  const [projectId, setProjectId] = useState(() => new URLSearchParams(window.location.hash.split('?')[1] || '').get('project') || '')
  const [period, setPeriod] = useState('30')
  const [custom, setCustom] = useState({ from: addDays(todayIso(), -29), to: todayIso() })
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const range = rangeFor(period, custom)

  useEffect(() => {
    api.projects().then((list) => setProjects(list || [])).catch(() => setProjects([]))
  }, [])

  useEffect(() => {
    let alive = true
    setLoading(true)
    setError('')
    api.dashboard({ projectId, from: range.from, to: range.to })
      .then((d) => { if (alive) setData(d) })
      .catch((e) => { if (alive) setError(e.message) })
      .finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [projectId, range.from, range.to])

  const days = useMemo(() => fillDays(data?.byDay || [], range.from, range.to), [data, range.from, range.to])
  const s = data?.summary
  const projectName = projects.find((p) => String(p.id) === String(projectId))?.projectName

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>{projectName ? `${projectName} support` : 'All projects'}</h1>
          <p className="muted">
            Tickets created {range.from === range.to ? `on ${fmtDay(range.from)}` : `from ${fmtDay(range.from)} to ${fmtDay(range.to)}`}
            {', India time'}
          </p>
        </div>
        <div className="filters">
          <label className="select">
            <span className="sr-only">Project</span>
            <select value={projectId} onChange={(e) => setProjectId(e.target.value)}>
              <option value="">All projects</option>
              {projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.projectName}{p.externalTicketSystem ? ` (${titleCase(p.externalTicketSystem)})` : ''}
                </option>
              ))}
            </select>
          </label>
          <div className="segmented" role="group" aria-label="Period">
            {PERIODS.map((p) => (
              <button key={p.key} type="button" className={period === p.key ? 'on' : ''}
                aria-pressed={period === p.key} onClick={() => setPeriod(p.key)}>
                {p.label}
              </button>
            ))}
          </div>
          {period === 'custom' && (
            <div className="dates">
              <input type="date" value={custom.from} max={custom.to} aria-label="From"
                onChange={(e) => setCustom({ ...custom, from: e.target.value })} />
              <span>to</span>
              <input type="date" value={custom.to} min={custom.from} aria-label="To"
                onChange={(e) => setCustom({ ...custom, to: e.target.value })} />
            </div>
          )}
        </div>
      </header>

      {error && <div className="alert error" role="alert">{error}</div>}

      <section className={`summary${loading ? ' dim' : ''}`} aria-label="Totals">
        <Stat label="Tickets" value={fmtNumber(s?.total)} note={`${fmtNumber(s?.escalated)} escalated`} />
        <Stat label="Done" value={fmtNumber(s?.done)} note={`${fmtPercent(s?.done, s?.total)} of all tickets`} tone="good" />
        <Stat label="Waiting" value={fmtNumber(s?.pending)} note="pending user, Tier 2 or hold" tone="warn" />
        <Stat label="Open or in progress" value={fmtNumber((s?.open || 0) + (s?.inProgress || 0))} note="not picked up yet or being worked" />
        <Stat label="Typical solve time" value={fmtMinutes(s?.medianResolutionMinutes)}
          note={`average ${fmtMinutes(s?.avgResolutionMinutes)}`} />
        <Stat label="Solved within 1 hour" value={fmtNumber(s?.resolvedWithinOneHour)}
          note={`${fmtPercent(s?.resolvedWithinOneHour, s?.done)} of done tickets`} />
      </section>

      {data && data.summary.total === 0 && !loading && (
        <div className="empty">
          <h2>No tickets in this period</h2>
          <p>Choose a longer period, or import the latest sheet on the Import sheet page.</p>
        </div>
      )}

      {data && data.summary.total > 0 && (
        <>
          <ShiftClock hours={data.byHour} total={data.summary.total} />

          <div className="grid two">
            <section className="panel">
              <PanelHead title="Tickets per day" hint="Created that day, and how many of those are done now" />
              <div className="chart">
                <ResponsiveContainer width="100%" height={260}>
                  <AreaChart data={days} margin={{ top: 10, right: 12, left: -18, bottom: 0 }}>
                    <defs>
                      <linearGradient id="gCreated" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor={cc.created} stopOpacity={0.28} />
                        <stop offset="100%" stopColor={cc.created} stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid stroke={cc.grid} vertical={false} />
                    <XAxis dataKey="label" tick={{ fontSize: 12, fill: cc.tick }} tickLine={false} axisLine={false}
                      interval="preserveStartEnd" minTickGap={18} />
                    <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: cc.tick }} tickLine={false} axisLine={false} />
                    <Tooltip content={<DayTip />} />
                    <Area type="monotone" dataKey="created" name="Created" stroke={cc.created} strokeWidth={2} fill="url(#gCreated)" />
                    <Area type="monotone" dataKey="done" name="Done" stroke={cc.done} strokeWidth={2} fill="transparent" />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
              <div className="legend">
                <span><i style={{ background: cc.created }} />Created</span>
                <span><i style={{ background: cc.done }} />Done</span>
              </div>
            </section>

            <section className="panel">
              <PanelHead title="Where tickets are now" hint="Current status of the tickets in this period" />
              <StatusBreakdown items={data.byStatus} total={data.summary.total} />
            </section>
          </div>

          <section className="panel">
            <PanelHead title="Agents" hint="Assignee in the sheet. Names turn into linked employees once agents are added to the team." />
            <AgentTable agents={data.byAgent} />
          </section>

          <div className="grid three">
            <section className="panel">
              <PanelHead title="How tickets arrive" hint="Source column: email, chat, call" />
              <BarList items={data.byChannel} color={cc.channel} />
            </section>
            <section className="panel">
              <PanelHead title="Top topics" hint="The ten most common" />
              <BarList items={data.byTopic} color={cc.topic} />
            </section>
            <section className="panel">
              <PanelHead title="Projects" hint={projectId ? 'Filtered to one project' : 'Tickets per project'} />
              <BarList items={data.byProject} color={cc.project} />
            </section>
          </div>
        </>
      )}
    </div>
  )
}

function Stat({ label, value, note, tone }) {
  return (
    <div className={`stat${tone ? ` ${tone}` : ''}`}>
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
      <div className="stat-note">{note}</div>
    </div>
  )
}

function PanelHead({ title, hint }) {
  return (
    <div className="panel-head">
      <h2>{title}</h2>
      {hint && <p>{hint}</p>}
    </div>
  )
}

/** The signature of the dashboard: when tickets arrive across the 24 hours, against the three shifts. */
function ShiftClock({ hours, total }) {
  const counts = Array.from({ length: 24 }, (_, h) => hours.find((x) => x.hour === h)?.count || 0)
  const max = Math.max(1, ...counts)
  const peak = counts.indexOf(Math.max(...counts))
  // Three 8-hour blocks of the day (India time). When the real shift timings are added
  // to the system, these blocks can follow them.
  const shifts = [
    { name: '12 am to 8 am', from: 0, to: 8 },
    { name: '8 am to 4 pm', from: 8, to: 16 },
    { name: '4 pm to 12 am', from: 16, to: 24 },
  ].map((sh) => {
    let sum = 0
    for (let h = sh.from; h < sh.to; h++) sum += counts[h]
    return { ...sh, sum }
  })
  return (
    <section className="panel shift">
      <div className="shift-top">
        <PanelHead title="When tickets arrive" hint={`Busiest hour: ${hourLabel(peak)} to ${hourLabel((peak + 1) % 24)}, India time`} />
        <div className="shift-totals">
          {shifts.map((sh) => (
            <div key={sh.name}>
              <span className="shift-name">{sh.name}</span>
              <strong>{fmtNumber(sh.sum)}</strong>
              <span className="muted"> {fmtPercent(sh.sum, total)}</span>
            </div>
          ))}
        </div>
      </div>
      <div className="clock" role="img" aria-label="Tickets per hour of the day">
        {counts.map((c, h) => (
          <div key={h} className="clock-col" title={`${hourLabel(h)}: ${c} tickets`}>
            <div className="clock-bar-wrap">
              <div className="clock-bar" style={{ height: `${Math.max(4, (c / max) * 100)}%`, opacity: c ? 0.35 + 0.65 * (c / max) : 0.15 }} />
            </div>
            <span className="clock-count">{c || ''}</span>
            <span className="clock-hour">{h % 3 === 0 ? hourLabel(h) : ''}</span>
          </div>
        ))}
      </div>
      <div className="shift-bands" aria-hidden="true">
        {shifts.map((sh, i) => (
          <span key={sh.name} className={`band b${i}`}>{sh.name}</span>
        ))}
      </div>
    </section>
  )
}

function StatusBreakdown({ items, total }) {
  return (
    <div className="status-break">
      <div className="stack" role="img" aria-label="Status share">
        {items.map((i) => (
          <span key={i.code} style={{ flex: i.count, background: GROUP_COLORS[i.group] || '#999' }} title={`${i.name}: ${i.count}`} />
        ))}
      </div>
      <ul className="status-list">
        {items.map((i) => (
          <li key={i.code}>
            <i style={{ background: GROUP_COLORS[i.group] || '#999' }} />
            <span className="status-name">{i.name}</span>
            <span className="status-group">{GROUP_LABELS[i.group]}</span>
            <strong>{fmtNumber(i.count)}</strong>
            <span className="muted pct">{fmtPercent(i.count, total)}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function AgentTable({ agents }) {
  const max = Math.max(1, ...agents.map((a) => a.total))
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th scope="col">Agent</th>
            <th scope="col" className="num">Tickets</th>
            <th scope="col" className="bar-col">Share</th>
            <th scope="col" className="num">Done</th>
            <th scope="col" className="num">Escalated</th>
            <th scope="col" className="num">Average solve time</th>
          </tr>
        </thead>
        <tbody>
          {agents.map((a) => (
            <tr key={a.name}>
              <td><span className="agent">{a.name}</span></td>
              <td className="num strong">{fmtNumber(a.total)}</td>
              <td className="bar-col">
                <div className="mini-bar">
                  <span className="done" style={{ width: `${(a.done / max) * 100}%` }} />
                  <span className="rest" style={{ width: `${((a.total - a.done) / max) * 100}%` }} />
                </div>
              </td>
              <td className="num">{fmtNumber(a.done)} <span className="muted">{fmtPercent(a.done, a.total)}</span></td>
              <td className="num">{fmtNumber(a.escalated)}</td>
              <td className="num">{fmtMinutes(a.avgResolutionMinutes)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function BarList({ items, color }) {
  const cc = useChartColors()
  if (!items.length) return <p className="muted">Nothing to show.</p>
  return (
    <div className="chart">
      <ResponsiveContainer width="100%" height={Math.max(120, items.length * 34)}>
        <BarChart data={items} layout="vertical" margin={{ top: 0, right: 36, left: 0, bottom: 0 }}>
          <XAxis type="number" hide allowDecimals={false} />
          <YAxis type="category" dataKey="name" width={150} tick={{ fontSize: 12, fill: cc.label }} tickLine={false} axisLine={false} />
          <Tooltip cursor={{ fill: cc.cursor }} formatter={(v) => [fmtNumber(v), 'Tickets']} />
          <Bar dataKey="count" radius={[0, 4, 4, 0]} barSize={16} label={{ position: 'right', fontSize: 12, fill: cc.label }}>
            {items.map((i) => <Cell key={i.name} fill={color} />)}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

function DayTip({ active, payload }) {
  if (!active || !payload?.length) return null
  const p = payload[0].payload
  return (
    <div className="tip">
      <strong>{p.label}</strong>
      <div>Created: {fmtNumber(p.created)}</div>
      <div>Done: {fmtNumber(p.done)}</div>
    </div>
  )
}

function fillDays(points, from, to) {
  const map = new Map(points.map((p) => [p.date, p]))
  const out = []
  let d = from
  let guard = 0
  while (d <= to && guard < 400) {
    const p = map.get(d)
    out.push({ date: d, label: fmtDay(d), created: p ? p.created : 0, done: p ? p.done : 0 })
    d = addDays(d, 1)
    guard++
  }
  return out
}

function hourLabel(h) {
  if (h === 0) return '12 am'
  if (h === 12) return '12 pm'
  return h < 12 ? `${h} am` : `${h - 12} pm`
}

function titleCase(s) {
  return s.charAt(0) + s.slice(1).toLowerCase()
}