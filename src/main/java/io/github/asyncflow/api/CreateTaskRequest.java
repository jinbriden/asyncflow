package io.github.asyncflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @NotBlank @Size(max = 64) String type,
        @NotNull JsonNode payload,
        @Min(1) @Max(10) Integer maxAttempts,
        @Min(0) @Max(10) Integer simulateFailures
) {
    public int resolvedMaxAttempts() { return maxAttempts == null ? 3 : maxAttempts; }
    public int resolvedSimulateFailures() { return simulateFailures == null ? 0 : simulateFailures; }
}
