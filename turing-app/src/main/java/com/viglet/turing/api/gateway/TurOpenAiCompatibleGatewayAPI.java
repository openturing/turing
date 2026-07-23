/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.gateway.TurGatewayGuardrailException;
import com.viglet.turing.genai.gateway.TurGatewayLimitException;
import com.viglet.turing.genai.gateway.TurGatewayLlmService;
import com.viglet.turing.genai.gateway.TurGatewayOptions;
import com.viglet.turing.genai.gateway.TurOpenAiWire.ChatCompletionRequest;
import com.viglet.turing.genai.gateway.TurOpenAiWire.EmbeddingRequest;
import com.viglet.turing.genai.gateway.TurOpenAiWire.ErrorEnvelope;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * T740 / §XLIX — OpenAI-compatible inbound surface for the Governed LLM Gateway
 * (Block AZ). A stock OpenAI client points its {@code base_url} at
 * {@code <turing>/v1} and calls {@code /chat/completions} (blocking or streaming
 * via {@code "stream": true}), {@code /embeddings} and {@code /models} unchanged.
 *
 * <p>The whole controller is {@code @ConditionalOnProperty(turing.gateway.enabled)}
 * — when the gateway is off (the default) these routes are not registered, so no
 * existing behaviour or integration test is affected. Wire translation and the
 * delegation to the existing resilient provider factory live in
 * {@link TurGatewayLlmService}; this class is transport + OpenAI-shaped error
 * envelopes only.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@ConditionalOnProperty(prefix = "turing.gateway", name = "enabled", havingValue = "true")
@Tag(name = "LLM Gateway", description = "OpenAI-compatible governed egress (Block AZ)")
public class TurOpenAiCompatibleGatewayAPI {

    private final TurGatewayLlmService gatewayService;

    public TurOpenAiCompatibleGatewayAPI(TurGatewayLlmService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @Operation(summary = "OpenAI-compatible chat completions (blocking or SSE streaming)")
    @PostMapping(value = "/chat/completions", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<?> chatCompletions(@RequestBody ChatCompletionRequest request,
            @RequestHeader(name = "x-turing-cache", required = false) String cacheHeader,
            @RequestHeader(name = "x-turing-guardrails", required = false) String guardrailsHeader,
            @RequestHeader(name = "x-turing-rag-site", required = false) String ragSiteHeader,
            @RequestHeader(name = "x-turing-tools", required = false) String toolsHeader,
            @RequestHeader(name = "x-turing-copilot-site", required = false) String copilotSiteHeader) {
        String username = resolveUsername();
        TurGatewayOptions options = TurGatewayOptions.from(cacheHeader, guardrailsHeader, ragSiteHeader,
                toolsHeader, copilotSiteHeader);
        try {
            if (Boolean.TRUE.equals(request.stream())) {
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(gatewayService.stream(request, username, options));
            }
            return ResponseEntity.ok(gatewayService.complete(request, username, options));
        } catch (TurGatewayLimitException e) {
            return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), e.getType(), e.getType());
        } catch (TurGatewayGuardrailException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage(), "content_filter", "content_filter");
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage(), "invalid_request_error", "model_not_found");
        } catch (RuntimeException e) {
            log.warn("[Gateway] chat/completions failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, e.getMessage(), "api_error", null);
        }
    }

    @Operation(summary = "OpenAI-compatible embeddings")
    @PostMapping(value = "/embeddings", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> embeddings(@RequestBody EmbeddingRequest request) {
        try {
            return ResponseEntity.ok(gatewayService.embed(request));
        } catch (TurGatewayLimitException e) {
            return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), e.getType(), e.getType());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage(), "invalid_request_error", "model_not_found");
        } catch (RuntimeException e) {
            log.warn("[Gateway] embeddings failed: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, e.getMessage(), "api_error", null);
        }
    }

    @Operation(summary = "OpenAI-compatible model listing")
    @GetMapping(value = "/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> models() {
        return ResponseEntity.ok(gatewayService.listModels());
    }

    private ResponseEntity<ErrorEnvelope> error(HttpStatus status, String message, String type, String code) {
        return ResponseEntity.status(status).body(ErrorEnvelope.of(message, type, code));
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "gateway";
    }
}
