package com.sumasoft.tracking.attendance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** What the Attendance pages send and receive (Step 41). All times are sent as instants; the screen shows India time. */
public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    /** Sent with every punch: which browser on which computer. The id is made once per browser and kept there. */
    public record DeviceInfo(
            @Size(max = 64, message = "Device id is too long") String deviceId,
            @Size(max = 120, message = "Device name is too long") String deviceLabel,
            @Pattern(regexp = "^(TEA|LUNCH|DINNER|REFRESHMENT)?$", message = "Choose Tea, Lunch, Dinner or Refreshment break")
            String breakType) {
    }

    public record Schedule(int shiftMinutes, int breakMinutes, boolean custom) {
    }

    public record BreakItem(Instant startedAt, Instant endedAt, long seconds, String kind, String breakType) {
    }

    /** One working day, with live numbers when it is still open. */
    public record Day(Long id, LocalDate workDate, String status, Instant punchInAt, Instant punchOutAt,
                      int shiftMinutes, int breakMinutes, long workedSeconds, long breakSeconds,
                      long continuousSeconds, Instant onBreakSince, Boolean marked, String markReason,
                      List<String> flags, List<BreakItem> breaks, String closeNote) {
    }

    /** Everything the timer and break cards need. serverNow lets the screen correct its own clock. */
    public record MyDay(Instant serverNow, Schedule schedule, Day day, boolean doneForToday, boolean canPunchInAgain, String ip) {
    }

    public record Event(long id, String kind, Instant at, String ip, String deviceId, String deviceLabel,
                        String userAgent, List<String> flags) {
    }

    public record TeamRow(long userId, String fullName, String employeeCode, String roleName, Schedule schedule,
                          Day day, String state, List<String> alerts, String lastIp, String lastDevice,
                          long deviceCount) {
    }

    public record Team(LocalDate date, Instant serverNow, boolean canManage, List<TeamRow> people) {
    }

    public record ScheduleRow(long userId, String fullName, String employeeCode, String roleName, Schedule schedule,
                              Instant updatedAt, String updatedBy, boolean editable) {
    }

    public record ScheduleForm(
            @NotNull(message = "Enter the shift hours")
            @Min(value = 60, message = "A shift must be at least 1 hour")
            @Max(value = 960, message = "A shift can be at most 16 hours") Integer shiftMinutes,
            @NotNull(message = "Enter the break time")
            @Min(value = 0, message = "Break time cannot be below 0")
            @Max(value = 240, message = "A break can be at most 4 hours") Integer breakMinutes) {
    }

    public record CloseForm(
            @NotBlank(message = "Enter the punch-out time")
            @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Enter the time as HH:MM, for example 18:30")
            String punchOutTime,
            @NotBlank(message = "Write why you are closing this day")
            @Size(min = 5, max = 300, message = "Write 5 to 300 characters") String note) {
    }
}
