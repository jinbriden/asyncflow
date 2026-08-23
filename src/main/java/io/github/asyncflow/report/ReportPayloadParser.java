package io.github.asyncflow.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ReportPayloadParser {
    private static final int MAX_RECORDS = 10_000;
    private final ObjectMapper objectMapper;

    public ReportPayloadParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ReportTaskPayload parse(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new InvalidReportPayloadException("Report payload is required");
        }
        try {
            return validate(objectMapper.treeToValue(payload, ReportTaskPayload.class));
        } catch (JsonProcessingException ex) {
            throw new InvalidReportPayloadException("Report payload format is invalid", ex);
        }
    }

    public ReportTaskPayload parse(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new InvalidReportPayloadException("Report payload is required");
        }
        try {
            return validate(objectMapper.readValue(payload, ReportTaskPayload.class));
        } catch (JsonProcessingException ex) {
            throw new InvalidReportPayloadException("Report payload format is invalid", ex);
        }
    }

    private ReportTaskPayload validate(ReportTaskPayload payload) {
        if (payload == null) throw new InvalidReportPayloadException("Report payload is required");
        requireText(payload.reportName(), "reportName", 100);
        requireText(payload.requestedBy(), "requestedBy", 100);
        if (payload.records() == null || payload.records().isEmpty()) {
            throw new InvalidReportPayloadException("records must contain at least one sales record");
        }
        if (payload.records().size() > MAX_RECORDS) {
            throw new InvalidReportPayloadException("records cannot contain more than " + MAX_RECORDS + " items");
        }
        for (int i = 0; i < payload.records().size(); i++) {
            SalesRecord record = payload.records().get(i);
            if (record == null) throw new InvalidReportPayloadException("records[" + i + "] is required");
            requireText(record.orderId(), "records[" + i + "].orderId", 64);
            requireText(record.region(), "records[" + i + "].region", 64);
            requireText(record.product(), "records[" + i + "].product", 100);
            if (record.quantity() <= 0) {
                throw new InvalidReportPayloadException("records[" + i + "].quantity must be greater than 0");
            }
            BigDecimal unitPrice = record.unitPrice();
            if (unitPrice == null || unitPrice.signum() <= 0) {
                throw new InvalidReportPayloadException("records[" + i + "].unitPrice must be greater than 0");
            }
        }
        return payload;
    }

    private void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidReportPayloadException(field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new InvalidReportPayloadException(field + " cannot exceed " + maxLength + " characters");
        }
    }
}
