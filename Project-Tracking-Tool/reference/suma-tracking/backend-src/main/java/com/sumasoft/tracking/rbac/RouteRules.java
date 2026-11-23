package com.sumasoft.tracking.rbac;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Which permission each API needs (Step 39). The first matching line wins.
 * "*" = one part of the address, "**" = the rest. A line with no permission only needs a signed-in user.
 * Notifications, the user's own account and login are open to every signed-in employee.
 * Which records a person sees inside an allowed API is still limited by AccessService (own projects only).
 */
public final class RouteRules {

    private RouteRules() {
    }

    public record Rule(String method, Pattern pattern, List<String> anyOf, String action) {
    }

    private static final List<Rule> RULES = new ArrayList<>();

    private static void add(String method, String path, String action, String... anyOf) {
        String regex = "^" + path.replace(".", "\\.").replace("**", "\u0000").replace("*", "[^/]+")
                .replace("\u0000", ".*") + "/?$";
        RULES.add(new Rule(method, Pattern.compile(regex), List.of(anyOf), action));
    }

    static {
        // ---- open to every signed-in employee
        add("*", "/api/auth/**", "sign in");
        add("*", "/api/notifications/**", "see notifications");
        add("GET", "/api/notifications", "see notifications");
        add("GET", "/api/rbac/me", "see own permissions");
        add("GET", "/api/settings/public", "read basic settings");
        add("GET", "/api/roles", "see role names");
        add("GET", "/api/ticket-statuses", "see ticket statuses");
        add("GET", "/api/ticket-priorities", "see ticket priorities");
        add("GET", "/api/projects", "see own projects");
        add("GET", "/api/attendance/me", "see own attendance");
        add("*", "/api/attendance/me/**", "punch in, breaks and punch out");

        // ---- attendance of other people (Step 41)
        add("GET", "/api/attendance/team", "view team attendance", "ATTENDANCE_VIEW", "ATTENDANCE_MANAGE");
        add("GET", "/api/attendance/people/**", "view team attendance", "ATTENDANCE_VIEW", "ATTENDANCE_MANAGE");
        add("GET", "/api/attendance/schedules", "view working hours", "ATTENDANCE_VIEW", "ATTENDANCE_MANAGE");
        add("*", "/api/attendance/**", "manage working hours", "ATTENDANCE_MANAGE");

        // ---- dashboard, reports, workload
        add("GET", "/api/dashboard", "view the dashboard", "DASHBOARD_VIEW", "REPORT_VIEW");
        add("GET", "/api/workload", "view team workload", "WORKLOAD_VIEW", "USER_MANAGE");

        // ---- tickets
        add("POST", "/api/tickets/*/status", "change ticket status", "TICKET_UPDATE");
        add("POST", "/api/tickets/*/assign", "assign tickets", "TICKET_ASSIGN");
        add("POST", "/api/tickets/*/escalate", "escalate tickets", "TICKET_ESCALATE");
        add("GET", "/api/tickets", "view tickets", "TICKET_VIEW");
        add("GET", "/api/tickets/**", "view tickets", "TICKET_VIEW");
        add("POST", "/api/tickets", "create tickets", "TICKET_CREATE");
        add("*", "/api/tickets/**", "change tickets", "TICKET_UPDATE");

        // ---- imports
        add("GET", "/api/imports", "view imports", "IMPORT_VIEW", "IMPORT_RUN");
        add("GET", "/api/imports/**", "view imports", "IMPORT_VIEW", "IMPORT_RUN");
        add("*", "/api/imports/**", "upload sheets", "IMPORT_RUN");

        // ---- clients
        add("GET", "/api/admin/clients", "view clients", "CLIENT_MANAGE", "CLIENT_EDIT_ASSIGNED");
        add("PUT", "/api/admin/clients/*", "edit clients", "CLIENT_MANAGE", "CLIENT_EDIT_ASSIGNED");
        add("*", "/api/admin/clients", "add clients", "CLIENT_MANAGE");
        add("*", "/api/admin/clients/**", "manage clients", "CLIENT_MANAGE");
        add("GET", "/api/clients", "view clients", "CLIENT_VIEW", "CLIENT_MANAGE", "PROJECT_MANAGE");
        add("GET", "/api/clients/**", "view clients", "CLIENT_VIEW", "CLIENT_MANAGE", "PROJECT_MANAGE");
        add("PUT", "/api/clients/*", "edit clients", "CLIENT_MANAGE", "CLIENT_EDIT_ASSIGNED");
        add("*", "/api/clients", "manage clients", "CLIENT_MANAGE");
        add("*", "/api/clients/**", "manage clients", "CLIENT_MANAGE");

        // ---- projects and project teams
        add("GET", "/api/projects/*/team", "view project teams", "PROJECT_VIEW", "PROJECT_TEAM_MANAGE", "TICKET_ASSIGN", "WORKLOAD_VIEW");
        add("GET", "/api/projects/**", "view projects", "PROJECT_VIEW", "PROJECT_MANAGE", "TICKET_VIEW");
        add("*", "/api/projects/*/team", "change project teams", "PROJECT_TEAM_MANAGE");
        add("*", "/api/projects/*/team/**", "change project teams", "PROJECT_TEAM_MANAGE");
        add("*", "/api/projects", "create projects", "PROJECT_MANAGE");
        add("*", "/api/projects/**", "change projects", "PROJECT_MANAGE");

        // ---- employees and roles
        add("GET", "/api/users", "view employees", "USER_MANAGE", "CLIENT_MANAGE");
        add("*", "/api/users", "manage employees", "USER_MANAGE");
        add("*", "/api/users/**", "manage employees", "USER_MANAGE");
        add("*", "/api/roles", "manage roles", "ROLE_MANAGE");
        add("*", "/api/roles/**", "manage roles", "ROLE_MANAGE");
        add("*", "/api/rbac/**", "manage permissions", "ROLE_MANAGE");
        add("GET", "/api/audit", "view the audit log", "AUDIT_VIEW");
        add("GET", "/api/audit/**", "view the audit log", "AUDIT_VIEW");

        // ---- SLA, automation, knowledge base, settings
        add("GET", "/api/sla/**", "view SLA", "SLA_VIEW", "SLA_MANAGE");
        add("*", "/api/sla/**", "change SLA settings", "SLA_MANAGE");
        add("*", "/api/automation/**", "manage automation", "AUTOMATION_MANAGE");
        add("GET", "/api/kb/unanswered", "see unanswered questions", "KB_MANAGE");
        add("GET", "/api/kb/articles/*/versions", "see guide history", "KB_MANAGE");
        add("POST", "/api/kb/articles", "write guides", "KB_MANAGE");
        add("PUT", "/api/kb/articles/*", "write guides", "KB_MANAGE");
        add("*", "/api/kb/**", "read the Knowledge Base", "KB_VIEW", "KB_MANAGE");
        add("*", "/api/settings", "change settings", "SETTINGS_MANAGE");
        add("*", "/api/settings/**", "change settings", "SETTINGS_MANAGE");
    }

    /** The first rule for this method and address, or null when no rule is written for it. */
    public static Rule find(String method, String path) {
        for (Rule r : RULES) {
            if (("*".equals(r.method()) || r.method().equalsIgnoreCase(method)) && r.pattern().matcher(path).matches()) {
                return r;
            }
        }
        return null;
    }

    public static List<Rule> all() {
        return List.copyOf(RULES);
    }
}
