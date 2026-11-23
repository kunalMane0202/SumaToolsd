package com.connectprojects.backend.reports.monthlylog;

import java.util.List;

public record MonthlyLogReportResponse(
        MonthlyLogImportResponse report,
        MonthlyLogSummaryResponse summary,
        List<MonthlyLogRowResponse> rows,
        int page,
        int pageSize,
        long totalRows
) {
}
