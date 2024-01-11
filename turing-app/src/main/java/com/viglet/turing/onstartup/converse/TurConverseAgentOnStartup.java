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
package com.viglet.turing.onstartup.converse;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import com.viglet.turing.persistence.model.converse.entity.TurConverseEntity;
import com.viglet.turing.persistence.model.converse.entity.TurConverseEntityTerm;
import com.viglet.turing.persistence.model.converse.intent.*;
import com.viglet.turing.persistence.repository.converse.TurConverseAgentRepository;
import com.viglet.turing.persistence.repository.converse.entity.TurConverseEntityRepository;
import com.viglet.turing.persistence.repository.converse.entity.TurConverseEntityTermRepository;
import com.viglet.turing.persistence.repository.converse.intent.*;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Component
@Transactional
public class TurConverseAgentOnStartup {
	private final TurConverseAgentRepository turConverseAgentRepository;
	private final TurConverseIntentRepository turConverseIntentRepository;
	private final TurConverseContextRepository turConverseContextRepository;
	private final TurConversePhraseRepository turConversePhraseRepository;
	private final TurConverseResponseRepository turConverseResponseRepository;
	private final TurConverseParameterRepository turConverseParameterRepository;
	private final TurConversePromptRepository turConversePromptRepository;
	private final TurSEInstanceRepository turSEInstanceRepository;
	private final TurConverseEntityRepository turConverseEntityRepository;
	private final TurConverseEntityTermRepository turConverseEntityTermRepository;

	@Inject
	public TurConverseAgentOnStartup(TurConverseAgentRepository turConverseAgentRepository,
									 TurConverseIntentRepository turConverseIntentRepository,
									 TurConverseContextRepository turConverseContextRepository,
									 TurConversePhraseRepository turConversePhraseRepository,
									 TurConverseResponseRepository turConverseResponseRepository,
									 TurConverseParameterRepository turConverseParameterRepository,
									 TurConversePromptRepository turConversePromptRepository,
									 TurSEInstanceRepository turSEInstanceRepository,
									 TurConverseEntityRepository turConverseEntityRepository,
									 TurConverseEntityTermRepository turConverseEntityTermRepository) {
		this.turConverseAgentRepository = turConverseAgentRepository;
		this.turConverseIntentRepository = turConverseIntentRepository;
		this.turConverseContextRepository = turConverseContextRepository;
		this.turConversePhraseRepository = turConversePhraseRepository;
		this.turConverseResponseRepository = turConverseResponseRepository;
		this.turConverseParameterRepository = turConverseParameterRepository;
		this.turConversePromptRepository = turConversePromptRepository;
		this.turSEInstanceRepository = turSEInstanceRepository;
		this.turConverseEntityRepository = turConverseEntityRepository;
		this.turConverseEntityTermRepository = turConverseEntityTermRepository;
	}

	public void createDefaultRows() {

		if (turConverseAgentRepository.findAll().isEmpty()) {

			TurConverseAgent turConverseAgent = new TurConverseAgent();
			turConverseAgent.setName("Agent01");
			turConverseAgent.setDescription("Sample Agent");
			turConverseAgent.setLanguage("pt_BR");
			turConverseAgent.setCore("converse");
			turConverseAgent.setTurSEInstance(turSEInstanceRepository.findAll().getFirst());

			turConverseAgentRepository.save(turConverseAgent);

			// Intent01
			TurConverseIntent turConverseIntent = new TurConverseIntent();
			turConverseIntent.setName("Intent01");
			turConverseIntent.setAgent(turConverseAgent);
			turConverseIntent.setActionName("dados-pessoais");
			turConverseIntentRepository.save(turConverseIntent);

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

			// Intent02

			TurConverseIntent turConverseIntent2 = new TurConverseIntent();
			turConverseIntent2.setName("Intent02");
			turConverseIntent2.setAgent(turConverseAgent);
			turConverseIntentRepository.save(turConverseIntent2);

			// Contexts
			TurConverseContext turConverseContextOutput01 = new TurConverseContext("intent01");
			Set<TurConverseIntent> intentInputs = new HashSet<>();
			intentInputs.add(turConverseIntent2);
			Set<TurConverseIntent> intentOutputs = new HashSet<>();
			intentOutputs.add(turConverseIntent);
			turConverseContextOutput01.setIntentInputs(intentInputs);
			turConverseContextOutput01.setIntentOutputs(intentOutputs);
			turConverseContextOutput01.setAgent(turConverseAgent);
			turConverseContextRepository.save(turConverseContextOutput01);

			TurConverseContext turConverseContextOutput02 = new TurConverseContext("intent02");
			intentOutputs = new HashSet<>();
			intentOutputs.add(turConverseIntent2);
			turConverseContextOutput02.setAgent(turConverseAgent);
			turConverseContextOutput02.setIntentOutputs(intentOutputs);
			turConverseContextRepository.save(turConverseContextOutput02);

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

			this.createEntities(turConverseAgent);
		}
	}

	private void createEntities(TurConverseAgent agent) {

		if (turConverseEntityRepository.findAll().isEmpty()) {

			// Carros
			TurConverseEntity turConverseEntity = new TurConverseEntity();
			turConverseEntity.setName("carros");
			turConverseEntity.setSynonyms(true);
			turConverseEntity.setAllowAutomatedExpansion(false);
			turConverseEntity.setFuzzyMatching(false);
			turConverseEntity.setUseRegexp(false);
			turConverseEntity.setAgent(agent);
			turConverseEntityRepository.save(turConverseEntity);

			Set<String> terms = new HashSet<>();
			terms.add("veículos");
			terms.add("automóveis");
			TurConverseEntityTerm turConverseEntityTerm = new TurConverseEntityTerm();
			turConverseEntityTerm.setName("carro");
			turConverseEntityTerm.setSynonyms(terms);
			turConverseEntityTerm.setEntity(turConverseEntity);

			turConverseEntityTermRepository.save(turConverseEntityTerm);
			Set<String> terms02 = new HashSet<>();
			terms02.add("bus");
			terms02.add("biarticulado");

			turConverseEntityTerm = new TurConverseEntityTerm();
			turConverseEntityTerm.setName("onibus");
			turConverseEntityTerm.setSynonyms(terms02);
			turConverseEntityTerm.setEntity(turConverseEntity);

			turConverseEntityTermRepository.save(turConverseEntityTerm);

			// Bicicletas
			turConverseEntity = new TurConverseEntity();
			turConverseEntity.setName("bibicleta");
			turConverseEntity.setSynonyms(true);
			turConverseEntity.setAllowAutomatedExpansion(false);
			turConverseEntity.setFuzzyMatching(false);
			turConverseEntity.setUseRegexp(false);
			turConverseEntity.setAgent(agent);
			turConverseEntityRepository.save(turConverseEntity);

			Set<String> terms03 = new HashSet<>();
			terms03.add("bike");
			terms03.add("bicycle");
			turConverseEntityTerm = new TurConverseEntityTerm();
			turConverseEntityTerm.setName("bibicleta");
			turConverseEntityTerm.setSynonyms(terms03);
			turConverseEntityTerm.setEntity(turConverseEntity);

			turConverseEntityTermRepository.save(turConverseEntityTerm);

			Set<String> terms04 = new HashSet<>();
			terms04.add("triciclo");
			terms04.add("moto");

			turConverseEntityTerm = new TurConverseEntityTerm();
			turConverseEntityTerm.setName("triciclo");
			turConverseEntityTerm.setSynonyms(terms04);
			turConverseEntityTerm.setEntity(turConverseEntity);

			turConverseEntityTermRepository.save(turConverseEntityTerm);
		}
	}

}