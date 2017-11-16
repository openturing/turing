package com.viglet.turing.api.sn.bean;

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

}
