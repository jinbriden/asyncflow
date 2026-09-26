package io.github.asyncflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.asyncflow.report.ReportTaskPayload;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @NotBlank @Size(max = 64)
        @Schema(description = "Task type. The frontend contract currently supports REPORT only.",
                allowableValues = "REPORT", example = "REPORT") String type,
        @NotNull
        @Schema(description = "Typed payload for a regional sales report.",
                implementation = ReportTaskPayload.class,
                requiredMode = Schema.RequiredMode.REQUIRED) JsonNode payload,
        @Min(1) @Max(10)
        @Schema(description = "Maximum processing attempts.", defaultValue = "3", minimum = "1", maximum = "10")
        Integer maxAttempts,
        @Min(0) @Max(10)
        @Schema(hidden = true) Integer simulateFailures
) {
    public int resolvedMaxAttempts() { return maxAttempts == null ? 3 : maxAttempts; }
    public int resolvedSimulateFailures() { return simulateFailures == null ? 0 : simulateFailures; }
}
