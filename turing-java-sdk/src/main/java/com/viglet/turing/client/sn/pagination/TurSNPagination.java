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

package com.viglet.turing.client.sn.pagination;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Optional;

import com.viglet.turing.api.sn.bean.TurSNSiteSearchPaginationBean;

/**
 * Class to interact with current pagination.
 * 
 * @since 0.3.4
 */
public class TurSNPagination {

	List<TurSNSiteSearchPaginationBean> paginationList;

	private static String NEXT = "next";
	private static String PREVIOUS = "previous";
	private static String LAST = "last";
	private static String FIRST = "first";
	private static String CURRENT = "current";

	public TurSNPagination(List<TurSNSiteSearchPaginationBean> paginationList) {
		super();
		this.paginationList = paginationList;
	}

	public List<TurSNPaginationItem> getAllPages() {
		return paginationList.stream().map(turSNPaginationItem -> {
			return new TurSNPaginationItem(turSNPaginationItem);
		}).collect(Collectors.toList());
	}

	public Optional<TurSNPaginationItem> findByType(String type) {
		TurSNSiteSearchPaginationBean turSNSiteSearchPaginationBean = paginationList.stream()
				.filter(paginationItem -> Objects.nonNull(paginationItem))
				.filter(paginationItem -> Objects.nonNull(paginationItem.getType()))
				.filter(paginationItem -> paginationItem.getType().equals(type)).findFirst().orElse(null);

		return turSNSiteSearchPaginationBean == null ? Optional.empty()
				: Optional.of(new TurSNPaginationItem(turSNSiteSearchPaginationBean));

	}

	public Optional<TurSNPaginationItem> getCurrentPage() {
		return findByType(CURRENT);
	}

	public Optional<TurSNPaginationItem> getNextPage() {
		return findByType(NEXT);
	}

	public Optional<TurSNPaginationItem> getPreviousPage() {
		return findByType(PREVIOUS);
	}

	public Optional<TurSNPaginationItem> getLastPage() {
		return findByType(LAST).isPresent() ? findByType(LAST) : findByType(CURRENT);

	}

	public Optional<TurSNPaginationItem> getFirstPage() {
		return findByType(FIRST).isPresent() ? findByType(FIRST) : findByType(CURRENT);

	}

	public TurSNPaginationItem findByPageNumber(int pageNumber) {
		return new TurSNPaginationItem(paginationList.stream()
				.filter(paginationItem -> paginationItem.getPage() == pageNumber).findFirst().orElse(null));
	}

	public List<Integer> getPageNumberList() {
		return paginationList.stream().map(paginationItem -> paginationItem.getPage()).collect(Collectors.toList());
	}
}
