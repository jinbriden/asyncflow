package io.github.asyncflow.api;

import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.repository.TaskRepository;
import io.github.asyncflow.repository.TaskEventRepository;
import io.github.asyncflow.service.RecoveryService;
import io.github.asyncflow.service.TaskCancellationService;
import io.github.asyncflow.service.TaskSubmissionService;
import io.github.asyncflow.report.ReportQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskSubmissionService submissions;
    private final RecoveryService recovery;
    private final TaskRepository tasks;
    private final TaskEventRepository events;
    private final TaskCancellationService cancellation;
    private final ReportQueryService reports;

    public TaskController(TaskSubmissionService submissions, RecoveryService recovery,
                          TaskRepository tasks, TaskEventRepository events,
                          TaskCancellationService cancellation,
                          ReportQueryService reports) {
        this.submissions = submissions;
        this.recovery = recovery;
        this.tasks = tasks;
        this.events = events;
        this.cancellation = cancellation;
        this.reports = reports;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> submit(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody CreateTaskRequest request) {
        TaskResponse response = submissions.submit(idempotencyKey, request);
        if (response.deduplicated()) return ResponseEntity.ok(response);
        return ResponseEntity.accepted()
                .location(URI.create("/api/tasks/" + response.taskId()))
                .body(response);
    }

    @GetMapping("/{taskId}")
    public TaskResponse get(@PathVariable String taskId) {
        return TaskResponse.from(submissions.get(taskId),
                false,
                reports.find(taskId).orElse(null));
    }

    @GetMapping("/{taskId}/result")
    public ResponseEntity<Resource> download(@PathVariable String taskId) {
        ReportQueryService.ReportDownload download = reports.download(taskId);
        ContentDisposition disposition = ContentDisposition.attachment().filename(download.fileName()).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }

    @PostMapping("/{taskId}/cancel")
    public TaskResponse cancel(@PathVariable String taskId) {
        return TaskResponse.from(cancellation.cancel(taskId), false);
    }

    @GetMapping("/{taskId}/events")
    public List<TaskEventResponse> events(@PathVariable String taskId) {
        submissions.get(taskId);
        return events.findByTaskIdOrderByOccurredAtAscIdAsc(taskId).stream().map(TaskEventResponse::from).toList();
    }

    @GetMapping
    public Page<TaskResponse> list(@RequestParam(required = false) TaskStatus status,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100));
        Page<TaskRecord> result = status == null ? tasks.findAll(pageable) : tasks.findByStatus(status, pageable);
        return result.map(task -> TaskResponse.from(task, false));
    }

    @PostMapping("/{taskId}/compensate")
    public TaskResponse compensate(@PathVariable String taskId) {
        return TaskResponse.from(recovery.compensate(taskId), false);
    }
}
