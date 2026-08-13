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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode;
import com.viglet.turing.persistence.model.sn.genai.TurSNRankingMode;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;

/**
 * T954 / §LXIX.30 — the {@link java.io.Serializable} contract the clustered
 * (Hazelcast) cache relies on, for the largest cached value in the product.
 *
 * <p>{@code TurCacheConfig} returns a Hazelcast-backed {@code CacheManager}
 * whenever a {@code HazelcastInstance} is present, so a cached value that does
 * not survive Java serialization fails in a clustered deployment and nowhere
 * else. {@code TurSNSiteConfigCacheTest} and {@code TurChatFlowGraphCacheTest}
 * already pin that for their own values; this one had none, and Block AC
 * (T898b–T898e, T953) has just replaced all six of its components with records,
 * so every byte in that cache is new.
 *
 * <p>The failure mode is why this is worth a test rather than a review: a
 * single-node install uses {@code ConcurrentMapCacheManager}, which never
 * serializes anything, so a non-serializable component added later passes every
 * suite and every local run.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNSiteSearchSnapshotSerializationTest {

    private static TurSNSiteSearchSnapshot populated() {
        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        site.setName("intranet");
        site.setHl(1);
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setId("genai-1");
        genAi.setKnowledgeBaseMode(TurSNKnowledgeBaseMode.VECTOR);
        genAi.setEmbeddingModelId("emb-1");
        genAi.setSnRankingMode(TurSNRankingMode.HYBRID_RRF);
        site.setTurSNSiteGenAi(genAi);

        return new TurSNSiteSearchSnapshot(
                TurSNSiteSearchSiteView.from(site, "solr"), Locale.US, "solr", true,
                List.of(new TurSNSiteSearchSnapshot.FieldExtView("f1", "ext", "category",
                        "desc", "Category", null, null, null, null, 1, true, false, null,
                        null, 0, 1, 0, 0, 1, 0, "dv")),
                Set.of("f1"),
                Map.of("f1", List.of(new TurSNSiteSearchSnapshot.CustomFacetView("cf1", "brand"))),
                Map.of("f1", Set.of(new TurSNSiteSearchSnapshot.FacetLabelView(Locale.US, "Label"))),
                List.of(new TurSNSiteSearchSnapshot.LocaleView(Locale.US, "site_en", 0)),
                List.of(new TurSNSiteSearchSnapshot.CustomSortView("s1", "newest", "Newest")));
    }

    private static TurSNSiteSearchSnapshot roundTrip(TurSNSiteSearchSnapshot snapshot)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(snapshot);
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (TurSNSiteSearchSnapshot) in.readObject();
        }
    }

    /**
     * Every component populated, because an empty collection serializes whatever
     * its element type is — a check on an empty snapshot would prove nothing.
     */
    @Test
    void aFullyPopulatedSnapshotSurvivesJavaSerialization() throws Exception {
        TurSNSiteSearchSnapshot back = roundTrip(populated());

        assertThat(back.siteName()).isEqualTo("intranet");
        assertThat(back.siteId()).isEqualTo("site-1");
        assertThat(back.pluginType()).isEqualTo("solr");
        assertThat(back.locale()).isEqualTo(Locale.US);
        assertThat(back.hasHlFields()).isTrue();
        assertThat(back.enabledFields()).singleElement()
                .satisfies(field -> assertThat(field.name()).isEqualTo("category"));
        assertThat(back.fieldIdsWithCustomFacets()).containsExactly("f1");
        assertThat(back.customFacetsByFieldId().get("f1")).singleElement()
                .satisfies(facet -> assertThat(facet.name()).isEqualTo("brand"));
        assertThat(back.facetLabelsByFieldId().get("f1")).singleElement()
                .satisfies(label -> assertThat(label.label()).isEqualTo("Label"));
        assertThat(back.allLocales()).singleElement()
                .satisfies(locale -> assertThat(locale.core()).isEqualTo("site_en"));
        assertThat(back.customSorts()).singleElement()
                .satisfies(sort -> assertThat(sort.name()).isEqualTo("newest"));
    }

    /**
     * The site is rebuilt from the deserialized view, so what the collaborators
     * receive after a cache round-trip has to be what they received before it —
     * including the two associations denormalised into the view.
     */
    @Test
    void theRebuiltSiteIsIntactAfterTheRoundTrip() throws Exception {
        TurSNSite back = roundTrip(populated()).site();

        assertThat(back.getId()).isEqualTo("site-1");
        assertThat(back.getName()).isEqualTo("intranet");
        assertThat(back.getHl()).isEqualTo(1);
        // TurSNHybridRankingService reads these three off the site.
        assertThat(back.getTurSNSiteGenAi().getKnowledgeBaseMode())
                .isEqualTo(TurSNKnowledgeBaseMode.VECTOR);
        assertThat(back.getTurSNSiteGenAi().getEmbeddingModelId()).isEqualTo("emb-1");
        assertThat(back.getTurSNSiteGenAi().getSnRankingMode())
                .isEqualTo(TurSNRankingMode.HYBRID_RRF);
        // getPluginForSite reads the vendor's plugin off the rebuilt SE instance.
        assertThat(back.getTurSEInstance().getTurSEVendor().getPlugin()).isEqualTo("solr");
    }

    /**
     * What the cache stores is the <em>unwrapped</em> value, not the
     * {@code Optional} the method returns — and that distinction is load-bearing,
     * because {@code java.util.Optional} is not {@code Serializable}. Spring's
     * {@code CacheAspectSupport} runs the return value through
     * {@code ObjectUtils.unwrapOptional} before the put and re-wraps it on the
     * read, so the snapshot itself is what has to serialize, and
     * {@code Optional.empty()} unwraps to {@code null}, which is how the absent
     * site is negatively cached.
     *
     * <p>Pinned because the opposite reading is an easy and expensive mistake: it
     * looks like a `NotSerializableException` waiting to happen in a clustered
     * deployment, and it is not one.
     */
    @Test
    void whatTheCacheStoresIsTheUnwrappedValue() throws Exception {
        assertThat(org.springframework.util.ObjectUtils.unwrapOptional(
                java.util.Optional.of(populated()))).isInstanceOf(TurSNSiteSearchSnapshot.class);
        assertThat(org.springframework.util.ObjectUtils.unwrapOptional(
                java.util.Optional.empty())).isNull();

        // The Optional itself would not survive — which is exactly why Spring
        // unwraps it, and why this test asserts the unwrapped value instead.
        assertThat(java.io.Serializable.class.isAssignableFrom(java.util.Optional.class))
                .isFalse();
    }
}
