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

package com.viglet.turing.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;

/**
 * Unit tests for TurElasticsearchInstanceProcess.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurElasticsearchInstanceProcessTest {

    @Mock
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;

    @Mock
    private TurSNSiteRepository turSNSiteRepository;

    @Test
    void testInitElasticsearchInstanceReturnsEmptyWhenSiteMissing() {
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.empty());

        TurElasticsearchInstanceProcess process = new TurElasticsearchInstanceProcess(
                turSNSiteLocaleRepository, turSNSiteRepository);

        assertThat(process.initElasticsearchInstance("site", Locale.US)).isEmpty();
    }

    @Test
    void testInitElasticsearchInstanceReturnsEmptyWhenLocaleMissing() {
        TurSNSite site = new TurSNSite();
        site.setName("site");
        when(turSNSiteRepository.findByNameIgnoreCase("site")).thenReturn(Optional.of(site));
        when(turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(any(TurSNSite.class), any(Locale.class)))
                .thenReturn(null);

        TurElasticsearchInstanceProcess process = new TurElasticsearchInstanceProcess(
                turSNSiteLocaleRepository, turSNSiteRepository);

        assertThat(process.initElasticsearchInstance("site", Locale.US)).isEmpty();
    }

    @Test
    void testInitElasticsearchInstanceByTurSEInstanceCreatesInstance() {
        TurElasticsearchInstanceProcess process = new TurElasticsearchInstanceProcess(
                turSNSiteLocaleRepository, turSNSiteRepository);

        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:9200");

        Optional<TurElasticsearchInstance> result = process.initElasticsearchInstance(seInstance, "test-index");

        assertThat(result).isPresent();
        assertThat(result.get().getIndex()).isEqualTo("test-index");
        assertThat(result.get().getClient()).isNotNull();

        // Cleanup
        result.get().close();
    }

    @Test
    void testInitElasticsearchInstanceByTurSEInstanceCachesInstance() {
        TurElasticsearchInstanceProcess process = new TurElasticsearchInstanceProcess(
                turSNSiteLocaleRepository, turSNSiteRepository);

        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:9200");

        Optional<TurElasticsearchInstance> first = process.initElasticsearchInstance(seInstance, "test-index");
        Optional<TurElasticsearchInstance> second = process.initElasticsearchInstance(seInstance, "test-index");

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first).containsSame(second.get());

        // Cleanup
        process.shutdown();
    }

    @Test
    void testInitElasticsearchInstanceNormalizesIndexToLowercase() {
        TurElasticsearchInstanceProcess process = new TurElasticsearchInstanceProcess(
                turSNSiteLocaleRepository, turSNSiteRepository);

        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:9200");

        Optional<TurElasticsearchInstance> result = process.initElasticsearchInstance(seInstance, "MyIndex");

        assertThat(result).isPresent();
        assertThat(result.get().getIndex()).isEqualTo("myindex");

        process.shutdown();
    }

    @Test
    void testInitElasticsearchInstanceBySiteLocale() {
        TurElasticsearchInstanceProcess process = new TurElasticsearchInstanceProcess(
                turSNSiteLocaleRepository, turSNSiteRepository);

        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:9200");

        TurSNSite site = new TurSNSite();
        site.setTurSEInstance(seInstance);

        TurSNSiteLocale siteLocale = new TurSNSiteLocale();
        siteLocale.setTurSNSite(site);
        siteLocale.setCore("locale-core");

        Optional<TurElasticsearchInstance> result = process.initElasticsearchInstance(siteLocale);

        assertThat(result).isPresent();
        assertThat(result.get().getIndex()).isEqualTo("locale-core");

        process.shutdown();
    }

    @Test
    void testEvictRemovesCachedInstance() {
        TurElasticsearchInstanceProcess process = new TurElasticsearchInstanceProcess(
                turSNSiteLocaleRepository, turSNSiteRepository);

        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:9200");

        Optional<TurElasticsearchInstance> first = process.initElasticsearchInstance(seInstance, "evict-test");
        assertThat(first).isPresent();

        process.evict("http://localhost:9200", "evict-test");

        // After eviction, a new instance should be created
        Optional<TurElasticsearchInstance> second = process.initElasticsearchInstance(seInstance, "evict-test");
        assertThat(second).isPresent();
        assertThat(second.get()).isNotSameAs(first.get());

        process.shutdown();
    }

    @Test
    void testEvictNonExistentKeyDoesNotFail() {
        TurElasticsearchInstanceProcess process = new TurElasticsearchInstanceProcess(
                turSNSiteLocaleRepository, turSNSiteRepository);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> process.evict("http://nonexistent:9200", "no-such-index"));
    }

    @Test
    void testShutdownClearsCache() {
        TurElasticsearchInstanceProcess process = new TurElasticsearchInstanceProcess(
                turSNSiteLocaleRepository, turSNSiteRepository);

        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:9200");

        process.initElasticsearchInstance(seInstance, "shutdown-test");
        process.shutdown();

        // After shutdown, a new request should create a new instance
        Optional<TurElasticsearchInstance> result = process.initElasticsearchInstance(seInstance, "shutdown-test");
        assertThat(result).isPresent();

        process.shutdown();
    }

    @Test
    void testInitWithSiteAndLocaleCreatesInstance() {
        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setEndpointUrl("http://localhost:9200");

        TurSNSite site = new TurSNSite();
        site.setName("mySite");
        site.setTurSEInstance(seInstance);

        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setCore("en_us_core");

        when(turSNSiteRepository.findByNameIgnoreCase("mySite")).thenReturn(Optional.of(site));
        when(turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(any(TurSNSite.class), any(Locale.class)))
                .thenReturn(locale);

        TurElasticsearchInstanceProcess process = new TurElasticsearchInstanceProcess(
                turSNSiteLocaleRepository, turSNSiteRepository);

        Optional<TurElasticsearchInstance> result = process.initElasticsearchInstance("mySite", Locale.US);

        assertThat(result).isPresent();
        assertThat(result.get().getIndex()).isEqualTo("en_us_core");

        process.shutdown();
    }
}
