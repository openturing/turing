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
package com.viglet.turing.genai.prompt.contributor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.prompt.TurPromptAssemblyContext;
import com.viglet.turing.genai.prompt.TurPromptContributor;
import com.viglet.turing.genai.prompt.TurPromptSegment;
import com.viglet.turing.genai.prompt.TurPromptStability;
import com.viglet.turing.persistence.model.agent.TurAIAgent;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Block AL / §XXXV.1 — emits the opt-in per-agent capability guidance blocks
 * (rich-content rendering / answer-as-app / co-browse / user-memory /
 * action-widget) that the legacy assembler appended with a {@code "\n\n"}
 * separator each. Each is a no-op for a default-off agent, so an existing agent
 * gets zero capability segments — byte-identical to before. The prompt
 * resources moved here from {@code TurChatPromptAssembler} so each capability
 * owns its text at its source. All STABLE — the guidance is constant for the
 * agent's configuration.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurCapabilityPromptContributor implements TurPromptContributor {

    @Value("classpath:prompts/render.md")
    private Resource renderPromptResource;

    @Value("classpath:prompts/answer-as-app.md")
    private Resource answerAsAppPromptResource;

    @Value("classpath:prompts/co-browse.md")
    private Resource coBrowsePromptResource;

    @Value("classpath:prompts/user-memory.md")
    private Resource userMemoryPromptResource;

    @Value("classpath:prompts/action-widget.md")
    private Resource actionWidgetPromptResource;

    private String renderPrompt = "";
    private String answerAsAppPrompt = "";
    private String coBrowsePrompt = "";
    private String userMemoryPrompt = "";
    private String actionWidgetPrompt = "";

    @PostConstruct
    void loadPrompts() {
        renderPrompt = load(renderPromptResource, "render.md");
        answerAsAppPrompt = load(answerAsAppPromptResource, "answer-as-app.md");
        coBrowsePrompt = load(coBrowsePromptResource, "co-browse.md");
        userMemoryPrompt = load(userMemoryPromptResource, "user-memory.md");
        actionWidgetPrompt = load(actionWidgetPromptResource, "action-widget.md");
    }

    private String load(Resource resource, String name) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[PromptAssembler] could not load prompts/{} — agents that opt into "
                    + "this capability will fall back to the model's own formatting: {}",
                    name, e.getMessage());
            return "";
        }
    }

    @Override
    public int order() {
        return ORDER_CAPABILITY;
    }

    @Override
    public List<TurPromptSegment> contribute(TurPromptAssemblyContext context) {
        TurAIAgent agent = context.agent();
        List<TurPromptSegment> segments = new ArrayList<>(5);
        // Order matches the legacy augmentWithCapabilityPrompts sequence exactly:
        // render → answer-as-app → co-browse → user-memory → action-widget. The
        // legacy code appended each as `augmented + "\n\n" + prompt`, so each
        // segment's text carries that leading "\n\n" to stay byte-identical.
        addIf(segments, agent.isRichContentEnabled(), "render", renderPrompt);
        addIf(segments, agent.isAnswerAsAppEnabled(), "answer-as-app", answerAsAppPrompt);
        addIf(segments, agent.isCoBrowseEnabled(), "co-browse", coBrowsePrompt);
        addIf(segments, agent.isUserMemoryEnabled(), "user-memory", userMemoryPrompt);
        addIf(segments, agent.isActionWidgetEnabled(), "action-widget", actionWidgetPrompt);
        return segments;
    }

    private void addIf(List<TurPromptSegment> segments, boolean enabled, String title,
            String prompt) {
        if (enabled && prompt != null && !prompt.isBlank()) {
            segments.add(TurPromptSegment.of(TurPromptSegment.ORIGIN_CAPABILITY,
                    title, "\n\n" + prompt, TurPromptStability.STABLE));
        }
    }
}
