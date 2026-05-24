/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import com.viglet.testsupport.genai.executor.TurAgentChatExecutorMockSupport;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.ExportEntry;
import com.viglet.turing.genai.testsupport.ChatFlowImportTestUtil.Imported;
import com.viglet.turing.persistence.model.agent.TurAIAgent;

/**
 * Smoke tests for the three harness fixtures created under task T5
 * (§VI.6 Phase 3 of {@code docs/IMPROVEMENTS.md}). Each {@code @Test}
 * imports one fixture, parses the graph through
 * {@link TurChatFlowEngineService#parseGraph}, and asserts the canonical
 * structural invariants the implementing skeletons (T4-T6) will rely on.
 *
 * <p>The goal here is NOT to exercise behavior — that's the structural /
 * contract / behavioral ITs. The goal is to fail FAST and loud when:
 *
 * <ul>
 *   <li>a fixture's JSON shape drifts away from what {@link ChatFlowImportTestUtil}
 *       expects (e.g. someone adds a new mandatory field on
 *       {@code TurChatFlow} and forgets to backfill the fixtures);</li>
 *   <li>a graph node id referenced by a structural test gets renamed in
 *       the fixture (these IDs are the contract — structural tests
 *       substring-match against them);</li>
 *   <li>a persona is dropped from a fixture and the runtime resolver
 *       silently falls back to default — the regression would show up
 *       deep in a structural test that asserts persona voice instead of
 *       here.</li>
 * </ul>
 *
 * <p>Per §VI.6 Phase 3: "Smoke `@Test` per fixture that imports + parses
 * + logs the graph." Each test prints a compact summary to {@code System.out}
 * so a developer running the suite locally can eyeball that the right
 * fixture loaded.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Import(TurAgentChatExecutorMockSupport.class)
class HarnessFixtureSmokeIT extends AbstractAgentExecutorIT {

    private static final String FIXTURE_TOOL_FREE     = "/harness-it/tool-free-node.chat-flow.json";
    private static final String FIXTURE_PERSONA_SWAP  = "/harness-it/persona-switch-mid-flow.chat-flow.json";
    private static final String FIXTURE_REGEN_CHIPS   = "/harness-it/regen-with-chips.chat-flow.json";

    @Autowired
    private TurChatFlowEngineService engine;

    @Test
    void toolFreeNodeFixture_importsAndParsesCleanly() throws IOException {
        ExportEntry entry = ChatFlowImportTestUtil.loadFromClasspath(FIXTURE_TOOL_FREE);
        TurAIAgent agent = createAgent("smoke-tool-free", null);
        Imported imp = ChatFlowImportTestUtil.importIntoAgent(agent, entry, importRepos());

        assertThat(imp.flow().getId()).as("Flow must be persisted").isNotBlank();
        assertThat(imp.persona())
                .as("Tool-free fixture has a single Camila persona that must be linked to the agent")
                .isNotNull();
        assertThat(imp.persona().getName()).contains("Camila");

        ChatFlowGraph graph = engine.parseGraph(imp.flow()).orElseThrow();
        // The canonical chain: start → persona → ai-coupon-code → switch-cupom-ok → ai-deliver → end.
        // Each id below is referenced verbatim by patches #3/#4/#5 implementations.
        assertThat(graph.nodeById("ai-coupon-code"))
                .as("Patches #3/#4/#5 substring-assert against this id — must not be renamed")
                .isPresent();
        assertThat(graph.nodeById("switch-cupom-ok")).isPresent();
        assertThat(graph.nodeById("ai-deliver")).isPresent();
        // Post-T16: the bilingual substring sentinel was deleted from
        // forbidsToolCalls. The fixture now uses the declarative
        // ChatFlowNode.toolsEnabled=false field (T11) — verify ALL
        // aiQuestion nodes in the tool-free chain are marked as such, so
        // earlyAdvance (T9) can't move the cursor onto a tool-enabled
        // node mid-test and silently invalidate the patch #3 assertion.
        ChatFlowNode coupon = graph.nodeById("ai-coupon-code").orElseThrow();
        ChatFlowNode deliver = graph.nodeById("ai-deliver").orElseThrow();
        assertThat(coupon.toolsEnabled())
                .as("ai-coupon-code MUST declare toolsEnabled=false (drift here breaks patch #3)")
                .isEqualTo(Boolean.FALSE);
        assertThat(deliver.toolsEnabled())
                .as("ai-deliver MUST also declare toolsEnabled=false — the tool-free fixture's "
                        + "name implies the WHOLE chain. Without this, HEURISTIC's earlyAdvance "
                        + "would move the cursor here and patch #3's structural assertion would "
                        + "see toolsEnabled=null (true by default).")
                .isEqualTo(Boolean.FALSE);

        summarize("tool-free-node", graph);
    }

    @Test
    void personaSwitchMidFlowFixture_importsAndParsesCleanly() throws IOException {
        ExportEntry entry = ChatFlowImportTestUtil.loadFromClasspath(FIXTURE_PERSONA_SWAP);
        TurAIAgent agent = createAgent("smoke-persona-swap", null);
        Imported imp = ChatFlowImportTestUtil.importIntoAgent(agent, entry, importRepos());

        assertThat(imp.flow().getId()).isNotBlank();
        // The import returns "the last persona created" — for this fixture
        // that's Lucas (declared second). Verify BOTH personas landed by
        // re-querying the agent.
        TurAIAgent reloaded = agentRepository.findById(agent.getId()).orElseThrow();
        assertThat(reloaded.getPersonas())
                .as("Both Marina and Lucas must be linked to the agent post-import")
                .extracting(p -> p.getName())
                .anyMatch(name -> name.contains("Marina"))
                .anyMatch(name -> name.contains("Lucas"));

        ChatFlowGraph graph = engine.parseGraph(imp.flow()).orElseThrow();
        assertThat(graph.nodeById("persona-marina")).isPresent();
        assertThat(graph.nodeById("persona-lucas")).isPresent();
        assertThat(graph.nodeById("ai-name")).isPresent();
        assertThat(graph.nodeById("switch-route")).isPresent();
        assertThat(graph.nodeById("ai-objetivo")).isPresent();

        // The personaId on each persona node MUST have been rewritten by
        // the import to a real UUID — verifies the rewritePersonaIds pass
        // of ChatFlowImportTestUtil ran.
        ChatFlowNode marinaNode = graph.nodeById("persona-marina").orElseThrow();
        ChatFlowNode lucasNode = graph.nodeById("persona-lucas").orElseThrow();
        assertThat(marinaNode.personaId())
                .as("Marina node's personaId must be the persisted UUID, not the export's 'HARNESS_PERSONA_MARINA'")
                .doesNotStartWith("HARNESS_PERSONA_");
        assertThat(lucasNode.personaId())
                .as("Lucas node's personaId must be the persisted UUID, not the export's 'HARNESS_PERSONA_LUCAS'")
                .doesNotStartWith("HARNESS_PERSONA_");
        assertThat(marinaNode.personaId()).isNotEqualTo(lucasNode.personaId());

        summarize("persona-switch-mid-flow", graph);
    }

    @Test
    void regenWithChipsFixture_importsAndParsesCleanly() throws IOException {
        ExportEntry entry = ChatFlowImportTestUtil.loadFromClasspath(FIXTURE_REGEN_CHIPS);
        TurAIAgent agent = createAgent("smoke-regen-chips", null);
        Imported imp = ChatFlowImportTestUtil.importIntoAgent(agent, entry, importRepos());

        assertThat(imp.flow().getId()).isNotBlank();

        ChatFlowGraph graph = engine.parseGraph(imp.flow()).orElseThrow();
        assertThat(graph.nodeById("ai-cargo")).isPresent();
        assertThat(graph.nodeById("switch-area")).isPresent();
        assertThat(graph.nodeById("ai-decision-role")).isPresent();

        // The 4 chip labels are THE CONTRACT for patch #13. If a label is
        // renamed in the fixture, structural tests that substring-assert
        // for "RH/L&D" / "Gestor" / "C-level" / "Comprador" silently break.
        ChatFlowNode roleNode = graph.nodeById("ai-decision-role").orElseThrow();
        assertThat(roleNode.inlineOptions())
                .as("Patch #13 contract: the 4 chip labels must remain EXACTLY these strings")
                .containsExactly("RH/L&D", "Gestor", "C-level", "Comprador");

        summarize("regen-with-chips", graph);
    }

    /**
     * Prints a compact one-liner summary of a parsed graph to
     * {@code System.out}. Output goes to the failsafe report so a
     * developer browsing the report can eyeball that the right fixture
     * loaded without re-running the test in debug.
     */
    private static void summarize(String fixtureName, ChatFlowGraph graph) {
        long aiQuestions = graph.nodes().stream().filter(n -> "aiQuestion".equals(n.type())).count();
        long switches    = graph.nodes().stream().filter(n -> "switch".equals(n.type())).count();
        long personas    = graph.nodes().stream().filter(n -> "persona".equals(n.type())).count();
        System.out.printf("[HarnessFixtureSmokeIT] %s: %d nodes (%d aiQuestion + %d switch + %d persona), %d edges%n",
                fixtureName,
                graph.nodes().size(), aiQuestions, switches, personas,
                graph.edges().size());
    }
}
