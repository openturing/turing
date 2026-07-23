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
import com.viglet.turing.genai.TurChatProviderErrorMapper;
import com.viglet.turing.genai.TurSNGenAi;
import com.viglet.turing.genai.TurSNGenAiChatRequest;
import com.viglet.turing.genai.TurSNGenAi.ConversationMessage;
import com.viglet.turing.genai.TurGenAiContext;
import com.viglet.turing.genai.TurGenAiContextFactory;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.workspace.TurAgentWorkspace;
import com.viglet.turing.genai.workspace.TurWorkspaceEvent;
import com.viglet.turing.genai.workspace.TurWorkspaceEventBus;
import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDto;
import com.viglet.turing.persistence.dto.intent.TurIntentDto;
import com.viglet.turing.persistence.mapper.intent.TurIntentMapper;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.repository.intent.TurIntentRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.service.chatmemory.TurChatMemoryService;
import com.viglet.turing.service.chatslots.TurChatHandoffService;
import com.viglet.turing.service.chatslots.TurChatHandoffService.Channel;
import com.viglet.turing.service.chatslots.TurChatHandoffService.HandoffRequest;
import com.viglet.turing.service.chatslots.TurChatHandoffService.HandoffResult;
import com.viglet.turing.service.chatslots.TurChatMultiModalSlotService;
import com.viglet.turing.service.chatslots.TurChatMultiModalSlotService.MultiModalSlotResult;
import com.viglet.turing.service.chatslots.TurChatShareOgService;
import com.viglet.turing.service.chatslots.TurChatSlotEventBus;
import com.viglet.turing.service.chatslots.TurChatSlotExtractionService;
import com.viglet.turing.service.chatslots.TurChatSlotExtractionService.SlotExtractionResult;
import com.viglet.turing.sn.TurSNSearchProcess;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/api/sn/{siteName}/chat")
@Tag(name = "Semantic Navigation with Generative AI", description = "Semantic Navigation with Generative AI API")
public class TurSNSiteGenAiAPI {

    // --- S1192: extracted duplicated literals ---
    private static final String SITE = "site '";
    private static final String HEARTBEAT = "heartbeat";

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
	private final TurChatSlotEventBus slotEventBus;
	private final com.viglet.turing.service.chatslots.TurProactiveCopilotService proactiveCopilotService;
	private final com.viglet.turing.service.chatslots.TurChatSlotSseRegistry slotSseRegistry;
	private final TurChatSlotExtractionService slotExtractionService;
	private final TurChatMultiModalSlotService multiModalSlotService;
	private final TurChatHandoffService handoffService;
	private final TurChatShareOgService shareOgService;
	private final TurAgentWorkspace agentWorkspace;
	private final TurWorkspaceEventBus workspaceEventBus;
	private final com.viglet.turing.genai.flow.TurConversationLock conversationLock;
	private final com.viglet.turing.genai.TurDefaultAgentResolver turDefaultAgentResolver;
	private final com.viglet.turing.genai.persona.TurAgentPersonaResolver turAgentPersonaResolver;
	private final com.viglet.turing.service.llm.budget.TurChatCostBudgetGate costBudgetGate;
	private final com.viglet.turing.properties.TurAbuseControlProperty abuseControlProperty;
	// T790 / §LIV.1 (Block BF) — resolves whether the vectorless copilot can answer
	// (a default LLM is configured) for the VECTORLESS_STRUCTURED readiness path.
	private final com.viglet.turing.genai.catalog.TurCatalogCopilotService catalogCopilotService;

	public TurSNSiteGenAiAPI(TurSNSearchProcess turSNSearchProcess,
			TurSNGenAi turGenAi,
			TurGenAiContextFactory turGenAiContextFactory,
			TurSNSiteLocaleRepository turSNSiteLocaleRepository,
			TurSNSiteSearchService turSNSiteSearchService,
			TurIntentRepository turIntentRepository,
			TurIntentMapper turIntentMapper,
			TurChatFlowEngineService chatFlowEngineService,
			TurConfigProperties configProperties,
			TurChatMemoryService chatMemoryService,
			TurChatSlotEventBus slotEventBus,
			com.viglet.turing.service.chatslots.TurProactiveCopilotService proactiveCopilotService,
			com.viglet.turing.service.chatslots.TurChatSlotSseRegistry slotSseRegistry,
			TurChatSlotExtractionService slotExtractionService,
			TurChatMultiModalSlotService multiModalSlotService,
			TurChatHandoffService handoffService,
			TurChatShareOgService shareOgService,
			TurAgentWorkspace agentWorkspace,
			TurWorkspaceEventBus workspaceEventBus,
			com.viglet.turing.genai.flow.TurConversationLock conversationLock,
			com.viglet.turing.genai.TurDefaultAgentResolver turDefaultAgentResolver,
			com.viglet.turing.genai.persona.TurAgentPersonaResolver turAgentPersonaResolver,
			com.viglet.turing.service.llm.budget.TurChatCostBudgetGate costBudgetGate,
			com.viglet.turing.properties.TurAbuseControlProperty abuseControlProperty,
			com.viglet.turing.genai.catalog.TurCatalogCopilotService catalogCopilotService) {
		this.turSNSearchProcess = turSNSearchProcess;
		this.turGenAi = turGenAi;
		this.turGenAiContextFactory = turGenAiContextFactory;
		this.turDefaultAgentResolver = turDefaultAgentResolver;
		this.turAgentPersonaResolver = turAgentPersonaResolver;
		this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
		this.turSNSiteSearchService = turSNSiteSearchService;
		this.turIntentRepository = turIntentRepository;
		this.turIntentMapper = turIntentMapper;
		this.chatFlowEngineService = chatFlowEngineService;
		this.configProperties = configProperties;
		this.chatMemoryService = chatMemoryService;
		this.slotEventBus = slotEventBus;
		this.proactiveCopilotService = proactiveCopilotService;
		this.slotSseRegistry = slotSseRegistry;
		this.slotExtractionService = slotExtractionService;
		this.multiModalSlotService = multiModalSlotService;
		this.handoffService = handoffService;
		this.shareOgService = shareOgService;
		this.agentWorkspace = agentWorkspace;
		this.workspaceEventBus = workspaceEventBus;
		this.conversationLock = conversationLock;
		this.costBudgetGate = costBudgetGate;
		this.abuseControlProperty = abuseControlProperty;
		this.catalogCopilotService = catalogCopilotService;
	}

	// T648 / §XXXVII.10 — reject an over-cap anonymous upload BEFORE Tika/vision.
	private void assertUploadWithinCap(org.springframework.web.multipart.MultipartFile file) {
		long cap = abuseControlProperty.getChat().getMaxUploadBytes();
		if (cap > 0 && file != null && file.getSize() > cap) {
			throw new IllegalArgumentException(
					"Uploaded file exceeds the maximum allowed size of " + cap + " bytes.");
		}
	}

	// T648 / §XXXVII.10 — bound anonymous conversation message count + length.
	private void assertMessagesWithinCaps(List<ConversationMessage> messages) {
		if (messages == null) {
			return;
		}
		var chat = abuseControlProperty.getChat();
		if (chat.getMaxMessagesPerTurn() > 0 && messages.size() > chat.getMaxMessagesPerTurn()) {
			throw new IllegalArgumentException(
					"Too many messages in one turn (max " + chat.getMaxMessagesPerTurn() + ").");
		}
		if (chat.getMaxMessageChars() > 0) {
			for (ConversationMessage m : messages) {
				if (m != null && m.content() != null && m.content().length() > chat.getMaxMessageChars()) {
					throw new IllegalArgumentException(
							"A chat message exceeds the maximum length of " + chat.getMaxMessageChars()
									+ " characters.");
				}
			}
		}
	}

	/**
	 * Reasons a site's chat may be disabled. The frontend uses this code to
	 * show a helpful admin-facing tooltip explaining what to configure.
	 *
	 * @since 2026.2.17
	 */
	public enum ChatDisabledReason {
		/** Chat is fully wired up — the AI Mode button should render. */
		NONE,
		/** The SN site has no {@code TurSNSiteGenAi} record bound. */
		NO_GENAI,
		/** {@code TurSNSiteGenAi} exists but no AI agent is linked. */
		NO_AGENT,
		/** Linked agent has {@code enabled = 0}. */
		AGENT_DISABLED,
		/** Linked agent has {@code ragEnabled = false}. */
		RAG_DISABLED,
		/** Linked agent has no LLM instance. */
		MISSING_LLM,
		/** Linked agent has no embedding model instance. */
		MISSING_EMBEDDING,
		/** Linked agent has no vector store instance. */
		MISSING_STORE,
		/**
		 * T790 / §LIV.1 (Block BF) — the site runs in {@code VECTORLESS_STRUCTURED}
		 * mode (the catalog copilot, which needs neither an embedding model nor a
		 * vector store) but no usable <em>default LLM</em> is configured, so the
		 * copilot cannot answer. The vectorless readiness path reports this instead
		 * of {@code MISSING_EMBEDDING}/{@code MISSING_STORE}.
		 */
		MISSING_DEFAULT_LLM
	}

	/**
	 * Site GenAI is reachable when an enabled agent with RAG enabled and a
	 * complete embedding/store/LLM setup is bound to the site. The site itself
	 * has no {@code ragEnabled} flag — it inherits everything from the linked
	 * {@link TurAIAgent}. The check is config-only — no model is instantiated,
	 * so broken credentials do not hide the AI Mode button.
	 */
	private ChatDisabledReason diagnoseAgentReadiness(TurSNSiteGenAi genAi) {
		// T790 / §LIV.1 (Block BF) — Vectorless (Structured-Data) RAG: a site in
		// VECTORLESS_STRUCTURED mode answers through the T392 catalog copilot, which
		// grounds the default LLM on a NL→DSL search over the declared field schema
		// (no embeddings). It needs neither an embedding model nor a vector store —
		// so readiness is "a usable default LLM is configured", not the classic
		// MISSING_EMBEDDING/MISSING_STORE walk. HYBRID keeps the full vector walk
		// below (it is a superset that also runs the copilot).
		var mode = genAi != null ? genAi.getKnowledgeBaseMode()
				: com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode.VECTOR;
		if (mode == com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode.VECTORLESS_STRUCTURED) {
			return catalogCopilotService.isAvailable()
					? ChatDisabledReason.NONE
					: ChatDisabledReason.MISSING_DEFAULT_LLM;
		}
		// T622 — a site with no agent of its own resolves to the global Default
		// AI Agent when one is set (fail-open); otherwise the classic reasons.
		TurAIAgent agent = turDefaultAgentResolver.resolveEffectiveAgent(genAi);
		if (agent == null) return genAi == null ? ChatDisabledReason.NO_GENAI : ChatDisabledReason.NO_AGENT;
		if (agent.getEnabled() != 1) return ChatDisabledReason.AGENT_DISABLED;
		if (!agent.isRagEnabled()) return ChatDisabledReason.RAG_DISABLED;
		boolean hasLlm = agent.getLlmInstances() != null && !agent.getLlmInstances().isEmpty();
		if (!hasLlm) return ChatDisabledReason.MISSING_LLM;
		if (agent.getTurEmbeddingModelInstance() == null) return ChatDisabledReason.MISSING_EMBEDDING;
		if (agent.getTurStoreInstance() == null) return ChatDisabledReason.MISSING_STORE;
		return ChatDisabledReason.NONE;
	}

	private boolean isAgentRagReady(TurSNSiteGenAi genAi) {
		return diagnoseAgentReadiness(genAi) == ChatDisabledReason.NONE;
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

	/**
	 * T707 — resolves a locale that actually has an index for this site. A
	 * client that requests a locale the site was never indexed in (e.g. {@code en}
	 * when only {@code en-US} exists) previously got the misleading
	 * "Language Model is not enabled for this site." Now we fall back to the
	 * site's default indexed locale so chat answers from the content that exists,
	 * instead of refusing. Returns the requested locale unchanged when it is
	 * indexed, or when no indexed fallback is available (the caller still guards).
	 */
	private Locale resolveIndexedLocale(String requested, String siteName) {
		Locale locale = resolveLocale(requested, siteName);
		if (locale != null && turSNSearchProcess.existsByTurSNSiteAndLanguage(siteName, locale)) {
			return locale;
		}
		Locale fallback = turSNSiteSearchService.resolveDefaultLocale(siteName);
		if (fallback != null && !fallback.equals(locale)
				&& turSNSearchProcess.existsByTurSNSiteAndLanguage(siteName, fallback)) {
			log.debug("RAG chat: requested locale '{}' not indexed for SN Site '{}', falling back to '{}'",
					requested, siteName, fallback);
			return fallback;
		}
		return locale;
	}

	@GetMapping
	public TurChatMessage chatMessage(@PathVariable String siteName,
			@RequestParam(name = TurSNParamType.QUERY) String q,
			@RequestParam(required = false, name = TurSNParamType.LOCALE) String localeRequest) {
		log.debug("RAG chat (single-turn) received for SN Site '{}': locale='{}', query='{}'",
				siteName, localeRequest, q);
		Locale locale = resolveIndexedLocale(localeRequest, siteName);
		if (turSNSearchProcess.existsByTurSNSiteAndLanguage(siteName, locale)) {
			return turSNSearchProcess.getSNSite(siteName).map(site -> {
				var turSNSiteGenAI = site.getTurSNSiteGenAi();
				if (!isAgentRagReady(turSNSiteGenAI)) {
					log.debug("RAG chat (single-turn) skipped for SN Site '{}': agent not ready", siteName);
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
				log.debug("RAG chat (single-turn) building context for SN Site '{}': locale='{}', collection='{}'",
						siteName, locale, collectionName);
				TurGenAiContext turGenAiContext = turGenAiContextFactory
						.build(turSNSiteGenAI, collectionName);
				return turGenAi.assistant(turGenAiContext, q);
			}).orElseGet(() -> {
				log.warn("RAG chat (single-turn) skipped: SN Site '{}' not found", siteName);
				return TurChatMessage.builder().text("Couldn't find site name.").build();
			});
		}
		log.debug("RAG chat (single-turn) skipped for SN Site '{}': locale '{}' not available", siteName, locale);
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
		ChatDisabledReason reason = turSNSearchProcess.getSNSite(siteName)
				.map(site -> diagnoseAgentReadiness(site.getTurSNSiteGenAi()))
				.orElse(ChatDisabledReason.NO_GENAI);
		var session = configProperties.getChat().getSession();
		return new ChatEnabledResponse(
				reason == ChatDisabledReason.NONE,
				reason.name(),
				session.getCookieName(),
				session.getTtlSeconds());
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
		log.debug("RAG chat conversation received for SN Site '{}': locale='{}', historySize={}",
				siteName, request.locale(), historySize);
		// T648 / §XXXVII.10 — bound anonymous message count + length before any work.
		assertMessagesWithinCaps(request.messages());
		// T707 — fall back to the site's default indexed locale when the client
		// requests one that was never indexed, instead of refusing the turn.
		Locale locale = resolveIndexedLocale(request.locale(), siteName);
		// Defer all heavy work (RAG retrieval + prompt build + provider calls) to
		// subscription time so the SSE handshake (200 text/event-stream) is
		// committed before any upstream call fires. onErrorResume then turns an
		// upstream failure (unreachable embedding model / LLM) into a readable
		// in-stream assistant message instead of a 503 with a JSON ProblemDetail
		// body the SSE client can't consume — mirroring TurAIAgentChatAPI /
		// TurPersonaChatAPI, which already map streaming failures this way.
		return Flux.defer(() -> buildConversationStream(siteName, request, locale))
				.onErrorResume(err -> {
					log.error("[SNChat] Conversation stream for SN Site '{}' failed: {}",
							siteName, err.getMessage(), err);
					return Flux.just(new TurAgentChatExecutor.ChatResponse(ASSISTANT_ROLE,
							TurChatProviderErrorMapper.toUserMessage(err, locale), "token"));
				});
	}

	private Flux<TurAgentChatExecutor.ChatResponse> buildConversationStream(String siteName,
			ConversationRequest request, Locale locale) {
		// T641 / §XXXVII.3 — hard cost kill-switch: refuse the anonymous turn
		// before any embedding/LLM call fires once the configured month-to-date
		// spend ceiling is reached. Off by default (cap <= 0).
		if (costBudgetGate.isAnonymousChatHardCapExceeded()) {
			log.warn("[SNChat] Refusing conversation for SN Site '{}' — anonymous-chat hard cost cap reached",
					siteName);
			return Flux.just(new TurAgentChatExecutor.ChatResponse(ASSISTANT_ROLE,
					"The assistant is temporarily unavailable. Please try again later."));
		}
		if (!turSNSearchProcess.existsByTurSNSiteAndLanguage(siteName, locale)) {
			log.debug("RAG chat skipped for SN Site '{}': locale '{}' not available", siteName, locale);
			return Flux.just(new TurAgentChatExecutor.ChatResponse(ASSISTANT_ROLE, NOT_ENABLED_MESSAGE));
		}
		return turSNSearchProcess.getSNSite(siteName).map(site -> {
			TurSNSiteGenAi turSNSiteGenAI = site.getTurSNSiteGenAi();
			if (!isAgentRagReady(turSNSiteGenAI)) {
				log.debug("RAG chat skipped for SN Site '{}': agent not ready", siteName);
				return Flux.just(new TurAgentChatExecutor.ChatResponse(ASSISTANT_ROLE, NOT_ENABLED_MESSAGE));
			}
			TurAIAgent agent = turDefaultAgentResolver.resolveEffectiveAgent(turSNSiteGenAI);
			TurLLMInstance llmInstance = agent.getLlmInstances().stream()
					.filter(llm -> llm.getEnabled() == 1)
					.findFirst()
					.orElseGet(() -> agent.getLlmInstances().iterator().next());
			String collectionName = null;
			var siteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(site, locale);
			if (siteLocale != null) {
				collectionName = siteLocale.getCore();
			}
			log.debug("RAG chat building context for SN Site '{}': locale='{}', collection='{}', agent='{}', llm='{}'",
					siteName, locale, collectionName, agent.getTitle(), llmInstance.getTitle());
			TurGenAiContext context = turGenAiContextFactory.build(turSNSiteGenAI, collectionName);
			Flux<TurAgentChatExecutor.ChatResponse> stream = turGenAi.assistantConversationStreaming(
					context, agent, llmInstance,
					new TurSNGenAiChatRequest(request.messages(), request.filters(),
							request.conversationId(), request.flowId(), request.forcedVariant(), locale,
							request.personaId()));
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
						// Only accumulate assistant text into chat memory — skip the
						// structured side-channel events (T292/T327 "sources",
						// "options", "form") so their JSON never pollutes the
						// recorded answer.
						if (chunk != null && chunk.content() != null && isAssistantText(chunk)) {
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
	 * A streamed chunk is assistant text (to record in chat memory) when its
	 * {@code type} is the default {@code "token"} or unset; structured
	 * side-channel events ({@code "sources"} / {@code "options"} / {@code "form"})
	 * are not.
	 */
	private static boolean isAssistantText(TurAgentChatExecutor.ChatResponse chunk) {
		String type = chunk.type();
		return type == null || "token".equals(type);
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
	 * Public, unauthenticated read of the slots captured during the current
	 * chat session, returned as a single flat map at the JSON root. Lets
	 * embedding sites pick up slot values the visitor has already filled
	 * through the chat (e.g. preferred locale, course of interest, name)
	 * and reflect them elsewhere on the page without re-asking. The
	 * {@code conversationId} comes from the SDK's {@code TUR_SESSION}
	 * cookie — same id the chat-flow engine keys on.
	 *
	 * @since 2026.2.7
	 */
	@GetMapping("/slots")
	public TurChatSessionSlotsDto chatSlots(@PathVariable String siteName,
			@RequestParam String conversationId) {
		return chatFlowEngineService.listSlotsForConversation(conversationId);
	}

	/**
	 * Snapshot of the conversation's active flow state — flow identity,
	 * current cursor node, guardrail method, A/B experiment context.
	 * Lightweight read, no LLM round-trip — used by debug surfaces like
	 * {@code SlotInspector} and by app-level logic that branches on
	 * variant (e.g. show different layout for Marina vs Lucas without
	 * a separate slot for that).
	 *
	 * <p>Returns a DTO with all-null fields when the conversation has
	 * no active state yet (visitor mid-typing first message).
	 *
	 * @since 2026.2.7
	 */
	@GetMapping("/state")
	public com.viglet.turing.genai.flow.TurChatFlowEngineService.ConversationStateDto chatState(
			@PathVariable String siteName,
			@RequestParam String conversationId) {
		return chatFlowEngineService.getConversationState(conversationId);
	}

	/**
	 * T121 / §IX.6.a — resume a conversation parked at a {@code suspend}
	 * node. Optionally carries {@code slotUpdates} (a webhook payload, an
	 * approval decision, the result of a scheduled job) which are applied
	 * BEFORE the engine walks past the suspend node so downstream
	 * condition / switch nodes branch on the freshest values.
	 *
	 * @since 2026.3.1
	 */
	@PostMapping("/chat/resume")
	public ChatResumeResponse resumeChat(@PathVariable String siteName,
			@RequestBody ChatResumeRequest body) {
		if (body == null || body.conversationId() == null || body.conversationId().isBlank()) {
			return new ChatResumeResponse(0, false, "conversationId is required");
		}
		TurChatFlowEngineService.ResumeResult result =
				conversationLock.runExclusive(body.conversationId(),
						() -> chatFlowEngineService.resumeSuspendedFlow(body.conversationId(),
								body.slotUpdates(), body.resumeReason()));
		return new ChatResumeResponse(result.resumed(), !result.nothingToResume(), null);
	}

	public record ChatResumeRequest(String conversationId,
			java.util.Map<String, String> slotUpdates, String resumeReason) {
	}

	public record ChatResumeResponse(int resumed, boolean wasParked, String error) {
	}

	@PostMapping("/slots")
	public ChatSlotWriteResponse writeChatSlot(@PathVariable String siteName,
			@RequestBody ChatSlotWriteRequest body) {
		if (body == null) {
			return new ChatSlotWriteResponse(0);
		}
		// Serialize against the conversation's chat turn so this optimistic
		// write cannot lost-update the cursor mid-advance (see TurConversationLock).
		log.debug("[ConvLock] slot-write endpoint conv='{}' name='{}'",
				body.conversationId(), body.name());
		int touched = conversationLock.runExclusive(body.conversationId(), () ->
				chatFlowEngineService.writeSlot(
						body.conversationId(), body.name(), body.value(),
						com.viglet.turing.persistence.model.agent.TurChatSlotAuditSource.ENDPOINT,
						"site=" + siteName));
		return new ChatSlotWriteResponse(touched);
	}

	public record ChatSlotWriteRequest(String conversationId, String name, String value) {
	}

	public record ChatSlotWriteResponse(int updatedStates) {
	}

	/**
	 * T107 / §VII.13.d — submit a native multi-field {@code formCapture} form.
	 * The SDK collects every field of the {@code "form"} SSE event into one
	 * payload and POSTs it here; each {@code values} entry is written to its
	 * named slot and the engine walks the conversation past the now-satisfied
	 * form node, so the next chat turn renders the following node instead of
	 * re-asking the visitor field-by-field.
	 *
	 * <p>{@code conversationId} must match the live {@code TUR_SESSION} cookie.
	 * {@code nodeId} is optional metadata (the form node's id) carried for
	 * audit/debug; the engine resolves the satisfied form node from the live
	 * cursor, not from this field.
	 *
	 * @since 2026.3.1
	 */
	@PostMapping("/form-submit")
	public ChatFormSubmitResponse submitChatForm(@PathVariable String siteName,
			@RequestBody ChatFormSubmitRequest body) {
		if (body == null || body.conversationId() == null || body.conversationId().isBlank()) {
			return new ChatFormSubmitResponse(false, 0, 0, null, "conversationId is required");
		}
		TurChatFlowEngineService.FormSubmitResult result =
				conversationLock.runExclusive(body.conversationId(),
						() -> chatFlowEngineService.submitForm(body.conversationId(), body.values()));
		return new ChatFormSubmitResponse(true, result.fieldsWritten(),
				result.advancedStates(), result.currentNodeId(), null);
	}

	public record ChatFormSubmitRequest(String conversationId, String nodeId,
			java.util.Map<String, String> values) {
	}

	public record ChatFormSubmitResponse(boolean success, int fieldsWritten,
			int advancedStates, String currentNodeId, String error) {
	}

	/**
	 * T92 / §VII.11.b — pin a specific chat flow on the conversation,
	 * overriding the LLM router for every subsequent turn. The portal hits
	 * this once on page load (typically wiring a deep-link param like
	 * {@code ?flow=in-company} into the {@code flow} field) and the SDK keeps
	 * chatting without ever threading a {@code flowId} through the chat call.
	 *
	 * <p>{@code flow} can be either the flow UUID (when known to the caller)
	 * or the flow name (case-insensitive) — names are convenient in URLs.
	 * Slots captured before the pin are preserved on the new flow's state,
	 * so a visitor pinned mid-conversation does not have to re-type values
	 * already filled. The receiving flow's satisfied-questions walker
	 * auto-skips any aiQuestion node whose {@code outputVariable} is already
	 * in the seeded map (matches T93's cross-flow slot inheritance).
	 *
	 * @since 2026.3.1
	 */
	@Transactional
	@PostMapping("/flow-select")
	public ChatFlowSelectResponse selectChatFlow(@PathVariable String siteName,
			@RequestBody ChatFlowSelectRequest body) {
		if (body == null) {
			return new ChatFlowSelectResponse(false, null, null, "body is required");
		}
		return turSNSearchProcess.getSNSite(siteName)
				.map(site -> {
					TurSNSiteGenAi genAi = site.getTurSNSiteGenAi();
					if (genAi == null) {
						return new ChatFlowSelectResponse(false, null, null,
								SITE + siteName + "' has no GenAI agent configured");
					}
					TurAIAgent agent = turDefaultAgentResolver.resolveEffectiveAgent(genAi);
					if (agent == null) {
						return new ChatFlowSelectResponse(false, null, null,
								SITE + siteName + "' has no GenAI agent configured");
					}
					// T633 — carry an anonymous per-request persona into the
					// pinned flow, validated against the effective agent's
					// catalog (unknown → null, i.e. no persona seeded).
					String personaId = turAgentPersonaResolver.validateCatalogPersonaId(
							agent, body.personaId());
					TurChatFlowEngineService.PinResult pin = conversationLock.runExclusive(
							body.conversationId(),
							() -> chatFlowEngineService.pinFlowForConversation(
									agent, body.conversationId(), body.flow(), personaId));
					return new ChatFlowSelectResponse(pin.success(), pin.pinnedFlowId(),
							pin.pinnedFlowName(), pin.reason());
				})
				.orElseGet(() -> new ChatFlowSelectResponse(false, null, null,
						SITE + siteName + "' not found"));
	}

	/**
	 * Pin request: {@code conversationId} is the live {@code TUR_SESSION}
	 * value; {@code flow} is either the flow UUID or its name (case-
	 * insensitive). Either id or name resolves — the deep-link convenience
	 * path uses the name.
	 */
	/**
	 * Pin request. T633 adds the optional {@code personaId} — a per-request
	 * persona seeded into the freshly-pinned flow (validated against the
	 * effective agent's catalog; unknown/blank → none), so the anonymous
	 * demo's chosen persona survives the flow selection.
	 */
	public record ChatFlowSelectRequest(String conversationId, String flow, String personaId) {
	}

	public record ChatFlowSelectResponse(boolean success, String pinnedFlowId,
			String pinnedFlowName, String reason) {
	}

	/**
	 * Server-sent stream of slot updates for the given conversation. Pushes
	 * the full merged slot map every time any of the three write paths
	 * mutates a chat-flow state: the {@code slot} node executor, the
	 * {@code slots.set(...)} helper inside a Custom Tool Groovy script, and
	 * the {@code POST /chat/slots} writeSlot endpoint.
	 *
	 * <p>The stream opens with one synchronous snapshot read from the
	 * persistence layer (no waiting for the next mutation), so a client that
	 * connects mid-conversation immediately sees the current state. After
	 * that, every event corresponds to a real write — plus a comment-only
	 * heartbeat every 25s to keep idle proxies (nginx default
	 * {@code proxy_read_timeout=60s}, corporate firewalls) from severing
	 * the TCP connection.
	 *
	 * <p>Heartbeats are SSE comments ({@code : heartbeat}) — the EventSource
	 * spec mandates they're silently discarded by the browser, so subscribers
	 * see them as a no-op. No need for a {@code transport: "sse"} client
	 * change to handle them.
	 *
	 * <p>Single-node only — see {@link com.viglet.turing.service.chatslots.TurChatSlotEventBus}
	 * for the cluster caveat. SDKs that need cluster-correctness should keep
	 * polling as a fallback.
	 *
	 * @since 2026.2.7
	 */
	@GetMapping(value = "/slots/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<org.springframework.http.codec.ServerSentEvent<TurChatSessionSlotsDto>> streamSlots(
			@PathVariable String siteName,
			@RequestParam String conversationId) {
		if (conversationId == null || conversationId.isBlank()) {
			return Flux.empty();
		}
		TurChatSessionSlotsDto initial = chatFlowEngineService.listSlotsForConversation(conversationId);
		Flux<org.springframework.http.codec.ServerSentEvent<TurChatSessionSlotsDto>> data =
				Flux.concat(Flux.just(initial), slotEventBus.subscribe(conversationId))
						.map(snapshot -> org.springframework.http.codec.ServerSentEvent
								.<TurChatSessionSlotsDto>builder(snapshot).build());
		Flux<org.springframework.http.codec.ServerSentEvent<TurChatSessionSlotsDto>> heartbeats =
				Flux.interval(java.time.Duration.ofSeconds(25))
						.map(tick -> org.springframework.http.codec.ServerSentEvent
								.<TurChatSessionSlotsDto>builder().comment(HEARTBEAT).build());
		// T90 — refcount this connection on the SSE channel registry so the
		// admin debug surface can report open channels. doFinally fires on
		// complete / error / cancel (cancel being the usual SSE disconnect).
		return Flux.merge(data, heartbeats)
				.doOnSubscribe(s -> slotSseRegistry.acquire(conversationId,
						com.viglet.turing.service.chatslots.TurChatSlotSseRegistry.Mode.SNAPSHOT))
				.doFinally(sig -> slotSseRegistry.release(conversationId,
						com.viglet.turing.service.chatslots.TurChatSlotSseRegistry.Mode.SNAPSHOT));
	}

	/**
	 * T63 / §VII.6.d — delta variant of {@link #streamSlots}. Emits an initial
	 * full snapshot ({@code snapshot:true}, whole map in {@code added}) then one
	 * incremental {@code {added, updated, removed}} delta per slot write —
	 * ~10× fewer bytes than re-sending the full map on conversations with many
	 * slots. Clients reconstruct the running map by applying each delta (the
	 * SDK's SSE multiplexer does this transparently and still surfaces full
	 * snapshots to consumers).
	 *
	 * <p>Opt-in + non-breaking: the snapshot stream at {@code /slots/stream}
	 * stays untouched for existing subscribers. Same 25s comment heartbeat and
	 * single-node caveat.
	 *
	 * @since 2026.3.1
	 */
	@GetMapping(value = "/slots/stream/delta", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<org.springframework.http.codec.ServerSentEvent<com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDeltaDto>> streamSlotDeltas(
			@PathVariable String siteName,
			@RequestParam String conversationId) {
		if (conversationId == null || conversationId.isBlank()) {
			return Flux.empty();
		}
		java.util.Map<String, String> initial =
				chatFlowEngineService.listSlotsForConversation(conversationId).slots();
		Flux<org.springframework.http.codec.ServerSentEvent<com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDeltaDto>> data =
				slotEventBus.subscribeDelta(conversationId, initial)
						.map(delta -> org.springframework.http.codec.ServerSentEvent
								.<com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDeltaDto>builder(delta).build());
		Flux<org.springframework.http.codec.ServerSentEvent<com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDeltaDto>> heartbeats =
				Flux.interval(java.time.Duration.ofSeconds(25))
						.map(tick -> org.springframework.http.codec.ServerSentEvent
								.<com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDeltaDto>builder().comment(HEARTBEAT).build());
		// T90 — same refcounting as the snapshot stream, on the DELTA channel.
		return Flux.merge(data, heartbeats)
				.doOnSubscribe(s -> slotSseRegistry.acquire(conversationId,
						com.viglet.turing.service.chatslots.TurChatSlotSseRegistry.Mode.DELTA))
				.doFinally(sig -> slotSseRegistry.release(conversationId,
						com.viglet.turing.service.chatslots.TurChatSlotSseRegistry.Mode.DELTA));
	}

	/**
	 * T445 / §XXIII.4 — ambient / proactive copilot stream. Opt-in per agent
	 * ({@code proactiveEnabled}); when off the stream is empty. Subscribes to the
	 * conversation's slot bus and runs each post-write slot map through
	 * {@link com.viglet.turing.service.chatslots.TurProactiveCopilotService}: when
	 * an ambient signal ({@code signal.<kind>} slot) crosses the agent's threshold
	 * it pushes ONE throttled proactive offer. The host writes those signal slots
	 * from real interactions (clicks/dwell/opened facets) via the existing
	 * {@code /chat/slots} endpoint. Same 25s heartbeat + single-node caveat as the
	 * slot streams.
	 *
	 * @since 2026.3.4
	 */
	@GetMapping(value = "/proactive/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<org.springframework.http.codec.ServerSentEvent<com.viglet.turing.service.chatslots.TurProactiveSuggestionDto>> streamProactive(
			@PathVariable String siteName,
			@RequestParam String conversationId) {
		if (conversationId == null || conversationId.isBlank()) {
			return Flux.empty();
		}
		TurAIAgent agent = turSNSearchProcess.getSNSite(siteName)
				.map(site -> site.getTurSNSiteGenAi())
				.map(genAi -> genAi == null ? null : genAi.getTurAIAgent())
				.orElse(null);
		if (agent == null || !agent.isProactiveEnabled()) {
			// Not opted in — nothing to stream (the client closes/ignores it).
			return Flux.empty();
		}
		final int threshold = agent.getProactiveThresholdSignals();
		final long throttleMillis =
				com.viglet.turing.service.chatslots.TurProactiveCopilotService.DEFAULT_THROTTLE_MILLIS;
		final var state = proactiveCopilotService.newState();
		TurChatSessionSlotsDto initial = chatFlowEngineService.listSlotsForConversation(conversationId);
		Flux<org.springframework.http.codec.ServerSentEvent<com.viglet.turing.service.chatslots.TurProactiveSuggestionDto>> data =
				Flux.concat(Flux.just(initial), slotEventBus.subscribe(conversationId))
						.flatMap(snapshot -> proactiveCopilotService.evaluate(state, conversationId,
								snapshot.slots(), threshold, throttleMillis, System.currentTimeMillis())
								.map(Flux::just).orElseGet(Flux::empty))
						.map(suggestion -> org.springframework.http.codec.ServerSentEvent
								.<com.viglet.turing.service.chatslots.TurProactiveSuggestionDto>builder(suggestion)
								.event("proactive-suggestion").build());
		Flux<org.springframework.http.codec.ServerSentEvent<com.viglet.turing.service.chatslots.TurProactiveSuggestionDto>> heartbeats =
				Flux.interval(java.time.Duration.ofSeconds(25))
						.map(tick -> org.springframework.http.codec.ServerSentEvent
								.<com.viglet.turing.service.chatslots.TurProactiveSuggestionDto>builder().comment(HEARTBEAT).build());
		return Flux.merge(data, heartbeats);
	}

	/**
	 * T113 / §IX.3.c — workspace artifact stream. The exact analogue of
	 * {@link #streamSlots} for the per-conversation blob store (T111): emits one
	 * server-sent event per workspace mutation so a portal can render the live
	 * "files this agent built for you" sidebar.
	 *
	 * <p>The first events are an initial <b>snapshot</b> — one {@code put} per
	 * artifact already present — so a client that connects mid-conversation sees
	 * the existing files immediately. The snapshot requires {@code agentId} (the
	 * blob store is scoped by agent + conversation); omit it and the stream still
	 * delivers live mutations, just without the back-fill. After the snapshot the
	 * stream relays every {@code put}/{@code delete} from
	 * {@link com.viglet.turing.genai.workspace.TurWorkspaceEventBus}.
	 *
	 * <p>Each event carries <b>metadata only</b>
	 * ({@code {event, key, contentType, size, signedUrl}}) — never the bytes, so
	 * a large blob never floods every connected client (the deliberate contrast
	 * with the slot bus, which re-pushes the whole value map). A consumer follows
	 * {@code signedUrl} to download the artifact.
	 *
	 * <p>Same 25s comment heartbeat, T90 refcounting (on the {@code WORKSPACE}
	 * channel), and single-node caveat as the slot streams.
	 *
	 * @since 2026.3.1
	 */
	@GetMapping(value = "/workspace/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<org.springframework.http.codec.ServerSentEvent<TurWorkspaceEvent>> streamWorkspace(
			@PathVariable String siteName,
			@RequestParam String conversationId,
			@RequestParam(required = false) String agentId) {
		if (conversationId == null || conversationId.isBlank()) {
			return Flux.empty();
		}
		Flux<TurWorkspaceEvent> initial = (agentId == null || agentId.isBlank())
				? Flux.empty()
				: Flux.fromIterable(agentWorkspace.list(agentId, conversationId, null))
						.map(entry -> TurWorkspaceEvent.put(conversationId, entry.key(),
								entry.contentType(), entry.sizeBytes(), entry.signedUrl()));
		Flux<org.springframework.http.codec.ServerSentEvent<TurWorkspaceEvent>> data =
				Flux.concat(initial, workspaceEventBus.subscribe(conversationId))
						.map(event -> org.springframework.http.codec.ServerSentEvent
								.<TurWorkspaceEvent>builder(event).build());
		Flux<org.springframework.http.codec.ServerSentEvent<TurWorkspaceEvent>> heartbeats =
				Flux.interval(java.time.Duration.ofSeconds(25))
						.map(tick -> org.springframework.http.codec.ServerSentEvent
								.<TurWorkspaceEvent>builder().comment(HEARTBEAT).build());
		// T90 — refcount this connection on the WORKSPACE channel so the admin
		// debug surface reports it alongside the slot channels.
		return Flux.merge(data, heartbeats)
				.doOnSubscribe(s -> slotSseRegistry.acquire(conversationId,
						com.viglet.turing.service.chatslots.TurChatSlotSseRegistry.Mode.WORKSPACE))
				.doFinally(sig -> slotSseRegistry.release(conversationId,
						com.viglet.turing.service.chatslots.TurChatSlotSseRegistry.Mode.WORKSPACE));
	}

	/**
	 * Document-to-slots extraction. The caller uploads any Tika-parseable
	 * file (PDF / DOCX / TXT / RTF / HTML) plus the current conversation
	 * id; the service extracts text via Apache Tika (same path the RAG
	 * asset pipeline uses), asks the agent's LLM to map the document onto
	 * the requested slots, and writes the results via the chat-flow engine
	 * — so the React UI sees the fills land in &lt;100 ms through the SSE
	 * channel.
	 *
	 * <p>Generic by design: caller passes the slot names it wants filled
	 * (comma-separated in {@code slotNames}) or leaves it empty to target
	 * the agent's full slot catalog. Use cases include CV/resume upload
	 * to skip the manual question flow, invoice upload to auto-fill
	 * billing fields, form upload to extract structured data, etc.
	 *
	 * <p>The endpoint is unauthenticated — same posture as the rest of
	 * {@code /api/sn/{site}/chat/*} — so deployments that need access
	 * control should layer it at the reverse proxy.
	 *
	 * @since 2026.2.7
	 */
	@PostMapping(value = "/slot-extract", consumes = "multipart/form-data")
	public ChatSlotExtractResponse extractSlots(@PathVariable String siteName,
			@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
			@RequestParam("conversationId") String conversationId,
			@RequestParam(value = "slotNames", required = false) String slotNames,
			@RequestParam(value = "confidence", required = false, defaultValue = "false") boolean confidence,
			@RequestParam(value = "nativeBinary", required = false, defaultValue = "false") boolean nativeBinary) {
		assertUploadWithinCap(file);
		return turSNSearchProcess.getSNSite(siteName)
				.map(site -> {
					TurSNSiteGenAi genAi = site.getTurSNSiteGenAi();
					if (genAi == null || genAi.getTurAIAgent() == null) {
						return ChatSlotExtractResponse.error(
								"No AI agent configured for site '" + siteName + "'");
					}
					TurAIAgent agent = genAi.getTurAIAgent();
					SlotExtractionResult result = slotExtractionService.extract(
							file, agent, conversationId, parseSlotNames(slotNames), confidence, nativeBinary);
					return ChatSlotExtractResponse.from(result);
				})
				.orElseGet(() -> ChatSlotExtractResponse.error("Site not found: " + siteName));
	}

	private static java.util.List<String> parseSlotNames(String slotNames) {
		return (slotNames == null || slotNames.isBlank())
				? java.util.List.of()
				: java.util.Arrays.stream(slotNames.split(","))
						.map(String::trim).filter(s -> !s.isEmpty())
						.toList();
	}

	public record ChatSlotExtractResponse(
			java.util.Map<String, String> extracted,
			int slotsWritten,
			int extractedTextChars,
			java.util.Map<String, Double> confidences,
			String error) {

		static ChatSlotExtractResponse error(String message) {
			return new ChatSlotExtractResponse(java.util.Map.of(), 0, 0, java.util.Map.of(), message);
		}

		static ChatSlotExtractResponse from(SlotExtractionResult result) {
			return new ChatSlotExtractResponse(result.extracted(), result.slotsWritten(),
					result.extractedTextChars(), result.confidences(), null);
		}
	}

	/**
	 * Multi-document extraction (T101). Same contract as {@code /slot-extract}
	 * but accepts several files in one request and asks the agent's LLM to
	 * reason <em>across</em> them — the headline use case is uploading a CV plus
	 * a job description so the model can fill a synthesised {@code skill_gap}
	 * slot. Text documents are concatenated under delimiters; image documents
	 * are attached as vision media. A single file behaves exactly like
	 * {@code /slot-extract}.
	 *
	 * <p>Unauthenticated, same posture as the rest of
	 * {@code /api/sn/{site}/chat/*}.
	 *
	 * @since 2026.3.1
	 */
	@PostMapping(value = "/slot-extract-multi", consumes = "multipart/form-data")
	public ChatSlotExtractResponse extractSlotsMulti(@PathVariable String siteName,
			@RequestParam("files") java.util.List<org.springframework.web.multipart.MultipartFile> files,
			@RequestParam("conversationId") String conversationId,
			@RequestParam(value = "slotNames", required = false) String slotNames,
			@RequestParam(value = "confidence", required = false, defaultValue = "false") boolean confidence,
			@RequestParam(value = "nativeBinary", required = false, defaultValue = "false") boolean nativeBinary) {
		if (files != null) {
			files.forEach(this::assertUploadWithinCap);
		}
		return turSNSearchProcess.getSNSite(siteName)
				.map(site -> {
					TurSNSiteGenAi genAi = site.getTurSNSiteGenAi();
					if (genAi == null || genAi.getTurAIAgent() == null) {
						return ChatSlotExtractResponse.error(
								"No AI agent configured for site '" + siteName + "'");
					}
					TurAIAgent agent = genAi.getTurAIAgent();
					SlotExtractionResult result = slotExtractionService.extractMulti(
							files, agent, conversationId, parseSlotNames(slotNames), confidence, nativeBinary);
					return ChatSlotExtractResponse.from(result);
				})
				.orElseGet(() -> ChatSlotExtractResponse.error("Site not found: " + siteName));
	}

	/**
	 * Multi-modal slot upload (T64). Stores a binary (image / audio / any
	 * file) in object storage and writes its URL into a multi-modal slot
	 * ({@code IMAGE}/{@code AUDIO}/{@code FILE}) on the live conversation, so
	 * a React component renders it straight from the slot value via the SSE
	 * channel. For {@code IMAGE} slots, pass {@code vision=true} to also run
	 * the agent's vision LLM over the image and fill scalar slots — the
	 * "photo of a CV → name / cargo / objetivo slots" use case.
	 *
	 * <p>Requires object storage to be enabled ({@code turing.storage.type}
	 * = {@code minio} or {@code filesystem}); returns an {@code error} when
	 * disabled, the slot is unknown, or the slot is not multi-modal.
	 *
	 * <p>Unauthenticated, same posture as the rest of
	 * {@code /api/sn/{site}/chat/*}.
	 *
	 * @since 2026.3.1
	 */
	@PostMapping(value = "/slot-upload", consumes = "multipart/form-data")
	public ChatSlotUploadResponse uploadSlotBinary(@PathVariable String siteName,
			@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
			@RequestParam("conversationId") String conversationId,
			@RequestParam("slotName") String slotName,
			@RequestParam(value = "vision", required = false, defaultValue = "false") boolean vision,
			@RequestParam(value = "visionSlotNames", required = false) String visionSlotNames) {
		assertUploadWithinCap(file);
		return turSNSearchProcess.getSNSite(siteName)
				.map(site -> {
					TurSNSiteGenAi genAi = site.getTurSNSiteGenAi();
					if (genAi == null || genAi.getTurAIAgent() == null) {
						return ChatSlotUploadResponse.error(
								"No AI agent configured for site '" + siteName + "'");
					}
					java.util.List<String> requested = (visionSlotNames == null || visionSlotNames.isBlank())
							? java.util.List.of()
							: java.util.Arrays.stream(visionSlotNames.split(","))
									.map(String::trim).filter(s -> !s.isEmpty())
									.toList();
					MultiModalSlotResult result = multiModalSlotService.upload(
							file, genAi.getTurAIAgent(), conversationId, slotName, vision, requested);
					return ChatSlotUploadResponse.from(result);
				})
				.orElseGet(() -> ChatSlotUploadResponse.error("Site not found: " + siteName));
	}

	public record ChatSlotUploadResponse(
			String slotName,
			String slotType,
			String objectName,
			String url,
			String contentType,
			long size,
			java.util.Map<String, String> visionExtracted,
			int visionSlotsWritten,
			String error) {

		static ChatSlotUploadResponse error(String message) {
			return new ChatSlotUploadResponse(null, null, null, null, null, 0,
					java.util.Map.of(), 0, message);
		}

		static ChatSlotUploadResponse from(MultiModalSlotResult result) {
			return new ChatSlotUploadResponse(result.slotName(),
					result.slotType() == null ? null : result.slotType().name(),
					result.objectName(), result.url(), result.contentType(), result.size(),
					result.visionExtracted(), result.visionSlotsWritten(), result.error());
		}
	}

	/**
	 * Channel-agnostic handoff: composes a deep link (WhatsApp / mailto /
	 * sms / …) carrying the conversation context so a human consultant
	 * can pick up where the bot left off without re-asking. The slot
	 * {@code handoff_status} is written as a side effect — both for the
	 * React UI (hide the CTA once clicked) and for the analytics dashboard
	 * (compare handoff rates across A/B variants).
	 *
	 * <p>Generic: caller passes the channel + destination + optional
	 * intro + slot whitelist. WhatsApp uses {@code wa.me} universal
	 * links; email uses {@code mailto:}; SMS uses {@code sms:}. New
	 * channels plug in via {@code TurChatHandoffService.Channel}.
	 *
	 * @since 2026.2.7
	 */
	@PostMapping("/handoff")
	public ChatHandoffResponse handoff(@PathVariable String siteName,
			@RequestBody ChatHandoffRequest body) {
		if (body == null) {
			return ChatHandoffResponse.error("Request body is required");
		}
		Channel channel;
		try {
			channel = Channel.valueOf(body.channel().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException | NullPointerException e) {
			return ChatHandoffResponse.error("Unknown channel: " + body.channel());
		}
		HandoffResult result = handoffService.buildLink(body.conversationId(),
				new HandoffRequest(channel, body.destination(), body.intro(), body.slotsToInclude()));
		if (result.error() != null) {
			return ChatHandoffResponse.error(result.error());
		}
		return new ChatHandoffResponse(result.url(), result.transcript(),
				result.slotsIncluded(), null);
	}

	public record ChatHandoffRequest(
			String conversationId,
			String channel,
			String destination,
			String intro,
			List<String> slotsToInclude) {
	}

	public record ChatHandoffResponse(
			String url,
			String transcript,
			int slotsIncluded,
			String error) {
		static ChatHandoffResponse error(String message) {
			return new ChatHandoffResponse(null, null, 0, message);
		}
	}

	/**
	 * Server-side rendered Open Graph share card. The chat-flow author
	 * builds a deep-link to this endpoint with the slot values they want
	 * surfaced in the preview (e.g. {@code ?name=Alexandre&area=financas
	 * &dest=https://ee.education.example.com/match}); social-platform scrapers
	 * (WhatsApp, Slack, LinkedIn, X, Discord, …) hit it, harvest the
	 * personalized {@code og:*} / {@code twitter:*} meta tags, and render
	 * a rich preview card. Human visitors who click the link are
	 * meta-refresh-redirected to {@code dest}.
	 *
	 * <p>{@code @RequestParam Map} collects all query params as-is, so
	 * the renderer is fully generic — any slot the flow author wants to
	 * weave into the title/description becomes a query key.
	 *
	 * @since 2026.2.7
	 */
	@GetMapping(value = "/share/og", produces = MediaType.TEXT_HTML_VALUE)
	public String shareOg(@PathVariable String siteName,
			@RequestParam java.util.Map<String, String> params) {
		return shareOgService.renderCard(params);
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

	/**
	 * Response for {@code GET /api/sn/{siteName}/chat/enabled}.
	 *
	 * <p>{@code reason} is a {@link ChatDisabledReason} name — {@code "NONE"} when
	 * chat is fully wired, otherwise the first missing condition the diagnostic
	 * walk found. The UI uses it to render a precise tooltip telling the admin
	 * what to fix (e.g. {@code RAG_DISABLED} → "Enable RAG on the agent").
	 *
	 * @since 2026.2.17
	 */
	public record ChatEnabledResponse(boolean enabled, String reason,
			String sessionCookieName, long sessionTtlSeconds) {
	}

	/**
	 * T633 / §XXVII.4 — {@code personaId} is an optional per-request persona
	 * override for the anonymous public SN chat ("same question, different
	 * eyes"). It is validated downstream against the effective agent's persona
	 * catalog (unknown → the agent's default persona, never an arbitrary one);
	 * null keeps the pre-T633 behaviour.
	 */
	public record ConversationRequest(List<ConversationMessage> messages, String locale,
			java.util.Map<String, List<String>> filters,
			String conversationId, String flowId, String forcedVariant, String personaId) {
	}
}
