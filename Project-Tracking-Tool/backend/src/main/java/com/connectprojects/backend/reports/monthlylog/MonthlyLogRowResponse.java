package com.connectprojects.backend.reports.monthlylog;

import java.util.Map;

public record MonthlyLogRowResponse(int sourceRow, String ticketId, Map<String, String> values) {
}
