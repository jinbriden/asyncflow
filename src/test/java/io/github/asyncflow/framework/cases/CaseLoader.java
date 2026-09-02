package io.github.asyncflow.framework.cases;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.asyncflow.framework.data.ReportTestDataFactory;
import org.junit.jupiter.params.provider.Arguments;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

public final class CaseLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CaseLoader() {
    }

    public static Stream<Arguments> submitValidation() throws Exception {
        try (InputStream in = CaseLoader.class.getResourceAsStream("/qa/cases/submit-validation.json")) {
            List<ValidationCase> cases = MAPPER.readValue(in, new TypeReference<>() {
            });
            return cases.stream().map(c -> Arguments.of(
                    c.name(),
                    invokeFactory(c.factoryMethod()),
                    c.status(),
                    c.code()));
        }
    }

    private static String invokeFactory(String factoryMethod) {
        try {
            Method method = ReportTestDataFactory.class.getMethod(factoryMethod);
            return (String) method.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unknown factory method: " + factoryMethod, e);
        }
    }

    public record ValidationCase(String name, String factoryMethod, int status, String code) {
    }
}
