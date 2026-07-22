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

import com.viglet.turing.api.sn.bean.TurProfileFieldConfigRequest;
import com.viglet.turing.api.sn.bean.TurSNSiteFieldProfileConfigDto;
import com.viglet.turing.api.sn.bean.TurSNSiteFieldProfileConfigDto.FacetConfig;
import com.viglet.turing.api.sn.bean.TurSNSiteFieldProfileConfigDto.OverrideFlags;
import com.viglet.turing.profile.TurProfileFieldConfigService;

/**
 * Unit tests for {@link TurSNSiteFieldProfileAPI} — verifies it delegates to
 * {@link TurProfileFieldConfigService}. Business rules are covered by the service
 * tests.
 *
 * @author Arthur Nascimento
 * @since 2026.2.7
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteFieldProfileAPITest {

    @Mock
    private TurProfileFieldConfigService service;

    @InjectMocks
    private TurSNSiteFieldProfileAPI api;

    private static TurSNSiteFieldProfileConfigDto dto() {
        FacetConfig config = new FacetConfig(1, "Cátedras", null, null, null, null, null, null);
        OverrideFlags flags = new OverrideFlags(true, true, false, false, false, false, false, false);
        return new TurSNSiteFieldProfileConfigDto("f1", "catedras", "STRING", 1, config, flags, config);
    }

    @Test
    void listDelegatesToService() {
        TurSNSiteFieldProfileConfigDto dto = dto();
        when(service.getEffectiveConfigs("s1", "p1")).thenReturn(List.of(dto));

        assertThat(api.list("s1", "p1")).containsExactly(dto);
    }

    @Test
    void saveDelegatesToService() {
        TurSNSiteFieldProfileConfigDto dto = dto();
        TurProfileFieldConfigRequest request =
                new TurProfileFieldConfigRequest(1, "Cátedras", null, null, null, null, null, null);
        when(service.saveOverride("s1", "p1", "f1", request)).thenReturn(dto);

        assertThat(api.save("s1", "p1", "f1", request)).isSameAs(dto);
    }

    @Test
    void clearDelegatesToService() {
        when(service.clearOverride("s1", "p1", "f1")).thenReturn(true);

        assertThat(api.clear("s1", "p1", "f1")).isTrue();
        verify(service).clearOverride("s1", "p1", "f1");
    }
}
