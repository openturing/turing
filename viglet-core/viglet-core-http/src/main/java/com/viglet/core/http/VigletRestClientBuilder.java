/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.http;

import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Assembles a Spring {@link RestClient.Builder} on a pooled httpclient5 request
 * factory, with an optional API-key default header and an optional
 * {@link VigletRestClientTracingInterceptor} — the shape Dumont's
 * {@code DumRestClientConfig} hand-rolls to talk to the Turing API.
 *
 * <p>When tracing is enabled the underlying request factory is wrapped in a
 * {@link BufferingClientHttpRequestFactory} so the interceptor can read the
 * response body without consuming it.
 *
 * <pre>{@code
 * RestClient client = VigletRestClientBuilder.create()
 *         .baseUrl(turingUrl)
 *         .apiKeyHeader("Key", turingApiKey)
 *         .tracing(true)
 *         .build();
 * }</pre>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class VigletRestClientBuilder {

    private VigletHttpClientSettings settings = VigletHttpClientSettings.defaults();
    private String baseUrl;
    private String apiKeyHeaderName;
    private String apiKeyHeaderValue;
    private boolean tracing;

    private VigletRestClientBuilder() {
    }

    public static VigletRestClientBuilder create() {
        return new VigletRestClientBuilder();
    }

    /** Pool/timeout settings for the backing httpclient5 client. */
    public VigletRestClientBuilder settings(VigletHttpClientSettings settings) {
        if (settings != null) {
            this.settings = settings;
        }
        return this;
    }

    /** Base URL prepended to relative request paths. */
    public VigletRestClientBuilder baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    /**
     * Default header injected on every request — typically the Turing API key
     * ({@code Key: <token>}). Ignored when either argument is blank.
     */
    public VigletRestClientBuilder apiKeyHeader(String name, String value) {
        this.apiKeyHeaderName = name;
        this.apiKeyHeaderValue = value;
        return this;
    }

    /** Enables {@code TRACE} request/response logging (and response buffering). */
    public VigletRestClientBuilder tracing(boolean tracing) {
        this.tracing = tracing;
        return this;
    }

    /** Builds the configured {@link RestClient}. */
    public RestClient build() {
        return builder().build();
    }

    /**
     * Builds the {@link RestClient.Builder} so a product can apply further
     * customizations before calling {@code build()}.
     */
    public RestClient.Builder builder() {
        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(VigletHttpClients.pooledClient(settings));

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(tracing ? new BufferingClientHttpRequestFactory(factory) : factory);

        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        if (isNotBlank(apiKeyHeaderName) && isNotBlank(apiKeyHeaderValue)) {
            builder.defaultHeader(apiKeyHeaderName, apiKeyHeaderValue);
        }
        if (tracing) {
            builder.requestInterceptor(new VigletRestClientTracingInterceptor());
        }
        return builder;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
