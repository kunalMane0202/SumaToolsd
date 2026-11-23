package com.sumasoft.tracking.imports;

import java.time.Instant;
import java.util.List;

/**
 * What the import web addresses send back.
 */
public final class ImportDtos {

    private ImportDtos() {
    }

    /** One import (or check-only run) of one file. issues is empty in lists. */
    public record ImportBatchResponse(
            Long id,
            Long projectId,
            String mode,
            String status,
            String sourceSystem,
            String fileName,
            String fileSha256,
            long fileSizeBytes,
            String sheetName,
            Long uploadedByUserId,
            Instant startedAt,
            Instant finishedAt,
            int totalRows,
            int createdCount,
            int updatedCount,
            int unchangedCount,
            int errorCount,
            int warningCount,
            String failureMessage,
            List<IssueResponse> issues
    ) {
        public ImportBatchResponse withIssues(List<IssueResponse> list) {
            return new ImportBatchResponse(id, projectId, mode, status, sourceSystem, fileName, fileSha256,
                    fileSizeBytes, sheetName, uploadedByUserId, startedAt, finishedAt, totalRows, createdCount,
                    updatedCount, unchangedCount, errorCount, warningCount, failureMessage, list);
        }
    }

    /** One problem found in the file. row 0 = the whole file. */
    public record IssueResponse(
            int row,
            String ticketId,
            String severity,
            String column,
            String message,
            String value
    ) {
    }
}