/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.skill.activation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.skill.TurSkillCatalogService;
import com.viglet.turing.genai.skill.sandbox.TurSkillSandboxService;
import com.viglet.turing.genai.skill.sandbox.TurSkillSandboxSessionManager;
import com.viglet.turing.persistence.model.skill.TurSkill;

/**
 * T322 / §IX.4.d — the <strong>skill activation harness</strong>: the piece
 * that makes an Anthropic-compatible skill folder actually <em>run</em>, via
 * progressive disclosure.
 *
 * <p>Activation is deliberately cheap-then-deep, matching Anthropic's own
 * three-level disclosure model:
 * <ol>
 *   <li><b>Cheap injection.</b> {@link #buildSystemPromptBlock(List)} lists every
 *       available skill's {@code name} + {@code description} only — a handful of
 *       tokens per skill — so the model can judge relevance without paying to
 *       load any instructions.</li>
 *   <li><b>Load on demand.</b> When the model decides a skill is relevant it
 *       calls the {@code load_skill} tool, which returns that skill's full
 *       {@code SKILL.md} body (the second disclosure level).</li>
 *   <li><b>Execute in the mounted folder.</b> The model then drives the skill's
 *       sandbox through {@code skill_bash} — reading {@code references/}, running
 *       {@code scripts/}, and writing drafts to the persistent {@code /workspace}
 *       (the third level: bundled files the model never has to read into the
 *       prompt).</li>
 * </ol>
 *
 * <p>This service is the single seam the chat path (T323) and the semantic
 * navigation path (T324) wire into: each resolves the <em>available</em> skills
 * for its turn (by description match and/or an explicit user pick) and asks the
 * harness for the prompt block and the tool callbacks. The harness itself is
 * generic — it neither decides which skills are offered nor where the resulting
 * callbacks are decorated ({@code TurToolCallbackPipeline.decorate(...)} is the
 * caller's job, per the project-wide tool convention).
 *
 * <p><b>Gating.</b> The harness is only {@link #isAvailable() available} when the
 * underlying sandbox is — i.e. object storage is configured <em>and</em> the
 * Code Interpreter execution mode is {@code DOCKER} (T80/T321). On a
 * {@code NATIVE} or storage-disabled deployment both {@code buildSystemPromptBlock}
 * and {@code buildToolCallbacks} return empty so the chat/SN prompts are
 * byte-for-byte unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Service
public class TurSkillActivationHarness {

    /** Markdown heading that opens the injected progressive-disclosure block. */
    static final String BLOCK_HEADER = "# Skills (progressive disclosure)";

    private final TurSkillCatalogService catalogService;
    private final TurSkillSandboxService sandboxService;
    private final TurSkillSandboxSessionManager sessionManager;

    public TurSkillActivationHarness(TurSkillCatalogService catalogService,
            TurSkillSandboxService sandboxService,
            TurSkillSandboxSessionManager sessionManager) {
        this.catalogService = catalogService;
        this.sandboxService = sandboxService;
        this.sessionManager = sessionManager;
    }

    /**
     * Whether skills can be activated at all: the sandbox engine must be
     * available (object storage configured AND Code Interpreter mode
     * {@code DOCKER}). Never throws.
     */
    public boolean isAvailable() {
        return sandboxService.isAvailable();
    }

    /**
     * All enabled, indexed skills — the default candidate set a caller may offer
     * to a turn when it has no narrower selection. Empty when storage is
     * disabled. The list is sorted by name for deterministic prompt ordering.
     */
    public List<TurSkill> availableSkills() {
        return catalogService.listAll().stream()
                .filter(skill -> skill.getEnabled() == 1)
                .sorted(Comparator.comparing(s -> safe(s.getName()).toLowerCase()))
                .toList();
    }

    /**
     * Compose the cheap progressive-disclosure system-prompt block for the
     * supplied skills: each skill's {@code name} (+ optional version) and
     * {@code description}, plus the activation protocol that tells the model how
     * to load a skill and drive its sandbox.
     *
     * @param skills the skills offered to this turn (may be {@code null}/empty)
     * @return a leading-newline block ready to append to the base prompt, or an
     *         empty string when the harness is unavailable or no skill is offered
     */
    public String buildSystemPromptBlock(List<TurSkill> skills) {
        if (!isAvailable() || skills == null || skills.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        skills.stream()
                .filter(skill -> skill != null && skill.getName() != null && !skill.getName().isBlank())
                .sorted(Comparator.comparing(s -> safe(s.getName()).toLowerCase()))
                .forEach(skill -> {
                    sb.append("\n\n## ").append(skill.getName().trim());
                    if (skill.getVersion() != null && !skill.getVersion().isBlank()) {
                        sb.append(" (v").append(skill.getVersion().trim()).append(')');
                    }
                    String description = safe(skill.getDescription()).trim();
                    sb.append('\n').append(description.isEmpty() ? "(no description)" : description);
                });
        if (sb.isEmpty()) {
            return "";
        }
        return "\n\n" + BLOCK_HEADER + "\n"
                + "You can use the following skills. Each skill is a self-contained capability"
                + " (instructions, reference files, and runnable scripts) that executes in a"
                + " sandboxed container. Do NOT load a skill's instructions unless its description"
                + " matches the task.\n\n"
                + "To use a skill:\n"
                + "1. When a skill below is relevant, call `" + TurLoadSkillToolCallback.TOOL_NAME
                + "` with its `name` to read its full instructions (SKILL.md).\n"
                + "2. Follow those instructions, using `" + TurSkillBashToolCallback.TOOL_NAME
                + "` to run shell commands inside that skill's sandbox. The skill folder is mounted"
                + " read-only at `/skill` (also `$SKILL_DIR`); a persistent `/workspace` is writable"
                + " and survives across turns. Use it to read `references/`, run `scripts/`, and"
                + " write output files.\n\n"
                + "Available skills:"
                + sb;
    }

    /**
     * Build the activation tool callbacks for the supplied skills:
     * {@code load_skill} (second-level disclosure) and {@code skill_bash}
     * (mounted-folder execution). Both are constrained to the offered set — a
     * skill not listed here cannot be loaded or run through these callbacks.
     *
     * <p>The returned callbacks are <em>raw</em>: the caller must still run them
     * through {@code TurToolCallbackPipeline.decorate(...)} before handing them
     * to a {@code ChatModel}, exactly like every other tool source.
     *
     * @param skills the skills offered to this turn (may be {@code null}/empty)
     * @return the two activation callbacks, or an empty array when the harness is
     *         unavailable or no skill is offered
     */
    public ToolCallback[] buildToolCallbacks(List<TurSkill> skills) {
        if (!isAvailable() || skills == null || skills.isEmpty()) {
            return new ToolCallback[0];
        }
        List<TurSkill> offered = new ArrayList<>(skills.stream()
                .filter(skill -> skill != null && skill.getName() != null && !skill.getName().isBlank())
                .toList());
        if (offered.isEmpty()) {
            return new ToolCallback[0];
        }
        return new ToolCallback[] {
                new TurLoadSkillToolCallback(offered, catalogService),
                new TurSkillBashToolCallback(offered, sessionManager, sandboxService)
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
