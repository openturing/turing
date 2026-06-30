/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.secondopinion;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * T522 / §XXVIII.18 — multi-provider "second opinion": a cheap model from a
 * <em>different</em> vendor critiques the primary RAG answer (a cross-vendor
 * LlmJudge) and the agreement is surfaced beside the answer as a confidence
 * signal. It never alters the answer — it only adds a {@link TurSecondOpinion}.
 *
 * <p>The cross-vendor constraint is the point: a second view from the same model
 * family tends to share the same blind spots, so the critic must resolve to a
 * <strong>different</strong> plugin type than the answering instance, else the
 * check is skipped.
 *
 * <p>Strictly opt-in ({@code turing}'s {@code GLOBAL_SECOND_OPINION_ENABLED} +
 * a configured critic instance) and fully fail-open: disabled, no/unusable
 * critic, same vendor, or any error → {@link Optional#empty()}, so the chat path
 * is byte-for-byte unchanged until an admin turns it on.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurSecondOpinionService {

    private static final int MAX_CONTEXT_CHARS = 6000;

    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurSecretCryptoService secretCryptoService;
    private final TurLlmModelFactory llmModelFactory;
    private final TurGenAiLlmProviderFactory providerFactory;

    public TurSecondOpinionService(TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurSecretCryptoService secretCryptoService,
            TurLlmModelFactory llmModelFactory,
            TurGenAiLlmProviderFactory providerFactory) {
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.secretCryptoService = secretCryptoService;
        this.llmModelFactory = llmModelFactory;
        this.providerFactory = providerFactory;
    }

    /** Whether the cross-check is enabled and a critic instance is configured. */
    public boolean isEnabled() {
        return globalSettingsService.isSecondOpinionEnabled()
                && StringUtils.hasText(globalSettingsService.getSecondOpinionLlmId());
    }

    /**
     * Runs a different-vendor critic over the primary answer. Returns
     * {@link Optional#empty()} when disabled, the answer is blank, the critic is
     * missing / same-vendor / unusable, or anything fails (fail-open).
     *
     * @param query           the user question
     * @param answer          the primary answer to critique
     * @param contextPassages retrieved grounding passages (may be empty)
     * @param primaryInstance the instance that produced the answer (for the
     *                        different-vendor check); may be {@code null}
     */
    public Optional<TurSecondOpinion> evaluate(String query, String answer,
            List<String> contextPassages, TurLLMInstance primaryInstance) {
        if (!isEnabled() || !StringUtils.hasText(answer)) {
            return Optional.empty();
        }
        try {
            TurLLMInstance critic = llmInstanceRepository.findById(globalSettingsService.getSecondOpinionLlmId())
                    .filter(instance -> instance.getEnabled() == 1)
                    .orElse(null);
            if (critic == null) {
                return Optional.empty();
            }
            String criticVendor = pluginType(critic);
            if (criticVendor != null && criticVendor.equals(pluginType(primaryInstance))) {
                log.debug("[SecondOpinion] critic vendor == answerer vendor ({}); skipping cross-check",
                        criticVendor);
                return Optional.empty();
            }
            String apiKey = secretCryptoService.decrypt(critic.getApiKeyEncrypted());
            ChatModel criticModel = llmModelFactory.createChatModel(critic, apiKey);
            if (criticModel == null) {
                return Optional.empty();
            }
            return Optional.of(judge(criticModel, critic, criticVendor, query, answer, contextPassages));
        } catch (RuntimeException e) {
            log.warn("[SecondOpinion] cross-check failed ({}); surfacing no signal", e.getMessage());
            return Optional.empty();
        }
    }

    private TurSecondOpinion judge(ChatModel criticModel, TurLLMInstance critic, String criticVendor,
            String query, String answer, List<String> contextPassages) {
        String context = joinContext(contextPassages);
        String system = "You are a strict, independent fact-checker reviewing another AI assistant's "
                + "answer. Judge ONLY whether the answer is accurate and supported by the provided "
                + "context. Reply with exactly AGREE or DISAGREE on the first line, then optionally "
                + "'Confidence: N%' on the second line, then a one-sentence reason.";
        String user = "Question:\n" + query + "\n\nRetrieved context:\n"
                + (context.isBlank() ? "(none provided)" : context)
                + "\n\nAnswer under review:\n" + answer;
        ChatResponse response = criticModel.call(new Prompt(List.of(
                new SystemMessage(system), new UserMessage(user))));
        String reply = response != null && response.getResult() != null
                && response.getResult().getOutput() != null
                        ? response.getResult().getOutput().getText()
                        : "";
        return parse(reply, critic, criticVendor);
    }

    /** Parses the critic reply into a verdict: first AGREE/DISAGREE token, optional Confidence, reason.
     *  Package-private for focused unit testing of the parse logic. */
    static TurSecondOpinion parse(String reply, TurLLMInstance critic, String criticVendor) {
        String trimmed = reply == null ? "" : reply.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        // Default to "agree" only on an explicit AGREE; a DISAGREE or an
        // unparseable reply is treated as a non-agreement signal worth surfacing.
        boolean agree = lower.startsWith("agree") || lower.startsWith("yes");
        boolean explicitDisagree = lower.startsWith("disagree") || lower.startsWith("no");
        // When neither token leads, fall back to scanning for the words.
        if (!agree && !explicitDisagree) {
            agree = lower.contains("agree") && !lower.contains("disagree");
        }
        Double confidence = parseConfidence(trimmed);
        String rationale = lastNonEmptyLine(trimmed);
        return new TurSecondOpinion(agree, confidence, rationale,
                critic.getModelName(), criticVendor);
    }

    /** Reads an optional {@code Confidence: N%} (0-100) into a {@code [0,1]} value. */
    private static Double parseConfidence(String reply) {
        int idx = reply.toLowerCase(Locale.ROOT).indexOf("confidence");
        if (idx < 0) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = idx; i < reply.length(); i++) {
            char c = reply.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        if (digits.isEmpty()) {
            return null;
        }
        try {
            double pct = Double.parseDouble(digits.toString());
            return Math.clamp(pct / 100.0, 0.0, 1.0);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String lastNonEmptyLine(String reply) {
        String[] lines = reply.split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty() && !line.toLowerCase(Locale.ROOT).startsWith("confidence")) {
                return line;
            }
        }
        return reply;
    }

    private String joinContext(List<String> contextPassages) {
        if (contextPassages == null || contextPassages.isEmpty()) {
            return "";
        }
        String joined = String.join("\n\n", contextPassages);
        return joined.length() > MAX_CONTEXT_CHARS ? joined.substring(0, MAX_CONTEXT_CHARS) : joined;
    }

    private String pluginType(TurLLMInstance instance) {
        if (instance == null) {
            return null;
        }
        try {
            return providerFactory.getProvider(instance).getPluginType().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
