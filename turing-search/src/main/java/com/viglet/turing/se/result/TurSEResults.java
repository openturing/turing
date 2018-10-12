package com.viglet.turing.se.result;

import java.util.List;

import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.similar.TurSESimilarResult;

public class TurSEResults {
	int qTime;
	long elapsedTime;
	long numFound;
	long start;
	int limit;
	int pageCount;
	int currentPage;
	String queryString;
	String sort;
	
	List<TurSESimilarResult> similarResults;
	List<TurSEFacetResult> facetResults;
	List<TurSEResult> results;

	public List<TurSESimilarResult> getSimilarResults() {
		return similarResults;
	}

	public void setSimilarResults(List<TurSESimilarResult> similarResults) {
		this.similarResults = similarResults;
	}

	public List<TurSEFacetResult> getFacetResults() {
		return facetResults;
	}

	public void setFacetResults(List<TurSEFacetResult> facetResults) {
		this.facetResults = facetResults;
	}

	public String getSort() {
		return sort;
	}

	public void setSort(String sort) {
		this.sort = sort;
	}

	public String getQueryString() {
		return queryString;
	}

	public void setQueryString(String queryString) {
		this.queryString = queryString;
	}

	public int getPageCount() {
		return pageCount;
	}

	public void setPageCount(int pageCount) {
		this.pageCount = pageCount;
	}

	public int getCurrentPage() {
		return currentPage;
	}

	public void setCurrentPage(int currentPage) {
		this.currentPage = currentPage;
	}

	public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	public int getqTime() {
		return qTime;
	}

	public void setqTime(int qTime) {
		this.qTime = qTime;
	}

	public long getElapsedTime() {
		return elapsedTime;
	}

	public void setElapsedTime(long elapsedTime) {
		this.elapsedTime = elapsedTime;
	}

	public long getNumFound() {
		return numFound;
	}

	public void setNumFound(long numFound) {
		this.numFound = numFound;
	}

	public long getStart() {
		return start;
	}

	public void setStart(long start) {
		this.start = start;
	}

	public List<TurSEResult> getResults() {
		return results;
	}

	public void setResults(List<TurSEResult> results) {
		this.results = results;
	}
}
