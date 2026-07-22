/*
 * Copyright (C) 2016-2026 the original author or authors.
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
package com.viglet.turing.sn.searchrule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRule;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleAction;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleActionTypeEnum;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleCondition;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleOperatorEnum;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleParameterEnum;
import com.viglet.turing.persistence.repository.sn.searchrule.TurSNSiteSearchRuleRepository;

@ExtendWith(MockitoExtension.class)
class TurSNSearchRuleEvaluatorTest {

    @Mock
    private TurSNSiteSearchRuleRepository searchRuleRepository;

    @InjectMocks
    private TurSNSearchRuleEvaluator evaluator;

    private static TurSNSite site() {
        TurSNSite site = new TurSNSite();
        site.setId("s1");
        site.setName("s1");
        return site;
    }

    private static TurSNSiteSearchRule ruleSettingProfile(String queryMatch, String profileValue) {
        TurSNSiteSearchRuleCondition condition = TurSNSiteSearchRuleCondition.builder()
                .parameter(TurSNSiteSearchRuleParameterEnum.QUERY)
                .operator(TurSNSiteSearchRuleOperatorEnum.EQUALS)
                .value(queryMatch)
                .build();
        TurSNSiteSearchRuleAction action = TurSNSiteSearchRuleAction.builder()
                .actionType(TurSNSiteSearchRuleActionTypeEnum.SET_PROFILE)
                .value(profileValue)
                .build();
        return TurSNSiteSearchRule.builder().id("r1").name("rule").enabled(1).position(1)
                .conditions(Set.of(condition)).actions(Set.of(action)).build();
    }

    private static TurSEParameters params(String query) {
        TurSEParameters params = new TurSEParameters(new TurSNSearchParams());
        params.setQuery(query);
        return params;
    }

    @Test
    void setProfileActionAppliesProfileWhenRuleMatches() {
        TurSNSite site = site();
        when(searchRuleRepository.findEnabledByTurSNSite(site))
                .thenReturn(List.of(ruleSettingProfile("livros", "Cursos EE")));
        TurSEParameters params = params("livros");

        evaluator.applyRules(site, params, null);

        assertThat(params.getProfile()).isEqualTo("Cursos EE");
    }

    @Test
    void setProfileActionIgnoredWhenRuleDoesNotMatch() {
        TurSNSite site = site();
        when(searchRuleRepository.findEnabledByTurSNSite(site))
                .thenReturn(List.of(ruleSettingProfile("livros", "Cursos EE")));
        TurSEParameters params = params("outro");

        evaluator.applyRules(site, params, null);

        assertThat(params.getProfile()).isNull();
    }
}
