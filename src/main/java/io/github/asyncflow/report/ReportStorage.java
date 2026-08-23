package io.github.asyncflow.report;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class ReportStorage {
    private final Path root;

    public ReportStorage(@Value("${asyncflow.report.storage-directory:./data/reports}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public StoredReport store(String taskId, String reportName, byte[] content) {
        String storageKey = taskId + ".csv";
        Path target = resolve(storageKey);
        Path temporary = null;
        try {
            Files.createDirectories(root);
            temporary = Files.createTempFile(root, taskId + "-", ".tmp");
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredReport(downloadName(reportName), storageKey, content.length, sha256(content));
        } catch (IOException ex) {
            throw new IllegalStateException("Report file could not be stored", ex);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A failed best-effort cleanup must not hide the business failure.
                }
            }
        }
    }

    public Resource load(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new ReportResultNotFoundException("Generated report file is missing");
        }
        return new FileSystemResource(path);
    }

    private Path resolve(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid report storage key");
        }
        return resolved;
    }

    private String downloadName(String reportName) {
        String safe = reportName.trim().replaceAll("[^a-zA-Z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (safe.isBlank()) safe = "sales-report";
        if (safe.length() > 100) safe = safe.substring(0, 100);
        return safe + ".csv";
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
