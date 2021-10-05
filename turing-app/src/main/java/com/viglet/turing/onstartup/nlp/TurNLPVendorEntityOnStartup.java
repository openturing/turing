/*
 * Copyright (C) 2016-2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.onstartup.nlp;

import java.io.File;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.nlp.TurNLPVendorsConstant;
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
	@Autowired
	private TurNLPEntityRepository turNLPEntityRepository;
	@Autowired
	private TurNLPVendorRepository turNLPVendorRepository;
	@Autowired
	private TurNLPVendorEntityRepository turNLPVendorEntityRepository;

	public void createDefaultRows() {
		if (turNLPVendorEntityRepository.findAll().isEmpty()) {
			turNLPVendorRepository.findById(TurNLPVendorsConstant.CORENLP).ifPresent(turNLPVendor -> {
				this.addNLPVendor(turNLPVendor, "PN", "PERSON", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "GL", "LOCATION", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "ON", "ORGANIZATION", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "DURATION", "DURATION", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "DATE", "DATE", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "MISC", "MISC", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "ORDINAL", "ORDINAL", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "TIME", "TIME", TurLocaleRepository.EN_US);
			});

			turNLPVendorRepository.findById(TurNLPVendorsConstant.OPENNLP).ifPresent(turNLPVendor -> {
				TurOpenNLPModelsOnStartup.downloadModels();
				File userDir = new File(System.getProperty("user.dir"));
				if (false) {
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
			});

			turNLPVendorRepository.findById(TurNLPVendorsConstant.OTCA).ifPresent(turNLPVendor -> {
				this.addNLPVendor(turNLPVendor, "PN", "PN", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "GL", "GL", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "ON", "ON", TurLocaleRepository.EN_US);

				this.addNLPVendor(turNLPVendor, "PN", "PN", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "GL", "GL", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "ON", "ON", TurLocaleRepository.PT_BR);
			});

			turNLPVendorRepository.findById(TurNLPVendorsConstant.POLYGLOT).ifPresent(turNLPVendor -> {
				this.addNLPVendor(turNLPVendor, "PN", "PERSON", TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, "GL", "LOC", TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, "ON", "ORG", TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, "FIRST_NAME", "FIRST_NAME", TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, "LAST_NAME", "LAST_NAME", TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, "EMAIL", "EMAIL", TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, "NIE", "NIE", TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, "CIF", "CIF", TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, "DNI", "DNI", TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, "PASSPORT", "PASSPORT", TurLocaleRepository.CA);
			});

			turNLPVendorRepository.findById(TurNLPVendorsConstant.SPACY).ifPresent(turNLPVendor -> {
				this.addNLPVendor(turNLPVendor, "PN", "PERSON", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "GL", "LOC", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "ON", "ORG", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "DURATION", "DURATION", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "DATE", "DATE", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "MISC", "MISC", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "ORDINAL", "ORDINAL", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "TIME", "TIME", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "PERCENTAGE", "PERCENT", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "MONEY", "MONEY", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "NORP", "NORP", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "FAC", "FAC", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "GPE", "GPE", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "PRODUCT", "PRODUCT", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "EVENT", "EVENT", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "WORK_OF_ART", "WORK_OF_ART", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "LAW", "LAW", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "LANGUAGE", "LANGUAGE", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "QUANTITY", "QUANTITY", TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, "CARDINAL", "CARDINAL", TurLocaleRepository.EN_US);

				this.addNLPVendor(turNLPVendor, "PN", "PER", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "GL", "LOC", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "ON", "ORG", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "DURATION", "DURATION", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "DATE", "DATE", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "MISC", "MISC", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "ORDINAL", "ORDINAL", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "TIME", "TIME", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "PERCENTAGE", "PERCENT", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "MONEY", "MONEY", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "NORP", "NORP", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "FAC", "FAC", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "GPE", "GPE", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "PRODUCT", "PRODUCT", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "EVENT", "EVENT", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "WORK_OF_ART", "WORK_OF_ART", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "LAW", "LAW", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "LANGUAGE", "LANGUAGE", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "QUANTITY", "QUANTITY", TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, "CARDINAL", "CARDINAL", TurLocaleRepository.PT_BR);
			});

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
