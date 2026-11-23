package com.sumasoft.tracking.sla;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

/**
 * Counts SLA time the way the project's support works (Step 37).
 *  - 24x7: every minute counts.
 *  - Business hours: only minutes between day start and day end, on working days,
 *    in the project's time zone; holidays do not count.
 */
public final class BusinessCalendar {

    /** Safety limit: never look further than this many days. */
    private static final int MAX_DAYS = 800;

    private final ZoneId zone;
    private final boolean allHours;
    private final Set<DayOfWeek> workDays;
    private final LocalTime dayStart;
    private final LocalTime dayEnd;
    private final Set<LocalDate> holidays;

    public BusinessCalendar(ZoneId zone, boolean allHours, Set<DayOfWeek> workDays,
                            LocalTime dayStart, LocalTime dayEnd, Set<LocalDate> holidays) {
        this.zone = zone;
        this.allHours = allHours;
        this.workDays = workDays;
        this.dayStart = dayStart;
        this.dayEnd = dayEnd;
        this.holidays = holidays;
    }

    public boolean isAllHours() {
        return allHours;
    }

    /** Support minutes between two moments (0 when 'to' is before 'from'). */
    public long minutesBetween(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) {
            return 0;
        }
        if (allHours) {
            return Duration.between(from, to).toMinutes();
        }
        LocalDate day = from.atZone(zone).toLocalDate();
        LocalDate last = to.atZone(zone).toLocalDate();
        long seconds = 0;
        for (int i = 0; i < MAX_DAYS && !day.isAfter(last); i++, day = day.plusDays(1)) {
            if (!isWorkDay(day)) {
                continue;
            }
            Instant open = day.atTime(dayStart).atZone(zone).toInstant();
            Instant close = day.atTime(dayEnd).atZone(zone).toInstant();
            Instant s = open.isAfter(from) ? open : from;
            Instant e = close.isBefore(to) ? close : to;
            if (e.isAfter(s)) {
                seconds += Duration.between(s, e).getSeconds();
            }
        }
        return seconds / 60;
    }

    /** The moment when 'minutes' support minutes have passed after 'from' (null if too far away). */
    public Instant plusMinutes(Instant from, long minutes) {
        if (from == null) {
            return null;
        }
        if (allHours) {
            return from.plus(Duration.ofMinutes(minutes));
        }
        long left = minutes * 60;
        LocalDate day = from.atZone(zone).toLocalDate();
        for (int i = 0; i < MAX_DAYS; i++, day = day.plusDays(1)) {
            if (!isWorkDay(day)) {
                continue;
            }
            Instant open = day.atTime(dayStart).atZone(zone).toInstant();
            Instant close = day.atTime(dayEnd).atZone(zone).toInstant();
            Instant s = open.isAfter(from) ? open : from;
            if (close.isAfter(s)) {
                long available = Duration.between(s, close).getSeconds();
                if (available >= left) {
                    return s.plusSeconds(left);
                }
                left -= available;
            }
        }
        return null;
    }

    private boolean isWorkDay(LocalDate day) {
        return workDays.contains(day.getDayOfWeek()) && !holidays.contains(day);
    }
}