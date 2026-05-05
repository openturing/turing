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

package com.viglet.turing.api.sn.genai;

import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.MediaType;

import com.viglet.turing.api.sn.search.TurSNSiteSearchService;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurChatMessage;
import com.viglet.turing.genai.TurSNGenAi;
import com.viglet.turing.genai.TurSNGenAi.ConversationMessage;
import com.viglet.turing.genai.TurGenAiContext;
import com.viglet.turing.genai.TurGenAiContextFactory;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.persistence.dto.intent.TurIntentDto;
import com.viglet.turing.persistence.mapper.intent.TurIntentMapper;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.repository.intent.TurIntentRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.service.chatmemory.TurChatMemoryService;
import com.viglet.turing.sn.TurSNSearchProcess;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/api/sn/{siteName}/chat")
@Tag(name = "Semantic Navigation with Generative AI", description = "Semantic Navigation with Generative AI API")
public class TurSNSiteGenAiAPI {
	private static final String NOT_ENABLED_MESSAGE = "Language Model is not enabled for this site.";
	private static final String ASSISTANT_ROLE = "assistant";

	private final TurSNSearchProcess turSNSearchProcess;
	private final TurSNGenAi turGenAi;
	private final TurGenAiContextFactory turGenAiContextFactory;
	private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
	private final TurSNSiteSearchService turSNSiteSearchService;
	private final TurIntentRepository turIntentRepository;
	private final TurIntentMapper turIntentMapper;
	private final TurChatFlowEngineService chatFlowEngineService;
	private final TurConfigProperties configProperties;
	private final TurChatMemoryService chatMemoryService;

	public TurSNSiteGenAiAPI(TurSNSearchProcess turSNSearchProcess,
			TurSNGenAi turGenAi,
			TurGenAiContextFactory turGenAiContextFactory,
			TurSNSiteLocaleRepository turSNSiteLocaleRepository,
			TurSNSiteSearchService turSNSiteSearchService,
			TurIntentRepository turIntentRepository,
			TurIntentMapper turIntentMapper,
			TurChatFlowEngineService chatFlowEngineService,
			TurConfigProperties configProperties,
			TurChatMemoryService chatMemoryService) {
		this.turSNSearchProcess = turSNSearchProcess;
		this.turGenAi = turGenAi;
		this.turGenAiContextFactory = turGenAiContextFactory;
		this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
		this.turSNSiteSearchService = turSNSiteSearchService;
		this.turIntentRepository = turIntentRepository;
		this.turIntentMapper = turIntentMapper;
		this.chatFlowEngineService = chatFlowEngineService;
		this.configProperties = configProperties;
		this.chatMemoryService = chatMemoryService;
	}

	/**
	 * Site GenAI is reachable when an enabled agent with RAG enabled and a
	 * complete embedding/store/LLM setup is bound to the site. The check is
	 * config-only — no model is instantiated, so broken credentials do not
	 * hide the AI Mode button.
	 */
	private boolean isAgentRagReady(TurSNSiteGenAi genAi) {
		if (genAi == null) return false;
		TurAIAgent agent = genAi.getTurAIAgent();
		if (agent == null || agent.getEnabled() != 1 || !agent.isRagEnabled()) return false;
		boolean hasLlm = agent.getLlmInstances() != null && !agent.getLlmInstances().isEmpty();
		return hasLlm
				&& agent.getTurEmbeddingModelInstance() != null
				&& agent.getTurStoreInstance() != null;
	}

	private Locale resolveLocale(String requested, String siteName) {
		if (requested == null || requested.isBlank()) {
			return turSNSiteSearchService.resolveDefaultLocale(siteName);
		}
		try {
			return LocaleUtils.toLocale(requested);
		} catch (IllegalArgumentException e) {
			return turSNSiteSearchService.resolveDefaultLocale(siteName);
		}
	}

	@GetMapping
	public TurChatMessage chatMessage(@PathVariable String siteName,
			@RequestParam(name = TurSNParamType.QUERY) String q,
			@RequestParam(required = false, name = TurSNParamType.LOCALE) String localeRequest) {
		log.info("RAG chat (single-turn) received for SN Site '{}': locale='{}', query='{}'",
				siteName, localeRequest, q);
		Locale locale = resolveLocale(localeRequest, siteName);
		if (turSNSearchProcess.existsByTurSNSiteAndLanguage(siteName, locale)) {
			return turSNSearchProcess.getSNSite(siteName).map(site -> {
				var turSNSiteGenAI = site.getTurSNSiteGenAi();
				if (!isAgentRagReady(turSNSiteGenAI)) {
					log.info("RAG chat (single-turn) skipped for SN Site '{}': agent not ready", siteName);
					return TurChatMessage.builder()
							.text(NOT_ENABLED_MESSAGE)
							.build();
				}
				String collectionName = null;
				var siteLocale = turSNSiteLocaleRepository
						.findByTurSNSiteAndLanguage(site, locale);
				if (siteLocale != null) {
					collectionName = siteLocale.getCore();
				}
				log.info("RAG chat (single-turn) building context for SN Site '{}': locale='{}', collection='{}'",
						siteName, locale, collectionName);
				TurGenAiContext turGenAiContext = turGenAiContextFactory
						.build(turSNSiteGenAI, collectionName);
				return turGenAi.assistant(turGenAiContext, q);
			}).orElseGet(() -> {
				log.warn("RAG chat (single-turn) skipped: SN Site '{}' not found", siteName);
				return TurChatMessage.builder().text("Couldn't find site name.").build();
			});
		}
		log.info("RAG chat (single-turn) skipped for SN Site '{}': locale '{}' not available", siteName, locale);
		return TurChatMessage.builder().build();
	}

	/**
	 * Config-only check whether the site has RAG wired up — verifies that the
	 * GenAI record is enabled and that LLM + embedding model + vector store
	 * are selected (either on the site or via global defaults). Does NOT attempt
	 * to instantiate the actual ChatModel/EmbeddingModel/VectorStore, so broken
	 * credentials or unreachable backends won't hide the AI Mode button — those
	 * errors surface on the real chat call instead.
	 *
	 * @since 2026.2.4
	 */
	@GetMapping("/enabled")
	public ChatEnabledResponse isEnabled(@PathVariable String siteName) {
		boolean enabled = turSNSearchProcess.getSNSite(siteName)
				.map(site -> isAgentRagReady(site.getTurSNSiteGenAi()))
				.orElse(false);
		var session = configProperties.getChat().getSession();
		return new ChatEnabledResponse(enabled, session.getCookieName(), session.getTtlSeconds());
	}

	/**
	 * Conversational RAG endpoint with full agent capabilities: streams the
	 * response (SSE) and exposes the agent's native tools + MCP servers to the
	 * LLM, while still injecting the SN site's RAG-retrieved chunks into the
	 * system prompt so answers stay grounded in the indexed content.
	 *
	 * @since 2026.2.4
	 */
	@PostMapping(value = "/conversation",
			produces = MediaType.TEXT_EVENT_STREAM_VALUE,
			consumes = MediaType.APPLICATION_JSON_VALUE)
	public Flux<TurAgentChatExecutor.ChatResponse> conversation(@PathVariable String siteName,
			@RequestBody ConversationRequest request) {
		int historySize = request.messages() == null ? 0 : request.messages().size();
		log.info("RAG chat conversation received for SN Site '{}': locale='{}', historySize={}",
				siteName, request.locale(), historySize);
		Locale locale = resolveLocale(request.locale(), siteName);
		if (!turSNSearchProcess.existsByTurSNSiteAndLanguage(siteName, locale)) {
			log.info("RAG chat skipped for SN Site '{}': locale '{}' not available", siteName, locale);
			return Flux.just(new TurAgentChatExecutor.ChatResponse(ASSISTANT_ROLE, NOT_ENABLED_MESSAGE));
		}
		return turSNSearchProcess.getSNSite(siteName).map(site -> {
			TurSNSiteGenAi turSNSiteGenAI = site.getTurSNSiteGenAi();
			if (!isAgentRagReady(turSNSiteGenAI)) {
				log.info("RAG chat skipped for SN Site '{}': agent not ready", siteName);
				return Flux.just(new TurAgentChatExecutor.ChatResponse(ASSISTANT_ROLE, NOT_ENABLED_MESSAGE));
			}
			TurAIAgent agent = turSNSiteGenAI.getTurAIAgent();
			TurLLMInstance llmInstance = agent.getLlmInstances().stream()
					.filter(llm -> llm.getEnabled() == 1)
					.findFirst()
					.orElseGet(() -> agent.getLlmInstances().iterator().next());
			String collectionName = null;
			var siteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(site, locale);
			if (siteLocale != null) {
				collectionName = siteLocale.getCore();
			}
			log.info("RAG chat building context for SN Site '{}': locale='{}', collection='{}', agent='{}', llm='{}'",
					siteName, locale, collectionName, agent.getTitle(), llmInstance.getTitle());
			TurGenAiContext context = turGenAiContextFactory.build(turSNSiteGenAI, collectionName);
			Flux<TurAgentChatExecutor.ChatResponse> stream = turGenAi.assistantConversationStreaming(
					context, agent, llmInstance, request.messages(), request.filters(),
					request.conversationId(), request.flowId());
			// Capture the turn for chat memory: accumulate the streamed assistant
			// chunks and enqueue once the stream finishes. The service is a no-op
			// when the agent has chatMemoryEnabled=false or the logging engine is
			// disabled, so guarding here would be redundant.
			String latestUserMessage = request.messages() == null ? null : request.messages().stream()
					.filter(m -> "user".equals(m.role()))
					.map(ConversationMessage::content)
					.reduce((first, second) -> second)
					.orElse(null);
			StringBuilder assistantBuffer = new StringBuilder();
			return stream
					.doOnNext(chunk -> {
						if (chunk != null && chunk.content() != null) {
							assistantBuffer.append(chunk.content());
						}
					})
					.doOnComplete(() -> chatMemoryService.recordTurn(
							siteName,
							agent,
							request.conversationId(),
							request.locale(),
							latestUserMessage,
							assistantBuffer.toString()));
		}).orElseGet(() -> {
			log.warn("RAG chat skipped: SN Site '{}' not found", siteName);
			return Flux.just(new TurAgentChatExecutor.ChatResponse(ASSISTANT_ROLE, "Couldn't find site name."));
		});
	}

	/**
	 * Drops every chat-flow runtime state attached to {@code conversationId}
	 * for this site's agent. Public consumers (search "AI Mode") don't know
	 * which flow the auto-router picked, so resetting by flow id would be
	 * awkward — clearing all flows for the conversation is the cleanest way
	 * to wire a "New chat" button on the public UI.
	 *
	 * @return number of flow state rows deleted (0 when the conversation has
	 *         no active flows or the site has no agent).
	 *
	 * @since 2026.2.16
	 */
	@Transactional
	@DeleteMapping("/conversation-state")
	public int resetConversationState(@PathVariable String siteName,
			@RequestParam String conversationId) {
		return turSNSearchProcess.getSNSite(siteName)
				.map(site -> {
					TurSNSiteGenAi genAi = site.getTurSNSiteGenAi();
					if (genAi == null) return 0;
					TurAIAgent agent = genAi.getTurAIAgent();
					if (agent == null) return 0;
					return chatFlowEngineService.resetAllStatesForAgent(conversationId, agent.getId());
				})
				.orElse(0);
	}

	/**
	 * Public, unauthenticated list of enabled intents for the site's AI agent.
	 * Surfaces the curated quick-prompt suggestions that authors set up in the
	 * agent's Intent admin UI, so the search frontend can render them as chips
	 * below the chat input. Returns an empty list when the site has no agent
	 * bound, the agent is disabled, or no intents are enabled.
	 *
	 * @since 2026.2.12
	 */
	@GetMapping("/intents")
	public List<TurIntentDto> intents(@PathVariable String siteName) {
		return turSNSearchProcess.getSNSite(siteName)
				.map(site -> {
					TurSNSiteGenAi genAi = site.getTurSNSiteGenAi();
					if (genAi == null) return null;
					TurAIAgent agent = genAi.getTurAIAgent();
					if (agent == null || agent.getEnabled() != 1) return null;
					return turIntentMapper.toDtoList(
							turIntentRepository
									.findByTurAIAgent_IdAndEnabledOrderBySortOrderAsc(
											agent.getId(), 1));
				})
				.orElseGet(List::of);
	}

	public record ChatEnabledResponse(boolean enabled, String sessionCookieName, long sessionTtlSeconds) {
	}

	public record ConversationRequest(List<ConversationMessage> messages, String locale,
			java.util.Map<String, List<String>> filters,
			String conversationId, String flowId) {
	}
}
