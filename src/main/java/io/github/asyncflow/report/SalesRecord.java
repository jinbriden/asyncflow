package io.github.asyncflow.report;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SalesRecord(
        @NotBlank @Size(min = 1, max = 64)
        @Schema(example = "SO-1001", minLength = 1, requiredMode = Schema.RequiredMode.REQUIRED) String orderId,
        @NotBlank @Size(min = 1, max = 64)
        @Schema(example = "East", minLength = 1, requiredMode = Schema.RequiredMode.REQUIRED) String region,
        @NotBlank @Size(min = 1, max = 100)
        @Schema(example = "Keyboard", minLength = 1, requiredMode = Schema.RequiredMode.REQUIRED) String product,
        @Min(1)
        @Schema(example = "2", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED) int quantity,
        @NotNull @DecimalMin(value = "0", inclusive = false)
        @Schema(example = "199.50", minimum = "0", exclusiveMinimum = true,
                requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal unitPrice
) {
}
