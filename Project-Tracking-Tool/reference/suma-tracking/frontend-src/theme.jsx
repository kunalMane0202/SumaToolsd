import { useEffect, useState } from 'react'

// Step 40: dark (default) and light theme. The choice is remembered in this browser only.
const KEY = 'suma.theme'

export function initialTheme() {
  try {
    const saved = localStorage.getItem(KEY)
    if (saved === 'dark' || saved === 'light') return saved
  } catch { /* storage blocked: use the default */ }
  return 'dark'
}

export function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme)
  document.documentElement.style.colorScheme = theme
}

export function useTheme() {
  const [theme, setTheme] = useState(initialTheme)
  useEffect(() => {
    applyTheme(theme)
    try { localStorage.setItem(KEY, theme) } catch { /* ignore */ }
  }, [theme])
  return [theme, setTheme]
}

/**
 * Switch with a sun that turns into a moon. Where the browser supports it, the new theme
 * spreads out in a circle from the switch (View Transitions); otherwise colours fade.
 */
export function ThemeToggle({ theme, onChange, compact }) {
  const dark = theme === 'dark'
  function flip(e) {
    const next = dark ? 'light' : 'dark'
    const reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (!document.startViewTransition || reduce) { onChange(next); return }
    const r = e.currentTarget.getBoundingClientRect()
    const x = r.left + r.width / 2
    const y = r.top + r.height / 2
    const radius = Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y))
    const t = document.startViewTransition(() => { applyTheme(next); onChange(next) })
    t.ready.then(() => {
      document.documentElement.animate(
        { clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`] },
        { duration: 520, easing: 'cubic-bezier(.4,0,.2,1)', pseudoElement: '::view-transition-new(root)' },
      )
    }).catch(() => {})
  }
  return (
    <button type="button" className={`theme-toggle${dark ? ' is-dark' : ''}${compact ? ' compact' : ''}`} onClick={flip}
      role="switch" aria-checked={dark} aria-label={dark ? 'Dark mode on. Switch to light mode' : 'Light mode on. Switch to dark mode'}
      title={dark ? 'Switch to light mode' : 'Switch to dark mode'}>
      <span className="tt-track" aria-hidden="true">
        <span className="tt-stars"><i /><i /><i /></span>
        <span className="tt-thumb">
          <svg viewBox="0 0 24 24" width="14" height="14">
            <mask id="tt-moon-mask">
              <rect x="0" y="0" width="24" height="24" fill="white" />
              <circle className="tt-bite" cx="24" cy="6" r="8" fill="black" />
            </mask>
            <circle className="tt-core" cx="12" cy="12" r="5.5" fill="currentColor" mask="url(#tt-moon-mask)" />
            <g className="tt-rays" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <path d="M12 1.5v2.2M12 20.3v2.2M1.5 12h2.2M20.3 12h2.2M4.6 4.6l1.5 1.5M17.9 17.9l1.5 1.5M4.6 19.4l1.5-1.5M17.9 6.1l1.5-1.5" />
            </g>
          </svg>
        </span>
      </span>
    </button>
  )
}

/** Chart colours for the current theme; charts redraw when the theme changes. */
const CHART = {
  dark: { created: '#3EE6C1', done: '#FF8A5B', grid: '#2A2D34', tick: '#969CA8', label: '#C9CDD4', cursor: 'rgba(255,255,255,0.05)', topic: '#9B87F5', project: '#3EE6C1', channel: '#5EA2FF' },
  light: { created: '#0B8A71', done: '#E2622B', grid: '#E6EAF0', tick: '#5F6672', label: '#334155', cursor: '#F1F4F9', topic: '#6D5BD0', project: '#0B8A71', channel: '#2F6FE0' },
}

export function useChartColors() {
  const read = () => (document.documentElement.getAttribute('data-theme') === 'light' ? 'light' : 'dark')
  const [mode, setMode] = useState(read)
  useEffect(() => {
    const obs = new MutationObserver(() => setMode(read()))
    obs.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
    return () => obs.disconnect()
  }, [])
  return CHART[mode]
}