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
package com.viglet.turing.persistence.adapter.sn;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.viglet.turing.domain.sn.TurSNSiteSearchRuleActionDomain;
import com.viglet.turing.domain.sn.TurSNSiteSearchRuleConditionDomain;
import com.viglet.turing.domain.sn.TurSNSiteSearchRuleDomain;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRule;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleAction;
import com.viglet.turing.persistence.model.sn.searchrule.TurSNSiteSearchRuleCondition;

/**
 * MapStruct mapper for the JPA {@link TurSNSiteSearchRule} entity to the
 * {@link TurSNSiteSearchRuleDomain} aggregate. Conditions and actions are
 * aggregate parts of the rule and are projected as full child records.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Mapper(componentModel = "spring")
public interface TurSNSiteSearchRuleDomainMapper {

    @Mapping(target = "snSiteId", source = "turSNSite.id")
    @Mapping(target = "conditions", source = "conditions")
    @Mapping(target = "actions", source = "actions")
    TurSNSiteSearchRuleDomain toDomain(TurSNSiteSearchRule entity);

    List<TurSNSiteSearchRuleDomain> toDomainList(List<TurSNSiteSearchRule> entities);

    TurSNSiteSearchRuleConditionDomain toConditionDomain(TurSNSiteSearchRuleCondition entity);

    TurSNSiteSearchRuleActionDomain toActionDomain(TurSNSiteSearchRuleAction entity);

    default Set<TurSNSiteSearchRuleConditionDomain> conditionsToDomain(
            Set<TurSNSiteSearchRuleCondition> set) {
        return set == null ? Set.of()
                : Collections.unmodifiableSet(
                        set.stream().map(this::toConditionDomain).collect(Collectors.toSet()));
    }

    default Set<TurSNSiteSearchRuleActionDomain> actionsToDomain(
            Set<TurSNSiteSearchRuleAction> set) {
        return set == null ? Set.of()
                : Collections.unmodifiableSet(
                        set.stream().map(this::toActionDomain).collect(Collectors.toSet()));
    }
}
