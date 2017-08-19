package com.viglet.turing.listener.onstartup.ml;

import com.viglet.turing.persistence.model.ml.TurMLInstance;
import com.viglet.turing.persistence.model.ml.TurMLVendor;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.service.ml.TurMLInstanceService;
import com.viglet.turing.persistence.service.ml.TurMLVendorService;
import com.viglet.turing.persistence.service.system.TurConfigVarService;

public class TurMLInstanceOnStartup {
	public static void createDefaultRows() {

		TurMLInstanceService turMLInstanceService = new TurMLInstanceService();
		TurMLVendorService turMLVendorService = new TurMLVendorService();
		TurConfigVarService turConfigVarService = new TurConfigVarService();
		TurConfigVar turConfigVar = new TurConfigVar();
		
		if (turMLInstanceService.listAll().isEmpty()) {

			TurMLVendor turMLVendor = turMLVendorService.get("OPENNLP");
			if (turMLVendor != null) {
				TurMLInstance turMLInstance = new TurMLInstance();
				turMLInstance.setTitle("OpenNLP");
				turMLInstance.setDescription("OpenNLP Production");
				turMLInstance.setTurMLVendor(turMLVendor);
				turMLInstance.setHost("");
				turMLInstance.setPort(0);
				turMLInstance.setLanguage("en");
				turMLInstance.setEnabled(1);
				turMLInstanceService.save(turMLInstance);
				
				turConfigVar.setId("DEFAULT_ML");
				turConfigVar.setPath("/ml");
				turConfigVar.setValue(Integer.toString(turMLInstance.getId()));
				turConfigVarService.save(turConfigVar);
			}
		}

	}
	
	
}
