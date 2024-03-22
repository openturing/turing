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
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class TurSEResults extends TurSEGenericResults {
	private int qTime;
	private long elapsedTime;
	private String queryString;
	private String sort;
	private TurSESpellCheckResult spellCheck;
	private List<TurSESimilarResult> similarResults;
	private List<TurSEFacetResult> facetResults;
	private List<TurSEGroup> groups;
	@Builder
	public TurSEResults(long numFound, long start, int limit, int pageCount, int currentPage, List<TurSEResult> results,
						int qTime, long elapsedTime, String queryString, String sort, TurSESpellCheckResult spellCheck,
						List<TurSESimilarResult> similarResults, List<TurSEFacetResult> facetResults,
						List<TurSEGroup> groups) {
		super(numFound, start, limit, pageCount, currentPage, results);
		this.qTime = qTime;
		this.elapsedTime = elapsedTime;
		this.queryString = queryString;
		this.sort = sort;
		this.spellCheck = spellCheck;
		this.similarResults = similarResults;
		this.facetResults = facetResults;
		this.groups = groups;
	}
}
