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
package com.viglet.turing.sn.querycontext;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.viglet.turing.commons.sn.bean.TurSNSiteSearchDefaultFieldsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchQueryContextBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchQueryContextQueryBean;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.se.result.TurSEResults;

/**
 * Builds the {@link TurSNSiteSearchQueryContextBean} attached to a search
 * response — page bounds, query echo, default field references and the
 * effective facet/facet-item type. Extracted from {@code TurSNSearchProcess} so
 * the mapping lives in its own package and can be exercised in isolation.
 *
 * <p>Behaviour is intentionally identical to the previous inline implementation.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Component
public class TurSNQueryContextBuilder {

    /**
     * Builds the query-context bean for the given site and search results.
     */
    public TurSNSiteSearchQueryContextBean build(TurSNSite turSNSite, TurSEResults turSEResults,
            Locale locale) {
        int lastItemOfFullPage = (int) turSEResults.getStart() + turSEResults.getLimit();
        int firstItemOfFullPage = (int) turSEResults.getStart() + 1;
        int count = (int) turSEResults.getNumFound();
        int pageEnd = Math.min(lastItemOfFullPage, count);
        return new TurSNSiteSearchQueryContextBean()
                .setQuery(new TurSNSiteSearchQueryContextQueryBean()
                        .setQueryString(turSEResults.getQueryString())
                        .setSort(turSEResults.getSort()).setLocale(locale))
                .setDefaultFields(defaultFields(turSNSite))
                .setPageCount(turSEResults.getPageCount())
                .setPage(turSEResults.getCurrentPage()).setCount(count)
                .setPageEnd(pageEnd)
                .setPageStart(Math.min(firstItemOfFullPage, pageEnd))
                .setLimit(turSEResults.getLimit()).setOffset(0)
                .setResponseTime(turSEResults.getElapsedTime())
                .setIndex(turSNSite.getName())
                .setFacetType(facetTypeOrDefault(turSNSite.getFacetType()))
                .setFacetItemType(facetTypeOrDefault(turSNSite.getFacetItemType()));
    }

    private static String facetTypeOrDefault(TurSNSiteFacetFieldEnum value) {
        return value != null ? value.toString() : TurSNSiteFacetFieldEnum.AND.toString();
    }

    private static TurSNSiteSearchDefaultFieldsBean defaultFields(TurSNSite turSNSite) {
        return new TurSNSiteSearchDefaultFieldsBean()
                .setDate(turSNSite.getDefaultDateField())
                .setDescription(turSNSite.getDefaultDescriptionField())
                .setImage(turSNSite.getDefaultImageField())
                .setText(turSNSite.getDefaultTextField())
                .setTitle(turSNSite.getDefaultTitleField())
                .setUrl(turSNSite.getDefaultURLField());
    }
}
