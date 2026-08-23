package io.github.asyncflow.report;

import io.github.asyncflow.domain.TaskRecord;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

@Service
public class SalesReportGenerator {
    private final ReportPayloadParser parser;
    private final ReportStorage storage;
    private final ReportResultRepository results;

    public SalesReportGenerator(ReportPayloadParser parser, ReportStorage storage, ReportResultRepository results) {
        this.parser = parser;
        this.storage = storage;
        this.results = results;
    }

    public ReportResult generate(TaskRecord task) {
        ReportResult existing = results.findById(task.getTaskId()).orElse(null);
        if (existing != null) return existing;

        ReportTaskPayload payload = parser.parse(task.getPayload());
        Map<String, RegionSummary> summaries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        long totalQuantity = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (SalesRecord record : payload.records()) {
            BigDecimal amount = record.unitPrice().multiply(BigDecimal.valueOf(record.quantity()));
            summaries.computeIfAbsent(record.region().trim(), ignored -> new RegionSummary()).add(record, amount);
            totalQuantity += record.quantity();
            totalAmount = totalAmount.add(amount);
        }
        totalAmount = money(totalAmount);

        byte[] csv = renderCsv(summaries, payload.records().size(), totalQuantity, totalAmount)
                .getBytes(StandardCharsets.UTF_8);
        StoredReport stored = storage.store(task.getTaskId(), payload.reportName(), csv);
        return results.save(ReportResult.create(task.getTaskId(), payload, stored, summaries.size(),
                totalQuantity, totalAmount));
    }

    private String renderCsv(Map<String, RegionSummary> summaries, int orders,
                             long totalQuantity, BigDecimal totalAmount) {
        StringBuilder csv = new StringBuilder("region,order_count,total_quantity,total_amount\r\n");
        summaries.forEach((region, summary) -> csv.append(escape(region)).append(',')
                .append(summary.orderCount).append(',')
                .append(summary.totalQuantity).append(',')
                .append(money(summary.totalAmount).toPlainString()).append("\r\n"));
        csv.append("TOTAL,").append(orders).append(',').append(totalQuantity).append(',')
                .append(totalAmount.toPlainString()).append("\r\n");
        return csv.toString();
    }

    private String escape(String value) {
        if (!value.contains(",") && !value.contains("\"") && !value.contains("\n") && !value.contains("\r")) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class RegionSummary {
        private int orderCount;
        private long totalQuantity;
        private BigDecimal totalAmount = BigDecimal.ZERO;

        void add(SalesRecord record, BigDecimal amount) {
            orderCount++;
            totalQuantity += record.quantity();
            totalAmount = totalAmount.add(amount);
        }
    }
}
