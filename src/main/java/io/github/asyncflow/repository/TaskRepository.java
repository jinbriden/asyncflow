package io.github.asyncflow.repository;

import io.github.asyncflow.domain.TaskRecord;
import io.github.asyncflow.domain.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.Instant;
import java.util.List;

public interface TaskRepository extends JpaRepository<TaskRecord, String> {
    Optional<TaskRecord> findByIdempotencyKey(String idempotencyKey);
    Page<TaskRecord> findByStatus(TaskStatus status, Pageable pageable);
    List<TaskRecord> findByStatusAndUpdatedAtBefore(TaskStatus status, Instant updatedAt, Pageable pageable);
}
