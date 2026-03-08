package com.viglet.turing.api.llm.tokenusage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2/llm/token-usage")
@Tag(name = "LLM Token Usage", description = "Token consumption reports for LLM instances")
public class TurLLMTokenUsageAPI {

    private final TurLLMTokenUsageRepository tokenUsageRepository;

    public TurLLMTokenUsageAPI(TurLLMTokenUsageRepository tokenUsageRepository) {
        this.tokenUsageRepository = tokenUsageRepository;
    }

    public record DailyUsageRow(
            String date,
            String instanceId,
            String instanceTitle,
            String vendorId,
            String modelName,
            long inputTokens,
            long outputTokens,
            long totalTokens,
            long requestCount) {
    }

    public record MonthlySummaryRow(
            String instanceId,
            String instanceTitle,
            String vendorId,
            String modelName,
            long inputTokens,
            long outputTokens,
            long totalTokens,
            long requestCount) {
    }

    public record UsageReport(
            String periodStart,
            String periodEnd,
            List<DailyUsageRow> daily,
            List<MonthlySummaryRow> summary,
            long totalInputTokens,
            long totalOutputTokens,
            long totalTokens,
            long totalRequests) {
    }

    @GetMapping
    public UsageReport getUsageReport(
            @RequestParam(required = false) String month) {

        LocalDate monthDate;
        if (month != null && !month.isBlank()) {
            monthDate = LocalDate.parse(month + "-01", DateTimeFormatter.ISO_LOCAL_DATE);
        } else {
            monthDate = LocalDate.now().withDayOfMonth(1);
        }

        LocalDateTime start = monthDate.atStartOfDay();
        LocalDateTime end = monthDate.plusMonths(1).atTime(LocalTime.MIN);

        List<Object[]> dailyRows = tokenUsageRepository.findDailyUsage(start, end);
        List<Object[]> summaryRows = tokenUsageRepository.findMonthlySummary(start, end);

        List<DailyUsageRow> daily = new ArrayList<>();
        for (Object[] row : dailyRows) {
            daily.add(new DailyUsageRow(
                    row[0].toString(),
                    (String) row[1],
                    (String) row[2],
                    (String) row[3],
                    (String) row[4],
                    ((Number) row[5]).longValue(),
                    ((Number) row[6]).longValue(),
                    ((Number) row[7]).longValue(),
                    ((Number) row[8]).longValue()));
        }

        List<MonthlySummaryRow> summary = new ArrayList<>();
        long totalInput = 0;
        long totalOutput = 0;
        long totalAll = 0;
        long totalReqs = 0;
        for (Object[] row : summaryRows) {
            long input = ((Number) row[4]).longValue();
            long output = ((Number) row[5]).longValue();
            long total = ((Number) row[6]).longValue();
            long reqs = ((Number) row[7]).longValue();
            summary.add(new MonthlySummaryRow(
                    (String) row[0],
                    (String) row[1],
                    (String) row[2],
                    (String) row[3],
                    input, output, total, reqs));
            totalInput += input;
            totalOutput += output;
            totalAll += total;
            totalReqs += reqs;
        }

        return new UsageReport(
                start.toLocalDate().toString(),
                end.toLocalDate().toString(),
                daily, summary,
                totalInput, totalOutput, totalAll, totalReqs);
    }
}
