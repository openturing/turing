package com.viglet.turing.exchange.sn;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

@Component
public class TurSNSiteImport {

	@Autowired
	TurSNSiteRepository turSNSiteRepository;
	@Autowired
	TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	TurSEInstanceRepository turSEInstanceRepository;
	@Autowired
	TurSNSiteFieldRepository turSNSiteFieldRepository;

	public void importSNSite(TurExchange turExchange) throws IOException {
		for (TurSNSiteExchange turSNSiteExchange : turExchange.getSnSites()) {
			if (!turSNSiteRepository.findById(turSNSiteExchange.getId()).isPresent()) {
				
				TurSNSite turSNSite = new TurSNSite();
				turSNSite.setCore(turSNSiteExchange.getCore());
				turSNSite.setDefaultDateField(turSNSiteExchange.getDefaultDateField());
				turSNSite.setDefaultDescriptionField(turSNSiteExchange.getDefaultDescriptionField());
				turSNSite.setDefaultImageField(turSNSiteExchange.getDefaultImageField());
				turSNSite.setDefaultTextField(turSNSiteExchange.getDefaultTextField());
				turSNSite.setDefaultTitleField(turSNSiteExchange.getDefaultTitleField());
				turSNSite.setDefaultURLField(turSNSiteExchange.getDefaultURLField());
				turSNSite.setDescription(turSNSiteExchange.getDescription());
				turSNSite.setFacet(turSNSiteExchange.getFacet() ? (byte) 1 : (byte) 0);
				turSNSite.setHl(turSNSiteExchange.getHl() ? (byte) 1 : (byte) 0);
				turSNSite.setHlPost(turSNSiteExchange.getHlPost());
				turSNSite.setHlPre(turSNSiteExchange.getHlPre());
				turSNSite.setId(turSNSiteExchange.getId());
				turSNSite.setItemsPerFacet(turSNSiteExchange.getItemsPerFacet());
				turSNSite.setLanguage(turSNSiteExchange.getLanguage());
				turSNSite.setMlt(turSNSiteExchange.getMlt() ? (byte) 1 : (byte) 0);
				turSNSite.setName(turSNSiteExchange.getName());
				turSNSite.setRowsPerPage(turSNSiteExchange.getRowsPerPage());
				turSNSite.setThesaurus(turSNSiteExchange.getThesaurus() ? (byte) 1 : (byte) 0);
				turSNSite.setTurNLPInstance(turNLPInstanceRepository.findById(turSNSiteExchange.getTurNLPInstance()));
				turSNSite.setTurSEInstance(turSEInstanceRepository.findById(turSNSiteExchange.getTurSEInstance()));

				turSNSiteRepository.save(turSNSite);

				for (TurSNSiteField turSNSiteField : turSNSiteExchange.getTurSNSiteFields()) {
					turSNSiteField.setTurSNSite(turSNSite);
					turSNSiteFieldRepository.save(turSNSiteField);
				}
			}
		}
	}
}
