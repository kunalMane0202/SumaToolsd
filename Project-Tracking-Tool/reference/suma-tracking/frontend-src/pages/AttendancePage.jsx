import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { Drawer, PageHead, Toast } from '../components.jsx'
import { useCan, useMe } from '../permissions.js'
import {
  AlertIcon, AnalogClock, BREAK_TYPES, BreakFood, FLAG, FlagChips, Ring, ZONE, breakType, bigTime, clock, hm, liveDay, longDate, timeOf,
  useAttendance, useNow,
} from '../attendance.jsx'

// Step 41: Attendance. Everyone: my day (punch in, break, punch out) and my history.
// With ATTENDANCE_VIEW / ATTENDANCE_MANAGE: the team board, the IP and device log, and working hours.

function todayIst() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: ZONE }).format(new Date())
}

function dayName(iso) {
  return new Intl.DateTimeFormat('en-IN', { weekday: 'short', day: 'numeric', month: 'short' }).format(new Date(`${iso}T00:00:00`))
}

export default function AttendancePage() {
  const can = useCan()
  const canView = can('ATTENDANCE_VIEW', 'ATTENDANCE_MANAGE')
  const tabs = [['day', 'My day'], ['history', 'My history']]
  if (canView) tabs.push(['team', 'Team board'], ['hours', 'Working hours'])
  const [tab, setTab] = useState('day')
  return (
    <div className="page">
      <PageHead title="Attendance" text="Punch in, take your break and punch out. Attendance is marked only after the full compulsory break." />
      <div className="segmented att-tabs" role="tablist">
        {tabs.map(([k, label]) => (
          <button key={k} type="button" role="tab" aria-selected={tab === k} className={tab === k ? 'on' : ''} onClick={() => setTab(k)}>{label}</button>
        ))}
      </div>
      {tab === 'day' && <MyDay />}
      {tab === 'history' && <MyHistory />}
      {tab === 'team' && canView && <TeamBoard />}
      {tab === 'hours' && canView && <WorkingHours />}
    </div>
  )
}

// ====================================================================== my day

function MyDay() {
  const a = useAttendance()
  const now = useNow()
  const [error, setError] = useState('')
  const [confirmOut, setConfirmOut] = useState(false)
  const [picking, setPicking] = useState(false)
  if (!a || (!a.data && !a.error)) return <div className="panel muted">Loading your day...</div>
  if (!a.data) return <div className="alert error" role="alert">{a.error}</div>
  const { data, live } = a
  const day = data.day
  const s = data.schedule
  const openBreak = day && (day.breaks || []).find((b) => b.kind !== 'OFF' && !b.endedAt)
  const current = openBreak ? breakType(openBreak.breakType) : null

  async function run(kind, extra) {
    setPicking(false)
    setError('')
    setConfirmOut(false)
    const e = await a.act(kind, extra)
    if (e) setError(e)
  }

  return (
    <div className="att-grid">
      {error && <div className="alert error att-wide" role="alert">{'\u26a0 '}{error}</div>}

      {!day && !data.doneForToday && (
        <section className="att-hero att-wide">
          <div className="att-hero-clock"><AnalogClock size={170} now={now} /></div>
          <div className="att-hero-copy">
            <span className="hud-label"><span className="hud-dot amber" aria-hidden="true" /> READY WHEN YOU ARE</span>
            <h2 className="mono att-bigtime">{bigTime(now)}</h2>
            <p className="muted">{longDate(now)}</p>
            <p>Your shift today: <strong>{hm(s.shiftMinutes * 60)}</strong> including a compulsory break of <strong>{hm(s.breakMinutes * 60)}</strong>.</p>
          </div>
          <button type="button" className="punch-btn big" disabled={a.busy} onClick={() => run('in')}>
            <span className="pulse-ring r1" aria-hidden="true" /><span className="pulse-ring r2" aria-hidden="true" /><span className="pulse-ring r3" aria-hidden="true" />
            <span className="punch-btn-core">
              <svg width="38" height="38" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 2v10M6.3 6.3a8 8 0 1 0 11.4 0" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" /></svg>
              <span>{a.busy ? 'Wait...' : 'PUNCH IN'}</span>
            </span>
          </button>
        </section>
      )}

      {day && live && live.open && !live.onBreak && (
        <section className="att-card timer-card">
          <div className="tc-head">
            <span className="hud-label"><span className="hud-dot live" aria-hidden="true" /> ON DUTY SINCE {timeOf(day.punchInAt).toUpperCase()}</span>
            <AnalogClock size={54} now={now} />
          </div>
          <div className="tc-body">
            <Ring size={230} stroke={14} value={live.worked / Math.max(1, live.required)} tone={live.workLeft <= 0 ? 'ok' : 'accent'}>
              <span className="ring-label">Worked</span>
              <span className="ring-value mono">{clock(live.worked)}</span>
              <span className="ring-sub">{live.workLeft > 0 ? `${hm(live.workLeft)} to go` : 'Hours complete'}</span>
            </Ring>
            <ul className="tc-stats">
              <li><span>Work target</span><strong>{hm(live.required)}</strong></li>
              <li><span>Break used</span><strong className={live.breakSec > live.allowance ? 'bad' : ''}>{hm(live.breakSec)} / {hm(live.allowance)}</strong></li>
              <li><span>Since last break</span><strong className={live.flags.includes('BREAK_DUE') ? 'warn' : ''}>{hm(live.continuous)}</strong></li>
              <li><span>Shift ends about</span><strong>{timeOf(new Date(Date.now() + Math.max(0, live.workLeft + Math.max(0, live.breakLeft)) * 1000).toISOString())}</strong></li>
            </ul>
          </div>
          {live.flags.includes('BREAK_DUE') && (
            <div className="att-nudge warn"><AlertIcon /><span>You have worked {hm(live.continuous)} without a break. Your {hm(live.allowance)} break is compulsory.</span></div>
          )}
          {live.flags.includes('NO_BREAK') && (
            <div className="att-nudge bad"><AlertIcon /><span>Hours are done but the compulsory break is not. Take {hm(Math.max(0, live.breakLeft))} more break, or attendance is not marked.</span></div>
          )}
          <div className="tc-actions">
            <button type="button" className="btn break-btn" aria-expanded={picking} disabled={a.busy || (live.breakLeft <= 0 && s.breakMinutes === 0)}
              onClick={() => { setConfirmOut(false); setPicking((p) => !p) }}>
              <span aria-hidden="true">{'\u2615'}</span> Start break
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d={picking ? 'M6 15l6-6 6 6' : 'M6 9l6 6 6-6'} stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" /></svg>
            </button>
            {!confirmOut && (
              <button type="button" className="btn danger" disabled={a.busy} onClick={() => (live.complete ? run('out') : setConfirmOut(true))}>Punch out</button>
            )}
          </div>
          {picking && (
            <div className="break-pick" role="group" aria-label="Choose the type of break">
              <p className="muted small">Which break are you taking? Break time left today: <strong>{hm(Math.max(0, live.breakLeft))}</strong> (shared by all breaks).</p>
              <div className="break-pick-grid">
                {BREAK_TYPES.map((b) => (
                  <button key={b.code} type="button" className={`break-opt bt-${b.code.toLowerCase()}`} disabled={a.busy} onClick={() => run('breakStart', { breakType: b.code })}>
                    <span className="break-opt-icon" aria-hidden="true">{b.icon}</span>
                    <strong>{b.name}</strong>
                    <span>{b.text}</span>
                  </button>
                ))}
              </div>
            </div>
          )}
          {confirmOut && (
            <div className="att-confirm" role="alertdialog" aria-label="Punch out early">
              <p><AlertIcon /> {live.breakSec + 60 < live.allowance ? `You have taken only ${hm(live.breakSec)} of your ${hm(live.allowance)} break.` : `You have worked ${hm(live.worked)} of ${hm(live.required)}.`} If you punch out now, <strong>attendance will not be marked</strong> for today.</p>
              <div className="modal-actions">
                <button type="button" className="btn ghost" onClick={() => setConfirmOut(false)}>Keep working</button>
                <button type="button" className="btn danger" onClick={() => run('out')} disabled={a.busy}>Punch out anyway</button>
              </div>
            </div>
          )}
          <FlagChips flags={live.flags.filter((f) => f !== 'BREAK_DUE')} />
        </section>
      )}

      {day && live && live.onBreak && (
        <section className={`att-card break-card${live.breakLeft < 0 ? ' over' : ''}`}>
          <div className="tc-head">
            <span className="hud-label">
              <span className={`hud-dot ${live.breakLeft < 0 ? 'bad' : 'amber'}`} aria-hidden="true" />
              {live.breakLeft < 0 ? 'BREAK OVER TIME' : `ON ${(current ? current.name : 'Break').toUpperCase()} \u00b7 ENJOY`}
            </span>
            <span className="mono muted">{bigTime(now)}</span>
          </div>
          <div className="bc-body">
            <div className="bc-food">
              <BreakFood type={current ? current.code : 'LUNCH'} size={300} over={live.breakLeft < 0} />
              {current && <span className={`bt-badge bt-${current.code.toLowerCase()}`}><span aria-hidden="true">{current.icon}</span> {current.name}</span>}
            </div>
            <div className="bc-count">
              <Ring size={200} stroke={12} value={live.breakSec / Math.max(1, live.allowance)} tone={live.breakLeft < 0 ? 'bad' : 'amber'}>
                {live.breakLeft >= 0 ? (
                  <>
                    <span className="ring-label">Break left</span>
                    <span className="ring-value mono">{clock(live.breakLeft)}</span>
                    <span className="ring-sub">of {hm(live.allowance)}</span>
                  </>
                ) : (
                  <>
                    <span className="ring-label bad"><AlertIcon size={16} /> Over by</span>
                    <span className="ring-value mono bad">+{clock(-live.breakLeft)}</span>
                    <span className="ring-sub">Your manager has been told</span>
                  </>
                )}
              </Ring>
              <p className="muted small">This break: {hm(live.currentBreak)} {'\u00b7'} Today: {hm(live.breakSec)}</p>
            </div>
          </div>
          {live.breakLeft < 0 && (
            <div className="att-nudge bad shake"><AlertIcon size={22} /><span>Your break is over the allowed time. Press <strong>End break</strong> as soon as you are back.</span></div>
          )}
          <div className="tc-actions">
            <button type="button" className="btn primary big" disabled={a.busy} onClick={() => run('breakEnd')}>I am back {'\u00b7'} End break</button>
          </div>
        </section>
      )}

      {day && day.status === 'CLOSED' && (
        <section className="att-card done-card att-wide">
          <div className={`done-badge ${day.marked ? 'ok' : 'bad'}`}>
            {day.marked
              ? <svg width="40" height="40" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 12l5 5L20 7" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" /></svg>
              : <AlertIcon size={40} />}
          </div>
          <div>
            <span className="hud-label"><span className="hud-dot" aria-hidden="true" /> DAY SAVED {'\u00b7'} {dayName(day.workDate).toUpperCase()}</span>
            <h2>{day.marked ? 'Present. See you tomorrow!' : 'Attendance not marked'}</h2>
            <p className={day.marked ? 'muted' : 'bad'}>{day.markReason}</p>
            <ul className="done-stats">
              <li><span>Punch in</span><strong>{timeOf(day.punchInAt)}</strong></li>
              <li><span>Punch out</span><strong>{timeOf(day.punchOutAt)}</strong></li>
              <li><span>Worked</span><strong>{hm(day.workedSeconds)}</strong></li>
              <li><span>Break</span><strong>{hm(day.breakSeconds)} / {hm(day.breakMinutes * 60)}</strong></li>
            </ul>
            <FlagChips flags={day.flags} />
            {day.closeNote && <p className="muted small">Note: {day.closeNote}</p>}
            {data.canPunchInAgain && (
              <div className="again-row">
                <button type="button" className="btn primary big" disabled={a.busy} onClick={() => run('in')}>Punch in again</button>
                <span className="muted small">Super Admin only. The time away is not counted as work or break.</span>
              </div>
            )}
          </div>
        </section>
      )}

      {day && (
        <section className="att-card">
          <h3>Today&apos;s timeline</h3>
          <Timeline day={day} />
        </section>
      )}

      <section className="att-card att-rules">
        <h3>The rules, in short</h3>
        <ul>
          <li><strong>{hm(s.shiftMinutes * 60)}</strong> shift with <strong>{hm(s.breakMinutes * 60)}</strong> compulsory break{s.custom ? ' (set for you by the Super Admin)' : ''}.</li>
          <li>Attendance is marked only when the full break is taken and the hours are done.</li>
          <li>A break longer than allowed is flagged as <span className="bad">break over time</span>.</li>
          <li>Every punch saves your computer and network{data.ip ? <> (now <span className="mono">{data.ip}</span>)</> : ''}. Punch only from your own computer.</li>
        </ul>
      </section>
    </div>
  )
}

function Timeline({ day }) {
  const items = [{ at: day.punchInAt, text: 'Punched in', kind: 'in' }]
  let n = 0
  ;(day.breaks || []).forEach((b) => {
    if (b.kind === 'OFF') {
      items.push({ at: b.startedAt, text: 'Punched out', kind: 'out' })
      items.push({ at: b.endedAt, text: `Punched in again (away ${hm(b.seconds)})`, kind: 'in' })
      return
    }
    n += 1
    const t = breakType(b.breakType)
    const name = t ? `${t.icon} ${t.name}` : `Break ${n}`
    items.push({ at: b.startedAt, text: `${name} started`, kind: 'break' })
    if (b.endedAt) items.push({ at: b.endedAt, text: `${name} ended (${hm(b.seconds)})`, kind: b.seconds > day.breakMinutes * 60 ? 'bad' : 'back' })
  })
  if (day.punchOutAt) items.push({ at: day.punchOutAt, text: 'Punched out', kind: 'out' })
  return (
    <ol className="timeline">
      {items.map((it, i) => (
        <li key={i} className={`tl-${it.kind}`}><span className="mono">{timeOf(it.at)}</span><span>{it.text}</span></li>
      ))}
    </ol>
  )
}

// ====================================================================== history

function DaysTable({ days }) {
  if (!days) return <div className="panel muted">Loading...</div>
  if (!days.length) return <div className="panel muted">No working days yet.</div>
  return (
    <div className="table-wrap">
      <table className="table">
        <thead><tr><th scope="col">Day</th><th scope="col">In</th><th scope="col">Out</th><th scope="col">Worked</th><th scope="col">Break</th><th scope="col">Attendance</th><th scope="col">Alerts</th></tr></thead>
        <tbody>
          {days.map((d) => (
            <tr key={d.id}>
              <td>{dayName(d.workDate)}</td>
              <td className="mono">{timeOf(d.punchInAt)}</td>
              <td className="mono">{d.punchOutAt ? timeOf(d.punchOutAt) : <span className="pill warn">Open</span>}</td>
              <td>{hm(d.workedSeconds)}</td>
              <td className={d.breakSeconds > d.breakMinutes * 60 + 60 ? 'bad' : ''}>{hm(d.breakSeconds)} / {hm(d.breakMinutes * 60)}</td>
              <td>{d.status !== 'CLOSED' ? <span className="pill info">In progress</span> : d.marked ? <span className="pill ok">Present</span> : <span className="pill bad" title={d.markReason}>Not marked</span>}</td>
              <td><FlagChips flags={d.flags} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function MyHistory() {
  const [days, setDays] = useState(null)
  const [error, setError] = useState('')
  useEffect(() => { api.attendanceHistory(31).then((d) => setDays(d || [])).catch((e) => setError(e.message)) }, [])
  const present = (days || []).filter((d) => d.marked).length
  return (
    <section className="panel">
      <div className="panel-head"><h2>Last 31 days</h2>{days && <p>{present} of {days.length} days marked present.</p>}</div>
      {error ? <div className="alert error" role="alert">{error}</div> : <DaysTable days={days} />}
    </section>
  )
}

// ====================================================================== team board

const STATE = {
  WORKING: ['Working', 'ok'], ON_BREAK: ['On break', 'amber'], PRESENT: ['Present', 'ok'], NOT_MARKED: ['Not marked', 'bad'],
  NOT_IN: ['Not punched in', 'muted'], ABSENT: ['Absent', 'muted'], FORGOT: ['Forgot to punch out', 'bad'],
}

function TeamBoard() {
  const [date, setDate] = useState(todayIst())
  const [team, setTeam] = useState(null)
  const [error, setError] = useState('')
  const [fetchedAt, setFetchedAt] = useState(Date.now())
  const [open, setOpen] = useState(null)
  const [filter, setFilter] = useState('ALL')
  const now = useNow()

  const load = useCallback(() => {
    api.attendanceTeam(date).then((t) => { setTeam(t); setFetchedAt(Date.now()); setError('') }).catch((e) => setError(e.message))
  }, [date])
  useEffect(() => {
    load()
    const id = setInterval(load, 30000)
    return () => clearInterval(id)
  }, [load])

  if (error) return <div className="alert error" role="alert">{error}</div>
  if (!team) return <div className="panel muted">Loading the team...</div>

  const rows = team.people.map((p) => ({ ...p, live: p.day ? liveDay({ ...p.day, _serverNow: team.serverNow }, fetchedAt, now) : null }))
  const alertsOf = (p) => (p.live ? p.live.flags : p.alerts).filter((f) => FLAG[f])
  const count = (fn) => rows.filter(fn).length
  const tiles = [
    ['ALL', 'Everyone', rows.length, ''],
    ['WORKING', 'Working now', count((p) => p.state === 'WORKING'), 'ok'],
    ['ON_BREAK', 'On break', count((p) => p.state === 'ON_BREAK'), 'amber'],
    ['NOT_IN', date === todayIst() ? 'Not punched in' : 'Absent', count((p) => p.state === 'NOT_IN' || p.state === 'ABSENT'), 'muted'],
    ['ALERTS', 'With alerts', count((p) => alertsOf(p).some((f) => FLAG[f].tone === 'bad')), 'bad'],
  ]
  const shown = rows.filter((p) => filter === 'ALL' || (filter === 'ALERTS' ? alertsOf(p).some((f) => FLAG[f].tone === 'bad')
    : filter === 'NOT_IN' ? (p.state === 'NOT_IN' || p.state === 'ABSENT') : p.state === filter))

  return (
    <>
      <div className="att-toolbar">
        <label className="field inline"><span>Day</span>
          <input type="date" value={date} max={todayIst()} onChange={(e) => e.target.value && setDate(e.target.value)} />
        </label>
        <button type="button" className="btn ghost" onClick={load}>Refresh</button>
        <span className="muted small">Updates every 30 seconds {'\u00b7'} times in India time</span>
      </div>
      <div className="att-tiles">
        {tiles.map(([k, label, n, tone]) => (
          <button key={k} type="button" className={`att-tile ${tone}${filter === k ? ' active' : ''}`} onClick={() => setFilter(k)}>
            <strong>{n}</strong><span>{label}</span>
          </button>
        ))}
      </div>
      <section className="panel">
        <div className="table-wrap">
          <table className="table att-team">
            <thead><tr><th scope="col">Employee</th><th scope="col">Status</th><th scope="col">In</th><th scope="col">Worked</th><th scope="col">Break</th><th scope="col">Alerts</th><th scope="col">IP and device</th></tr></thead>
            <tbody>
              {shown.map((p) => {
                const l = p.live
                const alerts = alertsOf(p)
                const [base, tone] = STATE[p.state] || [p.state, 'muted']
                const ob = p.state === 'ON_BREAK' && p.day ? breakType(((p.day.breaks || []).find((b) => b.kind !== 'OFF' && !b.endedAt) || {}).breakType) : null
                const label = ob ? `${ob.icon} ${ob.name}` : base
                const over = l && l.breakSec > l.allowance + 60
                return (
                  <tr key={p.userId} className={`clickable${alerts.some((f) => FLAG[f].tone === 'bad') ? ' row-alert' : ''}`} onClick={() => setOpen(p)}>
                    <td><strong>{p.fullName}</strong><span className="muted small block">{p.employeeCode ? `${p.employeeCode} \u00b7 ` : ''}{p.roleName}</span></td>
                    <td><span className={`state ${tone}`}><span className="state-dot" aria-hidden="true" />{label}</span></td>
                    <td className="mono">{p.day ? timeOf(p.day.punchInAt) : '\u2014'}</td>
                    <td className="mono">{l ? clock(l.worked) : '\u2014'}</td>
                    <td>
                      {l ? (
                        <div className={`mini-bar${over ? ' over' : ''}`} title={`${hm(l.breakSec)} of ${hm(l.allowance)}`}>
                          <span style={{ width: `${Math.min(100, (l.breakSec / Math.max(1, l.allowance)) * 100)}%` }} />
                          <em>{hm(l.breakSec)} / {hm(l.allowance)}</em>
                        </div>
                      ) : <span className="muted small">{hm(p.schedule.breakMinutes * 60)} allowed</span>}
                    </td>
                    <td>{alerts.length ? <FlagChips flags={alerts} /> : <span className="muted small">None</span>}</td>
                    <td><span className="mono small">{p.lastIp || '\u2014'}</span><span className="muted small block">{p.lastDevice || ''}{p.deviceCount > 1 ? ` \u00b7 ${p.deviceCount} devices` : ''}</span></td>
                  </tr>
                )
              })}
              {!shown.length && <tr><td colSpan={7} className="muted">Nobody here.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
      {open && <PersonDrawer person={open} canManage={team.canManage} onClose={() => setOpen(null)} onChanged={load} />}
    </>
  )
}

function PersonDrawer({ person, canManage, onClose, onChanged }) {
  const me = useMe()
  const [events, setEvents] = useState(null)
  const [days, setDays] = useState(null)
  const [error, setError] = useState('')
  const [closing, setClosing] = useState(null)
  const [toast, setToast] = useState('')

  const load = useCallback(() => {
    api.attendanceEvents(person.userId, 30).then((e) => setEvents(e || [])).catch((e) => setError(e.message))
    api.attendanceDays(person.userId, 31).then((d) => setDays(d || [])).catch((e) => setError(e.message))
  }, [person.userId])
  useEffect(() => { load() }, [load])

  const ips = new Set((events || []).map((e) => e.ip).filter(Boolean))
  const devices = new Set((events || []).map((e) => e.deviceId).filter(Boolean))
  const closable = (days || []).filter((d) => d.status !== 'CLOSED' || (d.flags || []).includes('FORGOT_PUNCH_OUT'))
  const selfBlocked = me && me.id === person.userId && me.roleCode !== 'SUPER_ADMIN'

  return (
    <Drawer title={person.fullName} onClose={onClose} wide>
      {error && <div className="alert error" role="alert">{error}</div>}
      <div className="att-person-head">
        <div><span className="muted small">Shift</span><strong>{hm(person.schedule.shiftMinutes * 60)} incl. {hm(person.schedule.breakMinutes * 60)} break</strong></div>
        <div><span className="muted small">IP addresses (30 days)</span><strong className={ips.size > 1 ? 'warn' : ''}>{ips.size}</strong></div>
        <div><span className="muted small">Devices (30 days)</span><strong className={devices.size > 1 ? 'bad' : ''}>{devices.size}</strong></div>
      </div>

      {canManage && closable.length > 0 && !selfBlocked && (
        <section className="att-close">
          <h3><AlertIcon /> Days to close</h3>
          {closable.map((d) => (
            <div key={d.id} className="att-close-row">
              <span>{dayName(d.workDate)} {'\u00b7'} in at {timeOf(d.punchInAt)} {d.status === 'CLOSED' ? '(closed by the system)' : '(still open)'}</span>
              <button type="button" className="btn small" onClick={() => setClosing(d)}>Close with a time</button>
            </div>
          ))}
        </section>
      )}
      {closing && <CloseForm day={closing} onCancel={() => setClosing(null)} onDone={() => { setClosing(null); setToast('Day closed'); load(); onChanged() }} />}

      <h3>IP address and device log</h3>
      <p className="muted small">Every sign-in and punch. A red row came from a new device, a new network or a computer someone else also used.</p>
      {!events ? <p className="muted">Loading...</p> : (
        <div className="table-wrap">
          <table className="table compact">
            <thead><tr><th scope="col">When</th><th scope="col">What</th><th scope="col">IP address</th><th scope="col">Device</th><th scope="col">Check</th></tr></thead>
            <tbody>
              {events.map((e) => (
                <tr key={e.id} className={e.flags.some((f) => FLAG[f]?.tone === 'bad') ? 'row-alert' : ''}>
                  <td className="mono small">{new Intl.DateTimeFormat('en-IN', { timeZone: ZONE, day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(e.at))}</td>
                  <td>{EVENT[e.kind] || e.kind}</td>
                  <td className="mono small">{e.ip || '\u2014'}</td>
                  <td className="small" title={e.userAgent || ''}>{e.deviceLabel || '\u2014'}{e.deviceId ? <span className="muted block mono">{e.deviceId.slice(0, 8)}</span> : null}</td>
                  <td>{e.flags.length ? <FlagChips flags={e.flags} /> : <span className="pill ok">OK</span>}</td>
                </tr>
              ))}
              {!events.length && <tr><td colSpan={5} className="muted">Nothing in the last 30 days.</td></tr>}
            </tbody>
          </table>
        </div>
      )}
      <h3>Working days</h3>
      <DaysTable days={days} />
      <Toast text={toast} onDone={() => setToast('')} />
    </Drawer>
  )
}

const EVENT = { SIGN_IN: 'Signed in', PUNCH_IN: 'Punched in', BREAK_START: 'Break started', BREAK_END: 'Break ended', PUNCH_OUT: 'Punched out', MANAGER_CLOSE: 'Closed by manager' }

function CloseForm({ day, onCancel, onDone }) {
  const [time, setTime] = useState('18:00')
  const [note, setNote] = useState('')
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function save(e) {
    e.preventDefault()
    const errs = {}
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) errs.time = 'Enter the time as HH:MM, for example 18:30.'
    if (note.trim().length < 5) errs.note = 'Write at least 5 characters, for example "Left at 6:30, confirmed by phone".'
    setErrors(errs)
    if (Object.keys(errs).length) return
    setBusy(true)
    setError('')
    try {
      await api.closeAttendanceDay(day.id, time, note.trim())
      onDone()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="att-close-form" onSubmit={save} noValidate>
      <p><strong>{dayName(day.workDate)}</strong>: when did this person really leave? The day is saved as "forgot to punch out" and is not marked present.</p>
      <div className="form two-col">
        <label className={`field${errors.time ? ' invalid' : ''}`}><span>Punch-out time (India time)</span>
          <input type="time" value={time} onChange={(e) => setTime(e.target.value)} />
          {errors.time && <small className="field-error" role="alert">{'\u26a0 '}{errors.time}</small>}
        </label>
        <label className={`field${errors.note ? ' invalid' : ''}`}><span>Why (saved in the audit log)</span>
          <input value={note} maxLength={300} onChange={(e) => setNote(e.target.value)} placeholder="Forgot to punch out, left at 18:30" />
          {errors.note && <small className="field-error" role="alert">{'\u26a0 '}{errors.note}</small>}
        </label>
      </div>
      {error && <div className="alert error" role="alert">{error}</div>}
      <div className="modal-actions">
        <button type="button" className="btn ghost" onClick={onCancel}>Cancel</button>
        <button type="submit" className="btn primary" disabled={busy}>{busy ? 'Saving...' : 'Close the day'}</button>
      </div>
    </form>
  )
}

// ====================================================================== working hours

function WorkingHours() {
  const can = useCan()
  const canManage = can('ATTENDANCE_MANAGE')
  const [rows, setRows] = useState(null)
  const [edit, setEdit] = useState({})
  const [errors, setErrors] = useState({})
  const [error, setError] = useState('')
  const [toast, setToast] = useState('')
  const [q, setQ] = useState('')

  const load = useCallback(() => {
    api.workSchedules().then((r) => {
      setRows(r || [])
      const e = {}
      ;(r || []).forEach((x) => { e[x.userId] = { shift: String(x.schedule.shiftMinutes / 60), brk: String(x.schedule.breakMinutes / 60) } })
      setEdit(e)
    }).catch((e) => setError(e.message))
  }, [])
  useEffect(() => { load() }, [load])

  async function save(r) {
    const v = edit[r.userId]
    const shift = Math.round(Number(v.shift) * 60)
    const brk = Math.round(Number(v.brk) * 60)
    let err = ''
    if (!v.shift || Number.isNaN(shift) || shift < 60 || shift > 960) err = 'Shift must be 1 to 16 hours.'
    else if (v.brk === '' || Number.isNaN(brk) || brk < 0 || brk > 240) err = 'Break must be 0 to 4 hours.'
    else if (brk >= shift) err = 'The break must be shorter than the shift.'
    setErrors({ ...errors, [r.userId]: err })
    if (err) return
    try {
      await api.saveWorkSchedule(r.userId, shift, brk)
      setToast(`Hours saved for ${r.fullName}`)
      load()
    } catch (e) {
      setErrors({ ...errors, [r.userId]: e.message })
    }
  }

  if (error) return <div className="alert error" role="alert">{error}</div>
  if (!rows) return <div className="panel muted">Loading...</div>
  const list = rows.filter((r) => !q || `${r.fullName} ${r.employeeCode || ''} ${r.roleName}`.toLowerCase().includes(q.toLowerCase()))

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Working hours per person</h2>
        <p>Shift includes the break. Example: 10 hours with a 2 hour break means 8 hours of work. {canManage ? 'A change starts at once, also for a day already running.' : 'You can see the hours; only people allowed to manage hours can change them.'}</p>
      </div>
      <div className="att-toolbar"><input className="search" placeholder="Search a name" value={q} onChange={(e) => setQ(e.target.value)} maxLength={60} /></div>
      <div className="table-wrap">
        <table className="table">
          <thead><tr><th scope="col">Employee</th><th scope="col">Shift (hours)</th><th scope="col">Break (hours)</th><th scope="col">Work time</th><th scope="col">Changed</th><th scope="col"><span className="sr-only">Save</span></th></tr></thead>
          <tbody>
            {list.map((r) => {
              const v = edit[r.userId] || { shift: '', brk: '' }
              const work = (Number(v.shift) - Number(v.brk)) * 3600
              return (
                <tr key={r.userId}>
                  <td><strong>{r.fullName}</strong><span className="muted small block">{r.roleName}{r.schedule.custom ? '' : ' \u00b7 normal hours'}</span></td>
                  <td><input className="hours-input" type="number" min="1" max="16" step="0.5" disabled={!r.editable} value={v.shift} aria-label={`Shift hours for ${r.fullName}`}
                    onChange={(e) => setEdit({ ...edit, [r.userId]: { ...v, shift: e.target.value } })} /></td>
                  <td><input className="hours-input" type="number" min="0" max="4" step="0.25" disabled={!r.editable} value={v.brk} aria-label={`Break hours for ${r.fullName}`}
                    onChange={(e) => setEdit({ ...edit, [r.userId]: { ...v, brk: e.target.value } })} /></td>
                  <td>{work > 0 ? hm(work) : '\u2014'}</td>
                  <td className="muted small">{r.updatedAt ? `${new Intl.DateTimeFormat('en-IN', { timeZone: ZONE, day: 'numeric', month: 'short' }).format(new Date(r.updatedAt))}${r.updatedBy ? ` by ${r.updatedBy}` : ''}` : '\u2014'}</td>
                  <td>
                    {r.editable ? <button type="button" className="btn small" onClick={() => save(r)}>Save</button> : <span className="muted small">{canManage ? 'Super Admin only' : ''}</span>}
                    {errors[r.userId] && <small className="field-error block" role="alert">{'\u26a0 '}{errors[r.userId]}</small>}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      <Toast text={toast} onDone={() => setToast('')} />
    </section>
  )
}
