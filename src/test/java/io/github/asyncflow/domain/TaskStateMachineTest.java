package io.github.asyncflow.domain;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskStateMachineTest {
    private static final Set<String> VALID = Set.of(
            "CREATED->QUEUED", "CREATED->CANCELLED",
            "QUEUED->RUNNING", "QUEUED->CANCELLED",
            "RUNNING->SUCCEEDED", "RUNNING->RETRYING", "RUNNING->DEAD",
            "RETRYING->QUEUED", "RETRYING->RUNNING", "RETRYING->DEAD", "RETRYING->CANCELLED",
            "DEAD->QUEUED", "DEAD->COMPENSATING",
            "COMPENSATING->COMPENSATED", "COMPENSATING->DEAD"
    );

    @TestFactory
    Stream<DynamicTest> acceptsEveryDocumentedTransition() {
        return VALID.stream().sorted().map(value -> DynamicTest.dynamicTest("accepts " + value, () -> {
            String[] pair = value.split("->");
            assertThatCode(() -> TaskStateMachine.validate(TaskStatus.valueOf(pair[0]),
                    TaskStatus.valueOf(pair[1]))).doesNotThrowAnyException();
        }));
    }

    @TestFactory
    Stream<DynamicTest> rejectsRepresentativeIllegalTransitions() {
        List<String> invalid = new ArrayList<>();
        invalid.addAll(List.of(
                "CREATED->RUNNING", "CREATED->SUCCEEDED", "CREATED->DEAD",
                "QUEUED->SUCCEEDED", "QUEUED->DEAD", "QUEUED->COMPENSATED",
                "RUNNING->CREATED", "RUNNING->QUEUED", "RUNNING->CANCELLED",
                "RETRYING->CREATED", "RETRYING->SUCCEEDED", "RETRYING->COMPENSATED",
                "DEAD->RUNNING", "DEAD->SUCCEEDED", "DEAD->CANCELLED",
                "COMPENSATING->CREATED", "COMPENSATING->RUNNING", "COMPENSATING->CANCELLED",
                "SUCCEEDED->QUEUED", "SUCCEEDED->CANCELLED", "SUCCEEDED->DEAD",
                "CANCELLED->QUEUED", "CANCELLED->RUNNING", "CANCELLED->SUCCEEDED",
                "COMPENSATED->QUEUED", "COMPENSATED->RUNNING", "COMPENSATED->DEAD"
        ));
        return invalid.stream().map(value -> DynamicTest.dynamicTest("rejects " + value, () -> {
            String[] pair = value.split("->");
            assertThatThrownBy(() -> TaskStateMachine.validate(TaskStatus.valueOf(pair[0]),
                    TaskStatus.valueOf(pair[1])))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(value.replace("->", " -> "));
        }));
    }
}
