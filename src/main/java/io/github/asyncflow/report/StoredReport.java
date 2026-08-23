package io.github.asyncflow.report;

public record StoredReport(String fileName, String storageKey, long sizeBytes, String sha256) {
}
