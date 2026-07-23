/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.viglet.turing.genai.gateway.TurOpenAiWire.ChatCompletion;
import com.viglet.turing.genai.gateway.TurOpenAiWire.ChatCompletionChunk;
import com.viglet.turing.genai.gateway.TurOpenAiWire.ChatCompletionRequest;
import com.viglet.turing.genai.gateway.TurOpenAiWire.Choice;
import com.viglet.turing.genai.gateway.TurOpenAiWire.ChunkChoice;
import com.viglet.turing.genai.gateway.TurOpenAiWire.Delta;
import com.viglet.turing.genai.gateway.TurOpenAiWire.EmbeddingData;
import com.viglet.turing.genai.gateway.TurOpenAiWire.EmbeddingRequest;
import com.viglet.turing.genai.gateway.TurOpenAiWire.EmbeddingResponse;
import com.viglet.turing.genai.gateway.TurOpenAiWire.ModelList;
import com.viglet.turing.genai.gateway.TurOpenAiWire.ModelObject;
import com.viglet.turing.genai.gateway.TurOpenAiWire.Usage;
import com.viglet.turing.genai.gateway.TurOpenAiWire.WireMessage;
import com.viglet.turing.genai.safety.TurModerationService;
import com.viglet.turing.persistence.model.gateway.TurGatewayKey;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.service.llm.budget.TurChatCostBudgetGate;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

/**
 * T740 / §XLIX — the thin wire-format translator behind the Governed LLM Gateway
 * (Block AZ). It resolves the requested {@code model} to a Turing
 * {@link TurLLMInstance}, builds the Spring AI model through the existing
 * resilient factory ({@link TurLlmModelFactory}) and translates the OpenAI
 * request/response wire format on the way in and out. No new LLM logic lives
 * here — blocking chat uses {@link ChatModel#call(Prompt)}, streaming uses
 * {@link ChatModel#stream(Prompt)}, embeddings use {@link EmbeddingModel}. Token
 * usage is recorded through the same {@link TurLLMTokenUsageService} every other
 * chat path uses.
 *
 * <p>Model resolution is honest passthrough: the client picks a real model id
 * from {@code GET /v1/models} (an id or {@code modelName} of an enabled
 * instance); an unmatched name falls back to the configured default LLM.
 * Turing-native {@code model} routing ({@code turing-agent:} / {@code turing-sn:}
 * / {@code turing-local:}) is layered on in T744.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurGatewayLlmService {

    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_SYSTEM = "system";
    private static final String OBJ_CHAT_COMPLETION = "chat.completion";
    private static final String OBJ_CHAT_CHUNK = "chat.completion.chunk";
    private static final String FINISH_STOP = "stop";
    private static final String STAGE_GATEWAY = "gateway.chat";
    private static final String RAG_PREAMBLE =
            "Use the following retrieved context to answer the user's question. "
                    + "If the answer is not contained in the context, say you don't know.\n<context>\n";
    /** SSE terminator every OpenAI streaming client waits for. */
    public static final String DONE = "[DONE]";

    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurLLMTokenUsageService tokenUsageService;
    private final TurGlobalSettingsService globalSettingsService;
    private final TurGatewayKeyService gatewayKeyService;
    private final TurGatewayRateLimiter gatewayRateLimiter;
    private final TurChatCostBudgetGate budgetGate;
    private final TurGatewayResponseCache responseCache;
    private final TurModerationService moderationService;
    private final TurGatewayModelRouter modelRouter;
    private final TurGatewayNativeToolsService nativeToolsService;
    private final TurGatewayRouterService routerService;
    private final TurGatewayTrafficCaptureService trafficCapture;
    private final ObjectMapper objectMapper;

    public TurGatewayLlmService(TurLLMInstanceRepository llmInstanceRepository,
            TurLlmModelFactory llmModelFactory,
            TurSecretCryptoService secretCryptoService,
            TurLLMTokenUsageService tokenUsageService,
            TurGlobalSettingsService globalSettingsService,
            TurGatewayKeyService gatewayKeyService,
            TurGatewayRateLimiter gatewayRateLimiter,
            TurChatCostBudgetGate budgetGate,
            TurGatewayResponseCache responseCache,
            TurModerationService moderationService,
            TurGatewayModelRouter modelRouter,
            TurGatewayNativeToolsService nativeToolsService,
            TurGatewayRouterService routerService,
            TurGatewayTrafficCaptureService trafficCapture,
            ObjectMapper objectMapper) {
        this.llmInstanceRepository = llmInstanceRepository;
        this.llmModelFactory = llmModelFactory;
        this.secretCryptoService = secretCryptoService;
        this.tokenUsageService = tokenUsageService;
        this.globalSettingsService = globalSettingsService;
        this.gatewayKeyService = gatewayKeyService;
        this.gatewayRateLimiter = gatewayRateLimiter;
        this.budgetGate = budgetGate;
        this.responseCache = responseCache;
        this.moderationService = moderationService;
        this.modelRouter = modelRouter;
        this.nativeToolsService = nativeToolsService;
        this.routerService = routerService;
        this.trafficCapture = trafficCapture;
        this.objectMapper = objectMapper;
    }

    /**
     * T745 — execute a {@code turing-router:*} turn: walk the ordered candidate
     * deployments (per the router strategy), returning the first success and
     * feeding the observed latency back for least-latency routing. Records spend
     * on the winning instance. Throws the last error when every candidate fails.
     */
    private String routerAnswer(ChatCompletionRequest request, String username,
            TurGatewayOptions options, TurGatewayKey key) {
        List<TurLLMInstance> candidates = routerService.orderedCandidates(request.model());
        RuntimeException last = null;
        for (TurLLMInstance instance : candidates) {
            try {
                ChatModel chatModel = buildChatModel(instance);
                Prompt prompt = new Prompt(buildPromptMessages(request.messages(), options));
                long startedAt = System.currentTimeMillis();
                ChatResponse response = chatModel.call(prompt);
                routerService.recordLatency(instance.getId(), System.currentTimeMillis() - startedAt);
                tokenUsageService.recordUsage(instance, response, username, null, STAGE_GATEWAY, keyId(key));
                String routerText = extractText(response);
                trafficCapture.capture(request.model(), request.messages(), routerText, instance, keyId(key));
                return routerText;
            } catch (RuntimeException e) {
                last = e;
                log.warn("[Gateway] router candidate {} failed, trying next: {}",
                        instance.getId(), e.getMessage());
            }
        }
        throw last != null ? last : new IllegalStateException("turing-router: all candidates failed");
    }

    /** T747 — map wire messages to executor chat items (for the native-tool vendor services). */
    private List<com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem> toChatItems(
            List<WireMessage> messages) {
        if (messages == null) {
            return List.of();
        }
        return messages.stream()
                .map(m -> new com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem(
                        m.role(), m.content()))
                .toList();
    }

    /** T747 — the RAG-as-a-header system prompt for a native-tool turn, or {@code null}. */
    private String ragSystemPrompt(TurGatewayOptions options, List<WireMessage> messages) {
        if (options.ragSite() == null) {
            return null;
        }
        String context = modelRouter.retrieveContext(options.ragSite(), lastUserText(messages));
        return (context == null || context.isBlank()) ? null : RAG_PREAMBLE + context + "\n</context>";
    }

    /**
     * T744 / T795 — a {@code turing-agent:} / {@code turing-sn:} / {@code turing-copilot:}
     * answer (the last also reachable via the {@code x-turing-copilot-site} header),
     * or {@code null} for passthrough.
     */
    private String routedAnswer(String model, List<WireMessage> messages, TurGatewayOptions options) {
        if (modelRouter.isAgent(model)) {
            return modelRouter.agentAnswer(model, messages);
        }
        if (modelRouter.isSn(model)) {
            return modelRouter.snAnswer(model, messages);
        }
        if (modelRouter.isCopilot(model)) {
            return modelRouter.copilotAnswer(model.substring(TurGatewayModelRouter.COPILOT_PREFIX.length()).trim(),
                    messages);
        }
        // T795 — header form: x-turing-copilot-site routes any model to the copilot.
        if (options != null && options.copilotSite() != null) {
            return modelRouter.copilotAnswer(options.copilotSite(), messages);
        }
        return null;
    }

    /** T744 — base-model messages, with retrieved context prepended for {@code x-turing-rag-site}. */
    private List<Message> buildPromptMessages(List<WireMessage> messages, TurGatewayOptions options) {
        List<Message> springMessages = buildMessages(messages);
        if (options.ragSite() != null) {
            String context = modelRouter.retrieveContext(options.ragSite(), lastUserText(messages));
            if (context != null && !context.isBlank()) {
                springMessages.add(0, new SystemMessage(RAG_PREAMBLE + context + "\n</context>"));
            }
        }
        return springMessages;
    }

    private String lastUserText(List<WireMessage> messages) {
        if (messages == null) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            WireMessage m = messages.get(i);
            if (m.content() != null && !ROLE_ASSISTANT.equals(m.role()) && !ROLE_SYSTEM.equals(m.role())) {
                return m.content();
            }
        }
        return "";
    }

    /**
     * T742 / §XLIX — per-key pre-call governance: enforce the key's rate limit and
     * hard budget cap before any upstream LLM call. Returns the authenticated key
     * (or {@code null} in open mode) so callers can attribute spend and apply the
     * soft-budget downgrade. Throws {@link TurGatewayLimitException} (→ 429) when a
     * limit is hit.
     */
    private TurGatewayKey applyPerKeyPreCall() {
        TurGatewayKey key = TurGatewayContext.get();
        if (key != null) {
            if (!gatewayRateLimiter.tryAcquire(key)) {
                throw TurGatewayLimitException.rateLimited();
            }
            if (budgetGate.isKeyHardCapExceeded(key)) {
                throw TurGatewayLimitException.quotaExceeded();
            }
        }
        return key;
    }

    private static String keyId(TurGatewayKey key) {
        return key == null ? null : key.getId();
    }

    /**
     * T743 / §XLIX — {@code x-turing-guardrails: strict} input moderation over the
     * concatenated user turns. Uses {@code moderateAlways} so it runs regardless
     * of the global moderation enable flag (fail-open when no moderation provider
     * is configured). Flagged input → {@link TurGatewayGuardrailException} (400).
     */
    private void guardInput(TurGatewayOptions options, List<WireMessage> messages) {
        if (!options.guardrails() || messages == null) {
            return;
        }
        String userText = messages.stream()
                .filter(m -> m.content() != null
                        && !ROLE_ASSISTANT.equals(m.role()) && !ROLE_SYSTEM.equals(m.role()))
                .map(WireMessage::content)
                .collect(Collectors.joining("\n"));
        if (userText.isBlank()) {
            return;
        }
        var verdict = moderationService.moderateAlways(userText);
        if (verdict.flagged()) {
            throw new TurGatewayGuardrailException("Request input flagged by guardrails", verdict.categories());
        }
    }

    /** T743 — {@code x-turing-guardrails: strict} output moderation (blocking path only). */
    private void guardOutput(TurGatewayOptions options, String text) {
        if (!options.guardrails() || text == null || text.isBlank()) {
            return;
        }
        var verdict = moderationService.moderateAlways(text);
        if (verdict.flagged()) {
            throw new TurGatewayGuardrailException("Model output flagged by guardrails", verdict.categories());
        }
    }

    /** T743 — replay a cache-hit as a single content chunk + terminator (streaming path). */
    private Flux<ServerSentEvent<String>> replayCached(String id, long created, String model, String text) {
        ServerSentEvent<String> content = chunkFrame(id, created, model,
                new Delta(ROLE_ASSISTANT, text), null);
        ServerSentEvent<String> stop = chunkFrame(id, created, model, new Delta(null, ""), FINISH_STOP);
        return Flux.just(content, stop, ServerSentEvent.<String>builder(DONE).build());
    }

    /**
     * T741 / §XLIX — enforce the authenticated virtual key's model allow-list.
     * No-op in open mode (no key on the request thread). Throws
     * {@link IllegalArgumentException} (→ 403-shaped OpenAI error) when the
     * requested model is outside the key's scope.
     */
    private void enforceScope(String model) {
        var key = TurGatewayContext.get();
        if (key != null && !gatewayKeyService.isModelAllowed(key, model)) {
            throw new IllegalArgumentException("Model not permitted for this key: " + model);
        }
    }

    // ---- Model listing ----------------------------------------------------

    /**
     * Advertises every enabled LLM instance as an OpenAI model object. A client
     * points {@code base_url} at Turing and lists models exactly as it would
     * against OpenAI.
     */
    public ModelList listModels() {
        long created = nowEpochSeconds();
        List<ModelObject> models = new ArrayList<>();
        for (TurLLMInstance instance : llmInstanceRepository.findAll()) {
            if (instance.getEnabled() != 1) {
                continue;
            }
            String vendor = instance.getTurLLMVendor() != null
                    ? instance.getTurLLMVendor().getId()
                    : "turing";
            // Advertise the stable instance id — unambiguous even when two
            // instances share the same modelName (e.g. two gpt-4o deployments).
            models.add(new ModelObject(instance.getId(), "model", created, vendor));
        }
        return new ModelList("list", models);
    }

    // ---- Chat completions (blocking) -------------------------------------

    /**
     * Blocking {@code chat.completion}. Resolves the instance, calls the model
     * once and assembles a single OpenAI completion object with usage.
     */
    public ChatCompletion complete(ChatCompletionRequest request, String username, TurGatewayOptions options) {
        enforceScope(request.model());
        TurGatewayKey key = applyPerKeyPreCall();
        guardInput(options, request.messages());

        String cacheKey = options.semanticCache()
                ? responseCache.keyFor(request.model(), request.messages()) : null;
        if (cacheKey != null) {
            String cached = responseCache.get(cacheKey);
            if (cached != null) {
                return completionOf(request.model(), cached, new Usage(0, 0, 0));
            }
        }

        // T744 — turing-agent: / turing-sn: route into the Turing stack.
        String routed = routedAnswer(request.model(), request.messages(), options);
        if (routed != null) {
            guardOutput(options, routed);
            if (cacheKey != null) {
                responseCache.put(cacheKey, routed);
            }
            return completionOf(request.model(), routed, new Usage(0, 0, 0));
        }

        // T745 — turing-router: load-balance across deployments with fallback.
        if (routerService.isRouter(request.model())) {
            String text = routerAnswer(request, username, options, key);
            guardOutput(options, text);
            if (cacheKey != null) {
                responseCache.put(cacheKey, text);
            }
            return completionOf(request.model(), text, new Usage(0, 0, 0));
        }

        TurLLMInstance instance = budgetGate.resolveInstanceForKey(key, resolveChatInstance(request.model()));

        // T747 — x-turing-tools: enable F.15 provider-native tools on the base model.
        if (!options.nativeTools().isEmpty()
                && nativeToolsService.supports(instance, options.nativeTools())) {
            String text = nativeToolsService.answer(instance, toChatItems(request.messages()),
                    ragSystemPrompt(options, request.messages()), options.nativeTools());
            guardOutput(options, text);
            if (cacheKey != null) {
                responseCache.put(cacheKey, text);
            }
            return completionOf(request.model(), text, new Usage(0, 0, 0));
        }

        ChatModel chatModel = buildChatModel(instance);
        Prompt prompt = new Prompt(buildPromptMessages(request.messages(), options));

        ChatResponse response = chatModel.call(prompt);
        tokenUsageService.recordUsage(instance, response, username, null, STAGE_GATEWAY, keyId(key));

        String text = extractText(response);
        guardOutput(options, text);
        if (cacheKey != null) {
            responseCache.put(cacheKey, text);
        }
        // T746 — capture base-model traffic for the eval/distillation bridge.
        trafficCapture.capture(request.model(), request.messages(), text, instance, keyId(key));
        return completionOf(request.model(), text, extractUsage(response));
    }

    private ChatCompletion completionOf(String model, String text, Usage usage) {
        Choice choice = new Choice(0, new WireMessage(ROLE_ASSISTANT, text), FINISH_STOP);
        return new ChatCompletion("chatcmpl-" + UUID.randomUUID(), OBJ_CHAT_COMPLETION,
                nowEpochSeconds(), model, List.of(choice), usage);
    }

    // ---- Chat completions (streaming SSE) --------------------------------

    /**
     * Streaming {@code chat.completion.chunk} SSE. Each provider token becomes a
     * {@code data: {chunk}} frame; a final {@code finish_reason:"stop"} chunk and
     * the {@code data: [DONE]} sentinel close the stream. Mid-stream provider
     * errors are surfaced as a readable final frame (the servlet stream is
     * already committed, so a JSON error body is no longer possible).
     */
    public Flux<ServerSentEvent<String>> stream(ChatCompletionRequest request, String username,
            TurGatewayOptions options) {
        enforceScope(request.model());
        TurGatewayKey key = applyPerKeyPreCall();
        // Input moderation blocks before any streaming starts. Output moderation
        // is not applied to streamed tokens (they are already committed) — use
        // the blocking endpoint for strict output guardrails.
        guardInput(options, request.messages());

        String id = "chatcmpl-" + UUID.randomUUID();
        long created = nowEpochSeconds();

        String cacheKey = options.semanticCache()
                ? responseCache.keyFor(request.model(), request.messages()) : null;
        if (cacheKey != null) {
            String cached = responseCache.get(cacheKey);
            if (cached != null) {
                return replayCached(id, created, request.model(), cached);
            }
        }

        // T744 — native routing produces a blocking answer, replayed as one chunk.
        String routed = routedAnswer(request.model(), request.messages(), options);
        if (routed != null) {
            if (cacheKey != null) {
                responseCache.put(cacheKey, routed);
            }
            return replayCached(id, created, request.model(), routed);
        }

        // T745 — turing-router: pick + fallback, blocking, replayed as one chunk.
        if (routerService.isRouter(request.model())) {
            String text = routerAnswer(request, username, options, key);
            if (cacheKey != null) {
                responseCache.put(cacheKey, text);
            }
            return replayCached(id, created, request.model(), text);
        }

        TurLLMInstance instance = budgetGate.resolveInstanceForKey(key, resolveChatInstance(request.model()));

        // T747 — x-turing-tools on a streamed base model: produce blocking, replay as one chunk.
        if (!options.nativeTools().isEmpty()
                && nativeToolsService.supports(instance, options.nativeTools())) {
            String text = nativeToolsService.answer(instance, toChatItems(request.messages()),
                    ragSystemPrompt(options, request.messages()), options.nativeTools());
            if (cacheKey != null) {
                responseCache.put(cacheKey, text);
            }
            return replayCached(id, created, request.model(), text);
        }

        ChatModel chatModel = llmModelFactory.createStreamingChatModel(
                instance, secretCryptoService.decrypt(instance.getApiKeyEncrypted()));
        Prompt prompt = new Prompt(buildPromptMessages(request.messages(), options));
        AtomicReference<ChatResponse> last = new AtomicReference<>();
        StringBuilder assembled = new StringBuilder();

        Flux<ServerSentEvent<String>> tokens = chatModel.stream(prompt)
                .doOnNext(last::set)
                .map(resp -> {
                    String token = extractText(resp);
                    if (!token.isEmpty()) {
                        assembled.append(token);
                    }
                    return chunkFrame(id, created, request.model(), new Delta(null, token), null);
                })
                .filter(frame -> frame != null);

        Flux<ServerSentEvent<String>> tail = Flux.defer(() -> {
            ChatResponse finalResponse = last.get();
            if (finalResponse != null) {
                tokenUsageService.recordUsage(instance, finalResponse, username, null, STAGE_GATEWAY, keyId(key));
            }
            if (cacheKey != null) {
                responseCache.put(cacheKey, assembled.toString());
            }
            ServerSentEvent<String> stop = chunkFrame(id, created, request.model(),
                    new Delta(null, ""), FINISH_STOP);
            return Flux.just(stop, ServerSentEvent.<String>builder(DONE).build());
        });

        return tokens.concatWith(tail)
                .onErrorResume(err -> {
                    log.warn("[Gateway] Stream error: {}", err.getMessage());
                    ServerSentEvent<String> errFrame = chunkFrame(id, created, request.model(),
                            new Delta(ROLE_ASSISTANT, "[error: " + err.getMessage() + "]"), FINISH_STOP);
                    return Flux.just(errFrame, ServerSentEvent.<String>builder(DONE).build());
                });
    }

    // ---- Embeddings -------------------------------------------------------

    /** {@code /v1/embeddings} over the resolved instance's embedding model. */
    public EmbeddingResponse embed(EmbeddingRequest request) {
        enforceScope(request.model());
        applyPerKeyPreCall();
        TurLLMInstance instance = resolveChatInstance(request.model());
        EmbeddingModel embeddingModel = llmModelFactory.createEmbeddingModel(
                instance, secretCryptoService.decrypt(instance.getApiKeyEncrypted()));

        List<String> inputs = normaliseInput(request.input());
        List<float[]> vectors = embeddingModel.embed(inputs);
        List<EmbeddingData> data = new ArrayList<>(vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            data.add(new EmbeddingData("embedding", i, vectors.get(i)));
        }
        return new EmbeddingResponse("list", data, request.model(), null);
    }

    // ---- Internals --------------------------------------------------------

    /**
     * Resolves a requested {@code model} to an enabled instance: by id, then by
     * {@code modelName}, else the configured default LLM. Package-visible so
     * T744's native-model router can reuse the base resolution.
     */
    TurLLMInstance resolveChatInstance(String model) {
        // T744 — turing-local:* forces the embedded ($0) provider instance.
        if (modelRouter.isLocal(model)) {
            return modelRouter.resolveEmbeddedInstance();
        }
        if (model != null && !model.isBlank()) {
            var byId = llmInstanceRepository.findById(model).filter(i -> i.getEnabled() == 1);
            if (byId.isPresent()) {
                return byId.get();
            }
            for (TurLLMInstance instance : llmInstanceRepository.findAll()) {
                if (instance.getEnabled() == 1 && model.equalsIgnoreCase(instance.getModelName())) {
                    return instance;
                }
            }
        }
        String defaultId = globalSettingsService.getDefaultLlmId();
        if (defaultId != null && !defaultId.isBlank()) {
            var def = llmInstanceRepository.findById(defaultId).filter(i -> i.getEnabled() == 1);
            if (def.isPresent()) {
                return def.get();
            }
        }
        throw new IllegalArgumentException("Unknown model and no enabled default LLM configured: " + model);
    }

    private ChatModel buildChatModel(TurLLMInstance instance) {
        return llmModelFactory.createChatModel(instance, secretCryptoService.decrypt(instance.getApiKeyEncrypted()));
    }

    private List<Message> buildMessages(List<WireMessage> items) {
        List<Message> messages = new ArrayList<>();
        if (items == null) {
            return messages;
        }
        for (WireMessage item : items) {
            String content = item.content() == null ? "" : item.content();
            if (ROLE_ASSISTANT.equals(item.role())) {
                messages.add(new AssistantMessage(content));
            } else if (ROLE_SYSTEM.equals(item.role())) {
                messages.add(new SystemMessage(content));
            } else {
                messages.add(new UserMessage(content));
            }
        }
        return messages;
    }

    private List<String> normaliseInput(Object input) {
        if (input instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(String.valueOf(o));
            }
            return out;
        }
        if (input == null) {
            return List.of("");
        }
        return List.of(String.valueOf(input));
    }

    private String extractText(ChatResponse response) {
        if (response != null && response.getResult() != null
                && response.getResult().getOutput() != null
                && response.getResult().getOutput().getText() != null) {
            return response.getResult().getOutput().getText();
        }
        return "";
    }

    private Usage extractUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return new Usage(0, 0, 0);
        }
        var u = response.getMetadata().getUsage();
        long in = u.getPromptTokens();
        long out = u.getCompletionTokens();
        long total = u.getTotalTokens();
        return new Usage(in, out, total > 0 ? total : in + out);
    }

    /** Serialises one streaming chunk into an SSE {@code data:} frame; null on serialisation failure. */
    private ServerSentEvent<String> chunkFrame(String id, long created, String model,
            Delta delta, String finishReason) {
        // Skip empty non-terminal deltas so we don't emit blank frames.
        if (finishReason == null && (delta.content() == null || delta.content().isEmpty())) {
            return null;
        }
        ChatCompletionChunk chunk = new ChatCompletionChunk(id, OBJ_CHAT_CHUNK, created, model,
                List.of(new ChunkChoice(0, delta, finishReason)));
        try {
            return ServerSentEvent.<String>builder(objectMapper.writeValueAsString(chunk)).build();
        } catch (RuntimeException e) {
            log.warn("[Gateway] Failed to serialise chunk: {}", e.getMessage());
            return null;
        }
    }

    private long nowEpochSeconds() {
        return System.currentTimeMillis() / 1000L;
    }
}
