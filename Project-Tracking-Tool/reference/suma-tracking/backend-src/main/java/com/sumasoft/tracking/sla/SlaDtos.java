package com.sumasoft.tracking.sla;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Requests and answers of the SLA Management page (Step 37). */
public final class SlaDtos {

    private SlaDtos() {
    }

    public static final String DAY = "(MON|TUE|WED|THU|FRI|SAT|SUN)";

    // ------------------------------------------------------------ settings

    public record TargetForm(
            @NotBlank(message = "Priority is required")
            @Pattern(regexp = "^(CRITICAL|HIGH|MEDIUM|LOW)$", message = "Priority must be CRITICAL, HIGH, MEDIUM or LOW")
            String priorityCode,
            @NotNull(message = "First response target is required")
            @Min(value = 1, message = "First response target must be at least 1 minute")
            @Max(value = 43200, message = "First response target can be at most 30 days")
            Integer firstResponseMinutes,
            @NotNull(message = "Resolution target is required")
            @Min(value = 1, message = "Resolution target must be at least 1 minute")
            @Max(value = 129600, message = "Resolution target can be at most 90 days")
            Integer resolutionMinutes
    ) {
    }

    public record HolidayForm(
            @NotNull(message = "Holiday date is required")
            LocalDate date,
            @NotBlank(message = "Holiday name is required")
            @Size(max = 100, message = "Holiday name must be at most 100 characters")
            String name
    ) {
    }

    public record SettingsForm(
            @NotBlank(message = "Support hours are required")
            @Pattern(regexp = "^(TWENTY_FOUR_SEVEN|BUSINESS_HOURS)$", message = "Support hours must be 24x7 or business hours")
            String hoursMode,
            @NotBlank(message = "Choose at least one working day")
            @Pattern(regexp = "^" + DAY + "(," + DAY + "){0,6}$", message = "Working days are not valid")
            String workDays,
            @NotBlank(message = "Day start is required")
            @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "Day start must be a time like 09:00")
            String dayStart,
            @NotBlank(message = "Day end is required")
            @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "Day end must be a time like 18:00")
            String dayEnd,
            @NotNull(message = "Warning level is required")
            @Min(value = 50, message = "Warning level must be between 50% and 95%")
            @Max(value = 95, message = "Warning level must be between 50% and 95%")
            Integer warnPercent,
            @NotNull(message = "Choose whether the clock pauses while waiting for the customer")
            Boolean pauseWhenWaiting,
            @NotNull(message = "Targets are required")
            @Size(min = 4, max = 4, message = "Give a target for each of the 4 priorities")
            List<@Valid TargetForm> targets,
            @Size(max = 100, message = "At most 100 holidays")
            List<@Valid HolidayForm> holidays
    ) {
    }

    public record Target(String priorityCode, int firstResponseMinutes, int resolutionMinutes) {
    }

    public record Holiday(LocalDate date, String name) {
    }

    public record Settings(Long projectId, String projectName, String timeZone, String hoursMode, String workDays,
                           String dayStart, String dayEnd, int warnPercent, boolean pauseWhenWaiting,
                           List<Target> targets, List<Holiday> holidays, Instant updatedAt, String updatedBy) {
    }

    // ------------------------------------------------------------ overview

    public record ProjectSla(Long projectId, String projectName, String hoursMode,
                             long responseMeasured, long responseMet,
                             long resolutionMeasured, long resolutionMet,
                             Double responseCompliance, Double resolutionCompliance,
                             long openOnTrack, long openAtRisk, long openBreached, long openPaused) {
    }

    public record AttentionTicket(Long ticketId, String ticketNumber, String subject, String projectName,
                                  String priority, String statusName, String assignee,
                                  String state, String clock, long targetMinutes, long elapsedMinutes,
                                  Instant dueAt) {
    }

    public record Overview(Instant generatedAt, String from, String to,
                           List<ProjectSla> projects, List<AttentionTicket> attention) {
    }
}