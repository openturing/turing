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

package com.viglet.turing.sn.template;

import static com.viglet.turing.commons.sn.field.TurSNFieldName.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurSolrProperty;

/**
 * Unit tests for TurSNTemplate.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
class TurSNTemplateTest {

    private TurSNTemplate newTemplate(
            TurSNSiteFieldRepository fieldRepository,
            TurSNSiteFieldExtRepository fieldExtRepository,
            TurSNSiteFieldExtFacetRepository facetRepository,
            TurSNSiteLocaleRepository localeRepository,
            TurSEInstanceRepository instanceRepository,
            TurConfigProperties configProperties,
            TurSearchEnginePluginFactory pluginFactory) {
        com.viglet.turing.tenant.TurTenantCoreNaming coreNaming =
                new com.viglet.turing.tenant.TurTenantCoreNaming(
                        new com.viglet.turing.tenant.TurTenantContext(configProperties));
        return new TurSNTemplate(fieldRepository, fieldExtRepository, facetRepository,
                localeRepository, instanceRepository, coreNaming, pluginFactory);
    }

    private TurConfigProperties config(boolean cloud, boolean tenancyEnabled) {
        TurConfigProperties config = new TurConfigProperties();
        TurSolrProperty solr = new TurSolrProperty();
        solr.setCloud(cloud);
        config.setSolr(solr);
        config.getTenancy().setEnabled(tenancyEnabled);
        return config;
    }

    private TurSNSite siteWithSEInstance(String instanceId) {
        TurSEInstance siteInstance = new TurSEInstance();
        siteInstance.setId(instanceId);
        TurSNSite site = new TurSNSite();
        site.setName("My Site");
        site.setTurSEInstance(siteInstance);
        return site;
    }

    @Test
    void testDefaultSNUI() {
        TurSNTemplate template = new TurSNTemplate(null, null, null, null, null, null, null);
        TurSNSite site = new TurSNSite();

        template.defaultSNUI(site);

        assertThat(site.getRowsPerPage()).isEqualTo(10);
        assertThat(site.getFacet()).isEqualTo(1);
        assertThat(site.getItemsPerFacet()).isEqualTo(10);
        assertThat(site.getHl()).isEqualTo(1);
        assertThat(site.getMlt()).isEqualTo(1);
        assertThat(site.getSpellCheck()).isEqualTo(1);
        assertThat(site.getSpellCheckFixes()).isEqualTo(1);
        assertThat(site.getThesaurus()).isZero();
        assertThat(site.getExactMatch()).isEqualTo(1);
        assertThat(site.getDefaultField()).isEqualTo(DEFAULT);
    }

    @Test
    void testCreateSNSiteCreatesDefaultsLocaleAndFields() {
        TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteFieldExtFacetRepository facetRepository = mock(TurSNSiteFieldExtFacetRepository.class);
        TurSNSiteLocaleRepository localeRepository = mock(TurSNSiteLocaleRepository.class);
        TurSEInstanceRepository instanceRepository = mock(TurSEInstanceRepository.class);
        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        TurSearchEnginePluginFactory pluginFactory = mock(TurSearchEnginePluginFactory.class);
        when(pluginFactory.getPluginForInstance(any())).thenReturn(plugin);

        TurSNSite site = siteWithSEInstance("se-1");
        TurSEInstance instance = new TurSEInstance();
        instance.setId("se-1");
        instance.setEndpointUrl("http://localhost:8983/solr");

        when(instanceRepository.findById("se-1")).thenReturn(Optional.of(instance));
        when(localeRepository.save(any(TurSNSiteLocale.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(localeRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(fieldRepository.save(any(TurSNSiteField.class))).thenAnswer(invocation -> {
            TurSNSiteField field = invocation.getArgument(0);
            field.setId(field.getName() + "-id");
            return field;
        });
        when(fieldExtRepository.save(any(TurSNSiteFieldExt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(facetRepository.save(any(TurSNSiteFieldExtFacet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TurSNTemplate template = newTemplate(fieldRepository, fieldExtRepository,
                facetRepository, localeRepository, instanceRepository, config(false, false), pluginFactory);

        template.createSNSite(site, "alex", Locale.US);

        assertThat(site.getRowsPerPage()).isEqualTo(10);
        verify(localeRepository).save(any(TurSNSiteLocale.class));
        verify(fieldRepository, times(13)).save(any(TurSNSiteField.class));
        verify(fieldExtRepository, times(13)).save(any(TurSNSiteFieldExt.class));
        verify(plugin).createIndex(eq(instance), any(TurSNSiteLocale.class), eq("my_site_en_US"), eq(Map.of()));
    }

    @Test
    void testCreateSolrCoreUsesExistingCoreAndFallbackConfigSet() {
        TurSEInstanceRepository instanceRepository = mock(TurSEInstanceRepository.class);
        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        TurSearchEnginePluginFactory pluginFactory = mock(TurSearchEnginePluginFactory.class);
        when(pluginFactory.getPluginForInstance(any())).thenReturn(plugin);

        TurSNTemplate template = newTemplate(mock(TurSNSiteFieldRepository.class),
                mock(TurSNSiteFieldExtRepository.class), mock(TurSNSiteFieldExtFacetRepository.class),
                mock(TurSNSiteLocaleRepository.class), instanceRepository, config(false, false), pluginFactory);

        TurSNSite site = siteWithSEInstance("se-1");
        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setTurSNSite(site);
        locale.setLanguage(Locale.GERMANY);
        locale.setCore("existing_core");

        TurSEInstance instance = new TurSEInstance();
        instance.setId("se-1");
        instance.setEndpointUrl("http://127.0.0.1:8984/solr");
        when(instanceRepository.findById("se-1")).thenReturn(Optional.of(instance));

        String coreName = template.createSolrCore(locale, "alex");

        assertThat(coreName).isEqualTo("existing_core");
        verify(plugin).createIndex(eq(instance), any(TurSNSiteLocale.class), eq("existing_core"), eq(Map.of()));
    }

    @Test
    void testCreateSolrCoreUsesPlainCoreNameAfterPseudoTenantRetired() {
        // T262: the legacy `<username>_<site>_<lang>` pseudo-tenant core name is
        // retired; isolation is the @TenantId discriminator, so the core name is
        // always `<site>_<lang>` regardless of the principal.
        TurSEInstanceRepository instanceRepository = mock(TurSEInstanceRepository.class);
        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        TurSearchEnginePluginFactory pluginFactory = mock(TurSearchEnginePluginFactory.class);
        when(pluginFactory.getPluginForInstance(any())).thenReturn(plugin);

        TurSNTemplate template = newTemplate(mock(TurSNSiteFieldRepository.class),
                mock(TurSNSiteFieldExtRepository.class), mock(TurSNSiteFieldExtFacetRepository.class),
                mock(TurSNSiteLocaleRepository.class), instanceRepository, config(true, true), pluginFactory);

        TurSNSite site = siteWithSEInstance("se-1");
        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setTurSNSite(site);
        locale.setLanguage(Locale.of("pt", "BR"));

        TurSEInstance instance = new TurSEInstance();
        instance.setId("se-1");
        instance.setEndpointUrl("http://solr-host:8983/solr");
        when(instanceRepository.findById("se-1")).thenReturn(Optional.of(instance));

        String coreName = template.createSolrCore(locale, "alex");

        assertThat(coreName).isEqualTo("my_site_pt_BR");
        verify(plugin).createIndex(eq(instance), any(TurSNSiteLocale.class), eq("my_site_pt_BR"), eq(Map.of()));
    }

    @Test
    void testCreateLocaleSavesLocaleWithCoreName() {
        TurSNSiteLocaleRepository localeRepository = mock(TurSNSiteLocaleRepository.class);
        TurSEInstanceRepository instanceRepository = mock(TurSEInstanceRepository.class);
        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        TurSearchEnginePluginFactory pluginFactory = mock(TurSearchEnginePluginFactory.class);
        when(pluginFactory.getPluginForInstance(any())).thenReturn(plugin);

        TurSNTemplate template = newTemplate(mock(TurSNSiteFieldRepository.class),
                mock(TurSNSiteFieldExtRepository.class), mock(TurSNSiteFieldExtFacetRepository.class),
                localeRepository, instanceRepository, config(false, false), pluginFactory);

        TurSNSite site = siteWithSEInstance("se-1");
        TurSEInstance instance = new TurSEInstance();
        instance.setId("se-1");
        instance.setEndpointUrl("http://localhost:8983/solr");
        when(instanceRepository.findById("se-1")).thenReturn(Optional.of(instance));
        when(localeRepository.save(any(TurSNSiteLocale.class))).thenAnswer(invocation -> invocation.getArgument(0));

        template.createLocale(site, "alex", Locale.of("es", "ES"));

        verify(localeRepository).save(any(TurSNSiteLocale.class));
        verify(plugin).createIndex(eq(instance), any(TurSNSiteLocale.class), eq("my_site_es_ES"), eq(Map.of()));
    }

    @Test
    void testCreateSEFieldsCreatesCopyFieldForEachLocale() {
        TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        TurSNSiteFieldExtFacetRepository facetRepository = mock(TurSNSiteFieldExtFacetRepository.class);
        TurSNSiteLocaleRepository localeRepository = mock(TurSNSiteLocaleRepository.class);
        TurSEInstanceRepository instanceRepository = mock(TurSEInstanceRepository.class);
        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        TurSearchEnginePluginFactory pluginFactory = mock(TurSearchEnginePluginFactory.class);
        when(pluginFactory.getPluginForInstance(any())).thenReturn(plugin);

        TurSNTemplate template = newTemplate(fieldRepository, fieldExtRepository,
                facetRepository, localeRepository, instanceRepository, config(false, false), pluginFactory);

        TurSNSite site = siteWithSEInstance("se-1");
        TurSEInstance instance = new TurSEInstance();
        instance.setId("se-1");
        when(instanceRepository.findById("se-1")).thenReturn(Optional.of(instance));

        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setTurSNSite(site);
        locale.setCore("core_en");
        when(localeRepository.findByTurSNSite(site)).thenReturn(List.of(locale));

        when(fieldRepository.save(any(TurSNSiteField.class))).thenAnswer(invocation -> {
            TurSNSiteField field = invocation.getArgument(0);
            field.setId(field.getName() + "-id");
            return field;
        });
        when(fieldExtRepository.save(any(TurSNSiteFieldExt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(facetRepository.save(any(TurSNSiteFieldExtFacet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        template.createSEFields(site);

        verify(fieldRepository, times(13)).save(any(TurSNSiteField.class));
        verify(fieldExtRepository, times(13)).save(any(TurSNSiteFieldExt.class));
        // createCopyField is delegated to plugin once per field × locale
        verify(plugin, times(13)).createCopyField(eq(instance), eq("core_en"), any(), any(), any(Boolean.class));
    }
}
