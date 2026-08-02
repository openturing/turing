/*
 * Copyright (C) 2016-2021 the original author or authors. 
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

package com.viglet.turing.commons.sn.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.io.Serializable;

/**
 * Metadata of document with relation with facets of Turing ES Semantic Navigation response.
 *
 * @author Alexandre Oliveira
 *
 * @since 0.3.4
 */

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSiteSearchDocumentMetadataBean implements Serializable {
	private String href;
	private String text;

	/**
	 * T864 / §LXV.6 — the facet field this chip belongs to, carried for the
	 * link-free projection.
	 *
	 * <p>
	 * Like the T708 facet-item action, it rides a {@code @JsonIgnore} field: the
	 * field name and the raw value are known only where the href is built, and
	 * recovering them later would mean parsing that href back — the exact
	 * round-trip Block AV exists to remove. Keeping the hand-off in-process
	 * leaves the legacy JSON byte-for-byte unchanged.
	 * </p>
	 *
	 * @since 2026.3.4
	 */
	@JsonIgnore
	private String field;

	/** T864 — the raw facet value, un-encoded. @see #field */
	@JsonIgnore
	private String value;

	/** T864 — the {@code field:value} pair to put on {@code request.fq}. @see #field */
	@JsonIgnore
	private String filterQuery;

}
