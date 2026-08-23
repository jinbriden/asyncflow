package io.github.asyncflow.repository;

import io.github.asyncflow.domain.TaskEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskEventRepository extends JpaRepository<TaskEvent, Long> {
    List<TaskEvent> findByTaskIdOrderByOccurredAtAscIdAsc(String taskId);
}
