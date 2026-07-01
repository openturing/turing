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

package com.viglet.turing.api.sn.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.api.sn.bean.TurSNSiteFacetOrderingDto;
import com.viglet.turing.api.sn.bean.TurSNSiteFacetProfileDto;
import com.viglet.turing.api.sn.bean.TurSNSiteFacetProfileRequest;
import com.viglet.turing.sn.facet.TurSNSiteFacetProfileService;

/**
 * Unit tests for {@link TurSNSiteFacetedFieldAPI} — verifies it delegates to
 * {@link TurSNSiteFacetProfileService}.
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteFacetedFieldAPITest {

    @Mock
    private TurSNSiteFacetProfileService service;

    @InjectMocks
    private TurSNSiteFacetedFieldAPI api;

    @Test
    void legacyListDelegatesToDefaultOrdering() {
        List<TurSNSiteFacetOrderingDto> expected = List.of(new TurSNSiteFacetOrderingDto().setId("a"));
        when(service.getDefaultOrdering("site")).thenReturn(expected);

        assertThat(api.turSNSiteFacetdFieldExtList("site")).isSameAs(expected);
    }

    @Test
    void legacyUpdateDelegatesToSaveDefaultOrdering() {
        List<TurSNSiteFacetOrderingDto> body = List.of(new TurSNSiteFacetOrderingDto().setId("a"));
        when(service.saveDefaultOrdering("site", body)).thenReturn(body);

        assertThat(api.turSNSiteFieldUpdate("site", body)).isSameAs(body);
    }

    @Test
    void profileCrudDelegates() {
        TurSNSiteFacetProfileDto dto = new TurSNSiteFacetProfileDto("p1", "Geral", null, true, 0);
        TurSNSiteFacetProfileRequest request = new TurSNSiteFacetProfileRequest("Geral", null);
        when(service.list("site")).thenReturn(List.of(dto));
        when(service.create("site", request)).thenReturn(dto);
        when(service.update("site", "p1", request)).thenReturn(dto);
        when(service.setDefault("site", "p1")).thenReturn(List.of(dto));

        assertThat(api.profileList("site")).containsExactly(dto);
        assertThat(api.profileCreate("site", request)).isSameAs(dto);
        assertThat(api.profileUpdate("site", "p1", request)).isSameAs(dto);
        assertThat(api.profileSetDefault("site", "p1")).containsExactly(dto);

        api.profileDelete("site", "p1");
        verify(service).delete("site", "p1");
    }

    @Test
    void profileOrderingDelegates() {
        List<TurSNSiteFacetOrderingDto> ordering = List.of(new TurSNSiteFacetOrderingDto().setId("a"));
        when(service.getOrdering("site", "p1")).thenReturn(ordering);
        when(service.saveOrdering("site", "p1", ordering)).thenReturn(ordering);

        assertThat(api.profileOrdering("site", "p1")).isSameAs(ordering);
        assertThat(api.profileOrderingUpdate("site", "p1", ordering)).isSameAs(ordering);
    }
}
