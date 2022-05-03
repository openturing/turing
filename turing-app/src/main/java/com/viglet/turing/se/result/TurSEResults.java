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

import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.se.facet.TurSEFacetResult;

public class TurSEResults extends TurSEGenericResults {
	private int qTime;
	private long elapsedTime;
	private String queryString;
	private String sort;
	private TurSESpellCheckResult spellCheck;
	private List<TurSESimilarResult> similarResults;
	private List<TurSEFacetResult> facetResults;
	private List<TurSEGroup> groups;

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

	public TurSESpellCheckResult getSpellCheck() {
		return spellCheck;
	}

	public void setSpellCheck(TurSESpellCheckResult spellCheck) {
		this.spellCheck = spellCheck;
	}

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

	public List<TurSEGroup> getGroups() {
		return groups;
	}

	public void setGroups(List<TurSEGroup> groups) {
		this.groups = groups;
	}

}
