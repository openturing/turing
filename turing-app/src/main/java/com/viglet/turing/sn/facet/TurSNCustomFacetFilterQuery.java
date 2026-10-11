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
package com.viglet.turing.sn.facet;

import java.time.Instant;
import java.util.Optional;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum;

/**
 * T1083 — the filter query a custom facet item stands for, in Solr's range
 * syntax ({@code field:[start TO end]}), which both the Solr engine and, since
 * T1075, the Lucene engine read. Built in one place so one item cannot mean two
 * ranges on two engines.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurSNCustomFacetFilterQuery {

    private static final String OPEN = "*";

    private TurSNCustomFacetFilterQuery() {
    }

    /** The filter query for {@code item} over the field it belongs to. */
    public static String of(String fieldName, TurSEFieldType fieldType, TurSNSiteCustomFacetItem item) {
        TurSNSiteCustomFacetOperatorEnum operator = Optional.ofNullable(item.getOperator())
                .orElse(TurSNSiteCustomFacetOperatorEnum.BETWEEN_INCLUSIVE);
        if (TurSEFieldType.DATE.equals(fieldType)) {
            return byOperator(fieldName, operator,
                    Optional.ofNullable(item.getRangeStartDate()).map(Instant::toString).orElse(null),
                    Optional.ofNullable(item.getRangeEndDate()).map(Instant::toString).orElse(null));
        }
        return byOperator(fieldName, operator,
                Optional.ofNullable(item.getRangeStart()).map(Number::toString).orElse(null),
                Optional.ofNullable(item.getRangeEnd()).map(Number::toString).orElse(null));
    }

    /** The filter query for an operator and its bounds; a missing bound is open. */
    public static String byOperator(String fieldName, TurSNSiteCustomFacetOperatorEnum operator,
            String startValue, String endValue) {
        String start = startValue != null ? startValue : OPEN;
        String end = endValue != null ? endValue : OPEN;
        return switch (operator) {
            case EQUAL -> String.format("%s:%s", fieldName,
                    startValue != null ? startValue : end);
            case GREATER_THAN_OR_EQUAL -> String.format("%s:[%s TO *]", fieldName, start);
            case GREATER_THAN -> String.format("%s:{%s TO *]", fieldName, start);
            case LESS_THAN_OR_EQUAL -> String.format("%s:[* TO %s]", fieldName, end);
            case LESS_THAN -> String.format("%s:[* TO %s}", fieldName, end);
            case BETWEEN_INCLUSIVE -> String.format("%s:[%s TO %s]", fieldName, start, end);
            case BETWEEN_EXCLUSIVE -> String.format("%s:{%s TO %s}", fieldName, start, end);
        };
    }
}
