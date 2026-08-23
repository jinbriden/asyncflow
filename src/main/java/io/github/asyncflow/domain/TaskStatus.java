package io.github.asyncflow.domain;

public enum TaskStatus {
    CREATED,
    QUEUED,
    RUNNING,
    RETRYING,
    SUCCEEDED,
    DEAD,
    CANCELLED,
    COMPENSATING,
    COMPENSATED
}
