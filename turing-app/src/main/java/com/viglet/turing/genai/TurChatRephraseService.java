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
package com.viglet.turing.genai;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.nativeapi.openai.TurOpenAiPredictedOutputsService;
import com.viglet.turing.genai.nativeapi.openai.TurPredictedOutputsResolver;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * F.10 / §X.11.b — T172. Re-phrases a prior assistant answer (the
 * "rephrase shorter" action on a RAG / SN or agent answer).
 *
 * <p>When the agent's LLM is OpenAI-backed and the agent opted into
 * {@code predicted-outputs} (T171), the previous answer is sent as the
 * {@code prediction} so the rewrite returns 3–5× faster — exactly the case
 * Predicted Outputs is built for (the output mostly overlaps the input). The
 * billing gate ({@link TurPredictedOutputsResolver}) decides whether the
 * prediction is attached; the rewrite still runs as a normal OpenAI completion
 * when it isn't.
 *
 * <p>For a non-OpenAI provider the action still works — it falls back to a
 * provider-agnostic Spring AI completion (no prediction acceleration). So the
 * feature is universal; Predicted Outputs only makes the OpenAI path snappy.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurChatRephraseService {

    /** Default style when the caller doesn't pick one. */
    static final String DEFAULT_STYLE = "shorter";

    private static final String SYSTEM_PROMPT = """
            You rewrite a prior assistant answer according to the user's instruction.
            Output ONLY the rewritten answer — no preamble, no explanation, no surrounding quotes.
            Preserve the answer's language, factual content, and any [n] or [n](url) citation markers.""";

    private final TurAIAgentRepository agentRepository;
    private final TurPredictedOutputsResolver predictedOutputsResolver;
    private final TurOpenAiPredictedOutputsService predictedOutputsService;
    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService secretCryptoService;

    /** Outcome of a rephrase request. {@code usedPrediction} reports whether the OpenAI fast-path ran. */
    public record RephraseResult(boolean success, String error, String rephrased, boolean usedPrediction) {
    }

    /**
     * Rewrite {@code answer} per {@code style} using the agent's LLM.
     *
     * @param agentId       the agent that produced the answer (resolves the LLM)
     * @param llmInstanceId optional explicit instance id (must belong to the agent); null → the agent's default
     * @param prompt        the original user question (added as context; may be null)
     * @param answer        the assistant answer to rewrite (required)
     * @param style         {@code shorter}/{@code simpler}/{@code longer}/{@code formal}/{@code friendly}, or free text
     */
    public RephraseResult rephrase(String agentId, String llmInstanceId, String prompt, String answer,
            String style) {
        if (!StringUtils.hasText(answer)) {
            return new RephraseResult(false, "Nothing to rephrase.", null, false);
        }
        TurAIAgent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return new RephraseResult(false, "AI Agent not found: " + agentId, null, false);
        }
        TurLLMInstance instance = resolveInstance(agent, llmInstanceId);
        if (instance == null) {
            return new RephraseResult(false, "No LLM instance available for agent " + agentId, null, false);
        }

        String instruction = buildInstruction(style, prompt, answer);
        boolean usePrediction = predictedOutputsResolver.enabledFor(agent, instance);
        try {
            // OpenAI: one Chat Completions call; the previous answer is the prediction
            // when opted in (T171), otherwise a plain completion. Empty → not OpenAI.
            Optional<String> viaOpenAi = predictedOutputsService.edit(instance, SYSTEM_PROMPT, instruction,
                    usePrediction ? answer : null);
            if (viaOpenAi.isPresent()) {
                return new RephraseResult(true, null, viaOpenAi.get().strip(), usePrediction);
            }
            // Non-OpenAI provider: provider-agnostic completion, no prediction.
            return new RephraseResult(true, null, rephraseViaChatModel(instance, instruction), false);
        } catch (Exception e) {
            log.error("[Rephrase] agent '{}' instance '{}' failed: {}", agentId, instance.getId(),
                    e.getMessage(), e);
            return new RephraseResult(false, "Failed to rephrase: " + e.getMessage(), null, false);
        }
    }

    private TurLLMInstance resolveInstance(TurAIAgent agent, String llmInstanceId) {
        if (StringUtils.hasText(llmInstanceId)) {
            return agent.getLlmInstances().stream()
                    .filter(llm -> llm.getId().equals(llmInstanceId))
                    .findFirst()
                    .orElse(null);
        }
        // Deterministic default mirrors TurAIAgentChatAPI: lowest id of an unordered Set.
        return agent.getLlmInstances().stream()
                .min(Comparator.comparing(TurLLMInstance::getId))
                .orElse(null);
    }

    private String rephraseViaChatModel(TurLLMInstance instance, String instruction) {
        String apiKey = secretCryptoService.decrypt(instance.getApiKeyEncrypted());
        ChatModel chatModel = llmModelFactory.createChatModel(instance, apiKey);
        List<Message> messages = List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(instruction));
        String text = chatModel.call(new Prompt(messages)).getResult().getOutput().getText();
        return text == null ? "" : text.strip();
    }

    String buildInstruction(String style, String prompt, String answer) {
        String directive = switch (style == null ? DEFAULT_STYLE : style.toLowerCase(Locale.ROOT)) {
            case "shorter" -> "Rewrite it to be shorter and more concise while keeping the key information.";
            case "simpler" -> "Rewrite it in simpler, plain language for a non-expert reader.";
            case "longer" -> "Expand it with more detail and explanation, staying accurate.";
            case "formal" -> "Rewrite it in a more formal, professional tone.";
            case "friendly" -> "Rewrite it in a warmer, friendlier tone.";
            default -> "Rewrite it to be " + style + ".";
        };
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(prompt)) {
            sb.append("The user originally asked:\n").append(prompt).append("\n\n");
        }
        sb.append(directive).append("\n\nAnswer to rewrite:\n").append(answer);
        return sb.toString();
    }
}
