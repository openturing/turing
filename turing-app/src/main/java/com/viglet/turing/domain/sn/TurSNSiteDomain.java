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

import com.viglet.turing.persistence.model.sn.TurSNSiteFacetSortEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;

/**
 * Domain entity for a Semantic Navigation site — the aggregate root identity
 * plus its scalar configuration value attributes. Free of JPA, Hibernate, and
 * Jackson annotations; immutable so callers can pass it across layers without
 * worrying about hidden lazy collections or proxy state.
 *
 * <p>Aggregate boundary: this record covers <em>only</em> the site's own
 * value attributes. Child collections (fields, locales, spotlights, ranking
 * expressions, etc.) belong to their own aggregates accessed via dedicated
 * ports — they are deliberately excluded here.
 *
 * <p>References to neighbouring aggregates ({@code TurSEInstance},
 * {@code TurSNSiteGenAi}) are represented by their IDs; resolve through the
 * appropriate port when the related aggregate is needed.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public record TurSNSiteDomain(
        String id,
        String name,
        String description,
        String icon,
        Integer rowsPerPage,
        Integer wildcardNoResults,
        Integer wildcardAlways,
        Integer exactMatch,
        Integer facet,
        Integer itemsPerFacet,
        Integer hl,
        String hlPre,
        String hlPost,
        Integer mlt,
        TurSNSiteFacetFieldEnum facetType,
        TurSNSiteFacetFieldEnum facetItemType,
        TurSNSiteFacetSortEnum facetSort,
        Integer thesaurus,
        String defaultField,
        String exactMatchField,
        String defaultTitleField,
        String defaultTextField,
        String defaultDescriptionField,
        String defaultDateField,
        String defaultImageField,
        String defaultURLField,
        Integer spellCheck,
        Integer spellCheckFixes,
        Integer spotlightWithResults,
        String searchTemplate,
        String seInstanceId,
        String genAiId,
        String createdBy) {

    /** True when the site has a non-blank template stored under the configured storage backend. */
    public boolean hasSearchTemplate() {
        return searchTemplate != null && !searchTemplate.isBlank();
    }
}
