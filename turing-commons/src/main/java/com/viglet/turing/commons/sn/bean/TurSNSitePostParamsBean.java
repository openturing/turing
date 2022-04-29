/*
 * Copyright (C) 2016-2022 the original author or authors. 
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

import java.util.ArrayList;
import java.util.List;

/**
 * Post Parameters for Search with sensitive data
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
public class TurSNSitePostParamsBean {

	private String userId;

	private boolean populateMetrics = true;

	private List<String> targetingRules = new ArrayList<>();

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public boolean isPopulateMetrics() {
		return populateMetrics;
	}

	public void setPopulateMetrics(boolean populateMetrics) {
		this.populateMetrics = populateMetrics;
	}

	public List<String> getTargetingRules() {
		return targetingRules;
	}

	public void setTargetingRules(List<String> targetingRules) {
		this.targetingRules = targetingRules;
	}
}
