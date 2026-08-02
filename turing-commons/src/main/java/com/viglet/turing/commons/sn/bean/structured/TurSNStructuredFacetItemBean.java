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
 * T708 / §XLV — a facet value in the link-free response.
 *
 * <p>
 * {@code field} + {@code value} are the decomposition of {@code filterQuery}
 * (which is always {@code field:value}), so a client can group, label or
 * round-trip a selection without ever splitting a string it got from a URL. The
 * {@code action} says what clicking does to the request's {@code fq} list — it
 * replaces the legacy item {@code link} entirely.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Accessors(chain = true)
public class TurSNStructuredFacetItemBean implements Serializable {

    /** The facet's field name, e.g. {@code section}. */
    private String field;

    /** The raw value, e.g. {@code RAG & Chat} — never percent-encoded. */
    private String value;

    /** {@code field:value}, ready to be pushed onto the request's {@code fq}. */
    private String filterQuery;

    /** Display label; falls back to {@code value} when the field has no label. */
    private String label;

    /** Documents matching this value. */
    private int count;

    /** Whether this value is part of the current request. */
    private boolean selected;

    /** What to do to {@code fq} when this item is clicked. */
    private TurSNFacetItemAction action;
}
