package com.sumasoft.tracking.attendance;

import com.sumasoft.tracking.attendance.AttendanceDtos.BreakItem;
import com.sumasoft.tracking.attendance.AttendanceDtos.CloseForm;
import com.sumasoft.tracking.attendance.AttendanceDtos.Day;
import com.sumasoft.tracking.attendance.AttendanceDtos.DeviceInfo;
import com.sumasoft.tracking.attendance.AttendanceDtos.Event;
import com.sumasoft.tracking.attendance.AttendanceDtos.MyDay;
import com.sumasoft.tracking.attendance.AttendanceDtos.Schedule;
import com.sumasoft.tracking.attendance.AttendanceDtos.ScheduleForm;
import com.sumasoft.tracking.attendance.AttendanceDtos.ScheduleRow;
import com.sumasoft.tracking.attendance.AttendanceDtos.Team;
import com.sumasoft.tracking.attendance.AttendanceDtos.TeamRow;
import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import com.sumasoft.tracking.notification.NotificationService;
import com.sumasoft.tracking.rbac.RbacService;
import com.sumasoft.tracking.settings.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Attendance and working hours (Step 41).
 * - Punch in, breaks, punch out. Hours come from work_schedules (default 9 h with a 1 h break).
 * - Attendance is marked only when the full break was taken and the working hours were done.
 * - Every sign-in and punch saves the IP address and the device; a new laptop, a new network or one
 *   computer used by two people is flagged and the Super Admin (and people allowed to watch) are told.
 * - A monitor runs every minute: break over time, break due, no break, forgot to punch out.
 * Nothing is deleted. A manager closes a forgotten day with a note, and it is audited.
 */
@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    public static final String VIEW = "ATTENDANCE_VIEW";
    public static final String MANAGE = "ATTENDANCE_MANAGE";

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final long GRACE_SECONDS = 60;
    /** A day still open this long after the shift should have ended counts as "forgot to punch out". */
    private static final long FORGOT_AFTER_MINUTES = 120;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM");

    private final AccessService access;
    private final RbacService rbac;
    private final AuditService audit;
    private final NotificationService notifications;
    private final SettingsService settings;
    private final JdbcTemplate jdbc;
    private final AtomicBoolean monitoring = new AtomicBoolean(false);

    public AttendanceService(AccessService access, RbacService rbac, AuditService audit,
                             NotificationService notifications, SettingsService settings, DataSource dataSource) {
        this.access = access;
        this.rbac = rbac;
        this.audit = audit;
        this.notifications = notifications;
        this.settings = settings;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ============================================================== rows from the database

    record DayRow(long id, long userId, LocalDate workDate, Instant punchInAt, Instant punchOutAt, String status,
                  int shiftMinutes, int breakMinutes, Boolean marked, String flags, String closeNote) {
    }

    /** kind BREAK = a break; kind OFF = the time between a punch-out and a Super Admin's punch-in again. */
    record BreakRow(Instant startedAt, Instant endedAt, String kind, String breakType) {
        BreakRow(Instant startedAt, Instant endedAt) {
            this(startedAt, endedAt, "BREAK", null);
        }

        BreakRow(Instant startedAt, Instant endedAt, String kind) {
            this(startedAt, endedAt, kind, null);
        }

        boolean off() {
            return "OFF".equals(kind);
        }
    }

    /** The numbers of one day at a moment in time. */
    record Live(long elapsed, long worked, long onBreak, long continuous, Instant onBreakSince) {
    }

    private static final String DAY_COLS = "id, user_id, work_date, punch_in_at, punch_out_at, status, shift_minutes, "
            + "break_minutes, marked, flags, close_note";

    private static DayRow dayRow(ResultSet rs, int n) throws SQLException {
        Object marked = rs.getObject("marked");
        return new DayRow(rs.getLong("id"), rs.getLong("user_id"), rs.getDate("work_date").toLocalDate(),
                inst(rs.getTimestamp("punch_in_at")), inst(rs.getTimestamp("punch_out_at")), rs.getString("status"),
                rs.getInt("shift_minutes"), rs.getInt("break_minutes"), marked == null ? null : (Boolean) marked,
                rs.getString("flags"), rs.getString("close_note"));
    }

    private List<BreakRow> breaks(long dayId) {
        return jdbc.query("SELECT started_at, ended_at, kind, break_type FROM attendance_breaks WHERE day_id = ? ORDER BY started_at",
                (rs, n) -> new BreakRow(inst(rs.getTimestamp("started_at")), inst(rs.getTimestamp("ended_at")), rs.getString("kind"),
                        rs.getString("break_type")), dayId);
    }

    private DayRow openDay(long userId, boolean lock) {
        List<DayRow> rows = jdbc.query("SELECT " + DAY_COLS + " FROM attendance_days WHERE user_id = ? AND status <> 'CLOSED' "
                + "ORDER BY work_date DESC LIMIT 1" + (lock ? " FOR UPDATE" : ""), AttendanceService::dayRow, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private DayRow dayOn(long userId, LocalDate date) {
        List<DayRow> rows = jdbc.query("SELECT " + DAY_COLS + " FROM attendance_days WHERE user_id = ? AND work_date = ?",
                AttendanceService::dayRow, userId, Date.valueOf(date));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private DayRow dayById(long id) {
        List<DayRow> rows = jdbc.query("SELECT " + DAY_COLS + " FROM attendance_days WHERE id = ? FOR UPDATE",
                AttendanceService::dayRow, id);
        if (rows.isEmpty()) {
            throw AccessService.notFound("Attendance day not found");
        }
        return rows.get(0);
    }

    // ============================================================== schedules

    Schedule schedule(long userId) {
        List<Schedule> rows = jdbc.query("SELECT shift_minutes, break_minutes FROM work_schedules WHERE user_id = ?",
                (rs, n) -> new Schedule(rs.getInt("shift_minutes"), rs.getInt("break_minutes"), true), userId);
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        return new Schedule(setting("attendance.defaultShiftMinutes", 540), setting("attendance.defaultBreakMinutes", 60), false);
    }

    private int setting(String key, int fallback) {
        try {
            String v = settings.text(key);
            return v == null || v.isBlank() ? fallback : Integer.parseInt(v.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    // ============================================================== the numbers

    static Live live(DayRow d, List<BreakRow> breaks, Instant now) {
        Instant end = d.punchOutAt() != null ? d.punchOutAt() : now;
        if (end.isBefore(d.punchInAt())) {
            end = d.punchInAt();
        }
        long elapsed = Duration.between(d.punchInAt(), end).getSeconds();
        long onBreak = 0;
        long offDuty = 0;
        Instant lastBack = d.punchInAt();
        Instant since = null;
        for (BreakRow b : breaks) {
            Instant s = b.startedAt().isAfter(end) ? end : b.startedAt();
            Instant e = b.endedAt() == null || b.endedAt().isAfter(end) ? end : b.endedAt();
            if (e.isAfter(s)) {
                if (b.off()) {
                    offDuty += Duration.between(s, e).getSeconds();
                } else {
                    onBreak += Duration.between(s, e).getSeconds();
                }
            }
            if (b.endedAt() == null && d.punchOutAt() == null && !b.off()) {
                since = b.startedAt();
            } else if (b.endedAt() != null && b.endedAt().isAfter(lastBack)) {
                lastBack = b.endedAt().isAfter(end) ? end : b.endedAt();
            }
        }
        elapsed = Math.max(0, elapsed - offDuty); // time away after a punch-out is not work and not break
        long worked = Math.max(0, elapsed - onBreak);
        long continuous = since != null ? 0 : Math.max(0, Duration.between(lastBack, end).getSeconds());
        return new Live(elapsed, worked, onBreak, continuous, since);
    }

    /** Flags worked out from the numbers, plus the device flags saved on the day. */
    static List<String> flags(DayRow d, Live l, LocalDate today) {
        Set<String> f = new LinkedHashSet<>();
        long allowance = d.breakMinutes() * 60L;
        long required = (long) (d.shiftMinutes() - d.breakMinutes()) * 60L;
        boolean open = !"CLOSED".equals(d.status());
        if (l.onBreak() > allowance + GRACE_SECONDS) {
            f.add("BREAK_OVERTIME");
        }
        if (open) {
            if (l.onBreakSince() == null && l.onBreak() + GRACE_SECONDS < allowance && l.continuous() >= required / 2) {
                f.add("BREAK_DUE");
            }
            if (l.worked() >= required && l.onBreak() + GRACE_SECONDS < allowance) {
                f.add("NO_BREAK");
            }
            if (d.workDate().isBefore(today) || l.elapsed() > (d.shiftMinutes() + FORGOT_AFTER_MINUTES) * 60L) {
                f.add("FORGOT_PUNCH_OUT");
            }
        } else {
            if (l.onBreak() + GRACE_SECONDS < allowance) {
                f.add(l.onBreak() < 60 ? "NO_BREAK" : "SHORT_BREAK");
            }
            if (l.worked() + GRACE_SECONDS < required) {
                f.add("SHORT_HOURS");
            }
        }
        for (String s : split(d.flags())) {
            f.add(s);
        }
        return new ArrayList<>(f);
    }

    static String markReason(DayRow d, Live l) {
        long allowance = d.breakMinutes() * 60L;
        long required = (long) (d.shiftMinutes() - d.breakMinutes()) * 60L;
        List<String> why = new ArrayList<>();
        if (l.onBreak() + GRACE_SECONDS < allowance) {
            why.add("the compulsory break of " + hm(allowance) + " was not taken (taken " + hm(l.onBreak()) + ")");
        }
        if (l.worked() + GRACE_SECONDS < required) {
            why.add("working time was " + hm(l.worked()) + " of " + hm(required));
        }
        return why.isEmpty() ? "Marked present" : "Not marked: " + String.join(" and ", why);
    }

    private Day toDay(DayRow d, Instant now) {
        List<BreakRow> b = breaks(d.id());
        Live l = live(d, b, now);
        List<BreakItem> items = new ArrayList<>();
        Instant end = d.punchOutAt() != null ? d.punchOutAt() : now;
        for (BreakRow r : b) {
            Instant e = r.endedAt() == null ? end : r.endedAt();
            items.add(new BreakItem(r.startedAt(), r.endedAt(), Math.max(0, Duration.between(r.startedAt(), e).getSeconds()), r.kind(), r.breakType()));
        }
        boolean closed = "CLOSED".equals(d.status());
        return new Day(d.id(), d.workDate(), d.status(), d.punchInAt(), d.punchOutAt(), d.shiftMinutes(), d.breakMinutes(),
                l.worked(), l.onBreak(), l.continuous(), l.onBreakSince(), closed ? d.marked() : null,
                closed ? markReason(d, l) : null, flags(d, l, LocalDate.now(ZONE)), items, d.closeNote());
    }

    // ============================================================== the employee's own day

    @Transactional(readOnly = true)
    public MyDay me(HttpServletRequest request) {
        AuthenticatedUser me = access.current();
        Instant now = Instant.now();
        DayRow open = openDay(me.userId(), false);
        DayRow today = open != null ? open : dayOn(me.userId(), LocalDate.now(ZONE));
        boolean done = open == null && today != null;
        return new MyDay(now, schedule(me.userId()), today == null ? null : toDay(today, now),
                done, done && access.isSuperAdmin(me), clientIp(request));
    }

    @Transactional(readOnly = true)
    public List<Day> myHistory(int days) {
        return history(access.current().userId(), days);
    }

    private List<Day> history(long userId, int days) {
        Instant now = Instant.now();
        LocalDate from = LocalDate.now(ZONE).minusDays(Math.max(1, Math.min(days, 92)));
        List<DayRow> rows = jdbc.query("SELECT " + DAY_COLS + " FROM attendance_days WHERE user_id = ? AND work_date >= ? "
                + "ORDER BY work_date DESC", AttendanceService::dayRow, userId, Date.valueOf(from));
        List<Day> out = new ArrayList<>();
        for (DayRow r : rows) {
            out.add(toDay(r, now));
        }
        return out;
    }

    /** Called once after sign-in: saves where the person signed in from. */
    @Transactional
    public List<String> seen(DeviceInfo device, HttpServletRequest request) {
        AuthenticatedUser me = access.current();
        return record(me.userId(), null, "SIGN_IN", device, request, null);
    }

    @Transactional
    public MyDay punchIn(DeviceInfo device, HttpServletRequest request) {
        AuthenticatedUser me = access.current();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZONE);
        DayRow open = openDay(me.userId(), true);
        if (open != null && open.workDate().equals(today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already punched in");
        }
        if (open != null) {
            autoClose(open, now);
        }
        DayRow closedToday = dayOn(me.userId(), today);
        if (closedToday != null) {
            if (!access.isSuperAdmin(me)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already punched out today. Your day is saved.");
            }
            reopen(closedToday, now);
            record(me.userId(), closedToday.id(), "PUNCH_IN", device, request, null);
            return me(request);
        }
        Schedule s = schedule(me.userId());
        int made = jdbc.update("INSERT INTO attendance_days (user_id, work_date, punch_in_at, status, shift_minutes, break_minutes) "
                        + "VALUES (?, ?, ?, 'WORKING', ?, ?) ON CONFLICT (user_id, work_date) DO NOTHING",
                me.userId(), Date.valueOf(today), Timestamp.from(now), s.shiftMinutes(), s.breakMinutes());
        if (made == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already punched in");
        }
        DayRow day = dayOn(me.userId(), today);
        // flags of today's sign-ins (a new laptop seen at sign-in) belong to the day too
        List<String> signInFlags = jdbc.queryForList("SELECT flags FROM attendance_events WHERE user_id = ? AND kind = 'SIGN_IN' "
                + "AND happened_at >= ? AND flags <> ''", String.class, me.userId(), Timestamp.from(today.atStartOfDay(ZONE).toInstant()));
        addDayFlags(day.id(), signInFlags);
        record(me.userId(), day.id(), "PUNCH_IN", device, request, null);
        return me(request);
    }

    @Transactional
    public MyDay breakStart(DeviceInfo device, HttpServletRequest request) {
        AuthenticatedUser me = access.current();
        DayRow d = requireOpen(me.userId());
        if ("ON_BREAK".equals(d.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already on a break");
        }
        String type = device == null || device.breakType() == null || device.breakType().isBlank() ? "TEA" : device.breakType();
        jdbc.update("INSERT INTO attendance_breaks (day_id, started_at, break_type) VALUES (?, now(), ?)", d.id(), type);
        jdbc.update("UPDATE attendance_days SET status = 'ON_BREAK' WHERE id = ?", d.id());
        record(me.userId(), d.id(), "BREAK_START", device, request, null);
        return me(request);
    }

    @Transactional
    public MyDay breakEnd(DeviceInfo device, HttpServletRequest request) {
        AuthenticatedUser me = access.current();
        DayRow d = requireOpen(me.userId());
        if (!"ON_BREAK".equals(d.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are not on a break");
        }
        endBreaks(d.id(), Instant.now());
        jdbc.update("UPDATE attendance_days SET status = 'WORKING' WHERE id = ?", d.id());
        record(me.userId(), d.id(), "BREAK_END", device, request, null);
        return me(request);
    }

    @Transactional
    public MyDay punchOut(DeviceInfo device, HttpServletRequest request) {
        AuthenticatedUser me = access.current();
        DayRow d = requireOpen(me.userId());
        Instant now = Instant.now();
        endBreaks(d.id(), now);
        List<String> f = record(me.userId(), d.id(), "PUNCH_OUT", device, request, null);
        finish(dayById(d.id()), now, f, null, null);
        return me(request);
    }

    /**
     * Super Admin only: punch in again on a day already punched out. The time away is saved as "off duty"
     * (not work, not break) and the day is worked out again at the next punch-out. Nothing is deleted.
     */
    private void reopen(DayRow d, Instant now) {
        Instant out = d.punchOutAt() == null ? now : d.punchOutAt();
        jdbc.update("INSERT INTO attendance_breaks (day_id, started_at, ended_at, kind) VALUES (?, ?, ?, 'OFF')",
                d.id(), Timestamp.from(out), Timestamp.from(now.isBefore(out) ? out : now));
        jdbc.update("UPDATE attendance_days SET status = 'WORKING', punch_out_at = NULL, worked_minutes = NULL, "
                + "break_taken_minutes = NULL, marked = NULL, flags = ? WHERE id = ?", clip(join(kept(d.flags())), 400), d.id());
        audit.record("ATTENDANCE_PUNCHED_IN_AGAIN", "ATTENDANCE_DAY", d.id(), null, null,
                Map.of("punchOutAt", out.toString()), Map.of("punchInAgainAt", now.toString()));
    }

    private DayRow requireOpen(long userId) {
        DayRow d = openDay(userId, true);
        if (d == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Punch in first");
        }
        return d;
    }

    private void endBreaks(long dayId, Instant at) {
        jdbc.update("UPDATE attendance_breaks SET ended_at = GREATEST(started_at, ?) WHERE day_id = ? AND ended_at IS NULL AND kind = 'BREAK'",
                Timestamp.from(at), dayId);
    }

    /** Saves the final numbers of a day. */
    private void finish(DayRow d, Instant end, List<String> extraFlags, Long closedBy, String note) {
        DayRow closed = new DayRow(d.id(), d.userId(), d.workDate(), d.punchInAt(), end, "CLOSED", d.shiftMinutes(),
                d.breakMinutes(), null, join(merge(kept(d.flags()), extraFlags)), note);
        Live l = live(closed, breaks(d.id()), end);
        long allowance = d.breakMinutes() * 60L;
        long required = (long) (d.shiftMinutes() - d.breakMinutes()) * 60L;
        boolean marked = l.onBreak() + GRACE_SECONDS >= allowance && l.worked() + GRACE_SECONDS >= required;
        List<String> all = flags(closed, l, LocalDate.now(ZONE));
        jdbc.update("UPDATE attendance_days SET punch_out_at = ?, status = 'CLOSED', worked_minutes = ?, break_taken_minutes = ?, "
                        + "marked = ?, flags = ?, closed_by_user_id = COALESCE(?, closed_by_user_id), close_note = COALESCE(?, close_note) WHERE id = ?",
                Timestamp.from(end), (int) (l.worked() / 60), (int) (l.onBreak() / 60), marked, clip(join(all), 400),
                closedBy, note, d.id());
    }

    /** A day left open from an earlier date is closed when the person punches in again: at the end of the shift, not marked. */
    private void autoClose(DayRow d, Instant now) {
        Instant end = d.punchInAt().plus(Duration.ofMinutes(d.shiftMinutes()));
        if (end.isAfter(now)) {
            end = now;
        }
        endBreaks(d.id(), end);
        finish(d, end, List.of("FORGOT_PUNCH_OUT"), null, "Closed by the system: no punch-out. A manager can correct the time.");
        jdbc.update("UPDATE attendance_days SET marked = FALSE WHERE id = ?", d.id());
    }

    // ============================================================== IP address and device

    /** The caller's IP. Behind the local website proxy the real address is in X-Forwarded-For. */
    static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && isLoopback(ip)) {
            ip = forwarded.split(",")[0].trim();
        }
        if (ip != null && (ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::ffff:127.0.0.1"))) {
            ip = ip.startsWith("::ffff") ? "127.0.0.1" : "::1";
        }
        if (ip != null && ip.startsWith("::ffff:")) {
            ip = ip.substring(7);
        }
        return clip(ip, 64);
    }

    private static boolean isLoopback(String ip) {
        return ip == null || ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1") || ip.startsWith("::ffff:127.");
    }

    static String cleanDeviceId(String id) {
        if (id == null) {
            return null;
        }
        String s = id.trim();
        return s.matches("^[A-Za-z0-9-]{8,64}$") ? s : null;
    }

    /** Saves one event and returns its flags (new device, new IP, shared device). */
    private List<String> record(long userId, Long dayId, String kind, DeviceInfo device, HttpServletRequest request,
                                String labelOverride) {
        String ip = clientIp(request);
        String deviceId = cleanDeviceId(device == null ? null : device.deviceId());
        String label = labelOverride != null ? labelOverride
                : clip(device == null || device.deviceLabel() == null ? null : device.deviceLabel().replaceAll("[\\p{Cntrl}]", " ").trim(), 120);
        String agent = request == null ? null : clip(request.getHeader("User-Agent"), 400);
        List<String> f = new ArrayList<>();
        List<String> sharedWith = List.of();
        if (labelOverride == null) {
            long knownDevices = count("SELECT COUNT(*) FROM attendance_devices WHERE user_id = ?", userId);
            long pastEvents = count("SELECT COUNT(*) FROM attendance_events WHERE user_id = ? AND kind <> 'MANAGER_CLOSE'", userId);
            if (deviceId == null) {
                f.add("UNKNOWN_DEVICE");
            } else {
                boolean known = count("SELECT COUNT(*) FROM attendance_devices WHERE user_id = ? AND device_id = ?", userId, deviceId) > 0;
                if (!known && knownDevices > 0) {
                    f.add("NEW_DEVICE");
                }
                sharedWith = jdbc.queryForList("SELECT DISTINCT u.full_name FROM attendance_devices d JOIN users u ON u.id = d.user_id "
                        + "WHERE d.device_id = ? AND d.user_id <> ? AND d.last_seen_at > now() - interval '30 days'", String.class, deviceId, userId);
                if (!sharedWith.isEmpty()) {
                    f.add("SHARED_DEVICE");
                }
                int updated = jdbc.update("UPDATE attendance_devices SET last_seen_at = now(), last_ip = ?, "
                        + "device_label = COALESCE(?, device_label) WHERE user_id = ? AND device_id = ?", ip, label, userId, deviceId);
                if (updated == 0) {
                    jdbc.update("INSERT INTO attendance_devices (user_id, device_id, device_label, last_ip) VALUES (?, ?, ?, ?) "
                            + "ON CONFLICT (user_id, device_id) DO NOTHING", userId, deviceId, label, ip);
                }
            }
            if (ip != null && pastEvents > 0
                    && count("SELECT COUNT(*) FROM attendance_events WHERE user_id = ? AND ip_address = ?", userId, ip) == 0) {
                f.add("NEW_IP");
            }
        }
        jdbc.update("INSERT INTO attendance_events (user_id, day_id, kind, ip_address, device_id, device_label, user_agent, flags) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", userId, dayId, kind, ip, deviceId, label, agent, join(f));
        if (dayId != null && !f.isEmpty()) {
            addDayFlags(dayId, f);
        }
        boolean worthTelling = f.contains("NEW_DEVICE") || f.contains("NEW_IP")
                || (f.contains("SHARED_DEVICE") && !"SIGN_IN".equals(kind));
        if (worthTelling && ("SIGN_IN".equals(kind) || "PUNCH_IN".equals(kind) || "PUNCH_OUT".equals(kind))) {
            tellAboutDevice(userId, kind, f, ip, label, sharedWith);
        }
        return f;
    }

    private void tellAboutDevice(long userId, String kind, List<String> f, String ip, String label, List<String> sharedWith) {
        try {
            String name = nameOf(userId);
            List<String> what = new ArrayList<>();
            if (f.contains("NEW_DEVICE")) {
                what.add("a new device (" + (label == null ? "unknown browser" : label) + ")");
            }
            if (f.contains("NEW_IP")) {
                what.add("a new IP address " + (ip == null ? "" : ip));
            }
            if (f.contains("SHARED_DEVICE")) {
                what.add("a computer also used by " + String.join(", ", sharedWith));
            }
            String action = switch (kind) {
                case "PUNCH_IN" -> "punched in";
                case "PUNCH_OUT" -> "punched out";
                default -> "signed in";
            };
            Set<Long> to = watchers(userId);
            notifications.send(to, "ATTENDANCE", "\u26a0 Device check: " + name,
                    name + " " + action + " from " + String.join(" and ", what) + ". Open Attendance to see the device log.", null, null);
        } catch (RuntimeException e) {
            log.warn("Could not send the device alert", e);
        }
    }

    private void addDayFlags(long dayId, List<String> more) {
        List<String> now = jdbc.queryForList("SELECT flags FROM attendance_days WHERE id = ?", String.class, dayId);
        if (now.isEmpty()) {
            return;
        }
        List<String> add = new ArrayList<>();
        for (String m : more) {
            add.addAll(split(m));
        }
        add.removeIf(s -> !s.equals("NEW_DEVICE") && !s.equals("NEW_IP") && !s.equals("SHARED_DEVICE") && !s.equals("UNKNOWN_DEVICE"));
        jdbc.update("UPDATE attendance_days SET flags = ? WHERE id = ?", clip(join(merge(split(now.get(0)), add)), 400), dayId);
    }

    // ============================================================== who may see whom

    /** People this viewer may see: everyone for the Super Admin, otherwise people who share an active project. */
    private Set<Long> visibleUsers(AuthenticatedUser me) {
        if (access.isSuperAdmin(me) || access.seesEverything(me)) {
            return new LinkedHashSet<>(jdbc.queryForList("SELECT id FROM users WHERE status = 'ACTIVE' ORDER BY full_name", Long.class));
        }
        Set<Long> ids = new LinkedHashSet<>(jdbc.queryForList("SELECT DISTINCT other.user_id FROM project_assignments mine "
                + "JOIN project_assignments other ON other.project_id = mine.project_id AND other.active "
                + "JOIN users u ON u.id = other.user_id AND u.status = 'ACTIVE' "
                + "WHERE mine.user_id = ? AND mine.active", Long.class, me.userId()));
        ids.add(me.userId());
        return ids;
    }

    private void requireCanSee(AuthenticatedUser me, long userId) {
        if (me.userId() == userId) {
            return;
        }
        rbac.require(me, VIEW, MANAGE);
        if (!visibleUsers(me).contains(userId)) {
            throw AccessService.notFound("Employee not found");
        }
    }

    /** Who is told about one employee: Super Admins, and people with the attendance permission who share a project. */
    Set<Long> watchers(long userId) {
        Set<Long> to = new LinkedHashSet<>(notifications.superAdmins());
        List<Long> colleagues = jdbc.queryForList("SELECT DISTINCT other.user_id FROM project_assignments mine "
                + "JOIN project_assignments other ON other.project_id = mine.project_id AND other.active "
                + "JOIN users u ON u.id = other.user_id AND u.status = 'ACTIVE' "
                + "WHERE mine.user_id = ? AND mine.active AND other.user_id <> ?", Long.class, userId, userId);
        for (Long c : colleagues) {
            Set<String> p = rbac.permissionsOf(c);
            if (p.contains(VIEW) || p.contains(MANAGE)) {
                to.add(c);
            }
        }
        to.remove(userId);
        return to;
    }

    // ============================================================== managers: the team board

    @Transactional(readOnly = true)
    public Team team(LocalDate date) {
        AuthenticatedUser me = access.current();
        rbac.require(me, VIEW, MANAGE);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate day = date == null ? today : date;
        Instant now = Instant.now();
        Set<Long> ids = visibleUsers(me);
        List<TeamRow> rows = new ArrayList<>();
        for (Long id : ids) {
            Map<String, Object> u = person(id);
            if (u == null) {
                continue;
            }
            DayRow d = dayOn(id, day);
            if (d == null && day.equals(today)) {
                DayRow open = openDay(id, false);
                if (open != null) {
                    d = open;
                }
            }
            Day shown = d == null ? null : toDay(d, now);
            String state;
            if (shown == null) {
                state = day.equals(today) ? "NOT_IN" : "ABSENT";
            } else if (shown.flags().contains("FORGOT_PUNCH_OUT") && !"CLOSED".equals(shown.status())) {
                state = "FORGOT";
            } else {
                state = switch (shown.status()) {
                    case "WORKING" -> "WORKING";
                    case "ON_BREAK" -> "ON_BREAK";
                    default -> Boolean.TRUE.equals(shown.marked()) ? "PRESENT" : "NOT_MARKED";
                };
            }
            List<Map<String, Object>> last = jdbc.queryForList("SELECT ip_address, device_label FROM attendance_events "
                    + "WHERE user_id = ? AND kind <> 'MANAGER_CLOSE' ORDER BY happened_at DESC LIMIT 1", id);
            rows.add(new TeamRow(id, (String) u.get("full_name"), (String) u.get("employee_code"), (String) u.get("role_name"),
                    schedule(id), shown, state, shown == null ? List.of() : shown.flags(),
                    last.isEmpty() ? null : (String) last.get(0).get("ip_address"),
                    last.isEmpty() ? null : (String) last.get(0).get("device_label"),
                    count("SELECT COUNT(*) FROM attendance_devices WHERE user_id = ?", id)));
        }
        return new Team(day, now, rbac.has(me, MANAGE), rows);
    }

    @Transactional(readOnly = true)
    public List<Event> events(long userId, int days) {
        AuthenticatedUser me = access.current();
        requireCanSee(me, userId);
        Instant from = Instant.now().minus(Duration.ofDays(Math.max(1, Math.min(days, 92))));
        return jdbc.query("SELECT id, kind, happened_at, ip_address, device_id, device_label, user_agent, flags FROM attendance_events "
                        + "WHERE user_id = ? AND happened_at >= ? ORDER BY happened_at DESC LIMIT 300",
                (rs, n) -> new Event(rs.getLong("id"), rs.getString("kind"), inst(rs.getTimestamp("happened_at")),
                        rs.getString("ip_address"), rs.getString("device_id"), rs.getString("device_label"),
                        rs.getString("user_agent"), split(rs.getString("flags"))), userId, Timestamp.from(from));
    }

    @Transactional(readOnly = true)
    public List<Day> personHistory(long userId, int days) {
        AuthenticatedUser me = access.current();
        requireCanSee(me, userId);
        return history(userId, days);
    }

    // ============================================================== managers: hours and corrections

    @Transactional(readOnly = true)
    public List<ScheduleRow> schedules() {
        AuthenticatedUser me = access.current();
        rbac.require(me, VIEW, MANAGE);
        boolean manage = rbac.has(me, MANAGE);
        boolean superAdmin = access.isSuperAdmin(me);
        List<ScheduleRow> out = new ArrayList<>();
        for (Long id : visibleUsers(me)) {
            Map<String, Object> u = person(id);
            if (u == null) {
                continue;
            }
            List<Map<String, Object>> meta = jdbc.queryForList("SELECT s.updated_at, b.full_name AS by_name FROM work_schedules s "
                    + "LEFT JOIN users b ON b.id = s.updated_by_user_id WHERE s.user_id = ?", id);
            boolean editable = manage && (superAdmin || (id != me.userId() && !"SUPER_ADMIN".equals(u.get("role_code"))));
            out.add(new ScheduleRow(id, (String) u.get("full_name"), (String) u.get("employee_code"), (String) u.get("role_name"),
                    schedule(id), meta.isEmpty() ? null : inst((Timestamp) meta.get(0).get("updated_at")),
                    meta.isEmpty() ? null : (String) meta.get(0).get("by_name"), editable));
        }
        return out;
    }

    @Transactional
    public ScheduleRow saveSchedule(long userId, ScheduleForm form) {
        AuthenticatedUser me = access.current();
        rbac.require(me, MANAGE);
        requireCanSee(me, userId);
        Map<String, Object> u = person(userId);
        if (u == null) {
            throw AccessService.notFound("Employee not found");
        }
        boolean superAdmin = access.isSuperAdmin(me);
        if (!superAdmin && userId == me.userId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the Super Admin can change your own working hours");
        }
        if (!superAdmin && "SUPER_ADMIN".equals(u.get("role_code"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a Super Admin can change a Super Admin's hours");
        }
        if (form.breakMinutes() >= form.shiftMinutes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The break must be shorter than the shift");
        }
        Schedule before = schedule(userId);
        jdbc.update("INSERT INTO work_schedules (user_id, shift_minutes, break_minutes, updated_at, updated_by_user_id) "
                        + "VALUES (?, ?, ?, now(), ?) ON CONFLICT (user_id) DO UPDATE SET shift_minutes = EXCLUDED.shift_minutes, "
                        + "break_minutes = EXCLUDED.break_minutes, updated_at = now(), updated_by_user_id = EXCLUDED.updated_by_user_id",
                userId, form.shiftMinutes(), form.breakMinutes(), me.userId());
        // today's open day follows the new hours at once
        jdbc.update("UPDATE attendance_days SET shift_minutes = ?, break_minutes = ? WHERE user_id = ? AND status <> 'CLOSED'",
                form.shiftMinutes(), form.breakMinutes(), userId);
        audit.record("ATTENDANCE_HOURS_CHANGED", "USER", userId, null, null,
                Map.of("shiftMinutes", before.shiftMinutes(), "breakMinutes", before.breakMinutes()),
                Map.of("shiftMinutes", form.shiftMinutes(), "breakMinutes", form.breakMinutes()));
        notifications.send(List.of(userId), "ATTENDANCE", "Your working hours changed",
                "Your shift is now " + hm(form.shiftMinutes() * 60L) + " including a break of " + hm(form.breakMinutes() * 60L) + ".", null, null);
        for (ScheduleRow r : schedules()) {
            if (r.userId() == userId) {
                return r;
            }
        }
        throw AccessService.notFound("Employee not found");
    }

    /** Closes a day someone forgot to punch out of (or corrects a day the system closed). */
    @Transactional
    public Day closeDay(long dayId, CloseForm form, HttpServletRequest request) {
        AuthenticatedUser me = access.current();
        rbac.require(me, MANAGE);
        DayRow d = dayById(dayId);
        requireCanSee(me, d.userId());
        if (d.userId() == me.userId() && !access.isSuperAdmin(me)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ask the Super Admin to close your own day");
        }
        boolean open = !"CLOSED".equals(d.status());
        if (!open && !split(d.flags()).contains("FORGOT_PUNCH_OUT")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This day was punched out normally and cannot be changed");
        }
        LocalTime t = LocalTime.parse(form.punchOutTime(), HM);
        Instant end = d.workDate().atTime(t).atZone(ZONE).toInstant();
        if (!end.isAfter(d.punchInAt())) {
            end = d.workDate().plusDays(1).atTime(t).atZone(ZONE).toInstant(); // a night shift ending after midnight
        }
        Instant now = Instant.now();
        if (end.isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The punch-out time cannot be in the future");
        }
        if (Duration.between(d.punchInAt(), end).toHours() >= 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That makes a day of more than 20 hours. Check the time.");
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", d.status());
        before.put("punchOutAt", d.punchOutAt() == null ? null : d.punchOutAt().toString());
        // breaks: open ones end at the new time; none may run past it
        jdbc.update("UPDATE attendance_breaks SET ended_at = GREATEST(started_at, ?) WHERE day_id = ? AND (ended_at IS NULL OR ended_at > ?)",
                Timestamp.from(end), d.id(), Timestamp.from(end));
        String note = clip(form.note().trim() + " (closed by " + nameOf(me.userId()) + ")", 300);
        DayRow base = new DayRow(d.id(), d.userId(), d.workDate(), d.punchInAt(), null, d.status(), d.shiftMinutes(),
                d.breakMinutes(), null, d.flags(), d.closeNote());
        finish(base, end, List.of("FORGOT_PUNCH_OUT"), me.userId(), null);
        jdbc.update("UPDATE attendance_days SET close_note = ?, closed_by_user_id = ? WHERE id = ?", note, me.userId(), d.id());
        record(d.userId(), d.id(), "MANAGER_CLOSE", null, request, clip("Closed by " + nameOf(me.userId()), 120));
        audit.record("ATTENDANCE_DAY_CLOSED", "ATTENDANCE_DAY", d.id(), null, null, before,
                Map.of("punchOutAt", end.toString(), "note", form.note().trim()));
        return toDay(dayById(d.id()), Instant.now());
    }

    // ============================================================== the monitor (every minute)

    public int monitor() {
        if (!monitoring.compareAndSet(false, true)) {
            return 0;
        }
        try {
            int sent = 0;
            Instant now = Instant.now();
            LocalDate today = LocalDate.now(ZONE);
            List<DayRow> open = jdbc.query("SELECT " + DAY_COLS + " FROM attendance_days WHERE status <> 'CLOSED'", AttendanceService::dayRow);
            for (DayRow d : open) {
                try {
                    sent += check(d, now, today);
                } catch (RuntimeException e) {
                    log.warn("Attendance check failed for day {}", d.id(), e);
                }
            }
            return sent;
        } finally {
            monitoring.set(false);
        }
    }

    private int check(DayRow d, Instant now, LocalDate today) {
        Live l = live(d, breaks(d.id()), now);
        List<String> f = flags(d, l, today);
        String name = nameOf(d.userId());
        int sent = 0;
        if (f.contains("BREAK_OVERTIME") && once(d.id(), "BREAK_OVERTIME")) {
            long over = l.onBreak() - d.breakMinutes() * 60L;
            String body = name + " has taken " + hm(l.onBreak()) + " of break, " + hm(over) + " over the allowed "
                    + hm(d.breakMinutes() * 60L) + (l.onBreakSince() != null ? ", and is still on break." : ".");
            sent += notifications.send(List.of(d.userId()), "ATTENDANCE", "\u26a0 Your break is over time",
                    "Your break is " + hm(over) + " over. Press End break when you are back.", null, null);
            sent += notifications.send(watchers(d.userId()), "ATTENDANCE", "\u26a0 Break over time: " + name, body, null, null);
        }
        if (f.contains("BREAK_DUE") && once(d.id(), "BREAK_DUE")) {
            sent += notifications.send(List.of(d.userId()), "ATTENDANCE", "Time for your break",
                    "You have worked " + hm(l.continuous()) + " without a break. Your " + hm(d.breakMinutes() * 60L)
                            + " break is compulsory: without it your attendance is not marked.", null, null);
        }
        if (f.contains("NO_BREAK") && once(d.id(), "NO_BREAK")) {
            sent += notifications.send(List.of(d.userId()), "ATTENDANCE", "\u26a0 Compulsory break not taken",
                    "You have worked " + hm(l.worked()) + " but taken only " + hm(l.onBreak()) + " of break. "
                            + "Take your break, or your attendance will not be marked.", null, null);
            sent += notifications.send(watchers(d.userId()), "ATTENDANCE", "\u26a0 No break: " + name,
                    name + " has worked " + hm(l.worked()) + " with only " + hm(l.onBreak()) + " of break. Attendance will not be marked.", null, null);
        }
        if (f.contains("FORGOT_PUNCH_OUT") && once(d.id(), "FORGOT_PUNCH_OUT")) {
            sent += notifications.send(List.of(d.userId()), "ATTENDANCE", "\u26a0 Did you forget to punch out?",
                    "Your day of " + d.workDate().format(DAY) + " is still open after " + hm(l.elapsed()) + ". Punch out now.", null, null);
            sent += notifications.send(watchers(d.userId()), "ATTENDANCE", "\u26a0 Forgot to punch out: " + name,
                    name + " punched in at " + d.punchInAt().atZone(ZONE).format(HM) + " on " + d.workDate().format(DAY)
                            + " and has not punched out. You can close the day in Attendance.", null, null);
        }
        return sent;
    }

    /** True the first time this alert is raised for this day. */
    private boolean once(long dayId, String kind) {
        return jdbc.update("INSERT INTO attendance_alerts (day_id, alert_kind) VALUES (?, ?) ON CONFLICT DO NOTHING", dayId, kind) == 1;
    }

    // ============================================================== small helpers

    private Map<String, Object> person(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT u.full_name, u.employee_code, r.name AS role_name, r.code AS role_code "
                + "FROM users u JOIN roles r ON r.id = u.role_id WHERE u.id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private final Map<Long, String> names = new HashMap<>();

    private String nameOf(long id) {
        synchronized (names) {
            if (names.size() > 500) {
                names.clear();
            }
            return names.computeIfAbsent(id, k -> {
                List<String> n = jdbc.queryForList("SELECT full_name FROM users WHERE id = ?", String.class, k);
                return n.isEmpty() ? "Employee " + k : n.get(0);
            });
        }
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    static String hm(long seconds) {
        long m = Math.max(0, seconds) / 60;
        return m >= 60 ? (m / 60) + " h " + String.format("%02d", m % 60) + " min" : m + " min";
    }

    static List<String> split(String s) {
        if (s == null || s.isBlank()) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (String p : Arrays.asList(s.split(","))) {
            if (!p.isBlank()) {
                out.add(p.trim());
            }
        }
        return out;
    }

    private static final Set<String> STORED = Set.of("NEW_DEVICE", "NEW_IP", "SHARED_DEVICE", "UNKNOWN_DEVICE", "FORGOT_PUNCH_OUT");

    /** Only device flags and "forgot to punch out" are kept; the rest are worked out again from the times. */
    static List<String> kept(String flags) {
        List<String> l = split(flags);
        l.removeIf(x -> !STORED.contains(x));
        return l;
    }

    static List<String> merge(List<String> a, List<String> b) {
        Set<String> s = new LinkedHashSet<>(a);
        if (b != null) {
            s.addAll(b);
        }
        return new ArrayList<>(s);
    }

    static String join(List<String> l) {
        return l == null ? "" : String.join(",", l);
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static Instant inst(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
