package com.sumasoft.tracking.settings;

import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool-wide settings (Step 38). Only known settings can be saved, each with its own rule.
 * The Super Admin changes them on the Settings page; every change is audited.
 * Other parts of the tool (SLA, Automation, new projects) read them from here.
 */
@Service
public class SettingsService {

    public record Def(String key, String group, String label, String help, String type, Integer min, Integer max) {
    }

    public static final List<Def> DEFS = List.of(
            new Def("company.name", "ORGANISATION", "Company name", "Shown in the daily summary.", "TEXT", 2, 100),
            new Def("project.defaultTimeZone", "ORGANISATION", "Default time zone for new projects",
                    "For example Asia/Kolkata or America/New_York.", "TIMEZONE", null, null),
            new Def("sla.default.CRITICAL.responseMinutes", "SLA", "Critical: first response", null, "MINUTES", 1, 43200),
            new Def("sla.default.CRITICAL.resolutionMinutes", "SLA", "Critical: resolution", null, "MINUTES", 1, 129600),
            new Def("sla.default.HIGH.responseMinutes", "SLA", "High: first response", null, "MINUTES", 1, 43200),
            new Def("sla.default.HIGH.resolutionMinutes", "SLA", "High: resolution", null, "MINUTES", 1, 129600),
            new Def("sla.default.MEDIUM.responseMinutes", "SLA", "Medium: first response", null, "MINUTES", 1, 43200),
            new Def("sla.default.MEDIUM.resolutionMinutes", "SLA", "Medium: resolution", null, "MINUTES", 1, 129600),
            new Def("sla.default.LOW.responseMinutes", "SLA", "Low: first response", null, "MINUTES", 1, 43200),
            new Def("sla.default.LOW.resolutionMinutes", "SLA", "Low: resolution", null, "MINUTES", 1, 129600),
            new Def("automation.alertLookbackDays", "AUTOMATION", "Alerts look at tickets of the last (days)",
                    "Older open tickets get no SLA alerts. 7 to 90 days.", "INT", 7, 90),
            new Def("automation.maxNotificationsPerRun", "AUTOMATION", "Most notifications in one run",
                    "Protects people from a flood of alerts. The rest go out in the next run. 50 to 2000.", "INT", 50, 2000),
            new Def("automation.dailySummaryHour", "AUTOMATION", "Daily summary hour (India time, 0-23)",
                    "The daily summary goes out at the first run after this hour.", "INT", 0, 23),
            new Def("attendance.defaultShiftMinutes", "ATTENDANCE", "Normal shift, including the break",
                    "For everyone without their own hours. 540 minutes = 9 hours.", "MINUTES", 60, 960),
            new Def("attendance.defaultBreakMinutes", "ATTENDANCE", "Normal break (compulsory)",
                    "Attendance is marked only when this break is taken. 60 minutes = 1 hour.", "MINUTES", 0, 240));

    private static final Map<String, String> FALLBACK = Map.ofEntries(
            Map.entry("company.name", "Suma Soft"), Map.entry("project.defaultTimeZone", "Asia/Kolkata"),
            Map.entry("sla.default.CRITICAL.responseMinutes", "60"), Map.entry("sla.default.CRITICAL.resolutionMinutes", "240"),
            Map.entry("sla.default.HIGH.responseMinutes", "120"), Map.entry("sla.default.HIGH.resolutionMinutes", "480"),
            Map.entry("sla.default.MEDIUM.responseMinutes", "240"), Map.entry("sla.default.MEDIUM.resolutionMinutes", "1440"),
            Map.entry("sla.default.LOW.responseMinutes", "480"), Map.entry("sla.default.LOW.resolutionMinutes", "2880"),
            Map.entry("automation.alertLookbackDays", "30"), Map.entry("automation.maxNotificationsPerRun", "300"),
            Map.entry("automation.dailySummaryHour", "9"),
            Map.entry("attendance.defaultShiftMinutes", "540"), Map.entry("attendance.defaultBreakMinutes", "60"));

    public record Setting(String key, String group, String label, String help, String type, Integer min, Integer max,
                          String value, Instant updatedAt, String updatedBy) {
    }

    public record PublicSettings(String companyName, String defaultTimeZone) {
    }

    public record SaveRequest(@NotNull(message = "Nothing to save") Map<String, String> values) {
    }

    private final AccessService access;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public SettingsService(AccessService access, AuditService audit, DataSource dataSource) {
        this.access = access;
        this.audit = audit;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ------------------------------------------------------------ page

    @Transactional(readOnly = true)
    public List<Setting> list() {
        access.requireSuperAdmin(access.current());
        Map<String, Object[]> saved = new HashMap<>();
        jdbc.query("SELECT s.setting_key, s.setting_value, s.updated_at, u.full_name FROM system_settings s "
                + "LEFT JOIN users u ON u.id = s.updated_by_user_id", rs -> {
            Timestamp ts = rs.getTimestamp("updated_at");
            saved.put(rs.getString("setting_key"), new Object[] {rs.getString("setting_value"),
                    ts == null ? null : ts.toInstant(), rs.getString("full_name")});
        });
        List<Setting> out = new ArrayList<>();
        for (Def d : DEFS) {
            Object[] s = saved.get(d.key());
            out.add(new Setting(d.key(), d.group(), d.label(), d.help(), d.type(), d.min(), d.max(),
                    s == null ? FALLBACK.get(d.key()) : (String) s[0],
                    s == null ? null : (Instant) s[1], s == null ? null : (String) s[2]));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public PublicSettings publicSettings() {
        access.current();
        return new PublicSettings(text("company.name"), text("project.defaultTimeZone"));
    }

    @Transactional
    public List<Setting> save(SaveRequest request) {
        AuthenticatedUser me = access.current();
        access.requireSuperAdmin(me);
        Map<String, String> clean = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : request.values().entrySet()) {
            Def d = DEFS.stream().filter(x -> x.key().equals(e.getKey())).findFirst()
                    .orElseThrow(() -> bad("Unknown setting: " + e.getKey()));
            clean.put(d.key(), check(d, e.getValue()));
        }
        // resolution cannot be shorter than first response, for each priority
        for (String p : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW")) {
            String rk = "sla.default." + p + ".responseMinutes";
            String sk = "sla.default." + p + ".resolutionMinutes";
            int r = Integer.parseInt(clean.getOrDefault(rk, text(rk)));
            int s = Integer.parseInt(clean.getOrDefault(sk, text(sk)));
            if (s < r) {
                throw bad(p.charAt(0) + p.substring(1).toLowerCase() + ": resolution cannot be shorter than first response");
            }
        }
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : clean.entrySet()) {
            String old = text(e.getKey());
            if (old.equals(e.getValue())) {
                continue;
            }
            before.put(e.getKey(), old);
            after.put(e.getKey(), e.getValue());
            jdbc.update("INSERT INTO system_settings (setting_key, setting_value, updated_at, updated_by_user_id) "
                    + "VALUES (?, ?, now(), ?) ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value, "
                    + "updated_at = now(), updated_by_user_id = EXCLUDED.updated_by_user_id", e.getKey(), e.getValue(), me.userId());
        }
        if (!after.isEmpty()) {
            audit.record("SETTINGS_UPDATED", "SETTINGS", null, null, null, before, after);
        }
        return list();
    }

    private static String check(Def d, String raw) {
        String v = raw == null ? "" : raw.trim();
        switch (d.type()) {
            case "TEXT" -> {
                if (v.length() < d.min() || v.length() > d.max()) {
                    throw bad(d.label() + " must be " + d.min() + " to " + d.max() + " characters");
                }
                if (!v.matches("^[A-Za-z0-9][A-Za-z0-9 &.,'()/-]*$")) {
                    throw bad(d.label() + " can contain letters, numbers, spaces and & . , ' ( ) / - only");
                }
                return v;
            }
            case "TIMEZONE" -> {
                try {
                    return ZoneId.of(v).getId();
                } catch (DateTimeException e) {
                    throw bad("\"" + v + "\" is not a valid time zone. Use a name like Asia/Kolkata");
                }
            }
            default -> {
                if (!v.matches("^[0-9]{1,6}$")) {
                    throw bad(d.label() + " must be a whole number");
                }
                int n = Integer.parseInt(v);
                if (n < d.min() || n > d.max()) {
                    throw bad(d.label() + " must be between " + d.min() + " and " + d.max());
                }
                return String.valueOf(n);
            }
        }
    }

    // ------------------------------------------------------------ for other services (no access check)

    public String text(String key) {
        List<String> v = jdbc.queryForList("SELECT setting_value FROM system_settings WHERE setting_key = ?", String.class, key);
        return v.isEmpty() ? FALLBACK.getOrDefault(key, "") : v.get(0);
    }

    public int number(String key) {
        try {
            return Integer.parseInt(text(key));
        } catch (NumberFormatException e) {
            return Integer.parseInt(FALLBACK.get(key));
        }
    }

    /** Starting SLA targets for projects without their own: priority -> {first response, resolution} minutes. */
    public Map<String, int[]> slaDefaults() {
        Map<String, String> all = new HashMap<>(FALLBACK);
        jdbc.query("SELECT setting_key, setting_value FROM system_settings WHERE setting_key LIKE 'sla.default.%'", rs -> {
            all.put(rs.getString("setting_key"), rs.getString("setting_value"));
        });
        Map<String, int[]> out = new LinkedHashMap<>();
        for (String p : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW")) {
            out.put(p, new int[] {parse(all.get("sla.default." + p + ".responseMinutes"), 60),
                    parse(all.get("sla.default." + p + ".resolutionMinutes"), 240)});
        }
        return out;
    }

    private static int parse(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException | NullPointerException e) {
            return fallback;
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
