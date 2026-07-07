/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatmemory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurTokenBudgetService;
import com.viglet.turing.genai.workspace.TurAgentWorkspace;
import com.viglet.turing.observability.TurChatPipelineObservation;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * T115 / §IX.3.e — workspace-backed chat-memory compression.
 *
 * <p>For a long conversation, the turns OLDER than the
 * {@link TurAIAgent#chatMemoryRecentN recent-N} window accumulate and
 * inflate the prompt. When the agent has opted in
 * ({@link TurAIAgent#chatMemoryCompressionEnabled}) and that older pool is
 * estimated to exceed {@link TurAIAgent#chatMemoryCompressionThresholdTokens},
 * this service summarizes the older pool in a single dedicated LLM call, stores
 * the summary in the conversation workspace (T111) at
 * {@code memory/summary-1-{to}.md}, and returns a compact summary block to be
 * prepended to the prompt context — replacing the bulky older turns.
 *
 * <h2>Composition with T30 relevance retrieval</h2>
 * This runs <em>alongside</em> {@link TurChatMemoryRelevanceRetriever}: the
 * BM25 retriever still surfaces specific high-signal older turns verbatim when
 * they match the current query; the summary is the cheap default carrying the
 * low-signal background. {@link com.viglet.turing.genai.TurChatPromptAssembler}
 * prepends the summary, then the retrieved turns, then the recent window.
 *
 * <h2>Cost control</h2>
 * The summary LLM call fires at most once per
 * {@link TurAIAgent#chatMemoryCompressionInterval} per conversation. An
 * in-memory per-conversation cache holds the last summary; within the interval
 * the cached text is reused (no LLM call, no re-read). The cache is a pure cost
 * optimization — losing it on restart just triggers one extra summary.
 *
 * <h2>Bypass conditions</h2>
 * Returns {@link Optional#empty()} (no summary block, no LLM call) when any of:
 * chat memory disabled, compression disabled, store disabled, blank
 * conversation id, fewer than {@link #MIN_OLDER_TURNS} older turns, the older
 * pool is within the token threshold, or no usable LLM instance resolves.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChatMemoryCompressionService {

    /** Don't bother compressing a trivially small older pool. */
    static final int MIN_OLDER_TURNS = 4;

    /** Hard upper bound on the persisted-read size, matches store contract. */
    static final int MAX_READ_LIMIT = 1000;

    /** Fallback interval when the agent's value is blank/unparseable. */
    private static final Duration DEFAULT_INTERVAL = Duration.ofHours(1);

    private final TurChatMemoryStore store;
    private final TurAgentWorkspace workspace;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurLLMTokenUsageService tokenUsageService;
    private final TurGlobalSettingsService globalSettingsService;
    private final TurChatMemoryCompressionWorker compressionWorker;
    private final TurChatPipelineObservation chatPipelineObservation;

    /** Per-conversation last-summary cache for the interval guard. */
    private final ConcurrentHashMap<String, CachedSummary> cache = new ConcurrentHashMap<>();

    /**
     * T309 — conversation ids with a background regeneration in flight, so two
     * concurrent turns of the same conversation don't double-enqueue. Cleared
     * by {@link #generateAndStore(CompressionJob)}'s {@code finally} (per-JVM;
     * cluster-wide a duplicate just overwrites the same idempotent key).
     */
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    @Value("classpath:prompts/chat-memory-compression.md")
    private Resource compressionPromptResource;

    private String compressionSystemPrompt = "";

    public TurChatMemoryCompressionService(TurChatMemoryStore store,
            TurAgentWorkspace workspace,
            TurLLMInstanceRepository llmInstanceRepository,
            TurLlmModelFactory llmModelFactory,
            TurSecretCryptoService secretCryptoService,
            TurLLMTokenUsageService tokenUsageService,
            TurGlobalSettingsService globalSettingsService,
            TurChatMemoryCompressionWorker compressionWorker,
            TurChatPipelineObservation chatPipelineObservation) {
        this.store = store;
        this.workspace = workspace;
        this.llmInstanceRepository = llmInstanceRepository;
        this.llmModelFactory = llmModelFactory;
        this.secretCryptoService = secretCryptoService;
        this.tokenUsageService = tokenUsageService;
        this.globalSettingsService = globalSettingsService;
        this.compressionWorker = compressionWorker;
        this.chatPipelineObservation = chatPipelineObservation;
    }

    @PostConstruct
    void loadPrompt() {
        try {
            compressionSystemPrompt = compressionPromptResource.getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            log.warn("[ChatMemoryCompression] could not load prompts/chat-memory-compression.md — "
                    + "compression disabled: {}", e.getMessage());
            compressionSystemPrompt = "";
        }
    }

    /**
     * Returns a single summary {@link ChatMessageItem} to prepend to the
     * prompt context, or {@link Optional#empty()} on any bypass / failure path.
     *
     * @param agent          the chatting agent — read for the compression knobs.
     * @param conversationId persisted-memory key; blank → no-op.
     */
    public Optional<ChatMessageItem> summaryBlock(TurAIAgent agent, String conversationId) {
        if (!eligible(agent, conversationId)) {
            return Optional.empty();
        }

        int recentN = Math.max(0, agent.getChatMemoryRecentN());
        int readLimit = clamp(agent.getChatMemoryMaxMessages(), 1, MAX_READ_LIMIT);

        List<Map<String, Object>> persisted = readPersisted(conversationId, readLimit);
        if (persisted.size() - recentN < MIN_OLDER_TURNS) {
            return Optional.empty();
        }

        int olderCount = persisted.size() - recentN;
        List<OlderTurn> older = buildOlderPool(persisted, olderCount);
        if (older.size() < MIN_OLDER_TURNS) {
            return Optional.empty();
        }

        // Only pay for a summary when the older pool is actually large.
        if (estimateTokens(older) <= agent.getChatMemoryCompressionThresholdTokens()) {
            return Optional.empty();
        }

        long intervalMillis = parseInterval(agent.getChatMemoryCompressionInterval()).toMillis();
        long now = System.currentTimeMillis();
        CachedSummary cached = cache.get(conversationId);
        if (cached != null && now - cached.generatedAtMillis() < intervalMillis) {
            // Within the interval — reuse the last summary even though the pool
            // may have grown by a few turns (T30 surfaces those if relevant).
            return Optional.of(block(cached.content(), cached.coveredTo()));
        }

        // Regeneration is due. The snapshot is built on this (request) thread so
        // the background worker never touches the lazy agent/store again.
        CompressionJob job = new CompressionJob(agent.getId(), conversationId,
                agent.getChatMemoryCompressionLlmId(), renderOlderTurns(older), olderCount);

        // T309 — async (default): enqueue the summary LLM call off the hot path
        // and proceed THIS turn uncompressed (reusing a stale summary if present).
        // The fresh summary lands in the cache for the next turn. Sync path
        // (legacy T115) blocks here for the LLM call so the summary applies now.
        if (agent.isChatMemoryCompressionAsync()) {
            enqueueAsync(job);
            return cached == null ? Optional.empty()
                    : Optional.of(block(cached.content(), cached.coveredTo()));
        }

        String summary = generateAndStore(job);
        if (summary == null || summary.isBlank()) {
            // Regeneration failed — fall back to a stale cache if we have one,
            // otherwise just skip compression this turn.
            return cached == null ? Optional.empty()
                    : Optional.of(block(cached.content(), cached.coveredTo()));
        }
        return Optional.of(block(summary, olderCount));
    }

    /**
     * Visible for tests — drops the per-conversation interval cache and the
     * in-flight guard so a test can force regeneration without waiting out the
     * interval.
     */
    void clearCache() {
        cache.clear();
        inFlight.clear();
    }

    /** True when this agent + conversation is a candidate for compression at all. */
    private boolean eligible(TurAIAgent agent, String conversationId) {
        return agent != null
                && agent.isChatMemoryEnabled()
                && agent.isChatMemoryCompressionEnabled()
                && store.isEnabled()
                && conversationId != null && !conversationId.isBlank()
                && !compressionSystemPrompt.isBlank();
    }

    /** Reads persisted history, returning an empty list on a store error. */
    private List<Map<String, Object>> readPersisted(String conversationId, int readLimit) {
        try {
            List<Map<String, Object>> messages = store.findMessages(conversationId, readLimit);
            return messages == null ? List.of() : messages;
        } catch (RuntimeException e) {
            log.warn("[ChatMemoryCompression] store read failed for conv '{}': {}",
                    conversationId, e.getMessage());
            return List.of();
        }
    }

    /** Projects the first {@code olderCount} persisted entries into the older pool, skipping blanks. */
    private static List<OlderTurn> buildOlderPool(List<Map<String, Object>> persisted, int olderCount) {
        List<OlderTurn> older = new ArrayList<>(olderCount);
        for (int i = 0; i < olderCount; i++) {
            Map<String, Object> raw = persisted.get(i);
            String role = asString(raw.get("role"));
            String content = asString(raw.get("content"));
            if (role == null || role.isBlank() || content == null || content.isBlank()) {
                continue;
            }
            older.add(new OlderTurn(i, role, content));
        }
        return older;
    }

    /**
     * T309 — hand a due regeneration to the background worker, deduplicated per
     * conversation so concurrent turns don't double-summarize. A rejected
     * submission (saturated pool) is treated as "skip this round" — the
     * interval guard retries on a later turn.
     */
    private void enqueueAsync(CompressionJob job) {
        if (!inFlight.add(job.conversationId())) {
            return; // a regeneration for this conversation is already running
        }
        try {
            compressionWorker.regenerate(job);
        } catch (RuntimeException e) {
            // e.g. TaskRejectedException when the bounded pool is saturated.
            inFlight.remove(job.conversationId());
            log.debug("[ChatMemoryCompression] async enqueue skipped for conv '{}': {}",
                    job.conversationId(), e.getMessage());
        }
    }

    /**
     * Runs the summary LLM call, persists the result to the workspace, and
     * refreshes the interval cache. Invoked synchronously on the request thread
     * (sync mode) or on the {@link TurChatMemoryCompressionWorker} pool (async
     * mode). Never throws — failures return {@code null} and leave any prior
     * summary in place. Package-visible so the worker and tests can drive it.
     *
     * @return the generated summary text, or {@code null} on any failure.
     */
    String generateAndStore(CompressionJob job) {
        try {
            String summary = callLlm(job.compressionLlmId(), job.olderText());
            if (summary == null || summary.isBlank()) {
                return null;
            }
            persistToWorkspace(job.agentId(), job.conversationId(), job.coveredTo(), summary);
            cache.put(job.conversationId(),
                    new CachedSummary(System.currentTimeMillis(), job.coveredTo(), summary));
            // T124 — compressed/original character ratio (lower is better).
            chatPipelineObservation.recordCompressionRatio(job.olderText().length(), summary.length());
            log.info("[ChatMemoryCompression] conv '{}': summarized older pool (covered 1–{}) into {} chars",
                    job.conversationId(), job.coveredTo(), summary.length());
            return summary;
        } finally {
            inFlight.remove(job.conversationId());
        }
    }

    /**
     * T123 — on-demand summarization of an arbitrary block of conversation
     * text for the budget-driven {@link com.viglet.turing.genai.TurPromptCompactor}.
     * Reuses the same {@code prompts/chat-memory-compression.md} system prompt
     * and LLM-instance resolution as the scheduled older-pool summary, but is
     * triggered by the executor's over-budget path rather than the recent-N
     * threshold — so it runs synchronously and ignores the per-conversation
     * interval cache. Returns {@code null} on any failure (blank input, no
     * usable LLM instance, or a call error) so the caller can fall back to the
     * uncompacted prompt.
     *
     * @param compressionLlmId explicit summary LLM id, or null → Global default.
     * @param text             the conversation text to summarize.
     * @return the summary text, or {@code null} on any failure.
     */
    public String summarizeText(String compressionLlmId, String text) {
        if (text == null || text.isBlank() || compressionSystemPrompt.isBlank()) {
            return null;
        }
        return callLlm(compressionLlmId, text);
    }

    private String callLlm(String compressionLlmId, String olderText) {
        TurLLMInstance instance = resolveInstance(compressionLlmId);
        if (instance == null || instance.getEnabled() != 1) {
            log.warn("[ChatMemoryCompression] no usable LLM instance (compression llm id '{}', "
                    + "global default may be unset/disabled) — skipping", compressionLlmId);
            return null;
        }
        try {
            String apiKey = secretCryptoService.decrypt(instance.getApiKeyEncrypted());
            ChatModel chatModel = llmModelFactory.createChatModel(instance, apiKey);
            List<Message> messages = List.of(
                    new SystemMessage(compressionSystemPrompt),
                    new UserMessage(olderText));
            var response = chatModel.call(new Prompt(messages));
            tokenUsageService.recordUsage(instance, response, "system",
                    null, com.viglet.turing.observability.TurMeterNames.STAGE_CHAT_BACKGROUND);
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.warn("[ChatMemoryCompression] summary call failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Best-effort persistence of the summary into the conversation workspace.
     * Never throws — when storage is disabled (or any error occurs) the summary
     * still flows into the prompt from the in-memory cache; the workspace copy
     * is for audit / the "files this agent built" surface only.
     */
    private void persistToWorkspace(String agentId, String conversationId, int olderCount, String summary) {
        if (agentId == null || agentId.isBlank()) {
            return;
        }
        String key = "memory/summary-1-" + olderCount + ".md";
        try {
            workspace.put(agentId, conversationId, key,
                    summary.getBytes(StandardCharsets.UTF_8), "text/markdown");
        } catch (RuntimeException e) {
            log.debug("[ChatMemoryCompression] workspace persist skipped for conv '{}' ({})",
                    conversationId, e.getMessage());
        }
    }

    private TurLLMInstance resolveInstance(String compressionLlmId) {
        if (StringUtils.hasText(compressionLlmId)) {
            return llmInstanceRepository.findById(compressionLlmId).orElse(null);
        }
        String defaultId = globalSettingsService.getDefaultLlmId();
        if (StringUtils.hasText(defaultId)) {
            return llmInstanceRepository.findById(defaultId).orElse(null);
        }
        return null;
    }

    private static ChatMessageItem block(String content, int coveredTo) {
        return new ChatMessageItem("user",
                "[Summary of earlier conversation (turns 1–" + coveredTo + ")]\n" + content);
    }

    private static String renderOlderTurns(List<OlderTurn> older) {
        StringBuilder sb = new StringBuilder(older.size() * 64);
        for (OlderTurn t : older) {
            sb.append('[').append(t.position() + 1).append(" | ").append(t.role()).append("]: ")
                    .append(t.content()).append('\n');
        }
        return sb.toString();
    }

    private static int estimateTokens(List<OlderTurn> older) {
        List<Message> messages = new ArrayList<>(older.size());
        for (OlderTurn t : older) {
            messages.add(new UserMessage(t.content()));
        }
        return TurTokenBudgetService.estimateTokens(messages);
    }

    private static Duration parseInterval(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_INTERVAL;
        }
        try {
            Duration d = Duration.parse(raw.strip());
            return d.isNegative() || d.isZero() ? DEFAULT_INTERVAL : d;
        } catch (DateTimeParseException e) {
            return DEFAULT_INTERVAL;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /** Internal representation of an older persisted turn. */
    private record OlderTurn(int position, String role, String content) {
    }

    /** Per-conversation cached summary for the interval guard. */
    private record CachedSummary(long generatedAtMillis, int coveredTo, String content) {
    }

    /**
     * Immutable snapshot of one compression unit of work — everything the
     * summarizer needs, captured on the request thread so the (possibly async)
     * worker never re-reads the lazy agent entity or the store.
     *
     * @param agentId         owning agent id (workspace scope).
     * @param conversationId  owning conversation id (workspace scope + cache key).
     * @param compressionLlmId explicit summary LLM id, or null → Global default.
     * @param olderText       the older turns already rendered to prompt text.
     * @param coveredTo       1-based upper bound of the summarized range (drives
     *                        the workspace key {@code memory/summary-1-{to}.md}).
     */
    public record CompressionJob(String agentId, String conversationId, String compressionLlmId,
            String olderText, int coveredTo) {
    }
}
