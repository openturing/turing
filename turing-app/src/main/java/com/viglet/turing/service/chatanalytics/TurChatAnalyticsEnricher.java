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

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.viglet.turing.observability.TurMeterNames;
import com.viglet.turing.service.chatmemory.TurChatMemoryStore;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Periodic worker that picks up finished but unclassified sessions from
 * {@link TurChatAnalyticsStore#findUnenrichedSessions(int)}, asks
 * {@link TurChatIntentClassifier} to label them, and writes the annotation
 * back via {@link TurChatAnalyticsStore#enrichSession}. Runs on a single node
 * at a time thanks to ShedLock — the existing
 * {@code TurSchedulerLockConfig} provides the lock provider.
 *
 * <p>Tunables (all properties optional):
 * <ul>
 *   <li>{@code turing.chat.analytics.enrich.cron} — cron, default every 5
 *       minutes.</li>
 *   <li>{@code turing.chat.analytics.enrich.batch-size} — max sessions per
 *       cycle, default 20. Keep small so a slow LLM doesn't starve the lock.</li>
 *   <li>{@code turing.chat.analytics.enrich.transcript-messages} — how many
 *       most-recent messages to feed the classifier, default 30.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@Service
public class TurChatAnalyticsEnricher {

    private final TurChatAnalyticsService analyticsService;
    private final TurChatMemoryStore chatMemoryStore;
    private final TurChatIntentClassifier classifier;
    private final MeterRegistry meterRegistry;
    private final int batchSize;
    private final int transcriptMessages;

    public TurChatAnalyticsEnricher(TurChatAnalyticsService analyticsService,
            TurChatMemoryStore chatMemoryStore,
            TurChatIntentClassifier classifier,
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Value("${turing.chat.analytics.enrich.batch-size:20}") int batchSize,
            @Value("${turing.chat.analytics.enrich.transcript-messages:30}") int transcriptMessages) {
        this.analyticsService = analyticsService;
        this.chatMemoryStore = chatMemoryStore;
        this.classifier = classifier;
        this.meterRegistry = meterRegistry;
        this.batchSize = batchSize;
        this.transcriptMessages = transcriptMessages;
    }

    /**
     * Default cron: every 5 minutes. Override via
     * {@code turing.chat.analytics.enrich.cron}. The ShedLock name is
     * stable so a rolling deploy doesn't double-classify in the seconds
     * after the new node takes over.
     */
    @Scheduled(cron = "${turing.chat.analytics.enrich.cron:0 */5 * * * *}")
    @SchedulerLock(name = "chatAnalyticsEnricher",
            lockAtMostFor = "PT15M", lockAtLeastFor = "PT1S")
    public void run() {
        Timer.Sample sample = meterRegistry == null ? null : Timer.start(meterRegistry);
        String status;
        if (!analyticsService.isEnabled()) {
            status = "skipped";
            recordCycle(sample, status);
            return;
        }
        if (!classifier.isAvailable()) {
            log.debug("[ChatEnricher] default LLM unavailable — skipping cycle");
            recordCycle(sample, "skipped");
            return;
        }
        TurChatAnalyticsStore store = analyticsService.getStore();
        List<Map<String, Object>> batch = store.findUnenrichedSessions(batchSize);
        if (batch.isEmpty()) {
            recordCycle(sample, "empty");
            return;
        }

        log.info("[ChatEnricher] processing {} session(s)", batch.size());
        int classified = 0;
        int unclassified = 0;
        long startNanos = System.nanoTime();
        for (Map<String, Object> row : batch) {
            String conversationId = stringOf(row.get("conversationId"));
            if (conversationId == null || conversationId.isBlank()) continue;

            String firstUserMessage = stringOf(row.get("firstUserMessage"));
            String agentId = stringOf(row.get("agentId"));
            List<Map<String, Object>> messages =
                    chatMemoryStore.findMessages(conversationId, transcriptMessages);

            // T28: threads agentId so the orchestrator can route to the
            // per-agent intent catalog when the Lucene/ES strategies are
            // active. Legacy rows without agentId still classify through
            // the LLM strategy (which ignores agentId).
            TurChatSessionEnrichment enrichment = classifier.classify(agentId, firstUserMessage, messages);
            store.enrichSession(conversationId, enrichment);
            if (enrichment.intentLabel() == TurChatIntentLabel.UNCLASSIFIED) {
                unclassified++;
            } else {
                classified++;
            }
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("[ChatEnricher] cycle done — processed={} classified={} unclassified={} elapsedMs={}",
                batch.size(), classified, unclassified, elapsedMs);
        recordCycle(sample, "success");
    }

    private void recordCycle(Timer.Sample sample, String status) {
        if (meterRegistry == null || sample == null) return;
        sample.stop(Timer.builder(TurMeterNames.CHAT_ANALYTICS_ENRICH_CYCLE)
                .tag(TurMeterNames.TAG_STATUS, status)
                .register(meterRegistry));
    }

    private static String stringOf(Object value) {
        return value == null ? null : value.toString();
    }
}
