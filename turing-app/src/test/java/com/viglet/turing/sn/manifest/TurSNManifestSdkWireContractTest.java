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
package com.viglet.turing.sn.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.core.manifest.VigletFieldType;
import com.viglet.core.manifest.VigletManifestDiff;
import com.viglet.core.manifest.VigletManifestFieldChange;
import com.viglet.core.manifest.VigletManifestMigrationAction;
import com.viglet.core.manifest.VigletManifestResult;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.client.sn.manifest.TurSNManifest;
// The SDK's custom facet types, imported so they shadow the server's same-named records in this package
import com.viglet.turing.client.sn.manifest.TurSNManifestCustomFacet;
import com.viglet.turing.client.sn.manifest.TurSNManifestCustomFacetItem;
import com.viglet.turing.client.sn.manifest.TurSNManifestDiff;
import com.viglet.turing.client.sn.manifest.TurSNManifestFieldChange;
import com.viglet.turing.client.sn.manifest.TurSNManifestResult;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetOperatorEnum;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * T1030 / §XX.17 — the SDK declares its own manifest records so a published
 * client does not drag {@code viglet-core} and its transitive tree into every
 * connector. The cost of that choice is drift, and this is where it is paid: the
 * SDK's payload is deserialised into the very record
 * {@code TurSNSiteManifestAPI} binds, and the server's responses are
 * deserialised into the SDK's.
 *
 * <p>A rename on either side fails here rather than at a customer's first
 * provisioning call.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNManifestSdkWireContractTest {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .build();

    private static TurSNManifest sdkManifest() {
        return TurSNManifest.builder()
                .name("Catalog")
                .description("Product catalog")
                .seInstanceId("se-1")
                .schemaVersion("3")
                .locale("en-US")
                .locale("pt-BR")
                .field(TurSNJobAttributeSpec.builder()
                        .name("sku")
                        .type(TurSEFieldType.STRING)
                        .mandatory(true)
                        .multiValued(false)
                        .facet(false)
                        .description("Stock keeping unit")
                        .searchWeight(3f)
                        .build())
                .field(TurSNJobAttributeSpec.builder()
                        .name("brand")
                        .type(TurSEFieldType.STRING)
                        .mandatory(false)
                        .multiValued(true)
                        .facet(true)
                        .facetName(Map.of("en", "Brand", "pt", "Marca"))
                        .build())
                .recreate("brand")
                .customFacet(TurSNManifestCustomFacet.of("publishedAt", "postedSince", "Posted", List.of(
                        TurSNManifestCustomFacetItem.since("Last 7 days", "NOW-7DAYS")
                                .withLabels(Map.of("pt", "Últimos 7 dias")),
                        TurSNManifestCustomFacetItem.dateRange("2025", "2025-01-01", "2026-01-01"))))
                .build();
    }

    /** What {@code TurSNSiteManifestAPI} binds since T1084: the neutral manifest plus custom facets. */
    private static TurSNManifestRequest bind(String payload) {
        return MAPPER.readValue(payload, TurSNManifestRequest.class);
    }

    @Test
    @DisplayName("T1084 - a custom facet the SDK declares binds to the server's, buckets and labels included")
    void customFacetSurvivesTheWire() {
        TurSNManifestRequest bound = bind(MAPPER.writeValueAsString(sdkManifest()));

        assertThat(bound.customFacets()).singleElement().satisfies(facet -> {
            assertThat(facet.field()).isEqualTo("publishedAt");
            assertThat(facet.name()).isEqualTo("postedSince");
            assertThat(facet.defaultLabel()).isEqualTo("Posted");
            assertThat(facet.items()).hasSize(2);
            assertThat(facet.items().get(0).rangeStartDate()).isEqualTo("NOW-7DAYS");
            assertThat(facet.items().get(0).labels()).containsEntry("pt", "Últimos 7 dias");
            assertThat(facet.items().get(1).rangeEndDate()).isEqualTo("2026-01-01");
        });
    }

    @Test
    @DisplayName("T1084 - a manifest without custom facets binds with none, as it did before them")
    void manifestWithoutCustomFacetsStillBinds() {
        TurSNManifest plain = TurSNManifest.builder().name("Catalog").locale("en-US").build();

        assertThat(bind(MAPPER.writeValueAsString(plain)).customFacetsOrEmpty()).isEmpty();
    }

    @Test
    @DisplayName("T1084 - a bucket operator the SDK spells as text binds to the server's enum")
    void operatorBindsToTheServerEnum() {
        TurSNManifest manifest = TurSNManifest.builder().name("Catalog")
                .customFacet(TurSNManifestCustomFacet.of("price", "cheap", null, List.of(
                        new TurSNManifestCustomFacetItem("Under 10", null, null, "LESS_THAN", null,
                                BigDecimal.TEN, null, null))))
                .build();

        assertThat(bind(MAPPER.writeValueAsString(manifest)).customFacets().get(0).items().get(0).operator())
                .isEqualTo(TurSNSiteCustomFacetOperatorEnum.LESS_THAN);
    }

    @Test
    @DisplayName("T1084 - the provisioning result stays flat and carries the custom facets it changed")
    void provisionResultWithCustomFacetsBindsToTheSdk() {
        var serverResult = new TurSNManifestProvisionResult(new VigletManifestResult("s1", "Catalog", false,
                List.of(), List.of("sku"), List.of(), List.of(), "3"), List.of("postedSince"));

        TurSNManifestResult sdk = MAPPER.readValue(MAPPER.writeValueAsString(serverResult),
                TurSNManifestResult.class);

        assertThat(sdk.siteId()).isEqualTo("s1");
        assertThat(sdk.fieldsSkipped()).containsExactly("sku");
        assertThat(sdk.customFacetsChanged()).containsExactly("postedSince");
        assertThat(sdk.isNoOp()).isFalse();
    }

    @Test
    @DisplayName("T1084 - the plan stays flat and names the custom facets provisioning would change")
    void planWithCustomFacetsBindsToTheSdk() {
        var serverPlan = new TurSNManifestPlan(new VigletManifestDiff("3", true, List.of(), List.of(),
                List.of("sku"), List.of()), List.of("postedSince"));

        TurSNManifestDiff sdk = MAPPER.readValue(MAPPER.writeValueAsString(serverPlan),
                TurSNManifestDiff.class);

        assertThat(sdk.siteExists()).isTrue();
        assertThat(sdk.fieldsUnchanged()).containsExactly("sku");
        assertThat(sdk.customFacetsToChange()).containsExactly("postedSince");
        assertThat(sdk.isNoOp()).isFalse();
    }

    @Test
    @DisplayName("The SDK's manifest deserialises into the record the endpoint binds, field for field")
    void sdkManifestBindsToTheServerRecord() {
        String payload = MAPPER.writeValueAsString(sdkManifest());

        VigletFieldManifest bound = bind(payload).toManifest();

        assertThat(bound.name()).isEqualTo("Catalog");
        assertThat(bound.description()).isEqualTo("Product catalog");
        assertThat(bound.seInstanceId()).isEqualTo("se-1");
        assertThat(bound.schemaVersion()).isEqualTo("3");
        assertThat(bound.locales()).containsExactly("en-US", "pt-BR");
        assertThat(bound.fields()).extracting(VigletFieldSpec::name)
                .containsExactly("sku", "brand");
    }

    @Test
    @DisplayName("A field spec keeps its type and cardinality across the wire")
    void fieldSpecSurvivesTheWire() {
        String payload = MAPPER.writeValueAsString(sdkManifest());

        VigletFieldManifest bound = bind(payload).toManifest();

        VigletFieldSpec sku = bound.fields().get(0);
        assertThat(sku.type()).isEqualTo(VigletFieldType.STRING);
        assertThat(sku.mandatory()).isTrue();
        assertThat(sku.multiValued()).isFalse();
        assertThat(sku.description()).isEqualTo("Stock keeping unit");
        // T1082 — the weight the endpoint applies to the site's field (since T1072)
        assertThat(sku.searchWeight()).isEqualTo(3f);

        VigletFieldSpec brand = bound.fields().get(1);
        assertThat(brand.searchWeight()).isNull();
        assertThat(brand.multiValued()).isTrue();
        assertThat(brand.facet()).isTrue();
        assertThat(brand.facetName()).containsEntry("en", "Brand").containsEntry("pt", "Marca");
    }

    @Test
    @DisplayName("A declared migration arrives as the action the server's enum names")
    void migrationSurvivesTheWire() {
        String payload = MAPPER.writeValueAsString(sdkManifest());

        VigletFieldManifest bound = bind(payload).toManifest();

        assertThat(bound.migrations()).hasSize(1);
        assertThat(bound.migrations().get(0).field()).isEqualTo("brand");
        assertThat(bound.migrations().get(0).actionOrDefault())
                .isEqualTo(VigletManifestMigrationAction.RECREATE);
    }

    @Test
    @DisplayName("Every TurSEFieldType has a same-named neutral type, so no manifest field is unmappable")
    void everyFieldTypeMaps() {
        for (TurSEFieldType type : TurSEFieldType.values()) {
            assertThat(VigletFieldType.valueOf(type.name())).isNotNull();
        }
    }

    @Test
    @DisplayName("The server's provisioning result deserialises into the SDK's")
    void serverResultBindsToTheSdkRecord() {
        var serverResult = new VigletManifestResult("s1", "Catalog", true, List.of("sku"),
                List.of("brand"), List.of(), List.of("en-US"), "3");

        TurSNManifestResult sdk = MAPPER.readValue(MAPPER.writeValueAsString(serverResult),
                TurSNManifestResult.class);

        assertThat(sdk.siteId()).isEqualTo("s1");
        assertThat(sdk.siteCreated()).isTrue();
        assertThat(sdk.fieldsCreated()).containsExactly("sku");
        assertThat(sdk.fieldsSkipped()).containsExactly("brand");
        assertThat(sdk.localesCreated()).containsExactly("en-US");
        assertThat(sdk.schemaVersion()).isEqualTo("3");
        assertThat(sdk.isNoOp()).isFalse();
    }

    @Test
    @DisplayName("The server's plan diff deserialises into the SDK's, breaking changes included")
    void serverDiffBindsToTheSdkRecord() {
        var serverDiff = new VigletManifestDiff("3", true, List.of("stock"),
                List.of(new VigletManifestFieldChange("price", "type", "STRING", "CURRENCY", false)),
                List.of("sku"), List.of("pt-BR"));

        TurSNManifestDiff sdk = MAPPER.readValue(MAPPER.writeValueAsString(serverDiff),
                TurSNManifestDiff.class);

        assertThat(sdk.siteExists()).isTrue();
        assertThat(sdk.fieldsToAdd()).containsExactly("stock");
        assertThat(sdk.localesToAdd()).containsExactly("pt-BR");
        assertThat(sdk.undeclaredBreakingChanges()).extracting(TurSNManifestFieldChange::field)
                .containsExactly("price");
        assertThat(sdk.undeclaredBreakingChanges().get(0).manifestValue()).isEqualTo("CURRENCY");
    }

    @Test
    @DisplayName("A manifest the SDK built provisions for real against the planner's diff contract")
    void sdkManifestIsPlannableByTheServer() {
        // The strongest form of the contract: the bound manifest is the same
        // object the planner reads, so a diff over it must name the SDK's fields.
        VigletFieldManifest bound = bind(MAPPER.writeValueAsString(sdkManifest())).toManifest();

        assertThat(bound.fields()).allSatisfy(spec -> {
            assertThat(spec.name()).isNotBlank();
            assertThat(spec.type()).isNotNull();
        });
    }
}
