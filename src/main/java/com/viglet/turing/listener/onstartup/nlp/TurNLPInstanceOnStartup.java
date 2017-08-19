package com.viglet.turing.listener.onstartup.nlp;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.service.nlp.TurNLPInstanceService;
import com.viglet.turing.persistence.service.nlp.TurNLPVendorService;
import com.viglet.turing.persistence.service.system.TurConfigVarService;

public class TurNLPInstanceOnStartup {
	public static void createDefaultRows() {

		TurNLPInstanceService turNLPInstanceService = new TurNLPInstanceService();
		TurNLPVendorService turNLPVendorService = new TurNLPVendorService();
		TurConfigVarService turConfigVarService = new TurConfigVarService();
		TurConfigVar turConfigVar = new TurConfigVar();

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
				turNLPInstance.setEnabled(1);
				turNLPInstanceService.save(turNLPInstance);
				
				turConfigVar.setId("DEFAULT_NLP");
				turConfigVar.setPath("/nlp");
				turConfigVar.setValue(Integer.toString(turNLPInstance.getId()));
				turConfigVarService.save(turConfigVar);
				

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
				turNLPInstance.setEnabled(1);
				turNLPInstanceService.save(turNLPInstance);
			}			

		}

	}
	
	
}
