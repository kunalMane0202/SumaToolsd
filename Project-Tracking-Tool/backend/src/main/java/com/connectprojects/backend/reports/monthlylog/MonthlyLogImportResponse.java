package com.connectprojects.backend.reports.monthlylog;

import java.time.Instant;
import java.time.LocalDate;

public record MonthlyLogImportResponse(
        Long id,
        String fileName,
        String sheetName,
        LocalDate periodStart,
        LocalDate periodEnd,
        int rowCount,
        Instant uploadedAt,
        String uploadedBy
) {
    static MonthlyLogImportResponse from(MonthlyLogImportEntity report) {
        return new MonthlyLogImportResponse(
                report.getId(),
                report.getFileName(),
                report.getSheetName(),
                report.getPeriodStart(),
                report.getPeriodEnd(),
                report.getRowCount(),
                report.getUploadedAt(),
                report.getUploadedBy()
        );
    }
}
