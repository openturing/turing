/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.api.sn.job;

import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Map;

@Component
public class TurSNJobItem implements Serializable{

	private static final long serialVersionUID = 1L;
	
	private String locale;
	
	private TurSNJobAction turSNJobAction;
	
	private Map<String, Object> attributes;

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
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

	public void setTurSNJobAction(TurSNJobAction turSNJobAction) {
		this.turSNJobAction = turSNJobAction;
	}

	public String toString() {
		return String.format("locale: %s, action: %s, attributes %s", this.getLocale(), this.getTurSNJobAction(), this.getAttributes().toString());
	}

}
