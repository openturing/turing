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

package com.viglet.turing.se.result;

import java.util.List;

import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.se.similar.TurSESimilarResult;

public class TurSEResults {
	private int qTime;
	private long elapsedTime;
	private long numFound;
	private long start;
	private int limit;
	private int pageCount;
	private int currentPage;
	private String queryString;
	private String sort;
	private TurSESpellCheckResult spellCheck;
	private List<TurSESimilarResult> similarResults;
	private List<TurSEFacetResult> facetResults;
	private List<TurSEResult> results;

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

	public TurSESpellCheckResult getSpellCheck() {
		return spellCheck;
	}

	public void setSpellCheck(TurSESpellCheckResult spellCheck) {
		this.spellCheck = spellCheck;
	}
}
