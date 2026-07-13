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

import static com.viglet.turing.commons.sn.field.TurSNFieldName.ABSTRACT;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.AUTHOR;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.DEFAULT;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.EXACT_MATCH;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.ID;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.IMAGE;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.MODIFICATION_DATE;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.PUBLICATION_DATE;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.SECTION;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.SITE;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.SOURCE_APPS;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.TEXT;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.TITLE;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.TYPE;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.URL;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.LocaleUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.se.field.TurSEFieldType;
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
import com.viglet.turing.tenant.TurTenantCoreNaming;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.sn.TurSNFieldType;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Alexandre Oliveira
 * @since 0.3.4
 */

@Slf4j
@Component
public class TurSNTemplate {
        public static final String PT_BR = "pt_BR";
        private final TurSNSiteFieldRepository turSNSiteFieldRepository;
        private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
        private final TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository;
        private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
        private final TurSEInstanceRepository turSEInstanceRepository;
        private final TurTenantCoreNaming turTenantCoreNaming;
        private final TurSearchEnginePluginFactory pluginFactory;

        public TurSNTemplate(TurSNSiteFieldRepository turSNSiteFieldRepository,
                        TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
                        TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository,
                        TurSNSiteLocaleRepository turSNSiteLocaleRepository,
                        TurSEInstanceRepository turSEInstanceRepository,
                        TurTenantCoreNaming turTenantCoreNaming,
                        TurSearchEnginePluginFactory pluginFactory) {
                this.turSNSiteFieldRepository = turSNSiteFieldRepository;
                this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
                this.turSNSiteFieldExtFacetRepository = turSNSiteFieldExtFacetRepository;
                this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
                this.turSEInstanceRepository = turSEInstanceRepository;
                this.turTenantCoreNaming = turTenantCoreNaming;
                this.pluginFactory = pluginFactory;
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
                turSNSite.setExactMatch(1);
                turSNSite.setExactMatchField(EXACT_MATCH);
                turSNSite.setDefaultField(DEFAULT);
                turSNSite.setDefaultTitleField(TITLE);
                turSNSite.setDefaultTextField(TEXT);
                turSNSite.setDefaultDescriptionField(ABSTRACT);
                turSNSite.setDefaultDateField(PUBLICATION_DATE);
                turSNSite.setDefaultImageField(IMAGE);
                turSNSite.setDefaultURLField(URL);
        }

        public String createSolrCore(TurSNSiteLocale turSNSiteLocale, String username) {
                final String coreName = getCoreName(turSNSiteLocale, username);
                turSEInstanceRepository
                                .findById(turSNSiteLocale.getTurSNSite().getTurSEInstance().getId())
                                .ifPresent(instance ->
                                        pluginFactory.getPluginForInstance(instance)
                                                .createIndex(instance, turSNSiteLocale, coreName, Map.of())
                                );
                return coreName;
        }

        private String getCoreName(TurSNSiteLocale turSNSiteLocale, String username) {
                if (StringUtils.hasText(turSNSiteLocale.getCore())) {
                        return turSNSiteLocale.getCore();
                }
                // T268 / §XIV.4.2 — prefix the core with the current tenant so two
                // tenants' same-named sites never share a Solr/ES core. No-op when
                // tenancy is off (legacy `<site>_<lang>` name preserved).
                String baseCoreName = String.format("%s_%s",
                                turSNSiteLocale.getTurSNSite().getName().toLowerCase().replace(" ", "_"),
                                turSNSiteLocale.getLanguage());
                return turTenantCoreNaming.scoped(baseCoreName);
        }

        private void sendFieldsToService(TurSNSite turSNSite, List<TurSNFieldDefinition> fieldDefinitions) {
                fieldDefinitions.forEach(fieldDefinition -> createSNSiteField(fieldDefinition, turSNSite));
        }

        private void createSNSiteField(TurSNFieldDefinition fieldDefinition, TurSNSite turSNSite) {
                TurSNSiteField turSNSiteField = new TurSNSiteField();
                turSNSiteField.setName(fieldDefinition.name());
                turSNSiteField.setDescription(fieldDefinition.description());
                turSNSiteField.setType(fieldDefinition.type());
                turSNSiteField.setMultiValued(fieldDefinition.multiValued());
                turSNSiteField.setTurSNSite(turSNSite);

                turSNSiteFieldRepository.save(turSNSiteField);
                TurSNSiteFieldExt turSNSiteFieldExt = TurSNSiteFieldExt.builder()
                                .enabled(1)
                                .name(turSNSiteField.getName())
                                .description(turSNSiteField.getDescription())
                                .facet(0)
                                .facetName(fieldDefinition.facetName())
                                // T707 — reserved identifier/URL fields must never be
                                // highlightable: a <mark> in `id` corrupts the value clients
                                // feed to /search/similar (breaks "Related"). Coerce hl=0
                                // regardless of the template definition.
                                .hl(com.viglet.turing.commons.sn.field.TurSNFieldName
                                                .isNonHighlightable(turSNSiteField.getName()) ? 0 : fieldDefinition.hl())
                                .multiValued(turSNSiteField.getMultiValued())
                                .mlt(0)
                                .externalId(turSNSiteField.getId())
                                .snType(TurSNFieldType.SE)
                                .type(turSNSiteField.getType())
                                .turSNSite(turSNSite).build();
                turSNSiteFieldExtRepository.save(turSNSiteFieldExt);
                fieldDefinition.locales().forEach(facetLocale -> {
                        facetLocale.setTurSNSiteFieldExt(turSNSiteFieldExt);
                        turSNSiteFieldExtFacetRepository.save(facetLocale);
                });
                turSNSiteLocaleRepository.findByTurSNSite(turSNSite).forEach(
                                turSNSiteLocale -> createCopyField(fieldDefinition.multiValued(), turSNSiteLocale,
                                                turSNSiteField));
        }

        private void createCopyField(int multiValued, TurSNSiteLocale turSNSiteLocale, TurSNSiteField turSNSiteField) {
                turSEInstanceRepository
                                .findById(turSNSiteLocale.getTurSNSite().getTurSEInstance().getId())
                                .ifPresent(turSEInstance ->
                                        pluginFactory.getPluginForInstance(turSEInstance)
                                                .createCopyField(turSEInstance, turSNSiteLocale.getCore(),
                                                        turSNSiteField.getName(), turSNSiteField.getType(),
                                                        multiValued == 1));
        }

        public void createSEFields(TurSNSite turSNSite) {
                List<TurSNFieldDefinition> fields = List.of(
                                new TurSNFieldDefinition(ID, "Id Field", TurSEFieldType.STRING, 0, "Ids",
                                                getFacetLocales("Ids"), 1),
                                new TurSNFieldDefinition(TITLE, "Title Field", TurSEFieldType.TEXT, 0, "Titles",
                                                getFacetLocales("Titulos"), 1),
                                new TurSNFieldDefinition(TEXT, "Text Field", TurSEFieldType.TEXT, 0, "Texts",
                                                getFacetLocales("Textos"), 1),
                                new TurSNFieldDefinition(ABSTRACT, "Short Description Field", TurSEFieldType.TEXT, 0,
                                                "Abstracts", getFacetLocales("Resumos"), 1),
                                new TurSNFieldDefinition(TYPE, "Content Type Field", TurSEFieldType.STRING, 0, "Types",
                                                getFacetLocales("Tipos"), 1),
                                new TurSNFieldDefinition(IMAGE, "Image Field", TurSEFieldType.STRING, 0, "Images",
                                                getFacetLocales("Images"), 0),
                                new TurSNFieldDefinition(URL, "URL Field", TurSEFieldType.STRING, 0, "URLs",
                                                getFacetLocales("URLs"), 0),
                                new TurSNFieldDefinition(PUBLICATION_DATE, "Publication Date", TurSEFieldType.DATE, 0,
                                                "Publication Dates", getFacetLocales("Datas de Publicação"), 0),
                                new TurSNFieldDefinition(MODIFICATION_DATE, "Modification Date", TurSEFieldType.DATE, 0,
                                                "Modification Dates", getFacetLocales("Datas de Modificação"), 0),
                                new TurSNFieldDefinition(SITE, "Site Name", TurSEFieldType.STRING, 0, "Sites",
                                                getFacetLocales("Nome dos Sites"), 0),
                                new TurSNFieldDefinition(AUTHOR, "Author", TurSEFieldType.STRING, 0, "Authors",
                                                getFacetLocales("Autores"), 0),
                                new TurSNFieldDefinition(SECTION, "Section", TurSEFieldType.STRING, 0, "Sections",
                                                getFacetLocales("Sessões"), 0),
                                new TurSNFieldDefinition(SOURCE_APPS, "Source Apps", TurSEFieldType.STRING, 1,
                                                "Source Apps", getFacetLocales("Apps de Origem"), 0));
                sendFieldsToService(turSNSite, fields);
        }

        public void createLocale(TurSNSite turSNSite, String username, Locale locale) {
                TurSNSiteLocale turSNSiteLocale = new TurSNSiteLocale();
                turSNSiteLocale.setLanguage(locale);
                turSNSiteLocale.setTurSNSite(turSNSite);
                turSNSiteLocale.setCore(createSolrCore(turSNSiteLocale, username));
                turSNSiteLocaleRepository.save(turSNSiteLocale);
        }
}
