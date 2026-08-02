/*
 * Copyright (C) 2016-2021 the original author or authors.
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

package com.viglet.turing.commons.sn.bean;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.commons.sn.search.TurSNFacetItemAction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Facet Item of Turing ES Semantic Navigation response.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.4
 */
@RequiredArgsConstructor
@Accessors(chain = true)
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSiteSearchFacetItemBean implements Serializable {
	private int count;
	/**
	 * T713 / §XLV — <strong>legacy</strong>: a pre-serialized GET URL that
	 * re-runs the search with this item toggled.
	 *
	 * <p>
	 * Kept, and not going away — it is what makes facet pages deep-linkable,
	 * bookmarkable, SEO-crawlable and usable without JavaScript, which §XLV.5
	 * protects as a non-goal. But it is <em>legacy for a JavaScript client</em>:
	 * parsing this URL back into parameters is the query-string round-trip the
	 * block exists to remove, and a value containing {@code & = + # %} or a
	 * space only survives it because the encoder is correct. A JS client should
	 * request {@code responseFormat: STRUCTURED} and toggle
	 * {@link #filterQuery} using the item's own {@link #action} instead; the
	 * shipped SDKs no longer parse this field.
	 * </p>
	 */
	private String link;
	private String label;
	private String filterQuery;
	private boolean selected;

	/**
	 * T708 / §XLV — what clicking this item does to the request's filter-query
	 * list. Resolved by the facet renderer, which is the only place that knows the
	 * field's facet-item mode (an {@code AND} field replaces its other selections
	 * instead of adding to them).
	 *
	 * <p>
	 * {@code @JsonIgnore} on purpose: this is an in-process hand-off to the
	 * structured-response mapper, so the legacy JSON stays byte-for-byte unchanged.
	 * The wire form of this field is
	 * {@code TurSNStructuredFacetItemBean.action}.
	 * </p>
	 *
	 * @since 2026.3.4
	 */
	@JsonIgnore
	private transient TurSNFacetItemAction action;
}
