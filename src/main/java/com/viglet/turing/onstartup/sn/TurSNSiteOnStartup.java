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

package com.viglet.turing.onstartup.sn;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;
import com.viglet.turing.se.field.TurSEFieldType;
import com.viglet.turing.sn.TurSNFieldType;

@Component
@Transactional
public class TurSNSiteOnStartup {

	@Autowired
	private TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private TurSNSiteFieldRepository turSNSiteFieldRepository;
	@Autowired
	private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
	@Autowired
	private TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	private TurSEInstanceRepository turSEInstanceRepository;
	@Autowired
	private TurNLPEntityRepository turNLPEntityRepository;

	public void createDefaultRows() {

		if (turSNSiteRepository.findAll().isEmpty()) {

			TurSNSite turSNSite = new TurSNSite();

			// Detail
			turSNSite.setName("Sample");
			turSNSite.setDescription("Semantic Sample Site");
			turSNSite.setLanguage(TurLocaleRepository.EN_US);
			turSNSite.setCore("turing");

			turSNSite.setTurNLPInstance(turNLPInstanceRepository.findAll().get(0));
			turSNSite.setTurSEInstance(turSEInstanceRepository.findAll().get(0));

			// UI
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

			turSNSiteRepository.save(turSNSite);

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

			// NER
			// ---- PN
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
	}
}
