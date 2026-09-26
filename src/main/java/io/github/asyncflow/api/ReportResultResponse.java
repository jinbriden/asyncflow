package io.github.asyncflow.api;

import io.github.asyncflow.report.ReportResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

public record ReportResultResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reportName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String requestedBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String fileName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String contentType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sourceRecordCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int summaryRowCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalQuantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal totalAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sizeBytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sha256,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant generatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String downloadUrl
) {
    public static ReportResultResponse from(ReportResult result) {
        return new ReportResultResponse(result.getReportName(), result.getRequestedBy(),
                result.getFileName(), result.getContentType(), result.getSourceRecordCount(),
                result.getSummaryRowCount(), result.getTotalQuantity(), result.getTotalAmount(),
                result.getSizeBytes(), result.getSha256(), result.getGeneratedAt(),
                "/api/tasks/" + result.getTaskId() + "/result");
    }
}
