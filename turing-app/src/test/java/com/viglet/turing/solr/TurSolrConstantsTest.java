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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for TurSolrConstants.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurSolrConstantsTest {

    @Test
    void testConstructorThrowsException() throws NoSuchMethodException {
        Constructor<TurSolrConstants> constructor = TurSolrConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Solr Constants class");
    }

    @Test
    void testSortingConstants() {
        assertThat(TurSolrConstants.NEWEST).isEqualTo("newest");
        assertThat(TurSolrConstants.OLDEST).isEqualTo("oldest");
        assertThat(TurSolrConstants.ASC).isEqualTo("asc");
    }

    @Test
    void testFieldConstants() {
        assertThat(TurSolrConstants.COUNT).isEqualTo("count");
        assertThat(TurSolrConstants.SCORE).isEqualTo("score");
        assertThat(TurSolrConstants.VERSION).isEqualTo("_version_");
        assertThat(TurSolrConstants.BOOST).isEqualTo("boost");
        assertThat(TurSolrConstants.TURING_ENTITY).isEqualTo("turing_entity_");
        assertThat(TurSolrConstants.ID).isEqualTo("id");
        assertThat(TurSolrConstants.TITLE).isEqualTo("title");
        assertThat(TurSolrConstants.TYPE).isEqualTo("type");
        assertThat(TurSolrConstants.URL).isEqualTo("url");
    }

    @Test
    void testQueryConstants() {
        assertThat(TurSolrConstants.MORE_LIKE_THIS).isEqualTo("moreLikeThis");
        assertThat(TurSolrConstants.BOOST_QUERY).isEqualTo("bq");
        assertThat(TurSolrConstants.QUERY).isEqualTo("q");
        assertThat(TurSolrConstants.TRUE).isEqualTo("true");
        assertThat(TurSolrConstants.EDISMAX).isEqualTo("edismax");
        assertThat(TurSolrConstants.DEF_TYPE).isEqualTo("defType");
        assertThat(TurSolrConstants.AND).isEqualTo("AND");
        assertThat(TurSolrConstants.Q_OP).isEqualTo("q.op");
        assertThat(TurSolrConstants.OR).isEqualTo("OR");
    }

    @Test
    void testRecentDatesConstant() {
        assertThat(TurSolrConstants.RECENT_DATES)
                .isEqualTo("{!func}recip(ms(NOW/DAY,%s),3.16e-11,1,1)")
                .contains("%s");
    }

    @Test
    void testHandlerConstants() {
        assertThat(TurSolrConstants.TUR_SUGGEST).isEqualTo("/tur_suggest");
        assertThat(TurSolrConstants.TUR_SPELL).isEqualTo("/tur_spell");
    }

    @Test
    void testFacetConstants() {
        assertThat(TurSolrConstants.FILTER_QUERY_OR).isEqualTo("{!tag=_all_}");
        assertThat(TurSolrConstants.FACET_OR).isEqualTo("{!ex=_all_}");
        assertThat(TurSolrConstants.NO_FACET_NAME).isEqualTo("__no_facet_name__");
        assertThat(TurSolrConstants.OR_OR).isEqualTo("OR-OR");
        assertThat(TurSolrConstants.OR_AND).isEqualTo("OR-AND");
        assertThat(TurSolrConstants.ALL).isEqualTo("_all_");
    }

    @Test
    void testMiscConstants() {
        assertThat(TurSolrConstants.PLUS_ONE).isEqualTo("+1");
        assertThat(TurSolrConstants.EMPTY).isEmpty();
        assertThat(TurSolrConstants.SOLR_DATE_PATTERN).isEqualTo("yyyy-MM-dd'T'HH:mm:ss'Z'");
        assertThat(TurSolrConstants.INDEX).isEqualTo("index");
        assertThat(TurSolrConstants.ROWS).isEqualTo("rows");
        assertThat(TurSolrConstants.HYPHEN).isEqualTo("-");
    }

    @Test
    void testAllConstantsAreNotNull() {
        assertThat(java.util.Arrays.asList(
                TurSolrConstants.NEWEST,
                TurSolrConstants.OLDEST,
                TurSolrConstants.ASC,
                TurSolrConstants.COUNT,
                TurSolrConstants.SCORE,
                TurSolrConstants.VERSION,
                TurSolrConstants.BOOST,
                TurSolrConstants.TURING_ENTITY,
                TurSolrConstants.ID,
                TurSolrConstants.TITLE,
                TurSolrConstants.TYPE,
                TurSolrConstants.URL,
                TurSolrConstants.MORE_LIKE_THIS,
                TurSolrConstants.BOOST_QUERY,
                TurSolrConstants.QUERY,
                TurSolrConstants.TRUE,
                TurSolrConstants.EDISMAX,
                TurSolrConstants.DEF_TYPE,
                TurSolrConstants.AND,
                TurSolrConstants.Q_OP,
                TurSolrConstants.RECENT_DATES,
                TurSolrConstants.TUR_SUGGEST,
                TurSolrConstants.TUR_SPELL,
                TurSolrConstants.FILTER_QUERY_OR,
                TurSolrConstants.FACET_OR,
                TurSolrConstants.PLUS_ONE,
                TurSolrConstants.EMPTY,
                TurSolrConstants.SOLR_DATE_PATTERN,
                TurSolrConstants.INDEX,
                TurSolrConstants.ROWS,
                TurSolrConstants.OR,
                TurSolrConstants.NO_FACET_NAME,
                TurSolrConstants.OR_OR,
                TurSolrConstants.OR_AND,
                TurSolrConstants.ALL,
                TurSolrConstants.HYPHEN)).doesNotContainNull();
    }

    @Test
    void testConstantValuePatterns() {
        assertThat(TurSolrConstants.VERSION)
                .startsWith("_")
                .endsWith("_");
        assertThat(TurSolrConstants.TURING_ENTITY).endsWith("_");
        assertThat(TurSolrConstants.TUR_SUGGEST).startsWith("/");
        assertThat(TurSolrConstants.TUR_SPELL).startsWith("/");
        assertThat(TurSolrConstants.FILTER_QUERY_OR).contains("tag=");
        assertThat(TurSolrConstants.FACET_OR).contains("ex=");
        assertThat(TurSolrConstants.NO_FACET_NAME)
                .startsWith("__")
                .endsWith("__");
        assertThat(TurSolrConstants.ALL)
                .startsWith("_")
                .endsWith("_");
    }
}
