package com.sumasoft.tracking.imports;

import java.util.Map;

/**
 * One data row as read from the file, BEFORE checking.
 * rowNumber = row number in the file as people see it (heading row = 1 when it is the first row).
 * values = column name (see ImportColumns) -> cell text.
 * A cell that Excel stores as a real date/time is given as "@D:yyyy-MM-ddTHH:mm:ss".
 */
public record RawRow(int rowNumber, Map<String, String> values) {

    public String get(String column) {
        String v = values.get(column);
        if (v == null) {
            return null;
        }
        String t = v.strip();
        return t.isEmpty() ? null : t;
    }
}