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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.turing.api.sn.bean.TurSNSiteMonitoringStatusBean;
import com.viglet.turing.exchange.sn.TurSNSiteContentExchangeService;
import com.viglet.turing.exchange.sn.TurSNSiteExport;
import com.viglet.turing.persistence.dto.sn.TurSNSiteDto;
import com.viglet.turing.persistence.dto.sn.TurSNSiteListDto;
import com.viglet.turing.persistence.mapper.sn.TurSNSiteMapper;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetSortEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.genai.TurSNSiteGenAiRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.sn.TurSNQueue;
import com.viglet.turing.sn.template.TurSNTemplate;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Unit tests for TurSNSiteAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteAPITest {

        private TurSNSiteAPI newApi(TurSNSiteRepository siteRepository,
                        TurSNSiteLocaleRepository localeRepository,
                        TurSNSiteGenAiRepository genAiRepository,
                        TurSNSiteExport export,
                        TurSNTemplate template,
                        TurSNQueue queue,
                        TurSearchEnginePluginFactory pluginFactory,
                        TurConfigProperties configProperties,
                        TurSNSiteMapper siteMapper) {
                return new TurSNSiteAPI(siteRepository, localeRepository, genAiRepository,
                                mock(com.viglet.turing.persistence.repository.agent.TurAIAgentRepository.class),
                                export, mock(TurSNSiteContentExchangeService.class),
                                template, queue, pluginFactory, configProperties, siteMapper);
        }

        @Test
        void testSiteListUsesMultiTenantFilter() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurConfigProperties configProperties = mock(TurConfigProperties.class);
                TurSNSiteMapper siteMapper = Mappers.getMapper(TurSNSiteMapper.class);
                TurSNSiteAPI api = newApi(siteRepository, mock(TurSNSiteLocaleRepository.class),
                                mock(TurSNSiteGenAiRepository.class), mock(TurSNSiteExport.class),
                                mock(TurSNTemplate.class), mock(TurSNQueue.class),
                                mock(TurSearchEnginePluginFactory.class), configProperties, siteMapper);
                Principal principal = () -> "Admin";
                TurSNSite site = new TurSNSite();

                when(configProperties.isMultiTenant()).thenReturn(true);
                when(siteRepository.findAllForListingByCreatedBy("admin"))
                                .thenReturn(List.of(site));

                List<TurSNSiteListDto> result = api.turSNSiteList(principal);

                assertThat(result).hasSize(1);
        }

        @Test
        void testSiteStructureHasDefaults() {
                TurSNSiteMapper siteMapper = Mappers.getMapper(TurSNSiteMapper.class);
                TurSNSiteAPI api = newApi(mock(TurSNSiteRepository.class),
                                mock(TurSNSiteLocaleRepository.class), mock(TurSNSiteGenAiRepository.class),
                                mock(TurSNSiteExport.class), mock(TurSNTemplate.class), mock(TurSNQueue.class),
                                mock(TurSearchEnginePluginFactory.class), mock(TurConfigProperties.class), siteMapper);

                TurSNSiteDto result = api.turSNSiteStructure();

                assertThat(result.getFacetSort()).isEqualTo(TurSNSiteFacetSortEnum.COUNT);
                assertThat(result.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.AND);
                assertThat(result.getTurSEInstance()).isNotNull();
                assertThat(result.getTurSNSiteGenAi()).isNotNull();
        }

        @Test
        void testSiteGetReturnsDefaultWhenMissing() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteMapper siteMapper = Mappers.getMapper(TurSNSiteMapper.class);
                TurSNSiteAPI api = newApi(siteRepository,
                                mock(TurSNSiteLocaleRepository.class), mock(TurSNSiteGenAiRepository.class),
                                mock(TurSNSiteExport.class), mock(TurSNTemplate.class), mock(TurSNQueue.class),
                                mock(TurSearchEnginePluginFactory.class), mock(TurConfigProperties.class), siteMapper);

                TurSNSiteDto result = api.turSNSiteGet("site");

                assertThat(result.getId()).isNull();
        }

        @Test
        void testSiteUpdateCopiesFields() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteGenAiRepository genAiRepository = mock(TurSNSiteGenAiRepository.class);
                TurSNSiteMapper siteMapper = Mappers.getMapper(TurSNSiteMapper.class);
                TurSNSiteAPI api = newApi(siteRepository,
                                mock(TurSNSiteLocaleRepository.class), genAiRepository,
                                mock(TurSNSiteExport.class), mock(TurSNTemplate.class), mock(TurSNQueue.class),
                                mock(TurSearchEnginePluginFactory.class), mock(TurConfigProperties.class), siteMapper);
                TurSNSite existing = new TurSNSite();
                TurSNSiteGenAi genAi = new TurSNSiteGenAi();
                existing.setTurSNSiteGenAi(genAi);
                TurSNSiteDto payload = new TurSNSiteDto();
                payload.setName("New");
                payload.setDescription("Desc");
                payload.setHl(1);
                payload.setTurSNSiteGenAi(new TurSNSiteGenAi());

                when(siteRepository.findByIdNoCache("site")).thenReturn(Optional.of(existing));
                when(siteRepository.findByNameIgnoreCase("New")).thenReturn(Optional.empty());

                TurSNSiteDto result = (TurSNSiteDto) api.turSNSiteUpdate("site", payload).getBody();

                assertThat(result).isNotNull();
                assertThat(result.getName()).isEqualTo("New");
                assertThat(result.getDescription()).isEqualTo("Desc");
                verify(siteRepository).save(existing);
                verify(genAiRepository).save(any(TurSNSiteGenAi.class));
        }

        @Test
        void testSiteDeleteDoesNotDeleteCores() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteMapper siteMapper = Mappers.getMapper(TurSNSiteMapper.class);
                TurSearchEnginePluginFactory pluginFactory = mock(TurSearchEnginePluginFactory.class);
                TurSNSiteAPI api = newApi(siteRepository, mock(TurSNSiteLocaleRepository.class),
                                mock(TurSNSiteGenAiRepository.class), mock(TurSNSiteExport.class),
                                mock(TurSNTemplate.class), mock(TurSNQueue.class),
                                pluginFactory, mock(TurConfigProperties.class), siteMapper);
                TurSNSite site = new TurSNSite();
                TurSEInstance instance = new TurSEInstance();
                instance.setEndpointUrl("http://localhost:8983/solr");
                site.setTurSEInstance(instance);

                when(siteRepository.findById("site")).thenReturn(Optional.of(site));

                boolean result = api.turSNSiteDelete("site");

                assertThat(result).isTrue();
                verify(pluginFactory, never()).getPluginForSite(any());
                verify(siteRepository).delete(site);
        }

        @Test
        void testSiteDeleteComplexRemovesReferencedObjectsBeforeDelete() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteGenAiRepository genAiRepository = mock(TurSNSiteGenAiRepository.class);
                TurSearchEnginePluginFactory pluginFactory = mock(TurSearchEnginePluginFactory.class);
                TurSNSiteMapper siteMapper = Mappers.getMapper(TurSNSiteMapper.class);
                TurSNSiteAPI api = newApi(siteRepository, mock(TurSNSiteLocaleRepository.class),
                                genAiRepository, mock(TurSNSiteExport.class),
                                mock(TurSNTemplate.class), mock(TurSNQueue.class),
                                pluginFactory, mock(TurConfigProperties.class), siteMapper);

                TurSNSite site = new TurSNSite();
                TurSEInstance instance = new TurSEInstance();
                instance.setEndpointUrl("http://localhost:8983/solr");
                site.setTurSEInstance(instance);
                TurSNSiteGenAi genAi = new TurSNSiteGenAi();
                site.setTurSNSiteGenAi(genAi);

                site.getTurSNSiteFields().add(new TurSNSiteField());
                site.getTurSNSiteFieldExts().add(new TurSNSiteFieldExt());
                site.getTurSNSiteSpotlights().add(new TurSNSiteSpotlight());
                site.getTurSNRankingExpressions().add(new TurSNRankingExpression());

                TurSNSiteMergeProviders mergeProvider1 = new TurSNSiteMergeProviders();
                TurSNSiteMergeProviders mergeProvider2 = new TurSNSiteMergeProviders();
                site.getTurSNSiteMergeProviders().add(mergeProvider1);
                site.getTurSNSiteMergeProviders().add(mergeProvider2);

                when(siteRepository.findById("site")).thenReturn(Optional.of(site));

                boolean result = api.turSNSiteDelete("site");

                assertThat(result).isTrue();
                assertThat(site.getTurSNSiteFields()).isEmpty();
                assertThat(site.getTurSNSiteFieldExts()).isEmpty();
                assertThat(site.getTurSNSiteSpotlights()).isEmpty();
                assertThat(site.getTurSNRankingExpressions()).isEmpty();
                assertThat(site.getTurSNSiteMergeProviders()).isEmpty();
                assertThat(site.getTurSNSiteGenAi()).isNull();
                verify(pluginFactory, never()).getPluginForSite(any());

                InOrder inOrder = inOrder(siteRepository);
                inOrder.verify(siteRepository).flush();
                inOrder.verify(siteRepository).delete(site);
                verify(genAiRepository).delete(genAi);
        }

        @Test
        void testSiteAddPersistsAndCreatesTemplate() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteGenAiRepository genAiRepository = mock(TurSNSiteGenAiRepository.class);
                TurSNTemplate template = mock(TurSNTemplate.class);
                TurSNSiteMapper siteMapper = Mappers.getMapper(TurSNSiteMapper.class);
                TurSNSiteAPI api = newApi(siteRepository,
                                mock(TurSNSiteLocaleRepository.class), genAiRepository,
                                mock(TurSNSiteExport.class), template, mock(TurSNQueue.class),
                                mock(TurSearchEnginePluginFactory.class), mock(TurConfigProperties.class), siteMapper);
                TurSNSiteDto site = new TurSNSiteDto();
                site.setTurSNSiteGenAi(new TurSNSiteGenAi());
                Principal principal = () -> "admin";
                when(siteRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());

                TurSNSiteDto result = (TurSNSiteDto) api.turSNSiteAdd(site, principal).getBody();

                assertThat(result).isNotNull();
                assertThat(result.getTurSNSiteGenAi()).isNotNull();
                verify(genAiRepository).save(any(TurSNSiteGenAi.class));
                verify(siteRepository, times(1)).save(any(TurSNSite.class));
                verify(template).createSNSite(any(TurSNSite.class), org.mockito.ArgumentMatchers.eq("admin"),
                                org.mockito.ArgumentMatchers.eq(Locale.US));
        }

        @Test
        void testSiteExportReturnsStreamingBody() {
                TurSNSiteExport export = mock(TurSNSiteExport.class);
                TurSNSiteMapper siteMapper = Mappers.getMapper(TurSNSiteMapper.class);
                TurSNSiteAPI api = newApi(mock(TurSNSiteRepository.class),
                                mock(TurSNSiteLocaleRepository.class), mock(TurSNSiteGenAiRepository.class),
                                export, mock(TurSNTemplate.class), mock(TurSNQueue.class),
                                mock(TurSearchEnginePluginFactory.class), mock(TurConfigProperties.class), siteMapper);
                HttpServletResponse response = mock(HttpServletResponse.class);
                StreamingResponseBody expected = outputStream -> outputStream.write(new byte[0]);

                when(export.exportAll(response)).thenReturn(expected);

                StreamingResponseBody body = api.turSNSiteExportAll(response);

                assertThat(body).isSameAs(expected);
        }

        @Test
        void testMonitoringStatusReturnsCounts() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteLocaleRepository localeRepository = mock(TurSNSiteLocaleRepository.class);
                TurSNQueue queue = mock(TurSNQueue.class);
                TurSearchEnginePluginFactory pluginFactory = mock(TurSearchEnginePluginFactory.class);
                TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
                TurSNSiteMapper siteMapper = Mappers.getMapper(TurSNSiteMapper.class);
                TurSNSiteAPI api = newApi(siteRepository, localeRepository,
                                mock(TurSNSiteGenAiRepository.class), mock(TurSNSiteExport.class),
                                mock(TurSNTemplate.class), queue, pluginFactory,
                                mock(TurConfigProperties.class), siteMapper);
                TurSNSite site = new TurSNSite();
                TurSNSiteLocale locale = new TurSNSiteLocale();

                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(localeRepository.findByTurSNSite(org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.eq(site)))
                                .thenReturn(List.of(locale));
                when(queue.getQueueSize()).thenReturn(2);
                when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
                when(plugin.getDocumentTotal(locale)).thenReturn(5L);

                TurSNSiteMonitoringStatusBean result = api.turSNSiteMonitoringStatus("site");

                assertThat(result.getQueue()).isEqualTo(2);
                assertThat(result.getDocuments()).isEqualTo(5);
        }
}
