package com.viglet.turing.onstartup.nlp;

import java.io.File;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.model.nlp.TurNLPVendorEntity;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorEntityRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorRepository;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;

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
				this.addNLPVendor(turNLPVendor, "PN", "PERSON", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "GL", "LOCATION", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "ON", "ORGANIZATION", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "DURATION", "DURATION", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "DATE", "DATE", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "MISC", "MISC", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "ORDINAL", "ORDINAL", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "TIME", "TIME", TurLocaleRepository.EN_US);
			}

			turNLPVendor = turNLPVendorRepository.findOne(OPENNLP);

			if (turNLPVendor != null) {
				TurOpenNLPModelsOnStartup.downloadModels();
				File userDir = new File(System.getProperty("user.dir"));
				if (userDir.exists() && userDir.isDirectory()) {

					this.addNLPVendor(turNLPVendor, "PN",
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-person.bin"),
							TurLocaleRepository.EN_US);
					this.addNLPVendor(turNLPVendor, "GL",
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-location.bin"),
							TurLocaleRepository.EN_US);
					this.addNLPVendor(turNLPVendor, "ON",
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-organization.bin"),
							TurLocaleRepository.EN_US);
					this.addNLPVendor(turNLPVendor, "MONEY",
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-money.bin"),
							TurLocaleRepository.EN_US);
					this.addNLPVendor(turNLPVendor, "DATE",
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-date.bin"),
							TurLocaleRepository.EN_US);
					this.addNLPVendor(turNLPVendor, "PERCENTAGE",
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-percentage.bin"),
							TurLocaleRepository.EN_US);
					this.addNLPVendor(turNLPVendor, "TIME",
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-time.bin"),
							TurLocaleRepository.EN_US);

					this.addNLPVendor(turNLPVendor, "PN",
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-person.bin"),
							TurLocaleRepository.PT_BR);
					this.addNLPVendor(turNLPVendor, "GL",
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-location.bin"),
							TurLocaleRepository.PT_BR);
					this.addNLPVendor(turNLPVendor, "ON",
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-organization.bin"),
							TurLocaleRepository.PT_BR);
					this.addNLPVendor(turNLPVendor, "MONEY",
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-money.bin"),
							TurLocaleRepository.PT_BR);
					this.addNLPVendor(turNLPVendor, "DATE",
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-date.bin"),
							TurLocaleRepository.PT_BR);
					this.addNLPVendor(turNLPVendor, "PERCENTAGE",
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-percentage.bin"),
							TurLocaleRepository.PT_BR);
					this.addNLPVendor(turNLPVendor, "TIME",
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-time.bin"),
							TurLocaleRepository.PT_BR);
				}
			}

			turNLPVendor = turNLPVendorRepository.findOne(OTCA);

			if (turNLPVendor != null) {
				this.addNLPVendor(turNLPVendor, "PN", "PN", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "GL", "GL", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "ON", "ON", TurLocaleRepository.EN_US);

				this.addNLPVendor(turNLPVendor, "PN", "PN", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "GL", "GL", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "ON", "ON", TurLocaleRepository.PT_BR);
			}

		}
	}

	public void addNLPVendor(TurNLPVendor turNLPVendor, String internalName, String name, String language) {
		TurNLPEntity turNLPEntity = turNLPEntityRepository.findByInternalName(internalName);
		if (turNLPEntity != null) {
			TurNLPVendorEntity turNLPVendorEntity = new TurNLPVendorEntity();
			turNLPVendorEntity.setName(name);
			turNLPVendorEntity.setTurNLPEntity(turNLPEntity);
			turNLPVendorEntity.setTurNLPVendor(turNLPVendor);
			turNLPVendorEntity.setLanguage(language);
			turNLPVendorEntityRepository.save(turNLPVendorEntity);
		}
	}
}
