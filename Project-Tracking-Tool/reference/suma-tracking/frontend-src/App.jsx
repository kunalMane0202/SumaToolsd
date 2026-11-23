import { useCallback, useEffect, useState } from 'react'
import { api, getToken, setToken, setUnauthorizedHandler } from './api.js'
import { PermissionContext, fallbackPermissions } from './permissions.js'
import LoginPage from './pages/LoginPage.jsx'
import LandingPage from './pages/LandingPage.jsx'
import { ThemeToggle, useTheme } from './theme.jsx'
import ChangePasswordPage from './pages/ChangePasswordPage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import ClientsPage from './pages/ClientsPage.jsx'
import ProjectsPage from './pages/ProjectsPage.jsx'
import IntegrationsPage from './pages/IntegrationsPage.jsx'
import TicketsPage from './pages/TicketsPage.jsx'
import TeamPage from './pages/TeamPage.jsx'
import ReportsPage from './pages/ReportsPage.jsx'
import SecurityPage from './pages/SecurityPage.jsx'
import KnowledgePage from './pages/KnowledgePage.jsx'
import SettingsPage from './pages/SettingsPage.jsx'
import SlaPage from './pages/SlaPage.jsx'
import NotificationsPage from './pages/NotificationsPage.jsx'
import AutomationPage from './pages/AutomationPage.jsx'
import AttendancePage from './pages/AttendancePage.jsx'
import { AttendanceProvider, PunchBanner, TopbarTimer } from './attendance.jsx'

// Step 39: each menu item needs one of these permissions (no list = every signed-in employee).
// The server checks the same permissions on every API call, so hiding is not the protection.
const MENU = [
  { key: 'dashboard', label: 'Dashboard', perms: ['DASHBOARD_VIEW'], icon: 'M4 20V10M10 20V4M16 20v-7M22 20H2' },
  { key: 'attendance', label: 'Attendance', icon: 'M12 7v5l3 2M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18M5 3L2 6M19 3l3 3' },
  { key: 'clients', label: 'Client Management', icon: 'M3 21h18M5 21V7l7-4 7 4v14M9 21v-6h6v6', perms: ['CLIENT_MANAGE', 'CLIENT_EDIT_ASSIGNED'] },
  { key: 'projects', label: 'Project Management', icon: 'M3 7h18v13H3zM8 7V4h8v3', perms: ['PROJECT_VIEW', 'PROJECT_MANAGE'] },
  { key: 'integrations', label: 'Integration Management', icon: 'M8 7h8M8 17h8M4 12h16M12 3v4M12 17v4', perms: ['IMPORT_VIEW', 'IMPORT_RUN'] },
  { key: 'tickets', label: 'Ticket Management', icon: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01', perms: ['TICKET_VIEW'] },
  { key: 'team', label: 'Team Management', icon: 'M16 21v-2a4 4 0 0 0-8 0v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M22 21v-2a3 3 0 0 0-3-3M2 21v-2a3 3 0 0 1 3-3', perms: ['WORKLOAD_VIEW', 'USER_MANAGE'] },
  { key: 'sla', label: 'SLA Management', icon: 'M12 7v5l3 3M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18', perms: ['SLA_VIEW', 'SLA_MANAGE'] },
  { key: 'automation', label: 'Automation', icon: 'M13 2L4 14h7l-1 8 9-12h-7l1-8z', perms: ['AUTOMATION_MANAGE'] },
  { key: 'reports', label: 'Reports', icon: 'M6 2h9l5 5v15H6zM14 2v6h6M9 13h6M9 17h6', perms: ['REPORT_VIEW'] },
  { key: 'notifications', label: 'Notifications', icon: 'M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0' },
  { key: 'knowledge', label: 'Knowledge Base', icon: 'M4 19.5A2.5 2.5 0 0 1 6.5 17H20V3H6.5A2.5 2.5 0 0 0 4 5.5zM4 19.5A2.5 2.5 0 0 0 6.5 22H20v-5', perms: ['KB_VIEW', 'KB_MANAGE'] },
  { key: 'security', label: 'Security', icon: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z', perms: ['ROLE_MANAGE', 'AUDIT_VIEW'] },
  { key: 'settings', label: 'Settings', icon: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z' },
]

// Older links (#/import, #/people) still work.
const ALIASES = { import: 'integrations', people: 'team' }

/** Public pages (no sign-in needed): #/about and #/login. */
function publicPage() {
  const raw = window.location.hash.replace('#/', '').split(/[?/]/)[0]
  return raw === 'about' || raw === 'login' ? raw : null
}

function pageFromHash() {
  // "#/tickets?q=124628&open=1" and "#/knowledge/add-employee" keep their page
  const raw = window.location.hash.replace('#/', '').split(/[?/]/)[0]
  const key = ALIASES[raw] || raw
  return MENU.some((m) => m.key === key) ? key : 'dashboard'
}

export default function App() {
  const [user, setUser] = useState(null)
  const [checking, setChecking] = useState(Boolean(getToken()))
  const [page, setPage] = useState(pageFromHash())
  const [hash, setHash] = useState(window.location.hash)
  const [unread, setUnread] = useState(0)
  const [theme, setTheme] = useTheme()
  const [pub, setPub] = useState(publicPage())
  const [perm, setPerm] = useState(null)
  const [menuOpen, setMenuOpen] = useState(false)
  const [navOpen, setNavOpen] = useState(false)

  const signOut = useCallback(() => {
    setToken(null)
    setUser(null)
    setPerm(null)
  }, [])

  // Step 39: what this employee may do (asked from the server after sign-in).
  useEffect(() => {
    if (!user || user.mustChangePassword) return
    let alive = true
    api.myPermissions()
      .then((p) => { if (alive) setPerm({ superAdmin: Boolean(p.superAdmin), permissions: p.permissions || [] }) })
      .catch(() => { if (alive) setPerm(fallbackPermissions(user)) })
    return () => { alive = false }
  }, [user])

  // close the user menu and the phone menu when the page changes
  useEffect(() => { setMenuOpen(false); setNavOpen(false) }, [page])

  useEffect(() => {
    setUnauthorizedHandler(signOut)
    if (!getToken()) return
    api.me()
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setChecking(false))
  }, [signOut])

  useEffect(() => {
    const onHash = () => { setPage(pageFromHash()); setHash(window.location.hash); setPub(publicPage()) }
    window.addEventListener('hashchange', onHash)
    return () => window.removeEventListener('hashchange', onHash)
  }, [])

  // Unread notifications: checked on sign-in, on every page change and every minute.
  useEffect(() => {
    if (!user || user.mustChangePassword) return undefined
    let alive = true
    const check = () => api.unreadCount().then((c) => { if (alive) setUnread(c.count) }).catch(() => {})
    check()
    const timer = setInterval(check, 60000)
    return () => { alive = false; clearInterval(timer) }
  }, [user, page])

  async function handleLogout() {
    try {
      await api.logout()
    } catch {
      /* the session ends on this side anyway */
    }
    signOut()
    window.location.hash = '#/login'
  }

  if (checking) return <div className="boot">Opening Suma Tracking...</div>
  if (pub === 'about' || (!user && pub !== 'login')) {
    return <LandingPage theme={theme} onTheme={setTheme} signedIn={Boolean(user)} />
  }
  if (!user) {
    return (
      <LoginPage theme={theme} onTheme={setTheme}
        onLoggedIn={(u) => { setUser(u); window.location.hash = '#/dashboard' }} />
    )
  }
  if (user.mustChangePassword) {
    return <ChangePasswordPage user={user} onDone={(u) => setUser(u)} onLogout={handleLogout} />
  }

  if (!perm) return <div className="boot">Opening Suma Tracking...</div>

  const ctx = { superAdmin: perm.superAdmin, permissions: new Set(perm.permissions), user }
  const allowed = (m) => !m.perms || perm.superAdmin || m.perms.some((p) => ctx.permissions.has(p))
  const menu = MENU.filter(allowed)
  const wanted = MENU.find((m) => m.key === page)
  const denied = wanted && !allowed(wanted)
  const current = denied ? null : (menu.some((m) => m.key === page) ? page : 'dashboard')
  const currentItem = MENU.find((m) => m.key === (current || page))
  const canTickets = allowed(MENU.find((m) => m.key === 'tickets'))

  function quickOpen(e) {
    e.preventDefault()
    const v = new FormData(e.currentTarget).get('ticket')?.toString().trim()
    if (!v) return
    e.currentTarget.reset()
    window.location.hash = `#/tickets?q=${encodeURIComponent(v.slice(0, 60))}&open=1`
  }

  return (
    <PermissionContext.Provider value={ctx}>
      <AttendanceProvider key={user.id}>
      <div className={`shell${navOpen ? ' nav-open' : ''}`}>
        <aside className="sidebar" aria-label="Main menu">
          <div className="brand">
            <span className="brand-mark" aria-hidden="true">S</span>
            <span className="brand-text">
              <span className="brand-name">Suma Tracking</span>
              <span className="brand-sub">Support operations</span>
            </span>
          </div>
          <nav className="nav" aria-label="Main">
            {menu.map((m) => (
              <a key={m.key} href={`#/${m.key}`} className={`nav-link${current === m.key ? ' active' : ''}`}
                aria-current={current === m.key ? 'page' : undefined}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d={m.icon} stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
                <span>{m.label}</span>
                {m.key === 'notifications' && unread > 0 && (
                  <span className="nav-badge" aria-label={`${unread} unread`}>{unread > 99 ? '99+' : unread}</span>
                )}
              </a>
            ))}
          </nav>
          <div className="side-foot">
            <span className="side-role">{user.roleName}</span>
            <span className="side-ver">Suma Tracking 1.0</span>
          </div>
        </aside>
        <button type="button" className="nav-scrim" aria-label="Close menu" onClick={() => setNavOpen(false)} tabIndex={navOpen ? 0 : -1} />

        <div className="workspace">
          <header className="topbar">
            <button type="button" className="icon-plain nav-toggle" aria-label="Open menu" onClick={() => setNavOpen(true)}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" strokeWidth="2" strokeLinecap="round" /></svg>
            </button>
            <div className="crumbs">
              <span className="crumb-root">Suma Tracking</span>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M9 6l6 6-6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg>
              <span className="crumb-here">{denied ? 'No access' : currentItem?.label}</span>
            </div>
            {canTickets && (
              <form className="quick-open" onSubmit={quickOpen} role="search">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="11" cy="11" r="7" stroke="currentColor" strokeWidth="2" /><path d="M20 20l-3.5-3.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" /></svg>
                <label htmlFor="quick-ticket" className="sr-only">Open a ticket by number</label>
                <input id="quick-ticket" name="ticket" placeholder="Open ticket no. e.g. 124628" maxLength={60} autoComplete="off" />
              </form>
            )}
            <TopbarTimer />
            <ThemeToggle theme={theme} onChange={setTheme} compact />
            <a className="icon-plain bell" href="#/notifications" aria-label={unread ? `Notifications, ${unread} unread` : 'Notifications'}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>
              {unread > 0 && <span className="bell-dot">{unread > 9 ? '9+' : unread}</span>}
            </a>
            <div className="user-menu">
              <button type="button" className="user-chip" aria-haspopup="menu" aria-expanded={menuOpen} onClick={() => setMenuOpen((o) => !o)}>
                <span className="me-avatar" aria-hidden="true">{initials(user.fullName)}</span>
                <span className="user-chip-text">
                  <span className="user-chip-name">{user.fullName}</span>
                  <span className="user-chip-role">{user.roleName}</span>
                </span>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /></svg>
              </button>
              {menuOpen && (
                <div className="user-pop" role="menu">
                  <div className="user-pop-head">
                    <strong>{user.fullName}</strong>
                    <span>{user.email}</span>
                    <span className="tag">{user.roleName}</span>
                  </div>
                  <a role="menuitem" href="#/settings" onClick={() => setMenuOpen(false)}>My account and password</a>
                  <a role="menuitem" href="#/knowledge" onClick={() => setMenuOpen(false)}>Help and guides</a>
                  <a role="menuitem" href="#/about" onClick={() => setMenuOpen(false)}>About Suma Tracking</a>
                  <button role="menuitem" type="button" onClick={handleLogout}>Log out</button>
                </div>
              )}
            </div>
          </header>

          <main className="main" id="main">
            {current !== 'attendance' && <PunchBanner />}
            {denied && <AccessDenied page={wanted.label} role={user.roleName} />}
            {current === 'dashboard' && <DashboardPage user={user} />}
            {current === 'clients' && <ClientsPage user={user} />}
            {current === 'projects' && <ProjectsPage user={user} />}
            {current === 'integrations' && <IntegrationsPage user={user} />}
            {current === 'tickets' && <TicketsPage key={hash} user={user} />}
            {current === 'team' && <TeamPage user={user} />}
            {current === 'reports' && <ReportsPage user={user} />}
            {current === 'security' && <SecurityPage user={user} />}
            {current === 'sla' && <SlaPage user={user} />}
            {current === 'automation' && <AutomationPage user={user} />}
            {current === 'notifications' && <NotificationsPage user={user} onCountChange={setUnread} />}
            {current === 'knowledge' && <KnowledgePage user={user} />}
            {current === 'settings' && <SettingsPage user={user} />}
            {current === 'attendance' && <AttendancePage user={user} />}
          </main>
        </div>
      </div>
      </AttendanceProvider>
    </PermissionContext.Provider>
  )
}

/** Shown when someone opens a page their role does not allow (for example by typing the address). */
function AccessDenied({ page, role }) {
  return (
    <div className="page narrow">
      <section className="panel denied" role="alert">
        <div className="denied-icon" aria-hidden="true">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none"><rect x="4" y="10" width="16" height="11" rx="2" stroke="currentColor" strokeWidth="1.8" /><path d="M8 10V7a4 4 0 0 1 8 0v3" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" /></svg>
        </div>
        <h1>No access to {page}</h1>
        <p>Your role, {role}, is not allowed to open this page. If you need it for your work, ask the Super Admin to give your role this permission.</p>
        <a className="btn primary" href="#/dashboard">Go to the dashboard</a>
      </section>
    </div>
  )
}

function initials(name) {
  return (name || '?').split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase()
}
