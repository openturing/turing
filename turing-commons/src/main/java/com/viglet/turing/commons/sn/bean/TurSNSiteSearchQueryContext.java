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

package com.viglet.turing.commons.sn.bean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Details about the results of query of Turing AI Semantic Navigation response.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.4
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSiteSearchQueryContext {

	private int count;
	private String index;
	private int limit;
	private int offset;
	private int page;
	private int pageCount;
	private int pageEnd;
	private int pageStart;
	private long responseTime;
	private TurSNSiteSearchQueryContextQueryBean query;
	private TurSNSiteSearchDefaultFieldsBean defaultFields;
	
	public int getCount() {
		return count;
	}
	public void setCount(int count) {
		this.count = count;
	}
	public String getIndex() {
		return index;
	}
	public void setIndex(String index) {
		this.index = index;
	}
	public int getLimit() {
		return limit;
	}
	public void setLimit(int limit) {
		this.limit = limit;
	}
	public int getOffset() {
		return offset;
	}
	public void setOffset(int offset) {
		this.offset = offset;
	}
	public int getPage() {
		return page;
	}
	public void setPage(int page) {
		this.page = page;
	}
	public int getPageCount() {
		return pageCount;
	}
	public void setPageCount(int pageCount) {
		this.pageCount = pageCount;
	}
	public int getPageEnd() {
		return pageEnd;
	}
	public void setPageEnd(int pageEnd) {
		this.pageEnd = pageEnd;
	}
	public int getPageStart() {
		return pageStart;
	}
	public void setPageStart(int pageStart) {
		this.pageStart = pageStart;
	}
	public long getResponseTime() {
		return responseTime;
	}
	public void setResponseTime(long responseTime) {
		this.responseTime = responseTime;
	}
	public TurSNSiteSearchQueryContextQueryBean getQuery() {
		return query;
	}
	public void setQuery(TurSNSiteSearchQueryContextQueryBean query) {
		this.query = query;
	}
	public TurSNSiteSearchDefaultFieldsBean getDefaultFields() {
		return defaultFields;
	}
	public void setDefaultFields(TurSNSiteSearchDefaultFieldsBean defaultFields) {
		this.defaultFields = defaultFields;
	}

}
