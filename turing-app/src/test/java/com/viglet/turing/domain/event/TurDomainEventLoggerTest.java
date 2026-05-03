/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.domain.event;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import com.viglet.turing.domain.llm.LlmInstanceUpdatedEvent;
import com.viglet.turing.domain.llm.LlmProviderType;
import com.viglet.turing.domain.sn.SnSiteIndexInvalidatedEvent;

/**
 * Smoke tests for the default domain-event listener. The listener's only side
 * effect is logging, so the test asserts it accepts each event type without
 * throwing — that's enough to catch obvious regressions (renamed fields,
 * missing accessors). Real listener behaviour (cache invalidation, reindex)
 * lives in dedicated listeners with their own test scope.
 */
class TurDomainEventLoggerTest {

    private final TurDomainEventLogger logger = new TurDomainEventLogger();

    @Test
    void onLlmInstanceUpdated_handlesCreatedEvent() {
        LlmInstanceUpdatedEvent event = LlmInstanceUpdatedEvent.created(
                "llm-1", LlmProviderType.of("openai"));

        assertThatCode(() -> logger.onLlmInstanceUpdated(event)).doesNotThrowAnyException();
    }

    @Test
    void onLlmInstanceUpdated_handlesUpdatedEvent() {
        LlmInstanceUpdatedEvent event = LlmInstanceUpdatedEvent.updated(
                "llm-1", LlmProviderType.of("anthropic"));

        assertThatCode(() -> logger.onLlmInstanceUpdated(event)).doesNotThrowAnyException();
    }

    @Test
    void onSnSiteIndexInvalidated_handlesCreatedEvent() {
        SnSiteIndexInvalidatedEvent event = SnSiteIndexInvalidatedEvent.created(
                "site-1", "site-name");

        assertThatCode(() -> logger.onSnSiteIndexInvalidated(event)).doesNotThrowAnyException();
    }

    @Test
    void onSnSiteIndexInvalidated_handlesUpdatedEvent() {
        SnSiteIndexInvalidatedEvent event = SnSiteIndexInvalidatedEvent.updated(
                "site-1", "site-name");

        assertThatCode(() -> logger.onSnSiteIndexInvalidated(event)).doesNotThrowAnyException();
    }
}
