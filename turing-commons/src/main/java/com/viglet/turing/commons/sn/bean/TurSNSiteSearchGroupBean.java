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

import java.util.List;

/**
 * List of documents with results by group of query of Turing AI Semantic
 * Navigation response.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 */

public class TurSNSiteSearchGroupBean {

	private String name;
	private int count;
	private int page;
	private int pageCount;
	private int pageEnd;
	private int pageStart;
	private int limit;
	private TurSNSiteSearchResultsBean results;
	private List<TurSNSiteSearchPaginationBean> pagination;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public TurSNSiteSearchResultsBean getResults() {
		return results;
	}

	public void setResults(TurSNSiteSearchResultsBean results) {
		this.results = results;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public List<TurSNSiteSearchPaginationBean> getPagination() {
		return pagination;
	}

	public void setPagination(List<TurSNSiteSearchPaginationBean> pagination) {
		this.pagination = pagination;
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

	public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}
}
