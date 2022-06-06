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
package com.viglet.turing.persistence.repository.sn.metric;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

/**
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
public class TurSNSiteMetricAccessTerm {

	private String term;
	private Date acessDate;
	private long total;
	private double numFound;

	public TurSNSiteMetricAccessTerm(String term, Date acessDate) {
		super();
		this.term = term;
		this.acessDate = acessDate;
	}

	public TurSNSiteMetricAccessTerm(String term, long total, double numFound) {
		super();
		this.term = term;
		this.total = total;
		this.numFound =  new BigDecimal(numFound).setScale(0, RoundingMode.HALF_UP).doubleValue();
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public Date getAcessDate() {
		return acessDate;
	}

	public void setAcessDate(Date acessDate) {
		this.acessDate = acessDate;
	}

	public long getTotal() {
		return total;
	}

	public void setTotal(long total) {
		this.total = total;
	}

	public double getNumFound() {
		return numFound;
	}

	public void setNumFound(double numFound) {
		this.numFound = numFound;
	}

}
