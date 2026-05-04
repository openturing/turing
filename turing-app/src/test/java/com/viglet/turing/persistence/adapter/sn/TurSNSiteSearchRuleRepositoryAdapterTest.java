/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.sn.TurSNSiteSearchRuleActionDomain;
import com.viglet.turing.domain.sn.TurSNSiteSearchRuleConditionDomain;
import com.viglet.turing.domain.sn.TurSNSiteSearchRuleDomain;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRule;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleAction;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleActionTypeEnum;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleCondition;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleOperatorEnum;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleParameterEnum;
import com.viglet.turing.persistence.repository.sn.searchrule.TurSNSiteSearchRuleRepository;

/** Unit tests for {@link TurSNSiteSearchRuleRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurSNSiteSearchRuleRepositoryAdapterTest {

    @Mock
    private TurSNSiteSearchRuleRepository turSNSiteSearchRuleRepository;

    private TurSNSiteSearchRuleRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurSNSiteSearchRuleRepositoryAdapter(turSNSiteSearchRuleRepository,
                Mappers.getMapper(TurSNSiteSearchRuleDomainMapper.class));
    }

    @Test
    void findByIdProjectsScalarsConditionsAndActions() {
        TurSNSiteSearchRule entity = TurSNSiteSearchRule.builder()
                .id("r-1")
                .name("rule-1")
                .position(0)
                .enabled(1)
                .turSNSite(stub("site-1"))
                .conditions(java.util.Set.of(buildCondition("c-1")))
                .actions(java.util.Set.of(buildAction("a-1")))
                .build();
        when(turSNSiteSearchRuleRepository.findById("r-1")).thenReturn(Optional.of(entity));

        TurSNSiteSearchRuleDomain domain = adapter.findById("r-1").orElseThrow();

        assertThat(domain.id()).isEqualTo("r-1");
        assertThat(domain.snSiteId()).isEqualTo("site-1");
        assertThat(domain.isEnabled()).isTrue();
        assertThat(domain.conditions()).extracting(TurSNSiteSearchRuleConditionDomain::id)
                .containsExactly("c-1");
        assertThat(domain.actions()).extracting(TurSNSiteSearchRuleActionDomain::id)
                .containsExactly("a-1");
    }

    @Test
    void findEnabledBySnSiteIdQueriesByStubParent() {
        when(turSNSiteSearchRuleRepository.findEnabledByTurSNSite(any())).thenReturn(List.of());

        adapter.findEnabledBySnSiteId("site-1");

        verify(turSNSiteSearchRuleRepository)
                .findEnabledByTurSNSite(argThat(s -> "site-1".equals(s.getId())));
    }

    @Test
    void findBySnSiteIdAndIdQueriesByStubParentAndId() {
        when(turSNSiteSearchRuleRepository.findByTurSNSiteAndId(any(), any()))
                .thenReturn(Optional.empty());

        adapter.findBySnSiteIdAndId("site-1", "r-1");

        verify(turSNSiteSearchRuleRepository).findByTurSNSiteAndId(
                argThat(s -> "site-1".equals(s.getId())), eq("r-1"));
    }

    private static TurSNSiteSearchRuleCondition buildCondition(String id) {
        return TurSNSiteSearchRuleCondition.builder()
                .id(id)
                .parameter(TurSNSiteSearchRuleParameterEnum.QUERY)
                .operator(TurSNSiteSearchRuleOperatorEnum.EQUALS)
                .fieldName("title")
                .value("foo")
                .build();
    }

    private static TurSNSiteSearchRuleAction buildAction(String id) {
        return TurSNSiteSearchRuleAction.builder()
                .id(id)
                .actionType(TurSNSiteSearchRuleActionTypeEnum.values()[0])
                .value("boost")
                .build();
    }

    private static TurSNSite stub(String id) {
        TurSNSite stub = new TurSNSite();
        stub.setId(id);
        return stub;
    }
}
