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

import com.viglet.turing.commons.sn.search.TurSNFacetItemAction;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * T864 / §LXV.6 — one of a document's facet chips, link-free.
 *
 * <p>
 * The legacy {@code TurSNSiteSearchDocumentMetadataBean} carries
 * {@code {href, text}}: clicking a chip meant navigating a server-rendered URL.
 * This is the same chip expressed as data — identical in shape to a
 * {@link TurSNStructuredFacetItemBean}, because it <em>is</em> the same action:
 * add {@link #filterQuery} to {@code request.fq} and re-send.
 * </p>
 *
 * <p>
 * T869 / §LXVII.5 — "identical in shape" is now literal: the chip also carries
 * {@link #selected} and {@link #action}. Without them a consumer could only ever
 * <em>add</em> a chip's filter, so a chip whose filter was already applied looked
 * the same as one that was not, and clicking it a second time was a no-op. Both
 * values are already computed for the facet rail; the chip reads them from the
 * matching facet item, so the rail and the chip can never disagree about the same
 * {@code field:value}.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Accessors(chain = true)
public class TurSNStructuredMetadataBean implements Serializable {

    /** The facet field this chip belongs to. */
    private String field;

    /** The raw facet value, un-encoded. */
    private String value;

    /** The {@code field:value} pair to add to {@code request.fq}. */
    private String filterQuery;

    /** What to render. */
    private String text;

    /**
     * T869 — whether this chip's filter is part of the current request.
     *
     * @since 2026.3.4
     */
    private boolean selected;

    /**
     * T869 — what to do to {@code request.fq} when this chip is clicked. Mirrors
     * the matching facet item's action, so a single-select (AND) field yields
     * {@code REPLACE} here too.
     *
     * @since 2026.3.4
     */
    private TurSNFacetItemAction action;
}
