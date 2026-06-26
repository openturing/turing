/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * E.8 / §IX.9 — observability interop configuration ({@code turing.observability.*}).
 *
 * <p>Two opt-in, fail-open trace exports that let customers keep the dashboards
 * they already invested in instead of abandoning them to adopt Turing:
 *
 * <ul>
 *   <li><b>{@link Spans spans}</b> (T129) — emit the chat-pipeline stage timers
 *   as OpenTelemetry spans (through the existing micrometer-tracing OTel bridge),
 *   exported to any OTLP backend (Honeycomb, Datadog, Jaeger). Off by default;
 *   the underlying timers are unaffected either way.</li>
 *   <li><b>{@link LangSmith langsmith}</b> (T128) — mirror each completed chat
 *   turn as a run in a LangSmith project via their public ingest API, so Turing
 *   traces appear side-by-side with a customer's LangChain agents.</li>
 * </ul>
 *
 * <p>Strategic principle (§IX.9): lock customers in via <em>capability</em>,
 * never via <em>format</em> — the trace export is deliberately replaceable.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
public class TurObservabilityProperty {

    private Spans spans = new Spans();
    private LangSmith langsmith = new LangSmith();

    /**
     * T129 — chat-pipeline OpenTelemetry span export. When {@code enabled},
     * {@link com.viglet.turing.observability.TurChatPipelineObservation#record}
     * wraps each stage in a Micrometer {@code Observation}, producing an OTel
     * span (via the bridge) in addition to the existing timer metric.
     */
    @Getter
    @Setter
    public static class Spans {
        /** Emit chat-pipeline stages as OTel spans. Default {@code false}. */
        private boolean enabled = false;
    }

    /**
     * T128 — LangSmith run mirror. A disabled exporter (or a blank
     * {@link #apiKey}) is a no-op: nothing is sent and no thread is spent.
     */
    @Getter
    @Setter
    public static class LangSmith {
        /** Mirror chat turns to LangSmith. Default {@code false}. */
        private boolean enabled = false;
        /** LangSmith API key sent as the {@code x-api-key} header. */
        private String apiKey;
        /** LangSmith ingest base URL (no trailing slash). */
        private String apiUrl = "https://api.smith.langchain.com";
        /** LangSmith project (a.k.a. {@code session_name}) runs are grouped under. */
        private String project = "turing";
    }
}
