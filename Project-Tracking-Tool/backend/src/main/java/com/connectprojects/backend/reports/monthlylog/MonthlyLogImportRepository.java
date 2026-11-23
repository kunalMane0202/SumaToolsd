package com.connectprojects.backend.reports.monthlylog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonthlyLogImportRepository extends JpaRepository<MonthlyLogImportEntity, Long> {
    List<MonthlyLogImportEntity> findAllByProject_IdOrderByUploadedAtDesc(Long projectId);
    Optional<MonthlyLogImportEntity> findByIdAndProject_Id(Long id, Long projectId);
}
