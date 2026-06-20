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
package com.viglet.turing.genai;

import java.util.List;
import java.util.Map;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.util.StringUtils;

/**
 * Shared helper that translates the {@code Map<String, List<String>>} facet
 * convention used by the ANN search and RAG chat APIs into a Spring AI
 * {@link Filter.Expression}. Multiple values for the same key produce an
 * {@code IN} clause; multiple keys are joined with {@code AND}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public final class TurRagFilters {

    private TurRagFilters() {
    }

    /**
     * @return {@code null} when no filter applies (no keys, all values blank,
     *         etc.) so callers can skip the {@code filterExpression(...)}
     *         setter on {@link org.springframework.ai.vectorstore.SearchRequest}.
     */
    public static Filter.Expression buildFilterExpression(Map<String, List<String>> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op combined = null;
        for (Map.Entry<String, List<String>> entry : filters.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            if (!StringUtils.hasText(key) || values == null || values.isEmpty()) {
                continue;
            }
            List<Object> nonEmpty = values.stream()
                    .filter(StringUtils::hasText)
                    .map(Object.class::cast)
                    .toList();
            if (!nonEmpty.isEmpty()) {
                FilterExpressionBuilder.Op op = nonEmpty.size() == 1
                        ? b.eq(key, nonEmpty.getFirst())
                        : b.in(key, nonEmpty.toArray());
                combined = combined == null ? op : b.and(combined, op);
            }
        }
        return combined != null ? combined.build() : null;
    }
}
