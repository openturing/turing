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
}
