package com.sumasoft.tracking.imports;

import com.sumasoft.tracking.audit.AuditService;
import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthSupport;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import com.sumasoft.tracking.imports.ImportDtos.ImportBatchResponse;
import com.sumasoft.tracking.imports.ImportDtos.IssueResponse;
import com.sumasoft.tracking.project.Project;
import com.sumasoft.tracking.project.ProjectRepository;
import com.sumasoft.tracking.ticket.ImportedTicketValues;
import com.sumasoft.tracking.ticket.Ticket;
import com.sumasoft.tracking.ticket.TicketHistory;
import com.sumasoft.tracking.ticket.TicketHistoryRepository;
import com.sumasoft.tracking.ticket.TicketImportRepository;
import com.sumasoft.tracking.ticket.TicketPriority;
import com.sumasoft.tracking.ticket.TicketPriorityRepository;
import com.sumasoft.tracking.ticket.TicketRepository;
import com.sumasoft.tracking.ticket.TicketStatus;
import com.sumasoft.tracking.ticket.TicketStatusRepository;
import com.sumasoft.tracking.user.AppUser;
import com.sumasoft.tracking.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Imports tickets from an Excel / CSV file into one project (FRD 8: import, validation, error report,
 * duplicate detection, audit).
 *
 *  mode DRY_RUN = "check only": the file is read and checked, the report is saved, NO ticket is changed.
 *  mode IMPORT  = the same checks, then new tickets are created and changed tickets are updated.
 *  sheet        = which sheet of the workbook to read (for example "Nov26"); empty = the first sheet with headings.
 *  The agents keep updating the monthly sheet, so the same sheet can be uploaded again and again:
 *  new tickets are created, changed tickets are updated, the rest is "unchanged".
 *
 * Rules:
 *  - Who: Super Admin, Client Admin of the project's client, or the Project Manager of the project.
 *  - A ticket is the same ticket when project + source system + Ticket ID match (no duplicates).
 *    Importing the same file twice is safe: the second time everything is "unchanged".
 *  - A row with an ERROR is skipped; WARNING rows are imported (see ImportRowParser for the checks).
 *  - The same Ticket ID twice in one file: the later row is an ERROR (the first one wins).
 *  - Assignee / Closed By names are linked to an employee only when they match exactly one ACTIVE
 *    member of the project team (full name, or first name when only one member has it).
 *    Otherwise the name is kept as text and a warning is given once per name.
 *  - Every changed field goes into the ticket history (source IMPORT); one audit record per file.
 *  - Three separate database steps: (1) the batch row is saved as RUNNING, (2) all the work,
 *    (3) only if step 2 fails: the batch is marked FAILED. So a failed import leaves no half-saved tickets.
 */
@Service
public class TicketImportService {

    private static final int MAX_STORED_ISSUES = 5000;
    private static final int LOOKUP_CHUNK = 1000;

    private final ProjectRepository projectRepository;
    private final TicketRepository ticketRepository;
    private final TicketImportRepository ticketImportRepository;
    private final TicketHistoryRepository historyRepository;
    private final TicketStatusRepository statusRepository;
    private final TicketPriorityRepository priorityRepository;
    private final UserRepository userRepository;
    private final AccessService access;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public TicketImportService(ProjectRepository projectRepository, TicketRepository ticketRepository,
                               TicketImportRepository ticketImportRepository, TicketHistoryRepository historyRepository,
                               TicketStatusRepository statusRepository, TicketPriorityRepository priorityRepository,
                               UserRepository userRepository, AccessService access, AuditService audit,
                               DataSource dataSource, PlatformTransactionManager transactionManager) {
        this.projectRepository = projectRepository;
        this.ticketRepository = ticketRepository;
        this.ticketImportRepository = ticketImportRepository;
        this.historyRepository = historyRepository;
        this.statusRepository = statusRepository;
        this.priorityRepository = priorityRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.audit = audit;
        this.jdbc = new JdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ================================================================ upload

    public ImportBatchResponse importTickets(Long projectId, String modeText, String sheetName, MultipartFile file) {
        AuthenticatedUser me = access.current();
        String mode = modeText == null ? "DRY_RUN" : modeText.trim().toUpperCase(Locale.ROOT);
        if (!mode.equals("DRY_RUN") && !mode.equals("IMPORT")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode must be DRY_RUN or IMPORT");
        }
        ProjectInfo project = tx.execute(s -> loadProjectForImport(me, projectId));

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose a file to upload");
        }
        if (file.getSize() > ImportUploadConfig.MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is bigger than 25 MB");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file could not be read");
        }
        String fileName = cleanFileName(file.getOriginalFilename());
        String sha = sha256(content);
        String sheet = (sheetName == null || sheetName.isBlank()) ? null : sheetName.strip();
        if (sheet != null && sheet.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The sheet name is too long");
        }

        // Step 1: remember that this upload started (stays even if the work fails)
        Long batchId = tx.execute(s -> jdbc.queryForObject(
                "INSERT INTO import_batches (project_id, mode, status, source_system, file_name, file_sha256, "
                        + "file_size_bytes, sheet_name, uploaded_by_user_id) VALUES (?, ?, 'RUNNING', ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, project.id(), mode, project.sourceSystem(), fileName, sha, (long) content.length, sheet,
                me.userId()));

        try {
            // Step 2: all the work in one transaction
            tx.executeWithoutResult(s -> runImport(me, project, mode, batchId, fileName, sheet, sha, content));
        } catch (ImportFileException e) {
            markFailed(project, mode, batchId, e.getMessage());
        } catch (RuntimeException e) {
            markFailed(project, mode, batchId, "Unexpected problem while importing. Nothing was changed. Please tell the administrator");
            throw e;
        }
        return get(batchId);
    }

    /** Step 2. Everything here is saved together, or not at all. */
    private void runImport(AuthenticatedUser me, ProjectInfo project, String mode, Long batchId,
                           String fileName, String sheetName, String sha, byte[] content) {
        boolean apply = mode.equals("IMPORT");
        Instant now = Instant.now();

        SpreadsheetReader.ReadResult sheet = SpreadsheetReader.read(fileName, content, sheetName);
        List<ImportIssue> issues = new ArrayList<>(sheet.fileIssues());

        // The same file was already imported for this project? (Harmless, but good to know.)
        List<Long> sameFile = jdbc.queryForList(
                "SELECT id FROM import_batches WHERE project_id = ? AND file_sha256 = ? AND mode = 'IMPORT' "
                        + "AND status = 'COMPLETED' AND id <> ? AND sheet_name IS NOT DISTINCT FROM ? ORDER BY id",
                Long.class, project.id(), sha, batchId, sheet.sheetName());
        if (!sameFile.isEmpty()) {
            issues.add(ImportIssue.warning(0, null, null, "This exact file was already imported (import number "
                    + sameFile.get(sameFile.size() - 1) + "). Unchanged tickets are simply skipped", fileName));
        }

        // What the checker needs to know about this project
        Map<String, TicketStatus> statuses = new HashMap<>();
        Map<String, String> statusGroups = new HashMap<>();
        for (TicketStatus st : statusRepository.findAll()) {
            statuses.put(st.getCode(), st);
            statusGroups.put(st.getCode(), st.getStatusGroup());
        }
        Map<String, TicketPriority> priorities = new HashMap<>();
        for (TicketPriority p : priorityRepository.findAll()) {
            priorities.put(p.getCode(), p);
        }
        Map<String, String> statusWords = new HashMap<>();
        Map<String, String> priorityWords = new HashMap<>();
        jdbc.query("SELECT field, lower(btrim(source_value)) AS word, target_code FROM ticket_value_mappings "
                + "WHERE project_id = ?", (RowCallbackHandler) rs -> {
            if ("STATUS".equals(rs.getString("field"))) {
                statusWords.put(rs.getString("word"), rs.getString("target_code"));
            } else {
                priorityWords.put(rs.getString("word"), rs.getString("target_code"));
            }
        }, project.id());
        ImportRowParser parser = new ImportRowParser(new ImportRowParser.Context(
                project.zone(), statusWords, statusGroups, priorityWords, priorities.keySet()));

        // Check every row
        List<ImportRowParser.Result> good = new ArrayList<>();
        Map<String, Integer> firstRowOfId = new HashMap<>();
        int errorRows = 0;
        for (RawRow raw : sheet.rows()) {
            ImportRowParser.Result r = parser.parse(raw);
            issues.addAll(r.issues());
            if (r.hasError()) {
                errorRows++;
                continue;
            }
            ImportedTicketValues v = r.values();
            if (statuses.get(v.statusCode()) == null) {
                issues.add(ImportIssue.error(r.rowNumber(), v.externalTicketId(), ImportColumns.STATUS,
                        "The status mapping points to '" + v.statusCode() + "', which does not exist", v.statusCode()));
                errorRows++;
                continue;
            }
            Integer first = firstRowOfId.putIfAbsent(v.externalTicketId(), r.rowNumber());
            if (first != null) {
                issues.add(ImportIssue.error(r.rowNumber(), v.externalTicketId(), ImportColumns.TICKET_ID,
                        "Same Ticket ID as row " + first + "; this row was skipped", v.externalTicketId()));
                errorRows++;
                continue;
            }
            good.add(r);
        }

        // Which tickets do we already have?
        Map<String, Ticket> existing = new HashMap<>();
        List<String> ids = new ArrayList<>(firstRowOfId.keySet());
        for (int i = 0; i < ids.size(); i += LOOKUP_CHUNK) {
            List<String> chunk = ids.subList(i, Math.min(ids.size(), i + LOOKUP_CHUNK));
            for (Ticket t : ticketImportRepository.findByProjectIdAndSourceSystemAndExternalTicketIdIn(
                    project.id(), project.sourceSystem(), chunk)) {
                existing.put(t.getExternalTicketId(), t);
            }
        }

        // Names -> employees of this project team
        TeamNames team = loadTeam(project.id());
        Set<String> warnedNames = new HashSet<>();

        AppUser actor = userRepository.getReferenceById(me.userId());
        Project projectEntity = projectRepository.findById(project.id()).orElseThrow();
        String note = "Import number " + batchId + " (" + fileName + ")";
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (ImportRowParser.Result r : good) {
            ImportedTicketValues v = r.values();
            TicketStatus status = statuses.get(v.statusCode());
            TicketPriority priority = v.priorityCode() == null ? null : priorities.get(v.priorityCode());
            AppUser assignee = team.find(v.assigneeName(), ImportColumns.ASSIGNEE, warnedNames, issues);
            AppUser closedBy = team.find(v.closedByName(), ImportColumns.CLOSED_BY, warnedNames, issues);

            Ticket ticket = existing.get(v.externalTicketId());
            if (ticket == null) {
                created++;
                if (apply) {
                    Ticket t = ticketRepository.save(Ticket.createImported(projectEntity, project.sourceSystem(),
                            v, status, priority, assignee, closedBy, batchId));
                    historyRepository.save(TicketHistory.of(t, "IMPORTED", null, null, status.getCode(),
                            actor, "IMPORT", "Created by " + note, now));
                }
                continue;
            }
            List<Ticket.FieldChange> changes = ticket.importChanges(v, status, priority, assignee, closedBy, batchId, apply);
            if (changes.isEmpty()) {
                unchanged++;
                continue;
            }
            updated++;
            if (apply) {
                ticketRepository.save(ticket);
                for (Ticket.FieldChange c : changes) {
                    historyRepository.save(TicketHistory.of(ticket, "IMPORTED", c.field(), c.oldValue(),
                            c.newValue(), actor, "IMPORT", note, now));
                }
            }
        }
        if (apply) {
            ticketRepository.flush();
        }

        // Save the report and the totals
        int warnings = (int) issues.stream().filter(i -> !i.isError()).count();
        List<ImportIssue> stored = issues.size() <= MAX_STORED_ISSUES ? issues : issues.subList(0, MAX_STORED_ISSUES);
        jdbc.batchUpdate("INSERT INTO import_row_issues (batch_id, row_number, external_ticket_id, severity, "
                        + "column_name, message, raw_value) VALUES (?, ?, ?, ?, ?, ?, ?)", stored, 500,
                (ps, i) -> {
                    ps.setLong(1, batchId);
                    ps.setInt(2, i.rowNumber());
                    ps.setString(3, AuthSupport.clip(i.externalTicketId(), 100));
                    ps.setString(4, i.severity());
                    ps.setString(5, AuthSupport.clip(i.columnName(), 60));
                    ps.setString(6, i.message());
                    ps.setString(7, i.rawValue());
                });
        String extra = issues.size() > MAX_STORED_ISSUES
                ? "Only the first " + MAX_STORED_ISSUES + " of " + issues.size() + " issues were saved" : null;
        jdbc.update("UPDATE import_batches SET status = 'COMPLETED', finished_at = now(), sheet_name = ?, total_rows = ?, "
                        + "created_count = ?, updated_count = ?, unchanged_count = ?, error_count = ?, warning_count = ?, "
                        + "failure_message = ? WHERE id = ?",
                AuthSupport.clip(sheet.sheetName(), 100), sheet.rows().size(), created, updated, unchanged,
                errorRows, warnings, extra, batchId);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("mode", mode);
        summary.put("fileName", fileName);
        summary.put("fileSha256", sha);
        summary.put("totalRows", sheet.rows().size());
        summary.put("created", created);
        summary.put("updated", updated);
        summary.put("unchanged", unchanged);
        summary.put("errorRows", errorRows);
        summary.put("warnings", warnings);
        audit.recordWithSource(apply ? "TICKETS_IMPORTED" : "TICKET_IMPORT_CHECKED", "IMPORT_BATCH", batchId,
                project.clientId(), project.id(), null, summary, "IMPORT", "import " + batchId);
    }

    /** Step 3: the work failed, so nothing was saved; mark the batch FAILED with a clear reason. */
    private void markFailed(ProjectInfo project, String mode, Long batchId, String message) {
        tx.executeWithoutResult(s -> {
            jdbc.update("UPDATE import_batches SET status = 'FAILED', finished_at = now(), failure_message = ? "
                    + "WHERE id = ?", AuthSupport.clip(message, 500), batchId);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("mode", mode);
            summary.put("failure", message);
            audit.recordWithSource("TICKET_IMPORT_FAILED", "IMPORT_BATCH", batchId,
                    project.clientId(), project.id(), null, summary, "IMPORT", "import " + batchId);
        });
    }

    // ================================================================ reading the reports

    public List<ImportBatchResponse> list(Long projectId) {
        AuthenticatedUser me = access.current();
        return tx.execute(s -> {
            if (projectId != null) {
                requireCanSeeImports(me, projectId);
                return jdbc.query(BATCH_SELECT + " WHERE project_id = ? ORDER BY id DESC LIMIT 200",
                        this::mapBatch, projectId);
            }
            if (!access.seesEverything(me)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please give a projectId");
            }
            return jdbc.query(BATCH_SELECT + " ORDER BY id DESC LIMIT 200", this::mapBatch);
        });
    }

    public ImportBatchResponse get(Long batchId) {
        AuthenticatedUser me = access.current();
        return tx.execute(s -> {
            List<ImportBatchResponse> found = jdbc.query(BATCH_SELECT + " WHERE id = ?", this::mapBatch, batchId);
            if (found.isEmpty()) {
                throw AccessService.notFound("Import");
            }
            ImportBatchResponse batch = found.get(0);
            requireCanSeeImports(me, batch.projectId());
            List<IssueResponse> issues = jdbc.query("SELECT row_number, external_ticket_id, severity, column_name, "
                            + "message, raw_value FROM import_row_issues WHERE batch_id = ? ORDER BY row_number, id",
                    (rs, n) -> new IssueResponse(rs.getInt("row_number"), rs.getString("external_ticket_id"),
                            rs.getString("severity"), rs.getString("column_name"), rs.getString("message"),
                            rs.getString("raw_value")), batchId);
            return batch.withIssues(issues);
        });
    }

    private static final String BATCH_SELECT = "SELECT id, project_id, mode, status, source_system, file_name, "
            + "file_sha256, file_size_bytes, sheet_name, uploaded_by_user_id, started_at, finished_at, total_rows, "
            + "created_count, updated_count, unchanged_count, error_count, warning_count, failure_message "
            + "FROM import_batches";

    private ImportBatchResponse mapBatch(ResultSet rs, int n) throws SQLException {
        OffsetDateTime started = rs.getObject("started_at", OffsetDateTime.class);
        OffsetDateTime finished = rs.getObject("finished_at", OffsetDateTime.class);
        return new ImportBatchResponse(
                rs.getLong("id"), rs.getLong("project_id"), rs.getString("mode"), rs.getString("status"),
                rs.getString("source_system"), rs.getString("file_name"), rs.getString("file_sha256"),
                rs.getLong("file_size_bytes"), rs.getString("sheet_name"), rs.getLong("uploaded_by_user_id"),
                started == null ? null : started.toInstant(), finished == null ? null : finished.toInstant(),
                rs.getInt("total_rows"), rs.getInt("created_count"), rs.getInt("updated_count"),
                rs.getInt("unchanged_count"), rs.getInt("error_count"), rs.getInt("warning_count"),
                rs.getString("failure_message"), List.of());
    }

    // ================================================================ permissions and helpers

    /** The project details the import needs (read once, used across the 3 steps). */
    private record ProjectInfo(Long id, Long clientId, ZoneId zone, String sourceSystem) {
    }

    private ProjectInfo loadProjectForImport(AuthenticatedUser me, Long projectId) {
        if (projectId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "projectId is required");
        }
        Project project = projectRepository.findById(projectId)
                .filter(p -> access.canSeeProject(me, p.getId()))
                .orElseThrow(() -> AccessService.notFound("Project"));
        if (!access.canManageProject(me, project)) {
            throw AccessService.forbidden();
        }
        if (!"ACTIVE".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tickets can only be imported into an ACTIVE project");
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(project.getTimeZone());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The project's time zone is not valid");
        }
        return new ProjectInfo(project.getId(), project.getClient().getId(), zone,
                sourceSystemOf(project.getExternalTicketSystem()));
    }

    /** Import reports may be read by people who may import into the project, and by Management Viewers. */
    private void requireCanSeeImports(AuthenticatedUser me, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .filter(p -> access.canSeeProject(me, p.getId()))
                .orElseThrow(() -> AccessService.notFound("Import"));
        if (!access.seesEverything(me) && !access.canManageProject(me, project)) {
            throw AccessService.forbidden();
        }
    }

    /** "Zendesk" -> ZENDESK. A project without an external system gets EXCEL_IMPORT. */
    static String sourceSystemOf(String externalSystem) {
        if (externalSystem == null || externalSystem.isBlank()) {
            return "EXCEL_IMPORT";
        }
        String code = externalSystem.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (code.isEmpty() || !Character.isLetter(code.charAt(0))) {
            return "EXCEL_IMPORT";
        }
        return code.length() <= 30 ? code : code.substring(0, 30);
    }

    private static String cleanFileName(String original) {
        if (original == null || original.isBlank()) {
            return "upload";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            return "upload";
        }
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Active members of the project team, for linking names in the file to employees. */
    private TeamNames loadTeam(Long projectId) {
        Map<String, List<Long>> byFullName = new HashMap<>();
        Map<String, List<Long>> byFirstName = new HashMap<>();
        jdbc.query("SELECT u.id, u.full_name FROM project_assignments pa JOIN users u ON u.id = pa.user_id "
                + "WHERE pa.project_id = ? AND pa.active = TRUE AND u.status = 'ACTIVE'", (RowCallbackHandler) rs -> {
            long id = rs.getLong("id");
            String full = normalise(rs.getString("full_name"));
            byFullName.computeIfAbsent(full, k -> new ArrayList<>()).add(id);
            byFirstName.computeIfAbsent(full.split(" ")[0], k -> new ArrayList<>()).add(id);
        }, projectId);
        return new TeamNames(byFullName, byFirstName, userRepository);
    }

    private static String normalise(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private record TeamNames(Map<String, List<Long>> byFullName, Map<String, List<Long>> byFirstName,
                             UserRepository users) {

        AppUser find(String name, String column, Set<String> warned, List<ImportIssue> issues) {
            if (name == null) {
                return null;
            }
            String key = normalise(name);
            List<Long> ids = byFullName.get(key);
            if (ids == null || ids.isEmpty()) {
                ids = key.contains(" ") ? null : byFirstName.get(key);
            }
            if (ids != null && ids.size() == 1) {
                return users.getReferenceById(ids.get(0));
            }
            if (warned.add(column + "|" + key)) {
                String why = (ids != null && ids.size() > 1)
                        ? "matches more than one team member"
                        : "does not match any active member of the project team";
                issues.add(ImportIssue.warning(0, null, column,
                        "Name '" + name + "' " + why + "; kept as text only", name));
            }
            return null;
        }
    }
}