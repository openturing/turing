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
package com.viglet.turing.nlp;

import java.util.List;

import com.viglet.turing.persistence.model.nlp.TurNLPVendorEntity;

/**
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
public class TurNLPEntityRequest {

	private String name;
	private List<String> types;
	private List<String> subTypes;
	private TurNLPVendorEntity turNLPVendorEntity;

	public TurNLPEntityRequest(String name, List<String> types, List<String> subTypes,
			TurNLPVendorEntity turNLPVendorEntity) {
		super();
		this.name = name;
		this.types = types;
		this.subTypes = subTypes;
		this.turNLPVendorEntity = turNLPVendorEntity;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<String> getTypes() {
		return types;
	}

	public void setTypes(List<String> types) {
		this.types = types;
	}

	public List<String> getSubTypes() {
		return subTypes;
	}

	public void setSubTypes(List<String> subTypes) {
		this.subTypes = subTypes;
	}

	public TurNLPVendorEntity getTurNLPVendorEntity() {
		return turNLPVendorEntity;
	}

	public void setTurNLPVendorEntity(TurNLPVendorEntity turNLPVendorEntity) {
		this.turNLPVendorEntity = turNLPVendorEntity;
	}

}
