import { useEffect, useRef } from "react";
import { X } from "lucide-react";
import "./ui.css";

export function initials(name = "") {
  const parts = String(name).trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return "?";
  return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase();
}

function hueIndex(name = "") {
  let hash = 0;
  for (const char of String(name)) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return hash % 6;
}

export function Avatar({ name, size = "md", muted = false }) {
  if (!name || muted) {
    return <span className={`ui-avatar ui-avatar--${size} ui-avatar--empty`} title="Unassigned" aria-label="Unassigned">?</span>;
  }
  return <span className={`ui-avatar ui-avatar--${size} ui-avatar--h${hueIndex(name)}`} title={name} aria-hidden="true">{initials(name)}</span>;
}

export function Toggle({ checked, onChange, disabled, label }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      className={`ui-toggle${checked ? " is-on" : ""}`}
      onClick={() => onChange(!checked)}
    >
      <span className="ui-toggle__knob" />
    </button>
  );
}

function useDialog(onClose) {
  const ref = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    const node = ref.current;
    const focusable = node?.querySelector("input, select, textarea, button:not(.ui-dialog__close)");
    (focusable || node)?.focus?.();
    const onKey = (event) => { if (event.key === "Escape") onClose(); };
    document.addEventListener("keydown", onKey);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = overflow;
      previous?.focus?.();
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
  return ref;
}

export function Modal({ title, subtitle, onClose, children, footer, size = "md" }) {
  const ref = useDialog(onClose);
  return (
    <div className="ui-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <div className={`ui-dialog ui-dialog--${size}`} role="dialog" aria-modal="true" aria-label={title} ref={ref} tabIndex={-1}>
        <header className="ui-dialog__head">
          <div><h3>{title}</h3>{subtitle && <p>{subtitle}</p>}</div>
          <button type="button" className="ui-dialog__close" onClick={onClose} aria-label="Close"><X size={18} /></button>
        </header>
        <div className="ui-dialog__body">{children}</div>
        {footer && <footer className="ui-dialog__foot">{footer}</footer>}
      </div>
    </div>
  );
}

export function Drawer({ title, subtitle, badge, onClose, children, tabs }) {
  const ref = useDialog(onClose);
  return (
    <div className="ui-overlay ui-overlay--drawer" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <aside className="ui-drawer" role="dialog" aria-modal="true" aria-label={title} ref={ref} tabIndex={-1}>
        <header className="ui-drawer__head">
          <div className="ui-drawer__title">
            <h3>{title}</h3>
            {subtitle && <p>{subtitle}</p>}
          </div>
          {badge}
          <button type="button" className="ui-dialog__close" onClick={onClose} aria-label="Close"><X size={18} /></button>
        </header>
        {tabs}
        <div className="ui-drawer__body">{children}</div>
      </aside>
    </div>
  );
}

export function Notice({ error, notice, onDismiss }) {
  if (!error && !notice) return null;
  return (
    <div className={`ui-notice ${error ? "ui-notice--error" : "ui-notice--success"}`} role={error ? "alert" : "status"}>
      <span>{error || notice}</span>
      {onDismiss && <button type="button" onClick={onDismiss} aria-label="Dismiss"><X size={14} /></button>}
    </div>
  );
}

export function EmptyState({ icon: Icon, title, text, action }) {
  return (
    <div className="ui-empty">
      {Icon && <span className="ui-empty__icon"><Icon size={22} /></span>}
      <strong>{title}</strong>
      {text && <p>{text}</p>}
      {action}
    </div>
  );
}
