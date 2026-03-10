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
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.turing.api.sn.bean.TurSNSiteMonitoringStatusBean;
import com.viglet.turing.exchange.sn.TurSNSiteExport;
import com.viglet.turing.persistence.dto.sn.TurSNSiteDto;
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
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.sn.TurSNQueue;
import com.viglet.turing.sn.template.TurSNTemplate;
import com.viglet.turing.elasticsearch.TurElasticsearch;
import com.viglet.turing.elasticsearch.TurElasticsearchInstanceProcess;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.solr.TurSolrUtils;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Unit tests for TurSNSiteAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteAPITest {

        @Test
        void testSiteListUsesMultiTenantFilter() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteLocaleRepository localeRepository = mock(TurSNSiteLocaleRepository.class);
                TurSNSiteGenAiRepository genAiRepository = mock(TurSNSiteGenAiRepository.class);
                TurSNSiteExport export = mock(TurSNSiteExport.class);
                TurSNTemplate template = mock(TurSNTemplate.class);
                TurSNQueue queue = mock(TurSNQueue.class);
                TurSolrInstanceProcess solrInstanceProcess = mock(TurSolrInstanceProcess.class);
                TurSolr turSolr = mock(TurSolr.class);
                TurConfigProperties configProperties = mock(TurConfigProperties.class);
                TurSNSiteMapper siteMapper = Mappers.getMapper(TurSNSiteMapper.class);
                TurSNSiteAPI api = new TurSNSiteAPI(siteRepository, localeRepository, genAiRepository, export,
                                template, queue, solrInstanceProcess, turSolr,
                                mock(TurElasticsearchInstanceProcess.class), mock(TurElasticsearch.class),
                                configProperties, siteMapper);
                Principal principal = () -> "Admin";
                TurSNSite site = new TurSNSite();

                when(configProperties.isMultiTenant()).thenReturn(true);
                when(siteRepository.findByCreatedBy(org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.eq("admin")))
                                .thenReturn(List.of(site));

                List<TurSNSiteDto> result = api.turSNSiteList(principal);

                assertThat(result).hasSize(1);
        }

        @Test
        void testSiteStructureHasDefaults() {
                TurSNSiteMapper siteMapper = Mappers.getMapper(TurSNSiteMapper.class);
                TurSNSiteAPI api = new TurSNSiteAPI(mock(TurSNSiteRepository.class),
                                mock(TurSNSiteLocaleRepository.class), mock(TurSNSiteGenAiRepository.class),
                                mock(TurSNSiteExport.class), mock(TurSNTemplate.class), mock(TurSNQueue.class),
                                mock(TurSolrInstanceProcess.class), mock(TurSolr.class),
                                mock(TurElasticsearchInstanceProcess.class), mock(TurElasticsearch.class),
                                mock(TurConfigProperties.class), siteMapper);

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
                TurSNSiteAPI api = new TurSNSiteAPI(siteRepository,
                                mock(TurSNSiteLocaleRepository.class), mock(TurSNSiteGenAiRepository.class),
                                mock(TurSNSiteExport.class), mock(TurSNTemplate.class), mock(TurSNQueue.class),
                                mock(TurSolrInstanceProcess.class), mock(TurSolr.class),
                                mock(TurElasticsearchInstanceProcess.class), mock(TurElasticsearch.class),
                                mock(TurConfigProperties.class), siteMapper);

                TurSNSiteDto result = api.turSNSiteGet("site");

                assertThat(result.getId()).isNull();
        }

        @Test
        void testSiteUpdateCopiesFields() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteGenAiRepository genAiRepository = mock(TurSNSiteGenAiRepository.class);
                TurSNSiteMapper siteMapper = Mappers.getMapper(TurSNSiteMapper.class);
                TurSNSiteAPI api = new TurSNSiteAPI(siteRepository,
                                mock(TurSNSiteLocaleRepository.class), genAiRepository,
                                mock(TurSNSiteExport.class), mock(TurSNTemplate.class), mock(TurSNQueue.class),
                                mock(TurSolrInstanceProcess.class), mock(TurSolr.class),
                                mock(TurElasticsearchInstanceProcess.class), mock(TurElasticsearch.class),
                                mock(TurConfigProperties.class), siteMapper);
                TurSNSite existing = new TurSNSite();
                TurSNSiteGenAi genAi = new TurSNSiteGenAi();
                existing.setTurSNSiteGenAi(genAi);
                TurSNSiteDto payload = new TurSNSiteDto();
                payload.setName("New");
                payload.setDescription("Desc");
                payload.setHl(1);
                payload.setTurSNSiteGenAi(new TurSNSiteGenAi());

                when(siteRepository.findById("site")).thenReturn(Optional.of(existing));

                TurSNSiteDto result = api.turSNSiteUpdate("site", payload);

                assertThat(result.getName()).isEqualTo("New");
                assertThat(result.getDescription()).isEqualTo("Desc");
                verify(siteRepository).save(existing);
                verify(genAiRepository).save(any(TurSNSiteGenAi.class));
        }

        @Test
        void testSiteDeleteDeletesCores() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteLocaleRepository localeRepository = mock(TurSNSiteLocaleRepository.class);
                TurSNSiteMapper siteMapper = Mappers.getMapper(TurSNSiteMapper.class);
                TurSNSiteAPI api = new TurSNSiteAPI(siteRepository, localeRepository,
                                mock(TurSNSiteGenAiRepository.class), mock(TurSNSiteExport.class),
                                mock(TurSNTemplate.class), mock(TurSNQueue.class), mock(TurSolrInstanceProcess.class),
                                mock(TurSolr.class),
                                mock(TurElasticsearchInstanceProcess.class), mock(TurElasticsearch.class),
                                mock(TurConfigProperties.class), siteMapper);
                TurSNSite site = new TurSNSite();
                TurSEInstance instance = new TurSEInstance();
                instance.setEndpointUrl("http://localhost:8983/solr");
                site.setTurSEInstance(instance);
                TurSNSiteLocale locale = new TurSNSiteLocale();
                locale.setCore("core1");

                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(localeRepository.findByTurSNSite(org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.eq(site)))
                                .thenReturn(List.of(locale));

                try (MockedStatic<TurSolrUtils> utils = org.mockito.Mockito.mockStatic(TurSolrUtils.class)) {
                        boolean result = api.turSNSiteDelete("site");

                        assertThat(result).isTrue();
                        utils.verify(() -> TurSolrUtils.deleteCore(instance, "core1"));
                        verify(siteRepository).delete(site);
                }
        }

        @Test
        void testSiteDeleteComplexRemovesReferencedObjectsBeforeDelete() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteLocaleRepository localeRepository = mock(TurSNSiteLocaleRepository.class);
                TurSNSiteGenAiRepository genAiRepository = mock(TurSNSiteGenAiRepository.class);
                TurSNSiteMapper siteMapper = Mappers.getMapper(TurSNSiteMapper.class);
                TurSNSiteAPI api = new TurSNSiteAPI(siteRepository, localeRepository,
                                genAiRepository, mock(TurSNSiteExport.class),
                                mock(TurSNTemplate.class), mock(TurSNQueue.class), mock(TurSolrInstanceProcess.class),
                                mock(TurSolr.class),
                                mock(TurElasticsearchInstanceProcess.class), mock(TurElasticsearch.class),
                                mock(TurConfigProperties.class), siteMapper);

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

                TurSNSiteLocale locale1 = new TurSNSiteLocale();
                locale1.setCore("core1");
                TurSNSiteLocale locale2 = new TurSNSiteLocale();
                locale2.setCore("core2");

                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(localeRepository.findByTurSNSite(org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.eq(site)))
                                .thenReturn(List.of(locale1, locale2));

                try (MockedStatic<TurSolrUtils> utils = org.mockito.Mockito.mockStatic(TurSolrUtils.class)) {
                        boolean result = api.turSNSiteDelete("site");

                        assertThat(result).isTrue();
                        assertThat(site.getTurSNSiteFields()).isEmpty();
                        assertThat(site.getTurSNSiteFieldExts()).isEmpty();
                        assertThat(site.getTurSNSiteSpotlights()).isEmpty();
                        assertThat(site.getTurSNRankingExpressions()).isEmpty();
                        assertThat(site.getTurSNSiteMergeProviders()).isEmpty();
                        assertThat(site.getTurSNSiteGenAi()).isNull();
                        utils.verify(() -> TurSolrUtils.deleteCore(instance, "core1"));
                        utils.verify(() -> TurSolrUtils.deleteCore(instance, "core2"));

                        InOrder inOrder = inOrder(siteRepository);
                        inOrder.verify(siteRepository).flush();
                        inOrder.verify(siteRepository).delete(site);
                        verify(genAiRepository).delete(genAi);
                }
        }

        @Test
        void testSiteAddPersistsAndCreatesTemplate() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteGenAiRepository genAiRepository = mock(TurSNSiteGenAiRepository.class);
                TurSNTemplate template = mock(TurSNTemplate.class);
                TurSNSiteMapper siteMapper = Mappers.getMapper(TurSNSiteMapper.class);
                TurSNSiteAPI api = new TurSNSiteAPI(siteRepository,
                                mock(TurSNSiteLocaleRepository.class), genAiRepository,
                                mock(TurSNSiteExport.class), template, mock(TurSNQueue.class),
                                mock(TurSolrInstanceProcess.class), mock(TurSolr.class),
                                mock(TurElasticsearchInstanceProcess.class), mock(TurElasticsearch.class),
                                mock(TurConfigProperties.class), siteMapper);
                TurSNSiteDto site = new TurSNSiteDto();
                site.setTurSNSiteGenAi(new TurSNSiteGenAi());
                Principal principal = () -> "admin";

                TurSNSiteDto result = api.turSNSiteAdd(site, principal);

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
                TurSNSiteAPI api = new TurSNSiteAPI(mock(TurSNSiteRepository.class),
                                mock(TurSNSiteLocaleRepository.class), mock(TurSNSiteGenAiRepository.class),
                                export, mock(TurSNTemplate.class), mock(TurSNQueue.class),
                                mock(TurSolrInstanceProcess.class), mock(TurSolr.class),
                                mock(TurElasticsearchInstanceProcess.class), mock(TurElasticsearch.class),
                                mock(TurConfigProperties.class), siteMapper);
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
                TurSolrInstanceProcess solrInstanceProcess = mock(TurSolrInstanceProcess.class);
                TurSolr turSolr = mock(TurSolr.class);
                TurSNSiteMapper siteMapper = Mappers.getMapper(TurSNSiteMapper.class);
                TurSNSiteAPI api = new TurSNSiteAPI(siteRepository, localeRepository,
                                mock(TurSNSiteGenAiRepository.class), mock(TurSNSiteExport.class),
                                mock(TurSNTemplate.class), queue, solrInstanceProcess, turSolr,
                                mock(TurElasticsearchInstanceProcess.class), mock(TurElasticsearch.class),
                                mock(TurConfigProperties.class), siteMapper);
                TurSNSite site = new TurSNSite();
                TurSNSiteLocale locale = new TurSNSiteLocale();
                TurSolrInstance instance = mock(TurSolrInstance.class);

                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(localeRepository.findByTurSNSite(org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.eq(site)))
                                .thenReturn(List.of(locale));
                when(queue.getQueueSize()).thenReturn(2);
                when(solrInstanceProcess.initSolrInstance(locale)).thenReturn(Optional.of(instance));
                when(turSolr.getDocumentTotal(instance)).thenReturn(5L);

                TurSNSiteMonitoringStatusBean result = api.turSNSiteMonitoringStatus("site");

                assertThat(result.getQueue()).isEqualTo(2);
                assertThat(result.getDocuments()).isEqualTo(5);
        }
}
