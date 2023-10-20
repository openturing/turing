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
package com.viglet.turing.sn.spotlight;

import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
public class TurSNSpotlightTermCacheBean implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;
	private String term;
	private String spotlightId;
	private TurSNSiteSpotlight spotlight;

	public TurSNSpotlightTermCacheBean(String term, TurSNSiteSpotlight spotlight) {
		super();
		this.term = term;
		this.spotlight = spotlight;
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public String getSpotlightId() {
		return spotlightId;
	}

	public void setSpotlightId(String spotlightId) {
		this.spotlightId = spotlightId;
	}

	public TurSNSiteSpotlight getSpotlight() {
		return spotlight;
	}

	public void setSpotlight(TurSNSiteSpotlight spotlight) {
		this.spotlight = spotlight;
	}

}
