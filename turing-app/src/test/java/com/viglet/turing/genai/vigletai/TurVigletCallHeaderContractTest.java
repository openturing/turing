/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.vigletai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.genai.provider.llm.TurVigletLlmProvider;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.properties.TurVigletAiProperty;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * T970 / §LXXIX — the other half of the wire: the headers a managed {@code /v1}
 * call carries, observed on a request that really left.
 *
 * <p>T968 asserted the pairing hop from its bytes and left this one asserted as
 * string constants, which is a test that passes whether or not the transport
 * sends anything. Deleting the header line from either model used to leave the
 * whole suite green — and the failure it hides does not surface here, it
 * surfaces at Viglet's edge as a credential refusal the operator reads as an
 * outage.
 *
 * <p>A throwaway JDK {@link HttpServer} stands in for {@code llm.viglet.cloud},
 * matching the in-process style the project already uses for
 * {@code TurCustomToolHttpHelperAuthTest} — no MockWebServer or WireMock
 * dependency, and no production seam invented for a test. The identity service is
 * real, so what arrives is a genuinely signed assertion and the test can verify
 * it against the public half the pairing flow would have registered.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurVigletCallHeaderContractTest {

    @Mock
    private TurProviderOptionsParser optionsParser;
    @Mock
    private TurGlobalSettingsService settings;

    private HttpServer server;
    private TurVigletAiProperty property;
    private TurVigletLlmProvider provider;
    private String registeredPublicKey;

    private final AtomicReference<String> installHeader = new AtomicReference<>();
    private final AtomicReference<String> assertionHeader = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    /** Mirrors the config-var pair the real settings service persists. */
    private final AtomicReference<String> privateKey = new AtomicReference<>("");
    private final AtomicReference<String> publicKey = new AtomicReference<>("");

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", this::handle);
        server.start();

        property = new TurVigletAiProperty();
        property.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");

        lenient().when(settings.getVigletAiInstallPrivateKey()).thenAnswer(i -> privateKey.get());
        lenient().when(settings.getVigletAiInstallPublicKey()).thenAnswer(i -> publicKey.get());
        lenient().when(settings.getOrCreateVigletAiInstallId()).thenReturn("install-uuid");
        lenient().doAnswer(invocation -> {
            privateKey.set(invocation.getArgument(0));
            publicKey.set(invocation.getArgument(1));
            return null;
        }).when(settings).updateVigletAiInstallKeyPair(any(), any());

        TurVigletInstallIdentityService identity =
                new TurVigletInstallIdentityService(settings, property);
        // Pair the install, exactly as T957 would: after this the private half
        // exists and every call is expected to sign with it.
        registeredPublicKey = identity.getOrCreatePublicKey();

        lenient().when(optionsParser.parse(any())).thenReturn(Map.of());
        lenient().when(optionsParser.stringValue(any(), any())).thenReturn(null);
        lenient().when(optionsParser.doubleValue(any(), any())).thenReturn(null);
        lenient().when(optionsParser.intValue(any(), any())).thenReturn(null);

        provider = new TurVigletLlmProvider(optionsParser, property, identity);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** The SSE transport — ours, and the one the console's chat runs on. */
    @Test
    void theStreamingCallCarriesTheInstallAssertion() {
        ChatModel model = provider.createStreamingChatModel(instance(), "vk-secret");

        model.stream(new Prompt("hello")).blockLast();

        assertBoundCallArrived();
    }

    /**
     * The blocking transport — the SDK's, and the one the eval runner and the
     * weekly report use. The SDK may reject the stub's minimal answer; that is
     * irrelevant to what is asserted, which is that the request carried the
     * binding before any of that mattered.
     */
    @Test
    void theBlockingCallCarriesTheInstallAssertion() {
        ChatModel model = provider.createChatModel(instance(), "vk-secret");

        try {
            model.call(new Prompt("hello"));
        } catch (Exception acceptable) {
            // The answer shape is the SDK's business; the headers are ours.
        }

        assertBoundCallArrived();
    }

    /** Both transports must also still present the credential itself. */
    private void assertBoundCallArrived() {
        assertThat(authorization.get()).isEqualTo("Bearer vk-secret");
        assertThat(installHeader.get()).isEqualTo("install-uuid");

        String assertion = assertionHeader.get();
        assertThat(assertion).as("the T960 binding must reach the managed endpoint").isNotNull();

        String[] parts = assertion.split("\\.");
        assertThat(parts).hasSize(5);
        assertThat(parts[0]).isEqualTo("v1");
        assertThat(parts[1]).isEqualTo("install-uuid");
        assertThat(Long.parseLong(parts[2])).isGreaterThan(java.time.Instant.now().getEpochSecond());
        // Signed with the key pairing registered — not merely present and well shaped.
        assertThat(verifies(registeredPublicKey,
                TurVigletInstallIdentityService.payload("install-uuid", Long.parseLong(parts[2]),
                        parts[3]),
                parts[4])).isTrue();
    }

    private TurLLMInstance instance() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("managed-instance");
        return instance;
    }

    private void handle(HttpExchange exchange) throws IOException {
        installHeader.set(exchange.getRequestHeaders()
                .getFirst(TurVigletInstallIdentityService.INSTALL_HEADER));
        assertionHeader.set(exchange.getRequestHeaders()
                .getFirst(TurVigletInstallIdentityService.ASSERTION_HEADER));
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));

        boolean sse = String.valueOf(exchange.getRequestHeaders().getFirst("Accept"))
                .contains("text/event-stream");
        byte[] body = (sse ? "data: [DONE]\n\n" : """
                {"id":"cmpl-1","object":"chat.completion","created":1,"model":"viglet-fast",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"hi"},
                 "finish_reason":"stop"}]}
                """).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type",
                sse ? "text/event-stream" : "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static boolean verifies(String publicKeyBase64, String payload, String signatureB64Url) {
        try {
            PublicKey key = KeyFactory.getInstance(TurVigletInstallIdentityService.KEY_ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(
                            Base64.getDecoder().decode(publicKeyBase64)));
            Signature signature =
                    Signature.getInstance(TurVigletInstallIdentityService.KEY_ALGORITHM);
            signature.initVerify(key);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getUrlDecoder().decode(signatureB64Url));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
