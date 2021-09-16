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
				turSNSite.setTurNLPInstance(turNLPInstanceRepository.findById(turSNSiteExchange.getTurNLPInstance()).orElse(null));
				turSNSite.setTurSEInstance(turSEInstanceRepository.findById(turSNSiteExchange.getTurSEInstance()).orElse(null));

				turSNSiteRepository.save(turSNSite);

				for (TurSNSiteField turSNSiteField : turSNSiteExchange.getTurSNSiteFields()) {
					turSNSiteField.setTurSNSite(turSNSite);
					turSNSiteFieldRepository.save(turSNSiteField);
				}
			}
		}
	}
}
