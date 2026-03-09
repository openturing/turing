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

package com.viglet.turing.sn.facet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.sn.bean.TurSNFilterParams;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;

/**
 * Unit tests for TurSNFacetTypeContext.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
class TurSNFacetTypeContextTest {

    @Test
    void testSpecificFieldFlag() {
        TurSNFilterParams params = TurSNFilterParams.builder().build();
        TurSNSite site = new TurSNSite();
        TurSNSiteFieldExtDto dto = new TurSNSiteFieldExtDto(TurSNSiteFieldExt.builder().name("field").build());

        TurSNFacetTypeContext contextWithField = new TurSNFacetTypeContext(dto, site, params);
        TurSNFacetTypeContext contextWithoutField = new TurSNFacetTypeContext(site, params);

        assertThat(contextWithField.isSpecificField()).isTrue();
        assertThat(contextWithoutField.isSpecificField()).isFalse();
    }
}
