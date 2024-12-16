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

package com.viglet.turing.onstartup.ml;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.ml.TurMLVendor;
import com.viglet.turing.persistence.repository.ml.TurMLVendorRepository;

@Component
@Transactional
public class TurMLVendorOnStartup {

	@Autowired
	private TurMLVendorRepository turMLVendorRepository;

	public void createDefaultRows() {

		if (turMLVendorRepository.findAll().isEmpty()) {

			TurMLVendor turMLVendor = new TurMLVendor();
			turMLVendor.setId("OPENNLP");
			turMLVendor.setDescription("Apache OpenNLP");
			turMLVendor.setPlugin("");
			turMLVendor.setTitle("Apache OpenNLP");
			turMLVendor.setWebsite("https://opennlp.apache.org");
			turMLVendorRepository.save(turMLVendor);
		}
	}

}