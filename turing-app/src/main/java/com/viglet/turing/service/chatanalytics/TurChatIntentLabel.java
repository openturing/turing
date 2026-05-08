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
 * Coarse categorisation of why a user opened the chat — assigned by
 * {@link TurChatIntentClassifier} from the conversation's transcript.
 *
 * <p>Kept small and stable so dashboards can pivot on it as a low-cardinality
 * tag. Add new values reluctantly; prefer reading {@code goal_summary} for
 * fine-grained insights.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public enum TurChatIntentLabel {
    /** User looking for help with an existing product/process. */
    SUPPORT,
    /** First-time user trying to get oriented. */
    ONBOARDING,
    /** Browsing/discovery, no immediate goal. */
    EXPLORATION,
    /** Negative feedback, expressing dissatisfaction. */
    COMPLAINT,
    /** Asking for facts/policies/dates. */
    INFORMATION_SEEKING,
    /** Showing buying signals (pricing, quote, contract). */
    CONVERSION_INTENT,
    /** Doesn't fit the others — read goal_summary for context. */
    OTHER,
    /** Classifier could not run yet (no transcript, LLM unavailable, etc.). */
    UNCLASSIFIED
}
