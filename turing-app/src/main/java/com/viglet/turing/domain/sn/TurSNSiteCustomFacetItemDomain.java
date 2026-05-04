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
package com.viglet.turing.domain.sn;

import java.math.BigDecimal;
import java.time.Instant;

import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum;

/**
 * Domain entity for one curated item of a custom facet — typically either a
 * fixed-value match (operator + label) or a numeric / date range. Free of
 * JPA / Jackson annotations and immutable.
 *
 * <p>Items are <em>aggregate parts</em> of the parent custom facet rather
 * than a standalone aggregate — they have no dedicated repository and are
 * loaded eagerly with the parent. Instances are emitted from
 * {@code TurSNSiteCustomFacetDomainMapper} as part of {@code
 * TurSNSiteCustomFacetDomain.items}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurSNSiteCustomFacetItemDomain(
        String id,
        String label,
        Integer position,
        BigDecimal rangeStart,
        BigDecimal rangeEnd,
        Instant rangeStartDate,
        Instant rangeEndDate,
        TurSNSiteCustomFacetOperatorEnum operator) {
}
