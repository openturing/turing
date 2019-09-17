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

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.converse.TurConverseContext;
import com.viglet.turing.persistence.model.converse.TurConverseIntent;
import com.viglet.turing.persistence.model.converse.TurConversePhrase;
import com.viglet.turing.persistence.model.converse.TurConverseResponse;
import com.viglet.turing.persistence.repository.converse.TurConverseContextRepository;
import com.viglet.turing.persistence.repository.converse.TurConverseIntentRepository;
import com.viglet.turing.persistence.repository.converse.TurConversePhraseRepository;
import com.viglet.turing.persistence.repository.converse.TurConverseResponseRepository;

@Component
@Transactional
public class TurConverseIntentOnStartup {

	@Autowired
	private TurConverseIntentRepository turConverseIntentRepository;
	@Autowired
	private TurConverseContextRepository turConverseContextRepository;
	@Autowired
	private TurConversePhraseRepository turConversePhraseRepository;
	@Autowired
	private TurConverseResponseRepository turConverseResponseRepository;

	public void createDefaultRows() {

		if (turConverseIntentRepository.findAll().isEmpty()) {
			
			// Intent01
			TurConverseIntent turConverseIntent = new TurConverseIntent();
			turConverseIntent.setName("Intent01");
			turConverseIntentRepository.save(turConverseIntent);

			// Contexts
			TurConverseContext turConverseContextOutput = new TurConverseContext("intent01");
			Set<TurConverseIntent> intentOutputs = new HashSet<>();
			intentOutputs.add(turConverseIntent);
			turConverseContextOutput.setIntentOutputs(intentOutputs);

			turConverseContextRepository.save(turConverseContextOutput);


			// Phrases
			TurConversePhrase turConversePhrase1a = new TurConversePhrase("Oi");
			turConversePhrase1a.setIntent(turConverseIntent);
			turConversePhraseRepository.save(turConversePhrase1a);

			TurConversePhrase turConversePhrase1b = new TurConversePhrase("Bom dia");
			turConversePhrase1b.setIntent(turConverseIntent);
			turConversePhraseRepository.save(turConversePhrase1b);

			TurConversePhrase turConversePhrase1c = new TurConversePhrase("Como vai?");
			turConversePhrase1c.setIntent(turConverseIntent);
			turConversePhraseRepository.save(turConversePhrase1c);					
			
			// Responses
			TurConverseResponse turConverseResponse1a = new TurConverseResponse("Acordei bem, obrigado!");
			turConverseResponse1a.setIntent(turConverseIntent);
			turConverseResponseRepository.save(turConverseResponse1a);

			TurConverseResponse turConverseResponse1b = new TurConverseResponse("Tive um dia cansativo");
			turConverseResponse1b.setIntent(turConverseIntent);
			turConverseResponseRepository.save(turConverseResponse1b);

			TurConverseResponse turConverseResponse1c = new TurConverseResponse("Mais um novo dia");
			turConverseResponse1c.setIntent(turConverseIntent);
			turConverseResponseRepository.save(turConverseResponse1c);
			
			// Intent02
			
			 turConverseIntent = new TurConverseIntent();
			turConverseIntent.setName("Intent02");
			turConverseIntentRepository.save(turConverseIntent);

			// Contexts

			TurConverseContext turConverseContextInput = new TurConverseContext("intent01");
			Set<TurConverseIntent> intentInputs = new HashSet<>();
			intentInputs.add(turConverseIntent);
			turConverseContextInput.setIntentInputs(intentInputs);

			turConverseContextRepository.save(turConverseContextInput);

			turConverseContextOutput = new TurConverseContext("intent02");
			intentOutputs = new HashSet<>();
			intentOutputs.add(turConverseIntent);
			turConverseContextOutput.setIntentOutputs(intentOutputs);

			turConverseContextRepository.save(turConverseContextOutput);

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