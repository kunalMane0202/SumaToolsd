package com.connectprojects.backend.reports.monthlylog;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonthlyLogWorkbookParserTest {
    private final MonthlyLogWorkbookParser parser = new MonthlyLogWorkbookParser();

    @Test
    void detectsCommonHeaderAliasesAndReturnsPreviewColumns() throws IOException {
        byte[] workbook = workbook(
                new String[]{"Ticket Number", "Created Date", "Assigned To", "Ticket Status"},
                new String[]{"T-14", "2026-10-12", "Alex Smith", "Resolved"}
        );

        MonthlyLogWorkbookParser.ParsedMonthlyLog parsed = parser.parse(
                "tickets.xlsx", new ByteArrayInputStream(workbook));

        assertEquals(List.of("Ticket Number", "Created Date", "Assigned To", "Ticket Status"), parsed.columns());
        assertEquals("Ticket Number", parsed.columnMappings().get("Ticket ID"));
        assertEquals("Created Date", parsed.columnMappings().get("Date of Creation"));
        assertEquals("T-14", parsed.rows().get(0).ticketId());
        assertEquals(LocalDate.of(2026, 10, 12), parsed.periodStart());
        assertTrue(parsed.warnings().isEmpty());
    }

    @Test
    void reportsRowsWithMissingDatesOrStatusesDuringPreview() throws IOException {
        byte[] workbook = workbook(
                new String[]{"Ticket ID", "Date of Creation", "Assignee", "Status"},
                new String[]{"T-14", "2026-10-12", "Alex Smith", "Resolved"},
                new String[]{"T-15", "", "Jamie Lee", ""}
        );

        MonthlyLogWorkbookParser.ParsedMonthlyLog parsed = parser.parse(
                "tickets.xlsx", new ByteArrayInputStream(workbook));

        assertEquals(2, parsed.rows().size());
        assertEquals(2, parsed.warnings().size());
        assertTrue(parsed.warnings().get(0).contains("Row 3"));
        assertTrue(parsed.warnings().get(1).contains("Row 3"));
    }

    @Test
    void acceptsBlankTicketIdsAndExcludesThemFromUniqueTicketCount() throws IOException {
        byte[] workbook = workbook(
                new String[]{"Ticket ID", "Date of Creation", "Assignee", "Status"},
                new String[]{"", "2026-10-12", "Alex Smith", "Resolved"},
                new String[]{"T-15", "2026-10-13", "Jamie Lee", "Open"}
        );

        MonthlyLogWorkbookParser.ParsedMonthlyLog parsed = parser.parse(
                "tickets.xlsx", new ByteArrayInputStream(workbook));
        MonthlyLogSummaryResponse summary = new MonthlyLogSummaryCalculator().calculate(parsed.rows(), Map.of());

        assertEquals("", parsed.rows().get(0).ticketId());
        assertEquals(2, summary.totalTickets());
        assertEquals(1, summary.uniqueTickets());
    }

    @Test
    void acceptsTicketTablesWithoutATicketIdColumn() throws IOException {
        byte[] workbook = workbook(
                new String[]{"Date of Creation", "Assignee", "Status"},
                new String[]{"2026-10-12", "Alex Smith", "Resolved"}
        );

        MonthlyLogWorkbookParser.ParsedMonthlyLog parsed = parser.parse(
                "tickets.xlsx", new ByteArrayInputStream(workbook));

        assertEquals("", parsed.rows().get(0).ticketId());
        assertEquals(LocalDate.of(2026, 10, 12), parsed.periodStart());
        assertNull(parsed.columnMappings().get("Ticket ID"));
    }

    @Test
    void parsesExampleLogHeadersAndSummarizesSatisfactionWithoutOpenTicketDurations() throws IOException {
        String[] headers = {
                "Sr.no", "Ticket ID", "Date of Creation", "Timeframe", "Time of Creation", "Assignee", "Day",
                "Escalated ( Yes / No )", "Date of Closure", "Time of Closure", "Resolution Time (hh:mm:ss)",
                "Closed By", "Status", "Satisfaction Rating", "Priority", "Source", "Issue Type", "Plan",
                "Topic", "Category", "Subcategory", "Requester", "Requester Role"
        };
        byte[] workbook = workbook(
                headers,
                new String[]{"1", "T-1", "10/31/2026", "11-12pm", "11:24:41 PM", "Agent A", "Saturday", "No",
                        "10/31/2026", "11:30:56 PM", "0:06:15", "Agent A", "Solved", "Good, I'm satisfied",
                        "Normal", "Chat", "Question", "N/A", "Screen", "After the test", "Scoring",
                        "Requester A", "Candidate"},
                new String[]{"2", "T-2", "11/1/2026", "2-3 pm", "2:02:12 PM", "Agent B", "Sunday", "No",
                        "0:00:00", "0:00:00", "0:00:00", "", "Pending", "", "Normal", "Email", "Question",
                        "N/A", "Technical Issue", "", "", "Requester B", "Candidate"}
        );

        MonthlyLogWorkbookParser.ParsedMonthlyLog parsed = parser.parse(
                "tickets.xlsx", new ByteArrayInputStream(workbook));
        MonthlyLogSummaryResponse summary = new MonthlyLogSummaryCalculator().calculate(parsed.rows(), Map.of());

        assertEquals("Escalated ( Yes / No )", parsed.columnMappings().get("Escalated"));
        assertEquals("Resolution Time (hh:mm:ss)", parsed.columnMappings().get("Resolution Time"));
        assertEquals("Good, I'm satisfied", parsed.rows().get(0).values().get("Satisfaction Rating"));
        assertEquals(0.1, summary.averageResolutionHours());
        assertEquals(List.of(new MonthlyLogBreakdown("Agent A", 1)), summary.byResolvedAssignee());
        assertEquals(List.of(
                new MonthlyLogBreakdown("Good, I'm satisfied", 1),
                new MonthlyLogBreakdown("Not recorded", 1)
        ), summary.bySatisfactionRating());
    }

    private static byte[] workbook(String[] headers, String[]... dataRows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Tickets");
            writeRow(sheet.createRow(0), headers);
            for (int index = 0; index < dataRows.length; index++) {
                writeRow(sheet.createRow(index + 1), dataRows[index]);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void writeRow(Row row, String[] values) {
        for (int index = 0; index < values.length; index++) {
            row.createCell(index).setCellValue(values[index]);
        }
    }
}
