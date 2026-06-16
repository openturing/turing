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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

/**
 * {@link ClientHttpRequestInterceptor} that trace-logs the outbound request and
 * inbound response (method, URI, headers and body) when {@code TRACE} is
 * enabled — the diagnostics aid lifted from Dumont's {@code DumRestClientConfig}.
 *
 * <p>Reading the response body consumes the stream, so this interceptor only
 * works on a {@link org.springframework.web.client.RestClient} whose request
 * factory buffers responses — {@link VigletRestClientBuilder} wires the
 * buffering factory automatically. Everything is guarded by
 * {@link Logger#isTraceEnabled()}, so the cost is nil when tracing is off.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class VigletRestClientTracingInterceptor implements ClientHttpRequestInterceptor {

    private final Logger log;

    /** Logs under this class's category. */
    public VigletRestClientTracingInterceptor() {
        this(LoggerFactory.getLogger(VigletRestClientTracingInterceptor.class));
    }

    /** Logs under the supplied logger — lets a product trace under its own category. */
    public VigletRestClientTracingInterceptor(Logger log) {
        this.log = log;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (log.isTraceEnabled()) {
            log.trace("HTTP --> {} {} headers={} body={}", request.getMethod(), request.getURI(),
                    request.getHeaders(), new String(body, StandardCharsets.UTF_8));
        }
        ClientHttpResponse response = execution.execute(request, body);
        if (log.isTraceEnabled()) {
            log.trace("HTTP <-- {} {} headers={} body={}", response.getStatusCode(), request.getURI(),
                    response.getHeaders(), readBody(response));
        }
        return response;
    }

    private String readBody(ClientHttpResponse response) {
        try {
            return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<unreadable: " + e.getMessage() + ">";
        }
    }
}
