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

package com.viglet.turing.api.converse;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.converse.chat.TurConverseChat;
import com.viglet.turing.persistence.model.converse.chat.TurConverseChatResponse;
import com.viglet.turing.persistence.model.converse.intent.TurConverseIntent;
import com.viglet.turing.persistence.model.converse.intent.TurConversePhrase;
import com.viglet.turing.persistence.repository.converse.chat.TurConverseChatRepository;
import com.viglet.turing.persistence.repository.converse.chat.TurConverseChatResponseRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseIntentRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConversePhraseRepository;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/converse/training")
@Api(tags = "Converse Training", description = "Converse Training API")
public class TurConverseTrainingAPI {
	@Autowired
	private TurConverseChatRepository turConverseChatRepository;
	@Autowired
	private TurConverseChatResponseRepository turConverseChatResponseRepository;
	@Autowired
	private TurConverseIntentRepository turConverseIntentRepository;
	@Autowired
	private TurConversePhraseRepository turConversePhraseRepository;

	@ApiOperation(value = "Converse Training List")
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

	@ApiOperation(value = "Show a Converse Training")
	@GetMapping("/{id}")
	public TurConverseChat turConverseTrainingGet(@PathVariable String id) {
		TurConverseChat turConverseChat = turConverseChatRepository.findById(id).get();
		List<TurConverseChatResponse> responses = turConverseChatResponseRepository.findByChatAndIsUser(turConverseChat,
				true);
		turConverseChat.setResponses(responses);
		return turConverseChat;
	}

	@ApiOperation(value = "Update a Converse Training")
	@PutMapping("/{id}")
	public TurConverseChat turConverseEntityUpdate(@PathVariable String id,
			@RequestBody TurConverseChat turConverseChat) {
		for (TurConverseChatResponse response : turConverseChat.getResponses()) {
			if (response.isTrainingToIntent() && response.getIntentId() != null) {
				TurConverseIntent intent = turConverseIntentRepository.findById(response.getIntentId()).get();
				addNewPhrase(response, intent);
			}
			if (response.isTrainingToFallback()) {
				List<TurConverseIntent> intents = turConverseIntentRepository.findByFallback(true);
				TurConverseIntent intent = null;

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
		for (TurConversePhrase phrase : phrases)
			if (phrase.getText().toLowerCase().trim().equals(response.getText().toLowerCase().trim()))
				foundText = true;

		if (!foundText) {
			TurConversePhrase turConversePhrase = new TurConversePhrase();
			turConversePhrase.setIntent(intent);
			turConversePhrase.setText(response.getText());
			turConversePhraseRepository.saveAndFlush(turConversePhrase);
		}
	}

}
