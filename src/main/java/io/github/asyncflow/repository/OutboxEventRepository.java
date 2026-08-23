package io.github.asyncflow.repository;

import io.github.asyncflow.domain.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}
