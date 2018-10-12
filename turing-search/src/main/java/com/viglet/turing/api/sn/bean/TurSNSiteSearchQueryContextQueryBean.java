package com.viglet.turing.api.sn.bean;

import org.springframework.stereotype.Component;

@Component
public class TurSNSiteSearchQueryContextQueryBean {

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
