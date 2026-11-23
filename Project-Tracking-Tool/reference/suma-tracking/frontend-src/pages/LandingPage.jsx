import { ThemeToggle } from '../theme.jsx'
import CodeSphere from '../CodeSphere.jsx'
import { ROLES } from './LoginPage.jsx'

// Step 40: the public page of Suma Tracking. Everything written here is true of the tool today.

const FEATURES = [
  { t: 'Every ticket in one place', d: 'Codility (Zendesk), SouthCo (Keypanion) and the agents\u2019 daily Excel sheets land in one list, with the same statuses and priorities.', i: 'M4 6h16M4 12h16M4 18h10' },
  { t: 'Daily sheets, no duplicates', d: 'Upload the month\u2019s sheet as often as you like. New tickets are created, changed ones updated, the rest left alone. Check first, then import.', i: 'M12 3v12m0 0l-4-4m4 4l4-4M4 17v3h16v-3' },
  { t: 'SLA you can trust', d: 'First response and resolution targets per project and priority, 24x7 or business hours with holidays. At-risk and breached tickets come first.', i: 'M12 7v5l3 3M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18' },
  { t: 'Automation every 5 minutes', d: 'SLA alerts, assignment alerts, auto-escalation, auto-assignment to the least busy L1 and a morning summary. Every action is logged.', i: 'M13 2L4 14h7l-1 8 9-12h-7z' },
  { t: 'Role-based access', d: '8 roles and 27 permissions, checked by the server on every request. People see only the clients, projects and tickets they belong to.', i: 'M12 3l7 3v6c0 4-3 7.5-7 9-4-1.5-7-5-7-9V6z' },
  { t: 'Dashboards and reports', d: 'Tickets by day, hour, status, project, channel and agent for any dates, plus project reports you can download.', i: 'M4 20V10M10 20V4M16 20v-7M22 20H2' },
  { t: 'Help in plain words', d: 'A Knowledge Base of simple guides. Search with a word, or ask in your own words, even in Hinglish.', i: 'M4 19.5A2.5 2.5 0 0 1 6.5 17H20V3H6.5A2.5 2.5 0 0 0 4 5.5z' },
  { t: 'Attendance with device checks', d: 'Punch in, compulsory breaks and punch out with a live timer. A break over time, a missed break, a new laptop or a shared computer is flagged at once.', i: 'M12 7v5l3 2M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18' },
  { t: 'A record of every change', d: 'An append-only audit log, and nothing is ever hard-deleted: clients, projects and people are deactivated, never erased.', i: 'M9 11l3 3 8-8M20 12v7a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h9' },
]

const FACTS = [
  ['3', 'ticket sources joined'], ['8', 'roles'], ['27', 'permissions checked on every request'], ['5 min', 'automation cycle'], ['44', 'help guides'],
]

const SECURITY = [
  'Passwords are stored only as BCrypt hashes; nobody can read them.',
  'Sign-in tokens are kept as fingerprints, not in plain form.',
  'An account locks for 15 minutes after 5 wrong passwords.',
  'A temporary password must be changed at the first sign-in.',
  'Every API call is checked against the role\u2019s permissions.',
  'Real client data stays on Suma Soft systems, never in the code repository.',
]

export default function LandingPage({ theme, onTheme, signedIn }) {
  const enter = signedIn ? '#/dashboard' : '#/login'
  return (
    <div className="site">
      <header className="site-nav">
        <a className="auth-brand" href="#/about">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span className="brand-text"><span className="brand-name">Suma Tracking</span><span className="brand-sub">by Suma Soft</span></span>
        </a>
        <nav className="site-links" aria-label="Page sections">
          <a href="#features" onClick={(e) => jump(e, 'features')}>Features</a>
          <a href="#how" onClick={(e) => jump(e, 'how')}>How it works</a>
          <a href="#roles" onClick={(e) => jump(e, 'roles')}>Roles</a>
          <a href="#security" onClick={(e) => jump(e, 'security')}>Security</a>
        </nav>
        <div className="site-nav-right">
          <ThemeToggle theme={theme} onChange={onTheme} />
          <a className="btn primary" href={enter}>{signedIn ? 'Open the app' : 'Sign in'}</a>
        </div>
      </header>

      <section className="hero">
        <div className="hero-copy">
          <div className="hud-label"><span className="hud-dot" aria-hidden="true" /> SUPPORT OPERATIONS PLATFORM</div>
          <h1>Every client&apos;s tickets. <span className="glow-text">One command centre.</span></h1>
          <p>
            Suma Soft supports clients who each use a different ticket tool. Suma Tracking brings them together,
            so every manager and agent sees the same truth: what came in, who owns it, and what is about to break its SLA.
          </p>
          <div className="hero-cta">
            <a className="btn primary big" href={enter}>{signedIn ? 'Open the app' : 'Sign in to Suma Tracking'}</a>
            <a className="btn big" href="#how" onClick={(e) => jump(e, 'how')}>See how it works</a>
          </div>
        </div>
        <div className="hero-visual">
          <div className="hero-sphere">
            <CodeSphere accent={theme === 'light' ? '#0B8A71' : '#3EE6C1'} second={theme === 'light' ? '#E2622B' : '#FF8A5B'} count={150} intensity={0.9} />
          </div>
          <p className="fine center">Drag to turn</p>
        </div>
      </section>

      <section className="facts" aria-label="At a glance">
        {FACTS.map(([n, l]) => <div key={l}><strong>{n}</strong><span>{l}</span></div>)}
      </section>

      <section className="site-section" id="motive">
        <div className="section-head">
          <div className="hud-label"><span className="hud-dot amber" aria-hidden="true" /> WHY WE BUILT IT</div>
          <h2>Our motive</h2>
        </div>
        <div className="motive">
          <p>
            Before Suma Tracking, each team kept its own sheet and its own tool. Nobody could answer simple questions quickly:
            How many tickets came in today across all clients? Which ones are late? Who is overloaded?
          </p>
          <p>
            We built one common platform so that every promise we make to a client is visible, measured and kept:
            a fast first reply, a fair share of work for every agent, and a clear record of who did what.
          </p>
        </div>
      </section>

      <section className="site-section" id="features">
        <div className="section-head">
          <div className="hud-label"><span className="hud-dot" aria-hidden="true" /> WHAT IT DOES</div>
          <h2>Everything a support desk needs, in one tool</h2>
        </div>
        <div className="feature-grid">
          {FEATURES.map((f) => (
            <article key={f.t} className="feature">
              <span className="feature-icon" aria-hidden="true">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none"><path d={f.i} stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>
              </span>
              <h3>{f.t}</h3>
              <p>{f.d}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="site-section" id="how">
        <div className="section-head">
          <div className="hud-label"><span className="hud-dot" aria-hidden="true" /> HOW IT WORKS</div>
          <h2>From every tool to one dashboard</h2>
          <p className="muted">Tickets flow in from each client&apos;s tool, are matched to one set of statuses, and then feed SLA, automation and reports.</p>
        </div>
        <Flow />
      </section>

      <section className="site-section" id="roles">
        <div className="section-head">
          <div className="hud-label"><span className="hud-dot amber" aria-hidden="true" /> WHO USES IT</div>
          <h2>Eight roles, each with exactly what it needs</h2>
        </div>
        <ul className="role-grid wide">
          {ROLES.map((r) => (
            <li key={r.code} className="role-card">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d={r.icon} stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>
              <div><strong>{r.name}</strong><span>{r.text}</span></div>
            </li>
          ))}
        </ul>
      </section>

      <section className="site-section" id="security">
        <div className="section-head">
          <div className="hud-label"><span className="hud-dot" aria-hidden="true" /> SECURITY</div>
          <h2>Safe by design</h2>
        </div>
        <ul className="sec-list">
          {SECURITY.map((s) => (
            <li key={s}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 12l5 5L20 7" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" /></svg>
              {s}
            </li>
          ))}
        </ul>
      </section>

      <section className="cta-band">
        <h2>Ready to start your shift?</h2>
        <a className="btn primary big" href={enter}>{signedIn ? 'Open the app' : 'Sign in'}</a>
      </section>

      <footer className="site-foot">
        <span>Suma Tracking {'\u00b7'} an internal platform of Suma Soft</span>
        <span>Accounts are created by your Super Admin.</span>
      </footer>
    </div>
  )
}

function jump(e, id) {
  e.preventDefault()
  const el = document.getElementById(id)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

/** The workflow picture: sources on the left, the hub in the middle, outcomes on the right, with moving signals. */
function Flow() {
  const left = [['Zendesk', 'Codility tickets', 60], ['Keypanion', 'SouthCo tickets', 170], ['Excel sheets', 'Daily agent logs', 280]]
  const right = [['SLA engine', 'At risk and breached', 40], ['Automation', 'Alerts and escalation', 130], ['Dashboards', 'Live numbers', 220], ['Reports', 'Download as CSV', 310]]
  const hub = { x: 470, y: 170 }
  return (
    <div className="flow-wrap">
      <svg className="flow" viewBox="0 0 940 360" role="img" aria-label="Zendesk, Keypanion and Excel sheets flow into Suma Tracking, which feeds the SLA engine, automation, dashboards and reports">
        <defs>
          <filter id="flow-glow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation="4" result="b" />
            <feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge>
          </filter>
        </defs>
        {left.map(([, , y], i) => {
          const d = `M 230 ${y} C 340 ${y}, 350 ${hub.y}, ${hub.x - 50} ${hub.y}`
          return (
            <g key={`l${i}`}>
              <path d={d} className="flow-line" id={`fl${i}`} />
              <circle r="4" className="flow-signal" filter="url(#flow-glow)">
                <animateMotion dur={`${2.4 + i * 0.4}s`} repeatCount="indefinite" path={d} />
              </circle>
            </g>
          )
        })}
        {right.map(([, , y], i) => {
          const d = `M ${hub.x + 50} ${hub.y} C 590 ${hub.y}, 600 ${y + 22}, 700 ${y + 22}`
          return (
            <g key={`r${i}`}>
              <path d={d} className="flow-line" />
              <circle r="4" className="flow-signal alt" filter="url(#flow-glow)">
                <animateMotion dur={`${2.2 + i * 0.35}s`} begin={`${i * 0.3}s`} repeatCount="indefinite" path={d} />
              </circle>
            </g>
          )
        })}
        {left.map(([t, s, y]) => (
          <g key={t} transform={`translate(40 ${y - 26})`}>
            <rect width="190" height="52" rx="10" className="flow-node" />
            <text x="16" y="23" className="flow-title">{t}</text>
            <text x="16" y="40" className="flow-sub">{s}</text>
            <circle cx="190" cy="26" r="4" className="flow-port" />
          </g>
        ))}
        <g transform={`translate(${hub.x - 50} ${hub.y - 50})`}>
          <rect width="100" height="100" rx="18" className="flow-hub" filter="url(#flow-glow)" />
          <text x="50" y="58" textAnchor="middle" className="flow-hub-mark">S</text>
          <text x="50" y="128" textAnchor="middle" className="flow-title">Suma Tracking</text>
        </g>
        {right.map(([t, s, y]) => (
          <g key={t} transform={`translate(700 ${y})`}>
            <rect width="200" height="46" rx="10" className="flow-node" />
            <circle cx="0" cy="23" r="4" className="flow-port alt" />
            <text x="16" y="20" className="flow-title">{t}</text>
            <text x="16" y="36" className="flow-sub">{s}</text>
          </g>
        ))}
      </svg>
    </div>
  )
}
