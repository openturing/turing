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
 * The T74 visitor cohort captured at session start: the request {@code locale},
 * the {@code timezone} (from the {@code X-Timezone} header) and the
 * {@code deviceType} (classified from the {@code User-Agent}). Off-request
 * callers (e.g. an {@code agent.invoke(...)} child session) pass an
 * {@link #empty()} instance, which records null cohort fields — correct, since a
 * child session has no visitor cohort of its own.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurChatVisitorContext(String locale, String timezone, String deviceType) {

    private static final TurChatVisitorContext EMPTY = new TurChatVisitorContext(null, null, null);

    /** Returns the shared all-null instance for off-request callers. */
    public static TurChatVisitorContext empty() {
        return EMPTY;
    }
}
