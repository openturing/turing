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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurAgentChatRequest;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.flow.ChatFlowGraph;
import com.viglet.turing.genai.flow.ChatFlowNode;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.flow.TurChatFlowEngineService.ConversationStateDto;
import com.viglet.turing.genai.eval.grader.TurEvalBuiltinGraders;
import com.viglet.turing.genai.eval.grader.TurEvalGrader;
import com.viglet.turing.genai.eval.grader.TurEvalGraderResult;
import com.viglet.turing.genai.eval.grader.TurEvalGradingContext;
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
    private final com.viglet.turing.genai.batch.TurBatchInferenceService batchInferenceService;
    private final com.viglet.turing.genai.nativeapi.openai.TurOpenAiEvalsService openAiEvalsService;
    private final com.viglet.turing.properties.TurConfigProperties configProperties;
    private final com.viglet.turing.genai.eval.grader.TurEvalGraderRegistry graderRegistry;

    /** Used by {@link #scoreWithSystemPrompt} to detach the agent before the
     *  in-memory prompt swap, so a trial run can never flush to the DB. */
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

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
            TurSecretCryptoService secretCryptoService,
            com.viglet.turing.genai.batch.TurBatchInferenceService batchInferenceService,
            com.viglet.turing.genai.nativeapi.openai.TurOpenAiEvalsService openAiEvalsService,
            com.viglet.turing.properties.TurConfigProperties configProperties,
            com.viglet.turing.genai.eval.grader.TurEvalGraderRegistry graderRegistry) {
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
        this.batchInferenceService = batchInferenceService;
        this.openAiEvalsService = openAiEvalsService;
        this.configProperties = configProperties;
        this.graderRegistry = graderRegistry;
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

    /** Negative sentinel returned by {@link #scoreWithSystemPrompt} when the
     *  agent can't be evaluated (missing agent / no enabled set / no LLM). */
    public static final double SCORE_UNAVAILABLE = -1d;

    /**
     * T447 / §XXIII.6 — score the agent's enabled golden sets under an IN-MEMORY
     * {@code systemPromptOverride}, WITHOUT persisting a report or touching the
     * green baseline. Used by the self-tuning loop to compare a proposed prompt
     * against the current one (call with {@code null} for the current prompt, then
     * with the proposal). The agent is detached before the swap so a trial can
     * never flush to the DB.
     *
     * @return the aggregate score in [0,1], or {@link #SCORE_UNAVAILABLE}.
     */
    public double scoreWithSystemPrompt(String agentId, String systemPromptOverride) {
        TurAIAgent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return SCORE_UNAVAILABLE;
        }
        List<TurAgentEvalSet> sets = evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(agentId)
                .stream().filter(s -> s.getEnabled() == 1).toList();
        if (sets.isEmpty()) {
            return SCORE_UNAVAILABLE;
        }
        TurLLMInstance llmInstance = resolveLlm(agent);
        if (llmInstance == null) {
            return SCORE_UNAVAILABLE;
        }
        // Detach so the in-memory prompt swap is never dirty-checked / flushed.
        entityManager.detach(agent);
        if (systemPromptOverride != null) {
            agent.setSystemPrompt(systemPromptOverride);
        }
        ChatModel judgeModel = createChatModel(llmInstance);
        List<TurAgentEvalCaseResultDto> results = new ArrayList<>();
        for (TurAgentEvalSet set : sets) {
            for (TurAgentEvalCase evalCase : orderedCases(set)) {
                results.add(runCase(agent, llmInstance, judgeModel, evalCase));
            }
        }
        return aggregateScore(results);
    }

    private static double aggregateScore(List<TurAgentEvalCaseResultDto> results) {
        return results.isEmpty() ? 0d
                : results.stream().mapToDouble(TurAgentEvalCaseResultDto::score).average().orElse(0d);
    }

    /**
     * F.7 / §X.8.d — submit every enabled case's <b>rubric</b> judgement as a
     * single LLM-Judge Batch (50% off, ~24h) instead of synchronous calls. The
     * rubric judge only needs the rubric + the scripted user turns (both on the
     * eval case), so no conversation replay is required — this is a pure,
     * fire-and-forget judging pass for overnight CI. The verdicts are parsed and
     * logged by {@code TurEvalJudgeBatchHandler} when the batch ends.
     *
     * <p>Returns the number of rubrics scheduled, or {@code -1} when the Batch
     * eval tier is unavailable (caller falls back to {@link #runAgent}).
     */
    public int submitJudgeBatch(String agentId) {
        if (!configProperties.getBatch().isEnabled()
                || !configProperties.getBatch().getEval().isEnabled()) {
            return -1;
        }
        TurAIAgent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return -1;
        }
        TurLLMInstance llmInstance = resolveLlm(agent);
        if (llmInstance == null || !batchInferenceService.isSupported(llmInstance)) {
            return -1;
        }
        List<com.viglet.turing.genai.batch.TurBatchChatRequest> requests = new ArrayList<>();
        for (TurAgentEvalSet set : evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(agentId)) {
            if (set.getEnabled() != 1) {
                continue;
            }
            for (TurAgentEvalCase evalCase : orderedCases(set)) {
                if (evalCase.getRubric() == null || evalCase.getRubric().isBlank()) {
                    continue;
                }
                requests.add(new com.viglet.turing.genai.batch.TurBatchChatRequest(
                        evalCase.getId(), TurEvalJudgePrompt.SYSTEM,
                        TurEvalJudgePrompt.buildUser(evalCase.getRubric(),
                                parseTurns(evalCase.getSeedTurnsJson())),
                        null, 0d, null));
            }
        }
        if (requests.isEmpty()) {
            return 0;
        }
        return batchInferenceService.submit(llmInstance, "llm-judge-eval", requests, agentId)
                .map(job -> requests.size()).orElse(-1);
    }

    /**
     * F.9 / §X.10.b — push every enabled case's <b>rubric</b> fixture to the
     * OpenAI Evals API and start a run whose server-side {@code label_model}
     * grader judges them (T168). Like {@link #submitJudgeBatch} the rubric judge
     * only needs the rubric + scripted user turns (both on the eval case), so no
     * conversation replay is required — Turing pushes the fixtures and OpenAI
     * runs the grader.
     *
     * <p>Returns the submission (eval/run ids + report URL) or empty when the
     * Evals tier is off, the eval LLM isn't OpenAI-backed, there are no rubric
     * cases, or the API call fails — the caller then falls back to {@link #runAgent}.
     */
    public Optional<com.viglet.turing.genai.nativeapi.openai.TurOpenAiEvalsService.EvalSubmission>
            submitToOpenAiEvals(String agentId) {
        TurAIAgent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return Optional.empty();
        }
        TurLLMInstance llmInstance = resolveLlm(agent);
        if (llmInstance == null || !openAiEvalsService.isSupported(llmInstance)) {
            return Optional.empty();
        }
        List<com.viglet.turing.genai.nativeapi.openai.TurOpenAiEvalsPayload.EvalFixture> fixtures =
                new ArrayList<>();
        for (TurAgentEvalSet set : evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(agentId)) {
            if (set.getEnabled() != 1) {
                continue;
            }
            for (TurAgentEvalCase evalCase : orderedCases(set)) {
                if (evalCase.getRubric() == null || evalCase.getRubric().isBlank()) {
                    continue;
                }
                fixtures.add(new com.viglet.turing.genai.nativeapi.openai.TurOpenAiEvalsPayload
                        .EvalFixture(evalCase.getId(), evalCase.getRubric(),
                        String.join("\n", parseTurns(evalCase.getSeedTurnsJson()))));
            }
        }
        if (fixtures.isEmpty()) {
            return Optional.empty();
        }
        return openAiEvalsService.submit(llmInstance, agent.getTitle(), fixtures);
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
            List<String> assistantReplies = replayTurns(agent, llmInstance, conversationId, turns);

            Map<String, String> capturedSlots =
                    chatFlowEngineService.listSlotsForConversation(conversationId).slots();
            ConversationStateDto state = chatFlowEngineService.getConversationState(conversationId);
            String finalNodeId = state.currentNodeId();
            String actualOutcome = resolveOutcome(conversationId, state);

            return scoreCase(evalCase, judgeModel, turns, assistantReplies, capturedSlots,
                    finalNodeId, actualOutcome);
        } catch (RuntimeException e) {
            log.warn("[AgentEval] case '{}' replay failed: {}", evalCase.getName(), e.getMessage());
            return failed(evalCase, e.getMessage());
        } finally {
            cleanupConversation(agent.getId(), conversationId);
        }
    }

    /** Replays the scripted turns and returns the assistant reply for each. */
    private List<String> replayTurns(TurAIAgent agent, TurLLMInstance llmInstance,
            String conversationId, List<String> turns) {
        List<ChatMessageItem> history = new ArrayList<>();
        List<String> assistantReplies = new ArrayList<>();
        for (String turn : turns) {
            history.add(new ChatMessageItem("user", turn));
            String assistant = blockForAssistantReply(agent, llmInstance, history, conversationId);
            history.add(new ChatMessageItem("assistant", assistant));
            assistantReplies.add(assistant);
        }
        return assistantReplies;
    }

    private String blockForAssistantReply(TurAIAgent agent, TurLLMInstance llmInstance,
            List<ChatMessageItem> history, String conversationId) {
        List<ChatResponse> emissions = agentChatExecutor
                .execute(new TurAgentChatRequest(agent, llmInstance, history, null,
                        conversationId, null, null))
                .collectList()
                .block();
        if (emissions == null || emissions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatResponse r : emissions) {
            // Only token emissions are assistant text; "options" carries a
            // JSON chip array that isn't part of the spoken reply.
            if ((r.type() == null || "token".equals(r.type())) && r.content() != null) {
                sb.append(r.content());
            }
        }
        return sb.toString();
    }

    // ─────────────────────────── Scoring (grader stack) ───────────────────────────

    /**
     * Scores one replayed case by running the resolved grader stack (T586).
     * With no grader config this is the legacy default stack (slot / outcome /
     * node / rubric), so the per-case score, pass flag and DTO projection are
     * byte-identical to the pre-SPI inline scoring. The well-known built-ins
     * still project into the legacy DTO fields (slot diffs, rubric verdict /
     * rationale); a generic per-grader breakdown arrives with the Eval Studio
     * (T599).
     */
    private TurAgentEvalCaseResultDto scoreCase(TurAgentEvalCase evalCase, ChatModel judgeModel,
            List<String> turns, List<String> assistantReplies, Map<String, String> capturedSlots,
            String finalNodeId, String actualOutcome) {
        TurEvalGradingContext ctx = new TurEvalGradingContext(evalCase, turns, assistantReplies,
                capturedSlots, finalNodeId, actualOutcome, judgeModel);

        List<Double> dimensionScores = new ArrayList<>();
        boolean allMatched = true;
        List<SlotDiff> slotDiffs = new ArrayList<>();
        String rubricVerdict = "na";
        String rubricRationale = null;

        for (TurEvalGrader grader : graderRegistry.resolveGraders(evalCase)) {
            if (!grader.appliesTo(ctx)) {
                continue;
            }
            TurEvalGraderResult result = grader.grade(ctx);
            if (result.deferred()) {
                // No HUMAN grader in the default stack yet; deferral lands in T592.
                continue;
            }
            dimensionScores.add(result.score());
            allMatched &= result.passed();
            switch (grader.graderId()) {
                case TurEvalBuiltinGraders.SLOT_MATCH -> result.details().forEach(d ->
                        slotDiffs.add(new SlotDiff(d.key(), d.expected(), d.actual(), d.match())));
                case TurEvalBuiltinGraders.RUBRIC -> {
                    rubricVerdict = result.verdict();
                    rubricRationale = result.rationale();
                }
                default -> {
                    // Other graders carry no legacy DTO projection yet.
                }
            }
        }

        double score = dimensionScores.isEmpty() ? 1d
                : dimensionScores.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
        TurAgentEvalExpectedOutcome expectedOutcome = evalCase.getExpectedOutcome();
        return new TurAgentEvalCaseResultDto(evalCase.getId(), evalCase.getName(), allMatched, score,
                expectedOutcome == null ? null : expectedOutcome.name(), actualOutcome,
                finalNodeId, slotDiffs, rubricVerdict, rubricRationale, null);
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
        double score = aggregateScore(results);

        // Compare to the green baseline to detect regressions.
        Optional<TurAgentEvalReport> baseline =
                reportRepository.findFirstByTurAIAgent_IdAndBaselineTrueOrderByCreatedAtDesc(agent.getId());
        boolean regressed = baseline.isPresent() && hasRegression(baseline.get(), results);

        TurAgentEvalReport report = new TurAgentEvalReport();
        report.setTurAIAgent(agent);
        report.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()));
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
