/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.llm.usage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.llm.TurAiUsageDaily;
import com.viglet.turing.persistence.repository.llm.TurAiUsageDailyRepository;
import com.viglet.turing.service.llm.usage.TurAnthropicUsageClient.Report;
import com.viglet.turing.service.llm.usage.TurAnthropicUsageParser.CostRow;
import com.viglet.turing.service.llm.usage.TurAnthropicUsageParser.UsageRow;
import com.viglet.turing.tenant.TurTenantContext;

import lombok.extern.slf4j.Slf4j;

/**
 * T183 / §X.14.c — merges the Anthropic usage + cost reports into
 * {@code tur_ai_usage_daily} rows and persists them idempotently.
 *
 * <p>Token figures come from the usage report and USD from the cost report;
 * each report contributes its metric once per its own grouping key
 * {@code (day, workspace, model, service tier)}, so rows that share the exact
 * key combine into one and the rest stay separate — sums are always correct and
 * <strong>never double-counted</strong> even when the two reports group at
 * different granularities. The window is deleted before re-insert so a re-run
 * (or an overlapping nightly window) converges rather than accumulating.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurAiUsageImportService {

    private static final String VENDOR_ANTHROPIC = "anthropic";

    private final TurAnthropicUsageClient anthropicUsageClient;
    private final TurAiUsageDailyRepository usageDailyRepository;
    private final TurTenantContext tenantContext;

    public TurAiUsageImportService(TurAnthropicUsageClient anthropicUsageClient,
            TurAiUsageDailyRepository usageDailyRepository,
            TurTenantContext tenantContext) {
        this.anthropicUsageClient = anthropicUsageClient;
        this.usageDailyRepository = usageDailyRepository;
        this.tenantContext = tenantContext;
    }

    public boolean isAnthropicConfigured() {
        return anthropicUsageClient.isConfigured();
    }

    /**
     * Import the Anthropic usage + cost for the {@code [from, to]} day window.
     * Returns the number of {@code tur_ai_usage_daily} rows written (0 when not
     * configured or the vendor returned nothing).
     */
    @Transactional
    public int importAnthropic(LocalDate from, LocalDate to) {
        Report report = anthropicUsageClient.fetch(from, to);
        List<TurAiUsageDaily> rows = merge(report, tenantContext.resolveCurrentTenant());
        usageDailyRepository.deleteByVendorIdAndUsageDateBetween(VENDOR_ANTHROPIC, from, to);
        if (rows.isEmpty()) {
            return 0;
        }
        usageDailyRepository.saveAll(rows);
        log.info("[Usage][Anthropic] imported {} daily usage row(s) for {}..{}", rows.size(), from, to);
        return rows.size();
    }

    private List<TurAiUsageDaily> merge(Report report, String tenantId) {
        Map<Key, TurAiUsageDaily> byKey = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        for (UsageRow usage : report.usage()) {
            TurAiUsageDaily row = byKey.computeIfAbsent(Key.of(usage.date(), usage.workspaceId(),
                    usage.model(), usage.serviceTier()), k -> newRow(k, tenantId, now));
            row.setInputTokens(row.getInputTokens() + usage.inputTokens());
            row.setOutputTokens(row.getOutputTokens() + usage.outputTokens());
        }
        for (CostRow cost : report.cost()) {
            TurAiUsageDaily row = byKey.computeIfAbsent(Key.of(cost.date(), cost.workspaceId(),
                    cost.model(), cost.serviceTier()), k -> newRow(k, tenantId, now));
            row.setCostUsd(row.getCostUsd() + cost.costUsd());
        }
        return new ArrayList<>(byKey.values());
    }

    private TurAiUsageDaily newRow(Key key, String tenantId, LocalDateTime now) {
        TurAiUsageDaily row = new TurAiUsageDaily();
        row.setUsageDate(key.date());
        row.setVendorId(VENDOR_ANTHROPIC);
        row.setWorkspaceId(blankToNull(key.workspaceId()));
        row.setModelName(blankToNull(key.model()));
        row.setServiceTier(blankToNull(key.serviceTier()));
        row.setTenantId(tenantId);
        row.setCreatedAt(now);
        return row;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Merge key — nulls normalized to "" so usage and cost rows align deterministically. */
    private record Key(LocalDate date, String workspaceId, String model, String serviceTier) {
        static Key of(LocalDate date, String workspaceId, String model, String serviceTier) {
            return new Key(date, nz(workspaceId), nz(model), nz(serviceTier));
        }

        private static String nz(String value) {
            return value == null ? "" : value;
        }
    }
}
