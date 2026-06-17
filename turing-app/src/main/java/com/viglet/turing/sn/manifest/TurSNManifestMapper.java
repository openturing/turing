/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.sn.manifest;

import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.core.manifest.VigletFieldType;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Maps the product-neutral manifest field model (viglet-core, T393) to Turing's
 * own search-engine field types at the provisioning boundary.
 *
 * <p>The manifest kernel lives in {@code com.viglet.core.manifest} so Shio and
 * Dumont can reuse it; Turing only needs to translate a {@link VigletFieldSpec}
 * into the {@link TurSNJobAttributeSpec} its {@code TurSNFieldProvisioner} (and
 * the rest of the SN stack) already speaks. The two field-type enums share their
 * constant names by construction, so the translation is a lossless
 * {@link Enum#name() name}-keyed lookup.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
public final class TurSNManifestMapper {

    private TurSNManifestMapper() {
    }

    /** Neutral field type → Turing search-engine field type (by name). */
    public static TurSEFieldType toSeType(VigletFieldType type) {
        return type == null ? null : TurSEFieldType.valueOf(type.name());
    }

    /** Turing search-engine field type → neutral field type (by name). */
    public static VigletFieldType toCoreType(TurSEFieldType type) {
        return type == null ? null : VigletFieldType.valueOf(type.name());
    }

    /** Neutral manifest field spec → Turing's index-time attribute spec. */
    public static TurSNJobAttributeSpec toJobSpec(VigletFieldSpec spec) {
        if (spec == null) {
            return null;
        }
        return TurSNJobAttributeSpec.builder()
                .name(spec.name())
                .type(toSeType(spec.type()))
                .mandatory(spec.mandatory())
                .multiValued(spec.multiValued())
                .description(spec.description())
                .facet(spec.facet())
                .facetName(spec.facetName())
                .build();
    }
}
