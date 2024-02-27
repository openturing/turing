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

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;

@Component
@Transactional
public class TurNLPEntityOnStartup {
	private final TurNLPEntityRepository turNLPEntityRepository;

	@Inject
	public TurNLPEntityOnStartup(TurNLPEntityRepository turNLPEntityRepository) {
		this.turNLPEntityRepository = turNLPEntityRepository;
	}

	public void createDefaultRows() {

		if (turNLPEntityRepository.findAll().isEmpty()) {
			saveTurNLPEntity("PN", "People", "People Entity.","persons");
			saveTurNLPEntity("GL", "Places", "Place Entity.","locations");
			saveTurNLPEntity("FR", "Fraud", "Fraud Entity.","frauds");
			saveTurNLPEntity("ON", "Organization", "Organization Entity.","organizations");
			saveTurNLPEntity("DURATION", "Duration", "Duration Entity.","durations");
			saveTurNLPEntity("ORDINAL", "Ordinal", "Ordinal Entity.","ordinals");
			saveTurNLPEntity("MISC", "Misc", "Misc Entity.","miscs");
			saveTurNLPEntity("DATE", "Date", "Date Entity.","dates");
			saveTurNLPEntity("TIME", "Time", "Time Entity.","times");
			saveTurNLPEntity("MONEY", "Money", "Money Entity.","moneys");
			saveTurNLPEntity("PERCENTAGE", "Percentage", "Percentage Entity.","percentages");
			saveTurNLPEntity("NORP", "NORP", "Nationalities or religious or political groups.","norps");
			saveTurNLPEntity("FAC", "FAC", "Buildings, airports, highways, bridges, etc.","norps");
			saveTurNLPEntity("GPE", "GPE", "Countries, cities, states.","gpe");
			saveTurNLPEntity("LOC", "LOC", "Non-GPE locations, mountain ranges, bodies of water.","locs");
			saveTurNLPEntity("PRODUCT", "Product", "Objects, vehicles, foods, etc. (Not services.)","products");
			saveTurNLPEntity("EVENT", "Event", "Named hurricanes, battles, wars, sports events, etc.","dates");
			saveTurNLPEntity("WORK_OF_ART", "Work of Art", "Titles of books, songs, etc.","worksOfArt");
			saveTurNLPEntity("LAW", "Law", "Named documents made into laws.","laws");
			saveTurNLPEntity("LANGUAGE", "Language", "Any named language.","languages");
			saveTurNLPEntity("QUANTITY", "Quantity", "Measurements, as of weight or distance.","quantities");
			saveTurNLPEntity("CARDINAL", "Cardinal", "Numerals that do not fall under another type.","cardinals");
			saveTurNLPEntity("DNI", "DNI", "National Identity Document.","dnis");
			saveTurNLPEntity("CIF", "CIF", "Certificate of Fiscal Identification.","cifs");
			saveTurNLPEntity("NIE", "NIE", "Extranjero Identification Number.","nies");
			saveTurNLPEntity("PASSPORT", "Passport", "Passport ID.","passports");
			saveTurNLPEntity("EMAIL", "Email", "Emails.","emails");
			saveTurNLPEntity("FIRST_NAME", "First Name", "First Name.","firstnames");
			saveTurNLPEntity("LAST_NAME", "Last Name", "Last Name.","lastnames");
		}
	}

	private void saveTurNLPEntity(String internalName, String name, String description, String collectionName) {
		TurNLPEntity turNLPEntity = new TurNLPEntity(internalName,name,description, collectionName, 0, 1);
		turNLPEntityRepository.save(turNLPEntity);
	}
}
