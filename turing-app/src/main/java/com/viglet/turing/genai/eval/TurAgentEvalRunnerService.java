/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.flow.TurChatFlowEngineService.ConversationStateDto;
import com.viglet.turing.persistence.dto.agent.TurAgentEvalCaseResultDto;
import com.viglet.turing.persistence.dto.agent.TurAgentEvalCaseResultDto.SlotDiff;
import com.viglet.turing.persistence.dto.agent.TurAgentEvalReportDto;
import com.viglet.turing.persistence.dto.agent.TurChatFlowSubmissionDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.persistence.model.agent.TurAgentEvalExpectedOutcome;
import com.viglet.turing.persistence.model.agent.TurAgentEvalReport;
import com.viglet.turing.persistence.model.agent.TurAgentEvalSet;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAgentEvalReportRepository;
import com.viglet.turing.persistence.repository.agent.TurAgentEvalSetRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowSubmissionRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * T286 / §XV.2 — the Agent-CI eval runner. Replays each {@link
 * TurAgentEvalCase} of an agent's enabled golden {@link TurAgentEvalSet}s
 * through the <b>real</b> {@link TurAgentChatExecutor} + {@link
 * TurChatFlowEngineService} (no mock harness — we want the production code
 * under test), captures the resulting slots / cursor / outcome, and scores
 * each case:
 *
 * <ul>
 *   <li><b>slots</b> — deterministic equality diff vs {@code expectedSlotsJson}.</li>
 *   <li><b>outcome</b> — terminal label (CAPTURED / ABANDONED / HANDOFF).</li>
 *   <li><b>node</b> — the cursor the replay lands on vs {@code expectedNodeId}.</li>
 *   <li><b>rubric</b> — a natural-language assertion scored by a bilingual
 *       LLM judge (JSON verdict, provider-agnostic).</li>
 * </ul>
 *
 * <p>Output is a persisted {@link TurAgentEvalReport}; when the run is green
 * it becomes the agent's new baseline. A run that breaks a baseline-green
 * case is flagged {@code regressed} so the pre-publish gate (T287) can block.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurAgentEvalRunnerService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurAIAgentRepository agentRepository;
    private final TurAgentEvalSetRepository evalSetRepository;
    private final TurAgentEvalReportRepository reportRepository;
    private final TurChatFlowEngineService chatFlowEngineService;
    private final TurChatFlowRepository chatFlowRepository;
    private final TurChatFlowSubmissionRepository submissionRepository;
    private final TurAgentChatExecutor agentChatExecutor;
    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService secretCryptoService;

    public TurAgentEvalRunnerService(TurAIAgentRepository agentRepository,
            TurAgentEvalSetRepository evalSetRepository,
            TurAgentEvalReportRepository reportRepository,
            TurChatFlowEngineService chatFlowEngineService,
            TurChatFlowRepository chatFlowRepository,
            TurChatFlowSubmissionRepository submissionRepository,
            TurAgentChatExecutor agentChatExecutor,
            TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurLlmModelFactory llmModelFactory,
            TurSecretCryptoService secretCryptoService) {
        this.agentRepository = agentRepository;
        this.evalSetRepository = evalSetRepository;
        this.reportRepository = reportRepository;
        this.chatFlowEngineService = chatFlowEngineService;
        this.chatFlowRepository = chatFlowRepository;
        this.submissionRepository = submissionRepository;
        this.agentChatExecutor = agentChatExecutor;
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.llmModelFactory = llmModelFactory;
        this.secretCryptoService = secretCryptoService;
    }

    // ─────────────────────────── Public API ───────────────────────────

    /**
     * Runs every <b>enabled</b> golden set of {@code agentId}, scores each
     * case, persists a {@link TurAgentEvalReport}, and updates the green
     * baseline / regression flags. Returns the report view.
     *
     * <p>Deliberately <b>not</b> {@code @Transactional}: a run makes many
     * real LLM calls and can take minutes — wrapping it would pin a DB
     * connection the whole time. Each engine turn manages its own
     * transaction; only the final {@link #persistReport} writes are batched.
     */
    public TurAgentEvalReportDto runAgent(String agentId) {
        TurAIAgent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return TurAgentEvalReportDto.error("Agent not found: " + agentId);
        }
        List<TurAgentEvalSet> sets = evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(agentId).stream()
                .filter(s -> s.getEnabled() == 1)
                .toList();
        if (sets.isEmpty()) {
            return TurAgentEvalReportDto.error("No enabled eval set configured for this agent");
        }
        TurLLMInstance llmInstance = resolveLlm(agent);
        if (llmInstance == null) {
            return TurAgentEvalReportDto.error(
                    "No usable LLM (attach an enabled LLM to the agent or set a default LLM)");
        }
        ChatModel judgeModel = createChatModel(llmInstance);

        List<TurAgentEvalCaseResultDto> results = new ArrayList<>();
        for (TurAgentEvalSet set : sets) {
            for (TurAgentEvalCase evalCase : orderedCases(set)) {
                results.add(runCase(agent, llmInstance, judgeModel, evalCase));
            }
        }
        return persistReport(agent, results);
    }

    /**
     * True when the agent has at least one enabled golden set and a usable
     * LLM — i.e. {@link #runAgent} would actually run rather than error out.
     */
    public boolean isAvailable(String agentId) {
        TurAIAgent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return false;
        }
        boolean hasSet = evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(agentId).stream()
                .anyMatch(s -> s.getEnabled() == 1 && !s.getCases().isEmpty());
        return hasSet && resolveLlm(agent) != null;
    }

    // ─────────────────────────── Per-case replay ───────────────────────────

    private TurAgentEvalCaseResultDto runCase(TurAIAgent agent, TurLLMInstance llmInstance,
            ChatModel judgeModel, TurAgentEvalCase evalCase) {
        String conversationId = "eval-" + UUID.randomUUID();
        try {
            List<String> turns = parseTurns(evalCase.getSeedTurnsJson());
            if (turns.isEmpty()) {
                return failed(evalCase, "Case has no seed turns");
            }
            replayTurns(agent, llmInstance, conversationId, turns);

            Map<String, String> capturedSlots =
                    chatFlowEngineService.listSlotsForConversation(conversationId).slots();
            ConversationStateDto state = chatFlowEngineService.getConversationState(conversationId);
            String finalNodeId = state.currentNodeId();
            String actualOutcome = resolveOutcome(conversationId, state);

            return scoreCase(evalCase, judgeModel, turns, capturedSlots, finalNodeId, actualOutcome);
        } catch (RuntimeException e) {
            log.warn("[AgentEval] case '{}' replay failed: {}", evalCase.getName(), e.getMessage());
            return failed(evalCase, e.getMessage());
        } finally {
            cleanupConversation(agent.getId(), conversationId);
        }
    }

    private void replayTurns(TurAIAgent agent, TurLLMInstance llmInstance,
            String conversationId, List<String> turns) {
        List<ChatMessageItem> history = new ArrayList<>();
        for (String turn : turns) {
            history.add(new ChatMessageItem("user", turn));
            String assistant = blockForAssistantReply(agent, llmInstance, history, conversationId);
            history.add(new ChatMessageItem("assistant", assistant));
        }
    }

    private String blockForAssistantReply(TurAIAgent agent, TurLLMInstance llmInstance,
            List<ChatMessageItem> history, String conversationId) {
        List<ChatResponse> emissions = agentChatExecutor
                .execute(agent, llmInstance, history, null, conversationId, null)
                .collectList()
                .block();
        if (emissions == null || emissions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatResponse r : emissions) {
            // Only token emissions are assistant text; "options" carries a
            // JSON chip array that isn't part of the spoken reply.
            if (r.type() == null || "token".equals(r.type())) {
                if (r.content() != null) {
                    sb.append(r.content());
                }
            }
        }
        return sb.toString();
    }

    // ─────────────────────────── Scoring ───────────────────────────

    private TurAgentEvalCaseResultDto scoreCase(TurAgentEvalCase evalCase, ChatModel judgeModel,
            List<String> turns, Map<String, String> capturedSlots,
            String finalNodeId, String actualOutcome) {
        List<Double> dimensionScores = new ArrayList<>();
        boolean allMatched = true;

        // Slots dimension.
        Map<String, String> expectedSlots = parseSlots(evalCase.getExpectedSlotsJson());
        List<SlotDiff> slotDiffs = new ArrayList<>();
        if (!expectedSlots.isEmpty()) {
            int matched = 0;
            for (Map.Entry<String, String> e : expectedSlots.entrySet()) {
                String actual = capturedSlots.get(e.getKey());
                boolean match = normalize(actual).equals(normalize(e.getValue()));
                if (match) {
                    matched++;
                }
                slotDiffs.add(new SlotDiff(e.getKey(), e.getValue(), actual, match));
            }
            double slotScore = (double) matched / expectedSlots.size();
            dimensionScores.add(slotScore);
            allMatched &= matched == expectedSlots.size();
        }

        // Outcome dimension.
        TurAgentEvalExpectedOutcome expectedOutcome = evalCase.getExpectedOutcome();
        if (expectedOutcome != null && expectedOutcome != TurAgentEvalExpectedOutcome.ANY) {
            boolean match = expectedOutcome.name().equals(actualOutcome);
            dimensionScores.add(match ? 1d : 0d);
            allMatched &= match;
        }

        // Node dimension.
        if (evalCase.getExpectedNodeId() != null && !evalCase.getExpectedNodeId().isBlank()) {
            boolean match = evalCase.getExpectedNodeId().equals(finalNodeId);
            dimensionScores.add(match ? 1d : 0d);
            allMatched &= match;
        }

        // Rubric dimension (LLM judge).
        String rubricVerdict = "na";
        String rubricRationale = null;
        if (evalCase.getRubric() != null && !evalCase.getRubric().isBlank()) {
            JudgeVerdict verdict = judge(judgeModel, evalCase.getRubric(), turns);
            rubricVerdict = verdict.pass() ? "pass" : "fail";
            rubricRationale = verdict.rationale();
            dimensionScores.add(verdict.score());
            allMatched &= verdict.pass();
        }

        double score = dimensionScores.isEmpty() ? 1d
                : dimensionScores.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
        boolean passed = allMatched;
        return new TurAgentEvalCaseResultDto(evalCase.getId(), evalCase.getName(), passed, score,
                expectedOutcome == null ? null : expectedOutcome.name(), actualOutcome,
                finalNodeId, slotDiffs, rubricVerdict, rubricRationale, null);
    }

    // ─────────────────────────── LLM judge ───────────────────────────

    private record JudgeVerdict(boolean pass, double score, String rationale) {
    }

    private JudgeVerdict judge(ChatModel judgeModel, String rubric, List<String> turns) {
        try {
            String transcript = String.join("\n", turns);
            String system = """
                    You are a strict, impartial conversation auditor for an AI agent.
                    Given a RUBRIC and the user's scripted turns of a conversation, decide
                    whether the agent's behaviour satisfies the rubric. Be conservative:
                    when in doubt, fail. Reply in the SAME language as the rubric.
                    Respond with ONLY a compact JSON object, no markdown fences, exactly:
                    {"verdict":"pass"|"fail","score":0.0-1.0,"rationale":"one short sentence"}
                    """;
            String user = "RUBRIC:\n" + rubric + "\n\nUSER TURNS:\n" + transcript;
            Prompt prompt = new Prompt(List.of(new SystemMessage(system), new UserMessage(user)));
            String reply = judgeModel.call(prompt).getResult().getOutput().getText();
            return parseVerdict(reply);
        } catch (RuntimeException e) {
            log.warn("[AgentEval] judge failed: {}", e.getMessage());
            return new JudgeVerdict(false, 0d, "judge error: " + e.getMessage());
        }
    }

    private JudgeVerdict parseVerdict(String reply) {
        if (reply == null || reply.isBlank()) {
            return new JudgeVerdict(false, 0d, "empty judge reply");
        }
        String json = extractJson(reply);
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
            String verdict = String.valueOf(parsed.getOrDefault("verdict", "fail")).trim().toLowerCase();
            boolean pass = "pass".equals(verdict);
            double score = parsed.get("score") instanceof Number n ? n.doubleValue() : (pass ? 1d : 0d);
            score = Math.max(0d, Math.min(1d, score));
            Object rationale = parsed.get("rationale");
            return new JudgeVerdict(pass, score, rationale == null ? null : String.valueOf(rationale));
        } catch (RuntimeException e) {
            log.warn("[AgentEval] could not parse judge JSON '{}': {}", json, e.getMessage());
            return new JudgeVerdict(false, 0d, "unparseable judge reply");
        }
    }

    /** Strips markdown fences / prose around the JSON object the judge returned. */
    private static String extractJson(String reply) {
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return reply.substring(start, end + 1);
        }
        return reply.trim();
    }

    // ─────────────────────────── Outcome resolution ───────────────────────────

    private String resolveOutcome(String conversationId, ConversationStateDto state) {
        // A finished root flow records a submission. endNodeId carrying an
        // "abandon" marker means the user quit; anything else is a capture.
        List<TurChatFlowSubmissionDto> subs =
                chatFlowEngineService.listSubmissionsForConversation(conversationId);
        if (!subs.isEmpty()) {
            String endNode = subs.get(0).endNodeId();
            if (endNode != null && endNode.toLowerCase().contains("abandon")) {
                return TurAgentEvalExpectedOutcome.ABANDONED.name();
            }
            return TurAgentEvalExpectedOutcome.CAPTURED.name();
        }
        // No submission: classify by the node the replay parked on.
        String nodeType = nodeType(state.flowId(), state.currentNodeId());
        if (nodeType != null) {
            String t = nodeType.toLowerCase();
            if (t.contains("handoff") || t.contains("human")) {
                return TurAgentEvalExpectedOutcome.HANDOFF.name();
            }
            if ("end".equals(t)) {
                return TurAgentEvalExpectedOutcome.CAPTURED.name();
            }
        }
        return TurAgentEvalExpectedOutcome.ABANDONED.name();
    }

    private String nodeType(String flowId, String nodeId) {
        if (flowId == null || nodeId == null) {
            return null;
        }
        Optional<TurChatFlow> flow = chatFlowRepository.findById(flowId);
        if (flow.isEmpty()) {
            return null;
        }
        Optional<ChatFlowGraph> graph = chatFlowEngineService.parseGraph(flow.get());
        if (graph.isEmpty()) {
            return null;
        }
        return graph.get().nodeById(nodeId).map(ChatFlowNode::type).orElse(null);
    }

    // ─────────────────────────── Report persistence ───────────────────────────

    private TurAgentEvalReportDto persistReport(TurAIAgent agent, List<TurAgentEvalCaseResultDto> results) {
        int caseCount = results.size();
        int passedCount = (int) results.stream().filter(TurAgentEvalCaseResultDto::passed).count();
        boolean passed = caseCount > 0 && passedCount == caseCount;
        double score = results.isEmpty() ? 0d
                : results.stream().mapToDouble(TurAgentEvalCaseResultDto::score).average().orElse(0d);

        // Compare to the green baseline to detect regressions.
        Optional<TurAgentEvalReport> baseline =
                reportRepository.findFirstByTurAIAgent_IdAndBaselineTrueOrderByCreatedAtDesc(agent.getId());
        boolean regressed = baseline.isPresent() && hasRegression(baseline.get(), results);

        TurAgentEvalReport report = new TurAgentEvalReport();
        report.setTurAIAgent(agent);
        report.setCreatedAt(LocalDateTime.now());
        report.setPassed(passed);
        report.setScore(score);
        report.setCaseCount(caseCount);
        report.setPassedCount(passedCount);
        report.setRegressed(regressed);
        report.setResultsJson(writeJson(results));

        // A green run becomes the new baseline (older baselines are demoted).
        if (passed) {
            reportRepository.findByTurAIAgent_IdAndBaselineTrue(agent.getId())
                    .forEach(prev -> {
                        prev.setBaseline(false);
                        reportRepository.save(prev);
                    });
            report.setBaseline(true);
        }
        report = reportRepository.save(report);

        return new TurAgentEvalReportDto(report.getId(), report.getCreatedAt(), passed, score,
                caseCount, passedCount, report.isBaseline(), regressed, results, null);
    }

    /**
     * True when any case that was green in the baseline flipped to red in the
     * new results.
     */
    private boolean hasRegression(TurAgentEvalReport baseline, List<TurAgentEvalCaseResultDto> results) {
        Map<String, Boolean> baselinePass = new LinkedHashMap<>();
        for (TurAgentEvalCaseResultDto prev : parseResults(baseline.getResultsJson())) {
            baselinePass.put(prev.caseId(), prev.passed());
        }
        for (TurAgentEvalCaseResultDto now : results) {
            if (Boolean.TRUE.equals(baselinePass.get(now.caseId())) && !now.passed()) {
                return true;
            }
        }
        return false;
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private TurLLMInstance resolveLlm(TurAIAgent agent) {
        if (agent.getLlmInstances() != null) {
            Optional<TurLLMInstance> agentLlm = agent.getLlmInstances().stream()
                    .filter(l -> l.getEnabled() == 1)
                    .findFirst();
            if (agentLlm.isPresent()) {
                return agentLlm.get();
            }
        }
        String defaultLlmId = globalSettingsService.getDefaultLlmId();
        if (defaultLlmId == null || defaultLlmId.isBlank()) {
            return null;
        }
        return llmInstanceRepository.findById(defaultLlmId)
                .filter(l -> l.getEnabled() == 1)
                .orElse(null);
    }

    private ChatModel createChatModel(TurLLMInstance llmInstance) {
        String apiKey = secretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
        return llmModelFactory.createChatModel(llmInstance, apiKey);
    }

    private void cleanupConversation(String agentId, String conversationId) {
        try {
            chatFlowEngineService.resetAllStatesForAgent(conversationId, agentId);
            submissionRepository.deleteAll(
                    submissionRepository.findByConversationIdOrderByCompletedAtDesc(conversationId));
        } catch (RuntimeException e) {
            log.debug("[AgentEval] cleanup skipped for conv={}: {}", conversationId, e.getMessage());
        }
    }

    private static List<TurAgentEvalCase> orderedCases(TurAgentEvalSet set) {
        return set.getCases().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .toList();
    }

    private static TurAgentEvalCaseResultDto failed(TurAgentEvalCase evalCase, String error) {
        return new TurAgentEvalCaseResultDto(evalCase.getId(), evalCase.getName(), false, 0d,
                evalCase.getExpectedOutcome() == null ? null : evalCase.getExpectedOutcome().name(),
                null, null, List.of(), "na", null, error);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private static List<String> parseTurns(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> turns = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
            return turns == null ? List.of() : turns;
        } catch (RuntimeException e) {
            log.warn("[AgentEval] bad seedTurnsJson: {}", e.getMessage());
            return List.of();
        }
    }

    private static Map<String, String> parseSlots(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> slots = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
            return slots == null ? Map.of() : slots;
        } catch (RuntimeException e) {
            log.warn("[AgentEval] bad expectedSlotsJson: {}", e.getMessage());
            return Map.of();
        }
    }

    private static String writeJson(List<TurAgentEvalCaseResultDto> results) {
        try {
            return OBJECT_MAPPER.writeValueAsString(results);
        } catch (RuntimeException e) {
            log.warn("[AgentEval] could not serialize results: {}", e.getMessage());
            return "[]";
        }
    }

    static List<TurAgentEvalCaseResultDto> parseResults(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<TurAgentEvalCaseResultDto> parsed = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
            return parsed == null ? List.of() : parsed;
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
