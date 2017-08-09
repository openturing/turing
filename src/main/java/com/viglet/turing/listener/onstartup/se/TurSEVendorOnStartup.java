package com.viglet.turing.listener.onstartup.se;

import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.service.se.TurSEVendorService;

public class TurSEVendorOnStartup {

	public static void createDefaultRows() {


		TurSEVendorService turSEVendorService = new TurSEVendorService();
		if (turSEVendorService.listAll().isEmpty()) {
			
			TurSEVendor turSEVendor = new TurSEVendor();
			turSEVendor.setId("SOLR");
			turSEVendor.setDescription("Apache Solr");
			turSEVendor.setPlugin("");
			turSEVendor.setTitle("Apache Solr");
			turSEVendor.setWebsite("http://lucene.apache.org/solr");
			turSEVendorService.save(turSEVendor);
			
			turSEVendor = new TurSEVendor();
			turSEVendor.setId("SPHINX");
			turSEVendor.setDescription("Sphinx");
			turSEVendor.setPlugin("");
			turSEVendor.setTitle("Sphinx");
			turSEVendor.setWebsite("http://sphinxsearch.com");
			turSEVendorService.save(turSEVendor);
			
			turSEVendor = new TurSEVendor();
			turSEVendor.setId("ELASTIC");
			turSEVendor.setDescription("ElasticSearch");
			turSEVendor.setPlugin("");
			turSEVendor.setTitle("ElasticSearch");
			turSEVendor.setWebsite("https://www.elastic.co/products/elasticsearch");
			turSEVendorService.save(turSEVendor);
			
		}
	}
}
