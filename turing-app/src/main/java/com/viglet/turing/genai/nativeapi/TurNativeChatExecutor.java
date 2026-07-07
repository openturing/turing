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
package com.viglet.turing.genai.nativeapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.anthropic.client.AnthropicClient;
import com.google.genai.Client;
import com.openai.client.OpenAIClient;
import com.viglet.turing.genai.TurChatPromptAssembler;
import com.viglet.turing.genai.TurChatPromptRequest;
import com.viglet.turing.genai.TurChatToolResolver;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.citation.TurCitationDocument;
import com.viglet.turing.genai.citation.TurCitationDriftService;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.genai.tool.TurRagSearchToolService;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.capability.TurNativeCapabilityResolver;
import com.viglet.turing.genai.nativeapi.capability.TurNativeCapabilityResolver.NativeSelection;
import com.viglet.turing.genai.nativeapi.mcp.TurMcpFederationDirectory;
import com.viglet.turing.genai.nativeapi.mcp.TurMcpFederationDirectory.FederatedMcp;
import com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicContextManagement;
import com.viglet.turing.genai.nativeapi.anthropic.TurAnthropicMessagesService;
import com.viglet.turing.genai.nativeapi.anthropic.computeruse.TurAnthropicComputerUseService;
import com.viglet.turing.genai.nativeapi.anthropic.editor.TurAnthropicEditorService;
import com.viglet.turing.genai.nativeapi.anthropic.memory.TurAgentMemoryScopeResolver;
import com.viglet.turing.genai.nativeapi.anthropic.memory.TurAnthropicMemoryService;
import com.viglet.turing.genai.nativeapi.openai.TurOpenAiResponsesService;
import com.viglet.turing.genai.nativeapi.openai.TurReasoningEffortResolver;
import com.viglet.turing.genai.nativeapi.openai.TurStoredCompletionsService;
import com.viglet.turing.genai.nativeapi.openai.computeruse.TurOpenAiComputerUseService;
import com.viglet.turing.genai.nativeapi.gemini.TurGeminiComputerUseService;
import com.viglet.turing.genai.nativeapi.gemini.TurGoogleGenAiNativeService;
import com.viglet.turing.genai.persona.TurAgentPersonaResolver;
import com.viglet.turing.genai.persona.TurPersonaModelCalibration;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.persona.TurPersona;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * T131 / §X.2 — sibling of {@code TurAgentChatExecutor} that routes a turn
 * through a native provider SDK when the instance has opted into a native
 * capability, and otherwise stands aside so the caller falls back to the Spring
 * AI path verbatim.
 *
 * <p>This is the opt-in seam in action: {@link #tryExecute} returns
 * {@link Optional#empty()} for every instance that has no enabled native
 * capability for its vendor — i.e. <b>all existing agents</b>, so behaviour is
 * unchanged until an admin flips a capability row on.
 *
 * <p>T131 / §X.2 — when it <em>does</em> handle the turn it now composes the
 * system prompt + history through the same post-T33 collaborator the Spring AI
 * path uses ({@link TurChatPromptAssembler}, fed by the single per-turn
 * {@link TurAgentPersonaResolver} resolution). That fuses the RAG override
 * base, persona voice + brand-context, MCP server instructions, rich-content
 * rendering guidance and the chat-memory layers (T30 relevance retrieval +
 * T115 compression) onto the native input — so a turn reads identically
 * whether it dispatches through Spring AI or the native OpenAI Responses SDK.
 * The flow harness ({@code flowContext}) is intentionally left {@code null}:
 * the chat REST controller only routes <b>plain</b> turns (no attachments, no
 * chat flow) into the native path, and the Responses built-in tools have no
 * equivalent of the Spring AI tool-execution loop the flow engine drives.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurNativeChatExecutor {

    private final TurGenAiLlmProviderFactory providerFactory;
    private final TurNativeProviderClient nativeClient;
    private final TurNativeCapabilityService capabilityService;
    private final TurOpenAiResponsesService openAiResponsesService;
    private final TurOpenAiComputerUseService openAiComputerUseService;
    private final TurAnthropicMessagesService anthropicMessagesService;
    private final TurAnthropicComputerUseService anthropicComputerUseService;
    private final TurAnthropicEditorService anthropicEditorService;
    private final TurAnthropicMemoryService anthropicMemoryService;
    private final TurGoogleGenAiNativeService googleGenAiNativeService;
    private final TurGeminiComputerUseService geminiComputerUseService;
    private final com.viglet.turing.genai.nativeapi.cohere.TurCohereMessagesService cohereMessagesService;
    private final TurChatPromptAssembler promptAssembler;
    private final TurAgentPersonaResolver personaResolver;
    private final TurNativeCapabilityResolver capabilityResolver;
    private final TurChatToolResolver toolResolver;
    private final TurMcpFederationDirectory mcpFederationDirectory;
    private final TurRagSearchToolService ragSearchToolService;
    private final TurProviderOptionsParser optionsParser;
    private final TurCitationDriftService citationDriftService;
    private final com.viglet.turing.genai.servicetier.TurServiceTierResolver serviceTierResolver;
    private final TurAnthropicContextManagement anthropicContextManagement;
    private final TurAgentMemoryScopeResolver memoryScopeResolver;
    private final TurStoredCompletionsService storedCompletionsService;
    private final com.viglet.turing.genai.nativeapi.files.TurNativeDocumentGroundingService
            nativeDocumentGroundingService;
    private final com.viglet.turing.genai.nativeapi.openai.TurReasoningEffortResolver
            reasoningEffortResolver;
    private final com.viglet.turing.genai.safety.TurSafetyIdentifierService safetyIdentifierService;
    private final com.viglet.turing.genai.nativeapi.liveanswers.TurLiveAnswersService liveAnswersService;
    private final com.viglet.turing.genai.capture.TurPromptCaptureService promptCaptureService;

    /** T152 / §X.7.a — request-option key (per-agent {@code requestOptionsJson}). */
    private static final String REQUEST_OPTION_CITATIONS = "citations";
    /** T176 / §X.12.b — request-option key for native PDF grounding. */
    private static final String REQUEST_OPTION_NATIVE_PDF = "native-pdf";
    /** T178 / §X.13.a — request-option key for the OpenAI reasoning summary mode. */
    private static final String REQUEST_OPTION_REASONING_SUMMARY = "reasoning-summary";
    /** T179 / §X.13.b — request-option key for the OpenAI reasoning effort budget. */
    private static final String REQUEST_OPTION_REASONING_EFFORT = "reasoning-effort";
    /** T152 — how many passages to attach as document blocks when citations are on. */
    private static final int CITATION_TOP_K = 5;

    public TurNativeChatExecutor(TurGenAiLlmProviderFactory providerFactory,
            TurNativeProviderClient nativeClient,
            TurNativeCapabilityService capabilityService,
            TurOpenAiResponsesService openAiResponsesService,
            TurOpenAiComputerUseService openAiComputerUseService,
            TurAnthropicMessagesService anthropicMessagesService,
            TurAnthropicComputerUseService anthropicComputerUseService,
            TurAnthropicEditorService anthropicEditorService,
            TurAnthropicMemoryService anthropicMemoryService,
            TurGoogleGenAiNativeService googleGenAiNativeService,
            TurGeminiComputerUseService geminiComputerUseService,
            com.viglet.turing.genai.nativeapi.cohere.TurCohereMessagesService cohereMessagesService,
            TurChatPromptAssembler promptAssembler,
            TurAgentPersonaResolver personaResolver,
            TurNativeCapabilityResolver capabilityResolver,
            TurChatToolResolver toolResolver,
            TurMcpFederationDirectory mcpFederationDirectory,
            TurRagSearchToolService ragSearchToolService,
            TurProviderOptionsParser optionsParser,
            TurCitationDriftService citationDriftService,
            com.viglet.turing.genai.servicetier.TurServiceTierResolver serviceTierResolver,
            TurAnthropicContextManagement anthropicContextManagement,
            TurAgentMemoryScopeResolver memoryScopeResolver,
            TurStoredCompletionsService storedCompletionsService,
            com.viglet.turing.genai.nativeapi.files.TurNativeDocumentGroundingService
                    nativeDocumentGroundingService,
            com.viglet.turing.genai.nativeapi.openai.TurReasoningEffortResolver
                    reasoningEffortResolver,
            com.viglet.turing.genai.safety.TurSafetyIdentifierService safetyIdentifierService,
            com.viglet.turing.genai.nativeapi.liveanswers.TurLiveAnswersService liveAnswersService,
            com.viglet.turing.genai.capture.TurPromptCaptureService promptCaptureService) {
        this.providerFactory = providerFactory;
        this.nativeClient = nativeClient;
        this.capabilityService = capabilityService;
        this.openAiResponsesService = openAiResponsesService;
        this.openAiComputerUseService = openAiComputerUseService;
        this.anthropicMessagesService = anthropicMessagesService;
        this.anthropicComputerUseService = anthropicComputerUseService;
        this.anthropicEditorService = anthropicEditorService;
        this.anthropicMemoryService = anthropicMemoryService;
        this.googleGenAiNativeService = googleGenAiNativeService;
        this.geminiComputerUseService = geminiComputerUseService;
        this.cohereMessagesService = cohereMessagesService;
        this.promptAssembler = promptAssembler;
        this.personaResolver = personaResolver;
        this.capabilityResolver = capabilityResolver;
        this.toolResolver = toolResolver;
        this.mcpFederationDirectory = mcpFederationDirectory;
        this.ragSearchToolService = ragSearchToolService;
        this.optionsParser = optionsParser;
        this.citationDriftService = citationDriftService;
        this.serviceTierResolver = serviceTierResolver;
        this.anthropicContextManagement = anthropicContextManagement;
        this.memoryScopeResolver = memoryScopeResolver;
        this.storedCompletionsService = storedCompletionsService;
        this.nativeDocumentGroundingService = nativeDocumentGroundingService;
        this.reasoningEffortResolver = reasoningEffortResolver;
        this.safetyIdentifierService = safetyIdentifierService;
        this.liveAnswersService = liveAnswersService;
        this.promptCaptureService = promptCaptureService;
    }

    /**
     * Attempt to handle the turn natively.
     *
     * @return the native SSE stream, or {@link Optional#empty()} when this turn
     *         is not a native one (no enabled capability, unsupported vendor, or
     *         no usable native client) — the caller must then delegate to the
     *         Spring AI executor.
     */
    public Optional<Flux<ChatResponse>> tryExecute(TurAIAgent agent, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPromptOverride, String conversationId) {
        String pluginType = resolvePluginType(instance);
        boolean isOpenAi = "openai".equals(pluginType);
        boolean isAnthropic = "anthropic".equals(pluginType);
        boolean isGemini = "gemini".equals(pluginType);
        boolean isCohere = "cohere".equals(pluginType);
        if (!isOpenAi && !isAnthropic && !isGemini && !isCohere) {
            // Only OpenAI (F.2), Anthropic (F.3), Gemini (T489 / F.16) and — for
            // native citations only — Cohere (T519 / §XXVIII.15) have a native
            // path; other vendors always fall through to the Spring AI executor.
            return Optional.empty();
        }

        // T519 / §XXVIII.15 — Cohere has a native path ONLY for Command citation
        // mode (the `citations` Request Option). Without it, a Cohere agent stays
        // on the OpenAI-compatible Spring AI path — unchanged.
        boolean cohereCitations = isCohere && isCitationsEnabled(agent);
        if (isCohere && !cohereCitations) {
            return Optional.empty();
        }

        // T152 / §X.7.a — the per-agent "citations" request option triggers the
        // native Anthropic path on its own: passages are attached to the prompt
        // as document blocks and the per-sentence citations are streamed back.
        // Gated strictly on requestOptionsJson, so it stays opt-in and never
        // changes existing agents (null/false → unchanged).
        boolean citationsEnabled = isAnthropic && isCitationsEnabled(agent);
        // T176 / §X.12.b — native PDF grounding rides the same Anthropic document
        // path as citations (and pairs with it). Either option forces the document
        // path; both stay opt-in (null/false → unchanged).
        boolean nativePdfEnabled = isAnthropic && isNativePdfEnabled(agent);
        boolean groundWithDocuments = citationsEnabled || nativePdfEnabled;

        // T178 / §X.13.a + T179 / §X.13.b — the OpenAI reasoning request options
        // (summary mode and/or effort budget) force the native Responses path on
        // their own (the Spring AI chat-completions path can't surface a reasoning
        // summary nor shape effort), exactly like citations force the Anthropic
        // path. Gated on a reasoning model: on a chat model (gpt-4o…) the options
        // are a no-op and the turn stays on the unchanged Spring AI path.
        String reasoningSummaryMode = isOpenAi
                ? requestOptionValue(agent, REQUEST_OPTION_REASONING_SUMMARY) : null;
        String reasoningEffortMode = isOpenAi
                ? requestOptionValue(agent, REQUEST_OPTION_REASONING_EFFORT) : null;
        boolean reasoningOptedIn = StringUtils.hasText(reasoningSummaryMode)
                || StringUtils.hasText(reasoningEffortMode);
        boolean openAiReasoningForced = isOpenAi && reasoningOptedIn
                && TurOpenAiResponsesService.isReasoningModel(instance);

        List<EnabledCapability> instanceCapabilities =
                capabilityService.enabledFor(instance.getId(), pluginType);
        if (instanceCapabilities.isEmpty() && !groundWithDocuments && !openAiReasoningForced
                && !cohereCitations) {
            return Optional.empty();
        }

        // T433 / §X.18.b — resolve the per-agent selection over what the instance
        // is capable of. Legacy (no explicit selection) keeps the pre-T433
        // winner-takes-all set; an explicit selection narrows to
        // agentSelected ∩ instanceCapable and may attach coexisting Turing tools.
        boolean legacy = capabilityResolver.isLegacy(agent.getNativeCapabilities());
        NativeSelection selection = legacy
                ? null
                : capabilityResolver.resolve(agent.getNativeCapabilities(), instanceCapabilities);
        if (selection != null && selection.isEmpty() && !groundWithDocuments
                && !openAiReasoningForced) {
            // The agent opted in but none of its picks are enabled on this
            // instance — defer to the Spring AI path (which runs the agent's
            // normal tool set) rather than a native turn with no capabilities.
            // (Citations / reasoning-summary still force the native path — below.)
            return Optional.empty();
        }
        List<EnabledCapability> capabilities = selection == null
                ? instanceCapabilities
                : selection.nativeCapabilities();

        // T145 / §X.5.b — federate the agent's attached MCP servers to this
        // vendor when the agent opted in (mcpNativeFederation). Each federated
        // server becomes a synthetic OPENAI_MCP / ANTHROPIC_MCP capability the
        // vendor service wires through its shipped remote-MCP path; the
        // federated servers are then excluded from the Turing MCP client path
        // (resolveCoexistingTools) so they aren't double-wired.
        List<FederatedMcp> federated = mcpFederationDirectory.federate(agent, pluginType);
        if (!federated.isEmpty()) {
            List<EnabledCapability> merged = new ArrayList<>(capabilities);
            federated.forEach(f -> merged.add(f.capability()));
            capabilities = merged;
        }
        Set<String> federatedServerIds = federated.stream()
                .map(f -> f.serverId())
                .collect(Collectors.toSet());

        // Acquire the vendor's native client up front; a missing/unusable client
        // (e.g. no API key) means we can't handle the turn natively.
        Optional<OpenAIClient> openAiClient = isOpenAi ? nativeClient.openAi(instance) : Optional.empty();
        Optional<AnthropicClient> anthropicClient = isAnthropic
                ? nativeClient.anthropic(instance) : Optional.empty();
        Optional<Client> geminiClient = isGemini ? nativeClient.gemini(instance) : Optional.empty();
        if (isOpenAi && openAiClient.isEmpty()) {
            return Optional.empty();
        }
        if (isAnthropic && anthropicClient.isEmpty()) {
            return Optional.empty();
        }
        if (isGemini && geminiClient.isEmpty()) {
            return Optional.empty();
        }

        // T131 / §X.2 — fuse the post-T33 prompt-assembly collaborator onto the
        // native path. The base prompt is the RAG override (when the SN AI Mode
        // flow passes one) or the agent's own system prompt; the assembler then
        // layers on MCP instructions, rich-content guidance, persona voice and
        // the chat-memory turns. Persona is resolved once per turn, mirroring
        // §I.5 step 2 of the Spring AI executor. The flow context is null on the
        // native path (see class javadoc).
        String baseSystemPrompt = StringUtils.hasText(systemPromptOverride)
                ? systemPromptOverride
                : agent.getSystemPrompt();
        TurPersona activePersona = personaResolver.resolve(agent, conversationId);
        // T606 — opt-in persona style→model calibration for the native paths (same
        // seam as the Spring-AI CALL path). Params.NONE when the persona hasn't
        // opted in, so the native request is byte-for-byte unchanged by default.
        TurPersonaModelCalibration.Params calibration =
                TurPersonaModelCalibration.forPersona(activePersona);
        String lastUserMessage = lastMessageOfRole(history, "user");
        List<Message> assembled = promptAssembler.assemble(
                new TurChatPromptRequest(agent, history, conversationId,
                        /* flowContext = */ null, activePersona, lastUserMessage, baseSystemPrompt));
        AssembledTurn turn = splitAssembled(assembled);

        // T187 / §X.15.a — "Live answers" hybrid mode. When the agent opted in,
        // fuse the indexed knowledge base + web_search + web_fetch + code_execution
        // into one turn: append the [KB-n] grounding block (the leg the OpenAI
        // Responses path otherwise lacks) + the fusion-guidance prompt. Skip the
        // grounding block when the Anthropic citations / native-PDF path already
        // attaches the passages as document blocks (groundWithDocuments), to avoid
        // double-grounding. No-op (unchanged turn) for every non-live agent.
        if (liveAnswersService.isEnabled(agent)) {
            String groundedPrompt = liveAnswersService.augment(
                    turn.systemPrompt(), lastUserMessage, groundWithDocuments);
            turn = new AssembledTurn(groundedPrompt, turn.history());
        }

        // T618 — capture the assembled prompt (system message + whole message
        // list) at send time, after the shared assembler + any live-answers
        // grounding but before the vendor branches dispatch, so the Live Preview
        // can replay this native turn verbatim. Opt-in + storage-backed +
        // fail-open (never breaks the turn).
        promptCaptureService.captureNative(agent, conversationId, turn.systemPrompt(), turn.history());

        // T519 / §XXVIII.15 — Cohere native citation branch. Retrieve the top-K
        // passages and run Cohere's v2/chat with them as documents; the response's
        // native citations decode onto the same TurChatCitation/SSE contract as
        // the Anthropic/Gemini paths. Reached only when the citations option is on
        // (gated above); persists the citations for the T155 drift scan like the
        // Anthropic path.
        if (isCohere) {
            List<TurCitationDocument> citationDocuments =
                    ragSearchToolService.retrievePassagesForCitations(lastUserMessage, CITATION_TOP_K);
            log.info("[Native] agent '{}' instance '{}' handled by Cohere citation path "
                    + "({} citation doc(s), persona '{}')", agent.getTitle(), instance.getId(),
                    citationDocuments.size(), activePersona == null ? "none" : activePersona.getName());
            Flux<ChatResponse> messages = cohereMessagesService.chat(instance, turn.history(),
                    turn.systemPrompt(), citationDocuments);
            return Optional.of(citationDriftService.tapForPersistence(messages, agent.getId(),
                    conversationId, lastUserMessage, CITATION_TOP_K));
        }

        // T489 / §X.19 — Gemini native branch. The base path streams a turn
        // through the Google GenAI SDK's generateContentStream with the fused
        // system prompt + history; the enabled capabilities are threaded through
        // for the server-side built-in tools (T490+) to wire. No coexisting-tool
        // loop on the foundation path (Gemini built-in tools are one-shot, like
        // the other vendors' server-side tools).
        if (isGemini) {
            // T494 / §X.19 — computer_use is a multi-turn screenshot/action loop
            // (not a one-shot tool), so it owns the whole turn when enabled, like
            // the OpenAI/Anthropic computer-use variants.
            Optional<EnabledCapability> geminiComputerUse = capabilities.stream()
                    .filter(c -> c.capability() == TurNativeCapability.GEMINI_COMPUTER_USE)
                    .findFirst();
            if (geminiComputerUse.isPresent()) {
                if (capabilities.size() > 1) {
                    log.info("[Native] agent '{}' instance '{}' has Gemini computer_use enabled "
                            + "alongside {} other native capability/ies — driving the computer-use "
                            + "loop only this turn", agent.getTitle(), instance.getId(),
                            capabilities.size() - 1);
                }
                log.info("[Native] agent '{}' instance '{}' handled by Gemini computer-use loop "
                        + "(persona '{}')", agent.getTitle(), instance.getId(),
                        activePersona == null ? "none" : activePersona.getName());
                return Optional.of(geminiComputerUseService.chat(geminiClient.get(), instance,
                        turn.history(), turn.systemPrompt(), geminiComputerUse.get()));
            }
            log.info("[Native] agent '{}' instance '{}' handled by Gemini native path "
                    + "({} capability/ies, persona '{}')", agent.getTitle(), instance.getId(),
                    capabilities.size(), activePersona == null ? "none" : activePersona.getName());
            return Optional.of(googleGenAiNativeService.chat(geminiClient.get(), instance,
                    turn.history(), turn.systemPrompt(), capabilities, calibration));
        }

        // T433 / §X.18.b — "capacidade é mutex, resto coexiste". For an explicit
        // (non-legacy) selection whose picks don't own the turn, attach the
        // agent's Turing/MCP/custom tools whose function isn't claimed by a
        // selected provider-native capability and run them through the native
        // tool-execution loop. Legacy turns (and owns-turn picks) attach none —
        // identical to the pre-T433 one-shot behaviour.
        ToolCallback[] coexistingTools = resolveCoexistingTools(
                selection, agent, activePersona, lastUserMessage, federatedServerIds);

        // F.3 / §X.4 — Anthropic path.
        if (isAnthropic) {
            // T164/T165 / §X.9 — resolve the agent's opt-in context-management
            // (context editing / compaction) once; passed to every Anthropic
            // native path so a turn prunes/summarizes server-side identically.
            TurAnthropicContextManagement.Options ctxOptions =
                    anthropicContextManagement.resolve(agent.getRequestOptionsJson(),
                            agent.isChatMemoryCompressionEnabled());
            // T143 / §X.4.e — computer_use is a multi-turn screenshot/action loop,
            // not a one-shot server tool (and uses the beta Messages API), so it
            // owns the whole turn when enabled — exactly like the OpenAI variant.
            Optional<EnabledCapability> computerUse = capabilities.stream()
                    .filter(c -> c.capability() == TurNativeCapability.ANTHROPIC_COMPUTER_USE)
                    .findFirst();
            if (computerUse.isPresent()) {
                if (capabilities.size() > 1) {
                    log.info("[Native] agent '{}' instance '{}' has Anthropic computer_use enabled "
                            + "alongside {} other native capability/ies — driving the computer-use "
                            + "loop only this turn", agent.getTitle(), instance.getId(),
                            capabilities.size() - 1);
                }
                log.info("[Native] agent '{}' instance '{}' handled by Anthropic computer-use loop "
                        + "(persona '{}')", agent.getTitle(), instance.getId(),
                        activePersona == null ? "none" : activePersona.getName());
                return Optional.of(anthropicComputerUseService.chat(anthropicClient.get(), instance,
                        turn.history(), turn.systemPrompt(), computerUse.get()));
            }

            // T163 / §X.9.a — the memory tool is a client-executed multi-turn loop
            // (Claude reads/writes its memory/ scratchpad in TurAgentWorkspace), so
            // it owns the turn when enabled, like the editor below.
            boolean hasMemoryTool = capabilities.stream()
                    .anyMatch(c -> c.capability() == TurNativeCapability.ANTHROPIC_MEMORY);
            if (hasMemoryTool) {
                // T166 / §X.9.d — point the memory tool at a per-user store when the
                // agent's memoryScope is USER (else the live conversation scope).
                String memoryScopeId = memoryScopeResolver.resolveScopeId(agent, conversationId);
                log.info("[Native] agent '{}' instance '{}' handled by Anthropic memory loop "
                        + "(scope '{}', persona '{}')", agent.getTitle(), instance.getId(),
                        agent.getMemoryScope(), activePersona == null ? "none" : activePersona.getName());
                return Optional.of(anthropicMemoryService.chat(anthropicClient.get(), instance,
                        agent.getId(), memoryScopeId, turn.history(), turn.systemPrompt(), capabilities,
                        ctxOptions));
            }

            // T142 / §X.4.d — text_editor / bash are client-executed multi-turn
            // tools (the editor loop drives them against TurAgentWorkspace + the
            // bash seam), so they own the turn when enabled.
            boolean hasEditorTool = capabilities.stream().anyMatch(c ->
                    c.capability() == TurNativeCapability.ANTHROPIC_TEXT_EDITOR
                            || c.capability() == TurNativeCapability.ANTHROPIC_BASH);
            if (hasEditorTool) {
                log.info("[Native] agent '{}' instance '{}' handled by Anthropic editor loop "
                        + "(persona '{}')", agent.getTitle(), instance.getId(),
                        activePersona == null ? "none" : activePersona.getName());
                return Optional.of(anthropicEditorService.chat(anthropicClient.get(), instance,
                        agent.getId(), conversationId, turn.history(), turn.systemPrompt(), capabilities));
            }

            // T152 / §X.7.a — when the agent enabled the citations request option,
            // retrieve the top-K knowledge-base passages for the question and pass
            // them so the Messages service attaches them as document blocks and
            // streams per-sentence citations back. Empty (RAG off / no hits) =
            // an uncited turn, identical to the pre-T152 path.
            // T152 — text passages for citations. T176 / §X.12.b — when native PDF
            // grounding is on, retrieve the raw hits and let the grounding service
            // swap PDF-backed passages for native base64 PDF document blocks (the
            // bridge caches the vendor file_id along the way).
            List<TurCitationDocument> citationDocuments;
            if (nativePdfEnabled) {
                List<org.springframework.ai.document.Document> rawDocs =
                        ragSearchToolService.retrieveRawForCitations(lastUserMessage, CITATION_TOP_K);
                citationDocuments = nativeDocumentGroundingService
                        .buildCitationDocuments(rawDocs, instance);
            } else if (citationsEnabled) {
                citationDocuments =
                        ragSearchToolService.retrievePassagesForCitations(lastUserMessage, CITATION_TOP_K);
            } else {
                citationDocuments = List.of();
            }

            // The remaining Anthropic server-side tools (web_search, web_fetch,
            // code_execution) execute inside Anthropic's infrastructure and return
            // the answer in a single turn — no per-tool branching.
            log.info("[Native] agent '{}' instance '{}' handled by Anthropic Messages path "
                    + "({} capability/ies, {} citation doc(s), persona '{}')",
                    agent.getTitle(), instance.getId(), capabilities.size(),
                    citationDocuments.size(),
                    activePersona == null ? "none" : activePersona.getName());
            Flux<ChatResponse> messages = anthropicMessagesService.chat(anthropicClient.get(), instance,
                    turn.history(), turn.systemPrompt(), capabilities, coexistingTools,
                    citationDocuments,
                    serviceTierResolver.resolveForChat(agent, instance).orElse(null), ctxOptions,
                    calibration);
            // T155 / §X.7.d — when citations are on and drift detection is
            // enabled, persist the per-sentence citations as they stream so the
            // daily scan can later re-resolve them. No-op pass-through otherwise.
            if (citationsEnabled) {
                messages = citationDriftService.tapForPersistence(messages, agent.getId(),
                        conversationId, lastUserMessage, CITATION_TOP_K);
            }
            return Optional.of(messages);
        }

        // T136 / §X.3.d — computer_use is not a one-shot built-in: it needs the
        // multi-turn screenshot/action loop. When it is enabled, the whole turn
        // is driven by the dedicated computer-use service (the other native
        // tools can't share its computer-use-preview model / request shape, so
        // they're ignored for this turn — logged below).
        Optional<EnabledCapability> computerUse = capabilities.stream()
                .filter(c -> c.capability() == TurNativeCapability.OPENAI_COMPUTER_USE)
                .findFirst();
        if (computerUse.isPresent()) {
            if (capabilities.size() > 1) {
                log.info("[Native] agent '{}' instance '{}' has computer_use enabled alongside {} "
                        + "other native capability/ies — driving the computer-use loop only this turn",
                        agent.getTitle(), instance.getId(), capabilities.size() - 1);
            }
            log.info("[Native] agent '{}' instance '{}' handled by OpenAI computer-use loop (persona '{}')",
                    agent.getTitle(), instance.getId(),
                    activePersona == null ? "none" : activePersona.getName());
            return Optional.of(openAiComputerUseService.chat(openAiClient.get(), instance, turn.history(),
                    turn.systemPrompt(), computerUse.get()));
        }

        // F.9 / §X.10.a — when the agent opted into stored completions, persist
        // this Responses turn on OpenAI's side tagged with structured metadata
        // (agentId/conversationId) so the T168 Evals / T169 distillation export
        // can slice it later. Disabled → byte-for-byte unchanged request.
        TurStoredCompletionsService.StoredCompletionsDirective storedCompletions =
                storedCompletionsService.resolveForChat(agent, conversationId);
        // T178 / §X.13.a + T179 / §X.13.b — carry the reasoning shape (when the
        // agent opted in on a reasoning model): the summary mode surfaces the
        // "Why this answer" SSE event, and the effort budget is resolved for the
        // LIVE stage — the agent's explicit reasoning-effort SELECT, or the
        // live-stage default (low) when only the summary was enabled. NONE on a
        // chat model / no opt-in → unchanged request.
        String liveEffort = reasoningOptedIn
                ? reasoningEffortResolver.resolve(
                        TurReasoningEffortResolver.Stage.LIVE, reasoningEffortMode)
                : null;
        TurOpenAiResponsesService.ReasoningOptions reasoningOptions = reasoningOptedIn
                ? new TurOpenAiResponsesService.ReasoningOptions(reasoningSummaryMode, liveEffort)
                : TurOpenAiResponsesService.ReasoningOptions.NONE;
        // T181 / §X.14.a — resolve the per-user safety_identifier on THIS (request)
        // thread; the Responses call runs on a reactive worker with no bound
        // security context. Blank for anonymous turns / when the feature is off.
        String safetyIdentifier = safetyIdentifierService.currentSafetyIdentifier().orElse(null);
        log.info("[Native] agent '{}' instance '{}' handled by OpenAI Responses path "
                + "({} capability/ies, persona '{}', storedCompletions={}, reasoningSummary={}, "
                + "reasoningEffort={}, safetyIdentifier={})",
                agent.getTitle(), instance.getId(), capabilities.size(),
                activePersona == null ? "none" : activePersona.getName(), storedCompletions.store(),
                reasoningOptions.hasSummary() ? reasoningSummaryMode : "off",
                reasoningOptions.hasEffort() ? liveEffort : "off",
                safetyIdentifier != null ? "on" : "off");
        return Optional.of(openAiResponsesService.chat(openAiClient.get(), instance, turn.history(),
                turn.systemPrompt(), capabilities, coexistingTools,
                serviceTierResolver.resolveForChat(agent, instance).orElse(null),
                agent.getId(), conversationId, storedCompletions, reasoningOptions, safetyIdentifier,
                calibration));
    }

    /**
     * T433 / §X.18.b — the agent's Turing/MCP/custom tools that coexist with the
     * selected provider-native picks this turn. Empty for the legacy path
     * ({@code selection == null}) and for owns-turn picks (computer_use), so the
     * native services run their one-shot server-tool turn unchanged. Otherwise
     * resolves the agent's full tool set (same collaborator the Spring AI path
     * uses, BM25 pre-filter included) and drops every callback whose abstract
     * {@code function} is claimed by a selected provider-native capability.
     */
    private ToolCallback[] resolveCoexistingTools(NativeSelection selection, TurAIAgent agent,
            TurPersona activePersona, String lastUserMessage, Set<String> federatedMcpServerIds) {
        if (selection == null || selection.hasOwnsTurn()) {
            return new ToolCallback[0];
        }
        // T145 / §X.5.b — exclude the MCP servers federated to the vendor this
        // turn from the Turing MCP client path so they aren't double-wired.
        ToolCallback[] agentTools = toolResolver.resolve(
                agent, activePersona, lastUserMessage, null, null, federatedMcpServerIds);
        return capabilityResolver.coexistingTools(agentTools, selection.claimedFunctions());
    }

    /**
     * The native Responses API takes a {@code instructions} string + a flat
     * input-item list, not Spring AI {@code Message} objects. Split the
     * assembler's output back into those two shapes: the leading
     * {@link MessageType#SYSTEM} message becomes the instructions, and every
     * remaining message maps to a {@link ChatMessageItem} (user / assistant
     * role). Media blocks attached to a user message are dropped — the native
     * path only handles plain turns (no attachments), so there are none.
     */
    private static AssembledTurn splitAssembled(List<Message> messages) {
        String systemPrompt = "";
        List<ChatMessageItem> history = new ArrayList<>(messages.size());
        for (Message message : messages) {
            String text = message.getText() == null ? "" : message.getText();
            MessageType type = message.getMessageType();
            switch (type) {
                // Keep the first (and only) system message as the instructions.
                case SYSTEM -> systemPrompt = text;
                case ASSISTANT -> history.add(new ChatMessageItem("assistant", text));
                default -> history.add(new ChatMessageItem("user", text));
            }
        }
        return new AssembledTurn(systemPrompt, history);
    }

    private record AssembledTurn(String systemPrompt, List<ChatMessageItem> history) {
    }

    private static String lastMessageOfRole(List<ChatMessageItem> history, String role) {
        if (history == null) {
            return "";
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessageItem item = history.get(i);
            if (item != null && role.equals(item.role())) {
                return item.content() == null ? "" : item.content();
            }
        }
        return "";
    }

    /**
     * T152 / §X.7.a — read the {@code citations} boolean from the agent's
     * {@code requestOptionsJson} (e.g. {@code {"citations":"true"}}). Values are
     * stored as strings by the registry-driven Request Options UI, so both
     * {@code "true"} and a JSON boolean {@code true} are accepted. {@code null}
     * options or any other value → disabled.
     */
    private boolean isCitationsEnabled(TurAIAgent agent) {
        return isRequestOptionTrue(agent, REQUEST_OPTION_CITATIONS);
    }

    /**
     * T176 / §X.12.b — read the {@code native-pdf} boolean from the agent's
     * {@code requestOptionsJson}. Opt-in; {@code null}/any non-true value →
     * disabled (unchanged text grounding).
     */
    private boolean isNativePdfEnabled(TurAIAgent agent) {
        return isRequestOptionTrue(agent, REQUEST_OPTION_NATIVE_PDF);
    }

    /** True when {@code requestOptionsJson[key]} stores the string/boolean {@code true}. */
    private boolean isRequestOptionTrue(TurAIAgent agent, String key) {
        String json = agent.getRequestOptionsJson();
        if (!StringUtils.hasText(json)) {
            return false;
        }
        Object value = optionsParser.parse(json).get(key);
        return value != null && "true".equalsIgnoreCase(value.toString().trim());
    }

    /**
     * T178/T179 — the raw string value of a request option (e.g. the
     * {@code reasoning-summary} mode {@code "auto"}/{@code "concise"}/
     * {@code "detailed"}), or {@code null} when unset/blank. Unlike
     * {@link #isRequestOptionTrue} these knobs are SELECT values, not booleans.
     */
    private String requestOptionValue(TurAIAgent agent, String key) {
        String json = agent.getRequestOptionsJson();
        if (!StringUtils.hasText(json)) {
            return null;
        }
        Object value = optionsParser.parse(json).get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String resolvePluginType(TurLLMInstance instance) {
        try {
            return providerFactory.getProvider(instance).getPluginType().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            log.debug("[Native] could not resolve provider for instance '{}': {}",
                    instance == null ? null : instance.getId(), e.getMessage());
            return null;
        }
    }
}
