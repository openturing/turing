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

package com.viglet.turing.api.converse;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.converse.chat.TurConverseChat;
import com.viglet.turing.persistence.model.converse.chat.TurConverseChatResponse;
import com.viglet.turing.persistence.model.converse.intent.TurConverseIntent;
import com.viglet.turing.persistence.model.converse.intent.TurConversePhrase;
import com.viglet.turing.persistence.repository.converse.chat.TurConverseChatRepository;
import com.viglet.turing.persistence.repository.converse.chat.TurConverseChatResponseRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseIntentRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConversePhraseRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/converse/training")
@Tag(name ="Converse Training", description = "Converse Training API")
public class TurConverseTrainingAPI {
	private final TurConverseChatRepository turConverseChatRepository;
	private final TurConverseChatResponseRepository turConverseChatResponseRepository;
	private final TurConverseIntentRepository turConverseIntentRepository;
	private final TurConversePhraseRepository turConversePhraseRepository;
	@Inject
	public TurConverseTrainingAPI(TurConverseChatRepository turConverseChatRepository,
								  TurConverseChatResponseRepository turConverseChatResponseRepository,
								  TurConverseIntentRepository turConverseIntentRepository,
								  TurConversePhraseRepository turConversePhraseRepository) {
		this.turConverseChatRepository = turConverseChatRepository;
		this.turConverseChatResponseRepository = turConverseChatResponseRepository;
		this.turConverseIntentRepository = turConverseIntentRepository;
		this.turConversePhraseRepository = turConversePhraseRepository;
	}

	@Operation(summary = "Converse Training List")
	@GetMapping
	public List<TurConverseChat> turConverseTrainingList() {
		List<TurConverseChat> turConverseChats = this.turConverseChatRepository.findAll();

		for (TurConverseChat turConverseChat : turConverseChats) {
			if (!turConverseChat.isUpdated()) {
				List<TurConverseChatResponse> responses = turConverseChatResponseRepository
						.findByChatOrderByDate(turConverseChat);
				turConverseChat.setUpdated(true);
				if (!responses.isEmpty())
					turConverseChat.setSummary(responses.get(0).getText());
				turConverseChat
						.setRequests(turConverseChatResponseRepository.countByChatAndIsUser(turConverseChat, true));
				turConverseChat.setNoMatch(
						turConverseChatResponseRepository.countByChatAndIsUserAndIntentIdIsNull(turConverseChat, true));
				turConverseChatRepository.save(turConverseChat);
			}
		}
		return turConverseChats;
	}

	@Operation(summary = "Show a Converse Training")
	@GetMapping("/{id}")
	public TurConverseChat turConverseTrainingGet(@PathVariable String id) {
		return turConverseChatRepository.findById(id).map(turConverseChat -> {
			List<TurConverseChatResponse> responses = turConverseChatResponseRepository
					.findByChatAndIsUser(turConverseChat, true);
			turConverseChat.setResponses(responses);
			return turConverseChat;
		}).orElse(new TurConverseChat());

	}

	@Operation(summary = "Update a Converse Training")
	@PutMapping("/{id}")
	public TurConverseChat turConverseEntityUpdate(@PathVariable String id,
			@RequestBody TurConverseChat turConverseChat) {
		for (TurConverseChatResponse response : turConverseChat.getResponses()) {
			if (response.isTrainingToIntent() && response.getIntentId() != null) {
				turConverseIntentRepository.findById(response.getIntentId())
						.ifPresent(intent -> addNewPhrase(response, intent));
			}
			if (response.isTrainingToFallback()) {
				List<TurConverseIntent> intents = turConverseIntentRepository
						.findByAgentAndFallback(turConverseChat.getAgent(), true);

				TurConverseIntent intent;

				if (!intents.isEmpty()) {
					intent = intents.get(0);
				} else {
					intent = new TurConverseIntent();
					intent.setName("Default Fallback");
					intent.setAgent(turConverseChat.getAgent());
					intent.setFallback(true);
					turConverseIntentRepository.saveAndFlush(intent);
				}

				if (intent != null)
					addNewPhrase(response, intent);

			}
			response.setChat(turConverseChat);
		}

		return turConverseChatRepository.saveAndFlush(turConverseChat);

	}

	private void addNewPhrase(TurConverseChatResponse response, TurConverseIntent intent) {
		Set<TurConversePhrase> phrases = turConversePhraseRepository.findByIntent(intent);
		boolean foundText = false;
		for (TurConversePhrase phrase : phrases) {
			if (phrase.getText().toLowerCase().trim().equals(response.getText().toLowerCase().trim())) {
				foundText = true;
				break;
			}
		}

		if (!foundText) {
			TurConversePhrase turConversePhrase = new TurConversePhrase();
			turConversePhrase.setIntent(intent);
			turConversePhrase.setText(response.getText());
			turConversePhraseRepository.saveAndFlush(turConversePhrase);
		}
	}

}
