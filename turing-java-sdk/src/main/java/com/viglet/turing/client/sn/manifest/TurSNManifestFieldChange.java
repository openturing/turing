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

/**
 * T1030 / §XX.17 — one breaking difference between a manifest field spec and the
 * live field of the same name: what changed, and its before/after values.
 *
 * @param field             the field name
 * @param attribute         the attribute that differs ({@code type} or
 *                          {@code multiValued})
 * @param liveValue         the value currently on the live schema
 * @param manifestValue     the value the manifest declares
 * @param migrationDeclared whether the manifest authorises this rewrite with a
 *                          {@link TurSNManifestMigration}; when false, posting
 *                          the manifest is rejected with HTTP 409
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNManifestFieldChange(String field, String attribute, String liveValue,
        String manifestValue, boolean migrationDeclared) {

    @Override
    public String toString() {
        return "%s.%s: %s -> %s%s".formatted(field, attribute, liveValue, manifestValue,
                migrationDeclared ? " (migration declared)" : " (UNDECLARED)");
    }
}
