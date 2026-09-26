package io.github.asyncflow.report;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReportTaskPayload(
        @NotBlank @Size(min = 1, max = 100)
        @Schema(description = "Human-readable report name.", example = "regional-sales", minLength = 1,
                requiredMode = Schema.RequiredMode.REQUIRED) String reportName,
        @NotBlank @Size(min = 1, max = 100)
        @Schema(description = "Requester identifier for display and audit purposes.", example = "qa@example.com",
                minLength = 1, requiredMode = Schema.RequiredMode.REQUIRED)
        String requestedBy,
        @NotEmpty @Size(min = 1, max = 10_000)
        @ArraySchema(minItems = 1, maxItems = 10_000,
                schema = @Schema(implementation = SalesRecord.class),
                arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED)) List<SalesRecord> records
) {
}
