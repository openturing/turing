/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.persistence.adapter.llm;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.turing.domain.llm.TurLLMTokenUsageDomain;
import com.viglet.turing.domain.llm.TurLLMTokenUsageRepositoryPort;
import com.viglet.turing.domain.llm.TurLLMTokenUsageSummaryDomain;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;

/**
 * Infrastructure adapter implementing {@link TurLLMTokenUsageRepositoryPort}.
 *
 * <p>The two aggregation queries return raw {@code Object[]} rows from
 * JPQL group-by projections. This adapter converts them into the typed
 * {@link TurLLMTokenUsageSummaryDomain} factory methods (daily / monthly).
 * Type coercion is defensive — the {@code CAST(createdAt AS date)} in the
 * daily query may surface as {@link java.time.LocalDate}, {@link java.sql.Date}
 * or {@link java.util.Date} depending on the JPA provider version, and the
 * SUM/COUNT aggregates as any {@link Number} subtype.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurLLMTokenUsageRepositoryAdapter implements TurLLMTokenUsageRepositoryPort {

    private final TurLLMTokenUsageRepository turLLMTokenUsageRepository;
    private final TurLLMTokenUsageDomainMapper mapper;

    public TurLLMTokenUsageRepositoryAdapter(
            TurLLMTokenUsageRepository turLLMTokenUsageRepository,
            TurLLMTokenUsageDomainMapper mapper) {
        this.turLLMTokenUsageRepository = turLLMTokenUsageRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<TurLLMTokenUsageDomain> findById(String id) {
        return turLLMTokenUsageRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<TurLLMTokenUsageDomain> findAll() {
        return mapper.toDomainList(turLLMTokenUsageRepository.findAll());
    }

    @Override
    public List<TurLLMTokenUsageSummaryDomain> findDailyUsage(LocalDateTime start,
            LocalDateTime end) {
        return turLLMTokenUsageRepository.findDailyUsage(start, end).stream()
                .map(row -> TurLLMTokenUsageSummaryDomain.daily(
                        asLocalDate(row[0]),
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        (String) row[4],
                        asLong(row[5]),
                        asLong(row[6]),
                        asLong(row[7]),
                        asLong(row[8])))
                .toList();
    }

    @Override
    public List<TurLLMTokenUsageSummaryDomain> findMonthlySummary(LocalDateTime start,
            LocalDateTime end) {
        return turLLMTokenUsageRepository.findMonthlySummary(start, end).stream()
                .map(row -> TurLLMTokenUsageSummaryDomain.monthly(
                        (String) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        asLong(row[4]),
                        asLong(row[5]),
                        asLong(row[6]),
                        asLong(row[7])))
                .toList();
    }

    private static LocalDate asLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return new java.sql.Date(date.getTime()).toLocalDate();
        }
        throw new IllegalArgumentException(
                "Unexpected date type from JPQL projection: " + value.getClass());
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException(
                "Unexpected numeric type from JPQL projection: " + value.getClass());
    }
}
