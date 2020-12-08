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

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;

@Component
@Transactional
public class TurNLPEntityOnStartup {
	@Autowired
	DataSource dataSource;

	@Autowired(required = true)
	private TurNLPEntityRepository turNLPEntityRepository;

	public void createDefaultRows() {

		if (turNLPEntityRepository.findAll().isEmpty()) {
			TurNLPEntity turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("People");
			turNLPEntity.setInternalName("PN");
			turNLPEntity.setDescription("Entidade de Pessoas");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("persons");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Places");
			turNLPEntity.setInternalName("GL");
			turNLPEntity.setDescription("Entidade de Lugares");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("locations");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Fraud");
			turNLPEntity.setInternalName("FR");
			turNLPEntity.setDescription("Fraud Entity");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("frauds");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Organization");
			turNLPEntity.setInternalName("ON");
			turNLPEntity.setDescription("Organization Entity");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("organizations");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Duration");
			turNLPEntity.setInternalName("DURATION");
			turNLPEntity.setDescription("Duration Entity");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("durations");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Ordinal");
			turNLPEntity.setInternalName("ORDINAL");
			turNLPEntity.setDescription("Ordinal Entity");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("ordinals");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Misc");
			turNLPEntity.setInternalName("MISC");
			turNLPEntity.setDescription("Misc Entity");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("miscs");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Date");
			turNLPEntity.setInternalName("DATE");
			turNLPEntity.setDescription("Date Entity");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("dates");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Time");
			turNLPEntity.setInternalName("TIME");
			turNLPEntity.setDescription("Time Entity");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("times");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Money");
			turNLPEntity.setInternalName("MONEY");
			turNLPEntity.setDescription("Money Entity");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("moneys");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Percentage");
			turNLPEntity.setInternalName("PERCENTAGE");
			turNLPEntity.setDescription("Percentage Entity");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("percentages");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("NORP");
			turNLPEntity.setInternalName("NORP");
			turNLPEntity.setDescription("Nationalities or religious or political groups.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("norps");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("FAC");
			turNLPEntity.setInternalName("FAC");
			turNLPEntity.setDescription("Buildings, airports, highways, bridges, etc.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("facs");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("GPE");
			turNLPEntity.setInternalName("GPE");
			turNLPEntity.setDescription("Countries, cities, states.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("gpe");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("LOC");
			turNLPEntity.setInternalName("LOC");
			turNLPEntity.setDescription("Non-GPE locations, mountain ranges, bodies of water.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("locs");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Product");
			turNLPEntity.setInternalName("PRODUCT");
			turNLPEntity.setDescription("Objects, vehicles, foods, etc. (Not services.)");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("products");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Event");
			turNLPEntity.setInternalName("EVENT");
			turNLPEntity.setDescription("Named hurricanes, battles, wars, sports events, etc.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("events");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Work of Art");
			turNLPEntity.setInternalName("WORK_OF_ART");
			turNLPEntity.setDescription("Titles of books, songs, etc.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("work of art");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Law");
			turNLPEntity.setInternalName("LAW");
			turNLPEntity.setDescription("Named documents made into laws.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("laws");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Language");
			turNLPEntity.setInternalName("LANGUAGE");
			turNLPEntity.setDescription("Any named language.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("languages");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Quantity");
			turNLPEntity.setInternalName("QUANTITY");
			turNLPEntity.setDescription("Measurements, as of weight or distance.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("quantities");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Cardinal");
			turNLPEntity.setInternalName("CARDINAL");
			turNLPEntity.setDescription("Numerals that do not fall under another type.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("cardinal");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("DNI");
			turNLPEntity.setInternalName("DNI");
			turNLPEntity.setDescription("Documento Nacional de Identidad.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("dnis");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("CIF");
			turNLPEntity.setInternalName("CIF");
			turNLPEntity.setDescription("Certificado de Identificación Fiscal.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("cifs");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("NIE");
			turNLPEntity.setInternalName("NIE");
			turNLPEntity.setDescription("Número de Identificación de Extranjero.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("nies");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Passaport");
			turNLPEntity.setInternalName("PASSAPORT");
			turNLPEntity.setDescription("Passport ID.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("passports");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Email");
			turNLPEntity.setInternalName("EMAIL");
			turNLPEntity.setDescription("Emails.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("emails");
			turNLPEntityRepository.save(turNLPEntity);
		}
	}
}
