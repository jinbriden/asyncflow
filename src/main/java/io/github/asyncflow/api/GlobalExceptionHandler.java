package io.github.asyncflow.api;

import io.github.asyncflow.service.TaskNotFoundException;
import io.github.asyncflow.service.UnauthorizedException;
import io.github.asyncflow.report.InvalidReportPayloadException;
import io.github.asyncflow.report.ReportNotReadyException;
import io.github.asyncflow.report.ReportResultNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(TaskNotFoundException.class)
    ResponseEntity<ApiError> notFound(TaskNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("TASK_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiError> unauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of("UNAUTHORIZED", ex.getMessage()));
    }

    @ExceptionHandler(InvalidReportPayloadException.class)
    ResponseEntity<ApiError> invalidReport(InvalidReportPayloadException ex) {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_REPORT_PAYLOAD", ex.getMessage()));
    }

    @ExceptionHandler(ReportNotReadyException.class)
    ResponseEntity<ApiError> reportNotReady(ReportNotReadyException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("REPORT_NOT_READY", ex.getMessage()));
    }

    @ExceptionHandler(ReportResultNotFoundException.class)
    ResponseEntity<ApiError> reportResultMissing(ReportResultNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("REPORT_RESULT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> conflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("INVALID_TASK_STATE", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> details.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", "Request validation failed",
                Instant.now(), details));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiError> missingRequestHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_FAILED", ex.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_FAILED", ex.getMessage()));
    }
}
