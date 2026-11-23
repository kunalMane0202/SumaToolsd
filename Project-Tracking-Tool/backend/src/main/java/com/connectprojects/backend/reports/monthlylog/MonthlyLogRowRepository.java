package com.connectprojects.backend.reports.monthlylog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthlyLogRowRepository extends JpaRepository<MonthlyLogRowEntity, Long> {
    List<MonthlyLogRowEntity> findAllByReport_IdOrderBySourceRowAsc(Long reportId);
}
