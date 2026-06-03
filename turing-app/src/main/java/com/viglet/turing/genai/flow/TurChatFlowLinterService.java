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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.dto.agent.TurChatFlowLintIssueDto;
import com.viglet.turing.persistence.dto.agent.TurChatFlowTriggerConflictDto;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlot;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;

/**
 * T94 / §VII.11.d — static analysis for chat-flow authoring. Renders a
 * sidebar of warnings in the admin editor so common authoring mistakes
 * (too-long aiInstruction, slot that nobody reads, dead-end node, ambiguous
 * trigger across flows, edge missing a label on a branching node) are
 * caught at edit time instead of through a customer complaint.
 *
 * <p>Rules:
 * <ul>
 *   <li><b>{@code ai_instruction_too_long}</b> (WARNING) — any node whose
 *       {@code aiInstruction} exceeds {@link #AI_INSTRUCTION_LIMIT} chars.
 *       The router reads the addendum every turn; verbose instructions
 *       inflate token cost and dilute the model's attention.</li>
 *   <li><b>{@code unused_output_variable}</b> (WARNING) — an
 *       {@code aiQuestion}/{@code formCapture} node whose
 *       {@code outputVariable} is never referenced downstream (no
 *       {@code {{slot}}} interpolation, no {@code switchVariable}, no
 *       slot inheritance map source in any of the agent's flows). Usually
 *       a renaming leftover or a forgotten consumer.</li>
 *   <li><b>{@code dead_end_node}</b> (ERROR) — a non-{@code end} node with
 *       no outgoing edges. Hitting it stalls the conversation forever.</li>
 *   <li><b>{@code trigger_conflict}</b> (WARNING / ERROR by T91 severity) —
 *       reuses {@link TurTriggerConflictService} so the linter and the
 *       trigger panel agree on what counts as ambiguous.</li>
 *   <li><b>{@code edge_without_label}</b> (WARNING) — an edge whose source
 *       is a {@code condition} or {@code switch} node and which carries no
 *       {@code sourceHandle} AND no {@code label}. The runtime needs the
 *       handle to pick the right branch; a missing one is ambiguous.</li>
 *   <li><b>{@code form_field_blank_name}</b> (ERROR) /
 *       <b>{@code form_field_duplicate_name}</b> (WARNING) /
 *       <b>{@code form_field_unknown_slot}</b> (WARNING) — T235 native-form
 *       ({@code formCapture} + {@code formFields}, T107) field checks: a field
 *       with no slot, two fields writing the same slot, and a field whose slot
 *       isn't declared on the agent (value lands outside the typed schema).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurChatFlowLinterService {

    /**
     * Soft cap on {@code aiInstruction} length. Above this the addendum
     * starts to dominate the system prompt and the LLM begins to ignore
     * earlier instructions. Tuned against the Education customer's flows —
     * everything that worked production-side fit under 500 chars; the
     * outliers were drafts that needed compression.
     */
    static final int AI_INSTRUCTION_LIMIT = 500;

    private static final Pattern SLOT_REFERENCE = Pattern.compile("\\{\\{\\s*([\\w.-]+)\\s*}}");
    private static final tools.jackson.databind.ObjectMapper STATIC_PARSE_MAPPER =
            new tools.jackson.databind.ObjectMapper();

    private final TurChatFlowRepository chatFlowRepository;
    private final TurChatFlowEngineService engineService;
    private final TurTriggerConflictService triggerConflictService;
    private final TurAIAgentSlotRepository slotRepository;

    public TurChatFlowLinterService(TurChatFlowRepository chatFlowRepository,
            TurChatFlowEngineService engineService,
            TurTriggerConflictService triggerConflictService,
            TurAIAgentSlotRepository slotRepository) {
        this.chatFlowRepository = chatFlowRepository;
        this.engineService = engineService;
        this.triggerConflictService = triggerConflictService;
        this.slotRepository = slotRepository;
    }

    /**
     * Lints {@code flow}. The {@code agentId} powers the cross-flow rules
     * ({@code unused_output_variable} scans every flow on the agent for
     * downstream readers; {@code trigger_conflict} delegates to
     * {@link TurTriggerConflictService}). Returns the issues newest-/loudest-
     * first: ERROR before WARNING before INFO, then ordered by node id for
     * deterministic UI sort.
     */
    public List<TurChatFlowLintIssueDto> lint(String agentId, TurChatFlow flow) {
        if (flow == null) {
            return List.of();
        }
        List<TurChatFlowLintIssueDto> issues = new ArrayList<>();
        Optional<ChatFlowGraph> graphOpt = engineService.parseGraph(flow);
        if (graphOpt.isPresent()) {
            ChatFlowGraph graph = graphOpt.get();
            // All flows on the agent — needed for cross-flow slot-usage
            // detection. Empty list defends against blank agent id passed
            // by callers that only have the flow in hand.
            List<TurChatFlow> agentFlows = agentId == null || agentId.isBlank()
                    ? List.of(flow)
                    : chatFlowRepository.findByTurAIAgent_IdOrderByNameAsc(agentId);
            checkAiInstructionLength(graph, issues);
            checkUnusedOutputVariables(flow, graph, agentFlows, issues);
            checkDeadEndNodes(graph, issues);
            checkBranchingEdgeLabels(graph, issues);
            checkFormFields(graph, agentId, issues);
            checkPlanningNodes(graph, issues);
        }
        if (agentId != null && !agentId.isBlank()) {
            checkTriggerConflicts(agentId, flow, issues);
        }
        issues.sort((a, b) -> {
            int byPriority = priority(a.severity()) - priority(b.severity());
            if (byPriority != 0) return byPriority;
            String aKey = a.nodeId() == null ? "" : a.nodeId();
            String bKey = b.nodeId() == null ? "" : b.nodeId();
            return aKey.compareTo(bKey);
        });
        return issues;
    }

    private static int priority(String severity) {
        if ("ERROR".equals(severity)) return 0;
        if ("WARNING".equals(severity)) return 1;
        return 2;
    }

    private static void checkAiInstructionLength(ChatFlowGraph graph,
            List<TurChatFlowLintIssueDto> out) {
        for (ChatFlowNode node : graph.nodes()) {
            String instr = node.aiInstruction();
            if (instr != null && instr.length() > AI_INSTRUCTION_LIMIT) {
                out.add(new TurChatFlowLintIssueDto(
                        node.id(), null, "WARNING", "ai_instruction_too_long",
                        "aiInstruction is " + instr.length() + " chars (soft limit "
                                + AI_INSTRUCTION_LIMIT + ")",
                        "Tighten the instruction or split the node — verbose addenda inflate"
                                + " token cost and dilute the model's attention."));
            }
        }
    }

    private static void checkUnusedOutputVariables(TurChatFlow currentFlow, ChatFlowGraph graph,
            List<TurChatFlow> agentFlows, List<TurChatFlowLintIssueDto> out) {
        // Build the set of every slot name referenced anywhere on the agent
        // — by any flow's interpolation tokens, switch variables, slot
        // operations, or slotInheritanceJson source names. A node is
        // "consuming" the slot if it appears in any of these positions.
        Set<String> referenced = collectReferencedSlots(agentFlows);
        for (ChatFlowNode node : graph.nodes()) {
            String type = node.type();
            if (!"aiQuestion".equals(type) && !"formCapture".equals(type)) {
                continue;
            }
            String slot = node.outputVariable();
            if (slot == null || slot.isBlank()) continue;
            if (referenced.contains(slot)) continue;
            out.add(new TurChatFlowLintIssueDto(
                    node.id(), null, "WARNING", "unused_output_variable",
                    "Slot '" + slot + "' is captured here but never read downstream",
                    "Either consume it (use {{" + slot + "}} in another node, drive a switch on it,"
                            + " or declare it as an inheritance source on another flow) or remove the"
                            + " outputVariable."));
        }
        if (currentFlow == null) return; // Defensive — never null in practice but the test exercises this.
    }

    private static Set<String> collectReferencedSlots(List<TurChatFlow> flows) {
        Set<String> out = new HashSet<>();
        for (TurChatFlow flow : flows) {
            // Interpolation in any text field: aiInstruction, slotValue, label.
            Optional<ChatFlowGraph> graphOpt = TurChatFlowLinterService.staticParse(flow);
            if (graphOpt.isPresent()) {
                ChatFlowGraph graph = graphOpt.get();
                for (ChatFlowNode node : graph.nodes()) {
                    collectInterpolated(out, node.aiInstruction());
                    collectInterpolated(out, node.slotValue());
                    collectInterpolated(out, node.label());
                    if (node.switchVariable() != null && !node.switchVariable().isBlank()) {
                        out.add(node.switchVariable().trim());
                    }
                    if ("slot".equals(node.type()) && node.slotName() != null
                            && !node.slotName().isBlank()) {
                        out.add(node.slotName().trim());
                    }
                    if ("writeSlot".equals(node.type()) && node.outputVariable() != null
                            && !node.outputVariable().isBlank()) {
                        // writeSlot's outputVariable is the destination — but the value field can
                        // reference others. Both directions count as references.
                        out.add(node.outputVariable().trim());
                    }
                    collectInterpolated(out, node.validationRule());
                    collectInterpolated(out, node.conditionExpression());
                }
            }
            // Slot inheritance: sources declared by this flow count as
            // downstream readers of upstream slots.
            String mapping = flow.getSlotInheritanceJson();
            if (mapping != null && !mapping.isBlank()) {
                Matcher m = SLOT_REFERENCE.matcher(mapping);
                while (m.find()) {
                    out.add(m.group(1));
                }
                // Quick-and-cheap: also pick string literals on the right-
                // hand side of a JSON object via a regex; the linter doesn't
                // need to formally parse the mapping for the unused-slot
                // detector (false-positive avoidance), and unrelated tokens
                // don't typically match slot names.
                Matcher q = Pattern.compile("\"([\\w.-]+)\"\\s*:\\s*\"([\\w.-]+)\"")
                        .matcher(mapping);
                while (q.find()) {
                    out.add(q.group(2));
                }
            }
        }
        return out;
    }

    private static void collectInterpolated(Set<String> out, String text) {
        if (text == null || text.isEmpty()) return;
        Matcher m = SLOT_REFERENCE.matcher(text);
        while (m.find()) {
            out.add(m.group(1));
        }
    }

    private static void checkDeadEndNodes(ChatFlowGraph graph,
            List<TurChatFlowLintIssueDto> out) {
        Set<String> sources = new HashSet<>();
        for (ChatFlowEdge edge : graph.edges()) {
            if (edge.source() != null) sources.add(edge.source());
        }
        for (ChatFlowNode node : graph.nodes()) {
            if ("end".equals(node.type())) continue;
            if (sources.contains(node.id())) continue;
            out.add(new TurChatFlowLintIssueDto(
                    node.id(), null, "ERROR", "dead_end_node",
                    "Node has no outgoing edge — the conversation stalls when it reaches here",
                    "Wire an edge to the next node, or change the type to 'end' if this is a"
                            + " legitimate terminal."));
        }
    }

    private static void checkBranchingEdgeLabels(ChatFlowGraph graph,
            List<TurChatFlowLintIssueDto> out) {
        // condition/switch sources need the engine to disambiguate which
        // branch a transition belongs to. The runtime keys on sourceHandle
        // first ("yes"/"no" for conditions, the switch option id for
        // switches); an explicit label is fallback / documentation. An edge
        // missing BOTH is the danger zone.
        java.util.Map<String, String> nodeTypeById = new java.util.HashMap<>();
        for (ChatFlowNode node : graph.nodes()) {
            nodeTypeById.put(node.id(), node.type());
        }
        for (ChatFlowEdge edge : graph.edges()) {
            String type = nodeTypeById.get(edge.source());
            if (!"condition".equals(type) && !"switch".equals(type)) {
                continue;
            }
            boolean missingHandle = edge.sourceHandle() == null || edge.sourceHandle().isBlank();
            boolean missingLabel = edge.label() == null || edge.label().isBlank();
            if (missingHandle && missingLabel) {
                out.add(new TurChatFlowLintIssueDto(
                        edge.source(), edge.id(), "WARNING", "edge_without_label",
                        "Edge from " + type + " node has no sourceHandle and no label",
                        "Pick which branch this edge represents ('yes'/'no' for condition, an option"
                                + " id for switch) — the runtime cannot disambiguate without one."));
            }
        }
    }

    /**
     * T235 — native-form ({@code formCapture} + {@code formFields}, T107)
     * field validation. Three codes:
     * <ul>
     *   <li>{@code form_field_blank_name} (ERROR) — a field with no slot name:
     *       its value can never be captured.</li>
     *   <li>{@code form_field_duplicate_name} (WARNING) — two fields writing the
     *       same slot; the later submitted value silently wins.</li>
     *   <li>{@code form_field_unknown_slot} (WARNING) — a field whose slot is
     *       not declared on the agent, so the value lands outside the typed
     *       schema and no downstream reader (switch / interpolation / handoff)
     *       can see it. Only checked when {@code agentId} resolves a catalog.</li>
     * </ul>
     */
    private void checkFormFields(ChatFlowGraph graph, String agentId,
            List<TurChatFlowLintIssueDto> out) {
        List<ChatFlowNode> formNodes = new ArrayList<>();
        for (ChatFlowNode node : graph.nodes()) {
            if ("formCapture".equals(node.type()) && !node.formFields().isEmpty()) {
                formNodes.add(node);
            }
        }
        if (formNodes.isEmpty()) {
            // No native forms on this flow — skip the slot-catalog lookup.
            return;
        }
        Set<String> declaredSlots = null;
        if (agentId != null && !agentId.isBlank()) {
            declaredSlots = new HashSet<>();
            for (TurAIAgentSlot slot : slotRepository.findByTurAIAgent_IdOrderByNameAsc(agentId)) {
                if (slot.getName() != null && !slot.getName().isBlank()) {
                    declaredSlots.add(slot.getName().trim());
                }
            }
        }
        for (ChatFlowNode node : formNodes) {
            Set<String> seen = new HashSet<>();
            for (ChatFlowNode.FormField field : node.formFields()) {
                String name = field == null ? null : field.name();
                if (name == null || name.isBlank()) {
                    out.add(new TurChatFlowLintIssueDto(node.id(), null, "ERROR",
                            "form_field_blank_name",
                            "A form field has no slot name — its value cannot be captured",
                            "Pick a declared slot for every field on the form."));
                    continue;
                }
                String trimmed = name.trim();
                if (!seen.add(trimmed)) {
                    out.add(new TurChatFlowLintIssueDto(node.id(), null, "WARNING",
                            "form_field_duplicate_name",
                            "Two form fields write the same slot '" + trimmed
                                    + "' — the later value wins",
                            "Give each field its own slot, or remove the duplicate field."));
                }
                if (declaredSlots != null && !declaredSlots.contains(trimmed)) {
                    out.add(new TurChatFlowLintIssueDto(node.id(), null, "WARNING",
                            "form_field_unknown_slot",
                            "Form field writes slot '" + trimmed
                                    + "' which is not declared on the agent",
                            "Declare the slot in the agent's slot catalog so the value lands in the"
                                    + " typed schema and is readable downstream."));
                }
            }
        }
    }

    /**
     * T108 — planning-node validation:
     * <ul>
     *   <li>{@code planning_step_unused_plan} (WARNING) — a {@code planningStep}
     *       whose plan slot is never consumed in this flow (no {@code iteratePlan}
     *       reads it and no {@code {{slot}}} interpolation references it). The
     *       expensive LLM planning call produces a value nobody walks.</li>
     *   <li>{@code iterate_plan_no_body} (WARNING) — an {@code iteratePlan} with
     *       no {@code subFlowId} (body flow). Without a body it just drains the
     *       plan marking items done without executing anything per item.</li>
     * </ul>
     *
     * <p>Scoped to the single flow graph — the common authoring shape keeps the
     * {@code planningStep} and its {@code iteratePlan} consumer in the same flow.
     */
    private static void checkPlanningNodes(ChatFlowGraph graph,
            List<TurChatFlowLintIssueDto> out) {
        boolean hasPlanningNodes = false;
        for (ChatFlowNode node : graph.nodes()) {
            if ("planningStep".equals(node.type()) || "iteratePlan".equals(node.type())) {
                hasPlanningNodes = true;
                break;
            }
        }
        if (!hasPlanningNodes) {
            return;
        }
        // Slots consumed as a plan: every iteratePlan's plan slot, plus any
        // {{slot}} interpolation anywhere in the graph (a planningStep whose
        // plan an author renders via {{__plan}} counts as consumed too).
        Set<String> consumed = new HashSet<>();
        for (ChatFlowNode node : graph.nodes()) {
            if ("iteratePlan".equals(node.type())) {
                consumed.add(planSlotName(node));
            }
            collectInterpolated(consumed, node.aiInstruction());
            collectInterpolated(consumed, node.slotValue());
            collectInterpolated(consumed, node.label());
            collectInterpolated(consumed, node.validationRule());
            collectInterpolated(consumed, node.conditionExpression());
        }
        for (ChatFlowNode node : graph.nodes()) {
            if ("planningStep".equals(node.type()) && !consumed.contains(planSlotName(node))) {
                out.add(new TurChatFlowLintIssueDto(
                        node.id(), null, "WARNING", "planning_step_unused_plan",
                        "Plan slot '" + planSlotName(node) + "' is generated here but never iterated"
                                + " or read downstream",
                        "Add an iteratePlan node reading this slot, or render it with {{"
                                + planSlotName(node) + "}} — otherwise the planning LLM call is wasted."));
            }
            if ("iteratePlan".equals(node.type())
                    && (node.subFlowId() == null || node.subFlowId().isBlank())) {
                out.add(new TurChatFlowLintIssueDto(
                        node.id(), null, "WARNING", "iterate_plan_no_body",
                        "iteratePlan has no body sub-flow — it will mark every plan item done"
                                + " without executing anything",
                        "Set the node's sub-flow (subFlowId) to the flow that should run once per"
                                + " plan item."));
            }
        }
    }

    /** Plan slot an iteratePlan/planningStep node operates on (outputVariable, default {@code __plan}). */
    private static String planSlotName(ChatFlowNode node) {
        String slot = node.outputVariable();
        return (slot == null || slot.isBlank()) ? "__plan" : slot.trim();
    }

    private void checkTriggerConflicts(String agentId, TurChatFlow flow,
            List<TurChatFlowLintIssueDto> out) {
        List<TurChatFlowTriggerConflictDto> conflicts = triggerConflictService.detect(agentId);
        for (TurChatFlowTriggerConflictDto c : conflicts) {
            boolean involvesThisFlow = flow.getId() != null
                    && (flow.getId().equals(c.flowAId()) || flow.getId().equals(c.flowBId()));
            if (!involvesThisFlow) continue;
            String other = flow.getId().equals(c.flowAId()) ? c.flowBName() : c.flowAName();
            String severity = "HIGH".equals(c.severity()) ? "ERROR" : "WARNING";
            out.add(new TurChatFlowLintIssueDto(
                    null, null, severity, "trigger_conflict",
                    "Trigger description overlaps with flow '" + other + "' ("
                            + Math.round(c.similarity() * 100) + "% Jaccard)",
                    c.suggestion()));
        }
    }

    /** Mirror of {@link TurChatFlowEngineService#parseGraph} for the static rules above. */
    private static Optional<ChatFlowGraph> staticParse(TurChatFlow flow) {
        if (flow == null || flow.getDefinitionJson() == null
                || flow.getDefinitionJson().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(STATIC_PARSE_MAPPER
                    .readValue(flow.getDefinitionJson(), ChatFlowGraph.class));
        } catch (tools.jackson.core.JacksonException e) {
            return Optional.empty();
        }
    }
}
