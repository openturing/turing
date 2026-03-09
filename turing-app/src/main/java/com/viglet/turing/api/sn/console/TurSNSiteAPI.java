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

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.turing.api.sn.bean.TurSNSiteMonitoringStatusBean;
import com.viglet.turing.exchange.sn.TurSNSiteExport;
import com.viglet.turing.persistence.dto.sn.TurSNSiteDto;
import com.viglet.turing.persistence.mapper.sn.TurSNSiteMapper;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetSortEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.genai.TurSNSiteGenAiRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.sn.TurSNQueue;
import com.viglet.turing.sn.template.TurSNTemplate;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.solr.TurSolrUtils;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/sn")
@Tag(name = "Semantic Navigation Site", description = "Semantic Navigation Site API")
@ComponentScan("com.viglet.turing")
@RequiredArgsConstructor
public class TurSNSiteAPI {
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteGenAiRepository turSNSiteGenAiRepository;
    private final TurSNSiteExport turSNSiteExport;
    private final TurSNTemplate turSNTemplate;
    private final TurSNQueue turSNQueue;
    private final TurSolrInstanceProcess turSolrInstanceProcess;
    private final TurSolr turSolr;
    private final TurConfigProperties turConfigProperties;
    private final TurSNSiteMapper turSNSiteMapper;

    @Operation(summary = "Semantic Navigation Site List")
    @GetMapping
    public List<TurSNSiteDto> turSNSiteList(Principal principal) {
        if (turConfigProperties.isMultiTenant()) {
            return turSNSiteMapper.toDtoList(this.turSNSiteRepository
                    .findByCreatedBy(TurPersistenceUtils.orderByNameIgnoreCase(),
                            principal.getName().toLowerCase()));
        } else {
            return turSNSiteMapper
                    .toDtoList(this.turSNSiteRepository.findAll(TurPersistenceUtils.orderByNameIgnoreCase()));
        }
    }

    @Operation(summary = "Semantic Navigation Site structure")
    @GetMapping("/structure")
    public TurSNSiteDto turSNSiteStructure() {
        TurSNSite turSNSite = new TurSNSite();
        turSNSite.setFacetSort(TurSNSiteFacetSortEnum.COUNT);
        turSNSite.setFacetType(TurSNSiteFacetFieldEnum.AND);
        turSNSite.setTurSEInstance(new TurSEInstance());
        turSNSite.setTurSNSiteGenAi(new TurSNSiteGenAi());
        return turSNSiteMapper.toDto(turSNSite);
    }

    @Operation(summary = "Show a Semantic Navigation Site")
    @GetMapping("/{id}")
    public TurSNSiteDto turSNSiteGet(@PathVariable String id) {
        return turSNSiteMapper.toDto(this.turSNSiteRepository.findById(id).orElse(new TurSNSite()));
    }

    @Operation(summary = "Update a Semantic Navigation Site")
    @PutMapping("/{id}")
    public TurSNSiteDto turSNSiteUpdate(@PathVariable String id, @RequestBody TurSNSiteDto turSNSiteDto) {
        TurSNSite turSNSite = turSNSiteMapper.toEntity(turSNSiteDto);
        return this.turSNSiteRepository.findById(id).map(turSNSiteEdit -> {
            turSNSiteEdit.setName(turSNSite.getName());
            turSNSiteEdit.setDescription(turSNSite.getDescription());
            turSNSiteEdit.setTurSEInstance(turSNSite.getTurSEInstance());
            turSNSiteEdit.setThesaurus(turSNSite.getThesaurus());
            turSNSiteEdit.setFacet(turSNSite.getFacet());
            turSNSiteEdit.setFacetType(turSNSite.getFacetType());
            turSNSiteEdit.setFacetItemType(turSNSite.getFacetItemType());
            turSNSiteEdit.setFacetSort(turSNSite.getFacetSort());
            turSNSiteEdit.setHl(turSNSite.getHl());
            turSNSiteEdit.setHlPost(turSNSite.getHlPost());
            turSNSiteEdit.setHlPre(turSNSite.getHlPre());
            turSNSiteEdit.setItemsPerFacet(turSNSite.getItemsPerFacet());
            turSNSiteEdit.setSpellCheck(turSNSite.getSpellCheck());
            turSNSiteEdit.setSpellCheckFixes(turSNSite.getSpellCheckFixes());
            turSNSiteEdit.setMlt(turSNSite.getMlt());
            turSNSiteEdit.setRowsPerPage(turSNSite.getRowsPerPage());
            turSNSiteEdit.setSpotlightWithResults(turSNSite.getSpotlightWithResults());
            turSNSiteEdit.setWildcardNoResults(turSNSite.getWildcardNoResults());
            turSNSiteEdit.setWildcardAlways(turSNSite.getWildcardAlways());
            turSNSiteEdit.setExactMatchField(turSNSite.getExactMatchField());
            turSNSiteEdit.setDefaultField(turSNSite.getDefaultField());
            turSNSiteEdit.setDefaultTitleField(turSNSite.getDefaultTitleField());
            turSNSiteEdit.setDefaultTextField(turSNSite.getDefaultTextField());
            turSNSiteEdit.setDefaultDescriptionField(turSNSite.getDefaultDescriptionField());
            turSNSiteEdit.setDefaultDateField(turSNSite.getDefaultDateField());
            turSNSiteEdit.setDefaultImageField(turSNSite.getDefaultImageField());
            turSNSiteEdit.setDefaultURLField(turSNSite.getDefaultURLField());
            turSNSiteEdit.setExactMatch(turSNSite.getExactMatch());
            Optional.ofNullable(turSNSite.getTurSNSiteGenAi()).ifPresent(genAi -> {
                TurSNSiteGenAi turSNSiteGenAi = Optional.ofNullable(turSNSiteEdit.getTurSNSiteGenAi())
                        .orElse(new TurSNSiteGenAi());
                turSNSiteGenAi.setEnabled(genAi.isEnabled());
                turSNSiteGenAi.setTurLLMInstance(genAi.getTurLLMInstance());
                turSNSiteGenAi.setTurStoreInstance(genAi.getTurStoreInstance());
                turSNSiteGenAi.setSystemPrompt(genAi.getSystemPrompt());
                turSNSiteGenAiRepository.save(turSNSiteGenAi);
                turSNSiteEdit.setTurSNSiteGenAi(turSNSiteGenAi);
            });
            turSNSiteRepository.save(turSNSiteEdit);
            return turSNSiteMapper.toDto(turSNSiteEdit);
        }).orElse(new TurSNSiteDto());

    }

    @Transactional
    @Operation(summary = "Delete a Semantic Navigation Site")
    @DeleteMapping("/{id}")
    public boolean turSNSiteDelete(@PathVariable String id) {
        Optional<TurSNSite> turSNSite = turSNSiteRepository.findById(id);
        turSNSite.ifPresent(site -> {
            TurSNSiteGenAi genAi = site.getTurSNSiteGenAi();
            turSNSiteLocaleRepository.findByTurSNSite(TurPersistenceUtils.orderByLanguageIgnoreCase(), site)
                    .forEach(locale -> TurSolrUtils.deleteCore(site.getTurSEInstance(), locale.getCore()));
            site.getTurSNSiteFields().clear();
            site.getTurSNSiteFieldExts().clear();
            site.getTurSNSiteSpotlights().clear();
            site.getTurSNRankingExpressions().clear();
            site.getTurSNSiteMergeProviders().clear();
            site.setTurSNSiteGenAi(null);
            turSNSiteRepository.flush();
            turSNSiteRepository.delete(site);
            Optional.ofNullable(genAi).ifPresent(turSNSiteGenAiRepository::delete);
        });

        return true;
    }

    @Operation(summary = "Create a Semantic Navigation Site")
    @PostMapping
    public TurSNSiteDto turSNSiteAdd(@RequestBody TurSNSiteDto turSNSiteDto, Principal principal) {
        TurSNSite turSNSite = turSNSiteMapper.toEntity(turSNSiteDto);
        if (turSNSite.getTurSNSiteGenAi() != null) {
            turSNSiteGenAiRepository.save(turSNSite.getTurSNSiteGenAi());
        }
        turSNSiteRepository.save(turSNSite);
        turSNTemplate.createSNSite(turSNSite, principal.getName(), Locale.US);
        return turSNSiteMapper.toDto(turSNSite);

    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public StreamingResponseBody turSNSiteExportAll(HttpServletResponse response) {

        try {
            return turSNSiteExport.exportAll(response);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    @GetMapping(value = "/{id}/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public StreamingResponseBody turSNSiteExport(@PathVariable String id, HttpServletResponse response) {

        try {
            return turSNSiteExport.exportBySiteId(id, response);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    @Operation(summary = "Semantic Navigation Site Monitoring Status")
    @GetMapping("/{id}/monitoring")
    public TurSNSiteMonitoringStatusBean turSNSiteMonitoringStatus(@PathVariable String id) {
        return this.turSNSiteRepository.findById(id).map(turSNSite -> {
            TurSNSiteMonitoringStatusBean turSNSiteMonitoringStatusBean = new TurSNSiteMonitoringStatusBean();
            turSNSiteMonitoringStatusBean.setQueue(turSNQueue.getQueueSize());
            long documentTotal = 0L;
            for (TurSNSiteLocale turSNSiteLocale : turSNSiteLocaleRepository
                    .findByTurSNSite(TurPersistenceUtils.orderByLanguageIgnoreCase(), turSNSite)) {
                Optional<TurSolrInstance> turSolrInstance = turSolrInstanceProcess.initSolrInstance(turSNSiteLocale);
                if (turSolrInstance.isPresent()) {
                    documentTotal += turSolr.getDocumentTotal(turSolrInstance.get());
                }
            }
            turSNSiteMonitoringStatusBean.setDocuments((int) documentTotal);
            return turSNSiteMonitoringStatusBean;
        }).orElse(new TurSNSiteMonitoringStatusBean());
    }

}
