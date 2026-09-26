package io.github.asyncflow.api;

import io.github.asyncflow.service.RecoveryService;
import io.github.asyncflow.service.UnauthorizedException;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/internal/tasks")
public class InternalTaskController {
    private final RecoveryService recovery;
    private final String internalToken;

    public InternalTaskController(RecoveryService recovery,
                                  @Value("${asyncflow.internal-token:change-me}") String internalToken) {
        this.recovery = recovery;
        this.internalToken = internalToken;
    }

    @PostMapping("/{taskId}/retry")
    public TaskResponse retry(@PathVariable String taskId,
                              @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        authorize(token);
        return TaskResponse.from(recovery.replay(taskId), false);
    }

    @PostMapping("/{taskId}/compensate")
    public TaskResponse compensate(@PathVariable String taskId,
                                   @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        authorize(token);
        return TaskResponse.from(recovery.compensate(taskId), false);
    }

    private void authorize(String token) {
        if (!internalToken.equals(token)) throw new UnauthorizedException("Invalid internal API token");
    }
}
