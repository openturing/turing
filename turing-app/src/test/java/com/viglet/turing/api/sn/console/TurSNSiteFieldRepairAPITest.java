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

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.turing.api.sn.bean.TurSNFieldRepairPayload;
import com.viglet.turing.api.sn.bean.TurSNFieldRepairType;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.solr.TurSolrFieldAction;
import com.viglet.turing.solr.TurSolrUtils;

/**
 * Unit tests for TurSNSiteFieldRepairAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
class TurSNSiteFieldRepairAPITest {

    @Test
    void testFieldRepairReturnsOkWhenSiteMissing() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSEInstanceRepository instanceRepository = mock(TurSEInstanceRepository.class);
        TurSNSiteFieldRepairAPI api = new TurSNSiteFieldRepairAPI(siteRepository, fieldExtRepository,
                instanceRepository);

        TurSNFieldRepairPayload payload = new TurSNFieldRepairPayload();
        payload.setId("field");
        payload.setCore("core");
        payload.setRepairType(TurSNFieldRepairType.SE_CREATE_FIELD);

        try (org.mockito.MockedStatic<TurSolrUtils> utils = org.mockito.Mockito.mockStatic(TurSolrUtils.class)) {
            String result = api.turSNSiteFieldRepair("site", payload);

            assertThat(result).isEqualTo("ok");
            utils.verifyNoInteractions();
        }
    }

    @Test
    void testFieldRepairCreatesFieldWhenRequested() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSEInstanceRepository instanceRepository = mock(TurSEInstanceRepository.class);
        TurSNSiteFieldRepairAPI api = new TurSNSiteFieldRepairAPI(siteRepository, fieldExtRepository,
                instanceRepository);

        TurSEInstance instance = new TurSEInstance();
        instance.setId("se");
        TurSNSite site = new TurSNSite();
        site.setTurSEInstance(instance);
        TurSNSiteFieldExt fieldExt = new TurSNSiteFieldExt();
        fieldExt.setName("title");
        fieldExt.setType(TurSEFieldType.STRING);
        fieldExt.setMultiValued(1);

        when(siteRepository.findById("site")).thenReturn(Optional.of(site));
        when(fieldExtRepository.findById("field")).thenReturn(Optional.of(fieldExt));
        when(instanceRepository.findById("se")).thenReturn(Optional.of(instance));

        TurSNFieldRepairPayload payload = new TurSNFieldRepairPayload();
        payload.setId("field");
        payload.setCore("core");
        payload.setRepairType(TurSNFieldRepairType.SE_CREATE_FIELD);

        try (org.mockito.MockedStatic<TurSolrUtils> utils = org.mockito.Mockito.mockStatic(TurSolrUtils.class)) {
            String result = api.turSNSiteFieldRepair("site", payload);

            assertThat(result).isEqualTo("ok");
            utils.verify(() -> TurSolrUtils.addOrUpdateField(TurSolrFieldAction.ADD, instance,
                    "core", "title", TurSEFieldType.STRING, true, true));
        }
    }
}
