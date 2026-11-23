package com.sumasoft.tracking.imports;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads an uploaded .xlsx, .xls or .csv file into plain rows (RawRow). It does NOT check the values;
 * that is ImportRowParser's job.
 *  - The heading row is searched for in the first 10 rows (title rows above it are fine).
 *  - In a workbook you may name the sheet to read (for example "Nov26"); when no name is given,
 *    the first sheet that has the required headings is used.
 *  - Rows without Ticket ID, Date of Creation AND Status are empty template rows and are skipped silently.
 *  - Real Excel dates in the 4 date/time columns are passed as "@D:yyyy-MM-ddTHH:mm:ss".
 *  - Formulas are NOT recalculated: the value Excel saved is used ("#VALUE!" when Excel shows an error).
 *  - At most 20 000 data rows.
 */
public final class SpreadsheetReader {

    public static final int MAX_ROWS = 20_000;
    private static final int HEADING_SEARCH_ROWS = 10;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final Set<String> DATE_COLUMNS = Set.of(
            ImportColumns.CREATED_DATE, ImportColumns.CREATED_TIME, ImportColumns.CLOSED_DATE, ImportColumns.CLOSED_TIME);
    private static final Set<String> NUMBER_COLUMNS = Set.of(ImportColumns.TICKET_ID, ImportColumns.SR_NO);

    /** What was read: the sheet used, the data rows, and file-level warnings (row 0). */
    public record ReadResult(String sheetName, List<RawRow> rows, List<ImportIssue> fileIssues) {
    }

    private SpreadsheetReader() {
    }

    public static ReadResult read(String fileName, byte[] content) {
        return read(fileName, content, null);
    }

    /** sheetName: which sheet of a workbook to read (capital letters and spaces ignored); null = automatic. */
    public static ReadResult read(String fileName, byte[] content, String sheetName) {
        if (content == null || content.length == 0) {
            throw new ImportFileException("The file is empty");
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            return fromGrid(null, readCsv(content));
        }
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            return readWorkbook(content, sheetName);
        }
        throw new ImportFileException("Only .xlsx, .xls and .csv files can be imported");
    }

    // ================================================================ Excel

    private static ReadResult readWorkbook(byte[] content, String sheetName) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            DataFormatter formatter = new DataFormatter(Locale.ENGLISH);
            if (sheetName != null && !sheetName.isBlank()) {
                String wanted = sheetKey(sheetName);
                List<String> names = new ArrayList<>();
                for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                    Sheet sheet = wb.getSheetAt(s);
                    names.add(sheet.getSheetName());
                    if (sheetKey(sheet.getSheetName()).equals(wanted)) {
                        return fromSheet(sheet, formatter);
                    }
                }
                String list = String.join(", ", names);
                throw new ImportFileException("Sheet '" + sheetName.strip() + "' was not found. Sheets in this file: "
                        + (list.length() <= 350 ? list : list.substring(0, 350) + " ..."));
            }
            ImportFileException firstProblem = null;
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                try {
                    return fromSheet(sheet, formatter);
                } catch (ImportFileException e) {
                    if (firstProblem == null) {
                        firstProblem = e;
                    }
                }
            }
            throw firstProblem != null ? firstProblem : new ImportFileException("The workbook has no sheets");
        } catch (EncryptedDocumentException e) {
            throw new ImportFileException("The file is password-protected. Remove the password and upload it again");
        } catch (ImportFileException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ImportFileException("The file could not be read as an Excel workbook. "
                    + "Tip: download/save only this month's sheet as CSV and upload that file instead");
        }
    }

    private static ReadResult fromSheet(Sheet sheet, DataFormatter formatter) {
        // 1) find the heading row
        int last = sheet.getLastRowNum();
        int headingIndex = -1;
        Map<Integer, String> columns = null;
        List<String> unknown = null;
        for (int r = 0; r <= Math.min(last, HEADING_SEARCH_ROWS - 1); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            List<String> texts = new ArrayList<>();
            for (int c = 0; c < Math.max(row.getLastCellNum(), 0); c++) {
                Cell cell = row.getCell(c);
                texts.add(cell == null ? null : displayText(cell, formatter));
            }
            Heading h = Heading.of(texts);
            if (h.hasRequired()) {
                headingIndex = r;
                columns = h.columns;
                unknown = h.unknown;
                break;
            }
        }
        if (headingIndex < 0) {
            throw missingHeadings();
        }
        if (last - headingIndex > MAX_ROWS + 1000) {     // quick stop for huge sheets (empty rows are skipped below)
            throw tooManyRows();
        }

        // 2) data rows
        List<RawRow> rows = new ArrayList<>();
        for (int r = headingIndex + 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Map<String, String> values = new HashMap<>();
            boolean empty = true;
            for (Map.Entry<Integer, String> col : columns.entrySet()) {
                Cell cell = row.getCell(col.getKey());
                String v = cell == null ? null : cellValue(cell, col.getValue(), formatter);
                if (v != null && !v.isBlank()) {
                    values.put(col.getValue(), v);
                    empty = false;
                }
            }
            if (empty || isTemplateRow(values)) {
                continue;
            }
            if (rows.size() >= MAX_ROWS) {
                throw tooManyRows();
            }
            rows.add(new RawRow(r + 1, values));          // +1: Excel shows rows starting at 1
        }
        return new ReadResult(sheet.getSheetName(), rows, unknownColumnWarnings(unknown));
    }

    /** The value of a data cell, as text. */
    private static String cellValue(Cell cell, String column, DataFormatter formatter) {
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();      // value saved by Excel; we never recalculate
        }
        switch (type) {
            case NUMERIC: {
                double v = cell.getNumericCellValue();
                CellStyle style = cell.getCellStyle();
                if (DATE_COLUMNS.contains(column) && style != null
                        && DateUtil.isADateFormat(style.getDataFormat(), style.getDataFormatString())
                        && DateUtil.isValidExcelDate(v)) {
                    LocalDateTime dt = DateUtil.getLocalDateTime(v, false, true);
                    return "@D:" + dt.format(ISO);
                }
                if (NUMBER_COLUMNS.contains(column) && v == Math.rint(v) && Math.abs(v) < 1e15) {
                    return Long.toString((long) v);
                }
                return displayNumber(v, style, formatter);
            }
            case STRING:
                return cell.getStringCellValue();
            case BOOLEAN:
                return cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            case ERROR:
                return errorText(cell.getErrorCellValue());
            default:
                return null;
        }
    }

    /** Heading cells: just the text people see. */
    private static String displayText(Cell cell, DataFormatter formatter) {
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> displayNumber(cell.getNumericCellValue(), cell.getCellStyle(), formatter);
            case BOOLEAN -> cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            default -> null;
        };
    }

    /** A number as Excel would show it (so "1:23:45" stays "1:23:45", and a "dddd" date shows "Friday"). */
    private static String displayNumber(double v, CellStyle style, DataFormatter formatter) {
        if (style == null) {
            return formatter.formatRawCellContents(v, 0, "General");
        }
        return formatter.formatRawCellContents(v, style.getDataFormat(), style.getDataFormatString());
    }

    private static String errorText(byte code) {
        try {
            return FormulaError.forInt(code).getString();
        } catch (RuntimeException e) {
            return "#ERROR";
        }
    }

    // ================================================================ CSV

    /** Splits a CSV file (comma or semicolon, "quoted" values allowed) into rows of cells. */
    static List<List<String>> readCsv(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);                       // Excel's "UTF-8 with BOM"
        }
        String firstLine = text.lines().findFirst().orElse("");
        char sep = count(firstLine, ';') > count(firstLine, ',') ? ';' : ',';

        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"' && cell.length() == 0) {
                quoted = true;
            } else if (ch == sep) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                if (rows.size() > MAX_ROWS + HEADING_SEARCH_ROWS + 1000) {
                    throw tooManyRows();
                }
            } else {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    /** CSV rows -> RawRows, with the same heading search as Excel. */
    static ReadResult fromGrid(String sheetName, List<List<String>> grid) {
        int headingIndex = -1;
        Heading heading = null;
        for (int r = 0; r < Math.min(grid.size(), HEADING_SEARCH_ROWS); r++) {
            Heading h = Heading.of(grid.get(r));
            if (h.hasRequired()) {
                headingIndex = r;
                heading = h;
                break;
            }
        }
        if (heading == null) {
            throw missingHeadings();
        }
        List<RawRow> rows = new ArrayList<>();
        for (int r = headingIndex + 1; r < grid.size(); r++) {
            List<String> cells = grid.get(r);
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<Integer, String> col : heading.columns.entrySet()) {
                if (col.getKey() < cells.size()) {
                    String v = cells.get(col.getKey());
                    if (v != null && !v.isBlank()) {
                        values.put(col.getValue(), v);
                    }
                }
            }
            if (values.isEmpty() || isTemplateRow(values)) {
                continue;
            }
            if (rows.size() >= MAX_ROWS) {
                throw tooManyRows();
            }
            rows.add(new RawRow(r + 1, values));
        }
        return new ReadResult(sheetName, rows, unknownColumnWarnings(heading.unknown));
    }

    // ================================================================ headings

    private static final class Heading {
        final Map<Integer, String> columns = new LinkedHashMap<>();   // cell position -> our column name
        final List<String> unknown = new ArrayList<>();

        static Heading of(List<String> cells) {
            Heading h = new Heading();
            for (int c = 0; c < cells.size(); c++) {
                String text = cells.get(c);
                if (text == null || text.isBlank()) {
                    continue;
                }
                String col = ImportColumns.recognise(text);
                if (col == null) {
                    h.unknown.add(text.strip());
                } else if (h.columns.containsValue(col)) {
                    h.unknown.add(text.strip() + " (second '" + col + "' column, ignored)");
                } else {
                    h.columns.put(c, col);
                }
            }
            return h;
        }

        boolean hasRequired() {
            return Arrays.stream(ImportColumns.REQUIRED).allMatch(columns::containsValue);
        }
    }

    /** A row with only Sr.no / formulas filled in but no Ticket ID, Date of Creation and Status. */
    private static boolean isTemplateRow(Map<String, String> values) {
        return isBlank(values.get(ImportColumns.TICKET_ID))
                && isBlank(values.get(ImportColumns.CREATED_DATE))
                && isBlank(values.get(ImportColumns.STATUS));
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    /** "Nov 26" = "nov26" = "NOV26". */
    private static String sheetKey(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static List<ImportIssue> unknownColumnWarnings(List<String> unknown) {
        List<ImportIssue> issues = new ArrayList<>();
        for (String u : unknown) {
            issues.add(ImportIssue.warning(0, null, null, "Column not recognised; it was ignored", u));
        }
        return issues;
    }

    private static ImportFileException missingHeadings() {
        return new ImportFileException("Could not find the heading row. The first 10 rows must contain the headings "
                + String.join(", ", ImportColumns.REQUIRED));
    }

    private static ImportFileException tooManyRows() {
        return new ImportFileException("The file has more than " + MAX_ROWS + " rows. Split it into smaller files");
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }
}