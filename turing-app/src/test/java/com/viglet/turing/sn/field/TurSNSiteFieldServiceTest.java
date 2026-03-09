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

package com.viglet.turing.sn.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;

/**
 * Unit tests for TurSNSiteFieldService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteFieldServiceTest {

    @Mock
    private TurSNSiteFieldRepository turSNSiteFieldRepository;

    @Test
    void testToMapBuildsNameMap() {
        TurSNSite site = new TurSNSite();
        TurSNSiteField field1 = new TurSNSiteField();
        field1.setName("title");
        TurSNSiteField field2 = new TurSNSiteField();
        field2.setName("text");
        when(turSNSiteFieldRepository.findByTurSNSite(site)).thenReturn(List.of(field1, field2));

        TurSNSiteFieldService service = new TurSNSiteFieldService(turSNSiteFieldRepository);
        Map<String, TurSNSiteField> map = service.toMap(site);

        assertThat(map).containsEntry("title", field1).containsEntry("text", field2);
    }
}
