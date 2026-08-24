/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.spring;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

/**
 * T1004 — the interceptor that keeps a proxy from buffering an SSE response.
 *
 * <p>What is worth asserting is the decision, not the two setHeader calls: it
 * has to fire on a streaming handler however that handler spells its mapping,
 * and stay off everything else, since {@code no-cache} on every JSON response
 * would be a quiet change to an unrelated contract.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSseNoBufferingInterceptorTest {

    private final TurSseNoBufferingInterceptor interceptor = new TurSseNoBufferingInterceptor();

    @RestController
    static class StreamingController {
        @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        public void stream() {
            // mapping-only fixture
        }

        @GetMapping("/plain")
        public void plain() {
            // mapping-only fixture
        }

        @GetMapping(value = "/json", produces = MediaType.APPLICATION_JSON_VALUE)
        public void json() {
            // mapping-only fixture
        }
    }

    /** A controller that declares the media type once, at the type level. */
    @RestController
    @RequestMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    static class TypeLevelStreamingController {
        @GetMapping("/inherited")
        public void inherited() {
            // mapping-only fixture
        }
    }

    private static HandlerMethod handler(Class<?> type, String method) throws NoSuchMethodException {
        Method target = type.getDeclaredMethod(method);
        return new HandlerMethod(org.mockito.Mockito.mock(type), target);
    }

    @Test
    @DisplayName("a handler declaring text/event-stream is recognised")
    void recognisesStreamingHandler() throws Exception {
        assertTrue(TurSseNoBufferingInterceptor
                .producesEventStream(handler(StreamingController.class, "stream")));
    }

    @Test
    @DisplayName("produces declared once on the controller counts for its methods")
    void recognisesTypeLevelDeclaration() throws Exception {
        assertTrue(TurSseNoBufferingInterceptor
                .producesEventStream(handler(TypeLevelStreamingController.class, "inherited")));
    }

    @Test
    @DisplayName("a handler with no produces, or a JSON one, is not")
    void ignoresNonStreamingHandlers() throws Exception {
        assertFalse(TurSseNoBufferingInterceptor
                .producesEventStream(handler(StreamingController.class, "plain")));
        assertFalse(TurSseNoBufferingInterceptor
                .producesEventStream(handler(StreamingController.class, "json")));
    }

    @Test
    @DisplayName("a non-HandlerMethod (static resource, forward) is not")
    void ignoresNonHandlerMethod() {
        assertFalse(TurSseNoBufferingInterceptor.producesEventStream(new Object()));
        assertFalse(TurSseNoBufferingInterceptor.producesEventStream(null));
    }

    @Test
    @DisplayName("both headers are set on a streaming response, before the handler runs")
    void setsHeadersOnStreamingResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(new MockHttpServletRequest(), response,
                handler(StreamingController.class, "stream"));

        assertTrue(proceed, "the interceptor must never short-circuit the request");
        assertEquals("no", response.getHeader(TurSseNoBufferingInterceptor.X_ACCEL_BUFFERING));
        assertEquals("no-cache", response.getHeader("Cache-Control"));
        assertFalse(response.isCommitted(), "headers must land before anything is written");
    }

    @Test
    @DisplayName("the configuration actually registers it — an interceptor nothing installs is inert")
    void configurationRegistersTheInterceptor() {
        InterceptorRegistry registry = new InterceptorRegistry();

        new TurSseStreamingConfiguration().addInterceptors(registry);

        List<?> registered = org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(registry, "getInterceptors");
        assertNotNull(registered);
        assertEquals(1, registered.size());
        assertTrue(registered.stream()
                .anyMatch(i -> i instanceof TurSseNoBufferingInterceptor
                        || (i instanceof MappedInterceptor mapped
                                && mapped.getInterceptor() instanceof TurSseNoBufferingInterceptor)),
                "TurSseNoBufferingInterceptor must be among the registered interceptors");
    }

    @Test
    @DisplayName("a JSON response keeps the caching contract it already had")
    void leavesNonStreamingResponseAlone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(new MockHttpServletRequest(), response,
                handler(StreamingController.class, "json"));

        assertNull(response.getHeader(TurSseNoBufferingInterceptor.X_ACCEL_BUFFERING));
        assertNull(response.getHeader("Cache-Control"));
    }
}
