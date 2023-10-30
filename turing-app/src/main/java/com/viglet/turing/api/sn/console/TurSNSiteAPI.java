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

import com.viglet.turing.api.sn.bean.TurSNSiteMonitoringStatusBean;
import com.viglet.turing.exchange.sn.TurSNSiteExport;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.sn.TurSNQueue;
import com.viglet.turing.sn.template.TurSNTemplate;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.solr.TurSolrUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.lang.invoke.MethodHandles;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/sn")
@Tag(name = "Semantic Navigation Site", description = "Semantic Navigation Site API")
@ComponentScan("com.viglet.turing")
public class TurSNSiteAPI {
    private static final Log logger = LogFactory.getLog(MethodHandles.lookup().lookupClass());
    @Autowired
    private TurSNSiteRepository turSNSiteRepository;
    @Autowired
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    @Autowired
    private TurSNSiteExport turSNSiteExport;
    @Autowired
    private TurSNTemplate turSNTemplate;
    @Autowired
    private TurSNQueue turSNQueue;
    @Autowired
    private TurSolrInstanceProcess turSolrInstanceProcess;
    @Autowired
    private TurSolr turSolr;
    @Autowired
    private TurConfigProperties turConfigProperties;

    @Operation(summary = "Semantic Navigation Site List")
    @GetMapping
    public List<TurSNSite> turSNSiteList(Principal principal) {
        if (turConfigProperties.isMultiTenant()) {
            return this.turSNSiteRepository.findByCreatedBy(principal.getName().toLowerCase());
        } else {
            return this.turSNSiteRepository.findAll();
        }
    }

    @Operation(summary = "Semantic Navigation Site structure")
    @GetMapping("/structure")
    public TurSNSite turSNSiteStructure() {
        TurSNSite turSNSite = new TurSNSite();
        turSNSite.setTurSEInstance(new TurSEInstance());
        turSNSite.setTurNLPVendor(new TurNLPVendor());
        return turSNSite;
    }

    @Operation(summary = "Show a Semantic Navigation Site")
    @GetMapping("/{id}")
    public TurSNSite turSNSiteGet(@PathVariable String id) {
        return this.turSNSiteRepository.findById(id).orElse(new TurSNSite());
    }

    @Operation(summary = "Update a Semantic Navigation Site")
    @PutMapping("/{id}")
    public TurSNSite turSNSiteUpdate(@PathVariable String id, @RequestBody TurSNSite turSNSite) {
        return this.turSNSiteRepository.findById(id).map(turSNSiteEdit -> {
            turSNSiteEdit.setName(turSNSite.getName());
            turSNSiteEdit.setDescription(turSNSite.getDescription());
            turSNSiteEdit.setTurSEInstance(turSNSite.getTurSEInstance());
            turSNSiteEdit.setTurNLPVendor(turSNSite.getTurNLPVendor());
            turSNSiteEdit.setThesaurus(turSNSite.getThesaurus());

            // UI
            turSNSiteEdit.setFacet(turSNSite.getFacet());
            turSNSiteEdit.setHl(turSNSite.getHl());
            turSNSiteEdit.setHlPost(turSNSite.getHlPost());
            turSNSiteEdit.setHlPre(turSNSite.getHlPre());
            turSNSiteEdit.setItemsPerFacet(turSNSite.getItemsPerFacet());
            turSNSiteEdit.setSpellCheck(turSNSite.getSpellCheck());
            turSNSiteEdit.setSpellCheckFixes(turSNSite.getSpellCheckFixes());
            turSNSiteEdit.setMlt(turSNSite.getMlt());
            turSNSiteEdit.setRowsPerPage(turSNSite.getRowsPerPage());
            turSNSiteEdit.setSpotlightWithResults(turSNSite.getSpotlightWithResults());
            turSNSiteEdit.setDefaultTitleField(turSNSite.getDefaultTitleField());
            turSNSiteEdit.setDefaultTextField(turSNSite.getDefaultTextField());
            turSNSiteEdit.setDefaultDescriptionField(turSNSite.getDefaultDescriptionField());
            turSNSiteEdit.setDefaultDateField(turSNSite.getDefaultDateField());
            turSNSiteEdit.setDefaultImageField(turSNSite.getDefaultImageField());
            turSNSiteEdit.setDefaultURLField(turSNSite.getDefaultURLField());

            turSNSiteRepository.save(turSNSiteEdit);
            return turSNSiteEdit;
        }).orElse(new TurSNSite());

    }

    @Transactional
    @Operation(summary = "Delete a Semantic Navigation Site")
    @DeleteMapping("/{id}")
    public boolean turSNSiteDelete(@PathVariable String id) {
        Optional<TurSNSite> turSNSite = turSNSiteRepository.findById(id);
        turSNSite.ifPresent(site ->
                turSNSiteLocaleRepository.findByTurSNSite(site).forEach(locale ->
                        TurSolrUtils.deleteCore(site.getTurSEInstance(), locale.getCore())
                )
        );
        turSNSiteRepository.delete(id);

        return true;
    }

    @Operation(summary = "Create a Semantic Navigation Site")
    @PostMapping
    public TurSNSite turSNSiteAdd(@RequestBody TurSNSite turSNSite, Principal principal) {
        turSNSiteRepository.save(turSNSite);
        turSNTemplate.createSNSite(turSNSite, principal.getName(), "en_US");
        return turSNSite;

    }

    @ResponseBody
    @GetMapping(value = "/export", produces = "application/zip")
    public StreamingResponseBody turSNSiteExport(HttpServletResponse response) {

        try {
            return turSNSiteExport.exportObject(response);
        } catch (Exception e) {
            logger.error(e);
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
            for (TurSNSiteLocale turSNSiteLocale : turSNSiteLocaleRepository.findByTurSNSite(turSNSite)) {
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