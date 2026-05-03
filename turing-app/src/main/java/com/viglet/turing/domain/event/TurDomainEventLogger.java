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
package com.viglet.turing.domain.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.viglet.turing.domain.llm.LlmInstanceUpdatedEvent;
import com.viglet.turing.domain.sn.SnSiteIndexInvalidatedEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Default listener for domain events — logs every event at INFO level so
 * operations teams have an audit trail without bespoke code in each
 * publisher. Real side effects (cache invalidation, reindex scheduling,
 * webhook fan-out) belong in dedicated listeners that can be added
 * incrementally without touching this class.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Slf4j
@Component
public class TurDomainEventLogger {

    @EventListener
    public void onLlmInstanceUpdated(LlmInstanceUpdatedEvent event) {
        log.info("[DomainEvent] LlmInstance {} {} (provider={}) at {}",
                event.kind(), event.instanceId(), event.providerType(), event.occurredAt());
    }

    @EventListener
    public void onSnSiteIndexInvalidated(SnSiteIndexInvalidatedEvent event) {
        log.info("[DomainEvent] SnSite index invalidated: id={}, name={}, reason={} at {}",
                event.siteId(), event.siteName(), event.reason(), event.occurredAt());
    }
}
