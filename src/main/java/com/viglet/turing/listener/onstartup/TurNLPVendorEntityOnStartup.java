package com.viglet.turing.listener.onstartup;

import com.viglet.turing.persistence.model.TurEntity;
import com.viglet.turing.persistence.model.TurNLPVendor;
import com.viglet.turing.persistence.model.TurNLPVendorEntity;
import com.viglet.turing.persistence.service.TurEntityService;
import com.viglet.turing.persistence.service.TurNLPVendorEntityService;
import com.viglet.turing.persistence.service.TurNLPVendorService;

public class TurNLPVendorEntityOnStartup {

	TurEntityService turNLPEntityService = new TurEntityService();
	TurNLPVendorEntityService turNLPVendorEntityService = new TurNLPVendorEntityService();

	public static void createDefaultRows() {
		TurNLPVendorService turNLPVendorService = new TurNLPVendorService();
		TurNLPVendorEntityService turNLPVendorEntityService = new TurNLPVendorEntityService();
		TurNLPVendorEntityOnStartup turNLPVendorEntityOnStartup = new TurNLPVendorEntityOnStartup();

		if (turNLPVendorEntityService.listAll().isEmpty()) {
			TurNLPVendor turNLPVendor = turNLPVendorService.get("CORENLP");

			if (turNLPVendor != null) {
				turNLPVendorEntityOnStartup.addNLPVendor(turNLPVendor, "PN", "PERSON");
				turNLPVendorEntityOnStartup.addNLPVendor(turNLPVendor, "GL", "LOCATION");
				turNLPVendorEntityOnStartup.addNLPVendor(turNLPVendor, "ON", "ORGANIZATION");
				turNLPVendorEntityOnStartup.addNLPVendor(turNLPVendor, "DURATION", "DURATION");
				turNLPVendorEntityOnStartup.addNLPVendor(turNLPVendor, "DATE", "DATE");
				turNLPVendorEntityOnStartup.addNLPVendor(turNLPVendor, "MISC", "MISC");
				turNLPVendorEntityOnStartup.addNLPVendor(turNLPVendor, "ORDINAL", "ORDINAL");
				turNLPVendorEntityOnStartup.addNLPVendor(turNLPVendor, "TIME", "TIME");
			}

			turNLPVendor = turNLPVendorService.get("OPENNLP");

			if (turNLPVendor != null) {
				turNLPVendorEntityOnStartup.addNLPVendor(turNLPVendor, "PN", "/models/opennlp/en/en-ner-person.bin");
				turNLPVendorEntityOnStartup.addNLPVendor(turNLPVendor, "GL", "/models/opennlp/en/en-ner-location.bin");
				turNLPVendorEntityOnStartup.addNLPVendor(turNLPVendor, "ON",
						"/models/opennlp/en/en-ner-organization.bin");
				turNLPVendorEntityOnStartup.addNLPVendor(turNLPVendor, "MONEY", "/models/opennlp/en/en-ner-money.bin");
				turNLPVendorEntityOnStartup.addNLPVendor(turNLPVendor, "DATE", "/models/opennlp/en/en-ner-date.bin");
				turNLPVendorEntityOnStartup.addNLPVendor(turNLPVendor, "PERCENTAGE",
						"/models/opennlp/en/en-ner-percentage.bin");
				turNLPVendorEntityOnStartup.addNLPVendor(turNLPVendor, "TIME", "/models/opennlp/en/en-ner-time.bin");
			}

		}
	}

	public void addNLPVendor(TurNLPVendor turNLPVendor, String internalName, String name) {

		TurEntity turEntity = turNLPEntityService.findByInternalName(internalName);
		if (turEntity != null) {
			TurNLPVendorEntity turNLPVendorEntity = new TurNLPVendorEntity();
			turNLPVendorEntity.setName(name);
			turNLPVendorEntity.setTurEntity(turEntity);
			turNLPVendorEntity.setTurNLPVendor(turNLPVendor);
			turNLPVendorEntityService.save(turNLPVendorEntity);
		}
	}
}
