package com.viglet.turing.listener.onstartup.ml;

import com.viglet.turing.persistence.model.ml.TurMLVendor;
import com.viglet.turing.persistence.service.ml.TurMLVendorService;

public class TurMLVendorOnStartup {

	public static void createDefaultRows() {


		TurMLVendorService turMLVendorService = new TurMLVendorService();
		if (turMLVendorService.listAll().isEmpty()) {
			
			TurMLVendor turMLVendor = new TurMLVendor();
			turMLVendor.setId("OPENNLP");
			turMLVendor.setDescription("Apache OpenNLP");
			turMLVendor.setPlugin("");
			turMLVendor.setTitle("Apache OpenNLP");
			turMLVendor.setWebsite("https://opennlp.apache.org");
			turMLVendorService.save(turMLVendor);
			
			
		}
	}

}
