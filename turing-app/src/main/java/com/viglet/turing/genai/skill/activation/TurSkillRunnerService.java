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

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.TurChatToolOptions;
import com.viglet.turing.genai.TurToolExecutionLoop;
import com.viglet.turing.genai.tool.TurCustomToolCallbackService;
import com.viglet.turing.genai.tool.TurDslToolService;
import com.viglet.turing.genai.tool.TurToolCallbackPipeline;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.skill.TurSkill;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * T323 / §IX.4.d — the chat/SN-facing <strong>skill delegation layer</strong>:
 * the seam that lets a conversation use an Anthropic-compatible skill while the
 * skill itself <em>always runs on the Global Settings Default LLM</em>, never on
 * the agent's (or SN site's) own model.
 *
 * <p><b>Why a separate model.</b> A skill is a portable capability authored to a
 * standard; it should behave identically regardless of which agent or site
 * triggers it. Binding skill execution to the deployment's default model
 * decouples a skill's behaviour from each caller's per-agent model choice (which
 * may be a cheap or domain-tuned model unsuited to operating a skill).
 *
 * <p><b>How the delegation works.</b> The parent chat turn (driven by the agent's
 * own model) sees only a cheap progressive-disclosure block — each available
 * skill's {@code name} + {@code description} — and a single {@code run_skill}
 * tool ({@link TurRunSkillToolCallback}). When the model decides a skill fits, it
 * calls {@code run_skill(skill, task)}; that tool routes here, which:
 * <ol>
 *   <li>resolves the Default LLM from Global Settings and builds its {@link ChatModel};</li>
 *   <li>seeds a focused single-skill sub-conversation using the T322
 *       {@link TurSkillActivationHarness} (its prompt block as the system message
 *       and its {@code load_skill} / {@code skill_bash} callbacks as the toolset),
 *       so the sub-loop reads the skill's {@code SKILL.md} and drives its sandbox;</li>
 *   <li>also exposes Turing's Semantic Navigation search to the skill (T324):
 *       the {@link TurDslToolService} toolset ({@code dsl_list_indices} /
 *       {@code dsl_get_mappings} / {@code dsl_search} / …) is added to the sub-loop
 *       so the skill can leverage Turing's own indexed content — the tools run
 *       in-process and the model bridges results into the sandbox {@code /workspace}
 *       via {@code skill_bash} (no container network required);</li>
 *   <li>runs the agentic tool loop ({@link TurToolExecutionLoop}) on the Default
 *       LLM until it produces a final answer, and returns that text to the parent
 *       turn as the {@code run_skill} tool result.</li>
 * </ol>
 *
 * <p>The sandbox session the sub-loop drives is keyed by
 * {@code (agentId, conversationId, skillId)} — the same conversation scope the
 * parent turn carries — so a skill's {@code /workspace} persists across turns of
 * the same conversation (T321).
 *
 * <p><b>Gating.</b> {@link #isAvailable()} requires both the sandbox engine
 * (object storage configured AND Code Interpreter mode {@code DOCKER}, via the
 * harness) <em>and</em> a configured, enabled Default LLM. When unavailable the
 * prompt block and tool callbacks are empty, so a chat/SN turn is byte-for-byte
 * unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurSkillRunnerService {

    private final TurSkillActivationHarness harness;
    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurLLMTokenUsageService tokenUsageService;
    private final TurToolCallbackPipeline toolCallbackPipeline;
    private final TurToolExecutionLoop toolExecutionLoop;
    private final TurDslToolService dslToolService;

    public TurSkillRunnerService(TurSkillActivationHarness harness,
            TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurLlmModelFactory llmModelFactory,
            TurSecretCryptoService secretCryptoService,
            TurLLMTokenUsageService tokenUsageService,
            TurToolCallbackPipeline toolCallbackPipeline,
            TurToolExecutionLoop toolExecutionLoop,
            TurDslToolService dslToolService) {
        this.harness = harness;
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.llmModelFactory = llmModelFactory;
        this.secretCryptoService = secretCryptoService;
        this.tokenUsageService = tokenUsageService;
        this.toolCallbackPipeline = toolCallbackPipeline;
        this.toolExecutionLoop = toolExecutionLoop;
        this.dslToolService = dslToolService;
    }

    /**
     * Whether skills can be delegated at all: the sandbox engine must be
     * available (storage + {@code DOCKER}) AND a Default LLM must be configured
     * and enabled in Global Settings. Never throws.
     */
    public boolean isAvailable() {
        return harness.isAvailable() && defaultLlmInstance() != null;
    }

    /** All enabled, indexed skills — the candidate set offered to a turn. */
    public List<TurSkill> availableSkills() {
        return harness.availableSkills();
    }

    /**
     * T325 / §IX.4.d — the skills offered to a turn, honouring an optional
     * <strong>skill-mode pin</strong>. A multi-skill ZIP imports as independent
     * units (each its own {@link TurSkill}); this lets a chat caller select one
     * of them as a distinct "mode/flow" for the conversation:
     * <ul>
     *   <li>when {@code selectedSkillId} is blank/null — the legacy path — the
     *       full {@link #availableSkills()} candidate set is offered for
     *       progressive disclosure (the model picks via {@code run_skill});</li>
     *   <li>when it pins one skill (by {@code id} or, for SDK convenience, by
     *       case-insensitive {@code name}) only that single enabled, indexed
     *       skill is offered — so the parent prompt names just it and
     *       {@code run_skill} is constrained to it.</li>
     * </ul>
     * An unknown or disabled pin yields an empty list (no skill offered),
     * never a fall-back to the whole set, so a pinned mode can't silently widen.
     */
    public List<TurSkill> offeredSkills(String selectedSkillId) {
        List<TurSkill> all = availableSkills();
        if (!StringUtils.hasText(selectedSkillId)) {
            return all;
        }
        String pin = selectedSkillId.trim();
        return all.stream()
                .filter(skill -> pin.equals(skill.getId()) || pin.equalsIgnoreCase(safe(skill.getName())))
                .findFirst()
                .map(List::<TurSkill>of)
                .orElseGet(List::of);
    }

    /**
     * The cheap parent-turn system-prompt block: each offered skill's
     * {@code name} (+ optional version) and {@code description}, plus the
     * instruction to invoke {@code run_skill} when one matches the task. Returns
     * an empty string when delegation is unavailable or no skill is offered, so
     * the parent prompt is unchanged.
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
        return "\n\n# Skills\n"
                + "You can use the following skills. Each skill is a self-contained capability"
                + " (instructions, reference files, and runnable scripts) that runs autonomously"
                + " in a sandboxed container. When one of the skills below matches the user's"
                + " task — judged by its short description — call the `" + TurRunSkillToolCallback.TOOL_NAME
                + "` tool with the skill `name` and a clear `task` describing what you need; the"
                + " skill runs to completion and returns its result. Do NOT call it unless a"
                + " skill's description matches the task.\n\n"
                + "Available skills:"
                + sb;
    }

    /**
     * The single parent-facing {@code run_skill} delegation callback, constrained
     * to the offered skill set. Raw — the caller must run it through
     * {@link TurToolCallbackPipeline#decorate} like every other tool source.
     * Returns an empty array when delegation is unavailable or no skill is offered.
     */
    public ToolCallback[] buildToolCallbacks(List<TurSkill> skills) {
        if (!isAvailable() || skills == null || skills.isEmpty()) {
            return new ToolCallback[0];
        }
        List<TurSkill> offered = skills.stream()
                .filter(skill -> skill != null && skill.getName() != null && !skill.getName().isBlank())
                .toList();
        if (offered.isEmpty()) {
            return new ToolCallback[0];
        }
        return new ToolCallback[] { new TurRunSkillToolCallback(offered, this) };
    }

    /**
     * Operate {@code skill} to accomplish {@code task}, autonomously, on the
     * Default LLM. Drives a focused single-skill sub-loop (system prompt + the
     * T322 {@code load_skill} / {@code skill_bash} tools) until the model
     * produces a final answer, then returns that text. The sandbox session is
     * scoped by {@code (agentId, conversationId, skillId)} so the skill's
     * {@code /workspace} persists across the conversation. Never throws —
     * failures are returned as a plain string the parent model can recover from.
     *
     * @param skill          the resolved skill to run (offered set member)
     * @param task           the parent model's description of what it needs
     * @param agentId        conversation's agent id (from the parent ToolContext)
     * @param conversationId conversation id (from the parent ToolContext)
     */
    public String runSkill(TurSkill skill, String task, String agentId, String conversationId) {
        if (skill == null) {
            return "run_skill: no skill resolved.";
        }
        if (!isAvailable()) {
            return "run_skill is unavailable: skills require object storage, the Code Interpreter "
                    + "execution mode to be DOCKER, and a Default LLM configured in Global Settings.";
        }
        TurLLMInstance llmInstance = defaultLlmInstance();
        if (llmInstance == null) {
            return "run_skill is unavailable: no enabled Default LLM is configured in Global Settings.";
        }
        try {
            String decryptedApiKey = secretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
            ChatModel chatModel = llmModelFactory.createChatModel(llmInstance, decryptedApiKey);

            List<TurSkill> one = List.of(skill);
            // Reuse the T322 harness for the SUB-loop: its progressive-disclosure
            // block (the skill + load_skill/skill_bash protocol) becomes the
            // sub-conversation's system message and its callbacks become the
            // sub-loop's toolset. The skill is read and driven entirely here, on
            // the Default LLM.
            String system = "You are operating a skill to accomplish the task the calling assistant"
                    + " delegated to you. Use the skill below: read its instructions and run it in its"
                    + " sandbox, then reply with ONLY the final result the caller needs (no preamble)."
                    + " You also have Turing Semantic Navigation search tools (list indices, get"
                    + " mappings, search): use them when the skill needs Turing's own indexed content"
                    + " — discover an index, inspect its fields, query it, and write any results you"
                    + " need into the skill's /workspace via skill_bash."
                    + harness.buildSystemPromptBlock(one);
            // T324 / §IX.4.d — expose Turing search to the skill as a tool. The
            // sub-loop gets the skill activation callbacks (load_skill/skill_bash)
            // PLUS the Semantic Navigation DSL toolset (dsl_list_indices /
            // dsl_get_mappings / dsl_search / …) — the same surface the semantic
            // chat exposes — so a skill can leverage Turing's own data. The tools
            // execute in-process (no container network needed); the model bridges
            // results into the sandbox workspace through skill_bash. All decorated
            // together so descriptions (prompts/tools/**) + logging apply uniformly.
            ToolCallback[] turingSearchCallbacks = MethodToolCallbackProvider.builder()
                    .toolObjects(dslToolService)
                    .build()
                    .getToolCallbacks();
            ToolCallback[] tools = toolCallbackPipeline.decorate(
                    Stream.concat(
                            Arrays.stream(harness.buildToolCallbacks(one)),
                            Arrays.stream(turingSearchCallbacks))
                            .toArray(ToolCallback[]::new));

            Map<String, Object> toolContext = new LinkedHashMap<>();
            if (StringUtils.hasText(agentId)) {
                toolContext.put(TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID, agentId);
            }
            if (StringUtils.hasText(conversationId)) {
                toolContext.put(TurCustomToolCallbackService.TOOL_CONTEXT_CONVERSATION_ID, conversationId);
            }
            // Seed from the provider's own concrete options — Spring AI 2.0.0
            // hard-casts prompt.getOptions() to the provider type, so a generic
            // DefaultToolCallingChatOptions throws ClassCastException. See
            // TurChatToolOptions.
            var optionsBuilder = TurChatToolOptions.builderFrom(chatModel)
                    .toolCallbacks(tools);
            if (!toolContext.isEmpty()) {
                optionsBuilder.toolContext(toolContext);
            }

            List<Message> messages = List.of(
                    new SystemMessage(system),
                    new UserMessage(StringUtils.hasText(task) ? task : "Use the skill to help the user."));

            ChatResponse response = toolExecutionLoop.call(chatModel,
                    new Prompt(messages, optionsBuilder.build()));

            recordUsageSafely(llmInstance, response);

            String text = response == null || response.getResult() == null
                    ? null
                    : response.getResult().getOutput().getText();
            if (text == null || text.isBlank()) {
                return "Skill '" + skill.getName() + "' produced no output.";
            }
            return text;
        } catch (RuntimeException e) {
            log.warn("[run_skill] skill='{}' conv={} failed: {}",
                    skill.getName(), conversationId, e.getMessage());
            return "Failed to run skill '" + skill.getName() + "': " + e.getMessage();
        }
    }

    /** Records token usage, swallowing (and logging) any failure so it never breaks the skill run. */
    private void recordUsageSafely(TurLLMInstance llmInstance, ChatResponse response) {
        try {
            tokenUsageService.recordUsage(llmInstance, response, resolveUsername(),
                    null, com.viglet.turing.observability.TurMeterNames.STAGE_CHAT_SKILL);
        } catch (RuntimeException usageError) {
            log.debug("[run_skill] token usage record failed: {}", usageError.getMessage());
        }
    }

    /** The configured Default LLM instance when present and enabled, else {@code null}. */
    private TurLLMInstance defaultLlmInstance() {
        String llmId = globalSettingsService.getDefaultLlmId();
        if (!StringUtils.hasText(llmId)) {
            return null;
        }
        return llmInstanceRepository.findById(llmId)
                .filter(instance -> instance.getEnabled() == 1)
                .orElse(null);
    }

    private static String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
