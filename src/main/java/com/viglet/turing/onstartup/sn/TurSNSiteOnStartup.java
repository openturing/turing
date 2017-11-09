package com.viglet.turing.onstartup.sn;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.se.field.TurSEFieldType;

@Component
@Transactional
public class TurSNSiteOnStartup {

	@Autowired
	private TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private TurSNSiteFieldRepository turSNSiteFieldRepository;
	@Autowired
	private TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	private TurSEInstanceRepository turSEInstanceRepository;

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
			turSNSiteField.setFacet(0);
			turSNSiteField.setFacetName("Title");
			turSNSiteField.setHl(1);
			turSNSiteField.setMultiValued(0);
			turSNSiteField.setTurSNSite(turSNSite);
			turSNSiteField.setType(TurSEFieldType.STRING);
			
			turSNSiteFieldRepository.save(turSNSiteField);

			// Text
			turSNSiteField = new TurSNSiteField();
			turSNSiteField.setName("text");
			turSNSiteField.setDescription("Text Field");
			turSNSiteField.setFacet(0);
			turSNSiteField.setFacetName("Text");
			turSNSiteField.setHl(1);
			turSNSiteField.setMultiValued(0);
			turSNSiteField.setTurSNSite(turSNSite);
			turSNSiteField.setType(TurSEFieldType.STRING);
			
			turSNSiteFieldRepository.save(turSNSiteField);

			// Abstract
			turSNSiteField = new TurSNSiteField();
			turSNSiteField.setName("abstract");
			turSNSiteField.setDescription("Short Description Field");
			turSNSiteField.setFacet(0);
			turSNSiteField.setFacetName("Abstract");
			turSNSiteField.setHl(1);
			turSNSiteField.setMultiValued(0);
			turSNSiteField.setTurSNSite(turSNSite);
			turSNSiteField.setType(TurSEFieldType.STRING);

			turSNSiteFieldRepository.save(turSNSiteField);
		}
	}
}
