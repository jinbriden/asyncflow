package io.github.asyncflow.framework.extension;

import io.github.asyncflow.framework.client.CallRecorder;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.nio.file.Files;
import java.nio.file.Path;

public class FailureEvidenceExtension implements TestWatcher {
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        String lastCall = CallRecorder.last();
        try {
            Path dir = Path.of("target", "test-evidence");
            Files.createDirectories(dir);
            String name = context.getRequiredTestClass().getSimpleName() + "-"
                    + context.getDisplayName().replaceAll("[^a-zA-Z0-9._-]", "_");
            Files.writeString(dir.resolve(name + ".txt"),
                    "test=" + context.getUniqueId() + System.lineSeparator()
                            + "error=" + cause + System.lineSeparator()
                            + "lastApiCall=" + lastCall + System.lineSeparator());
        } catch (Exception ignored) {
            // Evidence files are best-effort; the test failure itself is the signal.
        }
        System.err.println("TEST FAILURE EVIDENCE lastApiCall=" + lastCall);
        CallRecorder.clear();
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        CallRecorder.clear();
    }
}
