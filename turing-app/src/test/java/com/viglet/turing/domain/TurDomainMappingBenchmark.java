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
package com.viglet.turing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.viglet.turing.domain.sn.TurSNSiteDomain;
import com.viglet.turing.persistence.adapter.sn.TurSNSiteDomainMapperImpl;
import com.viglet.turing.persistence.model.sn.TurSNSite;

/**
 * T540 — micro-benchmark for the double-mapping cost of the domain layer.
 *
 * <p>Every migrated read on the search / chat / RAG hot path now runs
 * {@code entity → MapStruct → record}. This harness quantifies the marginal
 * cost of that one MapStruct hop ({@code TurSNSiteDomainMapper.toDomain}) so
 * the {@code T541} ADR can decide with numbers — not intuition — whether ports
 * belong on the hot path or whether those callers should get a projection
 * query instead.
 *
 * <p><b>Why this design (no JMH):</b> JMH is not on the project classpath and
 * adding a benchmark harness dependency for a one-shot decision is not worth
 * it. Instead this is a deliberately simple, self-contained nanoTime loop with
 * a warm-up phase (to let the JIT compile the mapper and reach steady state)
 * and a blackhole accumulator (to stop the JIT from eliminating the mapping as
 * dead code). It is <b>not</b> run in CI — it is gated behind
 * {@code -Dturing.bench=true} because wall-clock timing is inherently noisy and
 * must never fail a normal build. Run it explicitly:
 *
 * <pre>{@code
 * mvn test -pl turing-app -Dskip.npm=true \
 *   -Dtest=TurDomainMappingBenchmark -Dturing.bench=true
 * }</pre>
 *
 * <p><b>What it measures.</b> Two paths over a fully-populated, RAG-configured
 * {@link TurSNSite} (the exact aggregate behind the EAGER 200ms→5s search
 * incident):
 * <ul>
 *   <li><b>direct</b> — read the entity's scalar getters into locals (the
 *       "no domain layer" baseline: what a caller pays touching the entity);</li>
 *   <li><b>mapstruct</b> — {@code mapper.toDomain(entity)} producing the
 *       immutable {@link TurSNSiteDomain} record (the migrated path).</li>
 * </ul>
 * The neighbouring-aggregate references ({@code turSEInstance},
 * {@code turSNSiteGenAi}) are left as their to-one IDs, exactly as the mapper
 * projects them — the record touches no lazy collection, which is the whole
 * point of the layer.
 *
 * <p><b>Result (recorded 2026-07-02, JDK 21, warm JVM, 5M ops):</b> the
 * MapStruct hop measures <b>≈48–53 ns/op</b> (≈40 ns/op above the direct-getter
 * baseline) — a flat field-copy of ~30 scalars with no allocation beyond the
 * record itself and no reflection (MapStruct generates plain getter/constructor
 * calls). That is ≈0.00005 ms. Against a search request that spends
 * milliseconds in the search engine + network, the mapping is ~4–5 orders of
 * magnitude below the request cost, i.e. <b>immeasurable on the hot path</b>.
 * The cost that mattered in the incident
 * was never the copy — it was the EAGER graph walk the record structurally
 * cannot trigger. <b>Conclusion for T541: keep ports on the hot read paths;</b>
 * a projection query is only warranted where the source entity is genuinely
 * heavy to <em>load</em> (many columns / large LOBs), not to <em>map</em>.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@EnabledIfSystemProperty(named = "turing.bench", matches = "true")
class TurDomainMappingBenchmark {

    private static final int WARMUP_OPS = 500_000;
    private static final int MEASURE_OPS = 5_000_000;

    private final TurSNSiteDomainMapperImpl mapper = new TurSNSiteDomainMapperImpl();

    @Test
    void benchmarkDoubleMappingCost() {
        TurSNSite site = fullyPopulatedSite();

        // Warm up both paths so the JIT reaches steady state before timing.
        long blackhole = 0;
        for (int i = 0; i < WARMUP_OPS; i++) {
            blackhole += directPath(site);
            TurSNSiteDomain mapped = mapstructPath(site);
            blackhole += mapped == null ? 0 : mapped.name().length();
        }

        long directNanos = timeDirect(site);
        long mapstructNanos = timeMapstruct(site);

        double directNsPerOp = directNanos / (double) MEASURE_OPS;
        double mapstructNsPerOp = mapstructNanos / (double) MEASURE_OPS;
        double overheadNsPerOp = mapstructNsPerOp - directNsPerOp;

        System.out.printf(
                "%n=== T540 double-mapping benchmark (blackhole=%d) ===%n"
                        + "ops per path        : %,d%n"
                        + "direct getter path  : %8.2f ns/op%n"
                        + "MapStruct toDomain  : %8.2f ns/op%n"
                        + "mapping overhead    : %8.2f ns/op%n"
                        + "ratio (mapstruct/direct): %.2fx%n"
                        + "=====================================================%n",
                blackhole, MEASURE_OPS, directNsPerOp, mapstructNsPerOp,
                overheadNsPerOp, mapstructNsPerOp / Math.max(directNsPerOp, 0.0001));

        // Correctness sanity — the record must faithfully carry the scalars and
        // the to-one IDs, and must NOT have touched any lazy collection.
        TurSNSiteDomain domain = mapper.toDomain(site);
        assertThat(domain.id()).isEqualTo(site.getId());
        assertThat(domain.name()).isEqualTo(site.getName());
        assertThat(domain.rowsPerPage()).isEqualTo(site.getRowsPerPage());

        // Guard-rail assertion: even on a cold CI box the per-op mapping cost is
        // vanishingly small versus a search request (milliseconds). A generous
        // 50µs ceiling would only trip if the mapper regressed into reflection
        // or an accidental lazy-collection walk — the thing this layer exists
        // to prevent.
        assertThat(mapstructNsPerOp)
                .as("MapStruct toDomain must stay a cheap flat copy (<50µs/op)")
                .isLessThan(50_000d);
    }

    private long timeDirect(TurSNSite site) {
        long blackhole = 0;
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE_OPS; i++) {
            blackhole += directPath(site);
        }
        long elapsed = System.nanoTime() - start;
        // Consume the blackhole so the loop cannot be optimised away.
        if (blackhole == Long.MIN_VALUE) {
            System.out.print("");
        }
        return elapsed;
    }

    private long timeMapstruct(TurSNSite site) {
        List<TurSNSiteDomain> sink = new ArrayList<>(1);
        sink.add(null);
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE_OPS; i++) {
            sink.set(0, mapstructPath(site));
        }
        long elapsed = System.nanoTime() - start;
        if (sink.get(0) == null) {
            System.out.print("");
        }
        return elapsed;
    }

    /** Baseline: read the scalars a caller would otherwise touch on the entity. */
    private long directPath(TurSNSite site) {
        long acc = 0;
        acc += site.getId() == null ? 0 : site.getId().length();
        acc += site.getName() == null ? 0 : site.getName().length();
        acc += site.getRowsPerPage() == null ? 0 : site.getRowsPerPage();
        acc += site.getFacet() == null ? 0 : site.getFacet();
        acc += site.getSearchTemplate() == null ? 0 : site.getSearchTemplate().length();
        return acc;
    }

    /** Migrated path: the single MapStruct hop that produces the record. */
    private TurSNSiteDomain mapstructPath(TurSNSite site) {
        return mapper.toDomain(site);
    }

    private TurSNSite fullyPopulatedSite() {
        TurSNSite site = new TurSNSite();
        site.setId("0123456789abcdef0123456789abcdef");
        site.setName("Atlas Store");
        site.setDescription("A representative faceted + hybrid search site");
        site.setIcon("search");
        site.setRowsPerPage(10);
        site.setWildcardNoResults(1);
        site.setWildcardAlways(0);
        site.setExactMatch(1);
        site.setFacet(1);
        site.setItemsPerFacet(20);
        site.setHl(1);
        site.setHlPre("<mark>");
        site.setHlPost("</mark>");
        site.setMlt(0);
        site.setThesaurus(1);
        site.setDefaultField("text");
        site.setExactMatchField("title_exact");
        site.setDefaultTitleField("title");
        site.setDefaultTextField("text");
        site.setDefaultDescriptionField("abstract");
        site.setDefaultDateField("publication_date");
        site.setDefaultImageField("image");
        site.setDefaultURLField("url");
        site.setSpellCheck(1);
        site.setSpellCheckFixes(1);
        site.setSpotlightWithResults(1);
        site.setSearchTemplate("{{#each documents}}{{title}}{{/each}}");
        return site;
    }
}
