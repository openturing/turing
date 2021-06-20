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

import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;

@Component
@Transactional
public class TurSEVendorOnStartup {

	@Autowired
	private TurSEVendorRepository turSEVendorRepository;
	
	public void createDefaultRows() {

		if (turSEVendorRepository.findAll().isEmpty()) {
			
			TurSEVendor turSEVendor = new TurSEVendor();
			turSEVendor.setId("SOLR");
			turSEVendor.setDescription("Apache Solr");
			turSEVendor.setPlugin("");
			turSEVendor.setTitle("Apache Solr");
			turSEVendor.setWebsite("http://lucene.apache.org/solr");
			turSEVendorRepository.save(turSEVendor);
			
			turSEVendor = new TurSEVendor();
			turSEVendor.setId("SPHINX");
			turSEVendor.setDescription("Sphinx");
			turSEVendor.setPlugin("");
			turSEVendor.setTitle("Sphinx");
			turSEVendor.setWebsite("http://sphinxsearch.com");
			turSEVendorRepository.save(turSEVendor);
			
			turSEVendor = new TurSEVendor();
			turSEVendor.setId("ELASTIC");
			turSEVendor.setDescription("ElasticSearch");
			turSEVendor.setPlugin("");
			turSEVendor.setTitle("ElasticSearch");
			turSEVendor.setWebsite("https://www.elastic.co/products/elasticsearch");
			turSEVendorRepository.save(turSEVendor);
			
		}
	}
}
