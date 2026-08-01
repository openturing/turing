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
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.TurDefaultAgentResolver;
import com.viglet.turing.genai.TurGenAiContext;
import com.viglet.turing.genai.TurGenAiContextFactory;
import com.viglet.turing.genai.TurSNGenAi;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.TurSNSearchProcess;

import reactor.core.publisher.Flux;

/**
 * T833 / §LXII.2 — wiring + fail-open coverage for {@link TurSNAnswerService}.
 * The stream re-framing itself is covered by {@link TurAnswerStreamFramerTest};
 * here we assert the service degrades every failure to a single {@code error}
 * event and composes the pipeline on the happy path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNAnswerServiceTest {

    @Mock
    private TurSNSearchProcess turSNSearchProcess;
    @Mock
    private TurDefaultAgentResolver turDefaultAgentResolver;
    @Mock
    private TurGenAiContextFactory turGenAiContextFactory;
    @Mock
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    @Mock
    private TurSNGenAi turGenAi;
    @Mock
    private TurFollowUpQuestionService followUpQuestionService;

    @InjectMocks
    private TurSNAnswerService service;

    private static List<ServerSentEvent<String>> collect(Flux<ServerSentEvent<String>> flux) {
        return flux.collectList().block();
    }

    private static List<String> eventNames(List<ServerSentEvent<String>> events) {
        return events.stream().map(ServerSentEvent::event).toList();
    }

    @Test
    void blankQueryDegradesToASingleErrorEvent() {
        List<ServerSentEvent<String>> events = collect(service.answer("site1", "  ", Locale.ENGLISH));

        assertThat(eventNames(events)).containsExactly("error");
        assertThat(events.get(0).data()).contains("No query provided");
    }

    @Test
    void unknownSiteDegradesToASingleErrorEvent() {
        when(turSNSearchProcess.getSNSite("ghost")).thenReturn(Optional.empty());

        List<ServerSentEvent<String>> events = collect(service.answer("ghost", "q", Locale.ENGLISH));

        assertThat(eventNames(events)).containsExactly("error");
        assertThat(events.get(0).data()).contains("Site not found");
    }

    @Test
    void composesTheAnswerStreamOnTheHappyPath() {
        TurSNSite site = new TurSNSite();
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        site.setTurSNSiteGenAi(genAi);
        TurAIAgent agent = new TurAIAgent();
        TurLLMInstance llm = new TurLLMInstance();
        llm.setEnabled(1);
        agent.setLlmInstances(java.util.Set.of(llm));
        TurSNSiteLocale siteLocale = new TurSNSiteLocale();
        siteLocale.setCore("sn_site1_en");

        when(turSNSearchProcess.getSNSite("site1")).thenReturn(Optional.of(site));
        when(turDefaultAgentResolver.resolveEffectiveAgent(genAi)).thenReturn(agent);
        when(turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(site, Locale.ENGLISH))
                .thenReturn(siteLocale);
        // The context factory result is opaque to the service; return a mock.
        lenient().when(turGenAiContextFactory.build(any(), any()))
                .thenReturn(org.mockito.Mockito.mock(TurGenAiContext.class));
        when(turGenAi.assistantConversationStreaming(any(), any(), any(), any()))
                .thenReturn(Flux.just(new ChatResponse("assistant", "Hello", "token")));
        when(followUpQuestionService.generate(any(), any(), any(), any()))
                .thenReturn(List.of("What next?"));

        List<ServerSentEvent<String>> events = collect(service.answer("site1", "hi", Locale.ENGLISH));

        // Re-framed into the answer contract: token, then citations, the generated
        // follow-ups, and the trace.
        assertThat(eventNames(events)).containsExactly("token", "citations", "followups", "trace");
        assertThat(events.get(0).data()).isEqualTo("Hello");
    }
}
