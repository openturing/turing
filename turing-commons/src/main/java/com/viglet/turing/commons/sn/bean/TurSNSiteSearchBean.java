/*
 * Copyright (C) 2016-2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.commons.sn.bean;

import java.util.List;

public class TurSNSiteSearchBean {

	private List<TurSNSiteSearchPaginationBean> pagination;
	private TurSNSiteSearchQueryContextBean queryContext;
	private TurSNSiteSearchResultsBean results;
	private List<TurSNSiteSearchGroupBean> groups;
	private TurSNSiteSearchWidgetBean widget;
	public List<TurSNSiteSearchPaginationBean> getPagination() {
		return pagination;
	}
	public void setPagination(List<TurSNSiteSearchPaginationBean> pagination) {
		this.pagination = pagination;
	}
	public TurSNSiteSearchQueryContextBean getQueryContext() {
		return queryContext;
	}
	public void setQueryContext(TurSNSiteSearchQueryContextBean queryContext) {
		this.queryContext = queryContext;
	}
	public TurSNSiteSearchResultsBean getResults() {
		return results;
	}
	public void setResults(TurSNSiteSearchResultsBean results) {
		this.results = results;
	}
	public TurSNSiteSearchWidgetBean getWidget() {
		return widget;
	}
	public void setWidget(TurSNSiteSearchWidgetBean widget) {
		this.widget = widget;
	}
	public List<TurSNSiteSearchGroupBean> getGroups() {
		return groups;
	}
	public void setGroups(List<TurSNSiteSearchGroupBean> groups) {
		this.groups = groups;
	}

}