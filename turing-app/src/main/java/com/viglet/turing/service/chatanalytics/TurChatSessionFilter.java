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
 * Optional equality filters for a recent-sessions query. Each field is matched
 * with {@code field == value} when non-blank and ignored when null/blank, so a
 * fully-null instance ({@link #none()}) means "no filtering". Bundled into one
 * cohesive record so {@code findRecentSessions} stays below the parameter
 * threshold instead of threading six loose strings through every store
 * implementation.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurChatSessionFilter(
        String agentId,
        String personaId,
        String outcome,
        String intentLabel,
        String goalAchieved,
        String sentiment) {

    private static final TurChatSessionFilter NONE =
            new TurChatSessionFilter(null, null, null, null, null, null);

    /** Returns the shared no-op filter (all fields null → match everything). */
    public static TurChatSessionFilter none() {
        return NONE;
    }
}
