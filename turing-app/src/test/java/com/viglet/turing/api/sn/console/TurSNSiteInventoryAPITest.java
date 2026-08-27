/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.api.sn.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.inventory.TurSNIndexInventoryResponse;
import com.viglet.turing.sn.inventory.TurSNIndexInventoryService;

/**
 * T1035 / §XX.20 — the path variable takes a site <em>name</em> as well as an id,
 * because a connector holds the name it publishes to and not the row's uuid. The
 * id is still tried first, so no existing caller changes.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurSNSiteInventoryAPITest {

    @Mock
    private TurSNSiteRepository snSiteRepository;
    @Mock
    private TurSNIndexInventoryService inventoryService;

    private TurSNSiteInventoryAPI api;
    private TurSNSite site;

    @BeforeEach
    void setUp() {
        api = new TurSNSiteInventoryAPI(snSiteRepository, inventoryService);
        site = new TurSNSite();
        site.setId("site-uuid");
        site.setName("Catalog");
        when(inventoryService.inventory(any(), any(), any(), any(), anyInt()))
                .thenReturn(new TurSNIndexInventoryResponse("site-uuid", "Catalog", "en-US",
                        List.of("en-US"), "aem", List.of(), null, false, 0L, true));
    }

    @Test
    @DisplayName("An id resolves without ever consulting the name index")
    void idResolvesFirst() {
        when(snSiteRepository.findById("site-uuid")).thenReturn(Optional.of(site));

        var response = api.inventory("site-uuid", "aem", null, null, 0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(snSiteRepository, never()).findByNameIgnoreCase(any());
    }

    @Test
    @DisplayName("A site name resolves too — that is what a connector actually holds")
    void nameResolvesAsFallback() {
        when(snSiteRepository.findById("Catalog")).thenReturn(Optional.empty());
        when(snSiteRepository.findByNameIgnoreCase("Catalog")).thenReturn(Optional.of(site));

        var response = api.inventory("Catalog", "aem", null, null, 0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().siteName()).isEqualTo("Catalog");
        verify(inventoryService).inventory(eq(site), eq(null), eq("aem"), eq(null), eq(0));
    }

    @Test
    @DisplayName("Something that is neither is a 404, not an empty inventory")
    void unknownSiteIsNotFound() {
        when(snSiteRepository.findById("nope")).thenReturn(Optional.empty());
        when(snSiteRepository.findByNameIgnoreCase("nope")).thenReturn(Optional.empty());

        var response = api.inventory("nope", "aem", null, null, 0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(inventoryService, never()).inventory(any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("The query parameters reach the service unchanged")
    void parametersReachTheService() {
        when(snSiteRepository.findById("site-uuid")).thenReturn(Optional.of(site));

        api.inventory("site-uuid", "aem", "pt-BR", "doc-9", 50);

        verify(inventoryService).inventory(eq(site), eq("pt-BR"), eq("aem"), eq("doc-9"), eq(50));
    }
}
