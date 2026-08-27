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

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;

import tools.jackson.databind.json.JsonMapper;

/**
 * T1030 / §XX.17 — the manifest a connector builds and the JSON the server reads
 * have to be the same thing, so the shape is asserted on the wire and not only
 * through getters.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNManifestTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static TurSNJobAttributeSpec spec(String name, TurSEFieldType type, boolean facet) {
        return TurSNJobAttributeSpec.builder()
                .name(name)
                .type(type)
                .facet(facet)
                .mandatory(false)
                .multiValued(false)
                .build();
    }

    @Test
    @DisplayName("The builder keeps field order, which is what the manifest declares")
    void fieldOrderIsPreserved() {
        var manifest = TurSNManifest.builder()
                .name("Catalog")
                .field(spec("sku", TurSEFieldType.STRING, false))
                .field(spec("brand", TurSEFieldType.STRING, true))
                .field(spec("price", TurSEFieldType.CURRENCY, false))
                .build();

        assertThat(manifest.fields()).extracting(TurSNJobAttributeSpec::getName)
                .containsExactly("sku", "brand", "price");
    }

    @Test
    @DisplayName("A Locale becomes its language tag, the form the server provisions by")
    void localesBecomeLanguageTags() {
        var manifest = TurSNManifest.builder()
                .name("Catalog")
                .locale(Locale.forLanguageTag("pt-BR"))
                .locale(Locale.US)
                .locale("es-ES")
                .build();

        assertThat(manifest.locales()).containsExactly("pt-BR", "en-US", "es-ES");
    }

    @Test
    @DisplayName("Blank and null locales and fields are dropped rather than posted")
    void blanksAreDropped() {
        var manifest = TurSNManifest.builder()
                .name("Catalog")
                .locale((Locale) null)
                .locale("   ")
                .field(null)
                .fields(null)
                .build();

        assertThat(manifest.locales()).isEmpty();
        assertThat(manifest.fields()).isEmpty();
    }

    @Test
    @DisplayName("No declared migration means null, not an empty list authorising nothing twice")
    void noMigrationsMeansNull() {
        var manifest = TurSNManifest.builder().name("Catalog").build();

        assertThat(manifest.migrations()).isNull();
    }

    @Test
    @DisplayName("recreate() authorises one field's breaking change")
    void recreateDeclaresAMigration() {
        var manifest = TurSNManifest.builder()
                .name("Catalog")
                .recreate("price")
                .build();

        assertThat(manifest.migrations()).containsExactly(
                new TurSNManifestMigration("price", TurSNManifestMigrationAction.RECREATE));
    }

    @Test
    @DisplayName("of(server, fields) reads the site and locale the connector is already publishing to")
    void ofServerSeedsFromTheConfiguredServer() {
        var server = new TurSNServer(URI.create("http://localhost:2700"), "Catalog",
                Locale.forLanguageTag("pt-BR"));

        var manifest = TurSNManifest.of(server, List.of(spec("sku", TurSEFieldType.STRING, false)));

        assertThat(manifest.name()).isEqualTo("Catalog");
        assertThat(manifest.locales()).containsExactly("pt-BR");
        assertThat(manifest.fields()).hasSize(1);
    }

    @Test
    @DisplayName("The serialised payload uses the component names the server's manifest record declares")
    void payloadMatchesTheServerContract() {
        var manifest = TurSNManifest.builder()
                .name("Catalog")
                .description("Product catalog")
                .seInstanceId("se-1")
                .schemaVersion("3")
                .locale("en-US")
                .field(TurSNJobAttributeSpec.builder()
                        .name("brand")
                        .type(TurSEFieldType.STRING)
                        .mandatory(true)
                        .multiValued(true)
                        .facet(true)
                        .description("Brand")
                        .facetName(Map.of("en", "Brand"))
                        .build())
                .recreate("brand")
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> json = MAPPER.readValue(MAPPER.writeValueAsString(manifest), Map.class);

        assertThat(json).containsKeys("name", "description", "seInstanceId", "schemaVersion",
                "locales", "fields", "migrations");
        @SuppressWarnings("unchecked")
        var field = (Map<String, Object>) ((List<Object>) json.get("fields")).get(0);
        // The field spec is the type a connector already publishes with a job, so
        // the JSON is identical whether it arrives with a document or a manifest.
        assertThat(field).containsEntry("name", "brand")
                .containsEntry("type", "STRING")
                .containsEntry("mandatory", true)
                .containsEntry("multiValued", true)
                .containsEntry("facet", true)
                .containsEntry("description", "Brand")
                .containsEntry("facetName", Map.of("en", "Brand"));
        @SuppressWarnings("unchecked")
        var migration = (Map<String, Object>) ((List<Object>) json.get("migrations")).get(0);
        assertThat(migration).containsEntry("field", "brand").containsEntry("action", "RECREATE");
    }
}
