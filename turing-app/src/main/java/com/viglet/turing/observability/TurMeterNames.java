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
package com.viglet.turing.observability;

/**
 * Stable Micrometer/OpenTelemetry metric and span names. Treat these as a
 * public contract — dashboards, alerts and trace queries depend on them and
 * must be migrated when changed.
 *
 * <p>Naming follows OpenTelemetry semantic conventions: lower-case, dot
 * separators, plural for counters where appropriate.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public final class TurMeterNames {

    /** Timer for chat or embedding LLM calls. Tags: provider, operation, status. */
    public static final String LLM_CALLS = "turing.llm.calls";

    /** Counter for token usage. Tags: provider, model, direction (in|out). */
    public static final String LLM_TOKENS = "turing.llm.tokens";

    /** Timer for search engine plugin invocations. Tags: engine, operation, status. */
    public static final String SEARCH_CALLS = "turing.search.calls";

    // ---- Tag keys --------------------------------------------------------

    public static final String TAG_PROVIDER = "provider";
    public static final String TAG_MODEL = "model";
    public static final String TAG_OPERATION = "operation";
    public static final String TAG_STATUS = "status";
    public static final String TAG_DIRECTION = "direction";
    public static final String TAG_ENGINE = "engine";

    // ---- Tag values ------------------------------------------------------

    public static final String OP_CHAT = "chat";
    public static final String OP_STREAM = "stream";
    public static final String OP_EMBED = "embed";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_ERROR = "error";
    public static final String DIRECTION_IN = "in";
    public static final String DIRECTION_OUT = "out";

    private TurMeterNames() {}
}
