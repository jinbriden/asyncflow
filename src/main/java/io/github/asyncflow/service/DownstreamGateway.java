package io.github.asyncflow.service;

import io.github.asyncflow.domain.TaskRecord;

public interface DownstreamGateway {
    void execute(TaskRecord task);
}
