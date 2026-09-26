package io.github.asyncflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.asyncflow.api.CreateTaskRequest;
import io.github.asyncflow.api.TaskResponse;
import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.idempotency.IdempotencyStore;
import io.github.asyncflow.repository.TaskRepository;
import io.github.asyncflow.report.ReportPayloadParser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
public class TaskSubmissionService {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private final TaskRepository tasks;
    private final IdempotencyStore idempotency;
    private final ObjectMapper objectMapper;
    private final ReportPayloadParser reportPayloadParser;
    private final TaskCreationTransaction creation;
    private final Counter submitted;
    private final Counter deduplicated;

    public TaskSubmissionService(TaskRepository tasks, IdempotencyStore idempotency,
                                 ObjectMapper objectMapper, MeterRegistry meterRegistry,
                                 ReportPayloadParser reportPayloadParser,
                                 TaskCreationTransaction creation) {
        this.tasks = tasks;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
        this.reportPayloadParser = reportPayloadParser;
        this.creation = creation;
        this.submitted = meterRegistry.counter("asyncflow.tasks.submitted");
        this.deduplicated = meterRegistry.counter("asyncflow.tasks.deduplicated");
    }

    public TaskResponse submit(String key, CreateTaskRequest request) {
        if ("REPORT".equalsIgnoreCase(request.type())) {
            reportPayloadParser.parse(request.payload());
        }
        TaskRecord existing = findExisting(key);
        if (existing != null) return duplicate(existing);

        String payload = serialize(request);
        TaskRecord task = TaskRecord.create(key, request.type(), payload,
                request.resolvedMaxAttempts(), request.resolvedSimulateFailures());
        if (!idempotency.reserve(key, task.getTaskId(), IDEMPOTENCY_TTL)) {
            TaskRecord raced = waitForExisting(key);
            if (raced != null) return duplicate(raced);
            throw new IllegalStateException("Idempotency key is being processed; retry shortly");
        }

        try {
            creation.create(task, payload);
            submitted.increment();
            return TaskResponse.from(task, false);
        } catch (DataIntegrityViolationException ex) {
            releaseReservation(key, task.getTaskId(), ex);
            try {
                TaskRecord raced = findExisting(key);
                if (raced != null) return duplicate(raced);
            } catch (RuntimeException lookupFailure) {
                addSuppressed(ex, lookupFailure);
            }
            throw ex;
        } catch (RuntimeException ex) {
            releaseReservation(key, task.getTaskId(), ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public TaskRecord get(String taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    }

    private TaskRecord findExisting(String key) {
        return idempotency.getTaskId(key).flatMap(tasks::findById)
                .or(() -> tasks.findByIdempotencyKey(key)).orElse(null);
    }

    private TaskRecord waitForExisting(String key) {
        for (int i = 0; i < 60; i++) {
            TaskRecord existing = findExisting(key);
            if (existing != null) return existing;
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while resolving idempotent request", ex);
            }
        }
        return null;
    }

    private void releaseReservation(String key, String taskId, RuntimeException original) {
        try {
            idempotency.release(key, taskId);
        } catch (RuntimeException cleanupFailure) {
            addSuppressed(original, cleanupFailure);
        }
    }

    private void addSuppressed(RuntimeException original, RuntimeException secondary) {
        if (secondary != original) original.addSuppressed(secondary);
    }

    private TaskResponse duplicate(TaskRecord task) {
        deduplicated.increment();
        return TaskResponse.from(task, true);
    }

    private String serialize(CreateTaskRequest request) {
        try {
            return objectMapper.writeValueAsString(request.payload());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload cannot be serialized", e);
        }
    }
}
