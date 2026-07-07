/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.viglet.testsupport.cache.TurSNSiteConfigCacheTestConfig;

/**
 * T487 — verifies the {@code @Cacheable} memoization on {@link TurSNSiteConfigCache}
 * plus the {@link java.io.Serializable} contract the clustered (Hazelcast) cache
 * relies on.
 *
 * <p>Boots a tiny annotation-config context with {@code @EnableCaching} + a
 * {@link ConcurrentMapCacheManager} so the Spring cache proxy is in the path.
 * Asserts the same (lowercased) site name reuses the field-config read-model
 * (the {@link Supplier} runs once), a different name recomputes, and an unknown
 * site ({@code null}) is not pinned.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNSiteConfigCacheTest {

    private AnnotationConfigApplicationContext context;
    private TurSNSiteConfigCache cache;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TurSNSiteConfigCacheTestConfig.class);
        cache = context.getBean(TurSNSiteConfigCache.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    private static TurSNSiteFieldConfigModel sampleModel() {
        return new TurSNSiteFieldConfigModel("Sample", List.of(
                new TurSNSiteFieldConfigModel.FieldInfo("title", "text", false, false, "The title"),
                new TurSNSiteFieldConfigModel.FieldInfo("tags", "string", true, true, null)));
    }

    @Test
    void sameSiteNameSkipsReload() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<TurSNSiteFieldConfigModel> load = () -> {
            calls.incrementAndGet();
            return sampleModel();
        };

        TurSNSiteFieldConfigModel first = cache.fieldConfig("sample", load);
        TurSNSiteFieldConfigModel second = cache.fieldConfig("sample", load);

        assertThat(first).isNotNull();
        assertThat(second).isEqualTo(first);
        assertThat(calls.get()).as("second call for the same site name must hit the cache").isEqualTo(1);
    }

    @Test
    void differentSiteNameReloads() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<TurSNSiteFieldConfigModel> load = () -> {
            calls.incrementAndGet();
            return sampleModel();
        };

        cache.fieldConfig("sample", load);
        cache.fieldConfig("other", load);

        assertThat(calls.get()).as("a different site name is a new key").isEqualTo(2);
    }

    @Test
    void unknownSiteIsNotCached() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<TurSNSiteFieldConfigModel> load = () -> {
            calls.incrementAndGet();
            return null;
        };

        cache.fieldConfig("ghost", load);
        cache.fieldConfig("ghost", load);

        assertThat(calls.get()).as("an unknown site (null) must not be negatively pinned").isEqualTo(2);
    }

    @Test
    void modelIsSerializableForClusteredCache() throws Exception {
        TurSNSiteFieldConfigModel model = sampleModel();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(model);
        }
        TurSNSiteFieldConfigModel roundTripped;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            roundTripped = (TurSNSiteFieldConfigModel) ois.readObject();
        }

        assertThat(roundTripped).isEqualTo(model);
    }
}
