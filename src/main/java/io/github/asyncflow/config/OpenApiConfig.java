package io.github.asyncflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI asyncFlowOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AsyncFlow Frontend API")
                .version("v1")
                .description("Public business contract for submitting and tracking asynchronous sales reports."));
    }

    @Bean
    public GroupedOpenApi frontendApi() {
        return GroupedOpenApi.builder()
                .group("frontend")
                .displayName("AsyncFlow Frontend API")
                .pathsToMatch("/api/tasks/**", "/api/tasks")
                .addOpenApiCustomizer(this::customizeFrontendContract)
                .build();
    }

    private void customizeFrontendContract(OpenAPI openApi) {
        openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> operation.getResponses().values().forEach(response ->
                        response.addHeaderObject("X-Trace-Id", new Header()
                                .description("Correlation identifier returned for support and test evidence.")
                                .schema(new StringSchema())))));

        Schema<?> unitPrice = (Schema<?>) openApi.getComponents().getSchemas().get("SalesRecord")
                .getProperties().get("unitPrice");
        unitPrice.setMinimum(BigDecimal.ZERO);
        unitPrice.setExclusiveMinimumValue(BigDecimal.ZERO);

        Schema<?> taskResponse = openApi.getComponents().getSchemas().get("TaskResponse");
        taskResponse.getProperties().put("result", nullableReference("#/components/schemas/ReportResultResponse"));

        Schema<?> taskEvent = openApi.getComponents().getSchemas().get("TaskEventResponse");
        Schema<?> generatedFromStatus = (Schema<?>) taskEvent.getProperties().get("fromStatus");
        StringSchema taskStatus = new StringSchema();
        taskStatus.setEnum(generatedFromStatus.getEnum().stream().map(String::valueOf).toList());
        taskEvent.getProperties().put("fromStatus", nullableSchema(taskStatus));
    }

    private ComposedSchema nullableReference(String reference) {
        Schema<Object> referenceSchema = new Schema<>();
        referenceSchema.set$ref(reference);
        return nullableSchema(referenceSchema);
    }

    private ComposedSchema nullableSchema(Schema<?> valueSchema) {
        Schema<Object> nullSchema = new Schema<>();
        nullSchema.setTypes(Set.of("null"));
        ComposedSchema nullable = new ComposedSchema();
        nullable.setAnyOf(List.of(valueSchema, nullSchema));
        return nullable;
    }
}
