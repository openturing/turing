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
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProvidersField;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersFieldRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightDocumentRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.solr.TurSolrUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;

/**
 * @author Alexandre Oliveira
 * @since 0.3.4
 */

@Component
public class TurSNTemplate {
	@Autowired
	private TurSNSiteFieldRepository turSNSiteFieldRepository;
	@Autowired
	private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
	@Autowired
	private TurNLPEntityRepository turNLPEntityRepository;
	@Autowired
	private TurSNSiteSpotlightRepository turSNSiteSpotlightRepository;
	@Autowired
	private TurSNSiteSpotlightDocumentRepository turSNSiteSpotlightDocumentRepository;
	@Autowired
	private TurSNSiteSpotlightTermRepository turSNSiteSpotlightTermRepository;
	@Autowired
	private TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
	@Autowired
	private TurSNSiteMergeProvidersRepository turSNSiteMergeRepository;
	@Autowired
	private TurSNSiteMergeProvidersFieldRepository turSNSiteMergeFieldRepository;
	@Autowired
	private TurSEInstanceRepository turSEInstanceRepository;

	public void createSNSite(TurSNSite turSNSite, String username, String locale) {
		defaultSNUI(turSNSite);
		createSEFields(turSNSite);
		createLocale(turSNSite, username);
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
		turSNSite.setDefaultTitleField("title");
		turSNSite.setDefaultTextField("text");
		turSNSite.setDefaultDescriptionField("abstract");
		turSNSite.setDefaultDateField("publication_date");
		turSNSite.setDefaultImageField("image");
		turSNSite.setDefaultURLField("url");
	}

	public String createSolrCore(TurSNSiteLocale turSNSiteLocale, String username) {
		String coreName = String.format("%s_%s_%s", username,
				turSNSiteLocale.getTurSNSite().getName().toLowerCase().replaceAll(" ", "_"),
				turSNSiteLocale.getLanguage());
		Optional<TurSEInstance> turSEInstance = turSEInstanceRepository
				.findById(turSNSiteLocale.getTurSNSite().getTurSEInstance().getId());
		turSEInstance.ifPresent(instance -> {
			String solrURL = String.format("http://%s:%s", instance.getHost(), instance.getPort());
			TurSolrUtils.createCore(solrURL, coreName, "en");

		});
		return coreName;
	}

	public void createNERFields(TurSNSite turSNSite) {
		TurSNSiteFieldExt turSNSiteFieldExt;
		TurNLPEntity turNLPEntity = turNLPEntityRepository.findByInternalName("PN");
		turSNSiteFieldExt = new TurSNSiteFieldExt();
		turSNSiteFieldExt.setEnabled(1);
		turSNSiteFieldExt.setName(turNLPEntity.getInternalName());
		turSNSiteFieldExt.setDescription(turNLPEntity.getDescription());
		turSNSiteFieldExt.setFacet(1);
		turSNSiteFieldExt.setFacetName("People");
		turSNSiteFieldExt.setHl(0);
		turSNSiteFieldExt.setMultiValued(1);
		turSNSiteFieldExt.setMlt(0);
		turSNSiteFieldExt.setExternalId(turNLPEntity.getInternalName());
		turSNSiteFieldExt.setSnType(TurSNFieldType.NER);
		turSNSiteFieldExt.setType(TurSEFieldType.STRING);
		turSNSiteFieldExt.setTurSNSite(turSNSite);

		turSNSiteFieldExtRepository.save(turSNSiteFieldExt);
	}

	private TurSNSiteField createSNSiteField(TurSNSite turSNSite, String name, String description, TurSEFieldType type,
			int multiValued, String facetName, int hl) {
		TurSNSiteField turSNSiteField = new TurSNSiteField();
		turSNSiteField.setName(name);
		turSNSiteField.setDescription(description);
		turSNSiteField.setType(type);
		turSNSiteField.setMultiValued(multiValued);
		turSNSiteField.setTurSNSite(turSNSite);

		turSNSiteFieldRepository.save(turSNSiteField);

		TurSNSiteFieldExt turSNSiteFieldExt = new TurSNSiteFieldExt();
		turSNSiteFieldExt.setEnabled(1);
		turSNSiteFieldExt.setName(turSNSiteField.getName());
		turSNSiteFieldExt.setDescription(turSNSiteField.getDescription());
		turSNSiteFieldExt.setFacet(0);
		turSNSiteFieldExt.setFacetName(facetName);
		turSNSiteFieldExt.setHl(hl);
		turSNSiteFieldExt.setMultiValued(turSNSiteField.getMultiValued());
		turSNSiteFieldExt.setMlt(0);
		turSNSiteFieldExt.setExternalId(turSNSiteField.getId());
		turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
		turSNSiteFieldExt.setType(turSNSiteField.getType());
		turSNSiteFieldExt.setTurSNSite(turSNSite);

		turSNSiteFieldExtRepository.save(turSNSiteFieldExt);

		return turSNSiteField;
	}

	public void createSEFields(TurSNSite turSNSite) {
		createSNSiteField(turSNSite, "title", "Title Field", TurSEFieldType.STRING, 0, "Titles", 1);
		createSNSiteField(turSNSite, "text", "Text Field", TurSEFieldType.STRING, 0, "Texts", 1);
		createSNSiteField(turSNSite, "abstract", "Short Description Field", TurSEFieldType.STRING, 0, "Abstracts", 1);
		createSNSiteField(turSNSite, "type", "Content Type Field", TurSEFieldType.STRING, 0, "Types", 1);
		createSNSiteField(turSNSite, "image", "Image Field", TurSEFieldType.STRING, 0, "Images", 0);
		createSNSiteField(turSNSite, "url", "URL Field", TurSEFieldType.STRING, 0, "URLs", 0);
		createSNSiteField(turSNSite, "publication_date", "Publication Date", TurSEFieldType.DATE, 0,
				"Publication Dates", 0);
		createSNSiteField(turSNSite, "modification_date", "Modification Date", TurSEFieldType.DATE, 0,
				"Modification Dates", 0);
		createSNSiteField(turSNSite, "site", "Site Name", TurSEFieldType.STRING, 0, "Sites", 0);
		createSNSiteField(turSNSite, "author", "Author", TurSEFieldType.STRING, 0, "Authors", 0);
		createSNSiteField(turSNSite, "section", "Section", TurSEFieldType.STRING, 0, "Sections", 0);
		createSNSiteField(turSNSite, "source_apps", "Source Apps", TurSEFieldType.STRING, 1, "Source Apps", 0);
	}

	public void createSpotlight(TurSNSite turSNSite, TurSNSiteLocale turSNSiteLocale) {
		TurSNSiteSpotlight turSNSiteSpotlight = new TurSNSiteSpotlight();
		turSNSiteSpotlight.setDescription("Spotlight Sample");
		turSNSiteSpotlight.setName("Spotlight Sample");
		turSNSiteSpotlight.setModificationDate(new Date());
		turSNSiteSpotlight.setManaged(1);
		turSNSiteSpotlight.setProvider("TURING");
		turSNSiteSpotlight.setTurSNSite(turSNSite);
		turSNSiteSpotlight.setLanguage(turSNSiteLocale.getLanguage());
		turSNSiteSpotlightRepository.save(turSNSiteSpotlight);

		TurSNSiteSpotlightDocument turSNSiteSpotlightDocument = new TurSNSiteSpotlightDocument();
		turSNSiteSpotlightDocument.setPosition(1);
		turSNSiteSpotlightDocument.setTitle("Viglet Docs");
		turSNSiteSpotlightDocument.setTurSNSiteSpotlight(turSNSiteSpotlight);
		turSNSiteSpotlightDocument.setReferenceId("https://docs.viglet.com/");
		turSNSiteSpotlightDocument.setLink("https://docs.viglet.com/");
		turSNSiteSpotlightDocument.setType("Page");
		turSNSiteSpotlightDocumentRepository.save(turSNSiteSpotlightDocument);

		TurSNSiteSpotlightTerm turSNSiteSpotlightTerm = new TurSNSiteSpotlightTerm();
		turSNSiteSpotlightTerm.setName("sample");
		turSNSiteSpotlightTerm.setTurSNSiteSpotlight(turSNSiteSpotlight);
		turSNSiteSpotlightTermRepository.save(turSNSiteSpotlightTerm);

		TurSNSiteSpotlightTerm turSNSiteSpotlightTerm2 = new TurSNSiteSpotlightTerm();
		turSNSiteSpotlightTerm2.setName("sample2");
		turSNSiteSpotlightTerm2.setTurSNSiteSpotlight(turSNSiteSpotlight);
		turSNSiteSpotlightTermRepository.save(turSNSiteSpotlightTerm2);
	}

	public TurSNSiteLocale createLocale(TurSNSite turSNSite, String username) {

		TurSNSiteLocale turSNSiteLocale = new TurSNSiteLocale();
		turSNSiteLocale.setLanguage(TurLocaleRepository.EN_US);
		turSNSiteLocale.setTurNLPInstance(turNLPInstanceRepository.findAll().get(0));
		turSNSiteLocale.setTurSNSite(turSNSite);
		turSNSiteLocale.setCore(createSolrCore(turSNSiteLocale, username));


		turSNSiteLocaleRepository.save(turSNSiteLocale);

		return turSNSiteLocale;

	}

	public void createMergeProviders(TurSNSite turSNSite) {

		TurSNSiteMergeProviders turSNSiteMerge = new TurSNSiteMergeProviders();
		turSNSiteMerge.setTurSNSite(turSNSite);
		turSNSiteMerge.setLocale(TurLocaleRepository.EN_US);
		turSNSiteMerge.setProviderFrom("Nutch");
		turSNSiteMerge.setProviderTo("WEM");
		turSNSiteMerge.setRelationFrom("id");
		turSNSiteMerge.setRelationTo("url");
		turSNSiteMerge.setDescription("Merge content from Nutch into existing WEM content.");

		turSNSiteMergeRepository.save(turSNSiteMerge);

		TurSNSiteMergeProvidersField turSNSiteMergeField = new TurSNSiteMergeProvidersField();
		turSNSiteMergeField.setName("text");
		turSNSiteMergeField.setTurSNSiteMergeProviders(turSNSiteMerge);

		turSNSiteMergeFieldRepository.save(turSNSiteMergeField);
	}
}
