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
package com.viglet.turing.genai.nativeapi.openai;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FileSearchTool;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseIncludable;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseReasoningItem;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.Tool;
import com.openai.models.responses.WebSearchTool;
import com.viglet.turing.genai.rag.rerank.TurLogprobConfidence;
import com.viglet.turing.genai.rag.rerank.TurLogprobConfidence.TokenLogprob;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService.EnabledCapability;
import com.viglet.turing.genai.nativeapi.TurNativeFunctionToolSupport;
import com.viglet.turing.genai.persona.TurPersonaModelCalibration.Params;
import com.viglet.turing.genai.nativeapi.TurNativeFunctionToolSupport.ToolOutcome;
import com.viglet.turing.genai.nativeapi.openai.TurStoredCompletionsService.StoredCompletionsDirective;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.genai.workspace.TurAgentWorkspace;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * F.2 / §X.3 — runs a chat turn through the OpenAI <b>Responses API</b>
 * ({@code /v1/responses}) with server-side built-in tools.
 *
 * <p>Each enabled {@link TurNativeCapability} maps to one Responses built-in
 * tool that executes inside OpenAI's infrastructure — Turing writes <em>zero</em>
 * Java tool callbacks for any of them:
 * <ul>
 *   <li>{@code OPENAI_WEB_SEARCH}        → {@code web_search}</li>
 *   <li>{@code OPENAI_FILE_SEARCH}       → {@code file_search} (hosted vector stores)</li>
 *   <li>{@code OPENAI_CODE_INTERPRETER}  → {@code code_interpreter} (auto container)</li>
 *   <li>{@code OPENAI_IMAGE_GENERATION}  → {@code image_generation}</li>
 *   <li>{@code OPENAI_MCP}               → remote {@code mcp} connector</li>
 * </ul>
 *
 * <p>{@code OPENAI_COMPUTER_USE} is intentionally <em>not</em> wired here: the
 * computer-use tool needs a multi-turn screenshot/action loop, not a one-shot
 * server-side call, so it is handled by
 * {@link com.viglet.turing.genai.nativeapi.openai.computeruse.TurOpenAiComputerUseService}
 * (T136). This service skips it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurOpenAiResponsesService {

    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    /**
     * T433 — hard ceiling on tool-execution rounds for a single turn, mirroring
     * {@link com.viglet.turing.genai.TurToolExecutionLoop#MAX_TOOL_ITERATIONS}.
     * A misbehaving model must not spin forever holding the request thread.
     */
    static final int MAX_TOOL_ITERATIONS = 10;

    private final TurProviderOptionsParser optionsParser;
    private final TurNativeFunctionToolSupport toolSupport;
    private final TurAgentWorkspace workspace;

    public TurOpenAiResponsesService(TurProviderOptionsParser optionsParser,
            TurNativeFunctionToolSupport toolSupport,
            TurAgentWorkspace workspace) {
        this.optionsParser = optionsParser;
        this.toolSupport = toolSupport;
        this.workspace = workspace;
    }

    /**
     * Execute the turn and emit the assistant text as a single SSE token event
     * (CALL semantics — the Responses tool turn must complete before the answer
     * is known).
     *
     * <p>T433 / §X.18.b — {@code coexistingTools} are the agent's Turing/MCP/
     * custom tools whose abstract {@code function} isn't claimed by a selected
     * provider-native capability. When present they are advertised as Responses
     * {@code function} tools alongside the server-side built-ins, and the turn
     * runs a client-side tool-execution loop: the model emits {@code function_call}
     * items, Turing executes the matching {@link ToolCallback} and feeds the
     * result back, until a plain-text answer comes out. When empty (the legacy
     * native path, or an agent with no coexisting tools) the loop runs exactly
     * once — identical to the original one-shot behaviour.
     */
    public Flux<ChatResponse> chat(OpenAIClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities, ToolCallback[] coexistingTools) {
        return chat(client, instance, history, systemPrompt, capabilities, coexistingTools, null);
    }

    /** F.7 / §X.8.e — chat with an explicit {@link com.viglet.turing.genai.servicetier.TurServiceTier TurServiceTier} (null → provider default). */
    public Flux<ChatResponse> chat(OpenAIClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities, ToolCallback[] coexistingTools,
            com.viglet.turing.genai.servicetier.TurServiceTier serviceTier) {
        return chat(client, instance, history, systemPrompt, capabilities, coexistingTools,
                serviceTier, null, null);
    }

    /**
     * Canonical entry point. {@code agentId} + {@code conversationId} scope where
     * an {@code image_generation} result is persisted (the per-conversation
     * {@link TurAgentWorkspace}) so the assistant turn can carry a renderable
     * {@code sandbox:} image URL; pass {@code null} for both when there is no
     * conversation scope (the image is then dropped, logged, never failing the
     * turn).
     */
    public Flux<ChatResponse> chat(OpenAIClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities, ToolCallback[] coexistingTools,
            com.viglet.turing.genai.servicetier.TurServiceTier serviceTier,
            String agentId, String conversationId) {
        return chat(client, instance, history, systemPrompt, capabilities, coexistingTools,
                serviceTier, agentId, conversationId, StoredCompletionsDirective.DISABLED);
    }

    /**
     * F.9 / §X.10.a — chat with an explicit {@link StoredCompletionsDirective}.
     * When {@link StoredCompletionsDirective#store()} is true the turn is
     * persisted on OpenAI's side with the directive's structured metadata, so
     * the T168 Evals / T169 distillation export can slice it later. Disabled →
     * the request leaves {@code store}/{@code metadata} unset (unchanged path).
     */
    public Flux<ChatResponse> chat(OpenAIClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities, ToolCallback[] coexistingTools,
            com.viglet.turing.genai.servicetier.TurServiceTier serviceTier,
            String agentId, String conversationId, StoredCompletionsDirective storedCompletions) {
        return chat(client, instance, history, systemPrompt, capabilities, coexistingTools,
                serviceTier, agentId, conversationId, storedCompletions, ReasoningOptions.NONE);
    }

    /**
     * F.12 / §X.13 — chat with explicit {@link ReasoningOptions}. When the agent
     * opted into the {@code reasoning-summary} (T178) / {@code reasoning-effort}
     * (T179) request options <em>and</em> the instance runs a reasoning model
     * (o-series / GPT-5), the Responses request carries the {@code reasoning}
     * config and any reasoning summary the model returns is surfaced as a
     * second {@code "reasoning"} SSE event after the answer token — the chat UI
     * renders it as the collapsible "Why this answer" panel. {@link
     * ReasoningOptions#NONE} (or a non-reasoning model) leaves the request and
     * the emission byte-for-byte unchanged.
     */
    public Flux<ChatResponse> chat(OpenAIClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities, ToolCallback[] coexistingTools,
            com.viglet.turing.genai.servicetier.TurServiceTier serviceTier,
            String agentId, String conversationId, StoredCompletionsDirective storedCompletions,
            ReasoningOptions reasoning) {
        return chat(client, instance, history, systemPrompt, capabilities, coexistingTools,
                serviceTier, agentId, conversationId, storedCompletions, reasoning, null);
    }

    /**
     * T181 / §X.14.a — canonical entry point that also carries the per-user
     * {@code safety_identifier} (the hashed Keycloak {@code sub}). When non-blank
     * it is sent on every Responses request so OpenAI can detect and rate-limit
     * per-user abuse on its edge; {@code null}/blank leaves the request unchanged
     * (anonymous turns, or the feature opted out). It must be resolved on the
     * request thread by the caller — this method runs the provider call on a
     * reactive worker that has no bound security context.
     */
    public Flux<ChatResponse> chat(OpenAIClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities, ToolCallback[] coexistingTools,
            com.viglet.turing.genai.servicetier.TurServiceTier serviceTier,
            String agentId, String conversationId, StoredCompletionsDirective storedCompletions,
            ReasoningOptions reasoning, String safetyIdentifier) {
        return chat(client, instance, history, systemPrompt, capabilities, coexistingTools,
                serviceTier, agentId, conversationId, storedCompletions, reasoning, safetyIdentifier,
                Params.NONE);
    }

    /**
     * T606 — persona-aware overload. When the active persona opted into
     * style→model calibration, {@code calibration} carries the per-turn
     * temperature/maxOutputTokens overrides ({@link Params#NONE} = unchanged path).
     */
    public Flux<ChatResponse> chat(OpenAIClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities, ToolCallback[] coexistingTools,
            com.viglet.turing.genai.servicetier.TurServiceTier serviceTier,
            String agentId, String conversationId, StoredCompletionsDirective storedCompletions,
            ReasoningOptions reasoning, String safetyIdentifier, Params calibration) {
        return Mono.fromCallable(() -> runLoop(client, instance, history, systemPrompt,
                        capabilities, coexistingTools, serviceTier, agentId, conversationId,
                        storedCompletions, reasoning, safetyIdentifier, calibration))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(TurOpenAiResponsesService::emitResult)
                .doOnError(err -> log.error("[Native][OpenAI-Responses] instance '{}' error: {}",
                        instance.getId(), err.getMessage(), err));
    }

    /**
     * Fan a completed {@link LoopResult} into SSE events: the assistant answer
     * (token) first, then — only when the model returned a non-blank reasoning
     * summary — a {@code "reasoning"} event the UI renders as the "Why this
     * answer" panel.
     */
    private static Flux<ChatResponse> emitResult(LoopResult result) {
        ChatResponse answer = new ChatResponse("assistant",
                StringUtils.hasText(result.text()) ? result.text() : "");
        if (!StringUtils.hasText(result.reasoningSummary())) {
            return Flux.just(answer);
        }
        return Flux.just(answer,
                new ChatResponse("assistant", result.reasoningSummary(), "reasoning"));
    }

    /** The completed turn: the assistant answer plus any captured reasoning summary. */
    record LoopResult(String text, String reasoningSummary) {
    }

    /**
     * F.12 / §X.13 — the per-turn reasoning request shape resolved from the
     * agent's request options: {@code summary} ({@code auto}/{@code concise}/
     * {@code detailed}, T178) and {@code effort} ({@code minimal}/{@code low}/
     * {@code medium}/{@code high}, T179). Either may be blank/null; {@link #NONE}
     * is the unset value that leaves the request unchanged.
     */
    public record ReasoningOptions(String summary, String effort) {
        public static final ReasoningOptions NONE = new ReasoningOptions(null, null);

        public boolean hasSummary() {
            return StringUtils.hasText(summary);
        }

        public boolean hasEffort() {
            return StringUtils.hasText(effort);
        }

        public boolean isEmpty() {
            return !hasSummary() && !hasEffort();
        }
    }

    /**
     * T503 / §X.13 — resolved background-mode knobs for a turn. {@code enabled}
     * submits the Responses call with {@code background: true}; {@code pollIntervalMs}
     * / {@code maxWaitMs} bound the retrieve-poll loop. Disabled → unchanged
     * synchronous create.
     */
    public record BackgroundOptions(boolean enabled, long pollIntervalMs, long maxWaitMs) {
        public static final BackgroundOptions DISABLED = new BackgroundOptions(false, 0, 0);
    }

    /** Default poll cadence + ceiling when the instance opts in without naming them. */
    static final long DEFAULT_BG_POLL_MS = 1000L;
    static final long DEFAULT_BG_MAX_WAIT_MS = 120_000L;

    /**
     * T503 — read the per-instance background knobs from provider options:
     * {@code backgroundEnabled} (gate), {@code backgroundPollIntervalMs},
     * {@code backgroundMaxWaitMs}. Absent / false → {@link BackgroundOptions#DISABLED}.
     */
    BackgroundOptions resolveBackground(TurLLMInstance instance) {
        Map<String, Object> options = optionsParser.parse(instance.getProviderOptionsJson());
        Object enabled = options == null ? null : options.get("backgroundEnabled");
        if (enabled == null || !"true".equalsIgnoreCase(enabled.toString().trim())) {
            return BackgroundOptions.DISABLED;
        }
        long poll = positiveOr(optionsParser.intValue(options, "backgroundPollIntervalMs"),
                DEFAULT_BG_POLL_MS);
        long maxWait = positiveOr(optionsParser.intValue(options, "backgroundMaxWaitMs"),
                DEFAULT_BG_MAX_WAIT_MS);
        return new BackgroundOptions(true, poll, maxWait);
    }

    private static long positiveOr(Integer value, long fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    /**
     * T503 — a background create returns immediately as {@code queued}/{@code in_progress};
     * poll {@code responses.retrieve(id)} until a terminal status (or the wait
     * ceiling) and return the final response. Fail-open: a retrieve error, or
     * exhausting the budget, returns the latest response seen so the caller still
     * extracts whatever text exists. No-op for an already-terminal response.
     */
    Response awaitBackground(OpenAIClient client, Response response, BackgroundOptions options) {
        long deadline = System.currentTimeMillis() + options.maxWaitMs();
        Response current = response;
        while (!isTerminal(current.status().orElse(null))) {
            if (System.currentTimeMillis() >= deadline) {
                log.warn("[Native][OpenAI-Responses] background response '{}' still {} after {} ms "
                        + "— returning partial", current.id(),
                        current.status().map(Object::toString).orElse("unknown"), options.maxWaitMs());
                return current;
            }
            try {
                Thread.sleep(options.pollIntervalMs());
                current = client.responses().retrieve(current.id());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return current;
            } catch (RuntimeException e) {
                log.warn("[Native][OpenAI-Responses] background retrieve for '{}' failed ({}) "
                        + "— returning latest", current.id(), e.getMessage());
                return current;
            }
        }
        return current;
    }

    /** A response status the poll loop should stop on (anything but queued/in_progress). */
    static boolean isTerminal(ResponseStatus status) {
        return status == null
                || !(status.equals(ResponseStatus.QUEUED) || status.equals(ResponseStatus.IN_PROGRESS));
    }

    LoopResult runLoop(OpenAIClient client, TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, List<EnabledCapability> capabilities, ToolCallback[] coexistingTools,
            com.viglet.turing.genai.servicetier.TurServiceTier serviceTier,
            String agentId, String conversationId, StoredCompletionsDirective storedCompletions,
            ReasoningOptions reasoning, String safetyIdentifier, Params calibration) {
        long t0 = System.currentTimeMillis();
        List<Tool> serverTools = buildTools(capabilities);
        List<Tool> functionTools = buildFunctionTools(coexistingTools);
        Map<String, ToolCallback> toolIndex = toolSupport.indexByName(coexistingTools);
        List<ResponseInputItem> items = seedItems(history);
        StringBuilder text = new StringBuilder();
        String reasoningSummary = "";
        int toolCalls = 0;
        // T503 — resolve once; when enabled, each round's create is submitted in
        // background and awaited via retrieve-polling instead of a long blocking call.
        BackgroundOptions background = resolveBackground(instance);

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            ResponseCreateParams params = buildParamsFromItems(instance, items, systemPrompt,
                    serverTools, functionTools, serviceTier, storedCompletions, reasoning,
                    safetyIdentifier, calibration);
            Response response = client.responses().create(params);
            if (background.enabled()) {
                response = awaitBackground(client, response, background);
            }
            text.append(extractText(response));
            appendGeneratedImages(text, response, agentId, conversationId);
            // Keep the latest non-blank reasoning summary — the final (answer)
            // round's reasoning is what the "Why this answer" panel should show.
            String roundSummary = extractReasoningSummary(response);
            if (StringUtils.hasText(roundSummary)) {
                reasoningSummary = roundSummary;
            }

            List<ResponseFunctionToolCall> calls = functionCalls(response);
            if (calls.isEmpty()) {
                log.info("[Native][OpenAI-Responses] instance '{}' model '{}' server-tools {} "
                                + "function-tools {} -> {} chars, {} tool call(s) in {} ms",
                        instance.getId(), resolveModel(instance),
                        capabilities.stream().map(c -> c.capability().getKey()).toList(),
                        functionTools.size(), text.length(), toolCalls,
                        System.currentTimeMillis() - t0);
                return new LoopResult(text.toString(), reasoningSummary);
            }
            for (ResponseFunctionToolCall call : calls) {
                items.add(ResponseInputItem.ofFunctionCall(call));
                ToolOutcome outcome = toolSupport.execute(toolIndex, call.name(), call.arguments());
                items.add(ResponseInputItem.ofFunctionCallOutput(
                        ResponseInputItem.FunctionCallOutput.builder()
                                .callId(call.callId())
                                .output(outcome.output())
                                .build()));
                toolCalls++;
            }
        }

        log.warn("[Native][OpenAI-Responses] instance '{}' hit the {}-round tool-execution cap",
                instance.getId(), MAX_TOOL_ITERATIONS);
        return new LoopResult(text.toString(), reasoningSummary);
    }

    /** Legacy/test overload — no coexisting client tools (one-shot server-tool turn). */
    public Flux<ChatResponse> chat(OpenAIClient client, TurLLMInstance instance,
            List<ChatMessageItem> history, String systemPrompt,
            List<EnabledCapability> capabilities) {
        return chat(client, instance, history, systemPrompt, capabilities, new ToolCallback[0]);
    }

    ResponseCreateParams buildParams(TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, List<EnabledCapability> capabilities) {
        return buildParamsFromItems(instance, seedItems(history), systemPrompt,
                buildTools(capabilities), List.of(), null, StoredCompletionsDirective.DISABLED,
                ReasoningOptions.NONE, null, Params.NONE);
    }

    /** T606 — persona-calibration-aware builder overload (test/builder entry). */
    ResponseCreateParams buildParams(TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, List<EnabledCapability> capabilities, Params calibration) {
        return buildParamsFromItems(instance, seedItems(history), systemPrompt,
                buildTools(capabilities), List.of(), null, StoredCompletionsDirective.DISABLED,
                ReasoningOptions.NONE, null, calibration);
    }

    /** Test/builder overload with an explicit stored-completions directive. */
    ResponseCreateParams buildParams(TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, List<EnabledCapability> capabilities,
            StoredCompletionsDirective storedCompletions) {
        return buildParamsFromItems(instance, seedItems(history), systemPrompt,
                buildTools(capabilities), List.of(), null, storedCompletions, ReasoningOptions.NONE,
                null, Params.NONE);
    }

    /** F.12 / §X.13 — test/builder overload with explicit {@link ReasoningOptions}. */
    ResponseCreateParams buildParams(TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, List<EnabledCapability> capabilities, ReasoningOptions reasoning) {
        return buildParamsFromItems(instance, seedItems(history), systemPrompt,
                buildTools(capabilities), List.of(), null, StoredCompletionsDirective.DISABLED,
                reasoning, null, Params.NONE);
    }

    /**
     * T181 / §X.14.a — test/builder overload with an explicit
     * {@code safetyIdentifier} so a test can assert it lands on the request.
     */
    ResponseCreateParams buildParams(TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, List<EnabledCapability> capabilities, String safetyIdentifier) {
        return buildParamsFromItems(instance, seedItems(history), systemPrompt,
                buildTools(capabilities), List.of(), null, StoredCompletionsDirective.DISABLED,
                ReasoningOptions.NONE, safetyIdentifier, Params.NONE);
    }

    private ResponseCreateParams buildParamsFromItems(TurLLMInstance instance,
            List<ResponseInputItem> items, String systemPrompt, List<Tool> serverTools,
            List<Tool> functionTools,
            com.viglet.turing.genai.servicetier.TurServiceTier serviceTier,
            StoredCompletionsDirective storedCompletions, ReasoningOptions reasoning,
            String safetyIdentifier, Params calibration) {
        ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
                .model(resolveModel(instance));

        if (StringUtils.hasText(systemPrompt)) {
            builder.instructions(systemPrompt);
        }
        builder.inputOfResponse(items);

        // T606 — persona style→model calibration overrides the instance temperature
        // when opted in, and adds a maxOutputTokens ceiling from verbosity (the chat
        // path otherwise sets none). Null fields = unchanged path.
        Double temperature = calibration.temperature() != null
                ? calibration.temperature()
                : instance.getTemperature();
        if (temperature != null) {
            builder.temperature(temperature);
        }
        if (calibration.maxTokens() != null) {
            builder.maxOutputTokens(calibration.maxTokens().longValue());
        }
        if (serviceTier != null) {
            builder.serviceTier(com.openai.models.responses.ResponseCreateParams.ServiceTier
                    .of(serviceTier.openAiValue()));
        }
        if (StringUtils.hasText(safetyIdentifier)) {
            // T181 / §X.14.a — hashed per-user id for OpenAI edge abuse detection.
            builder.safetyIdentifier(safetyIdentifier);
        }
        applyStoredCompletions(builder, storedCompletions);
        applyReasoning(builder, instance, reasoning);
        // T503 / §X.13 — background mode: submit long reasoning/agentic turns
        // asynchronously (the create returns a queued id; runLoop then polls
        // retrieve) so a slow turn doesn't hold the connection. OpenAI requires a
        // background response to be stored, so force store(true) when enabled.
        if (resolveBackground(instance).enabled()) {
            builder.background(true).store(true);
        }
        for (Tool tool : serverTools) {
            builder.addTool(tool);
        }
        for (Tool tool : functionTools) {
            builder.addTool(tool);
        }
        return builder.build();
    }

    /**
     * F.9 / §X.10.a — when the agent opted into stored completions, persist this
     * turn on OpenAI's side ({@code store: true}) tagged with the structured
     * metadata so T168 Evals / T169 distillation can slice it. No-op when the
     * directive is null/disabled, leaving the request unchanged.
     */
    private void applyStoredCompletions(ResponseCreateParams.Builder builder,
            StoredCompletionsDirective storedCompletions) {
        if (storedCompletions == null || !storedCompletions.store()) {
            return;
        }
        builder.store(true);
        if (!storedCompletions.metadata().isEmpty()) {
            ResponseCreateParams.Metadata.Builder metadata = ResponseCreateParams.Metadata.builder();
            storedCompletions.metadata().forEach(
                    (key, value) -> metadata.putAdditionalProperty(key, JsonValue.from(value)));
            builder.metadata(metadata.build());
        }
    }

    /**
     * F.12 / §X.13 (T178/T179) — attach the {@code reasoning} config to the
     * request when the agent opted into a reasoning request option AND the
     * instance runs a reasoning model. A no-op for {@link ReasoningOptions#NONE}
     * or a non-reasoning model (e.g. gpt-4o-mini), which would otherwise reject
     * the {@code reasoning} parameter with a 400.
     */
    private void applyReasoning(ResponseCreateParams.Builder builder, TurLLMInstance instance,
            ReasoningOptions reasoning) {
        if (reasoning == null || reasoning.isEmpty() || !isReasoningModel(instance)) {
            return;
        }
        Reasoning.Builder reasoningBuilder = Reasoning.builder();
        if (reasoning.hasSummary()) {
            reasoningBuilder.summary(
                    Reasoning.Summary.of(reasoning.summary().toLowerCase(Locale.ROOT)));
        }
        if (reasoning.hasEffort()) {
            reasoningBuilder.effort(
                    ReasoningEffort.of(reasoning.effort().toLowerCase(Locale.ROOT)));
        }
        builder.reasoning(reasoningBuilder.build());
    }

    /**
     * Concatenate the reasoning summary parts the model emitted (one
     * {@link ResponseReasoningItem} may carry several {@link
     * ResponseReasoningItem.Summary} segments). Blank when the model returned no
     * reasoning summary (non-reasoning model, or {@code reasoning.summary} not
     * requested).
     */
    String extractReasoningSummary(Response response) {
        StringBuilder summary = new StringBuilder();
        for (ResponseOutputItem item : response.output()) {
            if (!item.isReasoning()) {
                continue;
            }
            for (ResponseReasoningItem.Summary part : item.asReasoning().summary()) {
                String partText = part.text();
                if (StringUtils.hasText(partText)) {
                    if (!summary.isEmpty()) {
                        summary.append("\n\n");
                    }
                    summary.append(partText);
                }
            }
        }
        return summary.toString();
    }

    /** True when the instance's configured model is an OpenAI reasoning model (o-series / GPT-5). */
    public static boolean isReasoningModel(TurLLMInstance instance) {
        return isReasoningModelName(instance == null ? null : instance.getModelName());
    }

    /**
     * Reasoning models accept the {@code reasoning} parameter; chat models reject
     * it. Detect by the documented model-name families: {@code o1}/{@code o3}/
     * {@code o4} (and minis) plus the {@code gpt-5} family. Conservative — an
     * unknown name is treated as non-reasoning so the request stays unchanged.
     */
    static boolean isReasoningModelName(String model) {
        if (model == null) {
            return false;
        }
        String normalized = model.toLowerCase(Locale.ROOT).trim();
        return normalized.startsWith("o1")
                || normalized.startsWith("o3")
                || normalized.startsWith("o4")
                || normalized.startsWith("gpt-5")
                || normalized.startsWith("gpt5");
    }

    private List<ResponseInputItem> seedItems(List<ChatMessageItem> history) {
        List<ResponseInputItem> items = new ArrayList<>();
        if (history == null) {
            return items;
        }
        for (ChatMessageItem message : history) {
            if (message == null || !StringUtils.hasText(message.content())) {
                continue;
            }
            items.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
                    .role(roleFor(message.role()))
                    .content(message.content())
                    .build()));
        }
        return items;
    }

    /**
     * T433 — translate the agent's coexisting {@link ToolCallback}s into Responses
     * {@code function} tools (client-executed). The JSON-Schema string each
     * callback exposes becomes the function's {@code parameters}; {@code strict}
     * is left {@code false} because Turing's tool schemas aren't authored to the
     * strict-mode subset.
     */
    List<Tool> buildFunctionTools(ToolCallback[] callbacks) {
        List<Tool> tools = new ArrayList<>();
        if (callbacks == null) {
            return tools;
        }
        for (ToolCallback callback : callbacks) {
            ToolDefinition definition = callback.getToolDefinition();
            FunctionTool.Parameters.Builder parameters = FunctionTool.Parameters.builder();
            for (Map.Entry<String, Object> entry : toolSupport.parseSchema(callback).entrySet()) {
                parameters.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
            }
            tools.add(Tool.ofFunction(FunctionTool.builder()
                    .name(definition.name())
                    .description(definition.description() == null ? "" : definition.description())
                    .parameters(parameters.build())
                    .strict(false)
                    .build()));
        }
        return tools;
    }

    private static List<ResponseFunctionToolCall> functionCalls(Response response) {
        List<ResponseFunctionToolCall> calls = new ArrayList<>();
        for (ResponseOutputItem item : response.output()) {
            if (item.isFunctionCall()) {
                calls.add(item.asFunctionCall());
            }
        }
        return calls;
    }

    /**
     * Translate the enabled capability set into Responses built-in tools.
     * Capabilities that need configuration they don't have (file_search with no
     * vector store, mcp with no server) are skipped with a warning rather than
     * producing an invalid request.
     */
    List<Tool> buildTools(List<EnabledCapability> capabilities) {
        List<Tool> tools = new ArrayList<>();
        if (capabilities == null) {
            return tools;
        }
        for (EnabledCapability enabled : capabilities) {
            Map<String, Object> config = optionsParser.parse(enabled.configJson());
            switch (enabled.capability()) {
                case OPENAI_WEB_SEARCH -> tools.add(Tool.ofWebSearch(WebSearchTool.builder()
                        .type(WebSearchTool.Type.WEB_SEARCH)
                        .build()));
                case OPENAI_FILE_SEARCH -> {
                    List<String> vectorStoreIds = optionsParser.stringListValue(config, "vectorStoreIds");
                    if (vectorStoreIds.isEmpty()) {
                        log.warn("[Native][OpenAI-Responses] file_search enabled but no "
                                + "vectorStoreIds configured — skipping tool");
                    } else {
                        tools.add(Tool.ofFileSearch(FileSearchTool.builder()
                                .vectorStoreIds(vectorStoreIds)
                                .build()));
                    }
                }
                case OPENAI_CODE_INTERPRETER -> tools.add(Tool.ofCodeInterpreter(
                        Tool.CodeInterpreter.builder()
                                .container(Tool.CodeInterpreter.Container.ofCodeInterpreterToolAuto(
                                        Tool.CodeInterpreter.Container.CodeInterpreterToolAuto.builder()
                                                .build()))
                                .build()));
                case OPENAI_IMAGE_GENERATION -> tools.add(Tool.ofImageGeneration(
                        Tool.ImageGeneration.builder().build()));
                case OPENAI_MCP -> {
                    String serverLabel = optionsParser.stringValue(config, "serverLabel");
                    String serverUrl = optionsParser.stringValue(config, "serverUrl");
                    if (StringUtils.hasText(serverLabel) && StringUtils.hasText(serverUrl)) {
                        tools.add(Tool.ofMcp(Tool.Mcp.builder()
                                .serverLabel(serverLabel)
                                .serverUrl(serverUrl)
                                .requireApproval(Tool.Mcp.RequireApproval.ofMcpToolApprovalSetting(
                                        Tool.Mcp.RequireApproval.McpToolApprovalSetting.NEVER))
                                .build()));
                    } else {
                        log.warn("[Native][OpenAI-Responses] mcp enabled but serverLabel/serverUrl "
                                + "missing — skipping tool");
                    }
                }
                case OPENAI_COMPUTER_USE -> log.debug("[Native][OpenAI-Responses] computer_use is "
                        + "handled by the dedicated multi-turn loop, not as a one-shot tool — skipping here");
            }
        }
        return tools;
    }

    /**
     * T180 / §X.13.c — one-shot yes/no relevance classification of a snippet
     * against a query, returning the model's calibrated <b>P(relevant)</b> in
     * {@code [0,1]} read from the first answer token's {@code top_logprobs}.
     *
     * <p>The Responses request constrains the model to a single yes/no token
     * ({@code maxOutputTokens=1}) with {@code top_logprobs} on, so the log-prob of
     * {@code yes} vs {@code no} is the model's confidence. Fail-open: any error,
     * missing logprobs, or a non-reasoning failure returns {@link Optional#empty}
     * so the reranker keeps retrieval order. Pure scoring lives in
     * {@link TurLogprobConfidence}.
     */
    public Optional<Double> classifyRelevanceProbability(OpenAIClient client, TurLLMInstance instance,
            String query, String snippet) {
        if (client == null || !StringUtils.hasText(query) || !StringUtils.hasText(snippet)) {
            return Optional.empty();
        }
        try {
            String input = "Question: " + query + "\n\nSnippet:\n" + snippet
                    + "\n\nIs the snippet relevant to answering the question? Answer with only "
                    + "\"yes\" or \"no\".";
            ResponseCreateParams params = ResponseCreateParams.builder()
                    .model(resolveModel(instance))
                    .instructions("You are a search relevance classifier. Reply with only \"yes\" or "
                            + "\"no\" — no other text.")
                    .input(input)
                    .maxOutputTokens(1L)
                    .topLogprobs(RELEVANCE_TOP_LOGPROBS)
                    .addInclude(ResponseIncludable.MESSAGE_OUTPUT_TEXT_LOGPROBS)
                    .build();
            Response response = client.responses().create(params);
            List<TokenLogprob> alternatives = firstTokenLogprobs(response);
            if (alternatives.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(TurLogprobConfidence.relevanceProbability(alternatives));
        } catch (RuntimeException e) {
            log.debug("[Native][OpenAI-Responses] relevance logprob classify failed: {}",
                    e.getMessage());
            return Optional.empty();
        }
    }

    /** How many token alternatives to request so both {@code yes} and {@code no} are visible. */
    private static final long RELEVANCE_TOP_LOGPROBS = 10L;

    /**
     * The first output-text token's logprob alternatives (the chosen token plus
     * its {@code top_logprobs}), mapped onto the provider-agnostic
     * {@link TokenLogprob}. Empty when the response carried no message text /
     * logprobs (logprobs not returned).
     */
    private static List<TokenLogprob> firstTokenLogprobs(Response response) {
        for (ResponseOutputItem item : response.output()) {
            if (!item.isMessage()) {
                continue;
            }
            for (ResponseOutputMessage.Content content : item.asMessage().content()) {
                if (!content.isOutputText()) {
                    continue;
                }
                Optional<List<ResponseOutputText.Logprob>> logprobs = content.asOutputText().logprobs();
                if (logprobs.isEmpty() || logprobs.get().isEmpty()) {
                    continue;
                }
                ResponseOutputText.Logprob first = logprobs.get().get(0);
                List<TokenLogprob> alternatives = new ArrayList<>();
                alternatives.add(new TokenLogprob(first.token(), first.logprob()));
                for (ResponseOutputText.Logprob.TopLogprob top : first.topLogprobs()) {
                    alternatives.add(new TokenLogprob(top.token(), top.logprob()));
                }
                return alternatives;
            }
        }
        return List.of();
    }

    /** Concatenate the text of every assistant message item in the response. */
    String extractText(Response response) {
        StringBuilder text = new StringBuilder();
        for (ResponseOutputItem item : response.output()) {
            if (!item.isMessage()) {
                continue;
            }
            ResponseOutputMessage message = item.asMessage();
            for (ResponseOutputMessage.Content content : message.content()) {
                if (content.isOutputText()) {
                    text.append(content.asOutputText().text());
                }
            }
        }
        return text.toString();
    }

    /**
     * Surface any {@code image_generation} output the model produced. The
     * Responses tool returns the image inline as base64 on an
     * {@code ImageGenerationCall} output item — which {@link #extractText} drops
     * (it only reads message text). Without this, the model would say "here is
     * the image" while nothing renders. Each image is persisted to the
     * per-conversation {@link TurAgentWorkspace} and a {@code sandbox:}-prefixed
     * signed URL is appended as markdown so the chat renders it inline (same
     * scheme the code-interpreter uses).
     *
     * <p>Persistence failures (storage disabled, no conversation scope) are
     * logged and skipped — the turn still returns its text, just without the
     * image, rather than failing outright.
     */
    private void appendGeneratedImages(StringBuilder text, Response response,
            String agentId, String conversationId) {
        if (workspace == null || !StringUtils.hasText(agentId) || !StringUtils.hasText(conversationId)) {
            return;
        }
        int index = 0;
        for (ResponseOutputItem item : response.output()) {
            if (!item.isImageGenerationCall()) {
                continue;
            }
            Optional<String> base64 = item.asImageGenerationCall().result();
            if (base64.isEmpty() || base64.get().isBlank()) {
                continue;
            }
            String key = "generated-images/" + safeImageId(item.asImageGenerationCall().id(), index) + ".png";
            try {
                byte[] bytes = Base64.getDecoder().decode(base64.get());
                workspace.put(agentId, conversationId, key, bytes, "image/png");
                String url = workspace.signedUrl(agentId, conversationId, key);
                text.append("\n\n![Generated image](sandbox:").append(url).append(")");
            } catch (RuntimeException e) {
                log.warn("[Native][OpenAI-Responses] could not surface generated image '{}': {}",
                        key, e.getMessage());
            }
            index++;
        }
    }

    /** Filename-safe image id from the provider id, falling back to the index. */
    static String safeImageId(String id, int index) {
        if (StringUtils.hasText(id)) {
            String safe = id.replaceAll("[^A-Za-z0-9_-]", "");
            if (!safe.isBlank()) {
                return safe;
            }
        }
        return "image-" + index;
    }

    private String resolveModel(TurLLMInstance instance) {
        return StringUtils.hasText(instance.getModelName()) ? instance.getModelName() : DEFAULT_MODEL;
    }

    private EasyInputMessage.Role roleFor(String role) {
        if (role == null) {
            return EasyInputMessage.Role.USER;
        }
        return switch (role.toLowerCase(Locale.ROOT)) {
            case "assistant" -> EasyInputMessage.Role.ASSISTANT;
            case "system" -> EasyInputMessage.Role.SYSTEM;
            case "developer" -> EasyInputMessage.Role.DEVELOPER;
            default -> EasyInputMessage.Role.USER;
        };
    }
}
