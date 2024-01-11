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
import org.springframework.beans.factory.annotation.Autowired;
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
			saveTurNLPEntity("PN", "People", "People Entity.","persons", 0, 1);
			saveTurNLPEntity("GL", "Places", "Place Entity.","locations", 0, 1);
			saveTurNLPEntity("FR", "Fraud", "Fraud Entity.","frauds", 0, 1);
			saveTurNLPEntity("ON", "Organization", "Organization Entity.","organizations", 0, 1);
			saveTurNLPEntity("DURATION", "Duration", "Duration Entity.","durations", 0, 1);
			saveTurNLPEntity("ORDINAL", "Ordinal", "Ordinal Entity.","ordinals", 0, 1);
			saveTurNLPEntity("MISC", "Misc", "Misc Entity.","miscs", 0, 1);
			saveTurNLPEntity("DATE", "Date", "Date Entity.","dates", 0, 1);
			saveTurNLPEntity("TIME", "Time", "Time Entity.","times", 0, 1);
			saveTurNLPEntity("MONEY", "Money", "Money Entity.","moneys", 0, 1);
			saveTurNLPEntity("PERCENTAGE", "Percentage", "Percentage Entity.","percentages", 0, 1);
			saveTurNLPEntity("NORP", "NORP", "Nationalities or religious or political groups.","norps", 0, 1);
			saveTurNLPEntity("FAC", "FAC", "Buildings, airports, highways, bridges, etc.","norps", 0, 1);
			saveTurNLPEntity("GPE", "GPE", "Countries, cities, states.","gpe", 0, 1);
			saveTurNLPEntity("LOC", "LOC", "Non-GPE locations, mountain ranges, bodies of water.","locs", 0, 1);
			saveTurNLPEntity("PRODUCT", "Product", "Objects, vehicles, foods, etc. (Not services.)","products", 0, 1);
			saveTurNLPEntity("EVENT", "Event", "Named hurricanes, battles, wars, sports events, etc.","dates", 0, 1);
			saveTurNLPEntity("WORK_OF_ART", "Work of Art", "Titles of books, songs, etc.","worksOfArt", 0, 1);
			saveTurNLPEntity("LAW", "Law", "Named documents made into laws.","laws", 0, 1);
			saveTurNLPEntity("LANGUAGE", "Language", "Any named language.","languages", 0, 1);
			saveTurNLPEntity("QUANTITY", "Quantity", "Measurements, as of weight or distance.","quantities", 0, 1);
			saveTurNLPEntity("CARDINAL", "Cardinal", "Numerals that do not fall under another type.","cardinals", 0, 1);
			saveTurNLPEntity("DNI", "DNI", "National Identity Document.","dnis", 0, 1);
			saveTurNLPEntity("CIF", "CIF", "Certificate of Fiscal Identification.","cifs", 0, 1);
			saveTurNLPEntity("NIE", "NIE", "Extranjero Identification Number.","nies", 0, 1);
			saveTurNLPEntity("PASSAPORT", "Passaport", "Passport ID.","passports", 0, 1);
			saveTurNLPEntity("EMAIL", "Email", "Emails.","emails", 0, 1);
			saveTurNLPEntity("FIRST_NAME", "First Name", "First Name.","firstnames", 0, 1);
			saveTurNLPEntity("LAST_NAME", "Last Name", "Last Name.","lastnames", 0, 1);
		}
	}

	private void saveTurNLPEntity(String internalName, String name, String description,  String collectionName, int local, int enabled) {
		TurNLPEntity turNLPEntity = new TurNLPEntity(internalName,name,description, collectionName, local, enabled);
		turNLPEntityRepository.save(turNLPEntity);
	}
}
