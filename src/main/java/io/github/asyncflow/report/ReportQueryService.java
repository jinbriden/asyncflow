package io.github.asyncflow.report;

import io.github.asyncflow.api.ReportResultResponse;
import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import io.github.asyncflow.service.TaskNotFoundException;
import io.github.asyncflow.repository.TaskRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ReportQueryService {
    private final TaskRepository tasks;
    private final ReportResultRepository results;
    private final ReportStorage storage;

    public ReportQueryService(TaskRepository tasks, ReportResultRepository results, ReportStorage storage) {
        this.tasks = tasks;
        this.results = results;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public Optional<ReportResultResponse> find(String taskId) {
        return results.findById(taskId).map(ReportResultResponse::from);
    }

    @Transactional(readOnly = true)
    public ReportDownload download(String taskId) {
        TaskRecord task = tasks.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
        if (task.getStatus() != TaskStatus.SUCCEEDED) {
            throw new ReportNotReadyException("Report is not ready; current task status is " + task.getStatus());
        }
        ReportResult result = results.findById(taskId)
                .orElseThrow(() -> new ReportResultNotFoundException("Report metadata is missing for task " + taskId));
        Resource resource = storage.load(result.getStorageKey());
        return new ReportDownload(result.getFileName(), result.getContentType(), result.getSizeBytes(), resource);
    }

    public record ReportDownload(String fileName, String contentType, long sizeBytes, Resource resource) {
    }
}
