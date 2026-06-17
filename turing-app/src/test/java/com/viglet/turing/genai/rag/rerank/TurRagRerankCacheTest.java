/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.rerank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * T341 — verifies the {@code @Cacheable} memoization on {@link TurRagRerankCache}.
 *
 * <p>Boots a tiny annotation-config context with {@code @EnableCaching} + a
 * {@link ConcurrentMapCacheManager} (the dev/non-clustered cache manager) so the
 * Spring cache proxy is actually in the path — a plain {@code new} of the bean
 * would bypass it. Asserts that an identical key reuses the cached ordering (the
 * {@link Supplier} runs once) while a changed key recomputes.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurRagRerankCacheTest {

    @Configuration
    @EnableCaching
    static class Config {
        @Bean
        ConcurrentMapCacheManager cacheManager() {
            return new ConcurrentMapCacheManager("turRagRerankScore");
        }

        @Bean
        TurRagRerankCache rerankCache() {
            return new TurRagRerankCache();
        }
    }

    private AnnotationConfigApplicationContext context;
    private TurRagRerankCache cache;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        cache = context.getBean(TurRagRerankCache.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void identicalKeySkipsRecompute() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<List<String>> compute = () -> {
            calls.incrementAndGet();
            return List.of("d3", "d1");
        };

        List<String> first = cache.orderedIds("LLM", "gpt", "q", "hashA", 2, compute);
        List<String> second = cache.orderedIds("LLM", "gpt", "q", "hashA", 2, compute);

        assertThat(first).containsExactly("d3", "d1");
        assertThat(second).containsExactly("d3", "d1");
        assertThat(calls.get()).as("second identical call must hit the cache").isEqualTo(1);
    }

    @Test
    void differentCandidateHashRecomputes() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<List<String>> compute = () -> {
            calls.incrementAndGet();
            return List.of("d0");
        };

        cache.orderedIds("LLM", "gpt", "q", "hashA", 2, compute);
        cache.orderedIds("LLM", "gpt", "q", "hashB", 2, compute);

        assertThat(calls.get()).as("a changed candidate hash is a new key").isEqualTo(2);
    }

    @Test
    void differentStrategyOrModelRecomputes() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<List<String>> compute = () -> {
            calls.incrementAndGet();
            return List.of("d0");
        };

        cache.orderedIds("LLM", "gpt", "q", "hashA", 2, compute);
        cache.orderedIds("COHERE", "gpt", "q", "hashA", 2, compute);
        cache.orderedIds("COHERE", "rerank-v3", "q", "hashA", 2, compute);

        assertThat(calls.get()).as("strategy and model are part of the key").isEqualTo(3);
    }
}
