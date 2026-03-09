/*
 * Copyright (C) 2016-2025 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.api.sn.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.dto.sn.ranking.TurSNRankingExpressionDto;
import com.viglet.turing.persistence.mapper.sn.ranking.TurSNRankingExpressionMapper;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingCondition;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingConditionRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;

/**
 * Unit tests for TurSNRankingExpressionAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNRankingExpressionAPITest {

    @Test
    void testRankingExpressionListReturnsEmptyWhenSiteMissing() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNRankingExpressionRepository expressionRepository = mock(TurSNRankingExpressionRepository.class);
        TurSNRankingConditionRepository conditionRepository = mock(TurSNRankingConditionRepository.class);
        TurSNRankingExpressionMapper mapper = Mappers.getMapper(TurSNRankingExpressionMapper.class);
        TurSNRankingExpressionAPI api = new TurSNRankingExpressionAPI(siteRepository,
                expressionRepository, conditionRepository, mapper);

        when(siteRepository.findById("site")).thenReturn(Optional.empty());

        Set<TurSNRankingExpressionDto> result = api.turSNRankingExpressionList("site");

        assertThat(result).isEmpty();
    }

    @Test
    void testRankingExpressionGetPopulatesConditions() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNRankingExpressionRepository expressionRepository = mock(TurSNRankingExpressionRepository.class);
        TurSNRankingConditionRepository conditionRepository = mock(TurSNRankingConditionRepository.class);
        TurSNRankingExpressionMapper mapper = Mappers.getMapper(TurSNRankingExpressionMapper.class);
        TurSNRankingExpressionAPI api = new TurSNRankingExpressionAPI(siteRepository,
                expressionRepository, conditionRepository, mapper);
        TurSNSite site = new TurSNSite();
        TurSNRankingExpression expression = new TurSNRankingExpression();
        Set<TurSNRankingCondition> conditions = new HashSet<>();
        conditions.add(new TurSNRankingCondition());

        when(siteRepository.findById("site")).thenReturn(Optional.of(site));
        when(expressionRepository.findById("expr")).thenReturn(Optional.of(expression));
        when(conditionRepository.findByTurSNRankingExpression(expression)).thenReturn(conditions);

        TurSNRankingExpressionDto result = api.turSNRankingExpressionGet("site", "expr");

        assertThat(result.getTurSNRankingConditions()).isEqualTo(conditions);
    }

    @Test
    void testRankingExpressionUpdateReturnsDefaultWhenSiteMissing() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNRankingExpressionRepository expressionRepository = mock(TurSNRankingExpressionRepository.class);
        TurSNRankingConditionRepository conditionRepository = mock(TurSNRankingConditionRepository.class);
        TurSNRankingExpressionMapper mapper = Mappers.getMapper(TurSNRankingExpressionMapper.class);
        TurSNRankingExpressionAPI api = new TurSNRankingExpressionAPI(siteRepository,
                expressionRepository, conditionRepository, mapper);

        TurSNRankingExpressionDto result = api.turSNRankingExpressionUpdate("expr",
                new TurSNRankingExpressionDto(), "site");

        assertThat(result.getId()).isNull();
    }

    @Test
    void testRankingExpressionDeleteReturnsTrueWhenSiteFound() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNRankingExpressionRepository expressionRepository = mock(TurSNRankingExpressionRepository.class);
        TurSNRankingConditionRepository conditionRepository = mock(TurSNRankingConditionRepository.class);
        TurSNRankingExpressionMapper mapper = Mappers.getMapper(TurSNRankingExpressionMapper.class);
        TurSNRankingExpressionAPI api = new TurSNRankingExpressionAPI(siteRepository,
                expressionRepository, conditionRepository, mapper);
        TurSNSite site = new TurSNSite();

        when(siteRepository.findById("site")).thenReturn(Optional.of(site));

        boolean result = api.turSNRankingExpressionDelete("expr", "site");

        assertThat(result).isTrue();
        verify(expressionRepository).deleteById("expr");
    }

    @Test
    void testRankingExpressionAddReturnsDefaultWhenSiteMissing() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNRankingExpressionRepository expressionRepository = mock(TurSNRankingExpressionRepository.class);
        TurSNRankingConditionRepository conditionRepository = mock(TurSNRankingConditionRepository.class);
        TurSNRankingExpressionMapper mapper = Mappers.getMapper(TurSNRankingExpressionMapper.class);
        TurSNRankingExpressionAPI api = new TurSNRankingExpressionAPI(siteRepository,
                expressionRepository, conditionRepository, mapper);

        TurSNRankingExpressionDto result = api.turSNRankingExpressionAdd(new TurSNRankingExpressionDto(), "site");

        assertThat(result.getId()).isNull();
    }

    @Test
    void testRankingExpressionStructureAssignsSite() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNRankingExpressionRepository expressionRepository = mock(TurSNRankingExpressionRepository.class);
        TurSNRankingConditionRepository conditionRepository = mock(TurSNRankingConditionRepository.class);
        TurSNRankingExpressionMapper mapper = Mappers.getMapper(TurSNRankingExpressionMapper.class);
        TurSNRankingExpressionAPI api = new TurSNRankingExpressionAPI(siteRepository,
                expressionRepository, conditionRepository, mapper);
        TurSNSite site = new TurSNSite();

        when(siteRepository.findById("site")).thenReturn(Optional.of(site));

        TurSNRankingExpressionDto result = api.turSNRankingExpressionStructure("site");

        assertThat(result.getTurSNSite()).isSameAs(site);
    }

    @Test
    void testRankingExpressionUpdateCopiesFields() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNRankingExpressionRepository expressionRepository = mock(TurSNRankingExpressionRepository.class);
        TurSNRankingConditionRepository conditionRepository = mock(TurSNRankingConditionRepository.class);
        TurSNRankingExpressionMapper mapper = Mappers.getMapper(TurSNRankingExpressionMapper.class);
        TurSNRankingExpressionAPI api = new TurSNRankingExpressionAPI(siteRepository,
                expressionRepository, conditionRepository, mapper);
        TurSNSite site = new TurSNSite();
        TurSNRankingExpression existing = new TurSNRankingExpression();
        TurSNRankingExpressionDto payload = new TurSNRankingExpressionDto();
        payload.setName("Updated");
        payload.setWeight(2.5f);
        payload.setTurSNRankingConditions(Collections.emptySet());

        when(siteRepository.findById("site")).thenReturn(Optional.of(site));
        when(expressionRepository.findById("expr")).thenReturn(Optional.of(existing));
        when(expressionRepository.save(existing)).thenReturn(existing);

        TurSNRankingExpressionDto result = api.turSNRankingExpressionUpdate("expr", payload, "site");

        assertThat(result.getName()).isEqualTo("Updated");
        assertThat(result.getWeight()).isEqualTo(2.5f);
    }
}
