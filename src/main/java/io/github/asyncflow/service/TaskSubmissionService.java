package io.github.asyncflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.asyncflow.api.CreateTaskRequest;
import io.github.asyncflow.api.TaskResponse;
import io.github.asyncflow.domain.OutboxEvent;
import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.idempotency.IdempotencyStore;
import io.github.asyncflow.repository.OutboxEventRepository;
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
    private final OutboxEventRepository outbox;
    private final IdempotencyStore idempotency;
    private final ObjectMapper objectMapper;
    private final TaskEventRecorder eventRecorder;
    private final ReportPayloadParser reportPayloadParser;
    private final Counter submitted;
    private final Counter deduplicated;

    public TaskSubmissionService(TaskRepository tasks, OutboxEventRepository outbox,
                                 IdempotencyStore idempotency, ObjectMapper objectMapper,
                                 MeterRegistry meterRegistry, TaskEventRecorder eventRecorder,
                                 ReportPayloadParser reportPayloadParser) {
        this.tasks = tasks;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
        this.eventRecorder = eventRecorder;
        this.reportPayloadParser = reportPayloadParser;
        this.submitted = meterRegistry.counter("asyncflow.tasks.submitted");
        this.deduplicated = meterRegistry.counter("asyncflow.tasks.deduplicated");
    }

    @Transactional
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
            tasks.saveAndFlush(task);
            eventRecorder.record(task, null, "API", "Task accepted");
            outbox.save(OutboxEvent.taskCreated(task.getTaskId(), payload));
            submitted.increment();
            return TaskResponse.from(task, false);
        } catch (DataIntegrityViolationException ex) {
            idempotency.release(key, task.getTaskId());
            TaskRecord raced = findExisting(key);
            if (raced != null) return duplicate(raced);
            throw ex;
        } catch (RuntimeException ex) {
            idempotency.release(key, task.getTaskId());
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
