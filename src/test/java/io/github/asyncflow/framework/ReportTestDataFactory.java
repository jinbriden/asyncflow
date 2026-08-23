package io.github.asyncflow.framework;

public final class ReportTestDataFactory {
    private ReportTestDataFactory() {
    }

    public static String validReportBody() {
        return validReportBody("regional-sales");
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
}
