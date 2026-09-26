package io.github.asyncflow.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant timestamp,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, String> details) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Instant.now(), Map.of());
    }
}
