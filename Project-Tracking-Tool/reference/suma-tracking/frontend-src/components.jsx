import { useEffect } from 'react'

/** Panel that slides in from the right, used for every Add / Edit form. */
export function Drawer({ title, onClose, children, footer, wide }) {
  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className="drawer-back" onClick={onClose}>
      <aside className={`drawer form-drawer${wide ? ' wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}
        onClick={(e) => e.stopPropagation()}>
        <div className="drawer-title">
          <h2>{title}</h2>
          <button className="drawer-x" type="button" onClick={onClose} aria-label="Close">{'\u00d7'}</button>
        </div>
        <div className="drawer-body">{children}</div>
        {footer && <div className="drawer-foot">{footer}</div>}
      </aside>
    </div>
  )
}

/** "Are you sure?" box with Cancel and a confirm button. */
export function Confirm({ title, message, confirmLabel, danger, onConfirm, onCancel, busy }) {
  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onCancel() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onCancel])
  return (
    <div className="modal-back" onClick={onCancel}>
      <div className="modal" role="alertdialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <h2>{title}</h2>
        <p>{message}</p>
        <div className="modal-actions">
          <button className="btn ghost" type="button" onClick={onCancel} autoFocus>Cancel</button>
          <button className={`btn ${danger ? 'danger' : 'primary'}`} type="button" onClick={onConfirm} disabled={busy}>
            {busy ? 'Please wait...' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

export function PageHead({ title, text, children }) {
  return (
    <header className="page-head">
      <div>
        <h1>{title}</h1>
        {text && <p className="muted">{text}</p>}
      </div>
      {children && <div className="head-actions">{children}</div>}
    </header>
  )
}

/** Short green message at the bottom of the screen after a save. */
export function Toast({ text, onDone }) {
  useEffect(() => {
    if (!text) return undefined
    const t = setTimeout(onDone, 3000)
    return () => clearTimeout(t)
  }, [text, onDone])
  if (!text) return null
  return <div className="toast" role="status">{text}</div>
}

export function Field({ label, error, children, hint }) {
  return (
    <label className={`field${error ? ' invalid' : ''}`}>
      <span>{label}</span>
      {children}
      {hint && !error && <small className="hint">{hint}</small>}
      {error && <small className="field-error" role="alert">{'\u26a0 '}{error}</small>}
    </label>
  )
}

export function IconButton({ label, onClick, kind, children }) {
  return (
    <button type="button" className={`icon-btn${kind ? ` ${kind}` : ''}`} onClick={onClick} title={label} aria-label={label}>
      {children}
    </button>
  )
}

export function EditIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M4 20h4L19 9l-4-4L4 16v4zM14 6l4 4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function DeleteIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}