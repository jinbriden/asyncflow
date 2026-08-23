package io.github.asyncflow.report;

import java.math.BigDecimal;

public record SalesRecord(
        String orderId,
        String region,
        String product,
        int quantity,
        BigDecimal unitPrice
) {
}
