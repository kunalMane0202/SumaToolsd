package com.connectprojects.backend.reports.monthlylog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "monthly_log_rows")
public class MonthlyLogRowEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_id", nullable = false)
    private MonthlyLogImportEntity report;

    @Column(name = "source_row", nullable = false)
    private int sourceRow;

    @Column(name = "search_text", nullable = false, columnDefinition = "TEXT")
    private String searchText;

    @Column(name = "data_json", nullable = false, columnDefinition = "TEXT")
    private String dataJson;

    protected MonthlyLogRowEntity() {
    }

    public MonthlyLogRowEntity(MonthlyLogImportEntity report, int sourceRow, String searchText, String dataJson) {
        this.report = report;
        this.sourceRow = sourceRow;
        this.searchText = searchText;
        this.dataJson = dataJson;
    }

    public Long getId() { return id; }
    public MonthlyLogImportEntity getReport() { return report; }
    public int getSourceRow() { return sourceRow; }
    public String getSearchText() { return searchText; }
    public String getDataJson() { return dataJson; }
}
