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

    public TurAnthropicMessagesService(TurProviderOptionsParser optionsParser,
            TurNativeFunctionToolSupport toolSupport,
            TurAnthropicCitationSupport citationSupport) {
        this.optionsParser = optionsParser;
        this.toolSupport = toolSupport;
        this.citationSupport = citationSupport;
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
        return chat(client, instance, history, systemPrompt, capabilities, coexistingTools, List.of());
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
            List<TurCitationDocument> citationDocuments) {
        return Mono.fromCallable(() -> runLoop(client, instance, history, systemPrompt,
                        capabilities, coexistingTools, citationDocuments))
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
    record TurnResult(String text, List<TurChatCitation> citations) {
    }

    /** Fan a {@link TurnResult} into the SSE token event + an optional citations event. */
    private static Flux<ChatResponse> emitTurn(TurnResult result) {
        Flux<ChatResponse> events = Flux.just(new ChatResponse("assistant",
                StringUtils.hasText(result.text()) ? result.text() : ""));
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
            List<TurCitationDocument> citationDocuments) {
        long t0 = System.currentTimeMillis();
        Set<String> betas = new LinkedHashSet<>();
        List<ToolUnion> serverTools = buildTools(capabilities, betas);
        List<ToolUnion> functionTools = buildFunctionTools(coexistingTools);
        List<Object> mcpServers = buildMcpServers(capabilities, betas);
        Map<String, ToolCallback> toolIndex = toolSupport.indexByName(coexistingTools);
        List<ToolUnion> tools = new ArrayList<>(serverTools);
        tools.addAll(functionTools);

        List<MessageParam> conversation = seedConversation(history, citationDocuments);
        StringBuilder text = new StringBuilder();
        List<TurChatCitation> citations = new ArrayList<>();
        int toolCalls = 0;

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            MessageCreateParams params = buildParamsFromConversation(
                    instance, conversation, systemPrompt, tools, betas, mcpServers);
            Message response = client.messages().create(params);
            appendText(response, text);
            citations.addAll(citationSupport.extractCitations(response, citationDocuments));

            List<ToolUseBlock> calls = clientToolUseBlocks(response, toolIndex);
            if (calls.isEmpty()) {
                log.info("[Native][Anthropic-Messages] instance '{}' model '{}' server-tools {} "
                                + "function-tools {} -> {} chars, {} tool call(s), {} citation(s) in {} ms",
                        instance.getId(), resolveModel(instance),
                        capabilities.stream().map(c -> c.capability().getKey()).toList(),
                        functionTools.size(), text.length(), toolCalls, citations.size(),
                        System.currentTimeMillis() - t0);
                return new TurnResult(text.toString(), citations);
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
        return new TurnResult(text.toString(), citations);
    }

    MessageCreateParams buildParams(TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, List<EnabledCapability> capabilities) {
        Set<String> betas = new LinkedHashSet<>();
        List<ToolUnion> tools = buildTools(capabilities, betas);
        List<Object> mcpServers = buildMcpServers(capabilities, betas);
        return buildParamsFromConversation(instance, seedConversation(history), systemPrompt, tools,
                betas, mcpServers);
    }

    private MessageCreateParams buildParamsFromConversation(TurLLMInstance instance,
            List<MessageParam> conversation, String systemPrompt, List<ToolUnion> tools,
            Set<String> betas, List<Object> mcpServers) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(resolveModel(instance))
                .maxTokens(resolveMaxTokens(instance))
                .messages(conversation);

        if (StringUtils.hasText(systemPrompt)) {
            builder.system(systemPrompt);
        }
        if (instance.getTemperature() != null) {
            builder.temperature(instance.getTemperature());
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
        if (betas != null && !betas.isEmpty()) {
            builder.putAdditionalHeader("anthropic-beta", String.join(",", betas));
        }
        return builder.build();
    }

    private List<MessageParam> seedConversation(List<ChatMessageItem> history) {
        return seedConversation(history, List.of());
    }

    /**
     * Build the conversation from history. T152 / §X.7.a — when
     * {@code citationDocuments} is non-empty, the <b>last user turn</b> (the
     * question) is rebuilt to carry the retrieved passages as leading
     * {@code document} content blocks (citations enabled) followed by the
     * question text, so Claude grounds and cites against them. All other turns
     * are plain text, identical to the pre-T152 path.
     */
    private List<MessageParam> seedConversation(List<ChatMessageItem> history,
            List<TurCitationDocument> citationDocuments) {
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
                List<ContentBlockParam> blocks = new ArrayList<>(
                        citationSupport.documentBlocks(citationDocuments));
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

    private void appendText(Message response, StringBuilder sink) {
        for (ContentBlock block : response.content()) {
            if (block.isText()) {
                sink.append(block.asText().text());
            }
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
