/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.answer;

import java.util.List;
import java.util.Locale;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurDefaultAgentResolver;
import com.viglet.turing.genai.TurGenAiContext;
import com.viglet.turing.genai.TurGenAiContextFactory;
import com.viglet.turing.genai.TurSNGenAi;
import com.viglet.turing.genai.TurSNGenAiChatRequest;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.TurSNSearchProcess;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * T833 / §LXII.2 — the answer-first streaming service behind
 * {@code GET /api/sn/{siteName}/answer}. It composes the platform's existing
 * retrieve → rerank → synthesis pipeline (the same one the public SN chat runs,
 * so it inherits hybrid ranking, the rerank seam, the site's
 * {@code TurSNKnowledgeBaseMode} and the {@code sources[]}-before-tokens ordering)
 * into a single-shot grounded answer, and re-frames the internal chat stream into
 * the dedicated answer SSE contract via {@link TurAnswerStreamFramer}.
 *
 * <p>Deliberately a separate surface from the search response (never an
 * {@code answer} field on {@code TurSNSiteSearchBean}): the hot search path must
 * not pay LLM latency, and the client fires search and answer in parallel and
 * composes them.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurSNAnswerService {

    private static final String USER_ROLE = "user";

    private final TurSNSearchProcess turSNSearchProcess;
    private final TurDefaultAgentResolver turDefaultAgentResolver;
    private final TurGenAiContextFactory turGenAiContextFactory;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNGenAi turGenAi;

    public TurSNAnswerService(TurSNSearchProcess turSNSearchProcess,
            TurDefaultAgentResolver turDefaultAgentResolver,
            TurGenAiContextFactory turGenAiContextFactory,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurSNGenAi turGenAi) {
        this.turSNSearchProcess = turSNSearchProcess;
        this.turDefaultAgentResolver = turDefaultAgentResolver;
        this.turGenAiContextFactory = turGenAiContextFactory;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turGenAi = turGenAi;
    }

    /**
     * Streams a grounded answer for {@code query} over {@code siteName}'s knowledge
     * base as a sequence of named SSE events: {@code sources} (before the first
     * token), then {@code token}s, then {@code citations}, {@code followups} and
     * {@code trace}. Every failure mode degrades to a single terminal
     * {@code error} event rather than a broken stream.
     *
     * @param siteName the SN site to answer over
     * @param query    the user's question
     * @param locale   the response/retrieval locale
     * @return the answer event stream, never {@code null}
     */
    public Flux<ServerSentEvent<String>> answer(String siteName, String query, Locale locale) {
        if (query == null || query.isBlank()) {
            return Flux.just(TurAnswerStreamFramer.errorEvent("No query provided"));
        }
        return Flux.defer(() -> turSNSearchProcess.getSNSite(siteName)
                .map(site -> composeAnswer(site, siteName, query, locale))
                .orElseGet(() -> Flux.just(
                        TurAnswerStreamFramer.errorEvent("Site not found: " + siteName))))
                .onErrorResume(err -> {
                    log.warn("[SNAnswer] answer stream failed for site '{}': {}", siteName,
                            err.getMessage());
                    return Flux.just(TurAnswerStreamFramer.errorEvent(
                            "The answer service is temporarily unavailable."));
                });
    }

    private Flux<ServerSentEvent<String>> composeAnswer(TurSNSite site, String siteName,
            String query, Locale locale) {
        TurSNSiteGenAi genAi = site.getTurSNSiteGenAi();
        if (genAi == null) {
            return Flux.just(TurAnswerStreamFramer.errorEvent("AI is not enabled for site: " + siteName));
        }
        TurAIAgent agent = turDefaultAgentResolver.resolveEffectiveAgent(genAi);
        if (agent == null || agent.getLlmInstances() == null || agent.getLlmInstances().isEmpty()) {
            return Flux.just(TurAnswerStreamFramer.errorEvent("No LLM configured for site: " + siteName));
        }
        TurLLMInstance llmInstance = agent.getLlmInstances().stream()
                .filter(llm -> llm.getEnabled() == 1)
                .findFirst()
                .orElseGet(() -> agent.getLlmInstances().iterator().next());

        String collectionName = null;
        TurSNSiteLocale siteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(site, locale);
        if (siteLocale != null) {
            collectionName = siteLocale.getCore();
        }

        TurGenAiContext context = turGenAiContextFactory.build(genAi, collectionName);
        TurSNGenAiChatRequest request = new TurSNGenAiChatRequest(
                List.of(new TurSNGenAi.ConversationMessage(USER_ROLE, query)),
                null, null, null, null, locale, null);
        Flux<TurAgentChatExecutor.ChatResponse> upstream =
                turGenAi.assistantConversationStreaming(context, agent, llmInstance, request);
        return TurAnswerStreamFramer.frame(upstream);
    }
}
