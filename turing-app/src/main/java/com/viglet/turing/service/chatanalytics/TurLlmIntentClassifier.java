/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatanalytics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.observability.TurLlmObservation;
import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * T28 / §III.5 — AI-on-AI conversation classifier strategy. Given a
 * session's transcript, asks the default LLM (resolved via
 * {@link TurGlobalSettingsService}) to label intent, goal, sentiment and
 * key terms.
 *
 * <p>This is the original behavior of the (now-orchestrator)
 * {@link TurChatIntentClassifier} extracted into a
 * {@link TurIntentClassifierStrategy} so other backends (Lucene MLT,
 * Elasticsearch MLT) can coexist behind the same interface.
 *
 * <p>Failure modes are explicit: when the LLM is misconfigured or
 * returns unparseable output the method returns
 * {@link TurChatSessionEnrichment#unclassified()} so the orchestrator's
 * {@code auto} cascade can fall through to the next strategy.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1 (extracted from {@code TurChatIntentClassifier} 2026.2.7)
 */
@Slf4j
@Component
public class TurLlmIntentClassifier implements TurIntentClassifierStrategy {

    public static final String TYPE = "llm";

    private static final String SYSTEM_PROMPT = """
            You are an analyst that classifies a chat conversation between a user and an AI agent.
            Read the transcript and infer:
              - the user's INTENT (one of: SUPPORT, ONBOARDING, EXPLORATION, COMPLAINT,
                INFORMATION_SEEKING, CONVERSION_INTENT, OTHER),
              - their GOAL summarised in one short sentence,
              - whether that goal was ACHIEVED by the agent (YES, PARTIAL, NO, UNKNOWN),
              - the user's SENTIMENT toward the agent (POSITIVE, NEUTRAL, NEGATIVE, FRUSTRATED,
                UNKNOWN; FRUSTRATED is reserved for repeated rephrasing or escalating tone),
              - up to 5 KEY TERMS that capture the topic,
              - a SENTIMENT TRAJECTORY: one sentiment value (same vocabulary as SENTIMENT) for
                EACH user turn, in chronological order, so a reader can see where the
                conversation soured. The array length must equal the number of user turns.
            Respond with ONLY valid JSON, no Markdown fences, no commentary, exactly:
            {
              "intentLabel": "...",
              "intentConfidence": 0.0,
              "goalSummary": "...",
              "goalAchieved": "...",
              "sentiment": "...",
              "keyTerms": ["...", "..."],
              "sentimentTrajectory": ["...", "..."]
            }
            intentConfidence is a number between 0 and 1.
            """;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurLlmObservation llmObservation;
    private final MeterRegistry meterRegistry;

    public TurLlmIntentClassifier(TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurLlmModelFactory llmModelFactory,
            TurSecretCryptoService secretCryptoService,
            TurLlmObservation llmObservation,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.llmModelFactory = llmModelFactory;
        this.secretCryptoService = secretCryptoService;
        this.llmObservation = llmObservation;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean isAvailable(String agentId) {
        // agentId is irrelevant for the LLM strategy — same model labels
        // every session regardless of which agent it belongs to. Returns
        // true iff a default LLM is configured and enabled.
        String llmId = globalSettingsService.getDefaultLlmId();
        if (StringUtils.isBlank(llmId)) {
            return false;
        }
        return llmInstanceRepository.findById(llmId)
                .map(i -> i.getEnabled() == 1)
                .orElse(false);
    }

    @Override
    public TurChatSessionEnrichment classify(String agentId, String firstUserMessage,
            List<Map<String, Object>> messages) {
        if (!isAvailable(agentId)) {
            return TurChatSessionEnrichment.unclassified();
        }
        String transcript = buildTranscript(firstUserMessage, messages);
        if (transcript.isBlank()) {
            return TurChatSessionEnrichment.unclassified();
        }
        try {
            TurLLMInstance llmInstance = llmInstanceRepository
                    .findById(globalSettingsService.getDefaultLlmId())
                    .orElseThrow();
            String apiKey = secretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
            ChatModel chatModel = llmModelFactory.createChatModel(llmInstance, apiKey);
            String provider = nullSafe(llmInstance.getTurLLMVendor() == null
                    ? null : llmInstance.getTurLLMVendor().getId());
            String model    = nullSafe(llmInstance.getModelName());

            List<Message> prompt = List.of(
                    new SystemMessage(SYSTEM_PROMPT),
                    new UserMessage(transcript));

            ChatResponse response = llmObservation.observeCall(
                    provider, TurMeterNames.OP_CHAT,
                    () -> chatModel.call(new Prompt(prompt)));
            recordTokens(provider, model, response);

            String text = response.getResult() != null && response.getResult().getOutput() != null
                    ? response.getResult().getOutput().getText() : null;
            if (text == null || text.isBlank()) {
                return TurChatSessionEnrichment.unclassified();
            }
            return parse(text);
        } catch (RuntimeException e) {
            log.warn("[LlmIntentClassifier] classification failed: {}", e.getMessage());
            return TurChatSessionEnrichment.unclassified();
        }
    }

    private void recordTokens(String provider, String model, ChatResponse response) {
        if (response == null || response.getMetadata() == null) return;
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) return;
        long in  = usage.getPromptTokens()     == null ? 0L : usage.getPromptTokens().longValue();
        long out = usage.getCompletionTokens() == null ? 0L : usage.getCompletionTokens().longValue();
        llmObservation.recordTokens(provider, model, in, out);
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    @SuppressWarnings("unchecked")
    private TurChatSessionEnrichment parse(String rawJson) {
        String cleaned = stripCodeFences(rawJson);
        try {
            Map<String, Object> parsed = MAPPER.readValue(cleaned, Map.class);
            TurChatIntentLabel intent = parseEnum(TurChatIntentLabel.class,
                    asString(parsed.get("intentLabel")), TurChatIntentLabel.OTHER);
            double confidence = toDouble(parsed.get("intentConfidence"));
            String goal = asString(parsed.get("goalSummary"));
            TurChatGoalAchieved achieved = parseEnum(TurChatGoalAchieved.class,
                    asString(parsed.get("goalAchieved")), TurChatGoalAchieved.UNKNOWN);
            TurChatSentiment sentiment = parseEnum(TurChatSentiment.class,
                    asString(parsed.get("sentiment")), TurChatSentiment.UNKNOWN);
            List<String> keyTerms = Collections.emptyList();
            Object rawTerms = parsed.get("keyTerms");
            if (rawTerms instanceof List<?> list) {
                List<String> tmp = new ArrayList<>();
                for (Object item : list) {
                    if (item != null) tmp.add(String.valueOf(item));
                }
                keyTerms = tmp.stream().limit(5).toList();
            }
            List<TurChatSentiment> trajectory = parseSentimentTrajectory(parsed.get("sentimentTrajectory"));
            return new TurChatSessionEnrichment(intent, confidence, goal, achieved,
                    sentiment, keyTerms, trajectory, Instant.now());
        } catch (JacksonException e) {
            log.warn("[LlmIntentClassifier] could not parse classifier output as JSON: {}",
                    e.getMessage());
            return TurChatSessionEnrichment.unclassified();
        }
    }

    /**
     * T87 — turn a raw {@code sentimentTrajectory} JSON value into a bounded
     * list of {@link TurChatSentiment}. Tolerant by design: a non-array, a
     * {@code null}, or unknown labels collapse to {@link TurChatSentiment#UNKNOWN}
     * / an empty list rather than failing the whole classification. Capped at
     * {@value #MAX_TRAJECTORY_TURNS} turns so a runaway model can't bloat the
     * stored document.
     */
    static List<TurChatSentiment> parseSentimentTrajectory(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<TurChatSentiment> trajectory = new ArrayList<>();
        for (Object item : list) {
            if (trajectory.size() >= MAX_TRAJECTORY_TURNS) break;
            trajectory.add(parseEnum(TurChatSentiment.class, asString(item), TurChatSentiment.UNKNOWN));
        }
        return List.copyOf(trajectory);
    }

    /** Upper bound on the per-turn sentiment trajectory length stored per session. */
    static final int MAX_TRAJECTORY_TURNS = 200;

    static String buildTranscript(String firstUserMessage, List<Map<String, Object>> messages) {
        StringBuilder sb = new StringBuilder();
        if (messages != null && !messages.isEmpty()) {
            for (Map<String, Object> m : messages) {
                String role = asString(m.get("role"));
                String content = asString(m.get("content"));
                if (content == null || content.isBlank()) continue;
                sb.append(StringUtils.isNotBlank(role) ? role.toLowerCase() : "user")
                  .append(": ")
                  .append(truncate(content, 800))
                  .append("\n");
            }
        }
        if (sb.length() == 0 && StringUtils.isNotBlank(firstUserMessage)) {
            sb.append("user: ").append(truncate(firstUserMessage, 800));
        }
        return sb.toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static String stripCodeFences(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstBreak = t.indexOf('\n');
            if (firstBreak > 0) t = t.substring(firstBreak + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return 0.0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** Test-visible meter registry accessor. */
    MeterRegistry meterRegistry() {
        return meterRegistry;
    }
}
