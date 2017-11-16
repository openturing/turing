package com.viglet.turing.api.sn.bean;

import java.util.List;

public class TurSNSiteSearchBean {

	private List<TurSNSiteSearchPaginationBean> pagination;
	private TurSNSiteSearchQueryContextBean queryContext;
	private TurSNSiteSearchResultsBean results;
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
	
	

}
