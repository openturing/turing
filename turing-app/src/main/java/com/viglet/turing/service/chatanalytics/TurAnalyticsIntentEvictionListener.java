/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatanalytics;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.chatanalytics.TurAnalyticsIntent;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;

/**
 * T28 / §III.5 — JPA entity callback that fans out
 * {@link TurAnalyticsIntent} mutations to two consumers:
 *
 * <ol>
 *   <li>{@link TurLuceneIntentClassifier#evictAll()} — wipes the
 *       in-memory per-agent Lucene index cache so the Phase A strategy
 *       picks up edits without a restart;</li>
 *   <li>{@link TurAnalyticsIntentIndexer} — pushes the row to the
 *       SE-backed index (Solr/ES) so the Phase B strategy sees the
 *       same changes in its standalone index.</li>
 * </ol>
 *
 * <p>Same shape as the T27 {@code TurChatFlowRouterEvictionListener}:
 * static fallbacks cover the rare path where JPA instantiates the
 * listener through the no-arg constructor before Spring wires it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurAnalyticsIntentEvictionListener {

    private static final AtomicReference<TurLuceneIntentClassifier> classifierStatic = new AtomicReference<>();
    private static final AtomicReference<TurAnalyticsIntentIndexer> indexerStatic = new AtomicReference<>();

    private final TurLuceneIntentClassifier classifier;
    private final TurAnalyticsIntentIndexer indexer;

    public TurAnalyticsIntentEvictionListener() {
        this.classifier = null;
        this.indexer = null;
    }

    @Autowired
    public TurAnalyticsIntentEvictionListener(@Lazy TurLuceneIntentClassifier classifier,
            @Lazy TurAnalyticsIntentIndexer indexer) {
        this.classifier = classifier;
        this.indexer = indexer;
        classifierStatic.set(classifier);
        indexerStatic.set(indexer);
    }

    @PostPersist
    @PostUpdate
    public void onUpsert(TurAnalyticsIntent entity) {
        evictEmbeddedIndex();
        pushToSe(entity);
    }

    @PostRemove
    public void onRemove(TurAnalyticsIntent entity) {
        evictEmbeddedIndex();
        TurAnalyticsIntentIndexer target = indexer != null ? indexer : indexerStatic.get();
        if (target != null && entity != null && entity.getTurAIAgent() != null) {
            target.removeRow(entity.getId(), entity.getTurAIAgent().getId());
        }
    }

    private void evictEmbeddedIndex() {
        TurLuceneIntentClassifier target = classifier != null ? classifier : classifierStatic.get();
        if (target != null) {
            target.evictAll();
        }
    }

    private void pushToSe(TurAnalyticsIntent entity) {
        TurAnalyticsIntentIndexer target = indexer != null ? indexer : indexerStatic.get();
        if (target != null && entity != null) {
            target.indexRow(entity);
        }
    }
}
