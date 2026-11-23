import { createContext, useContext } from 'react'

// Step 39: what the signed-in employee may do. The server is the real gatekeeper (it checks every
// API call); the website only uses this to show the pages and buttons that will work.

/** The same defaults as the database (V13), used only if the server cannot be asked. */
const DEFAULTS = {
  CLIENT_ADMIN: ['DASHBOARD_VIEW', 'REPORT_VIEW', 'TICKET_VIEW', 'TICKET_CREATE', 'TICKET_UPDATE', 'TICKET_ASSIGN', 'TICKET_ESCALATE', 'IMPORT_VIEW', 'IMPORT_RUN', 'CLIENT_VIEW', 'CLIENT_EDIT_ASSIGNED', 'PROJECT_VIEW', 'WORKLOAD_VIEW', 'SLA_VIEW', 'KB_VIEW'],
  PROJECT_MANAGER: ['DASHBOARD_VIEW', 'REPORT_VIEW', 'TICKET_VIEW', 'TICKET_CREATE', 'TICKET_UPDATE', 'TICKET_ASSIGN', 'TICKET_ESCALATE', 'IMPORT_VIEW', 'IMPORT_RUN', 'CLIENT_VIEW', 'PROJECT_VIEW', 'WORKLOAD_VIEW', 'SLA_VIEW', 'KB_VIEW'],
  TEAM_LEADER: ['DASHBOARD_VIEW', 'REPORT_VIEW', 'TICKET_VIEW', 'TICKET_CREATE', 'TICKET_UPDATE', 'TICKET_ASSIGN', 'TICKET_ESCALATE', 'CLIENT_VIEW', 'PROJECT_VIEW', 'WORKLOAD_VIEW', 'SLA_VIEW', 'KB_VIEW'],
  L1_SUPPORT: ['DASHBOARD_VIEW', 'TICKET_VIEW', 'TICKET_CREATE', 'TICKET_UPDATE', 'TICKET_ESCALATE', 'KB_VIEW'],
  L2_SUPPORT: ['DASHBOARD_VIEW', 'TICKET_VIEW', 'TICKET_CREATE', 'TICKET_UPDATE', 'KB_VIEW'],
  L3_SUPPORT: ['DASHBOARD_VIEW', 'TICKET_VIEW', 'TICKET_CREATE', 'TICKET_UPDATE', 'KB_VIEW'],
  MANAGEMENT_VIEWER: ['DASHBOARD_VIEW', 'REPORT_VIEW', 'TICKET_VIEW', 'CLIENT_VIEW', 'PROJECT_VIEW', 'WORKLOAD_VIEW', 'SLA_VIEW', 'KB_VIEW'],
  GROUP_PROJECT_MANAGER: ['DASHBOARD_VIEW', 'REPORT_VIEW', 'TICKET_VIEW', 'TICKET_CREATE', 'TICKET_UPDATE', 'TICKET_ASSIGN', 'TICKET_ESCALATE', 'CLIENT_VIEW', 'PROJECT_VIEW', 'WORKLOAD_VIEW', 'SLA_VIEW', 'KB_VIEW'],
  GROUP_SUPPORT_USER: ['DASHBOARD_VIEW', 'TICKET_VIEW', 'TICKET_CREATE', 'TICKET_UPDATE', 'TICKET_ESCALATE', 'KB_VIEW'],
}

export function fallbackPermissions(user) {
  if (!user) return { superAdmin: false, permissions: [] }
  if (user.roleCode === 'SUPER_ADMIN') return { superAdmin: true, permissions: [] }
  const list = DEFAULTS[user.roleCode] || DEFAULTS[user.userGroup === 'SUPPORT_USER' ? 'GROUP_SUPPORT_USER' : 'GROUP_PROJECT_MANAGER']
  return { superAdmin: false, permissions: list }
}

export const PermissionContext = createContext({ superAdmin: false, permissions: new Set(), user: null })

/** can('A', 'B') is true when the person has A or B. */
export function useCan() {
  const ctx = useContext(PermissionContext)
  return (...codes) => ctx.superAdmin || codes.some((c) => ctx.permissions.has(c))
}

export function useMe() {
  return useContext(PermissionContext).user
}