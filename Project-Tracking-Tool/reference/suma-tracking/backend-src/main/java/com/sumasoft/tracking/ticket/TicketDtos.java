package com.sumasoft.tracking.ticket;

import com.sumasoft.tracking.user.AppUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * The "message shapes" used by the ticket API, kept together in one file.
 */
public final class TicketDtos {

    private TicketDtos() {
    }

    /** What a person sends to create a ticket by hand. */
    public record TicketCreateRequest(
            @NotNull(message = "Project is required")
            Long projectId,

            @NotBlank(message = "Subject is required")
            @Size(max = 500, message = "Subject must be at most 500 characters")
            String subject,

            @Size(max = 20000, message = "Description must be at most 20000 characters")
            String description,

            @Size(max = 100, message = "Category must be at most 100 characters")
            String category,

            @Size(max = 100, message = "Subcategory must be at most 100 characters")
            String subcategory,

            @Size(max = 100, message = "Issue type must be at most 100 characters")
            String issueType,

            @Size(max = 200, message = "Topic must be at most 200 characters")
            String topic,

            @Size(max = 100, message = "Plan must be at most 100 characters")
            String plan,

            @Pattern(regexp = "^[A-Za-z_]{1,20}$", message = "Priority must be CRITICAL, HIGH, MEDIUM or LOW")
            String priorityCode,

            @Size(max = 50, message = "Channel must be at most 50 characters")
            String channel,

            @Size(max = 200, message = "Requester name must be at most 200 characters")
            String requesterName,

            @Size(max = 100, message = "Requester role must be at most 100 characters")
            String requesterRole,

            Long assigneeUserId,

            Boolean escalated
    ) {
    }

    /** The filters of the ticket list. Every filter is optional. */
    public record TicketFilter(
            String q,
            Long clientId,
            Long projectId,
            String status,
            String statusGroup,
            String priority,
            String category,
            Long assigneeUserId,
            Boolean escalated,
            LocalDate createdFrom,
            LocalDate createdTo
    ) {
    }

    /** One ticket as the API shows it, with calculated Day, Timeframe, Resolution time and Age. */
    public record TicketResponse(
            Long id,
            String ticketCode,
            Long clientId,
            String clientName,
            Long projectId,
            String projectName,
            String sourceSystem,
            String externalTicketId,
            String subject,
            String description,
            String category,
            String subcategory,
            String issueType,
            String topic,
            String plan,
            String priorityCode,
            String priorityName,
            String statusCode,
            String statusName,
            String statusGroup,
            Long assigneeUserId,
            String assigneeName,
            Long closedByUserId,
            String closedByName,
            String requesterName,
            String requesterRole,
            String channel,
            boolean escalated,
            String satisfactionRating,
            Instant createdAtSource,
            Instant updatedAtSource,
            Instant firstResponseAt,
            Instant resolvedAt,
            Instant closedAt,
            Instant slaDueAt,
            String day,
            String timeframe,
            String resolutionTime,
            Long resolutionMinutes,
            Long ageMinutes,
            String timeframeRaw,
            String dayRaw,
            String resolutionTimeRaw,
            Integer importRowNumber,
            Instant createdAt,
            Instant updatedAt
    ) {

        public static TicketResponse from(Ticket t, Instant now) {
            ZoneId zone = zoneOf(t.getProject().getTimeZone());
            ZonedDateTime created = t.getCreatedAtSource().atZone(zone);
            int hour = created.getHour();
            String timeframe = String.format("%02d:00-%02d:00", hour, (hour + 1) % 24);
            String day = created.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

            Instant end = t.getClosedAt() != null ? t.getClosedAt() : t.getResolvedAt();
            Long resolutionMinutes = null;
            String resolutionTime = null;
            if (end != null) {
                Duration d = Duration.between(t.getCreatedAtSource(), end);
                resolutionMinutes = d.toMinutes();
                resolutionTime = hms(d);
            }
            Long ageMinutes = t.getStatus().isDone() ? null : Duration.between(t.getCreatedAtSource(), now).toMinutes();

            return new TicketResponse(
                    t.getId(),
                    String.format("TK-%06d", t.getId()),
                    t.getClient().getId(),
                    t.getClient().getClientName(),
                    t.getProject().getId(),
                    t.getProject().getProjectName(),
                    t.getSourceSystem(),
                    t.getExternalTicketId(),
                    t.getSubject(),
                    t.getDescription(),
                    t.getCategory(),
                    t.getSubcategory(),
                    t.getIssueType(),
                    t.getTopic(),
                    t.getPlan(),
                    t.getPriority() == null ? null : t.getPriority().getCode(),
                    t.getPriority() == null ? null : t.getPriority().getName(),
                    t.getStatus().getCode(),
                    t.getStatus().getName(),
                    t.getStatus().getStatusGroup(),
                    t.getAssignee() == null ? null : t.getAssignee().getId(),
                    nameOf(t.getAssignee(), t.getAssigneeNameRaw()),
                    t.getClosedBy() == null ? null : t.getClosedBy().getId(),
                    nameOf(t.getClosedBy(), t.getClosedByNameRaw()),
                    t.getRequesterName(),
                    t.getRequesterRole(),
                    t.getChannel(),
                    t.isEscalated(),
                    t.getSatisfactionRating(),
                    t.getCreatedAtSource(),
                    t.getUpdatedAtSource(),
                    t.getFirstResponseAt(),
                    t.getResolvedAt(),
                    t.getClosedAt(),
                    t.getSlaDueAt(),
                    day,
                    timeframe,
                    resolutionTime,
                    resolutionMinutes,
                    ageMinutes,
                    t.getTimeframeRaw(),
                    t.getDayRaw(),
                    t.getResolutionTimeRaw(),
                    t.getImportRowNumber(),
                    t.getCreatedAt(),
                    t.getUpdatedAt());
        }

        private static String nameOf(AppUser user, String rawName) {
            return user != null ? user.getFullName() : rawName;
        }

        private static ZoneId zoneOf(String zone) {
            try {
                return ZoneId.of(zone == null ? "Asia/Kolkata" : zone);
            } catch (RuntimeException e) {
                return ZoneId.of("Asia/Kolkata");
            }
        }

        /** Like the Excel column: hours:minutes:seconds (hours can be above 24, negative means bad data). */
        private static String hms(Duration d) {
            long seconds = d.getSeconds();
            String sign = seconds < 0 ? "-" : "";
            seconds = Math.abs(seconds);
            return String.format("%s%d:%02d:%02d", sign, seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        }
    }

    /** One page of tickets. */
    public record TicketPage(List<TicketResponse> items, int page, int size, long totalItems, int totalPages) {
    }

    /** One line of a ticket's history. */
    public record TicketHistoryResponse(
            Long id,
            Instant occurredAt,
            String eventType,
            String fieldName,
            String oldValue,
            String newValue,
            Long changedByUserId,
            String changedByName,
            String source,
            String note
    ) {
        public static TicketHistoryResponse from(TicketHistory h) {
            return new TicketHistoryResponse(h.getId(), h.getOccurredAt(), h.getEventType(), h.getFieldName(),
                    h.getOldValue(), h.getNewValue(),
                    h.getChangedBy() == null ? null : h.getChangedBy().getId(),
                    h.getChangedBy() == null ? null : h.getChangedBy().getFullName(),
                    h.getSource(), h.getNote());
        }
    }

    public record StatusResponse(String code, String name, String statusGroup, boolean finalStatus) {
        public static StatusResponse from(TicketStatus s) {
            return new StatusResponse(s.getCode(), s.getName(), s.getStatusGroup(), s.isFinalStatus());
        }
    }

    public record PriorityResponse(String code, String name) {
        public static PriorityResponse from(TicketPriority p) {
            return new PriorityResponse(p.getCode(), p.getName());
        }
    }
}