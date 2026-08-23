package io.github.asyncflow.api;

import io.github.asyncflow.report.ReportResult;

import java.math.BigDecimal;
import java.time.Instant;

public record ReportResultResponse(
        String reportName,
        String requestedBy,
        String fileName,
        String contentType,
        int sourceRecordCount,
        int summaryRowCount,
        long totalQuantity,
        BigDecimal totalAmount,
        long sizeBytes,
        String sha256,
        Instant generatedAt,
        String downloadUrl
) {
    public static ReportResultResponse from(ReportResult result) {
        return new ReportResultResponse(result.getReportName(), result.getRequestedBy(),
                result.getFileName(), result.getContentType(), result.getSourceRecordCount(),
                result.getSummaryRowCount(), result.getTotalQuantity(), result.getTotalAmount(),
                result.getSizeBytes(), result.getSha256(), result.getGeneratedAt(),
                "/api/tasks/" + result.getTaskId() + "/result");
    }
}
