/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.cohere;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatResponse;
import com.viglet.turing.genai.citation.TurChatCitation;
import com.viglet.turing.genai.citation.TurCitationDocument;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T519 / §XXVIII.15 — native Cohere {@code Command} citation path. When an agent
 * on a {@code cohere} instance enables the {@code citations} Request Option, the
 * retrieved knowledge-base passages are sent to Cohere's native
 * {@code POST /v2/chat} as the {@code documents} array, and the response's
 * {@code message.citations[]} are decoded onto the existing
 * {@link TurChatCitation} contract and streamed back as the {@code citations}
 * SSE event — exactly like the Anthropic / Gemini native citations.
 *
 * <p>Cohere's chat answer otherwise rides the OpenAI-compatible endpoint
 * ({@code TurCohereLlmProvider}); that path can't surface native citations, so
 * this dedicated v2 call is taken only for the citations turn (mirroring how the
 * Anthropic Messages path is taken only when citations/native-PDF are on). The
 * call is buffered (decode needs the whole {@code message}); the result is then
 * emitted as one token event + the citations event, the same shape
 * {@code TurAnthropicMessagesService} produces.
 *
 * <p>Uses the instance's encrypted API key and a native base URL (the
 * {@code nativeBaseUrl} provider option, else {@code https://api.cohere.com/v2/chat}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurCohereMessagesService {

    private static final String DEFAULT_NATIVE_URL = "https://api.cohere.com/v2/chat";
    private static final String DEFAULT_MODEL = "command-r-plus";
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private final TurSecretCryptoService secretCryptoService;
    private final TurProviderOptionsParser optionsParser;
    private final TurCohereCitationSupport citationSupport;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    public TurCohereMessagesService(TurSecretCryptoService secretCryptoService,
            TurProviderOptionsParser optionsParser,
            TurCohereCitationSupport citationSupport) {
        this.secretCryptoService = secretCryptoService;
        this.optionsParser = optionsParser;
        this.citationSupport = citationSupport;
    }

    /**
     * Runs the native Cohere citation turn and streams the answer token + the
     * decoded {@code citations} SSE event. Fails the Flux with an error (logged)
     * on any transport/parse failure — the caller already gated this on the
     * citations option, so there is no silent fallback here.
     */
    public Flux<ChatResponse> chat(TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, List<TurCitationDocument> citationDocuments) {
        return Mono.fromCallable(() -> runTurn(instance, history, systemPrompt, citationDocuments))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(TurCohereMessagesService::emitTurn)
                .doOnError(err -> log.error("[Native][Cohere-Chat] instance '{}' error: {}",
                        instance.getId(), err.getMessage(), err));
    }

    /** The buffered result of a native Cohere turn: answer text + decoded citations. */
    private record TurnResult(String text, List<TurChatCitation> citations) {
    }

    private static Flux<ChatResponse> emitTurn(TurnResult result) {
        Flux<ChatResponse> events = Flux.just(new ChatResponse("assistant",
                StringUtils.hasText(result.text()) ? result.text() : "", null));
        if (result.citations() != null && !result.citations().isEmpty()) {
            try {
                String citationsJson = OBJECT_MAPPER.writeValueAsString(result.citations());
                events = events.concatWith(Flux.just(
                        new ChatResponse("assistant", citationsJson, "citations")));
            } catch (RuntimeException e) {
                log.warn("[Native][Cohere-Chat] could not serialize citations: {}", e.getMessage());
            }
        }
        return events;
    }

    private TurnResult runTurn(TurLLMInstance instance, List<ChatMessageItem> history,
            String systemPrompt, List<TurCitationDocument> citationDocuments) {
        Map<String, Object> options = optionsParser.parse(instance.getProviderOptionsJson());
        String apiKey = secretCryptoService.decrypt(instance.getApiKeyEncrypted());
        String url = resolveNativeUrl(options);
        String model = StringUtils.hasText(instance.getModelName())
                ? instance.getModelName() : DEFAULT_MODEL;

        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("model", model);
        ArrayNode messages = body.putArray("messages");
        if (StringUtils.hasText(systemPrompt)) {
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", systemPrompt);
        }
        for (ChatMessageItem item : history) {
            ObjectNode message = messages.addObject();
            message.put("role", "assistant".equals(item.role()) ? "assistant" : "user");
            message.put("content", item.content() == null ? "" : item.content());
        }
        if (!citationDocuments.isEmpty()) {
            body.set("documents", citationSupport.documentsArray(body, citationDocuments));
        }

        JsonNode root = postChat(url, apiKey, body);
        JsonNode messageNode = root.path("message");
        String text = extractText(messageNode);
        List<TurChatCitation> citations = citationSupport.decode(messageNode, citationDocuments);
        return new TurnResult(text, citations);
    }

    /** Concatenates {@code message.content[].text} into the answer string. */
    private static String extractText(JsonNode messageNode) {
        StringBuilder sb = new StringBuilder();
        JsonNode content = messageNode.path("content");
        if (content.isArray()) {
            for (JsonNode part : content) {
                JsonNode textNode = part.path("text");
                if (textNode.isString()) {
                    sb.append(textNode.asString());
                }
            }
        } else if (content.isString()) {
            sb.append(content.asString());
        }
        return sb.toString();
    }

    private JsonNode postChat(String url, String apiKey, ObjectNode body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Cohere v2/chat HTTP " + response.statusCode()
                        + ": " + response.body());
            }
            return OBJECT_MAPPER.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cohere v2/chat call interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cohere v2/chat call failed: " + e.getMessage(), e);
        }
    }

    /**
     * The native Cohere chat endpoint: the {@code nativeBaseUrl} provider option
     * when set, else the OpenAI-compat instance URL rewritten to native v2/chat,
     * else the documented default.
     */
    private String resolveNativeUrl(Map<String, Object> options) {
        String configured = optionsParser.stringValue(options, "nativeBaseUrl");
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        return DEFAULT_NATIVE_URL;
    }
}
