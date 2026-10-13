/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.turing.client.sn.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T1030 / §XX.17 — the diff is what lets a connector fail its own start-up with a
 * reason instead of posting and catching a 409, so the two predicates a connector
 * branches on are asserted directly.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNManifestDiffTest {

    private static TurSNManifestFieldChange change(String field, boolean declared) {
        return new TurSNManifestFieldChange(field, "type", "STRING", "INT", declared);
    }

    @Test
    @DisplayName("Only the undeclared breaking changes are the ones that would block provisioning")
    void undeclaredChangesAreSeparated() {
        var diff = new TurSNManifestDiff("3", true, List.of(),
                List.of(change("price", true), change("stock", false)), List.of("sku"), List.of());

        assertThat(diff.hasBreakingChanges()).isTrue();
        assertThat(diff.undeclaredBreakingChanges()).extracting(TurSNManifestFieldChange::field)
                .containsExactly("stock");
    }

    @Test
    @DisplayName("A null breakingChanges list is no breaking change, not a crash")
    void nullBreakingChangesIsEmpty() {
        var diff = new TurSNManifestDiff(null, true, null, null, null, null);

        assertThat(diff.hasBreakingChanges()).isFalse();
        assertThat(diff.undeclaredBreakingChanges()).isEmpty();
        assertThat(diff.isNoOp()).isTrue();
    }

    @Test
    @DisplayName("A manifest that would create the site is never a no-op")
    void creatingASiteIsNotANoOp() {
        var diff = new TurSNManifestDiff("1", false, List.of("sku"), List.of(), List.of(),
                List.of("en-US"));

        assertThat(diff.isNoOp()).isFalse();
    }

    @Test
    @DisplayName("A field or locale left to add is not a no-op either")
    void pendingAdditionsAreNotANoOp() {
        assertThat(new TurSNManifestDiff("1", true, List.of("sku"), List.of(), List.of(), List.of())
                .isNoOp()).isFalse();
        assertThat(new TurSNManifestDiff("1", true, List.of(), List.of(), List.of(),
                List.of("pt-BR")).isNoOp()).isFalse();
    }

    @Test
    @DisplayName("A result that created nothing is the idempotence signal a connector checks")
    void resultNoOpDetectsIdempotentReRun() {
        var noOp = new TurSNManifestResult("s1", "Catalog", false, List.of(),
                List.of("sku", "brand"), List.of(), List.of(), "3");
        var created = new TurSNManifestResult("s1", "Catalog", false, List.of("stock"), List.of(),
                List.of(), List.of(), "3");

        assertThat(noOp.isNoOp()).isTrue();
        assertThat(created.isNoOp()).isFalse();
        assertThat(new TurSNManifestResult("s1", "Catalog", true, null, null, null, null, null)
                .isNoOp()).isFalse();
    }

    @Test
    @DisplayName("T1084 - a custom facet created or left to change is not a no-op")
    void customFacetChangesAreNotANoOp() {
        assertThat(new TurSNManifestResult("s1", "Catalog", false, List.of(), List.of("sku"), List.of(),
                List.of(), "3", List.of("postedSince")).isNoOp()).isFalse();
        assertThat(new TurSNManifestResult("s1", "Catalog", false, List.of(), List.of("sku"), List.of(),
                List.of(), "3", List.of()).isNoOp()).isTrue();
        assertThat(new TurSNManifestDiff("3", true, List.of(), List.of(), List.of("sku"), List.of(),
                List.of("postedSince")).isNoOp()).isFalse();
    }

    @Test
    @DisplayName("T1084 - the builder sends declared custom facets, and none as none")
    void builderCarriesCustomFacets() {
        TurSNManifestCustomFacet facet = TurSNManifestCustomFacet.of("publishedAt", "postedSince", "Posted",
                List.of(TurSNManifestCustomFacetItem.since("Last 7 days", "NOW-7DAYS")));

        assertThat(TurSNManifest.builder().name("jobs").customFacet(facet).customFacet(null).build()
                .customFacets()).containsExactly(facet);
        assertThat(TurSNManifest.builder().name("jobs").build().customFacets()).isNull();
        assertThat(facet.items().get(0).rangeStartDate()).isEqualTo("NOW-7DAYS");
        assertThat(facet.items().get(0).rangeEndDate()).isNull();
    }

    @Test
    @DisplayName("A field change reads as its before/after, and says whether it is authorised")
    void fieldChangeIsReadable() {
        assertThat(change("price", false)).hasToString("price.type: STRING -> INT (UNDECLARED)");
        assertThat(change("price", true))
                .hasToString("price.type: STRING -> INT (migration declared)");
    }
}
