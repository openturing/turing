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
package com.viglet.turing.service.chatanalytics;

/**
 * One tool-call latency observation captured during a chat session — the raw
 * material the T88 per-tool p95 view aggregates across sessions.
 *
 * <p>The session-level rollup ({@link TurChatSessionEvent#totalToolLatencyMs()})
 * sums every call's latency into a single counter, which only yields an
 * <em>average</em>. p95 (and any percentile) is not composable from per-session
 * sums, so the store keeps the individual samples and the dashboard pools them
 * at query time. A sample carries the {@code tool} name so the percentiles can
 * be disaggregated — the whole point of T88 is answering "<i>which</i> tool is
 * the p95 outlier?", not "what is the average across all tools?".
 *
 * @param tool      sanitized tool name (matches Spring AI's
 *                  {@code ToolDefinition.name()}); {@code "unknown"} when blank
 * @param latencyMs wall-clock duration of the {@code call(...)}, in ms
 *                  (never negative — clamped at capture)
 * @param success   {@code false} when the callback threw or returned an error
 *                  envelope
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurToolLatencySample(String tool, long latencyMs, boolean success) {

    /** Normalizes a blank/null tool name to {@code "unknown"} and clamps latency at 0. */
    public TurToolLatencySample {
        tool = (tool == null || tool.isBlank()) ? "unknown" : tool;
        latencyMs = Math.max(0L, latencyMs);
    }
}
