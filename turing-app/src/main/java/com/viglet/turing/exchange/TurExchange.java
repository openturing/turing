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
package com.viglet.turing.exchange;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.viglet.turing.exchange.sn.TurSNSiteExchange;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurExchange {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private List<TurSNSiteExchange> snSites;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private List<TurLLMInstance> llm;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private List<TurStoreInstance> store;

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private List<TurSEInstance> se;

	public void setSnSites(List<TurSNSiteExchange> snSites) {
		this.snSites = snSites;
	}

	public void setLlm(List<TurLLMInstance> llm) {
		this.llm = llm;
	}

	public void setStore(List<TurStoreInstance> store) {
		this.store = store;
	}

	public void setSe(List<TurSEInstance> se) {
		this.se = se;
	}
}
