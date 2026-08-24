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

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * T1004 — tells every proxy in front of Turing not to hold an SSE response
 * back.
 *
 * <p>A streaming endpoint that emits its events as they happen is only half the
 * contract: the events still have to reach the browser as they happen. A
 * reverse proxy that buffers reads the whole response before forwarding any of
 * it, which turns a token-by-token stream back into one delivery at the end —
 * indistinguishable, from the consumer's side, from a server that never
 * streamed at all.
 *
 * <p>Measured against {@code turing-demo.viglet.org} on 2026-08-24, which sits
 * behind Cloudflare: the SN chat's {@code sources} event is emitted before the
 * LLM call and its {@code token} event seconds later, yet both arrived 2ms
 * apart — and the response headers arrived with them, 5s in. Nothing had been
 * forwarded until the backend was finished.
 *
 * <p>The two headers are the standard remedy and are set on the response before
 * the handler runs, so they are in place when the stream commits:
 *
 * <ul>
 *   <li>{@code X-Accel-Buffering: no} — the explicit opt-out honoured by nginx
 *       and by proxies that follow it, Cloudflare included.</li>
 *   <li>{@code Cache-Control: no-cache} — keeps an intermediary from holding or
 *       replaying a stream that is per-turn by nature.</li>
 * </ul>
 *
 * <p>The interceptor applies to any handler declaring {@code produces =
 * text/event-stream}, so it covers the SSE endpoints that exist today (chat,
 * transcription, import, marketplace, persona, research) and any added later
 * without each one having to remember.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurSseNoBufferingInterceptor implements HandlerInterceptor {

    /**
     * Non-standard but widely honoured: nginx documents it as the per-response
     * opt-out from {@code proxy_buffering}, and proxies modelled on it read the
     * same header. Harmless where it is not understood.
     */
    static final String X_ACCEL_BUFFERING = "X-Accel-Buffering";

    static final String NO = "no";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) {
        if (producesEventStream(handler)) {
            response.setHeader(X_ACCEL_BUFFERING, NO);
            response.setHeader(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-cache");
        }
        return true;
    }

    /**
     * Whether this handler declares {@code produces = text/event-stream}.
     *
     * <p>Reads the merged {@link RequestMapping} so the shorthand annotations
     * ({@code @PostMapping}, {@code @GetMapping}) are covered too, and falls
     * back to the type-level declaration for a controller that sets
     * {@code produces} once for all of its methods.
     *
     * <p>Package-visible so the decision can be asserted without standing up a
     * dispatcher.
     */
    static boolean producesEventStream(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }
        return declaresEventStream(AnnotatedElementUtils
                .findMergedAnnotation(handlerMethod.getMethod(), RequestMapping.class))
                || declaresEventStream(AnnotatedElementUtils
                        .findMergedAnnotation(handlerMethod.getBeanType(), RequestMapping.class));
    }

    private static boolean declaresEventStream(RequestMapping mapping) {
        if (mapping == null) {
            return false;
        }
        for (String produces : mapping.produces()) {
            if (produces != null && produces.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)) {
                return true;
            }
        }
        return false;
    }
}
