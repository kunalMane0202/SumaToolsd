import { useEffect, useRef, useState } from 'react'
import { api, setToken } from '../api.js'
import { rules } from '../validation.js'
import { ThemeToggle } from '../theme.jsx'
import { playBell } from '../sound.js'
import CodeSphere from '../CodeSphere.jsx'

// Step 40: secure sign-in screen. The role list is for information only: a person's role comes from
// their account, never from what they click here.
export const ROLES = [
  { code: 'SUPER_ADMIN', name: 'Super Admin', text: 'Runs the whole platform: clients, projects, people, roles and settings.', icon: 'M12 3l7 3v6c0 4-3 7.5-7 9-4-1.5-7-5-7-9V6z' },
  { code: 'CLIENT_ADMIN', name: 'Client Admin', text: 'Looks after the clients given to them and their projects.', icon: 'M3 21h18M5 21V8l7-4 7 4v13M9 21v-5h6v5' },
  { code: 'PROJECT_MANAGER', name: 'Project Manager', text: 'Runs projects: imports, tickets, SLA and reports.', icon: 'M3 7h18v13H3zM8 7V4h8v3' },
  { code: 'TEAM_LEADER', name: 'Team Leader', text: 'Shares the work: assigns tickets and watches workload.', icon: 'M16 20v-2a4 4 0 0 0-8 0v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8' },
  { code: 'L1_SUPPORT', name: 'L1 Support', text: 'First reply to customers. Escalates when needed.', icon: 'M4 5h16v11H8l-4 4z' },
  { code: 'L2_SUPPORT', name: 'L2 Support', text: 'Solves harder problems and escalated tickets.', icon: 'M14 6l6 6-6 6M4 12h15' },
  { code: 'L3_SUPPORT', name: 'L3 Support', text: 'Technical and development level issues.', icon: 'M8 8l-5 4 5 4M16 8l5 4-5 4M13 5l-2 14' },
  { code: 'MANAGEMENT_VIEWER', name: 'Management Viewer', text: 'Sees dashboards and reports. Changes nothing.', icon: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6' },
]

const WORD = 'ACCESS GRANTED'
const GLYPHS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#$%&*<>/\\'

export default function LoginPage({ onLoggedIn, theme, onTheme }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [emailErr, setEmailErr] = useState('')
  const [busy, setBusy] = useState(false)
  const [denied, setDenied] = useState(0)
  const [granted, setGranted] = useState(null)

  async function submit(e) {
    e.preventDefault()
    setError('')
    const emailError = rules.email('Email', true)(email)
    setEmailErr(emailError)
    if (emailError) return
    if (!password) { setError('Enter your password.'); return }
    setBusy(true)
    try {
      const res = await api.login(email.trim(), password)
      setToken(res.token)
      setPassword('')
      playBell()
      setGranted({ ...res.user, mustChangePassword: res.mustChangePassword })
    } catch (err) {
      setError(err.message)
      setDenied((n) => n + 1)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth">
      <div className="auth-grid" aria-hidden="true" />
      <div className="auth-scan" aria-hidden="true" />
      <header className="auth-top">
        <a className="auth-brand" href="#/about">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span className="brand-text"><span className="brand-name">Suma Tracking</span><span className="brand-sub">Support operations</span></span>
        </a>
        <div className="auth-top-right">
          <a className="auth-link" href="#/about">About the platform</a>
          <ThemeToggle theme={theme} onChange={onTheme} />
        </div>
      </header>

      <main className="auth-main">
        <section className={`hud-card${denied ? ' is-denied' : ''}`} key={denied} aria-labelledby="signin-title">
          <span className="hud-corner tl" aria-hidden="true" /><span className="hud-corner tr" aria-hidden="true" />
          <span className="hud-corner bl" aria-hidden="true" /><span className="hud-corner br" aria-hidden="true" />
          <div className="hud-label"><span className="hud-dot" aria-hidden="true" /> SECURE CHANNEL {'\u00b7'} TLS</div>
          <h1 id="signin-title">Sign in</h1>
          <p className="muted">Use the company email and password your Super Admin gave you.</p>
          <form className="login-form" onSubmit={submit} noValidate>
            <label className={`field${emailErr ? ' invalid' : ''}`}>
              <span>Email</span>
              <input type="email" autoComplete="username" value={email} placeholder="name@sumasoft.com" aria-invalid={Boolean(emailErr)}
                onChange={(e) => { setEmail(e.target.value); if (emailErr) setEmailErr(rules.email('Email', true)(e.target.value)) }}
                onBlur={() => email && setEmailErr(rules.email('Email', true)(email))} />
              {emailErr && <small className="field-error" role="alert">{'\u26a0 '}{emailErr}</small>}
            </label>
            <label className="field">
              <span>Password</span>
              <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
            </label>
            {error && <div className="alert error hud-denied" role="alert"><strong>ACCESS DENIED.</strong> {error}</div>}
            <button className="btn primary wide hud-submit" type="submit" disabled={busy}>
              {busy ? 'Verifying...' : 'Sign in'}
            </button>
            <p className="fine">After 5 wrong passwords the account is locked for 15 minutes. A sign-in sound plays when access is granted.</p>
          </form>
        </section>

        <section className="hud-roles" aria-labelledby="roles-title">
          <div className="hud-label"><span className="hud-dot amber" aria-hidden="true" /> ACCESS LEVELS</div>
          <h2 id="roles-title">One sign-in, eight roles</h2>
          <p className="muted">Your role is set on your account. It decides which pages and actions you get.</p>
          <ul className="role-grid">
            {ROLES.map((r) => (
              <li key={r.code} className="role-card">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d={r.icon} stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>
                <div><strong>{r.name}</strong><span>{r.text}</span></div>
              </li>
            ))}
          </ul>
        </section>
      </main>

      {granted && <Granted user={granted} theme={theme} onEnter={() => onLoggedIn(granted)} />}
    </div>
  )
}

/** Full-screen "access granted": a 3D sphere of decorative code that can be turned with the mouse. */
function Granted({ user, theme, onEnter }) {
  const [word, setWord] = useState(WORD.replace(/\S/g, '#'))
  const [left, setLeft] = useState(6)
  const entered = useRef(false)
  const role = ROLES.find((r) => r.code === user.roleCode)

  // the words scramble, then settle letter by letter
  useEffect(() => {
    let frame = 0
    const id = setInterval(() => {
      frame += 1
      const settled = Math.floor(frame / 2)
      setWord(WORD.split('').map((c, i) => (c === ' ' || i < settled ? c : GLYPHS[Math.floor(Math.random() * GLYPHS.length)])).join(''))
      if (settled >= WORD.length) clearInterval(id)
    }, 45)
    return () => clearInterval(id)
  }, [])

  useEffect(() => {
    const id = setInterval(() => setLeft((s) => s - 1), 1000)
    return () => clearInterval(id)
  }, [])

  useEffect(() => {
    if (left <= 0 && !entered.current) { entered.current = true; onEnter() }
  }, [left, onEnter])

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Enter' && !entered.current) { entered.current = true; onEnter() } }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onEnter])

  return (
    <div className="granted" role="dialog" aria-modal="true" aria-labelledby="granted-title">
      <CodeSphere accent={theme === 'light' ? '#0B8A71' : '#3EE6C1'} second={theme === 'light' ? '#E2622B' : '#FF8A5B'} count={260} />
      <div className="granted-panel">
        <div className="hud-label"><span className="hud-dot" aria-hidden="true" /> IDENTITY VERIFIED</div>
        <h1 id="granted-title" className="granted-word" aria-label="Access granted">{word}</h1>
        <p className="granted-who">Welcome, <strong>{user.fullName}</strong></p>
        {role && <p className="granted-role">{role.name} {'\u00b7'} {role.text}</p>}
        <div className="granted-bar" aria-hidden="true"><span style={{ animationDuration: '6s' }} /></div>
        <button type="button" className="btn primary" onClick={() => { if (!entered.current) { entered.current = true; onEnter() } }}>
          Enter Suma Tracking
        </button>
        <p className="fine">Opening in {Math.max(left, 0)} s {'\u00b7'} move or drag the mouse to turn the sphere</p>
      </div>
    </div>
  )
}