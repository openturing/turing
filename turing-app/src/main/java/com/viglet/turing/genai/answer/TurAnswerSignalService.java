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

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.genai.TurDefaultAgentResolver;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.answer.TurAnswerSignalEvent;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.answer.TurAnswerSignalRepository;
import com.viglet.turing.sn.TurSNSearchProcess;

import lombok.extern.slf4j.Slf4j;

/**
 * T845 / §LXII.14 — records answer-level interaction signals from the answer
 * surface, keyed by the answer's effective {@link TurAIAgent agent} so the
 * {@code selftuning} miner can consume them alongside the chat-analytics session
 * failures.
 *
 * <p>The answer endpoint is stateless SN-site streaming (no chat-analytics
 * session), but it resolves the same effective agent the chat runs through — so
 * this service re-resolves that agent for the site and stamps the signal with its
 * id, giving the agent-keyed miner a row to read. Best-effort by construction: a
 * missing site / disabled AI / unknown signal is a silent no-op, never an error.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurAnswerSignalService {

    private static final int QUERY_MAX = 2000;

    private final TurSNSearchProcess turSNSearchProcess;
    private final TurDefaultAgentResolver turDefaultAgentResolver;
    private final TurAnswerSignalRepository repository;

    public TurAnswerSignalService(TurSNSearchProcess turSNSearchProcess,
            TurDefaultAgentResolver turDefaultAgentResolver,
            TurAnswerSignalRepository repository) {
        this.turSNSearchProcess = turSNSearchProcess;
        this.turDefaultAgentResolver = turDefaultAgentResolver;
        this.repository = repository;
    }

    /**
     * Records one answer-level signal for {@code siteName}. Resolves the site's
     * effective agent and stamps the signal with its id; a signal with no
     * resolvable agent is dropped (the miner is agent-keyed, so it would be
     * unreadable anyway). Returns the persisted row, or empty when nothing was
     * recorded.
     *
     * @param siteName       the SN site the answer was produced over
     * @param conversationId the answer interaction id correlating several signals (may be {@code null})
     * @param signal         the interaction signal (a {@code null} unknown signal is a no-op)
     * @param query          the question the answer answered (context; may be {@code null})
     */
    @Transactional
    public Optional<TurAnswerSignalEvent> record(String siteName, String conversationId,
            TurAnswerSignal signal, String query) {
        if (signal == null || siteName == null || siteName.isBlank()) {
            return Optional.empty();
        }
        String agentId = resolveAgentId(siteName);
        if (agentId == null) {
            log.debug("[AnswerSignal] no effective agent for site '{}' — dropping {} signal",
                    siteName, signal);
            return Optional.empty();
        }
        TurAnswerSignalEvent event = new TurAnswerSignalEvent();
        event.setAgentId(agentId);
        event.setSiteName(siteName);
        event.setConversationId(conversationId);
        event.setQuery(truncate(query, QUERY_MAX));
        event.setSignal(signal);
        event.setRelevanceMiss(signal.isRelevanceMiss());
        try {
            TurAnswerSignalEvent saved = repository.save(event);
            log.debug("[AnswerSignal] recorded {} for agent '{}' (site '{}', conv '{}')",
                    signal, agentId, siteName, conversationId);
            return Optional.of(saved);
        } catch (RuntimeException e) {
            log.warn("[AnswerSignal] failed to record {} for site '{}': {}",
                    signal, siteName, e.getMessage());
            return Optional.empty();
        }
    }

    /** Resolves the effective agent id for a site, or {@code null} when none applies. */
    private String resolveAgentId(String siteName) {
        Optional<TurSNSite> site = turSNSearchProcess.getSNSite(siteName);
        if (site.isEmpty()) {
            return null;
        }
        TurAIAgent agent = turDefaultAgentResolver.resolveEffectiveAgent(site.get().getTurSNSiteGenAi());
        return agent == null ? null : agent.getId();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
