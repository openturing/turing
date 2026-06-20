package com.viglet.turing.api.llm.tokenusage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;

@ExtendWith(MockitoExtension.class)
class TurLLMTokenUsageAPITest {

    @Mock
    private TurLLMTokenUsageRepository tokenUsageRepository;

    private TurLLMTokenUsageAPI api;

    // Pinned "today" so the default-month report is deterministic; the API is
    // given the same clock, and the assertions derive the expected month from it.
    private static final LocalDate FIXED_TODAY = LocalDate.parse("2026-06-15");

    @BeforeEach
    void setUp() {
        api = new TurLLMTokenUsageAPI(tokenUsageRepository);
        api.setClockForTest(Clock.fixed(
                FIXED_TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC));
    }

    // --- Record Tests ---

    @Test
    void shouldCreateDailyUsageRow() {
        var row = new TurLLMTokenUsageAPI.DailyUsageRow(
                "2026-03-08", "inst-1", "GPT-4 Instance", "openai", "gpt-4",
                100L, 50L, 150L, 5L);

        assertThat(row.date()).isEqualTo("2026-03-08");
        assertThat(row.instanceId()).isEqualTo("inst-1");
        assertThat(row.instanceTitle()).isEqualTo("GPT-4 Instance");
        assertThat(row.vendorId()).isEqualTo("openai");
        assertThat(row.modelName()).isEqualTo("gpt-4");
        assertThat(row.inputTokens()).isEqualTo(100L);
        assertThat(row.outputTokens()).isEqualTo(50L);
        assertThat(row.totalTokens()).isEqualTo(150L);
        assertThat(row.requestCount()).isEqualTo(5L);
    }

    @Test
    void shouldCreateMonthlySummaryRow() {
        var row = new TurLLMTokenUsageAPI.MonthlySummaryRow(
                "inst-1", "Claude Instance", "anthropic", "claude-3",
                5000L, 3000L, 8000L, 100L);

        assertThat(row.instanceId()).isEqualTo("inst-1");
        assertThat(row.vendorId()).isEqualTo("anthropic");
        assertThat(row.totalTokens()).isEqualTo(8000L);
        assertThat(row.requestCount()).isEqualTo(100L);
    }

    @Test
    void shouldCreateUsageReport() {
        var report = new TurLLMTokenUsageAPI.UsageReport(
                "2026-03-01", "2026-04-01",
                List.of(), List.of(),
                1000L, 500L, 1500L, 20L);

        assertThat(report.periodStart()).isEqualTo("2026-03-01");
        assertThat(report.periodEnd()).isEqualTo("2026-04-01");
        assertThat(report.daily()).isEmpty();
        assertThat(report.summary()).isEmpty();
        assertThat(report.totalTokens()).isEqualTo(1500L);
    }

    // --- getUsageReport Tests ---

    @Test
    void shouldReturnEmptyReportWhenNoData() {
        when(tokenUsageRepository.findDailyUsage(any(), any())).thenReturn(Collections.emptyList());
        when(tokenUsageRepository.findMonthlySummary(any(), any())).thenReturn(Collections.emptyList());

        TurLLMTokenUsageAPI.UsageReport report = api.getUsageReport(null);

        assertThat(report.daily()).isEmpty();
        assertThat(report.summary()).isEmpty();
        assertThat(report.totalInputTokens()).isZero();
        assertThat(report.totalOutputTokens()).isZero();
        assertThat(report.totalTokens()).isZero();
        assertThat(report.totalRequests()).isZero();
    }

    @Test
    void shouldUseCurrentMonthWhenNoMonthParam() {
        when(tokenUsageRepository.findDailyUsage(any(), any())).thenReturn(Collections.emptyList());
        when(tokenUsageRepository.findMonthlySummary(any(), any())).thenReturn(Collections.emptyList());

        TurLLMTokenUsageAPI.UsageReport report = api.getUsageReport(null);

        LocalDate now = FIXED_TODAY;
        assertThat(report.periodStart()).isEqualTo(now.withDayOfMonth(1).toString());
    }

    @Test
    void shouldParseSpecificMonth() {
        when(tokenUsageRepository.findDailyUsage(any(), any())).thenReturn(Collections.emptyList());
        when(tokenUsageRepository.findMonthlySummary(any(), any())).thenReturn(Collections.emptyList());

        TurLLMTokenUsageAPI.UsageReport report = api.getUsageReport("2026-01");

        assertThat(report.periodStart()).isEqualTo("2026-01-01");
        assertThat(report.periodEnd()).isEqualTo("2026-02-01");
    }

    @Test
    void shouldMapDailyRows() {
        Object[] row = new Object[]{
                java.sql.Date.valueOf("2026-03-08"),
                "inst-1", "GPT Instance", "openai", "gpt-4",
                1000L, 500L, 1500L, 10L
        };
        List<Object[]> dailyRows = new ArrayList<>();
        dailyRows.add(row);
        when(tokenUsageRepository.findDailyUsage(any(), any())).thenReturn(dailyRows);
        when(tokenUsageRepository.findMonthlySummary(any(), any())).thenReturn(Collections.emptyList());

        TurLLMTokenUsageAPI.UsageReport report = api.getUsageReport("2026-03");

        assertThat(report.daily()).hasSize(1);
        assertThat(report.daily().getFirst().instanceId()).isEqualTo("inst-1");
        assertThat(report.daily().getFirst().inputTokens()).isEqualTo(1000L);
        assertThat(report.daily().getFirst().totalTokens()).isEqualTo(1500L);
    }

    @Test
    void shouldAggregateSummaryTotals() {
        Object[] summaryRow1 = new Object[]{
                "inst-1", "GPT Instance", "openai", "gpt-4",
                2000L, 1000L, 3000L, 20L
        };
        Object[] summaryRow2 = new Object[]{
                "inst-2", "Claude Instance", "anthropic", "claude-3",
                500L, 300L, 800L, 5L
        };
        when(tokenUsageRepository.findDailyUsage(any(), any())).thenReturn(Collections.emptyList());
        List<Object[]> summaryRows = new ArrayList<>();
        summaryRows.add(summaryRow1);
        summaryRows.add(summaryRow2);
        when(tokenUsageRepository.findMonthlySummary(any(), any())).thenReturn(summaryRows);

        TurLLMTokenUsageAPI.UsageReport report = api.getUsageReport("2026-03");

        assertThat(report.summary()).hasSize(2);
        assertThat(report.totalInputTokens()).isEqualTo(2500L);
        assertThat(report.totalOutputTokens()).isEqualTo(1300L);
        assertThat(report.totalTokens()).isEqualTo(3800L);
        assertThat(report.totalRequests()).isEqualTo(25L);
    }

    @Test
    void shouldHandleBlankMonthParam() {
        when(tokenUsageRepository.findDailyUsage(any(), any())).thenReturn(Collections.emptyList());
        when(tokenUsageRepository.findMonthlySummary(any(), any())).thenReturn(Collections.emptyList());

        TurLLMTokenUsageAPI.UsageReport report = api.getUsageReport("  ");

        LocalDate now = FIXED_TODAY;
        assertThat(report.periodStart()).isEqualTo(now.withDayOfMonth(1).toString());
    }
}
