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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.persistence.model.llm.TurAiUsageDaily;
import com.viglet.turing.persistence.repository.llm.TurAiUsageDailyRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * T183 / §X.14.c — boots the full context (so Liquibase {@code v2026.3.1.70}
 * actually creates {@code tur_ai_usage_daily}) and exercises the repository's
 * aggregation + window-delete used by the importer and the T184 widget.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAiUsageDailyRepositoryIT extends AbstractTuringSpringIT {

    @Autowired
    private TurAiUsageDailyRepository repository;

    private TurAiUsageDaily row(LocalDate date, String model, long input, long output, double cost) {
        TurAiUsageDaily row = new TurAiUsageDaily();
        row.setUsageDate(date);
        row.setVendorId("anthropic");
        row.setWorkspaceId("ws_1");
        row.setModelName(model);
        row.setServiceTier("standard");
        row.setInputTokens(input);
        row.setOutputTokens(output);
        row.setCostUsd(cost);
        row.setTenantId("DEFAULT");
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void persistsAggregatesAndWindowDeletes() {
        LocalDate day = LocalDate.of(2026, 6, 27);
        repository.deleteByVendorIdAndUsageDateBetween("anthropic", day.minusDays(1), day.plusDays(1));
        repository.saveAll(List.of(
                row(day, "claude-opus-4", 1000, 300, 12.34),
                row(day, "claude-opus-4", 500, 100, 5.66),
                row(day, "claude-haiku", 200, 50, 1.00)));

        assertThat(repository.sumCostBetween(day, day)).isEqualTo(19.00);

        List<Object[]> byModel = repository.findVendorCostByModel(day, day);
        assertThat(byModel).hasSize(2);
        // ordered by costUsd desc → opus first (12.34 + 5.66 = 18.0)
        assertThat((String) byModel.get(0)[1]).isEqualTo("claude-opus-4");
        assertThat(((Number) byModel.get(0)[2]).doubleValue()).isEqualTo(18.0);
        assertThat(((Number) byModel.get(0)[3]).longValue()).isEqualTo(1500L);

        repository.deleteByVendorIdAndUsageDateBetween("anthropic", day, day);
        assertThat(repository.sumCostBetween(day, day)).isZero();
    }
}
