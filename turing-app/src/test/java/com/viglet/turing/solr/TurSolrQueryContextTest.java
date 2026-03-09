/*
 * Copyright (C) 2016-2025 the original author or authors.
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

package com.viglet.turing.solr;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.solr.client.solrj.SolrQuery;
import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;

/**
 * Unit tests for TurSolrQueryContext.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
class TurSolrQueryContextTest {

    @Test
    void testBuilderAndGetters() {
        SolrQuery query = new SolrQuery("test");
        TurSEParameters params = new TurSEParameters(new TurSNSearchParams());
        TurSESpellCheckResult spellCheck = new TurSESpellCheckResult();
        List<TurSNSiteFieldExt> mltFields = List.of(TurSNSiteFieldExt.builder().name("mlt").build());
        List<TurSNSiteFieldExt> facetFields = List.of(TurSNSiteFieldExt.builder().name("facet").build());
        List<TurSNSiteFieldExt> hlFields = List.of(TurSNSiteFieldExt.builder().name("hl").build());

        TurSolrQueryContext context = TurSolrQueryContext.builder()
                .query(query)
                .turSEParameters(params)
                .mltFieldExtList(mltFields)
                .facetFieldExtList(facetFields)
                .hlFieldExtList(hlFields)
                .spellCheckResult(spellCheck)
                .queryToRenderFacet(true)
                .build();

        assertThat(context.getQuery()).isSameAs(query);
        assertThat(context.getTurSEParameters()).isSameAs(params);
        assertThat(context.getMltFieldExtList()).isEqualTo(mltFields);
        assertThat(context.getFacetFieldExtList()).isEqualTo(facetFields);
        assertThat(context.getHlFieldExtList()).isEqualTo(hlFields);
        assertThat(context.getSpellCheckResult()).isSameAs(spellCheck);
        assertThat(context.isQueryToRenderFacet()).isTrue();
    }
}
