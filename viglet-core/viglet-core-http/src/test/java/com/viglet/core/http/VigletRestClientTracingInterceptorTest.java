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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

class VigletRestClientTracingInterceptorTest {

    private final HttpRequest request = mock(HttpRequest.class);
    private final ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    private final ClientHttpResponse response = mock(ClientHttpResponse.class);
    private final Logger log = mock(Logger.class);

    @Test
    void passesThroughAndDoesNotLogWhenTraceDisabled() throws Exception {
        when(log.isTraceEnabled()).thenReturn(false);
        byte[] body = "{\"q\":1}".getBytes(StandardCharsets.UTF_8);
        when(execution.execute(request, body)).thenReturn(response);

        ClientHttpResponse result = new VigletRestClientTracingInterceptor(log).intercept(request, body, execution);

        assertThat(result).isSameAs(response);
        verify(execution).execute(request, body);
        verify(log, never()).trace(any(String.class), any(), any(), any(), any());
    }

    @Test
    void tracesRequestAndResponseWhenTraceEnabled() throws Exception {
        when(log.isTraceEnabled()).thenReturn(true);
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getURI()).thenReturn(URI.create("http://localhost:2700/api/sn"));
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        when(execution.execute(request, body)).thenReturn(response);
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        when(response.getBody()).thenReturn(new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)));

        ClientHttpResponse result = new VigletRestClientTracingInterceptor(log).intercept(request, body, execution);

        assertThat(result).isSameAs(response);
        verify(log, atLeastOnce()).trace(any(String.class), any(), any(), any(), any());
    }
}
