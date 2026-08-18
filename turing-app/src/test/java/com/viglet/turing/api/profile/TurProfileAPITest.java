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
package com.viglet.turing.api.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.api.profile.bean.TurProfileDto;
import com.viglet.turing.api.profile.bean.TurProfileRequest;
import com.viglet.turing.profile.TurProfileFacetOrderingService;
import com.viglet.turing.profile.TurProfileService;

/**
 * Unit tests for {@link TurProfileAPI} — verifies it delegates to
 * {@link TurProfileService} (CRUD) and, for the default promotion, to
 * {@link TurProfileFacetOrderingService} (which also rewrites the legacy
 * facetPosition mirror — DT-6). Business rules are covered by the service tests.
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
@ExtendWith(MockitoExtension.class)
class TurProfileAPITest {

    @Mock
    private TurProfileService service;
    @Mock
    private TurProfileFacetOrderingService orderingService;

    @InjectMocks
    private TurProfileAPI api;

    @Test
    void crudDelegatesToService() {
        TurProfileDto dto = new TurProfileDto("p1", "Padrão", null, true);
        TurProfileRequest request = new TurProfileRequest("Padrão", null);
        when(service.list()).thenReturn(List.of(dto));
        when(service.get("p1")).thenReturn(dto);
        when(service.create(request)).thenReturn(dto);
        when(service.update("p1", request)).thenReturn(dto);

        assertThat(api.profileList()).containsExactly(dto);
        assertThat(api.profileGet("p1")).isSameAs(dto);
        assertThat(api.profileCreate(request)).isSameAs(dto);
        assertThat(api.profileUpdate("p1", request)).isSameAs(dto);

        api.profileDelete("p1");
        verify(service).delete("p1");
    }

    @Test
    void setDefaultDelegatesToOrderingServiceForLegacySync() {
        TurProfileDto dto = new TurProfileDto("p1", "Cursos EE", null, true);
        when(orderingService.setDefault("p1")).thenReturn(List.of(dto));

        assertThat(api.profileSetDefault("p1")).containsExactly(dto);
        verify(orderingService).setDefault("p1");
    }
}
