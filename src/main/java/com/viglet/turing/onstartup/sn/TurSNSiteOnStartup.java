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
			turSNSite.setLanguage("pt_BR");
			turSNSite.setCore("turing");

			turSNSite.setTurNLPInstance(turNLPInstanceRepository.findById(1));
			turSNSite.setTurSEInstance(turSEInstanceRepository.findById(1));

			// UI
			turSNSite.setRowsPerPage(10);
			turSNSite.setFacet(1);
			turSNSite.setItemsPerFacet(10);
			turSNSite.setHl(1);
			turSNSite.setHlPre("<mark>");
			turSNSite.setHlPost("</mark>");
			turSNSite.setMlt(1);

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
