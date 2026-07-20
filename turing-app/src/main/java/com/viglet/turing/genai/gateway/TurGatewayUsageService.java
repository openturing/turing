/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.gateway.TurGatewayKey;
import com.viglet.turing.persistence.repository.gateway.TurGatewayKeyRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;

/**
 * T747 / §XLIX — gateway observability: rolls the per-key spend/token metering
 * captured by T742 ({@code llm_token_usage.key_id}) into dashboard rows, reusing
 * the same {@code TurLLMTokenUsageRepository} aggregations that back the Block L
 * cost dashboard. Inbound gateway traffic thus feeds the existing spend view,
 * now sliceable per virtual key.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurGatewayUsageService {

    private static final int DEFAULT_WINDOW_DAYS = 30;

    private final TurLLMTokenUsageRepository tokenUsageRepository;
    private final TurGatewayKeyRepository gatewayKeyRepository;

    public TurGatewayUsageService(TurLLMTokenUsageRepository tokenUsageRepository,
            TurGatewayKeyRepository gatewayKeyRepository) {
        this.tokenUsageRepository = tokenUsageRepository;
        this.gatewayKeyRepository = gatewayKeyRepository;
    }

    /** One row of spend for a virtual key over the window. */
    public record KeyUsageRow(String keyId, String keyName,
            double costUsd, long totalTokens, long requestCount) {
    }

    /** Month-to-date budget posture for a key with a soft/hard cap configured. */
    public record KeyBudgetStatus(String keyId, String keyName,
            Double monthlyBudgetUsd, Double hardMonthlyCapUsd, double monthToDateSpendUsd,
            boolean overSoftBudget, boolean overHardCap) {
    }

    /**
     * Per-key spend/tokens/requests over {@code [start, end)}. Window defaults to
     * the last {@value #DEFAULT_WINDOW_DAYS} days when {@code start} is null.
     */
    public List<KeyUsageRow> usageByKey(LocalDateTime start, LocalDateTime end) {
        LocalDateTime from = start != null ? start
                : LocalDate.now(ZoneId.systemDefault()).minusDays(DEFAULT_WINDOW_DAYS).atStartOfDay();
        LocalDateTime to = end != null ? end
                : LocalDate.now(ZoneId.systemDefault()).plusDays(1).atStartOfDay();

        Map<String, String> keyNames = gatewayKeyRepository.findAll().stream()
                .collect(Collectors.toMap(TurGatewayKey::getId,
                        k -> k.getName() == null ? k.getId() : k.getName(), (a, b) -> a));

        List<KeyUsageRow> rows = new ArrayList<>();
        for (Object[] row : tokenUsageRepository.findCostByKey(from, to)) {
            String keyId = (String) row[0];
            rows.add(new KeyUsageRow(keyId, keyNames.getOrDefault(keyId, keyId),
                    num(row[1]), lng(row[2]), lng(row[3])));
        }
        return rows;
    }

    /** Month-to-date budget status for every key with a positive soft or hard cap. */
    public List<KeyBudgetStatus> budgetStatus() {
        LocalDateTime monthStart = LocalDate.now(ZoneId.systemDefault())
                .withDayOfMonth(1).atStartOfDay();
        List<KeyBudgetStatus> out = new ArrayList<>();
        for (TurGatewayKey key : gatewayKeyRepository.findAll()) {
            Double soft = key.getMonthlyBudgetUsd();
            Double hard = key.getHardMonthlyCapUsd();
            boolean hasSoft = soft != null && soft > 0;
            boolean hasHard = hard != null && hard > 0;
            if (!hasSoft && !hasHard) {
                continue;
            }
            double spent = tokenUsageRepository.sumCostByKeySince(key.getId(), monthStart);
            out.add(new KeyBudgetStatus(key.getId(),
                    key.getName() == null ? key.getId() : key.getName(),
                    soft, hard, spent,
                    hasSoft && spent >= soft, hasHard && spent >= hard));
        }
        out.sort((a, b) -> Double.compare(b.monthToDateSpendUsd(), a.monthToDateSpendUsd()));
        return out;
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0d;
    }

    private static long lng(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }
}
