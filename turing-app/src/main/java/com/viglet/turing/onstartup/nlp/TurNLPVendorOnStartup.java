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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.nlp.TurNLPVendorsConstant;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorRepository;

@Component
@Transactional
public class TurNLPVendorOnStartup {

	private final TurNLPVendorRepository turNLPVendorRepository;

	@Inject
	public TurNLPVendorOnStartup(TurNLPVendorRepository turNLPVendorRepository) {
		this.turNLPVendorRepository = turNLPVendorRepository;
	}

	public void createDefaultRows() {
		
		if (turNLPVendorRepository.findAll().isEmpty()) {
			
			TurNLPVendor turNLPVendor = new TurNLPVendor();
			turNLPVendor.setId(TurNLPVendorsConstant.CORENLP);
			turNLPVendor.setDescription("Stanford CoreNLP");
			turNLPVendor.setPlugin("com.viglet.turing.plugins.nlp.corenlp.TurCoreNLPConnector");
			turNLPVendor.setTitle("Stanford CoreNLP");
			turNLPVendor.setWebsite("https://stanfordnlp.github.io/CoreNLP");
			turNLPVendorRepository.save(turNLPVendor);

			turNLPVendor = new TurNLPVendor();
			turNLPVendor.setId(TurNLPVendorsConstant.OTCA);
			turNLPVendor.setDescription("OpenText OTCA");
			turNLPVendor.setPlugin("com.viglet.turing.plugins.nlp.otca.TurTMEConnector");
			turNLPVendor.setTitle("OpenText OTCA");
			turNLPVendor.setWebsite("https://opentext.com/what-we-do/products/discovery");
			turNLPVendorRepository.save(turNLPVendor);

			turNLPVendor = new TurNLPVendor();
			turNLPVendor.setId(TurNLPVendorsConstant.OPENNLP);
			turNLPVendor.setDescription("Apache OpenNLP");
			turNLPVendor.setPlugin("com.viglet.turing.plugins.nlp.opennlp.TurOpenNLPConnector");
			turNLPVendor.setTitle("Apache OpenNLP");
			turNLPVendor.setWebsite("https://opennlp.apache.org");
			turNLPVendorRepository.save(turNLPVendor);
			
			turNLPVendor = new TurNLPVendor();
			turNLPVendor.setId(TurNLPVendorsConstant.SPACY);
			turNLPVendor.setTitle("SpaCy");
			turNLPVendor.setPlugin("com.viglet.turing.plugins.nlp.spacy.TurSpaCyConnector");
			turNLPVendor.setDescription("SpaCy");
			turNLPVendor.setWebsite("https://spacy.io");
			turNLPVendorRepository.save(turNLPVendor);
			
			turNLPVendor = new TurNLPVendor();
			turNLPVendor.setId(TurNLPVendorsConstant.POLYGLOT);
			turNLPVendor.setTitle("Polyglot");
			turNLPVendor.setPlugin("com.viglet.turing.plugins.nlp.polyglot.TurPolyglotConnector");
			turNLPVendor.setDescription("Polyglot");
			turNLPVendor.setWebsite("https://polyglot-nlp.com");
			turNLPVendorRepository.save(turNLPVendor);
			
			turNLPVendor = new TurNLPVendor();
			turNLPVendor.setId(TurNLPVendorsConstant.GCP);
			turNLPVendor.setTitle("Google Cloud Platform - NLP");
			turNLPVendor.setPlugin("com.viglet.turing.plugins.nlp.gcp.TurNLPGCPConnector");
			turNLPVendor.setDescription("Google Cloud Platform - NLP");
			turNLPVendor.setWebsite("https://cloud.google.com/natural-language");
			turNLPVendorRepository.save(turNLPVendor);
		}
	}

}
