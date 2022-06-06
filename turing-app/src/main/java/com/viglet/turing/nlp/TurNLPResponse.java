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
import java.util.Map;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.model.nlp.TurNLPVendorEntity;

/**
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
public class TurNLPResponse {
	private TurNLPInstance turNLPInstance;
	private TurNLPVendor turNLPVendor;
	private List<TurNLPVendorEntity> turNLPVendorEntities;
	private Map<String, List<String>> entityMapWithProcessedValues;

	public TurNLPInstance getTurNLPInstance() {
		return turNLPInstance;
	}

	public void setTurNLPInstance(TurNLPInstance turNLPInstance) {
		this.turNLPInstance = turNLPInstance;
	}

	public TurNLPVendor getTurNLPVendor() {
		return turNLPVendor;
	}

	public void setTurNLPVendor(TurNLPVendor turNLPVendor) {
		this.turNLPVendor = turNLPVendor;
	}

	public Map<String, List<String>> getEntityMapWithProcessedValues() {
		return entityMapWithProcessedValues;
	}

	public void setEntityMapWithProcessedValues(Map<String, List<String>> entityMapWithProcessedValues) {
		this.entityMapWithProcessedValues = entityMapWithProcessedValues;
	}

	public List<TurNLPVendorEntity> getTurNLPVendorEntities() {
		return turNLPVendorEntities;
	}

	public void setTurNLPVendorEntities(List<TurNLPVendorEntity> turNLPVendorEntities) {
		this.turNLPVendorEntities = turNLPVendorEntities;
	}

}
