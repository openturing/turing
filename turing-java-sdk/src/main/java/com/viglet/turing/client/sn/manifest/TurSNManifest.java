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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;

/**
 * T1030 / §XX.17 — the declarative manifest a connector posts to provision the
 * site it is about to index: the site, its locales and its ordered field schema,
 * converged in one idempotent call.
 *
 * <p>The field specs are {@link TurSNJobAttributeSpec}s — the very type a
 * connector already publishes with every document. There is no second schema
 * language to keep in step: the same declaration that travels with a job
 * provisions the site.
 *
 * <p>The component names match the server's manifest record exactly, so the JSON
 * is identical on both ends.
 *
 * @param name          the SN site name (required; matched case-insensitively to
 *                      decide create vs. converge)
 * @param description   optional human description, applied on create only
 * @param seInstanceId  the search-engine instance to bind on create; may be
 *                      {@code null} when the site already exists
 * @param schemaVersion optional version tag echoed back in the result and the
 *                      diff. Purely informational — the live diff, not the tag,
 *                      decides what converges
 * @param locales       language tags to provision; the first seeds the site on
 *                      create, the rest are added idempotently
 * @param fields        ordered field specifications to ensure exist
 * @param migrations    breaking changes this manifest authorises; {@code null}
 *                      means none are, so a breaking change is rejected
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNManifest(String name, String description, String seInstanceId,
        String schemaVersion, List<String> locales, List<TurSNJobAttributeSpec> fields,
        List<TurSNManifestMigration> migrations) {

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The manifest a connector's own configuration already implies: the site name
     * and locale it publishes to, plus the field specs it declares.
     *
     * @param server the configured server — its site name and locale seed the
     *               manifest
     * @param fields the declared field specs, in the order they should exist
     */
    public static TurSNManifest of(TurSNServer server, Collection<TurSNJobAttributeSpec> fields) {
        return builder()
                .name(server.getSiteName())
                .locale(server.getLocale())
                .fields(fields)
                .build();
    }

    /**
     * Fluent builder. The SDK's own types use Lombok, but this one is hand-rolled
     * so {@code locale(Locale)} and {@code field(spec)} can accumulate — the two
     * calls a connector makes most.
     */
    public static final class Builder {
        private String name;
        private String description;
        private String seInstanceId;
        private String schemaVersion;
        private final List<String> locales = new ArrayList<>();
        private final List<TurSNJobAttributeSpec> fields = new ArrayList<>();
        private final List<TurSNManifestMigration> migrations = new ArrayList<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder seInstanceId(String seInstanceId) {
            this.seInstanceId = seInstanceId;
            return this;
        }

        public Builder schemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        /** Adds one locale as its language tag ({@code pt-BR}), the form the server expects. */
        public Builder locale(Locale locale) {
            if (locale != null) {
                this.locales.add(locale.toLanguageTag());
            }
            return this;
        }

        public Builder locale(String languageTag) {
            if (languageTag != null && !languageTag.isBlank()) {
                this.locales.add(languageTag.trim());
            }
            return this;
        }

        public Builder field(TurSNJobAttributeSpec field) {
            if (field != null) {
                this.fields.add(field);
            }
            return this;
        }

        public Builder fields(Collection<TurSNJobAttributeSpec> fields) {
            if (fields != null) {
                fields.stream().filter(java.util.Objects::nonNull).forEach(this.fields::add);
            }
            return this;
        }

        public Builder migration(TurSNManifestMigration migration) {
            if (migration != null) {
                this.migrations.add(migration);
            }
            return this;
        }

        /** Authorises a {@code RECREATE} of one field whose type or cardinality changed. */
        public Builder recreate(String field) {
            return migration(TurSNManifestMigration.recreate(field));
        }

        public TurSNManifest build() {
            // An empty migrations list and a null one mean the same thing to the
            // server ("nothing is authorised"); null keeps the payload honest
            // about a manifest that declares no migration at all.
            return new TurSNManifest(name, description, seInstanceId, schemaVersion,
                    List.copyOf(locales), List.copyOf(fields),
                    migrations.isEmpty() ? null : List.copyOf(migrations));
        }
    }
}
