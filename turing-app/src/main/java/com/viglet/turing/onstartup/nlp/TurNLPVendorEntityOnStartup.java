/*
 * Copyright (C) 2016-2021 the original author or authors. 
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

import com.viglet.turing.nlp.TurNLPEntityConstant;
import com.viglet.turing.nlp.TurNLPVendorsConstant;
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.model.nlp.TurNLPVendorEntity;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorEntityRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorRepository;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;
import com.viglet.turing.plugins.nlp.gcp.response.TurNLPGCPEntityTypeResponse;

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
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERSON_INTERNAL, TurNLPEntityConstant.PERSON_CORENLP,
						TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LOCATION_INTERNAL, TurNLPEntityConstant.LOCATION_CORENLP,
						TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ORGANIZATION_INTERNAL,
						TurNLPEntityConstant.ORGANIZATION_CORENLP, TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.DURATION_INTERNAL, TurNLPEntityConstant.DURATION_CORENLP,
						TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.DATE_INTERNAL, TurNLPEntityConstant.DATE_CORENLP,
						TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.MISC_INTERNAL, TurNLPEntityConstant.MISC_CORENLP,
						TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ORDINAL_INTERNAL, TurNLPEntityConstant.ORDINAL_CORENLP,
						TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.TIME_INTERNAL, TurNLPEntityConstant.TIME_CORENLP,
						TurLocaleRepository.EN_US);
			});

			turNLPVendorRepository.findById(TurNLPVendorsConstant.OPENNLP).ifPresent(turNLPVendor -> {
				TurOpenNLPModelsOnStartup.downloadModels();
				File userDir = new File(System.getProperty("user.dir"));

				if (userDir.exists() && userDir.isDirectory()) {

					this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERSON_INTERNAL,
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-person.bin"),
							TurLocaleRepository.EN_US);
					this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LOCATION_INTERNAL,
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-location.bin"),
							TurLocaleRepository.EN_US);
					this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ORGANIZATION_INTERNAL,
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-organization.bin"),
							TurLocaleRepository.EN_US);
					this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.MONEY_INTERNAL,
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-money.bin"),
							TurLocaleRepository.EN_US);
					this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.DATE_INTERNAL,
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-date.bin"),
							TurLocaleRepository.EN_US);
					this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERCENTAGE_INTERNAL,
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-percentage.bin"),
							TurLocaleRepository.EN_US);
					this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.TIME_INTERNAL,
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-time.bin"),
							TurLocaleRepository.EN_US);

					this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERSON_INTERNAL,
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-person.bin"),
							TurLocaleRepository.PT_BR);
					this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LOCATION_INTERNAL,
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-location.bin"),
							TurLocaleRepository.PT_BR);
					this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ORGANIZATION_INTERNAL,
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-organization.bin"),
							TurLocaleRepository.PT_BR);
					this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.MONEY_INTERNAL,
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-money.bin"),
							TurLocaleRepository.PT_BR);
					this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.DATE_INTERNAL,
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-date.bin"),
							TurLocaleRepository.PT_BR);
					this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERCENTAGE_INTERNAL,
							userDir.getAbsolutePath().concat("/models/opennlp/en/en-ner-percentage.bin"),
							TurLocaleRepository.PT_BR);
					this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.TIME_INTERNAL,
							userDir.getAbsolutePath()
									.concat("/models/opennlp/en/en-ner-time.bin"),
							TurLocaleRepository.PT_BR);

				}
			});

			turNLPVendorRepository.findById(TurNLPVendorsConstant.OTCA).ifPresent(turNLPVendor -> {
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERSON_INTERNAL, TurNLPEntityConstant.PERSON_OTCA, TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LOCATION_INTERNAL, TurNLPEntityConstant.LOCATION_OTCA, TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ORGANIZATION_INTERNAL, TurNLPEntityConstant.ORGANIZATION_OTCA, TurLocaleRepository.EN_US);

				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERSON_INTERNAL, TurNLPEntityConstant.PERSON_OTCA, TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LOCATION_INTERNAL, TurNLPEntityConstant.LOCATION_OTCA, TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ORGANIZATION_INTERNAL, TurNLPEntityConstant.ORGANIZATION_OTCA, TurLocaleRepository.PT_BR);
			});

			turNLPVendorRepository.findById(TurNLPVendorsConstant.POLYGLOT).ifPresent(turNLPVendor -> {
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERSON_INTERNAL, TurNLPEntityConstant.PERSON_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LOCATION_INTERNAL, TurNLPEntityConstant.LOCATION_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ORGANIZATION_INTERNAL, TurNLPEntityConstant.ORGANIZATION_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.FIRST_NAME_INTERNAL, TurNLPEntityConstant.FIRST_NAME_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LAST_NAME_INTERNAL, TurNLPEntityConstant.LAST_NAME_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.EMAIL_INTERNAL, TurNLPEntityConstant.EMAIL_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.NIE_INTERNAL, TurNLPEntityConstant.NIE_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.CIF_INTERNAL, TurNLPEntityConstant.CIF_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.DNI_INTERNAL, TurNLPEntityConstant.DNI_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PASSPORT_INTERNAL, TurNLPEntityConstant.PASSPORT_POLYGLOT, TurLocaleRepository.CA);
			});

			turNLPVendorRepository.findById(TurNLPVendorsConstant.SPACY).ifPresent(turNLPVendor -> {
				spacyEntities(turNLPVendor, TurLocaleRepository.EN_US);
				spacyEntities(turNLPVendor, TurLocaleRepository.PT_BR);
			});
			
			turNLPVendorRepository.findById(TurNLPVendorsConstant.GCP).ifPresent(turNLPVendor -> {
				gcpEntities(turNLPVendor, TurLocaleRepository.EN_US);
				gcpEntities(turNLPVendor, TurLocaleRepository.PT_BR);
			});

		}
	}

	private void spacyEntities(TurNLPVendor turNLPVendor, String language) {
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERSON_INTERNAL, TurNLPEntityConstant.PERSON_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LOCATION_INTERNAL, TurNLPEntityConstant.LOCATION_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ORGANIZATION_INTERNAL, TurNLPEntityConstant.ORGANIZATION_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.DURATION_INTERNAL, TurNLPEntityConstant.DURATION_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.DATE_INTERNAL, TurNLPEntityConstant.DATE_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.MISC_INTERNAL, TurNLPEntityConstant.MISC_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ORDINAL_INTERNAL, TurNLPEntityConstant.ORDINAL_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.TIME_INTERNAL, TurNLPEntityConstant.TIME_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERCENTAGE_INTERNAL,TurNLPEntityConstant.PERCENTAGE_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.MONEY_INTERNAL, TurNLPEntityConstant.MONEY_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.NORP_INTERNAL, TurNLPEntityConstant.NORP_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.FAC_INTERNAL, TurNLPEntityConstant.FAC_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.GPE_INTERNAL,TurNLPEntityConstant.GPE_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PRODUCT_INTERNAL, TurNLPEntityConstant.PRODUCT_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.EVENT_INTERNAL, TurNLPEntityConstant.EVENT_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.WORK_OF_ART_INTERNAL, TurNLPEntityConstant.WORK_OF_ART_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LAW_INTERNAL, TurNLPEntityConstant.LAW_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LANGUAGE_INTERNAL, TurNLPEntityConstant.LANGUAGE_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.QUANTITY_INTERNAL, TurNLPEntityConstant.QUANTITY_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.CARDINAL_INTERNAL, TurNLPEntityConstant.CARDINAL_SPACY, language);
	}
	
	private void gcpEntities(TurNLPVendor turNLPVendor, String language) {
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERSON_INTERNAL, TurNLPGCPEntityTypeResponse.PERSON.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LOCATION_INTERNAL, TurNLPGCPEntityTypeResponse.LOCATION.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ORGANIZATION_INTERNAL, TurNLPGCPEntityTypeResponse.ORGANIZATION.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.DATE_INTERNAL, TurNLPGCPEntityTypeResponse.DATE.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.MISC_INTERNAL, TurNLPGCPEntityTypeResponse.OTHER.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.MONEY_INTERNAL, TurNLPGCPEntityTypeResponse.PRICE.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.EVENT_INTERNAL, TurNLPGCPEntityTypeResponse.EVENT.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.WORK_OF_ART_INTERNAL, TurNLPGCPEntityTypeResponse.WORK_OF_ART.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.CONSUMER_GOOD, TurNLPGCPEntityTypeResponse.CONSUMER_GOOD.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PHONE_NUMBER, TurNLPGCPEntityTypeResponse.PHONE_NUMBER.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ADDRESS, TurNLPGCPEntityTypeResponse.ADDRESS.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.CARDINAL_INTERNAL, TurNLPGCPEntityTypeResponse.NUMBER.toString(), language);
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
