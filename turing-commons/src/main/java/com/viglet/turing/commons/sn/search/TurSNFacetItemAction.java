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
package com.viglet.turing.commons.sn.search;

/**
 * T708 / §XLV — what a client must do to its filter-query list when a facet item
 * is clicked. This is the structured replacement for the item's {@code link}.
 *
 * <p>
 * The action cannot be inferred from the item alone: in a field configured for
 * {@code AND} facet-<em>item</em> mode, picking a value <strong>replaces</strong>
 * the field's other selections rather than adding to them, and that mode is
 * per-field. It is therefore resolved where the mode is known — the facet
 * renderer — and carried on the item.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurSNFacetItemAction {

    /** Append this item's filter query to the request's {@code fq} list. */
    ADD,

    /** Drop this item's filter query from the request's {@code fq} list. */
    REMOVE,

    /**
     * Drop every filter query targeting this item's field, then append this one —
     * the single-select behaviour of {@code AND} facet-item mode.
     */
    REPLACE
}
