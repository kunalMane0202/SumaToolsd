package com.sumasoft.tracking.imports;

import com.sumasoft.tracking.ticket.ImportedTicketValues;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks ONE row of the monthly ticket sheet and turns it into clean values.
 * Pure logic: no database, so it can be tested on its own (see ImportRowParserTest).
 *
 * Rules approved by the user on 4 Nov 2026:
 *  - Times in the sheet are India time (the project's time zone).
 *  - Dates are month/day/year; a date is read as day/month only when the first number is above 12.
 *    For the CREATION date, when both readings are possible, the "Day" column decides; if it still
 *    does not fit, the row gets a warning.
 *  - Status and priority words are translated with the project's mapping (e.g. Solved -> RESOLVED).
 *  - Day, Timeframe and Resolution Time are CALCULATED; the original text is kept and differences are reported.
 *  - Nothing is silently "fixed": a closure that is before the creation, incomplete, or that does not fit
 *    the status is NOT used, and a warning explains why.
 * A row with an ERROR is not imported: missing/unknown Ticket ID, creation date, or status.
 */
public class ImportRowParser {

    /** What the checker needs to know about the project. */
    public record Context(
            ZoneId zone,
            Map<String, String> statusWords,    // lower-case word in the file -> our status code
            Map<String, String> statusGroups,   // our status code -> OPEN / IN_PROGRESS / PENDING / RESOLVED / CLOSED
            Map<String, String> priorityWords,  // lower-case word in the file -> our priority code
            java.util.Set<String> priorityCodes
    ) {
    }

    /** The result for one row: values (null when the row has an error) and every issue found. */
    public record Result(int rowNumber, String externalTicketId, ImportedTicketValues values, List<ImportIssue> issues) {
        public boolean hasError() {
            return values == null;
        }
    }

    private static final Pattern DMY = Pattern.compile("^(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4})$");
    private static final Pattern YMD = Pattern.compile("^(\\d{4})[/.\\-](\\d{1,2})[/.\\-](\\d{1,2})$");
    private static final Pattern TIME = Pattern.compile("^(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?:\\.\\d+)?\\s*([AaPp][Mm])?$");
    private static final Pattern DURATION = Pattern.compile("^(-)?(\\d+):(\\d{2}):(\\d{2})$");
    private static final Pattern TIMEFRAME = Pattern.compile("^(\\d{1,2})\\s*-\\s*(\\d{1,2})\\s*([AaPp][Mm])?$");
    private static final String EXCEL_DATE = "@D:";

    private final Context ctx;

    public ImportRowParser(Context ctx) {
        this.ctx = ctx;
    }

    public Result parse(RawRow row) {
        int n = row.rowNumber();
        List<ImportIssue> issues = new ArrayList<>();

        // ---------- Ticket ID (required)
        String ticketId = cleanId(row.get(ImportColumns.TICKET_ID));
        if (ticketId == null) {
            issues.add(ImportIssue.error(n, null, ImportColumns.TICKET_ID, "Ticket ID is empty", null));
            return new Result(n, null, null, issues);
        }
        if (ticketId.length() > 100) {
            issues.add(ImportIssue.error(n, null, ImportColumns.TICKET_ID, "Ticket ID is longer than 100 characters", ticketId));
            return new Result(n, null, null, issues);
        }

        // ---------- Sr.no (optional)
        Integer srNo = null;
        String rawSr = row.get(ImportColumns.SR_NO);
        if (rawSr != null) {
            String s = rawSr.endsWith(".0") ? rawSr.substring(0, rawSr.length() - 2) : rawSr;
            if (s.matches("^\\d{1,9}$")) {
                srNo = Integer.parseInt(s);
            } else {
                issues.add(ImportIssue.warning(n, ticketId, ImportColumns.SR_NO, "Sr.no is not a number; ignored", rawSr));
            }
        }

        // ---------- Creation date + time (date required)
        String rawDay = row.get(ImportColumns.DAY);
        String rawCreatedDate = row.get(ImportColumns.CREATED_DATE);
        DateReading created = readDate(rawCreatedDate);
        if (created.error != null) {
            issues.add(ImportIssue.error(n, ticketId, ImportColumns.CREATED_DATE, created.error, rawCreatedDate));
            return new Result(n, ticketId, null, issues);
        }
        LocalDate createdDate = chooseCreationDate(created, rawDay, n, ticketId, rawCreatedDate, issues);

        String rawCreatedTime = row.get(ImportColumns.CREATED_TIME);
        LocalTime createdTime = readTime(rawCreatedTime);
        if (createdTime == null) {
            createdTime = LocalTime.MIDNIGHT;
            issues.add(ImportIssue.warning(n, ticketId, ImportColumns.CREATED_TIME,
                    rawCreatedTime == null ? "Time of Creation is empty; 00:00 used"
                                           : "Time of Creation is not a valid time; 00:00 used", rawCreatedTime));
        }
        LocalDateTime createdLocal = LocalDateTime.of(createdDate, createdTime);
        Instant createdAt = createdLocal.atZone(ctx.zone()).toInstant();

        // ---------- Status (required)
        String rawStatus = row.get(ImportColumns.STATUS);
        String statusCode = mapStatus(rawStatus);
        if (statusCode == null) {
            issues.add(ImportIssue.error(n, ticketId, ImportColumns.STATUS,
                    rawStatus == null ? "Status is empty"
                                      : "Unknown status '" + rawStatus + "'. Add it to the project's status mapping", rawStatus));
            return new Result(n, ticketId, null, issues);
        }
        String group = ctx.statusGroups().getOrDefault(statusCode, "OPEN");

        // ---------- Closure (optional; used only when complete, after the creation, and fitting the status)
        String rawClosedDate = row.get(ImportColumns.CLOSED_DATE);
        String rawClosedTime = row.get(ImportColumns.CLOSED_TIME);
        Instant closure = null;
        if (rawClosedDate != null || rawClosedTime != null) {
            DateReading cd = readDate(rawClosedDate);
            LocalTime ct = readTime(rawClosedTime);
            if (rawClosedDate == null || rawClosedTime == null) {
                issues.add(ImportIssue.warning(n, ticketId,
                        rawClosedDate == null ? ImportColumns.CLOSED_DATE : ImportColumns.CLOSED_TIME,
                        "Closure needs both Date of Closure and Time of Closure; closure not used",
                        rawClosedDate == null ? rawClosedTime : rawClosedDate));
            } else if (cd.error != null) {
                issues.add(ImportIssue.warning(n, ticketId, ImportColumns.CLOSED_DATE, cd.error + "; closure not used", rawClosedDate));
            } else if (ct == null) {
                issues.add(ImportIssue.warning(n, ticketId, ImportColumns.CLOSED_TIME,
                        "Time of Closure is not a valid time; closure not used", rawClosedTime));
            } else {
                Instant candidate = LocalDateTime.of(cd.primary, ct).atZone(ctx.zone()).toInstant();
                if (candidate.isBefore(createdAt)) {
                    issues.add(ImportIssue.warning(n, ticketId, ImportColumns.CLOSED_DATE,
                            "Closure (" + cd.primary + " " + ct + ") is before creation (" + createdLocal + "); closure not used",
                            rawClosedDate + " " + rawClosedTime));
                } else {
                    closure = candidate;
                }
            }
        }

        Instant resolvedAt = null;
        Instant closedAt = null;
        if ("RESOLVED".equals(group)) {
            resolvedAt = closure;
        } else if ("CLOSED".equals(group)) {
            closedAt = closure;
        } else if (closure != null) {
            issues.add(ImportIssue.warning(n, ticketId, ImportColumns.STATUS,
                    "Status '" + rawStatus + "' is not finished but a closure is filled; closure not used", rawStatus));
            closure = null;
        }
        if (("RESOLVED".equals(group) || "CLOSED".equals(group)) && closure == null
                && rawClosedDate == null && rawClosedTime == null) {
            issues.add(ImportIssue.warning(n, ticketId, ImportColumns.CLOSED_DATE,
                    "Status '" + rawStatus + "' is finished but there is no closure date/time", null));
        }

        // ---------- Resolution Time: compare the file's value with ours
        String rawResolution = row.get(ImportColumns.RESOLUTION_TIME);
        if (rawResolution != null) {
            if (rawResolution.startsWith("#")) {
                issues.add(ImportIssue.warning(n, ticketId, ImportColumns.RESOLUTION_TIME,
                        "Excel error in Resolution Time; the calculated value is used instead", rawResolution));
            } else if (closure != null) {
                Long fileSeconds = readDurationSeconds(rawResolution);
                long ours = Duration.between(createdAt, closure).getSeconds();
                if (fileSeconds == null) {
                    issues.add(ImportIssue.warning(n, ticketId, ImportColumns.RESOLUTION_TIME,
                            "Resolution Time is not in h:mm:ss form; the calculated value is used", rawResolution));
                } else if (Math.abs(fileSeconds - ours) > 60) {
                    issues.add(ImportIssue.warning(n, ticketId, ImportColumns.RESOLUTION_TIME,
                            "File says " + rawResolution + " but creation-to-closure is " + hms(ours)
                                    + "; the calculated value is used", rawResolution));
                }
            }
        }

        // ---------- Timeframe and Day: compare with the creation time
        String rawTimeframe = row.get(ImportColumns.TIMEFRAME);
        if (rawTimeframe != null) {
            Matcher m = TIMEFRAME.matcher(rawTimeframe.replace(" ", ""));
            if (!m.matches()) {
                issues.add(ImportIssue.warning(n, ticketId, ImportColumns.TIMEFRAME,
                        "Timeframe is not in a known form (like 3-4pm); the calculated value is used", rawTimeframe));
            } else if (Integer.parseInt(m.group(1)) % 12 != createdTime.getHour() % 12) {
                issues.add(ImportIssue.warning(n, ticketId, ImportColumns.TIMEFRAME,
                        "Timeframe '" + rawTimeframe + "' does not match the Time of Creation " + createdTime
                                + "; the calculated value is used", rawTimeframe));
            }
        }

        // ---------- Escalated
        boolean escalated = false;
        String rawEscalated = row.get(ImportColumns.ESCALATED);
        if (rawEscalated != null) {
            String e = rawEscalated.toLowerCase(Locale.ROOT);
            if (e.equals("yes") || e.equals("y") || e.equals("true") || e.equals("1")) {
                escalated = true;
            } else if (!(e.equals("no") || e.equals("n") || e.equals("false") || e.equals("0"))) {
                issues.add(ImportIssue.warning(n, ticketId, ImportColumns.ESCALATED,
                        "Escalated should be Yes or No; No used", rawEscalated));
            }
        }

        // ---------- Priority (optional)
        String rawPriority = row.get(ImportColumns.PRIORITY);
        String priorityCode = null;
        if (rawPriority != null) {
            priorityCode = mapPriority(rawPriority);
            if (priorityCode == null) {
                issues.add(ImportIssue.warning(n, ticketId, ImportColumns.PRIORITY,
                        "Unknown priority '" + rawPriority + "'; left empty", rawPriority));
            }
        }

        // ---------- Plain text columns (cut to the database size, with a warning)
        ImportedTicketValues values = new ImportedTicketValues(
                ticketId,
                srNo,
                createdAt,
                resolvedAt,
                closedAt,
                statusCode,
                priorityCode,
                text(row, ImportColumns.ASSIGNEE, 150, n, ticketId, issues),
                text(row, ImportColumns.CLOSED_BY, 150, n, ticketId, issues),
                text(row, ImportColumns.REQUESTER, 200, n, ticketId, issues),
                text(row, ImportColumns.REQUESTER_ROLE, 100, n, ticketId, issues),
                text(row, ImportColumns.SOURCE, 50, n, ticketId, issues),
                escalated,
                text(row, ImportColumns.SATISFACTION, 50, n, ticketId, issues),
                text(row, ImportColumns.ISSUE_TYPE, 100, n, ticketId, issues),
                text(row, ImportColumns.PLAN, 100, n, ticketId, issues),
                text(row, ImportColumns.TOPIC, 200, n, ticketId, issues),
                text(row, ImportColumns.CATEGORY, 100, n, ticketId, issues),
                text(row, ImportColumns.SUBCATEGORY, 100, n, ticketId, issues),
                text(row, ImportColumns.DESCRIPTION, 4000, n, ticketId, issues),
                cut(rawTimeframe, 50),
                cut(rawDay, 20),
                cut(rawResolution, 20));
        return new Result(n, ticketId, values, issues);
    }

    // ================================================================ dates and times

    /** A date reading: the main reading, an alternative (when both month/day and day/month are possible), or an error. */
    static final class DateReading {
        final LocalDate primary;
        final LocalDate alternative;
        final String error;

        DateReading(LocalDate primary, LocalDate alternative, String error) {
            this.primary = primary;
            this.alternative = alternative;
            this.error = error;
        }
    }

    static DateReading readDate(String raw) {
        if (raw == null) {
            return new DateReading(null, null, "Date is empty");
        }
        String s = raw.strip();
        try {
            if (s.startsWith(EXCEL_DATE)) {
                LocalDateTime dt = LocalDateTime.parse(s.substring(EXCEL_DATE.length()));
                if (dt.getYear() < 1901) {
                    return new DateReading(null, null, "Cell contains only a time, not a date");
                }
                return new DateReading(dt.toLocalDate(), null, null);
            }
            Matcher ymd = YMD.matcher(s);
            if (ymd.matches()) {
                return new DateReading(LocalDate.of(Integer.parseInt(ymd.group(1)),
                        Integer.parseInt(ymd.group(2)), Integer.parseInt(ymd.group(3))), null, null);
            }
            if (s.matches("^\\d{5}(\\.0+)?$")) {               // Excel day number, e.g. 46142
                long days = Long.parseLong(s.replaceAll("\\..*$", ""));
                return new DateReading(LocalDate.of(1899, 12, 30).plusDays(days), null, null);
            }
            Matcher m = DMY.matcher(s);
            if (!m.matches()) {
                return new DateReading(null, null, "Date is not in a known form (like 5/1/2026)");
            }
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(2));
            int y = Integer.parseInt(m.group(3));
            if (y < 2000 || y > 2100) {
                return new DateReading(null, null, "Year " + y + " looks wrong");
            }
            if (a > 12 && b > 12) {
                return new DateReading(null, null, "Date is not valid (both numbers above 12)");
            }
            if (a > 12) {                                         // only day/month is possible
                return new DateReading(LocalDate.of(y, b, a), null, null);
            }
            if (b > 12) {                                         // only month/day is possible
                return new DateReading(LocalDate.of(y, a, b), null, null);
            }
            LocalDate monthDay = LocalDate.of(y, a, b);           // approved default: month/day
            LocalDate dayMonth = (a == b) ? null : LocalDate.of(y, b, a);
            return new DateReading(monthDay, dayMonth, null);
        } catch (RuntimeException e) {
            return new DateReading(null, null, "Date is not a real calendar date");
        }
    }

    /** For the creation date: when both readings are possible, the Day column decides. */
    private LocalDate chooseCreationDate(DateReading d, String rawDay, int n, String ticketId,
                                         String rawDate, List<ImportIssue> issues) {
        DayOfWeek day = readDay(rawDay);
        if (d.alternative == null) {
            if (day != null && d.primary.getDayOfWeek() != day) {
                issues.add(ImportIssue.warning(n, ticketId, ImportColumns.DAY,
                        "Day '" + rawDay + "' does not match the date " + d.primary + " (" + dayName(d.primary) + ")", rawDay));
            }
            return d.primary;
        }
        if (day == null) {
            if (rawDay != null) {
                issues.add(ImportIssue.warning(n, ticketId, ImportColumns.DAY,
                        "Day is not a weekday name; date read as month/day (" + d.primary + ")", rawDay));
            }
            return d.primary;
        }
        if (d.primary.getDayOfWeek() == day) {
            return d.primary;
        }
        if (d.alternative.getDayOfWeek() == day) {
            issues.add(ImportIssue.warning(n, ticketId, ImportColumns.CREATED_DATE,
                    "Date read as day/month (" + d.alternative + ") because the Day column says " + rawDay, rawDate));
            return d.alternative;
        }
        issues.add(ImportIssue.warning(n, ticketId, ImportColumns.DAY,
                "Day '" + rawDay + "' fits neither " + d.primary + " nor " + d.alternative
                        + "; month/day used (" + d.primary + ")", rawDay));
        return d.primary;
    }

    static LocalTime readTime(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        try {
            if (s.startsWith(EXCEL_DATE)) {
                return LocalDateTime.parse(s.substring(EXCEL_DATE.length())).toLocalTime();
            }
            if (s.matches("^0?\\.\\d+$")) {                       // Excel time as a fraction of a day
                long seconds = Math.round(Double.parseDouble(s) * 86400);
                return LocalTime.ofSecondOfDay(seconds % 86400);
            }
            Matcher m = TIME.matcher(s);
            if (!m.matches()) {
                return null;
            }
            int h = Integer.parseInt(m.group(1));
            int min = Integer.parseInt(m.group(2));
            int sec = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
            String ampm = m.group(4);
            if (ampm != null) {
                if (h < 1 || h > 12) {
                    return null;
                }
                boolean pm = ampm.equalsIgnoreCase("pm");
                h = (h % 12) + (pm ? 12 : 0);
            }
            return LocalTime.of(h, min, sec);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static DayOfWeek readDay(String raw) {
        if (raw == null || raw.length() < 3) {
            return null;
        }
        String p = raw.strip().substring(0, 3).toLowerCase(Locale.ROOT);
        for (DayOfWeek d : DayOfWeek.values()) {
            if (d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toLowerCase(Locale.ROOT).equals(p)) {
                return d;
            }
        }
        return null;
    }

    static Long readDurationSeconds(String raw) {
        Matcher m = DURATION.matcher(raw.strip());
        if (!m.matches()) {
            return null;
        }
        long s = Long.parseLong(m.group(2)) * 3600 + Long.parseLong(m.group(3)) * 60 + Long.parseLong(m.group(4));
        return m.group(1) != null ? -s : s;
    }

    static String hms(long seconds) {
        String sign = seconds < 0 ? "-" : "";
        long s = Math.abs(seconds);
        return String.format("%s%d:%02d:%02d", sign, s / 3600, (s % 3600) / 60, s % 60);
    }

    private static String dayName(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    // ================================================================ words and text

    private String mapStatus(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.strip().toLowerCase(Locale.ROOT);
        String mapped = ctx.statusWords().get(key);
        if (mapped != null) {
            return mapped;
        }
        String asCode = raw.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        return ctx.statusGroups().containsKey(asCode) ? asCode : null;
    }

    private String mapPriority(String raw) {
        String key = raw.strip().toLowerCase(Locale.ROOT);
        String mapped = ctx.priorityWords().get(key);
        if (mapped != null) {
            return mapped;
        }
        String asCode = raw.strip().toUpperCase(Locale.ROOT);
        return ctx.priorityCodes().contains(asCode) ? asCode : null;
    }

    /** Ticket IDs: "115061.0" (Excel number) -> "115061". */
    private static String cleanId(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        if (s.matches("^\\d+\\.0+$")) {
            s = s.substring(0, s.indexOf('.'));
        }
        return s.isEmpty() ? null : s;
    }

    private static String text(RawRow row, String column, int max, int n, String ticketId, List<ImportIssue> issues) {
        String v = row.get(column);
        if (v != null && v.startsWith(EXCEL_DATE)) {
            v = v.substring(EXCEL_DATE.length());
        }
        if (v != null && v.length() > max) {
            issues.add(ImportIssue.warning(n, ticketId, column, "Text longer than " + max + " characters was cut", v));
            v = v.substring(0, max);
        }
        return v;
    }

    private static String cut(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }
}