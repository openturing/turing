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

import com.viglet.turing.converse.TurConverseIndex;
import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import com.viglet.turing.persistence.model.converse.intent.TurConverseContext;
import com.viglet.turing.persistence.model.converse.intent.TurConverseIntent;
import com.viglet.turing.persistence.model.converse.intent.TurConverseParameter;
import com.viglet.turing.persistence.model.converse.intent.TurConversePhrase;
import com.viglet.turing.persistence.model.converse.intent.TurConversePrompt;
import com.viglet.turing.persistence.model.converse.intent.TurConverseResponse;
import com.viglet.turing.persistence.repository.converse.TurConverseAgentRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseContextRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseIntentRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseParameterRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConversePhraseRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConversePromptRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseResponseRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;

@Component
@Transactional
public class TurConverseAgentOnStartup {
	@Autowired
	private TurConverseAgentRepository turConverseAgentRepository;
	@Autowired
	private TurConverseIntentRepository turConverseIntentRepository;
	@Autowired
	private TurConverseContextRepository turConverseContextRepository;
	@Autowired
	private TurConversePhraseRepository turConversePhraseRepository;
	@Autowired
	private TurConverseResponseRepository turConverseResponseRepository;
	@Autowired
	private TurConverseParameterRepository turConverseParameterRepository;
	@Autowired
	private TurConversePromptRepository turConversePromptRepository;
	@Autowired
	private TurSEInstanceRepository turSEInstanceRepository;
	@Autowired
	private TurConverseIndex turConverseIndex;

	public void createDefaultRows() {

		if (turConverseAgentRepository.findAll().isEmpty()) {

			TurConverseAgent turConverseAgent = new TurConverseAgent();
			turConverseAgent.setName("Agent01");
			turConverseAgent.setDescription("Sample Agent");
			turConverseAgent.setLanguage("pt_BR");
			turConverseAgent.setCore("converse");
			turConverseAgent.setTurSEInstance(turSEInstanceRepository.findAll().get(0));

			turConverseAgentRepository.save(turConverseAgent);

			// Intent01
			TurConverseIntent turConverseIntent = new TurConverseIntent();
			turConverseIntent.setName("Intent01");
			turConverseIntent.setAgent(turConverseAgent);
			turConverseIntent.setActionName("dados-pessoais");
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

			TurConverseParameter turConverseParameter01 = new TurConverseParameter();
			turConverseParameter01.setPosition(1);
			turConverseParameter01.setRequired(true);
			turConverseParameter01.setName("nome");
			turConverseParameter01.setEntity("@sys.any");
			turConverseParameter01.setIntent(turConverseIntent);

			turConverseParameterRepository.save(turConverseParameter01);

			TurConversePrompt prompt11 = new TurConversePrompt(1, "Qual é o seu nome?");
			prompt11.setParameter(turConverseParameter01);

			turConversePromptRepository.save(prompt11);
			
			TurConversePrompt prompt12 = new TurConversePrompt(2, "Digite seu nome");
			prompt12.setParameter(turConverseParameter01);

			turConversePromptRepository.save(prompt12);

			TurConverseParameter turConverseParameter02 = new TurConverseParameter();
			turConverseParameter02.setPosition(2);
			turConverseParameter02.setRequired(true);
			turConverseParameter02.setName("idade");
			turConverseParameter02.setEntity("@sys.any");
			turConverseParameter02.setIntent(turConverseIntent);

			turConverseParameterRepository.save(turConverseParameter02);

			TurConversePrompt prompt21 = new TurConversePrompt(1, "Qual é o sua idade?");
			prompt21.setParameter(turConverseParameter02);

			turConversePromptRepository.save(prompt21);
			
			TurConversePrompt prompt22 = new TurConversePrompt(2, "Digite sua idade");
			prompt22.setParameter(turConverseParameter02);

			turConversePromptRepository.save(prompt22);

		
			// Solr
			turConverseIndex.index(turConverseIntent);

			// Intent02

			TurConverseIntent turConverseIntent2 = new TurConverseIntent();
			turConverseIntent2.setName("Intent02");
			turConverseIntent2.setAgent(turConverseAgent);
			turConverseIntentRepository.save(turConverseIntent2);

			// Contexts

			TurConverseContext turConverseContextInput = new TurConverseContext("intent01");
			Set<TurConverseIntent> intentInputs = new HashSet<>();
			intentInputs.add(turConverseIntent2);
			turConverseContextInput.setIntentInputs(intentInputs);

			turConverseContextRepository.save(turConverseContextInput);

			turConverseContextOutput = new TurConverseContext("intent02");
			intentOutputs = new HashSet<>();
			intentOutputs.add(turConverseIntent2);
			turConverseContextOutput.setIntentOutputs(intentOutputs);

			turConverseContextRepository.save(turConverseContextOutput);

			// Phrases
			TurConversePhrase turConversePhrase2a = new TurConversePhrase("Muito bom");
			turConversePhrase2a.setIntent(turConverseIntent2);
			turConversePhraseRepository.save(turConversePhrase2a);

			TurConversePhrase turConversePhrase2b = new TurConversePhrase("Legal");
			turConversePhrase2b.setIntent(turConverseIntent2);
			turConversePhraseRepository.save(turConversePhrase2b);

			TurConversePhrase turConversePhrase2c = new TurConversePhrase("Ótimo");
			turConversePhrase2c.setIntent(turConverseIntent2);
			turConversePhraseRepository.save(turConversePhrase2c);

			// Responses
			TurConverseResponse turConverseResponse2a = new TurConverseResponse("Obrigado por perguntar");
			turConverseResponse2a.setIntent(turConverseIntent2);
			turConverseResponseRepository.save(turConverseResponse2a);

			TurConverseResponse turConverseResponse2b = new TurConverseResponse("Um belo dia hoje");
			turConverseResponse2b.setIntent(turConverseIntent2);
			turConverseResponseRepository.save(turConverseResponse2b);

			TurConverseResponse turConverseResponse2c = new TurConverseResponse("Como vai sua família?");
			turConverseResponse2c.setIntent(turConverseIntent2);
			turConverseResponseRepository.save(turConverseResponse2c);

			// Solr
			turConverseIndex.index(turConverseIntent2);

		}
	}

}