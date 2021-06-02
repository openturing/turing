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

package com.viglet.turing.onstartup.se;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;

@Component
@Transactional
public class TurSEInstanceOnStartup {
	
	@Autowired
	private TurSEInstanceRepository turSEInstanceRepository;
	@Autowired
	private TurSEVendorRepository turSEVendorRepository;
	@Autowired
	private TurConfigVarRepository turConfigVarRepository;
	
	public void createDefaultRows() {


		TurConfigVar turConfigVar = new TurConfigVar();
		
		if (turSEInstanceRepository.findAll().isEmpty()) {

			TurSEVendor turSEVendor = turSEVendorRepository.getOne("SOLR");
			if (turSEVendor != null) {
				TurSEInstance turSEInstance = new TurSEInstance();
				turSEInstance.setTitle("Apache Solr");
				turSEInstance.setDescription("Solr Production");
				turSEInstance.setTurSEVendor(turSEVendor);
				turSEInstance.setHost("turing-solr");
				turSEInstance.setPort(8983);
				turSEInstance.setLanguage(TurLocaleRepository.EN_US);
				turSEInstance.setEnabled(1);
				turSEInstanceRepository.save(turSEInstance);
				
				turConfigVar.setId("DEFAULT_SE");
				turConfigVar.setPath("/se");
				turConfigVar.setValue(turSEInstance.getId());
				turConfigVarRepository.save(turConfigVar);
			}
		}

	}
	
	
}
