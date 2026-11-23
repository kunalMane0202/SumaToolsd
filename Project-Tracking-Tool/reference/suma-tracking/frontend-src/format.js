// Small helpers for showing numbers, dates and durations the same way everywhere.

const IST = 'Asia/Kolkata'

export function fmtNumber(n) {
  if (n === null || n === undefined) return '0'
  return new Intl.NumberFormat('en-IN').format(n)
}

export function fmtPercent(part, whole) {
  if (!whole) return '0%'
  return `${Math.round((part / whole) * 100)}%`
}

/** Minutes -> "45 min", "3 h 20 min", "2 d 4 h". */
export function fmtMinutes(min) {
  if (min === null || min === undefined) return '-'
  const m = Math.round(min)
  if (m < 60) return `${m} min`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} h ${m % 60} min`
  const d = Math.floor(h / 24)
  return `${d} d ${h % 24} h`
}

export function fmtDateTime(iso) {
  if (!iso) return '-'
  return new Intl.DateTimeFormat('en-IN', {
    timeZone: IST, day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(iso))
}

export function fmtDay(isoDate) {
  if (!isoDate) return ''
  const [y, m, d] = isoDate.split('-').map(Number)
  return new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short' }).format(new Date(y, m - 1, d))
}

/** Today in India as yyyy-mm-dd. */
export function todayIso() {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: IST, year: 'numeric', month: '2-digit', day: '2-digit' })
    .format(new Date())
  return parts
}

export function addDays(iso, days) {
  const [y, m, d] = iso.split('-').map(Number)
  const dt = new Date(Date.UTC(y, m - 1, d + days))
  return dt.toISOString().slice(0, 10)
}

export function monthStart(iso) {
  return `${iso.slice(0, 8)}01`
}

/** Colour for each status group, used in charts, badges and the legend. */
export const GROUP_COLORS = {
  OPEN: '#3D5AFE',
  IN_PROGRESS: '#7B61FF',
  PENDING: '#E8A33D',
  RESOLVED: '#14A38B',
  CLOSED: '#5B6B7F',
}

export const GROUP_LABELS = {
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  PENDING: 'Waiting',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
}