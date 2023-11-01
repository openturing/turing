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
package com.viglet.turing.api.sn.bean;

import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
@Getter
@Setter
public class TurSNSiteMetricsTopTermsBean {
	private List<TurSNSiteMetricAccessTerm> topTerms = new ArrayList<>();
	private int totalTermsPeriod = 0;
	private int totalTermsPreviousPeriod =0;
	private int variationPeriod = 0;
	public TurSNSiteMetricsTopTermsBean() {
		super();
	}
	public TurSNSiteMetricsTopTermsBean(List<TurSNSiteMetricAccessTerm> metricsTerms, int totalTermsPeriod,
			int totalTermsPreviousPeriod) {
		super();
		this.topTerms = metricsTerms;
		this.totalTermsPeriod = totalTermsPeriod;
		this.totalTermsPreviousPeriod = totalTermsPreviousPeriod;
		if (totalTermsPreviousPeriod == 0) {
			this.variationPeriod = 0;
		} else {
			float total = ((float) totalTermsPeriod / (float) totalTermsPreviousPeriod);
			this.variationPeriod = (int) ((total < 1) ? (-1) * (1 - total) * 100 : (total * 100) - 100);
		}
	}
}
