/*
 * Copyright (C) 2016-2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.sn.template;

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
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
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersFieldRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightDocumentRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;
import com.viglet.turing.se.field.TurSEFieldType;
import com.viglet.turing.sn.TurSNFieldType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

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

	public TurSNSiteLocale createLocale(TurSNSite turSNSite) {

		TurSNSiteLocale turSNSiteLocale = new TurSNSiteLocale();
		turSNSiteLocale.setLanguage(TurLocaleRepository.EN_US);
		turSNSiteLocale.setCore("turing");
		turSNSiteLocale.setTurNLPInstance(turNLPInstanceRepository.findAll().get(0));
		turSNSiteLocale.setTurSNSite(turSNSite);
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
