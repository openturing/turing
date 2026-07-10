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
import com.viglet.turing.genai.eval.grader.TurEvalGraderConfigView;
import com.viglet.turing.genai.eval.grader.TurEvalGraderResult;
import com.viglet.turing.genai.eval.grader.TurEvalGradingContext;
import com.viglet.turing.genai.eval.grader.TurEvalResolvedGrader;
import com.viglet.turing.genai.eval.grader.TurEvalStackAggregator;
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
import com.viglet.turing.persistence.model.agent.TurEvalDatasetRow;
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
    private final TurEvalReviewTaskService reviewTaskService;
    private final com.viglet.turing.persistence.repository.agent.TurEvalDatasetRowRepository datasetRowRepository;
    private final com.viglet.turing.persistence.repository.agent.TurEvalDatasetRepository datasetRepository;

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
            com.viglet.turing.genai.eval.grader.TurEvalGraderRegistry graderRegistry,
            TurEvalReviewTaskService reviewTaskService,
            com.viglet.turing.persistence.repository.agent.TurEvalDatasetRowRepository datasetRowRepository,
            com.viglet.turing.persistence.repository.agent.TurEvalDatasetRepository datasetRepository) {
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
        this.reviewTaskService = reviewTaskService;
        this.datasetRowRepository = datasetRowRepository;
        this.datasetRepository = datasetRepository;
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
            for (EvalCaseSource source : resolveCases(set)) {
                // persistSideEffects = true: a real run parks human-review tasks.
                results.add(runCase(agent, llmInstance, judgeModel, set, source, true));
            }
        }
        // T598 — pin the bound dataset + its live version (if any) on the report.
        String datasetId = sets.stream().map(TurAgentEvalSet::getDatasetId)
                .filter(id -> id != null && !id.isBlank()).findFirst().orElse(null);
        int datasetVersion = datasetId == null ? 0
                : datasetRepository.findById(datasetId)
                        .map(com.viglet.turing.persistence.model.agent.TurEvalDataset::getVersion)
                        .orElse(0);
        return persistReport(agent, results, datasetId, datasetVersion);
    }

    /**
     * T602 / §XXXIII.17 — the public "run a named {@code dataset × grader stack}
     * remotely" entry-point behind {@code POST /api/eval/run}, the CLI
     * ({@code turing eval --dataset}), and the SDK {@code runEval}. Unlike {@link
     * #runAgent} — which replays the agent's own <b>configured</b> golden sets —
     * this scores an <b>arbitrary</b> dataset against an <b>arbitrary</b> grader
     * stack, so the same agent runtime can be gated by any reusable dataset in
     * CI (extends T428/T430).
     *
     * <p>It builds a transient, non-persisted {@link TurAgentEvalSet} that only
     * carries the {@code datasetId} + {@code graderStackId} bindings, then reuses
     * the exact per-case replay + grader-stack scoring machinery. A blank / null
     * {@code graderStackId} resolves to the legacy default stack.
     *
     * <p>The run replays with {@code persistSideEffects=false} (an ad-hoc CI run
     * never parks human-review tasks); a deferring HUMAN grader therefore just
     * marks its case {@code pendingReview}, which fails the gate. The report is
     * persisted for history / the Eval Studio timeline but — deliberately —
     * <b>never</b> becomes or demotes the agent's golden baseline and is never
     * flagged {@code regressed}, so ad-hoc dataset runs can't corrupt the
     * pre-publish gate baseline (which scores a different case set).
     */
    public TurAgentEvalReportDto runDataset(String agentId, String datasetId, String graderStackId) {
        TurAIAgent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return TurAgentEvalReportDto.error("Agent not found: " + agentId);
        }
        List<TurEvalDatasetRow> rows =
                datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc(datasetId);
        if (rows.isEmpty()) {
            return TurAgentEvalReportDto.error("Dataset has no rows: " + datasetId);
        }
        TurLLMInstance llmInstance = resolveLlm(agent);
        if (llmInstance == null) {
            return TurAgentEvalReportDto.error(
                    "No usable LLM (attach an enabled LLM to the agent or set a default LLM)");
        }
        ChatModel judgeModel = createChatModel(llmInstance);

        TurAgentEvalSet ephemeral = new TurAgentEvalSet();
        ephemeral.setName("ad-hoc:" + datasetId);
        ephemeral.setEnabled(1);
        ephemeral.setDatasetId(datasetId);
        ephemeral.setGraderStackId(graderStackId == null || graderStackId.isBlank() ? null : graderStackId);
        ephemeral.setTurAIAgent(agent);

        List<TurAgentEvalCaseResultDto> results = new ArrayList<>();
        for (EvalCaseSource source : resolveCases(ephemeral)) {
            results.add(runCase(agent, llmInstance, judgeModel, ephemeral, source, false));
        }
        int datasetVersion = datasetRepository.findById(datasetId)
                .map(com.viglet.turing.persistence.model.agent.TurEvalDataset::getVersion).orElse(0);
        return persistDatasetRunReport(agent, results, datasetId, datasetVersion);
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
            for (EvalCaseSource source : resolveCases(set)) {
                // persistSideEffects = false: a trial scoring never parks review tasks.
                results.add(runCase(agent, llmInstance, judgeModel, set, source, false));
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

    /**
     * T603 — resolve a judge {@link ChatModel} for {@code agent} (its first
     * enabled LLM, else the default LLM), or {@code null} when none is usable.
     * Exposed so the continuous / online eval service can score sampled live
     * traffic with MODEL graders through the exact same model resolution as a
     * pre-publish run, rather than duplicating the decrypt + factory dance.
     */
    public ChatModel resolveJudgeModel(TurAIAgent agent) {
        if (agent == null) {
            return null;
        }
        TurLLMInstance llmInstance = resolveLlm(agent);
        return llmInstance == null ? null : createChatModel(llmInstance);
    }

    // ─────────────────────────── Per-case replay ───────────────────────────

    private TurAgentEvalCaseResultDto runCase(TurAIAgent agent, TurLLMInstance llmInstance,
            ChatModel judgeModel, TurAgentEvalSet set, EvalCaseSource source,
            boolean persistSideEffects) {
        TurAgentEvalCase evalCase = source.evalCase();
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

            return scoreCase(agent, set, evalCase, source.referenceAnswer(), conversationId,
                    persistSideEffects, judgeModel, turns, assistantReplies, capturedSlots,
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
                        conversationId, null, null, null))
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
    private TurAgentEvalCaseResultDto scoreCase(TurAIAgent agent, TurAgentEvalSet set,
            TurAgentEvalCase evalCase, String referenceAnswer, String conversationId,
            boolean persistSideEffects, ChatModel judgeModel, List<String> turns,
            List<String> assistantReplies, Map<String, String> capturedSlots, String finalNodeId,
            String actualOutcome) {
        TurEvalGradingContext ctx = new TurEvalGradingContext(evalCase, conversationId, turns,
                assistantReplies, capturedSlots, finalNodeId, actualOutcome, judgeModel,
                referenceAnswer);

        // T600 — collect each grader's weighted contribution + blocking policy.
        List<TurEvalStackAggregator.Contribution> contributions = new ArrayList<>();
        boolean pendingReview = false;
        List<SlotDiff> slotDiffs = new ArrayList<>();
        String rubricVerdict = "na";
        String rubricRationale = null;

        for (TurEvalResolvedGrader resolved : graderRegistry.resolveStack(set, evalCase)) {
            TurEvalGrader grader = resolved.grader();
            TurEvalGraderConfigView config = resolved.config();
            if (!grader.appliesTo(ctx, config)) {
                continue;
            }
            TurEvalGraderResult result = grader.grade(ctx, config);
            if (result.deferred()) {
                // T592 — a HUMAN grader can't decide now: the case awaits review.
                // Park a review task (only on a real run, never a trial scoring).
                pendingReview = true;
                if (persistSideEffects) {
                    reviewTaskService.createIfAbsent(agent.getId(), evalCase, grader.graderId(),
                            turns, assistantReplies, finalNodeId, actualOutcome);
                }
                continue;
            }
            contributions.add(new TurEvalStackAggregator.Contribution(result.score(), result.passed(),
                    config.weight(), config.threshold(), config.blocking() == 1));
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

        TurEvalStackAggregator.Aggregate aggregate = TurEvalStackAggregator.aggregate(contributions);
        double score = aggregate.score();
        // A case awaiting human review is neither pass nor fail yet.
        boolean passed = aggregate.passed() && !pendingReview;
        TurAgentEvalExpectedOutcome expectedOutcome = evalCase.getExpectedOutcome();
        return new TurAgentEvalCaseResultDto(evalCase.getId(), evalCase.getName(), passed, score,
                expectedOutcome == null ? null : expectedOutcome.name(), actualOutcome,
                finalNodeId, slotDiffs, rubricVerdict, rubricRationale, null, pendingReview);
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

    private TurAgentEvalReportDto persistReport(TurAIAgent agent, List<TurAgentEvalCaseResultDto> results,
            String datasetId, int datasetVersion) {
        int caseCount = results.size();
        int passedCount = (int) results.stream().filter(TurAgentEvalCaseResultDto::passed).count();
        // T592 — a run with any case awaiting human review is amber, not green.
        boolean pendingReview = results.stream().anyMatch(TurAgentEvalCaseResultDto::pendingReview);
        boolean passed = caseCount > 0 && passedCount == caseCount && !pendingReview;
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
        report.setPendingReview(pendingReview);
        report.setDatasetId(datasetId);
        report.setDatasetVersion(datasetVersion);
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
                caseCount, passedCount, report.isBaseline(), regressed, pendingReview,
                datasetId, datasetVersion, results, null);
    }

    /**
     * T602 — persists an ad-hoc {@code dataset × grader stack} run for history /
     * the Eval Studio timeline, tagged with the dataset + version it ran against.
     * Unlike {@link #persistReport} it never mutates the agent's green baseline
     * and never sets {@code regressed}, so an arbitrary dataset run cannot
     * corrupt the pre-publish golden-set gate.
     */
    private TurAgentEvalReportDto persistDatasetRunReport(TurAIAgent agent,
            List<TurAgentEvalCaseResultDto> results, String datasetId, int datasetVersion) {
        int caseCount = results.size();
        int passedCount = (int) results.stream().filter(TurAgentEvalCaseResultDto::passed).count();
        boolean pendingReview = results.stream().anyMatch(TurAgentEvalCaseResultDto::pendingReview);
        boolean passed = caseCount > 0 && passedCount == caseCount && !pendingReview;
        double score = aggregateScore(results);

        TurAgentEvalReport report = new TurAgentEvalReport();
        report.setTurAIAgent(agent);
        report.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()));
        report.setPassed(passed);
        report.setScore(score);
        report.setCaseCount(caseCount);
        report.setPassedCount(passedCount);
        report.setRegressed(false);
        report.setPendingReview(pendingReview);
        report.setDatasetId(datasetId);
        report.setDatasetVersion(datasetVersion);
        report.setResultsJson(writeJson(results));
        report = reportRepository.save(report);

        return new TurAgentEvalReportDto(report.getId(), report.getCreatedAt(), passed, score,
                caseCount, passedCount, false, false, pendingReview,
                datasetId, datasetVersion, results, null);
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

    /**
     * T595 — one scorable case + its golden reference. Inline cases carry a null
     * reference; a bound dataset supplies the row's {@code referenceAnswer}.
     */
    private record EvalCaseSource(TurAgentEvalCase evalCase, String referenceAnswer) {
    }

    /**
     * Resolves the cases to score for a set: the bound {@link
     * com.viglet.turing.persistence.model.agent.TurEvalDataset}'s rows (mapped to
     * transient cases) when {@code datasetId} is set, else the legacy inline
     * cases (byte-identical).
     */
    private List<EvalCaseSource> resolveCases(TurAgentEvalSet set) {
        String datasetId = set.getDatasetId();
        if (datasetId == null || datasetId.isBlank()) {
            return orderedCases(set).stream()
                    .map(c -> new EvalCaseSource(c, null))
                    .toList();
        }
        return datasetRowRepository.findByTurEvalDataset_IdOrderBySortOrderAsc(datasetId).stream()
                .map(TurAgentEvalRunnerService::rowToSource)
                .toList();
    }

    /** Maps a dataset row to a transient {@link TurAgentEvalCase} + its reference. */
    private static EvalCaseSource rowToSource(TurEvalDatasetRow row) {
        TurAgentEvalCase c = new TurAgentEvalCase();
        c.setId(row.getId());
        c.setName(row.getName() == null ? row.getId() : row.getName());
        c.setSeedTurnsJson(row.getSeedTurnsJson());
        c.setExpectedSlotsJson(row.getExpectedSlotsJson());
        c.setExpectedOutcome(row.getExpectedOutcome());
        c.setExpectedNodeId(row.getExpectedNodeId());
        c.setRubric(row.getRubric());
        return new EvalCaseSource(c, row.getReferenceAnswer());
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
