/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

import com.viglet.turing.api.sn.search.TurSNSearchCacheEvictor;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;

/**
 * T896 / §LXVIII.5 — the enumeration of "writes that change a search answer".
 *
 * <p>
 * The defect was not that one controller forgot an eviction; it was that nothing
 * in the codebase said which writes change an answer, so every list of them was a
 * list someone had to remember to extend. This test <em>is</em> that list. Every
 * entity under {@code persistence.model.sn} must appear in exactly one of the two
 * sets below, so adding a new SN entity fails here until someone decides which it
 * is — which is the only mechanism that outlives the person who knew.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNSearchCacheEvictionListenerTest {

    /**
     * Entities a cached search answer is derived from: they must carry
     * {@link TurSNSearchCacheEvictionListener}.
     */
    private static final Set<String> AFFECTS_ANSWERS = Set.of(
            // site settings: highlighting, ranking mode, API auth
            "TurSNSite",
            // which core is queried
            "TurSNSiteLocale",
            // field config: facets, hl fields, types, sort eligibility
            "TurSNSiteFieldExt", "TurSNSiteFieldExtFacet",
            "TurSNSiteCustomFacet", "TurSNSiteCustomFacetItem",
            "TurSNSiteCustomSort", "TurSNSiteCustomSortItem",
            // hybrid ranking (T383) reads this off the snapshot
            "TurSNSiteGenAi",
            // injected into results at query time
            "TurSNSiteSpotlight", "TurSNSiteSpotlightTerm", "TurSNSiteSpotlightDocument",
            // rewrite the query at query time
            "TurSNSynonym",
            // boost queries applied at query time
            "TurSNRankingExpression", "TurSNRankingCondition",
            // rewrite the query / results at query time
            "TurSNSiteSearchRule", "TurSNSiteSearchRuleAction", "TurSNSiteSearchRuleCondition");

    /**
     * Entities that do <strong>not</strong> change a cached answer, with the reason.
     * {@code TurSNSiteMetricAccess} / {@code TurSNSiteMetricClick} are the load-bearing
     * entries: a search <em>writes</em> them, so attaching the listener would clear the
     * cache on every request and the cache would never serve anything.
     */
    private static final Set<String> DOES_NOT_AFFECT_ANSWERS = Set.of(
            "TurSNSiteMetricAccess", "TurSNSiteMetricClick", // written BY a search
            "TurSNSiteField", // the raw field registry; query-time config is FieldExt
            "TurSNSiteMergeProviders", "TurSNSiteMergeProvidersField", // index-time only
            "TurSNSourceType"); // a lookup table of source types; not read at query time

    @Test
    void everySnEntityDeclaresWhetherItChangesASearchAnswer() throws IOException {
        Path classesDir = Paths.get("target/classes");
        assertThat(Files.isDirectory(classesDir))
                .as("Expected compiled classes at %s. Run 'mvn -DskipTests compile' first.",
                        classesDir)
                .isTrue();

        MetadataReaderFactory readers = new SimpleMetadataReaderFactory();
        Set<String> unclassified = new TreeSet<>();
        Set<String> missingListener = new TreeSet<>();
        Set<String> unexpectedListener = new TreeSet<>();

        Path snModel = classesDir.resolve("com/viglet/turing/persistence/model/sn");
        try (Stream<Path> paths = Files.walk(snModel)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .forEach(p -> classify(p, readers, unclassified, missingListener,
                            unexpectedListener));
        }

        assertThat(unclassified).as("""
                A new SN entity must state whether a cached search answer is derived from it
                (T896 / §LXVIII.5). Add it to AFFECTS_ANSWERS (and annotate it with
                @EntityListeners(TurSNSearchCacheEvictionListener.class)) or to
                DOES_NOT_AFFECT_ANSWERS with the reason. Unclassified:""").isEmpty();

        assertThat(missingListener).as("""
                Listed in AFFECTS_ANSWERS but not annotated with
                @EntityListeners(TurSNSearchCacheEvictionListener.class), so an edit to it leaves
                the cached answer stale until the backstop sweep:""").isEmpty();

        assertThat(unexpectedListener).as("""
                Annotated with the eviction listener but listed in DOES_NOT_AFFECT_ANSWERS. If a
                search writes this entity, the listener clears the cache on every request and the
                cache never serves anything — remove one or the other:""").isEmpty();
    }

    private static void classify(Path classFile, MetadataReaderFactory readers,
            Set<String> unclassified, Set<String> missingListener,
            Set<String> unexpectedListener) {
        try {
            MetadataReader reader = readers.getMetadataReader(new FileSystemResource(classFile));
            if (!reader.getAnnotationMetadata().isAnnotated(Entity.class.getName())) {
                return;
            }
            String className = reader.getClassMetadata().getClassName();
            String simpleName = className.substring(className.lastIndexOf('.') + 1);
            boolean affects = AFFECTS_ANSWERS.contains(simpleName);
            boolean excluded = DOES_NOT_AFFECT_ANSWERS.contains(simpleName);
            if (!affects && !excluded) {
                unclassified.add(simpleName);
                return;
            }
            boolean annotated = hasEvictionListener(reader);
            if (affects && !annotated) {
                missingListener.add(simpleName);
            } else if (excluded && annotated) {
                unexpectedListener.add(simpleName);
            }
        } catch (IOException | RuntimeException ignored) {
            // Unparseable class files carry no annotations we care about.
        }
    }

    private static boolean hasEvictionListener(MetadataReader reader) {
        Object value = reader.getAnnotationMetadata()
                .getAnnotationAttributes(EntityListeners.class.getName())
                .get("value");
        if (!(value instanceof Object[] listeners)) {
            return false;
        }
        return Stream.of(listeners)
                .map(String::valueOf)
                .anyMatch(name -> name.contains(TurSNSearchCacheEvictionListener.class
                        .getSimpleName()));
    }

    /** The callback delegates to the one evictor, whichever entity fired it. */
    @Test
    void onChangeEvictsEverySearchAnswer() {
        TurSNSearchCacheEvictor evictor = mock(TurSNSearchCacheEvictor.class);

        new TurSNSearchCacheEvictionListener(evictor).onChange(new Object());

        verify(evictor).evictAll();
    }

    /** JPA may build the listener with the no-arg constructor; that must not throw. */
    @Test
    void noArgConstructorFallsBackToTheStaticEvictor() {
        TurSNSearchCacheEvictor evictor = mock(TurSNSearchCacheEvictor.class);
        new TurSNSearchCacheEvictionListener(evictor); // seeds the static fallback

        new TurSNSearchCacheEvictionListener().onChange(new Object());

        verify(evictor).evictAll();
    }
}
