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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightDocument;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightDocumentRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightRepository;
import com.viglet.turing.persistence.repository.sn.spotlight.TurSNSiteSpotlightTermRepository;
import com.viglet.turing.se.field.TurSEFieldType;
import com.viglet.turing.sn.TurSNFieldType;

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
	
	public void defaultSNUI(TurSNSite turSNSite) {
		turSNSite.setRowsPerPage(10);
		turSNSite.setFacet(1);
		turSNSite.setItemsPerFacet(10);
		turSNSite.setHl(1);
		turSNSite.setHlPre("<mark>");
		turSNSite.setHlPost("</mark>");
		turSNSite.setMlt(1);
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
		// PN
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
		turSNSiteFieldExt.setExternalId(turNLPEntity.getId());
		turSNSiteFieldExt.setSnType(TurSNFieldType.NER);
		turSNSiteFieldExt.setType(TurSEFieldType.STRING);
		turSNSiteFieldExt.setTurSNSite(turSNSite);

		turSNSiteFieldExtRepository.save(turSNSiteFieldExt);
	}

	public void createSEFields(TurSNSite turSNSite) {
		// Title
		TurSNSiteField turSNSiteField = new TurSNSiteField();
		turSNSiteField.setName("title");
		turSNSiteField.setDescription("Title Field");
		turSNSiteField.setType(TurSEFieldType.STRING);
		turSNSiteField.setMultiValued(0);
		turSNSiteField.setTurSNSite(turSNSite);

		turSNSiteFieldRepository.save(turSNSiteField);

		TurSNSiteFieldExt turSNSiteFieldExt = new TurSNSiteFieldExt();
		turSNSiteFieldExt.setEnabled(1);
		turSNSiteFieldExt.setName(turSNSiteField.getName());
		turSNSiteFieldExt.setDescription(turSNSiteField.getDescription());
		turSNSiteFieldExt.setFacet(0);
		turSNSiteFieldExt.setFacetName("Title");
		turSNSiteFieldExt.setHl(1);
		turSNSiteFieldExt.setMultiValued(turSNSiteField.getMultiValued());
		turSNSiteFieldExt.setMlt(0);
		turSNSiteFieldExt.setExternalId(turSNSiteField.getId());
		turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
		turSNSiteFieldExt.setType(turSNSiteField.getType());
		turSNSiteFieldExt.setTurSNSite(turSNSite);

		turSNSiteFieldExtRepository.save(turSNSiteFieldExt);

		// Text
		turSNSiteField = new TurSNSiteField();
		turSNSiteField.setName("text");
		turSNSiteField.setDescription("Text Field");
		turSNSiteField.setMultiValued(0);
		turSNSiteField.setTurSNSite(turSNSite);
		turSNSiteField.setType(TurSEFieldType.STRING);

		turSNSiteFieldRepository.save(turSNSiteField);

		turSNSiteFieldExt = new TurSNSiteFieldExt();
		turSNSiteFieldExt.setEnabled(1);
		turSNSiteFieldExt.setName(turSNSiteField.getName());
		turSNSiteFieldExt.setDescription(turSNSiteField.getDescription());
		turSNSiteFieldExt.setFacet(0);
		turSNSiteFieldExt.setFacetName("Text");
		turSNSiteFieldExt.setHl(1);
		turSNSiteFieldExt.setMultiValued(turSNSiteField.getMultiValued());
		turSNSiteFieldExt.setMlt(0);
		turSNSiteFieldExt.setExternalId(turSNSiteField.getId());
		turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
		turSNSiteFieldExt.setType(turSNSiteField.getType());
		turSNSiteFieldExt.setTurSNSite(turSNSite);

		turSNSiteFieldExtRepository.save(turSNSiteFieldExt);

		// Abstract
		turSNSiteField = new TurSNSiteField();
		turSNSiteField.setName("abstract");
		turSNSiteField.setDescription("Short Description Field");
		turSNSiteField.setMultiValued(0);
		turSNSiteField.setTurSNSite(turSNSite);
		turSNSiteField.setType(TurSEFieldType.STRING);

		turSNSiteFieldRepository.save(turSNSiteField);

		turSNSiteFieldExt = new TurSNSiteFieldExt();
		turSNSiteFieldExt.setEnabled(1);
		turSNSiteFieldExt.setName(turSNSiteField.getName());
		turSNSiteFieldExt.setDescription(turSNSiteField.getDescription());
		turSNSiteFieldExt.setFacet(0);
		turSNSiteFieldExt.setFacetName("Abstract");
		turSNSiteFieldExt.setHl(1);
		turSNSiteFieldExt.setMultiValued(turSNSiteField.getMultiValued());
		turSNSiteFieldExt.setMlt(0);
		turSNSiteFieldExt.setExternalId(turSNSiteField.getId());
		turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
		turSNSiteFieldExt.setType(turSNSiteField.getType());
		turSNSiteFieldExt.setTurSNSite(turSNSite);

		turSNSiteFieldExtRepository.save(turSNSiteFieldExt);

		// Type
		turSNSiteField = new TurSNSiteField();
		turSNSiteField.setName("type");
		turSNSiteField.setDescription("Content Type Field");
		turSNSiteField.setMultiValued(0);
		turSNSiteField.setTurSNSite(turSNSite);
		turSNSiteField.setType(TurSEFieldType.STRING);

		turSNSiteFieldRepository.save(turSNSiteField);

		turSNSiteFieldExt = new TurSNSiteFieldExt();
		turSNSiteFieldExt.setEnabled(1);
		turSNSiteFieldExt.setName(turSNSiteField.getName());
		turSNSiteFieldExt.setDescription(turSNSiteField.getDescription());
		turSNSiteFieldExt.setFacet(1);
		turSNSiteFieldExt.setFacetName("Types");
		turSNSiteFieldExt.setHl(1);
		turSNSiteFieldExt.setMultiValued(turSNSiteField.getMultiValued());
		turSNSiteFieldExt.setMlt(0);
		turSNSiteFieldExt.setExternalId(turSNSiteField.getId());
		turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
		turSNSiteFieldExt.setType(turSNSiteField.getType());
		turSNSiteFieldExt.setTurSNSite(turSNSite);

		turSNSiteFieldExtRepository.save(turSNSiteFieldExt);

		// Image
		turSNSiteField = new TurSNSiteField();
		turSNSiteField.setName("image");
		turSNSiteField.setDescription("Image Field");
		turSNSiteField.setMultiValued(0);
		turSNSiteField.setTurSNSite(turSNSite);
		turSNSiteField.setType(TurSEFieldType.STRING);

		turSNSiteFieldRepository.save(turSNSiteField);

		turSNSiteFieldExt = new TurSNSiteFieldExt();
		turSNSiteFieldExt.setEnabled(1);
		turSNSiteFieldExt.setName(turSNSiteField.getName());
		turSNSiteFieldExt.setDescription(turSNSiteField.getDescription());
		turSNSiteFieldExt.setFacet(1);
		turSNSiteFieldExt.setFacetName("Images");
		turSNSiteFieldExt.setHl(0);
		turSNSiteFieldExt.setMultiValued(turSNSiteField.getMultiValued());
		turSNSiteFieldExt.setMlt(0);
		turSNSiteFieldExt.setExternalId(turSNSiteField.getId());
		turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
		turSNSiteFieldExt.setType(turSNSiteField.getType());
		turSNSiteFieldExt.setTurSNSite(turSNSite);

		turSNSiteFieldExtRepository.save(turSNSiteFieldExt);

		// URL
		turSNSiteField = new TurSNSiteField();
		turSNSiteField.setName("url");
		turSNSiteField.setDescription("URL Field");
		turSNSiteField.setMultiValued(0);
		turSNSiteField.setTurSNSite(turSNSite);
		turSNSiteField.setType(TurSEFieldType.STRING);

		turSNSiteFieldRepository.save(turSNSiteField);

		turSNSiteFieldExt = new TurSNSiteFieldExt();
		turSNSiteFieldExt.setEnabled(1);
		turSNSiteFieldExt.setName(turSNSiteField.getName());
		turSNSiteFieldExt.setDescription(turSNSiteField.getDescription());
		turSNSiteFieldExt.setFacet(0);
		turSNSiteFieldExt.setFacetName("URLs");
		turSNSiteFieldExt.setHl(0);
		turSNSiteFieldExt.setMultiValued(turSNSiteField.getMultiValued());
		turSNSiteFieldExt.setMlt(0);
		turSNSiteFieldExt.setExternalId(turSNSiteField.getId());
		turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
		turSNSiteFieldExt.setType(turSNSiteField.getType());
		turSNSiteFieldExt.setTurSNSite(turSNSite);

		turSNSiteFieldExtRepository.save(turSNSiteFieldExt);

		// Publication Date
		turSNSiteField = new TurSNSiteField();
		turSNSiteField.setName("publication_date");
		turSNSiteField.setDescription("Publication Date");
		turSNSiteField.setMultiValued(0);
		turSNSiteField.setTurSNSite(turSNSite);
		turSNSiteField.setType(TurSEFieldType.DATE);

		turSNSiteFieldRepository.save(turSNSiteField);

		turSNSiteFieldExt = new TurSNSiteFieldExt();
		turSNSiteFieldExt.setEnabled(1);
		turSNSiteFieldExt.setName(turSNSiteField.getName());
		turSNSiteFieldExt.setDescription(turSNSiteField.getDescription());
		turSNSiteFieldExt.setFacet(0);
		turSNSiteFieldExt.setFacetName("Publication Dates");
		turSNSiteFieldExt.setHl(0);
		turSNSiteFieldExt.setMultiValued(turSNSiteField.getMultiValued());
		turSNSiteFieldExt.setMlt(0);
		turSNSiteFieldExt.setExternalId(turSNSiteField.getId());
		turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
		turSNSiteFieldExt.setType(turSNSiteField.getType());
		turSNSiteFieldExt.setTurSNSite(turSNSite);

		turSNSiteFieldExtRepository.save(turSNSiteFieldExt);

		// Modification Date
		turSNSiteField = new TurSNSiteField();
		turSNSiteField.setName("modification_date");
		turSNSiteField.setDescription("Modification Date");
		turSNSiteField.setMultiValued(0);
		turSNSiteField.setTurSNSite(turSNSite);
		turSNSiteField.setType(TurSEFieldType.DATE);

		turSNSiteFieldRepository.save(turSNSiteField);

		turSNSiteFieldExt = new TurSNSiteFieldExt();
		turSNSiteFieldExt.setEnabled(1);
		turSNSiteFieldExt.setName(turSNSiteField.getName());
		turSNSiteFieldExt.setDescription(turSNSiteField.getDescription());
		turSNSiteFieldExt.setFacet(0);
		turSNSiteFieldExt.setFacetName("Modification Dates");
		turSNSiteFieldExt.setHl(0);
		turSNSiteFieldExt.setMultiValued(turSNSiteField.getMultiValued());
		turSNSiteFieldExt.setMlt(0);
		turSNSiteFieldExt.setExternalId(turSNSiteField.getId());
		turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
		turSNSiteFieldExt.setType(turSNSiteField.getType());
		turSNSiteFieldExt.setTurSNSite(turSNSite);

		turSNSiteFieldExtRepository.save(turSNSiteFieldExt);

		// Site
		turSNSiteField = new TurSNSiteField();
		turSNSiteField.setName("site");
		turSNSiteField.setDescription("Site Name");
		turSNSiteField.setMultiValued(0);
		turSNSiteField.setTurSNSite(turSNSite);
		turSNSiteField.setType(TurSEFieldType.STRING);

		turSNSiteFieldRepository.save(turSNSiteField);

		turSNSiteFieldExt = new TurSNSiteFieldExt();
		turSNSiteFieldExt.setEnabled(1);
		turSNSiteFieldExt.setName(turSNSiteField.getName());
		turSNSiteFieldExt.setDescription(turSNSiteField.getDescription());
		turSNSiteFieldExt.setFacet(0);
		turSNSiteFieldExt.setFacetName("Sites");
		turSNSiteFieldExt.setHl(0);
		turSNSiteFieldExt.setMultiValued(turSNSiteField.getMultiValued());
		turSNSiteFieldExt.setMlt(0);
		turSNSiteFieldExt.setExternalId(turSNSiteField.getId());
		turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
		turSNSiteFieldExt.setType(turSNSiteField.getType());
		turSNSiteFieldExt.setTurSNSite(turSNSite);

		turSNSiteFieldExtRepository.save(turSNSiteFieldExt);

		// Author
		turSNSiteField = new TurSNSiteField();
		turSNSiteField.setName("author");
		turSNSiteField.setDescription("Author");
		turSNSiteField.setMultiValued(0);
		turSNSiteField.setTurSNSite(turSNSite);
		turSNSiteField.setType(TurSEFieldType.STRING);

		turSNSiteFieldRepository.save(turSNSiteField);

		turSNSiteFieldExt = new TurSNSiteFieldExt();
		turSNSiteFieldExt.setEnabled(1);
		turSNSiteFieldExt.setName(turSNSiteField.getName());
		turSNSiteFieldExt.setDescription(turSNSiteField.getDescription());
		turSNSiteFieldExt.setFacet(0);
		turSNSiteFieldExt.setFacetName("Authors");
		turSNSiteFieldExt.setHl(0);
		turSNSiteFieldExt.setMultiValued(turSNSiteField.getMultiValued());
		turSNSiteFieldExt.setMlt(0);
		turSNSiteFieldExt.setExternalId(turSNSiteField.getId());
		turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
		turSNSiteFieldExt.setType(turSNSiteField.getType());
		turSNSiteFieldExt.setTurSNSite(turSNSite);

		turSNSiteFieldExtRepository.save(turSNSiteFieldExt);

		// Section
		turSNSiteField = new TurSNSiteField();
		turSNSiteField.setName("section");
		turSNSiteField.setDescription("Section");
		turSNSiteField.setMultiValued(0);
		turSNSiteField.setTurSNSite(turSNSite);
		turSNSiteField.setType(TurSEFieldType.STRING);

		turSNSiteFieldRepository.save(turSNSiteField);

		turSNSiteFieldExt = new TurSNSiteFieldExt();
		turSNSiteFieldExt.setEnabled(1);
		turSNSiteFieldExt.setName(turSNSiteField.getName());
		turSNSiteFieldExt.setDescription(turSNSiteField.getDescription());
		turSNSiteFieldExt.setFacet(0);
		turSNSiteFieldExt.setFacetName("Sections");
		turSNSiteFieldExt.setHl(0);
		turSNSiteFieldExt.setMultiValued(turSNSiteField.getMultiValued());
		turSNSiteFieldExt.setMlt(0);
		turSNSiteFieldExt.setExternalId(turSNSiteField.getId());
		turSNSiteFieldExt.setSnType(TurSNFieldType.SE);
		turSNSiteFieldExt.setType(turSNSiteField.getType());
		turSNSiteFieldExt.setTurSNSite(turSNSite);

		turSNSiteFieldExtRepository.save(turSNSiteFieldExt);
	}

	public void createSpotlight(TurSNSite turSNSite) {
		TurSNSiteSpotlight turSNSiteSpotlight = new TurSNSiteSpotlight();
		turSNSiteSpotlight.setDescription("Spotlight Sample");
		turSNSiteSpotlight.setName("Spotlight Sample");
		turSNSiteSpotlight.setTurSNSite(turSNSite);
		turSNSiteSpotlightRepository.save(turSNSiteSpotlight);

		TurSNSiteSpotlightDocument turSNSiteSpotlightDocument = new TurSNSiteSpotlightDocument();
		turSNSiteSpotlightDocument.setPosition(1);
		turSNSiteSpotlightDocument.setTitle("Sample Document");
		turSNSiteSpotlightDocument.setTurSNSiteSpotlight(turSNSiteSpotlight);
		turSNSiteSpotlightDocument.setType("News");
		turSNSiteSpotlightDocumentRepository.save(turSNSiteSpotlightDocument);
		
		TurSNSiteSpotlightTerm turSNSiteSpotlightTerm = new TurSNSiteSpotlightTerm();
		turSNSiteSpotlightTerm.setTerm("sample");
		turSNSiteSpotlightTerm.setTurSNSiteSpotlight(turSNSiteSpotlight);
		turSNSiteSpotlightTermRepository.save(turSNSiteSpotlightTerm);
	}
}
