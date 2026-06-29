/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.chatanalytics;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.chatanalytics.TurAnalyticsIntent;

/**
 * T28 / §III.5 — JPA repository for {@link TurAnalyticsIntent}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public interface TurAnalyticsIntentRepository extends JpaRepository<TurAnalyticsIntent, String> {

    /**
     * Hot path called once per enricher cycle per agent.
     */
    List<TurAnalyticsIntent> findByTurAIAgent_IdAndEnabledOrderByLabelAsc(String agentId,
            int enabled);

    /**
     * Admin-listing variant — returns enabled + disabled rows. This powers
     * the admin UI which is low-traffic compared to the classifier hot path.
     */
    List<TurAnalyticsIntent> findByTurAIAgent_IdOrderByLabelAsc(String agentId);
}
