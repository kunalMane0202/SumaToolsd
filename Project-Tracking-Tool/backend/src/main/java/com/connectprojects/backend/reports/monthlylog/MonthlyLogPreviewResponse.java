package com.connectprojects.backend.reports.monthlylog;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record MonthlyLogPreviewResponse(
        String fileName,
        String sheetName,
        LocalDate periodStart,
        LocalDate periodEnd,
        int rowCount,
        List<String> columns,
        Map<String, String> columnMappings,
        List<String> warnings,
        MonthlyLogSummaryResponse summary,
        List<MonthlyLogRowResponse> rows
) {
}
