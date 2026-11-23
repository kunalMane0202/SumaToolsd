// Talks to the backend. The login token is kept only for this browser tab (sessionStorage)
// and is sent as "Authorization: Bearer <token>". It is never written to the code or to GitHub.

const TOKEN_KEY = 'suma.token'
let onUnauthorized = () => {}

export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn
}

export function getToken() {
  try {
    return sessionStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token) {
  try {
    if (token) sessionStorage.setItem(TOKEN_KEY, token)
    else sessionStorage.removeItem(TOKEN_KEY)
  } catch {
    /* private mode: the token simply lives until the page is closed */
  }
}

export class ApiError extends Error {
  constructor(status, message, fieldErrors) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors || {}
  }
}

async function request(method, path, { body, form, query } = {}) {
  const url = new URL(path, window.location.origin)
  if (query) {
    Object.entries(query).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, v)
    })
  }
  const headers = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  let payload
  if (form) {
    payload = form
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  let res
  try {
    res = await fetch(url, { method, headers, body: payload })
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Is the backend running on port 8080?')
  }
  const text = await res.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = null
    }
  }
  if (!res.ok) {
    if (res.status === 401 && path !== '/api/auth/login') onUnauthorized()
    const message = (data && data.message) || `Request failed (${res.status})`
    throw new ApiError(res.status, message, data && data.fieldErrors)
  }
  return data
}

export const api = {
  // attendance (Step 41)
  attendanceMe: () => request('GET', '/api/attendance/me'),
  punchIn: (device) => request('POST', '/api/attendance/me/punch-in', { body: device }),
  breakStart: (device) => request('POST', '/api/attendance/me/break-start', { body: device }),
  breakEnd: (device) => request('POST', '/api/attendance/me/break-end', { body: device }),
  punchOut: (device) => request('POST', '/api/attendance/me/punch-out', { body: device }),
  attendanceSeen: (device) => request('POST', '/api/attendance/me/seen', { body: device }),
  attendanceHistory: (days) => request('GET', '/api/attendance/me/history', { query: { days } }),
  attendanceTeam: (date) => request('GET', '/api/attendance/team', { query: { date } }),
  attendanceEvents: (userId, days) => request('GET', `/api/attendance/people/${userId}/events`, { query: { days } }),
  attendanceDays: (userId, days) => request('GET', `/api/attendance/people/${userId}/days`, { query: { days } }),
  workSchedules: () => request('GET', '/api/attendance/schedules'),
  saveWorkSchedule: (userId, shiftMinutes, breakMinutes) =>
    request('PUT', `/api/attendance/schedules/${userId}`, { body: { shiftMinutes, breakMinutes } }),
  closeAttendanceDay: (dayId, punchOutTime, note) =>
    request('POST', `/api/attendance/days/${dayId}/close`, { body: { punchOutTime, note } }),
  // Login
  login: (email, password) => request('POST', '/api/auth/login', { body: { email, password } }),
  logout: () => request('POST', '/api/auth/logout'),
  me: () => request('GET', '/api/auth/me'),
  changePassword: (currentPassword, newPassword) =>
    request('POST', '/api/auth/change-password', { body: { currentPassword, newPassword } }),

  // Dashboard and reports
  dashboard: (query) => request('GET', '/api/dashboard', { query }),
  workload: () => request('GET', '/api/workload'),

  // Tickets
  statuses: () => request('GET', '/api/ticket-statuses'),
  tickets: (query) => request('GET', '/api/tickets', { query }),
  ticket: (id) => request('GET', `/api/tickets/${id}`),
  ticketHistory: (id) => request('GET', `/api/tickets/${id}/history`),
  changeStatus: (id, statusCode, note) => request('POST', `/api/tickets/${id}/status`, { body: { statusCode, note } }),
  assignTicket: (id, assigneeUserId, note) => request('POST', `/api/tickets/${id}/assign`, { body: { assigneeUserId, note } }),
  escalateTicket: (id, note) => request('POST', `/api/tickets/${id}/escalate`, { body: { note } }),

  // Excel import
  importTickets: ({ file, projectId, mode, sheet }) => {
    const form = new FormData()
    form.append('file', file)
    form.append('projectId', projectId)
    form.append('mode', mode)
    if (sheet) form.append('sheet', sheet)
    return request('POST', '/api/imports/tickets', { form })
  },
  imports: (projectId) => request('GET', '/api/imports', { query: { projectId } }),
  importDetails: (id) => request('GET', `/api/imports/${id}`),

  // Client Management
  clients: () => request('GET', '/api/clients'),
  adminClients: (includeRemoved) => request('GET', '/api/admin/clients', { query: { includeRemoved } }),
  addClient: (body) => request('POST', '/api/admin/clients', { body }),
  updateClient: (id, body) => request('PUT', `/api/admin/clients/${id}`, { body }),
  removeClient: (id) => request('DELETE', `/api/admin/clients/${id}`),
  restoreClient: (id) => request('POST', `/api/admin/clients/${id}/restore`),

  // Project Management
  projects: () => request('GET', '/api/projects'),
  createProject: (body) => request('POST', '/api/projects', { body }),
  updateProject: (id, body) => request('PUT', `/api/projects/${id}`, { body }),
  activateProject: (id) => request('POST', `/api/projects/${id}/activate`),
  deactivateProject: (id) => request('POST', `/api/projects/${id}/deactivate`),
  closeProject: (id) => request('POST', `/api/projects/${id}/close`),
  projectTeam: (id) => request('GET', `/api/projects/${id}/team`),
  assignToProject: (projectId, body) => request('PUT', `/api/projects/${projectId}/team`, { body }),
  removeFromProject: (projectId, userId) => request('POST', `/api/projects/${projectId}/team/${userId}/remove`),

  // Team Management
  users: () => request('GET', '/api/users'),
  createUser: (body) => request('POST', '/api/users', { body }),
  updateUser: (id, body) => request('PUT', `/api/users/${id}`, { body }),
  activateUser: (id) => request('POST', `/api/users/${id}/activate`),
  deactivateUser: (id) => request('POST', `/api/users/${id}/deactivate`),
  resetPassword: (id, newPassword) => request('POST', `/api/users/${id}/reset-password`, { body: { newPassword } }),
  userProjects: (id) => request('GET', `/api/users/${id}/projects`),

  // SLA Management (Step 37)
  slaOverview: (query) => request('GET', '/api/sla/overview', { query }),
  slaSettings: (projectId) => request('GET', `/api/sla/settings/${projectId}`),
  saveSlaSettings: (projectId, body) => request('PUT', `/api/sla/settings/${projectId}`, { body }),

  // Notifications (Step 37)
  notifications: (unreadOnly) => request('GET', '/api/notifications', { query: { unreadOnly } }),
  unreadCount: () => request('GET', '/api/notifications/unread-count'),
  markNotificationRead: (id) => request('POST', `/api/notifications/${id}/read`),
  markAllNotificationsRead: () => request('POST', '/api/notifications/read-all'),

  // Automation (Step 37)
  automationRules: () => request('GET', '/api/automation/rules'),
  setAutomationRule: (code, enabled) => request('PUT', `/api/automation/rules/${code}`, { body: { enabled } }),
  runAutomation: () => request('POST', '/api/automation/run'),
  automationRuns: () => request('GET', '/api/automation/runs'),
  automationActions: (runId) => request('GET', `/api/automation/runs/${runId}/actions`),

  // Knowledge Base (Step 38)
  kbArticles: (all) => request('GET', '/api/kb/articles', { query: { all } }),
  kbArticle: (slug) => request('GET', `/api/kb/articles/${encodeURIComponent(slug)}`),
  kbSearch: (q) => request('GET', '/api/kb/search', { query: { q } }),
  kbAsk: (question) => request('POST', '/api/kb/ask', { body: { question } }),
  kbFeedback: (slug, helpful) => request('POST', `/api/kb/articles/${encodeURIComponent(slug)}/feedback`, { body: { helpful } }),
  kbCreate: (body) => request('POST', '/api/kb/articles', { body }),
  kbUpdate: (id, body) => request('PUT', `/api/kb/articles/${id}`, { body }),
  kbVersions: (id) => request('GET', `/api/kb/articles/${id}/versions`),
  kbUnanswered: () => request('GET', '/api/kb/unanswered'),

  // Settings (Step 38)
  settings: () => request('GET', '/api/settings'),
  saveSettings: (values) => request('PUT', '/api/settings', { body: { values } }),
  publicSettings: () => request('GET', '/api/settings/public'),

  // Roles and permissions (Step 39)
  myPermissions: () => request('GET', '/api/rbac/me'),
  rbacMatrix: () => request('GET', '/api/rbac/matrix'),
  saveRolePermissions: (code, permissions) => request('PUT', `/api/rbac/roles/${code}/permissions`, { body: { permissions } }),
  resetRolePermissions: (code) => request('POST', `/api/rbac/roles/${code}/reset`),

  // Security
  roles: () => request('GET', '/api/roles'),
  createRole: (body) => request('POST', '/api/roles', { body }),
  updateRole: (code, body) => request('PUT', `/api/roles/${code}`, { body }),
  audit: (query) => request('GET', '/api/audit', { query }),
}
