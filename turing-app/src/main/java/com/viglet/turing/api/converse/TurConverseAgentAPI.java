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
import com.viglet.turing.bean.converse.TurConverseAgentResponse;
import com.viglet.turing.converse.TurConverse;
import com.viglet.turing.converse.TurConverseSE;
import com.viglet.turing.converse.exchange.TurConverseImportExchange;
import com.viglet.turing.converse.exchange.agent.TurConverseAgentExchange;
import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import com.viglet.turing.persistence.model.converse.chat.TurConverseChat;
import com.viglet.turing.persistence.model.converse.entity.TurConverseEntity;
import com.viglet.turing.persistence.model.converse.intent.TurConverseContext;
import com.viglet.turing.persistence.model.converse.intent.TurConverseIntent;
import com.viglet.turing.persistence.repository.converse.TurConverseAgentRepository;
import com.viglet.turing.persistence.repository.converse.chat.TurConverseChatRepository;
import com.viglet.turing.persistence.repository.converse.entity.TurConverseEntityRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseContextRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseIntentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/converse/agent")
@Tag(name = "Converse Agent", description = "Converse Agent API")
public class TurConverseAgentAPI {

	private static final String CONVERSATION_ID = "conversationId";
	private static final String HAS_PARAMETER = "hasParameter";

	private final TurConverseAgentRepository turConverseAgentRepository;
	private final TurConverseIntentRepository turConverseIntentRepository;
	private final TurConverseEntityRepository turConverseEntityRepository;
	private final TurConverseContextRepository turConverseContextRepository;
	private final TurConverseChatRepository turConverseChatRepository;
	private final TurConverseImportExchange turConverseImportExchange;
	private final TurConverse turConverse;
	private final TurConverseSE turConverseSE;

	@Inject
	public TurConverseAgentAPI(TurConverseAgentRepository turConverseAgentRepository,
							   TurConverseIntentRepository turConverseIntentRepository,
							   TurConverseEntityRepository turConverseEntityRepository,
							   TurConverseContextRepository turConverseContextRepository,
							   TurConverseChatRepository turConverseChatRepository,
							   TurConverseImportExchange turConverseImportExchange,
							   TurConverse turConverse, TurConverseSE turConverseSE) {
		this.turConverseAgentRepository = turConverseAgentRepository;
		this.turConverseIntentRepository = turConverseIntentRepository;
		this.turConverseEntityRepository = turConverseEntityRepository;
		this.turConverseContextRepository = turConverseContextRepository;
		this.turConverseChatRepository = turConverseChatRepository;
		this.turConverseImportExchange = turConverseImportExchange;
		this.turConverse = turConverse;
		this.turConverseSE = turConverseSE;
	}

	@Operation(summary = "Converse Agent List")
	@GetMapping
	public List<TurConverseAgent> turConverseAgentList() {
		return this.turConverseAgentRepository.findAll();
	}

	@Operation(summary = "Show a Converse Agent")
	@GetMapping("/{id}")
	public TurConverseAgent turConverseAgentGet(@PathVariable String id) {
		return turConverseAgentRepository.findById(id).orElse(new TurConverseAgent());
	}

	@Operation(summary = "Show a Converse Intent List")
	@GetMapping("/{id}/intents")
	public Set<TurConverseIntent> turConverseAgentIntentsGet(@PathVariable String id) {
		return turConverseAgentRepository.findById(id)
				.map(turConverseIntentRepository::findByAgent)
				.orElse(new HashSet<>());

	}

	@Operation(summary = "Show a Converse Entity List")
	@GetMapping("/{id}/entities")
	public Set<TurConverseEntity> turConverseAgentEntitiesGet(@PathVariable String id) {
		return turConverseAgentRepository.findById(id)
				.map(turConverseEntityRepository::findByAgent)
				.orElse(new HashSet<>());
	}

	@Operation(summary = "Show a Converse Context List")
	@GetMapping("/{id}/contexts")
	public Set<TurConverseContext> turConverseAgentContextsGet(@PathVariable String id) {
		return turConverseAgentRepository.findById(id)
				.map(turConverseContextRepository::findByAgent)
				.orElse(new HashSet<>());
	}

	@Operation(summary = "Create a Converse Agent")
	@PostMapping
	public TurConverseAgent turConverseAgentAdd(@RequestBody TurConverseAgent turConverseAgent) {
		return turConverseAgentRepository.save(turConverseAgent);
	}

	@Operation(summary = "Update a Converse Agent")
	@PutMapping("/{id}")
	public TurConverseAgent turConverseAgentUpdate(@PathVariable String id,
			@RequestBody TurConverseAgent turConverseAgent) {
		return turConverseAgentRepository.findById(turConverseAgent.getId()).map(turConverseAgentEdit -> {

			turConverseAgentEdit.setCore(turConverseAgent.getCore());
			turConverseAgentEdit.setDescription(turConverseAgent.getDescription());
			turConverseAgentEdit.setLanguage(turConverseAgent.getLanguage());
			turConverseAgentEdit.setName(turConverseAgent.getName());
			turConverseAgentEdit.setTurSEInstance(turConverseAgent.getTurSEInstance());

			return turConverseAgentRepository.save(turConverseAgentEdit);

		}).orElse(new TurConverseAgent());

	}

	@Transactional
	@Operation(summary = "Delete a Converse Agent")
	@DeleteMapping("/{id}")
	public boolean turConverseAgentDelete(@PathVariable String id) {
		return turConverseAgentRepository.findById(id).map(turConverseAgent -> {
			Set<TurConverseContext> turConverseContexts = turConverseContextRepository.findByAgent(turConverseAgent);
			for (TurConverseContext context : turConverseContexts) {
				context.setIntentInputs(null);
				context.setIntentOutputs(null);
				turConverseContextRepository.saveAndFlush(context);
			}
			this.turConverseAgentRepository.deleteById(id);
			return true;
		}).orElse(false);

	}

	@Operation(summary = "Converse Agent Model")
	@GetMapping("/model")
	public TurConverseAgent turConverseAgentModel() {
		return new TurConverseAgent();
	}

	@Operation(summary = "Rebuild Chat")
	@GetMapping("/{id}/rebuild")
	public boolean turConverseAgentRebuild(@PathVariable String id) {

		return turConverseAgentRepository.findById(id).map(turConverseAgent -> {
			Set<TurConverseIntent> turConverseIntents = turConverseIntentRepository.findByAgent(turConverseAgent);
			turConverseSE.desindexAll(turConverseAgent);

			for (TurConverseIntent turConverseIntent : turConverseIntents) {
				turConverseSE.index(turConverseIntent);
			}
			return true;
		}).orElse(false);

	}

	@Operation(summary = "Converse Chat")
	@GetMapping("/{id}/chat")
	public TurConverseAgentResponse turConverseAgentChat(@PathVariable String id,
			@RequestParam(required = false, name = "q") String q,
			@RequestParam(required = false, name = "start") boolean start, HttpSession session) {

		String conversationId;

		if (start || session.getAttribute(CONVERSATION_ID) == null) {
			turConverse.cleanSession(session);
			SimpleDateFormat dateFormat = new SimpleDateFormat("ddMMyyyyHHmmss");
			conversationId = dateFormat.format(new Date());

			session.setAttribute(CONVERSATION_ID, conversationId);
		} else {
			conversationId = (String) session.getAttribute(CONVERSATION_ID);
		}

		String sessionId = String.format("%s.%s", session.getId(), conversationId);
		turConverse.showSession(session);

		boolean hasParameter = session.getAttribute(HAS_PARAMETER) != null
				&& (boolean) session.getAttribute(HAS_PARAMETER);

		TurConverseChat chat = null;
		if (session.getId() != null) {
			chat = turConverseChatRepository.findBySession(sessionId);
			if (chat == null) {

				chat = new TurConverseChat();
				chat.setSession(sessionId);
				chat.setAgent(turConverseAgentRepository.findById(id).orElse(null));
				chat.setDate(new Date());
				turConverseChatRepository.save(chat);
			}
		}

		turConverse.saveChatResponseUser(q, chat, session);

		TurConverseAgentResponse turConverseAgentResponse = new TurConverseAgentResponse();
		if (chat != null) {
			if (hasParameter) {
				turConverse.getChatParameter(chat, session, turConverseAgentResponse);
			} else {
				String nextContext = (String) session.getAttribute("nextContext");
				turConverseAgentResponse = turConverse.interactionNested(chat, q, nextContext, session);
			}
		}
		turConverse.saveChatResponseBot(chat, turConverseAgentResponse, session);

		return turConverseAgentResponse;
	}

	@PostMapping("/import")
	@Transactional
	public TurConverseAgentExchange shImport(@RequestParam("file") MultipartFile multipartFile) {
		return turConverseImportExchange.importFromMultipartFile(multipartFile);
	}
}
