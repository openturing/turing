/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.dto.agent.TurSystemPromptIssueDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptPreviewDto;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptSegmentDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.system.TurLlmSummaryService;
import com.viglet.turing.system.TurLlmSummaryService.SummaryResult;

/**
 * Tests for {@link TurSystemPromptValidatorService}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurSystemPromptValidatorServiceTest {

    @Mock
    private TurSystemPromptPreviewService previewService;
    @Mock
    private TurLlmSummaryService llmSummaryService;

    private TurSystemPromptValidatorService service;

    @BeforeEach
    void setUp() {
        service = new TurSystemPromptValidatorService(previewService, llmSummaryService);
        // T607 — validate() always consults flow persona diagnostics; default to
        // none so the pre-existing checks stay isolated (lenient: deepCheck tests
        // don't run validate()).
        lenient().when(previewService.diagnoseFlowPersonas(any())).thenReturn(List.of());
    }

    private void stubPreview(String assembledText) {
        when(previewService.buildPreview(any()))
                .thenReturn(new TurSystemPromptPreviewDto(List.of(), List.of(), assembledText, List.of(), null));
    }

    /** Stubs a preview whose assembled article is made of the given segments (T610). */
    private void stubPreviewWithSegments(TurSystemPromptSegmentDto... segments) {
        when(previewService.buildPreview(any()))
                .thenReturn(new TurSystemPromptPreviewDto(List.of(segments), List.of(), "assembled",
                        List.of(), null));
    }

    private static TurSystemPromptSegmentDto segment(String origin, String content) {
        return new TurSystemPromptSegmentDto(origin, null, content, true, false, null);
    }

    private static TurAIAgent agent(String systemPrompt) {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        agent.setSystemPrompt(systemPrompt);
        return agent;
    }

    private static TurPersona persona(String name) {
        TurPersona p = new TurPersona();
        p.setName(name);
        return p;
    }

    private static List<String> codes(List<TurSystemPromptIssueDto> issues) {
        return issues.stream().map(TurSystemPromptIssueDto::code).toList();
    }

    @Test
    void cleanPromptHasNoIssues() {
        stubPreview("You are a helpful assistant. Always respond in English.");
        var issues = service.validate(agent("You are a helpful assistant. Always respond in English."));
        assertThat(issues).isEmpty();
    }

    @Test
    void emptyPromptWarns() {
        stubPreview("");
        var issues = service.validate(agent("   "));
        assertThat(codes(issues)).contains("empty_system_prompt");
    }

    @Test
    void longPromptWarns() {
        stubPreview("x".repeat(TurSystemPromptValidatorService.LONG_PROMPT_THRESHOLD + 1));
        var issues = service.validate(agent("You are an assistant."));
        assertThat(codes(issues)).contains("system_prompt_too_long");
    }

    @Test
    void conflictingLanguageDirectivesAcrossAgentAndPersonaWarn() {
        stubPreview("base");
        TurAIAgent agent = agent("Always respond in English.");
        TurPersona persona = persona("Voz BR");
        persona.setSystemInstruction("Você é simpático. Sempre responda em português.");
        agent.setDefaultPersona(persona);

        var issues = service.validate(agent);
        assertThat(codes(issues)).contains("language_directive_conflict");
    }

    @Test
    void singleLanguageDirectiveIsClean() {
        stubPreview("base");
        TurAIAgent agent = agent("Always respond in English.");
        TurPersona persona = persona("Voice");
        persona.setSystemInstruction("Be friendly and reply in English.");
        agent.setDefaultPersona(persona);

        assertThat(codes(service.validate(agent))).doesNotContain("language_directive_conflict");
    }

    @Test
    void personaTermRequiredAndForbiddenIsError() {
        stubPreview("base");
        TurAIAgent agent = agent("You are an assistant.");
        TurPersona persona = persona("P");
        persona.setMandatoryTerms("innovation | synergy");
        persona.setForbiddenTerms("synergy | cheap");
        agent.setDefaultPersona(persona);

        var issues = service.validate(agent);
        assertThat(issues).anyMatch(i -> i.code().equals("persona_vocab_contradiction")
                && i.severity().equals("ERROR"));
    }

    @Test
    void forbiddenTermUsedInAgentPromptWarns() {
        stubPreview("base");
        TurAIAgent agent = agent("You must always be cheap and fast.");
        TurPersona persona = persona("P");
        persona.setForbiddenTerms("cheap");
        agent.setDefaultPersona(persona);

        assertThat(codes(service.validate(agent))).contains("forbidden_term_in_prompt");
    }

    @Test
    void flowPersonaNotInCatalogWarns() {
        stubPreview("base");
        when(previewService.diagnoseFlowPersonas(any())).thenReturn(List.of(
                new TurSystemPromptPreviewService.FlowPersonaDiagnostic(
                        "Concierge", "persona-ghost", true, false)));

        var issues = service.validate(agent("You are an assistant."));
        assertThat(issues).anyMatch(i -> i.code().equals("flow_persona_not_in_catalog")
                && i.severity().equals("WARNING")
                && i.source().equals("FLOW")
                && i.message().contains("Concierge"));
    }

    @Test
    void flowPersonaAudienceOnlyWarns() {
        stubPreview("base");
        when(previewService.diagnoseFlowPersonas(any())).thenReturn(List.of(
                new TurSystemPromptPreviewService.FlowPersonaDiagnostic(
                        "Concierge", "Reader Persona", false, true)));

        assertThat(codes(service.validate(agent("You are an assistant."))))
                .contains("flow_persona_audience_only");
    }

    @Test
    void crossSegmentRedundancyWarnsWhenRuleRepeatedAcrossSegments() {
        stubPreviewWithSegments(
                segment("PERSONA", "Never use the words talvez, impossivel or nao sei."),
                segment("AGENT", "Never use the words talvez, impossivel or nao sei."));

        var issues = service.validate(agent("You are an assistant."));
        assertThat(issues).anyMatch(i -> i.code().equals("cross_segment_redundancy")
                && i.severity().equals("WARNING")
                && i.source().contains("PERSONA")
                && i.source().contains("AGENT"));
    }

    @Test
    void crossSegmentNoRedundancyWhenRuleInOneSegmentOnly() {
        stubPreviewWithSegments(
                segment("PERSONA", "Never use the words talvez, impossivel or nao sei."),
                segment("AGENT", "Answer concisely and cite your sources."));

        assertThat(codes(service.validate(agent("You are an assistant."))))
                .doesNotContain("cross_segment_redundancy");
    }

    @Test
    void crossSegmentContradictionWhenDirectiveNegatedInAnotherSegment() {
        stubPreviewWithSegments(
                segment("AGENT", "Never answer general knowledge questions from users."),
                segment("FLOW", "Always answer general knowledge questions from users."));

        var issues = service.validate(agent("You are an assistant."));
        assertThat(issues).anyMatch(i -> i.code().equals("cross_segment_contradiction")
                && i.severity().equals("ERROR"));
    }

    @Test
    void deepCheckUnavailableWhenNoLlm() {
        when(llmSummaryService.isAvailable()).thenReturn(false);
        var issues = service.deepCheck(agent("You are an assistant."));
        assertThat(codes(issues)).containsExactly("deep_check_unavailable");
    }

    @Test
    void deepCheckParsesJsonArray() {
        when(llmSummaryService.isAvailable()).thenReturn(true);
        stubPreview("assembled prompt text");
        String json = """
                Here is the analysis:
                [
                  {"severity":"ERROR","code":"contradiction","message":"Says both A and not A.","hint":"Pick one."},
                  {"severity":"info","code":"note","message":"Minor overlap."}
                ]
                """;
        when(llmSummaryService.generate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, json, true));

        var issues = service.deepCheck(agent("You are an assistant."));
        assertThat(codes(issues)).containsExactly("contradiction", "note");
        assertThat(issues.get(0).severity()).isEqualTo("ERROR");
        assertThat(issues.get(0).source()).isEqualTo("LLM");
        // "info" normalized to upper-case INFO.
        assertThat(issues.get(1).severity()).isEqualTo("INFO");
    }

    @Test
    void deepCheckUnparseableFallsBackToRawNote() {
        when(llmSummaryService.isAvailable()).thenReturn(true);
        stubPreview("assembled");
        when(llmSummaryService.generate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, "No conflicts found, looks good.", true));

        var issues = service.deepCheck(agent("You are an assistant."));
        assertThat(codes(issues)).containsExactly("deep_check_note");
    }

    @Test
    void deepCheckEmptyArrayMeansNoIssues() {
        when(llmSummaryService.isAvailable()).thenReturn(true);
        stubPreview("assembled");
        when(llmSummaryService.generate(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(new SummaryResult(true, null, "[]", true));

        assertThat(service.deepCheck(agent("You are an assistant."))).isEmpty();
    }

    @Test
    void detectsLanguageDirectivesBilingually() {
        assertThat(service.detectLanguageDirectives("Always respond in English."))
                .containsExactly("English");
        assertThat(service.detectLanguageDirectives("Sempre responda em português, por favor."))
                .containsExactly("Portuguese");
        assertThat(service.detectLanguageDirectives("Just a mention of the Portuguese culture."))
                .isEmpty();
    }

    @Test
    void extractJsonArrayHandlesFencesAndProse() {
        assertThat(TurSystemPromptValidatorService.extractJsonArray("noise ```json [1,2] ``` tail"))
                .isEqualTo("[1,2]");
        assertThat(TurSystemPromptValidatorService.extractJsonArray("no array here")).isNull();
    }
}
