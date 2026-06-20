/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import com.viglet.turing.genai.flow.strategy.TurChatFlowGuardrailStrategy;
import com.viglet.turing.persistence.dto.agent.TurChatFlowLintIssueDto;
import com.viglet.turing.persistence.dto.agent.TurChatFlowTriggerConflictDto;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlot;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowSubmissionRepository;
import com.viglet.turing.service.chatanalytics.TurChatAnalyticsService;
import com.viglet.turing.service.chatslots.TurChatSlotEventBus;

/**
 * Pins the T94 lint rule set. Each test isolates one rule by giving the
 * linter the minimum flow that exercises it; the cross-flow trigger rule
 * is verified separately because it stitches in {@link TurTriggerConflictService}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurChatFlowLinterServiceTest {

    private static final String AGENT_ID = "agent-1";

    @Mock
    private TurChatFlowStateRepository stateRepository;
    @Mock
    private TurChatFlowRepository chatFlowRepository;
    @Mock
    private TurChatFlowSubmissionRepository submissionRepository;
    @Mock
    private TurChatAnalyticsService chatAnalyticsService;
    @Mock
    private TurChatSlotEventBus slotEventBus;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private TurAIAgentSlotRepository slotRepository;

    private TurTriggerConflictService triggerConflictService;
    private TurChatFlowLinterService linter;

    @BeforeEach
    void setUp() {
        TurChatFlowEngineService engine = new TurChatFlowEngineService(stateRepository,
                chatFlowRepository, submissionRepository, chatAnalyticsService,
                slotEventBus,
                org.mockito.Mockito.mock(
                        com.viglet.turing.service.chatslots.TurChatSlotAuditService.class),
                org.mockito.Mockito.mock(
                        com.viglet.turing.service.chatanalytics.TurAbBanditService.class),
                cacheManager,
                org.mockito.Mockito.mock(TurFunctionCallNodeExecutor.class),
                org.mockito.Mockito.mock(
                        com.viglet.turing.genai.flow.routine.TurScheduleAgentNodeExecutor.class),
                org.mockito.Mockito.mock(TurChatWebhookNodeExecutor.class),
                org.mockito.Mockito.mock(TurHumanApprovalNodeExecutor.class),
                org.mockito.Mockito.mock(TurChatFlowTriggerRouter.class),
                List.<TurChatFlowGuardrailStrategy>of());
        triggerConflictService = new TurTriggerConflictService(chatFlowRepository);
        linter = new TurChatFlowLinterService(chatFlowRepository, engine, triggerConflictService,
                slotRepository);
    }

    @Test
    void flagsAiInstructionsOverFiveHundredChars() {
        String longInstr = "a".repeat(TurChatFlowLinterService.AI_INSTRUCTION_LIMIT + 50);
        String definition = "{"
                + "\"nodes\":["
                + "  {\"id\":\"start\",\"type\":\"start\",\"data\":{}},"
                + "  {\"id\":\"q\",\"type\":\"aiQuestion\",\"data\":{"
                + "      \"label\":\"x\",\"outputVariable\":\"name\","
                + "      \"aiInstruction\":\"" + longInstr + "\"}},"
                + "  {\"id\":\"end\",\"type\":\"end\",\"data\":{}}"
                + "],"
                + "\"edges\":["
                + "  {\"id\":\"e1\",\"source\":\"start\",\"target\":\"q\"},"
                + "  {\"id\":\"e2\",\"source\":\"q\",\"target\":\"end\",\"label\":\"continue\"}"
                + "]}";
        TurChatFlow flow = flow("flow-1", definition);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(flow));

        List<TurChatFlowLintIssueDto> issues = linter.lint(AGENT_ID, flow);

        assertThat(issues).extracting(TurChatFlowLintIssueDto::code)
                .contains("ai_instruction_too_long");
    }

    @Test
    void flagsUnusedOutputVariable() {
        // Captures `area` but nothing references it anywhere on the agent.
        String definition = "{"
                + "\"nodes\":["
                + "  {\"id\":\"start\",\"type\":\"start\",\"data\":{}},"
                + "  {\"id\":\"q\",\"type\":\"aiQuestion\",\"data\":{"
                + "      \"label\":\"area?\",\"outputVariable\":\"area\","
                + "      \"aiInstruction\":\"Ask about the area\"}},"
                + "  {\"id\":\"end\",\"type\":\"end\",\"data\":{}}"
                + "],"
                + "\"edges\":["
                + "  {\"id\":\"e1\",\"source\":\"start\",\"target\":\"q\"},"
                + "  {\"id\":\"e2\",\"source\":\"q\",\"target\":\"end\",\"label\":\"continue\"}"
                + "]}";
        TurChatFlow flow = flow("flow-1", definition);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(flow));

        List<TurChatFlowLintIssueDto> issues = linter.lint(AGENT_ID, flow);

        assertThat(issues).extracting(TurChatFlowLintIssueDto::code)
                .contains("unused_output_variable");
    }

    @Test
    void doesNotFlagOutputVariableConsumedByInterpolationDownstream() {
        // `area` is consumed by a downstream node's aiInstruction via {{area}}.
        String definition = "{"
                + "\"nodes\":["
                + "  {\"id\":\"start\",\"type\":\"start\",\"data\":{}},"
                + "  {\"id\":\"q\",\"type\":\"aiQuestion\",\"data\":{"
                + "      \"label\":\"area?\",\"outputVariable\":\"area\","
                + "      \"aiInstruction\":\"Ask\"}},"
                + "  {\"id\":\"summary\",\"type\":\"aiQuestion\",\"data\":{"
                + "      \"label\":\"sum\",\"outputVariable\":\"ack\","
                + "      \"aiInstruction\":\"Summarise: {{area}}\"}},"
                + "  {\"id\":\"end\",\"type\":\"end\",\"data\":{}}"
                + "],"
                + "\"edges\":["
                + "  {\"id\":\"e1\",\"source\":\"start\",\"target\":\"q\"},"
                + "  {\"id\":\"e2\",\"source\":\"q\",\"target\":\"summary\",\"label\":\"continue\"},"
                + "  {\"id\":\"e3\",\"source\":\"summary\",\"target\":\"end\",\"label\":\"continue\"}"
                + "]}";
        TurChatFlow flow = flow("flow-1", definition);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(flow));

        List<TurChatFlowLintIssueDto> issues = linter.lint(AGENT_ID, flow);

        assertThat(issues)
                .filteredOn(i -> "unused_output_variable".equals(i.code())
                        && "q".equals(i.nodeId()))
                .as("`area` is consumed downstream — must not be flagged as unused")
                .isEmpty();
    }

    @Test
    void doesNotFlagOutputVariableConsumedByAnotherFlowsInheritance() {
        // Flow A captures `cargo_atual`; flow B inherits it via slotInheritanceJson.
        // Without cross-flow scanning the linter would falsely warn on A.
        String defA = "{"
                + "\"nodes\":["
                + "  {\"id\":\"start\",\"type\":\"start\",\"data\":{}},"
                + "  {\"id\":\"q\",\"type\":\"aiQuestion\",\"data\":{"
                + "      \"label\":\"cargo?\",\"outputVariable\":\"cargo_atual\","
                + "      \"aiInstruction\":\"Ask\"}},"
                + "  {\"id\":\"end\",\"type\":\"end\",\"data\":{}}"
                + "],"
                + "\"edges\":["
                + "  {\"id\":\"e1\",\"source\":\"start\",\"target\":\"q\"},"
                + "  {\"id\":\"e2\",\"source\":\"q\",\"target\":\"end\",\"label\":\"continue\"}"
                + "]}";
        String defB = "{\"nodes\":[],\"edges\":[]}";
        TurChatFlow flowA = flow("flow-a", defA);
        TurChatFlow flowB = flow("flow-b", defB);
        flowB.setSlotInheritanceJson("{\"position\":\"cargo_atual\"}");
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(flowA, flowB));

        List<TurChatFlowLintIssueDto> issues = linter.lint(AGENT_ID, flowA);

        assertThat(issues)
                .filteredOn(i -> "unused_output_variable".equals(i.code()))
                .as("`cargo_atual` is sourced by flow B's inheritance map — must not be flagged")
                .isEmpty();
    }

    @Test
    void flagsDeadEndNodeAsError() {
        // q has no outgoing edge.
        String definition = "{"
                + "\"nodes\":["
                + "  {\"id\":\"start\",\"type\":\"start\",\"data\":{}},"
                + "  {\"id\":\"q\",\"type\":\"aiQuestion\",\"data\":{"
                + "      \"label\":\"x\",\"outputVariable\":\"name\"}}"
                + "],"
                + "\"edges\":["
                + "  {\"id\":\"e1\",\"source\":\"start\",\"target\":\"q\"}"
                + "]}";
        TurChatFlow flow = flow("flow-1", definition);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(flow));

        List<TurChatFlowLintIssueDto> issues = linter.lint(AGENT_ID, flow);

        assertThat(issues)
                .filteredOn(i -> "dead_end_node".equals(i.code()))
                .extracting(TurChatFlowLintIssueDto::severity, TurChatFlowLintIssueDto::nodeId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("ERROR", "q"));
    }

    @Test
    void flagsBranchingEdgeWithoutHandleOrLabel() {
        // Switch node with one edge missing both sourceHandle and label.
        String definition = "{"
                + "\"nodes\":["
                + "  {\"id\":\"start\",\"type\":\"start\",\"data\":{}},"
                + "  {\"id\":\"sw\",\"type\":\"switch\",\"data\":{"
                + "      \"label\":\"x\",\"switchVariable\":\"area\","
                + "      \"switchOptions\":[{\"id\":\"a\",\"label\":\"A\"}]}},"
                + "  {\"id\":\"end\",\"type\":\"end\",\"data\":{}}"
                + "],"
                + "\"edges\":["
                + "  {\"id\":\"e1\",\"source\":\"start\",\"target\":\"sw\"},"
                + "  {\"id\":\"e2\",\"source\":\"sw\",\"target\":\"end\"}"
                + "]}";
        TurChatFlow flow = flow("flow-1", definition);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(flow));

        List<TurChatFlowLintIssueDto> issues = linter.lint(AGENT_ID, flow);

        assertThat(issues)
                .filteredOn(i -> "edge_without_label".equals(i.code()))
                .extracting(TurChatFlowLintIssueDto::edgeId)
                .containsExactly("e2");
    }

    @Test
    void wiresInTriggerConflicts() {
        // Two flows on the agent share overlapping trigger descriptions —
        // the linter should surface that as a per-flow issue (T91 ↔ T94 stitch).
        String def = "{"
                + "\"nodes\":["
                + "  {\"id\":\"start\",\"type\":\"start\",\"data\":{}},"
                + "  {\"id\":\"end\",\"type\":\"end\",\"data\":{}}"
                + "],"
                + "\"edges\":[{\"id\":\"e1\",\"source\":\"start\",\"target\":\"end\"}]}";
        TurChatFlow flowA = flow("flow-a", def);
        flowA.setTriggerDescription(
                "Plano de carreira desenvolvimento profissional individual");
        flowA.setTriggerLanguage(
                com.viglet.turing.persistence.model.agent.TurChatFlowTriggerLanguage.PT);
        TurChatFlow flowB = flow("flow-b", def);
        flowB.setTriggerDescription(
                "Plano de carreira desenvolvimento profissional adicional");
        flowB.setTriggerLanguage(
                com.viglet.turing.persistence.model.agent.TurChatFlowTriggerLanguage.PT);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(flowA, flowB));

        // Sanity check that the conflict service really fires; the linter
        // depends on that — without the conflict, this test would falsely pass.
        List<TurChatFlowTriggerConflictDto> conflicts = triggerConflictService.detect(AGENT_ID);
        assertThat(conflicts).as("test fixture must produce a real conflict").isNotEmpty();

        List<TurChatFlowLintIssueDto> issues = linter.lint(AGENT_ID, flowA);
        assertThat(issues).extracting(TurChatFlowLintIssueDto::code)
                .contains("trigger_conflict");
    }

    @Test
    void cleanFlowReturnsEmptyIssueList() {
        String definition = "{"
                + "\"nodes\":["
                + "  {\"id\":\"start\",\"type\":\"start\",\"data\":{}},"
                + "  {\"id\":\"q\",\"type\":\"aiQuestion\",\"data\":{"
                + "      \"label\":\"x\",\"outputVariable\":\"name\","
                + "      \"aiInstruction\":\"Ask for name\"}},"
                + "  {\"id\":\"sum\",\"type\":\"aiQuestion\",\"data\":{"
                + "      \"label\":\"sum\",\"outputVariable\":\"ack\","
                + "      \"aiInstruction\":\"Hi {{name}}\"}},"
                + "  {\"id\":\"end\",\"type\":\"end\",\"data\":{}}"
                + "],"
                + "\"edges\":["
                + "  {\"id\":\"e1\",\"source\":\"start\",\"target\":\"q\"},"
                + "  {\"id\":\"e2\",\"source\":\"q\",\"target\":\"sum\",\"label\":\"continue\"},"
                + "  {\"id\":\"e3\",\"source\":\"sum\",\"target\":\"end\",\"label\":\"continue\"}"
                + "]}";
        TurChatFlow flow = flow("flow-1", definition);
        flow.setTriggerDescription("Distinctive trigger about onboarding only");
        lenient().when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(flow));

        // `ack` is captured but not consumed downstream — clean flow still has
        // that one warning, but no errors.
        List<TurChatFlowLintIssueDto> issues = linter.lint(AGENT_ID, flow);
        assertThat(issues).extracting(TurChatFlowLintIssueDto::severity)
                .isNotEmpty()
                .doesNotContain("ERROR");
    }

    @Test
    void flagsFormFieldWithUnknownSlot() {
        // Native form (T107) with two fields; only `name` is a declared slot.
        TurChatFlow flow = flow("flow-1", formFlow(
                "[{\"name\":\"name\",\"type\":\"text\",\"required\":true},"
                        + "{\"name\":\"email\",\"type\":\"email\",\"required\":true}]"));
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(flow));
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(slot("name")));

        List<TurChatFlowLintIssueDto> issues = linter.lint(AGENT_ID, flow);

        assertThat(issues)
                .filteredOn(i -> "form_field_unknown_slot".equals(i.code()))
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.severity()).isEqualTo("WARNING");
                    assertThat(i.message()).contains("email");
                });
    }

    @Test
    void flagsBlankFormFieldNameAsError() {
        TurChatFlow flow = flow("flow-1", formFlow(
                "[{\"name\":\"\",\"type\":\"text\"}]"));
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(flow));
        lenient().when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of());

        List<TurChatFlowLintIssueDto> issues = linter.lint(AGENT_ID, flow);

        assertThat(issues)
                .filteredOn(i -> "form_field_blank_name".equals(i.code()))
                .extracting(TurChatFlowLintIssueDto::severity)
                .containsExactly("ERROR");
    }

    @Test
    void flagsDuplicateFormFieldSlot() {
        // Two fields write the same slot `email`.
        TurChatFlow flow = flow("flow-1", formFlow(
                "[{\"name\":\"email\",\"type\":\"email\"},"
                        + "{\"name\":\"email\",\"type\":\"text\"}]"));
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(flow));
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(slot("email")));

        List<TurChatFlowLintIssueDto> issues = linter.lint(AGENT_ID, flow);

        assertThat(issues)
                .filteredOn(i -> "form_field_duplicate_name".equals(i.code()))
                .singleElement()
                .satisfies(i -> assertThat(i.message()).contains("email"));
    }

    @Test
    void cleanNativeFormHasNoFormFieldIssues() {
        TurChatFlow flow = flow("flow-1", formFlow(
                "[{\"name\":\"name\",\"type\":\"text\"},"
                        + "{\"name\":\"email\",\"type\":\"email\"}]"));
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(flow));
        when(slotRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(slot("name"), slot("email")));

        List<TurChatFlowLintIssueDto> issues = linter.lint(AGENT_ID, flow);

        // A fully clean native form produces no lint issues at all — assert the
        // strong, non-vacuous expectation rather than just the absence of the
        // form_field_* codes (which would pass trivially on an empty list).
        assertThat(issues).isEmpty();
    }

    @Test
    void flagsPlanningStepWhosePlanIsNeverConsumed() {
        // planningStep writes __plan but nothing iterates or interpolates it.
        String definition = "{"
                + "\"nodes\":["
                + "  {\"id\":\"start\",\"type\":\"start\",\"data\":{}},"
                + "  {\"id\":\"plan\",\"type\":\"planningStep\",\"data\":{"
                + "      \"label\":\"Plan\",\"outputVariable\":\"__plan\","
                + "      \"aiInstruction\":\"Break the goal into steps\"}},"
                + "  {\"id\":\"end\",\"type\":\"end\",\"data\":{}}"
                + "],"
                + "\"edges\":["
                + "  {\"id\":\"e1\",\"source\":\"start\",\"target\":\"plan\"},"
                + "  {\"id\":\"e2\",\"source\":\"plan\",\"target\":\"end\",\"label\":\"continue\"}"
                + "]}";
        TurChatFlow flow = flow("flow-1", definition);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(flow));

        List<TurChatFlowLintIssueDto> issues = linter.lint(AGENT_ID, flow);

        assertThat(issues)
                .filteredOn(i -> "planning_step_unused_plan".equals(i.code()))
                .extracting(TurChatFlowLintIssueDto::severity, TurChatFlowLintIssueDto::nodeId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("WARNING", "plan"));
    }

    @Test
    void doesNotFlagPlanningStepWhenIteratePlanConsumesThePlan() {
        // planningStep → iteratePlan reading the same __plan slot (with a body
        // sub-flow). Neither planning_step_unused_plan nor iterate_plan_no_body
        // should fire.
        String definition = "{"
                + "\"nodes\":["
                + "  {\"id\":\"start\",\"type\":\"start\",\"data\":{}},"
                + "  {\"id\":\"plan\",\"type\":\"planningStep\",\"data\":{"
                + "      \"label\":\"Plan\",\"outputVariable\":\"__plan\","
                + "      \"aiInstruction\":\"Break the goal into steps\"}},"
                + "  {\"id\":\"iter\",\"type\":\"iteratePlan\",\"data\":{"
                + "      \"label\":\"Iterate\",\"outputVariable\":\"__plan\","
                + "      \"subFlowId\":\"body-flow\"}},"
                + "  {\"id\":\"end\",\"type\":\"end\",\"data\":{}}"
                + "],"
                + "\"edges\":["
                + "  {\"id\":\"e1\",\"source\":\"start\",\"target\":\"plan\"},"
                + "  {\"id\":\"e2\",\"source\":\"plan\",\"target\":\"iter\",\"label\":\"continue\"},"
                + "  {\"id\":\"e3\",\"source\":\"iter\",\"target\":\"end\",\"label\":\"done\"}"
                + "]}";
        TurChatFlow flow = flow("flow-1", definition);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(flow));

        List<TurChatFlowLintIssueDto> issues = linter.lint(AGENT_ID, flow);

        // The plan is consumed by iteratePlan, so neither planning_step_unused_plan
        // nor iterate_plan_no_body should fire — and this clean flow yields no lint
        // issues at all, so assert the non-vacuous empty expectation.
        assertThat(issues).isEmpty();
    }

    @Test
    void flagsIteratePlanWithoutBodySubFlow() {
        String definition = "{"
                + "\"nodes\":["
                + "  {\"id\":\"start\",\"type\":\"start\",\"data\":{}},"
                + "  {\"id\":\"iter\",\"type\":\"iteratePlan\",\"data\":{"
                + "      \"label\":\"Iterate\",\"outputVariable\":\"__plan\"}},"
                + "  {\"id\":\"end\",\"type\":\"end\",\"data\":{}}"
                + "],"
                + "\"edges\":["
                + "  {\"id\":\"e1\",\"source\":\"start\",\"target\":\"iter\"},"
                + "  {\"id\":\"e2\",\"source\":\"iter\",\"target\":\"end\",\"label\":\"done\"}"
                + "]}";
        TurChatFlow flow = flow("flow-1", definition);
        when(chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(flow));

        List<TurChatFlowLintIssueDto> issues = linter.lint(AGENT_ID, flow);

        assertThat(issues)
                .filteredOn(i -> "iterate_plan_no_body".equals(i.code()))
                .extracting(TurChatFlowLintIssueDto::severity, TurChatFlowLintIssueDto::nodeId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("WARNING", "iter"));
    }

    /** A minimal flow whose single {@code formCapture} node carries the given JSON formFields array. */
    private static String formFlow(String formFieldsJson) {
        return "{"
                + "\"nodes\":["
                + "  {\"id\":\"start\",\"type\":\"start\",\"data\":{}},"
                + "  {\"id\":\"form\",\"type\":\"formCapture\",\"data\":{"
                + "      \"label\":\"Lead\",\"aiInstruction\":\"Fill in\","
                + "      \"formFields\":" + formFieldsJson + "}},"
                + "  {\"id\":\"end\",\"type\":\"end\",\"data\":{}}"
                + "],"
                + "\"edges\":["
                + "  {\"id\":\"e1\",\"source\":\"start\",\"target\":\"form\"},"
                + "  {\"id\":\"e2\",\"source\":\"form\",\"target\":\"end\",\"label\":\"continue\"}"
                + "]}";
    }

    private static TurAIAgentSlot slot(String name) {
        TurAIAgentSlot slot = new TurAIAgentSlot();
        slot.setName(name);
        return slot;
    }

    private static TurChatFlow flow(String id, String definitionJson) {
        TurChatFlow flow = new TurChatFlow();
        flow.setId(id);
        flow.setName(id);
        flow.setEnabled(1);
        flow.setDefinitionJson(definitionJson);
        com.viglet.turing.persistence.model.agent.TurAIAgent owning =
                new com.viglet.turing.persistence.model.agent.TurAIAgent();
        owning.setId(AGENT_ID);
        flow.setTurAIAgent(owning);
        return flow;
    }
}
