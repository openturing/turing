/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.sn.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.viglet.turing.api.exception.TurApiException;
import com.viglet.turing.api.exception.TurNotFoundException;
import com.viglet.turing.api.exception.TurPayloadTooLargeException;
import com.viglet.turing.api.exception.TurValidationException;
import com.viglet.turing.api.sn.genai.TurSNSitePersonaContentFitAPI.ContentFitRequest;
import com.viglet.turing.genai.TurDefaultAgentResolver;
import com.viglet.turing.genai.persona.fit.TurContentFitMisfit;
import com.viglet.turing.genai.persona.fit.TurContentFitResult;
import com.viglet.turing.genai.persona.fit.TurPersonaContentFitEvaluator;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.sn.TurSNSearchProcess;

/**
 * Unit tests for the T634 anonymous persona content-fit endpoint — the
 * catalog/audience validation, the input-size cap, and the happy path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNSitePersonaContentFitAPITest {

    private static final String SITE = "turing-docs";

    @Mock
    private TurSNSearchProcess turSNSearchProcess;
    @Mock
    private TurDefaultAgentResolver turDefaultAgentResolver;
    @Mock
    private TurPersonaContentFitEvaluator evaluator;

    private TurSNSitePersonaContentFitAPI api;
    private TurAIAgent agent;

    @BeforeEach
    void setUp() {
        api = new TurSNSitePersonaContentFitAPI(turSNSearchProcess, turDefaultAgentResolver, evaluator);

        TurPersona audience = persona("p-audience", TurPersonaKind.AUDIENCE);
        TurPersona speakerOnly = persona("p-speaker", TurPersonaKind.SPEAKER);
        agent = new TurAIAgent();
        agent.setId("agent-1");
        agent.setPersonas(Set.of(audience, speakerOnly));

        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        TurSNSite site = new TurSNSite();
        site.setTurSNSiteGenAi(genAi);
        lenient().when(turSNSearchProcess.getSNSite(SITE)).thenReturn(Optional.of(site));
        lenient().when(turDefaultAgentResolver.resolveEffectiveAgent(genAi)).thenReturn(agent);
    }

    @Test
    void audiencePersonaInCatalogReturnsVerdict() {
        when(evaluator.evaluateText(any(), eq("Some marketing copy."), any(), any(), eq(false)))
                .thenReturn(new TurContentFitResult(0.72, "Mostly fits.",
                        List.of("Clear intro"),
                        List.of(new TurContentFitMisfit("synergy", "jargon", "Use 'teamwork'")),
                        0.6, null, true, null, false, null, "copy"));

        var response = api.evaluate(SITE, "p-audience",
                new ContentFitRequest("Some marketing copy.", "copy"));

        assertThat(response.personaId()).isEqualTo("p-audience");
        assertThat(response.fitScore()).isEqualTo(0.72);
        assertThat(response.fits()).containsExactly("Clear intro");
        assertThat(response.misfits()).hasSize(1);
        assertThat(response.llmUsed()).isTrue();
    }

    @Test
    void blankContentIsBadRequest() {
        assertThatThrownBy(() -> api.evaluate(SITE, "p-audience", new ContentFitRequest("  ", null)))
                .isInstanceOf(TurValidationException.class)
                .extracting(e -> ((TurApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void nullBodyIsBadRequest() {
        assertThatThrownBy(() -> api.evaluate(SITE, "p-audience", null))
                .isInstanceOf(TurValidationException.class)
                .extracting(e -> ((TurApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void oversizedContentIsRejected() {
        String huge = "x".repeat(TurSNSitePersonaContentFitAPI.MAX_CONTENT_CHARS + 1);
        assertThatThrownBy(() -> api.evaluate(SITE, "p-audience", new ContentFitRequest(huge, null)))
                .isInstanceOf(TurPayloadTooLargeException.class)
                .extracting(e -> ((TurApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
    }

    @Test
    void unknownPersonaIsNotFound() {
        assertThatThrownBy(() -> api.evaluate(SITE, "does-not-exist",
                new ContentFitRequest("text", null)))
                .isInstanceOf(TurNotFoundException.class)
                .extracting(e -> ((TurApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void speakerOnlyPersonaIsNotFound() {
        // In the catalog, but not usable as an audience → cannot be validated against.
        assertThatThrownBy(() -> api.evaluate(SITE, "p-speaker",
                new ContentFitRequest("text", null)))
                .isInstanceOf(TurNotFoundException.class)
                .extracting(e -> ((TurApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void missingSiteIsNotFound() {
        when(turSNSearchProcess.getSNSite("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> api.evaluate("ghost", "p-audience",
                new ContentFitRequest("text", null)))
                .isInstanceOf(TurNotFoundException.class)
                .extracting(e -> ((TurApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void siteWithoutGenAiBindingFallsBackToDefaultAgent() {
        // The public-demo case: a search-only site has no per-site GenAI binding,
        // but a global Default AI Agent is configured. The resolver is null-safe,
        // so the endpoint must still validate — never a spurious 404/500.
        TurSNSite searchOnly = new TurSNSite();
        searchOnly.setTurSNSiteGenAi(null);
        when(turSNSearchProcess.getSNSite("demo")).thenReturn(Optional.of(searchOnly));
        when(turDefaultAgentResolver.resolveEffectiveAgent(null)).thenReturn(agent);
        when(evaluator.evaluateText(any(), eq("text"), any(), any(), eq(false)))
                .thenReturn(new TurContentFitResult(0.5, "ok", List.of(), List.of(),
                        0.5, null, true, null, false, null, "text"));

        var response = api.evaluate("demo", "p-audience", new ContentFitRequest("text", null));

        assertThat(response.personaId()).isEqualTo("p-audience");
    }

    @Test
    void noSiteAgentAndNoDefaultAgentIsNotFound() {
        // Neither a site agent nor a global default → the only true 404 for agents.
        when(turDefaultAgentResolver.resolveEffectiveAgent(any())).thenReturn(null);
        assertThatThrownBy(() -> api.evaluate(SITE, "p-audience",
                new ContentFitRequest("text", null)))
                .isInstanceOf(TurNotFoundException.class)
                .extracting(e -> ((TurApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static TurPersona persona(String id, TurPersonaKind kind) {
        TurPersona p = new TurPersona();
        p.setId(id);
        p.setName(id);
        p.setPersonaKind(kind);
        return p;
    }
}
