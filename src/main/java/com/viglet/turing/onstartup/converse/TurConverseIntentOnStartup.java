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

package com.viglet.turing.onstartup.converse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.converse.TurConverseIntent;
import com.viglet.turing.persistence.model.converse.TurConversePhrase;
import com.viglet.turing.persistence.model.converse.TurConverseResponse;
import com.viglet.turing.persistence.repository.converse.TurConverseIntentRepository;
import com.viglet.turing.persistence.repository.converse.TurConversePhraseRepository;
import com.viglet.turing.persistence.repository.converse.TurConverseResponseRepository;

@Component
@Transactional
public class TurConverseIntentOnStartup {

	@Autowired
	private TurConverseIntentRepository turConverseIntentRepository;
	@Autowired
	private TurConversePhraseRepository turConversePhraseRepository;
	@Autowired
	private TurConverseResponseRepository turConverseResponseRepository;
	
	public void createDefaultRows() {

		if (turConverseIntentRepository.findAll().isEmpty()) {
			
			TurConverseIntent turConverseIntent = new TurConverseIntent();
			turConverseIntent.setName("Intent02");
			turConverseIntentRepository.save(turConverseIntent);

			// Phrases
			TurConversePhrase turConversePhrase2a = new TurConversePhrase("Muito bom");
			turConversePhrase2a.setIntent(turConverseIntent);
			turConversePhraseRepository.save(turConversePhrase2a);
			
			TurConversePhrase turConversePhrase2b = new TurConversePhrase("Legal");
			turConversePhrase2b.setIntent(turConverseIntent);
			turConversePhraseRepository.save(turConversePhrase2b);
			
			TurConversePhrase turConversePhrase2c = new TurConversePhrase("Ótimo");
			turConversePhrase2c.setIntent(turConverseIntent);
			turConversePhraseRepository.save(turConversePhrase2c);

			// Responses
			TurConverseResponse turConverseResponse2a = new TurConverseResponse("Obrigado por perguntar");
			turConverseResponse2a.setIntent(turConverseIntent);
			turConverseResponseRepository.save(turConverseResponse2a);
			
			TurConverseResponse turConverseResponse2b = new TurConverseResponse("Um belo dia hoje");
			turConverseResponse2b.setIntent(turConverseIntent);
			turConverseResponseRepository.save(turConverseResponse2b);
			
			TurConverseResponse turConverseResponse2c = new TurConverseResponse("Como vai sua família?");
			turConverseResponse2c.setIntent(turConverseIntent);
			turConverseResponseRepository.save(turConverseResponse2c);
			
		}
	}

}