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

package com.viglet.turing.persistence.dto.sn;

import java.util.List;
import java.util.Locale;

/**
 * Lightweight DTO for listing Semantic Navigation sites.
 * <p>
 * Intentionally excludes heavy {@code @OneToMany} associations that the
 * frontend listing does not consume (fields, field extensions, spotlights,
 * rankings, merge providers, custom sorts, search rules, metric accesses).
 * Only the site-identity fields plus a summarized locale projection are
 * exposed, which avoids the N+1 lazy-fetch pattern triggered when the full
 * {@link com.viglet.turing.persistence.model.sn.TurSNSite} entity is mapped.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public record TurSNSiteListDto(
        String id,
        String name,
        String description,
        String icon,
        String searchTemplate,
        boolean genAiEnabled,
        List<TurSNSiteLocaleSummary> turSNSiteLocales
) {
    public record TurSNSiteLocaleSummary(Locale language) {
    }
}
