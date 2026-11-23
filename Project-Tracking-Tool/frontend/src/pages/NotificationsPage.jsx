import { useCallback, useEffect, useMemo, useState } from "react";
import { getTasks, getTickets } from "../auth/authService.js";

/*
  Notifications are derived from live backend data (tasks and tickets the signed-in
  user is allowed to see). Nothing is mocked. "Read" state is kept per user in
  localStorage, which is only a per-browser convenience.
*/

const DAY = 86400000;
const readKey = (user) => `pt.notifications.read.${user?.id ?? user?.username ?? "anon"}`;

function loadRead(user) {
  try {
    return new Set(JSON.parse(localStorage.getItem(readKey(user)) || "[]"));
  } catch {
    return new Set();
  }
}

function saveRead(user, set) {
  try {
    localStorage.setItem(readKey(user), JSON.stringify([...set].slice(-500)));
  } catch {
    /* storage unavailable: read state simply is not remembered */
  }
}

function startOfToday() {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

export function buildNotifications(user, tasks, tickets) {
  const items = [];
  const today = startOfToday();
  const now = Date.now();

  for (const task of tasks) {
    if (task.assigneeId !== user.id || task.status === "Completed" || !task.due) continue;
    const due = new Date(`${String(task.due).slice(0, 10)}T00:00:00`).getTime();
    if (Number.isNaN(due)) continue;
    const days = Math.round((due - today) / DAY);
    if (days < 0) {
      items.push({
        key: `task-overdue-${task.id}-${task.due}`,
        level: "danger",
        title: "Task overdue",
        text: `"${task.title}" (${task.project}) was due ${Math.abs(days)} day${Math.abs(days) === 1 ? "" : "s"} ago.`,
        target: "tasks",
        at: due,
      });
    } else if (days <= 3) {
      items.push({
        key: `task-due-${task.id}-${task.due}`,
        level: "warning",
        title: days === 0 ? "Task due today" : `Task due in ${days} day${days === 1 ? "" : "s"}`,
        text: `"${task.title}" (${task.project}) is not completed yet.`,
        target: "tasks",
        at: due,
      });
    }
  }

  for (const ticket of tickets) {
    if (ticket.status === "Closed") continue;
    const mine = ticket.assigneeId === user.id;
    const resolved = ticket.status === "Resolved";
    const label = `${ticket.ticketNumber}: ${ticket.subject}`;

    if (mine && !resolved) {
      items.push({
        key: `ticket-assigned-${ticket.id}`,
        level: "info",
        title: "Ticket assigned to you",
        text: `${label} (${ticket.priority}, ${ticket.status}).`,
        target: "tickets",
        at: new Date(ticket.updatedAt || ticket.createdAt).getTime(),
      });
    }
    if (!resolved && ticket.resolutionDueAt) {
      const dueAt = new Date(ticket.resolutionDueAt).getTime();
      if (dueAt < now) {
        items.push({
          key: `ticket-sla-breach-${ticket.id}`,
          level: "danger",
          title: "Resolution SLA breached",
          text: `${label} passed its resolution target.`,
          target: "tickets",
          at: dueAt,
        });
      } else if (dueAt - now < 4 * 3600000) {
        items.push({
          key: `ticket-sla-risk-${ticket.id}`,
          level: "warning",
          title: "Resolution SLA due soon",
          text: `${label} is due within 4 hours.`,
          target: "tickets",
          at: dueAt,
        });
      }
    }
    if (!ticket.assigneeId && !resolved) {
      items.push({
        key: `ticket-unassigned-${ticket.id}`,
        level: "warning",
        title: "Ticket not assigned",
        text: `${label} has no assignee.`,
        target: "tickets",
        at: new Date(ticket.createdAt).getTime(),
      });
    }
    if (resolved && (ticket.createdBy === user.name || mine)) {
      items.push({
        key: `ticket-resolved-${ticket.id}-${ticket.resolvedAt}`,
        level: "success",
        title: "Ticket resolved",
        text: `${label} is resolved and waiting to be closed.`,
        target: "tickets",
        at: new Date(ticket.resolvedAt || ticket.updatedAt).getTime(),
      });
    }
  }

  const rank = { danger: 0, warning: 1, info: 2, success: 3 };
  return items.sort((a, b) => rank[a.level] - rank[b.level] || b.at - a.at);
}

/** Loads notifications and exposes the unread count (used by the top-bar bell). */
export function useNotifications(user) {
  const [items, setItems] = useState([]);
  const [read, setRead] = useState(() => loadRead(user));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const canTasks = (user.permissions || []).includes("tasks.view");
  const canTickets = (user.permissions || []).includes("tickets.view");

  const refresh = useCallback(async () => {
    try {
      const [tasks, tickets] = await Promise.all([
        canTasks ? getTasks() : Promise.resolve([]),
        canTickets ? getTickets() : Promise.resolve([]),
      ]);
      setItems(buildNotifications(user, tasks, tickets));
      setError("");
    } catch (err) {
      setError(err.message || "Could not load notifications.");
    } finally {
      setLoading(false);
    }
  }, [user, canTasks, canTickets]);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, 60000);
    return () => clearInterval(timer);
  }, [refresh]);

  const markRead = useCallback(
    (keys) => {
      setRead((current) => {
        const next = new Set(current);
        keys.forEach((key) => next.add(key));
        saveRead(user, next);
        return next;
      });
    },
    [user]
  );

  const unread = useMemo(() => items.filter((item) => !read.has(item.key)).length, [items, read]);
  return { items, read, unread, loading, error, refresh, markRead };
}

export default function NotificationsPage({ notifications, onNavigate }) {
  const { items, read, loading, error, refresh, markRead } = notifications;

  return (
    <section className="panel panel--wide">
      <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
        <div>
          <h2 className="mb-1">Notifications</h2>
          <p className="topbar__sub mb-0">Alerts for your tasks and tickets, based on live data.</p>
        </div>
        <div className="d-flex gap-2">
          <button className="btn btn-outline-secondary" onClick={refresh}>Refresh</button>
          <button
            className="btn btn-outline-secondary"
            disabled={items.every((item) => read.has(item.key))}
            onClick={() => markRead(items.map((item) => item.key))}
          >
            Mark all as read
          </button>
        </div>
      </div>

      {error && <p className="form-error" role="alert">{error}</p>}
      {loading ? (
        <p className="empty">Loading notifications…</p>
      ) : items.length === 0 ? (
        <p className="empty">You are all caught up. Nothing needs your attention right now.</p>
      ) : (
        <ul className="notif-list">
          {items.map((item) => {
            const isRead = read.has(item.key);
            return (
              <li key={item.key} className={`notif notif--${item.level} ${isRead ? "is-read" : ""}`}>
                <div className="notif__body">
                  <strong>{item.title}</strong>
                  <span>{item.text}</span>
                </div>
                <div className="notif__actions">
                  <button
                    className="btn btn-outline-secondary btn-sm"
                    onClick={() => {
                      markRead([item.key]);
                      onNavigate(item.target);
                    }}
                  >
                    Open
                  </button>
                  {!isRead && (
                    <button className="btn btn-outline-secondary btn-sm" onClick={() => markRead([item.key])}>
                      Mark read
                    </button>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
