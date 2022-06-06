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

package com.viglet.turing.se.result;

import java.util.List;

import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.se.facet.TurSEFacetResult;

public class TurSEResults extends TurSEGenericResults {
	private int qTime;
	private long elapsedTime;
	private String queryString;
	private String sort;
	private TurSESpellCheckResult spellCheck;
	private List<TurSESimilarResult> similarResults;
	private List<TurSEFacetResult> facetResults;
	private List<TurSEGroup> groups;

	public int getqTime() {
		return qTime;
	}

	public void setqTime(int qTime) {
		this.qTime = qTime;
	}

	public long getElapsedTime() {
		return elapsedTime;
	}

	public void setElapsedTime(long elapsedTime) {
		this.elapsedTime = elapsedTime;
	}

	public String getQueryString() {
		return queryString;
	}

	public void setQueryString(String queryString) {
		this.queryString = queryString;
	}

	public String getSort() {
		return sort;
	}

	public void setSort(String sort) {
		this.sort = sort;
	}

	public TurSESpellCheckResult getSpellCheck() {
		return spellCheck;
	}

	public void setSpellCheck(TurSESpellCheckResult spellCheck) {
		this.spellCheck = spellCheck;
	}

	public List<TurSESimilarResult> getSimilarResults() {
		return similarResults;
	}

	public void setSimilarResults(List<TurSESimilarResult> similarResults) {
		this.similarResults = similarResults;
	}

	public List<TurSEFacetResult> getFacetResults() {
		return facetResults;
	}

	public void setFacetResults(List<TurSEFacetResult> facetResults) {
		this.facetResults = facetResults;
	}

	public List<TurSEGroup> getGroups() {
		return groups;
	}

	public void setGroups(List<TurSEGroup> groups) {
		this.groups = groups;
	}

}
