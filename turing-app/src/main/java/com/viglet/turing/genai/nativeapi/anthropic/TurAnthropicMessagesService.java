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
package com.viglet.turing.genai.nativeapi.anthropic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.CodeExecutionTool20250522;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUnion;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.WebFetchTool20250910;
import com.anthropic.models.messages.WebSearchTool20250305;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.citation.TurChatCitation;
import com.viglet.turing.genai.citation.TurCitationDocument;
import com.viglet.turing.genai.persona.TurPersonaModelCalibration.Params;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.TurNativeFunctionToolSupport;
import com.viglet.turing.genai.nativeapi.TurNativeFunctionToolSupport.ToolOutcome;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * F.3 / §X.4 — runs a chat turn through the Anthropic <b>Messages API</b>
 * ({@code /v1/messages}) with server-side built-in tools, the Anthropic analog
 * of {@link com.viglet.turing.genai.nativeapi.openai.TurOpenAiResponsesService}.
 *
 * <p>Each enabled {@link TurNativeCapability} keyed to the {@code anthropic}
 * vendor maps to one server-side tool that executes inside Anthropic's
 * infrastructure — Turing writes <em>zero</em> Java tool callbacks for any of
 * them, and the answer comes back in a single turn (CALL semantics):
 * <ul>
 *   <li>{@code ANTHROPIC_WEB_SEARCH} → {@code web_search_20250305} (T139)</li>
 *   <li>{@code ANTHROPIC_WEB_FETCH}  → {@code web_fetch_20250910} (T140, beta)</li>
 *   <li>{@code ANTHROPIC_CODE_EXECUTION} → {@code code_execution_20250522} (T141, beta)</li>
 *   <li>{@code ANTHROPIC_MCP} → the {@code mcp_servers} request parameter
 *       (T144 / §X.5.a, beta {@code mcp-client-2025-11-20}) — Claude calls any
 *       remote MCP server with no client-side tool code, the Anthropic analog of
 *       the OpenAI remote {@code mcp} tool. See {@link #buildMcpServers}.</li>
 * </ul>
 *
 * <p>Tools that ship as Anthropic beta features (web_fetch, code_execution, the
 * MCP connector) also require their {@code anthropic-beta} header;
 * {@link #buildTools} and {@link #buildMcpServers} collect the required beta
 * tokens into the supplied set so {@link #buildParams} can attach the header.
 * web_search is GA and needs no beta token.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurAnthropicMessagesService {

    private static final String DEFAULT_MODEL = "claude-sonnet-4-20250514";
    private static final long DEFAULT_MAX_TOKENS = 4096L;

    /**
     * T433 — hard ceiling on tool-execution rounds for a single turn, mirroring
     * {@link com.viglet.turing.genai.TurToolExecutionLoop#MAX_TOOL_ITERATIONS}.
     */
    static final int MAX_TOOL_ITERATIONS = 10;

    /** {@code anthropic-beta} token gating the web_fetch server tool (T140). */
    private static final String BETA_WEB_FETCH = "web-fetch-2025-09-10";
    /** {@code anthropic-beta} token gating the code_execution server tool (T141). */
    private static final String BETA_CODE_EXECUTION = "code-execution-2025-05-22";
    /** {@code anthropic-beta} token gating the MCP Connector (T144 / §X.5.a). */
    private static final String BETA_MCP_CLIENT = "mcp-client-2025-11-20";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurProviderOptionsParser optionsParser;
    private final TurNativeFunctionToolSupport toolSupport;
    private final TurAnthropicCitationSupport citationSupport;
    private final TurAnthropicContextManagement contextManagement;
    private final com.viglet.turing.genai.nativeapi.cache.TurContextCacheProviderFactory contextCacheFactory;

    public TurAnthropicMessagesService(TurProviderOptionsParser optionsParser,
            TurNativeFunctionToolSupport toolSupport,
            TurAnthropicCitationSupport citationSupport,
            TurAnthropicContextManagement contextManagement,
            com.viglet.turing.genai.nativeapi.cache.TurContextCacheProviderFactory contextCacheFactory) {
        this.optionsParser = optionsParser;
        this.toolSupport = toolSupport;
        this.citationSupport = citationSupport;
        this.contextManagement = contextManagement;
        this.contextCacheFactory = contextCacheFactory;
    }

    /**
     * Execute the turn and emit the assistant text as a single SSE token event
     * (CALL semantics — the tool turn must complete before the answer is known).
     *
     * <p>T433 / §X.18.b — {@code coexistingTools} are the agent's Turing/MCP/
     * custom tools whose abstract {@code function} isn't claimed by a selected
     * provider-native capability. When present they are advertised as Anthropic
     * custom tools alongside the server-side built-ins, and the turn runs a
     * client-side tool-execution loop: Claude emits {@code tool_use} blocks,
     * Turing executes the matching {@link ToolCallback} and feeds back
     * {@code tool_result} blocks, until a final text answer is produced
     * (Anthropic is stateless, so the full conversation is resent each step).
     * When empty (the legacy native path) the loop runs once — identical to the
     * original one-shot server-tool behaviour.
     */
    public Flux<ChatResponse> chat(AnthropicClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities, ToolCallback[] coexistingTools) {
        return chat(client, instance, history, systemPrompt, capabilities, coexistingTools,
                List.of(), null);
    }

    /** F.7 / §X.8.e — citations + explicit {@link com.viglet.turing.genai.servicetier.TurServiceTier}. */
    public Flux<ChatResponse> chat(AnthropicClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities, ToolCallback[] coexistingTools,
            List<TurCitationDocument> citationDocuments) {
        return chat(client, instance, history, systemPrompt, capabilities, coexistingTools,
                citationDocuments, null);
    }

    /**
     * T152 / §X.7.a — citations-aware overload. When {@code citationDocuments} is
     * non-empty the retrieved RAG passages are attached to the question as
     * Anthropic {@code document} content blocks ({@code citations: enabled}), and
     * the per-sentence citations Claude returns are emitted as a second SSE event
     * ({@code type = "citations"}) after the answer token. An empty list is the
     * unchanged pre-T152 path — no document blocks, no citations event.
     */
    public Flux<ChatResponse> chat(AnthropicClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities, ToolCallback[] coexistingTools,
            List<TurCitationDocument> citationDocuments,
            com.viglet.turing.genai.servicetier.TurServiceTier serviceTier) {
        return chat(client, instance, history, systemPrompt, capabilities, coexistingTools,
                citationDocuments, serviceTier, TurAnthropicContextManagement.Options.NONE);
    }

    /**
     * T164/T165 / §X.9 — context-management-aware overload. When the agent enabled
     * the {@code context-editing} or {@code compaction} Request Option, the turn
     * carries the matching {@code context_management.edits} + beta header so Claude
     * prunes/summarizes the conversation server-side. {@link TurAnthropicContextManagement.Options#NONE}
     * is the unchanged path (no body property, no beta).
     */
    public Flux<ChatResponse> chat(AnthropicClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities, ToolCallback[] coexistingTools,
            List<TurCitationDocument> citationDocuments,
            com.viglet.turing.genai.servicetier.TurServiceTier serviceTier,
            TurAnthropicContextManagement.Options contextOptions) {
        return chat(client, instance, history, systemPrompt, capabilities, coexistingTools,
                citationDocuments, serviceTier, contextOptions, Params.NONE);
    }

    /**
     * T606 — persona-aware overload. When the active persona opted into
     * style→model calibration, {@code calibration} carries the per-turn
     * temperature/maxTokens overrides ({@link Params#NONE} = unchanged path).
     */
    public Flux<ChatResponse> chat(AnthropicClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities, ToolCallback[] coexistingTools,
            List<TurCitationDocument> citationDocuments,
            com.viglet.turing.genai.servicetier.TurServiceTier serviceTier,
            TurAnthropicContextManagement.Options contextOptions, Params calibration) {
        TurAnthropicContextManagement.Options options =
                contextOptions == null ? TurAnthropicContextManagement.Options.NONE : contextOptions;
        return Mono.fromCallable(() -> runLoop(client, instance, history, systemPrompt,
                        capabilities, coexistingTools, citationDocuments, serviceTier, options,
                        calibration))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(TurAnthropicMessagesService::emitTurn)
                .doOnError(err -> log.error("[Native][Anthropic-Messages] instance '{}' error: {}",
                        instance.getId(), err.getMessage(), err));
    }

    /** Legacy/test overload — no coexisting client tools (one-shot server-tool turn). */
    public Flux<ChatResponse> chat(AnthropicClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities) {
        return chat(client, instance, history, systemPrompt, capabilities, new ToolCallback[0]);
    }

    /**
     * The result of a native Anthropic turn: the assistant text and, when the
     * {@code citations} request option is on, the per-sentence citations decoded
     * from the response (empty otherwise).
     */
    record TurnResult(String text, List<TurChatCitation> citations, String reasoningSummary) {
        TurnResult(String text, List<TurChatCitation> citations) {
            this(text, citations, "");
        }
    }

    /**
     * Fan a {@link TurnResult} into the SSE token event, the optional T504
     * {@code reasoning} event (the thought summary → T178 "Why this answer" panel),
     * and the optional citations event.
     */
    private static Flux<ChatResponse> emitTurn(TurnResult result) {
        Flux<ChatResponse> events = Flux.just(new ChatResponse("assistant",
                StringUtils.hasText(result.text()) ? result.text() : ""));
        if (StringUtils.hasText(result.reasoningSummary())) {
            events = events.concatWith(Flux.just(
                    new ChatResponse("assistant", result.reasoningSummary(), "reasoning")));
        }
        if (result.citations() != null && !result.citations().isEmpty()) {
            try {
                String citationsJson = OBJECT_MAPPER.writeValueAsString(result.citations());
                events = events.concatWith(Flux.just(
                        new ChatResponse("assistant", citationsJson, "citations")));
            } catch (JacksonException e) {
                log.warn("[Native][Anthropic-Messages] could not serialize citations: {}", e.getMessage());
            }
        }
        return events;
    }

    TurnResult runLoop(AnthropicClient client, TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, List<EnabledCapability> capabilities, ToolCallback[] coexistingTools,
            List<TurCitationDocument> citationDocuments,
            com.viglet.turing.genai.servicetier.TurServiceTier serviceTier,
            TurAnthropicContextManagement.Options contextOptions, Params calibration) {
        long t0 = System.currentTimeMillis();
        Set<String> betas = new LinkedHashSet<>();
        List<ToolUnion> serverTools = buildTools(capabilities, betas);
        List<ToolUnion> functionTools = buildFunctionTools(coexistingTools);
        List<Object> mcpServers = buildMcpServers(capabilities, betas);
        Map<String, ToolCallback> toolIndex = toolSupport.indexByName(coexistingTools);
        List<ToolUnion> tools = new ArrayList<>(serverTools);
        tools.addAll(functionTools);

        // T502 — one ephemeral cache_control breakpoint (when opted in) stamps the
        // last retrieved search_result block here and the system prompt below.
        CacheControlEphemeral cacheControl = resolveCacheControl(instance).orElse(null);
        List<MessageParam> conversation = seedConversation(history, citationDocuments, cacheControl);
        StringBuilder text = new StringBuilder();
        List<TurChatCitation> citations = new ArrayList<>();
        String reasoningSummary = "";
        int toolCalls = 0;

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            MessageCreateParams params = buildParamsFromConversation(
                    instance, conversation, systemPrompt, tools, betas, mcpServers, serviceTier,
                    contextOptions, calibration);
            Message response = client.messages().create(params);
            // T154 / §X.7.c — append text and decode citations in one pass so each
            // citation carries its answer-span offsets (for inline underlining).
            citationSupport.appendTextAndCitations(response, text, citations, citationDocuments);
            // T504 / §X.13 — keep the latest non-blank thinking summary (the final
            // answer round's reasoning is what the "Why this answer" panel shows).
            String roundThinking = extractThinking(response);
            if (StringUtils.hasText(roundThinking)) {
                reasoningSummary = roundThinking;
            }

            List<ToolUseBlock> calls = clientToolUseBlocks(response, toolIndex);
            if (calls.isEmpty()) {
                log.info("[Native][Anthropic-Messages] instance '{}' model '{}' server-tools {} "
                                + "function-tools {} -> {} chars, {} tool call(s), {} citation(s) in {} ms",
                        instance.getId(), resolveModel(instance),
                        capabilities.stream().map(c -> c.capability().getKey()).toList(),
                        functionTools.size(), text.length(), toolCalls, citations.size(),
                        System.currentTimeMillis() - t0);
                return new TurnResult(text.toString(), citations, reasoningSummary);
            }

            conversation.add(response.toParam());
            List<ContentBlockParam> results = new ArrayList<>(calls.size());
            for (ToolUseBlock call : calls) {
                ToolOutcome outcome = toolSupport.execute(toolIndex, call.name(), argumentsJson(call));
                results.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(call.id())
                        .content(StringUtils.hasText(outcome.output()) ? outcome.output() : "(no output)")
                        .isError(outcome.error())
                        .build()));
                toolCalls++;
            }
            conversation.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(results)
                    .build());
        }

        log.warn("[Native][Anthropic-Messages] instance '{}' hit the {}-round tool-execution cap",
                instance.getId(), MAX_TOOL_ITERATIONS);
        return new TurnResult(text.toString(), citations, reasoningSummary);
    }

    MessageCreateParams buildParams(TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, List<EnabledCapability> capabilities) {
        return buildParams(instance, history, systemPrompt, capabilities, Params.NONE);
    }

    /** T606 — persona-calibration-aware builder overload (test/builder entry). */
    MessageCreateParams buildParams(TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, List<EnabledCapability> capabilities, Params calibration) {
        Set<String> betas = new LinkedHashSet<>();
        List<ToolUnion> tools = buildTools(capabilities, betas);
        List<Object> mcpServers = buildMcpServers(capabilities, betas);
        return buildParamsFromConversation(instance, seedConversation(history), systemPrompt, tools,
                betas, mcpServers, null, TurAnthropicContextManagement.Options.NONE, calibration);
    }

    private MessageCreateParams buildParamsFromConversation(TurLLMInstance instance,
            List<MessageParam> conversation, String systemPrompt, List<ToolUnion> tools,
            Set<String> betas, List<Object> mcpServers,
            com.viglet.turing.genai.servicetier.TurServiceTier serviceTier,
            TurAnthropicContextManagement.Options contextOptions, Params calibration) {
        // T606 — persona style→model calibration takes precedence over the instance
        // maxTokens/temperature when opted in (null fields = no override).
        long maxTokens = calibration.maxTokens() != null
                ? calibration.maxTokens()
                : resolveMaxTokens(instance);
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(resolveModel(instance))
                .maxTokens(maxTokens)
                .messages(conversation);

        if (StringUtils.hasText(systemPrompt)) {
            // T502 / §X.20 — when context caching is opted in (the T499 seam),
            // carry the system prompt as a single text block with an ephemeral
            // cache_control breakpoint so Anthropic serves the repeated prefix at a
            // discount. Default (no ref) → the plain string, byte-for-byte unchanged.
            Optional<CacheControlEphemeral> cacheControl = resolveCacheControl(instance);
            if (cacheControl.isPresent()) {
                builder.systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(systemPrompt)
                        .cacheControl(cacheControl.get())
                        .build()));
            } else {
                builder.system(systemPrompt);
            }
        }
        // T504 / §X.13 — extended thinking: opt-in budget_tokens enables a
        // reasoning pass whose thought summary feeds the same T178 panel. When on,
        // Anthropic requires temperature = 1, so the per-instance temperature is
        // NOT applied (it would 400). Default (no budget) → unchanged request.
        ThinkingOptions thinking = resolveThinking(instance);
        if (thinking.enabled()) {
            builder.thinking(com.anthropic.models.messages.ThinkingConfigEnabled.builder()
                    .budgetTokens(thinking.budgetTokens())
                    .build());
        } else {
            // T606 — persona calibration overrides the instance temperature when
            // opted in; still skipped under thinking (Anthropic forces temp=1).
            Double temperature = calibration.temperature() != null
                    ? calibration.temperature()
                    : instance.getTemperature();
            if (temperature != null) {
                applyTemperature(builder, temperature);
            }
        }
        if (serviceTier != null) {
            builder.serviceTier(com.anthropic.models.messages.MessageCreateParams.ServiceTier
                    .of(serviceTier.anthropicValue()));
        }
        for (ToolUnion tool : tools) {
            builder.addTool(tool);
        }
        // T144 / §X.5.a — the MCP Connector is the top-level mcp_servers request
        // parameter, not an entry in tools. The non-beta Messages builder has no
        // typed setter for it, so inject the raw JSON array; the mcp-client beta
        // token is added by buildMcpServers when a server is produced.
        if (mcpServers != null && !mcpServers.isEmpty()) {
            builder.putAdditionalBodyProperty("mcp_servers", JsonValue.from(mcpServers));
        }
        // T164/T165 / §X.9 — opt-in context editing / compaction. The standard
        // Messages builder has no typed context_management setter, so inject the
        // raw body property + the context-management beta token (added to the same
        // anthropic-beta header below). Options.NONE → nothing attached (unchanged).
        Set<String> allBetas = new LinkedHashSet<>(betas == null ? Set.of() : betas);
        contextManagement.rawConfig(contextOptions).ifPresent(config ->
                builder.putAdditionalBodyProperty("context_management", config));
        allBetas.addAll(contextManagement.rawBetaTokens(contextOptions));
        // T504 — interleaved thinking lets the model reason BETWEEN tool calls in
        // the F.3 tool loop (beta token); only meaningful with thinking enabled.
        if (thinking.enabled() && thinking.interleaved()) {
            allBetas.add(com.anthropic.models.beta.AnthropicBeta.INTERLEAVED_THINKING_2025_05_14
                    .asString());
        }
        if (!allBetas.isEmpty()) {
            builder.putAdditionalHeader("anthropic-beta", String.join(",", allBetas));
        }
        return builder.build();
    }

    /**
     * T502 / §X.20 — resolve the ephemeral {@code cache_control} breakpoint for an
     * instance through the T499 {@link com.viglet.turing.genai.nativeapi.cache.TurContextCacheProvider}
     * seam, or empty when context caching is not opted in. The Anthropic provider
     * returns a ref whose {@code handle} is the TTL token ({@code 5m} / {@code 1h});
     * any other vendor's ref (or none) leaves the request uncached here.
     */
    Optional<CacheControlEphemeral> resolveCacheControl(TurLLMInstance instance) {
        return contextCacheFactory.resolve(instance)
                .ensureCache(com.viglet.turing.genai.nativeapi.cache.TurContextCacheRequest
                        .ofSystemInstruction(instance, resolveModel(instance), "x", null))
                .filter(ref -> "anthropic".equals(ref.pluginType()))
                .map(ref -> CacheControlEphemeral.builder()
                        .ttl("1h".equals(ref.handle())
                                ? CacheControlEphemeral.Ttl.TTL_1H
                                : CacheControlEphemeral.Ttl.TTL_5M)
                        .build());
    }

    /**
     * T504 / §X.13 — resolved extended-thinking knobs for an instance:
     * {@code thinkingBudget} (budget_tokens; &gt; 0 enables the reasoning pass) and
     * {@code interleavedThinking} (think between tool calls, a beta). Absent /
     * non-positive budget → disabled (unchanged request).
     */
    record ThinkingOptions(boolean enabled, long budgetTokens, boolean interleaved) {
        static final ThinkingOptions DISABLED = new ThinkingOptions(false, 0, false);
    }

    ThinkingOptions resolveThinking(TurLLMInstance instance) {
        Map<String, Object> options = optionsParser.parse(instance.getProviderOptionsJson());
        Integer budget = optionsParser.intValue(options, "thinkingBudget");
        if (budget == null || budget <= 0) {
            return ThinkingOptions.DISABLED;
        }
        Object interleaved = options == null ? null : options.get("interleavedThinking");
        boolean inter = interleaved != null && "true".equalsIgnoreCase(interleaved.toString().trim());
        return new ThinkingOptions(true, budget, inter);
    }

    /** T504 — concatenate the response's thinking blocks (the thought summary), or "". */
    static String extractThinking(Message response) {
        if (response == null || response.content() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        response.content().forEach(block -> {
            if (block.isThinking()) {
                String thought = block.asThinking().thinking();
                if (StringUtils.hasText(thought)) {
                    if (sb.length() > 0) {
                        sb.append("\n\n");
                    }
                    sb.append(thought);
                }
            }
        });
        return sb.toString();
    }

    private List<MessageParam> seedConversation(List<ChatMessageItem> history) {
        return seedConversation(history, List.of(), null);
    }

    /**
     * Build the conversation from history. T152 / §X.7.a + T153 / §X.7.b — when
     * {@code citationDocuments} is non-empty, the <b>last user turn</b> (the
     * question) is rebuilt to carry the retrieved passages as leading
     * {@code search_result} content blocks (citations enabled) followed by the
     * question text, so Claude grounds and cites against them. Turing's retrieval
     * hits use the {@code search_result} block (the encyclopedia-quality citation
     * format, T153) rather than the older {@code document} block (T152). All other
     * turns are plain text, identical to the pre-T152 path.
     */
    private List<MessageParam> seedConversation(List<ChatMessageItem> history,
            List<TurCitationDocument> citationDocuments, CacheControlEphemeral cacheControl) {
        List<MessageParam> conversation = new ArrayList<>();
        if (history == null) {
            return conversation;
        }
        boolean withDocuments = citationDocuments != null && !citationDocuments.isEmpty();
        int lastUserIdx = withDocuments ? lastUserIndex(history) : -1;
        for (int i = 0; i < history.size(); i++) {
            ChatMessageItem message = history.get(i);
            if (message == null || !StringUtils.hasText(message.content())) {
                continue;
            }
            if (i == lastUserIdx) {
                // T176 / §X.12.b — when any passage carries a native PDF, send the
                // whole batch as {@code document} blocks (base64 PDF for the
                // PDF-backed ones, text for the rest) so block types stay uniform
                // and the citation indices line up; otherwise the T153
                // {@code search_result} blocks (the default citation path).
                boolean nativePdf = citationDocuments.stream()
                        .anyMatch(TurCitationDocument::hasNativePdf);
                // T502 — stamp the last search_result block with cache_control when
                // caching is opted in (the native-PDF document path is left uncached
                // for now — base64 PDFs aren't a stable cross-turn prefix).
                List<ContentBlockParam> blocks = new ArrayList<>(nativePdf
                        ? citationSupport.documentBlocks(citationDocuments)
                        : citationSupport.searchResultBlocks(citationDocuments, cacheControl));
                blocks.add(ContentBlockParam.ofText(
                        TextBlockParam.builder().text(message.content()).build()));
                conversation.add(MessageParam.builder()
                        .role(MessageParam.Role.USER)
                        .contentOfBlockParams(blocks)
                        .build());
                continue;
            }
            conversation.add(MessageParam.builder()
                    .role(isAssistant(message.role()) ? MessageParam.Role.ASSISTANT
                            : MessageParam.Role.USER)
                    .content(message.content())
                    .build());
        }
        return conversation;
    }

    /** Index of the last user-role message in the history, or -1 when none. */
    private static int lastUserIndex(List<ChatMessageItem> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessageItem message = history.get(i);
            if (message != null && StringUtils.hasText(message.content())
                    && !isAssistant(message.role())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * T433 — translate the agent's coexisting {@link ToolCallback}s into Anthropic
     * custom tools. The callback's JSON-Schema {@code properties} / {@code required}
     * map onto {@link Tool.InputSchema}; tools with no declared parameters still
     * advertise a valid empty-object schema.
     */
    List<ToolUnion> buildFunctionTools(ToolCallback[] callbacks) {
        List<ToolUnion> tools = new ArrayList<>();
        if (callbacks == null) {
            return tools;
        }
        for (ToolCallback callback : callbacks) {
            ToolDefinition definition = callback.getToolDefinition();
            tools.add(ToolUnion.ofTool(Tool.builder()
                    .name(definition.name())
                    .description(definition.description() == null ? "" : definition.description())
                    .inputSchema(toInputSchema(toolSupport.parseSchema(callback)))
                    .build()));
        }
        return tools;
    }

    private Tool.InputSchema toInputSchema(Map<String, Object> schema) {
        Tool.InputSchema.Builder builder = Tool.InputSchema.builder();
        Object properties = schema.get("properties");
        if (properties instanceof Map<?, ?> propertyMap) {
            Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
            for (Map.Entry<?, ?> entry : propertyMap.entrySet()) {
                props.putAdditionalProperty(String.valueOf(entry.getKey()),
                        JsonValue.from(entry.getValue()));
            }
            builder.properties(props.build());
        }
        Object required = schema.get("required");
        if (required instanceof List<?> requiredList) {
            builder.required(requiredList.stream().map(String::valueOf).toList());
        }
        return builder.build();
    }

    /** tool_use blocks the model wants the <em>client</em> to run (i.e. our custom tools). */
    private static List<ToolUseBlock> clientToolUseBlocks(Message response,
            Map<String, ToolCallback> toolIndex) {
        List<ToolUseBlock> calls = new ArrayList<>();
        for (ContentBlock block : response.content()) {
            if (block.isToolUse()) {
                ToolUseBlock call = block.asToolUse();
                if (toolIndex.containsKey(call.name())) {
                    calls.add(call);
                }
            }
        }
        return calls;
    }

    private String argumentsJson(ToolUseBlock call) {
        try {
            Object input = call._input().convert(Object.class);
            return input == null ? "{}" : toolSupport.toJson(input);
        } catch (RuntimeException e) {
            log.debug("[Native][Anthropic-Messages] could not serialize tool_use input: {}",
                    e.getMessage());
            return "{}";
        }
    }

    /** Convenience overload that discards the beta-token set (tests / web_search-only). */
    List<ToolUnion> buildTools(List<EnabledCapability> capabilities) {
        return buildTools(capabilities, new LinkedHashSet<>());
    }

    /**
     * Translate the enabled capability set into Anthropic server-side tools.
     * Capabilities keyed to another vendor (defensive — the caller already
     * filters by {@code anthropic}) are skipped. Beta-gated tools push their
     * {@code anthropic-beta} token into {@code betas}.
     */
    List<ToolUnion> buildTools(List<EnabledCapability> capabilities, Set<String> betas) {
        List<ToolUnion> tools = new ArrayList<>();
        if (capabilities == null) {
            return tools;
        }
        for (EnabledCapability enabled : capabilities) {
            switch (enabled.capability()) {
                case ANTHROPIC_WEB_SEARCH -> tools.add(ToolUnion.ofWebSearchTool20250305(
                        WebSearchTool20250305.builder().build()));
                case ANTHROPIC_WEB_FETCH -> {
                    tools.add(ToolUnion.ofWebFetchTool20250910(WebFetchTool20250910.builder().build()));
                    betas.add(BETA_WEB_FETCH);
                }
                case ANTHROPIC_CODE_EXECUTION -> {
                    tools.add(ToolUnion.ofCodeExecutionTool20250522(
                            CodeExecutionTool20250522.builder().build()));
                    betas.add(BETA_CODE_EXECUTION);
                }
                default -> log.debug("[Native][Anthropic-Messages] capability '{}' is not wired on "
                        + "the Anthropic path — skipping", enabled.capability().getKey());
            }
        }
        return tools;
    }

    /**
     * T144 / §X.5.a — translate every enabled {@code ANTHROPIC_MCP} capability into
     * a remote MCP server definition for the Messages API {@code mcp_servers}
     * parameter (beta {@code mcp-client-2025-11-20}). Each server is a raw JSON
     * object so the non-beta Messages builder can carry it via
     * {@code putAdditionalBodyProperty} — the typed {@code mcp_servers} setter
     * lives only on the beta builder, and switching the whole service to the beta
     * endpoint would needlessly re-touch the shipped server-tool path.
     *
     * <p>Per-instance {@code configJson} keys (mirroring the OpenAI remote MCP
     * tool, {@link com.viglet.turing.genai.nativeapi.TurNativeCapability#OPENAI_MCP}):
     * <ul>
     *   <li>{@code serverUrl} — Streamable-HTTP / SSE endpoint (required)</li>
     *   <li>{@code serverLabel} / {@code serverName} — unique server name (required)</li>
     *   <li>{@code authToken} — optional {@code authorization_token} (Bearer)</li>
     *   <li>{@code allowedTools} — optional comma-separated allow-list → {@code tool_configuration.allowed_tools}</li>
     * </ul>
     * A server missing {@code serverUrl} or its name is skipped with a warning
     * rather than producing an invalid request. The {@code mcp-client} beta token
     * is pushed into {@code betas} only when at least one server is produced.
     */
    List<Object> buildMcpServers(List<EnabledCapability> capabilities, Set<String> betas) {
        List<Object> servers = new ArrayList<>();
        if (capabilities == null) {
            return servers;
        }
        for (EnabledCapability enabled : capabilities) {
            if (enabled.capability() == TurNativeCapability.ANTHROPIC_MCP) {
                toMcpServer(enabled).ifPresent(servers::add);
            }
        }
        if (!servers.isEmpty()) {
            betas.add(BETA_MCP_CLIENT);
        }
        return servers;
    }

    /** Build one {@code mcp_servers} entry from a capability's config, empty when unconfigured. */
    private Optional<Map<String, Object>> toMcpServer(EnabledCapability enabled) {
        Map<String, Object> config = optionsParser.parse(enabled.configJson());
        String serverUrl = optionsParser.stringValue(config, "serverUrl");
        String serverName = optionsParser.stringValue(config, "serverName");
        if (!StringUtils.hasText(serverName)) {
            serverName = optionsParser.stringValue(config, "serverLabel");
        }
        if (!StringUtils.hasText(serverUrl) || !StringUtils.hasText(serverName)) {
            log.warn("[Native][Anthropic-Messages] mcp enabled but serverUrl/serverName missing "
                    + "— skipping server");
            return Optional.empty();
        }
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("type", "url");
        server.put("url", serverUrl);
        server.put("name", serverName);
        String authToken = optionsParser.stringValue(config, "authToken");
        if (StringUtils.hasText(authToken)) {
            server.put("authorization_token", authToken);
        }
        List<String> allowedTools = parseAllowedTools(config);
        if (!allowedTools.isEmpty()) {
            Map<String, Object> toolConfiguration = new LinkedHashMap<>();
            toolConfiguration.put("enabled", true);
            toolConfiguration.put("allowed_tools", allowedTools);
            server.put("tool_configuration", toolConfiguration);
        }
        return Optional.of(server);
    }

    /** Parse the optional {@code allowedTools} allow-list (list or comma-separated string). */
    private List<String> parseAllowedTools(Map<String, Object> config) {
        List<String> fromList = optionsParser.stringListValue(config, "allowedTools");
        if (!fromList.isEmpty()) {
            return fromList;
        }
        String csv = optionsParser.stringValue(config, "allowedTools");
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        List<String> tools = new ArrayList<>();
        for (String token : csv.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                tools.add(trimmed);
            }
        }
        return tools;
    }

    /** Concatenate the text of every text content block in the response. */
    String extractText(Message response) {
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : response.content()) {
            if (block.isText()) {
                text.append(block.asText().text());
            }
        }
        return text.toString();
    }

    /**
     * Apply the per-instance / persona temperature. The Anthropic SDK deprecated
     * {@code temperature(double)} because models after Claude Opus 4.6 reject any
     * non-1.0 temperature (HTTP 400). Turing still honours the configured
     * temperature deliberately — for the many older Claude models, and
     * OpenAI-compatible Anthropic proxies, that accept it — so the deprecation is
     * suppressed here, scoped to this one call to keep the signal for any other
     * deprecated API elsewhere.
     */
    @SuppressWarnings("deprecation")
    private static void applyTemperature(MessageCreateParams.Builder builder, double temperature) {
        builder.temperature(temperature);
    }

    private long resolveMaxTokens(TurLLMInstance instance) {
        Integer configured = optionsParser.intValue(
                optionsParser.parse(instance.getProviderOptionsJson()), "maxTokens");
        return configured != null && configured > 0 ? configured.longValue() : DEFAULT_MAX_TOKENS;
    }

    private String resolveModel(TurLLMInstance instance) {
        return StringUtils.hasText(instance.getModelName()) ? instance.getModelName() : DEFAULT_MODEL;
    }

    private static boolean isAssistant(String role) {
        return role != null && "assistant".equals(role.toLowerCase(Locale.ROOT));
    }
}
