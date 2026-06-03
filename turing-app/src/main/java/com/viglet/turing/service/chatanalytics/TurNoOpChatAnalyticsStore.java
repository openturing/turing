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

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Drops everything on the floor. Used when {@code turing.logging.engine=none}
 * or the backing store is misconfigured. Reads always return empty.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public class TurNoOpChatAnalyticsStore implements TurChatAnalyticsStore {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public TurChatAnalyticsEngine getEngine() {
        return TurChatAnalyticsEngine.NONE;
    }

    @Override
    public void upsertSession(TurChatSessionEvent event) {
        // intentionally empty
    }

    @Override
    public List<Map<String, Object>> findRecentSessions(Instant from, Instant to,
            String agentId, String personaId, String outcome,
            String intentLabel, String goalAchieved, String sentiment,
            int limit) {
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> aggregate(Instant from, Instant to, String groupBy, int maxGroups) {
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> findUnenrichedSessions(int limit) {
        return Collections.emptyList();
    }

    @Override
    public void enrichSession(String conversationId, TurChatSessionEnrichment enrichment) {
        // intentionally empty
    }

    @Override
    public List<Map<String, Object>> timeseries(Instant from, Instant to, String metric, String interval) {
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> scorecard(Instant from, Instant to, String dimension,
            Map<String, String> filters, int maxRows) {
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> toolLatency(Instant from, Instant to, String agentId, int maxTools) {
        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> findSessionById(String conversationId) {
        return null;
    }

    @Override
    public long purgeOlderThan(Instant threshold) {
        return 0L;
    }
}
