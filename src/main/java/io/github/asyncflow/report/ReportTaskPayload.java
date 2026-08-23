package io.github.asyncflow.report;

import java.util.List;

public record ReportTaskPayload(
        String reportName,
        String requestedBy,
        List<SalesRecord> records
) {
}
