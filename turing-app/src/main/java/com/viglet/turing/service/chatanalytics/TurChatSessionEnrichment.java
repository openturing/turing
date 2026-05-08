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
import java.util.List;

/**
 * AI-on-AI annotation produced by {@link TurChatIntentClassifier}, applied
 * post-hoc to a finished session. Persisted onto the same document the
 * {@link TurChatSessionEvent} writes; never modifies the start/end timestamps
 * or aggregate counters.
 *
 * <p>{@code processedAt} is the watermark that lets the scheduled enricher
 * find unprocessed sessions — it's set by the store on success and never
 * cleared.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public record TurChatSessionEnrichment(
        TurChatIntentLabel intentLabel,
        double intentConfidence,
        String goalSummary,
        TurChatGoalAchieved goalAchieved,
        TurChatSentiment sentiment,
        List<String> keyTerms,
        Instant processedAt) {

    /** Used by the enricher when the LLM is unavailable; marks the session
     *  processed so it isn't retried in a tight loop, but with UNCLASSIFIED. */
    public static TurChatSessionEnrichment unclassified() {
        return new TurChatSessionEnrichment(
                TurChatIntentLabel.UNCLASSIFIED, 0.0, null,
                TurChatGoalAchieved.UNKNOWN, TurChatSentiment.UNKNOWN,
                List.of(), Instant.now());
    }
}
