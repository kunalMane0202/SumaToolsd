package com.connectprojects.backend.reports.monthlylog;

import com.connectprojects.backend.api.ApiException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class MonthlyLogWorkbookParser {
    private static final int HEADER_SCAN_ROWS = 30;
    private static final int MAX_COLUMNS = 64;
    private static final int MAX_ROWS = 10_000;
    private static final int MAX_WARNINGS = 50;
    private static final List<List<String>> REQUIRED_HEADER_ALIASES = List.of(
            List.of("dateofcreation", "creationdate", "createddate", "datecreated"),
            List.of("assignee", "assignedto"),
            List.of("status", "ticketstatus")
    );
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
            DateTimeFormatter.ofPattern("M/d/yy", Locale.US),
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.US),
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US)
    );

    public ParsedMonthlyLog parse(String fileName, InputStream input) throws IOException {
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw badRequest("Choose an .xlsx workbook.");
        }

        try (Workbook workbook = WorkbookFactory.create(input)) {
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            for (Sheet sheet : workbook) {
                for (int rowIndex = 0; rowIndex <= Math.min(sheet.getLastRowNum(), HEADER_SCAN_ROWS - 1); rowIndex++) {
                    Row headerRow = sheet.getRow(rowIndex);
                    HeaderLayout headers = readHeaders(headerRow, formatter);
                    if (headers != null) {
                        return readRows(sheet, rowIndex, headers, formatter);
                    }
                }
            }
        }

        throw badRequest("No ticket table found. The workbook must include Date of Creation, Assignee, and Status columns.");
    }

    private HeaderLayout readHeaders(Row row, DataFormatter formatter) {
        if (row == null || row.getLastCellNum() <= 0 || row.getLastCellNum() > MAX_COLUMNS) return null;

        Map<Integer, String> labels = new LinkedHashMap<>();
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int index = 0; index < row.getLastCellNum(); index++) {
            String label = cellText(row.getCell(index), formatter);
            if (label.isBlank()) continue;
            String normalized = normalize(label);
            if (columns.putIfAbsent(normalized, index) != null) {
                throw badRequest("The ticket table contains duplicate column names.");
            }
            labels.put(index, label);
        }
        if (REQUIRED_HEADER_ALIASES.stream()
                .anyMatch(aliases -> aliases.stream().noneMatch(columns::containsKey))) return null;
        return new HeaderLayout(labels, columns);
    }

    private ParsedMonthlyLog readRows(Sheet sheet, int headerIndex, HeaderLayout headers, DataFormatter formatter) {
        List<MonthlyLogData> rows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        LocalDate periodStart = null;
        LocalDate periodEnd = null;

        for (int rowIndex = headerIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;

            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> header : headers.labels().entrySet()) {
                String value = cellText(row.getCell(header.getKey()), formatter);
                if (!value.isBlank()) values.put(header.getValue(), value);
            }
            if (values.isEmpty()) continue;

            String ticketId = value(values, headers, "ticketid", "ticketnumber", "ticketno");

            LocalDate createdDate = dateValue(columnCell(row, headers,
                    "dateofcreation", "creationdate", "createddate", "datecreated"));
            LocalDate closedDate = dateValue(columnCell(row, headers, "dateofclosure", "closeddate"));
            if (createdDate == null) {
                addWarning(warnings, "Row " + (rowIndex + 1) + " has a blank or unsupported Date of Creation.");
            }
            if (value(values, headers, "status", "ticketstatus").isBlank()) {
                addWarning(warnings, "Row " + (rowIndex + 1) + " has no Status.");
            }
            if (createdDate != null) {
                if (periodStart == null || createdDate.isBefore(periodStart)) periodStart = createdDate;
                if (periodEnd == null || createdDate.isAfter(periodEnd)) periodEnd = createdDate;
            }

            rows.add(new MonthlyLogData(
                    rowIndex + 1,
                    ticketId,
                    createdDate,
                    closedDate,
                    value(values, headers, "assignee", "assignedto"),
                    value(values, headers, "closedby"),
                    value(values, headers, "status", "ticketstatus"),
                    value(values, headers, "priority"),
                    value(values, headers, "source"),
                    value(values, headers, "issuetype"),
                    value(values, headers, "plan"),
                    value(values, headers, "topic"),
                    value(values, headers, "category"),
                    value(values, headers, "subcategory"),
                    value(values, headers, "requester"),
                    value(values, headers, "requesterrole"),
                    booleanValue(columnCell(row, headers, "escalatedyesno", "escalated")),
                    durationSeconds(columnCell(row, headers, "resolutiontime")),
                    values
            ));

            if (rows.size() > MAX_ROWS) {
                throw badRequest("The workbook exceeds the 10,000 ticket row limit.");
            }
        }

        if (rows.isEmpty()) throw badRequest("The ticket table contains no ticket rows.");
        if (periodStart == null || periodEnd == null) {
            throw badRequest("The ticket rows do not contain usable Date of Creation values.");
        }
        if (warnings.size() == MAX_WARNINGS) warnings.add("Additional validation warnings were omitted.");
        return new ParsedMonthlyLog(
                sheet.getSheetName(),
                periodStart,
                periodEnd,
                List.copyOf(headers.labels().values()),
                columnMappings(headers),
                List.copyOf(warnings),
                List.copyOf(rows)
        );
    }

    private static Map<String, String> columnMappings(HeaderLayout headers) {
        Map<String, String> mappings = new LinkedHashMap<>();
        addMapping(mappings, headers, "Ticket ID", "ticketid", "ticketnumber", "ticketno");
        addMapping(mappings, headers, "Date of Creation", "dateofcreation", "creationdate", "createddate", "datecreated");
        addMapping(mappings, headers, "Assignee", "assignee", "assignedto");
        addMapping(mappings, headers, "Status", "status", "ticketstatus");
        addMapping(mappings, headers, "Date of Closure", "dateofclosure", "closeddate");
        addMapping(mappings, headers, "Closed By", "closedby");
        addMapping(mappings, headers, "Priority", "priority");
        addMapping(mappings, headers, "Source", "source");
        addMapping(mappings, headers, "Issue Type", "issuetype");
        addMapping(mappings, headers, "Plan", "plan");
        addMapping(mappings, headers, "Topic", "topic");
        addMapping(mappings, headers, "Category", "category");
        addMapping(mappings, headers, "Subcategory", "subcategory");
        addMapping(mappings, headers, "Requester", "requester");
        addMapping(mappings, headers, "Requester Role", "requesterrole");
        addMapping(mappings, headers, "Escalated", "escalatedyesno", "escalated");
        addMapping(mappings, headers, "Resolution Time", "resolutiontime");
        return Collections.unmodifiableMap(mappings);
    }

    private static void addMapping(Map<String, String> mappings, HeaderLayout headers, String field,
                                   String... aliases) {
        String header = headers.header(aliases);
        if (header != null) mappings.put(field, header);
    }

    private static void addWarning(List<String> warnings, String warning) {
        if (warnings.size() < MAX_WARNINGS) warnings.add(warning);
    }

    private static String value(Map<String, String> values, HeaderLayout headers, String... aliases) {
        int column = headers.column(aliases);
        if (column < 0) return "";
        String header = headers.labels().get(column);
        return values.getOrDefault(header, "");
    }

    private static Cell columnCell(Row row, HeaderLayout headers, String... aliases) {
        int index = headers.column(aliases);
        return index < 0 ? null : row.getCell(index);
    }

    private static String cellText(Cell cell, DataFormatter formatter) {
        return cell == null || cell.getCellType() == CellType.BLANK
                ? ""
                : formatter.formatCellValue(cell).trim();
    }

    private static LocalDate dateValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
            return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate();
        }

        String text = cell.toString().trim();
        if (text.isEmpty()) return null;
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, format);
            } catch (DateTimeParseException ignored) {
                // Try the next supported spreadsheet date format.
            }
        }
        return null;
    }

    private static Long durationSeconds(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            double days = cell.getNumericCellValue();
            if (!Double.isFinite(days) || days < 0 || days > 36_500) return null;
            return Math.round(days * 86_400);
        }

        String text = cell.toString().trim();
        if (text.isEmpty()) return null;
        String[] parts = text.split(":");
        if (parts.length == 3) {
            try {
                long hours = Long.parseLong(parts[0]);
                long minutes = Long.parseLong(parts[1]);
                double seconds = Double.parseDouble(parts[2]);
                if (hours < 0 || minutes < 0 || minutes >= 60 || seconds < 0 || seconds >= 60) return null;
                return Math.round(hours * 3_600 + minutes * 60 + seconds);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        try {
            double days = Double.parseDouble(text);
            if (!Double.isFinite(days) || days < 0 || days > 36_500) return null;
            return Math.round(days * 86_400);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Boolean booleanValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.toString().trim().toLowerCase(Locale.ROOT)) {
            case "yes", "y", "true", "1" -> true;
            case "no", "n", "false", "0" -> false;
            default -> null;
        };
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public record ParsedMonthlyLog(String sheetName, LocalDate periodStart, LocalDate periodEnd,
                                   List<String> columns, Map<String, String> columnMappings,
                                   List<String> warnings, List<MonthlyLogData> rows) {
    }

    private record HeaderLayout(Map<Integer, String> labels, Map<String, Integer> columns) {
        String header(String... aliases) {
            int index = column(aliases);
            return index < 0 ? null : labels.get(index);
        }

        int column(String... aliases) {
            for (String alias : aliases) {
                Integer match = columns.get(alias);
                if (match != null) return match;
            }
            for (Map.Entry<String, Integer> column : columns.entrySet()) {
                for (String alias : aliases) {
                    if (column.getKey().startsWith(alias)) return column.getValue();
                }
            }
            return -1;
        }
    }
}
