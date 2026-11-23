import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { PageHead } from '../components.jsx'
import { fmtDateTime } from '../format.js'

const KIND = {
  SLA_RESPONSE_BREACHED: { label: 'SLA breached', tone: 'bad' },
  SLA_RESOLUTION_BREACHED: { label: 'SLA breached', tone: 'bad' },
  SLA_RESPONSE_AT_RISK: { label: 'SLA at risk', tone: 'warn' },
  SLA_RESOLUTION_AT_RISK: { label: 'SLA at risk', tone: 'warn' },
  AUTO_ESCALATED: { label: 'Escalated', tone: 'bad' },
  TICKET_ESCALATED: { label: 'Escalated', tone: 'bad' },
  TICKET_ASSIGNED: { label: 'Assigned to you', tone: 'info' },
  DAILY_SUMMARY: { label: 'Daily summary', tone: 'ok' },
  ATTENDANCE: { label: 'Attendance', tone: 'warn' },
}

/** The signed-in employee's notifications (Step 37). */
export default function NotificationsPage({ onCountChange }) {
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [rows, setRows] = useState(null)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    setError('')
    api.notifications(unreadOnly).then((r) => setRows(r || [])).catch((e) => setError(e.message))
  }, [unreadOnly])
  useEffect(() => { load() }, [load])

  async function read(n) {
    try {
      const c = await api.markNotificationRead(n.id)
      onCountChange(c.count)
      setRows((list) => (unreadOnly ? list.filter((x) => x.id !== n.id) : list.map((x) => (x.id === n.id ? { ...x, read: true } : x))))
    } catch (e) {
      setError(e.message)
    }
  }

  async function readAll() {
    try {
      const c = await api.markAllNotificationsRead()
      onCountChange(c.count)
      load()
    } catch (e) {
      setError(e.message)
    }
  }

  const unread = rows ? rows.filter((r) => !r.read).length : 0
  return (
    <div className="page narrow">
      <PageHead title="Notifications" text="SLA alerts, escalations, tickets assigned to you and the daily summary. New ones arrive within 5 minutes.">
        <button className="btn" type="button" onClick={load}>Refresh</button>
        <button className="btn primary" type="button" onClick={readAll} disabled={!unread}>Mark all as read</button>
      </PageHead>
      <div className="segmented" role="tablist">
        <button type="button" role="tab" aria-selected={!unreadOnly} className={!unreadOnly ? 'on' : ''} onClick={() => setUnreadOnly(false)}>All</button>
        <button type="button" role="tab" aria-selected={unreadOnly} className={unreadOnly ? 'on' : ''} onClick={() => setUnreadOnly(true)}>Unread</button>
      </div>
      {error && <div className="alert error" role="alert">{error}</div>}
      {rows && rows.length === 0 && (
        <div className="empty">
          <strong>{unreadOnly ? 'Nothing unread' : 'No notifications yet'}</strong>
          <p>Alerts appear here when a ticket is close to breaking its SLA, is escalated, or is assigned to you.</p>
        </div>
      )}
      {rows && rows.length > 0 && (
        <ul className="note-list">
          {rows.map((n) => {
            const k = KIND[n.kind] || { label: 'Notice', tone: 'info' }
            return (
              <li key={n.id} className={n.read ? 'read' : 'unread'}>
                <span className={`note-dot ${k.tone}`} aria-hidden="true" />
                <div className="note-body">
                  <div className="note-top">
                    <span className={`note-kind ${k.tone}`}>{k.label}</span>
                    <span className="muted small">{fmtDateTime(n.createdAt)}</span>
                  </div>
                  <div className="note-title">{n.title}</div>
                  {n.body && <p className="note-text">{n.body}</p>}
                  {(n.ticketNumber || n.projectName) && (
                    <div className="muted small">
                      {n.projectName}{n.projectName && n.ticketNumber ? ' \u00b7 ' : ''}{n.ticketNumber && <a href={`#/tickets?q=${encodeURIComponent(n.ticketNumber)}&open=1`}>Open ticket {n.ticketNumber}</a>}
                    </div>
                  )}
                </div>
                {!n.read && <button type="button" className="btn ghost" onClick={() => read(n)}>Mark read</button>}
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
