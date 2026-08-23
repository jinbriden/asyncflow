package io.github.asyncflow.report;

public class InvalidReportPayloadException extends IllegalArgumentException {
    public InvalidReportPayloadException(String message) {
        super(message);
    }

    public InvalidReportPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
