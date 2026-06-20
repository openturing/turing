/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.provider.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

/**
 * End-to-end coverage for {@link TurChromaStoreProvider} against a real Chroma
 * (Testcontainers, v2 tenant/database API): collection lifecycle, vector-store
 * creation + indexing, count, chunk listing. Each test uses its own collection.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurChromaStoreProviderIT {

    private static final GenericContainer<?> CHROMA =
            new GenericContainer<>(DockerImageName.parse("chromadb/chroma:0.5.23"))
                    .withExposedPorts(8000)
                    .waitingFor(Wait.forHttp("/api/v2/heartbeat").forStatusCode(200));

    private static final AtomicInteger SEQ = new AtomicInteger();

    private TurChromaStoreProvider provider;
    private TurStoreInstance instance;
    private String collection;

    @BeforeAll
    void startContainer() {
        CHROMA.start();
    }

    @AfterAll
    void stopContainer() {
        CHROMA.stop();
    }

    @BeforeEach
    void setUp() {
        provider = new TurChromaStoreProvider(new TurProviderOptionsParser());
        collection = "coll" + SEQ.incrementAndGet();
        instance = new TurStoreInstance();
        instance.setId("chroma-1");
        instance.setTitle("Chroma Test");
        instance.setUrl("http://" + CHROMA.getHost() + ":" + CHROMA.getMappedPort(8000));
        instance.setCollectionName(collection);
        instance.setProviderOptionsJson("{}");
    }

    private EmbeddingModel embeddingModel() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        lenient().when(model.embed(anyString())).thenAnswer(inv -> vectorFor(inv.getArgument(0)));
        lenient().when(model.dimensions()).thenReturn(8);
        return model;
    }

    private static float[] vectorFor(String text) {
        float[] v = new float[8];
        int h = text == null ? 0 : text.hashCode();
        for (int i = 0; i < v.length; i++) {
            v[i] = ((h >> i) & 1) == 1 ? 1f : 0.1f;
        }
        return v;
    }

    @Test
    void pluginTypeIsChroma() {
        assertThat(provider.getPluginType()).isEqualTo("chroma");
    }

    @Test
    void createListAndDeleteCollection() {
        provider.createCollection(instance, null, collection);
        assertThat(provider.listCollections(instance, null))
                .extracting(TurStoreCollectionInfo::name).contains(collection);

        provider.deleteCollection(instance, null, collection);
        assertThat(provider.listCollections(instance, null))
                .extracting(TurStoreCollectionInfo::name).doesNotContain(collection);
    }

    @Test
    void createVectorStoreAndQueryEmptyCollection() {
        provider.createCollection(instance, null, collection);
        // Exercises the ChromaVectorStore builder wiring (no add: the Spring AI
        // store's collection-id init is a managed-bean lifecycle step).
        VectorStore store = provider.createVectorStore(instance, embeddingModel(), null);
        assertThat(store).isNotNull();

        assertThat(provider.countDocuments(instance, null, collection)).isZero();
        TurStoreChunkPage chunkPage = provider.listChunks(instance, null, collection, 0, 10, Map.of());
        assertThat(chunkPage.chunks()).isEmpty();
    }
}
