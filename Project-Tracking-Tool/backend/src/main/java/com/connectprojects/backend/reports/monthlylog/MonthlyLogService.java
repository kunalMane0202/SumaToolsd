package com.connectprojects.backend.reports.monthlylog;

import com.connectprojects.backend.account.AppUser;
import com.connectprojects.backend.account.PermissionCatalog;
import com.connectprojects.backend.account.UserRole;
import com.connectprojects.backend.api.ApiException;
import com.connectprojects.backend.auth.CurrentUserService;
import com.connectprojects.backend.work.ProjectEntity;
import com.connectprojects.backend.work.ProjectRepository;
import com.connectprojects.backend.work.ProjectSlaEntity;
import com.connectprojects.backend.work.ProjectSlaRepository;
import com.connectprojects.backend.work.TicketPriorityEntity;
import com.connectprojects.backend.work.TicketPriorityRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MonthlyLogService {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;
    private final MonthlyLogImportRepository imports;
    private final MonthlyLogRowRepository rows;
    private final ProjectRepository projects;
    private final TicketPriorityRepository priorities;
    private final ProjectSlaRepository slas;
    private final CurrentUserService currentUserService;
    private final MonthlyLogWorkbookParser parser;
    private final MonthlyLogSummaryCalculator calculator;
    private final ObjectMapper objectMapper;

    public MonthlyLogService(
            MonthlyLogImportRepository imports,
            MonthlyLogRowRepository rows,
            ProjectRepository projects,
            TicketPriorityRepository priorities,
            ProjectSlaRepository slas,
            CurrentUserService currentUserService,
            MonthlyLogWorkbookParser parser,
            ObjectMapper objectMapper
    ) {
        this.imports = imports;
        this.rows = rows;
        this.projects = projects;
        this.priorities = priorities;
        this.slas = slas;
        this.currentUserService = currentUserService;
        this.parser = parser;
        this.objectMapper = objectMapper;
        this.calculator = new MonthlyLogSummaryCalculator();
    }

    @Transactional
    public MonthlyLogReportResponse upload(Long projectId, MultipartFile file, Authentication authentication) {
        ProjectAccess access = requireProject(projectId, authentication, true);
        ParsedUpload upload = parseUpload(file);
        String fileName = upload.fileName();
        MonthlyLogWorkbookParser.ParsedMonthlyLog parsed = upload.parsed();

        MonthlyLogImportEntity report = imports.save(new MonthlyLogImportEntity(
                access.project(),
                access.user().getUsername(),
                fileName,
                parsed.sheetName(),
                parsed.periodStart(),
                parsed.periodEnd(),
                parsed.rows().size()
        ));

        List<MonthlyLogRowEntity> storedRows = new ArrayList<>(parsed.rows().size());
        for (MonthlyLogData data : parsed.rows()) {
            try {
                String json = objectMapper.writeValueAsString(data);
                String searchText = String.join(" ", data.values().values()).toLowerCase(Locale.ROOT);
                storedRows.add(new MonthlyLogRowEntity(report, data.sourceRow(), searchText, json));
            } catch (JsonProcessingException exception) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The parsed workbook rows could not be stored.");
            }
        }
        rows.saveAll(storedRows);
        return report(report, "", null, 0, DEFAULT_PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public MonthlyLogPreviewResponse preview(Long projectId, MultipartFile file, Authentication authentication) {
        ProjectAccess access = requireProject(projectId, authentication, true);
        ParsedUpload upload = parseUpload(file);
        MonthlyLogWorkbookParser.ParsedMonthlyLog parsed = upload.parsed();
        List<MonthlyLogRowResponse> previewRows = parsed.rows().stream()
                .limit(10)
                .map(row -> new MonthlyLogRowResponse(row.sourceRow(), row.ticketId(), row.values()))
                .toList();

        return new MonthlyLogPreviewResponse(
                upload.fileName(),
                parsed.sheetName(),
                parsed.periodStart(),
                parsed.periodEnd(),
                parsed.rows().size(),
                parsed.columns(),
                parsed.columnMappings(),
                parsed.warnings(),
                calculator.calculate(parsed.rows(), resolutionSlas(access.project().getId())),
                previewRows
        );
    }

    @Transactional(readOnly = true)
    public List<MonthlyLogImportResponse> list(Long projectId, Authentication authentication) {
        ProjectAccess access = requireProject(projectId, authentication, false);
        return imports.findAllByProject_IdOrderByUploadedAtDesc(access.project().getId())
                .stream()
                .map(MonthlyLogImportResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonthlyLogReportResponse get(Long projectId, Long reportId, String query, String month, int page,
                                        int pageSize, Authentication authentication) {
        ProjectAccess access = requireProject(projectId, authentication, false);
        MonthlyLogImportEntity report = findReport(access.project().getId(), reportId);
        return report(report, query, parseMonth(month), page, pageSize);
    }

    @Transactional
    public void delete(Long projectId, Long reportId, Authentication authentication) {
        ProjectAccess access = requireProject(projectId, authentication, true);
        imports.delete(findReport(access.project().getId(), reportId));
    }

    private MonthlyLogReportResponse report(MonthlyLogImportEntity report, String query, YearMonth month,
                                             int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw badRequest("Choose a valid page and a page size between 1 and 100.");
        }
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (search.length() > 100) throw badRequest("Search text must be 100 characters or fewer.");

        List<StoredMonthlyLogRow> storedRows = rows.findAllByReport_IdOrderBySourceRowAsc(report.getId())
                .stream()
                .map(row -> new StoredMonthlyLogRow(row, readRow(row)))
                .toList();
        List<MonthlyLogData> summaryRows = storedRows.stream()
                .map(StoredMonthlyLogRow::data)
                .filter(row -> matchesMonth(row, month))
                .toList();
        List<StoredMonthlyLogRow> filtered = storedRows.stream()
                .filter(entry -> search.isEmpty() || entry.row().getSearchText().contains(search))
                .filter(entry -> matchesMonth(entry.data(), month))
                .toList();
        long offset = (long) page * pageSize;
        List<MonthlyLogRowResponse> pageRows = filtered.stream()
                .skip(offset)
                .limit(pageSize)
                .map(entry -> rowResponse(entry.row(), entry.data()))
                .toList();

        return new MonthlyLogReportResponse(
                MonthlyLogImportResponse.from(report),
                calculator.calculate(summaryRows, resolutionSlas(report.getProject().getId())),
                pageRows,
                page,
                pageSize,
                filtered.size()
        );
    }

    private Map<String, ProjectSlaEntity> resolutionSlas(Long projectId) {
        Map<Long, ProjectSlaEntity> byPriorityId = new HashMap<>();
        for (ProjectSlaEntity sla : slas.findByProjectId(projectId)) {
            byPriorityId.put(sla.getPriorityId(), sla);
        }
        Map<String, ProjectSlaEntity> byPriorityName = new HashMap<>();
        for (TicketPriorityEntity priority : priorities.findByProjectIdOrderByLevel(projectId)) {
            ProjectSlaEntity sla = byPriorityId.get(priority.getId());
            if (sla != null) byPriorityName.put(normalize(priority.getName()), sla);
        }
        return byPriorityName;
    }

    private MonthlyLogData readRow(MonthlyLogRowEntity row) {
        try {
            return objectMapper.readValue(row.getDataJson(), MonthlyLogData.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "A stored workbook row could not be read.");
        }
    }

    private MonthlyLogRowResponse rowResponse(MonthlyLogRowEntity row, MonthlyLogData data) {
        return new MonthlyLogRowResponse(row.getSourceRow(), data.ticketId(), data.values());
    }

    static boolean matchesMonth(MonthlyLogData row, YearMonth month) {
        return month == null || row.createdDate() != null && YearMonth.from(row.createdDate()).equals(month);
    }

    private static YearMonth parseMonth(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            throw badRequest("Choose a valid month in YYYY-MM format.");
        }
    }

    private ProjectAccess requireProject(Long projectId, Authentication authentication, boolean write) {
        AppUser user = currentUserService.require(authentication, "projects.view");
        if (write && !PermissionCatalog.allows(user, "projects.edit")
                && !PermissionCatalog.allows(user, "reports.generate")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have permission to upload or delete project reports.");
        }
        ProjectEntity project = projects.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Project not found."));
        if (user.getRole() == UserRole.PROJECT_LEAD
                && (project.getLead() == null || !project.getLead().getId().equals(user.getId()))
                && project.getTeamLeaders().stream().noneMatch(leader -> leader.getId().equals(user.getId()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not have access to this project.");
        }
        return new ProjectAccess(project, user);
    }

    private MonthlyLogImportEntity findReport(Long projectId, Long reportId) {
        return imports.findByIdAndProject_Id(reportId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Monthly log report not found."));
    }

    private String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) return "monthly-log.xlsx";
        String normalized = originalName.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (name.isBlank()) return "monthly-log.xlsx";
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private ParsedUpload parseUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Choose a non-empty .xlsx workbook.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "The workbook exceeds the 10 MB upload limit.");
        }

        String fileName = safeFileName(file.getOriginalFilename());
        try (InputStream input = file.getInputStream()) {
            return new ParsedUpload(fileName, parser.parse(fileName, input));
        } catch (IOException exception) {
            throw badRequest("The workbook could not be read. Ensure it is a valid, unencrypted .xlsx file.");
        }
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private record ProjectAccess(ProjectEntity project, AppUser user) {
    }

    private record StoredMonthlyLogRow(MonthlyLogRowEntity row, MonthlyLogData data) {
    }

    private record ParsedUpload(String fileName, MonthlyLogWorkbookParser.ParsedMonthlyLog parsed) {
    }
}
