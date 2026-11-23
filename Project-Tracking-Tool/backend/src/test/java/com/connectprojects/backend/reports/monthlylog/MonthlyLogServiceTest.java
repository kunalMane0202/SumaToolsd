package com.connectprojects.backend.reports.monthlylog;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonthlyLogServiceTest {
    @Test
    void matchesRowsByCreationMonthAndLeavesAllMonthsUnfiltered() {
        YearMonth october = YearMonth.of(2026, 10);

        assertTrue(MonthlyLogService.matchesMonth(row(LocalDate.of(2026, 10, 31)), october));
        assertFalse(MonthlyLogService.matchesMonth(row(LocalDate.of(2026, 9, 30)), october));
        assertFalse(MonthlyLogService.matchesMonth(row(null), october));
        assertTrue(MonthlyLogService.matchesMonth(row(null), null));
    }

    private static MonthlyLogData row(LocalDate createdDate) {
        return new MonthlyLogData(
                1, "T-1", createdDate, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, Map.of()
        );
    }
}
