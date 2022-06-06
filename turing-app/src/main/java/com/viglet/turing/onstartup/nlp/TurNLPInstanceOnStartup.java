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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.nlp.TurNLPVendorsConstant;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;

@Component
@Transactional
public class TurNLPInstanceOnStartup {
	private static final String LOCALHOST = "http://localhost";
	@Autowired
	private TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	private TurNLPVendorRepository turNLPVendorRepository;
	@Autowired
	private TurConfigVarRepository turConfigVarRepository;
	
	public void createDefaultRows() {

		TurConfigVar turConfigVar = new TurConfigVar();

		if (turNLPInstanceRepository.findAll().isEmpty()) {

			turNLPVendorRepository.findById(TurNLPVendorsConstant.OPENNLP).ifPresent(turNLPVendorOpenNLP -> {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("OpenNLP English");
				turNLPInstance.setDescription("OpenNLP Production - English");
				turNLPInstance.setTurNLPVendor(turNLPVendorOpenNLP);
				turNLPInstance.setEndpointURL(LOCALHOST);
				turNLPInstance.setLanguage(TurLocaleRepository.EN_US);
				turNLPInstance.setEnabled(1);

				turConfigVar.setId("DEFAULT_NLP");
				turConfigVar.setPath("/nlp");
				turConfigVar.setValue(turNLPInstance.getId());
				turConfigVarRepository.save(turConfigVar);

				turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("OpenNLP Portuguese");
				turNLPInstance.setDescription("OpenNLP Production - Portuguese");
				turNLPInstance.setTurNLPVendor(turNLPVendorOpenNLP);
				turNLPInstance.setEndpointURL(LOCALHOST);
				turNLPInstance.setLanguage(TurLocaleRepository.PT_BR);
				turNLPInstance.setEnabled(1);
			});

			turNLPVendorRepository.findById(TurNLPVendorsConstant.CORENLP).ifPresent(turNLPVendorCoreNLP -> {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("CoreNLP");
				turNLPInstance.setDescription("CoreNLP Production - English");
				turNLPInstance.setTurNLPVendor(turNLPVendorCoreNLP);
				turNLPInstance.setEndpointURL(LOCALHOST.concat(":9001"));
				turNLPInstance.setLanguage(TurLocaleRepository.EN_US);
				turNLPInstance.setEnabled(1);

			});

			turNLPVendorRepository.findById(TurNLPVendorsConstant.SPACY).ifPresent(turNLPVendorSpaCy -> {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("SpaCy");
				turNLPInstance.setDescription("SpaCy Production - English");
				turNLPInstance.setTurNLPVendor(turNLPVendorSpaCy);
				turNLPInstance.setEndpointURL(LOCALHOST.concat(":2800"));
				turNLPInstance.setLanguage(TurLocaleRepository.EN_US);
				turNLPInstance.setEnabled(1);
			});

			turNLPVendorRepository.findById(TurNLPVendorsConstant.POLYGLOT).ifPresent(turNLPVendorPolyglot -> {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("Polyglot");
				turNLPInstance.setDescription("Polyglot Production - Catalan");
				turNLPInstance.setTurNLPVendor(turNLPVendorPolyglot);
				turNLPInstance.setEndpointURL(LOCALHOST.concat(":2810"));
				turNLPInstance.setLanguage(TurLocaleRepository.CA);
				turNLPInstance.setEnabled(1);
			});
			
			turNLPVendorRepository.findById(TurNLPVendorsConstant.GCP).ifPresent(turNLPVendorGCP -> {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("Google Cloud Platform NLP");
				turNLPInstance.setDescription("Google Cloud Platform NLP - Portuguese");
				turNLPInstance.setTurNLPVendor(turNLPVendorGCP);
				turNLPInstance.setEndpointURL("https://language.googleapis.com/v1/documents:analyzeEntities");
				turNLPInstance.setLanguage(TurLocaleRepository.PT_BR);
				turNLPInstance.setEnabled(1);
			});
		}

	}
}
