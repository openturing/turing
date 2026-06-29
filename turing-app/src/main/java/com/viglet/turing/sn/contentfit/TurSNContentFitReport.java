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
package com.viglet.turing.sn.contentfit;

/**
 * T472 / §XXVI.9 — index-time audience content-fit coverage for an SN site:
 * how many indexed documents are "too complex for their audience" versus a fit.
 * Computed purely by range-counting the {@code content_fit_score} field that
 * {@link TurSNContentFitIndexer} writes at index time — adds no indexing
 * behaviour, mirrors the T388 field-coverage report shape.
 *
 * @param siteId             the SN site id
 * @param siteName           the SN site name
 * @param enabled            whether index-time content-fit is switched on
 * @param personaId          the target-audience persona id (null when unset)
 * @param personaName        the target-audience persona name (null when unset)
 * @param totalDocuments     total indexed documents across all locales
 * @param scoredDocuments    documents carrying a {@code content_fit_score}
 * @param tooComplex         documents whose score is below {@code tooComplexThreshold} (red)
 * @param borderline         documents in [{@code tooComplexThreshold}, {@code goodThreshold}) (amber)
 * @param good               documents at or above {@code goodThreshold} (green)
 * @param tooComplexThreshold the "too complex for its audience" cutoff (exclusive upper)
 * @param goodThreshold       the "good fit" cutoff
 * @param tooComplexPercent  {@code tooComplex / scoredDocuments * 100}, one decimal
 * @param supported          false when the engine can't range-count the score field
 * @since 2026.3.4
 */
public record TurSNContentFitReport(
        String siteId,
        String siteName,
        boolean enabled,
        String personaId,
        String personaName,
        long totalDocuments,
        long scoredDocuments,
        long tooComplex,
        long borderline,
        long good,
        int tooComplexThreshold,
        int goodThreshold,
        double tooComplexPercent,
        boolean supported) {
}
