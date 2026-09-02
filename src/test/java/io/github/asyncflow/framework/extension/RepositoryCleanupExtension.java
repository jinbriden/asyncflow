package io.github.asyncflow.framework.extension;

import io.github.asyncflow.report.ReportResultRepository;
import io.github.asyncflow.repository.OutboxEventRepository;
import io.github.asyncflow.repository.TaskEventRepository;
import io.github.asyncflow.repository.TaskRepository;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public class RepositoryCleanupExtension implements BeforeEachCallback {
    @Override
    public void beforeEach(ExtensionContext context) {
        ApplicationContext ctx = SpringExtension.getApplicationContext(context);
        if (ctx.getBeanNamesForType(ReportResultRepository.class).length > 0) {
            ctx.getBean(ReportResultRepository.class).deleteAll();
        }
        ctx.getBean(TaskEventRepository.class).deleteAll();
        ctx.getBean(OutboxEventRepository.class).deleteAll();
        ctx.getBean(TaskRepository.class).deleteAll();
    }
}
