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
package com.viglet.turing.sn.coverage;

/**
 * T388 — completeness of a single Semantic Navigation field across a site's
 * indexed documents: how many documents actually populate it, out of the total.
 *
 * <p>{@code presentDocuments} is {@code -1} and {@code supported} is
 * {@code false} when the search engine cannot report field presence; clients
 * must render that as "unknown" rather than 0%.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurSNFieldCoverage(
        String fieldName,
        String fieldType,
        boolean multiValued,
        boolean facet,
        long presentDocuments,
        long totalDocuments,
        double coveragePercent,
        boolean supported) {
}
