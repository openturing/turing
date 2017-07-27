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

			TurNLPVendor turNLPVendor = turNLPVendorService.get("OPENNLP");
			if (turNLPVendor != null) {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("OpenNLP");
				turNLPInstance.setDescription("OpenNLP Production");
				turNLPInstance.setTurNLPVendor(turNLPVendor);
				turNLPInstance.setHost("");
				turNLPInstance.setPort(0);
				turNLPInstance.setLanguage("pt-BR");
				turNLPInstance.setSelected(1);
				turNLPInstance.setEnabled(1);
				turNLPInstanceService.save(turNLPInstance);
			}

			turNLPVendor = turNLPVendorService.get("CORENLP");
			if (turNLPVendor != null) {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("CoreNLP");
				turNLPInstance.setDescription("CoreNLP Production");
				turNLPInstance.setTurNLPVendor(turNLPVendor);
				turNLPInstance.setHost("localhost");
				turNLPInstance.setPort(9000);
				turNLPInstance.setLanguage("en-US");
				turNLPInstance.setSelected(1);
				turNLPInstance.setEnabled(1);
				turNLPInstanceService.save(turNLPInstance);
			}

		}
	}

}
