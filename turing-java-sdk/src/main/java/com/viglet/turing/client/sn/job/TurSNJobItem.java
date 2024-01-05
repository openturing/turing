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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Job to index and deIndex in Turing ES.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.4
 */

public class TurSNJobItem implements Serializable{

	private static final long serialVersionUID = 1L;
	
	private Locale locale;
	
	private TurSNJobAction turSNJobAction;

	private List<TurSNAttributeSpec> specs = new ArrayList<>();
	private Map<String, Object> attributes;
	public TurSNJobItem() {
		super();
	}
	public TurSNJobItem(TurSNJobAction turSNJobAction, Locale locale,
						Map<String, Object> attributes) {
		super();
		this.locale = locale;
		this.turSNJobAction = turSNJobAction;
		this.attributes = attributes;
	}
	public TurSNJobItem(TurSNJobAction turSNJobAction, Locale locale, List<TurSNAttributeSpec> specs,
						Map<String, Object> attributes) {
		super();
		this.locale = locale;
		this.turSNJobAction = turSNJobAction;
		this.specs = specs;
		this.attributes = attributes;
	}

	public TurSNJobItem(TurSNJobAction turSNJobAction, Locale locale) {
		super();
		this.locale = locale;
		this.turSNJobAction = turSNJobAction;
	}
	public TurSNJobItem(TurSNJobAction turSNJobAction) {
		super();
		this.locale = Locale.ENGLISH;
		this.turSNJobAction = turSNJobAction;
	}
	public Locale getLocale() {
		return locale;
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, Object> attributes) {
		this.attributes = attributes;
	}

	public TurSNJobAction getTurSNJobAction() {
		return turSNJobAction;
	}

	public List<TurSNAttributeSpec> getSpecs() {
		return specs;
	}

	public void setSpecs(List<TurSNAttributeSpec> specs) {
		this.specs = specs;
	}

	public String toString() {
		return String.format("action: %s, attributes %s", this.getTurSNJobAction(), this.getAttributes().toString());
	}

}