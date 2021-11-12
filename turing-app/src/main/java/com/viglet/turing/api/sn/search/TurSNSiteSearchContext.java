package com.viglet.turing.api.sn.search;

import java.net.URI;
import java.util.List;

public class TurSNSiteSearchContext {
	private String siteName;
	private String query;
	private Integer currentPage;
	private List<String> filterQueries;
	private List<String> targetingRules;
	private String sort;
	private Integer rows;
	private Integer autoCorrectionDisabled;
	private String locale;
	private URI uri;

	public TurSNSiteSearchContext(String siteName, String query, Integer currentPage, List<String> filterQueries,
			List<String> targetingRules, String sort, Integer rows, Integer autoCorrectionDisabled, String locale,
			URI uri) {
		super();
		this.siteName = siteName;
		this.query = query;
		this.currentPage = currentPage;
		this.filterQueries = filterQueries;
		this.targetingRules = targetingRules;
		this.sort = sort;
		this.rows = rows;
		this.autoCorrectionDisabled = autoCorrectionDisabled;
		this.locale = locale;
		this.uri = uri;
	}

	public String getSiteName() {
		return siteName;
	}

	public void setSiteName(String siteName) {
		this.siteName = siteName;
	}

	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public Integer getCurrentPage() {
		return currentPage;
	}

	public void setCurrentPage(Integer currentPage) {
		this.currentPage = currentPage;
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

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

	public URI getUri() {
		return uri;
	}

	public void setUri(URI uri) {
		this.uri = uri;
	}
}
