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
package com.viglet.turing.genai.nativeapi.liveanswers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.citation.TurCitationDocument;
import com.viglet.turing.genai.tool.TurRagSearchToolService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * T187 / §X.15.a — "Live answers" hybrid mode: a thin composition layer that
 * makes a single native turn fuse the indexed knowledge base, {@code web_search}
 * citations, live {@code web_fetch} data, and {@code code_execution} analytics
 * into one cited reply.
 *
 * <p>It does two things, both opt-in via {@link TurAIAgent}'s {@code isLiveAnswersEnabled()}
 * and both no-ops for every existing agent:
 *
 * <ol>
 *   <li><b>Knowledge-base grounding.</b> Retrieves the top-K SN/RAG passages for
 *       the user's question and formats them as a {@code [KB-n]}-tagged grounding
 *       block appended to the system prompt. This gives the OpenAI Responses path
 *       the indexed-hits leg it otherwise lacks (the Anthropic path attaches the
 *       same passages as citations {@code document} blocks, so the caller passes
 *       {@code alreadyGrounded=true} there to avoid double-grounding).</li>
 *   <li><b>Fusion guidance.</b> Appends {@code prompts/live-answers.md}, which
 *       teaches the model to weave whichever of its admin-selected native tools
 *       are relevant into one coherent, well-attributed answer.</li>
 * </ol>
 *
 * <p>Live answers is a composition over the two-level capability gate — it never
 * force-enables a capability the instance doesn't support; the admin selects
 * {@code web_search} / {@code web_fetch} / {@code code_execution} on the agent as
 * usual, and this layer simply adds the grounding + the instruction to fuse them.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurLiveAnswersService {

    /** How many knowledge-base passages to inject as grounding context. */
    private static final int GROUNDING_TOP_K = 5;
    /** Defensive per-passage character cap so the grounding block can't blow the prompt. */
    private static final int MAX_PASSAGE_CHARS = 2000;

    private final TurRagSearchToolService ragSearchToolService;

    @Value("classpath:prompts/live-answers.md")
    private Resource fusionPromptResource;

    private String fusionPrompt = "";

    public TurLiveAnswersService(TurRagSearchToolService ragSearchToolService) {
        this.ragSearchToolService = ragSearchToolService;
    }

    @PostConstruct
    void loadFusionPrompt() {
        try {
            fusionPrompt = fusionPromptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[LiveAnswers] could not load prompts/live-answers.md — live-answers "
                    + "agents will fall back to the model's own fusion behaviour: {}", e.getMessage());
            fusionPrompt = "";
        }
    }

    /** True when the agent opted into live-answers hybrid mode. */
    public boolean isEnabled(TurAIAgent agent) {
        return agent != null && agent.isLiveAnswersEnabled();
    }

    /**
     * Augment the assembled system prompt for a live-answers turn: append the
     * fusion guidance and, unless the caller is already attaching the passages as
     * citation document blocks ({@code alreadyGrounded}), the {@code [KB-n]}
     * knowledge-base grounding block retrieved for {@code lastUserMessage}.
     *
     * <p>Never throws and never mutates the prompt when nothing is available — an
     * agent with the flag on but no RAG hits and an unreadable prompt file simply
     * gets its base prompt back, identical to a non-live turn.
     *
     * @param baseSystemPrompt the assembled system prompt (may be blank)
     * @param lastUserMessage  the question to ground against (may be blank)
     * @param alreadyGrounded  true when the vendor path already attaches the
     *                         passages (Anthropic citations / native-PDF), so the
     *                         grounding block is skipped to avoid duplication
     * @return the augmented system prompt
     */
    public String augment(String baseSystemPrompt, String lastUserMessage, boolean alreadyGrounded) {
        String prompt = baseSystemPrompt == null ? "" : baseSystemPrompt;
        if (!fusionPrompt.isBlank()) {
            prompt = prompt + "\n\n" + fusionPrompt;
        }
        if (!alreadyGrounded) {
            String grounding = buildGroundingBlock(lastUserMessage);
            if (!grounding.isBlank()) {
                prompt = prompt + "\n\n" + grounding;
            }
        }
        return prompt;
    }

    /**
     * Retrieve the top-K knowledge-base passages for the question and format them
     * as a {@code [KB-n]}-tagged grounding block. Returns an empty string — never
     * throws — when the question is blank, RAG is unavailable, or there are no
     * hits, so the turn degrades to a web/code-only live answer.
     */
    String buildGroundingBlock(String lastUserMessage) {
        if (!StringUtils.hasText(lastUserMessage)) {
            return "";
        }
        List<TurCitationDocument> passages =
                ragSearchToolService.retrievePassagesForCitations(lastUserMessage, GROUNDING_TOP_K);
        if (passages == null || passages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("LIVE-ANSWERS KNOWLEDGE BASE — the following passages were retrieved from the "
                + "indexed knowledge base for the user's question. They are the authoritative "
                + "internal source; cite them inline as [KB-1], [KB-2]… when you use them.\n");
        int index = 1;
        for (TurCitationDocument passage : passages) {
            String text = passage.text() == null ? "" : passage.text().strip();
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() > MAX_PASSAGE_CHARS) {
                text = text.substring(0, MAX_PASSAGE_CHARS) + "…";
            }
            sb.append("\n[KB-").append(index).append("] ");
            String title = passage.title();
            if (StringUtils.hasText(title)) {
                sb.append(title);
            }
            String url = passage.url();
            if (StringUtils.hasText(url)) {
                sb.append(" (").append(url).append(')');
            }
            sb.append('\n').append(text).append('\n');
            index++;
        }
        return index == 1 ? "" : sb.toString();
    }
}
