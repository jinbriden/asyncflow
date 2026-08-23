package io.github.asyncflow.report;

public class ReportNotReadyException extends RuntimeException {
    public ReportNotReadyException(String message) {
        super(message);
    }
}
