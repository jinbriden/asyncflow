package io.github.asyncflow.framework.data;

public final class ReportTestDataFactory {
    public static final String REGIONAL_SALES_NAME = "regional-sales";
    public static final String INTEGRATION_REPORT_NAME = "integration-report";
    public static final int REGIONAL_SOURCE_RECORD_COUNT = 3;
    public static final int REGIONAL_SUMMARY_ROW_COUNT = 2;
    public static final int REGIONAL_TOTAL_QUANTITY = 6;
    public static final String REGIONAL_TOTAL_AMOUNT = "1967.70";
    public static final String REGIONAL_CSV_HEADER = "region,order_count,total_quantity,total_amount";
    public static final String REGIONAL_EAST_ROW = "East,2,3,1698.00";
    public static final String REGIONAL_WEST_ROW = "West,1,3,269.70";
    public static final String REGIONAL_TOTAL_ROW = "TOTAL,3,6,1967.70";

    private ReportTestDataFactory() {
    }

    public static String validReportBody() {
        return validReportBody(REGIONAL_SALES_NAME);
    }

    public static String validReportBody(String reportName) {
        return """
                {
                  "type": "REPORT",
                  "payload": {
                    "reportName": "%s",
                    "requestedBy": "qa@example.com",
                    "records": [
                      {"orderId":"SO-1001","region":"East","product":"Keyboard","quantity":2,"unitPrice":199.50},
                      {"orderId":"SO-1002","region":"West","product":"Mouse","quantity":3,"unitPrice":89.90},
                      {"orderId":"SO-1003","region":"East","product":"Monitor","quantity":1,"unitPrice":1299.00}
                    ]
                  },
                  "maxAttempts": 3
                }
                """.formatted(reportName);
    }

    public static String emptyRecordsBody() {
        return """
                {"type":"REPORT","payload":{"reportName":"empty","requestedBy":"qa@example.com","records":[]}}
                """;
    }

    public static String blankTypeBody() {
        return "{\"type\":\"\",\"payload\":{}}";
    }

    public static String zeroMaxAttemptsBody() {
        return "{\"type\":\"REPORT\",\"payload\":{},\"maxAttempts\":0}";
    }

    public static String excessiveFailureInjectionBody() {
        return "{\"type\":\"REPORT\",\"payload\":{},\"simulateFailures\":11}";
    }

    public static String reportBodyWithSimulatedFailures(int simulateFailures) {
        return validReportBody("fault-injection-report")
                .replace("\"maxAttempts\": 3", "\"maxAttempts\": 3, \"simulateFailures\": " + simulateFailures);
    }

    public static String containerReportBody() {
        return "{\"type\":\"REPORT\",\"payload\":{\"reportName\":\"container-report\",\"requestedBy\":\"qa@example.com\",\"records\":[{\"orderId\":\"SO-C1\",\"region\":\"East\",\"product\":\"Keyboard\",\"quantity\":1,\"unitPrice\":199.50}]},\"maxAttempts\":3,\"simulateFailures\":0}";
    }
}
