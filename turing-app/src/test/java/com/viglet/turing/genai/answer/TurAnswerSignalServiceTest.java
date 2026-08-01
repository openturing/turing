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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.TurDefaultAgentResolver;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.answer.TurAnswerSignalEvent;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.repository.answer.TurAnswerSignalRepository;
import com.viglet.turing.sn.TurSNSearchProcess;

/**
 * T845 / §LXII.14 — coverage for {@link TurAnswerSignalService}: an answer-level
 * signal is stamped with the site's effective agent and its relevance-miss flag,
 * and dropped fail-open when there is no agent or no signal.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurAnswerSignalServiceTest {

    @Mock
    private TurSNSearchProcess turSNSearchProcess;
    @Mock
    private TurDefaultAgentResolver turDefaultAgentResolver;
    @Mock
    private TurAnswerSignalRepository repository;

    @InjectMocks
    private TurAnswerSignalService service;

    private void siteResolvesToAgent(String siteName, String agentId) {
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        TurSNSite site = new TurSNSite();
        site.setTurSNSiteGenAi(genAi);
        TurAIAgent agent = new TurAIAgent();
        agent.setId(agentId);
        when(turSNSearchProcess.getSNSite(siteName)).thenReturn(Optional.of(site));
        when(turDefaultAgentResolver.resolveEffectiveAgent(genAi)).thenReturn(agent);
    }

    @Test
    void recordStampsTheEffectiveAgentAndTheRelevanceMissFlag() {
        siteResolvesToAgent("site1", "agent-1");
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        Optional<TurAnswerSignalEvent> saved = service.record("site1", "conv-1",
                TurAnswerSignal.FACET_REFINED_AFTER_ANSWER, "cheapest model?");

        assertThat(saved).isPresent();
        ArgumentCaptor<TurAnswerSignalEvent> captor = ArgumentCaptor.forClass(TurAnswerSignalEvent.class);
        verify(repository).save(captor.capture());
        TurAnswerSignalEvent event = captor.getValue();
        assertThat(event.getAgentId()).isEqualTo("agent-1");
        assertThat(event.getSiteName()).isEqualTo("site1");
        assertThat(event.getConversationId()).isEqualTo("conv-1");
        assertThat(event.getQuery()).isEqualTo("cheapest model?");
        assertThat(event.getSignal()).isEqualTo(TurAnswerSignal.FACET_REFINED_AFTER_ANSWER);
        // The clean relevance-miss signal is flagged so the miner counts it.
        assertThat(event.isRelevanceMiss()).isTrue();
    }

    @Test
    void recordFlagsAnEngagementSignalAsNotAMiss() {
        siteResolvesToAgent("site1", "agent-1");
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.record("site1", "conv-1", TurAnswerSignal.CITATION_CLICKED, "q");

        ArgumentCaptor<TurAnswerSignalEvent> captor = ArgumentCaptor.forClass(TurAnswerSignalEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isRelevanceMiss()).isFalse();
    }

    @Test
    void recordIsANoOpWhenTheSignalIsUnknown() {
        assertThat(service.record("site1", "conv-1", null, "q")).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void recordIsANoOpWhenNoEffectiveAgentResolves() {
        lenient().when(turSNSearchProcess.getSNSite("site1")).thenReturn(Optional.of(new TurSNSite()));
        lenient().when(turDefaultAgentResolver.resolveEffectiveAgent(any())).thenReturn(null);

        assertThat(service.record("site1", "conv-1", TurAnswerSignal.THUMBS_DOWN, "q")).isEmpty();
        verify(repository, never()).save(any());
    }
}
