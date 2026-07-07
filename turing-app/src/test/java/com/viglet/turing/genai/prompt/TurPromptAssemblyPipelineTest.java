/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.TurAgentChatFlowContext;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.persona.TurPersonaFewShotRetriever;
import com.viglet.turing.genai.persona.TurPersonaPromptComposer;
import com.viglet.turing.genai.persona.TurPersonaStaticPromptCache;
import com.viglet.turing.genai.prompt.contributor.TurAgentBasePromptContributor;
import com.viglet.turing.genai.prompt.contributor.TurCapabilityPromptContributor;
import com.viglet.turing.genai.prompt.contributor.TurFlowPromptContributor;
import com.viglet.turing.genai.prompt.contributor.TurMcpPromptContributor;
import com.viglet.turing.genai.prompt.contributor.TurPersonaPromptContributor;
import com.viglet.turing.genai.prompt.contributor.TurSkillPromptContributor;
import com.viglet.turing.genai.skill.activation.TurSkillRunnerService;
import com.viglet.turing.genai.tool.TurMcpInstructionsProvider;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.persona.TurPersona;

/**
 * Block AL / T613 + T614 — proves the single-pass contributor pipeline reproduces
 * the legacy {@link TurPersonaPromptComposer} concatenation <b>byte-for-byte</b>
 * in legacy order, and that the T614 stable-first ordering places the cache
 * breakpoint correctly.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurPromptAssemblyPipelineTest {

    @Mock
    private TurPersonaStaticPromptCache staticPromptCache;
    @Mock
    private TurPersonaFewShotRetriever fewShotRetriever;
    @Mock
    private TurMcpInstructionsProvider mcpInstructionsProvider;
    @Mock
    private TurSkillRunnerService skillRunnerService;
    @Mock
    private TurChatFlowEngineService chatFlowEngineService;
    @Mock
    private TurAIAgent agent;
    @Mock
    private TurPersona persona;

    private TurPersonaPromptComposer composer;
    private TurPromptAssemblyPipeline legacyPipeline;
    private TurPromptAssemblyPipeline stableFirstPipeline;

    private static final String STATIC = "PERSONA STATIC BLOCK";
    private static final String MCP = "\n\n# MCP server instructions\n...";
    private static final String SKILL = "\n\n# Skills\n...";
    private static final String FLOW = "\n\n## ACTIVE FLOW STEP\nGoal: collect X\n";

    @BeforeEach
    void setUp() {
        composer = new TurPersonaPromptComposer(fewShotRetriever, staticPromptCache);
        List<TurPromptContributor> contributors = List.of(
                new TurPersonaPromptContributor(staticPromptCache, composer),
                new TurAgentBasePromptContributor(),
                new TurMcpPromptContributor(mcpInstructionsProvider),
                new TurCapabilityPromptContributor(), // no @PostConstruct → all prompts blank
                new TurSkillPromptContributor(skillRunnerService),
                new TurFlowPromptContributor(chatFlowEngineService));
        legacyPipeline = new TurPromptAssemblyPipeline(contributors, false);
        stableFirstPipeline = new TurPromptAssemblyPipeline(contributors, true);

        lenient().when(persona.getName()).thenReturn("Concierge");
        lenient().when(persona.getFewShotStore()).thenReturn(null);
        lenient().when(staticPromptCache.composeStaticBlock(persona)).thenReturn(STATIC);
        lenient().when(mcpInstructionsProvider.buildSystemPromptBlock(any())).thenReturn(MCP);
        lenient().when(skillRunnerService.isAvailable()).thenReturn(true);
        lenient().when(skillRunnerService.offeredSkills(any())).thenReturn(List.of());
        lenient().when(skillRunnerService.buildSystemPromptBlock(any())).thenReturn(SKILL);
        lenient().when(chatFlowEngineService.buildSystemPromptAddendum(any(), any(), any()))
                .thenReturn(FLOW);
    }

    private TurPromptAssemblyContext ctx(TurPersona p, String base, boolean withMcp,
            boolean withSkill, TurAgentChatFlowContext flow) {
        when(agent.isSkillsEnabled()).thenReturn(withSkill);
        // Capability flags default to false on the mock → no capability segments.
        when(agent.getMcpServers()).thenReturn(withMcp ? java.util.Set.of() : null);
        if (!withMcp) {
            when(mcpInstructionsProvider.buildSystemPromptBlock(null)).thenReturn("");
        }
        return new TurPromptAssemblyContext(agent, p, null, null, base, flow, null);
    }

    /**
     * Replicates the historical {@code TurPersonaPromptComposer}-based
     * concatenation (retired from the runtime in T615) so the pipeline's legacy
     * ordering can still be pinned byte-for-byte against it.
     */
    private String legacy(TurPersona p, String base, boolean withMcp, boolean withSkill,
            TurAgentChatFlowContext flow) {
        String mcp = withMcp ? MCP : "";
        String skill = withSkill ? SKILL : "";
        String flowText = flow == null ? "" : FLOW;
        String body = (base == null ? "" : base) + mcp + skill + flowText;
        return composer.compose(p, null, null, body);
    }

    @Test
    void personaNull_returnsBodyUnchanged() {
        var context = ctx(null, "BASE PROMPT", true, false, null);
        String out = legacyPipeline.assemble(context).systemText();
        assertThat(out).isEqualTo("BASE PROMPT" + MCP);
        assertThat(out).isEqualTo(legacy(null, "BASE PROMPT", true, false, null));
    }

    @Test
    void personaWrapsBase_matchesLegacy() {
        var context = ctx(persona, "BASE PROMPT", false, false, null);
        String out = legacyPipeline.assemble(context).systemText();
        assertThat(out).isEqualTo(STATIC + "\n\n----\n" + "BASE PROMPT");
        assertThat(out).isEqualTo(legacy(persona, "BASE PROMPT", false, false, null));
    }

    @Test
    void personaBaseMcpSkillFlow_matchesLegacy() {
        var flow = new TurAgentChatFlowContext(null, null, null, false);
        var context = ctx(persona, "BASE", true, true, flow);
        String out = legacyPipeline.assemble(context).systemText();
        assertThat(out).isEqualTo(STATIC + "\n\n----\n" + "BASE" + MCP + SKILL + FLOW);
        assertThat(out).isEqualTo(legacy(persona, "BASE", true, true, flow));
    }

    @Test
    void personaWithBlankBase_stillWrapsBody() {
        var context = ctx(persona, "", true, false, null);
        String out = legacyPipeline.assemble(context).systemText();
        // hasText(body) is true (mcp), so the "----" wrap is applied before mcp.
        assertThat(out).isEqualTo(STATIC + "\n\n----\n" + MCP);
        assertThat(out).isEqualTo(legacy(persona, "", true, false, null));
    }

    @Test
    void legacyOrder_hasNoCacheBreakpoint() {
        var flow = new TurAgentChatFlowContext(null, null, null, false);
        var context = ctx(persona, "BASE", true, true, flow);
        TurPromptAssembly assembly = legacyPipeline.assemble(context);
        assertThat(assembly.cacheBreakpointIndex()).isEqualTo(-1);
        assertThat(assembly.segments()).extracting(TurPromptSegment::origin)
                .containsExactly(TurPromptSegment.ORIGIN_PERSONA, TurPromptSegment.ORIGIN_AGENT,
                        TurPromptSegment.ORIGIN_MCP, TurPromptSegment.ORIGIN_SKILL,
                        TurPromptSegment.ORIGIN_FLOW);
    }

    @Test
    void stableFirstOrder_movesFlowToTail_andSetsBreakpoint() {
        var flow = new TurAgentChatFlowContext(null, null, null, false);
        var context = ctx(persona, "BASE", true, true, flow);
        TurPromptAssembly assembly = stableFirstPipeline.assemble(context);

        // PERSONA(static), AGENT, MCP, SKILL are STABLE; FLOW is PER_TURN → tail.
        assertThat(assembly.segments()).extracting(TurPromptSegment::origin)
                .containsExactly(TurPromptSegment.ORIGIN_PERSONA, TurPromptSegment.ORIGIN_AGENT,
                        TurPromptSegment.ORIGIN_MCP, TurPromptSegment.ORIGIN_SKILL,
                        TurPromptSegment.ORIGIN_FLOW);
        assertThat(assembly.cacheBreakpointIndex()).isEqualTo(4);
        assertThat(assembly.segments().get(4).stability()).isEqualTo(TurPromptStability.PER_TURN);
        // Uniform blank-line join, leading separators trimmed (displayText uses
        // stripLeading — trailing content, e.g. the flow block's newline, is kept).
        assertThat(assembly.systemText())
                .isEqualTo(STATIC + "\n\n" + "BASE" + "\n\n" + MCP.stripLeading()
                        + "\n\n" + SKILL.stripLeading() + "\n\n" + FLOW.stripLeading());
    }

    @Test
    void segmentsCarryTokenEstimates() {
        var context = ctx(persona, "BASE", false, false, null);
        TurPromptAssembly assembly = legacyPipeline.assemble(context);
        assertThat(assembly.totalTokens()).isPositive();
        assertThat(assembly.segments()).allSatisfy(s -> assertThat(s.tokens()).isGreaterThanOrEqualTo(0));
    }
}
