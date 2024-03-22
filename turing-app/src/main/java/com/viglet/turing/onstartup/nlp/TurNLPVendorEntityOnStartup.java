/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.onstartup.nlp;

import com.google.inject.Inject;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;

@Component
@Transactional
public class TurNLPVendorEntityOnStartup {
	private final TurNLPEntityRepository turNLPEntityRepository;
	private final TurNLPVendorRepository turNLPVendorRepository;
	private final TurNLPVendorEntityRepository turNLPVendorEntityRepository;

	@Inject
	public TurNLPVendorEntityOnStartup(TurNLPEntityRepository turNLPEntityRepository,
									   TurNLPVendorRepository turNLPVendorRepository,
									   TurNLPVendorEntityRepository turNLPVendorEntityRepository) {
		this.turNLPEntityRepository = turNLPEntityRepository;
		this.turNLPVendorRepository = turNLPVendorRepository;
		this.turNLPVendorEntityRepository = turNLPVendorEntityRepository;
	}

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
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERSON_INTERNAL,
						TurNLPEntityConstant.PERSON_OTCA, TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LOCATION_INTERNAL,
						TurNLPEntityConstant.LOCATION_OTCA, TurLocaleRepository.EN_US);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ORGANIZATION_INTERNAL,
						TurNLPEntityConstant.ORGANIZATION_OTCA, TurLocaleRepository.EN_US);

				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERSON_INTERNAL,
						TurNLPEntityConstant.PERSON_OTCA, TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LOCATION_INTERNAL,
						TurNLPEntityConstant.LOCATION_OTCA, TurLocaleRepository.PT_BR);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ORGANIZATION_INTERNAL,
						TurNLPEntityConstant.ORGANIZATION_OTCA, TurLocaleRepository.PT_BR);
			});
			turNLPVendorRepository.findById(TurNLPVendorsConstant.POLYGLOT).ifPresent(turNLPVendor -> {
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERSON_INTERNAL,
						TurNLPEntityConstant.PERSON_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LOCATION_INTERNAL,
						TurNLPEntityConstant.LOCATION_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ORGANIZATION_INTERNAL,
						TurNLPEntityConstant.ORGANIZATION_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.FIRST_NAME_INTERNAL,
						TurNLPEntityConstant.FIRST_NAME_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LAST_NAME_INTERNAL,
						TurNLPEntityConstant.LAST_NAME_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.EMAIL_INTERNAL,
						TurNLPEntityConstant.EMAIL_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.NIE_INTERNAL,
						TurNLPEntityConstant.NIE_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.CIF_INTERNAL,
						TurNLPEntityConstant.CIF_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.DNI_INTERNAL,
						TurNLPEntityConstant.DNI_POLYGLOT, TurLocaleRepository.CA);
				this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PASSPORT_INTERNAL,
						TurNLPEntityConstant.PASSPORT_POLYGLOT, TurLocaleRepository.CA);
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
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERSON_INTERNAL,
				TurNLPEntityConstant.PERSON_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LOCATION_INTERNAL,
				TurNLPEntityConstant.LOCATION_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ORGANIZATION_INTERNAL,
				TurNLPEntityConstant.ORGANIZATION_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.DURATION_INTERNAL,
				TurNLPEntityConstant.DURATION_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.DATE_INTERNAL,
				TurNLPEntityConstant.DATE_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.MISC_INTERNAL,
				TurNLPEntityConstant.MISC_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ORDINAL_INTERNAL,
				TurNLPEntityConstant.ORDINAL_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.TIME_INTERNAL,
				TurNLPEntityConstant.TIME_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERCENTAGE_INTERNAL,
				TurNLPEntityConstant.PERCENTAGE_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.MONEY_INTERNAL,
				TurNLPEntityConstant.MONEY_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.NORP_INTERNAL,
				TurNLPEntityConstant.NORP_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.FAC_INTERNAL,
				TurNLPEntityConstant.FAC_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.GPE_INTERNAL,
				TurNLPEntityConstant.GPE_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PRODUCT_INTERNAL,
				TurNLPEntityConstant.PRODUCT_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.EVENT_INTERNAL,
				TurNLPEntityConstant.EVENT_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.WORK_OF_ART_INTERNAL,
				TurNLPEntityConstant.WORK_OF_ART_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LAW_INTERNAL,
				TurNLPEntityConstant.LAW_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LANGUAGE_INTERNAL,
				TurNLPEntityConstant.LANGUAGE_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.QUANTITY_INTERNAL,
				TurNLPEntityConstant.QUANTITY_SPACY, language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.CARDINAL_INTERNAL,
				TurNLPEntityConstant.CARDINAL_SPACY, language);
	}
	
	private void gcpEntities(TurNLPVendor turNLPVendor, String language) {
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PERSON_INTERNAL,
				TurNLPGCPEntityTypeResponse.PERSON.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.LOCATION_INTERNAL,
				TurNLPGCPEntityTypeResponse.LOCATION.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ORGANIZATION_INTERNAL,
				TurNLPGCPEntityTypeResponse.ORGANIZATION.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.DATE_INTERNAL,
				TurNLPGCPEntityTypeResponse.DATE.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.MISC_INTERNAL,
				TurNLPGCPEntityTypeResponse.OTHER.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.MONEY_INTERNAL,
				TurNLPGCPEntityTypeResponse.PRICE.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.EVENT_INTERNAL,
				TurNLPGCPEntityTypeResponse.EVENT.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.WORK_OF_ART_INTERNAL,
				TurNLPGCPEntityTypeResponse.WORK_OF_ART.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.CONSUMER_GOOD,
				TurNLPGCPEntityTypeResponse.CONSUMER_GOOD.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.PHONE_NUMBER,
				TurNLPGCPEntityTypeResponse.PHONE_NUMBER.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.ADDRESS,
				TurNLPGCPEntityTypeResponse.ADDRESS.toString(), language);
		this.addNLPVendor(turNLPVendor, TurNLPEntityConstant.CARDINAL_INTERNAL,
				TurNLPGCPEntityTypeResponse.NUMBER.toString(), language);
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
