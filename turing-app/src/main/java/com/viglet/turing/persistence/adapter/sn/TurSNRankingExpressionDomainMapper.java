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
import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.viglet.turing.domain.sn.TurSNRankingConditionDomain;
import com.viglet.turing.domain.sn.TurSNRankingExpressionDomain;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingCondition;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;

/**
 * MapStruct mapper for the JPA {@link TurSNRankingExpression} entity to the
 * {@link TurSNRankingExpressionDomain} aggregate.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Mapper(componentModel = "spring")
public interface TurSNRankingExpressionDomainMapper {

    @Mapping(target = "snSiteId", source = "turSNSite.id")
    @Mapping(target = "conditions", source = "turSNRankingConditions")
    TurSNRankingExpressionDomain toDomain(TurSNRankingExpression entity);

    TurSNRankingConditionDomain toConditionDomain(TurSNRankingCondition entity);

    default Set<TurSNRankingConditionDomain> conditionsToDomain(Set<TurSNRankingCondition> set) {
        return set == null ? Set.of()
                : Collections.unmodifiableSet(
                        set.stream().map(this::toConditionDomain).collect(Collectors.toSet()));
    }
}
