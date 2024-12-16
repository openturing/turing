package com.viglet.turing.se;

import java.util.List;

public class TurSEParameters {
	private String query;
	private List<String> filterQueries;
	private List<String> targetingRules;
	private Integer currentPage;
	private String sort;
	private Integer rows;
	private Integer autoCorrectionDisabled;

	public TurSEParameters(String query, List<String> filterQueries, List<String> targetingRules, Integer currentPage,
			String sort, Integer rows, Integer autoCorrectionDisabled) {
		super();
		this.query = query;
		this.filterQueries = filterQueries;
		this.targetingRules = targetingRules;
		this.currentPage = currentPage;
		this.sort = sort;
		this.rows = rows;
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

	public List<String> getTargetingRules() {
		return targetingRules;
	}

	public void setTargetingRules(List<String> targetingRules) {
		this.targetingRules = targetingRules;
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
}
