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

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.*;

import static com.viglet.turing.commons.sn.field.TurSNFieldName.ID;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.SOURCE_APPS;

/**
 * Job to index and deIndex in Turing ES.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.4
 */
@Getter
@Setter
public class TurSNJobItem implements Serializable{

	private static final long serialVersionUID = 1L;
    private Locale locale;
	private TurSNJobAction turSNJobAction;
	private List<String> siteNames;
    private List<TurSNJobAttributeSpec> specs = new ArrayList<>();
    private Map<String, Object> attributes = new HashMap<>();
	private String checksum;
	public TurSNJobItem() {
		super();
	}

	public TurSNJobItem(TurSNJobAction turSNJobAction, List<String> siteNames) {
		super();
		this.turSNJobAction = turSNJobAction;
		this.siteNames = siteNames;
		this.locale = Locale.ENGLISH;
		this.specs = null;
		this.checksum = null;
	}

	public TurSNJobItem(TurSNJobAction turSNJobAction,  List<String> siteNames, Locale locale) {
		this(turSNJobAction, siteNames);
		this.locale = locale;
		this.specs = null;
		this.checksum = null;
	}

	public TurSNJobItem(TurSNJobAction turSNJobAction,  List<String> siteNames, Locale locale,
						Map<String, Object> attributes) {
		this(turSNJobAction, siteNames, locale);
		this.attributes = attributes;
		this.specs = null;
		this.checksum = null;
	}

	public TurSNJobItem(TurSNJobAction turSNJobAction, List<String> siteNames,  Locale locale,
						Map<String, Object> attributes, List<TurSNJobAttributeSpec> specs) {
		this(turSNJobAction, siteNames, locale, attributes);
		this.specs = specs;
		this.checksum = null;
	}

	public TurSNJobItem(TurSNJobAction turSNJobAction, List<String> siteNames,  Locale locale,
						Map<String, Object> attributes, List<TurSNJobAttributeSpec> specs, String checksum) {
		this(turSNJobAction, siteNames, locale, attributes, specs);
		this.checksum = checksum;
	}


    public String toString() {
		return String.format("action: %s, attributes %s", this.getTurSNJobAction(), this.getAttributes().toString());
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