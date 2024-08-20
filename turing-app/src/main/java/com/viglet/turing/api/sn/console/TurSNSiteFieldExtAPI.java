/*
 * Copyright (C) 2016-2022 the original author or authors.
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

import com.google.inject.Inject;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetRangeEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldSortEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorEntityRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.utils.TurPersistenceUtils;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.sn.template.TurSNTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/sn/{ignoredSnSiteId}/field/ext")
@Tag(name = "Semantic Navigation Field Ext", description = "Semantic Navigation Field Ext API")
public class TurSNSiteFieldExtAPI {
    public static final String TEXT_GENERAL = "text_general";
    public static final String MULTI_VALUED = "multiValued";
    public static final String STORED = "stored";
    public static final String INDEXED = "indexed";
    public static final String TYPE = "type";
    public static final String STRING = "string";
    public static final String ADD_FIELD = "add-field";
    public static final String TEXT = "_text_";
    public static final String SOURCE = "source";
    public static final String ADD_COPY_FIELD = "add-copy-field";
    public static final String DEST = "dest";
    public static final String PDATE = "pdate";
    public static final String NAME = "name";
    public static final String SOLR_SCHEMA_REQUEST = "http://%s:%d/solr/%s/schema";
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository;
    private final TurSNSiteFieldRepository turSNSiteFieldRepository;
    private final TurNLPEntityRepository turNLPEntityRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurNLPVendorEntityRepository turNLPVendorEntityRepository;
    private final TurSNTemplate turSNTemplate;

    @Inject
    public TurSNSiteFieldExtAPI(TurSNSiteRepository turSNSiteRepository,
                                TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
                                TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository,
                                TurSNSiteFieldRepository turSNSiteFieldRepository,
                                TurNLPEntityRepository turNLPEntityRepository,
                                TurSNSiteLocaleRepository turSNSiteLocaleRepository,
                                TurNLPVendorEntityRepository turNLPVendorEntityRepository,
                                TurSNTemplate turSNTemplate) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNSiteFieldExtFacetRepository = turSNSiteFieldExtFacetRepository;
        this.turSNSiteFieldRepository = turSNSiteFieldRepository;
        this.turNLPEntityRepository = turNLPEntityRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turNLPVendorEntityRepository = turNLPVendorEntityRepository;
        this.turSNTemplate = turSNTemplate;
    }

    @Operation(summary = "Semantic Navigation Site Field Ext List")
    @Transactional
    @GetMapping
    public List<TurSNSiteFieldExt> turSNSiteFieldExtList(@PathVariable String ignoredSnSiteId) {
        return turSNSiteRepository.findById(ignoredSnSiteId).map(turSNSite -> {
            updateFieldExtFromSNSite(turSNSite);
            return turSNSiteFieldExtRepository
                    .findByTurSNSite(TurPersistenceUtils.orderByNameIgnoreCase(), turSNSite);

        }).orElse(Collections.emptyList());

    }

    private void updateFieldExtFromSNSite(TurSNSite turSNSite) {
        Map<String, TurNLPEntity> nerMap = new HashMap<>();
        if (turSNSite.getTurNLPVendor() != null) {
            nerMap = createNERMap(turSNSite.getTurNLPVendor());
        } else {
            turSNSiteFieldExtRepository.deleteByTurSNSiteAndSnType(turSNSite, TurSNFieldType.NER);
        }
        List<TurNLPEntity> turNLPEntityThesaurus = turNLPEntityRepository.findByLocal(1);
        Map<String, TurSNSiteField> fieldMap = createFieldMap(turSNSite);
        Map<String, TurNLPEntity> thesaurusMap = createThesaurusMap(turNLPEntityThesaurus);
        List<TurSNSiteFieldExt> turSNSiteFieldExtList =
                this.turSNSiteFieldExtRepository
                        .findByTurSNSite(TurPersistenceUtils.orderByNameIgnoreCase(), turSNSite);
        removeDuplicatedFields(fieldMap, nerMap, thesaurusMap, turSNSiteFieldExtList);
        for (TurSNSiteField turSNSiteField : fieldMap.values()) {
            TurSNSiteFieldExt turSNSiteFieldExt = saveSNSiteFieldExt(turSNSite, turSNSiteField);
            turSNSiteFieldExtList.add(turSNSiteFieldExt);
        }
        nerMap.values().forEach(turNLPEntity -> addTurSNSiteFieldExt(TurSNFieldType.NER, turSNSite,
                turSNSiteFieldExtList, turNLPEntity));
        thesaurusMap.values().forEach(turNLPEntity -> addTurSNSiteFieldExt(TurSNFieldType.THESAURUS, turSNSite,
                turSNSiteFieldExtList, turNLPEntity));
    }

    private Map<String, TurNLPEntity> createNERMap(TurNLPVendor turNLPVendor) {
        Map<String, TurNLPEntity> nerMap = new HashMap<>();
        turNLPVendorEntityRepository.findByTurNLPVendor(turNLPVendor).forEach(turNLPVendorEntity -> {
            TurNLPEntity turNLPEntity = turNLPVendorEntity.getTurNLPEntity();
            nerMap.put(turNLPEntity.getInternalName(), turNLPEntity);
        });

        return nerMap;
    }

    private Map<String, TurNLPEntity> createThesaurusMap(List<TurNLPEntity> turNLPEntityThesaurus) {
        Map<String, TurNLPEntity> thesaurusMap = new HashMap<>();
        turNLPEntityThesaurus.forEach(turNLPEntityThesaurusSingle -> thesaurusMap
                .put(turNLPEntityThesaurusSingle.getInternalName(), turNLPEntityThesaurusSingle));
        return thesaurusMap;
    }

    private Map<String, TurSNSiteField> createFieldMap(TurSNSite turSNSite) {
        List<TurSNSiteField> turSNSiteFields = turSNSiteFieldRepository.findByTurSNSite(turSNSite);
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        if (turSNSiteFields.isEmpty()) {
            turSNTemplate.createSEFields(turSNSite);
        } else {
            turSNSiteFields.forEach(turSNSiteField -> fieldMap.put(turSNSiteField.getId(), turSNSiteField));
        }
        return fieldMap;
    }

    private void removeDuplicatedFields(Map<String, TurSNSiteField> fieldMap, Map<String, TurNLPEntity> nerMap,
                                        Map<String, TurNLPEntity> thesaurusMap, List<TurSNSiteFieldExt> turSNSiteFieldExtensions) {
        for (TurSNSiteFieldExt turSNSiteFieldExtension : turSNSiteFieldExtensions) {
            switch (turSNSiteFieldExtension.getSnType()) {
                case SE:
                    fieldMap.remove(turSNSiteFieldExtension.getExternalId());
                    break;
                case NER:
                    nerMap.remove(turSNSiteFieldExtension.getExternalId());
                    break;
                case THESAURUS:
                    thesaurusMap.remove(turSNSiteFieldExtension.getExternalId());
                    break;
            }
        }
    }

    private TurSNSiteFieldExt saveSNSiteFieldExt(TurSNSite turSNSite, TurSNSiteField turSNSiteField) {
        return turSNSiteFieldExtRepository.save(TurSNSiteFieldExt.builder()
                .enabled(0)
                .name(turSNSiteField.getName())
                .description(turSNSiteField.getDescription())
                .facet(0)
                .facetName(turSNSiteField.getName())
                .facetRange(TurSNSiteFacetRangeEnum.DISABLED)
                .facetType(TurSNSiteFacetFieldEnum.DEFAULT)
                .facetSort(TurSNSiteFacetFieldSortEnum.COUNT)
                .hl(0)
                .multiValued(turSNSiteField.getMultiValued())
                .facetPosition(0)
                .mlt(0)
                .externalId(turSNSiteField.getId())
                .snType(TurSNFieldType.SE)
                .type(turSNSiteField.getType())
                .turSNSite(turSNSite).build());
    }

    private void addTurSNSiteFieldExt(TurSNFieldType turSNFieldType, TurSNSite turSNSite,
                                      List<TurSNSiteFieldExt> turSNSiteFieldExtList, TurNLPEntity turNLPEntity) {
        turSNSiteFieldExtList.add(turSNSiteFieldExtRepository.save(TurSNSiteFieldExt.builder()
                .enabled(0)
                .name(turNLPEntity.getInternalName())
                .description(turNLPEntity.getDescription())
                .facet(0)
                .facetName(turNLPEntity.getName())
                .facetRange(TurSNSiteFacetRangeEnum.DISABLED)
                .facetType(TurSNSiteFacetFieldEnum.DEFAULT)
                .facetSort(TurSNSiteFacetFieldSortEnum.COUNT)
                .hl(0)
                .multiValued(1)
                .facetPosition(0)
                .mlt(0)
                .externalId(turNLPEntity.getInternalName())
                .snType(turSNFieldType)
                .type(TurSEFieldType.STRING)
                .turSNSite(turSNSite).build()));
    }

    @Operation(summary = "Show a Semantic Navigation Site Field Ext")
    @GetMapping("/{id}")
    public TurSNSiteFieldExt turSNSiteFieldExtGet(@PathVariable String ignoredSnSiteId, @PathVariable String id) {
        TurSNSiteFieldExt turSNSiteFieldExt = turSNSiteFieldExtRepository.findById(id)
                .orElse(TurSNSiteFieldExt.builder().build());
        turSNSiteFieldExt.setFacetLocales(turSNSiteFieldExtFacetRepository.findByTurSNSiteFieldExt(turSNSiteFieldExt));
        return turSNSiteFieldExt;
    }

    @Operation(summary = "Update a Semantic Navigation Site Field Ext")
    @PutMapping("/{id}")
    public TurSNSiteFieldExt turSNSiteFieldExtUpdate(@PathVariable String ignoredSnSiteId, @PathVariable String id,
                                                     @RequestBody TurSNSiteFieldExt turSNSiteFieldExt) {
        return this.turSNSiteFieldExtRepository.findById(id).map(turSNSiteFieldExtEdit -> {
            turSNSiteFieldExtEdit.setFacetName(turSNSiteFieldExt.getFacetName());
            turSNSiteFieldExtEdit.setMultiValued(turSNSiteFieldExt.getMultiValued());
            turSNSiteFieldExtEdit.setName(turSNSiteFieldExt.getName());
            turSNSiteFieldExtEdit.setDescription(turSNSiteFieldExt.getDescription());
            turSNSiteFieldExtEdit.setType(turSNSiteFieldExt.getType());
            turSNSiteFieldExtEdit.setFacet(turSNSiteFieldExt.getFacet());
            turSNSiteFieldExtEdit.setFacetLocales(turSNSiteFieldExt.getFacetLocales()
                    .stream()
                    .peek(fieldExtFacet ->
                            fieldExtFacet.setTurSNSiteFieldExt(turSNSiteFieldExt))
                    .collect(Collectors.toSet()));
            turSNSiteFieldExtEdit.setFacetRange(turSNSiteFieldExt.getFacetRange());
            turSNSiteFieldExtEdit.setFacetSort(turSNSiteFieldExt.getFacetSort());
            turSNSiteFieldExtEdit.setHl(turSNSiteFieldExt.getHl());
            turSNSiteFieldExtEdit.setEnabled(turSNSiteFieldExt.getEnabled());
            turSNSiteFieldExtEdit.setMlt(turSNSiteFieldExt.getMlt());
            turSNSiteFieldExtEdit.setExternalId(turSNSiteFieldExt.getExternalId());
            turSNSiteFieldExtEdit.setRequired(turSNSiteFieldExt.getRequired());
            turSNSiteFieldExtEdit.setDefaultValue(turSNSiteFieldExt.getDefaultValue());
            turSNSiteFieldExtEdit.setNlp(turSNSiteFieldExt.getNlp());
            turSNSiteFieldExtEdit.setSnType(turSNSiteFieldExt.getSnType());
            turSNSiteFieldExtEdit.setFacetType(turSNSiteFieldExt.getFacetType());
            if (turSNSiteFieldExt.getFacet() == 1) {
                turSNSiteFieldExtEdit.setFacetPosition(hasFacetPosition(turSNSiteFieldExt) ?
                        turSNSiteFieldExt.getFacetPosition() :
                        getFacetPositionIncrement());
            } else {
                turSNSiteFieldExtEdit.setFacetPosition(0);
            }

            this.turSNSiteFieldExtRepository.save(turSNSiteFieldExtEdit);


            this.updateExternalField(turSNSiteFieldExt);
            return turSNSiteFieldExtEdit;
        }).orElse(TurSNSiteFieldExt.builder().build());

    }

    private static boolean hasFacetPosition(TurSNSiteFieldExt turSNSiteFieldExt) {
        return turSNSiteFieldExt.getFacetPosition() != null && turSNSiteFieldExt.getFacetPosition() > 0;
    }

    @NotNull
    private Integer getFacetPositionIncrement() {
        return this.turSNSiteFieldExtRepository.findMaxFacetPosition().map(max ->
                max + 1).orElse(1);
    }

    @Transactional
    @Operation(summary = "Delete a Semantic Navigation Site Field Ext")
    @DeleteMapping("/{id}")
    public boolean turSNSiteFieldExtDelete(@PathVariable String ignoredSnSiteId, @PathVariable String id) {
        return this.turSNSiteFieldExtRepository.findById(id).map(turSNSiteFieldExtEdit -> {
            if (turSNSiteFieldExtEdit.getSnType().equals(TurSNFieldType.SE)) {
                this.turSNSiteFieldRepository.delete(turSNSiteFieldExtEdit.getExternalId());
            }
            this.turSNSiteFieldExtRepository.delete(id);
            return true;
        }).orElse(false);
    }

    @Operation(summary = "Create a Semantic Navigation Site Field Ext")
    @PostMapping
    public TurSNSiteFieldExt turSNSiteFieldExtAdd(@PathVariable String ignoredSnSiteId,
                                                  @RequestBody TurSNSiteFieldExt turSNSiteFieldExt) {
        return createSEField(ignoredSnSiteId, turSNSiteFieldExt);
    }

    @Operation(summary = "Semantic Navigation Site Field Ext structure")
    @GetMapping("structure")
    public TurSNSiteFieldExt urSNSiteFieldExtStructure(@PathVariable String ignoredSnSiteId) {
        return turSNSiteRepository.findById(ignoredSnSiteId).map(turSNSite -> {
            TurSNSiteFieldExt turSNSiteFieldExt = new TurSNSiteFieldExt();
            turSNSiteFieldExt.setTurSNSite(turSNSite);
            return turSNSiteFieldExt;
        }).orElse(new TurSNSiteFieldExt());
    }

    private TurSNSiteFieldExt createSEField(String snSiteId, TurSNSiteFieldExt turSNSiteFieldExt) {
        return turSNSiteRepository.findById(snSiteId).map(turSNSite -> {
            TurSNSiteField turSNSiteField = new TurSNSiteField();
            turSNSiteField.setDescription(turSNSiteFieldExt.getDescription());
            turSNSiteField.setMultiValued(turSNSiteFieldExt.getMultiValued());
            turSNSiteField.setName(turSNSiteFieldExt.getName());
            turSNSiteField.setType(turSNSiteFieldExt.getType());
            turSNSiteField.setTurSNSite(turSNSite);
            this.turSNSiteFieldRepository.save(turSNSiteField);

            turSNSiteFieldExt.setTurSNSite(turSNSite);
            turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
            turSNSiteFieldExt.setExternalId(turSNSiteField.getId());
            if (turSNSiteFieldExt.getFacet() == 1) {
                turSNSiteFieldExt.setFacetPosition(getFacetPositionIncrement());
            }
            else {
                turSNSiteFieldExt.setFacetPosition(0);
            }
            this.turSNSiteFieldExtRepository.save(turSNSiteFieldExt);

            return turSNSiteFieldExt;

        }).orElse(TurSNSiteFieldExt.builder().build());

    }

    public void updateExternalField(TurSNSiteFieldExt turSNSiteFieldExt) {
        if (Objects.requireNonNull(turSNSiteFieldExt.getSnType()) == TurSNFieldType.SE) {
            turSNSiteFieldRepository.findById(turSNSiteFieldExt.getExternalId()).ifPresent(turSNSiteField -> {
                turSNSiteField.setDescription(turSNSiteFieldExt.getDescription());
                turSNSiteField.setMultiValued(turSNSiteFieldExt.getMultiValued());
                turSNSiteField.setName(turSNSiteFieldExt.getName());
                turSNSiteField.setType(turSNSiteFieldExt.getType());
                this.turSNSiteFieldRepository.save(turSNSiteField);
            });
        } else if (turSNSiteFieldExt.getSnType() == TurSNFieldType.NER || turSNSiteFieldExt.getSnType() == TurSNFieldType.THESAURUS) {
            turNLPEntityRepository.findById(turSNSiteFieldExt.getExternalId()).ifPresent(turNLPEntityNER -> {
                turNLPEntityNER.setDescription(turSNSiteFieldExt.getDescription());
                turNLPEntityNER.setInternalName(turSNSiteFieldExt.getName());
                this.turNLPEntityRepository.save(turNLPEntityNER);
            });
        }
    }

    @GetMapping("/create/{localeRequest}")
    public List<TurSNSite> turSNSiteFieldExtCreate(@PathVariable String ignoredSnSiteId, @PathVariable String localeRequest) {
        Locale locale = LocaleUtils.toLocale(localeRequest);
        return turSNSiteRepository.findById(ignoredSnSiteId).map(turSNSite -> {
            List<TurSNSiteFieldExt> turSNSiteFieldExtList = turSNSiteFieldExtRepository
                    .findByTurSNSiteAndEnabled(turSNSite, 1);
            turSNSiteFieldExtList.forEach(turSNSiteFieldExt -> this.createField(turSNSite, locale, turSNSiteFieldExt));
            return this.turSNSiteRepository.findAll();
        }).orElse(new ArrayList<>());
    }

    public void createField(TurSNSite turSNSite, Locale locale, TurSNSiteFieldExt turSNSiteFieldExt) {
        TurSNSiteLocale turSNSiteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(turSNSite, locale);
        JSONObject jsonAddField = new JSONObject();
        String fieldName;
        if (turSNSiteFieldExt.getSnType() == TurSNFieldType.NER) {
            fieldName = String.format("turing_entity_%s", turSNSiteFieldExt.getName());
        } else {
            fieldName = turSNSiteFieldExt.getName();
        }

        jsonAddField.put(NAME, fieldName);

        jsonAddField.put(INDEXED, true);
        jsonAddField.put(STORED, true);
        if (turSNSiteFieldExt.getMultiValued() == 1) {
            jsonAddField.put(TYPE, STRING);
            jsonAddField.put(MULTI_VALUED, true);
        } else {
            if (turSNSiteFieldExt.getType().equals(TurSEFieldType.DATE)) {
                jsonAddField.put(TYPE, PDATE);
            } else {
                jsonAddField.put(TYPE, TEXT_GENERAL);
            }
            jsonAddField.put(MULTI_VALUED, false);
        }
        JSONObject json = new JSONObject();
        json.put(ADD_FIELD, jsonAddField);
        HttpPost httpPost = new HttpPost(
                String.format(SOLR_SCHEMA_REQUEST, turSNSite.getTurSEInstance().getHost(),
                        turSNSite.getTurSEInstance().getPort(), turSNSiteLocale.getCore()));
        executeHttpPost(json, httpPost);
        this.copyField(turSNSiteLocale, fieldName, TEXT);
    }

    public void copyField(TurSNSiteLocale turSNSiteLocale, String field, String dest) {
        JSONObject jsonAddField = new JSONObject();
        jsonAddField.put(SOURCE, field);
        jsonAddField.put(DEST, dest);
        JSONObject json = new JSONObject();
        json.put(ADD_COPY_FIELD, jsonAddField);
        TurSEInstance turSEInstance = turSNSiteLocale.getTurSNSite().getTurSEInstance();
        HttpPost httpPost = new HttpPost(String.format(SOLR_SCHEMA_REQUEST, turSEInstance.getHost(),
                turSEInstance.getPort(), turSNSiteLocale.getCore()));
        executeHttpPost(json, httpPost);
    }

    private void executeHttpPost(JSONObject json, HttpPost httpPost) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            StringEntity entity = new StringEntity(json.toString());
            httpPost.setEntity(entity);
            httpPost.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            httpPost.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            client.execute(httpPost);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}
