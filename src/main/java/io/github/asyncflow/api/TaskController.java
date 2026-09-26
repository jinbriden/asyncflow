package io.github.asyncflow.api;

import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.repository.TaskRepository;
import io.github.asyncflow.repository.TaskEventRepository;
import io.github.asyncflow.service.TaskCancellationService;
import io.github.asyncflow.service.TaskSubmissionService;
import io.github.asyncflow.service.UnauthorizedException;
import io.github.asyncflow.report.ReportQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
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
@Tag(name = "Report tasks", description = "Submit, track, cancel, and download asynchronous sales reports.")
public class TaskController {
    private final TaskSubmissionService submissions;
    private final TaskRepository tasks;
    private final TaskEventRepository events;
    private final TaskCancellationService cancellation;
    private final ReportQueryService reports;
    private final String internalToken;

    public TaskController(TaskSubmissionService submissions,
                          TaskRepository tasks, TaskEventRepository events,
                          TaskCancellationService cancellation,
                          ReportQueryService reports,
                          @Value("${asyncflow.internal-token:change-me}") String internalToken) {
        this.submissions = submissions;
        this.tasks = tasks;
        this.events = events;
        this.cancellation = cancellation;
        this.reports = reports;
        this.internalToken = internalToken;
    }

    @PostMapping
    @Operation(summary = "Submit a report task",
            description = "Returns 202 for a newly accepted task and 200 when the idempotency key resolves to an existing task.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Existing task returned for an idempotent retry",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "202", description = "New task accepted for asynchronous processing",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request validation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<TaskResponse> submit(
            @Parameter(description = "Client-generated key. Reuse it only when retrying the same logical submission.",
                    required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 1, max = 128) String idempotencyKey,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @Valid @RequestBody CreateTaskRequest request) {
        if (request.resolvedSimulateFailures() > 0 && !internalToken.equals(token)) {
            throw new UnauthorizedException("Failure injection requires a valid internal API token");
        }
        TaskResponse response = submissions.submit(idempotencyKey, request);
        if (response.deduplicated()) return ResponseEntity.ok(response);
        return ResponseEntity.accepted()
                .location(URI.create("/api/tasks/" + response.taskId()))
                .body(response);
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "Get report task details")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task details",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    public TaskResponse get(@PathVariable String taskId) {
        return TaskResponse.from(submissions.get(taskId),
                false,
                reports.find(taskId).orElse(null));
    }

    @GetMapping(value = "/{taskId}/result", produces = "text/csv")
    @Operation(summary = "Download a generated CSV report",
            description = "Available only after the task reaches SUCCEEDED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Generated CSV report",
                    content = @Content(mediaType = "text/csv",
                            schema = @Schema(type = "string", format = "binary"))),
            @ApiResponse(responseCode = "404", description = "Task or report result not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Report is not ready",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
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
    @Operation(summary = "Cancel an eligible report task",
            description = "Cancellation is accepted only from CREATED, QUEUED, or RETRYING.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task cancelled",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TaskResponse.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Task cannot be cancelled from its current state",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    public TaskResponse cancel(@PathVariable String taskId) {
        return TaskResponse.from(cancellation.cancel(taskId), false);
    }

    @GetMapping(value = "/{taskId}/events", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the ordered task processing timeline")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ordered task events",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = TaskEventResponse.class)))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class)))
    })
    public List<TaskEventResponse> events(@PathVariable String taskId) {
        submissions.get(taskId);
        return events.findByTaskIdOrderByOccurredAtAscIdAsc(taskId).stream().map(TaskEventResponse::from).toList();
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List report tasks")
    public Page<TaskResponse> list(
            @Parameter(description = "Optional exact task-status filter.")
            @RequestParam(required = false) TaskStatus status,
            @Parameter(description = "Zero-based page index.", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, clamped to the range 1..100.", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100));
        Page<TaskRecord> result = status == null ? tasks.findAll(pageable) : tasks.findByStatus(status, pageable);
        return result.map(task -> TaskResponse.from(task, false));
    }

}
