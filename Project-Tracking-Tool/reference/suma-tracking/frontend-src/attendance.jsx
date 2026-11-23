import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { api, getToken } from './api.js'
import { playAlarm, playLoudBell } from './sound.js'

// Step 41: the employee's working day, shared by the punch banner, the topbar timer and the Attendance page.
// The server keeps the real times; the screen only counts the seconds between two refreshes.

export const ZONE = 'Asia/Kolkata'

const DEVICE_KEY = 'suma.device'

/** One id per browser on this computer, kept in the browser. It is how a new or shared laptop is noticed. */
export function deviceInfo() {
  let id = null
  try {
    id = localStorage.getItem(DEVICE_KEY)
    if (!id || !/^[A-Za-z0-9-]{8,64}$/.test(id)) {
      id = (crypto.randomUUID ? crypto.randomUUID() : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`)
      localStorage.setItem(DEVICE_KEY, id)
    }
  } catch {
    id = null
  }
  return { deviceId: id, deviceLabel: deviceLabel() }
}

function deviceLabel() {
  const ua = navigator.userAgent || ''
  const browser = /Edg\//.test(ua) ? 'Edge' : /OPR\//.test(ua) ? 'Opera' : /Chrome\//.test(ua) ? 'Chrome'
    : /Firefox\//.test(ua) ? 'Firefox' : /Safari\//.test(ua) ? 'Safari' : 'Browser'
  const os = /Windows NT 10/.test(ua) ? 'Windows' : /Windows/.test(ua) ? 'Windows' : /Android/.test(ua) ? 'Android'
    : /iPhone|iPad/.test(ua) ? 'iPhone/iPad' : /Mac OS X/.test(ua) ? 'Mac' : /Linux/.test(ua) ? 'Linux' : 'unknown system'
  const screenSize = typeof window !== 'undefined' && window.screen ? ` \u00b7 ${window.screen.width}x${window.screen.height}` : ''
  return `${browser} on ${os}${screenSize}`.slice(0, 120)
}

export const FLAG = {
  BREAK_OVERTIME: { text: 'Break over time', tone: 'bad', help: 'The break took longer than allowed.' },
  BREAK_DUE: { text: 'Break due', tone: 'warn', help: 'Working a long time without a break.' },
  NO_BREAK: { text: 'Compulsory break not taken', tone: 'bad', help: 'Attendance is not marked without the full break.' },
  SHORT_BREAK: { text: 'Break too short', tone: 'warn', help: 'Less break than required, so attendance is not marked.' },
  SHORT_HOURS: { text: 'Fewer hours', tone: 'warn', help: 'Worked less than the shift.' },
  FORGOT_PUNCH_OUT: { text: 'Forgot to punch out', tone: 'bad', help: 'The day was left open.' },
  NEW_DEVICE: { text: 'New device', tone: 'bad', help: 'Punched or signed in from a computer not used before.' },
  NEW_IP: { text: 'New IP address', tone: 'warn', help: 'Came from a network not used before.' },
  SHARED_DEVICE: { text: 'Shared computer', tone: 'bad', help: 'The same computer was used by another person.' },
  UNKNOWN_DEVICE: { text: 'Device not recognised', tone: 'warn', help: 'The browser did not allow a device id.' },
}

/** A tick every second. */
export function useNow(ms = 1000) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), ms)
    return () => clearInterval(id)
  }, [ms])
  return now
}

/** The live numbers of an open day: what the server said, plus the seconds since then. */
export function liveDay(day, fetchedAt, now) {
  if (!day) return null
  const open = day.status !== 'CLOSED'
  const passed = open ? Math.max(0, Math.floor((now - fetchedAt) / 1000)) : 0
  const onBreak = day.status === 'ON_BREAK'
  const worked = day.workedSeconds + (open && !onBreak ? passed : 0)
  const breakSec = day.breakSeconds + (onBreak ? passed : 0)
  const continuous = onBreak ? 0 : day.continuousSeconds + passed
  const allowance = day.breakMinutes * 60
  const required = (day.shiftMinutes - day.breakMinutes) * 60
  const flags = new Set(day.flags || [])
  if (open) {
    if (breakSec > allowance + 60) flags.add('BREAK_OVERTIME')
    if (!onBreak && breakSec + 60 < allowance && continuous >= required / 2) flags.add('BREAK_DUE')
    if (worked >= required && breakSec + 60 < allowance) flags.add('NO_BREAK')
  }
  const currentBreak = onBreak && day.onBreakSince ? Math.max(0, Math.floor((now - fetchedAt) / 1000) + Math.floor((Date.parse(day._serverNow) - Date.parse(day.onBreakSince)) / 1000)) : 0
  return {
    open, onBreak, worked, breakSec, continuous, allowance, required, currentBreak,
    breakLeft: allowance - breakSec,
    workLeft: required - worked,
    flags: [...flags],
    complete: breakSec + 60 >= allowance && worked + 60 >= required,
  }
}

const AttendanceContext = createContext(null)

export function AttendanceProvider({ children }) {
  const [state, setState] = useState({ data: null, fetchedAt: Date.now(), error: '' })
  const [busy, setBusy] = useState(false)
  const alarmed = useRef(0)

  const load = useCallback(() => api.attendanceMe()
    .then((d) => setState({ data: withServerNow(d), fetchedAt: Date.now(), error: '' }))
    .catch((e) => setState((s) => ({ ...s, error: e.message }))), [])

  useEffect(() => {
    load()
    const id = setInterval(load, 60000)
    const onFocus = () => load()
    window.addEventListener('focus', onFocus)
    return () => { clearInterval(id); window.removeEventListener('focus', onFocus) }
  }, [load])

  // once per sign-in: tell the server which computer and network this is
  useEffect(() => {
    try {
      const key = 'suma.seen'
      const mark = (getToken() || '').slice(-16)
      if (sessionStorage.getItem(key) === mark) return
      sessionStorage.setItem(key, mark)
    } catch {
      /* fine */
    }
    api.attendanceSeen(deviceInfo()).catch(() => {})
  }, [])

  const act = useCallback(async (kind, extra) => {
    const call = { in: api.punchIn, breakStart: api.breakStart, breakEnd: api.breakEnd, out: api.punchOut }[kind]
    setBusy(true)
    try {
      const d = await call({ ...deviceInfo(), ...(extra || {}) })
      setState({ data: withServerNow(d), fetchedAt: Date.now(), error: '' })
      if (kind === 'in') playLoudBell('in')
      if (kind === 'out') playLoudBell('out')
      return null
    } catch (e) {
      load()
      return e.message
    } finally {
      setBusy(false)
    }
  }, [load])

  // the alarm: when the break runs over, ring now and again every 2 minutes while it lasts
  const now = useNow()
  const live = state.data?.day ? liveDay(state.data.day, state.fetchedAt, now) : null
  const over = Boolean(live && live.onBreak && live.breakLeft < 0)
  useEffect(() => {
    if (!over) { alarmed.current = 0; return }
    if (Date.now() - alarmed.current > 120000) {
      alarmed.current = Date.now()
      playAlarm(8)
    }
  }, [over, now])

  const value = useMemo(() => ({ ...state, busy, live, act, reload: load }), [state, busy, live, act, load])
  return <AttendanceContext.Provider value={value}>{children}</AttendanceContext.Provider>
}

function withServerNow(d) {
  if (d && d.day) d.day._serverNow = d.serverNow
  return d
}

export function useAttendance() {
  return useContext(AttendanceContext)
}

// ------------------------------------------------------------------ formatting

export function clock(seconds, withSeconds = true) {
  const s = Math.max(0, Math.floor(Math.abs(seconds)))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  const two = (n) => String(n).padStart(2, '0')
  return withSeconds ? `${two(h)}:${two(m)}:${two(sec)}` : `${h} h ${two(m)} min`
}

export function hm(seconds) {
  const m = Math.floor(Math.max(0, seconds) / 60)
  return m >= 60 ? `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min` : `${m} min`
}

export function timeOf(iso) {
  if (!iso) return '\u2014'
  return new Intl.DateTimeFormat('en-IN', { timeZone: ZONE, hour: '2-digit', minute: '2-digit', hour12: true }).format(new Date(iso))
}

export function nowParts(ms) {
  const parts = new Intl.DateTimeFormat('en-GB', { timeZone: ZONE, hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
    .formatToParts(new Date(ms))
  const get = (t) => Number(parts.find((p) => p.type === t)?.value || 0)
  return { h: get('hour') % 24, m: get('minute'), s: get('second') }
}

export function bigTime(ms) {
  return new Intl.DateTimeFormat('en-IN', { timeZone: ZONE, hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true }).format(new Date(ms))
}

export function longDate(ms) {
  return new Intl.DateTimeFormat('en-IN', { timeZone: ZONE, weekday: 'long', day: 'numeric', month: 'long' }).format(new Date(ms))
}

// ------------------------------------------------------------------ pictures

/** A real-time analog clock (India time). */
export function AnalogClock({ size = 120, now, tone = 'accent' }) {
  const { h, m, s } = nowParts(now)
  const hourA = ((h % 12) + m / 60) * 30
  const minA = (m + s / 60) * 6
  const secA = s * 6
  return (
    <svg className={`aclock ${tone}`} width={size} height={size} viewBox="0 0 100 100" aria-hidden="true">
      <circle cx="50" cy="50" r="46" className="aclock-face" />
      {Array.from({ length: 60 }, (_, i) => (
        <line key={i} x1="50" y1={i % 5 ? 8 : 6} x2="50" y2={i % 5 ? 10 : 13} className={i % 5 ? 'aclock-min' : 'aclock-hour'}
          transform={`rotate(${i * 6} 50 50)`} />
      ))}
      <line x1="50" y1="50" x2="50" y2="27" className="aclock-h" transform={`rotate(${hourA} 50 50)`} />
      <line x1="50" y1="50" x2="50" y2="17" className="aclock-m" transform={`rotate(${minA} 50 50)`} />
      <g className="aclock-s" style={{ transform: `rotate(${secA}deg)` }}>
        <line x1="50" y1="58" x2="50" y2="12" />
        <circle cx="50" cy="12" r="1.8" />
      </g>
      <circle cx="50" cy="50" r="3" className="aclock-pin" />
    </svg>
  )
}

/** A progress ring. value 0..1 (more than 1 shows as over). */
export function Ring({ size = 220, stroke = 12, value, tone = 'accent', children }) {
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  const v = Math.max(0, Math.min(1, value))
  return (
    <div className={`ring ${tone}`} style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden="true">
        <circle cx={size / 2} cy={size / 2} r={r} className="ring-track" strokeWidth={stroke} />
        <circle cx={size / 2} cy={size / 2} r={r} className="ring-bar" strokeWidth={stroke}
          strokeDasharray={c} strokeDashoffset={c * (1 - v)} transform={`rotate(-90 ${size / 2} ${size / 2})`} />
      </svg>
      <div className="ring-inner" style={{ '--ring-font': `${Math.round(size * 0.145)}px` }}>{children}</div>
    </div>
  )
}

/** The lunch thali: rice, dal and curry, chapatis, a samosa and a cup of chai, all steaming. */
/** The four kinds of break. They all share the same daily break time. */
export const BREAK_TYPES = [
  { code: 'TEA', name: 'Tea Break', icon: '\u2615', text: 'A quick cup of chai' },
  { code: 'LUNCH', name: 'Lunch Break', icon: '\ud83c\udf71', text: 'Your main meal' },
  { code: 'DINNER', name: 'Dinner Break', icon: '\ud83c\udf7d\ufe0f', text: 'For evening and night shifts' },
  { code: 'REFRESHMENT', name: 'Refreshment Break', icon: '\ud83c\udf3f', text: 'Snacks, a walk or fresh air' },
]

export function breakType(code) {
  return BREAK_TYPES.find((b) => b.code === code) || null
}

/** The picture on the break card, by type of break. */
export function BreakFood({ type, size = 300, over = false }) {
  if (type === 'TEA') return <TeaCup size={size} over={over} />
  if (type === 'REFRESHMENT') return <Snacks size={size} over={over} />
  return <Thali size={size} over={over} night={type === 'DINNER'} />
}

/** Tea: a big cutting chai with steam and two biscuits. */
function TeaCup({ size, over }) {
  return (
    <svg className={`thali${over ? ' over' : ''}`} width={size} height={size * 0.8} viewBox="0 0 300 240" role="img" aria-label="A cup of tea with biscuits">
      <ellipse cx="150" cy="200" rx="110" ry="22" fill="#9AA1AC" opacity=".55" />
      <ellipse cx="150" cy="196" rx="96" ry="16" fill="#E7EAEE" />
      <path d="M100 96 L200 96 L188 186 Q150 200 112 186 Z" fill="#F4F6F8" stroke="#AEB4BE" strokeWidth="3" />
      <ellipse cx="150" cy="96" rx="50" ry="11" fill="#B9773C" />
      <ellipse cx="146" cy="94" rx="20" ry="3" fill="#D99A5B" opacity=".8" />
      <path d="M198 112 Q232 114 226 140 Q220 162 194 158" fill="none" stroke="#AEB4BE" strokeWidth="7" strokeLinecap="round" />
      <g className="th-samosa">
        <circle cx="58" cy="186" r="20" fill="#D9A55A" /><circle cx="58" cy="186" r="15" fill="none" stroke="#B8823A" strokeWidth="1.5" strokeDasharray="2 4" />
        <circle cx="244" cy="190" r="18" fill="#D9A55A" /><circle cx="244" cy="190" r="13" fill="none" stroke="#B8823A" strokeWidth="1.5" strokeDasharray="2 4" />
      </g>
      <g className="th-steam" fill="none" strokeWidth="4" strokeLinecap="round">
        <path d="M128 84 q-10 -16 0 -32 q10 -16 0 -32" />
        <path d="M150 82 q-10 -16 0 -32 q10 -16 0 -32" />
        <path d="M172 84 q-10 -16 0 -32 q10 -16 0 -32" />
      </g>
    </svg>
  )
}

/** Refreshment: samosas, a glass of nimbu pani and a little plant. */
function Snacks({ size, over }) {
  return (
    <svg className={`thali${over ? ' over' : ''}`} width={size} height={size * 0.8} viewBox="0 0 300 240" role="img" aria-label="Samosas, a glass of lime juice and a plant">
      <ellipse cx="120" cy="190" rx="96" ry="30" fill="url(#sn-plate)" />
      <defs>
        <radialGradient id="sn-plate" cx="50%" cy="45%" r="60%"><stop offset="0%" stopColor="#F4F6F8" /><stop offset="100%" stopColor="#A9AFB8" /></radialGradient>
        <linearGradient id="sn-juice" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#D9F99D" /><stop offset="100%" stopColor="#84CC16" /></linearGradient>
      </defs>
      <g className="th-samosa">
        <path d="M60 192 L96 132 L120 194 Z" fill="#D9932E" /><path d="M60 192 L96 132 L92 196 Z" fill="#E9AB45" />
        <path d="M112 196 L150 140 L172 198 Z" fill="#D9932E" /><path d="M112 196 L150 140 L146 199 Z" fill="#E9AB45" />
      </g>
      <circle cx="178" cy="180" r="9" fill="#2F9E44" /><circle cx="190" cy="186" r="7" fill="#C2410C" />
      <g className="th-chai">
        <path d="M206 70 L262 70 L254 196 L214 196 Z" fill="rgba(255,255,255,.35)" stroke="#AEB4BE" strokeWidth="3" />
        <path d="M209 104 L259 104 L253 192 L215 192 Z" fill="url(#sn-juice)" opacity=".9" />
        <circle cx="236" cy="104" r="15" fill="#A3E635" stroke="#65A30D" strokeWidth="3" />
        <path d="M226 60 L246 120" stroke="#F97316" strokeWidth="5" strokeLinecap="round" />
        <circle cx="225" cy="150" r="3" fill="#fff" opacity=".8" /><circle cx="240" cy="170" r="2.5" fill="#fff" opacity=".8" /><circle cx="232" cy="128" r="2" fill="#fff" opacity=".8" />
      </g>
      <g className="th-roti">
        <path d="M34 120 Q20 90 40 70 Q52 96 34 120 Z" fill="#40C057" />
        <path d="M38 122 Q60 96 80 102 Q64 124 38 122 Z" fill="#2F9E44" />
        <path d="M36 124 L36 150" stroke="#2F9E44" strokeWidth="4" />
        <path d="M22 150 L50 150 L46 172 L26 172 Z" fill="#C2410C" />
      </g>
    </svg>
  )
}

export function Thali({ size = 260, over = false, night = false }) {
  return (
    <svg className={`thali${over ? ' over' : ''}`} width={size} height={size * 0.8} viewBox="0 0 300 240" role="img" aria-label="Lunch plate with rice, curry, chapati, samosa and chai">
      <defs>
        <radialGradient id="th-plate" cx="50%" cy="45%" r="60%">
          <stop offset="0%" stopColor="#F4F6F8" /><stop offset="70%" stopColor="#C9CED6" /><stop offset="100%" stopColor="#9AA1AC" />
        </radialGradient>
        <radialGradient id="th-curry" cx="50%" cy="40%" r="60%">
          <stop offset="0%" stopColor="#F7A43B" /><stop offset="100%" stopColor="#C2560F" />
        </radialGradient>
        <radialGradient id="th-dal" cx="50%" cy="40%" r="60%">
          <stop offset="0%" stopColor="#FFD86B" /><stop offset="100%" stopColor="#D99A16" />
        </radialGradient>
        <radialGradient id="th-roti" cx="45%" cy="40%" r="65%">
          <stop offset="0%" stopColor="#F3D29A" /><stop offset="100%" stopColor="#C8954C" />
        </radialGradient>
      </defs>
      {/* plate */}
      <ellipse cx="140" cy="150" rx="128" ry="74" fill="url(#th-plate)" />
      <ellipse cx="140" cy="146" rx="108" ry="60" fill="none" stroke="#AEB4BE" strokeWidth="2" />
      {/* chapati stack */}
      <g className="th-roti">
        <ellipse cx="88" cy="160" rx="46" ry="22" fill="#B98443" />
        <ellipse cx="90" cy="154" rx="46" ry="22" fill="url(#th-roti)" />
        <circle cx="76" cy="150" r="3" fill="#9C6A2E" opacity=".7" /><circle cx="100" cy="158" r="2.5" fill="#9C6A2E" opacity=".6" />
        <circle cx="108" cy="148" r="2" fill="#9C6A2E" opacity=".6" /><circle cx="84" cy="162" r="2" fill="#9C6A2E" opacity=".5" />
      </g>
      {/* rice mound */}
      <g className="th-rice">
        <ellipse cx="150" cy="168" rx="36" ry="16" fill="#E9E5DA" />
        <path d="M116 168 Q150 128 184 168 Z" fill="#FBF9F3" />
        {Array.from({ length: 14 }, (_, i) => (
          <ellipse key={i} cx={128 + (i * 37) % 46} cy={150 + (i * 13) % 16} rx="3.2" ry="1.4" fill="#E3DDCC" transform={`rotate(${(i * 47) % 180} ${128 + (i * 37) % 46} ${150 + (i * 13) % 16})`} />
        ))}
      </g>
      {/* curry bowl */}
      <g className="th-bowl">
        <ellipse cx="200" cy="124" rx="38" ry="18" fill="#8E949E" />
        <path d="M162 124 Q164 152 200 154 Q236 152 238 124 Z" fill="#A9AFB8" />
        <ellipse cx="200" cy="124" rx="32" ry="13" fill="url(#th-curry)" />
        <circle cx="190" cy="121" r="3" fill="#7BB547" /><circle cx="206" cy="127" r="2.4" fill="#7BB547" /><circle cx="212" cy="119" r="2" fill="#FFF3D6" />
      </g>
      {/* dal bowl */}
      <g className="th-bowl">
        <ellipse cx="128" cy="110" rx="30" ry="14" fill="#8E949E" />
        <path d="M98 110 Q100 132 128 134 Q156 132 158 110 Z" fill="#A9AFB8" />
        <ellipse cx="128" cy="110" rx="25" ry="10" fill="url(#th-dal)" />
        <circle cx="122" cy="108" r="1.6" fill="#B5541A" /><circle cx="134" cy="111" r="1.4" fill="#B5541A" />
      </g>
      {/* samosa */}
      <g className="th-samosa">
        <path d="M206 186 L238 140 L256 190 Z" fill="#D9932E" />
        <path d="M206 186 L238 140 L232 188 Z" fill="#E9AB45" />
        <path d="M214 180 L236 150" stroke="#B9741E" strokeWidth="1.5" />
      </g>
      {/* chai cup */}
      <g className="th-chai">
        <path d="M236 70 L274 70 L268 108 Q255 114 242 108 Z" fill="#F4F6F8" stroke="#AEB4BE" strokeWidth="2" />
        <ellipse cx="255" cy="70" rx="19" ry="5" fill="#B9773C" />
        <path d="M273 78 Q288 80 284 92 Q280 100 270 98" fill="none" stroke="#AEB4BE" strokeWidth="3" />
      </g>
      {night && (
        <g className="th-night">
          <path d="M44 34 A20 20 0 1 0 70 62 A16 16 0 1 1 44 34 Z" fill="#FFD43B" />
          <circle cx="96" cy="30" r="2.5" fill="#FFE066" /><circle cx="120" cy="52" r="2" fill="#FFE066" /><circle cx="24" cy="80" r="2" fill="#FFE066" />
        </g>
      )}
      {/* steam */}
      <g className="th-steam" fill="none" strokeWidth="3" strokeLinecap="round">
        <path d="M190 104 q-8 -12 0 -24 q8 -12 0 -24" />
        <path d="M206 102 q-8 -12 0 -24 q8 -12 0 -24" />
        <path d="M126 96 q-6 -10 0 -20 q6 -10 0 -20" />
        <path d="M250 60 q-6 -10 0 -20 q6 -10 0 -20" />
        <path d="M262 60 q-6 -10 0 -20 q6 -10 0 -20" />
      </g>
    </svg>
  )
}

export function AlertIcon({ size = 18 }) {
  return (
    <svg className="alert-icon" width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M12 3L2 20h20L12 3z" fill="currentColor" opacity=".18" />
      <path d="M12 3L2 20h20L12 3zM12 10v4M12 17h.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function FlagChips({ flags }) {
  if (!flags || !flags.length) return null
  return (
    <ul className="flag-chips">
      {flags.map((f) => {
        const d = FLAG[f] || { text: f, tone: 'warn', help: '' }
        return (
          <li key={f} className={`flag-chip ${d.tone}`} title={d.help}>
            <AlertIcon size={14} />{d.text}
          </li>
        )
      })}
    </ul>
  )
}

// ------------------------------------------------------------------ the big punch-in call to action (on every page)

export function PunchBanner() {
  const a = useAttendance()
  const now = useNow()
  const [error, setError] = useState('')
  if (!a || !a.data) return null
  const { data } = a
  const again = Boolean(data.canPunchInAgain)
  if (!again && (data.day || data.doneForToday)) return null
  const s = data.schedule
  const { h } = nowParts(now)
  const hello = h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'
  return (
    <section className="punch-banner" aria-label="Punch in">
      <div className="pb-glow" aria-hidden="true" />
      <AnalogClock size={92} now={now} />
      <div className="pb-copy">
        <span className="hud-label"><span className="hud-dot amber" aria-hidden="true" /> {again ? 'PUNCHED OUT \u00b7 SUPER ADMIN' : 'NOT PUNCHED IN'}</span>
        <h2>{again ? 'You can punch in again.' : `${hello}! Start your day.`}</h2>
        <p>{longDate(now)} {'\u00b7'} <span className="mono">{bigTime(now)}</span> {'\u00b7'} Shift {hm(s.shiftMinutes * 60)} with {hm(s.breakMinutes * 60)} break</p>
        {error && <p className="pb-error" role="alert">{'\u26a0 '}{error}</p>}
      </div>
      <button type="button" className="punch-btn" disabled={a.busy}
        onClick={async () => { setError((await a.act('in')) || '') }}>
        <span className="pulse-ring r1" aria-hidden="true" /><span className="pulse-ring r2" aria-hidden="true" /><span className="pulse-ring r3" aria-hidden="true" />
        <span className="punch-btn-core">
          <svg width="30" height="30" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 2v10M6.3 6.3a8 8 0 1 0 11.4 0" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" /></svg>
          <span>{a.busy ? 'Wait...' : again ? 'PUNCH IN AGAIN' : 'PUNCH IN'}</span>
        </span>
      </button>
    </section>
  )
}

/** Small live timer in the top bar. */
export function TopbarTimer() {
  const a = useAttendance()
  if (!a || !a.data || !a.data.day || !a.live || !a.live.open) return null
  const l = a.live
  const over = l.onBreak && l.breakLeft < 0
  const ob = l.onBreak ? (a.data.day.breaks || []).find((b) => b.kind !== 'OFF' && !b.endedAt) : null
  const icon = ob ? breakType(ob.breakType)?.icon : null
  const alerts = l.flags.filter((f) => FLAG[f] && FLAG[f].tone === 'bad').length
  const cls = over ? 'tt over' : l.onBreak ? 'tt break' : l.workLeft <= 0 ? 'tt done' : 'tt'
  return (
    <a className={cls} href="#/attendance" aria-label="Open my attendance">
      <span className="tt-dot" aria-hidden="true" />
      {l.onBreak
        ? <span className="mono">{icon ? `${icon} ` : ''}{over ? `Break +${clock(-l.breakLeft)}` : `Break ${clock(l.breakLeft)} left`}</span>
        : <span className="mono">{clock(l.worked)}</span>}
      {l.workLeft <= 0 && !l.onBreak && <span className="tt-hint">Punch out</span>}
      {(over || alerts > 0) && <AlertIcon size={15} />}
    </a>
  )
}
