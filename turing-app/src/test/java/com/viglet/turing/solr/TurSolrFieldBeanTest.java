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

import org.junit.jupiter.api.Test;

import com.viglet.turing.solr.bean.TurSolrFieldBean;

/**
 * Unit tests for TurSolrFieldBean.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
class TurSolrFieldBeanTest {

    @Test
    void testNoArgsConstructorDefaults() {
        TurSolrFieldBean bean = new TurSolrFieldBean();

        assertThat(bean.getName()).isNull();
        assertThat(bean.getType()).isNull();
        assertThat(bean.isMultiValued()).isFalse();
        assertThat(bean.isIndexed()).isFalse();
        assertThat(bean.isStored()).isFalse();
    }

    @Test
    void testBuilder() {
        TurSolrFieldBean bean = TurSolrFieldBean.builder()
                .name("title")
                .type("text_general")
                .multiValued(true)
                .indexed(true)
                .stored(true)
                .build();

        assertThat(bean.getName()).isEqualTo("title");
        assertThat(bean.getType()).isEqualTo("text_general");
        assertThat(bean.isMultiValued()).isTrue();
        assertThat(bean.isIndexed()).isTrue();
        assertThat(bean.isStored()).isTrue();
    }
}
