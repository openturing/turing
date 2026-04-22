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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.sn.search.TurSNSiteSearchService;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.genai.TurChatMessage;
import com.viglet.turing.genai.TurSNGenAi;
import com.viglet.turing.genai.TurSNGenAi.ConversationMessage;
import com.viglet.turing.genai.TurGenAiContext;
import com.viglet.turing.genai.TurGenAiContextFactory;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.TurSNSearchProcess;
import com.viglet.turing.system.TurGlobalSettingsService;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/sn/{siteName}/chat")
@Tag(name = "Semantic Navigation with Generative AI", description = "Semantic Navigation with Generative AI API")
public class TurSNSiteGenAiAPI {
	private static final String NOT_ENABLED_MESSAGE = "Language Model is not enabled for this site.";

	private final TurSNSearchProcess turSNSearchProcess;
	private final TurSNGenAi turGenAi;
	private final TurGenAiContextFactory turGenAiContextFactory;
	private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
	private final TurSNSiteSearchService turSNSiteSearchService;
	private final TurGlobalSettingsService turGlobalSettingsService;

	public TurSNSiteGenAiAPI(TurSNSearchProcess turSNSearchProcess,
			TurSNGenAi turGenAi,
			TurGenAiContextFactory turGenAiContextFactory,
			TurSNSiteLocaleRepository turSNSiteLocaleRepository,
			TurSNSiteSearchService turSNSiteSearchService,
			TurGlobalSettingsService turGlobalSettingsService) {
		this.turSNSearchProcess = turSNSearchProcess;
		this.turGenAi = turGenAi;
		this.turGenAiContextFactory = turGenAiContextFactory;
		this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
		this.turSNSiteSearchService = turSNSiteSearchService;
		this.turGlobalSettingsService = turGlobalSettingsService;
	}

	private boolean hasLlm(com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi genAi) {
		return genAi.getTurLLMInstance() != null
				|| org.springframework.util.StringUtils.hasText(turGlobalSettingsService.getDefaultLlmId());
	}

	private boolean hasEmbeddingModel(com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi genAi) {
		return genAi.getTurEmbeddingModelInstance() != null
				|| org.springframework.util.StringUtils.hasText(turGlobalSettingsService.getDefaultEmbeddingModelId());
	}

	private boolean hasStore(com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi genAi) {
		return genAi.getTurStoreInstance() != null
				|| org.springframework.util.StringUtils.hasText(turGlobalSettingsService.getDefaultEmbeddingStoreId());
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
		Locale locale = resolveLocale(localeRequest, siteName);
		if (turSNSearchProcess.existsByTurSNSiteAndLanguage(siteName, locale)) {
			return turSNSearchProcess.getSNSite(siteName).map(site -> {
				var turSNSiteGenAI = site.getTurSNSiteGenAi();
				if (turSNSiteGenAI == null || !turSNSiteGenAI.isEnabled()) {
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
				TurGenAiContext turGenAiContext = turGenAiContextFactory
						.build(turSNSiteGenAI, collectionName);
				return turGenAi.assistant(turGenAiContext, q);
			}).orElse(TurChatMessage.builder().text("Couldn't find site name.").build());
		}
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
				.map(site -> {
					var turSNSiteGenAI = site.getTurSNSiteGenAi();
					if (turSNSiteGenAI == null || !turSNSiteGenAI.isEnabled()) {
						return false;
					}
					return hasLlm(turSNSiteGenAI)
							&& hasEmbeddingModel(turSNSiteGenAI)
							&& hasStore(turSNSiteGenAI);
				})
				.orElse(false);
		return new ChatEnabledResponse(enabled);
	}

	/**
	 * Conversational RAG endpoint: accepts the full message history so follow-up
	 * questions preserve context. The latest user message drives document retrieval.
	 *
	 * @since 2026.2.4
	 */
	@PostMapping("/conversation")
	public TurChatMessage conversation(@PathVariable String siteName,
			@RequestBody ConversationRequest request) {
		Locale locale = resolveLocale(request.locale(), siteName);
		if (!turSNSearchProcess.existsByTurSNSiteAndLanguage(siteName, locale)) {
			return TurChatMessage.builder()
					.text(NOT_ENABLED_MESSAGE)
					.enabled(false)
					.build();
		}
		return turSNSearchProcess.getSNSite(siteName).map(site -> {
			var turSNSiteGenAI = site.getTurSNSiteGenAi();
			if (turSNSiteGenAI == null || !turSNSiteGenAI.isEnabled()) {
				return TurChatMessage.builder()
						.text(NOT_ENABLED_MESSAGE)
						.enabled(false)
						.build();
			}
			String collectionName = null;
			var siteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(site, locale);
			if (siteLocale != null) {
				collectionName = siteLocale.getCore();
			}
			TurGenAiContext context = turGenAiContextFactory.build(turSNSiteGenAI, collectionName);
			return turGenAi.assistantConversation(context, request.messages());
		}).orElse(TurChatMessage.builder().text("Couldn't find site name.").enabled(false).build());
	}

	public record ChatEnabledResponse(boolean enabled) {
	}

	public record ConversationRequest(List<ConversationMessage> messages, String locale) {
	}
}
