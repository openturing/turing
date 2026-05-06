/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.llm.TurLLMTokenUsageDomain;
import com.viglet.turing.domain.llm.TurLLMTokenUsageSummaryDomain;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMTokenUsage;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;

/**
 * Unit tests for {@link TurLLMTokenUsageRepositoryAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class TurLLMTokenUsageRepositoryAdapterTest {

    @Mock
    private TurLLMTokenUsageRepository turLLMTokenUsageRepository;

    private TurLLMTokenUsageRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurLLMTokenUsageRepositoryAdapter(turLLMTokenUsageRepository,
                Mappers.getMapper(TurLLMTokenUsageDomainMapper.class));
    }

    @Test
    void findByIdProjectsScalarsAndManyToOneId() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 15, 10, 30);
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm-1");
        instance.setTitle("OpenAI prod");

        TurLLMTokenUsage entity = new TurLLMTokenUsage();
        entity.setId("usage-1");
        entity.setTurLLMInstance(instance);
        entity.setVendorId("openai");
        entity.setModelName("gpt-4o-mini");
        entity.setUsername("alex");
        entity.setInputTokens(120L);
        entity.setOutputTokens(80L);
        entity.setTotalTokens(200L);
        entity.setCreatedAt(createdAt);
        when(turLLMTokenUsageRepository.findById("usage-1")).thenReturn(Optional.of(entity));

        TurLLMTokenUsageDomain domain = adapter.findById("usage-1").orElseThrow();

        assertThat(domain.id()).isEqualTo("usage-1");
        assertThat(domain.llmInstanceId()).isEqualTo("llm-1");
        assertThat(domain.vendorId()).isEqualTo("openai");
        assertThat(domain.modelName()).isEqualTo("gpt-4o-mini");
        assertThat(domain.username()).isEqualTo("alex");
        assertThat(domain.inputTokens()).isEqualTo(120L);
        assertThat(domain.outputTokens()).isEqualTo(80L);
        assertThat(domain.totalTokens()).isEqualTo(200L);
        assertThat(domain.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void findByIdEmpty() {
        when(turLLMTokenUsageRepository.findById("missing")).thenReturn(Optional.empty());
        assertThat(adapter.findById("missing")).isEmpty();
    }

    @Test
    void findDailyUsageMapsObjectArrayRows() {
        LocalDateTime start = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 1, 0, 0);
        Object[] row = new Object[] {
                LocalDate.of(2026, 4, 15),
                "llm-1", "OpenAI prod", "openai", "gpt-4o-mini",
                120L, 80L, 200L, 5L
        };
        when(turLLMTokenUsageRepository.findDailyUsage(start, end))
                .thenReturn(List.<Object[]>of(row));

        List<TurLLMTokenUsageSummaryDomain> result = adapter.findDailyUsage(start, end);

        assertThat(result).singleElement().satisfies(d -> {
            assertThat(d.day()).isEqualTo(LocalDate.of(2026, 4, 15));
            assertThat(d.instanceId()).isEqualTo("llm-1");
            assertThat(d.instanceTitle()).isEqualTo("OpenAI prod");
            assertThat(d.vendorId()).isEqualTo("openai");
            assertThat(d.modelName()).isEqualTo("gpt-4o-mini");
            assertThat(d.inputTokens()).isEqualTo(120L);
            assertThat(d.outputTokens()).isEqualTo(80L);
            assertThat(d.totalTokens()).isEqualTo(200L);
            assertThat(d.requestCount()).isEqualTo(5L);
        });
    }

    @Test
    void findDailyUsageCoercesSqlDateToLocalDate() {
        LocalDateTime start = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 1, 0, 0);
        Object[] row = new Object[] {
                java.sql.Date.valueOf(LocalDate.of(2026, 4, 15)),
                "llm-1", "OpenAI prod", "openai", "gpt-4o-mini",
                120, 80, 200, 5  // ints coerced to long
        };
        when(turLLMTokenUsageRepository.findDailyUsage(start, end))
                .thenReturn(List.<Object[]>of(row));

        List<TurLLMTokenUsageSummaryDomain> result = adapter.findDailyUsage(start, end);

        assertThat(result).singleElement().satisfies(d -> {
            assertThat(d.day()).isEqualTo(LocalDate.of(2026, 4, 15));
            assertThat(d.inputTokens()).isEqualTo(120L);
            assertThat(d.totalTokens()).isEqualTo(200L);
        });
    }

    @Test
    void findMonthlySummaryProjectsWithoutDay() {
        LocalDateTime start = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 1, 0, 0);
        Object[] row = new Object[] {
                "llm-1", "OpenAI prod", "openai", "gpt-4o-mini",
                3600L, 2400L, 6000L, 150L
        };
        when(turLLMTokenUsageRepository.findMonthlySummary(start, end))
                .thenReturn(List.<Object[]>of(row));

        List<TurLLMTokenUsageSummaryDomain> result = adapter.findMonthlySummary(start, end);

        assertThat(result).singleElement().satisfies(d -> {
            assertThat(d.day()).isNull();
            assertThat(d.instanceId()).isEqualTo("llm-1");
            assertThat(d.instanceTitle()).isEqualTo("OpenAI prod");
            assertThat(d.totalTokens()).isEqualTo(6000L);
            assertThat(d.requestCount()).isEqualTo(150L);
        });
    }
}
