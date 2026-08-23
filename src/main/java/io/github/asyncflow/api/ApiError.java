package io.github.asyncflow.api;

import java.time.Instant;
import java.util.Map;

public record ApiError(String code, String message, Instant timestamp, Map<String, String> details) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Instant.now(), Map.of());
    }
}
