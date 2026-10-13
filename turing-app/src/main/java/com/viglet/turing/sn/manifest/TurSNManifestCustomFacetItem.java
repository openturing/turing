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

package com.viglet.turing.sn.manifest;

import java.math.BigDecimal;
import java.util.Map;

import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum;

/**
 * One bucket of a {@link TurSNManifestCustomFacet} (T1084), spelled as the
 * console's custom facet item so the two paths store the same row.
 *
 * @param label          the default label (required)
 * @param labels         per-locale labels
 * @param position       order inside the facet; {@code null} means its place in
 *                       the list
 * @param operator       how the bounds apply; {@code null} means
 *                       {@code BETWEEN_INCLUSIVE}
 * @param rangeStart     lower bound for a numeric field
 * @param rangeEnd       upper bound for a numeric field
 * @param rangeStartDate lower bound for a DATE field: an ISO date or date math
 *                       such as {@code NOW-7DAYS}
 * @param rangeEndDate   upper bound for a DATE field, in the same forms
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNManifestCustomFacetItem(String label, Map<String, String> labels, Integer position,
        TurSNSiteCustomFacetOperatorEnum operator, BigDecimal rangeStart, BigDecimal rangeEnd,
        String rangeStartDate, String rangeEndDate) {
}
