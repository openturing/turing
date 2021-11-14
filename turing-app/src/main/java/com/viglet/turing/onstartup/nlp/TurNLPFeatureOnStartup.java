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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.nlp.TurNLPFeature;
import com.viglet.turing.persistence.repository.nlp.TurNLPFeatureRepository;

@Component
@Transactional
public class TurNLPFeatureOnStartup {

	@Autowired
	private TurNLPFeatureRepository turNLPFeatureRepository;

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
			saveNLPFeature("Coreference");
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
