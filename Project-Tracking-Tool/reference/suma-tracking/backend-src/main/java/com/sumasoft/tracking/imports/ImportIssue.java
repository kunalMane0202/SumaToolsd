package com.sumasoft.tracking.imports;

/**
 * One problem found in an imported file.
 * ERROR   = the row is NOT imported.
 * WARNING = the row is imported, but someone should look at it.
 * rowNumber 0 = a problem with the whole file (for example an unknown column).
 */
public record ImportIssue(
        int rowNumber,
        String externalTicketId,
        String severity,
        String columnName,
        String message,
        String rawValue
) {

    public static ImportIssue error(int row, String ticketId, String column, String message, String raw) {
        return new ImportIssue(row, ticketId, "ERROR", column, cut(message, 500), cut(raw, 500));
    }

    public static ImportIssue warning(int row, String ticketId, String column, String message, String raw) {
        return new ImportIssue(row, ticketId, "WARNING", column, cut(message, 500), cut(raw, 500));
    }

    public boolean isError() {
        return "ERROR".equals(severity);
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}