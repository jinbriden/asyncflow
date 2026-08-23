package io.github.asyncflow.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "report_result")
public class ReportResult {
    @Id
    @Column(name = "task_id", length = 36, nullable = false)
    private String taskId;

    @Column(name = "report_name", length = 100, nullable = false)
    private String reportName;

    @Column(name = "requested_by", length = 100, nullable = false)
    private String requestedBy;

    @Column(name = "file_name", length = 180, nullable = false)
    private String fileName;

    @Column(name = "storage_key", length = 255, nullable = false)
    private String storageKey;

    @Column(name = "content_type", length = 64, nullable = false)
    private String contentType;

    @Column(name = "source_record_count", nullable = false)
    private int sourceRecordCount;

    @Column(name = "summary_row_count", nullable = false)
    private int summaryRowCount;

    @Column(name = "total_quantity", nullable = false)
    private long totalQuantity;

    @Column(name = "total_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", length = 64, nullable = false)
    private String sha256;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected ReportResult() {
    }

    public static ReportResult create(String taskId, ReportTaskPayload payload, StoredReport stored,
                                      int summaryRowCount, long totalQuantity, BigDecimal totalAmount) {
        ReportResult result = new ReportResult();
        result.taskId = taskId;
        result.reportName = payload.reportName().trim();
        result.requestedBy = payload.requestedBy().trim();
        result.fileName = stored.fileName();
        result.storageKey = stored.storageKey();
        result.contentType = "text/csv";
        result.sourceRecordCount = payload.records().size();
        result.summaryRowCount = summaryRowCount;
        result.totalQuantity = totalQuantity;
        result.totalAmount = totalAmount;
        result.sizeBytes = stored.sizeBytes();
        result.sha256 = stored.sha256();
        result.generatedAt = Instant.now();
        return result;
    }

    public String getTaskId() { return taskId; }
    public String getReportName() { return reportName; }
    public String getRequestedBy() { return requestedBy; }
    public String getFileName() { return fileName; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public int getSourceRecordCount() { return sourceRecordCount; }
    public int getSummaryRowCount() { return summaryRowCount; }
    public long getTotalQuantity() { return totalQuantity; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public Instant getGeneratedAt() { return generatedAt; }
}
