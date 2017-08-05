package com.viglet.turing.listener.onstartup;

import com.viglet.turing.persistence.model.TurNLPInstance;
import com.viglet.turing.persistence.model.TurNLPVendor;
import com.viglet.turing.persistence.service.TurNLPInstanceService;
import com.viglet.turing.persistence.service.TurNLPVendorService;

public class TurNLPInstanceOnStartup {
	public static void createDefaultRows() {

		TurNLPInstanceService turNLPInstanceService = new TurNLPInstanceService();
		TurNLPVendorService turNLPVendorService = new TurNLPVendorService();

		if (turNLPInstanceService.listAll().isEmpty()) {

			TurNLPVendor turNLPVendorOpenNLP = turNLPVendorService.get("OPENNLP");
			if (turNLPVendorOpenNLP != null) {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("OpenNLP");
				turNLPInstance.setDescription("OpenNLP Production");
				turNLPInstance.setTurNLPVendor(turNLPVendorOpenNLP);
				turNLPInstance.setHost("");
				turNLPInstance.setPort(0);
				turNLPInstance.setLanguage("pt-BR");
				turNLPInstance.setSelected(1);
				turNLPInstance.setEnabled(1);
				turNLPInstanceService.save(turNLPInstance);
			}

			TurNLPVendor turNLPVendorCoreNLP = turNLPVendorService.get("CORENLP");
			if (turNLPVendorCoreNLP != null) {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("CoreNLP");
				turNLPInstance.setDescription("CoreNLP Production");
				turNLPInstance.setTurNLPVendor(turNLPVendorCoreNLP);
				turNLPInstance.setHost("localhost");
				turNLPInstance.setPort(9001);
				turNLPInstance.setLanguage("en-US");
				turNLPInstance.setSelected(0);
				turNLPInstance.setEnabled(1);
				turNLPInstanceService.save(turNLPInstance);
			}

		}

	}
	
	
}
