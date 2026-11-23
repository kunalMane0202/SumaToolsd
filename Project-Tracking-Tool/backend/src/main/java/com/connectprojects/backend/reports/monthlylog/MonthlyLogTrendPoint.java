package com.connectprojects.backend.reports.monthlylog;

import java.time.LocalDate;

public record MonthlyLogTrendPoint(LocalDate date, long created, long closed) {
}
