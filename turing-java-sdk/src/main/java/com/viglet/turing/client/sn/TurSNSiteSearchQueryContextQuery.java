/*
 * Copyright (C) 2016-2021 the original author or authors. 
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

package com.viglet.turing.client.sn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Return details about Turing AI response query.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.4
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSiteSearchQueryContextQuery {

	private String queryString;
	private String sort;
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

}
