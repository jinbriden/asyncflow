package io.github.asyncflow.framework.assertion;

import io.github.asyncflow.framework.data.ReportTestDataFactory;
import io.restassured.response.Response;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public final class ReportAssertions {
    private ReportAssertions() {
    }

    public static void assertQueryableRegionalSales(Response task, String taskId) {
        task.then().statusCode(200);
        assertThat(task.path("status").toString()).isEqualTo("SUCCEEDED");
        assertThat((Integer) task.path("result.sourceRecordCount"))
                .isEqualTo(ReportTestDataFactory.REGIONAL_SOURCE_RECORD_COUNT);
        assertThat((Integer) task.path("result.summaryRowCount"))
                .isEqualTo(ReportTestDataFactory.REGIONAL_SUMMARY_ROW_COUNT);
        assertThat((Integer) task.path("result.totalQuantity"))
                .isEqualTo(ReportTestDataFactory.REGIONAL_TOTAL_QUANTITY);
        assertThat(new BigDecimal(task.path("result.totalAmount").toString()))
                .isEqualByComparingTo(ReportTestDataFactory.REGIONAL_TOTAL_AMOUNT);
        assertThat(task.path("result.sha256").toString()).hasSize(64);
        assertThat(task.path("result.downloadUrl").toString()).isEqualTo("/api/tasks/" + taskId + "/result");
    }

    public static void assertDownloadableRegionalSalesCsv(Response download) {
        download.then().statusCode(200);
        assertThat(download.header("Content-Type")).startsWith("text/csv");
        assertThat(download.header("Content-Disposition"))
                .contains(ReportTestDataFactory.REGIONAL_SALES_NAME + ".csv");
        String csv = new String(download.asByteArray(), StandardCharsets.UTF_8);
        assertThat(csv)
                .contains(ReportTestDataFactory.REGIONAL_CSV_HEADER)
                .contains(ReportTestDataFactory.REGIONAL_EAST_ROW)
                .contains(ReportTestDataFactory.REGIONAL_WEST_ROW)
                .contains(ReportTestDataFactory.REGIONAL_TOTAL_ROW);
    }
}
