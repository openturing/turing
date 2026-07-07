/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.prompt.TurPromptSegment;
import com.viglet.turing.persistence.dto.agent.TurSystemPromptInertRegionDto;

/**
 * Block AK / T609 — detects the regions of an agent's system prompt that are
 * <em>inert this turn</em>. When a chat flow governs the turn, the flow addendum
 * explicitly tells the model to ignore the agent prompt's welcome /
 * first-interaction / routing scaffolding ("IGNORE everything else … follow the
 * Goal"), so those tokens are dead weight the model still has to attend to.
 *
 * <p>Detection is markdown-heading-scoped and works two ways:
 * <ul>
 *   <li><b>Heuristic</b> — a heading whose text matches the welcome / greeting /
 *       first-interaction / routing / menu vocabulary (bilingual EN/PT) marks its
 *       whole section inert <em>when a flow is active</em>.</li>
 *   <li><b>Author marker</b> — a section containing {@code <!-- turing:concierge-only -->}
 *       is inert when a flow is active; {@code <!-- turing:flow-only -->} is inert
 *       on a no-flow turn. Markers take precedence over the heuristic so authors
 *       can attribute a block precisely.</li>
 * </ul>
 *
 * <p>Read-only and advisory — it never rewrites the prompt (§XXXIV invariant).
 * Offsets are into the exact text passed in, so the caller can slice/dim spans.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurInertRegionDetector {

    /** Markdown ATX heading line (levels 1–6). */
    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6})[ \\t]+(\\S.*?)[ \\t]*$");

    /**
     * Welcome / first-interaction / routing vocabulary a flow addendum typically
     * neutralizes — bilingual EN/PT, matched against the heading text only so a
     * passing mention in body copy doesn't flag a whole section.
     */
    private static final Pattern INERT_HEADING = Pattern.compile(
            "(?iu)\\b(welcome|greeting|greetings|first[ -]?(interaction|message|contact|time)"
                    + "|initial (message|greeting)|onboarding|main menu|menu|routing|route|options|choose"
                    + "|boas[- ]?vindas|bem[- ]?vind[oa]|sauda[çc][ãa]o|primeir[ao] (intera[çc][ãa]o|mensagem|contato)"
                    + "|experi[êe]ncias?|op[çc][õo]es|roteamento|escolha)\\b");

    private static final Pattern CONCIERGE_ONLY = Pattern.compile(
            "(?i)<!--\\s*turing:concierge-only\\s*-->");
    private static final Pattern FLOW_ONLY = Pattern.compile(
            "(?i)<!--\\s*turing:flow-only\\s*-->");

    static final String REASON_HEURISTIC = "heuristic";
    static final String REASON_CONCIERGE_ONLY = "concierge_only";
    static final String REASON_FLOW_ONLY = "flow_only";

    /**
     * Returns the inert regions of {@code text} for a turn where a flow is (or
     * isn't) governing. Empty when nothing is inert. Offsets index into
     * {@code text}.
     *
     * @param text       the agent-prompt text as displayed (segment content).
     * @param flowActive whether a chat flow governs the previewed turn.
     */
    public List<TurSystemPromptInertRegionDto> detect(String text, boolean flowActive) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<TurSystemPromptInertRegionDto> regions = new ArrayList<>();
        Matcher headings = HEADING.matcher(text);
        List<int[]> sections = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        while (headings.find()) {
            sections.add(new int[] { headings.start(), headings.end() });
            labels.add(headings.group(2).trim());
        }
        for (int i = 0; i < sections.size(); i++) {
            int start = sections.get(i)[0];
            int end = i + 1 < sections.size() ? sections.get(i + 1)[0] : text.length();
            String sectionText = text.substring(start, end);
            String reason = classify(labels.get(i), sectionText, flowActive);
            if (reason != null) {
                regions.add(new TurSystemPromptInertRegionDto(labels.get(i), start, end,
                        TurPromptSegment.estimateTokens(sectionText), reason));
            }
        }
        return regions;
    }

    /**
     * Decides whether a section is inert and why. Author markers win over the
     * heuristic so a block can be attributed precisely; returns {@code null}
     * when the section is live this turn.
     */
    private static String classify(String heading, String sectionText, boolean flowActive) {
        if (CONCIERGE_ONLY.matcher(sectionText).find()) {
            return flowActive ? REASON_CONCIERGE_ONLY : null;
        }
        if (FLOW_ONLY.matcher(sectionText).find()) {
            return flowActive ? null : REASON_FLOW_ONLY;
        }
        if (flowActive && INERT_HEADING.matcher(heading).find()) {
            return REASON_HEURISTIC;
        }
        return null;
    }
}
