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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldDto;
import com.viglet.turing.persistence.mapper.sn.field.TurSNSiteFieldMapper;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;

/**
 * Unit tests for TurSNSiteFieldAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
class TurSNSiteFieldAPITest {

    @Test
    void testFieldListReturnsEmptyWhenSiteMissing() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
        TurSNSiteFieldMapper fieldMapper = Mappers.getMapper(TurSNSiteFieldMapper.class);
        TurSNSiteFieldAPI api = new TurSNSiteFieldAPI(siteRepository, fieldRepository, fieldMapper);

        when(siteRepository.findById("site")).thenReturn(Optional.empty());

        List<TurSNSiteFieldDto> result = api.turSNSiteFieldList("site");

        assertThat(result).isEmpty();
    }

    @Test
    void testFieldGetReturnsExisting() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
        TurSNSiteFieldMapper fieldMapper = Mappers.getMapper(TurSNSiteFieldMapper.class);
        TurSNSiteFieldAPI api = new TurSNSiteFieldAPI(siteRepository, fieldRepository, fieldMapper);
        TurSNSiteField field = new TurSNSiteField();
        field.setName("title");

        when(fieldRepository.findById("field")).thenReturn(Optional.of(field));

        TurSNSiteFieldDto result = api.turSNSiteFieldGet("site", "field");

        assertThat(result.getName()).isEqualTo("title");
    }

    @Test
    void testFieldUpdateCopiesFields() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
        TurSNSiteFieldMapper fieldMapper = Mappers.getMapper(TurSNSiteFieldMapper.class);
        TurSNSiteFieldAPI api = new TurSNSiteFieldAPI(siteRepository, fieldRepository, fieldMapper);
        TurSNSiteField existing = new TurSNSiteField();
        TurSNSiteFieldDto payload = new TurSNSiteFieldDto();
        payload.setName("title");
        payload.setDescription("Title");
        payload.setMultiValued(1);

        when(fieldRepository.findById("field")).thenReturn(Optional.of(existing));

        TurSNSiteFieldDto result = api.turSNSiteFieldUpdate("site", "field", payload);

        assertThat(result.getName()).isEqualTo("title");
        verify(fieldRepository).save(existing);
    }

    @Test
    void testFieldDeleteReturnsTrue() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
        TurSNSiteFieldMapper fieldMapper = Mappers.getMapper(TurSNSiteFieldMapper.class);
        TurSNSiteFieldAPI api = new TurSNSiteFieldAPI(siteRepository, fieldRepository, fieldMapper);

        boolean result = api.turSNSiteFieldDelete("site", "field");

        assertThat(result).isTrue();
        verify(fieldRepository).delete("field");
    }

    @Test
    void testFieldAddSetsSite() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
        TurSNSiteFieldMapper fieldMapper = Mappers.getMapper(TurSNSiteFieldMapper.class);
        TurSNSiteFieldAPI api = new TurSNSiteFieldAPI(siteRepository, fieldRepository, fieldMapper);
        TurSNSite site = new TurSNSite();
        TurSNSiteFieldDto field = new TurSNSiteFieldDto();
        field.setName("title");

        when(siteRepository.findById("site")).thenReturn(Optional.of(site));

        TurSNSiteFieldDto result = api.turSNSiteFieldAdd("site", field);

        assertThat(result.getName()).isEqualTo("title");
        verify(fieldRepository).save(argThat(saved -> saved.getTurSNSite() == site));
    }
}
