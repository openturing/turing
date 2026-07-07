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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.mapper.sn.field.TurSNSiteFieldExtMapper;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.sn.template.TurSNTemplate;
import com.viglet.turing.solr.TurSolrFieldAction;
import com.viglet.turing.solr.TurSolrUtils;

/**
 * Unit tests for TurSNSiteFieldExtAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
class TurSNSiteFieldExtAPITest {

        private static TurSNSiteFieldExtDto asDto(TurSNSiteFieldExt fieldExt) {
                TurSNSiteFieldExtDto dto = new TurSNSiteFieldExtDto(fieldExt);
                dto.setFacetLocales(fieldExt.getFacetLocales());
                return dto;
        }

        private static TurSNSiteFieldExtMapper createFieldExtMapper() {
                IdentityHashMap<TurSNSiteFieldExtDto, TurSNSiteFieldExt> cache = new IdentityHashMap<>();
                TurSNSiteFieldExtMapper mapper = mock(TurSNSiteFieldExtMapper.class);
                when(mapper.toDto(any(TurSNSiteFieldExt.class))).thenAnswer(invocation -> {
                        TurSNSiteFieldExt entity = invocation.getArgument(0);
                        TurSNSiteFieldExtDto dto = asDto(entity);
                        cache.put(dto, entity);
                        return dto;
                });
                when(mapper.toDtoList(any())).thenAnswer(invocation -> {
                        List<TurSNSiteFieldExt> entities = invocation.getArgument(0);
                        List<TurSNSiteFieldExtDto> dtos = new ArrayList<>();
                        if (entities != null) {
                                entities.forEach(entity -> {
                                        TurSNSiteFieldExtDto dto = asDto(entity);
                                        cache.put(dto, entity);
                                        dtos.add(dto);
                                });
                        }
                        return dtos;
                });
                when(mapper.toEntity(any(TurSNSiteFieldExtDto.class))).thenAnswer(invocation -> {
                        TurSNSiteFieldExtDto dto = invocation.getArgument(0);
                        if (dto == null) {
                                return null;
                        }
                        if (cache.containsKey(dto)) {
                                return cache.get(dto);
                        }
                        TurSNSiteFieldExt entity = new TurSNSiteFieldExt();
                        entity.setId(dto.getId());
                        entity.setExternalId(dto.getExternalId());
                        entity.setName(dto.getName());
                        entity.setDescription(dto.getDescription());
                        entity.setFacetName(dto.getFacetName());
                        entity.setSnType(dto.getSnType());
                        entity.setType(dto.getType());
                        entity.setMultiValued(dto.getMultiValued());
                        entity.setFacet(dto.getFacet());
                        entity.setFacetRange(dto.getFacetRange());
                        entity.setFacetType(dto.getFacetType());
                        entity.setFacetItemType(dto.getFacetItemType());
                        entity.setSecondaryFacet(dto.getSecondaryFacet());
                        entity.setShowAllFacetItems(dto.getShowAllFacetItems());
                        entity.setHl(dto.getHl());
                        entity.setMlt(dto.getMlt());
                        entity.setEnabled(dto.getEnabled());
                        entity.setRequired(dto.getRequired());
                        entity.setDefaultValue(dto.getDefaultValue());
                        entity.setTurSNSite(dto.getTurSNSite());
                        if (dto.getFacetLocales() != null) {
                                Set<TurSNSiteFieldExtFacet> facetLocales = dto.getFacetLocales().stream()
                                                .map(facetDto -> {
                                                        TurSNSiteFieldExtFacet facet = new TurSNSiteFieldExtFacet();
                                                        facet.setId(facetDto.getId());
                                                        facet.setLocale(facetDto.getLocale());
                                                        facet.setLabel(facetDto.getLabel());
                                                        return facet;
                                                })
                                                .collect(java.util.stream.Collectors.toSet());
                                entity.setFacetLocales(facetLocales);
                        }
                        return entity;
                });
                return mapper;
        }

        private static TurSNSiteFieldExtAPI newApi(TurSNSiteRepository siteRepository,
                        TurSNSiteFieldExtRepository fieldExtRepository,
                        TurSNSiteFieldExtFacetRepository facetRepository,
                        TurSNSiteFieldRepository fieldRepository,
                        TurSEInstanceRepository instanceRepository,
                        TurSNTemplate template) {
                return new TurSNSiteFieldExtAPI(siteRepository, fieldExtRepository, facetRepository, fieldRepository,
                                instanceRepository, template, createFieldExtMapper());
        }

        private static Object invokePrivate(Object target, String methodName, Class<?>[] parameterTypes,
                        Object... args) throws Exception {
                Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return method.invoke(target, args);
        }

        @Test
        void testFieldExtListReturnsEmptyWhenSiteMissing() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteFieldExtAPI api = newApi(siteRepository,
                                mock(TurSNSiteFieldExtRepository.class), mock(TurSNSiteFieldExtFacetRepository.class),
                                mock(TurSNSiteFieldRepository.class),
                                mock(TurSEInstanceRepository.class),
                                mock(TurSNTemplate.class));

                when(siteRepository.findById("site")).thenReturn(Optional.empty());

                List<TurSNSiteFieldExtDto> result = api.turSNSiteFieldExtList("site");

                assertThat(result).isEmpty();
        }

        @Test
        void testFieldExtListCreatesFieldsWhenSiteHasNoFields() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
                TurSNTemplate template = mock(TurSNTemplate.class);
                TurSNSiteFieldExtAPI api = newApi(siteRepository,
                                fieldExtRepository, mock(TurSNSiteFieldExtFacetRepository.class),
                                fieldRepository,
                                mock(TurSEInstanceRepository.class),
                                template);

                TurSNSite site = new TurSNSite();
                TurSNSiteField field = new TurSNSiteField();
                field.setId("field-id");
                field.setName("title");
                field.setType(TurSEFieldType.STRING);
                field.setMultiValued(0);

                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(fieldRepository.findByTurSNSite(site))
                                .thenReturn(Collections.emptyList())
                                .thenReturn(List.of(field));
                when(fieldExtRepository.findByTurSNSite(any(), org.mockito.ArgumentMatchers.eq(site)))
                                .thenReturn(new ArrayList<>())
                                .thenReturn(new ArrayList<>());
                when(fieldExtRepository.save(any(TurSNSiteFieldExt.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                List<TurSNSiteFieldExtDto> result = api.turSNSiteFieldExtList("site");

                verify(template).createSEFields(site);
                verify(fieldExtRepository).deleteByTurSNSiteAndSnType(site, TurSNFieldType.NER);
                verify(fieldExtRepository).save(any(TurSNSiteFieldExt.class));
                assertThat(result).isEmpty();
        }

        @Test
        void testFieldExtListRemovesDuplicatedSEField() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
                TurSNSiteFieldExtAPI api = newApi(siteRepository,
                                fieldExtRepository, mock(TurSNSiteFieldExtFacetRepository.class),
                                fieldRepository,
                                mock(TurSEInstanceRepository.class),
                                mock(TurSNTemplate.class));

                TurSNSite site = new TurSNSite();
                TurSNSiteField field = new TurSNSiteField();
                field.setId("field-id");
                field.setName("title");
                field.setType(TurSEFieldType.STRING);
                field.setMultiValued(0);

                TurSNSiteFieldExt duplicated = new TurSNSiteFieldExt();
                duplicated.setSnType(TurSNFieldType.SE);
                duplicated.setExternalId("field-id");

                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(fieldRepository.findByTurSNSite(site)).thenReturn(List.of(field));
                when(fieldExtRepository.findByTurSNSite(any(), org.mockito.ArgumentMatchers.eq(site)))
                                .thenReturn(new ArrayList<>(List.of(duplicated)))
                                .thenReturn(new ArrayList<>());

                api.turSNSiteFieldExtList("site");

                verify(fieldExtRepository, never()).save(any(TurSNSiteFieldExt.class));
        }

        @Test
        void testFieldExtGetReturnsFacetLocales() {
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteFieldExtFacetRepository facetRepository = mock(TurSNSiteFieldExtFacetRepository.class);
                TurSNSiteFieldExtAPI api = newApi(mock(TurSNSiteRepository.class),
                                fieldExtRepository, facetRepository, mock(TurSNSiteFieldRepository.class),
                                mock(TurSEInstanceRepository.class),
                                mock(TurSNTemplate.class));

                when(fieldExtRepository.findById("id")).thenReturn(Optional.empty());

                TurSNSiteFieldExtDto result = api.turSNSiteFieldExtGet("site", "id");

                assertThat(result).isNotNull();
                verify(facetRepository).findByTurSNSiteFieldExt(any(TurSNSiteFieldExt.class));
        }

        @Test
        void testFieldExtUpdateSetsFacetPositionWhenMissing() {
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteFieldExtAPI api = newApi(siteRepository,
                                fieldExtRepository, mock(TurSNSiteFieldExtFacetRepository.class),
                                fieldRepository,
                                mock(TurSEInstanceRepository.class), mock(TurSNTemplate.class));
                TurSNSite site = new TurSNSite();
                TurSNSiteFieldExt existing = new TurSNSiteFieldExt();
                existing.setSnType(TurSNFieldType.SE);
                existing.setExternalId("ext");
                existing.setTurSNSite(site);
                TurSNSiteFieldExt payload = new TurSNSiteFieldExt();
                payload.setName("title");
                payload.setFacet(1);
                payload.setSnType(TurSNFieldType.SE);
                payload.setExternalId("ext");
                payload.setTurSNSite(site);
                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(fieldExtRepository.findById("id")).thenReturn(Optional.of(existing));
                when(fieldExtRepository.findMaxFacetPosition(site)).thenReturn(Optional.of(3));
                when(fieldRepository.findById("ext")).thenReturn(Optional.empty());

                TurSNSiteFieldExtDto result = api.turSNSiteFieldExtUpdate("site", "id", asDto(payload));

                assertThat(result).isNotNull();
                assertThat(existing.getFacetPosition()).isEqualTo(4);
        }

        @Test
        void testFieldExtUpdateResetsFacetPositionWhenFacetDisabled() {
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteFieldExtAPI api = newApi(siteRepository,
                                fieldExtRepository, mock(TurSNSiteFieldExtFacetRepository.class),
                                mock(TurSNSiteFieldRepository.class),
                                mock(TurSEInstanceRepository.class), mock(TurSNTemplate.class));
                TurSNSite site = new TurSNSite();
                TurSNSiteFieldExt existing = new TurSNSiteFieldExt();
                existing.setFacetPosition(7);
                existing.setSnType(TurSNFieldType.SE);
                TurSNSiteFieldExt payload = new TurSNSiteFieldExt();
                payload.setFacet(0);
                payload.setSnType(TurSNFieldType.SE);

                when(fieldExtRepository.findById("id")).thenReturn(Optional.of(existing));
                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(fieldExtRepository.save(ArgumentMatchers.any(TurSNSiteFieldExt.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                api.turSNSiteFieldExtUpdate("site", "id", asDto(payload));

                assertThat(existing.getFacetPosition()).isZero();
        }

        @Test
        void testFieldExtUpdateUsesProvidedFacetPosition() {
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteFieldExtAPI api = newApi(siteRepository,
                                fieldExtRepository, mock(TurSNSiteFieldExtFacetRepository.class),
                                mock(TurSNSiteFieldRepository.class),
                                mock(TurSEInstanceRepository.class), mock(TurSNTemplate.class));
                TurSNSite site = new TurSNSite();
                TurSNSiteFieldExt existing = new TurSNSiteFieldExt();
                existing.setSnType(TurSNFieldType.SE);
                TurSNSiteFieldExt payload = new TurSNSiteFieldExt();
                payload.setFacet(1);
                payload.setFacetPosition(9);
                payload.setSnType(TurSNFieldType.SE);

                when(fieldExtRepository.findById("id")).thenReturn(Optional.of(existing));
                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(fieldExtRepository.save(ArgumentMatchers.any(TurSNSiteFieldExt.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                api.turSNSiteFieldExtUpdate("site", "id", asDto(payload));

                assertThat(existing.getFacetPosition()).isEqualTo(1);
                verify(fieldExtRepository).findMaxFacetPosition(ArgumentMatchers.any(TurSNSite.class));
        }

        @Test
        void testFieldExtUpdateLinksFacetLocalesToFieldExt() {
                TurSNSiteFieldExtFacetRepository facetRepo = mock(TurSNSiteFieldExtFacetRepository.class);
                TurSNSiteFieldExtRepository fieldExtRepo = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteRepository siteRepo = mock(TurSNSiteRepository.class);
                TurSNSiteFieldRepository fieldRepo = mock(TurSNSiteFieldRepository.class);

                TurSNSiteFieldExtAPI api = newApi(
                                siteRepo, fieldExtRepo, facetRepo,
                                fieldRepo,
                                mock(TurSEInstanceRepository.class), mock(TurSNTemplate.class));

                TurSNSite site = new TurSNSite();

                TurSNSiteFieldExt existing = new TurSNSiteFieldExt();
                existing.setSnType(TurSNFieldType.SE);

                TurSNSiteFieldExt payload = new TurSNSiteFieldExt();
                payload.setSnType(TurSNFieldType.SE);
                payload.setExternalId("ext-id");

                Set<TurSNSiteFieldExtFacet> payloadFacets = new HashSet<>();
                TurSNSiteFieldExtFacet enFacet = new TurSNSiteFieldExtFacet();
                enFacet.setLocale(Locale.ENGLISH);
                enFacet.setLabel("EN");
                TurSNSiteFieldExtFacet frFacet = new TurSNSiteFieldExtFacet();
                frFacet.setLocale(Locale.FRENCH);
                frFacet.setLabel("FR");
                payloadFacets.add(enFacet);
                payloadFacets.add(frFacet);
                payload.setFacetLocales(payloadFacets);

                when(fieldExtRepo.findById("id")).thenReturn(Optional.of(existing));
                when(siteRepo.findById("site")).thenReturn(Optional.of(site));
                when(fieldExtRepo.findMaxFacetPosition(site)).thenReturn(Optional.of(1));
                when(facetRepo.saveAll(any())).thenAnswer(inv -> {
                        Iterable<?> it = inv.getArgument(0);
                        List<Object> list = new ArrayList<>();
                        it.forEach(list::add);
                        return list;
                });

                when(fieldExtRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
                when(fieldRepo.findById("ext-id")).thenReturn(Optional.of(new TurSNSiteField()));

                TurSNSiteFieldExtDto result = api.turSNSiteFieldExtUpdate("site", "id", asDto(payload));

                assertThat(result.getFacetLocales()).hasSize(2);
                assertThat(result.getFacetLocales())
                                .anySatisfy(f -> assertThat(f.getLocale()).isEqualTo(Locale.ENGLISH));
                assertThat(result.getFacetLocales())
                                .anySatisfy(f -> assertThat(f.getLocale()).isEqualTo(Locale.FRENCH));
        }

        @Test
        void testFieldExtUpdateReturnsDefaultWhenIdNotFound() {
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteFieldExtAPI api = newApi(mock(TurSNSiteRepository.class),
                                fieldExtRepository, mock(TurSNSiteFieldExtFacetRepository.class),
                                mock(TurSNSiteFieldRepository.class),
                                mock(TurSEInstanceRepository.class), mock(TurSNTemplate.class));

                when(fieldExtRepository.findById("id")).thenReturn(Optional.empty());

                TurSNSiteFieldExtDto result = api.turSNSiteFieldExtUpdate("site", "id", new TurSNSiteFieldExtDto());

                assertThat(result.getId()).isNull();
        }

        @Test
        void testFieldExtDeleteDeletesExternalFieldWhenSE() {
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
                TurSNSiteFieldExtAPI api = newApi(mock(TurSNSiteRepository.class),
                                fieldExtRepository, mock(TurSNSiteFieldExtFacetRepository.class),
                                fieldRepository,
                                mock(TurSEInstanceRepository.class),
                                mock(TurSNTemplate.class));
                TurSNSiteFieldExt existing = new TurSNSiteFieldExt();
                existing.setSnType(TurSNFieldType.SE);
                existing.setExternalId("ext");

                when(fieldExtRepository.findById("id")).thenReturn(Optional.of(existing));

                boolean result = api.turSNSiteFieldExtDelete("site", "id");

                assertThat(result).isTrue();
                verify(fieldRepository).delete("ext");
                verify(fieldExtRepository).delete("id");
        }

        @Test
        void testFieldExtDeleteReturnsFalseWhenFieldExtMissing() {
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
                TurSNSiteFieldExtAPI api = newApi(mock(TurSNSiteRepository.class),
                                fieldExtRepository, mock(TurSNSiteFieldExtFacetRepository.class),
                                fieldRepository,
                                mock(TurSEInstanceRepository.class),
                                mock(TurSNTemplate.class));

                when(fieldExtRepository.findById("id")).thenReturn(Optional.empty());

                boolean result = api.turSNSiteFieldExtDelete("site", "id");

                assertThat(result).isFalse();
                verify(fieldRepository, never()).delete(any(String.class));
        }

        @Test
        void testFieldExtDeleteSkipsExternalFieldWhenNotSE() {
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
                TurSNSiteFieldExtAPI api = newApi(mock(TurSNSiteRepository.class),
                                fieldExtRepository, mock(TurSNSiteFieldExtFacetRepository.class),
                                fieldRepository,
                                mock(TurSEInstanceRepository.class),
                                mock(TurSNTemplate.class));
                TurSNSiteFieldExt existing = new TurSNSiteFieldExt();
                existing.setSnType(TurSNFieldType.THESAURUS);
                existing.setExternalId("ext");

                when(fieldExtRepository.findById("id")).thenReturn(Optional.of(existing));

                boolean result = api.turSNSiteFieldExtDelete("site", "id");

                assertThat(result).isTrue();
                verify(fieldRepository, never()).delete(any(String.class));
                verify(fieldExtRepository).delete("id");
        }

        @Test
        void testFieldExtAddReturnsDefaultWhenSiteMissing() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteFieldExtAPI api = newApi(siteRepository,
                                mock(TurSNSiteFieldExtRepository.class), mock(TurSNSiteFieldExtFacetRepository.class),
                                mock(TurSNSiteFieldRepository.class),
                                mock(TurSEInstanceRepository.class), mock(TurSNTemplate.class));

                when(siteRepository.findById("site")).thenReturn(Optional.empty());

                TurSNSiteFieldExtDto result = api.turSNSiteFieldExtAdd("site", new TurSNSiteFieldExtDto());

                assertThat(result.getId()).isNull();
        }

        @Test
        void testFieldExtStructureSetsSite() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteFieldExtAPI api = newApi(siteRepository,
                                mock(TurSNSiteFieldExtRepository.class), mock(TurSNSiteFieldExtFacetRepository.class),
                                mock(TurSNSiteFieldRepository.class),
                                mock(TurSEInstanceRepository.class), mock(TurSNTemplate.class));
                TurSNSite site = new TurSNSite();

                when(siteRepository.findById("site")).thenReturn(Optional.of(site));

                TurSNSiteFieldExtDto result = api.turSNSiteFieldExtStructure("site");

                assertThat(result.getTurSNSite()).isSameAs(site);
        }

        @Test
        void testFieldExtStructureReturnsDefaultWhenSiteMissing() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteFieldExtAPI api = newApi(siteRepository,
                                mock(TurSNSiteFieldExtRepository.class), mock(TurSNSiteFieldExtFacetRepository.class),
                                mock(TurSNSiteFieldRepository.class),
                                mock(TurSEInstanceRepository.class), mock(TurSNTemplate.class));

                when(siteRepository.findById("site")).thenReturn(Optional.empty());

                TurSNSiteFieldExtDto result = api.turSNSiteFieldExtStructure("site");

                assertThat(result.getId()).isNull();
                assertThat(result.getTurSNSite()).isNull();
        }

        @Test
        void testFieldExtAddCreatesSEFieldWhenSiteFound() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSEInstanceRepository instanceRepository = mock(TurSEInstanceRepository.class);
                TurSNSiteFieldExtAPI api = newApi(siteRepository,
                                fieldExtRepository, mock(TurSNSiteFieldExtFacetRepository.class),
                                fieldRepository,
                                instanceRepository, mock(TurSNTemplate.class));
                TurSNSite site = new TurSNSite();
                TurSEInstance instance = new TurSEInstance();
                instance.setId("se-id");
                site.setTurSEInstance(instance);
                site.setTurSNSiteLocales(new HashSet<>());
                TurSNSiteFieldExt payload = new TurSNSiteFieldExt();
                payload.setName("title");
                payload.setType(TurSEFieldType.STRING);

                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(fieldExtRepository.existsByTurSNSiteAndName(site, "title")).thenReturn(false);
                when(fieldRepository.existsByTurSNSiteAndName(site, "title")).thenReturn(false);
                when(instanceRepository.findById("se-id")).thenReturn(Optional.empty());
                when(fieldRepository.save(org.mockito.ArgumentMatchers.any(TurSNSiteField.class)))
                                .thenAnswer(invocation -> {
                                        TurSNSiteField field = invocation.getArgument(0);
                                        field.setId("field-id");
                                        return field;
                                });
                when(fieldExtRepository.save(ArgumentMatchers.any(TurSNSiteFieldExt.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                TurSNSiteFieldExtDto result = api.turSNSiteFieldExtAdd("site", asDto(payload));

                assertThat(result.getSnType()).isEqualTo(TurSNFieldType.SE);
                assertThat(result.getExternalId()).isEqualTo("field-id");
        }

        @Test
        void testFieldExtAddReturnsDefaultWhenNameAlreadyExists() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteFieldExtAPI api = newApi(siteRepository,
                                fieldExtRepository, mock(TurSNSiteFieldExtFacetRepository.class),
                                fieldRepository,
                                mock(TurSEInstanceRepository.class), mock(TurSNTemplate.class));
                TurSNSite site = new TurSNSite();
                TurSNSiteFieldExt payload = new TurSNSiteFieldExt();
                payload.setName("title");

                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(fieldExtRepository.existsByTurSNSiteAndName(site, "title")).thenReturn(true);

                TurSNSiteFieldExtDto payloadDto = asDto(payload);
                assertThatThrownBy(() -> api.turSNSiteFieldExtAdd("site", payloadDto))
                                .isInstanceOf(ResponseStatusException.class);

                verify(fieldRepository, never()).save(ArgumentMatchers.any(TurSNSiteField.class));
                verify(fieldExtRepository, never()).save(ArgumentMatchers.any(TurSNSiteFieldExt.class));
        }

        @Test
        void testFieldExtAddReturnsConflictWhenFieldNameAlreadyExistsInFieldRepository() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteFieldExtAPI api = newApi(siteRepository,
                                fieldExtRepository, mock(TurSNSiteFieldExtFacetRepository.class),
                                fieldRepository,
                                mock(TurSEInstanceRepository.class), mock(TurSNTemplate.class));
                TurSNSite site = new TurSNSite();
                TurSNSiteFieldExt payload = new TurSNSiteFieldExt();
                payload.setName("title");

                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(fieldExtRepository.existsByTurSNSiteAndName(site, "title")).thenReturn(false);
                when(fieldRepository.existsByTurSNSiteAndName(site, "title")).thenReturn(true);

                TurSNSiteFieldExtDto payloadDto = asDto(payload);
                assertThatThrownBy(() -> api.turSNSiteFieldExtAdd("site", payloadDto))
                                .isInstanceOf(ResponseStatusException.class);

                verify(fieldRepository, never()).save(any(TurSNSiteField.class));
                verify(fieldExtRepository, never()).save(any(TurSNSiteFieldExt.class));
        }

        @Test
        void testFieldExtAddReturnsConflictWhenNameBlank() {
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteFieldExtAPI api = newApi(siteRepository,
                                fieldExtRepository, mock(TurSNSiteFieldExtFacetRepository.class),
                                fieldRepository,
                                mock(TurSEInstanceRepository.class), mock(TurSNTemplate.class));
                TurSNSite site = new TurSNSite();
                TurSNSiteFieldExt payload = new TurSNSiteFieldExt();
                payload.setName("   ");

                when(siteRepository.findById("site")).thenReturn(Optional.of(site));

                TurSNSiteFieldExtDto payloadDto = asDto(payload);
                assertThatThrownBy(() -> api.turSNSiteFieldExtAdd("site", payloadDto))
                                .isInstanceOf(ResponseStatusException.class);
                assertThat(siteRepository.findById("site")).isPresent();

                verify(fieldRepository, never()).existsByTurSNSiteAndName(any(TurSNSite.class), any(String.class));
                verify(fieldExtRepository, never()).existsByTurSNSiteAndName(any(TurSNSite.class), any(String.class));
        }

        @Test
        void testFieldExtUpdateMergesAndRemovesFacetLocales() {
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteFieldExtFacetRepository facetRepository = mock(TurSNSiteFieldExtFacetRepository.class);
                TurSNSiteFieldExtAPI api = newApi(siteRepository,
                                fieldExtRepository, facetRepository,
                                mock(TurSNSiteFieldRepository.class),
                                mock(TurSEInstanceRepository.class), mock(TurSNTemplate.class));

                TurSNSite site = new TurSNSite();
                TurSNSiteFieldExt existing = new TurSNSiteFieldExt();
                existing.setSnType(TurSNFieldType.SE);
                existing.setExternalId("ext");

                TurSNSiteFieldExtFacet existingEn = new TurSNSiteFieldExtFacet();
                existingEn.setLocale(Locale.ENGLISH);
                existingEn.setLabel("Old EN");
                existingEn.setTurSNSiteFieldExt(existing);

                TurSNSiteFieldExtFacet existingPt = new TurSNSiteFieldExtFacet();
                existingPt.setLocale(Locale.of("pt", "BR"));
                existingPt.setLabel("Old PT");
                existingPt.setTurSNSiteFieldExt(existing);

                existing.getFacetLocales().add(existingEn);
                existing.getFacetLocales().add(existingPt);

                TurSNSiteFieldExt payload = new TurSNSiteFieldExt();
                payload.setSnType(TurSNFieldType.SE);
                payload.setFacet(0);
                payload.setExternalId("ext");

                TurSNSiteFieldExtFacet incomingEn = new TurSNSiteFieldExtFacet();
                incomingEn.setLocale(Locale.ENGLISH);
                incomingEn.setLabel("New EN");
                TurSNSiteFieldExtFacet incomingFr = new TurSNSiteFieldExtFacet();
                incomingFr.setLocale(Locale.FRENCH);
                incomingFr.setLabel("FR");
                payload.setFacetLocales(new HashSet<>(Set.of(incomingEn, incomingFr)));

                when(fieldExtRepository.findById("id")).thenReturn(Optional.of(existing));
                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(fieldExtRepository.save(any(TurSNSiteFieldExt.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                TurSNSiteFieldExtDto result = api.turSNSiteFieldExtUpdate("site", "id", asDto(payload));

                assertThat(result.getFacetLocales()).hasSize(2);
                assertThat(result.getFacetLocales()).anySatisfy(facet -> {
                        if (Locale.ENGLISH.equals(facet.getLocale())) {
                                assertThat(facet.getLabel()).isEqualTo("New EN");
                        }
                });
                assertThat(result.getFacetLocales())
                                .anySatisfy(facet -> assertThat(facet.getLocale()).isEqualTo(Locale.FRENCH));
                assertThat(result.getFacetLocales()).allSatisfy(
                                facet -> assertThat(facet.getLocale()).isNotEqualTo(Locale.of("pt", "BR")));
        }

        @Test
        void testFieldExtUpdateClearsFacetLocalesWhenPayloadFacetLocalesIsNull() {
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSNSiteFieldExtAPI api = newApi(siteRepository,
                                fieldExtRepository, mock(TurSNSiteFieldExtFacetRepository.class),
                                mock(TurSNSiteFieldRepository.class),
                                mock(TurSEInstanceRepository.class), mock(TurSNTemplate.class));

                TurSNSite site = new TurSNSite();
                TurSNSiteFieldExt existing = new TurSNSiteFieldExt();
                existing.setSnType(TurSNFieldType.SE);
                existing.setExternalId("ext");
                TurSNSiteFieldExtFacet facet = new TurSNSiteFieldExtFacet();
                facet.setLocale(Locale.ENGLISH);
                facet.setLabel("EN");
                facet.setTurSNSiteFieldExt(existing);
                existing.getFacetLocales().add(facet);

                TurSNSiteFieldExt payload = new TurSNSiteFieldExt();
                payload.setSnType(TurSNFieldType.SE);
                payload.setFacet(0);
                payload.setExternalId("ext");
                payload.setFacetLocales(null);

                when(fieldExtRepository.findById("id")).thenReturn(Optional.of(existing));
                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(fieldExtRepository.save(any(TurSNSiteFieldExt.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                TurSNSiteFieldExtDto result = api.turSNSiteFieldExtUpdate("site", "id", asDto(payload));

                assertThat(result.getFacetLocales()).isEmpty();
        }

        @Test
        void testUpdateExternalFieldSkipsWhenSnTypeNotSE() throws Exception {
                TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
                TurSNSiteFieldExtAPI api = newApi(mock(TurSNSiteRepository.class),
                                mock(TurSNSiteFieldExtRepository.class), mock(TurSNSiteFieldExtFacetRepository.class),
                                fieldRepository,
                                mock(TurSEInstanceRepository.class),
                                mock(TurSNTemplate.class));
                TurSNSiteFieldExt fieldExt = new TurSNSiteFieldExt();
                fieldExt.setSnType(TurSNFieldType.THESAURUS);
                fieldExt.setExternalId("ext");

                invokePrivate(api, "updateExternalField",
                                new Class<?>[] { TurSNSiteFieldExt.class, TurSNSite.class }, fieldExt,
                                new TurSNSite());

                verify(fieldRepository, never()).findById("ext");
        }

        @Test
        void testUpdateExternalFieldSkipsWhenExternalFieldMissing() throws Exception {
                TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
                TurSNSiteFieldExtAPI api = newApi(mock(TurSNSiteRepository.class),
                                mock(TurSNSiteFieldExtRepository.class), mock(TurSNSiteFieldExtFacetRepository.class),
                                fieldRepository,
                                mock(TurSEInstanceRepository.class),
                                mock(TurSNTemplate.class));
                TurSNSiteFieldExt fieldExt = new TurSNSiteFieldExt();
                fieldExt.setSnType(TurSNFieldType.SE);
                fieldExt.setExternalId("ext");

                when(fieldRepository.findById("ext")).thenReturn(Optional.empty());

                invokePrivate(api, "updateExternalField",
                                new Class<?>[] { TurSNSiteFieldExt.class, TurSNSite.class }, fieldExt,
                                new TurSNSite());

                verify(fieldRepository, never()).save(any(TurSNSiteField.class));
        }

        @Test
        void testUpdateExternalFieldUpdatesFieldAndSolrSchema() throws Exception {
                TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
                TurSEInstanceRepository instanceRepository = mock(TurSEInstanceRepository.class);
                TurSNSiteFieldExtAPI api = newApi(mock(TurSNSiteRepository.class),
                                mock(TurSNSiteFieldExtRepository.class), mock(TurSNSiteFieldExtFacetRepository.class),
                                fieldRepository,
                                instanceRepository,
                                mock(TurSNTemplate.class));

                TurSNSiteFieldExt fieldExt = new TurSNSiteFieldExt();
                fieldExt.setSnType(TurSNFieldType.SE);
                fieldExt.setExternalId("ext");
                fieldExt.setName("title");
                fieldExt.setDescription("desc");
                fieldExt.setType(TurSEFieldType.STRING);
                fieldExt.setMultiValued(1);

                TurSNSiteField field = new TurSNSiteField();
                field.setId("ext");
                field.setName("oldName");
                field.setType(TurSEFieldType.TEXT);
                field.setMultiValued(0);

                TurSNSite site = new TurSNSite();
                TurSEInstance siteInstance = new TurSEInstance();
                siteInstance.setId("se-id");
                site.setTurSEInstance(siteInstance);

                TurSNSiteLocale locale = new TurSNSiteLocale();
                locale.setCore("core_en");
                site.setTurSNSiteLocales(new HashSet<>(Set.of(locale)));

                TurSEInstance seInstance = new TurSEInstance();
                seInstance.setId("se-id");

                when(fieldRepository.findById("ext")).thenReturn(Optional.of(field));
                when(fieldRepository.save(any(TurSNSiteField.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(instanceRepository.findById("se-id")).thenReturn(Optional.of(seInstance));

                try (MockedStatic<TurSolrUtils> utils = Mockito.mockStatic(TurSolrUtils.class)) {
                        invokePrivate(api, "updateExternalField",
                                        new Class<?>[] { TurSNSiteFieldExt.class, TurSNSite.class }, fieldExt, site);

                        assertThat(field.getName()).isEqualTo("title");
                        assertThat(field.getDescription()).isEqualTo("desc");
                        assertThat(field.getType()).isEqualTo(TurSEFieldType.STRING);
                        assertThat(field.getMultiValued()).isEqualTo(1);
                        verify(fieldRepository).save(field);
                        utils.verify(() -> TurSolrUtils.addOrUpdateField(
                                        TurSolrFieldAction.ADD,
                                        seInstance,
                                        "core_en",
                                        "title",
                                        TurSEFieldType.STRING,
                                        true,
                                        true));
                }
        }

        @Test
        void testFieldExtDeleteDeletesSolrSchemaWhenSiteAndFieldArePresent() {
                TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
                TurSNSiteFieldRepository fieldRepository = mock(TurSNSiteFieldRepository.class);
                TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
                TurSEInstanceRepository instanceRepository = mock(TurSEInstanceRepository.class);
                TurSNSiteFieldExtAPI api = newApi(siteRepository,
                                fieldExtRepository, mock(TurSNSiteFieldExtFacetRepository.class),
                                fieldRepository,
                                instanceRepository,
                                mock(TurSNTemplate.class));

                TurSNSiteFieldExt existing = new TurSNSiteFieldExt();
                existing.setSnType(TurSNFieldType.SE);
                existing.setExternalId("ext");

                TurSNSite site = new TurSNSite();
                TurSEInstance siteInstance = new TurSEInstance();
                siteInstance.setId("se-id");
                site.setTurSEInstance(siteInstance);
                TurSNSiteLocale locale = new TurSNSiteLocale();
                locale.setCore("core_en");
                site.setTurSNSiteLocales(new HashSet<>(Set.of(locale)));

                TurSNSiteField field = new TurSNSiteField();
                field.setId("ext");
                field.setName("title");
                field.setType(TurSEFieldType.STRING);

                TurSEInstance seInstance = new TurSEInstance();
                seInstance.setId("se-id");

                when(fieldExtRepository.findById("id")).thenReturn(Optional.of(existing));
                when(siteRepository.findById("site")).thenReturn(Optional.of(site));
                when(fieldRepository.findById("ext")).thenReturn(Optional.of(field));
                when(instanceRepository.findById("se-id")).thenReturn(Optional.of(seInstance));

                try (MockedStatic<TurSolrUtils> utils = Mockito.mockStatic(TurSolrUtils.class)) {
                        boolean result = api.turSNSiteFieldExtDelete("site", "id");

                        assertThat(result).isTrue();
                        utils.verify(() -> TurSolrUtils.deleteField(seInstance, "core_en", "title",
                                        TurSEFieldType.STRING));
                        verify(fieldRepository).delete("ext");
                        verify(fieldExtRepository).delete("id");
                }
        }

        @Test
        void testUpdateSolrSchemaSkipsWhenInstanceMissing() throws Exception {
                TurSEInstanceRepository instanceRepository = mock(TurSEInstanceRepository.class);
                TurSNSiteFieldExtAPI api = newApi(mock(TurSNSiteRepository.class),
                                mock(TurSNSiteFieldExtRepository.class), mock(TurSNSiteFieldExtFacetRepository.class),
                                mock(TurSNSiteFieldRepository.class),
                                instanceRepository, mock(TurSNTemplate.class));
                TurSNSite site = new TurSNSite();
                site.setTurSNSiteLocales(new HashSet<>());

                invokePrivate(api, "updateSolrSchema",
                                new Class<?>[] { TurSNSite.class, TurSNSiteField.class }, site, new TurSNSiteField());

                verify(instanceRepository, never()).findById(ArgumentMatchers.anyString());
        }

        @Test
        void testDeleteSolrSchemaSkipsWhenLocalesEmpty() throws Exception {
                TurSEInstanceRepository instanceRepository = mock(TurSEInstanceRepository.class);
                TurSNSiteFieldExtAPI api = newApi(mock(TurSNSiteRepository.class),
                                mock(TurSNSiteFieldExtRepository.class), mock(TurSNSiteFieldExtFacetRepository.class),
                                mock(TurSNSiteFieldRepository.class),
                                instanceRepository, mock(TurSNTemplate.class));
                TurSNSite site = new TurSNSite();
                TurSEInstance instance = new TurSEInstance();
                instance.setId("se-id");
                site.setTurSEInstance(instance);
                site.setTurSNSiteLocales(new HashSet<>());

                invokePrivate(api, "deleteSolrSchema",
                                new Class<?>[] { TurSNSite.class, TurSNSiteField.class }, site, new TurSNSiteField());

                verify(instanceRepository, never()).findById("se-id");
        }
}
