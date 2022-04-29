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

public class TurSNSiteSearchQueryContextBean {

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
