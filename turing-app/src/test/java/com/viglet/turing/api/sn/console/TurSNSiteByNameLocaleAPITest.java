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

package com.viglet.turing.api.sn.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.viglet.turing.persistence.dto.sn.locale.TurSNSiteLocaleDto;
import com.viglet.turing.persistence.mapper.sn.locale.TurSNSiteLocaleMapper;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;

/**
 * Unit tests for TurSNSiteByNameLocaleAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
class TurSNSiteByNameLocaleAPITest {

    @Test
    void testLocaleByNameReturnsEmptyWhenMissing() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteLocaleRepository localeRepository = mock(TurSNSiteLocaleRepository.class);
        TurSNSiteLocaleMapper localeMapper = Mappers.getMapper(TurSNSiteLocaleMapper.class);
        TurSNSiteByNameLocaleAPI api = new TurSNSiteByNameLocaleAPI(siteRepository, localeRepository, localeMapper);

        when(siteRepository.findByName("site")).thenReturn(Optional.empty());

        List<TurSNSiteLocaleDto> result = api.turSNSiteLocaleByNameList("site");

        assertThat(result).isEqualTo(Collections.emptyList());
    }

    @Test
    void testLocaleByNameReturnsLocalesWhenFound() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteLocaleRepository localeRepository = mock(TurSNSiteLocaleRepository.class);
        TurSNSiteLocaleMapper localeMapper = Mappers.getMapper(TurSNSiteLocaleMapper.class);
        TurSNSiteByNameLocaleAPI api = new TurSNSiteByNameLocaleAPI(siteRepository, localeRepository, localeMapper);
        TurSNSite site = new TurSNSite();
        TurSNSiteLocale locale = new TurSNSiteLocale();

        when(siteRepository.findByName("site")).thenReturn(Optional.of(site));
        when(localeRepository.findByTurSNSite(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(site)))
                .thenReturn(List.of(locale));

        List<TurSNSiteLocaleDto> result = api.turSNSiteLocaleByNameList("site");

        assertThat(result).hasSize(1);
    }
}
