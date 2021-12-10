/*
 * Copyright (C) 2016-2021 the original author or authors. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.viglet.turing.client.sn;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Configure the query that will send to Turing AI.
 * 
 * @since 0.3.4
 */
public class TurSNQuery {

	/**
	 * Sorting types.
	 * 
	 * @since 0.3.4
	 */
	public enum ORDER {
		asc, desc
	}

	private String query;
	private int rows;
	private TurSNSortField sortField;
	private TurSNClientBetweenDates betweenDates;
	private List<String> fieldQueries;
	private List<String> targetingRules;
	private int pageNumber;

	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public int getRows() {
		return rows;
	}

	public void setRows(int rows) {
		this.rows = rows;
	}

	public TurSNSortField getSortField() {
		return sortField;
	}

	public void setSortField(TurSNSortField sortField) {
		this.sortField = sortField;
	}

	public void setSortField(String field, TurSNQuery.ORDER sort) {
		if (this.sortField == null) {
			this.sortField = new TurSNSortField();
		}
		this.sortField.setField(field);
		this.sortField.setSort(sort);
	}

	public void setSortField(TurSNQuery.ORDER sort) {
		if (this.sortField == null) {
			this.sortField = new TurSNSortField();
		}
		this.sortField.setField(null);
		this.sortField.setSort(sort);
	}

	public TurSNClientBetweenDates getBetweenDates() {
		return betweenDates;
	}

	public void setBetweenDates(TurSNClientBetweenDates betweenDates) {
		this.betweenDates = betweenDates;
	}

	public void setBetweenDates(String field, Date startDate, Date endDate) {

		this.betweenDates = new TurSNClientBetweenDates(field, startDate, endDate);
	}

	public void addFilterQuery(String... fq) {
		if (this.fieldQueries == null) {
			this.fieldQueries = new ArrayList<String>();
		}
		for (int i = 0; i < fq.length; i++) {
			fieldQueries.add(fq[i]);
		}
	}

	public List<String> getFieldQueries() {
		return fieldQueries;
	}

	public void setFieldQueries(List<String> fieldQueries) {
		this.fieldQueries = fieldQueries;
	}

	public void addTargetingRule(String... tr) {
		if (this.targetingRules == null) {
			this.targetingRules = new ArrayList<>();
		}
		for (int i = 0; i < tr.length; i++) {
			targetingRules.add(tr[i]);
		}
	}

	public List<String> getTargetingRules() {
		return targetingRules;
	}

	public void setTargetingRules(List<String> targetingRules) {
		this.targetingRules = targetingRules;
	}

	public int getPageNumber() {
		return pageNumber;
	}

	public void setPageNumber(int pageNumber) {
		this.pageNumber = pageNumber;
	}

}
