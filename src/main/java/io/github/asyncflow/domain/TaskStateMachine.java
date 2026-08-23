package io.github.asyncflow.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class TaskStateMachine {
    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED = new EnumMap<>(TaskStatus.class);

    static {
        ALLOWED.put(TaskStatus.CREATED, EnumSet.of(TaskStatus.QUEUED, TaskStatus.CANCELLED));
        ALLOWED.put(TaskStatus.QUEUED, EnumSet.of(TaskStatus.RUNNING, TaskStatus.CANCELLED));
        ALLOWED.put(TaskStatus.RUNNING, EnumSet.of(TaskStatus.SUCCEEDED, TaskStatus.RETRYING,
                TaskStatus.DEAD));
        ALLOWED.put(TaskStatus.RETRYING, EnumSet.of(TaskStatus.QUEUED, TaskStatus.RUNNING,
                TaskStatus.DEAD, TaskStatus.CANCELLED));
        ALLOWED.put(TaskStatus.DEAD, EnumSet.of(TaskStatus.QUEUED, TaskStatus.COMPENSATING));
        ALLOWED.put(TaskStatus.COMPENSATING, EnumSet.of(TaskStatus.COMPENSATED, TaskStatus.DEAD));
        ALLOWED.put(TaskStatus.SUCCEEDED, EnumSet.noneOf(TaskStatus.class));
        ALLOWED.put(TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class));
        ALLOWED.put(TaskStatus.COMPENSATED, EnumSet.noneOf(TaskStatus.class));
    }

    private TaskStateMachine() {
    }

    public static void validate(TaskStatus current, TaskStatus next) {
        if (!ALLOWED.getOrDefault(current, Set.of()).contains(next)) {
            throw new IllegalStateException("Illegal task transition: " + current + " -> " + next);
        }
    }
}
