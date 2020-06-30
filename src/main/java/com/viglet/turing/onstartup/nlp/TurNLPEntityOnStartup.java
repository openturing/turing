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
			turNLPEntity.setDescription("Entidade de Fraude");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("frauds");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Organization");
			turNLPEntity.setInternalName("ON");
			turNLPEntity.setDescription("Entidades de Organizações");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("organizations");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Duration");
			turNLPEntity.setInternalName("DURATION");
			turNLPEntity.setDescription("Entidade de Durações");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("durations");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Ordinal");
			turNLPEntity.setInternalName("ORDINAL");
			turNLPEntity.setDescription("Entidade Ordinal");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("ordinals");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Misc");
			turNLPEntity.setInternalName("MISC");
			turNLPEntity.setDescription("Entidade Misc");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("miscs");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Date");
			turNLPEntity.setInternalName("DATE");
			turNLPEntity.setDescription("Entidade Date");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("dates");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Time");
			turNLPEntity.setInternalName("TIME");
			turNLPEntity.setDescription("Entidade Time");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("times");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Money");
			turNLPEntity.setInternalName("MONEY");
			turNLPEntity.setDescription("Entidade Money");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("moneys");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Percentage");
			turNLPEntity.setInternalName("PERCENTAGE");
			turNLPEntity.setDescription("Entidade de Porcentagem");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("percentages");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("NORP");
			turNLPEntity.setInternalName("NORP");
			turNLPEntity.setDescription("Nationalities or religious or political groups.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("NORPs");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("FAC");
			turNLPEntity.setInternalName("FAC");
			turNLPEntity.setDescription("Buildings, airports, highways, bridges, etc.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("FACs");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("GPE");
			turNLPEntity.setInternalName("GPE");
			turNLPEntity.setDescription("Countries, cities, states.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("GPE");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("LOC");
			turNLPEntity.setInternalName("LOC");
			turNLPEntity.setDescription("Non-GPE locations, mountain ranges, bodies of water.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("LOCs");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Product");
			turNLPEntity.setInternalName("PRODUCT");
			turNLPEntity.setDescription("Objects, vehicles, foods, etc. (Not services.)");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("Products");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Event");
			turNLPEntity.setInternalName("EVENT");
			turNLPEntity.setDescription("Named hurricanes, battles, wars, sports events, etc.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("Events");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Work of Art");
			turNLPEntity.setInternalName("WORK_OF_ART");
			turNLPEntity.setDescription("Titles of books, songs, etc.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("Work of Art");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Law");
			turNLPEntity.setInternalName("LAW");
			turNLPEntity.setDescription("Named documents made into laws.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("Laws");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Language");
			turNLPEntity.setInternalName("LANGUAGE");
			turNLPEntity.setDescription("Any named language.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("Languages");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Quantity");
			turNLPEntity.setInternalName("QUANTITY");
			turNLPEntity.setDescription("Measurements, as of weight or distance.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("Quantities");
			turNLPEntityRepository.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Cardinal");
			turNLPEntity.setInternalName("CARDINAL");
			turNLPEntity.setDescription("Numerals that do not fall under another type.");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("Cardinal");
			turNLPEntityRepository.save(turNLPEntity);
		}
	}
}
