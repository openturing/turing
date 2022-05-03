package com.viglet.turing.commons.se;

import java.util.List;

public class TurSEParameters {
	private String query;
	private List<String> filterQueries;
	private Integer currentPage;
	private String sort;
	private Integer rows;
	private String group;
	private Integer autoCorrectionDisabled;

	public TurSEParameters(String query, List<String> filterQueries, Integer currentPage, String sort, Integer rows,
			String group, Integer autoCorrectionDisabled) {
		super();
		this.query = query;
		this.filterQueries = filterQueries;
		this.currentPage = currentPage;
		this.sort = sort;
		this.rows = rows;
		this.group = group;
		this.autoCorrectionDisabled = autoCorrectionDisabled;
	}

	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public List<String> getFilterQueries() {
		return filterQueries;
	}

	public void setFilterQueries(List<String> filterQueries) {
		this.filterQueries = filterQueries;
	}

	public Integer getCurrentPage() {
		return currentPage;
	}

	public void setCurrentPage(Integer currentPage) {
		this.currentPage = currentPage;
	}

	public String getSort() {
		return sort;
	}

	public void setSort(String sort) {
		this.sort = sort;
	}

	public Integer getRows() {
		return rows;
	}

	public void setRows(Integer rows) {
		this.rows = rows;
	}

	public Integer getAutoCorrectionDisabled() {
		return autoCorrectionDisabled;
	}

	public void setAutoCorrectionDisabled(Integer autoCorrectionDisabled) {
		this.autoCorrectionDisabled = autoCorrectionDisabled;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

}
