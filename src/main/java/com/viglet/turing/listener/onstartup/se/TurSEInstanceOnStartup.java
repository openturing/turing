package com.viglet.turing.listener.onstartup.se;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.service.se.TurSEInstanceService;
import com.viglet.turing.persistence.service.se.TurSEVendorService;
import com.viglet.turing.persistence.service.system.TurConfigVarService;

public class TurSEInstanceOnStartup {
	public static void createDefaultRows() {

		TurSEInstanceService turSEInstanceService = new TurSEInstanceService();
		TurSEVendorService turSEVendorService = new TurSEVendorService();
		TurConfigVarService turConfigVarService = new TurConfigVarService();
		TurConfigVar turConfigVar = new TurConfigVar();
		
		if (turSEInstanceService.listAll().isEmpty()) {

			TurSEVendor turSEVendor = turSEVendorService.get("SOLR");
			if (turSEVendor != null) {
				TurSEInstance turSEInstance = new TurSEInstance();
				turSEInstance.setTitle("Apache Solr");
				turSEInstance.setDescription("Solr Production");
				turSEInstance.setTurSEVendor(turSEVendor);
				turSEInstance.setHost("localhost");
				turSEInstance.setPort(8983);
				turSEInstance.setLanguage("pt-BR");
				turSEInstance.setEnabled(1);
				turSEInstanceService.save(turSEInstance);
				
				turConfigVar.setId("DEFAULT_SE");
				turConfigVar.setPath("/se");
				turConfigVar.setValue(Integer.toString(turSEInstance.getId()));
				turConfigVarService.save(turConfigVar);
			}
		}

	}
	
	
}
