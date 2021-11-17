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

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

import com.viglet.turing.sn.template.TurSNTemplate;

@Component
@Transactional
public class TurSNSiteOnStartup {

	@Autowired
	private TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private TurSEInstanceRepository turSEInstanceRepository;
	@Autowired
	private TurNLPVendorRepository turNLPVendorRepository;
	@Autowired
	private TurSNTemplate turSNTemplate;
	
	public void createDefaultRows() {

		if (turSNSiteRepository.findAll().isEmpty()) {

			TurSNSite turSNSite = new TurSNSite();

			// Detail
			turSNSite.setName("Sample");
			turSNSite.setDescription("Semantic Sample Site");
			turSNSite.setTurNLPVendor(turNLPVendorRepository.findAll().get(0));
			turSNSite.setTurSEInstance(turSEInstanceRepository.findAll().get(0));
						
			turSNTemplate.defaultSNUI(turSNSite);

			turSNSiteRepository.save(turSNSite);

			turSNTemplate.createSEFields(turSNSite);

			turSNTemplate.createNERFields(turSNSite);

			turSNTemplate.createLocale(turSNSite);

			turSNTemplate.createSpotlight(turSNSite);
		}
	}

	
}
