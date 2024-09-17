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

package com.viglet.turing.sn.template;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingCondition;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingConditionRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.solr.TurSolrUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.LocaleUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * @author Alexandre Oliveira
 * @since 0.3.4
 */

@Slf4j
@Component
public class TurSNTemplate {
    public static final String PT_BR = "pt_BR";
    public static final String TITLE = "title";
    private final ResourceLoader resourceloader;
    private final TurSNSiteFieldRepository turSNSiteFieldRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository;
    private final TurNLPInstanceRepository turNLPInstanceRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSEInstanceRepository turSEInstanceRepository;
    private final TurSNRankingExpressionRepository turSNRankingExpressionRepository;
    private final TurSNRankingConditionRepository turSNRankingConditionRepository;
    private final TurConfigProperties turConfigProperties;

    public TurSNTemplate(ResourceLoader resourceloader,
                         TurSNSiteFieldRepository turSNSiteFieldRepository,
                         TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
                         TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository,
                         TurNLPInstanceRepository turNLPInstanceRepository,
                         TurSNSiteLocaleRepository turSNSiteLocaleRepository,
                         TurSEInstanceRepository turSEInstanceRepository,
                         TurSNRankingExpressionRepository turSNRankingExpressionRepository,
                         TurSNRankingConditionRepository turSNRankingConditionRepository,
                         TurConfigProperties turConfigProperties) {
        this.resourceloader = resourceloader;
        this.turSNSiteFieldRepository = turSNSiteFieldRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNSiteFieldExtFacetRepository = turSNSiteFieldExtFacetRepository;
        this.turNLPInstanceRepository = turNLPInstanceRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSEInstanceRepository = turSEInstanceRepository;
        this.turSNRankingExpressionRepository = turSNRankingExpressionRepository;
        this.turSNRankingConditionRepository = turSNRankingConditionRepository;
        this.turConfigProperties = turConfigProperties;
    }

    @NotNull
    private static HashSet<TurSNSiteFieldExtFacet> getFacetLocales(String label) {
        TurSNSiteFieldExtFacet turSNSiteFieldExtFacet = new TurSNSiteFieldExtFacet();
        turSNSiteFieldExtFacet.setLocale(LocaleUtils.toLocale(PT_BR));
        turSNSiteFieldExtFacet.setLabel(label);
        return new HashSet<>(List.of(turSNSiteFieldExtFacet));
    }

    public void createSNSite(TurSNSite turSNSite, String username, Locale locale) {
        defaultSNUI(turSNSite);
        createLocale(turSNSite, username, locale);
        createSEFields(turSNSite);
    }

    public void defaultSNUI(TurSNSite turSNSite) {
        turSNSite.setRowsPerPage(10);
        turSNSite.setFacet(1);
        turSNSite.setItemsPerFacet(10);
        turSNSite.setHl(1);
        turSNSite.setHlPre("<mark>");
        turSNSite.setHlPost("</mark>");
        turSNSite.setMlt(1);
        turSNSite.setSpellCheck(1);
        turSNSite.setSpellCheckFixes(1);
        turSNSite.setThesaurus(0);
        turSNSite.setDefaultTitleField(TITLE);
        turSNSite.setDefaultTextField("text");
        turSNSite.setDefaultDescriptionField("abstract");
        turSNSite.setDefaultDateField("publication_date");
        turSNSite.setDefaultImageField("image");
        turSNSite.setDefaultURLField("url");
    }

    public String createSolrCore(TurSNSiteLocale turSNSiteLocale, String username) {
        final String coreName = getCoreName(turSNSiteLocale, username);
        Optional<TurSEInstance> turSEInstance = turSEInstanceRepository
                .findById(turSNSiteLocale.getTurSNSite().getTurSEInstance().getId());
        turSEInstance.ifPresent(instance -> {
            String configSet = turSNSiteLocale.getLanguage().getLanguage();
            String[] locales = {"en", "es", "pt"};
            if (!Arrays.asList(locales).contains(configSet)) {
                configSet = "en";
            }
            String solrURL = String.format("http://%s:%s", instance.getHost(), instance.getPort());
            if (turConfigProperties.getSolr().isCloud()) {
                try {
                    TurSolrUtils.createCollection(solrURL, coreName,
                            resourceloader.getResource(
                                            String.format("classpath:solr/configsets/%s.zip", configSet))
                                    .getInputStream(),
                            1);
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            } else {
                TurSolrUtils.createCore(solrURL, coreName, configSet);
            }
        });
        return coreName;
    }

    private String getCoreName(TurSNSiteLocale turSNSiteLocale, String username) {
        if (turConfigProperties.isMultiTenant()) {
            return String.format("%s_%s_%s", username,
                    turSNSiteLocale.getTurSNSite().getName().toLowerCase().replace(" ", "_"),
                    turSNSiteLocale.getLanguage());
        } else {
            return String.format("%s_%s",
                    turSNSiteLocale.getTurSNSite().getName().toLowerCase().replace(" ", "_"),
                    turSNSiteLocale.getLanguage());
        }
    }

    private void createSNSiteField(TurSNSite turSNSite, String name, String description, TurSEFieldType type,
                                   int multiValued, String facetName, HashSet<TurSNSiteFieldExtFacet> facetLocales, int hl) {
        TurSNSiteField turSNSiteField = new TurSNSiteField();
        turSNSiteField.setName(name);
        turSNSiteField.setDescription(description);
        turSNSiteField.setType(type);
        turSNSiteField.setMultiValued(multiValued);
        turSNSiteField.setTurSNSite(turSNSite);

        turSNSiteFieldRepository.save(turSNSiteField);
        TurSNSiteFieldExt turSNSiteFieldExt = TurSNSiteFieldExt.builder()
                .enabled(1)
                .name(turSNSiteField.getName())
                .description(turSNSiteField.getDescription())
                .facet(0)
                .facetName(facetName)
                .hl(hl)
                .multiValued(turSNSiteField.getMultiValued())
                .mlt(0)
                .externalId(turSNSiteField.getId())
                .snType(TurSNFieldType.SE)
                .type(turSNSiteField.getType())
                .turSNSite(turSNSite).build();
        turSNSiteFieldExtRepository.save(turSNSiteFieldExt);
        facetLocales.forEach(facetLocale -> {
            facetLocale.setTurSNSiteFieldExt(turSNSiteFieldExt);
            turSNSiteFieldExtFacetRepository.save(facetLocale);
        });
        turSNSiteLocaleRepository.findByTurSNSite(turSNSite).forEach(turSNSiteLocale ->
                createCopyField(multiValued, turSNSiteLocale, turSNSiteField));
    }

    private void createCopyField(int multiValued, TurSNSiteLocale turSNSiteLocale, TurSNSiteField turSNSiteField) {
        turSEInstanceRepository
                .findById(turSNSiteLocale.getTurSNSite().getTurSEInstance().getId()).ifPresent(turSEInstance -> {
                    if (TurSolrUtils.isCreateCopyFieldByCore(turSEInstance, turSNSiteLocale.getCore(),
                            turSNSiteField.getName(), turSNSiteField.getType())) {
                        TurSolrUtils.createCopyFieldByCore(turSEInstance, turSNSiteLocale.getCore(),
                                turSNSiteField.getName(), multiValued == 1);
                    }
                });
    }

    public void createSEFields(TurSNSite turSNSite) {
        createSNSiteField(turSNSite, TITLE, "Title Field", TurSEFieldType.TEXT, 0,
                "Titles", getFacetLocales("Titulos"), 1);
        createSNSiteField(turSNSite, "text", "Text Field", TurSEFieldType.TEXT, 0,
                "Texts", getFacetLocales("Textos"), 1);
        createSNSiteField(turSNSite, "abstract", "Short Description Field", TurSEFieldType.TEXT,
                0, "Abstracts", getFacetLocales("Resumos"), 1);
        createSNSiteField(turSNSite, "type", "Content Type Field", TurSEFieldType.STRING, 0,
                "Types", getFacetLocales("Tipos"), 1);
        createSNSiteField(turSNSite, "image", "Image Field", TurSEFieldType.STRING, 0,
                "Images", getFacetLocales("Images"), 0);
        createSNSiteField(turSNSite, "url", "URL Field", TurSEFieldType.STRING, 0,
                "URLs", getFacetLocales("URLs"), 0);
        createSNSiteField(turSNSite, "publication_date", "Publication Date", TurSEFieldType.DATE,
                0, "Publication Dates",
                getFacetLocales("Datas de Publicação"), 0);
        createSNSiteField(turSNSite, "modification_date", "Modification Date", TurSEFieldType.DATE,
                0, "Modification Dates",
                getFacetLocales("Datas de Modificação"), 0);
        createSNSiteField(turSNSite, "site", "Site Name", TurSEFieldType.TEXT, 0,
                "Sites", getFacetLocales("Nome dos Sites"), 0);
        createSNSiteField(turSNSite, "author", "Author", TurSEFieldType.STRING, 0,
                "Authors", getFacetLocales("Autores"), 0);
        createSNSiteField(turSNSite, "section", "Section", TurSEFieldType.STRING, 1,
                "Sections", getFacetLocales("Sessões"), 0);
        createSNSiteField(turSNSite, "source_apps", "Source Apps", TurSEFieldType.STRING, 1,
                "Source Apps", getFacetLocales("Apps de Origem"), 0);
    }

    public void createLocale(TurSNSite turSNSite, String username, Locale locale) {
        TurSNSiteLocale turSNSiteLocale = new TurSNSiteLocale();
        turSNSiteLocale.setLanguage(locale);
        turSNSiteLocale.setTurNLPInstance(turNLPInstanceRepository.findAll().getFirst());
        turSNSiteLocale.setTurSNSite(turSNSite);
        turSNSiteLocale.setCore(createSolrCore(turSNSiteLocale, username));
        turSNSiteLocaleRepository.save(turSNSiteLocale);
    }
}
