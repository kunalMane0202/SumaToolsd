package com.connectprojects.backend.reports.monthlylog;

import com.connectprojects.backend.work.ProjectEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "monthly_log_imports")
public class MonthlyLogImportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Column(name = "uploaded_by", nullable = false, length = 30)
    private String uploadedBy;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "sheet_name", nullable = false, length = 80)
    private String sheetName;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected MonthlyLogImportEntity() {
    }

    public MonthlyLogImportEntity(ProjectEntity project, String uploadedBy, String fileName, String sheetName,
                                  LocalDate periodStart, LocalDate periodEnd, int rowCount) {
        this.project = project;
        this.uploadedBy = uploadedBy;
        this.fileName = fileName;
        this.sheetName = sheetName;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.rowCount = rowCount;
    }

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) uploadedAt = Instant.now();
    }

    public Long getId() { return id; }
    public ProjectEntity getProject() { return project; }
    public String getUploadedBy() { return uploadedBy; }
    public String getFileName() { return fileName; }
    public String getSheetName() { return sheetName; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public int getRowCount() { return rowCount; }
    public Instant getUploadedAt() { return uploadedAt; }
}
