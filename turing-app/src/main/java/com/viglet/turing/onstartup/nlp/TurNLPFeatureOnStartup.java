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

import com.viglet.turing.persistence.model.nlp.TurNLPFeature;
import com.viglet.turing.persistence.repository.nlp.TurNLPFeatureRepository;

@Component
@Transactional
public class TurNLPFeatureOnStartup {

	private final TurNLPFeatureRepository turNLPFeatureRepository;

	@Inject
	public TurNLPFeatureOnStartup(TurNLPFeatureRepository turNLPFeatureRepository) {
		this.turNLPFeatureRepository = turNLPFeatureRepository;
	}

	public void createDefaultRows() {

		if (turNLPFeatureRepository.findAll().isEmpty()) {
			saveNLPFeature("Tokenize / Segment");
			saveNLPFeature("Sentence Split");
			saveNLPFeature("Part of Speech");
			saveNLPFeature("Lemma");
			saveNLPFeature("Named Entities");
			saveNLPFeature("Constituency Parsing");
			saveNLPFeature("Dependency Parsing");
			saveNLPFeature("Sentiment Analysis");
			saveNLPFeature("Mention Detection");
			saveNLPFeature("Conference");
			saveNLPFeature("Open IE");
		}
	}

	private void saveNLPFeature(String title) {
		TurNLPFeature turNLPFeature = new TurNLPFeature();
		turNLPFeature.setTitle(title);
		turNLPFeature.setDescription(title);
		turNLPFeatureRepository.save(turNLPFeature);
	}

}
