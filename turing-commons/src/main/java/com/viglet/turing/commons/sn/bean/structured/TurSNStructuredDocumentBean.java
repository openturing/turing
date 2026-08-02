/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.commons.sn.bean.structured;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.viglet.turing.commons.sn.bean.TurSNDuplicateCluster;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * T864 / §LXV.6 — one search result, link-free.
 *
 * <p>
 * Every field mirrors {@code TurSNSiteSearchDocumentBean} by name, so a
 * consumer's result rendering is unchanged — with one exception, which is the
 * point of the type: {@link #getMetadata()} is a list of
 * {@link TurSNStructuredMetadataBean} instead of {@code {href, text}} pairs.
 * §XLV.3 carried {@code results} over from the legacy bean on the grounds that
 * it "was already link-free"; T859's first end-to-end run over a response with
 * documents showed it was not.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Accessors(chain = true)
public class TurSNStructuredDocumentBean implements Serializable {

    private String source;

    private boolean elevate;

    /** The document's facet chips, as data rather than URLs. */
    private List<TurSNStructuredMetadataBean> metadata;

    @SuppressWarnings("java:S1948")
    private Map<String, Object> fields;

    /** T389 — the "why this result ranked here" breakdown, carried over as-is. */
    @SuppressWarnings("java:S1948")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> rankingExplanation;

    /** T390 — the near-duplicate cluster, carried over as-is. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private TurSNDuplicateCluster duplicateCluster;
}
