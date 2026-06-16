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

package com.viglet.turing.client.sn.job;

import static com.viglet.turing.commons.sn.field.TurSNFieldName.ID;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.SOURCE_APPS;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Job to index and deIndex in Turing ES.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.4
 */
@Getter
@Setter
@ToString
public class TurSNJobItem implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;
	private Locale locale;
	private TurSNJobAction turSNJobAction;
	private List<String> siteNames;
	private List<TurSNJobAttributeSpec> specs = new ArrayList<>();
	/**
	 * The document's indexable fields.
	 *
	 * <p><b>Conservative-grounding contract (T381).</b> This map carries
	 * <em>only the fields a source actually supplies</em>: an <em>absent</em>
	 * field is a missing key, never a key mapped to {@code null}, {@code ""},
	 * or an empty collection. The indexing path enforces the same rule on the
	 * way out — a {@code null}, blank string, or empty collection value is
	 * treated as absent and is <b>not</b> materialized as an empty value in
	 * Solr/ES. This keeps facet counts and {@code exists}-style filters correct
	 * (absent ≠ empty) and is the indexing-side half of Dumont's grounding rule
	 * (D03). Producers should omit a field rather than send an empty value to
	 * convey "no value".
	 */
	@SuppressWarnings("java:S1948")
	private Map<String, Object> attributes = new HashMap<>();
	private String checksum;
	private String environment;

	public TurSNJobItem() {
		super();
	}

	public Object getAttribute(String attributeName) {
		return attributes.get(attributeName);
	}

	public String getStringAttribute(String attributeName) {
		if (attributes.get(attributeName) == null)
			return null;
		return attributes.get(attributeName).toString();
	}

	public boolean containsAttribute(String attributeName) {
		return attributes.containsKey(attributeName);
	}

	public TurSNJobItem(TurSNJobAction turSNJobAction, List<String> siteNames) {
		super();
		this.turSNJobAction = turSNJobAction;
		this.siteNames = siteNames;
		this.locale = Locale.ENGLISH;
		this.specs = null;
		this.checksum = null;
	}

	public TurSNJobItem(TurSNJobAction turSNJobAction, List<String> siteNames, Locale locale) {
		this(turSNJobAction, siteNames);
		this.locale = locale;
		this.specs = null;
		this.checksum = null;
	}

	public TurSNJobItem(TurSNJobAction turSNJobAction, List<String> siteNames, Locale locale,
			Map<String, Object> attributes) {
		this(turSNJobAction, siteNames, locale);
		this.attributes = attributes;
		this.specs = null;
		this.checksum = null;
	}

	public TurSNJobItem(TurSNJobAction turSNJobAction, List<String> siteNames, Locale locale,
			Map<String, Object> attributes, List<TurSNJobAttributeSpec> specs) {
		this(turSNJobAction, siteNames, locale, attributes);
		this.specs = specs;
		this.checksum = null;
	}

	public TurSNJobItem(TurSNJobAction turSNJobAction, List<String> siteNames, Locale locale,
			Map<String, Object> attributes, List<TurSNJobAttributeSpec> specs, String checksum) {
		this(turSNJobAction, siteNames, locale, attributes, specs);
		this.checksum = checksum;
	}

	public String getId() {
		if (this.getAttributes() != null && this.getAttributes().containsKey(ID)) {
			return this.attributes.get(ID).toString();
		}
		return null;
	}

	public String getProviderName() {
		if (this.getAttributes() != null && this.getAttributes().containsKey(SOURCE_APPS)) {
			return this.attributes.get(SOURCE_APPS).toString();
		}
		return null;
	}
}