package com.viglet.turing.listener.onstartup.nlp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.model.nlp.TurNLPVendorEntity;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorEntityRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorRepository;

@Component
@Transactional
public class TurNLPVendorEntityOnStartup {
	private static final String CORENLP = "CORENLP";
	private static final String OPENNLP = "OPENNLP";
	private static final String OTCA = "OTCA";

	@Autowired
	private TurNLPEntityRepository turNLPEntityRepository;
	@Autowired
	private TurNLPVendorRepository turNLPVendorRepository;
	@Autowired
	private TurNLPVendorEntityRepository turNLPVendorEntityRepository;

	public void createDefaultRows() {

		if (turNLPVendorEntityRepository.findAll().isEmpty()) {
			TurNLPVendor turNLPVendor = turNLPVendorRepository.findOne(CORENLP);
			if (turNLPVendor != null) {
				this.addNLPVendor(turNLPVendor, "PN", "PERSON");
				this.addNLPVendor(turNLPVendor, "GL", "LOCATION");
				this.addNLPVendor(turNLPVendor, "ON", "ORGANIZATION");
				this.addNLPVendor(turNLPVendor, "DURATION", "DURATION");
				this.addNLPVendor(turNLPVendor, "DATE", "DATE");
				this.addNLPVendor(turNLPVendor, "MISC", "MISC");
				this.addNLPVendor(turNLPVendor, "ORDINAL", "ORDINAL");
				this.addNLPVendor(turNLPVendor, "TIME", "TIME");
			}
			
			turNLPVendor = turNLPVendorRepository.findOne(OPENNLP);

			if (turNLPVendor != null) {
				TurOpenNLPModelsOnStartup.downloadModels();
				this.addNLPVendor(turNLPVendor, "PN", "/models/opennlp/en/en-ner-person.bin");
				this.addNLPVendor(turNLPVendor, "GL", "/models/opennlp/en/en-ner-location.bin");
				this.addNLPVendor(turNLPVendor, "ON", "/models/opennlp/en/en-ner-organization.bin");
				this.addNLPVendor(turNLPVendor, "MONEY", "/models/opennlp/en/en-ner-money.bin");
				this.addNLPVendor(turNLPVendor, "DATE", "/models/opennlp/en/en-ner-date.bin");
				this.addNLPVendor(turNLPVendor, "PERCENTAGE", "/models/opennlp/en/en-ner-percentage.bin");
				this.addNLPVendor(turNLPVendor, "TIME", "/models/opennlp/en/en-ner-time.bin");
			}

			turNLPVendor = turNLPVendorRepository.findOne(OTCA);

			if (turNLPVendor != null) {
				this.addNLPVendor(turNLPVendor, "PN", "PN");
				this.addNLPVendor(turNLPVendor, "GL", "GL");
				this.addNLPVendor(turNLPVendor, "ON", "ON");
			}

		}
	}

	public void addNLPVendor(TurNLPVendor turNLPVendor, String internalName, String name) {
		TurNLPEntity turNLPEntity = turNLPEntityRepository.findByInternalName(internalName);
		if (turNLPEntity != null) {
			TurNLPVendorEntity turNLPVendorEntity = new TurNLPVendorEntity();
			turNLPVendorEntity.setName(name);
			turNLPVendorEntity.setTurNLPEntity(turNLPEntity);
			turNLPVendorEntity.setTurNLPVendor(turNLPVendor);
			turNLPVendorEntityRepository.save(turNLPVendorEntity);
		}
	}
}
