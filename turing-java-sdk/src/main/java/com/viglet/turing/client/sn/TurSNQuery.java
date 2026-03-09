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
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import lombok.Setter;

/**
 * Configure the query that will send to Turing ES.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.4
 */
public class TurSNQuery {

	/**
	 * Sorting types.
	 * 
	 * @since 0.3.4
	 */
	@SuppressWarnings("java:S115")
	public enum Order {
		asc, desc, relevance
	}

	@Setter
	private String query;
	@Setter
	private int rows;
	@Setter
	private String groupBy;
	private TurSNSortField sortField;
	private TurSNClientBetweenDates betweenDates;
	@Setter
	private List<String> fieldQueries;
	@Setter
	private List<String> targetingRules;
	@Setter
	private int pageNumber;
	@Setter
	private boolean populateMetrics;

	public String getQuery() {
		return query;
	}

	public int getRows() {
		return rows;
	}

	public String getGroupBy() {
		return groupBy;
	}

	public TurSNSortField getSortField() {
		return sortField;
	}

	public void setSortField(TurSNSortField sortField) {
		this.sortField = sortField;
	}

	public void setSortField(String field, TurSNQuery.Order sort) {
		if (this.sortField == null) {
			this.sortField = new TurSNSortField();
		}
		this.sortField.setField(field);
		this.sortField.setSort(sort);
	}

	public void setSortField(TurSNQuery.Order sort) {
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
			this.fieldQueries = new ArrayList<>();
		}
		fieldQueries.addAll(Arrays.asList(fq));
	}

	public List<String> getFieldQueries() {
		return fieldQueries;
	}

	public void addTargetingRule(String... tr) {
		if (this.targetingRules == null) {
			this.targetingRules = new ArrayList<>();
		}
		targetingRules.addAll(Arrays.asList(tr));
	}

	public List<String> getTargetingRules() {
		return targetingRules;
	}

	public int getPageNumber() {
		return pageNumber;
	}

	public boolean isPopulateMetrics() {
		return populateMetrics;
	}

}
